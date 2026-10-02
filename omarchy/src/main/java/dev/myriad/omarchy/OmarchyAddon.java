package dev.myriad.omarchy;

import dev.myriad.api.addon.AddonContext;
import dev.myriad.api.addon.MyriadAddon;
import dev.myriad.api.ui.Theme;

/**
 * Adds an "Omarchy" theme that always matches the active Omarchy system theme, switching when you run
 * {@code omarchy-theme-set}. On systems without Omarchy it registers nothing.
 */
public final class OmarchyAddon implements MyriadAddon {
	@Override
	public void initialize(AddonContext ctx) {
		String blocked = Omarchy.blockedHint();
		if (blocked != null) {
			ctx.logger().warn("Omarchy detected, but the game is sandboxed and can't read your theme. Close the launcher and run: {}", blocked);
		}
		if (!Omarchy.isInstalled() && blocked == null) return;

		Theme theme = new Theme(ctx.id("omarchy"), "Omarchy", t -> Omarchy.read(Omarchy.CURRENT).ifPresent(p -> p.apply(t)))
			// Re-applied whenever the system theme changes.
			.live(Omarchy::signature)
			// Myriad used to ship this theme itself under this id; saved choices and edits carry over.
			.aliases("myriad:omarchy_current")
			.notice(() -> Omarchy.blockedHint() == null ? null
				: "Omarchy detected, but this launcher runs the game in a Flatpak sandbox that can't see your theme. Close the launcher, run this in a terminal, and start it again:\n"
				+ Omarchy.blockedHint());
		if (Omarchy.isInstalled()) theme.preferOnFirstRun();
		ctx.registerTheme(theme);
	}
}
