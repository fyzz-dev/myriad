package dev.myriad.essentials.modules.combat;

import dev.myriad.api.Myriad;
import dev.myriad.api.combat.TargetSettings;
import dev.myriad.api.combat.Targets;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.Renderer3D;
import dev.myriad.api.service.Rotations;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.api.util.ColorUtil;
import dev.myriad.api.util.Entities;
import dev.myriad.api.util.Interactions;
import dev.myriad.api.util.Reach;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Attacks the closest valid target in range. It can switch to your best weapon for the hit (and back), wait for
 * that weapon's attack cooldown, aim server-side, and use a mace for a smash when you're falling. Pulling turns
 * you away right after a sword hit, which drags the target toward you on some servers.
 */
public class KillAura extends Module {
	private static final Item[] WEAPONS = {
		Items.NETHERITE_SWORD, Items.MACE, Items.DIAMOND_SWORD, Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.TRIDENT,
		Items.IRON_SWORD, Items.STONE_SWORD, Items.IRON_AXE, Items.STONE_AXE, Items.GOLDEN_SWORD, Items.WOODEN_SWORD,
		Items.GOLDEN_AXE, Items.WOODEN_AXE
	};

	private final DoubleSetting range = sgGeneral.doubleSetting("Range").defaultValue(4).range(0, 6).decimals(1).build();
	private final DoubleSetting maceRange = sgGeneral.doubleSetting("Mace Range").description("Range for mace smashes.").defaultValue(4).range(0, 6).decimals(1).build();
	private final DoubleSetting delay = sgGeneral.doubleSetting("Delay").description("Attack charge needed before hitting (1 = full).").defaultValue(1).range(0.1, 1).decimals(2).build();
	private final BoolSetting tpsSync = sgGeneral.bool("TPS Sync").description("Allow for server lag when timing hits.").defaultValue(true).build();
	private final BoolSetting rotate = sgGeneral.bool("Rotate").description("Face the target server-side.").defaultValue(true).build();
	private final BoolSetting rotateHold = sgGeneral.bool("Rotate Hold").description("Keep facing the target between hits.").visible(rotate::get).build();
	private final BoolSetting swing = sgGeneral.bool("Swing").description("Show your hand swing (otherwise only the server sees it).").defaultValue(true).build();
	private final BoolSetting pulling = sgGeneral.bool("Pulling").description("Turn away right after a sword hit to pull the target.").build();
	private final BoolSetting autoSwap = sgGeneral.bool("Auto Swap").description("Switch to your best weapon for the hit, then back.").defaultValue(true).build();
	private final BoolSetting swapAwait = sgGeneral.bool("Swap Await").description("Time hits by the swapped-in weapon's cooldown.").defaultValue(true).visible(autoSwap::get).build();
	private final BoolSetting requireWeapon = sgGeneral.bool("Require Weapon").description("Only attack while holding a weapon.").visible(() -> !autoSwap.get()).build();
	private final BoolSetting pauseWhileUsing = sgGeneral.bool("Pause While Using").description("Don't attack while eating or using an item.").defaultValue(true).build();
	private final DoubleSetting maceFallStart = sgGeneral.doubleSetting("Mace Fall Start").description("Hold the mace from this fall distance, ready to smash.").defaultValue(1).range(0, 1.5).decimals(1).build();

	// The standard targets group, shared with every combat module (same names, so saved settings carry over).
	private final TargetSettings targets = new TargetSettings(settings, Targets.Type.PLAYERS);

