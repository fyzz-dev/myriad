package dev.myriad.essentials.mixin;

import dev.myriad.essentials.util.GlideHold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/** Glide Hold: a teleport lets held ping answers go first. */
	@Inject(method = "handleMovePlayer", at = @At("HEAD"))
	private void essentials$teleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) GlideHold.onTeleport();
	}
}
