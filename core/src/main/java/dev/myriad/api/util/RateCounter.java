package dev.myriad.api.util;

import java.util.ArrayDeque;

/** Counts events over the last second: clicks per second, packets per second, placements per second. Thread-safe. */
public final class RateCounter {
	private final ArrayDeque<Long> times = new ArrayDeque<>();
	private final long windowMs;

	public RateCounter() {
		this(1000);
	}

	public RateCounter(long windowMs) {
		this.windowMs = windowMs;
	}

	public synchronized void record() {
		long now = System.nanoTime() / 1_000_000L;
		times.addLast(now);
		trim(now);
	}

	/** Events in the last window (a second by default). */
	public synchronized int count() {
		trim(System.nanoTime() / 1_000_000L);
		return times.size();
	}

	public synchronized void clear() {
		times.clear();
	}

	private void trim(long now) {
		while (!times.isEmpty() && now - times.peekFirst() >= windowMs) times.pollFirst();
	}
}
