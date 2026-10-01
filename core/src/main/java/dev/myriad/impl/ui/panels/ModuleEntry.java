package dev.myriad.impl.ui.panels;

import dev.myriad.api.module.Module;
import dev.myriad.api.render.Animated;
import dev.myriad.api.render.Bezier;
import dev.myriad.api.render.Canvas;
import dev.myriad.api.render.FontFamily;
import dev.myriad.api.ui.widget.Label;
import dev.myriad.api.ui.widget.SettingsView;
import dev.myriad.api.ui.widget.VBox;
import dev.myriad.api.ui.widget.Widget;
import dev.myriad.api.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Set;

/**
 * A module inside a category window: a header row (click toggles the module) whose settings unfold underneath into
 * a card (right-click, the chevron, or l/→ with the keyboard). Shift+right-click opens the settings in their own
 * window.
 */
final class ModuleEntry extends Widget {
	/** Inner padding of the unfolded card. */
	private static final float PAD_X = 7, PAD_TOP = 5, PAD_BOTTOM = 5;

	private final Module module;
	private final Set<Module> expandedSet;
	private final Animated on, open;
	private final String source;
	private VBox body;

	ModuleEntry(Module module, Set<Module> expandedSet) {
		this.module = module;
		this.expandedSet = expandedSet;
		this.source = AddonNames.of(module);
		this.on = new Animated(module.isEnabled() ? 1 : 0);
		this.open = new Animated(expandedSet.contains(module) ? 1 : 0);
		this.tooltip = tooltipText();
	}

	/** Description, then the source addon and category as pills (in the category's current colour). */
	private String tooltipText() {
		return (module.description().isEmpty() ? "" : module.description() + "\n")
			+ badgeLine("\uf1e6 " + source + "@" + String.format("%08X", module.category().color()), module.category().name());
	}

	private boolean expanded() {
		return expandedSet.contains(module);
	}

	private void setExpanded(boolean value) {
		if (value) expandedSet.add(module);
		else expandedSet.remove(module);
	}

	private VBox body() {
		if (body == null) {
			body = new VBox(4, 0);
			body.add(new Info());
			body.add(SettingsView.build(module.settings));
			attachChild(body);
		}
		return body;
	}

	@Override
	protected float measure(Canvas c, float width) {
		open.animateTo(expanded() ? 1 : 0, 200, Bezier.EASE_OUT_QUINT);
		float t = open.get();
		float h = ROW + 2;
		if (t > 0.001f || expanded()) {
			float bh = body().layout(c, x + PAD_X, y + h + PAD_TOP, width - PAD_X * 2);
			h += (bh + PAD_TOP + PAD_BOTTOM) * t;
		}
		return h;
	}

	@Override
	public float navHeight() {
		return ROW + 2;
	}

	@Override
	public void render(Canvas c, float mx, float my) {
		on.animateTo(module.isEnabled() ? 1 : 0, 180, Bezier.EASE_OUT_QUINT);
		float t = on.get(), o = open.get();
		float rowH = ROW + 2;
		boolean hover = mx >= x && my >= y && mx < x + width && my < y + rowH;
		int accent = module.category().color();

		// The unfolded card sits behind the header so the two read as one block.
		if (o > 0.01f && body != null) {
			int card = ColorUtil.withAlpha(theme().surface.argb(), (int) (ColorUtil.alpha(theme().surface.argb()) * 0.55f * o));
			c.roundRect(x, y, width, height, 4, card);
			c.outline(x, y, width, height, 4, 1, ColorUtil.withAlpha(accent, (int) (45 * o)));
		}
		float corner = 4 * (1 - o);
		c.roundRect(x, y, width, rowH, 4, 4, corner, corner, hover ? theme().surfaceHover.argb() : theme().surface.argb());
		if (t > 0.01f) {
			// Square the bottom corners as the card opens, like the header itself.
			c.roundRect(x, y, width * t, rowH, 4, 4, corner, corner, ColorUtil.withAlpha(accent, (int) (55 * t)));
			c.roundRect(x, y + 3, 2, rowH - 6, 1, ColorUtil.withAlpha(accent, (int) (255 * t)));
		}
		float ty = y + (rowH - c.textHeight()) / 2;
		float rx = x + width - 10;
		float right = rx - 6;
		if (module.keybind.get().isSet()) {
			right = badge(c, module.keybind.get().displayName(), right, y + rowH / 2, theme().textDim.argb(), false) - 4;
		}
		c.text(c.ellipsize(c.defaultFont(), c.defaultFontSize(), module.name(), right - x - 6), x + 6, ty,
			ColorUtil.lerp(theme().textDim.argb(), theme().text.argb(), t));
		float iconSize = c.defaultFontSize() * 0.75f;
		String chevron = expanded() ? "" : "";
		c.text(FontFamily.MONO, iconSize, chevron, rx, y + (rowH - c.textHeight(FontFamily.MONO, iconSize)) / 2,
			expanded() ? accent : mx >= rx - 3 && hover ? theme().text.argb() : ColorUtil.withAlpha(theme().textDim.argb(), 150));
		if (o > 0.01f && body != null) {
			c.push();
			c.clip(x, y + rowH, width, height - rowH);
			if (o < 1) c.alpha(o);
			body.render(c, mx, my);
			c.pop();
		}
		if (hover) {
			tooltip = tooltipText();
			offerTooltip(mx, my);
		}
	}

