package dev.myriad.impl.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.InputEvent;
import dev.myriad.api.event.events.MovementPacketsEvent;
import dev.myriad.api.event.events.PlayerMoveEvent;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets {@link MovementPacketsEvent} handlers rewrite what {@code sendMovementPackets} sends, without touching the
 * client-side player (so the camera doesn't move).
 */
@Mixin(LocalPlayer.class)
public abstract class ClientPlayerEntityMixin {
	@Unique
	private MovementPacketsEvent myriad$event;

	@Inject(method = "sendPosition", at = @At("HEAD"))
	private void myriad$preMovement(CallbackInfo ci) {
		if (!Myriad.isReady()) return;
		LocalPlayer self = (LocalPlayer) (Object) this;
		myriad$event = Myriad.events().post(new MovementPacketsEvent(self.getX(), self.getY(), self.getZ(), self.getYRot(), self.getXRot(), self.onGround()));
	}

	@Inject(method = "sendPosition", at = @At("RETURN"))
	private void myriad$postMovement(CallbackInfo ci) {
		if (myriad$event == null) return;
		myriad$event = null;
		Myriad.events().post(MovementPacketsEvent.Post.INSTANCE);
	}

	/** PlayerMoveEvent: lets features change this tick's movement before collisions. */
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
	private Vec3 myriad$move(Vec3 movement, MoverType type, Vec3 original) {
		if (!Myriad.isReady() || !Myriad.events().hasListeners(PlayerMoveEvent.class)) return movement;
		return Myriad.events().post(new PlayerMoveEvent(type, movement)).movement();
	}

	/** Posts InputEvent right after the keyboard is read, and applies any changes for this tick. */
	@Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER))
	private void myriad$input(CallbackInfo ci) {
		if (!Myriad.isReady() || !Myriad.events().hasListeners(InputEvent.class)) return;
		ClientInput input = ((LocalPlayer) (Object) this).input;
		Input in = input.keyPresses;
		InputEvent e = Myriad.events().post(new InputEvent(in.forward(), in.backward(), in.left(), in.right(), in.jump(), in.shift(), in.sprint()));
		Input out = new Input(e.forward, e.backward, e.left, e.right, e.jump, e.sneak, e.sprint);
		if (out.equals(in)) return;
		input.keyPresses = out;
		float forward = e.forward == e.backward ? 0 : e.forward ? 1 : -1;
		float left = e.left == e.right ? 0 : e.left ? 1 : -1;
		input.moveVector = new Vec2(left, forward).normalized();
	}

	@ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
	private double myriad$x(double original) {
		return myriad$event != null ? myriad$event.x : original;
	}

	@ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
	private double myriad$y(double original) {
		return myriad$event != null ? myriad$event.y : original;
	}

	@ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
	private double myriad$z(double original) {
		return myriad$event != null ? myriad$event.z : original;
	}

	@ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
	private float myriad$yaw(float original) {
		return myriad$event != null ? myriad$event.yaw : original;
	}

	@ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
	private float myriad$pitch(float original) {
		return myriad$event != null ? myriad$event.pitch : original;
	}

	@ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;onGround()Z"))
	private boolean myriad$onGround(boolean original) {
		return myriad$event != null ? myriad$event.onGround : original;
	}
}
