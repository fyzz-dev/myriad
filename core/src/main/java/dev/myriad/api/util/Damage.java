package dev.myriad.api.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.explosion.ExplosionImpl;

/** Client-side estimates of damage the server would deal, for predicting lethal hits. */
public final class Damage {
	private static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private Damage() {
	}

	/** Damage an explosion of {@code power} at {@code center} would deal to {@code target} after armour and effects. */
	public static float explosion(LivingEntity target, Vec3d center, float power) {
		double distance = Math.sqrt(target.squaredDistanceTo(center));
		double radius = power * 2.0;
		if (distance > radius) return 0;
		float exposure = ExplosionImpl.calculateReceivedDamage(center, target);
		double impact = (1.0 - distance / radius) * exposure;
		float damage = (float) ((impact * impact + impact) / 2.0 * 7.0 * radius + 1.0);
		if (target instanceof PlayerEntity && mc().world != null) {
			Difficulty difficulty = mc().world.getDifficulty();
			damage = switch (difficulty) {
				case PEACEFUL -> 0;
				case EASY -> Math.min(damage / 2 + 1, damage);
				case NORMAL -> damage;
				case HARD -> damage * 1.5f;
			};
		}
		return reduce(target, damage, true);
	}

	public static float crystal(LivingEntity target, Vec3d crystal) {
		return explosion(target, crystal, 6);
	}

	/** Applies armour, toughness, resistance and protection enchantments. */
	public static float reduce(LivingEntity target, float damage, boolean blast) {
		var source = mc().world.getDamageSources().generic();
		damage = DamageUtil.getDamageLeft(target, damage, source, target.getArmor(), (float) target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS));
		StatusEffectInstance resistance = target.getStatusEffect(StatusEffects.RESISTANCE);
		if (resistance != null) damage = Math.max(damage * (1 - 0.2f * (resistance.getAmplifier() + 1)), 0);
		int protection = 0;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack s = target.getEquippedStack(slot);
			protection += level(s, Enchantments.PROTECTION);
			if (blast) protection += level(s, Enchantments.BLAST_PROTECTION) * 2;
		}
		return DamageUtil.getInflictedDamage(damage, Math.min(protection, 20));
	}

	/** Fall damage for falling {@code distance} blocks, after armour. */
	public static float fall(LivingEntity target, float distance) {
		var effect = target.getStatusEffect(StatusEffects.JUMP_BOOST);
		float safe = (float) target.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE) + (effect == null ? 0 : effect.getAmplifier() + 1);
		float damage = (float) Math.ceil((distance - safe) * target.getAttributeValue(EntityAttributes.FALL_DAMAGE_MULTIPLIER));
		if (damage <= 0) return 0;
		int featherFalling = level(target.getEquippedStack(EquipmentSlot.FEET), Enchantments.FEATHER_FALLING);
		int protection = 0;
		for (EquipmentSlot slot : ARMOR) protection += level(target.getEquippedStack(slot), Enchantments.PROTECTION);
		return DamageUtil.getInflictedDamage(damage, Math.min(protection + featherFalling * 3, 20));
	}

	/** A rough melee hit from {@code attacker} right now (charge, sharpness, crits). */
	public static float melee(PlayerEntity attacker, LivingEntity target) {
		float base = (float) attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
		int sharpness = level(attacker.getMainHandStack(), Enchantments.SHARPNESS);
		float bonus = sharpness > 0 ? 1 + 0.5f * (sharpness - 1) : 0;
		float charge = attacker.getAttackCooldownProgress(0.5f);
		base *= 0.2f + charge * charge * 0.8f;
		bonus *= charge;
		if (charge > 0.9f && attacker.fallDistance > 0 && !attacker.isOnGround() && !attacker.isClimbing() && !attacker.isTouchingWater()
			&& !attacker.hasStatusEffect(StatusEffects.BLINDNESS) && !attacker.hasVehicle()) base *= 1.5f;
		return reduce(target, base + bonus, false);
	}

	public static int level(ItemStack stack, RegistryKey<Enchantment> key) {
		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : stack.getEnchantments().getEnchantmentEntries()) {
			if (e.getKey().matchesKey(key)) return e.getIntValue();
		}
		return 0;
	}
}
