package dev.myriad.api.render;

import dev.myriad.impl.render.WorldRenderQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

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

	/**
	 * The block at {@code pos} as its real shape (slabs, stairs, chests, fences), not a full cube: filled with its
	 * boxes and outlined along its edges. Draws a full cube for air, so a block that's about to be placed shows too.
	 */
	public static void blockShape(BlockPos pos, int fillColor, int lineColor, ShapeMode mode, boolean throughWalls) {
		MinecraftClient mc = MinecraftClient.getInstance();
		VoxelShape shape = mc.world == null ? VoxelShapes.fullCube() : mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
		if (shape.isEmpty()) shape = VoxelShapes.fullCube();
		double x = pos.getX(), y = pos.getY(), z = pos.getZ();
		if (mode != ShapeMode.LINES) {
			for (Box b : shape.getBoundingBoxes()) WorldRenderQueue.boxFill(b.offset(x, y, z), fillColor, throughWalls);
		}
		if (mode != ShapeMode.FILL) {
			shape.forEachEdge((x1, y1, z1, x2, y2, z2) ->
				WorldRenderQueue.line(new Vec3d(x + x1, y + y1, z + z1), new Vec3d(x + x2, y + y2, z + z2), lineColor, lineColor, throughWalls));
		}
	}

	/** One face of the block at {@code pos}, filled (e.g. the side a placement will click). */
	public static void side(BlockPos pos, Direction face, int color, boolean throughWalls) {
		double t = 0.002;
		Box full = new Box(pos);
		Box b = switch (face) {
			case DOWN -> new Box(full.minX, full.minY - t, full.minZ, full.maxX, full.minY + t, full.maxZ);
			case UP -> new Box(full.minX, full.maxY - t, full.minZ, full.maxX, full.maxY + t, full.maxZ);
			case NORTH -> new Box(full.minX, full.minY, full.minZ - t, full.maxX, full.maxY, full.minZ + t);
			case SOUTH -> new Box(full.minX, full.minY, full.maxZ - t, full.maxX, full.maxY, full.maxZ + t);
			case WEST -> new Box(full.minX - t, full.minY, full.minZ, full.minX + t, full.maxY, full.maxZ);
			case EAST -> new Box(full.maxX - t, full.minY, full.minZ, full.maxX + t, full.maxY, full.maxZ);
		};
		WorldRenderQueue.boxFill(b, color, throughWalls);
	}

	/** A flat ring around {@code center} (e.g. a range indicator at the player's feet). */
	public static void circle(Vec3d center, double radius, int color, boolean throughWalls) {
		int segments = Math.clamp((int) (radius * 12), 24, 128);
		Vec3d prev = null;
		for (int i = 0; i <= segments; i++) {
			double a = i * Math.PI * 2 / segments;
			Vec3d p = center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
			if (prev != null) WorldRenderQueue.line(prev, p, color, color, throughWalls);
			prev = p;
		}
	}

	/** Line width in pixels for the lines queued after this call. Resets to the default (2) every frame. */
	public static void lineWidth(float width) {
		WorldRenderQueue.lineWidth(width);
	}
}
