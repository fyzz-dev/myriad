package dev.myriad.impl.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** The screen that hosts the desktop; all logic lives in {@link WindowManager}. */
public final class DesktopScreen extends Screen {
	private final WindowManager wm;

	DesktopScreen(WindowManager wm) {
		super(Text.literal("Myriad"));
		this.wm = wm;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		wm.renderDesktop(context, mouseX, mouseY);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public boolean shouldPause() {
		return wm.theme().pauseGame.get();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return wm.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return wm.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return wm.mouseDragged(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		return wm.mouseScrolled(mouseX, mouseY, verticalAmount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return wm.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		return wm.charTyped(chr, modifiers);
	}

	@Override
	public void removed() {
		wm.onScreenRemoved();
	}

	@Override
	public void tick() {
		wm.tickDesktop();
	}
}
