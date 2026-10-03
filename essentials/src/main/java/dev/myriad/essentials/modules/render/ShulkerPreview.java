package dev.myriad.essentials.modules.render;

import dev.myriad.api.Myriad;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.Canvas;
import dev.myriad.api.render.FontFamily;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.KeybindSetting;
import dev.myriad.api.ui.ThemeSettings;
import dev.myriad.api.util.ColorUtil;
import dev.myriad.api.util.ItemInfo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Hovering a shulker box in any inventory shows its 27 slots in a themed panel instead of the vanilla tooltip.
 * Hold the lock key to pin the panel where it is, so you can move the mouse over it and read item tooltips.
 * Shulker boxes in slots and in the hotbar can also show their most common item as a small icon.
 */
public class ShulkerPreview extends Module {

	private static final int COLS = 9, ROWS = 3, SLOT = 18, WIDTH = COLS * SLOT + 14, HEADER = 16, FOOTER = 7;
	private static final int HEIGHT = HEADER + ROWS * SLOT + FOOTER;

	private final KeybindSetting lockKey = sgGeneral.keybind("Lock Preview").description("Hold to pin the preview so you can hover its items.").build();
	private final BoolSetting slotIcons = sgGeneral.bool("Slot Icons").description("Show the most common item on shulker boxes in inventories.").defaultValue(true).build();
	private final BoolSetting hotbarIcons = sgGeneral.bool("Hotbar Icons").description("Show the most common item on shulker boxes in your hotbar.").defaultValue(true).build();
	private final BoolSetting emptyPreview = sgGeneral.bool("Empty Boxes").description("Preview empty shulker boxes too.").build();

	private ItemStack lockedShulker;
	private List<ItemStack> lockedContents;
	private int lockedX, lockedY;

	public ShulkerPreview() {
		super(Categories.RENDER, "Shulker Preview", "Shows shulker box contents when you hover one.");
	}

	@Override
	protected void onDisable() {
		lockedShulker = null;
		lockedContents = null;
	}

	public static boolean isShulker(ItemStack stack) {
		return stack != null && ItemInfo.isShulkerBox(stack);
	}

	/** The box's 27 slots, empty ones included, so the preview grid lines up. */
	public static List<ItemStack> contents(ItemStack stack) {
		List<ItemStack> items = new ArrayList<>(27);
		for (ItemStack s : ItemInfo.contents(stack)) if (items.size() < 27) items.add(s);
		while (items.size() < 27) items.add(ItemStack.EMPTY);
		return items;
	}

	private static boolean hasItems(List<ItemStack> items) {
		for (ItemStack s : items) if (!s.isEmpty()) return true;
		return false;
	}

	/** The item the box holds the most of, or null. */
	public static @Nullable ItemStack mostCommon(ItemStack shulker) {
		Map<Item, Integer> counts = new HashMap<>();
		Map<Item, ItemStack> first = new HashMap<>();
		for (ItemStack s : contents(shulker)) {
			if (s.isEmpty()) continue;
			counts.merge(s.getItem(), s.getCount(), Integer::sum);
			first.putIfAbsent(s.getItem(), s);
		}
		return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(e -> first.get(e.getKey())).orElse(null);
	}

	public boolean slotIcons() {
		return slotIcons.get();
	}

	public boolean hotbarIcons() {
		return hotbarIcons.get();
	}

	/** Draws the small icon over a shulker box at (x, y). */
	public static void drawIcon(GuiGraphicsExtractor ctx, ItemStack shulker, int x, int y) {
		ItemStack top = mostCommon(shulker);
		if (top == null) return;
		var pose = ctx.pose();
		pose.pushMatrix();
		pose.translate(x + 6, y + 6);
		pose.scale(0.6f, 0.6f);
		ctx.item(top, 0, 0);
		pose.popMatrix();
	}

