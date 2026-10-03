package dev.myriad.impl.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.WindowResizeEvent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
	@Shadow
	@Final
	private Window window;

	@Inject(method = "resizeGui", at = @At("TAIL"))
	private void myriad$onResize(CallbackInfo ci) {
		if (Myriad.isReady()) Myriad.events().post(new WindowResizeEvent(window.getWidth(), window.getHeight()));
	}
}
