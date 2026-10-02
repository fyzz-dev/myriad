package dev.myriad.impl.network;

import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/** Shared state between {@code Packets} and the connection mixin. */
public final class PacketGate {
	private static final ThreadLocal<int[]> SILENT = ThreadLocal.withInitial(() -> new int[1]);

	private PacketGate() {
	}

	/** True while a packet is being sent with {@code Packets.sendSilently}: events are skipped for it. */
	public static boolean isSilent() {
		return SILENT.get()[0] > 0;
	}

	public static void runSilently(Runnable send) {
		int[] depth = SILENT.get();
		depth[0]++;
		try {
			send.run();
		} finally {
			depth[0]--;
		}
	}

	public static boolean isAttack(PlayerInteractEntityC2SPacket packet) {
		boolean[] attack = {false};
		packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
			@Override
			public void interact(Hand hand) {
			}

			@Override
			public void interactAt(Hand hand, Vec3d pos) {
			}

			@Override
			public void attack() {
				attack[0] = true;
			}
		});
		return attack[0];
	}
}
