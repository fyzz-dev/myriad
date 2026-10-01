package dev.myriad.impl.addon;

import com.google.gson.JsonElement;
import dev.myriad.api.addon.AddonStorage;
import dev.myriad.impl.config.JsonFiles;

import java.nio.file.Path;
import java.util.Optional;

record AddonStorageImpl(Path directory) implements AddonStorage {
	@Override
	public Optional<JsonElement> readJson(String name) {
		return JsonFiles.read(directory.resolve(name));
	}

	@Override
	public void writeJson(String name, JsonElement json) {
		JsonFiles.write(directory.resolve(name), json);
	}
}
