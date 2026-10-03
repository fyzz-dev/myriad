package dev.myriad.essentials.modules.movement;

import dev.myriad.api.Myriad;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.util.Slots;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Swaps your chestplate for an elytra or back again, then turns itself off; bind it to a key.
 */
public class ChestSwap extends Module {
	private static final List<Item> CHESTPLATES = List.of(Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.IRON_CHESTPLATE,
		Items.CHAINMAIL_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.LEATHER_CHESTPLATE);

	private final BoolSetting preferBest = sgGeneral.bool("Best Chestplate").description("Pick the strongest chestplate (netherite first).").defaultValue(true).build();

	public ChestSwap() {
		super(Categories.MOVEMENT, "Chest Swap", "Swap between your elytra and chestplate.");
		chatFeedback.set(false);
	}

	@Override
	protected void onEnable() {
		if (inGame()) {
			boolean wearingElytra = mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
			int slot = wearingElytra ? findChestplate() : find(Items.ELYTRA);
			if (slot >= 0) swapIntoChest(slot);
			else warn(wearingElytra ? "No chestplate in your inventory" : "No elytra in your inventory");
		}
		mc.execute(this::disable);
	}

	private void swapIntoChest(int invSlot) {
		Myriad.inventory().move(invSlot, Slots.CHEST);
	}

	private static int find(Item item) {
		return Myriad.inventory().bestInInventory(s -> s.is(item) ? 1 : 0);
	}

	/** The strongest chestplate (earliest in CHESTPLATES), or any chestplate when not preferring the best. */
	private int findChestplate() {
		return Myriad.inventory().bestInInventory(s -> {
			int i = CHESTPLATES.indexOf(s.getItem());
			return i < 0 ? 0 : preferBest.get() ? CHESTPLATES.size() - i : 1;
		});
	}
}
