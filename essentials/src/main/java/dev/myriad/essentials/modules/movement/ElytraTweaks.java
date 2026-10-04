package dev.myriad.essentials.modules.movement;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Priority;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.util.MathUtil;
import dev.myriad.essentials.mixin.FireworkRocketEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Tweaks to elytra flight that work with Grim (2b2t).
 * <p>
 * <b>Rocket Boost.</b> Grim doesn't simulate a rocket's push; while one is attached to you it allows any movement in a
 * box around your look: on each axis up to 1.7 times the sum of that axis of your current look and the one before it
 * (as Grim tracks them), capped at 1.7 blocks a tick. Vanilla's push only ever approaches 1.7 blocks a tick along your
 * look, and takes a while to get there. With Rocket Boost, every tick a rocket is attached you move as far along your
 * look as that box allows (with a margin): full speed straight away, and since the cap is per axis, faster when you fly
 * diagonally (about 34 blocks a second along an axis, 46 on a diagonal). Looking level holds your height. Applied by
 * this addon's LivingEntityMixin.
 */
public class ElytraTweaks extends Module {
	private final BoolSetting rocketBoost = sgGeneral.bool("Rocket Boost")
		.description("While a rocket is attached, fly as fast along your look as Grim allows: full speed at once, and faster on diagonals.")
		.defaultValue(true).build();

	/** Grim's cap on a rocket's movement, blocks a tick per axis. */
	private static final double CAP = 1.7;
	/**
	 * Stay this far inside Grim's box: a little holding still, more while turning and for a while after (its box then
	 * came out up to a few percent tighter than worked out here, on the test server, especially vertically).
	 */
	private static final double MARGIN = 0.95, TURNING_MARGIN = 0.85;
	/** Ticks after a turn that still get the turning margin. */
	private static final int TURN_TICKS = 10;
	/** How long a look sent before the latest one may still be Grim's previous look, in ticks (covers lag). */
	private static final int RECENT_TICKS = 20;
	/** Ticks after a server teleport with vanilla's own flight, while Grim settles. */
	private static final int TELEPORT_PAUSE = 5;
	/** How far along the look to aim before clamping to the box (anything past the cap). */
	private static final double WANT = 10;

	/**
	 * The two rotations Grim builds the box from, as it tracks them: it only takes a new look from packets that carry
	 * one, and vanilla only sends one when you turn, so while you hold still its "last look" is the one from before
	 * your latest turn, however long ago.
	 */
	private float sentYaw, sentPitch, beforeYaw, beforePitch;

	public ElytraTweaks() {
		super(Categories.MOVEMENT, "Elytra Tweaks", "Faster rocket boosts that Grim accepts.");
	}

	@Override
	protected void onEnable() {
		sentYaw = beforeYaw = Myriad.rotations().serverYaw();
		sentPitch = beforePitch = Myriad.rotations().serverPitch();
	}

	/** A server teleport sets both of Grim's looks to the rotation you answer it with. */
	private volatile boolean teleported;
	private volatile int teleportPause;
	private int sinceTurn = TURN_TICKS;

	/** A look sent, and when (client ticks). */
	private record Sent(Vec3 look, int tick) {
	}

	/** Looks sent in the last {@link #RECENT_TICKS} ticks. */
	private final Deque<Sent> recent = new ArrayDeque<>();
	private int ticks;

	@Subscribe
	private void onTick(TickEvent.Post e) {
		ticks++;
		while (!recent.isEmpty() && ticks - recent.peekFirst().tick > RECENT_TICKS) recent.pollFirst();
	}

	@Subscribe(priority = Priority.LOWEST)
	private void onSend(PacketEvent.Send e) {
		if (!(e.packet() instanceof ServerboundMovePlayerPacket p) || !p.hasRotation()) return;
		recent.addLast(new Sent(MathUtil.direction(sentYaw, sentPitch), ticks));
		beforeYaw = teleported ? p.getYRot(sentYaw) : sentYaw;
		beforePitch = teleported ? p.getXRot(sentPitch) : sentPitch;
		sentYaw = p.getYRot(sentYaw);
		sentPitch = p.getXRot(sentPitch);
		teleported = false;
	}

