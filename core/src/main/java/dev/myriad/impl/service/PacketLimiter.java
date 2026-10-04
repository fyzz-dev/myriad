package dev.myriad.impl.service;

import dev.myriad.api.service.PacketLimits;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

/**
 * Sliding-window packet budget. The limits sit under what strict servers (2b2t, Grim setups) tolerate in bursts while
 * leaving normal play and fast modules untouched: about 160 block actions, 30 interactions and 16 inventory clicks a
 * second, counted over half a second so a burst can't spend a whole second's worth at once.
 */
public final class PacketLimiter implements PacketLimits {
	private static final int WINDOW_MS = 500;
	private static final Map<Kind, Integer> LIMITS = Map.of(Kind.BLOCK_ACTION, 80, Kind.INTERACT, 15, Kind.INVENTORY, 8);

	private final Map<Kind, ArrayDeque<Long>> sent = new EnumMap<>(Kind.class);
	private int urgentDepth;

	public PacketLimiter() {
		for (Kind k : Kind.values()) sent.put(k, new ArrayDeque<>());
	}

	/** Counts a packet the client is sending; called for every outgoing packet, including silent ones. */
	public void record(Packet<?> packet) {
		Kind kind = kindOf(packet);
		if (kind != null) record(kind);
	}

	synchronized void record(Kind kind) {
		sent.get(kind).addLast(now());
	}

	static Kind kindOf(Packet<?> packet) {
		return switch (packet) {
			case ServerboundPlayerActionPacket p -> Kind.BLOCK_ACTION;
			case ServerboundUseItemOnPacket p -> Kind.INTERACT;
			case ServerboundUseItemPacket p -> Kind.INTERACT;
			case ServerboundInteractPacket p -> Kind.INTERACT;
			case ServerboundAttackPacket p -> Kind.INTERACT;
			case ServerboundContainerClickPacket p -> Kind.INVENTORY;
			default -> null;
		};
	}

	@Override
	public synchronized boolean canSend(Kind kind, int packets) {
		return urgentDepth > 0 || used(kind) + packets <= limit(kind);
	}

	@Override
	public synchronized int used(Kind kind) {
		ArrayDeque<Long> times = sent.get(kind);
		long cutoff = now() - WINDOW_MS;
		while (!times.isEmpty() && times.peekFirst() <= cutoff) times.pollFirst();
		return times.size();
	}

	@Override
	public int limit(Kind kind) {
		return LIMITS.get(kind);
	}

	@Override
	public int windowMs() {
		return WINDOW_MS;
	}

	@Override
	public void urgent(Runnable action) {
		synchronized (this) {
			urgentDepth++;
		}
		try {
			action.run();
		} finally {
			synchronized (this) {
				urgentDepth--;
			}
		}
	}

	private static long now() {
		return System.nanoTime() / 1_000_000L;
	}
}
