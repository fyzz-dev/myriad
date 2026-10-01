package dev.myriad.essentials.modules.movement;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.MovementPacketsEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.EnumSetting;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.MathHelper;

/**
 * Flight in survival. Normal sets your velocity directly (WASD, jump, sneak); Vanilla turns on creative-style flying.
 * Anti Kick drops you a little whenever you've hovered for a second, since vanilla servers kick players who float
 * for 80 ticks: Normal moves you down, Packet only tells the server you dropped.
 */
public class Flight extends Module {
	public enum Mode {
		NORMAL, VANILLA
	}

	public enum AntiKick {
		NORMAL, PACKET, OFF
	}

	private final EnumSetting<Mode> mode = sgGeneral.enumSetting("Mode", Mode.NORMAL).build();
	private final DoubleSetting speed = sgGeneral.doubleSetting("Speed").description("Horizontal speed.").defaultValue(2.5).range(0.1, 10).decimals(1).build();
	private final DoubleSetting verticalSpeed = sgGeneral.doubleSetting("Vertical Speed").defaultValue(1).range(0.1, 5).decimals(1).build();
	private final EnumSetting<AntiKick> antiKick = sgGeneral.enumSetting("Anti Kick", AntiKick.NORMAL).build();
	private final BoolSetting accelerate = sgGeneral.bool("Accelerate").description("Build up speed as you fly.").build();
	private final DoubleSetting accelerateSpeed = sgGeneral.doubleSetting("Accelerate Speed").defaultValue(0.2).range(0.01, 1).decimals(2).visible(accelerate::get).build();
	private final DoubleSetting maxSpeed = sgGeneral.doubleSetting("Max Speed").defaultValue(5).range(1, 10).decimals(1).visible(accelerate::get).build();

	private double currentSpeed, lastY;
	private boolean floating, modifyY, hasLastY;
	private int floatingTicks;

	public Flight() {
		super(Categories.MOVEMENT, "Flight", "Lets you fly in survival.");
	}

	@Override
	public String hudInfo() {
		return mode.get() == Mode.NORMAL ? "Normal" : "Vanilla";
	}

	@Override
	protected void onEnable() {
		currentSpeed = 0;
		hasLastY = false;
		if (inGame() && mode.get() == Mode.VANILLA) setVanillaFly(true);
	}

	@Override
	protected void onDisable() {
		if (inGame()) setVanillaFly(false);
		modifyY = false;
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) return;
		var p = mc.player;
		var in = p.input.playerInput;
		boolean moving = in.forward() || in.backward() || in.left() || in.right();
		if (accelerate.get()) {
			if (!moving || p.horizontalCollision) currentSpeed = 0;
			currentSpeed = Math.min(currentSpeed + accelerateSpeed.get(), maxSpeed.get());
		} else {
			currentSpeed = speed.get();
		}

		if (mode.get() == Mode.VANILLA) {
			setVanillaFly(true);
			p.getAbilities().setFlySpeed((float) (currentSpeed * 0.05));
		} else {
			setVanillaFly(false);
		}

		boolean stopVertical = false;
		if (floating && ++floatingTicks >= 20) {
			if (antiKick.get() == AntiKick.PACKET) modifyY = true;
			else if (antiKick.get() == AntiKick.NORMAL) {
				p.setPosition(p.getX(), p.getY() - 0.0313, p.getZ());
				if (mode.get() == Mode.VANILLA) {
					setVanillaFly(false);
					p.setVelocity(p.getVelocity().x, 0, p.getVelocity().z);
					stopVertical = true;
				}
			}
			floatingTicks = 0;
			floating = false;
		}

		if (mode.get() != Mode.NORMAL || p.isGliding()) return;
		double vy = 0;
		if (mc.options.jumpKey.isPressed() && !stopVertical) vy = verticalSpeed.get();
		else if (mc.options.sneakKey.isPressed()) vy = -verticalSpeed.get();
		double h = Math.max(currentSpeed, 0.2873);
		float forward = p.input.movementForward, strafe = p.input.movementSideways;
		if (forward == 0 && strafe == 0) {
			p.setVelocity(0, vy, 0);
			return;
		}
		double rx = Math.cos(Math.toRadians(p.getYaw() + 90)), rz = Math.sin(Math.toRadians(p.getYaw() + 90));
		p.setVelocity(forward * h * rx + strafe * h * rz, vy, forward * h * rz - strafe * h * rx);
	}

	@Subscribe
	private void onMovement(MovementPacketsEvent e) {
		if (antiKick.get() == AntiKick.OFF || !inGame()) return;
		if (modifyY && hasLastY) {
			e.y = lastY - 0.04;
			modifyY = false;
			return;
		}
		double y = MathHelper.clamp(e.y, -2.0E7, 2.0E7);
		floating = hasLastY && y - lastY >= -0.03125 && !mc.player.groundCollision && inAir();
		lastY = e.y;
		hasLastY = true;
	}

	private boolean inAir() {
		var p = mc.player;
		return p.getWorld().getStatesInBox(p.getBoundingBox().expand(0.0625).stretch(0, -0.55, 0)).allMatch(AbstractBlock.AbstractBlockState::isAir);
	}

	private void setVanillaFly(boolean on) {
		var a = mc.player.getAbilities();
		if (on) {
			a.allowFlying = true;
			a.flying = true;
		} else {
			if (!mc.player.isCreative() && !mc.player.isSpectator()) a.allowFlying = false;
			if (!mc.player.isSpectator()) a.flying = false;
			a.setFlySpeed(0.05f);
		}
	}
}
