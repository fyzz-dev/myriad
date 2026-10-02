package dev.myriad.impl.service;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.service.ServerStats;
import dev.myriad.api.util.RateCounter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server TPS from the once-a-second time updates, and totem pops from entity status 35 (pop) and 3 (death). */
public final class ServerStatsTracker implements ServerStats {
	private final float[] samples = new float[20];
	private int count, next;
	private long lastUpdate;
	private final Map<UUID, Integer> pops = new ConcurrentHashMap<>();
	private final RateCounter sent = new RateCounter(), received = new RateCounter();
	private volatile long lastPacket = System.currentTimeMillis(), lastRubberband;

	@Subscribe
	private void onSend(PacketEvent.Send e) {
		sent.record();
	}

	@Subscribe
	private void onPacket(PacketEvent.Receive e) {
		lastPacket = System.currentTimeMillis();
		received.record();
		if (e.packet() instanceof PlayerPositionLookS2CPacket) lastRubberband = System.currentTimeMillis();
		if (e.packet() instanceof WorldTimeUpdateS2CPacket) sample();
		else if (e.packet() instanceof EntityStatusS2CPacket p && (p.getStatus() == 35 || p.getStatus() == 3)) {
			MinecraftClient mc = MinecraftClient.getInstance();
			byte status = p.getStatus();
			mc.execute(() -> {
				if (mc.world == null) return;
				Entity entity = p.getEntity(mc.world);
				if (!(entity instanceof PlayerEntity player)) return;
				if (status == 35) pops.merge(player.getUuid(), 1, Integer::sum);
				else pops.remove(player.getUuid());
			});
		}
	}

	@Subscribe
	private void onJoin(WorldEvent.Join e) {
		synchronized (this) {
			count = next = 0;
			lastUpdate = 0;
		}
		pops.clear();
		lastPacket = System.currentTimeMillis();
		lastRubberband = 0;
	}

	private synchronized void sample() {
		long now = System.currentTimeMillis();
		if (lastUpdate != 0) {
			float seconds = (now - lastUpdate) / 1000f;
			samples[next] = Math.clamp(20f / Math.max(seconds, 0.05f), 0f, 20f);
			next = (next + 1) % samples.length;
			count = Math.min(count + 1, samples.length);
		}
		lastUpdate = now;
	}

	@Override
	public synchronized float tps() {
		if (count == 0) return 20f;
		float sum = 0;
		for (int i = 0; i < count; i++) sum += samples[i];
		return sum / count;
	}

	@Override
	public int ping() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.getNetworkHandler() == null) return 0;
		var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
		return entry == null ? 0 : entry.getLatency();
	}

	@Override
	public String address() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.getCurrentServerEntry() == null || mc.isInSingleplayer() ? null : mc.getCurrentServerEntry().address;
	}

	@Override
	public boolean isSingleplayer() {
		return MinecraftClient.getInstance().isInSingleplayer();
	}

	@Override
	public long msSinceLastPacket() {
		return MinecraftClient.getInstance().getNetworkHandler() == null ? 0 : System.currentTimeMillis() - lastPacket;
	}

	@Override
	public long msSinceRubberband() {
		return lastRubberband == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - lastRubberband;
	}

	@Override
	public int packetsSentPerSecond() {
		return sent.count();
	}

	@Override
	public int packetsReceivedPerSecond() {
		return received.count();
	}

	@Override
	public int totemPops(UUID player) {
		return pops.getOrDefault(player, 0);
	}
}
