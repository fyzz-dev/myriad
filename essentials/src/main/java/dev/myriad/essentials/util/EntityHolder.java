package dev.myriad.essentials.util;

import net.minecraft.entity.Entity;

/** Implemented on EntityRenderState by mixin. */
public interface EntityHolder {
	Entity essentials$getEntity();

	void essentials$setEntity(Entity entity);
}
