package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.ScreenOpenEvent;
import dev.myriad.api.event.events.WindowResizeEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Shadow
	@Final
	private Window window;

	@Shadow
	public abstract void setScreen(Screen screen);

	@Unique
	private boolean myriad$replacingScreen;

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void myriad$onSetScreen(Screen screen, CallbackInfo ci) {
		if (myriad$replacingScreen || !Myriad.isReady()) return;
		ScreenOpenEvent event = Myriad.events().post(new ScreenOpenEvent(screen));
		if (event.isCancelled()) {
			ci.cancel();
		} else if (event.screen() != screen) {
			ci.cancel();
			myriad$replacingScreen = true;
			try {
				setScreen(event.screen());
			} finally {
				myriad$replacingScreen = false;
			}
		}
	}

	@Inject(method = "onResolutionChanged", at = @At("TAIL"))
	private void myriad$onResize(CallbackInfo ci) {
		if (Myriad.isReady()) Myriad.events().post(new WindowResizeEvent(window.getFramebufferWidth(), window.getFramebufferHeight()));
	}
}
