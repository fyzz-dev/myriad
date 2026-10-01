package dev.myriad.essentials.mixin;

import dev.myriad.api.module.Modules;
import dev.myriad.essentials.modules.render.ShulkerPreview;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
	@Shadow
	protected Slot focusedSlot;

	@Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
	private void essentials$shulkerPreview(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
		ShulkerPreview preview = Modules.active(ShulkerPreview.class);
		if (preview != null && preview.renderTooltip(context, focusedSlot, mouseX, mouseY)) ci.cancel();
	}

	@Inject(method = "drawSlot", at = @At("TAIL"))
	private void essentials$shulkerIcon(DrawContext context, Slot slot, CallbackInfo ci) {
		ShulkerPreview preview = Modules.active(ShulkerPreview.class);
		if (preview != null && preview.slotIcons() && slot.hasStack() && ShulkerPreview.isShulker(slot.getStack())) {
			ShulkerPreview.drawIcon(context, slot.getStack(), slot.x, slot.y);
		}
	}
}
