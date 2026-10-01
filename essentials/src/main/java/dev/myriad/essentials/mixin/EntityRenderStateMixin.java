package dev.myriad.essentials.mixin;

import dev.myriad.essentials.util.EntityHolder;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Since 1.21.2 renderers only get a snapshot state; remember which entity it came from. */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements EntityHolder {
	@Unique
	private Entity essentials$entity;

	@Override
	public Entity essentials$getEntity() {
		return essentials$entity;
	}

	@Override
	public void essentials$setEntity(Entity entity) {
		essentials$entity = entity;
	}
}
