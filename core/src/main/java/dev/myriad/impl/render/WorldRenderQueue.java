package dev.myriad.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Collects {@code Renderer3D} shapes during Render3DEvent and draws them in a few batches afterwards.
 * <p>
 * Shapes are stored relative to the camera, subtracted while still in doubles. Absolute world coordinates don't fit in
 * a float far from spawn (at 1,000,000 a float only resolves 1/16 of a block), which makes boxes jitter as the camera
 * moves and lose their corners.
 */
public final class WorldRenderQueue {
	private static final Layer DEPTH = new Layer();
	private static final Layer XRAY = new Layer();
	private static final FloatArrayList TRACERS = new FloatArrayList();
	private static final IntArrayList TRACER_COLORS = new IntArrayList();
	private static final FloatArrayList TRACER_WIDTHS = new FloatArrayList();
	private static final float DEFAULT_LINE_WIDTH = 2f;
	private static float lineWidth = DEFAULT_LINE_WIDTH;
	private static double originX, originY, originZ;

	private WorldRenderQueue() {
	}

	private static Layer layer(boolean throughWalls) {
		return throughWalls ? XRAY : DEPTH;
	}

	/** Sets the camera position shapes are stored relative to; called before Render3DEvent is posted. */
	public static void begin(Vec3d camera) {
		originX = camera.x;
		originY = camera.y;
		originZ = camera.z;
	}

	public static void lineWidth(float width) {
		lineWidth = width;
	}

