package dev.myriad.essentials.mixin;

import dev.myriad.api.module.Modules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.myriad.essentials.modules.movement.ElytraBounce;
import dev.myriad.essentials.modules.movement.NoSlow;
import dev.myriad.essentials.modules.movement.Velocity;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.myriad.essentials.modules.render.Chams;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
	@Shadow
	protected Vec3 stuckSpeedMultiplier;

	@Shadow
	public abstract void resetFallDistance();

	/** Velocity No Push: other entities don't shove the local player. */
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void essentials$noEntityPush(Entity entity, CallbackInfo ci) {
		Object self = this;
		if (self == Minecraft.getInstance().player && Velocity.cancelsEntityPush()) ci.cancel();
	}

	/** No Slow webs: replace the cobweb/berry bush slowdown. */
	@Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
	private void essentials$noWebSlow(BlockState state, Vec3 multiplier, CallbackInfo ci) {
		Object self = this;
		if (self != Minecraft.getInstance().player) return;
		Vec3 replaced = NoSlow.webMultiplier(state, multiplier);
		if (replaced == null) return;
		resetFallDistance();
		stuckSpeedMultiplier = replaced;
		ci.cancel();
	}

	/** Chams glow: the vanilla outline takes the entity's team colour, so recolour it. */
	@Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
	private void essentials$glowColor(CallbackInfoReturnable<Integer> cir) {
		Chams chams = Modules.active(Chams.class);
		if (chams != null && chams.glowsFor((Entity) (Object) this)) cir.setReturnValue(chams.glowColor() & 0xFFFFFF);
	}

	/** Elytra Bounce Silent: walking between bounces follows the lane, not the camera. */
	@ModifyExpressionValue(method = "moveRelative", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
	private float essentials$moveYaw(float original) {
		Object self = this;
		return self == Minecraft.getInstance().player && ElytraBounce.spoofing() ? ElytraBounce.spoofYaw() : original;
	}

	/** Elytra Bounce Packet: keep the standing pose (and hitbox) while gliding. */
	@ModifyReturnValue(method = "getPose", at = @At("RETURN"))
	private Pose essentials$standingPose(Pose original) {
		Object self = this;
		return original == Pose.FALL_FLYING && self == Minecraft.getInstance().player && ElytraBounce.holdStandingPose() ? Pose.STANDING : original;
	}
}
