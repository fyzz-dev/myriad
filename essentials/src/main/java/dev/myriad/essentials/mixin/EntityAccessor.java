package dev.myriad.essentials.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Glide Hold: the synced entity flags (sprinting, gliding, ...) the server sends. */
@Mixin(Entity.class)
public interface EntityAccessor {
	@Accessor("DATA_SHARED_FLAGS_ID")
	static EntityDataAccessor<Byte> essentials$sharedFlags() {
		throw new AssertionError();
	}
}