	/**
	 * Draws a small pill ending at {@code right}, vertically centred on {@code cy}; returns its left edge. Tinted
	 * pills take the colour as background; plain ones are a neutral outline.
	 */
	private float badge(Canvas c, String text, float right, float cy, int color, boolean tinted) {
		float s = c.defaultFontSize() * 0.7f;
		float tw = badgeWidth(c, text, tinted);
		float th = c.textHeight(FontFamily.SANS, s) + 3;
		float bx = right - tw, by = cy - th / 2;
		if (tinted) {
			c.roundRect(bx, by, tw, th, th / 2, ColorUtil.withAlpha(color, 50));
			c.outline(bx, by, tw, th, th / 2, 1, ColorUtil.withAlpha(color, 110));
			c.text(FontFamily.SANS_BOLD, s, text, bx + 4, by + 1.5f, ColorUtil.lerp(color, theme().text.argb(), 0.35f));
		} else {
			c.outline(bx, by, tw, th, th / 2, 1, ColorUtil.withAlpha(color, 90));
			c.text(FontFamily.SANS, s, text, bx + 4, by + 1.5f, color);
		}
		return bx;
	}

	private static float badgeWidth(Canvas c, String text, boolean tinted) {
		return c.textWidth(tinted ? FontFamily.SANS_BOLD : FontFamily.SANS, c.defaultFontSize() * 0.7f, text) + 8;
	}

	/** Description, then badges for the source addon and the category, at the top of the card. */
	private final class Info extends Widget {
		private final Label description = new Label(module.description()).dim();

		@Override
		protected float measure(Canvas c, float width) {
			float h = 0;
			if (!module.description().isEmpty()) h += description.layout(c, x, y, width) + 3;
			return h + c.textHeight(FontFamily.SANS, c.defaultFontSize() * 0.7f) + 3;
		}

		@Override
		public void render(Canvas c, float mx, float my) {
			if (!module.description().isEmpty()) description.render(c, mx, my);
			float s = c.defaultFontSize() * 0.7f;
			float th = c.textHeight(FontFamily.SANS, s) + 3;
			float cy = y + height - th / 2;
			int accent = module.category().color();
			String tag = "\uf1e6 " + source;
			float bx = x + badgeWidth(c, tag, true);
			badge(c, tag, bx, cy, accent, true);
			String category = module.category().name();
			badge(c, category, bx + 4 + badgeWidth(c, category, false), cy, theme().textDim.argb(), false);
		}
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		float rowH = ROW + 2;
		if (mx >= x && my >= y && mx < x + width && my < y + rowH) {
			boolean chevron = mx >= x + width - 14;
			if (button == 1 && GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) {
				CorePanels.openModuleSettings(module, true);
			} else if (button == 1 || chevron) {
				setExpanded(!expanded());
			} else if (button == 0) {
				module.toggle();
			} else if (button == 2) {
				module.visibleInList.toggle();
			}
			return true;
		}
		return expanded() && body != null && body.mouseClicked(mx, my, button) && press(body);
	}

	@Override
	public boolean mouseScrolled(float mx, float my, float amount) {
		return expanded() && body != null && body.mouseScrolled(mx, my, amount);
	}

	// ---- keyboard: Enter/Space toggle, l/→ unfold, h/← fold, o toggles the fold ----

	@Override
	public boolean isNavigable() {
		return true;
	}

	@Override
	public void activate() {
		module.toggle();
	}

	@Override
	public void activateSecondary() {
		setExpanded(!expanded());
	}

	@Override
	public boolean adjust(int direction) {
		boolean want = direction > 0;
		if (expanded() == want) return false;
		setExpanded(want);
		return true;
	}

	@Override
	public void collectNavigable(List<Widget> out) {
		if (!isVisible()) return;
		out.add(this);
		if (expanded() && body != null) body.collectNavigable(out);
	}
}
