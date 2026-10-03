package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.ChatSendEvent;
import dev.myriad.impl.MyriadImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {
	@Unique
	private boolean myriad$resending;

	@Shadow
	public abstract void sendChat(String content);

	@Inject(method = "handleOpenScreen", at = @At("TAIL"))
	private void myriad$containerOpened(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
		if (Myriad.isReady() && MyriadImpl.get() != null) MyriadImpl.get().containerTracker().onOpened(packet.getContainerId());
	}

	@Inject(method = "handleContainerContent", at = @At("TAIL"))
	private void myriad$containerContents(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
		if (Myriad.isReady() && MyriadImpl.get() != null) MyriadImpl.get().containerTracker().onContents(packet.containerId());
	}

	@Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
	private void myriad$containerSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
		if (Myriad.isReady() && MyriadImpl.get() != null) MyriadImpl.get().containerTracker().onSlot(packet.getContainerId(), packet.getSlot());
	}

	@Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
	private void myriad$onChat(String message, CallbackInfo ci) {
		if (myriad$resending || !Myriad.isReady()) return;
		ChatSendEvent event = Myriad.events().post(new ChatSendEvent(message));
		if (event.isCancelled()) {
			ci.cancel();
		} else if (!event.message().equals(message)) {
			ci.cancel();
			myriad$resending = true;
			try {
				sendChat(event.message());
			} finally {
				myriad$resending = false;
			}
		}
	}
}
