package dev.myriad.essentials.modules.movement;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Priority;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.service.Rotations;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.EnumSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Sprints for you. Legit only sprints forwards, like holding the sprint key, and can leave swimming to you. Rage
 * sprints in every direction and keeps sprinting into walls; with Rotate it faces the server along your movement so
 * that sideways sprinting looks legitimate.
 */
public class Sprint extends Module {

	public enum Mode {
		LEGIT, RAGE
	}

	private final EnumSetting<Mode> mode = sgGeneral.enumSetting("Mode", Mode.LEGIT).build();
	private final BoolSetting rotate = sgGeneral.bool("Rotate").description("Face the server along your movement direction.").visible(() -> mode.get() == Mode.RAGE).build();
	private final BoolSetting waterToggle = sgGeneral.bool("Water Toggle").description("Leave swimming to the sprint key.").defaultValue(true).visible(() -> mode.get() == Mode.LEGIT).build();

	public Sprint() {
		super(Categories.MOVEMENT, "Sprint", "Sprints automatically.");
	}

	@Override
	public String hudInfo() {
		return mode.get() == Mode.RAGE ? "Rage" : "Legit";
	}

	/** Whether vanilla should be stopped from cancelling the sprint this tick. */
	public static boolean keepSprinting() {
		Sprint m = Modules.active(Sprint.class);
		return m != null && m.canSprint() && (m.mode.get() == Mode.RAGE || m.noCollision());
	}

	@Subscribe(priority = Priority.HIGHEST)
	private void onTick(TickEvent.Post e) {
		if (!inGame()) return;
		var p = mc.player;
		if (!canSprint() || !noCollision()) {
			boolean water = p.isTouchingWater() || p.isSubmergedInWater();
			if (water && mode.get() == Mode.LEGIT) {
				if (!waterToggle.get() && p.input.hasForwardMovement() && !p.isSneaking()) p.setSprinting(true);
				return;
			}
			p.setSprinting(false);
			return;
		}
		if (mode.get() == Mode.RAGE && rotate.get() && moving()) {
			Myriad.rotations().request(this, sprintYaw(p.getYaw()), p.getPitch(), Rotations.PRIORITY_LOW);
		}
		p.setSprinting(true);
	}

	private boolean moving() {
		var in = mc.player.input.playerInput;
		return in.forward() || in.backward() || in.left() || in.right();
	}

	private boolean canSprint() {
		var p = mc.player;
		if (inWeb()) return false;
		boolean input = mode.get() == Mode.LEGIT ? p.input.hasForwardMovement() : moving();
		boolean angle = mode.get() != Mode.LEGIT || !Myriad.rotations().isRotating()
			|| Math.abs(MathHelper.wrapDegrees(p.getYaw() - Myriad.rotations().serverYaw())) < 1f;
		return input && angle && !p.isSneaking() && !p.hasVehicle() && !p.isGliding() && !p.isTouchingWater() && !p.isSubmergedInWater()
			&& !p.isInLava() && !p.isHoldingOntoLadder() && !p.hasStatusEffect(StatusEffects.BLINDNESS) && p.getHungerManager().getFoodLevel() > 6;
	}

	private boolean noCollision() {
		return !mc.player.horizontalCollision || mc.player.collidedSoftly;
	}

	private boolean inWeb() {
		for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(mc.player.getBoundingBox().minX, mc.player.getBoundingBox().minY, mc.player.getBoundingBox().minZ),
			BlockPos.ofFloored(mc.player.getBoundingBox().maxX, mc.player.getBoundingBox().maxY, mc.player.getBoundingBox().maxZ))) {
			if (mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) return true;
		}
		return false;
	}

	private float sprintYaw(float yaw) {
		var in = mc.player.input.playerInput;
		boolean f = in.forward(), b = in.backward(), l = in.left(), r = in.right();
		if (f && !b) {
			if (l && !r) yaw -= 45;
			else if (r && !l) yaw += 45;
		} else if (b && !f) {
			yaw += 180;
			if (l && !r) yaw += 45;
			else if (r && !l) yaw -= 45;
		} else if (l && !r) yaw -= 90;
		else if (r && !l) yaw += 90;
		return MathHelper.wrapDegrees(yaw);
	}
}
