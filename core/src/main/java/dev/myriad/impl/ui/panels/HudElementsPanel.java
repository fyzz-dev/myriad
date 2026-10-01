package dev.myriad.impl.ui.panels;

import com.google.gson.JsonObject;
import dev.myriad.api.Myriad;
import dev.myriad.api.ui.PanelType;
import dev.myriad.api.ui.WidgetPanel;
import dev.myriad.api.ui.widget.Button;
import dev.myriad.api.ui.widget.HBox;
import dev.myriad.api.ui.widget.Label;
import dev.myriad.api.ui.widget.Toggle;
import dev.myriad.api.ui.widget.VBox;
import dev.myriad.impl.ui.WindowManager;

/**
 * Adds and removes HUD elements. Every HUD-capable panel any addon registers is listed; switch one on to place it,
 * then drag it on screen. Opens automatically on the HUD workspace.
 */
public final class HudElementsPanel extends WidgetPanel {
	private final WindowManager wm;

	public HudElementsPanel(WindowManager wm) {
		this.wm = wm;
	}

	@Override
	public String title() {
		return "HUD Elements";
	}

	@Override
	public String icon() {
		return "";
	}

	@Override
	protected void build(VBox content) {
		content.add(new Label("Drag elements to move them. Right-click one for its options, Delete removes the hovered one.").dim());
		boolean any = false;
		for (PanelType type : Myriad.panels()) {
			if (!type.isHud()) continue;
			any = true;
			HBox row = new HBox(4);
			row.addWeighted(new Label((type.icon() == null ? "" : type.icon() + "  ") + type.name()), 1);
			row.addFixed(new Button("", () -> wm.hudElement(type).ifPresent(w -> {
				JsonObject args = new JsonObject();
				args.addProperty("window", w.uid());
				wm.openPanel(CorePanels.PANEL_SETTINGS, args, wm.activeWorkspace(), true);
			})).visible(() -> wm.hudElement(type).isPresent() && !wm.hudElement(type).get().panel().settings.all().isEmpty()), 16);
			row.addFixed(new Toggle(() -> wm.hudElement(type).isPresent(), on -> {
				if (on) wm.addHudElement(type);
				else wm.removeHudElement(type);
			}), 24);
			content.add(row);
		}
		if (!any) content.add(new Label("No HUD elements installed. Addons such as myriad-essentials provide them.").dim());
	}
}
