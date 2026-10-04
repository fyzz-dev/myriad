package dev.myriad.essentials.modules.movement;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.InputEvent;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.service.Rotations;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.util.Baritone;
import dev.myriad.api.util.Interactions;
import dev.myriad.api.util.Mining;
import dev.myriad.api.util.Packets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Two ways to fly an elytra, by what you're doing:
 * <ul>
 * <li><b>Recast</b> bounces along a highway along the ground. It jumps each time you touch down and keeps you on the nearest 45°
 * lane. The client keeps gliding through each ground touch instead of letting the elytra close; the server still closes
 * it on landing, so it's reopened (one packet) once you're back in the air, but your own physics never drop to walking
 * while that round trip happens. Jump is only pressed on the ticks you're on the ground and forward is never pressed
 * (strict anticheats check both against the glide); sprint stays on so every jump still adds its boost. The pitch dives
 * while you're rising and levels out as you fall, which turns each bounce into the most forward speed. Fake Lag holds
 * your packets back while you're at ground level, so the server takes the landing and the next hop together and never
 * sees you stop gliding. Something in the lane (an ender chest, a portal, a wall) stops the bounce, and Obstacles decides
 * what happens next: stop, mine through it, or have Baritone walk you round it and carry on bouncing past it.</li>
 * <li><b>Altitude</b> crosses open country without fireworks, "pitch 40" style. It dives until you're fast, pulls up hard, then eases
 * back to level: pulling up gives back more height than the speed it costs, so the cycle holds the altitude you started
 * gliding at (it dives harder when you're above it and climbs more when you're below). Start high, since the first dive
 * from a slow glide drops you 50 or so blocks. Steer with the camera.</li>
 * </ul>
 * The flight rotation only goes to the server and the flight physics, so you can look around freely.
 */
public class ElytraFly extends Module {
	public enum Mode {
		RECAST, ALTITUDE
	}

	public enum Obstacles {
		STOP, MINE, BARITONE
	}

	private final EnumSetting<Mode> mode = sgGeneral.enumSetting("Mode", Mode.RECAST)
		.description("Recast to bounce along a highway, Altitude to cross open country without fireworks.").build();
	private final EnumSetting<Obstacles> obstacles = sgGeneral.enumSetting("Obstacles", Obstacles.MINE)
		.description("What to do about blocks in the lane: stop, mine them, or walk round with Baritone (mines if Baritone isn't installed).")
		.visible(() -> mode.get() == Mode.RECAST).build();
	private final BoolSetting fakeLag = sgGeneral.bool("Fake Lag")
		.description("Hold your packets back while you're at ground level, so the server never sees the elytra close between bounces.").defaultValue(true)
		.visible(() -> mode.get() == Mode.RECAST).build();

	private enum State {
		IDLE, BOUNCING, MINING, PATHING
	}

	private enum Phase {
		DIVE, PULL_UP, CLIMB
	}

	/** Recast: dive while vertical speed is above this, level out below it. */
	private static final double DIVE_UNTIL_Y = -0.2;
	/** Recast: how far ahead to look for blocks in the lane, and how far past them Baritone walks. */
	private static final double LOOK_AHEAD = 3.5, BYPASS_DISTANCE = 8;
	/** Recast: ticks to stop after the server snaps you back. */
	private static final int FLAG_PAUSE = 5;
	/**
	 * Altitude, worked out on vanilla's glide physics: dive at {@code DIVE_PITCH} until faster than the threshold (blocks a
	 * second), snap up to {@code CLIMB_PITCH} at {@code PULL_RATE} a tick, then ease back to level at {@code EASE_RATE}.
	 * The threshold rises by {@code HOLD_GAIN} for every block above the cruise height (more speed, less climb) and falls
	 * below it, which keeps the cycle at that height: about 28 blocks a second.
	 */
	private static final float DIVE_PITCH = 36, CLIMB_PITCH = -49, PULL_RATE = 5, EASE_RATE = 0.8f;
	private static final double DIVE_SPEED = 52, HOLD_GAIN = 0.2, MIN_DIVE_SPEED = 36, MAX_DIVE_SPEED = 58;

	private Mode activeMode = Mode.RECAST;
	private State state = State.IDLE;
	private Phase phase = Phase.DIVE;
	private float climbPitch;
	private double cruiseY = Double.NaN;
	/** Recast's Fake Lag gives up and sends what it's holding after this many ticks, so a stall on the ground can't time you out. */
	private static final int MAX_LAG_TICKS = 5;
	/** Fake Lag holds packets while you're less than this above the last ground you touched. */
	private static final double LAG_HEIGHT = 0.163;

