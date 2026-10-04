package dev.myriad.impl.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.AttackEvent;
import dev.myriad.api.event.events.PacketEvent;
import dev.myriad.impl.network.PacketGate;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.entity.Entity;

@Mixin(Connection.class)
public abstract class ClientConnectionMixin {
	@Unique
	private static final ThreadLocal<Boolean> MYRIAD_RESENDING = ThreadLocal.withInitial(() -> false);

	@Shadow
	public abstract PacketFlow getReceiving();

	/** Only the client's end: in singleplayer the integrated server's connections run through here too. */
	@Unique
	private boolean myriad$isClientSide() {
		return getReceiving() == PacketFlow.CLIENTBOUND;
	}

	@Shadow
	public abstract void send(Packet<?> packet, @Nullable ChannelFutureListener callbacks, boolean flush);

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
	private void myriad$onSend(Packet<?> packet, @Nullable ChannelFutureListener callbacks, boolean flush, CallbackInfo ci) {
		if (MYRIAD_RESENDING.get() || PacketGate.isSilent() || !Myriad.isReady() || !myriad$isClientSide()) return;
		if (packet instanceof ServerboundAttackPacket attack && Myriad.events().hasListeners(AttackEvent.class)) {
			Minecraft mc = Minecraft.getInstance();
			Entity target = mc.level == null ? null : mc.level.getEntity(attack.entityId());
			if (target != null && Myriad.events().post(new AttackEvent(target)).isCancelled()) {
				ci.cancel();
				return;
			}
		}
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
	@WrapMethod(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V")
	private void myriad$onReceive(ChannelHandlerContext ctx, Packet<?> packet, Operation<Void> original) {
		if (!Myriad.isReady() || !myriad$isClientSide()) {
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
		for (Packet<?> inner : bundle.subPackets()) {
			PacketEvent.Receive e = Myriad.events().post(new PacketEvent.Receive(inner));
			if (!e.isCancelled()) kept.add(e.packet());
		}
		((BundlePacket) bundle).packets = kept;
	}
}
