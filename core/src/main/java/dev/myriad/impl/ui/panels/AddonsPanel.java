package dev.myriad.impl.ui.panels;

import dev.myriad.api.Myriad;
import dev.myriad.api.addon.Addon;
import dev.myriad.api.addon.AddonState;
import dev.myriad.api.ui.WidgetPanel;
import dev.myriad.api.ui.widget.Collapsible;
import dev.myriad.api.ui.widget.Label;
import dev.myriad.api.ui.widget.VBox;

/** Lists every addon Myriad found, what it contributes, and why it failed if it did. */
public final class AddonsPanel extends WidgetPanel {
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
		content.add(new Label(Myriad.addons().size() + " addons loaded. Myriad core ships no modules; everything below plugs in through the addon API.").dim());
		for (Addon a : Myriad.addons()) {
			VBox body = new VBox(2, 0);
			if (a.description() != null && !a.description().isEmpty()) body.add(new Label(a.description()).dim());
			if (!a.authors().isEmpty()) body.add(new Label("By " + String.join(", ", a.authors())).dim());
			String id = a.id();
			body.add(new Label(() -> String.format("%d modules · %d panels · %d commands · %d bar widgets · %d themes",
				Myriad.modules().ownedBy(id).size(), Myriad.panels().ownedBy(id).size(), Myriad.commands().ownedBy(id).size(),
				Myriad.barWidgets().ownedBy(id).size(), Myriad.themes().ownedBy(id).size())));
			if (a.state() == AddonState.FAILED) {
				body.add(new Label("Failed to load: " + a.failure()).color(() -> 0xFFF38BA8));
			}
			String status = a.state() == AddonState.FAILED ? "  " : "";
			content.add(new Collapsible(a.name() + "  " + a.version() + status, body));
		}
	}
}
