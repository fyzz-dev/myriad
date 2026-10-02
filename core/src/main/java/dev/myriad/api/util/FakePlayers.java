package dev.myriad.api.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Client-side dummy players for testing combat and render features in singleplayer: they stand where you do, wear
 * a copy of your gear, and count as players for targeting, damage maths and rendering. Only you can see them; the
 * server knows nothing about them. They're removed when you leave the world. Also {@code .fakeplayer}.
 */
public final class FakePlayers {
	private static final List<OtherClientPlayerEntity> players = new ArrayList<>();
	/** Negative ids can't collide with real entities. */
	private static int nextId = -1_000_000;

	private FakePlayers() {
	}

	/** Spawns a fake player named {@code name} at your position with {@code health} and a copy of your inventory. */
	public static @Nullable PlayerEntity spawn(String name, float health, boolean copyInventory) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return null;
		OtherClientPlayerEntity fake = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.nameUUIDFromBytes(("fake:" + name).getBytes()), name));
		fake.copyPositionAndRotation(mc.player);
		fake.setHeadYaw(mc.player.getHeadYaw());
		fake.setBodyYaw(mc.player.getBodyYaw());
		if (copyInventory) fake.getInventory().clone(mc.player.getInventory());
		fake.setHealth(health);
		fake.setId(nextId--);
		mc.world.addEntity(fake);
		players.add(fake);
		return fake;
	}

	public static boolean remove(String name) {
		prune();
		for (OtherClientPlayerEntity p : List.copyOf(players)) {
			if (p.getGameProfile().getName().equalsIgnoreCase(name)) {
				despawn(p);
				return true;
			}
		}
		return false;
	}

	public static void clear() {
		prune();
		for (OtherClientPlayerEntity p : List.copyOf(players)) despawn(p);
	}

	private static void despawn(OtherClientPlayerEntity p) {
		players.remove(p);
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world != null) mc.world.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
	}

	public static List<PlayerEntity> all() {
		prune();
		return Collections.unmodifiableList(players);
	}

	public static boolean isFake(Entity entity) {
		return entity instanceof OtherClientPlayerEntity p && players.contains(p);
	}

	/** Fake players belong to the world they were spawned in; forget them once it's gone. */
	private static void prune() {
		var world = MinecraftClient.getInstance().world;
		players.removeIf(p -> p.getWorld() != world);
	}
}
