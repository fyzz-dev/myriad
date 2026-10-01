package dev.myriad.essentials.modules.render;

import dev.myriad.api.Myriad;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.SettingColor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A better tab list: show more than 80 players, list you and your friends first and highlight them, show gamemodes,
 * and print ping as a number. Applied by this addon's PlayerListHudMixin.
 */
public class ExtraTab extends Module {

	public enum Latency {
		NUMBER, VANILLA, NONE
	}

	private final IntSetting tabSize = sgGeneral.intSetting("Tab Size").description("Most players shown (vanilla shows 80).").defaultValue(200).range(1, 1000).build();
	private final BoolSetting friendsFirst = sgGeneral.bool("Friends First").description("List you, then friends, before everyone else.").defaultValue(true).build();
	private final BoolSetting highlightSelf = sgGeneral.bool("Highlight Self").defaultValue(true).build();
	private final ColorSetting selfColor = sgGeneral.color("Self Color").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT)).visible(highlightSelf::get).build();
	private final BoolSetting highlightFriends = sgGeneral.bool("Highlight Friends").defaultValue(true).build();
	private final ColorSetting friendColor = sgGeneral.color("Friend Color").defaultValue(SettingColor.role(SettingColor.Mode.CYAN)).visible(highlightFriends::get).build();
	private final BoolSetting gamemode = sgGeneral.bool("Gamemode").description("Show each player's gamemode.").build();
	private final EnumSetting<Latency> latency = sgGeneral.enumSetting("Latency", Latency.NUMBER).build();

	public ExtraTab() {
		super(Categories.RENDER, "Extra Tab", "A better tab list.");
	}

	public int tabSize() {
		return tabSize.get();
	}

	public List<PlayerListEntry> sort(List<PlayerListEntry> entries) {
		if (!friendsFirst.get()) return entries;
		List<PlayerListEntry> list = new ArrayList<>(entries);
		list.sort(Comparator.comparingInt(this::priority));
		return list;
	}

	private int priority(PlayerListEntry e) {
		String name = e.getProfile().getName();
		if (mc.player != null && e.getProfile().getId().equals(mc.player.getUuid())) return 0;
		return Myriad.friends().isFriend(name) ? 1 : 2;
	}

	public Text name(PlayerListEntry entry, Text original) {
		Integer color = null;
		if (highlightSelf.get() && mc.player != null && entry.getProfile().getId().equals(mc.player.getUuid())) color = selfColor.argb();
		else if (highlightFriends.get() && Myriad.friends().isFriend(entry.getProfile().getName())) color = friendColor.argb();
		Text out = original;
		if (color != null) out = Text.literal(original.getString()).setStyle(original.getStyle().withColor(TextColor.fromRgb(color & 0xFFFFFF)));
		if (gamemode.get()) {
			GameMode gm = entry.getGameMode();
			String tag = gm == null ? "?" : switch (gm) {
				case SURVIVAL -> "S";
				case CREATIVE -> "C";
				case ADVENTURE -> "A";
				case SPECTATOR -> "Sp";
			};
			MutableText t = Text.empty().append(out);
			t.append(Text.literal(" [" + tag + "]").styled(s -> s.withColor(0xAAAAAA)));
			out = t;
		}
		return out;
	}

	/** Draws the latency for a row; returns true if vanilla's icon should be skipped. */
	public boolean drawLatency(DrawContext ctx, int width, int x, int y, PlayerListEntry entry) {
		return switch (latency.get()) {
			case VANILLA -> false;
			case NONE -> true;
			case NUMBER -> {
				int ms = entry.getLatency();
				String text = String.valueOf(ms);
				int color = ms < 75 ? 0x55FF55 : ms < 150 ? 0xFFFF55 : ms < 300 ? 0xFFAA55 : 0xFF5555;
				ctx.getMatrices().push();
				ctx.getMatrices().translate(x + width - mc.textRenderer.getWidth(text) * 0.75f - 1, y + 1.5f, 0);
				ctx.getMatrices().scale(0.75f, 0.75f, 1);
				ctx.drawTextWithShadow(mc.textRenderer, text, 0, 0, 0xFF000000 | color);
				ctx.getMatrices().pop();
				yield true;
			}
		};
	}
}
