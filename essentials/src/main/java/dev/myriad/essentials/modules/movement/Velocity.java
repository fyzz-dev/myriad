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
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
	private Vec3 pendingReconcile = Vec3.ZERO;
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

	@Subscribe(priority = Priority.LOWEST, inGame = true, packets = {ClientboundSetEntityMotionPacket.class, ClientboundPlayerPositionPacket.class, ClientboundEntityEventPacket.class})
	private void onReceive(PacketEvent.Receive e) {
		if (e.packet() instanceof ClientboundSetEntityMotionPacket p && p.id() == mc.player.getId()) {
			if (concealVelocity && p.movement().lengthSqr() == 0) {
				concealVelocity = false;
				return;
			}
			Vec3 incoming = p.movement();
			Vec3 delta = incoming.subtract(mc.player.getDeltaMovement());
			if (shouldCancel(delta)) {
				queueReconcile(delta);
				e.cancel();
			} else if (!wallsOnly.get()) {
				e.setPacket(new ClientboundSetEntityMotionPacket(p.id(), scale(incoming)));
			}
			queueStatic();
		} else if (explosions.get() && e.packet() instanceof ClientboundExplodePacket p && p.playerKnockback().isPresent()) {
			Vec3 kb = p.playerKnockback().get();
			Optional<Vec3> kept;
			if (shouldCancel(kb)) {
				queueReconcile(kb);
				kept = Optional.empty();
			} else {
				kept = wallsOnly.get() ? Optional.of(kb) : Optional.of(scale(kb));
			}
			// Keep the packet (particles and sound), just change the push.
			e.setPacket(new ClientboundExplodePacket(p.center(), p.radius(), p.blockCount(), kept, p.explosionParticle(), p.explosionSound(), p.blockParticles()));
		} else if (e.packet() instanceof ClientboundPlayerPositionPacket && pauseLag.get()) {
			concealVelocity = true;
		} else if (fishingRods.get() && e.packet() instanceof ClientboundEntityEventPacket p && p.getEventId() == EntityEvent.FISHING_ROD_REEL_IN) {
			Entity entity = p.getEntity(mc.level);
			if (entity instanceof FishingHook hook && hook.getHookedIn() == mc.player) e.cancel();
		}
	}

	private Vec3 scale(Vec3 v) {
		return new Vec3(v.x * horizontal.get() / 100, v.y * vertical.get() / 100, v.z * horizontal.get() / 100);
	}

	private boolean shouldCancel(Vec3 delta) {
		if (!isKnockback(delta)) return false;
		if (wallsOnly.get()) {
			if (!phaseActive()) return false;
			return !requireBlocked.get() || blocked(delta);
		}
		return horizontal.get() == 0 && vertical.get() == 0;
	}

	private boolean isKnockback(Vec3 delta) {
		if (!wallsOnly.get()) return true;
		return Math.sqrt(delta.x * delta.x + delta.z * delta.z) >= 0.1 || delta.y >= 0.1;
	}

	private boolean phaseActive() {
		return (phased() || (trapped.get() && trappedHead())) && (!groundOnly.get() || mc.player.onGround());
	}

	private boolean phased() {
		AABB box = mc.player.getBoundingBox().inflate(-1.0E-7);
		return mc.level.getBlockCollisions(mc.player, box).iterator().hasNext();
	}

	private boolean trappedHead() {
		BlockPos head = mc.player.blockPosition().above(mc.player.isVisuallyCrawling() ? 1 : 2);
		return !mc.level.getBlockState(head).canBeReplaced();
	}

	private boolean blocked(Vec3 delta) {
		double len = delta.length();
		if (len < 1.0E-4) return false;
		Vec3 step = delta.scale(Math.min(len, 2.0) / len);
		return mc.level.getBlockCollisions(mc.player, mc.player.getBoundingBox().move(step).inflate(-1.0E-7)).iterator().hasNext();
	}

	private void queueStatic() {
		if (staticVelocity.get()) pendingStatic = true;
	}

	private void applyStatic() {
		if (!pendingStatic) return;
		pendingStatic = false;
		if (!staticVelocity.get() || (staticPhasedOnly.get() && !phased())) return;
		var in = mc.player.input.keyPresses;
		if (in.forward() || in.backward() || in.left() || in.right() || in.jump()) return;
		mc.player.setDeltaMovement(Vec3.ZERO);
	}

	private synchronized void queueReconcile(Vec3 delta) {
		if (!wallsOnly.get() || !isKnockback(delta)) return;
		pendingReconcile = pendingReconcile.add(delta);
		if (pendingReconcile.length() > MAX_RECONCILE_SPEED) pendingReconcile = pendingReconcile.normalize().scale(MAX_RECONCILE_SPEED);
		reconcileTicks = MAX_RECONCILE_TICKS;
	}

	/** Gives back cancelled knockback once it no longer pushes into a block. */
	private synchronized void applyReconcile() {
		if (reconcileTicks <= 0 || pendingReconcile.lengthSqr() < 9.0E-4) {
			clearReconcile();
			return;
		}
		reconcileTicks--;
		if (blocked(pendingReconcile)) {
			pendingReconcile = new Vec3(pendingReconcile.x * 0.91, pendingReconcile.y * 0.98, pendingReconcile.z * 0.91);
			return;
		}
		mc.player.setDeltaMovement(mc.player.getDeltaMovement().add(pendingReconcile));
		clearReconcile();
	}

	private synchronized void clearReconcile() {
		pendingReconcile = Vec3.ZERO;
		reconcileTicks = 0;
	}
}
