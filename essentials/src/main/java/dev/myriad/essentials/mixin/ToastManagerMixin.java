package dev.myriad.essentials.mixin;

import dev.myriad.essentials.modules.render.NoRender;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
	@Inject(method = "draw", at = @At("HEAD"), cancellable = true)
	private void essentials$noToasts(DrawContext context, CallbackInfo ci) {
		if (NoRender.hides(n -> n.toasts)) ci.cancel();
	}
}