	public static void boxFill(Box b, int color, boolean throughWalls) {
		Layer l = layer(throughWalls);
		float x0 = (float) (b.minX - originX), y0 = (float) (b.minY - originY), z0 = (float) (b.minZ - originZ);
		float x1 = (float) (b.maxX - originX), y1 = (float) (b.maxY - originY), z1 = (float) (b.maxZ - originZ);
		// six faces, four vertices each
		l.quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, color);
		l.quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, color);
		l.quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, color);
		l.quad(x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, color);
		l.quad(x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, color);
		l.quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, color);
	}

	public static void boxLines(Box b, int color, boolean throughWalls) {
		Layer l = layer(throughWalls);
		double x0 = b.minX, y0 = b.minY, z0 = b.minZ, x1 = b.maxX, y1 = b.maxY, z1 = b.maxZ;
		double[][] edges = {
			{x0, y0, z0, x1, y0, z0}, {x1, y0, z0, x1, y0, z1}, {x1, y0, z1, x0, y0, z1}, {x0, y0, z1, x0, y0, z0},
			{x0, y1, z0, x1, y1, z0}, {x1, y1, z0, x1, y1, z1}, {x1, y1, z1, x0, y1, z1}, {x0, y1, z1, x0, y1, z0},
			{x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x1, y0, z1, x1, y1, z1}, {x0, y0, z1, x0, y1, z1}
		};
		for (double[] e : edges) l.line(e[0], e[1], e[2], e[3], e[4], e[5], color, color);
	}

	public static void line(Vec3d a, Vec3d b, int ca, int cb, boolean throughWalls) {
		layer(throughWalls).line(a.x, a.y, a.z, b.x, b.y, b.z, ca, cb);
	}

	public static void tracer(Vec3d to, int color) {
		TRACERS.add((float) (to.x - originX));
		TRACERS.add((float) (to.y - originY));
		TRACERS.add((float) (to.z - originZ));
		TRACER_COLORS.add(color);
		TRACER_WIDTHS.add(lineWidth);
	}

	/** Draws everything queued; called after Render3DEvent with the camera rotation on the model-view stack. */
	public static void flush(Camera camera) {
		// Tracers start just in front of the camera along its look vector (the camera is the origin here).
		if (!TRACERS.isEmpty()) {
			Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).multiply(0.5);
			for (int i = 0; i < TRACER_COLORS.size(); i++) {
				lineWidth = TRACER_WIDTHS.getFloat(i);
				XRAY.relativeLine((float) look.x, (float) look.y, (float) look.z, TRACERS.getFloat(i * 3), TRACERS.getFloat(i * 3 + 1), TRACERS.getFloat(i * 3 + 2), TRACER_COLORS.getInt(i), TRACER_COLORS.getInt(i));
			}
		}

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.depthMask(false);

		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		// Pulled towards the camera so shapes lying on a block face (hole fills, block outlines) don't z-fight with it.
		RenderSystem.enablePolygonOffset();
		RenderSystem.polygonOffset(-1f, -10f);
		DEPTH.draw();
		RenderSystem.polygonOffset(0f, 0f);
		RenderSystem.disablePolygonOffset();
		RenderSystem.disableDepthTest();
		XRAY.draw();

		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		RenderSystem.lineWidth(1f);

		TRACERS.clear();
		TRACER_COLORS.clear();
		TRACER_WIDTHS.clear();
		lineWidth = DEFAULT_LINE_WIDTH;
	}

	private static final class Layer {
		final FloatArrayList quads = new FloatArrayList();
		final IntArrayList quadColors = new IntArrayList();
		final FloatArrayList lines = new FloatArrayList();
		final IntArrayList lineColors = new IntArrayList();
		final FloatArrayList lineWidths = new FloatArrayList();

		void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, int color) {
			quads.add(ax); quads.add(ay); quads.add(az);
			quads.add(bx); quads.add(by); quads.add(bz);
			quads.add(cx); quads.add(cy); quads.add(cz);
			quads.add(dx); quads.add(dy); quads.add(dz);
			quadColors.add(color);
		}

		/** A line between two absolute world positions. */
		void line(double ax, double ay, double az, double bx, double by, double bz, int ca, int cb) {
			relativeLine((float) (ax - originX), (float) (ay - originY), (float) (az - originZ),
				(float) (bx - originX), (float) (by - originY), (float) (bz - originZ), ca, cb);
		}

		/** A line between two camera-relative positions. */
		void relativeLine(float ax, float ay, float az, float bx, float by, float bz, int ca, int cb) {
			lines.add(ax); lines.add(ay); lines.add(az);
			lines.add(bx); lines.add(by); lines.add(bz);
			lineColors.add(ca);
			lineColors.add(cb);
			lineWidths.add(lineWidth);
		}

		void draw() {
			Matrix4f m = new Matrix4f();
			if (!quadColors.isEmpty()) {
				BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
				for (int q = 0; q < quadColors.size(); q++) {
					int c = quadColors.getInt(q);
					for (int v = 0; v < 4; v++) {
						int i = q * 12 + v * 3;
						buf.vertex(m, quads.getFloat(i), quads.getFloat(i + 1), quads.getFloat(i + 2)).color(c);
					}
				}
				var built = buf.endNullable();
				if (built != null) {
					RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
					BufferRenderer.drawWithGlobalProgram(built);
				}
			}
			// One batch per distinct line width (GL line width is per draw call).
			FloatArrayList widths = new FloatArrayList();
			for (int l = 0; l < lineWidths.size(); l++) if (!widths.contains(lineWidths.getFloat(l))) widths.add(lineWidths.getFloat(l));
			for (int wi = 0; wi < widths.size(); wi++) {
				float width = widths.getFloat(wi);
				RenderSystem.lineWidth(width);
				BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
				for (int l = 0; l < lineColors.size() / 2; l++) {
					if (lineWidths.getFloat(l) != width) continue;
					int i = l * 6;
					float ax = lines.getFloat(i), ay = lines.getFloat(i + 1), az = lines.getFloat(i + 2);
					float bx = lines.getFloat(i + 3), by = lines.getFloat(i + 4), bz = lines.getFloat(i + 5);
					float nx = bx - ax, ny = by - ay, nz = bz - az;
					float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
					if (len < 1e-5f) continue;
					nx /= len;
					ny /= len;
					nz /= len;
					buf.vertex(m, ax, ay, az).color(lineColors.getInt(l * 2)).normal(nx, ny, nz);
					buf.vertex(m, bx, by, bz).color(lineColors.getInt(l * 2 + 1)).normal(nx, ny, nz);
				}
				var built = buf.endNullable();
				if (built != null) {
					RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
					BufferRenderer.drawWithGlobalProgram(built);
				}
			}
			quads.clear();
			quadColors.clear();
			lines.clear();
			lineColors.clear();
			lineWidths.clear();
		}
	}
}
