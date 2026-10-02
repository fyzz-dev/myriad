package dev.myriad.api.service;

import java.util.function.BooleanSupplier;

/**
 * Work spread over game ticks: "do this in 5 ticks", "every 10 ticks", or a sequence of steps that wait for
 * conditions (open a chest, wait until it has loaded, move items, close it). Everything runs on the render thread at
 * the end of a tick. Tasks belong to an owner; a module's tasks are cancelled when it's disabled.
 *
 * <pre>{@code
 * Myriad.tasks().sequence(this)
 *     .run(() -> Myriad.containers().open(pos, 20))
 *     .waitUntil(() -> Myriad.containers().current().map(Containers.View::isLoaded).orElse(false), 40)
 *     .run(this::moveItems)
 *     .wait(2)
 *     .run(Myriad.containers()::close)
 *     .onTimeout(() -> warn("Chest didn't open"))
 *     .start();
 * }</pre>
 */
public interface Tasks {
	/** Runs {@code action} after {@code ticks} ticks (0 = the next time tasks run, at the end of this tick or the next). */
	Handle later(Object owner, int ticks, Runnable action);

	/** Runs {@code action} every {@code intervalTicks} ticks until cancelled. */
	Handle every(Object owner, int intervalTicks, Runnable action);

	/** Starts building a sequence of steps. Nothing runs until {@link Sequence#start()}. */
	Sequence sequence(Object owner);

	/** Cancels everything {@code owner} scheduled. */
	void cancel(Object owner);

	/** Whether {@code owner} has anything scheduled. */
	boolean isBusy(Object owner);

	interface Handle {
		void cancel();

		boolean isDone();
	}

	/** Steps run in order; each waits for the one before. */
	interface Sequence {
		/** Runs {@code action}, then moves straight on to the next step in the same tick. */
		Sequence run(Runnable action);

		/** Waits {@code ticks} ticks. */
		Sequence wait(int ticks);

		/**
		 * Waits until {@code condition} is true, checking once per tick. After {@code timeoutTicks} the sequence
		 * stops and {@link #onTimeout} runs.
		 */
		Sequence waitUntil(BooleanSupplier condition, int timeoutTicks);

		/** Runs if a {@link #waitUntil} times out. */
		Sequence onTimeout(Runnable action);

		/** Runs after the last step. */
		Sequence onFinish(Runnable action);

		Handle start();
	}
}
