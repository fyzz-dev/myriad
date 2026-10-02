package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.ChatSendEvent;
import dev.myriad.impl.MyriadImpl;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
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

	@Inject(method = "onOpenScreen", at = @At("TAIL"))
	private void myriad$containerOpened(OpenScreenS2CPacket packet, CallbackInfo ci) {
		if (Myriad.isReady() && MyriadImpl.get() != null) MyriadImpl.get().containerTracker().onOpened(packet.getSyncId());
	}

	@Inject(method = "onInventory", at = @At("TAIL"))
	private void myriad$containerContents(InventoryS2CPacket packet, CallbackInfo ci) {
		if (Myriad.isReady() && MyriadImpl.get() != null) MyriadImpl.get().containerTracker().onContents(packet.getSyncId());
	}

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
