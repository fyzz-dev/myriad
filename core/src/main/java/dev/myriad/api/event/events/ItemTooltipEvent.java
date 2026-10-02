package dev.myriad.api.event.events;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/** An item's tooltip lines were built. Add, remove or change {@link #lines()}; the first line is the item's name. */
public final class ItemTooltipEvent {
	private final ItemStack stack;
	private final List<Text> lines;
	private final TooltipType type;

	public ItemTooltipEvent(ItemStack stack, List<Text> lines, TooltipType type) {
		this.stack = stack;
		this.lines = lines;
		this.type = type;
	}

	public ItemStack stack() {
		return stack;
	}

	/** Mutable. */
	public List<Text> lines() {
		return lines;
	}

	/** Whether advanced tooltips (F3+H) are on. */
	public boolean advanced() {
		return type.isAdvanced();
	}
}
