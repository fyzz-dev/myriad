package dev.myriad.api.ui.widget;

import dev.myriad.api.render.Canvas;
import dev.myriad.api.util.ColorUtil;

/** Empty space, optionally with a divider line. */
public class Spacer extends Widget {
	private final float size;
	private final boolean line;

	public Spacer(float size, boolean line) {
		this.size = size;
		this.line = line;
	}

	public static Spacer gap(float size) {
		return new Spacer(size, false);
	}

	public static Spacer divider() {
		return new Spacer(7, true);
	}

	@Override
	protected float measure(Canvas canvas, float width) {
		return size;
	}

	@Override
	public void render(Canvas canvas, float mx, float my) {
		if (line) canvas.rect(x, y + size / 2, width, 1, ColorUtil.withAlpha(theme().textDim.argb(), 40));
	}
}