	/**
	 * Called in place of the inventory tooltip. Returns true if the preview replaced it.
	 */
	public boolean renderTooltip(GuiGraphicsExtractor ctx, @Nullable Slot hovered, int mouseX, int mouseY) {
		if (lockKey.get().isPressed()) {
			if (lockedShulker == null && hovered != null && hovered.hasItem() && isShulker(hovered.getItem())) {
				List<ItemStack> items = contents(hovered.getItem());
				if (hasItems(items) || emptyPreview.get()) {
					lockedShulker = hovered.getItem().copy();
					lockedContents = items;
					lockedX = mouseX;
					lockedY = mouseY;
				}
			}
		} else {
			lockedShulker = null;
			lockedContents = null;
		}

		ItemStack shulker;
		List<ItemStack> items;
		int ax, ay;
		if (lockedShulker != null) {
			shulker = lockedShulker;
			items = lockedContents;
			ax = lockedX;
			ay = lockedY;
		} else {
			if (hovered == null || !hovered.hasItem() || !isShulker(hovered.getItem())) return false;
			shulker = hovered.getItem();
			items = contents(shulker);
			if (!hasItems(items) && !emptyPreview.get()) return false;
			ax = mouseX;
			ay = mouseY;
		}

		int[] p = position(ax, ay);
		ItemStack under = hoveredItem(items, p[0], p[1], mouseX, mouseY);
		// A layer of its own above the inventory, like a vanilla tooltip.
		ctx.nextStratum();
		Myriad.ui().draw(ctx, c -> drawPanel(c, shulker, items, p[0], p[1]));
		if (under != null) ctx.setTooltipForNextFrame(mc.font, under, mouseX, mouseY);
		return true;
	}

	private static int[] position(int mouseX, int mouseY) {
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		int x = Math.max(4, Math.min(mouseX + 12, sw - WIDTH - 4));
		int y = Math.max(4, Math.min(mouseY - 6, sh - HEIGHT - 4));
		return new int[]{x, y};
	}

	private static @Nullable ItemStack hoveredItem(List<ItemStack> items, int px, int py, int mx, int my) {
		int col = Math.floorDiv(mx - (px + 7), SLOT), row = Math.floorDiv(my - (py + HEADER), SLOT);
		if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return null;
		ItemStack s = items.get(row * COLS + col);
		return s.isEmpty() ? null : s;
	}

	private static void drawPanel(Canvas c, ItemStack shulker, List<ItemStack> items, float x, float y) {
		ThemeSettings theme = Myriad.ui().theme();
		float r = theme.rounding.get();
		if (theme.shadow.get()) c.shadow(x, y, WIDTH, HEIGHT, r, theme.shadowRange.get(), theme.shadowColor.argb());
		c.backdrop(x, y, WIDTH, HEIGHT, r, 1);
		c.roundRect(x, y, WIDTH, HEIGHT, r, ColorUtil.withAlpha(theme.windowBackground.argb(), Math.max(200, ColorUtil.alpha(theme.windowBackground.argb()))));
		c.gradientOutline(x, y, WIDTH, HEIGHT, r, Math.max(1, theme.borderSize.get()), theme.activeBorderFrom.argb(), theme.activeBorderTo.argb(), theme.borderAngle.get().floatValue());
		String title = c.ellipsize(FontFamily.SANS_BOLD, c.defaultFontSize(), shulker.getHoverName().getString(), WIDTH - 14);
		c.text(FontFamily.SANS_BOLD, c.defaultFontSize(), title, x + 7, y + (HEADER - c.textHeight()) / 2 + 1, theme.text.argb());
		float gx = x + 7, gy = y + HEADER;
		int cell = ColorUtil.withAlpha(theme.surface.argb(), 110);
		for (int i = 0; i < 27; i++) {
			float sx = gx + (i % COLS) * SLOT, sy = gy + (i / COLS) * SLOT;
			c.roundRect(sx + 0.5f, sy + 0.5f, SLOT - 1, SLOT - 1, 2, cell);
		}
		for (int i = 0; i < 27; i++) {
			ItemStack s = items.get(i);
			if (!s.isEmpty()) c.item(s, gx + (i % COLS) * SLOT + 1, gy + (i / COLS) * SLOT + 1, 16);
		}
	}
}
