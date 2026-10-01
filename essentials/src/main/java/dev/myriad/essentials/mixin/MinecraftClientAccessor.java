package dev.myriad.essentials.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
	@Accessor("itemUseCooldown")
	int myriad$getItemUseCooldown();

	@Accessor("itemUseCooldown")
	void myriad$setItemUseCooldown(int value);
}
