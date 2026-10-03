package dev.myriad.api.service;

import java.util.Collection;
import net.minecraft.world.entity.player.Player;

public interface Friends {
	boolean isFriend(String name);

	default boolean isFriend(Player player) {
		return isFriend(player.getGameProfile().name());
	}

	boolean add(String name);

	boolean remove(String name);

	Collection<String> all();
}
