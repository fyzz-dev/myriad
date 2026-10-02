package dev.myriad.essentials.modules.player;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.InputEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.util.Timer;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps idle-kick timers from firing by doing a small action every so often. The enabled actions take turns: hold
 * sneak for a few ticks, jump, swing your hand, or turn your head a little.
 */
public class AntiAFK extends Module {
	private final BoolSetting sneak = sgGeneral.bool("Sneak").description("Hold sneak for a few ticks.").defaultValue(true).build();
	private final IntSetting sneakDuration = sgGeneral.intSetting("Sneak Duration").description("Ticks to hold sneak.").defaultValue(5).range(1, 60).visible(sneak::get).build();
	private final BoolSetting jump = sgGeneral.bool("Jump").description("Jump in place.").defaultValue(true).build();
	private final BoolSetting swing = sgGeneral.bool("Swing Hand").description("Swing your main hand.").defaultValue(true).build();
	private final BoolSetting rotate = sgGeneral.bool("Rotate").description("Turn your head a few degrees.").build();
	private final DoubleSetting delay = sgGeneral.doubleSetting("Delay").description("Seconds between actions.").defaultValue(2).range(0.25, 20).decimals(2).build();

	private final Timer actionTimer = new Timer();
	private int phase, sneakTicks;

	public AntiAFK() {
		super(Categories.PLAYER, "Anti AFK", "Does small actions so you aren't kicked for idling.");
	}

	@Override
	protected void onEnable() {
		actionTimer.reset();
		sneakTicks = 0;
	}

	@Subscribe
	private void onInput(InputEvent e) {
		if (sneakTicks > 0) e.sneak = true;
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) {
			sneakTicks = 0;
			return;
		}
		if (sneakTicks > 0) {
			sneakTicks--;
			return;
		}
		if (actionTimer.tick((long) (delay.get() * 1000))) nextAction();
	}

	private void nextAction() {
		for (int i = 0; i < 4; i++) {
			phase = (phase + 1) % 4;
			switch (phase) {
				case 0 -> {
					if (sneak.get()) {
						sneakTicks = sneakDuration.get();
						return;
					}
				}
				case 1 -> {
					if (jump.get() && mc.player.isOnGround()) {
						mc.player.jump();
						return;
					}
				}
				case 2 -> {
					if (swing.get()) {
						mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
						return;
					}
				}
				case 3 -> {
					if (rotate.get()) {
						mc.player.setYaw(mc.player.getYaw() + (float) ThreadLocalRandom.current().nextDouble(-8, 8));
						return;
					}
				}
				default -> {
				}
			}
		}
	}
}
