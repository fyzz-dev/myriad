package dev.myriad.essentials.mixin;

import dev.myriad.essentials.modules.render.NoRender;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void essentials$bossBar(DrawContext context, CallbackInfo ci) {
		if (NoRender.hides(n -> n.bossBar)) ci.cancel();
	}
}
