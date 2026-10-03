package dev.myriad.essentials.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.myriad.api.module.Modules;
import dev.myriad.essentials.modules.render.Fullbright;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapTextureManagerMixin {
	@ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0),
		slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")))
	private Object essentials$fullbright(Object original) {
		Fullbright fullbright = Modules.active(Fullbright.class);
		return fullbright != null ? fullbright.gamma.get() : original;
	}
}
