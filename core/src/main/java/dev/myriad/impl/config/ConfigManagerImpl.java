package dev.myriad.impl.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.myriad.api.config.ConfigManager;
import dev.myriad.api.module.Module;
import dev.myriad.api.service.KeyAction;
import dev.myriad.api.util.Keybind;
import dev.myriad.api.util.MyriadId;
import dev.myriad.impl.MyriadImpl;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Persists Myriad to {@code .minecraft/myriad/}. Module entries for modules that aren't installed are carried over
 * unchanged on every save, so removing an addon and adding it back later loses nothing.
 */
public final class ConfigManagerImpl implements ConfigManager {
	private static final Logger LOG = LoggerFactory.getLogger("Myriad/Config");
	private static final long SAVE_DELAY_MS = 2000;

	private final MyriadImpl myriad;
	private final Path root = FabricLoader.getInstance().getGameDir().resolve("myriad");
	private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Myriad Config IO");
		t.setDaemon(true);
		return t;
	});

	private String profile = "default";
	private String prefix = ".";
	private JsonObject rawModules = new JsonObject();
	private JsonObject rawKeyActions = new JsonObject();
	private boolean loading;
	private long dirtySince = -1;

	public ConfigManagerImpl(MyriadImpl myriad) {
		this.myriad = myriad;
	}

	@Override
	public Path root() {
		return root;
	}

	private Path profileDir(String name) {
		return root.resolve("profiles").resolve(name);
	}

	@Override
	public void markDirty() {
		if (!loading && dirtySince < 0) dirtySince = System.currentTimeMillis();
	}

	/** Called every tick; saves once things have been quiet for a moment. */
	public void tick() {
		if (dirtySince >= 0 && System.currentTimeMillis() - dirtySince > SAVE_DELAY_MS) {
			dirtySince = -1;
			save(true);
		}
	}

	public void load() {
		loading = true;
		try {
			JsonFiles.read(root.resolve("myriad.json")).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).ifPresent(o -> {
				if (o.has("profile")) profile = sanitize(o.get("profile").getAsString());
				if (o.has("prefix")) prefix = o.get("prefix").getAsString();
				if (o.has("keyActions") && o.get("keyActions").isJsonObject()) rawKeyActions = o.getAsJsonObject("keyActions");
				// "Open Desktop" was renamed to "Open Menu"; keep the user's bind.
				if (rawKeyActions.has("myriad:open_desktop") && !rawKeyActions.has("myriad:open_menu")) {
					rawKeyActions.add("myriad:open_menu", rawKeyActions.get("myriad:open_desktop"));
				}
			});
			for (KeyAction a : myriad.keyActions()) {
				JsonElement e = rawKeyActions.get(a.id().toString());
				if (e != null) a.setBind(Keybind.deserialize(e.getAsString()));
			}
			loadProfile(profile);
		} finally {
			loading = false;
		}
	}

	private void loadProfile(String name) {
		Path dir = profileDir(name);
		rawModules = JsonFiles.read(dir.resolve("modules.json")).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(new JsonObject());
		for (Module m : myriad.modules()) {
			JsonElement e = rawModules.get(m.id().toString());
			if (e == null || !e.isJsonObject()) {
				m.settings.resetAll();
				m.setEnabledSilently(false);
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			if (o.has("settings") && o.get("settings").isJsonObject()) m.settings.fromJson(o.getAsJsonObject("settings"));
			m.setEnabledSilently(o.has("enabled") && o.get("enabled").getAsBoolean());
		}
		JsonObject ui = JsonFiles.read(dir.resolve("ui.json")).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(null);
		myriad.windowManager().load(ui);
	}

	/** Builds the JSON on this (render) thread; writes on the IO thread if {@code async}. */
	private void save(boolean async) {
		JsonObject global = new JsonObject();
		global.addProperty("profile", profile);
		global.addProperty("prefix", prefix);
		JsonObject keys = rawKeyActions.deepCopy();
		for (KeyAction a : myriad.keyActions()) keys.addProperty(a.id().toString(), a.bind().serialize());
		global.add("keyActions", keys);

		JsonObject modules = rawModules.deepCopy();
		for (Module m : myriad.modules()) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", m.isEnabled());
			o.add("settings", m.settings.toJson());
			modules.add(m.id().toString(), o);
		}
		rawModules = modules;
		JsonObject ui = myriad.windowManager().save();
		Path dir = profileDir(profile);
		Runnable write = () -> {
			JsonFiles.write(root.resolve("myriad.json"), global);
			JsonFiles.write(dir.resolve("modules.json"), modules);
			if (ui != null) JsonFiles.write(dir.resolve("ui.json"), ui);
		};
		if (async) io.execute(write);
		else write.run();
	}

	@Override
	public void saveNow() {
		dirtySince = -1;
		save(false);
	}

	public void shutdown() {
		saveNow();
		io.shutdown();
	}

	@Override
	public String activeProfile() {
		return profile;
	}

	@Override
	public List<String> profiles() {
		List<String> list = new ArrayList<>();
		Path dir = root.resolve("profiles");
		if (Files.isDirectory(dir)) {
			try (Stream<Path> s = Files.list(dir)) {
				s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted(Comparator.naturalOrder()).forEach(list::add);
			} catch (IOException e) {
				LOG.error("Could not list profiles", e);
			}
		}
		if (!list.contains(profile)) list.add(profile);
		return list;
	}

	@Override
	public void switchProfile(String name) {
		name = sanitize(name);
		if (name.isEmpty() || name.equals(profile)) return;
		saveNow();
		boolean exists = Files.isDirectory(profileDir(name));
		profile = name;
		if (exists) {
			loading = true;
			try {
				loadProfile(name);
			} finally {
				loading = false;
			}
		}
		// A new profile starts as a copy of the current state.
		saveNow();
	}

	@Override
	public void reload() {
		loading = true;
		try {
			loadProfile(profile);
		} finally {
			loading = false;
		}
	}

	@Override
	public boolean deleteProfile(String name) {
		name = sanitize(name);
		if (name.equals(profile)) return false;
		Path dir = profileDir(name);
		if (!Files.isDirectory(dir)) return false;
		try (Stream<Path> s = Files.walk(dir)) {
			s.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
			return true;
		} catch (Exception e) {
			LOG.error("Could not delete profile {}", name, e);
			return false;
		}
	}

	@Override
	public String commandPrefix() {
		return prefix;
	}

	@Override
	public void setCommandPrefix(String prefix) {
		if (prefix == null || prefix.isBlank()) return;
		this.prefix = prefix.trim();
		markDirty();
	}

	private static String sanitize(String name) {
		return name.trim().replaceAll("[^A-Za-z0-9_\\-]", "_");
	}

	/** For tests and tooling: the raw module JSON including entries for modules not currently installed. */
	public JsonObject rawModules() {
		return rawModules;
	}

}
