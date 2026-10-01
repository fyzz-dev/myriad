package dev.myriad.impl.ui.panels;

import dev.myriad.api.Myriad;
import dev.myriad.api.module.Module;
import dev.myriad.api.ui.WidgetPanel;
import dev.myriad.api.ui.widget.HBox;
import dev.myriad.api.ui.widget.Label;
import dev.myriad.api.ui.widget.SettingsView;
import dev.myriad.api.ui.widget.Toggle;
import dev.myriad.api.ui.widget.VBox;

public final class ModuleSettingsPanel extends WidgetPanel {
	private final Module module;

	public ModuleSettingsPanel(Module module) {
		this.module = module;
	}

	public Module module() {
		return module;
	}

	@Override
	public String title() {
		return module.name();
	}

	@Override
	public String icon() {
		return module.category().icon();
	}

	@Override
	protected void build(VBox content) {
		HBox header = new HBox(4);
		header.addWeighted(new Label(() -> module.isEnabled() ? "Enabled" : "Disabled").bold(), 1);
		header.addFixed(new Toggle(module::isEnabled, module::setEnabled), 24);
		content.add(header);
		if (!module.description().isEmpty()) content.add(new Label(module.description()).dim());
		String owner = Myriad.modules().ownerOf(module);
		content.add(new Label(module.category().name() + " · " + (owner == null ? "?" : owner)).dim());
		content.add(SettingsView.build(module.settings));
	}
}
