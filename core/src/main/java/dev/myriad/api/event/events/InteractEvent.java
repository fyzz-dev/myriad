package dev.myriad.api.event.events;

import dev.myriad.api.event.Cancellable;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

/**
 * The local player is about to right-click: a block, an item in the air, or an entity. Posted for clicks from the
 * mouse and from features alike. Cancel to stop the interaction.
 */
public abstract class InteractEvent extends Cancellable {
	private final Hand hand;

	protected InteractEvent(Hand hand) {
		this.hand = hand;
	}

	public Hand hand() {
		return hand;
	}

	/** Right-clicking a block face (placing, opening, using). */
	public static final class Block extends InteractEvent {
		private final BlockHitResult hit;

		public Block(Hand hand, BlockHitResult hit) {
			super(hand);
			this.hit = hit;
		}

		public BlockHitResult hit() {
			return hit;
		}
	}

	/** Using the held item without a block (eating, throwing, shooting). */
	public static final class Item extends InteractEvent {
		public Item(Hand hand) {
			super(hand);
		}
	}

	/** Right-clicking an entity (trading, mounting, naming). */
	public static final class EntityTarget extends InteractEvent {
		private final Entity entity;

		public EntityTarget(Hand hand, Entity entity) {
			super(hand);
			this.entity = entity;
		}

		public Entity entity() {
			return entity;
		}
	}
}
