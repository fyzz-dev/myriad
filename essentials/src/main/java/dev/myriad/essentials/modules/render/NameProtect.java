package dev.myriad.essentials.modules.render;

import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.setting.StringSetting;

/**
 * Replaces your username with a placeholder everywhere text is drawn (chat, tab list, name tags, scoreboard), so it
 * doesn't show on stream or in screenshots. Applied by this addon's text mixin.
 */
public class NameProtect extends Module {

	private final StringSetting placeholder = sgGeneral.string("Placeholder").description("Shown instead of your name.").defaultValue("Player").build();

	public NameProtect() {
		super(Categories.RENDER, "Name Protect", "Hides your username on screen.");
	}

	public static String replace(String text) {
		NameProtect m = Modules.active(NameProtect.class);
		if (m == null || text == null || mc.getUser() == null) return text;
		String name = mc.getUser().getName();
		return name.isEmpty() || !text.contains(name) ? text : text.replace(name, m.placeholder.get());
	}
}
