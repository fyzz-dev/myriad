package dev.myriad.impl.service;

import net.minecraft.world.entity.player.Input;

/**
 * What Grim (MultiActionsC) refuses an inventory click for, which depends on the version the server sees you as: it
 * judges sprinting for every version, the movement keys and jump of the last input packet from 1.21.2 (older clients
 * don't send them), and sneaking before 1.15 and from 1.21.9.
 */
public enum ClickRules {
	/** 1.15 to 1.21.1: only sprinting. */
	SPRINT(false, false),
	/** 1.21.2 to 1.21.8: sprinting, or moving or jumping in the last input. */
	INPUT(true, false),
	/** 1.21.9 on (and before 1.15), or a version that couldn't be told: all of it, sneaking too. */
	ALL(true, true);

	/** Protocol numbers of the versions the rules change at. */
	static final int V1_15 = 573, V1_21_2 = 768, V1_21_9 = 773;

	private final boolean input, sneak;

	ClickRules(boolean input, boolean sneak) {
		this.input = input;
		this.sneak = sneak;
	}

	/** The rules for a client the server sees as protocol {@code protocol}; {@link #ALL} when it's unknown (-1). */
	public static ClickRules forProtocol(int protocol) {
		if (protocol < V1_15 || protocol >= V1_21_9) return ALL;
		return protocol >= V1_21_2 ? INPUT : SPRINT;
	}

	/** Whether the movement keys and jump of the last input packet count. */
	public boolean input() {
		return input;
	}

	/** Whether sneaking counts. */
	public boolean sneak() {
		return sneak;
	}

	/** Whether a click now would be refused, given the last input the server got and its sprint state. */
	public boolean refuses(Input sent, boolean sprinting) {
		if (sprinting) return true;
		if (input && (sent.forward() || sent.backward() || sent.left() || sent.right() || sent.jump())) return true;
		return sneak && sent.shift();
	}
}