	@Subscribe
	private void onReceive(PacketEvent.Receive e) {
		if (e.packet() instanceof ClientboundPlayerPositionPacket) {
			teleported = true;
			teleportPause = TELEPORT_PAUSE;
		}
	}

	/**
	 * The movement the local player's glide should make this tick, given vanilla's: unchanged unless Rocket Boost is on,
	 * you're gliding with a rocket attached, and no module is turning you server-side (the box is built from the
	 * rotation the server gets, so it has to be yours).
	 */
	public static Vec3 glideMovement(Vec3 vanilla) {
		ElytraTweaks m = Modules.active(ElytraTweaks.class);
		if (m == null || !m.rocketBoost.get()) return vanilla;
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || !p.isFallFlying() || Myriad.rotations().isRotating() || !rocketAttached(p)) return vanilla;
		if (m.teleported || m.teleportPause > 0) {
			m.teleportPause--;
			return vanilla;
		}
		Vec3 now = MathUtil.direction(p.getYRot(), p.getXRot());
		// This tick's packet carries a look only if you've turned since the last one; that look is Grim's current one.
		// Its previous one is the look before your latest turn, or, on the test server (more so with lag), sometimes one
		// sent shortly before that, or the current look again. Each axis is held inside the tightest of those; holding
		// still, they're all the same.
		boolean turning = p.getYRot() != m.sentYaw || p.getXRot() != m.sentPitch;
		Vec3 current = turning ? now : MathUtil.direction(m.sentYaw, m.sentPitch);
		List<Vec3> previous = new ArrayList<>(m.recent.size() + 3);
		previous.add(current);
		previous.add(MathUtil.direction(m.sentYaw, m.sentPitch));
		previous.add(MathUtil.direction(m.beforeYaw, m.beforePitch));
		for (Sent r : m.recent) if (m.ticks - r.tick <= RECENT_TICKS) previous.add(r.look);
		m.sinceTurn = turning ? 0 : Math.min(TURN_TICKS, m.sinceTurn + 1);
		double margin = m.sinceTurn < TURN_TICKS ? TURNING_MARGIN : MARGIN;
		return new Vec3(axis(now.x, current.x, previous, Vec3::x, margin), axis(now.y, current.y, previous, Vec3::y, margin),
			axis(now.z, current.z, previous, Vec3::z, margin));
	}

	/**
	 * One axis: as far along your look as the box allows with your current look and the least favourable previous one.
	 * Always inside the box: vanilla's movement here already includes the rocket's own push, which Grim doesn't
	 * predict, so going past the box with it is flagged.
	 */
	private static double axis(double look, double current, List<Vec3> previous, ToDoubleFunction<Vec3> component, double margin) {
		double minPositive = Double.MAX_VALUE, maxNegative = -Double.MAX_VALUE;
		for (Vec3 l : previous) {
			minPositive = Math.min(minPositive, Math.max(0, component.applyAsDouble(l)));
			maxNegative = Math.max(maxNegative, Math.min(0, component.applyAsDouble(l)));
		}
		double hi = Math.min(CAP, CAP * (Math.max(0, current) + minPositive)) * margin;
		double lo = Math.max(-CAP, CAP * (Math.min(0, current) + maxNegative)) * margin;
		return Math.clamp(look * WANT, lo, hi);
	}

	/** Whether a rocket is boosting {@code p}, as the server told us (the same moment Grim learns it). */
	private static boolean rocketAttached(LocalPlayer p) {
		for (FireworkRocketEntity rocket : p.level().getEntitiesOfClass(FireworkRocketEntity.class, p.getBoundingBox().inflate(4))) {
			if (rocket.isRemoved()) continue;
			var target = rocket.getEntityData().get(FireworkRocketEntityAccessor.essentials$attachedTarget());
			if (target.isPresent() && target.getAsInt() == p.getId()) return true;
		}
		return false;
	}
}
