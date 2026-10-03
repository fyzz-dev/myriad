package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.ItemTooltipEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "getTooltipLines", at = @At("RETURN"))
	private void myriad$tooltip(Item.TooltipContext context, Player player, TooltipFlag type, CallbackInfoReturnable<List<Component>> cir) {
		if (Myriad.isReady() && Myriad.events().hasListeners(ItemTooltipEvent.class)) {
			Myriad.events().post(new ItemTooltipEvent((ItemStack) (Object) this, cir.getReturnValue(), type));
		}
	}
}
