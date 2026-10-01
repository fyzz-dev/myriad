package dev.myriad.impl.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.InputEvent;
import dev.myriad.api.event.events.MovementPacketsEvent;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets {@link MovementPacketsEvent} handlers rewrite what {@code sendMovementPackets} sends, without touching the
 * client-side player (so the camera doesn't move).
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
	@Unique
	private MovementPacketsEvent myriad$event;

	@Inject(method = "sendMovementPackets", at = @At("HEAD"))
	private void myriad$preMovement(CallbackInfo ci) {
		if (!Myriad.isReady()) return;
		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
		myriad$event = Myriad.events().post(new MovementPacketsEvent(self.getX(), self.getY(), self.getZ(), self.getYaw(), self.getPitch(), self.isOnGround()));
	}

	@Inject(method = "sendMovementPackets", at = @At("RETURN"))
	private void myriad$postMovement(CallbackInfo ci) {
		if (myriad$event == null) return;
		myriad$event = null;
		Myriad.events().post(MovementPacketsEvent.Post.INSTANCE);
	}

	/** Posts InputEvent right after the keyboard is read, and applies any changes for this tick. */
	@Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V", shift = At.Shift.AFTER))
	private void myriad$input(CallbackInfo ci) {
		if (!Myriad.isReady() || !Myriad.events().hasListeners(InputEvent.class)) return;
		Input input = ((ClientPlayerEntity) (Object) this).input;
		PlayerInput in = input.playerInput;
		InputEvent e = Myriad.events().post(new InputEvent(in.forward(), in.backward(), in.left(), in.right(), in.jump(), in.sneak(), in.sprint()));
		PlayerInput out = new PlayerInput(e.forward, e.backward, e.left, e.right, e.jump, e.sneak, e.sprint);
		if (out.equals(in)) return;
		input.playerInput = out;
		input.movementForward = e.forward == e.backward ? 0 : e.forward ? 1 : -1;
		input.movementSideways = e.left == e.right ? 0 : e.left ? 1 : -1;
	}

	@ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D"))
	private double myriad$x(double original) {
		return myriad$event != null ? myriad$event.x : original;
	}

	@ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getY()D"))
	private double myriad$y(double original) {
		return myriad$event != null ? myriad$event.y : original;
	}

	@ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D"))
	private double myriad$z(double original) {
		return myriad$event != null ? myriad$event.z : original;
	}

	@ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
	private float myriad$yaw(float original) {
		return myriad$event != null ? myriad$event.yaw : original;
	}

	@ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
	private float myriad$pitch(float original) {
		return myriad$event != null ? myriad$event.pitch : original;
	}

	@ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isOnGround()Z"))
	private boolean myriad$onGround(boolean original) {
		return myriad$event != null ? myriad$event.onGround : original;
	}
}
