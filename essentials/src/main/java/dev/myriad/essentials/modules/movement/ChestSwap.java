package dev.myriad.essentials.modules.movement;

import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;

/**
 * Swaps your chestplate for an elytra or back again, then turns itself off; bind it to a key.
 */
public class ChestSwap extends Module {
	/** Player screen handler slot of the chest armour piece. */
	private static final int CHEST_SLOT = 6;
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
			boolean wearingElytra = mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
			int slot = wearingElytra ? findChestplate() : find(Items.ELYTRA);
			if (slot >= 0) swapIntoChest(slot);
			else warn(wearingElytra ? "No chestplate in your inventory" : "No elytra in your inventory");
		}
		mc.execute(this::disable);
	}

	private void swapIntoChest(int invSlot) {
		var im = mc.interactionManager;
		int sync = mc.player.playerScreenHandler.syncId;
		if (invSlot < 9) {
			im.clickSlot(sync, CHEST_SLOT, invSlot, SlotActionType.SWAP, mc.player);
			return;
		}
		// Main inventory: bring it through the selected hotbar slot, then put the hotbar item back.
		int hotbar = mc.player.getInventory().selectedSlot;
		im.clickSlot(sync, invSlot, hotbar, SlotActionType.SWAP, mc.player);
		im.clickSlot(sync, CHEST_SLOT, hotbar, SlotActionType.SWAP, mc.player);
		im.clickSlot(sync, invSlot, hotbar, SlotActionType.SWAP, mc.player);
	}

	private int find(Item item) {
		for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
		return -1;
	}

	private int findChestplate() {
		if (!preferBest.get()) {
			for (int i = 0; i < 36; i++) {
				ItemStack s = mc.player.getInventory().getStack(i);
				if (CHESTPLATES.contains(s.getItem())) return i;
			}
			return -1;
		}
		for (Item item : CHESTPLATES) {
			int slot = find(item);
			if (slot >= 0) return slot;
		}
		return -1;
	}
}
