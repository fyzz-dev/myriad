package dev.myriad.api.service;

import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

/**
 * Facts about the current server that several features want, tracked once by Myriad instead of by each addon.
 *
 * <pre>{@code
 * float tps = Myriad.server().tps();
 * int pops = Myriad.server().totemPops(player);
 * }</pre>
 */
public interface ServerStats {
	/** Average ticks per second over the last ~20 seconds, from the spacing of time updates; 20 until known. */
	float tps();

	/** Your latency to the server in milliseconds, as the tab list shows it; 0 in singleplayer or before it's known. */
	int ping();

	/** Totems {@code player} has used since they last died (reset when you join a world). */
	int totemPops(UUID player);

	default int totemPops(PlayerEntity player) {
		return totemPops(player.getUuid());
	}

	/** Green for few pops, through yellow, to red for many: a shared scale so every addon shows pops alike. */
	static int popColor(int pops) {
		return switch (Math.min(pops, 6)) {
			case 0, 1 -> 0xFF55FF55;
			case 2 -> 0xFFAAFF55;
			case 3 -> 0xFFFFFF55;
			case 4 -> 0xFFFFAA55;
			case 5 -> 0xFFFF7755;
			default -> 0xFFFF5555;
		};
	}
}
