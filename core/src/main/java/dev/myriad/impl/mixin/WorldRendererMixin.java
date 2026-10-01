package dev.myriad.impl.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.CameraEvent;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	/** Draw the local player when a module has moved the camera out of its head. */
	@WrapOperation(method = "getEntitiesToRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;isThirdPerson()Z"))
	private boolean myriad$detached(Camera camera, Operation<Boolean> original) {
		boolean third = original.call(camera);
		if (!Myriad.isReady() || !Myriad.events().hasListeners(CameraEvent.Detached.class)) return third;
		return Myriad.events().post(new CameraEvent.Detached(third)).renderSelf;
	}
}
