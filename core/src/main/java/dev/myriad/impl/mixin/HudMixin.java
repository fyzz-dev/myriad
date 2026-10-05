package dev.myriad.impl.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.myriad.api.Myriad;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public abstract class HudMixin {
	/** A hotbar slot an item is borrowed into keeps showing what was there (see Inventory.borrow). */
	@WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack myriad$borrowedSlot(Inventory inventory, int slot, Operation<ItemStack> original) {
		return Myriad.isReady() && slot >= 0 && slot < 9 ? Myriad.inventory().shownInHotbar(slot) : original.call(inventory, slot);
	}
}
