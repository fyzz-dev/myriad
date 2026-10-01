package dev.myriad.api.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The settings of one module or panel, organised in groups. Setting ids are unique within their group; the JSON
 * form nests by group ({@code {"general": {"range": 4.5}, "targets": {...}}}). Commands accept a bare setting id
 * when it is unambiguous, or {@code group.setting}.
 */
public class Settings {
	private final List<SettingGroup> groups = new ArrayList<>();

	/** Returns the group with this name, creating it if needed. */
	public SettingGroup group(String name) {
		for (SettingGroup g : groups) if (g.name().equals(name)) return g;
		SettingGroup g = new SettingGroup(this, name);
		// The first group (a module's "General") starts open; the rest are sub-menus and start folded.
		g.setExpanded(groups.isEmpty());
		groups.add(g);
		return g;
	}

	public List<SettingGroup> groups() {
		return Collections.unmodifiableList(groups);
	}

	public List<Setting<?>> all() {
		List<Setting<?>> list = new ArrayList<>();
		for (SettingGroup g : groups) list.addAll(g.settings());
		return list;
	}

	/** Looks up {@code group.setting}, or a bare setting id (first match). */
	public Optional<Setting<?>> get(String key) {
		int dot = key.indexOf('.');
		if (dot > 0) {
			String group = key.substring(0, dot), id = key.substring(dot + 1);
			for (SettingGroup g : groups) {
				if (!g.id().equalsIgnoreCase(group)) continue;
				for (Setting<?> s : g.settings()) if (s.id().equalsIgnoreCase(id)) return Optional.of(s);
			}
			return Optional.empty();
		}
		for (SettingGroup g : groups) for (Setting<?> s : g.settings()) if (s.id().equalsIgnoreCase(key)) return Optional.of(s);
		return Optional.empty();
	}

	/** The shortest key that identifies {@code setting}: its id, or {@code group.id} if another group reuses the id. */
	public String keyOf(Setting<?> setting) {
		int matches = 0;
		for (Setting<?> s : all()) if (s.id().equals(setting.id())) matches++;
		return matches > 1 && setting.group() != null ? setting.group().id() + "." + setting.id() : setting.id();
	}

	void checkUnique(SettingGroup group, Setting<?> setting) {
		for (Setting<?> s : group.settings()) {
			if (s.id().equals(setting.id())) throw new IllegalArgumentException("Duplicate setting id '" + setting.id() + "' in group '" + group.name() + "'");
		}
	}

	public void resetAll() {
		for (Setting<?> s : all()) s.reset();
	}

	/**
	 * Only values that differ from their defaults are written, so improving a default in an update reaches users who
	 * never changed it.
	 */
	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		for (SettingGroup g : groups) {
			JsonObject go = new JsonObject();
			for (Setting<?> s : g.settings()) if (s.isSerializable() && !s.isDefault()) go.add(s.id(), s.toJson());
			if (!go.isEmpty()) o.add(g.id(), go);
		}
		return o;
	}

	/**
	 * Loads known groups/ids. Settings missing from the JSON are reset to their defaults (only non-defaults are
	 * saved); unknown keys are ignored (the config layer preserves them separately).
	 */
	public void fromJson(JsonObject o) {
		for (SettingGroup g : groups) {
			JsonElement ge = o.get(g.id());
			JsonObject go = ge != null && ge.isJsonObject() ? ge.getAsJsonObject() : new JsonObject();
			for (Setting<?> s : g.settings()) {
				if (!s.isSerializable()) continue;
				JsonElement e = go.get(s.id());
				if (e == null) {
					s.reset();
					continue;
				}
				try {
					s.fromJson(e);
				} catch (RuntimeException ignored) {
					// Keep the current value; a bad entry must not stop the rest from loading.
				}
			}
		}
	}
}
