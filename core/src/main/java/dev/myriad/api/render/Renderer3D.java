package dev.myriad.api.render;

import dev.myriad.impl.render.WorldRenderQueue;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Batched world-space shapes. Call these from a {@code Render3DEvent} handler; everything queued is drawn once
 * after all handlers ran. Coordinates are absolute world coordinates.
 */
public final class Renderer3D {
	public enum ShapeMode {
		FILL, LINES, BOTH
	}

	private Renderer3D() {
	}

	/** A box with a translucent fill and/or outline. {@code throughWalls} draws it without depth testing. */
	public static void box(Box box, int fillColor, int lineColor, ShapeMode mode, boolean throughWalls) {
		if (mode != ShapeMode.LINES) WorldRenderQueue.boxFill(box, fillColor, throughWalls);
		if (mode != ShapeMode.FILL) WorldRenderQueue.boxLines(box, lineColor, throughWalls);
	}

	public static void line(Vec3d from, Vec3d to, int color, boolean throughWalls) {
		WorldRenderQueue.line(from, to, color, color, throughWalls);
	}

	public static void line(Vec3d from, Vec3d to, int fromColor, int toColor, boolean throughWalls) {
		WorldRenderQueue.line(from, to, fromColor, toColor, throughWalls);
	}

	/** A line from the centre of the screen to {@code to}. */
	public static void tracer(Vec3d to, int color) {
		WorldRenderQueue.tracer(to, color);
	}

	/** Line width in pixels for the lines queued after this call. Resets to the default (2) every frame. */
	public static void lineWidth(float width) {
		WorldRenderQueue.lineWidth(width);
	}
}
