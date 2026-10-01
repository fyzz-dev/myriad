package dev.myriad.api.addon;

import com.google.gson.JsonElement;

import java.nio.file.Path;
import java.util.Optional;

/** A private folder for an addon's own data, at {@code .minecraft/myriad/addons/<mod id>/}. */
public interface AddonStorage {
	Path directory();

	/** Reads {@code name} as JSON, if it exists and parses. */
	Optional<JsonElement> readJson(String name);

	/** Atomically writes {@code json} to {@code name}. */
	void writeJson(String name, JsonElement json);
}