	private final SettingGroup sgRender = settings.group("Render");
	private final BoolSetting render = sgRender.bool("Render").description("Box the current target, and fade out boxes on recent hits.").defaultValue(true).build();
	private final BoolSetting onSwing = sgRender.bool("On Swing").description("Only box the target when you hit it.").build();
	private final ColorSetting fill = sgRender.color("Fill").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT, 30)).build();
	private final ColorSetting line = sgRender.color("Line").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT)).build();
	private final DoubleSetting lineWidth = sgRender.doubleSetting("Line Width").defaultValue(1.5).range(0.5, 5).decimals(1).build();
	private final IntSetting fadeTime = sgRender.intSetting("Fade Time").description("Milliseconds hit boxes take to fade.").defaultValue(300).range(0, 1000).build();

	private Entity target, renderTarget;
	private int weaponSlot = -1;
	private boolean attackThisTick;
	private final List<Hit> hits = new ArrayList<>();

	private record Hit(Entity entity, long time) {
	}

	public KillAura() {
		super(Categories.COMBAT, "Kill Aura", "Attacks entities around you.");
	}

	@Override
	public String hudInfo() {
		return target == null ? null : String.format("%s %.1f", target.getName().getString(), mc.player == null ? 0 : target.distanceTo(mc.player));
	}

	@Override
	protected void onDisable() {
		target = renderTarget = null;
		hits.clear();
	}

	/** Choose a target and weapon, and request the rotation before this tick's movement packet goes out. */
	@Subscribe
	private void onPreTick(TickEvent.Pre e) {
		attackThisTick = false;
		if (!inGame() || (pauseWhileUsing.get() && mc.player.isUsingItem())) {
			target = null;
			return;
		}
		if (!autoSwap.get() && requireWeapon.get() && !isWeapon(mc.player.getMainHandStack())) {
			target = null;
			return;
		}
		weaponSlot = autoSwap.get() ? chooseWeapon() : mc.player.getInventory().selectedSlot;
		target = findTarget();
		if (target == null) {
			if (renderTarget != null && !renderTarget.isAlive()) renderTarget = null;
			return;
		}
		if (render.get() && !onSwing.get()) renderTarget = target;
		attackThisTick = canAttack();
		if (rotate.get() && (attackThisTick || rotateHold.get())) Myriad.rotations().lookAt(this, Reach.aimPoint(target), Rotations.PRIORITY_HIGH);
	}

	/** Attack after the movement packet, so the server already has our rotation. */
	@Subscribe
	private void onPostTick(TickEvent.Post e) {
		if (!attackThisTick || target == null || !inGame() || !target.isAlive()) return;
		attackThisTick = false;
		int current = mc.player.getInventory().selectedSlot;
		if (autoSwap.get() && weaponSlot >= 0 && weaponSlot != current) Myriad.inventory().silentSwap(weaponSlot, () -> attack(target));
		else attack(target);
	}

	private void attack(Entity t) {
		var net = mc.getNetworkHandler();
		boolean sprinting = mc.player.isSprinting() && !mc.player.isGliding();
		boolean pull = pulling.get() && mc.player.getMainHandStack().isIn(ItemTags.SWORDS);
		if (sprinting) net.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
		mc.interactionManager.attackEntity(mc.player, t);
		if (pull) {
			float[] r = Myriad.rotations().anglesTo(Reach.aimPoint(t));
			net.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(MathHelper.wrapDegrees(r[0] + 180), r[1], mc.player.isOnGround(), mc.player.horizontalCollision));
		}
		if (sprinting) net.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
		if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
		else net.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
		hits.add(new Hit(t, System.currentTimeMillis()));
		if (render.get() && onSwing.get()) renderTarget = t;
	}

	// ---- targets ----------------------------------------------------------------------------------------------------

	private Entity findTarget() {
		return targets.query().range(attackRange()).best();
	}

	// ---- weapons and timing -----------------------------------------------------------------------------------------

	private static boolean isWeapon(ItemStack s) {
		return s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES) || s.isOf(Items.TRIDENT) || s.isOf(Items.MACE);
	}

	private boolean maceReady() {
		var p = mc.player;
		return !p.isOnGround() && p.getVelocity().y < 0 && p.fallDistance >= maceFallStart.get() && !p.isTouchingWater() && !p.isInLava() && !p.isHoldingOntoLadder();
	}

	private boolean canSmash() {
		return mc.player.fallDistance > 1.5f && maceReady();
	}

	private int chooseWeapon() {
		if (maceReady()) {
			int mace = Myriad.inventory().findInHotbar(s -> s.isOf(Items.MACE));
			if (mace >= 0) return mace;
		}
		for (Item item : WEAPONS) {
			if (item == Items.MACE) continue;
			int slot = Myriad.inventory().findInHotbar(s -> s.isOf(item));
			if (slot >= 0) return slot;
		}
		return mc.player.getInventory().selectedSlot;
	}

	private double attackRange() {
		return weaponSlot >= 0 && mc.player.getInventory().getStack(weaponSlot).isOf(Items.MACE) ? maceRange.get() : range.get();
	}

	private boolean canAttack() {
		ItemStack weapon = weaponSlot >= 0 ? mc.player.getInventory().getStack(weaponSlot) : mc.player.getMainHandStack();
		if (weapon.isOf(Items.MACE) && canSmash()) return true;
		if (weapon.isOf(Items.MACE) && maceReady()) return false;
		float base = 0.5f;
		if (tpsSync.get()) base -= 20f - Myriad.server().tps();
		float progress = autoSwap.get() && swapAwait.get() ? cooldown(weapon, base) : mc.player.getAttackCooldownProgress(base);
		return progress >= delay.get();
	}

	/** Attack charge as if {@code weapon} were in hand (each weapon has its own attack speed). */
	private float cooldown(ItemStack weapon, float base) {
		double[] speed = {4.0};
		weapon.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute.equals(EntityAttributes.ATTACK_SPEED) && modifier.operation() == EntityAttributeModifier.Operation.ADD_VALUE) speed[0] += modifier.value();
		});
		float ticksPerAttack = (float) (20.0 / speed[0]);
		int last = Interactions.ticksSinceAttack();
		return MathHelper.clamp((last + base) / ticksPerAttack, 0f, 1f);
	}

	// ---- render -----------------------------------------------------------------------------------------------------

	@Subscribe
	private void onRender(Render3DEvent e) {
		if (!render.get() || !inGame()) return;
		Renderer3D.lineWidth(lineWidth.getFloat());
		if (renderTarget != null && renderTarget.isAlive() && (target != null || onSwing.get())) {
			Renderer3D.box(Entities.lerpedBox(renderTarget, e.tickDelta()), fill.argb(), line.argb(), Renderer3D.ShapeMode.BOTH, true);
		}
		long now = System.currentTimeMillis();
		hits.removeIf(h -> now - h.time > fadeTime.get() || !h.entity.isAlive());
		for (Hit h : hits) {
			if (h.entity == renderTarget) continue;
			float a = (float) Math.pow(1 - Math.min(1, (now - h.time) / (float) Math.max(1, fadeTime.get())), 2);
			int f = fill.argb(), l = line.argb();
			Renderer3D.box(Entities.lerpedBox(h.entity, e.tickDelta()), ColorUtil.withAlpha(f, (int) (ColorUtil.alpha(f) * a)), ColorUtil.withAlpha(l, (int) (ColorUtil.alpha(l) * a)),
				Renderer3D.ShapeMode.BOTH, true);
		}
	}
}
