package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.PacketEvent;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
	@Unique
	private static final ThreadLocal<Boolean> MYRIAD_RESENDING = ThreadLocal.withInitial(() -> false);

	@Shadow
	public abstract void send(Packet<?> packet, @Nullable PacketCallbacks callbacks, boolean flush);

	@Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V", at = @At("HEAD"), cancellable = true)
	private void myriad$onSend(Packet<?> packet, @Nullable PacketCallbacks callbacks, boolean flush, CallbackInfo ci) {
		if (MYRIAD_RESENDING.get() || !Myriad.isReady()) return;
		PacketEvent.Send event = Myriad.events().post(new PacketEvent.Send(packet));
		if (event.isCancelled()) {
			ci.cancel();
		} else if (event.packet() != packet) {
			ci.cancel();
			MYRIAD_RESENDING.set(true);
			try {
				send(event.packet(), callbacks, flush);
			} finally {
				MYRIAD_RESENDING.set(false);
			}
		}
	}

	/** Wraps packet handling so receive handlers can drop or replace packets. */
	@WrapMethod(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V")
	private void myriad$onReceive(ChannelHandlerContext ctx, Packet<?> packet, Operation<Void> original) {
		if (!Myriad.isReady()) {
			original.call(ctx, packet);
			return;
		}
		if (packet instanceof BundlePacket<?> bundle) {
			myriad$filterBundle(bundle);
			original.call(ctx, packet);
			return;
		}
		PacketEvent.Receive event = Myriad.events().post(new PacketEvent.Receive(packet));
		if (!event.isCancelled()) original.call(ctx, event.packet());
	}

	/** Runs each packet of a bundle through the event separately, dropping cancelled ones. */
	@Unique
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void myriad$filterBundle(BundlePacket<?> bundle) {
		List<Packet<?>> kept = new ArrayList<>();
		for (Packet<?> inner : bundle.getPackets()) {
			PacketEvent.Receive e = Myriad.events().post(new PacketEvent.Receive(inner));
			if (!e.isCancelled()) kept.add(e.packet());
		}
		((BundlePacket) bundle).packets = kept;
	}
}
