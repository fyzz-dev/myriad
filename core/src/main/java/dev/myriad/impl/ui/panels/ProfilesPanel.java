package dev.myriad.impl.ui.panels;

import dev.myriad.api.Myriad;
import dev.myriad.api.ui.WidgetPanel;
import dev.myriad.api.ui.widget.Button;
import dev.myriad.api.ui.widget.HBox;
import dev.myriad.api.ui.widget.Label;
import dev.myriad.api.ui.widget.Spacer;
import dev.myriad.api.ui.widget.TextField;
import dev.myriad.api.ui.widget.VBox;

/** Switch, create and delete config profiles. A profile holds module state and the desktop layout. */
public final class ProfilesPanel extends WidgetPanel {
	private String newName = "";

	@Override
	public String title() {
		return "Profiles";
	}

	@Override
	public String icon() {
		return "";
	}

	@Override
	protected void build(VBox content) {
		content.add(new Label("Profiles hold module settings and your menu layout. Friends and key actions are shared.").dim());
		for (String p : Myriad.config().profiles()) {
			HBox row = new HBox(4);
			row.addWeighted(new Label(() -> (p.equals(Myriad.config().activeProfile()) ? "  " : "     ") + p), 1);
			row.addFixed(new Button("Load", () -> {
				Myriad.config().switchProfile(p);
				rebuild();
			}), 40);
			row.addFixed(new Button("Delete", () -> {
				if (Myriad.config().deleteProfile(p)) rebuild();
			}), 44).visible(() -> !p.equals(Myriad.config().activeProfile()));
			content.add(row);
		}
		content.add(Spacer.divider());
		HBox create = new HBox(4);
		create.addWeighted(new TextField(() -> newName).placeholder("New profile name").onChange(s -> newName = s), 1);
		create.addFixed(new Button("Create", () -> {
			if (!newName.isBlank()) {
				Myriad.config().switchProfile(newName);
				newName = "";
				rebuild();
			}
		}).accent(), 50);
		content.add(create);
		content.add(new Label("Creating a profile copies the current state into it.").dim());
	}
}
