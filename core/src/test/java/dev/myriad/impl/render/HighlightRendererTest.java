package dev.myriad.impl.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Highlight ids survive the trip through an outline colour (and so through the RGB of the mask). */
class HighlightRendererTest {
	@Test
	void idsRoundTripThroughOutlineColours() {
		for (int id : new int[]{0, 1, 255, 256, 65535, 65536, 0xABCDEF, (1 << 24) - 1}) {
			int argb = HighlightRenderer.encode(id);
			assertEquals(0xFF, argb >>> 24, "opaque, so never 0 (vanilla's 'no outline')");
			assertEquals(id, HighlightRenderer.decode(argb));
		}
		// Low byte in red, as the shaders read it.
		assertEquals(0xFF010000, HighlightRenderer.encode(1));
		assertEquals(0xFF000100, HighlightRenderer.encode(256));
	}
}
