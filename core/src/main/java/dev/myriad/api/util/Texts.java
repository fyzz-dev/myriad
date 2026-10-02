package dev.myriad.api.util;

import dev.myriad.api.Myriad;
import net.minecraft.item.ItemStack;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Chat text that does something: run a Myriad command on click, fill the chat box, copy a value, show a tooltip.
 * Build messages from these and send them with {@code Myriad.chat(...)} or a command's {@code info(Text)}.
 *
 * <pre>{@code
 * info(Text.literal("Saved Home ").append(Texts.coords(pos)).append(" ").append(Texts.command("[remove]", "waypoint remove Home")));
 * }</pre>
 */
public final class Texts {
	private Texts() {
	}

	/** {@code label}, which runs the Myriad command {@code command} (without the prefix) when clicked. */
	public static MutableText command(String label, String command) {
		String full = Myriad.config().commandPrefix() + command;
		return Text.literal(label).styled(s -> s.withColor(Formatting.AQUA)
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, full))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(full).formatted(Formatting.GRAY))));
	}

	/** {@code label}, which puts {@code command} (without the prefix) in the chat box to finish typing. */
	public static MutableText suggest(String label, String command) {
		String full = Myriad.config().commandPrefix() + command;
		return Text.literal(label).styled(s -> s.withColor(Formatting.AQUA)
			.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, full))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to edit").formatted(Formatting.GRAY))));
	}

	/** {@code label}, which copies {@code value} to the clipboard when clicked. */
	public static MutableText copy(String label, String value) {
		return Text.literal(label).styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy").formatted(Formatting.GRAY))));
	}

	/** "x y z", copied when clicked. */
	public static MutableText coords(BlockPos pos) {
		String c = Format.coords(pos);
		return copy(c, c).formatted(Formatting.GRAY);
	}

	/** {@code text} with a tooltip. */
	public static MutableText hover(Text text, Text tooltip) {
		return text.copy().styled(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip)));
	}

	/** The item's name, showing its full tooltip on hover. */
	public static MutableText item(ItemStack stack) {
		return stack.getName().copy().styled(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(stack))));
	}

	/** {@code text} in an ARGB colour (e.g. a theme colour: {@code Myriad.ui().theme().accent.argb()}). */
	public static MutableText colored(String text, int argb) {
		return Text.literal(text).styled(s -> s.withColor(TextColor.fromRgb(argb & 0xFFFFFF)));
	}
}
