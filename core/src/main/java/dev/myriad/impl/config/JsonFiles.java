package dev.myriad.impl.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** JSON read/atomic write helpers (write to a temp file, then move it into place). */
public final class JsonFiles {
	private static final Logger LOG = LoggerFactory.getLogger("Myriad/Config");
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private JsonFiles() {
	}

	public static Optional<JsonElement> read(Path file) {
		if (!Files.isRegularFile(file)) return Optional.empty();
		try {
			return Optional.of(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
		} catch (Exception e) {
			LOG.error("Could not read {}; keeping a backup and using defaults", file, e);
			try {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
			}
			return Optional.empty();
		}
	}

	public static void write(Path file, JsonElement json) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LOG.error("Could not write {}", file, e);
		}
	}
}
