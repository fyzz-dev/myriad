package dev.myriad.impl.service;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.service.AntiCheat;
import net.minecraft.network.protocol.common.ClientboundPingPacket;

import java.util.Locale;
import java.util.Set;

/**
 * The anti-cheat profile: the player's choice, or (Auto) a guess from the server. Anti-cheats that simulate the player
 * send a ping ("transaction") with nearly everything that changes the player, many a second; the vanilla server never
 * sends one in game. A few of them in a row mean such an anti-cheat.
 */
public final class AntiCheatTracker implements AntiCheat {
	/** Pings seen since joining before the server counts as running one. */
	private static final int PINGS = 5;
	/** Servers known to run Grim, by address (and their subdomains). */
	private static final Set<String> KNOWN_GRIM = Set.of("2b2t.org");

	private volatile Mode mode = Mode.AUTO;
	private volatile int pings;
	private volatile boolean known;

	@Override
	public Profile profile() {
		return switch (mode) {
			case AUTO -> detected();
			case VANILLA -> Profile.VANILLA;
			case GRIM -> Profile.GRIM;
		};
	}

	@Override
	public Profile detected() {
		return known || pings >= PINGS ? Profile.GRIM : Profile.VANILLA;
	}

	@Override
	public Mode mode() {
		return mode;
	}

	@Override
	public void setMode(Mode mode) {
		if (mode == null || mode == this.mode) return;
		this.mode = mode;
		if (Myriad.isReady()) Myriad.config().markDirty();
	}

	@Subscribe
	private void onReceive(PacketEvent.Receive e) {
		if (e.packet() instanceof ClientboundPingPacket && pings < PINGS) pings++;
	}

	@Subscribe
	private void onJoin(WorldEvent.Join e) {
		pings = 0;
		String address = Myriad.server().address();
		known = address != null && isKnown(address.toLowerCase(Locale.ROOT));
	}

	@Subscribe
	private void onLeave(WorldEvent.Leave e) {
		pings = 0;
		known = false;
	}

	private static boolean isKnown(String address) {
		int colon = address.lastIndexOf(':');
		String host = colon > 0 ? address.substring(0, colon) : address;
		for (String k : KNOWN_GRIM) if (host.equals(k) || host.endsWith("." + k)) return true;
		return false;
	}
}
