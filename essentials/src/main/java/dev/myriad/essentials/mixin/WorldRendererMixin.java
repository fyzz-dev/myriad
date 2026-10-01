package dev.myriad.essentials.mixin;

import dev.myriad.essentials.modules.render.NoRender;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	@Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
	private void essentials$noWeather(FrameGraphBuilder frameGraphBuilder, Vec3d pos, float tickDelta, Fog fog, CallbackInfo ci) {
		if (NoRender.hides(n -> n.weather)) ci.cancel();
	}

	@Inject(method = "addWeatherParticlesAndSound", at = @At("HEAD"), cancellable = true)
	private void essentials$noWeatherParticles(Camera camera, CallbackInfo ci) {
		if (NoRender.hides(n -> n.weather)) ci.cancel();
	}
}
