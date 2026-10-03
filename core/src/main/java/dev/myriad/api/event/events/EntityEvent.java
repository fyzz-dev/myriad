package dev.myriad.api.event.events;

import net.minecraft.world.entity.Entity;

/** Entities entering and leaving the client world. Posted on the render thread. */
public abstract class EntityEvent {
	private final Entity entity;

	protected EntityEvent(Entity entity) {
		this.entity = entity;
	}

	public Entity entity() {
		return entity;
	}

	/** An entity was added to the world (spawned, or came into view). */
	public static final class Added extends EntityEvent {
		public Added(Entity entity) {
			super(entity);
		}
	}

	/** An entity is about to be removed; it's still in the world while handlers run. */
	public static final class Removed extends EntityEvent {
		private final Entity.RemovalReason reason;

		public Removed(Entity entity, Entity.RemovalReason reason) {
			super(entity);
			this.reason = reason;
		}

		public Entity.RemovalReason reason() {
			return reason;
		}
	}
}
