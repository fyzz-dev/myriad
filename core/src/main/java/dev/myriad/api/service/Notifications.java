package dev.myriad.api.service;

/** Toast notifications, drawn by the window manager overlay both in game and on the desktop. */
public interface Notifications {
	enum Level {
		INFO, SUCCESS, WARNING, ERROR
	}

	/**
	 * Shows a notification. A notification with the same non-null {@code key} replaces the previous one instead of
	 * stacking (useful for repeated status updates).
	 */
	void send(String title, String message, Level level, long durationMs, String key);

	default void info(String title, String message) {
		send(title, message, Level.INFO, 3000, null);
	}

	default void success(String title, String message) {
		send(title, message, Level.SUCCESS, 3000, null);
	}

	default void warn(String title, String message) {
		send(title, message, Level.WARNING, 4000, null);
	}

	default void error(String title, String message) {
		send(title, message, Level.ERROR, 5000, null);
	}
}
