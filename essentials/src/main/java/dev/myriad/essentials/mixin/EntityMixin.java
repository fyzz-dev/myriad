package dev.myriad.essentials.mixin;

import dev.myriad.api.module.Modules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.myriad.essentials.modules.movement.ElytraBounce;
import dev.myriad.essentials.modules.movement.NoSlow;
import dev.myriad.essentials.modules.movement.Velocity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.myriad.essentials.modules.render.Chams;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
	@Shadow
	protected Vec3d movementMultiplier;

	@Shadow
	public abstract void onLanding();

	/** Velocity No Push: other entities don't shove the local player. */
	@Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
	private void essentials$noEntityPush(Entity entity, CallbackInfo ci) {
		Object self = this;
		if (self == MinecraftClient.getInstance().player && Velocity.cancelsEntityPush()) ci.cancel();
	}

	/** No Slow webs: replace the cobweb/berry bush slowdown. */
	@Inject(method = "slowMovement", at = @At("HEAD"), cancellable = true)
	private void essentials$noWebSlow(BlockState state, Vec3d multiplier, CallbackInfo ci) {
		Object self = this;
		if (self != MinecraftClient.getInstance().player) return;
		Vec3d replaced = NoSlow.webMultiplier(state, multiplier);
		if (replaced == null) return;
		onLanding();
		movementMultiplier = replaced;
		ci.cancel();
	}

	/** Chams glow: the vanilla outline takes the entity's team colour, so recolour it. */
	@Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true)
	private void essentials$glowColor(CallbackInfoReturnable<Integer> cir) {
		Chams chams = Modules.active(Chams.class);
		if (chams != null && chams.glowsFor((Entity) (Object) this)) cir.setReturnValue(chams.glowColor() & 0xFFFFFF);
	}

	/** Elytra Bounce Silent: walking between bounces follows the lane, not the camera. */
	@ModifyExpressionValue(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
	private float essentials$moveYaw(float original) {
		Object self = this;
		return self == MinecraftClient.getInstance().player && ElytraBounce.spoofing() ? ElytraBounce.spoofYaw() : original;
	}

	/** Elytra Bounce Packet: keep the standing pose (and hitbox) while gliding. */
	@ModifyReturnValue(method = "getPose", at = @At("RETURN"))
	private EntityPose essentials$standingPose(EntityPose original) {
		Object self = this;
		return original == EntityPose.GLIDING && self == MinecraftClient.getInstance().player && ElytraBounce.holdStandingPose() ? EntityPose.STANDING : original;
	}
}
