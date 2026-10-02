package dev.myriad.api.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** The local player's movement: which way the keys point, and setting speed along it. Speed/flight features use these. */
public final class Movement {
	private Movement() {
	}

	private static ClientPlayerEntity player() {
		return MinecraftClient.getInstance().player;
	}

	/** True while any movement key is held. */
	public static boolean hasInput() {
		var p = player();
		if (p == null) return false;
		var in = p.input.playerInput;
		return in.forward() != in.backward() || in.left() != in.right();
	}

	/**
	 * The yaw the movement keys point in (the camera's yaw + 90 while holding only D, for example), or {@code NaN}
	 * with no input. Combine with {@link MathUtil#horizontalDirection(float)} for a direction vector.
	 */
	public static float inputYaw() {
		return inputYaw(player() == null ? 0 : player().getYaw());
	}

	/** Like {@link #inputYaw()} relative to {@code yaw} instead of where the camera faces (e.g. a locked flight yaw). */
	public static float inputYaw(float yaw) {
		var p = player();
		if (p == null) return Float.NaN;
		var in = p.input.playerInput;
		int forward = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
		int strafe = (in.left() ? 1 : 0) - (in.right() ? 1 : 0);
		if (forward == 0 && strafe == 0) return Float.NaN;
		// atan2 of the input in the player's frame; strafing left turns the heading anticlockwise (negative yaw).
		float offset = (float) Math.toDegrees(Math.atan2(-strafe, forward));
		return yaw + offset;
	}

	/** Current speed along the ground, in blocks per tick. */
	public static double horizontalSpeed() {
		var p = player();
		if (p == null) return 0;
		Vec3d v = p.getVelocity();
		return Math.sqrt(v.x * v.x + v.z * v.z);
	}

	/** Sets horizontal velocity to {@code speed} blocks per tick in the input direction (or stops with no input). */
	public static void setHorizontalSpeed(double speed) {
		var p = player();
		if (p == null) return;
		float yaw = inputYaw();
		Vec3d v = p.getVelocity();
		if (Float.isNaN(yaw)) {
			p.setVelocity(0, v.y, 0);
			return;
		}
		Vec3d dir = MathUtil.horizontalDirection(yaw).multiply(speed);
		p.setVelocity(dir.x, v.y, dir.z);
	}
}
