package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.DisconnectEvent;
import dev.myriad.impl.MyriadImpl;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerMixin {
	/** DisconnectEvent, before the disconnect screen replaces the world. */
	@Inject(method = "onDisconnect", at = @At("HEAD"))
	private void myriad$disconnect(DisconnectionDetails details, CallbackInfo ci) {
		if (!Myriad.isReady()) return;
		MyriadImpl.get().serverStatsTracker().rememberServer();
		Myriad.events().post(new DisconnectEvent(details.reason(), Myriad.server().address()));
	}
}
