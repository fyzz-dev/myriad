package dev.myriad.api.event.events;

import dev.myriad.api.event.Cancellable;
import org.jetbrains.annotations.ApiStatus;

public final class MouseScrollEvent extends Cancellable {
	private final double horizontal, vertical;

	@ApiStatus.Internal
	public MouseScrollEvent(double horizontal, double vertical) {
		this.horizontal = horizontal;
		this.vertical = vertical;
	}

	public double horizontal() {
		return horizontal;
	}

	public double vertical() {
		return vertical;
	}
}
