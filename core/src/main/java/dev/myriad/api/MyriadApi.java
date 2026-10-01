package dev.myriad.api;

import dev.myriad.api.addon.Addon;
import dev.myriad.api.command.Command;
import dev.myriad.api.config.ConfigManager;
import dev.myriad.api.event.EventBus;
import dev.myriad.api.module.Category;
import dev.myriad.api.module.ModuleRegistry;
import dev.myriad.api.registry.Registry;
import dev.myriad.api.service.Friends;
import dev.myriad.api.service.Inventory;
import dev.myriad.api.service.KeyAction;
import dev.myriad.api.service.Notifications;
import dev.myriad.api.service.Placement;
import dev.myriad.api.service.Rotations;
import dev.myriad.api.service.ServerStats;
import dev.myriad.api.ui.BarWidget;
import dev.myriad.api.ui.Desktop;
import dev.myriad.api.ui.PanelType;
import dev.myriad.api.ui.Theme;
import dev.myriad.api.ui.layout.Layout;
import net.minecraft.text.Text;

import java.util.List;

/** The services behind the {@link Myriad} facade. */
public interface MyriadApi {
	EventBus events();

	List<Addon> addons();

	Registry<Category> categories();

	ModuleRegistry modules();

	Registry<Command> commands();

	Registry<PanelType> panels();

	Registry<Layout> layouts();

	Registry<Theme> themes();

	Registry<BarWidget> barWidgets();

	Registry<KeyAction> keyActions();

	ConfigManager config();

	Notifications notifications();

	Rotations rotations();

	Inventory inventory();

	Placement placement();

	Friends friends();

	ServerStats server();

	Desktop ui();

	void chat(Text message);
}
