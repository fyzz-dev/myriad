package dev.myriad.impl.service;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.EntityEvent;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.service.ServerStats;
import dev.myriad.api.util.RateCounter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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
		if (e.packet() instanceof ClientboundPlayerPositionPacket) lastRubberband = System.currentTimeMillis();
		if (e.packet() instanceof ClientboundSetTimePacket) sample();
		else if (e.packet() instanceof ClientboundEntityEventPacket p && (p.getEventId() == 35 || p.getEventId() == 3)) {
			Minecraft mc = Minecraft.getInstance();
			byte status = p.getEventId();
			mc.execute(() -> {
				if (mc.level == null) return;
				Entity entity = p.getEntity(mc.level);
				if (!(entity instanceof LivingEntity living)) return;
				if (status == 35) {
					int n = pops.merge(living.getUUID(), 1, Integer::sum);
					Myriad.events().post(new EntityEvent.TotemPopped(living, n));
				} else {
					Integer n = pops.remove(living.getUUID());
					Myriad.events().post(new EntityEvent.Died(living, n == null ? 0 : n));
				}
			});
		}
	}

	@Subscribe
	private void onJoin(WorldEvent.Join e) {
		rememberServer();
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
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) return 0;
		var entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return entry == null ? 0 : entry.getLatency();
	}

	@Override
	public String address() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getCurrentServer() == null || mc.isLocalServer() ? null : mc.getCurrentServer().ip;
	}

	/** The server you're on, remembered when you leave it (the client forgets it once disconnected). */
	private volatile ServerData lastServer;

	public void rememberServer() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getCurrentServer() != null && !mc.isLocalServer()) lastServer = mc.getCurrentServer();
	}

	@Override
	public @Nullable String lastAddress() {
		ServerData s = lastServer;
		return s == null ? address() : s.ip;
	}

	@Override
	public boolean reconnect() {
		Minecraft mc = Minecraft.getInstance();
		rememberServer();
		ServerData server = lastServer;
		if (server == null) return false;
		mc.execute(() -> {
			if (mc.level != null) mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
			ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(server.ip), server, false, null);
		});
		return true;
	}

	@Override
	public boolean isSingleplayer() {
		return Minecraft.getInstance().isLocalServer();
	}

	@Override
	public long msSinceLastPacket() {
		return Minecraft.getInstance().getConnection() == null ? 0 : System.currentTimeMillis() - lastPacket;
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
