package dev.myriad.impl.addon;

import dev.myriad.api.addon.Addon;
import dev.myriad.api.addon.AddonContext;
import dev.myriad.api.addon.AddonStorage;
import dev.myriad.api.command.Command;
import dev.myriad.api.event.EventBus;
import dev.myriad.api.module.Category;
import dev.myriad.api.module.Module;
import dev.myriad.api.service.KeyAction;
import dev.myriad.api.ui.BarWidget;
import dev.myriad.api.ui.PanelType;
import dev.myriad.api.ui.SettingWidgets;
import dev.myriad.api.ui.Theme;
import dev.myriad.api.ui.layout.Layout;
import dev.myriad.api.util.Keybind;
import dev.myriad.api.util.MyriadId;
import dev.myriad.impl.MyriadImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AddonContextImpl implements AddonContext {
	private final Addon addon;
	private final MyriadImpl myriad;
	private final Logger logger;
	private final AddonStorage storage;

	AddonContextImpl(Addon addon, MyriadImpl myriad) {
		this.addon = addon;
		this.myriad = myriad;
		this.logger = LoggerFactory.getLogger(addon.name());
		this.storage = new AddonStorageImpl(myriad.configImpl().root().resolve("addons").resolve(addon.id()));
	}

	@Override
	public Addon addon() {
		return addon;
	}

	@Override
	public Logger logger() {
		return logger;
	}

	@Override
	public Category registerCategory(String name, String icon, int color) {
		return registerCategory(name, icon, dev.myriad.api.setting.SettingColor.of(color));
	}

	@Override
	public Category registerCategory(String name, String icon, dev.myriad.api.setting.SettingColor color) {
		for (Category core : dev.myriad.api.module.Categories.DEFAULTS) {
			if (core.name().equalsIgnoreCase(name)) {
				logger.warn("Registering a category named '{}', which duplicates Myriad's shared one; use Categories.{} instead "
					+ "so your modules share a window with other addons'", name, core.id().path().toUpperCase());
			}
		}
		return myriad.categories().register(new Category(id(MyriadId.toPath(name)), name, icon, color), addon.id());
	}

	@Override
	public Category registerCategory(Category category) {
		return myriad.categories().register(category, addon.id());
	}

	@Override
	public <M extends Module> M registerModule(M module) {
		myriad.modules().register(module, addon.id());
		return module;
	}

	@Override
	public <C extends Command> C registerCommand(C command) {
		command.assignNamespace(addon.id());
		myriad.commands().register(command, addon.id());
		return command;
	}

	@Override
	public PanelType registerPanel(PanelType type) {
		return myriad.panels().register(type, addon.id());
	}

	@Override
	public BarWidget registerBarWidget(BarWidget widget) {
		return myriad.barWidgets().register(widget, addon.id());
	}

	@Override
	public Layout registerLayout(Layout layout) {
		return myriad.layouts().register(layout, addon.id());
	}

	@Override
	public Theme registerTheme(Theme theme) {
		return myriad.themes().register(theme, addon.id());
	}

	@Override
	public KeyAction registerKeyAction(String name, Keybind defaultBind, Runnable action) {
		return myriad.keyActions().register(new KeyAction(id(MyriadId.toPath(name)), name, defaultBind, action), addon.id());
	}

	@Override
	public SettingWidgets settingWidgets() {
		return myriad.ui().settingWidgets();
	}

	@Override
	public EventBus events() {
		return myriad.events();
	}

	@Override
	public AddonStorage storage() {
		return storage;
	}
}
