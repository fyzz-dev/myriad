package dev.myriad.essentials.hud;

import dev.myriad.api.render.Canvas;
import dev.myriad.api.render.FontFamily;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.ui.Rect;
import dev.myriad.api.ui.hud.HudPanel;
import dev.myriad.api.ui.hud.ItemHud;
import dev.myriad.api.ui.hud.HudStyle;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * The item in your off hand, with how many of it you carry in total (handy for totems, crystals or gapples) and its
 * durability.
 */
public final class OffhandPanel extends HudPanel {
	public enum Count {
		TOTAL, STACK, NONE
	}

	private final EnumSetting<Count> count = sgGeneral.enumSetting("Count", Count.TOTAL).description("Total counts the item across your whole inventory.").build();
	private final BoolSetting hideEmpty = sgGeneral.bool("Hide Empty").description("Draw nothing when the off hand is empty.").defaultValue(true).build();
	private final BoolSetting durabilityBar = sgGeneral.bool("Durability Bar").defaultValue(true).build();

	private final HudStyle style = new HudStyle(settings, HudStyle.Mode.TEXT, false);

	public OffhandPanel() {
		super("Off Hand", "\uf256");
	}

	private ItemStack stack() {
		return mc.player == null ? Items.TOTEM_OF_UNDYING.getDefaultStack() : mc.player.getOffHandStack();
	}

	private int total(Item item) {
		if (mc.player == null) return 1;
		int n = 0;
		var inv = mc.player.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (s.isOf(item)) n += s.getCount();
		}
		return n;
	}

	private float size() {
		return 16 * scale.getFloat();
	}

	@Override
	public Rect preferredSize(Canvas c) {
		return new Rect(0, 0, size() + 1, size() + 1);
	}

	@Override
	public void render(Canvas c, float w, float h, float mx, float my) {
		ItemStack stack = stack();
		if (stack.isEmpty()) {
			if (!hideEmpty.get()) c.roundRect(0, 0, size(), size(), 3, 0x33000000);
			return;
		}
		float size = size();
		c.item(stack, 0, 0, size, false);
		if (durabilityBar.get() && stack.isDamageable() && ItemHud.percent(stack) < 1f) ItemHud.bar(c, stack, 0, 0, size);
		int n = switch (count.get()) {
			case TOTAL -> total(stack.getItem());
			case STACK -> stack.getCount();
			case NONE -> 0;
		};
		if (count.get() == Count.NONE || (n <= 1 && !stack.isStackable())) return;
		String text = String.valueOf(n);
		float ts = c.defaultFontSize() * scale.getFloat();
		float tw = c.textWidth(FontFamily.SANS_BOLD, ts, text);
		HudStyle.draw(c, FontFamily.SANS_BOLD, ts, text, size - tw, size - c.textHeight(FontFamily.SANS_BOLD, ts) + 1, style.color(0, 1), style.shadow());
	}
}
