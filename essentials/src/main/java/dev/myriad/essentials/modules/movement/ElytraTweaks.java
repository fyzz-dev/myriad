package dev.myriad.essentials.modules.movement;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Priority;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.InputEvent;
import dev.myriad.api.event.events.InteractEvent;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.util.MathUtil;
import dev.myriad.essentials.mixin.FireworkRocketEntityAccessor;
import dev.myriad.essentials.util.ChestSwap;
import dev.myriad.essentials.util.GlideHold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;


/**
 * Tweaks to elytra flight that work with Grim (2b2t).
 * <p>
 * <b>Rocket Boost.</b> Grim doesn't simulate a rocket's push. While one is attached to you it takes the plain glide it
 * predicts from last tick's movement and widens it, on each axis, by up to 1.7 times your current look plus the one
 * before it (with a little slack for skipped ticks), capped at 1.7 blocks a tick; anything inside passes. Vanilla's push
 * only ever approaches 1.7 blocks a tick along your look, and takes a while to get there. With Rocket Boost, every
 * tick a rocket is attached you move as far along your look as that window allows (a little inside it): full speed at
 * once, and since the cap is per axis, faster when you fly diagonally. Looking level holds your height. Every
 * movement packet carries your rotation meanwhile, so the look before is exactly the one sent last tick. Applied by
 * this addon's LivingEntityMixin.
 * <p>
 * <b>No Durability.</b> The server wears an elytra by one for every 20 ticks of gliding without a break. With a
 * chestplate in your off hand or hotbar, the elytra is swapped for it (using it, as a right-click equips armour)
 * while you glide, so the server stops the glide on its next tick; the client keeps gliding through that (see
 * {@link GlideHold}), and Grim doesn't notice. Every 8 ticks the elytra goes back on just long enough to start the
 * glide again (jump pressed, as vanilla starts one), and comes off again. The server never glides for more than a
 * tick in one go, so it never wears the elytra. Rockets you use meanwhile go out in those moments, when the server
 * takes them. Swaps only run with enough air below you, so the server never sees you land without the elytra. The
 * server's equip sound for each swap is muted. Eating (or anything you hold to use) pauses the swapping: Grim stops what
 * you're using at every swap, so the elytra goes back on at the next start, and swapping picks up again once you're
 * done.
 */
public class ElytraTweaks extends Module {
	private final BoolSetting rocketBoost = sgGeneral.bool("Rocket Boost")
		.description("While a rocket is attached, fly as fast along your look as Grim allows: full speed at once, and faster on diagonals.")
		.defaultValue(true).build();
	private final BoolSetting noDurability = sgGeneral.bool("No Durability")
		.description("Keep the elytra from wearing out while you glide, by swapping it with a chestplate held in your off hand or hotbar.")
		.defaultValue(true).build();

	// ---- Rocket Boost -----------------------------------------------------------------------------------------------

	/** Grim's per-axis allowance for a rocket, blocks a tick (both its scale and its cap), with a margin. */
	private static final double AMOUNT = 1.68;
	/** Grim's slack for a skipped tick, on each look's axes. */
	private static final double TICK_SKIP = 0.05;
	/** Ticks after a server teleport with vanilla's own flight, while Grim settles. */
	private static final int TELEPORT_PAUSE = 5;
	/** How far along the look to aim before clamping to the window (anything past the cap). */
	private static final double WANT = 10;

	/** Where the glide started last tick, to measure the movement Grim starts its prediction from. */
	private Vec3 lastGlidePos;
	private volatile boolean teleported;
	private int teleportPause;

	// ---- No Durability ----------------------------------------------------------------------------------------------

	/** Ticks between glide restarts: well under vanilla's 80-tick floating kick and the 20 ticks that wear the elytra. */
	private static final int INTERVAL = 8;
	/** Give up if the server hasn't stopped the glide this long after a swap (it refused it). */
	private static final int CLEAR_TIMEOUT = 40;
	/** Ticks of gliding with room below before the first swap. */
	private static final int ARM_TICKS = 10;

