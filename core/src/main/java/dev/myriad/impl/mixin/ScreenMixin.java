package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.impl.MyriadImpl;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {
	/** Vanilla only runs "/" commands from clicked chat text; run Myriad's (from Texts.command) too. */
	@Inject(method = "handleTextClick", at = @At("HEAD"), cancellable = true)
	private void myriad$runCommand(Style style, CallbackInfoReturnable<Boolean> cir) {
		if (style == null || !Myriad.isReady() || MyriadImpl.get() == null) return;
		ClickEvent click = style.getClickEvent();
		if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND) return;
		String prefix = Myriad.config().commandPrefix();
		if (prefix.isEmpty() || !click.getValue().startsWith(prefix)) return;
		MyriadImpl.get().commandManager().execute(click.getValue().substring(prefix.length()));
		cir.setReturnValue(true);
	}
}
