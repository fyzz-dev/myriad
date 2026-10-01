package dev.myriad.impl.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.CameraEvent;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
	private void myriad$rotation(Camera camera, float yaw, float pitch, Operation<Void> original, @Local(argsOnly = true) float tickDelta) {
		if (Myriad.isReady() && Myriad.events().hasListeners(CameraEvent.Rotation.class)) {
			CameraEvent.Rotation e = Myriad.events().post(new CameraEvent.Rotation(yaw, pitch, tickDelta));
			yaw = e.yaw;
			pitch = e.pitch;
		}
		original.call(camera, yaw, pitch);
	}

	@WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
	private void myriad$position(Camera camera, double x, double y, double z, Operation<Void> original, @Local(argsOnly = true) float tickDelta) {
		if (Myriad.isReady() && Myriad.events().hasListeners(CameraEvent.Position.class)) {
			CameraEvent.Position e = Myriad.events().post(new CameraEvent.Position(x, y, z, tickDelta));
			x = e.x;
			y = e.y;
			z = e.z;
		}
		original.call(camera, x, y, z);
	}
}
