package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.ContainerScreenEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	protected @Nullable Slot hoveredSlot;

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
	private void myriad$tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (Myriad.isReady() && Myriad.events().hasListeners(ContainerScreenEvent.Tooltip.class)
			&& Myriad.events().post(new ContainerScreenEvent.Tooltip((AbstractContainerScreen<?>) (Object) this, hoveredSlot, graphics, mouseX, mouseY)).isCancelled()) ci.cancel();
	}

	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void myriad$slot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (Myriad.isReady() && Myriad.events().hasListeners(ContainerScreenEvent.SlotDrawn.class)) {
			Myriad.events().post(new ContainerScreenEvent.SlotDrawn((AbstractContainerScreen<?>) (Object) this, slot, graphics));
		}
	}

	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void myriad$click(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
		if (Myriad.isReady() && Myriad.events().hasListeners(ContainerScreenEvent.Click.class)
			&& Myriad.events().post(new ContainerScreenEvent.Click((AbstractContainerScreen<?>) (Object) this, slot, slotId, button, input)).isCancelled()) ci.cancel();
	}
}
