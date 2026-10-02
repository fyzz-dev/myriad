package dev.myriad.essentials.modules.player;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.RegistryListSetting;
import dev.myriad.api.util.Interactions;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Shortens the delay between item uses (vanilla waits 4 ticks), for placing blocks or throwing XP bottles quickly.
 * Limit it to a list of items, or to everything except a list. Start Delay waits half a second into a held click
 * first, so single clicks stay normal.
 */
public class FastUse extends Module {
	public enum Selection {
		WHITELIST, BLACKLIST, ALL
	}

	private final EnumSetting<Selection> selection = sgGeneral.enumSetting("Selection", Selection.WHITELIST).description("Which items it applies to.").build();
	private final RegistryListSetting<Item> itemList = sgGeneral.items("Items").defaultValue(Items.ENDER_CHEST, Items.EXPERIENCE_BOTTLE).visible(() -> selection.get() != Selection.ALL).build();
	private final IntSetting delay = sgGeneral.intSetting("Delay").description("Ticks between uses.").defaultValue(1).range(0, 4).build();
	private final BoolSetting startDelay = sgGeneral.bool("Start Delay").description("Wait half a second into a held click before speeding up.").build();

	private long firstPress;
	private boolean pressing;

	public FastUse() {
		super(Categories.PLAYER, "Fast Use", "Removes the delay between item uses.");
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) return;
		if (!mc.options.useKey.isPressed()) {
			pressing = false;
			return;
		}
		if (!pressing) {
			pressing = true;
			firstPress = System.currentTimeMillis();
		}
		if (startDelay.get() && System.currentTimeMillis() - firstPress < 500) return;
		if (!applies(mc.player.getMainHandStack()) && !applies(mc.player.getOffHandStack())) return;
		if (Interactions.itemUseCooldown() > delay.get()) Interactions.setItemUseCooldown(delay.get());
	}

	private boolean applies(ItemStack stack) {
		if (stack.isEmpty()) return false;
		return switch (selection.get()) {
			case WHITELIST -> itemList.contains(stack.getItem());
			case BLACKLIST -> !itemList.contains(stack.getItem());
			case ALL -> true;
		};
	}
}
