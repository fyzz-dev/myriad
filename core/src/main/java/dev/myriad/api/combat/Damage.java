package dev.myriad.api.combat;

import dev.myriad.api.util.Entities;
import dev.myriad.api.util.ItemInfo;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.Difficulty;
import net.minecraft.world.explosion.ExplosionImpl;

import java.util.function.Predicate;

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
		return explosion(target, target.getPos(), target.getBoundingBox(), center, power, NO_OVERRIDES);
	}

	/**
	 * The full form: the target standing at {@code targetPos} with box {@code targetBox} (pass a predicted position
	 * from {@code Entities.predict} to aim where a player is going), and {@code asAir} naming blocks to treat as gone
	 * when working out how much of the blast reaches them ("what if I break this surround block first?").
	 */
	public static float explosion(LivingEntity target, Vec3d targetPos, Box targetBox, Vec3d center, float power, Predicate<BlockPos> asAir) {
		double radius = power * 2.0;
		double distance = targetPos.distanceTo(center);
		if (distance > radius) return 0;
		float exposure = asAir == NO_OVERRIDES && targetBox.equals(target.getBoundingBox())
			? ExplosionImpl.calculateReceivedDamage(center, target)
			: exposure(center, targetBox, asAir);
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

	/** Crystal damage against where {@code target} will be in {@code ticks} ticks. */
	public static float crystal(LivingEntity target, Vec3d crystal, int predictTicks, Predicate<BlockPos> asAir) {
		return explosion(target, Entities.predict(target, predictTicks), Entities.predictBox(target, predictTicks), crystal, 6, asAir);
	}

	/** A bed exploding at {@code bed} (in the Nether or End). */
	public static float bed(LivingEntity target, BlockPos bed) {
		return explosion(target, Vec3d.ofCenter(bed), 5);
	}

	/** A respawn anchor exploding at {@code anchor} (outside the Nether). */
	public static float anchor(LivingEntity target, BlockPos anchor) {
		return explosion(target, Vec3d.ofCenter(anchor), 5);
	}

	private static final Predicate<BlockPos> NO_OVERRIDES = p -> false;

	/** Vanilla's exposure: the share of sample points on the box with a clear line to the blast, skipping {@code asAir}. */
	private static float exposure(Vec3d source, Box box, Predicate<BlockPos> asAir) {
		double dx = 1.0 / ((box.maxX - box.minX) * 2 + 1), dy = 1.0 / ((box.maxY - box.minY) * 2 + 1), dz = 1.0 / ((box.maxZ - box.minZ) * 2 + 1);
		if (dx < 0 || dy < 0 || dz < 0 || mc().world == null) return 0;
		double ox = (1 - Math.floor(1 / dx) * dx) / 2, oz = (1 - Math.floor(1 / dz) * dz) / 2;
		int clear = 0, total = 0;
		for (double x = 0; x <= 1; x += dx) {
			for (double y = 0; y <= 1; y += dy) {
				for (double z = 0; z <= 1; z += dz) {
					Vec3d point = new Vec3d(MathHelper.lerp(x, box.minX, box.maxX) + ox, MathHelper.lerp(y, box.minY, box.maxY), MathHelper.lerp(z, box.minZ, box.maxZ) + oz);
					if (!blocked(point, source, asAir)) clear++;
					total++;
				}
			}
		}
		return total == 0 ? 0 : clear / (float) total;
	}

	private static boolean blocked(Vec3d from, Vec3d to, Predicate<BlockPos> asAir) {
		var world = mc().world;
		BlockHitResult hit = BlockView.raycast(from, to, null, (ctx, pos) -> {
			if (asAir.test(pos)) return null;
			BlockState state = world.getBlockState(pos);
			return world.raycastBlock(from, to, pos, state.getCollisionShape(world, pos), state);
		}, ctx -> null);
		return hit != null;
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
			protection += ItemInfo.enchantmentLevel(s, Enchantments.PROTECTION);
			if (blast) protection += ItemInfo.enchantmentLevel(s, Enchantments.BLAST_PROTECTION) * 2;
		}
		return DamageUtil.getInflictedDamage(damage, Math.min(protection, 20));
	}

	/** Fall damage for falling {@code distance} blocks, after armour. */
	public static float fall(LivingEntity target, float distance) {
		var effect = target.getStatusEffect(StatusEffects.JUMP_BOOST);
		float safe = (float) target.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE) + (effect == null ? 0 : effect.getAmplifier() + 1);
		float damage = (float) Math.ceil((distance - safe) * target.getAttributeValue(EntityAttributes.FALL_DAMAGE_MULTIPLIER));
		if (damage <= 0) return 0;
		int featherFalling = ItemInfo.enchantmentLevel(target.getEquippedStack(EquipmentSlot.FEET), Enchantments.FEATHER_FALLING);
		int protection = 0;
		for (EquipmentSlot slot : ARMOR) protection += ItemInfo.enchantmentLevel(target.getEquippedStack(slot), Enchantments.PROTECTION);
		return DamageUtil.getInflictedDamage(damage, Math.min(protection + featherFalling * 3, 20));
	}

	/** A rough melee hit from {@code attacker} right now (charge, sharpness, crits). */
	public static float melee(PlayerEntity attacker, LivingEntity target) {
		float base = (float) attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
		int sharpness = ItemInfo.enchantmentLevel(attacker.getMainHandStack(), Enchantments.SHARPNESS);
		float bonus = sharpness > 0 ? 1 + 0.5f * (sharpness - 1) : 0;
		float charge = attacker.getAttackCooldownProgress(0.5f);
		base *= 0.2f + charge * charge * 0.8f;
		bonus *= charge;
		if (charge > 0.9f && attacker.fallDistance > 0 && !attacker.isOnGround() && !attacker.isClimbing() && !attacker.isTouchingWater()
			&& !attacker.hasStatusEffect(StatusEffects.BLINDNESS) && !attacker.hasVehicle()) base *= 1.5f;
		return reduce(target, base + bonus, false);
	}
}
