package dev.myriad.essentials.modules.render;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.Renderer3D;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.util.Entities;
import net.minecraft.world.entity.Entity;

public class Tracers extends Module {
	private final BoolSetting players = sgGeneral.bool("Players").defaultValue(true).build();
	private final BoolSetting hostiles = sgGeneral.bool("Hostiles").build();
	private final DoubleSetting maxDistance = sgGeneral.doubleSetting("Max Distance").defaultValue(128).range(8, 512).decimals(0).build();
	private final ColorSetting color = sgGeneral.color("Color").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT)).build();
	private final ColorSetting friendColor = sgGeneral.color("Friend Color").defaultValue(SettingColor.role(SettingColor.Mode.CYAN)).build();

	public Tracers() {
		super(Categories.RENDER, "Tracers", "Draws lines to entities.");
	}

	@Subscribe
	private void onRender(Render3DEvent e) {
		if (!inGame()) return;
		double max = maxDistance.get() * maxDistance.get();
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!Entities.isAliveTarget(entity) || mc.player.distanceToSqr(entity) > max) continue;
			boolean show = switch (Entities.kind(entity)) {
				case PLAYER -> players.get();
				case HOSTILE -> hostiles.get();
				default -> false;
			};
			if (!show) continue;
			Renderer3D.tracer(Entities.lerpedCenter(entity, e.tickDelta()), Entities.isFriend(entity) ? friendColor.argb() : color.argb());
		}
	}
}
