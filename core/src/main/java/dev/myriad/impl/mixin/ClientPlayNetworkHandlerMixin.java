package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.ChatSendEvent;
import dev.myriad.api.event.events.HealthEvent;
import dev.myriad.api.event.events.TeleportEvent;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import dev.myriad.impl.MyriadImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
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

	/** TeleportEvent, on the render thread (the packet first arrives on the network thread and is handed over). */
	@Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	private void myriad$teleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		Player player = Minecraft.getInstance().player;
		if (!Myriad.isReady() || player == null || player.isPassenger()) return;
		PositionMoveRotation now = PositionMoveRotation.of(player);
		Vec3 to = PositionMoveRotation.calculateAbsolute(now, packet.change(), packet.relatives()).position();
		Myriad.events().post(new TeleportEvent(now.position(), to));
	}

	/** HealthEvent, with the values before the packet is applied. */
	@Inject(method = "handleSetHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	private void myriad$health(ClientboundSetHealthPacket packet, CallbackInfo ci) {
		Player player = Minecraft.getInstance().player;
		if (!Myriad.isReady() || player == null || !Myriad.events().hasListeners(HealthEvent.class)) return;
		Myriad.events().post(new HealthEvent(player.getHealth(), packet.getHealth(), player.getFoodData().getFoodLevel(), packet.getFood()));
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
