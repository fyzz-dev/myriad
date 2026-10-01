package dev.myriad.essentials.modules.player;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.essentials.mixin.FireworkRocketEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps a firework boost going after the server removes the rocket. When the server destroys the rocket you're
 * riding, the removal is ignored so the client keeps boosting, and pong replies are held back so the server's
 * view of you lags behind. Everything is released when you land, get set back, reach the time limit, or turn the
 * module off.
 */
public class FireworkExtender extends Module {
	private final DoubleSetting maxTime = sgGeneral.doubleSetting("Max Time").description("Stop extending after this many seconds.").defaultValue(44).range(1, 60).decimals(0).build();

	private final List<CommonPongC2SPacket> held = new CopyOnWriteArrayList<>();
	private volatile boolean extending;
	private volatile long extendStart;
	private volatile FireworkRocketEntity firework;

	public FireworkExtender() {
		super(Categories.PLAYER, "Firework Extender", "Keeps a firework boost going after the rocket expires.");
	}

	@Override
	public String hudInfo() {
		return String.format("%.1fs", extending ? (System.currentTimeMillis() - extendStart) / 1000f : 0f);
	}

	@Override
	protected void onDisable() {
		stop();
	}

	@Subscribe
	private void onJoin(WorldEvent.Join e) {
		held.clear();
		extending = false;
		firework = null;
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) return;
		if (!extending) {
			firework = null;
			for (Entity entity : mc.world.getEntities()) {
				if (entity instanceof FireworkRocketEntity rocket && ((FireworkRocketEntityAccessor) rocket).myriad$wasShotByEntity()
					&& ((FireworkRocketEntityAccessor) rocket).myriad$getShooter() == mc.player) {
					firework = rocket;
					break;
				}
			}
			return;
		}
		if (mc.player.isOnGround() || System.currentTimeMillis() - extendStart > maxTime.get() * 1000) stop();
	}

	@Subscribe
	private void onSend(PacketEvent.Send e) {
		if (extending && e.packet() instanceof CommonPongC2SPacket p) {
			e.cancel();
			held.add(p);
		}
	}

	@Subscribe
	private void onReceive(PacketEvent.Receive e) {
		FireworkRocketEntity rocket = firework;
		if (e.packet() instanceof EntitiesDestroyS2CPacket p && rocket != null && !extending) {
			for (int id : p.getEntityIds()) {
				if (id == rocket.getId()) {
					e.cancel();
					extendStart = System.currentTimeMillis();
					extending = true;
					return;
				}
			}
		}
		if (e.packet() instanceof PlayerPositionLookS2CPacket && extending) mc.execute(this::stop);
	}

	private void stop() {
		extending = false;
		FireworkRocketEntity rocket = firework;
		firework = null;
		if (rocket != null) rocket.discard();
		if (mc.getNetworkHandler() != null) for (CommonPongC2SPacket p : held) mc.getNetworkHandler().sendPacket(p);
		held.clear();
	}
}
