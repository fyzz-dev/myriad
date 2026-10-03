package dev.myriad.essentials.modules.movement;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.InputEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.service.Rotations;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.util.Mining;
import dev.myriad.api.util.Packets;
import dev.myriad.essentials.modules.player.SpeedMine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Elytra bouncing for highway travel. Holds forward (and jump), reopens the elytra the moment it closes on the
 * ground, locks your pitch and keeps you on the nearest 45° highway lane.
 * <p>
 * With Silent, the lane yaw and bounce pitch are only sent to the server and used for the flight physics, so you
 * can look around freely while bouncing. With Obstacle Pass, blocks in the lane ahead (ender chests, portals, …)
 * stop the bounce: you hold a pickaxe and mine them, through Speed Mine when it's on, then carry on.
 */
public class ElytraBounce extends Module {

	private final DoubleSetting pitch = sgGeneral.doubleSetting("Pitch").description("Pitch while bouncing.").defaultValue(40).range(0, 90).decimals(0).build();
	private final BoolSetting lockPitch = sgGeneral.bool("Lock Pitch").description("Hold the bounce pitch.").defaultValue(true).build();
	private final BoolSetting silent = sgGeneral.bool("Silent").description("Only the server and flight physics see the locked rotation; your camera stays free.")
		.defaultValue(true).visible(lockPitch::get).build();
	private final BoolSetting packet = sgGeneral.bool("Packet").description("Keep a standing pose while gliding, so your hitbox doesn't shrink between bounces.").build();
	private final BoolSetting autoJump = sgGeneral.bool("Auto Jump").description("Hold jump so you bounce on ground contact.").defaultValue(true).build();
	private final BoolSetting highwayYaw = sgGeneral.bool("Highway Yaw").description("Snap your heading to the nearest 45° lane.").defaultValue(true).build();
	private final BoolSetting obstaclePass = sgGeneral.bool("Obstacle Pass").description("Stop and mine blocks in the lane (ender chests, portals, …) until it's clear.").defaultValue(true).build();
	private final DoubleSetting passDistance = sgGeneral.doubleSetting("Pass Distance").description("How far ahead to look for blocks in the lane.").defaultValue(3.5).range(1, 6).decimals(1)
		.visible(obstaclePass::get).build();

	private boolean previouslyGliding, holdingInput, bouncing, clearing, pickaxeHeld, spoofing;
	private float lane, spoofYaw, spoofPitch;
	private Vec3 travelDir = new Vec3(0, 0, 1);
	private Vec3 lastPos;

	public ElytraBounce() {
		super(Categories.MOVEMENT, "Elytra Bounce", "Bounce along highways with an elytra.");
	}

	@Override
	protected void onEnable() {
		holdingInput = bouncing = clearing = spoofing = false;
		releasePickaxe();
		if (!inGame()) return;
		previouslyGliding = mc.player.isFallFlying();
		lane = snap(mc.player.getYRot());
		travelDir = Vec3.directionFromRotation(0, mc.player.getYRot());
		lastPos = mc.player.position();
	}

	@Override
	protected void onDisable() {
		holdingInput = bouncing = clearing = spoofing = false;
		releasePickaxe();
	}

	@Override
	public String hudInfo() {
		if (clearing) return "Mine";
		return bouncing ? String.format("%.0f°", lane) : null;
	}

	// ---- hooks used by this addon's mixins ------------------------------------------------------------------------

	/** Whether flight physics should use the spoofed rotation instead of the camera's. */
	public static boolean spoofing() {
		ElytraBounce m = Modules.active(ElytraBounce.class);
		return m != null && m.spoofing;
	}

	/** The rotation flight physics should use; only meaningful while {@link #spoofing()}. */
	public static float spoofYaw() {
		ElytraBounce m = Modules.get(ElytraBounce.class);
		return m == null ? 0 : m.spoofYaw;
	}

	public static float spoofPitch() {
		ElytraBounce m = Modules.get(ElytraBounce.class);
		return m == null ? 0 : m.spoofPitch;
	}

	/** Keep the standing pose (Packet option). */
	public static boolean holdStandingPose() {
		ElytraBounce m = Modules.active(ElytraBounce.class);
		return m != null && m.packet.get() && m.bouncing && !m.clearing;
	}

	// ---- tick -----------------------------------------------------------------------------------------------------

