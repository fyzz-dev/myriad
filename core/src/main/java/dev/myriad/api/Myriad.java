package dev.myriad.api;

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
import dev.myriad.api.service.Placement;
import dev.myriad.api.service.Rotations;
import dev.myriad.api.service.ServerStats;
import dev.myriad.api.service.Tasks;
import dev.myriad.api.ui.BarWidget;
import dev.myriad.api.ui.Desktop;
import dev.myriad.api.ui.PanelType;
import dev.myriad.api.ui.Theme;
import dev.myriad.api.ui.layout.Layout;
import net.minecraft.text.Text;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Static entry point to everything Myriad exposes to addons.
 *
 * <pre>{@code
 * Myriad.modules().get(KillAura.class).ifPresent(Module::toggle);
 * Myriad.notifications().info("My Addon", "Hello!");
 * Myriad.ui().openPanel(MyriadId.of("my-addon", "stats"));
 * }</pre>
 */
public final class Myriad {
	public static final String MOD_ID = "myriad";
	private static MyriadApi api;

	private Myriad() {
	}

	private static MyriadApi api() {
		if (api == null) throw new IllegalStateException("Myriad is not initialised yet");
		return api;
	}

	@ApiStatus.Internal
	public static void install(MyriadApi impl) {
		api = impl;
	}

	public static boolean isReady() {
		return api != null;
	}

	public static EventBus events() {
		return api().events();
	}

	/** Every addon Myriad found, including failed ones and Myriad core itself. */
	public static List<Addon> addons() {
		return api().addons();
	}

	public static Registry<Category> categories() {
		return api().categories();
	}

	public static ModuleRegistry modules() {
		return api().modules();
	}

	public static Registry<Command> commands() {
		return api().commands();
	}

	public static Registry<PanelType> panels() {
		return api().panels();
	}

	public static Registry<Layout> layouts() {
		return api().layouts();
	}

	public static Registry<Theme> themes() {
		return api().themes();
	}

	public static Registry<BarWidget> barWidgets() {
		return api().barWidgets();
	}

	public static Registry<KeyAction> keyActions() {
		return api().keyActions();
	}

	public static ConfigManager config() {
		return api().config();
	}

	public static Notifications notifications() {
		return api().notifications();
	}

	public static Rotations rotations() {
		return api().rotations();
	}

	public static Inventory inventory() {
		return api().inventory();
	}

	/** Shared block placement (neighbour finding, rotation, silent swap, cooldowns). */
	public static Placement placement() {
		return api().placement();
	}

	public static Friends friends() {
		return api().friends();
	}

	/** Server TPS and totem pops, tracked once for every addon. */
	public static ServerStats server() {
		return api().server();
	}

	/** Opening containers, reading them and moving items. */
	public static Containers containers() {
		return api().containers();
	}

	/** Work spread over ticks: delays, repeats and step-by-step sequences. */
	public static Tasks tasks() {
		return api().tasks();
	}

	/** The window manager. */
	public static Desktop ui() {
		return api().ui();
	}

	/** Prints a client-side chat message with the Myriad prefix (also mirrored in the console panel). */
	public static void chat(Text message) {
		api().chat(message);
	}
}
