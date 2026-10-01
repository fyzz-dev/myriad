package dev.myriad.impl.ui.panels;

import dev.myriad.api.Myriad;
import dev.myriad.api.addon.Addon;
import dev.myriad.api.module.Category;
import dev.myriad.api.module.Module;

/** Short, human names for where things come from ("Essentials" rather than "Myriad Essentials"). */
final class AddonNames {
	private AddonNames() {
	}

	static String of(String addonId) {
		if (addonId == null) return "?";
		for (Addon a : Myriad.addons()) {
			if (!a.id().equals(addonId)) continue;
			String n = a.name();
			return n.startsWith("Myriad ") && n.length() > 7 ? n.substring(7) : n;
		}
		return addonId;
	}

	static String of(Module m) {
		return of(Myriad.modules().ownerOf(m));
	}

	/**
	 * A category's window title. Core's shared categories keep their plain name; an addon's own category gets the
	 * addon name appended when another category has the same name, so two "Combat" windows can be told apart.
	 */
	static String title(Category c) {
		long same = Myriad.categories().values().stream().filter(o -> o.name().equalsIgnoreCase(c.name())).count();
		if (same < 2 || c.id().namespace().equals("myriad")) return c.name();
		return c.name() + " · " + of(Myriad.categories().ownerOf(c));
	}
}
