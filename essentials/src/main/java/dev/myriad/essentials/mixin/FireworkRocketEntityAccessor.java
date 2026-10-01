package dev.myriad.essentials.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
	@Accessor("shooter")
	LivingEntity myriad$getShooter();

	@Accessor("life")
	int myriad$getLife();

	@Accessor("lifeTime")
	int myriad$getLifeTime();

	@Invoker("wasShotByEntity")
	boolean myriad$wasShotByEntity();
}
