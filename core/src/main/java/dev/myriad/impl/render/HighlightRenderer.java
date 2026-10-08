package dev.myriad.impl.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.GpuFormat;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.ListenerFlag;
import dev.myriad.api.event.events.HighlightEvent;
import dev.myriad.api.render.HighlightStyle;
import dev.myriad.impl.compat.EntityCullingCompat;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws {@link HighlightEvent} highlights: outlines, glows and fills around exact silhouettes.
 * <p>
 * Silhouettes come from vanilla's entity outline target, the one the glowing effect uses. Every entity layer (armour,
 * held items, capes) already passes the entity's outline colour along and is drawn into that target with its texture's
 * cut-outs, so a highlighted entity's whole shape costs no extra work: its outline colour is set to a highlight id
 * (RGB, 24 bits) and vanilla draws the mask. Shapes are drawn into the same target. While Myriad owns the target in a
 * frame, vanilla's own outline post effect is skipped and glowing entities become highlights in a vanilla-like style.
 * <p>
 * Then, only over the screen area the highlights cover (scissored):
 * <ol>
 * <li>Resolve (only if some highlight isn't through walls): drop mask pixels hidden behind the world, comparing the
 * mask's depth with the scene's before translucent terrain is drawn (so water doesn't hide what's under it).</li>
 * <li>Spread: per pixel, the nearest mask pixel in its row within the reach (early-out from the centre).</li>
 * <li>Composite, after the world and before the hand: the nearest mask pixel in its column of row results, which is the
 * exact Euclidean distance; outline, glow and fill from that distance and the highlight's data.</li>
 * </ol>
 * Two texture reads per tap at most, and the work scales with the reach in pixels, not its square. Per-highlight data
 * (colours, gradient rectangle, style) lives in a texel buffer, so there's no limit on how many there are.
 */
public final class HighlightRenderer {
	public static final HighlightRenderer INSTANCE = new HighlightRenderer();

	/** vec4s per highlight in the data buffer: top colour, bottom colour, gradient rectangle, shape, fill. */
	private static final int TEXELS = 5;
	private static final int FLOATS = TEXELS * 4;
	private static final int MAX_RADIUS = 64;
	/** Glowing entities while Myriad owns the outline target: close to vanilla's soft outline. */
	private static final HighlightStyle VANILLA_GLOW = HighlightStyle.OUTLINE.withOutlineWidth(1).withGlow(3).withGlowStrength(0.7f);

	private final HighlightEvent.Entity entityEvent = new HighlightEvent.Entity();
	private final HighlightEvent.Shapes shapesEvent = new HighlightEvent.Shapes();
	private ListenerFlag entityListeners, shapeListeners;

	// This frame's highlights.
	private long frame;
	private boolean armed;
	private int count;
	private float[] data = new float[FLOATS * 64];
	/** Per highlight: the box the gradient spans, then a box sure to contain the whole silhouette (12 doubles). */
	private double[] bounds = new double[12 * 64];
	private float maxReach;
	private boolean anyDepthTested;
	/** Shape geometry relative to the camera (min x/y/z, max x/y/z per box) and each box's encoded id. */
	private FloatArrayList shapeBoxes = new FloatArrayList();
	/** Entities already asked about this frame (while deciding whether to keep them from being culled). */
	private final Reference2ObjectOpenHashMap<Entity, HighlightStyle> asked = new Reference2ObjectOpenHashMap<>();
	private final Reference2IntOpenHashMap<Entity> askedColor = new Reference2IntOpenHashMap<>();
	private IntArrayList shapeIds = new IntArrayList();

	// This frame's camera, captured before the level is drawn.
	private final Matrix4f projection = new Matrix4f();
	private final Matrix4f view = new Matrix4f();
	private final Matrix4f viewProjection = new Matrix4f();
	private final Vector4f corner = new Vector4f();
	private double camX, camY, camZ;
	private boolean outlinesShown;
	private Frustum frustum;

	// GPU state.
	private TextureTarget resolved;
	private TextureTarget spread;
	private MappableRingBuffer buffer;
	private boolean prepared;
	private final int[] compositeRect = new int[4];

	private HighlightRenderer() {
	}

	/** Advances every time the level is extracted; styles built from settings are cached per frame. */
	public long frame() {
		return frame;
	}

	/** Whether anything listens for highlights this frame (checked before doing any per-entity work). */
	public boolean isArmed() {
		return armed;
	}

	/** Start of a frame's extraction, before any entity is. */
	public void beginFrame() {
		frame++;
		count = 0;
		maxReach = 0;
		anyDepthTested = false;
		prepared = false;
		shapeBoxes.clear();
		shapeIds.clear();
		asked.clear();
		askedColor.clear();
		if (!Myriad.isReady()) {
			armed = false;
			return;
		}
		if (entityListeners == null) {
			entityListeners = Myriad.events().flag(HighlightEvent.Entity.class);
			shapeListeners = Myriad.events().flag(HighlightEvent.Shapes.class);
		}
		armed = entityListeners.isSet() || shapeListeners.isSet();
	}

	/**
	 * Whether {@code entity} is drawn, given whether the game would draw it ({@code shown}). Occlusion culling (Sodium's
	 * per section, EntityCulling's traced) skips entities behind blocks, which through-walls highlights must still draw,
	 * so an entity on screen that's culled, or any entity on screen while EntityCulling (which culls later) is installed,
	 * is asked about here; the answer is kept for {@link #entity}.
	 */
	public boolean shouldRender(Entity entity, boolean shown, Frustum frustum, double camX, double camY, double camZ) {
		if (!entityListeners.isSet()) return shown;
		if (!shown) {
			if (!entity.shouldRender(camX, camY, camZ) || !frustum.isVisible(entity.getBoundingBox().inflate(0.5))) return false;
		} else if (!EntityCullingCompat.ACTIVE) {
			return true;
		}
		HighlightEvent.Entity e = Myriad.events().post(entityEvent.reset(entity, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
		asked.put(entity, e.style());
		askedColor.put(entity, e.color());
		if (e.style() == null || !e.style().throughWalls()) return shown;
		EntityCullingCompat.keepVisible(entity);
		return true;
	}

	/** An entity's render state was just built: ask who highlights it, and point its outline colour at the highlight. */
	public void entity(EntityRenderState state, Entity entity, float tickDelta, AABB cullingBox) {
		if (!armed) return;
		HighlightStyle style = null;
		int color = 0;
		if (asked.containsKey(entity)) {
			style = asked.get(entity);
			color = askedColor.getInt(entity);
		} else if (entityListeners.isSet()) {
			HighlightEvent.Entity e = Myriad.events().post(entityEvent.reset(entity, tickDelta));
			style = e.style();
			color = e.color();
		}
		if (style == null) {
			if (state.outlineColor == 0) return;
			style = VANILLA_GLOW;
			color = state.outlineColor;
		}
		// The silhouette can reach past the hitbox (held items, wings, swinging arms): use the renderer's culling box,
		// moved to where the entity is drawn, with a block of slack.
		Vec3 pos = entity.getPosition(tickDelta);
		double dx = pos.x - entity.getX(), dy = pos.y - entity.getY(), dz = pos.z - entity.getZ();
		AABB hitbox = entity.getBoundingBox().move(dx, dy, dz);
		AABB around = cullingBox.move(dx, dy, dz).inflate(1);
		int id = add(style, color, hitbox, around);
		if (id >= 0) state.outlineColor = encode(id);
	}

	/** Posts {@link HighlightEvent.Shapes} and submits the shapes' silhouettes to the outline target. */
	public void collectShapes(SubmitNodeCollector collector, PoseStack poseStack, float tickDelta) {
		if (!armed || !shapeListeners.isSet()) return;
		Myriad.events().post(shapesEvent.reset(tickDelta));
		if (shapeIds.isEmpty()) return;
		FloatArrayList boxes = shapeBoxes;
		IntArrayList ids = shapeIds;
		// Drawn later in the frame: hand the lists over and start new ones.
		shapeBoxes = new FloatArrayList(boxes.size());
		shapeIds = new IntArrayList(ids.size());
		collector.submitCustomGeometry(poseStack, MyriadPipelines.HIGHLIGHT_SHAPES, (pose, buf) -> {
			for (int n = 0; n < ids.size(); n++) {
				int i = n * 6, c = ids.getInt(n);
				float x0 = boxes.getFloat(i), y0 = boxes.getFloat(i + 1), z0 = boxes.getFloat(i + 2);
				float x1 = boxes.getFloat(i + 3), y1 = boxes.getFloat(i + 4), z1 = boxes.getFloat(i + 5);
				quad(buf, pose, c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
				quad(buf, pose, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
				quad(buf, pose, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
				quad(buf, pose, c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
				quad(buf, pose, c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
				quad(buf, pose, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
			}
		});
	}

	private static void quad(com.mojang.blaze3d.vertex.VertexConsumer buf, PoseStack.Pose pose, int c, float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz) {
		buf.addVertex(pose, ax, ay, az).setColor(c);
		buf.addVertex(pose, bx, by, bz).setColor(c);
		buf.addVertex(pose, cx, cy, cz).setColor(c);
		buf.addVertex(pose, dx, dy, dz).setColor(c);
	}

	/** {@link HighlightEvent.Shapes#box}. Off-screen shapes are skipped, so callers can pass everything in range. */
	public void box(AABB box, HighlightStyle style, int color) {
		if (!armed || style == null || (color >>> 24) == 0 || (frustum != null && !frustum.isVisible(box))) return;
		int id = add(style, color, box, box);
		if (id >= 0) shapeBox(box, 0, 0, 0, encode(id));
	}

	/** {@link HighlightEvent.Shapes#block}. */
	public void block(BlockPos pos, HighlightStyle style, int color) {
		if (!armed || style == null || (color >>> 24) == 0) return;
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
		AABB bounds = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
		if (frustum != null && !frustum.isVisible(bounds)) return;
		int id = add(style, color, bounds, bounds);
		if (id < 0) return;
		int c = encode(id);
		if (shape.isEmpty()) shapeBox(new AABB(0, 0, 0, 1, 1, 1), pos.getX(), pos.getY(), pos.getZ(), c);
		else shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> shapeBox(new AABB(x0, y0, z0, x1, y1, z1), pos.getX(), pos.getY(), pos.getZ(), c));
	}

	private void shapeBox(AABB b, double ox, double oy, double oz, int encoded) {
		// Relative to the camera while still in doubles, so boxes far from spawn keep their precision.
		shapeBoxes.add((float) (b.minX + ox - camX));
		shapeBoxes.add((float) (b.minY + oy - camY));
		shapeBoxes.add((float) (b.minZ + oz - camZ));
		shapeBoxes.add((float) (b.maxX + ox - camX));
		shapeBoxes.add((float) (b.maxY + oy - camY));
		shapeBoxes.add((float) (b.maxZ + oz - camZ));
		shapeIds.add(encoded);
	}

	/** Records a highlight; returns its id, or -1 when there are too many to encode. */
	private int add(HighlightStyle style, int color, AABB gradientBox, AABB around) {
		if (count >= 1 << 24) return -1;
		int id = count++;
		if (data.length < count * FLOATS) {
			data = Arrays.copyOf(data, data.length * 2);
			bounds = Arrays.copyOf(bounds, bounds.length * 2);
		}
		float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
		float alpha = (color >>> 24) / 255f;
		int i = id * FLOATS;
		putColor(i, color, alpha);
		if (style.hasGradient()) putColor(i + 4, style.gradient(), alpha * (style.gradient() >>> 24) / 255f);
		else System.arraycopy(data, i, data, i + 4, 4);
		// i + 8..11: the gradient rectangle, filled in once the frame's projection is known.
		// Thinner with distance (to half), but an outline never drops below a pixel.
		Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
		float far = style.widthScale(Math.sqrt(gradientBox.distanceToSqr(eye)));
		float outline = style.outlineWidth() * scale * far, glow = style.glow() * scale * far;
		if (outline > 0) outline = Math.max(1, outline);
		data[i + 12] = outline;
		data[i + 13] = glow;
		data[i + 14] = style.glowStrength();
		data[i + 15] = style.fillOpacity();
		data[i + 16] = style.fill().shaderId();
		data[i + 17] = style.dotSpacing() * scale;
		data[i + 18] = style.dotSize() * scale * 0.5f;
		data[i + 19] = style.throughWalls() ? 0 : 1;
		maxReach = Math.max(maxReach, outline + glow);
		anyDepthTested |= !style.throughWalls();
		int b = id * 12;
		putBox(b, gradientBox);
		putBox(b + 6, around);
		return id;
	}

	private void putColor(int i, int argb, float alpha) {
		data[i] = (argb >> 16 & 0xFF) / 255f;
		data[i + 1] = (argb >> 8 & 0xFF) / 255f;
		data[i + 2] = (argb & 0xFF) / 255f;
		data[i + 3] = alpha;
	}

	private void putBox(int b, AABB box) {
		bounds[b] = box.minX;
		bounds[b + 1] = box.minY;
		bounds[b + 2] = box.minZ;
		bounds[b + 3] = box.maxX;
		bounds[b + 4] = box.maxY;
		bounds[b + 5] = box.maxZ;
	}

	/** A highlight id as an opaque outline colour: the outline shader writes it to the mask's RGB unchanged. */
	public static int encode(int id) {
		return 0xFF000000 | (id & 0xFF) << 16 | (id >> 8 & 0xFF) << 8 | id >> 16 & 0xFF;
	}

	/** The id {@link #encode} packed into an outline colour's RGB. */
	public static int decode(int argb) {
		return (argb >> 16 & 0xFF) | (argb >> 8 & 0xFF) << 8 | (argb & 0xFF) << 16;
	}

	/** The level camera, at the start of {@code LevelRenderer.render}. */
	public void camera(Vec3 pos, Matrix4fc modelView, Frustum frustum, boolean outlinesShown) {
		this.frustum = frustum;
		camX = pos.x;
		camY = pos.y;
		camZ = pos.z;
		view.set(modelView);
		this.outlinesShown = outlinesShown;
	}

	/** The level projection, view bobbing and nausea included, as the frame is drawn with it. */
	public void projection(Matrix4fc projection) {
		this.projection.set(projection);
	}

	/** Whether this frame's outline target holds highlight ids (so vanilla mustn't post-process or blit it). */
	public boolean ownsOutlines() {
		return count > 0 && outlinesShown;
	}

	/**
	 * Right after the outline target is drawn, before translucent terrain: resolve hidden pixels and run the row pass.
	 */
	public void afterMask() {
		prepared = false;
		if (!ownsOutlines()) return;
		RenderTarget mask = Minecraft.getInstance().levelRenderer.entityOutlineTarget;
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		if (mask == null || mask.getColorTextureView() == null || main.getDepthTextureView() == null) return;
		int w = mask.width, h = mask.height;
		int radius = Math.min(MAX_RADIUS, (int) Math.ceil(maxReach) + 1);
		int[] area = screenArea(w, h);
		if (area == null) return;
		resize(w, h);
		GpuBuffer gpuData = upload(radius);

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		GpuTextureView source = mask.getColorTextureView();
		if (anyDepthTested) {
			try (RenderPass pass = begin(encoder, MyriadPipelines.HIGHLIGHT_RESOLVE, resolved.getColorTextureView(), gpuData, grow(area, 2 * radius, 2 * radius, w, h))) {
				pass.bindTexture("Mask", source, nearest);
				pass.bindTexture("MaskDepth", mask.getDepthTextureView(), nearest);
				pass.bindTexture("SceneDepth", main.getDepthTextureView(), nearest);
				pass.draw(3, 1, 0, 0);
			}
			source = resolved.getColorTextureView();
		}
		try (RenderPass pass = begin(encoder, MyriadPipelines.HIGHLIGHT_SPREAD, spread.getColorTextureView(), gpuData, grow(area, radius, 2 * radius, w, h))) {
			pass.bindTexture("InSampler", source, nearest);
			pass.draw(3, 1, 0, 0);
		}
		int[] c = grow(area, radius, radius, w, h);
		System.arraycopy(c, 0, compositeRect, 0, 4);
		prepared = true;
	}

	/** After the level is drawn, before the hand: outline, glow and fill onto the main target. */
	public void composite() {
		if (!prepared) return;
		prepared = false;
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (RenderPass pass = begin(encoder, MyriadPipelines.HIGHLIGHT_COMPOSITE, main.getColorTextureView(), buffer.currentBuffer(), compositeRect)) {
			pass.bindTexture("SpreadSampler", spread.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(3, 1, 0, 0);
		}
	}

	private RenderPass begin(CommandEncoder encoder, RenderPipeline pipeline, GpuTextureView target, GpuBuffer gpuData, int[] rect) {
		RenderPass pass = encoder.createRenderPass(() -> "Myriad highlights", target, Optional.empty(), null, OptionalDouble.empty());
		pass.setPipeline(pipeline);
		RenderSystem.bindDefaultUniforms(pass);
		pass.setUniform("Highlights", gpuData);
		pass.enableScissor(rect[0], rect[1], rect[2], rect[3]);
		return pass;
	}

	/**
	 * Projects every highlight with this frame's camera: fills in the gradient rectangles and returns the framebuffer
	 * area {x0, y0, x1, y1} that holds every silhouette, or null if none is on screen. Pixel rows count up from the
	 * bottom of the image (OpenGL) or down from the top (Vulkan), the same way texel rows and gl_FragCoord do on each,
	 * so the shaders and scissor agree either way.
	 */
	private int[] screenArea(int w, int h) {
		projection.mul(view, viewProjection);
		float[] r = new float[4];
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
		for (int id = 0; id < count; id++) {
			int b = id * 12, i = id * FLOATS;
			if (!project(b, w, h, r)) {
				r[0] = 0;
				r[1] = 0;
				r[2] = w;
				r[3] = h;
			}
			data[i + 8] = r[0];
			data[i + 9] = r[1];
			data[i + 10] = r[2];
			data[i + 11] = r[3];
			if (!project(b + 6, w, h, r)) {
				r[0] = 0;
				r[1] = 0;
				r[2] = w;
				r[3] = h;
			}
			x0 = Math.min(x0, (int) Math.floor(r[0]));
			y0 = Math.min(y0, (int) Math.floor(r[1]));
			x1 = Math.max(x1, (int) Math.ceil(r[2]));
			y1 = Math.max(y1, (int) Math.ceil(r[3]));
		}
		x0 = Math.max(0, x0 - 1);
		y0 = Math.max(0, y0 - 1);
		x1 = Math.min(w, x1 + 1);
		y1 = Math.min(h, y1 + 1);
		return x1 <= x0 || y1 <= y0 ? null : new int[]{x0, y0, x1, y1};
	}

	/** The pixel rectangle around the box at {@code bounds[b]}; false if part of it is behind the camera. */
	private boolean project(int b, int w, int h, float[] out) {
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (int k = 0; k < 8; k++) {
			double x = bounds[b + ((k & 1) == 0 ? 0 : 3)], y = bounds[b + ((k & 2) == 0 ? 1 : 4)], z = bounds[b + ((k & 4) == 0 ? 2 : 5)];
			viewProjection.transform(corner.set((float) (x - camX), (float) (y - camY), (float) (z - camZ), 1));
			if (corner.w <= 1e-4f) return false;
			float px = (corner.x / corner.w * 0.5f + 0.5f) * w, py = (corner.y / corner.w * 0.5f + 0.5f) * h;
			minX = Math.min(minX, px);
			minY = Math.min(minY, py);
			maxX = Math.max(maxX, px);
			maxY = Math.max(maxY, py);
		}
		out[0] = minX;
		out[1] = minY;
		out[2] = maxX;
		out[3] = maxY;
		return true;
	}

	/** {@code area} grown by {@code dx}, {@code dy} and clamped to the screen, as a scissor {x, y, width, height}. */
	private static int[] grow(int[] area, int dx, int dy, int w, int h) {
		int x0 = Math.max(0, area[0] - dx), y0 = Math.max(0, area[1] - dy);
		int x1 = Math.min(w, area[2] + dx), y1 = Math.min(h, area[3] + dy);
		return new int[]{x0, y0, x1 - x0, y1 - y0};
	}

	/** Writes the header and every highlight to this frame's slot of the data buffer. */
	private GpuBuffer upload(int radius) {
		int bytes = (1 + count * TEXELS) * 16;
		if (buffer == null || buffer.size() < bytes) {
			if (buffer != null) GpuGarbage.close(buffer);
			int size = Math.max(4096, Integer.highestOneBit(bytes - 1) << 1);
			buffer = new MappableRingBuffer(() -> "Myriad highlights", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, size);
		} else {
			buffer.rotate();
		}
		GpuBuffer gpu = buffer.currentBuffer();
		try (GpuBufferSlice.MappedView view = gpu.map(false, true)) {
			FloatBuffer out = view.data().order(ByteOrder.nativeOrder()).asFloatBuffer();
			out.put(radius).put((float) Minecraft.getInstance().getWindow().getGuiScale()).put(count).put(0);
			out.put(data, 0, count * FLOATS);
		}
		return gpu;
	}

	private void resize(int w, int h) {
		if (spread == null) {
			resolved = new TextureTarget("Myriad highlights resolved", w, h, false, GpuFormat.RGBA8_UNORM);
			spread = new TextureTarget("Myriad highlights spread", w, h, false, GpuFormat.RGBA8_UNORM);
		} else if (spread.width != w || spread.height != h) {
			resolved.resize(w, h);
			spread.resize(w, h);
		}
	}
}
