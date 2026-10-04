package dev.myriad.essentials.modules.combat;

import dev.myriad.api.Myriad;
import dev.myriad.api.combat.TargetSettings;
import dev.myriad.api.combat.Targets;
import dev.myriad.api.event.Priority;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.MovementPacketsEvent;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.Renderer3D;
import dev.myriad.api.service.Rotations;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.util.ColorUtil;
import dev.myriad.api.util.Entities;
import dev.myriad.api.util.Interactions;
import dev.myriad.api.util.ItemInfo;
import dev.myriad.api.util.MathUtil;
import dev.myriad.api.util.Reach;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Attacks the best target in reach, the way Grim (2b2t) checks hits: it turns to face the target first (walking along
 * that yaw meanwhile, so movement still matches), and only hits once the rotation the server already has puts the
 * target under the crosshair within your entity reach, measured along the look as Grim does. Hits wait for a full
 * attack charge, go out before the tick's movement as a click would, and are followed by the swing, in vanilla's order.
 * <p>
 * It pauses while you use an item (eating, blocking, drawing a bow), fly with an elytra (turning would steer you) or
 * have a container open, as vanilla can't attack then either.
 */
public class KillAura extends Module {
	public enum Weapon {
		/** Only attack while holding a weapon. */
		HOLDING,
		/** Switch to the best weapon in the hotbar. */
		SWITCH,
		/** Hit with whatever is in your hand. */
		ANYTHING
	}

	private final EnumSetting<Weapon> weapon = sgGeneral.enumSetting("Weapon", Weapon.HOLDING)
		.description("Holding: only attack while you hold a weapon. Switch: switch to your best weapon. Anything: hit with whatever you hold.").build();
	private final IntSetting turnSpeed = sgGeneral.intSetting("Turn Speed")
		.description("Most degrees to turn per tick towards a target; 0 turns at once (fine on Grim, some anti-cheats want it limited).")
		.defaultValue(0).range(0, 180).build();
	private final BoolSetting render = sgGeneral.bool("Render").description("Outline the target.").defaultValue(true).build();
	private final ColorSetting color = sgGeneral.color("Color").defaultValue(SettingColor.role(SettingColor.Mode.RED)).visible(render::get).build();

	private final TargetSettings targets = new TargetSettings(settings, Targets.Type.PLAYERS, Targets.Type.HOSTILES);

	private Entity target;
	/** A weapon was switched in this tick. */
	private boolean switched;

	public KillAura() {
		super(Categories.COMBAT, "Kill Aura", "Attacks targets in reach, facing them first.");
	}

	@Override
	protected void onDisable() {
		target = null;
	}

	@Override
	public String hudInfo() {
		return target == null ? null : target.getName().getString();
	}

	/** Before the movement packet, so the hit goes out first (like a click) and this tick's turn goes out with it. */
	@Subscribe
	private void onTick(TickEvent.Pre e) {
		target = null;
		if (!inGame() || paused()) return;
		double range = Reach.entityRange();
		Entity t = targets.best(range);
		if (t == null || !armed()) return;
		target = t;

		// The server judges the hit by the rotation it already has: hit only once that lands on the target. The charge
		// counts for the item you held last tick (switching resets it), so not the tick a weapon is switched in.
		if (!switched && Interactions.attackCharge() >= 1 && lands(t, range)) Interactions.attack(t, true);

		// Aimed from where your eyes will be once you've moved this tick: the server judges the hit from there.
		Vec3 eyes = mc.player.getEyePosition().add(mc.player.getDeltaMovement());
		float[] r = MathUtil.anglesTo(eyes, aimPoint(t, eyes));
		Myriad.rotations().request(this, r[0], r[1], Rotations.PRIORITY_NORMAL, options(), null);
	}

	/**
	 * Just before the rotation goes out, now that you've moved: the pitch is aimed again from where your eyes really are.
	 * The yaw stays (your walking already follows it, which Grim checks).
	 */
	@Subscribe(priority = Priority.HIGH)
	private void onBeforeRotationSent(MovementPacketsEvent e) {
		if (target == null || !inGame()) return;
		Vec3 eyes = mc.player.getEyePosition();
		float[] r = MathUtil.anglesTo(eyes, aimPoint(target, eyes));
		Myriad.rotations().request(this, Float.NaN, r[1], Rotations.PRIORITY_NORMAL + 1, options(), null);
	}

	private Rotations.Options options() {
		return new Rotations.Options(turnSpeed.get(), true);
	}

	private boolean paused() {
		return mc.player.isSpectator() || mc.player.isFallFlying() || mc.player.isUsingItem() || mc.player.containerMenu != mc.player.inventoryMenu;
	}

	/** Whether the hand is ready to fight, switching to the best weapon in Switch mode. */
	private boolean armed() {
		switched = false;
		return switch (weapon.get()) {
			case ANYTHING -> true;
			case HOLDING -> weaponScore(mc.player.getMainHandItem()) > 0;
			case SWITCH -> {
				int best = Myriad.inventory().bestInHotbar(this::weaponScore);
				if (best >= 0 && best != mc.player.getInventory().getSelectedSlot()
					&& weaponScore(mc.player.getInventory().getItem(best)) > weaponScore(mc.player.getMainHandItem())) {
					// Switching resets the attack charge, so the hit comes once it's full again.
					Myriad.inventory().select(best);
					switched = true;
				}
				yield true;
			}
		};
	}

	/**
	 * Whether looking along the rotation the server has from your eyes meets the target's hitbox within reach: Grim's
	 * Reach and Hitboxes checks.
	 */
	private boolean lands(Entity t, double range) {
		Vec3 eyes = mc.player.getEyePosition();
		AABB box = t.getBoundingBox();
		if (box.contains(eyes)) return true;
		Vec3 look = MathUtil.direction(Myriad.rotations().serverYaw(), Myriad.rotations().serverPitch());
		return box.clip(eyes, eyes.add(look.scale(range))).isPresent();
	}

	/**
	 * The point of the target's hitbox nearest {@code eyes}, kept well inside the box, so the look meets it at nearly the
	 * shortest reach without grazing the edge (you and the target move between the look and the hit).
	 */
	private static Vec3 aimPoint(Entity t, Vec3 eyes) {
		AABB box = t.getBoundingBox();
		double ix = box.getXsize() * 0.3, iy = Math.min(0.3, box.getYsize() * 0.3), iz = box.getZsize() * 0.3;
		return MathUtil.closestPoint(box.deflate(ix, iy, iz), eyes);
	}

	/** Damage per second at full charge (with Sharpness); 0 for things that aren't weapons or are about to break. */
	private double weaponScore(ItemStack s) {
		if (s.isEmpty() || s.isDamageableItem() && ItemInfo.durabilityFraction(s) < 0.03) return 0;
		ItemAttributeModifiers mods = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double damage = mods.compute(Attributes.ATTACK_DAMAGE, 1, EquipmentSlot.MAINHAND);
		if (damage <= 1) return 0;
		int sharpness = ItemInfo.enchantmentLevel(s, Enchantments.SHARPNESS);
		if (sharpness > 0) damage += 0.5 * sharpness + 0.5;
		return damage * mods.compute(Attributes.ATTACK_SPEED, 4, EquipmentSlot.MAINHAND);
	}

	@Subscribe
	private void onRender(Render3DEvent e) {
		if (!render.get() || target == null || !inGame()) return;
		int c = color.argb();
		Renderer3D.box(Entities.lerpedBox(target, e.tickDelta()), ColorUtil.withAlpha(c, 40), c, Renderer3D.ShapeMode.BOTH, false);
	}
}
