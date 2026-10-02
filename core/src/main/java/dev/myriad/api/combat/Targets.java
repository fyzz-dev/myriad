package dev.myriad.api.combat;

import dev.myriad.api.util.Entities;
import dev.myriad.api.util.MathUtil;
import dev.myriad.api.util.Reach;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finding entities to attack, aim at or highlight, with the same rules everywhere: what counts as a hostile, how
 * neutral mobs are treated, friends, invisibility, walls, and sorting. Most modules should use {@link TargetSettings},
 * which gives players the standard options and builds the query for you.
 *
 * <pre>{@code
 * Entity target = Targets.query().range(4.5).types(Targets.Type.PLAYERS, Targets.Type.HOSTILES).sort(Targets.Sort.HEALTH).best();
 * }</pre>
 */
public final class Targets {
	public enum Type {
		PLAYERS, HOSTILES, ANIMALS, VILLAGERS, BOSSES, PROJECTILES
	}

	public enum Sort {
		/** Closest first (to the nearest point of the hitbox). */
		DISTANCE,
		/** Lowest health (with absorption) first. */
		HEALTH,
		/** Smallest turn from where you're looking first. */
		ANGLE
	}

	private Targets() {
	}

	public static Query query() {
		return new Query();
	}

	/**
	 * What kind of target an entity is, or null for things that are never targets (items, orbs, the world).
	 * Neutral mobs (wolves, bees, endermen, zombified piglins) are hostiles while angry; when calm they're animals,
	 * or nothing at all with {@code onlyAngry}.
	 */
	public static @Nullable Type type(Entity e, boolean onlyAngry) {
		if (e instanceof PlayerEntity) return Type.PLAYERS;
		if (e instanceof WitherEntity || e instanceof WardenEntity || e instanceof EnderDragonEntity) return Type.BOSSES;
		if (e instanceof ProjectileEntity) return Type.PROJECTILES;
		if (e instanceof MerchantEntity) return Type.VILLAGERS;
		if (e instanceof EndermanEntity enderman) return !onlyAngry || enderman.isProvoked() ? Type.HOSTILES : null;
		if (e instanceof ZombifiedPiglinEntity piglin) return !onlyAngry || piglin.isAttacking() ? Type.HOSTILES : null;
		if (e instanceof Angerable angerable && !(e instanceof HostileEntity)) {
			if (angerable.getAngerTime() > 0) return Type.HOSTILES;
			return onlyAngry ? null : Type.ANIMALS;
		}
		if (e instanceof AnimalEntity) return Type.ANIMALS;
		if (e instanceof HostileEntity || e instanceof Monster || Entities.isHostile(e)) return Type.HOSTILES;
		return null;
	}

	/** Health plus absorption; 0 for non-living entities. */
	public static float health(Entity e) {
		return e instanceof LivingEntity l ? l.getHealth() + l.getAbsorptionAmount() : 0;
	}

	public static final class Query {
		private double range = 6;
		private final Set<Type> types = EnumSet.of(Type.PLAYERS);
		private boolean onlyAngry, friends, invisibles = true, named = true, creative = true, throughWalls = true;
		private Sort sort = Sort.DISTANCE;
		private Predicate<Entity> filter = e -> true;

		private Query() {
		}

		/** Maximum distance from your eyes to the nearest point of the hitbox. */
		public Query range(double range) {
			this.range = range;
			return this;
		}

		/** Which kinds of entity count (players only by default). */
		public Query types(Type... types) {
			this.types.clear();
			this.types.addAll(List.of(types));
			return this;
		}

		public Query types(Set<Type> types) {
			this.types.clear();
			this.types.addAll(types);
			return this;
		}

		public Query onlyAngry(boolean onlyAngry) {
			this.onlyAngry = onlyAngry;
			return this;
		}

		/** Include friends (off by default). */
		public Query friends(boolean include) {
			this.friends = include;
			return this;
		}

		public Query invisibles(boolean include) {
			this.invisibles = include;
			return this;
		}

		/** Include mobs with name tags. */
		public Query named(boolean include) {
			this.named = include;
			return this;
		}

		/** Include players in creative mode. */
		public Query creative(boolean include) {
			this.creative = include;
			return this;
		}

		/** Include entities you can't see (on by default). */
		public Query throughWalls(boolean include) {
			this.throughWalls = include;
			return this;
		}

		public Query sort(Sort sort) {
			this.sort = sort;
			return this;
		}

		/** An extra condition of your own. */
		public Query filter(Predicate<Entity> filter) {
			this.filter = this.filter.and(filter);
			return this;
		}

		public boolean test(Entity e) {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player == null || e == mc.player || !e.isAlive() || !e.isAttackable() || e instanceof ExperienceOrbEntity) return false;
			if (e instanceof LivingEntity l && l.isDead()) return false;
			Type type = type(e, onlyAngry);
			if (type == null || !types.contains(type)) return false;
			if (!invisibles && e.isInvisible()) return false;
			if (!named && e.hasCustomName() && !(e instanceof PlayerEntity)) return false;
			if (e instanceof PlayerEntity p) {
				if (!friends && Entities.isFriend(p)) return false;
				if (!creative && p.isCreative()) return false;
			}
			if (MathUtil.distanceTo(e.getBoundingBox(), mc.player.getEyePos()) > range) return false;
			if (!throughWalls && !mc.player.canSee(e)) return false;
			return filter.test(e);
		}

		/** Every match, best first. */
		public List<Entity> list() {
			MinecraftClient mc = MinecraftClient.getInstance();
			List<Entity> out = new ArrayList<>();
			if (mc.world == null || mc.player == null) return out;
			for (Entity e : mc.world.getEntities()) if (test(e)) out.add(e);
			out.sort(comparator());
			return out;
		}

		/** The best match, or null. */
		public @Nullable Entity best() {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.world == null || mc.player == null) return null;
			Comparator<Entity> order = comparator();
			Entity best = null;
			for (Entity e : mc.world.getEntities()) {
				if (test(e) && (best == null || order.compare(e, best) < 0)) best = e;
			}
			return best;
		}

		private Comparator<Entity> comparator() {
			MinecraftClient mc = MinecraftClient.getInstance();
			Vec3d eyes = Reach.eyes();
			Comparator<Entity> byDistance = Comparator.comparingDouble(e -> MathUtil.distanceTo(e.getBoundingBox(), eyes));
			return switch (sort) {
				case DISTANCE -> byDistance;
				case HEALTH -> Comparator.comparingDouble(Targets::health).thenComparing(byDistance);
				case ANGLE -> Comparator.comparingDouble((Entity e) -> MathUtil.angleTo(eyes, mc.player.getYaw(), mc.player.getPitch(), e.getBoundingBox().getCenter()))
					.thenComparing(byDistance);
			};
		}
	}
}
