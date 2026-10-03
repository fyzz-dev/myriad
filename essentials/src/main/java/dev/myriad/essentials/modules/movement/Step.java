package dev.myriad.essentials.modules.movement;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.DoubleSetting;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class Step extends Module {
	private static final double VANILLA = 0.6;
	private final DoubleSetting height = sgGeneral.doubleSetting("Height").defaultValue(1.0).range(0.6, 10).sliderRange(0.6, 3).decimals(1).build();

	public Step() {
		super(Categories.MOVEMENT, "Step", "Walk up blocks without jumping.");
	}

	private void apply(double value) {
		if (!inGame()) return;
		var attr = mc.player.getAttribute(Attributes.STEP_HEIGHT);
		if (attr != null && attr.getBaseValue() != value) attr.setBaseValue(value);
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		apply(mc.player != null && mc.player.isShiftKeyDown() ? VANILLA : height.get());
	}

	@Override
	protected void onDisable() {
		apply(VANILLA);
	}

	@Override
	public String hudInfo() {
		return height.valueString();
	}
}
