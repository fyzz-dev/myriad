package dev.myriad.impl.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.WindowResizeEvent;
import dev.myriad.impl.MyriadImpl;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
	@Shadow
	@Final
	private Window window;

	@Inject(method = "resizeGui", at = @At("TAIL"))
	private void myriad$onResize(CallbackInfo ci) {
		if (Myriad.isReady()) Myriad.events().post(new WindowResizeEvent(window.getWidth(), window.getHeight()));
	}

	/** Tick speed: shortens (or stretches) the time the client waits between game ticks. */
	@Inject(method = "getTickTargetMillis", at = @At("RETURN"), cancellable = true)
	private void myriad$tickSpeed(float defaultMillis, CallbackInfoReturnable<Float> cir) {
		if (MyriadImpl.get() == null) return;
		float m = MyriadImpl.get().tickSpeedManager().multiplier();
		if (m != 1f) cir.setReturnValue(cir.getReturnValue() / m);
	}
}