	private boolean engaged, startNow, rocketWanted, firing, warned;
	/** Something to eat or hold to use was used meanwhile: swapping stops at the next start, so it can go then. */
	private boolean stopForUse;
	private InteractionHand rocketHand = InteractionHand.MAIN_HAND;
	/** The hotbar slot a waiting rocket was used from (it may have been a silent swap, as Middle Click's are). */
	private int rocketSlot = -1;
	private int armTicks, sinceStart;

	public ElytraTweaks() {
		super(Categories.MOVEMENT, "Elytra Tweaks", "Faster rocket boosts and an elytra that doesn't wear out, Grim-safe.");
	}

	@Override
	protected void onEnable() {
		lastGlidePos = null;
		engaged = startNow = rocketWanted = stopForUse = false;
		armTicks = 0;
	}

	@Override
	protected void onDisable() {
		if (inGame()) finish();
		engaged = false;
		GlideHold.disarm(this);
	}

	/** Whether the elytra is being swapped (Auto Armor leaves the chest slot alone meanwhile). */
	public static boolean holdsChest() {
		ElytraTweaks m = Modules.active(ElytraTweaks.class);
		return m != null && m.engaged || ElytraFly.swapsChest();
	}

	/** Whether No Durability is on, for Elytra Fly's bounce to swap with a chestplate as well. */
	public static boolean noDurability() {
		ElytraTweaks m = Modules.active(ElytraTweaks.class);
		return m != null && m.noDurability.get();
	}

	/** Before any of this tick's actions: a restart sends held ping answers, which must come first (Grim's Post). */
	@Subscribe(priority = Priority.BEFORE_ACTIONS)
	private void onTickStart(TickEvent.Pre e) {
		startNow = false;
		if (!inGame()) {
			engaged = false;
			return;
		}
		LocalPlayer p = mc.player;
		if (teleportPause > 0) teleportPause--;
		// Rocket Boost: every movement packet carries a rotation while a rocket pushes, so Grim's look before is known.
		if (rocketBoost.get() && p.isFallFlying() && rocketAttached(p)) Myriad.rotations().sendRotationThisTick();
		tickNoDurability();
	}

	// ---- No Durability ----------------------------------------------------------------------------------------------

	private void tickNoDurability() {
		LocalPlayer p = mc.player;
		if (!noDurability.get()) {
			finish();
			return;
		}
		if (!engaged) {
			// Elytra Fly's bounce does its own swapping.
			// Not while you eat (or use anything held): a swap would stop it (see onUse).
			boolean ready = p.isFallFlying() && ChestSwap.elytraWorn() && !p.onGround() && !p.isInWater() && !p.isPassenger() && !p.isUsingItem()
				&& !ElytraFly.holdsGlide() && roomBelow(true);
			armTicks = ready ? armTicks + 1 : 0;
			if (armTicks < ARM_TICKS) return;
			if (ChestSwap.pair() == null) {
				if (!ChestSwap.fetchChestplate()) warnOnce("No Durability needs a chestplate in your inventory.");
				return;
			}
			if (!ChestSwap.ready()) {
				warnOnce("No Durability can't swap armour with Curse of Binding.");
				return;
			}
			if (!GlideHold.arm(this)) return;
			ChestSwap.swap();
			engaged = true;
			sinceStart = 0;
			return;
		}
		// Landing, water, or the ground close enough that the server could see you land without the elytra.
		if (!p.isFallFlying() || p.onGround() || p.isInWater() || !roomBelow(false)) {
			finish();
			return;
		}
		sinceStart++;
		boolean cleared = GlideHold.cleared(this);
		boolean due = sinceStart >= INTERVAL || rocketWanted || stopForUse || GlideHold.exposed(this);
		// Not two starts in a row (Grim's ElytraC).
		if (cleared && due && sinceStart >= 2) {
			// Something to eat is waiting: this start puts the elytra back for good, and the swapping stops.
			if (stopForUse) finish();
			else restart(true);
		} else if (!cleared && sinceStart > INTERVAL + CLEAR_TIMEOUT) {
			finish();
		}
	}

