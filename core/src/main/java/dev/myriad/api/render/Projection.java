package dev.myriad.api.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Maps world positions to the screen using the camera of the last rendered frame. Use it from a
 * {@code Render2DEvent} handler to place labels over things in the world (nametags, logout spots, item names).
 * Screen coordinates are scaled GUI pixels, the same units as {@code Render2DEvent.canvas()}.
 */
public final class Projection {
	private static final Matrix4f view = new Matrix4f();
	private static final Matrix4f projection = new Matrix4f();
	private static Vec3d camera = Vec3d.ZERO;
	private static boolean valid;

	private Projection() {
	}

	/** Called by Myriad after the world renders. */
	@ApiStatus.Internal
	public static void update(Matrix4f positionMatrix, Matrix4f projectionMatrix, Vec3d cameraPos) {
		view.set(positionMatrix);
		projection.set(projectionMatrix);
		camera = cameraPos;
		valid = true;
	}

	/** The camera position of the last frame. */
	public static Vec3d camera() {
		return camera;
	}

	/**
	 * Projects {@code world} to the screen. Returns {@code (x, y, depth)} in scaled GUI pixels, or null when the point
	 * is behind the camera. Points off to the side still project (with x/y outside the screen).
	 */
	public static @Nullable Vec3d toScreen(Vec3d world) {
		if (!valid) return null;
		Vector4f v = new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1f);
		view.transform(v);
		projection.transform(v);
		if (v.w <= 0.05f) return null;
		float nx = v.x / v.w, ny = v.y / v.w;
		var window = MinecraftClient.getInstance().getWindow();
		double x = (nx * 0.5 + 0.5) * window.getScaledWidth();
		double y = (0.5 - ny * 0.5) * window.getScaledHeight();
		return new Vec3d(x, y, v.w);
	}

	/** Whether a projected point lies on screen (with {@code margin} extra pixels around the edges). */
	public static boolean onScreen(Vec3d screen, double margin) {
		var window = MinecraftClient.getInstance().getWindow();
		return screen != null && screen.x >= -margin && screen.y >= -margin
			&& screen.x <= window.getScaledWidth() + margin && screen.y <= window.getScaledHeight() + margin;
	}
}
