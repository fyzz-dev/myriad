package dev.myriad.api.ui.layout;

import dev.myriad.api.registry.Identified;

/**
 * A tiling algorithm. Each workspace has its own {@link LayoutState}; switching a workspace's layout re-inserts its
 * tiled windows into a fresh state. Myriad ships {@code dwindle} (Hyprland's default binary-split tree) and
 * {@code master}; addons can register more.
 */
public interface Layout extends Identified {
	String name();

	<W> LayoutState<W> createState();
}
