package dev.myriad.api.event.events;

import dev.myriad.api.event.Cancellable;
import net.minecraft.network.protocol.Packet;

/** Packet traffic. Bundles are split, so handlers see each inner packet. Receive is fired on the network thread. */
public abstract class PacketEvent extends Cancellable {
	private Packet<?> packet;

	protected PacketEvent(Packet<?> packet) {
		this.packet = packet;
	}

	public Packet<?> packet() {
		return packet;
	}

	public void setPacket(Packet<?> packet) {
		this.packet = packet;
	}

	public static final class Send extends PacketEvent {
		public Send(Packet<?> packet) {
			super(packet);
		}
	}

	public static final class Receive extends PacketEvent {
		public Receive(Packet<?> packet) {
			super(packet);
		}
	}
}
