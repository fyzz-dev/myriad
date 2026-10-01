package dev.myriad.api.event.events;

import dev.myriad.api.event.Cancellable;

/** A character was typed. Cancel to hide it from Minecraft. */
public final class CharEvent extends Cancellable {
	private final int codePoint, modifiers;

	public CharEvent(int codePoint, int modifiers) {
		this.codePoint = codePoint;
		this.modifiers = modifiers;
	}

	public int codePoint() {
		return codePoint;
	}

	public int modifiers() {
		return modifiers;
	}
}
