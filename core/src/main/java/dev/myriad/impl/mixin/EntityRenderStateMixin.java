package dev.myriad.impl.mixin;

import dev.myriad.impl.render.EntityRenderStateAccess;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements EntityRenderStateAccess {
	@Unique
	private Entity myriad$entity;

	@Override
	public Entity myriad$entity() {
		return myriad$entity;
	}

	@Override
	public void myriad$setEntity(Entity entity) {
		myriad$entity = entity;
	}
}
