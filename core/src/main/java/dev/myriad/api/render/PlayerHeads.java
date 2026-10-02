package dev.myriad.api.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Players' faces drawn on the canvas, with their hat layer, for radars, target HUDs, nametags and lists. Skins come
 * from the tab list (so players out of render distance still have one); unknown players get a default skin.
 */
public final class PlayerHeads {
	private PlayerHeads() {
	}

	/** The skin texture for {@code player}. */
	public static Identifier skin(UUID player) {
		var handler = MinecraftClient.getInstance().getNetworkHandler();
		var entry = handler == null ? null : handler.getPlayerListEntry(player);
		return entry != null ? entry.getSkinTextures().texture() : DefaultSkinHelper.getSkinTextures(player).texture();
	}

	public static Identifier skin(PlayerEntity player) {
		return player instanceof AbstractClientPlayerEntity p ? p.getSkinTextures().texture() : skin(player.getUuid());
	}

	/** Draws the face of {@code skin} at (x, y), {@code size} units square, tinted (use 0xFFFFFFFF for none). */
	public static void draw(Canvas c, Identifier skin, float x, float y, float size, int tint) {
		c.texture(skin, x, y, size, size, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, tint);
		// The hat layer sits slightly larger, as in game.
		float hat = size / 16f;
		c.texture(skin, x - hat, y - hat, size + hat * 2, size + hat * 2, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, tint);
	}

	public static void draw(Canvas c, PlayerEntity player, float x, float y, float size) {
		draw(c, skin(player), x, y, size, 0xFFFFFFFF);
	}

	public static void draw(Canvas c, UUID player, float x, float y, float size) {
		draw(c, skin(player), x, y, size, 0xFFFFFFFF);
	}
}
