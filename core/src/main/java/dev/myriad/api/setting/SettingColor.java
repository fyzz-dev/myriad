package dev.myriad.api.setting;

import dev.myriad.api.util.ColorUtil;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.ToIntFunction;

/**
 * A colour value as edited by {@link ColorSetting}: a fixed ARGB colour, a rainbow, or a <em>theme role</em> such as
 * {@link Mode#ACCENT} or {@link Mode#RED} that follows the active theme. Prefer roles for defaults so modules match
 * whatever theme the user picks. Call {@link #argb()} every frame rather than caching the result.
 */
public record SettingColor(int color, Mode mode) {
	private static ToIntFunction<Mode> palette = m -> 0xFF89B4FA;

	public enum Mode {
		STATIC, RAINBOW,
		/** Theme roles. */
		ACCENT, SECONDARY, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, TEXT;

		public boolean isThemeRole() {
			return ordinal() >= ACCENT.ordinal();
		}
	}

	public static SettingColor of(int argb) {
		return new SettingColor(argb, Mode.STATIC);
	}

	/** A theme role at full opacity. */
	public static SettingColor role(Mode role) {
		return new SettingColor(0xFFFFFFFF, role);
	}

	/** A theme role with the given alpha (0-255). */
	public static SettingColor role(Mode role, int alpha) {
		return new SettingColor(ColorUtil.withAlpha(0xFFFFFF, alpha), role);
	}

	public static SettingColor accent() {
		return role(Mode.ACCENT);
	}

	/** The colour to draw with right now. Rainbow and theme roles keep the stored colour's alpha. */
	public int argb() {
		return switch (mode) {
			case STATIC -> color;
			case RAINBOW -> ColorUtil.rainbow(1f, 0f, 0.7f, 1f, ColorUtil.alpha(color));
			default -> ColorUtil.withAlpha(palette.applyAsInt(mode), ColorUtil.alpha(color));
		};
	}

	public SettingColor withColor(int argb) {
		return new SettingColor(argb, mode);
	}

	public SettingColor withMode(Mode mode) {
		return new SettingColor(color, mode);
	}

	@ApiStatus.Internal
	public static void setPaletteProvider(ToIntFunction<Mode> provider) {
		palette = provider;
	}
}
