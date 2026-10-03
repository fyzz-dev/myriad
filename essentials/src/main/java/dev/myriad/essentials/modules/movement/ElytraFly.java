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
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.api.util.Baritone;
import dev.myriad.api.util.Mining;
import dev.myriad.api.util.Packets;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Elytra bouncing for highway travel: holds forward (and jump), reopens the elytra the moment it closes on the ground,
 * locks your pitch and keeps you on the nearest 45° highway lane. With Silent, the lane yaw and bounce pitch only go to
 * the server and the flight physics, so you can look around freely.
 * <p>
 * Something in the lane (an ender chest, a portal, a wall) stops the bounce. Obstacles decides what happens next:
 * stop, mine through it with the best tool, or have Baritone walk you round it and carry on bouncing past it.
 */
public class ElytraFly extends Module {
	public enum Obstacles {
		STOP, MINE, BARITONE
	}

	private final DoubleSetting pitch = sgGeneral.doubleSetting("Pitch").description("Pitch while bouncing.").defaultValue(40).range(0, 90).decimals(0).build();
	private final BoolSetting lockPitch = sgGeneral.bool("Lock Pitch").description("Hold the bounce pitch.").defaultValue(true).build();
	private final BoolSetting silent = sgGeneral.bool("Silent").description("Only the server and flight physics see the locked rotation; your camera stays free.")
		.defaultValue(true).visible(lockPitch::get).build();
	private final BoolSetting highwayYaw = sgGeneral.bool("Highway Yaw").description("Snap your heading to the nearest 45° lane.").defaultValue(true).build();
	private final BoolSetting autoJump = sgGeneral.bool("Auto Jump").description("Hold jump so you take off from the ground and bounce on contact.").defaultValue(true).build();
	private final BoolSetting takeOff = sgGeneral.bool("Take Off").description("Open the elytra by itself whenever you're in the air.").defaultValue(true).build();
	private final BoolSetting packet = sgGeneral.bool("Packet").description("Keep a standing pose while gliding, so your hitbox doesn't shrink between bounces.").build();

	private final SettingGroup sgObstacles = settings.group("Obstacles");
	private final EnumSetting<Obstacles> obstacles = sgObstacles.enumSetting("Obstacles", Obstacles.MINE)
		.description("What to do about blocks in the lane: stop, mine them, or walk round with Baritone (mines if Baritone isn't installed).").build();
	private final DoubleSetting lookAhead = sgObstacles.doubleSetting("Look Ahead").description("How far ahead to look for blocks in the lane.").defaultValue(3.5).range(1, 6).decimals(1).build();
	private final IntSetting bypassDistance = sgObstacles.intSetting("Bypass Distance").description("How far past the obstacle Baritone walks before bouncing resumes.")
		.defaultValue(8).range(3, 32).visible(() -> obstacles.get() == Obstacles.BARITONE).build();
	private final BoolSetting forceY = sgObstacles.bool("Force Y").description("Have Baritone come back to a fixed Y level (the highway's).")
		.visible(() -> obstacles.get() == Obstacles.BARITONE).build();
	private final IntSetting yLevel = sgObstacles.intSetting("Y Level").defaultValue(120).range(-64, 320)
		.visible(() -> obstacles.get() == Obstacles.BARITONE && forceY.get()).build();

	private enum State {
		IDLE, BOUNCING, MINING, PATHING
	}

	private State state = State.IDLE;
	private boolean previouslyGliding, holdingInput, spoofing, toolHeld;
	private float lane, spoofYaw, spoofPitch;
	private Vec3 travelDir = new Vec3(0, 0, 1);
	private Vec3 lastPos;
	private BlockPos mining;
	private int pathWait;

	public ElytraFly() {
		super(Categories.MOVEMENT, "Elytra Fly", "Bounce along highways with an elytra, past whatever's in the way.");
	}

	@Override
	protected void onEnable() {
		reset();
		if (!inGame()) return;
		previouslyGliding = mc.player.isFallFlying();
		lane = snap(mc.player.getYRot());
		travelDir = Vec3.directionFromRotation(0, mc.player.getYRot());
		lastPos = mc.player.position();
	}

	@Override
	protected void onDisable() {
		if (state == State.PATHING) Baritone.stop();
		reset();
	}

	private void reset() {
		state = State.IDLE;
		holdingInput = spoofing = false;
		mining = null;
		pathWait = 0;
		releaseTool();
	}

	@Override
	public String hudInfo() {
		return switch (state) {
			case MINING -> "Mining";
			case PATHING -> "Baritone";
			case BOUNCING -> String.format("%.0f°", lane);
			case IDLE -> null;
		};
	}

	// ---- hooks used by this addon's mixins ------------------------------------------------------------------------

	/** Whether flight physics should use the spoofed rotation instead of the camera's. */
	public static boolean spoofing() {
		ElytraFly m = Modules.active(ElytraFly.class);
		return m != null && m.spoofing;
	}

	/** The rotation flight physics should use; only meaningful while {@link #spoofing()}. */
	public static float spoofYaw() {
		ElytraFly m = Modules.get(ElytraFly.class);
		return m == null ? 0 : m.spoofYaw;
	}

	public static float spoofPitch() {
		ElytraFly m = Modules.get(ElytraFly.class);
		return m == null ? 0 : m.spoofPitch;
	}

