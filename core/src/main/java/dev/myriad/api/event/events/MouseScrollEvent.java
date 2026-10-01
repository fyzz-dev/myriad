package dev.myriad.api.event.events;

import dev.myriad.api.event.Cancellable;

public final class MouseScrollEvent extends Cancellable {
	private final double horizontal, vertical;

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
