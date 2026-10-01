package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.MouseButtonEvent;
import dev.myriad.api.event.events.MouseScrollEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.myriad.api.event.events.MouseLookEvent;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
	@Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
	private void myriad$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (window != mc.getWindow().getHandle() || !Myriad.isReady()) return;
		if (Myriad.events().post(new MouseButtonEvent(button, action, mods, mc.currentScreen != null)).isCancelled()) ci.cancel();
	}

	@WrapOperation(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
	private void myriad$look(ClientPlayerEntity player, double dx, double dy, Operation<Void> original) {
		if (Myriad.isReady() && Myriad.events().hasListeners(MouseLookEvent.class)) {
			MouseLookEvent e = Myriad.events().post(new MouseLookEvent(dx, dy));
			if (e.isCancelled()) return;
			dx = e.deltaX();
			dy = e.deltaY();
		}
		original.call(player, dx, dy);
	}

	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void myriad$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (window != mc.getWindow().getHandle() || !Myriad.isReady()) return;
		if (Myriad.events().post(new MouseScrollEvent(horizontal, vertical)).isCancelled()) ci.cancel();
	}
}