	/**
	 * Starts the glide again: the held ping answers go first (Grim sees the stop), the elytra goes on, the start is
	 * sent with jump pressed this tick and released the tick before (as vanilla starts one; Grim's ElytraB), a waiting
	 * rocket is used while the server takes it, and with {@code swapBack} the chestplate goes straight back on.
	 */
	private void restart(boolean swapBack) {
		GlideHold.release(this);
		if (!ChestSwap.startGlide()) {
			finish();
			return;
		}
		if (rocketWanted) fireRocket();
		startNow = true;
		sinceStart = 0;
		if (swapBack) ChestSwap.swap();
	}

	/** Stops swapping, with the elytra back on, gliding again if the server had stopped and you're still in the air. */
	private void finish() {
		if (!engaged) return;
		engaged = false;
		armTicks = 0;
		LocalPlayer p = mc.player;
		boolean cleared = GlideHold.cleared(this);
		if (cleared && p.isFallFlying() && !p.onGround() && sinceStart >= 2) {
			restart(false);
		} else {
			GlideHold.release(this);
			ChestSwap.restoreElytra();
			// The server had stopped the glide: so does the client, where you are.
			if (cleared && p.isFallFlying()) p.stopFallFlying();
		}
		GlideHold.disarm(this);
		rocketWanted = stopForUse = false;
	}

	/** While swapping: jump only as the press that starts the glide again (released the tick before). */
	@Subscribe(priority = Priority.LOWEST)
	private void onInput(InputEvent e) {
		if (engaged || startNow) e.jump = startNow;
	}

	/**
	 * A rocket used meanwhile waits for the next start: the server only attaches rockets while it sees you gliding.
	 * Something you hold to use (food, a potion, a bow) waits for the swapping to stop: Grim stops whatever you're using
	 * each time you use another item (each swap) or change slot, so you'd never finish eating. It's used once the elytra
	 * is back on for good, at the next start (Auto Eat tries again, and holding right click uses it again).
	 */
	@Subscribe
	private void onUse(InteractEvent.Item e) {
		if (!engaged || firing) return;
		ItemStack stack = mc.player.getItemInHand(e.hand());
		if (stack.is(Items.FIREWORK_ROCKET)) {
			e.cancel();
			rocketWanted = true;
			rocketHand = e.hand();
			rocketSlot = e.hand() == InteractionHand.MAIN_HAND ? Myriad.inventory().serverSlot() : -1;
		} else if (stack.getUseDuration(mc.player) > 0) {
			e.cancel();
			stopForUse = true;
		}
	}

