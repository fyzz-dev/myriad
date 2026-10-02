package dev.myriad.impl;

import dev.myriad.api.Myriad;
import dev.myriad.api.MyriadApi;
import dev.myriad.api.addon.Addon;
import dev.myriad.api.command.Command;
import dev.myriad.api.config.ConfigManager;
import dev.myriad.api.event.EventBus;
import dev.myriad.api.module.Category;
import dev.myriad.api.module.ModuleRegistry;
import dev.myriad.api.registry.Registry;
import dev.myriad.api.service.Containers;
import dev.myriad.api.service.Friends;
import dev.myriad.api.service.Inventory;
import dev.myriad.api.service.KeyAction;
import dev.myriad.api.service.Notifications;
import dev.myriad.api.service.Rotations;
import dev.myriad.api.service.Tasks;
import dev.myriad.api.setting.Setting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.ui.BarWidget;
import dev.myriad.api.ui.Desktop;
import dev.myriad.api.ui.PanelType;
import dev.myriad.api.ui.Theme;
import dev.myriad.api.ui.layout.Layout;
import dev.myriad.impl.addon.AddonLoader;
import dev.myriad.impl.command.CommandManager;
import dev.myriad.impl.config.ConfigManagerImpl;
import dev.myriad.impl.event.MyriadEventBus;
import dev.myriad.impl.keybind.KeybindManager;
import dev.myriad.impl.service.ContainerTracker;
import dev.myriad.impl.service.FriendsManager;
import dev.myriad.impl.service.InventoryManager;
import dev.myriad.impl.service.NotificationManager;
import dev.myriad.impl.service.RotationManager;
import dev.myriad.impl.service.TaskScheduler;
import dev.myriad.impl.ui.WindowManager;
import dev.myriad.impl.ui.panels.ConsoleLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Owns every Myriad service and drives startup. */
public final class MyriadImpl implements MyriadApi {
	public static final Logger LOG = LoggerFactory.getLogger("Myriad");
	private static MyriadImpl instance;

	private final MinecraftClient mc = MinecraftClient.getInstance();
	private final MyriadEventBus events = new MyriadEventBus();
	private final Registry<Category> categories = new Registry<>("category");
	private final ModuleRegistry modules = new ModuleRegistry();
	private final Registry<Command> commands = new Registry<>("command");
	private final Registry<PanelType> panels = new Registry<>("panel");
	private final Registry<Layout> layouts = new Registry<>("layout");
	private final Registry<Theme> themes = new Registry<>("theme");
	private final Registry<BarWidget> barWidgets = new Registry<>("bar widget");
	private final Registry<KeyAction> keyActions = new Registry<>("key action");
	private final ConfigManagerImpl config = new ConfigManagerImpl(this);
	private final NotificationManager notifications = new NotificationManager();
	private final RotationManager rotations = new RotationManager();
	private final InventoryManager inventory = new InventoryManager();
	private final dev.myriad.impl.service.PlacementManager placement = new dev.myriad.impl.service.PlacementManager();
	private final FriendsManager friends = new FriendsManager(config.root());
	private final dev.myriad.impl.service.ServerStatsTracker serverStats = new dev.myriad.impl.service.ServerStatsTracker();
	private final ContainerTracker containers = new ContainerTracker();
	private final TaskScheduler tasks = new TaskScheduler();
	private final WindowManager windowManager = new WindowManager();
	private final CommandManager commandManager = new CommandManager(commands);
	private final KeybindManager keybindManager = new KeybindManager();
	private final AddonLoader addonLoader = new AddonLoader(this);

	private MyriadImpl() {
	}

	public static MyriadImpl get() {
		return instance;
	}

	/** Creates and installs Myriad, then loads every addon. */
	public static MyriadImpl bootstrap() {
		// Addon classes can load before the client exists (e.g. from an early mixin); make sure they see it.
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		dev.myriad.api.module.Module.bindClient(client);
		dev.myriad.api.ui.Panel.bindClient(client);
		dev.myriad.api.command.Command.bindClient(client);
		instance = new MyriadImpl();
		Myriad.install(instance);
		instance.start();
		return instance;
	}

	private void start() {
		long t0 = System.nanoTime();
		events.setOwnerResolver(addonLoader::ownerOf);
		Setting.setGlobalChangeHook(config::markDirty);
		SettingColor.setPaletteProvider(windowManager.theme()::roleColor);
		windowManager.setDirtyHook(config::markDirty);
		modules.onRemove(m -> m.setEnabled(false));

		events.subscribe(rotations);
		events.subscribe(inventory);
		events.subscribe(serverStats);
		events.subscribe(containers);
		events.subscribe(tasks);
		events.subscribe(keybindManager);
		events.subscribe(commandManager);

		addonLoader.discover();
		addonLoader.registerCategories();
		addonLoader.initialize();
		config.load();
		addonLoader.postInitialize();
		for (Registry<?> r : registries()) r.freeze();

		LOG.info("Myriad ready: {} addons, {} modules, {} panels, {} commands in {} ms", addonLoader.addons().size(), modules.size(),
			panels.size(), commands.size(), (System.nanoTime() - t0) / 1_000_000);
	}

	public List<Registry<?>> registries() {
		return List.of(categories, modules, commands, panels, layouts, themes, barWidgets, keyActions);
	}

	public ConfigManagerImpl configImpl() {
		return config;
	}

	public WindowManager windowManager() {
		return windowManager;
	}

	public CommandManager commandManager() {
		return commandManager;
	}

	@Override
	public EventBus events() {
		return events;
	}

	@Override
	public List<Addon> addons() {
		return addonLoader.addons();
	}

	@Override
	public Registry<Category> categories() {
		return categories;
	}

	@Override
	public ModuleRegistry modules() {
		return modules;
	}

	@Override
	public Registry<Command> commands() {
		return commands;
	}

	@Override
	public Registry<PanelType> panels() {
		return panels;
	}

	@Override
	public Registry<Layout> layouts() {
		return layouts;
	}

	@Override
	public Registry<Theme> themes() {
		return themes;
	}

	@Override
	public Registry<BarWidget> barWidgets() {
		return barWidgets;
	}

	@Override
	public Registry<KeyAction> keyActions() {
		return keyActions;
	}

	@Override
	public ConfigManager config() {
		return config;
	}

	@Override
	public Notifications notifications() {
		return notifications;
	}

	@Override
	public Rotations rotations() {
		return rotations;
	}

	@Override
	public Inventory inventory() {
		return inventory;
	}

	@Override
	public dev.myriad.api.service.Placement placement() {
		return placement;
	}

	@Override
	public Friends friends() {
		return friends;
	}

	@Override
	public dev.myriad.api.service.ServerStats server() {
		return serverStats;
	}

	@Override
	public Containers containers() {
		return containers;
	}

	@Override
	public Tasks tasks() {
		return tasks;
	}

	public ContainerTracker containerTracker() {
		return containers;
	}

	@Override
	public Desktop ui() {
		return windowManager;
	}

	@Override
	public void chat(Text message) {
		ConsoleLog.add(message.getString());
		if (!mc.isOnThread()) {
			mc.execute(() -> sendChat(message));
			return;
		}
		sendChat(message);
	}

	private void sendChat(Text message) {
		if (mc.inGameHud == null || mc.player == null) return;
		int accent = windowManager.theme().accent.get().color() & 0xFFFFFF;
		MutableText prefix = Text.literal("[Myriad] ").styled(s -> s.withColor(TextColor.fromRgb(accent)));
		mc.inGameHud.getChatHud().addMessage(prefix.append(message));
	}
}
