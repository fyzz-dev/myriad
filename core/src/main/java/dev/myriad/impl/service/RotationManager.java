package dev.myriad.impl.service;

import dev.myriad.api.event.Priority;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.MovementPacketsEvent;
import dev.myriad.api.service.Rotations;
import dev.myriad.api.util.MathUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Per-tick rotation requests; the highest priority one is written into the movement packet. */
public final class RotationManager implements Rotations {
	private final MinecraftClient mc = MinecraftClient.getInstance();
	private Object owner;
	private float yaw, pitch;
	private int priority = Integer.MIN_VALUE;
	private boolean rotating;
	private float serverYaw, serverPitch;
	private Runnable callback, sentCallback;

	@Override
	public void request(Object owner, float yaw, float pitch, int priority) {
		request(owner, yaw, pitch, priority, null);
	}

	@Override
	public synchronized void request(Object owner, float yaw, float pitch, int priority, Runnable afterSent) {
		if (this.owner == null || priority > this.priority) {
			this.owner = owner;
			this.yaw = yaw;
			this.pitch = MathHelper.clamp(pitch, -90, 90);
			this.priority = priority;
			this.callback = afterSent;
		}
	}

	/** Runs the winning request's callback now that its rotation has been sent. */
	@Subscribe
	private void onMovementSent(MovementPacketsEvent.Post e) {
		Runnable run;
		synchronized (this) {
			run = sentCallback;
			sentCallback = null;
		}
		if (run != null) run.run();
	}

	@Subscribe(priority = Priority.LOW)
	private synchronized void onMovement(MovementPacketsEvent e) {
		if (owner != null) {
			// Keep the sent yaw continuous with the real one to avoid huge deltas the server would flag.
			float base = mc.player != null ? mc.player.getYaw() : e.yaw;
			e.yaw = base + MathHelper.wrapDegrees(yaw - base);
			e.pitch = pitch;
			rotating = true;
		} else {
			rotating = false;
		}
		serverYaw = e.yaw;
		serverPitch = e.pitch;
		sentCallback = owner != null ? callback : null;
		callback = null;
		owner = null;
		priority = Integer.MIN_VALUE;
	}

	@Override
	public boolean isRotating() {
		return rotating;
	}

	@Override
	public float serverYaw() {
		return serverYaw;
	}

	@Override
	public float serverPitch() {
		return serverPitch;
	}

	@Override
	public float[] anglesTo(Vec3d target) {
		return MathUtil.anglesTo(mc.player.getEyePos(), target);
	}
}
