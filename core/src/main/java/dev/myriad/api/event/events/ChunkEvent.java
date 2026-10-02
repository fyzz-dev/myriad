package dev.myriad.api.event.events;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

/** Chunks arriving from and leaving the client world. Posted on the render thread. */
public abstract class ChunkEvent {
	private final ChunkPos pos;

	protected ChunkEvent(ChunkPos pos) {
		this.pos = pos;
	}

	public ChunkPos pos() {
		return pos;
	}

	/** A chunk's blocks and block entities arrived. Scan it here instead of rescanning the world. */
	public static final class Loaded extends ChunkEvent {
		private final WorldChunk chunk;

		public Loaded(WorldChunk chunk) {
			super(chunk.getPos());
			this.chunk = chunk;
		}

		public WorldChunk chunk() {
			return chunk;
		}
	}

	/** A chunk is being dropped from the client. */
	public static final class Unloaded extends ChunkEvent {
		public Unloaded(ChunkPos pos) {
			super(pos);
		}
	}
}
