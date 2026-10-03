package dev.myriad.api.service;

import net.minecraft.world.phys.Vec3;

/**
 * Server-side rotation arbitration. Modules {@link #request} a rotation every tick they need one; each tick the
 * highest priority request is sent in the movement packet and all requests are cleared. The camera is not moved.
 */
public interface Rotations {
	int PRIORITY_LOW = 0;
	int PRIORITY_NORMAL = 50;
	int PRIORITY_HIGH = 100;

	/** Requests a rotation for the next movement packet. Higher priority wins; ties go to the first request. */
	void request(Object owner, float yaw, float pitch, int priority);

	/**
	 * Like {@link #request(Object, float, float, int)}, then runs {@code afterSent} once the movement packet carrying
	 * this rotation has gone out, so the server already faces where you want when the action arrives (strict servers
	 * check the rotation of placements and hits). Doesn't run if a higher-priority request wins this tick.
	 */
	void request(Object owner, float yaw, float pitch, int priority, Runnable afterSent);

	default void lookAt(Object owner, Vec3 target, int priority) {
		float[] r = anglesTo(target);
		request(owner, r[0], r[1], priority);
	}

	/** Whether a rotation was sent last tick. */
	boolean isRotating();

	/** Yaw/pitch the server currently believes the player has. */
	float serverYaw();

	float serverPitch();

	/** Yaw and pitch from the player's eyes to {@code target}. */
	float[] anglesTo(Vec3 target);
}
