package dev.myriad.essentials.mixin;

import dev.myriad.api.module.Modules;
import dev.myriad.essentials.modules.render.NoRender;
import dev.myriad.essentials.modules.render.ShulkerPreview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
	private void essentials$vignette(DrawContext context, Entity entity, CallbackInfo ci) {
		if (NoRender.hides(n -> n.vignette)) ci.cancel();
	}

	@Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
	private void essentials$portal(DrawContext context, float nauseaStrength, CallbackInfo ci) {
		if (NoRender.hides(n -> n.portal)) ci.cancel();
	}

	@Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
	private void essentials$pumpkin(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
		if (texture.getPath().contains("pumpkinblur") && NoRender.hides(n -> n.pumpkin)) ci.cancel();
	}

	@Inject(method = "renderHotbar", at = @At("TAIL"))
	private void essentials$hotbarShulkers(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ShulkerPreview preview = Modules.active(ShulkerPreview.class);
		if (preview == null || !preview.hotbarIcons() || mc.player == null) return;
		int x = context.getScaledWindowWidth() / 2 - 90, y = context.getScaledWindowHeight() - 19;
		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getStack(i);
			if (ShulkerPreview.isShulker(stack)) ShulkerPreview.drawIcon(context, stack, x + i * 20 + 2, y);
		}
	}

	@Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
	private void essentials$statusEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (NoRender.hides(n -> n.statusEffects)) ci.cancel();
	}

	@Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
	private void essentials$xpBar(DrawContext context, int x, CallbackInfo ci) {
		if (NoRender.hides(n -> n.xpBar)) ci.cancel();
	}

	@Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
	private void essentials$xpLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (NoRender.hides(n -> n.xpBar)) ci.cancel();
	}

	@Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
	private void essentials$itemName(DrawContext context, CallbackInfo ci) {
		if (NoRender.hides(n -> n.itemName)) ci.cancel();
	}
}
