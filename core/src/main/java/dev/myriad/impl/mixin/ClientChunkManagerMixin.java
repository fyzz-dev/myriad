package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.ChunkEvent;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ClientChunkManager.class)
public abstract class ClientChunkManagerMixin {
	@Inject(method = "loadChunkFromPacket", at = @At("RETURN"))
	private void myriad$loaded(int x, int z, PacketByteBuf buf, NbtCompound heightmaps, Consumer<ChunkData.BlockEntityVisitor> visitor,
							   CallbackInfoReturnable<WorldChunk> cir) {
		WorldChunk chunk = cir.getReturnValue();
		if (chunk != null && Myriad.isReady() && Myriad.events().hasListeners(ChunkEvent.Loaded.class)) Myriad.events().post(new ChunkEvent.Loaded(chunk));
	}

	@Inject(method = "unload", at = @At("HEAD"))
	private void myriad$unloaded(ChunkPos pos, CallbackInfo ci) {
		if (Myriad.isReady() && Myriad.events().hasListeners(ChunkEvent.Unloaded.class)) Myriad.events().post(new ChunkEvent.Unloaded(pos));
	}
}
