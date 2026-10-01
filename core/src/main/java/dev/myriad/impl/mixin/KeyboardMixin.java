package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.CharEvent;
import dev.myriad.api.event.events.KeyEvent;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
	@Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
	private void myriad$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (window != mc.getWindow().getHandle() || !Myriad.isReady()) return;
		if (Myriad.events().post(new KeyEvent(key, scancode, action, modifiers, mc.currentScreen != null)).isCancelled()) ci.cancel();
	}

	@Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
	private void myriad$onChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (window != mc.getWindow().getHandle() || !Myriad.isReady()) return;
		if (Myriad.events().post(new CharEvent(codePoint, modifiers)).isCancelled()) ci.cancel();
	}
}
