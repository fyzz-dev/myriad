package dev.myriad.impl.mixin;

import net.minecraft.client.gui.hud.ChatHudLine;
import dev.myriad.impl.ChatLines;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.ChatReceiveEvent;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
	@Unique
	private boolean myriad$readding;

	@Shadow
	public abstract void addMessage(Text message, MessageSignatureData signature, MessageIndicator indicator);

	@Inject(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", at = @At("HEAD"))
	private void myriad$lineAdded(ChatHudLine line, CallbackInfo ci) {
		ChatLines.onLineAdded(line);
	}

	@Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
		at = @At("HEAD"), cancellable = true)
	private void myriad$receive(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
		if (myriad$readding || !Myriad.isReady() || !Myriad.events().hasListeners(ChatReceiveEvent.class)) return;
		ChatReceiveEvent event = Myriad.events().post(new ChatReceiveEvent(message));
		if (event.isCancelled()) {
			ci.cancel();
		} else if (event.message() != message) {
			ci.cancel();
			myriad$readding = true;
			try {
				addMessage(event.message(), signature, indicator);
			} finally {
				myriad$readding = false;
			}
		}
	}
}
