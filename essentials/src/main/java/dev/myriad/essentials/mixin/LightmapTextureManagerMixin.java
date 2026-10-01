package dev.myriad.essentials.mixin;

import dev.myriad.api.module.Modules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.myriad.essentials.modules.render.Fullbright;
import dev.myriad.essentials.modules.render.Xray;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
	@ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"),
		slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getGamma()Lnet/minecraft/client/option/SimpleOption;")))
	private Object essentials$fullbright(Object original) {
		Fullbright fullbright = Modules.active(Fullbright.class);
		if (fullbright != null) return fullbright.gamma.get();
		return Modules.isActive(Xray.class) ? 16.0 : original;
	}
}
