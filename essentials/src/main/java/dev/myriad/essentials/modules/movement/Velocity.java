package dev.myriad.essentials.modules.movement;

import dev.myriad.api.event.Priority;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.essentials.modules.combat.Criticals;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Reduces or cancels knockback from hits and explosions, and can stop blocks, liquids and other entities from
 * pushing you. Walls Only cancels knockback only while you're phased into a block (and, with Require Blocked, only
 * knockback that would push you further into it), then gives the cancelled push back once you can move, so the
 * server and client agree. Static stops you dead after a hit when you aren't pressing movement keys.
 */
public class Velocity extends Module {

	private static final int MAX_RECONCILE_TICKS = 20;
	private static final double MAX_RECONCILE_SPEED = 0.5;

	private final DoubleSetting horizontal = sgGeneral.doubleSetting("Horizontal").description("Percent of horizontal knockback to keep.").defaultValue(0).range(0, 100).decimals(0).build();
	private final DoubleSetting vertical = sgGeneral.doubleSetting("Vertical").description("Percent of vertical knockback to keep.").defaultValue(0).range(0, 100).decimals(0).build();
	private final BoolSetting explosions = sgGeneral.bool("Explosions").defaultValue(true).build();
	private final BoolSetting fishingRods = sgGeneral.bool("Fishing Rods").description("Don't get pulled by fishing rods.").defaultValue(true).build();
	private final BoolSetting pauseLag = sgGeneral.bool("Pause Lag").description("Ignore the zero-velocity packet some servers send right after a setback.").build();
	private final BoolSetting wallsOnly = sgGeneral.bool("Walls Only").description("Only cancel knockback while phased into a block.").build();
	private final BoolSetting trapped = sgGeneral.bool("Trapped").description("Also count a block over your head as phased.").visible(wallsOnly::get).build();
	private final BoolSetting groundOnly = sgGeneral.bool("Ground Only").defaultValue(true).visible(wallsOnly::get).build();
	private final BoolSetting requireBlocked = sgGeneral.bool("Require Blocked").description("Only cancel knockback that would push you further into a block.")
		.defaultValue(true).visible(wallsOnly::get).build();
	private final BoolSetting staticVelocity = sgGeneral.bool("Static").description("Stop dead after knockback when you aren't pressing movement keys.").build();
	private final BoolSetting staticPhasedOnly = sgGeneral.bool("Static Phased Only").description("Only stop dead while phased.").visible(staticVelocity::get).build();

	private final SettingGroup sgPush = settings.group("No Push");
	private final BoolSetting pushBlocks = sgPush.bool("Blocks").description("Don't get pushed out of blocks.").build();
	private final BoolSetting pushLiquids = sgPush.bool("Liquids").description("Don't get pushed by flowing water and lava.").build();
	private final BoolSetting pushEntities = sgPush.bool("Entities").description("Don't get pushed by other entities.").build();

	private volatile boolean concealVelocity, pendingStatic;
	private Vec3d pendingReconcile = Vec3d.ZERO;
	private int reconcileTicks;

	public Velocity() {
		super(Categories.MOVEMENT, "Velocity", "Reduces knockback and pushing.");
	}

	@Override
	public String hudInfo() {
		if (wallsOnly.get()) return inGame() && phased() ? "Phase" : "Wait";
		return String.format("H%.0f%% V%.0f%%", horizontal.get(), vertical.get());
	}

	@Override
	protected void onDisable() {
		concealVelocity = pendingStatic = false;
		clearReconcile();
	}

	public static boolean cancelsBlockPush() {
		Velocity m = Modules.active(Velocity.class);
		return m != null && m.pushBlocks.get() && (!m.wallsOnly.get() || m.phased());
	}

	public static boolean cancelsLiquidPush() {
		Velocity m = Modules.active(Velocity.class);
		return m != null && m.pushLiquids.get();
	}

