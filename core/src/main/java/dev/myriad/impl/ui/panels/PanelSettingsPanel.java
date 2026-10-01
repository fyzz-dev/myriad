package dev.myriad.impl.ui.panels;

import dev.myriad.api.ui.Panel;
import dev.myriad.api.ui.WidgetPanel;
import dev.myriad.api.ui.widget.Label;
import dev.myriad.api.ui.widget.SettingsView;
import dev.myriad.api.ui.widget.VBox;
import dev.myriad.impl.ui.WindowManager;

/** Shows the per-instance settings of another window's panel (e.g. a HUD element's options). */
public final class PanelSettingsPanel extends WidgetPanel {
	private final WindowManager wm;
	private final int uid;

	public PanelSettingsPanel(WindowManager wm, int uid) {
		this.wm = wm;
		this.uid = uid;
	}

	private Panel target() {
		return wm.panelByUid(uid).orElse(null);
	}

	@Override
	public String title() {
		Panel p = target();
		return p == null ? "Settings" : p.title() + " · Settings";
	}

	@Override
	public String icon() {
		return "";
	}

	@Override
	protected void build(VBox content) {
		Panel p = target();
		if (p == null) {
			content.add(new Label("That window was closed.").dim());
			return;
		}
		content.add(SettingsView.build(p.settings));
	}
}
