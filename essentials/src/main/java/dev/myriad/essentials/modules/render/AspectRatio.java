package dev.myriad.essentials.modules.render;

import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.setting.DoubleSetting;

/** Stretches or squeezes the view by overriding the projection's aspect ratio (width / height). */
public class AspectRatio extends Module {

	public final DoubleSetting ratio = sgGeneral.doubleSetting("Ratio").description("Width divided by height; 1.78 is 16:9, 1.33 is 4:3.")
		.defaultValue(1.33).range(0.5, 3).decimals(2).build();

	public AspectRatio() {
		super(Categories.RENDER, "Aspect Ratio", "Stretches the view to a different aspect ratio.");
	}

}
