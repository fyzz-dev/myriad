package dev.myriad.api.event.events;

import net.minecraft.util.math.Vec3d;

/**
 * Where the local player's view ray starts and points, as used for the crosshair target, block reach and placement.
 * Overwrite {@link #value} to aim from somewhere else (e.g. a freecam).
 */
public abstract class PlayerViewEvent {
	public Vec3d value;
	private final float tickDelta;

	protected PlayerViewEvent(Vec3d value, float tickDelta) {
		this.value = value;
		this.tickDelta = tickDelta;
	}

	public float tickDelta() {
		return tickDelta;
	}

	/** The eye position the view ray starts from. */
	public static final class Eyes extends PlayerViewEvent {
		public Eyes(Vec3d value, float tickDelta) {
			super(value, tickDelta);
		}
	}

	/** The unit look direction. */
	public static final class Look extends PlayerViewEvent {
		public Look(Vec3d value, float tickDelta) {
			super(value, tickDelta);
		}
	}
}
