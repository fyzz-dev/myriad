package dev.myriad.essentials.hud;

import dev.myriad.api.render.Canvas;
import dev.myriad.api.render.FontFamily;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.StringSetting;
import dev.myriad.api.ui.Rect;
import dev.myriad.api.ui.hud.HudPanel;
import dev.myriad.api.ui.hud.HudStyle;
import net.fabricmc.loader.api.FabricLoader;

public final class WatermarkPanel extends HudPanel {
	private final StringSetting text = sgGeneral.string("Text").defaultValue("Myriad").build();
	private final BoolSetting version = sgGeneral.bool("Show Version").defaultValue(true).build();

	private final HudStyle style = new HudStyle(settings, HudStyle.Mode.GRADIENT, true);

	private static final String VERSION = FabricLoader.getInstance().getModContainer("myriad")
		.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");

	public WatermarkPanel() {
		super("Watermark", "\uf02b", 1.5);
	}

	@Override
	public Rect preferredSize(Canvas c) {
		float s = fontSize(c);
		float w = c.textWidth(FontFamily.SANS_BOLD, s, text.get());
		if (version.get()) w += 3 + c.textWidth(FontFamily.SANS, s * 0.6f, VERSION);
		return new Rect(0, 0, w + 1, c.textHeight(FontFamily.SANS_BOLD, s) + 1);
	}

	@Override
	public void render(Canvas c, float w, float h, float mx, float my) {
		float s = fontSize(c);
		String t = text.get();
		float x = 0;
		for (int i = 0; i < t.length(); i++) {
			String ch = String.valueOf(t.charAt(i));
			x += HudStyle.draw(c, FontFamily.SANS_BOLD, s, ch, x, 0, style.color(i, t.length()), style.shadow());
		}
		if (version.get()) {
			float vs = s * 0.6f;
			HudStyle.draw(c, FontFamily.SANS, vs, VERSION, x + 3, c.textHeight(FontFamily.SANS_BOLD, s) - c.textHeight(FontFamily.SANS, vs) - 1, style.label(), style.shadow());
		}
	}
}