	@Subscribe
	private void onInput(InputEvent e) {
		if (!holdingInput) return;
		e.forward = true;
		if (autoJump.get()) e.jump = true;
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) return;
		bouncing = canBounce();
		if (!bouncing && !clearing) {
			stopRotation();
			holdingInput = false;
			previouslyGliding = mc.player.isFallFlying();
			return;
		}
		if (highwayYaw.get() && !clearing) lane = snap(mc.player.getYRot());
		updateTravelDir();
		tickClearing();

		if (clearing) {
			stopRotation();
			pauseFlight();
			previouslyGliding = mc.player.isFallFlying();
			return;
		}
		if (!bouncing) {
			stopRotation();
			holdingInput = false;
			previouslyGliding = mc.player.isFallFlying();
			return;
		}
		tickRotation();

		// Vanilla closes the elytra on touching the ground; reopen it straight away to keep bouncing.
		if (previouslyGliding && !mc.player.isFallFlying()) recast();
		previouslyGliding = mc.player.isFallFlying();
		if (mc.options.keyJump.isDown() && !mc.player.isFallFlying()) recast();

		holdingInput = true;
		mc.player.setSprinting(!mc.player.isFallFlying() || mc.player.onGround());
	}

	private void tickRotation() {
		float yaw = highwayYaw.get() ? Mth.wrapDegrees(lane) : mc.player.getYRot();
		if (!highwayYaw.get() && !lockPitch.get()) {
			stopRotation();
			return;
		}
		if (!silent.get() || !lockPitch.get()) {
			stopRotation();
			if (lockPitch.get()) mc.player.setXRot(pitch.getFloat());
			if (highwayYaw.get()) {
				mc.player.setYRot(yaw);
				mc.player.setYHeadRot(yaw);
				mc.player.setYBodyRot(yaw);
			}
			return;
		}
		spoofYaw = yaw;
		spoofPitch = pitch.getFloat();
		spoofing = true;
		Myriad.rotations().request(this, spoofYaw, spoofPitch, Rotations.PRIORITY_HIGH + 50);
	}

	private void stopRotation() {
		spoofing = false;
	}

	private void recast() {
		if (!canBounce() || clearing) return;
		mc.player.setOnGround(false);
		mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
		mc.player.startFallFlying();
	}

	private void pauseFlight() {
		holdingInput = false;
		Vec3 v = mc.player.getDeltaMovement();
		mc.player.setDeltaMovement(0, Math.min(v.y, 0), 0);
		mc.player.hurtMarked = true;
		if (mc.player.isFallFlying()) mc.player.stopFallFlying();
	}

	private boolean canBounce() {
		var p = mc.player;
		if (p.getAbilities().flying || p.isPassenger() || p.isInWater() || p.hasEffect(MobEffects.LEVITATION)) return false;
		if (p.getInBlockState().is(BlockTags.CLIMBABLE)) return false;
		for (EquipmentSlot slot : EquipmentSlot.VALUES) if (LivingEntity.canGlideUsing(p.getItemBySlot(slot), slot)) return true;
		return false;
	}

	private void updateTravelDir() {
		Vec3 p = mc.player.position();
		if (!clearing) {
			Vec3 v = mc.player.getDeltaMovement();
			Vec3 horizontal = new Vec3(v.x, 0, v.z);
			if (horizontal.lengthSqr() > 0.0025) travelDir = horizontal.normalize();
			else if (lastPos != null) {
				Vec3 d = new Vec3(p.x - lastPos.x, 0, p.z - lastPos.z);
				if (d.lengthSqr() > 1e-4) travelDir = d.normalize();
			}
		}
		lastPos = p;
	}

	private static float snap(float yaw) {
		return Math.round(Mth.wrapDegrees(yaw) / 45f) * 45f;
	}

	// ---- obstacle pass --------------------------------------------------------------------------------------------

	private void tickClearing() {
		if (!obstaclePass.get()) {
			if (clearing) {
				clearing = false;
				releasePickaxe();
			}
			return;
		}
		List<BlockPos> blocked = laneBlocks();
		SpeedMine speedMine = speedMine();
		boolean stillMining = speedMine != null && laneStillMining(speedMine, blocked);
		if (blocked.isEmpty() && !stillMining) {
			if (clearing) {
				clearing = false;
				releasePickaxe();
			}
			return;
		}
		clearing = true;
		if (!blocked.isEmpty()) {
			holdPickaxe(mc.level.getBlockState(blocked.getFirst()));
			mineLane(blocked, speedMine);
		}
	}

	private SpeedMine speedMine() {
		return Modules.active(SpeedMine.class);
	}

	private void mineLane(List<BlockPos> blocked, SpeedMine speedMine) {
		int started = 0, limit = speedMine != null && speedMine.doubleMine() ? 2 : 1;
		for (BlockPos p : blocked) {
			if (started >= limit) {
				if (speedMine != null) speedMine.offerMine(p);
				continue;
			}
			if (speedMine != null && speedMine.isMining(p)) {
				started++;
				continue;
			}
			if (mineBlock(p, speedMine)) started++;
		}
	}

	private boolean mineBlock(BlockPos p, SpeedMine speedMine) {
		BlockState state = mc.level.getBlockState(p);
		if (!isLaneBlock(state, p)) return false;
		Direction face = Direction.getApproximateNearest(travelDir.x, 0, travelDir.z);
		if (speedMine != null && state.getDestroySpeed(mc.level, p) >= 0) return speedMine.offerMine(p);
		if (isPortal(state)) {
			Packets.sendSequenced(seq -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, p, face, seq));
			Packets.sendSequenced(seq -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, p, face, seq));
			mc.player.swing(InteractionHand.MAIN_HAND);
			return true;
		}
		mc.gameMode.startDestroyBlock(p, face);
		mc.player.swing(InteractionHand.MAIN_HAND);
		return true;
	}

	private void holdPickaxe(BlockState state) {
		int slot = bestToolSlot(state);
		if (slot < 0) return;
		if (Myriad.inventory().hold(this, slot, 80)) pickaxeHeld = true;
	}

	private void releasePickaxe() {
		if (!pickaxeHeld) return;
		pickaxeHeld = false;
		Myriad.inventory().release(this);
	}

	/** Best hotbar tool, preferring pickaxes. */
	private int bestToolSlot(BlockState state) {
		int best = Myriad.inventory().bestInHotbar(s -> s.isEmpty() ? 0 : s.getDestroySpeed(state) + (s.is(ItemTags.PICKAXES) ? 100 : 0));
		return best >= 0 ? best : Mining.fastestSlot(state, 0, 9);
	}

	private List<BlockPos> laneBlocks() {
		List<BlockPos> found = new ArrayList<>();
		Vec3 forward = travelDir.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, highwayYaw.get() ? lane : mc.player.getYRot()) : travelDir;
		double range = Math.max(passDistance.get(), 2.0);
		Vec3 origin = mc.player.position();
		AABB box = mc.player.getBoundingBox();
		int minY = Mth.floor(box.minY + 0.2), maxY = Mth.floor(box.maxY + 0.6);
		Vec3 side = new Vec3(-forward.z, 0, forward.x);
		int steps = Math.max(1, Mth.ceil(range * 2));
		for (int i = 1; i <= steps; i++) {
			for (double offset : new double[]{-0.4, 0, 0.4}) {
				Vec3 sample = origin.add(forward.scale(i * 0.5)).add(side.scale(offset));
				BlockPos column = BlockPos.containing(sample.x, origin.y, sample.z);
				for (int y = minY; y <= maxY; y++) addLaneBlock(found, new BlockPos(column.getX(), y, column.getZ()));
			}
		}
		if (mc.player.horizontalCollision) {
			BlockPos bump = BlockPos.containing(origin.add(forward.scale(0.8)));
			for (int y = minY; y <= maxY; y++) addLaneBlock(found, new BlockPos(bump.getX(), y, bump.getZ()));
		}
		found.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(mc.player.position())));
		return found;
	}

	private void addLaneBlock(List<BlockPos> found, BlockPos p) {
		if (found.contains(p) || !isLaneBlock(mc.level.getBlockState(p), p)) return;
		found.add(p.immutable());
	}

	private boolean isLaneBlock(BlockState state, BlockPos p) {
		if (state.isAir() || !state.getFluidState().isEmpty()) return false;
		if (isPortal(state)) return true;
		if (state.getDestroySpeed(mc.level, p) < 0) return false;
		return !state.getCollisionShape(mc.level, p).isEmpty();
	}

	private boolean laneStillMining(SpeedMine speedMine, List<BlockPos> blocked) {
		if (!speedMine.isMining()) return false;
		BlockPos primary = speedMine.primaryPos(), secondary = speedMine.secondaryPos();
		if (primary != null && (blocked.contains(primary) || isLaneBlock(mc.level.getBlockState(primary), primary))) return true;
		if (secondary != null && (blocked.contains(secondary) || isLaneBlock(mc.level.getBlockState(secondary), secondary))) return true;
		return speedMine.hasExternalWork();
	}

	private static boolean isPortal(BlockState state) {
		return state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
	}
}