	/** Keep the standing pose (Packet option). */
	public static boolean holdStandingPose() {
		ElytraFly m = Modules.active(ElytraFly.class);
		return m != null && m.packet.get() && m.state == State.BOUNCING;
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
		if (state == State.PATHING) {
			tickPathing();
			return;
		}
		if (!canBounce()) {
			stopBouncing();
			state = State.IDLE;
			previouslyGliding = mc.player.isFallFlying();
			return;
		}
		if (highwayYaw.get() && state != State.MINING) lane = snap(mc.player.getYRot());
		updateTravelDir();

		List<BlockPos> blocked = laneBlocks();
		if (!blocked.isEmpty()) {
			handleObstacle(blocked);
			previouslyGliding = mc.player.isFallFlying();
			return;
		}
		if (state == State.MINING) {
			mining = null;
			releaseTool();
		}
		state = State.BOUNCING;
		tickRotation();

		// Vanilla closes the elytra on touching the ground; reopen it straight away to keep bouncing.
		if (previouslyGliding && !mc.player.isFallFlying()) recast();
		previouslyGliding = mc.player.isFallFlying();
		// Take off: once off the ground (Auto Jump's jump, a ledge, or your own jump), open the elytra.
		if (!mc.player.isFallFlying() && !mc.player.onGround() && (takeOff.get() || mc.options.keyJump.isDown())) recast();

		holdingInput = true;
		mc.player.setSprinting(!mc.player.isFallFlying() || mc.player.onGround());
	}

	private void handleObstacle(List<BlockPos> blocked) {
		stopBouncing();
		Obstacles how = obstacles.get();
		if (how == Obstacles.BARITONE && Baritone.isAvailable()) {
			startPathing();
			return;
		}
		if (how == Obstacles.STOP) {
			state = State.IDLE;
			return;
		}
		// Unbreakable blocks (bedrock, barriers) can only be stopped at or walked round.
		for (BlockPos pos : blocked) {
			if (mc.level.getBlockState(pos).getDestroySpeed(mc.level, pos) < 0) continue;
			state = State.MINING;
			mine(pos);
			return;
		}
		state = State.IDLE;
	}

	private void stopBouncing() {
		spoofing = false;
		holdingInput = false;
		if (state == State.BOUNCING || state == State.MINING) pauseFlight();
	}

	// ---- Baritone -------------------------------------------------------------------------------------------------

	private void startPathing() {
		Vec3 lane = laneDir();
		Vec3 target = mc.player.position().add(lane.scale(lookAhead.get() + bypassDistance.get()));
		int y = forceY.get() ? yLevel.get() : mc.player.getBlockY();
		if (!Baritone.pathTo(Mth.floor(target.x), y, Mth.floor(target.z))) {
			state = State.MINING;
			return;
		}
		state = State.PATHING;
		pathWait = 0;
		info("Lane blocked, walking round it with Baritone");
	}

	/** Waits for Baritone to get past the obstacle, then bounces on (or gives up after a few seconds without a path). */
	private void tickPathing() {
		holdingInput = false;
		spoofing = false;
		if (Baritone.isPathing()) {
			pathWait = 0;
			return;
		}
		// Give the pathfinder a moment to start before deciding it's done or failed.
		if (++pathWait < 40) return;
		state = State.IDLE;
		if (laneBlocks().isEmpty()) return;
		warn("Baritone couldn't get past the obstacle");
		disable();
	}

	// ---- rotation and flight --------------------------------------------------------------------------------------

	private void tickRotation() {
		float yaw = highwayYaw.get() ? Mth.wrapDegrees(lane) : mc.player.getYRot();
		if (!highwayYaw.get() && !lockPitch.get()) {
			spoofing = false;
			return;
		}
		if (!silent.get() || !lockPitch.get()) {
			spoofing = false;
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

	private void recast() {
		if (!canBounce()) return;
		mc.player.setOnGround(false);
		mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
		mc.player.startFallFlying();
	}

	private void pauseFlight() {
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
		if (state != State.MINING) {
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

	/** The way the lane runs: the snapped heading on a highway, else where you're going. */
	private Vec3 laneDir() {
		if (highwayYaw.get()) return Vec3.directionFromRotation(0, lane);
		return travelDir.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, mc.player.getYRot()) : travelDir;
	}

	private static float snap(float yaw) {
		return Math.round(Mth.wrapDegrees(yaw) / 45f) * 45f;
	}

	// ---- mining ---------------------------------------------------------------------------------------------------

	private void mine(BlockPos pos) {
		BlockState block = mc.level.getBlockState(pos);
		holdTool(block);
		Direction face = Direction.getApproximateNearest(-laneDir().x, 0, -laneDir().z);
		if (isPortal(block)) {
			// Portals break instantly server-side but not client-side: send the dig directly.
			Packets.sendSequenced(seq -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, face, seq));
			Packets.sendSequenced(seq -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, face, seq));
		} else if (!pos.equals(mining)) {
			mc.gameMode.startDestroyBlock(pos, face);
		} else {
			mc.gameMode.continueDestroyBlock(pos, face);
		}
		mining = pos.immutable();
		mc.player.swing(InteractionHand.MAIN_HAND);
	}

	private void holdTool(BlockState state) {
		int best = Myriad.inventory().bestInHotbar(s -> s.isEmpty() ? 0 : s.getDestroySpeed(state) + (s.is(ItemTags.PICKAXES) ? 100 : 0));
		int slot = best >= 0 ? best : Mining.fastestSlot(state, 0, 9);
		if (slot >= 0 && Myriad.inventory().hold(this, slot, 80)) toolHeld = true;
	}

	private void releaseTool() {
		if (!toolHeld) return;
		toolHeld = false;
		Myriad.inventory().release(this);
	}

	private List<BlockPos> laneBlocks() {
		List<BlockPos> found = new ArrayList<>();
		Vec3 forward = laneDir();
		double range = Math.max(lookAhead.get(), 2.0);
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
		return isPortal(state) || !state.getCollisionShape(mc.level, p).isEmpty();
	}

	private static boolean isPortal(BlockState state) {
		return state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
	}
}
