package dev.myriad.api.event.events;

import dev.myriad.api.event.Cancellable;
import net.minecraft.world.entity.Entity;

/**
 * You are about to attack an entity: posted just before the attack packet is sent, whoever sends it (vanilla
 * clicks, a module, {@code Interactions.attack}). Cancel to stop the attack. Packets you send from a handler go out
 * before the attack, which is how criticals work.
 */
public final class AttackEvent extends Cancellable {
	private final Entity target;

	public AttackEvent(Entity target) {
		this.target = target;
	}

	public Entity target() {
		return target;
	}
}
