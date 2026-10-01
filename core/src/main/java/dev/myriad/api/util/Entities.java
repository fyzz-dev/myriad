package dev.myriad.api.util;

import dev.myriad.api.Myriad;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Entity helpers for combat and render features: classifying entities the same way everywhere (so "hostiles" means
 * the same thing in every addon), friend checks, and render-time interpolation.
 */
public final class Entities {
	private static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	private Entities() {
	}

	public enum Kind {
		PLAYER, HOSTILE, PASSIVE, OTHER
	}

	/** Players; hostiles (anything vanilla treats as a monster, including phantoms, slimes, ghasts and shulkers); passives; the rest. */
	public static Kind kind(Entity e) {
		if (e instanceof PlayerEntity) return Kind.PLAYER;
		SpawnGroup g = e.getType().getSpawnGroup();
		if (g == SpawnGroup.MONSTER || e instanceof Monster) return Kind.HOSTILE;
		if (g == SpawnGroup.CREATURE || g == SpawnGroup.AMBIENT || g == SpawnGroup.WATER_CREATURE
			|| g == SpawnGroup.WATER_AMBIENT || g == SpawnGroup.UNDERGROUND_WATER_CREATURE || g == SpawnGroup.AXOLOTLS) return Kind.PASSIVE;
		return Kind.OTHER;
	}

	public static boolean isHostile(Entity e) {
		return kind(e) == Kind.HOSTILE;
	}

	public static boolean isPassive(Entity e) {
		return kind(e) == Kind.PASSIVE;
	}

	/** A player on the user's friends list. */
	public static boolean isFriend(Entity e) {
		return e instanceof PlayerEntity p && Myriad.friends().isFriend(p);
	}

	/** Alive, not dying, and not the local player. */
	public static boolean isAliveTarget(Entity e) {
		return e != mc().player && e.isAlive() && !(e instanceof LivingEntity l && l.isDead());
	}

	/** The entity's bounding box at render time (interpolated between ticks). */
	public static Box lerpedBox(Entity e, float tickDelta) {
		double x = MathHelper.lerp(tickDelta, e.lastRenderX, e.getX()) - e.getX();
		double y = MathHelper.lerp(tickDelta, e.lastRenderY, e.getY()) - e.getY();
		double z = MathHelper.lerp(tickDelta, e.lastRenderZ, e.getZ()) - e.getZ();
		return e.getBoundingBox().offset(x, y, z);
	}

	public static Vec3d lerpedCenter(Entity e, float tickDelta) {
		return lerpedBox(e, tickDelta).getCenter();
	}
}
