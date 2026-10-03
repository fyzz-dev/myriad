package dev.myriad.essentials.mixin;

import dev.myriad.api.module.Modules;
import dev.myriad.essentials.modules.render.Tooltips;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {
	@Shadow
	protected Slot hoveredSlot;

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
	private void essentials$containerPreview(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
		Tooltips preview = Modules.active(Tooltips.class);
		if (preview != null && preview.renderTooltip(context, hoveredSlot, mouseX, mouseY)) ci.cancel();
	}

	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void essentials$shulkerIcon(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		Tooltips preview = Modules.active(Tooltips.class);
		if (preview != null && preview.slotIcons() && slot.hasItem() && Tooltips.isShulker(slot.getItem())) {
			Tooltips.drawIcon(context, slot.getItem(), slot.x, slot.y);
		}
	}
}
