package dev.myriad.api.event.events;

import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Fired after the world renders. The matrix stack already contains the camera rotation; translate by
 * {@code -camera.getPos()} to draw in world coordinates (or use {@code Renderer3D}, which does that for you).
 */
public final class Render3DEvent {
	private final MatrixStack matrices;
	private final Camera camera;
	private final float tickDelta;

	public Render3DEvent(MatrixStack matrices, Camera camera, float tickDelta) {
		this.matrices = matrices;
		this.camera = camera;
		this.tickDelta = tickDelta;
	}

	public MatrixStack matrices() {
		return matrices;
	}

	public Camera camera() {
		return camera;
	}

	public float tickDelta() {
		return tickDelta;
	}
}