	private boolean wantJump, spoofing, toolHeld;
	/** Keep gliding client-side through ground touches; set once the elytra has opened while bouncing. */
	private boolean holdGlide;
	private volatile boolean flagged;
	private int pauseTicks;
	private double groundY;
	private final Queue<Packet<?>> heldPackets = new ConcurrentLinkedQueue<>();
	private final Queue<ClientboundPingPacket> heldPings = new ConcurrentLinkedQueue<>();
	private int lagTicks;
	private boolean flushing;
	private float lane, spoofYaw, spoofPitch;
	private BlockPos mining;
	private int pathWait;

	public ElytraFly() {
		super(Categories.MOVEMENT, "Elytra Fly", "Bounce along highways, or cross open country without fireworks.");
	}

	@Override
	protected void onEnable() {
		activeMode = mode.get();
		reset();
		if (inGame()) lane = snap(mc.player.getYRot());
	}

	@Override
	protected void onDisable() {
		reset();
	}

	private void reset() {
		if (state == State.PATHING) Baritone.stop();
		state = State.IDLE;
		phase = Phase.DIVE;
		cruiseY = groundY = Double.NaN;
		wantJump = spoofing = holdGlide = flagged = false;
		flushLag();
		mining = null;
		pathWait = pauseTicks = 0;
		releaseTool();
	}

