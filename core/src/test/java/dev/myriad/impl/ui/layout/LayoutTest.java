package dev.myriad.impl.ui.layout;

import dev.myriad.api.ui.Rect;
import dev.myriad.api.ui.layout.LayoutState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LayoutTest {
	private static final Rect AREA = new Rect(0, 0, 1000, 500);

	private static Map<String, Rect> arrange(LayoutState<String> s, float gap) {
		Map<String, Rect> out = new LinkedHashMap<>();
		s.arrange(AREA, gap, out::put);
		return out;
	}

	@Test
	void dwindleSplitsFocusedWindowAlongLongerSide() {
		LayoutState<String> s = new DwindleLayout().createState();
		s.add("a", null);
		assertEquals(AREA, arrange(s, 0).get("a"));
		s.add("b", "a");
		Map<String, Rect> r = arrange(s, 0);
		assertEquals(new Rect(0, 0, 500, 500), r.get("a"));
		assertEquals(new Rect(500, 0, 500, 500), r.get("b"));
		s.add("c", "b"); // b is 500x500 -> splits side by side
		r = arrange(s, 0);
		assertEquals(new Rect(500, 0, 250, 500), r.get("b"));
		assertEquals(new Rect(750, 0, 250, 500), r.get("c"));
	}

	@Test
	void dwindleGapsSeparateTilesAndKeepOuterEdges() {
		LayoutState<String> s = new DwindleLayout().createState();
		s.add("a", null);
		s.add("b", "a");
		Map<String, Rect> r = arrange(s, 10);
		assertEquals(0, r.get("a").x(), 1e-4);
		assertEquals(1000, r.get("b").right(), 1e-4);
		assertEquals(10, r.get("b").x() - r.get("a").right(), 1e-4);
	}

	@Test
	void dwindleRemoveSwapToggleAndSplitter() {
		LayoutState<String> s = new DwindleLayout().createState();
		s.add("a", null);
		s.add("b", "a");
		s.add("c", "b");
		s.remove("b");
		assertEquals(List.of("a", "c"), s.windows());
		s.swap("a", "c");
		assertEquals(List.of("c", "a"), s.windows());
		s.toggleSplit("a");
		Map<String, Rect> r = arrange(s, 0);
		assertEquals(new Rect(0, 0, 1000, 250), r.get("c"));
		LayoutState.Splitter sp = s.splitters().getFirst();
		sp.drag(0, 400);
		assertEquals(400, arrange(s, 0).get("c").h(), 1e-3);
	}

	@Test
	void dwindleSavesAndLoadsDroppingMissingWindows() {
		LayoutState<String> s = new DwindleLayout().createState();
		s.add("a", null);
		s.add("b", "a");
		s.add("c", "b");
		var json = s.save(w -> w);
		LayoutState<String> loaded = new DwindleLayout().createState();
		loaded.load(json, k -> k.equals("b") ? null : k);
		assertEquals(List.of("a", "c"), loaded.windows());
		assertEquals(2, arrange(loaded, 0).size());
	}

	@Test
	void masterPutsFirstWindowLeftAndStacksTheRest() {
		LayoutState<String> s = new MasterLayout().createState();
		s.add("a", null);
		s.add("b", null);
		s.add("c", null);
		Map<String, Rect> r = arrange(s, 0);
		assertEquals(550, r.get("a").w(), 1e-3);
		assertEquals(250, r.get("b").h(), 1e-3);
		assertEquals(250, r.get("c").y(), 1e-3);
		s.swap("a", "c");
		assertEquals("c", s.windows().getFirst());
	}
}
