package dev.myriad.api.service;

import net.minecraft.world.phys.Vec3;

/**
 * Server-side rotation arbitration. Modules {@link #request} a rotation every tick they need one; each tick the
 * winning request is sent in the movement packet and all requests are cleared. The camera is not moved.
 *
 * <p>Yaw and pitch are arbitrated separately: pass {@link Float#NaN} for an axis you don't care about and another
 * module may take it (Elytra Fly steering pitch while an aura aims yaw). For each axis the highest priority request
 * wins; ties go to the first request.
 *
 * <p>With {@link Options}, a request can turn at a limited speed instead of snapping, and can correct your movement
 * for the rotation (see {@link Options#moveFix}). When requests stop, a rotation that was turning eases back to where
 * you look at the same speed instead of snapping back.
 *
 * <pre>{@code
 * // Request in TickEvent.Pre so move fix applies to this tick's movement.
 * Myriad.rotations().lookAt(this, target.getEyePosition(), Rotations.PRIORITY_HIGH,
 *     new Rotations.Options(turnSpeed.get(), strict.get()));
 * }</pre>
 */
public interface Rotations {
	int PRIORITY_LOW = 0;
	int PRIORITY_NORMAL = 50;
	int PRIORITY_HIGH = 100;

	/**
	 * How a rotation is applied.
	 *
	 * @param turnSpeed the most each axis turns per tick, in degrees, from the rotation the server last saw towards
	 *                  the target; 0 snaps there at once. Strict anti-cheats flag big instant turns, so modules that
	 *                  aim at moving targets usually let the user pick a speed.
	 * @param moveFix   makes your movement match the sent yaw, as anti-cheats that simulate movement (Grim) expect:
	 *                  the movement keys are re-pressed relative to the sent yaw to go the way you're steering as
	 *                  closely as 8 directions allow, and walking, jumping and sprinting use the sent yaw. Needs the
	 *                  request made before the player moves, e.g. in {@code TickEvent.Pre}.
	 */
	record Options(float turnSpeed, boolean moveFix) {
		public static final Options INSTANT = new Options(0, false);

		public Options {
			turnSpeed = Math.max(0, turnSpeed);
		}

		public boolean isInstant() {
			return turnSpeed <= 0;
		}
	}

	/** Requests a rotation for the next movement packet, applied instantly. */
	default void request(Object owner, float yaw, float pitch, int priority) {
		request(owner, yaw, pitch, priority, Options.INSTANT, null);
	}

	/**
	 * Like {@link #request(Object, float, float, int)}, then runs {@code afterSent} once the movement packet carrying
	 * this rotation has gone out, so the server already faces where you want when the action arrives (strict servers
	 * check the rotation of placements and hits). Doesn't run if a higher-priority request wins an axis you asked for.
	 */
	default void request(Object owner, float yaw, float pitch, int priority, Runnable afterSent) {
		request(owner, yaw, pitch, priority, Options.INSTANT, afterSent);
	}

	/**
	 * The full form. {@code afterSent} (may be null) runs only once the sent rotation has reached the target, which
	 * with a turn speed can take a few ticks of requesting.
	 */
	void request(Object owner, float yaw, float pitch, int priority, Options options, Runnable afterSent);

	default void lookAt(Object owner, Vec3 target, int priority) {
		lookAt(owner, target, priority, Options.INSTANT);
	}

	default void lookAt(Object owner, Vec3 target, int priority, Options options) {
		float[] r = anglesTo(target);
		request(owner, r[0], r[1], priority, options, null);
	}

	/** Whether a rotation other than your real one was sent last tick (including while easing back). */
	boolean isRotating();

	/** Yaw/pitch the server currently believes the player has. */
	float serverYaw();

	float serverPitch();

	/** Whether the server already believes you face {@code yaw}/{@code pitch}, within {@code tolerance} degrees per axis. */
	default boolean isFacing(float yaw, float pitch, float tolerance) {
		float dy = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - serverYaw()));
		return dy <= tolerance && Math.abs(pitch - serverPitch()) <= tolerance;
	}

	/** Yaw and pitch from the player's eyes to {@code target}. */
	float[] anglesTo(Vec3 target);
}
