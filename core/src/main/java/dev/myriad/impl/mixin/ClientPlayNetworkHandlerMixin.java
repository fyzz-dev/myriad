package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.ChatSendEvent;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
	@Unique
	private boolean myriad$resending;

	@Shadow
	public abstract void sendChatMessage(String content);

	@Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
	private void myriad$onChat(String message, CallbackInfo ci) {
		if (myriad$resending || !Myriad.isReady()) return;
		ChatSendEvent event = Myriad.events().post(new ChatSendEvent(message));
		if (event.isCancelled()) {
			ci.cancel();
		} else if (!event.message().equals(message)) {
			ci.cancel();
			myriad$resending = true;
			try {
				sendChatMessage(event.message());
			} finally {
				myriad$resending = false;
			}
		}
	}
}
