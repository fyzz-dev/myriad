package dev.myriad.api.ui.layout;

import org.jetbrains.annotations.ApiStatus;
import com.google.gson.JsonObject;
import dev.myriad.api.ui.Rect;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The tiling of one workspace. {@code W} is an opaque window handle; layouts only compare handles by identity.
 */
@ApiStatus.NonExtendable
public interface LayoutState<W> {
	/** Adds a window. {@code focused} is the currently focused tiled window (or null), which new windows split. */
	void add(W window, W focused);

	void remove(W window);

	List<W> windows();

	/**
	 * Computes tile rectangles inside {@code area} with {@code gap} units between tiles (outer gaps are already
	 * applied to the area).
	 */
	void arrange(Rect area, float gap, BiConsumer<W, Rect> out);

	/** Swap the positions of two windows. */
	void swap(W a, W b);

	/** Flip the split orientation around {@code window} (dwindle) or do nothing. */
	default void toggleSplit(W window) {
	}

	/**
	 * Grow ({@code amount} &gt; 0) or shrink the window's tile by a fraction of its container, for keyboard resizing.
	 * Layouts that can't resize ignore it.
	 */
	default void resize(W window, float amount) {
	}

	/** Draggable boundaries between tiles, valid after the last {@link #arrange}. */
	List<Splitter> splitters();

	/** Serialise using the given window keys. */
	JsonObject save(Function<W, String> keys);

	/** Restore; {@code windows} resolves keys (returning null for windows that no longer exist). */
	void load(JsonObject json, Function<String, W> windows);

	/** A resizable boundary: {@code bounds} is the hit area; {@link #drag} receives the mouse position. */
	@ApiStatus.NonExtendable
	interface Splitter {
		Rect bounds();

		boolean vertical();

		void drag(float mouseX, float mouseY);
	}
}
