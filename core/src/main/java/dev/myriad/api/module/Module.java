package dev.myriad.api.module;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.ModuleToggleEvent;
import dev.myriad.api.registry.Identified;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.KeybindSetting;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.api.setting.Settings;
import dev.myriad.api.util.MyriadId;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A toggleable feature. While enabled, the module is subscribed to the event bus, so {@code @Subscribe} methods
 * only run when it is on.
 *
 * <pre>{@code
 * public class Sprint extends Module {
 *     private final BoolSetting omni = sgGeneral.bool("Omnidirectional").build();
 *
 *     public Sprint() {
 *         super(Categories.MOVEMENT, "Sprint", "Sprints automatically.");
 *     }
 *
 *     @Subscribe
 *     private void onTick(TickEvent.Post e) { ... }
 * }
 * }</pre>
 *
 * Register it from your addon with {@code ctx.registerModule(new Sprint())}. Elsewhere (other modules, mixins, other
 * addons) reach it with {@link Modules#active(Class)}, which returns null while it's off or not installed.
 */
public abstract class Module implements Identified {
	/** The game client. Bound again when Myriad starts, in case this class loaded before the client existed. */
	protected static MinecraftClient mc = MinecraftClient.getInstance();
	private static final Logger LOG = LoggerFactory.getLogger("Myriad/Module");

	private final String name;
	private final String path;
	private final String description;
	private final Category category;
	private MyriadId id;
	private boolean enabled;

	public final Settings settings = new Settings();
	protected final SettingGroup sgGeneral = settings.group("General");

	private final SettingGroup sgModule = settings.group("Module");
	public final KeybindSetting keybind = sgModule.keybind("Keybind").description("Toggles the module.").build();
	public final BoolSetting holdMode = sgModule.bool("Hold").description("Only active while the keybind is held.").build();
	public final BoolSetting visibleInList = sgModule.bool("Show In List").description("Show in the module list HUD.").defaultValue(true).build();
	public final BoolSetting chatFeedback = sgModule.bool("Chat Feedback").description("Announce toggles in chat.").defaultValue(true).build();

	protected Module(Category category, String name, String description) {
		this.category = category;
		this.name = name;
		this.path = MyriadId.toPath(name);
		this.description = description;
	}

	@Override
	public MyriadId id() {
		if (id == null) throw new IllegalStateException("Module '" + name + "' is not registered yet");
		return id;
	}

	/** Called by the module registry with the registering addon's namespace. */
	@ApiStatus.Internal
	public void assignNamespace(String namespace) {
		if (id != null && !id.namespace().equals(namespace)) throw new IllegalStateException("Module '" + name + "' already registered as " + id);
		id = MyriadId.of(namespace, path);
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public Category category() {
		return category;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	public void enable() {
		setEnabled(true);
	}

	public void disable() {
		setEnabled(false);
	}

	public void setEnabled(boolean enable) {
		setEnabled(enable, true);
	}

	private void setEnabled(boolean enable, boolean feedback) {
		if (enable == enabled) return;
		enabled = enable;
		if (enable) {
			Myriad.events().subscribe(this);
			try {
				onEnable();
			} catch (Throwable t) {
				LOG.error("{} failed to enable", id, t);
				error("Failed to enable: " + t);
				enabled = false;
				Myriad.events().unsubscribe(this);
				return;
			}
		} else {
			Myriad.events().unsubscribe(this);
			Myriad.tasks().cancel(this);
			try {
				onDisable();
			} catch (Throwable t) {
				LOG.error("{} failed to disable cleanly", id, t);
			}
		}
		Myriad.events().post(new ModuleToggleEvent(this, enabled));
		Myriad.config().markDirty();
		if (feedback && chatFeedback.get() && mc.player != null && !holdMode.get()) {
			// One line per module: toggling it again replaces the line instead of adding another.
			Myriad.chat(Text.literal(name).formatted(Formatting.WHITE).append(Text.literal(enabled ? " enabled" : " disabled")
				.formatted(enabled ? Formatting.GREEN : Formatting.RED)), "myriad:toggle:" + id);
		}
	}

	/** Restores state from config without side effects like chat feedback. */
	@ApiStatus.Internal
	public void setEnabledSilently(boolean enable) {
		setEnabled(enable, false);
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Extra text shown next to the module in the module list, e.g. the current mode. */
	public String hudInfo() {
		return null;
	}

	/** True when a world and player exist. */
	protected static boolean inGame() {
		return mc.world != null && mc.player != null;
	}

	public void info(String message) {
		Myriad.notifications().info(name, message);
	}

	public void warn(String message) {
		Myriad.notifications().warn(name, message);
	}

	public void error(String message) {
		Myriad.notifications().error(name, message);
	}

	/** Sends a client-side chat line prefixed with the Myriad tag. */
	protected void sendChat(Text text) {
		Myriad.chat(text);
	}

	@Override
	public String toString() {
		return name;
	}

	@org.jetbrains.annotations.ApiStatus.Internal
	public static void bindClient(MinecraftClient client) {
		mc = client;
	}
}
