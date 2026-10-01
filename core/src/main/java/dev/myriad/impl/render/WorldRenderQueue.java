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

/** Collects {@code Renderer3D} shapes during Render3DEvent and draws them in a few batches afterwards. */
public final class WorldRenderQueue {
	private static final Layer DEPTH = new Layer();
	private static final Layer XRAY = new Layer();
	private static final FloatArrayList TRACERS = new FloatArrayList();
	private static final IntArrayList TRACER_COLORS = new IntArrayList();
	private static final FloatArrayList TRACER_WIDTHS = new FloatArrayList();
	private static final float DEFAULT_LINE_WIDTH = 2f;
	private static float lineWidth = DEFAULT_LINE_WIDTH;

	private WorldRenderQueue() {
	}

	private static Layer layer(boolean throughWalls) {
		return throughWalls ? XRAY : DEPTH;
	}

	public static void lineWidth(float width) {
		lineWidth = width;
	}

	public static void boxFill(Box b, int color, boolean throughWalls) {
		Layer l = layer(throughWalls);
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
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
		TRACERS.add((float) to.x);
		TRACERS.add((float) to.y);
		TRACERS.add((float) to.z);
		TRACER_COLORS.add(color);
		TRACER_WIDTHS.add(lineWidth);
	}

	/** Draws everything queued; called after Render3DEvent with the camera rotation on the model-view stack. */
	public static void flush(Camera camera) {
		Vec3d cam = camera.getPos();
		// Tracers start just in front of the camera along its look vector.
		if (!TRACERS.isEmpty()) {
			Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).multiply(0.5).add(cam);
			for (int i = 0; i < TRACER_COLORS.size(); i++) {
				lineWidth = TRACER_WIDTHS.getFloat(i);
				XRAY.line(look.x, look.y, look.z, TRACERS.getFloat(i * 3), TRACERS.getFloat(i * 3 + 1), TRACERS.getFloat(i * 3 + 2), TRACER_COLORS.getInt(i), TRACER_COLORS.getInt(i));
			}
		}

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.depthMask(false);

		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		DEPTH.draw(cam);
		RenderSystem.disableDepthTest();
		XRAY.draw(cam);

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

		void line(double ax, double ay, double az, double bx, double by, double bz, int ca, int cb) {
			lines.add((float) ax); lines.add((float) ay); lines.add((float) az);
			lines.add((float) bx); lines.add((float) by); lines.add((float) bz);
			lineColors.add(ca);
			lineColors.add(cb);
			lineWidths.add(lineWidth);
		}

		void draw(Vec3d cam) {
			Matrix4f m = new Matrix4f();
			float ox = (float) cam.x, oy = (float) cam.y, oz = (float) cam.z;
			if (!quadColors.isEmpty()) {
				BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
				for (int q = 0; q < quadColors.size(); q++) {
					int c = quadColors.getInt(q);
					for (int v = 0; v < 4; v++) {
						int i = q * 12 + v * 3;
						buf.vertex(m, quads.getFloat(i) - ox, quads.getFloat(i + 1) - oy, quads.getFloat(i + 2) - oz).color(c);
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
					float ax = lines.getFloat(i) - ox, ay = lines.getFloat(i + 1) - oy, az = lines.getFloat(i + 2) - oz;
					float bx = lines.getFloat(i + 3) - ox, by = lines.getFloat(i + 4) - oy, bz = lines.getFloat(i + 5) - oz;
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
