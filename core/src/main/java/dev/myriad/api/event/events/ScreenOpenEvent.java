package dev.myriad.api.event.events;

import dev.myriad.api.event.Cancellable;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

/** A screen is about to open ({@code null} = closing to the game). Replace it with {@link #setScreen} or cancel. */
public final class ScreenOpenEvent extends Cancellable {
	private @Nullable Screen screen;

	public ScreenOpenEvent(@Nullable Screen screen) {
		this.screen = screen;
	}

	public @Nullable Screen screen() {
		return screen;
	}

	public void setScreen(@Nullable Screen screen) {
		this.screen = screen;
	}
}
