package dev.myriad.api.config;

import java.nio.file.Path;
import java.util.List;

/**
 * Profiles and persistence. Module state and UI layout belong to the active profile; friends and global options
 * do not. Changes are saved automatically shortly after they happen.
 */
public interface ConfigManager {
	Path root();

	void markDirty();

	void saveNow();

	String activeProfile();

	List<String> profiles();

	/** Saves the current profile and loads {@code name}, creating it from the current state if it doesn't exist. */
	void switchProfile(String name);

	boolean deleteProfile(String name);

	String commandPrefix();

	void setCommandPrefix(String prefix);
}
