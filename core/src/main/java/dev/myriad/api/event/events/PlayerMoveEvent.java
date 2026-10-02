package dev.myriad.api.event.events;

import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;

/**
 * The local player is about to move by {@link #movement} this tick (after input, gravity and friction were applied,
 * before collisions). Change it to speed up, slow down or stop: speed, flight, step and no-slow features hook here.
 */
public final class PlayerMoveEvent {
	private final MovementType type;
	private Vec3d movement;

	public PlayerMoveEvent(MovementType type, Vec3d movement) {
		this.type = type;
		this.movement = movement;
	}

	/** {@code SELF} for normal movement; pistons, shulkers and the like push with other types. */
	public MovementType type() {
		return type;
	}

	public Vec3d movement() {
		return movement;
	}

	public void setMovement(Vec3d movement) {
		this.movement = movement;
	}

	/** Keeps the vertical movement and replaces the horizontal part. */
	public void setHorizontal(double x, double z) {
		this.movement = new Vec3d(x, movement.y, z);
	}
}
