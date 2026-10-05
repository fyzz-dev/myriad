package dev.myriad.impl.ui.panels;

import dev.myriad.api.Myriad;
import dev.myriad.api.addon.Addon;
import dev.myriad.api.addon.AddonState;
import dev.myriad.api.render.Canvas;
import dev.myriad.api.ui.WidgetPanel;
import dev.myriad.api.ui.widget.Button;
import dev.myriad.api.ui.widget.HBox;
import dev.myriad.api.addon.AddonSettings;
import dev.myriad.api.ui.widget.Label;
import dev.myriad.api.ui.widget.SettingsView;
import dev.myriad.api.ui.widget.VBox;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.Util;

/** Every addon Myriad found, as cards: what it adds, who made it, where to find it, and why it failed if it did. */
public final class AddonsPanel extends WidgetPanel {
	private final Set<String> unfolded = new HashSet<>();

	@Override
	public String title() {
		return "Addons";
	}

	@Override
	public String icon() {
		return "";
	}

	@Override
	protected void build(VBox content) {
		long failed = Myriad.addons().stream().filter(a -> a.state() == AddonState.FAILED).count();
		content.add(new Label(Myriad.addons().size() + " addons" + (failed > 0 ? ", " + failed + " failed" : "")
			+ ". Myriad core ships no modules; everything here plugs in through the addon API.").dim());
		for (Addon a : Myriad.addons()) content.add(new AddonEntry(a));
	}

	private final class AddonEntry extends CardEntry {
		private final Addon addon;

		AddonEntry(Addon addon) {
			super(addon.state() == AddonState.LOADED, false);
			this.addon = addon;
		}

		private boolean failed() {
			return addon.state() == AddonState.FAILED;
		}

		@Override
		protected String title() {
			return addon.name();
		}

		@Override
		protected boolean isOn() {
			return addon.state() == AddonState.LOADED;
		}

		@Override
		protected int accent() {
			return failed() ? theme().red.argb() : theme().accent.argb();
		}

		@Override
		protected boolean isExpanded() {
			return unfolded.contains(addon.id());
		}

		@Override
		protected void setExpanded(boolean expanded) {
			if (expanded) unfolded.add(addon.id());
			else unfolded.remove(addon.id());
		}

		@Override
		protected float drawBadges(Canvas c, float right, float cy) {
			if (failed()) right = badge(c, " Failed", right, cy, theme().red.argb(), true) - 4;
			return badge(c, addon.version(), right, cy, theme().textDim.argb(), false) - 4;
		}

		@Override
		protected String tooltipText() {
			String d = addon.description();
			return d == null || d.isEmpty() ? null : d;
		}

		@Override
		protected void buildBody(VBox body) {
			if (failed()) body.add(new Label("Failed to load: " + addon.failure()).color(() -> theme().red.argb()));
			if (addon.description() != null && !addon.description().isEmpty()) body.add(new Label(addon.description()).dim());
			if (!addon.authors().isEmpty()) body.add(new Label("By " + String.join(", ", addon.authors())).dim());
			String id = addon.id();
			body.add(new Label(() -> counts(id)));
			// Links from the addon's fabric.mod.json "contact" block.
			HBox links = new HBox(4);
			addon.homepage().ifPresent(url -> links.add(new Button(" Website", () -> Util.getPlatform().openUri(url))).tooltip(url));
			addon.sources().ifPresent(url -> links.add(new Button(" Source", () -> Util.getPlatform().openUri(url))).tooltip(url));
			addon.issues().ifPresent(url -> links.add(new Button(" Issues", () -> Util.getPlatform().openUri(url))).tooltip(url));
			if (!links.children().isEmpty()) body.add(links);
			for (AddonSettings s : Myriad.addonSettings().ownedBy(id)) {
				body.add(new Label(s.name()).bold());
				body.add(SettingsView.build(s.settings()));
			}
		}

		/** "12 modules · 3 HUD elements · 1 command", leaving out what the addon doesn't add. */
		private static String counts(String id) {
			StringBuilder sb = new StringBuilder();
			count(sb, Myriad.modules().ownedBy(id).size(), "module");
			count(sb, (int) Myriad.panels().ownedBy(id).stream().filter(p -> p.isHud()).count(), "HUD element");
			count(sb, (int) Myriad.panels().ownedBy(id).stream().filter(p -> !p.isHud()).count(), "window");
			count(sb, Myriad.commands().ownedBy(id).size(), "command");
			count(sb, Myriad.themes().ownedBy(id).size(), "theme");
			count(sb, Myriad.barWidgets().ownedBy(id).size(), "bar widget");
			count(sb, Myriad.keyActions().ownedBy(id).size(), "key action");
			return sb.isEmpty() ? "Adds nothing visible yet." : sb.toString();
		}

		private static void count(StringBuilder sb, int n, String what) {
			if (n == 0) return;
			if (!sb.isEmpty()) sb.append("  ·  ");
			sb.append(n).append(' ').append(what).append(n == 1 ? "" : "s");
		}
	}
}
