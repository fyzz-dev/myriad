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
import dev.myriad.essentials.modules.player.SpeedMine;
import dev.myriad.api.util.Mining;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
	private Vec3d travelDir = new Vec3d(0, 0, 1);
	private Vec3d lastPos;

	public ElytraBounce() {
		super(Categories.MOVEMENT, "Elytra Bounce", "Bounce along highways with an elytra.");
	}

	@Override
	protected void onEnable() {
		holdingInput = bouncing = clearing = spoofing = false;
		releasePickaxe();
		if (!inGame()) return;
		previouslyGliding = mc.player.isGliding();
		lane = snap(mc.player.getYaw());
		travelDir = Vec3d.fromPolar(0, mc.player.getYaw());
		lastPos = mc.player.getPos();
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
			previouslyGliding = mc.player.isGliding();
			return;
		}
		if (highwayYaw.get() && !clearing) lane = snap(mc.player.getYaw());
		updateTravelDir();
		tickClearing();

		if (clearing) {
			stopRotation();
			pauseFlight();
			previouslyGliding = mc.player.isGliding();
			return;
		}
		if (!bouncing) {
			stopRotation();
			holdingInput = false;
			previouslyGliding = mc.player.isGliding();
			return;
		}
		tickRotation();

		// Vanilla closes the elytra on touching the ground; reopen it straight away to keep bouncing.
		if (previouslyGliding && !mc.player.isGliding()) recast();
		previouslyGliding = mc.player.isGliding();
		if (mc.options.jumpKey.isPressed() && !mc.player.isGliding()) recast();

		holdingInput = true;
		mc.player.setSprinting(!mc.player.isGliding() || mc.player.isOnGround());
	}

	private void tickRotation() {
		float yaw = highwayYaw.get() ? MathHelper.wrapDegrees(lane) : mc.player.getYaw();
		if (!highwayYaw.get() && !lockPitch.get()) {
			stopRotation();
			return;
		}
		if (!silent.get() || !lockPitch.get()) {
			stopRotation();
			if (lockPitch.get()) mc.player.setPitch(pitch.getFloat());
			if (highwayYaw.get()) {
				mc.player.setYaw(yaw);
				mc.player.setHeadYaw(yaw);
				mc.player.setBodyYaw(yaw);
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
		mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
		mc.player.startGliding();
	}

	private void pauseFlight() {
		holdingInput = false;
		Vec3d v = mc.player.getVelocity();
		mc.player.setVelocity(0, Math.min(v.y, 0), 0);
		mc.player.velocityModified = true;
		if (mc.player.isGliding()) mc.player.stopGliding();
	}

	private boolean canBounce() {
		var p = mc.player;
		if (p.getAbilities().flying || p.hasVehicle() || p.isTouchingWater() || p.hasStatusEffect(StatusEffects.LEVITATION)) return false;
		if (p.getBlockStateAtPos().isIn(BlockTags.CLIMBABLE)) return false;
		for (EquipmentSlot slot : EquipmentSlot.VALUES) if (LivingEntity.canGlideWith(p.getEquippedStack(slot), slot)) return true;
		return false;
	}

	private void updateTravelDir() {
		Vec3d p = mc.player.getPos();
		if (!clearing) {
			Vec3d v = mc.player.getVelocity();
			Vec3d horizontal = new Vec3d(v.x, 0, v.z);
			if (horizontal.lengthSquared() > 0.0025) travelDir = horizontal.normalize();
			else if (lastPos != null) {
				Vec3d d = new Vec3d(p.x - lastPos.x, 0, p.z - lastPos.z);
				if (d.lengthSquared() > 1e-4) travelDir = d.normalize();
			}
		}
		lastPos = p;
	}

	private static float snap(float yaw) {
		return Math.round(MathHelper.wrapDegrees(yaw) / 45f) * 45f;
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
			holdPickaxe(mc.world.getBlockState(blocked.getFirst()));
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
		BlockState state = mc.world.getBlockState(p);
		if (!isLaneBlock(state, p)) return false;
		Direction face = Direction.getFacing(travelDir.x, 0, travelDir.z);
		if (speedMine != null && state.getHardness(mc.world, p) >= 0) return speedMine.offerMine(p);
		if (isPortal(state)) {
			mc.interactionManager.sendSequencedPacket(mc.world, seq -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, p, face, seq));
			mc.interactionManager.sendSequencedPacket(mc.world, seq -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, p, face, seq));
			mc.player.swingHand(Hand.MAIN_HAND);
			return true;
		}
		mc.interactionManager.attackBlock(p, face);
		mc.player.swingHand(Hand.MAIN_HAND);
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
		int best = -1;
		double bestScore = -1;
		for (int i = 0; i < 9; i++) {
			ItemStack s = mc.player.getInventory().getStack(i);
			if (s.isEmpty()) continue;
			double score = s.getMiningSpeedMultiplier(state) + (s.isIn(ItemTags.PICKAXES) ? 100 : 0);
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		return best >= 0 ? best : Mining.fastestSlot(state, 0, 9);
	}

	private List<BlockPos> laneBlocks() {
		List<BlockPos> found = new ArrayList<>();
		Vec3d forward = travelDir.lengthSquared() < 1e-6 ? Vec3d.fromPolar(0, highwayYaw.get() ? lane : mc.player.getYaw()) : travelDir;
		double range = Math.max(passDistance.get(), 2.0);
		Vec3d origin = mc.player.getPos();
		Box box = mc.player.getBoundingBox();
		int minY = MathHelper.floor(box.minY + 0.2), maxY = MathHelper.floor(box.maxY + 0.6);
		Vec3d side = new Vec3d(-forward.z, 0, forward.x);
		int steps = Math.max(1, MathHelper.ceil(range * 2));
		for (int i = 1; i <= steps; i++) {
			for (double offset : new double[]{-0.4, 0, 0.4}) {
				Vec3d sample = origin.add(forward.multiply(i * 0.5)).add(side.multiply(offset));
				BlockPos column = BlockPos.ofFloored(sample.x, origin.y, sample.z);
				for (int y = minY; y <= maxY; y++) addLaneBlock(found, new BlockPos(column.getX(), y, column.getZ()));
			}
		}
		if (mc.player.horizontalCollision) {
			BlockPos bump = BlockPos.ofFloored(origin.add(forward.multiply(0.8)));
			for (int y = minY; y <= maxY; y++) addLaneBlock(found, new BlockPos(bump.getX(), y, bump.getZ()));
		}
		found.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getPos())));
		return found;
	}

	private void addLaneBlock(List<BlockPos> found, BlockPos p) {
		if (found.contains(p) || !isLaneBlock(mc.world.getBlockState(p), p)) return;
		found.add(p.toImmutable());
	}

	private boolean isLaneBlock(BlockState state, BlockPos p) {
		if (state.isAir() || !state.getFluidState().isEmpty()) return false;
		if (isPortal(state)) return true;
		if (state.getHardness(mc.world, p) < 0) return false;
		return !state.getCollisionShape(mc.world, p).isEmpty();
	}

	private boolean laneStillMining(SpeedMine speedMine, List<BlockPos> blocked) {
		if (!speedMine.isMining()) return false;
		BlockPos primary = speedMine.primaryPos(), secondary = speedMine.secondaryPos();
		if (primary != null && (blocked.contains(primary) || isLaneBlock(mc.world.getBlockState(primary), primary))) return true;
		if (secondary != null && (blocked.contains(secondary) || isLaneBlock(mc.world.getBlockState(secondary), secondary))) return true;
		return speedMine.hasExternalWork();
	}

	private static boolean isPortal(BlockState state) {
		return state.isOf(Blocks.NETHER_PORTAL) || state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.END_GATEWAY);
	}
}
