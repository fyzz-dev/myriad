package dev.myriad.essentials.util;

/** A 0..1 factor that moves towards 1 while on and towards 0 while off, over a fixed duration. */
public final class Fade {
	private final long durationMs;
	private boolean on;
	private float factor;
	private long last = System.currentTimeMillis();

	public Fade(boolean on, long durationMs) {
		this.durationMs = Math.max(1, durationMs);
		this.on = on;
		this.factor = on ? 1 : 0;
	}

	public void set(boolean on) {
		this.on = on;
	}

	public boolean isOn() {
		return on;
	}

	public float get() {
		long now = System.currentTimeMillis();
		float d = (now - last) / (float) durationMs;
		last = now;
		factor = on ? Math.min(1, factor + d) : Math.max(0, factor - d);
		return factor;
	}
}
