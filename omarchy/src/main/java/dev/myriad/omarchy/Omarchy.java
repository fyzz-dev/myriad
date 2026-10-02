package dev.myriad.omarchy;

import dev.myriad.api.ui.ThemePalette;
import dev.myriad.api.util.ColorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads Omarchy themes. Every Omarchy theme has a {@code colors.toml} (accent, foreground, background, selection and
 * color0-15) and a {@code hyprland.lua}/{@code hyprland.conf} with border colours and rounding, so Myriad can mirror
 * any of them, and follow whichever one is active.
 */
public final class Omarchy {
	private static final Logger LOG = LoggerFactory.getLogger("Myriad Omarchy");
	private static final Path HOME = Path.of(System.getProperty("user.home"));
	public static final Path CURRENT = HOME.resolve(".local/state/omarchy/current/theme");
	private static final Path CURRENT_NAME = HOME.resolve(".local/state/omarchy/current/theme.name");
	private static final List<Path> THEME_ROOTS = List.of(HOME.resolve(".local/share/omarchy/themes"), HOME.resolve(".config/omarchy/themes"));
	private static final Pattern HEX = Pattern.compile("rgba?\\(([0-9a-fA-F]{6,8})\\)|#([0-9a-fA-F]{6,8})");

	private Omarchy() {
	}

	public static boolean isInstalled() {
		return Files.isRegularFile(CURRENT.resolve("colors.toml"));
	}

	/**
	 * The system looks like Omarchy (its kernel) but the theme files can't be read, which happens when the game runs
	 * in a Flatpak sandbox (e.g. the Flatpak Prism Launcher). Returns the command that grants access, or null.
	 */
	public static String blockedHint() {
		if (isInstalled() || !System.getProperty("os.version", "").contains("omarchy")) return null;
		String app = System.getenv("FLATPAK_ID");
		if (app == null && !Files.exists(Path.of("/.flatpak-info"))) return null;
		return "flatpak override --user --filesystem=~/.local/state/omarchy:ro " + (app != null ? app : "<launcher app id>");
	}

	/** Changes whenever the active Omarchy theme does (switched, or its colours edited). */
	public static String signature() {
		try {
			return currentName().orElse("") + "@" + Files.getLastModifiedTime(CURRENT.resolve("colors.toml")).toMillis();
		} catch (IOException e) {
			return "";
		}
	}

	/** Name of the active Omarchy theme, or empty. */
	public static Optional<String> currentName() {
		try {
			return Files.isRegularFile(CURRENT_NAME) ? Optional.of(Files.readString(CURRENT_NAME).trim()) : Optional.empty();
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/** Installed themes by directory name (user themes override bundled ones). */
	public static Map<String, Path> installed() {
		Map<String, Path> out = new LinkedHashMap<>();
		for (Path root : THEME_ROOTS) {
			if (!Files.isDirectory(root)) continue;
			try (Stream<Path> s = Files.list(root)) {
				s.filter(p -> Files.isRegularFile(p.resolve("colors.toml"))).sorted().forEach(p -> out.put(p.getFileName().toString(), p));
			} catch (IOException e) {
				LOG.warn("Could not list {}", root, e);
			}
		}
		return out;
	}

	public static Optional<ThemePalette> read(Path dir) {
		try {
			Map<String, Integer> c = parseColors(Files.readString(dir.resolve("colors.toml")));
			int bg = c.getOrDefault("background", 0xFF1A1B26), fg = c.getOrDefault("foreground", 0xFFC0CAF5);
			int[] ansi = new int[16];
			for (int i = 0; i < 16; i++) ansi[i] = c.getOrDefault("color" + i, fg);
			int sel = c.getOrDefault("selection_background", ColorUtil.lerp(bg, fg, 0.2f));
			int accent = c.getOrDefault("accent", ansi[4]);
			Integer from = null, to = null, inactive = null, rounding = null;
			for (String name : new String[]{"hyprland.lua", "hyprland.conf"}) {
				Path p = dir.resolve(name);
				if (!Files.isRegularFile(p)) continue;
				for (String line : Files.readAllLines(p)) {
					String l = line.trim();
					if (l.startsWith("--") || l.startsWith("#")) continue;
					String lower = l.toLowerCase();
					if (lower.contains("inactive_border")) {
						List<Integer> cols = colors(l);
						if (!cols.isEmpty() && inactive == null) inactive = cols.getFirst();
					} else if (lower.contains("active_border")) {
						List<Integer> cols = colors(l);
						if (!cols.isEmpty() && from == null) {
							from = cols.getFirst();
							to = cols.size() > 1 ? cols.get(1) : cols.getFirst();
						}
					} else if (lower.matches(".*\\brounding\\s*=\\s*\\d+.*") && !lower.contains("rounding_power")) {
						Matcher m = Pattern.compile("rounding\\s*=\\s*(\\d+)").matcher(lower);
						if (m.find()) rounding = Integer.parseInt(m.group(1));
					}
				}
			}
			return Optional.of(new ThemePalette(bg, fg, sel, accent, ansi, from, to, inactive, rounding));
		} catch (Exception e) {
			LOG.warn("Could not read Omarchy theme {}", dir, e);
			return Optional.empty();
		}
	}

	private static Map<String, Integer> parseColors(String toml) {
		Map<String, Integer> out = new HashMap<>();
		for (String line : toml.split("\n")) {
			int eq = line.indexOf('=');
			if (eq < 0) continue;
			String key = line.substring(0, eq).trim();
			List<Integer> cols = colors(line.substring(eq + 1));
			if (!cols.isEmpty()) out.put(key, cols.getFirst());
		}
		return out;
	}

	/** All colours in a line: {@code #rrggbb}, {@code rgb(rrggbb)} or {@code rgba(rrggbbaa)} (Hyprland order). */
	private static List<Integer> colors(String s) {
		List<Integer> out = new ArrayList<>();
		Matcher m = HEX.matcher(s);
		while (m.find()) {
			String hex = m.group(1) != null ? m.group(1) : m.group(2);
			long v = Long.parseLong(hex, 16);
			int argb = hex.length() == 8 ? (int) (((v & 0xFF) << 24) | (v >>> 8)) : (int) (0xFF000000L | v);
			out.add(argb);
		}
		return out;
	}
}
