package dev.myriad.api.service;

import net.minecraft.entity.player.PlayerEntity;

import java.util.Collection;

public interface Friends {
	boolean isFriend(String name);

	default boolean isFriend(PlayerEntity player) {
		return isFriend(player.getGameProfile().getName());
	}

	boolean add(String name);

	boolean remove(String name);

	Collection<String> all();
}