	public static boolean cancelsEntityPush() {
		Velocity m = Modules.active(Velocity.class);
		return m != null && m.pushEntities.get();
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) {
			pendingStatic = false;
			clearReconcile();
			return;
		}
		applyStatic();
		applyReconcile();
	}

	@Subscribe(priority = Priority.LOWEST)
	private void onReceive(PacketEvent.Receive e) {
		if (!inGame()) return;
		if (e.packet() instanceof EntityVelocityUpdateS2CPacket p && p.getEntityId() == mc.player.getId()) {
			if (concealVelocity && p.getVelocityX() == 0 && p.getVelocityY() == 0 && p.getVelocityZ() == 0) {
				concealVelocity = false;
				return;
			}
			Vec3d incoming = new Vec3d(p.getVelocityX(), p.getVelocityY(), p.getVelocityZ());
			Vec3d delta = incoming.subtract(mc.player.getVelocity());
			if (shouldCancel(delta)) {
				queueReconcile(delta);
				e.cancel();
			} else if (!wallsOnly.get()) {
				e.setPacket(new EntityVelocityUpdateS2CPacket(p.getEntityId(), scale(incoming)));
			}
			queueStatic();
		} else if (explosions.get() && e.packet() instanceof ExplosionS2CPacket p && p.playerKnockback().isPresent()) {
			Vec3d kb = p.playerKnockback().get();
			Optional<Vec3d> kept;
			if (shouldCancel(kb)) {
				queueReconcile(kb);
				kept = Optional.empty();
			} else {
				kept = wallsOnly.get() ? Optional.of(kb) : Optional.of(scale(kb));
			}
			// Keep the packet (particles and sound), just change the push.
			e.setPacket(new ExplosionS2CPacket(p.center(), kept, p.explosionParticle(), p.explosionSound()));
		} else if (e.packet() instanceof PlayerPositionLookS2CPacket && pauseLag.get()) {
			concealVelocity = true;
		} else if (fishingRods.get() && e.packet() instanceof EntityStatusS2CPacket p && p.getStatus() == EntityStatuses.PULL_HOOKED_ENTITY) {
			Entity entity = p.getEntity(mc.world);
			if (entity instanceof FishingBobberEntity hook && hook.getHookedEntity() == mc.player) e.cancel();
		}
	}

	private Vec3d scale(Vec3d v) {
		return new Vec3d(v.x * horizontal.get() / 100, v.y * vertical.get() / 100, v.z * horizontal.get() / 100);
	}

	private boolean shouldCancel(Vec3d delta) {
		if (!isKnockback(delta)) return false;
		if (wallsOnly.get()) {
			if (!phaseActive()) return false;
			return !requireBlocked.get() || blocked(delta);
		}
		return horizontal.get() == 0 && vertical.get() == 0;
	}

	private boolean isKnockback(Vec3d delta) {
		if (!wallsOnly.get()) return true;
		return Math.sqrt(delta.x * delta.x + delta.z * delta.z) >= 0.1 || delta.y >= 0.1;
	}

	private boolean phaseActive() {
		return (phased() || (trapped.get() && trappedHead())) && (!groundOnly.get() || mc.player.isOnGround());
	}

	private boolean phased() {
		Box box = mc.player.getBoundingBox().expand(-1.0E-7);
		return mc.world.getBlockCollisions(mc.player, box).iterator().hasNext();
	}

	private boolean trappedHead() {
		BlockPos head = mc.player.getBlockPos().up(mc.player.isCrawling() ? 1 : 2);
		return !mc.world.getBlockState(head).isReplaceable();
	}

	private boolean blocked(Vec3d delta) {
		double len = delta.length();
		if (len < 1.0E-4) return false;
		Vec3d step = delta.multiply(Math.min(len, 2.0) / len);
		return mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(step).expand(-1.0E-7)).iterator().hasNext();
	}

	private void queueStatic() {
		if (staticVelocity.get()) pendingStatic = true;
	}

	private void applyStatic() {
		if (!pendingStatic) return;
		pendingStatic = false;
		if (!staticVelocity.get() || (staticPhasedOnly.get() && !phased())) return;
		var in = mc.player.input.playerInput;
		if (in.forward() || in.backward() || in.left() || in.right() || in.jump() || critActive()) return;
		mc.player.setVelocity(Vec3d.ZERO);
	}

	private static boolean critActive() {
		return System.currentTimeMillis() - Criticals.lastCrit < 100;
	}

	private synchronized void queueReconcile(Vec3d delta) {
		if (!wallsOnly.get() || !isKnockback(delta)) return;
		pendingReconcile = pendingReconcile.add(delta);
		if (pendingReconcile.length() > MAX_RECONCILE_SPEED) pendingReconcile = pendingReconcile.normalize().multiply(MAX_RECONCILE_SPEED);
		reconcileTicks = MAX_RECONCILE_TICKS;
	}

	/** Gives back cancelled knockback once it no longer pushes into a block. */
	private synchronized void applyReconcile() {
		if (reconcileTicks <= 0 || pendingReconcile.lengthSquared() < 9.0E-4) {
			clearReconcile();
			return;
		}
		if (critActive()) return;
		reconcileTicks--;
		if (blocked(pendingReconcile)) {
			pendingReconcile = new Vec3d(pendingReconcile.x * 0.91, pendingReconcile.y * 0.98, pendingReconcile.z * 0.91);
			return;
		}
		mc.player.setVelocity(mc.player.getVelocity().add(pendingReconcile));
		clearReconcile();
	}

	private synchronized void clearReconcile() {
		pendingReconcile = Vec3d.ZERO;
		reconcileTicks = 0;
	}
}
