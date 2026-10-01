package dev.myriad.essentials.modules.render;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.Render2DEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.Canvas;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.util.ColorUtil;

/**
 * Replaces the vanilla crosshair with a crisp, themed one: a classic cross with a gap, a dot, or a circle, with an
 * optional outline and attack cooldown bar.
 */
public class Crosshair extends Module {

	public enum Style {
		CROSS, DOT, CIRCLE, CROSS_DOT
	}

	private final EnumSetting<Style> style = sgGeneral.enumSetting("Style", Style.CROSS).build();
	private final ColorSetting color = sgGeneral.color("Color").defaultValue(SettingColor.role(SettingColor.Mode.TEXT)).build();
	private final DoubleSetting length = sgGeneral.doubleSetting("Length").description("Length of each arm.").defaultValue(4).range(1, 12).decimals(1)
		.visible(() -> style.get() == Style.CROSS || style.get() == Style.CROSS_DOT).build();
	private final DoubleSetting width = sgGeneral.doubleSetting("Width").description("Thickness of the arms or ring.").defaultValue(1).range(0.5, 4).decimals(1).build();
	private final DoubleSetting gap = sgGeneral.doubleSetting("Gap").description("Space between the arms and the centre, or the ring radius.").defaultValue(2).range(0, 10).decimals(1)
		.visible(() -> style.get() != Style.DOT).build();
	private final DoubleSetting dotSize = sgGeneral.doubleSetting("Dot Size").defaultValue(1.5).range(0.5, 5).decimals(1)
		.visible(() -> style.get() == Style.DOT || style.get() == Style.CROSS_DOT).build();
	private final BoolSetting outline = sgGeneral.bool("Outline").description("A dark edge so it shows on bright backgrounds.").defaultValue(true).build();
	private final BoolSetting cooldown = sgGeneral.bool("Cooldown Indicator").description("A bar under the crosshair while your attack recharges.").defaultValue(true).build();
	private final BoolSetting thirdPerson = sgGeneral.bool("Third Person").description("Also draw it in third person.").build();

	public Crosshair() {
		super(Categories.RENDER, "Crosshair", "A custom crosshair.");
	}

	@Subscribe
	private void onRender(Render2DEvent e) {
		if (!inGame() || mc.options.hudHidden || mc.currentScreen != null) return;
		if (!mc.options.getPerspective().isFirstPerson() && !thirdPerson.get()) return;
		Canvas c = e.canvas();
		float cx = mc.getWindow().getScaledWidth() / 2f, cy = mc.getWindow().getScaledHeight() / 2f;
		int col = color.argb(), edge = ColorUtil.withAlpha(0xFF000000, ColorUtil.alpha(col) * 3 / 4);
		float w = width.getFloat(), g = gap.getFloat(), l = length.getFloat();
		float extent = switch (style.get()) {
			case DOT -> dotSize.getFloat();
			case CIRCLE -> g + w;
			default -> g + l;
		};
		if (outline.get()) shape(c, cx, cy, w, g, l, edge, 0.75f);
		shape(c, cx, cy, w, g, l, col, 0);
		if (cooldown.get()) {
			float progress = mc.player.getAttackCooldownProgress(0);
			if (progress < 1) {
				float bw = 15, by = cy + extent + 5;
				c.rect(cx - bw / 2, by, bw, 1, ColorUtil.shade(col, 0.4f));
				c.rect(cx - bw / 2, by, bw * progress, 1, col);
			}
		}
	}

	private void shape(Canvas c, float cx, float cy, float w, float g, float l, int col, float grow) {
		Style s = style.get();
		if (s == Style.CROSS || s == Style.CROSS_DOT) {
			c.rect(cx - w / 2 - grow, cy - g - l - grow, w + grow * 2, l + grow * 2, col);
			c.rect(cx - w / 2 - grow, cy + g - grow, w + grow * 2, l + grow * 2, col);
			c.rect(cx - g - l - grow, cy - w / 2 - grow, l + grow * 2, w + grow * 2, col);
			c.rect(cx + g - grow, cy - w / 2 - grow, l + grow * 2, w + grow * 2, col);
		}
		if (s == Style.DOT || s == Style.CROSS_DOT) {
			float d = dotSize.getFloat();
			c.circle(cx, cy, d / 2 + grow, col);
		}
		if (s == Style.CIRCLE) {
			float r = g + w / 2;
			c.outline(cx - r - grow, cy - r - grow, (r + grow) * 2, (r + grow) * 2, r + grow, w + grow * 2, col);
		}
	}
}
