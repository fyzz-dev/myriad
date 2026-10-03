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
import dev.myriad.api.util.Movement;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;

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
			boolean water = p.isInWater() || p.isUnderWater();
			if (water && mode.get() == Mode.LEGIT) {
				if (!waterToggle.get() && p.input.hasForwardImpulse() && !p.isShiftKeyDown()) p.setSprinting(true);
				return;
			}
			p.setSprinting(false);
			return;
		}
		if (mode.get() == Mode.RAGE && rotate.get() && moving()) {
			Myriad.rotations().request(this, sprintYaw(p.getYRot()), p.getXRot(), Rotations.PRIORITY_LOW);
		}
		p.setSprinting(true);
	}

	private boolean moving() {
		var in = mc.player.input.keyPresses;
		return in.forward() || in.backward() || in.left() || in.right();
	}

	private boolean canSprint() {
		var p = mc.player;
		if (inWeb()) return false;
		boolean input = mode.get() == Mode.LEGIT ? p.input.hasForwardImpulse() : moving();
		boolean angle = mode.get() != Mode.LEGIT || !Myriad.rotations().isRotating()
			|| Math.abs(Mth.wrapDegrees(p.getYRot() - Myriad.rotations().serverYaw())) < 1f;
		return input && angle && !p.isShiftKeyDown() && !p.isPassenger() && !p.isFallFlying() && !p.isInWater() && !p.isUnderWater()
			&& !p.isInLava() && !p.isSuppressingSlidingDownLadder() && !p.hasEffect(MobEffects.BLINDNESS) && p.getFoodData().getFoodLevel() > 6;
	}

	private boolean noCollision() {
		return !mc.player.horizontalCollision || mc.player.minorHorizontalCollision;
	}

	private boolean inWeb() {
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(mc.player.getBoundingBox().minX, mc.player.getBoundingBox().minY, mc.player.getBoundingBox().minZ),
			BlockPos.containing(mc.player.getBoundingBox().maxX, mc.player.getBoundingBox().maxY, mc.player.getBoundingBox().maxZ))) {
			if (mc.level.getBlockState(pos).is(Blocks.COBWEB)) return true;
		}
		return false;
	}

	private float sprintYaw(float yaw) {
		float moving = Movement.inputYaw(yaw);
		return Mth.wrapDegrees(Float.isNaN(moving) ? yaw : moving);
	}
}
