package dev.myriad.impl.ui;

import dev.myriad.api.addon.AddonContext;
import dev.myriad.api.ui.Theme;
import dev.myriad.api.util.MyriadId;


/**
 * Stock colour schemes, plus "Omarchy", which always mirrors your active Omarchy system theme. Presets set colours
 * (and rounding when the Omarchy theme sets it); everything else starts from the defaults and can be edited per theme in the Theme panel.
 */
public final class CoreThemes {
	public static final MyriadId OMARCHY_CURRENT = MyriadId.of("myriad", "omarchy_current");

	private CoreThemes() {
	}

	private static void preset(AddonContext ctx, String path, String name, OmarchyThemes.Palette p) {
		ctx.registerTheme(new Theme(ctx.id(path), name, t -> OmarchyThemes.apply(t, p)));
	}

	public static void register(AddonContext ctx) {
		// Myriad's own look: the plain ThemeSettings defaults.
		ctx.registerTheme(new Theme(ThemeManager.DEFAULT, "Myriad", t -> {
		}));
		preset(ctx, "tokyo_night", "Tokyo Night", OmarchyThemes.palette(0xFF1A1B26, 0xFFC0CAF5, 0xFF33467C, 0xFF7AA2F7, 0xFFBB9AF7,
			0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68, 0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF));
		preset(ctx, "gruvbox", "Gruvbox", OmarchyThemes.palette(0xFF282828, 0xFFEBDBB2, 0xFF504945, 0xFFFABD2F, 0xFFFE8019,
			0xFFFB4934, 0xFFB8BB26, 0xFFFABD2F, 0xFF83A598, 0xFFD3869B, 0xFF8EC07C));

		// One theme that mirrors whatever Omarchy theme is active, re-read whenever it changes.
		String hint = OmarchyThemes.blockedHint();
		if (hint != null) {
			org.slf4j.LoggerFactory.getLogger("Myriad/Omarchy").warn("Omarchy detected, but the game is sandboxed and can't read your theme. "
				+ "Close the launcher and run: {}", hint);
		}
		if (!OmarchyThemes.isInstalled()) return;
		ctx.registerTheme(new Theme(OMARCHY_CURRENT, "Omarchy", t ->
			OmarchyThemes.read(OmarchyThemes.CURRENT).ifPresent(p -> OmarchyThemes.apply(t, p))));
	}
}
