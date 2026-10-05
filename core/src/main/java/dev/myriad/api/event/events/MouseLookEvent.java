package dev.myriad.api.event.events;

import org.jetbrains.annotations.ApiStatus;
import dev.myriad.api.event.Cancellable;

/**
 * Mouse movement is about to turn the player. Cancel to keep the player still (and use the deltas yourself), or
 * change the deltas with {@link #set} to scale or redirect the turn (Zoom slows it down).
 */
public final class MouseLookEvent extends Cancellable {
	private double deltaX, deltaY;

	@ApiStatus.Internal
	public MouseLookEvent(double deltaX, double deltaY) {
		this.deltaX = deltaX;
		this.deltaY = deltaY;
	}

	/** Horizontal look delta in vanilla units (multiply by 0.15 for degrees). */
	public double deltaX() {
		return deltaX;
	}

	public double deltaY() {
		return deltaY;
	}

	public void set(double deltaX, double deltaY) {
		this.deltaX = deltaX;
		this.deltaY = deltaY;
	}
}