	@Override
	public String hudInfo() {
		if (activeMode == Mode.ALTITUDE) return Double.isNaN(cruiseY) ? "Altitude" : String.format("Y%.0f", cruiseY);
		return switch (state) {
			case MINING -> "Mining";
			case PATHING -> "Baritone";
			case BOUNCING -> pauseTicks > 0 ? "Flagged" : String.format("%.0f°", lane);
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

	/** Whether the local player should count as gliding even though the elytra closed (it closes on every landing). */
	public static boolean holdsGlide() {
		ElytraFly m = Modules.active(ElytraFly.class);
		return m != null && m.holdGlide;
	}

	/** Keep sprinting while gliding, so each jump off the ground adds the sprint boost without holding forward. */
	public static boolean holdsSprint() {
		return holdsGlide();
	}

	// ---- tick -----------------------------------------------------------------------------------------------------

	@Subscribe
	private void onInput(InputEvent e) {
		if (wantJump) e.jump = true;
	}

	@Subscribe
	private void onPacket(PacketEvent.Receive e) {
		if (e.packet() instanceof ClientboundPlayerPositionPacket) flagged = true;
		// Grim and the like time your packets against their pings: hold those too, so the gap reads as lag.
		if (e.packet() instanceof ClientboundPingPacket ping && lagging()) {
			heldPings.add(ping);
			e.cancel();
		}
	}

	@Subscribe
	private void onSend(PacketEvent.Send e) {
		if (flushing) return;
		if (!lagging()) {
			// Whatever was held goes out first, so the server still sees everything in order.
			if (!heldPackets.isEmpty()) flushLag();
			return;
		}
		heldPackets.add(e.packet());
		e.cancel();
	}

	/** Fake Lag: down at ground level while gliding, where the server would see you land and close the elytra. */
	private boolean lagging() {
		return fakeLag.get() && holdGlide && lagTicks < MAX_LAG_TICKS && mc.player != null && mc.player.getY() - groundY < LAG_HEIGHT;
	}

	private void tickLag() {
		if (heldPackets.isEmpty() && heldPings.isEmpty()) {
			lagTicks = 0;
			return;
		}
		if (lagging() && ++lagTicks < MAX_LAG_TICKS) return;
		flushLag();
	}

	/** Sends the held packets, then answers the held pings, in the order they came. */
	private void flushLag() {
		flushing = true;
		try {
			for (Packet<?> p; (p = heldPackets.poll()) != null; ) Packets.sendSilently(p);
			var connection = mc.getConnection();
			for (ClientboundPingPacket p; (p = heldPings.poll()) != null; ) if (connection != null) p.handle(connection);
		} finally {
			flushing = false;
			lagTicks = 0;
		}
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		wantJump = false;
		if (!inGame()) return;
		if (mode.get() != activeMode) {
			reset();
			activeMode = mode.get();
		}
		if (activeMode == Mode.ALTITUDE) tickAltitude();
		else tickBounce();
	}

	// ---- Altitude -------------------------------------------------------------------------------------------------

	private void tickAltitude() {
		flagged = false;
		if (!canGlide()) {
			spoofing = false;
			return;
		}
		var p = mc.player;
		if (!p.isFallFlying()) {
			spoofing = false;
			phase = Phase.DIVE;
			cruiseY = Double.NaN;
			// Open the elytra once you're falling: off a ledge, or on the way down from a jump.
			if (!p.onGround() && p.getDeltaMovement().y < -0.3) startGliding();
			return;
		}
		if (Double.isNaN(cruiseY)) cruiseY = p.getY();

		double speed = p.getDeltaMovement().length() * 20;
		double diveUntil = Mth.clamp(DIVE_SPEED + HOLD_GAIN * (p.getY() - cruiseY), MIN_DIVE_SPEED, MAX_DIVE_SPEED);
		float flightPitch = switch (phase) {
			case DIVE -> {
				if (speed > diveUntil) {
					phase = Phase.PULL_UP;
					climbPitch = 0;
				}
				yield DIVE_PITCH;
			}
			case PULL_UP -> {
				climbPitch = Math.max(CLIMB_PITCH, climbPitch - PULL_RATE);
				if (climbPitch <= CLIMB_PITCH) phase = Phase.CLIMB;
				yield climbPitch;
			}
			case CLIMB -> {
				climbPitch = Math.min(0, climbPitch + EASE_RATE);
				if (climbPitch >= 0) phase = Phase.DIVE;
				yield climbPitch;
			}
		};
		spoof(p.getYRot(), flightPitch);
	}

	// ---- Recast ---------------------------------------------------------------------------------------------------

	private void tickBounce() {
		tickLag();
		if (flagged) {
			flagged = false;
			pauseTicks = FLAG_PAUSE;
			flushLag();
		}
		if (state == State.PATHING) {
			tickPathing();
			return;
		}
		if (!canGlide()) {
			stopBouncing();
			state = State.IDLE;
			return;
		}
		if (state != State.MINING) lane = snap(mc.player.getYRot());

		List<BlockPos> blocked = laneBlocks();
		if (!blocked.isEmpty()) {
			handleObstacle(blocked);
			return;
		}
		if (state == State.MINING) {
			mining = null;
			releaseTool();
		}
		state = State.BOUNCING;
		if (pauseTicks > 0) {
			// The server rejected a move: let its correction land before bouncing on from there.
			pauseTicks--;
			spoofing = holdGlide = false;
			return;
		}
		spoof(lane, mc.player.getDeltaMovement().y > DIVE_UNTIL_Y ? 90 : 4);
		// Vanilla waits 10 ticks between held jumps; jump the tick you touch down.
		Interactions.setJumpCooldown(0);
		if (mc.player.onGround()) {
			groundY = mc.player.getY();
			wantJump = true;
		}

		boolean gliding = elytraOpen();
		if (gliding) holdGlide = true;
		// The server closes the elytra on every landing; reopen it once you're back in the air. Take off the same way,
		// after the first jump or off a ledge.
		if (!gliding && !mc.player.onGround()) startGliding();
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
		holdGlide = false;
		flushLag();
		if (state == State.BOUNCING || state == State.MINING) pauseFlight();
	}

	// ---- Baritone -------------------------------------------------------------------------------------------------

	private void startPathing() {
		Vec3 lane = laneDir();
		Vec3 target = mc.player.position().add(lane.scale(LOOK_AHEAD + BYPASS_DISTANCE));
		// Come back to the lane's own level, not wherever the obstacle left you standing.
		int y = Double.isNaN(groundY) ? mc.player.getBlockY() : Mth.floor(groundY + 0.5);
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
		spoofing = false;
		holdGlide = false;
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

	/** Flies (and tells the server) with this rotation, leaving the camera alone. */
	private void spoof(float yaw, float pitch) {
		spoofYaw = Mth.wrapDegrees(yaw);
		spoofPitch = pitch;
		spoofing = true;
		Myriad.rotations().request(this, spoofYaw, spoofPitch, Rotations.PRIORITY_HIGH + 50);
	}

	private void startGliding() {
		mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
		mc.player.startFallFlying();
	}

	/** Whether the elytra is really open (as the server last said, or as we just asked), ignoring {@link #holdsGlide()}. */
	private boolean elytraOpen() {
		boolean held = holdGlide;
		holdGlide = false;
		boolean open = mc.player.isFallFlying();
		holdGlide = held;
		return open;
	}

	private void pauseFlight() {
		holdGlide = false;
		Vec3 v = mc.player.getDeltaMovement();
		mc.player.setDeltaMovement(0, Math.min(v.y, 0), 0);
		mc.player.hurtMarked = true;
		if (mc.player.isFallFlying()) mc.player.stopFallFlying();
	}

	private boolean canGlide() {
		var p = mc.player;
		if (p.getAbilities().flying || p.isPassenger() || p.isInWater() || p.hasEffect(MobEffects.LEVITATION)) return false;
		if (p.getInBlockState().is(BlockTags.CLIMBABLE)) return false;
		for (EquipmentSlot slot : EquipmentSlot.VALUES) if (LivingEntity.canGlideUsing(p.getItemBySlot(slot), slot)) return true;
		return false;
	}

	/** The way the lane runs. */
	private Vec3 laneDir() {
		return Vec3.directionFromRotation(0, lane);
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
		Vec3 origin = mc.player.position();
		AABB box = mc.player.getBoundingBox();
		int minY = Mth.floor(box.minY + 0.2), maxY = Mth.floor(box.maxY + 0.6);
		Vec3 side = new Vec3(-forward.z, 0, forward.x);
		int steps = Mth.ceil(LOOK_AHEAD * 2);
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
