package dev.myriad.essentials.mixin;

import dev.myriad.api.module.Modules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.myriad.essentials.modules.render.Swing;
import dev.myriad.essentials.modules.render.ViewModel;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@Shadow
	private ItemStack mainHand;
	@Shadow
	private ItemStack offHand;
	@Shadow
	private float equipProgressMainHand;
	@Shadow
	private float prevEquipProgressMainHand;
	@Shadow
	private float equipProgressOffHand;
	@Shadow
	private float prevEquipProgressOffHand;
	@Shadow
	@Final
	private MinecraftClient client;

	/** Swing: no dip when switching items; show the new item straight away. */
	@Inject(method = "updateHeldItems", at = @At("TAIL"))
	private void essentials$noSwitchAnimation(CallbackInfo ci) {
		if (!Swing.noSwitchAnimation() || client.player == null) return;
		mainHand = client.player.getMainHandStack();
		offHand = client.player.getOffHandStack();
		equipProgressMainHand = prevEquipProgressMainHand = 1;
		equipProgressOffHand = prevEquipProgressOffHand = 1;
	}

	private static final String RENDER_ITEM = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V";

	/** The first two rotations in renderItem are the camera sway (pitch, then yaw). */
	@ModifyExpressionValue(method = RENDER_ITEM, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/RotationAxis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 0))
	private Quaternionf essentials$noPitchSway(Quaternionf original) {
		ViewModel vm = Modules.active(ViewModel.class);
		return vm != null && vm.noSway.get() ? new Quaternionf() : original;
	}

	@ModifyExpressionValue(method = RENDER_ITEM, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/RotationAxis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 1))
	private Quaternionf essentials$noYawSway(Quaternionf original) {
		ViewModel vm = Modules.active(ViewModel.class);
		return vm != null && vm.noSway.get() ? new Quaternionf() : original;
	}

	@WrapOperation(method = RENDER_ITEM, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
	private void essentials$viewModel(HeldItemRenderer renderer, AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swing,
									  ItemStack item, float equip, MatrixStack matrices, VertexConsumerProvider consumers, int light, Operation<Void> original) {
		ViewModel vm = Modules.active(ViewModel.class);
		if (vm == null) {
			original.call(renderer, player, tickDelta, pitch, hand, swing, item, equip, matrices, consumers, light);
			return;
		}
		boolean main = hand == Hand.MAIN_HAND;
		if (main ? vm.hideMain.get() : vm.hideOff.get()) return;
		Arm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
		float mirror = arm == Arm.RIGHT ? 1 : -1;
		matrices.push();
		matrices.translate(vm.x.get() * mirror, vm.y.get(), vm.z.get());
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(vm.rotY.get() * mirror));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(vm.rotX.get()));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(vm.rotZ.get() * mirror));
		float s = main ? vm.scale.getFloat() : vm.offhandScale.getFloat();
		if (s != 1) {
			// Scale around where the item sits rather than the camera, so it grows in place.
			float px = 0.56f * mirror, py = -0.52f, pz = -0.72f;
			matrices.translate(px, py, pz);
			matrices.scale(s, s, s);
			matrices.translate(-px, -py, -pz);
		}
		int l = vm.brighten.get() ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
		original.call(renderer, player, tickDelta, pitch, hand, swing, item, equip, matrices, consumers, l);
		matrices.pop();
	}
}
