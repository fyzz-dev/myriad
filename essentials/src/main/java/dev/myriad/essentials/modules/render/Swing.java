package dev.myriad.essentials.modules.render;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hand animation tweaks: change how long a swing takes, skip the dip when you switch items, and draw off-hand
 * swings with the main hand. Applied by this addon's LivingEntity and HeldItemRenderer mixins.
 */
public class Swing extends Module {

	private final BoolSetting customSpeed = sgGeneral.bool("Custom Speed").defaultValue(true).build();
	private final IntSetting swingSpeed = sgGeneral.intSetting("Swing Speed").description("Ticks a swing lasts; vanilla is 6, lower is faster.").defaultValue(10).range(1, 20)
		.visible(customSpeed::get).build();
	private final BoolSetting selfOnly = sgGeneral.bool("Self Only").description("Only change your own swing speed.").defaultValue(true).visible(customSpeed::get).build();
	private final BoolSetting noSwitchAnimation = sgGeneral.bool("No Switch Animation").description("Skip the dip when switching items.").build();
	private final BoolSetting alwaysMainHand = sgGeneral.bool("Always Main Hand").description("Off-hand swings animate the main hand.").build();

	public Swing() {
		super(Categories.RENDER, "Swing", "Changes the hand swing animation.");
	}

	/** The swing duration to use for {@code entity}, or -1 to keep vanilla's. */
	public static int swingDuration(LivingEntity entity) {
		Swing m = Modules.active(Swing.class);
		if (m == null || !m.customSpeed.get()) return -1;
		if (m.selfOnly.get() && entity != Minecraft.getInstance().player) return -1;
		return m.swingSpeed.get();
	}

	public static boolean noSwitchAnimation() {
		Swing m = Modules.active(Swing.class);
		return m != null && m.noSwitchAnimation.get();
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (inGame() && alwaysMainHand.get() && mc.player.swinging && mc.player.swingingArm == InteractionHand.OFF_HAND) mc.player.swingingArm = InteractionHand.MAIN_HAND;
	}
}
