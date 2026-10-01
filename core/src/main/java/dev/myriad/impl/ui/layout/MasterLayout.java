package dev.myriad.impl.ui.layout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.myriad.api.ui.Rect;
import dev.myriad.api.ui.layout.Layout;
import dev.myriad.api.ui.layout.LayoutState;
import dev.myriad.api.util.MyriadId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Hyprland's master layout: the first window takes the left part, the rest stack on the right. */
public final class MasterLayout implements Layout {
	public static final MyriadId ID = MyriadId.of("myriad", "master");

	@Override
	public MyriadId id() {
		return ID;
	}

	@Override
	public String name() {
		return "Master";
	}

	@Override
	public <W> LayoutState<W> createState() {
		return new State<>();
	}

	static final class State<W> implements LayoutState<W> {
		private final List<W> windows = new ArrayList<>();
		private final List<Splitter> splitters = new ArrayList<>();
		private float ratio = 0.55f;
		private Rect area = Rect.ZERO;

		@Override
		public void add(W window, W focused) {
			windows.add(window);
		}

		@Override
		public void remove(W window) {
			windows.remove(window);
		}

		@Override
		public List<W> windows() {
			return Collections.unmodifiableList(windows);
		}

		@Override
		public void arrange(Rect area, float gap, BiConsumer<W, Rect> out) {
			this.area = area;
			splitters.clear();
			int n = windows.size();
			if (n == 0) return;
			if (n == 1) {
				out.accept(windows.getFirst(), area);
				return;
			}
			float masterW = (area.w() - gap) * ratio;
			out.accept(windows.getFirst(), new Rect(area.x(), area.y(), masterW, area.h()));
			float sx = area.x() + masterW + gap, sw = area.w() - masterW - gap;
			int stack = n - 1;
			float sh = (area.h() - gap * (stack - 1)) / stack;
			for (int i = 0; i < stack; i++) {
				out.accept(windows.get(i + 1), new Rect(sx, area.y() + i * (sh + gap), sw, sh));
			}
			splitters.add(new Splitter() {
				@Override
				public Rect bounds() {
					return new Rect(area.x() + masterW - 2, area.y(), gap + 4, area.h());
				}

				@Override
				public boolean vertical() {
					return true;
				}

				@Override
				public void drag(float mouseX, float mouseY) {
					ratio = Math.clamp((mouseX - State.this.area.x()) / State.this.area.w(), 0.15f, 0.85f);
				}
			});
		}

		@Override
		public void swap(W a, W b) {
			int i = windows.indexOf(a), j = windows.indexOf(b);
			if (i < 0 || j < 0) return;
			Collections.swap(windows, i, j);
		}

		@Override
		public void resize(W window, float amount) {
			if (windows.size() < 2) return;
			ratio = Math.clamp(ratio + (windows.getFirst() == window ? amount : -amount), 0.15f, 0.85f);
		}

		@Override
		public List<Splitter> splitters() {
			return splitters;
		}

		@Override
		public JsonObject save(Function<W, String> keys) {
			JsonObject o = new JsonObject();
			o.addProperty("ratio", ratio);
			JsonArray a = new JsonArray();
			for (W w : windows) a.add(keys.apply(w));
			o.add("windows", a);
			return o;
		}

		@Override
		public void load(JsonObject json, Function<String, W> lookup) {
			windows.clear();
			if (json.has("ratio")) ratio = json.get("ratio").getAsFloat();
			if (json.has("windows")) {
				for (JsonElement e : json.getAsJsonArray("windows")) {
					W w = lookup.apply(e.getAsString());
					if (w != null) windows.add(w);
				}
			}
		}
	}
}
