package dev.myriad.api.service;

import net.minecraft.util.math.Vec3d;

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

	default void lookAt(Object owner, Vec3d target, int priority) {
		float[] r = anglesTo(target);
		request(owner, r[0], r[1], priority);
	}

	/** Whether a rotation was sent last tick. */
	boolean isRotating();

	/** Yaw/pitch the server currently believes the player has. */
	float serverYaw();

	float serverPitch();

	/** Yaw and pitch from the player's eyes to {@code target}. */
	float[] anglesTo(Vec3d target);
}
