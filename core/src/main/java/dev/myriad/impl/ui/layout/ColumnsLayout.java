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

/** Side-by-side columns with adjustable widths; the default for category windows (a tiled click-GUI). */
public final class ColumnsLayout implements Layout {
	public static final MyriadId ID = MyriadId.of("myriad", "columns");

	@Override
	public MyriadId id() {
		return ID;
	}

	@Override
	public String name() {
		return "Columns";
	}

	@Override
	public <W> LayoutState<W> createState() {
		return new State<>();
	}

	static final class State<W> implements LayoutState<W> {
		private final List<W> windows = new ArrayList<>();
		/** Relative widths, parallel to {@link #windows}. */
		private final List<Float> weights = new ArrayList<>();
		private final List<Splitter> splitters = new ArrayList<>();

		@Override
		public void add(W window, W focused) {
			int at = focused == null ? windows.size() : windows.indexOf(focused) + 1;
			if (at <= 0) at = windows.size();
			windows.add(at, window);
			weights.add(at, 1f);
		}

		@Override
		public void remove(W window) {
			int i = windows.indexOf(window);
			if (i < 0) return;
			windows.remove(i);
			weights.remove(i);
		}

		@Override
		public List<W> windows() {
			return Collections.unmodifiableList(windows);
		}

		private float total() {
			float t = 0;
			for (float w : weights) t += w;
			return t;
		}

		@Override
		public void arrange(Rect area, float gap, BiConsumer<W, Rect> out) {
			splitters.clear();
			int n = windows.size();
			if (n == 0) return;
			float usable = area.w() - gap * (n - 1), total = total();
			float x = area.x();
			for (int i = 0; i < n; i++) {
				float w = usable * weights.get(i) / total;
				out.accept(windows.get(i), new Rect(x, area.y(), w, area.h()));
				if (i < n - 1) {
					int left = i;
					float boundary = x + w;
					splitters.add(new Splitter() {
						@Override
						public Rect bounds() {
							return new Rect(boundary - 2, area.y(), gap + 4, area.h());
						}

						@Override
						public boolean vertical() {
							return true;
						}

						@Override
						public void drag(float mouseX, float mouseY) {
							// Move the boundary between columns left and left+1, keeping their combined width.
							float pairStart = columnStart(area, gap, left), pair = weights.get(left) + weights.get(left + 1);
							float pairWidth = usable * pair / total();
							float f = Math.clamp((mouseX - pairStart) / pairWidth, 0.1f, 0.9f);
							weights.set(left, pair * f);
							weights.set(left + 1, pair * (1 - f));
						}
					});
				}
				x += w + gap;
			}
		}

		private float columnStart(Rect area, float gap, int index) {
			float usable = area.w() - gap * (windows.size() - 1), total = total(), x = area.x();
			for (int i = 0; i < index; i++) x += usable * weights.get(i) / total + gap;
			return x;
		}

		@Override
		public void swap(W a, W b) {
			int i = windows.indexOf(a), j = windows.indexOf(b);
			if (i < 0 || j < 0) return;
			Collections.swap(windows, i, j);
		}

		@Override
		public void resize(W window, float amount) {
			int i = windows.indexOf(window);
			if (i < 0 || windows.size() < 2) return;
			int other = i + 1 < windows.size() ? i + 1 : i - 1;
			float delta = total() * amount;
			if (weights.get(i) + delta < 0.2f || weights.get(other) - delta < 0.2f) return;
			weights.set(i, weights.get(i) + delta);
			weights.set(other, weights.get(other) - delta);
		}

		@Override
		public List<Splitter> splitters() {
			return splitters;
		}

		@Override
		public JsonObject save(Function<W, String> keys) {
			JsonObject o = new JsonObject();
			JsonArray a = new JsonArray();
			for (int i = 0; i < windows.size(); i++) {
				JsonObject e = new JsonObject();
				e.addProperty("window", keys.apply(windows.get(i)));
				e.addProperty("weight", weights.get(i));
				a.add(e);
			}
			o.add("columns", a);
			return o;
		}

		@Override
		public void load(JsonObject json, Function<String, W> lookup) {
			windows.clear();
			weights.clear();
			if (!json.has("columns")) return;
			for (JsonElement el : json.getAsJsonArray("columns")) {
				JsonObject e = el.getAsJsonObject();
				W w = lookup.apply(e.get("window").getAsString());
				if (w == null) continue;
				windows.add(w);
				weights.add(e.has("weight") ? e.get("weight").getAsFloat() : 1f);
			}
		}
	}
}
