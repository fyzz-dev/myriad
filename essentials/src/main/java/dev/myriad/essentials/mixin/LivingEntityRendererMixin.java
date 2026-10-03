package dev.myriad.essentials.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.myriad.api.module.Modules;
import dev.myriad.api.render.RenderLayers;
import dev.myriad.api.render.RenderStates;
import dev.myriad.essentials.modules.render.Chams;
import dev.myriad.essentials.modules.render.ESP;
import dev.myriad.essentials.modules.render.NoRender;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	/** Chams: submit the model again as a flat colour (optionally through walls), with or without the original. */
	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
	private void essentials$chams(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack poseStack, RenderType renderType, int light, int overlay,
								  int color, TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumbling, Operation<Void> original) {
		if (!(state instanceof LivingEntityRenderState living)) {
			original.call(collector, model, state, poseStack, renderType, light, overlay, color, sprite, outlineColor, crumbling);
			return;
		}
		Chams chams = Modules.active(Chams.class);
		if (chams == null || !chams.appliesTo(RenderStates.entity(living))) {
			original.call(collector, model, state, poseStack, renderType, light, overlay, color, sprite, outlineColor, crumbling);
		} else {
			if (chams.keepTexture()) original.call(collector, model, state, poseStack, renderType, light, overlay, color, sprite, outlineColor, crumbling);
			if (chams.fill()) original.call(collector, model, state, poseStack, RenderLayers.flatModel(chams.throughWalls()), light, overlay, chams.fillColor(), null, 0, null);
		}
		// ESP's Complex and Shader modes draw the model again on top.
		if (Modules.active(ESP.class) instanceof ESP esp) esp.submitModel(collector, model, living, poseStack);
	}

	/** No Render: drop the red hurt tint, keep the white creeper flash. */
	@ModifyReturnValue(method = "getOverlayCoords", at = @At("RETURN"))
	private static int essentials$noDamageTint(int original, LivingEntityRenderState state, float whiteOverlayProgress) {
		if (!NoRender.hides(n -> n.damageTint)) return original;
		return OverlayTexture.pack(OverlayTexture.u(whiteOverlayProgress), OverlayTexture.v(false));
	}
}