	/** Uses the waiting rocket, from the hand or hotbar slot it was used from (or any rockets in the hotbar). */
	private void fireRocket() {
		rocketWanted = false;
		var inv = mc.player.getInventory();
		int slot = rocketSlot;
		if (rocketHand == InteractionHand.MAIN_HAND && (slot < 0 || !inv.getItem(slot).is(Items.FIREWORK_ROCKET))) slot = Myriad.inventory().findInHotbar(s -> s.is(Items.FIREWORK_ROCKET));
		boolean offHand = rocketHand == InteractionHand.OFF_HAND && mc.player.getOffhandItem().is(Items.FIREWORK_ROCKET);
		if (!offHand && slot < 0) return;
		firing = true;
		try {
			if (offHand) mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
			else Myriad.inventory().silentSwap(slot, () -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
		} finally {
			firing = false;
		}
	}

	private void warnOnce(String message) {
		if (!warned) warn(message);
		warned = true;
	}

	/**
	 * Whether there's enough air below that the server won't see you land while it isn't gliding you: a few blocks,
	 * plus how far you fall (or rise) in a round trip.
	 */
	private boolean roomBelow(boolean arming) {
		LocalPlayer p = mc.player;
		double vy = p.getDeltaMovement().y;
		int roundTrip = Mth.clamp(Myriad.server().ping() / 50 + 2, 2, 12);
		double need = 6 + (vy < 0 ? -vy * (roundTrip + 4) : vy * (roundTrip / 2.0 + 2)) + (arming ? 2 : 0);
		return mc.level.noBlockCollision(p, p.getBoundingBox().expandTowards(0, -need, 0));
	}

	// ---- Rocket Boost -----------------------------------------------------------------------------------------------

	@Subscribe
	private void onReceive(PacketEvent.Receive e) {
		if (e.packet() instanceof ClientboundPlayerPositionPacket) teleported = true;
	}

	/**
	 * The movement the local player's glide should make this tick, given vanilla's: unchanged unless Rocket Boost is on,
	 * you're gliding with a rocket attached, and no module is turning you server-side (the window is built from the
	 * rotation the server gets, so it has to be yours).
	 */
	public static Vec3 glideMovement(Vec3 vanilla) {
		ElytraTweaks m = Modules.active(ElytraTweaks.class);
		if (m == null || !m.rocketBoost.get()) return vanilla;
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || !p.isFallFlying()) {
			if (m != null) m.lastGlidePos = null;
			return vanilla;
		}
		Vec3 pos = p.position(), last = m.lastGlidePos;
		m.lastGlidePos = pos;
		if (m.teleported) {
			m.teleported = false;
			m.teleportPause = TELEPORT_PAUSE;
		}
		if (last == null || m.teleportPause > 0 || p.hurtTime > 0 || Myriad.rotations().isRotating() || !rocketAttached(p)) return vanilla;
		// Grim starts from the movement it saw last tick.
		Vec3 start = pos.subtract(last);
		if (start.lengthSqr() > 40 * 40) return vanilla;
		Vec3 look = MathUtil.direction(p.getYRot(), p.getXRot());
		Vec3 before = MathUtil.direction(Myriad.rotations().serverYaw(), Myriad.rotations().serverPitch());
		Vec3 predicted = glide(p, start, look, p.getXRot());
		Vec3 boosted = new Vec3(
			axis(look.x, before.x, start.x, predicted.x),
			axis(look.y, before.y, start.y, predicted.y),
			axis(look.z, before.z, start.z, predicted.z));
		// Vanilla's push is always inside the window; only take over where this is faster along the look.
		return boosted.dot(look) > vanilla.dot(look) ? boosted : vanilla;
	}

	/**
	 * One axis: as far along your look as Grim's window allows. The window is the predicted glide, widened by however
	 * far the rocket box (from the current and previous looks) reaches past last tick's movement.
	 */
	private static double axis(double look, double before, double start, double predicted) {
		double lo = Math.max(-AMOUNT, (Math.min(-TICK_SKIP, look) + Math.min(-TICK_SKIP, before)) * AMOUNT);
		double hi = Math.min(AMOUNT, (Math.max(TICK_SKIP, look) + Math.max(TICK_SKIP, before)) * AMOUNT);
		return Math.clamp(look * WANT, predicted + Math.min(0, lo - start), predicted + Math.max(0, hi - start));
	}

	/** Vanilla's (and Grim's) glide from {@code velocity} with {@code look}, before any rocket, friction included. */
	private static Vec3 glide(LocalPlayer p, Vec3 velocity, Vec3 look, float pitchDegrees) {
		double gravity = p.getAttributeValue(Attributes.GRAVITY);
		if (velocity.y <= 0 && p.hasEffect(MobEffects.SLOW_FALLING)) gravity = Math.min(gravity, 0.01);
		float pitch = pitchDegrees * Mth.DEG_TO_RAD;
		double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
		double speed = velocity.horizontalDistance();
		double lift = Math.cos(pitch);
		lift = lift * lift * Math.min(1, look.length() / 0.4);
		Vec3 v = velocity.add(0, gravity * (-1 + lift * 0.75), 0);
		if (v.y < 0 && lookHorizontal > 0) {
			double d = v.y * -0.1 * lift;
			v = v.add(look.x * d / lookHorizontal, d, look.z * d / lookHorizontal);
		}
		if (pitch < 0 && lookHorizontal > 0) {
			double d = speed * -Mth.sin(pitch) * 0.04;
			v = v.add(-look.x * d / lookHorizontal, d * 3.2, -look.z * d / lookHorizontal);
		}
		if (lookHorizontal > 0) v = v.add((look.x / lookHorizontal * speed - v.x) * 0.1, 0, (look.z / lookHorizontal * speed - v.z) * 0.1);
		return v.multiply(0.99f, 0.98f, 0.99f);
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
