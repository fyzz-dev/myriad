package dev.myriad.impl.render.font;

/**
 * A rasterised glyph inside a {@link GlyphPage} texture. Sizes are in pixels. The quad is drawn at
 * (pen x + {@code offsetX}, line top + {@code offsetY}) and is sized to the glyph's ink plus padding, so glyphs that
 * overhang their advance (most icon glyphs) are never clipped.
 */
public record Glyph(int texture, float u0, float v0, float u1, float v1, int cellWidth, int cellHeight, float advance,
					boolean visible, int offsetX, int offsetY) {
}
