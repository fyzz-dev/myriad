package dev.myriad.impl.service;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.service.ServerStats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
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

	@Subscribe
	private void onPacket(PacketEvent.Receive e) {
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
	public int totemPops(UUID player) {
		return pops.getOrDefault(player, 0);
	}
}
