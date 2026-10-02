package dev.myriad.api.util;

import dev.myriad.impl.network.PacketGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.network.packet.Packet;

/** Sending packets to the server. All of these do nothing outside a world. */
public final class Packets {
	private Packets() {
	}

	/** Sends a packet like vanilla would: other features see it in {@code PacketEvent.Send} and may change or cancel it. */
	public static void send(Packet<?> packet) {
		var handler = MinecraftClient.getInstance().getNetworkHandler();
		if (handler != null) handler.sendPacket(packet);
	}

	/**
	 * Sends a packet without posting {@code PacketEvent.Send} or {@code AttackEvent}. Use it for packets that are
	 * part of your own trick (a crit hop, a desync fix) and that other features shouldn't react to or rewrite.
	 */
	public static void sendSilently(Packet<?> packet) {
		PacketGate.runSilently(() -> send(packet));
	}

	/**
	 * Sends a block or item action that the server acknowledges by sequence number (block breaking actions, using an
	 * item, interacting with a block), keeping the client's prediction in step. Without the sequence the server's
	 * reply can undo the client's view of the change.
	 *
	 * <pre>{@code
	 * Packets.sendSequenced(seq -> new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, side, seq));
	 * }</pre>
	 */
	public static void sendSequenced(SequencedPacketCreator creator) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.interactionManager != null && mc.world != null) mc.interactionManager.sendSequencedPacket(mc.world, creator);
	}
}
