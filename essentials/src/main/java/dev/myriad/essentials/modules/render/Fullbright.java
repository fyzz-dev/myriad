package dev.myriad.essentials.modules.render;

import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.DoubleSetting;

/** Read by {@code LightmapTextureManagerMixin} in this addon. */
public class Fullbright extends Module {

	public final DoubleSetting gamma = sgGeneral.doubleSetting("Gamma").defaultValue(16).range(1, 16).decimals(0).build();

	public Fullbright() {
		super(Categories.RENDER, "Fullbright", "Lights up the world.");
	}

}
