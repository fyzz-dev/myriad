package dev.myriad.api.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Block positions around a point: the loops every placing, breaking and scanning feature needs. Results are
 * immutable {@link BlockPos}es, nearest first unless noted, so "do the closest one" is just the first element.
 */
public final class Positions {
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private Positions() {
	}

	/** Every block whose centre is within {@code radius} of {@code center}, nearest first. */
	public static List<BlockPos> sphere(Vec3d center, double radius) {
		return sphere(center, radius, p -> true);
	}

	/** Like {@link #sphere(Vec3d, double)}, keeping only positions that pass {@code filter}. */
	public static List<BlockPos> sphere(Vec3d center, double radius, Predicate<BlockPos> filter) {
		List<BlockPos> out = new ArrayList<>();
		int r = (int) Math.ceil(radius);
		BlockPos origin = BlockPos.ofFloored(center);
		double max = radius * radius;
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					if (Vec3d.ofCenter(m).squaredDistanceTo(center) <= max && filter.test(m)) out.add(m.toImmutable());
				}
			}
		}
		out.sort(nearest(center));
		return out;
	}

	/** Every block in the cube {@code radius} blocks out from {@code center} in each direction, nearest first. */
	public static List<BlockPos> cube(BlockPos center, int radius) {
		return box(center.add(-radius, -radius, -radius), center.add(radius, radius, radius), Vec3d.ofCenter(center));
	}

	/** Every block between two corners (inclusive), nearest to {@code sortFrom} first (or in order if null). */
	public static List<BlockPos> box(BlockPos a, BlockPos b, Vec3d sortFrom) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.iterate(a, b)) out.add(p.toImmutable());
		if (sortFrom != null) out.sort(nearest(sortFrom));
		return out;
	}

	public static Comparator<BlockPos> nearest(Vec3d to) {
		return Comparator.comparingDouble(p -> Vec3d.ofCenter(p).squaredDistanceTo(to));
	}

	/** The four positions beside {@code pos} (north, east, south, west). */
	public static List<BlockPos> horizontalNeighbours(BlockPos pos) {
		List<BlockPos> out = new ArrayList<>(4);
		for (Direction d : HORIZONTAL) out.add(pos.offset(d));
		return out;
	}

	/** The six positions touching {@code pos}. */
	public static List<BlockPos> neighbours(BlockPos pos) {
		List<BlockPos> out = new ArrayList<>(6);
		for (Direction d : Direction.values()) out.add(pos.offset(d));
		return out;
	}

	/** The middle of one face of the block at {@code pos}. */
	public static Vec3d faceCenter(BlockPos pos, Direction face) {
		return Vec3d.ofCenter(pos).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
	}
}
