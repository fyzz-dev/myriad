package com.example.myriadaddon.waypoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.myriad.api.addon.AddonStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every waypoint, grouped by server or singleplayer world, saved in this addon's own storage folder
 * ({@code .minecraft/myriad/addons/myriad-example/waypoints.json}).
 *
 * <p>This is addon state, not module state: the module, the command, the window and the HUD element all share one
 * store, handed to them when the addon registers them. Module settings are saved by Myriad; data like this is yours
 * to save, and {@link AddonStorage} gives you a folder and atomic JSON writes for it.
 */
public final class WaypointStore {
	private static final String FILE = "waypoints.json";

	private final AddonStorage storage;
	private final Map<String, List<Waypoint>> worlds = new LinkedHashMap<>();
	private int version;

	public WaypointStore(AddonStorage storage) {
		this.storage = storage;
	}

	public void load() {
		worlds.clear();
		storage.readJson(FILE).filter(JsonElement::isJsonObject).ifPresent(json -> {
			for (var e : json.getAsJsonObject().entrySet()) {
				List<Waypoint> list = new ArrayList<>();
				for (JsonElement w : e.getValue().getAsJsonArray()) {
					try {
						list.add(Waypoint.fromJson(w.getAsJsonObject()));
					} catch (RuntimeException ignored) {
						// Skip a hand-edited entry that doesn't parse rather than losing the whole file.
					}
				}
				worlds.put(e.getKey(), list);
			}
		});
		version++;
	}

	private void save() {
		JsonObject json = new JsonObject();
		for (var e : worlds.entrySet()) {
			if (e.getValue().isEmpty()) continue;
			JsonArray arr = new JsonArray();
			for (Waypoint w : e.getValue()) arr.add(w.toJson());
			json.add(e.getKey(), arr);
		}
		storage.writeJson(FILE, json);
		version++;
	}

	/** Goes up on every change. Compare it to rebuild UI only when something changed. */
	public int version() {
		return version;
	}

	/** The current server's address or singleplayer world name, or null outside a world. */
	public static @Nullable String worldKey() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) return null;
		if (mc.getCurrentServerEntry() != null) return "server:" + mc.getCurrentServerEntry().address;
		if (mc.getServer() != null) return "local:" + mc.getServer().getSaveProperties().getLevelName();
		return null;
	}

	public static @Nullable String currentDimension() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.world == null ? null : mc.world.getRegistryKey().getValue().toString();
	}

	/** Waypoints for this world, in every dimension. */
	public List<Waypoint> here() {
		String key = worldKey();
		return key == null ? List.of() : List.copyOf(worlds.getOrDefault(key, List.of()));
	}

	/** Waypoints for this world and dimension. */
	public List<Waypoint> inDimension() {
		String dim = currentDimension();
		return here().stream().filter(w -> w.dimension().equals(dim)).toList();
	}

	public Optional<Waypoint> find(String name) {
		return here().stream().filter(w -> w.name().equalsIgnoreCase(name)).findFirst();
	}

	/** The closest visible waypoint in this dimension. */
	public Optional<Waypoint> nearest(Vec3d from) {
		return inDimension().stream().filter(Waypoint::visible).min(Comparator.comparingDouble(w -> w.center().squaredDistanceTo(from)));
	}

	/** Adds a waypoint here, replacing one with the same name. Returns false outside a world. */
	public boolean put(Waypoint waypoint) {
		String key = worldKey();
		if (key == null) return false;
		List<Waypoint> list = worlds.computeIfAbsent(key, k -> new ArrayList<>());
		list.removeIf(w -> w.name().equalsIgnoreCase(waypoint.name()));
		list.add(waypoint);
		save();
		return true;
	}

	/** A waypoint at {@code pos} in the current dimension. */
	public boolean put(String name, BlockPos pos) {
		String dim = currentDimension();
		return dim != null && put(new Waypoint(name, pos, dim, 0, true));
	}

	public boolean remove(String name) {
		String key = worldKey();
		if (key == null || !worlds.containsKey(key) || !worlds.get(key).removeIf(w -> w.name().equalsIgnoreCase(name))) return false;
		save();
		return true;
	}

	public boolean setVisible(String name, boolean visible) {
		return find(name).map(w -> put(w.withVisible(visible))).orElse(false);
	}

	/** Removes every waypoint here. Returns how many there were. */
	public int clear() {
		String key = worldKey();
		List<Waypoint> removed = key == null ? null : worlds.remove(key);
		if (removed == null) return 0;
		save();
		return removed.size();
	}

	/** "Waypoint 3" etc.: the first unused default name. */
	public String nextName(String base) {
		for (int i = 1; ; i++) {
			String name = base + " " + i;
			if (find(name).isEmpty()) return name;
		}
	}
}
