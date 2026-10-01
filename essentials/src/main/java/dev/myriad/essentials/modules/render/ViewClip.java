package dev.myriad.essentials.modules.render;

import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.DoubleSetting;

/**
 * Changes how far the third-person camera sits from you, and optionally lets it pass through blocks instead of
 * being pulled in front of them. Applied by this addon's CameraMixin.
 */
public class ViewClip extends Module {

	private final BoolSetting noClip = sgGeneral.bool("No Clip").description("Let the camera go through blocks.").defaultValue(true).build();
	private final DoubleSetting distance = sgGeneral.doubleSetting("Distance").description("Camera distance in third person (vanilla is 4).").defaultValue(4).range(1, 20).decimals(1).build();

	public ViewClip() {
		super(Categories.RENDER, "View Clip", "Third-person camera distance and clipping.");
	}

	public static boolean noClip() {
		ViewClip m = Modules.active(ViewClip.class);
		return m != null && m.noClip.get();
	}

	/** Scales vanilla's third-person camera distance (4 blocks) to the configured one; unchanged while off. */
	public static float distance(float vanilla) {
		ViewClip m = Modules.active(ViewClip.class);
		return m != null ? m.distance.getFloat() * vanilla / 4f : vanilla;
	}
}
