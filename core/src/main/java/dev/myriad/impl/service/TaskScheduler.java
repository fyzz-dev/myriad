package dev.myriad.impl.service;

import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.service.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Runs scheduled tasks at the end of every client tick. {@link #tick()} is separate from the event for tests. */
public final class TaskScheduler implements Tasks {
	private static final Logger LOG = LoggerFactory.getLogger("Myriad/Tasks");

	private final List<Task> tasks = new ArrayList<>();
	private final List<Task> added = new ArrayList<>();
	private boolean ticking;

	@Subscribe
	private void onTick(TickEvent.Post e) {
		tick();
	}

	public void tick() {
		ticking = true;
		try {
			for (Task t : tasks) {
				if (t.done) continue;
				try {
					t.step();
				} catch (Throwable ex) {
					LOG.error("Task owned by {} failed", t.owner, ex);
					t.done = true;
				}
			}
		} finally {
			ticking = false;
		}
		tasks.removeIf(t -> t.done);
		tasks.addAll(added);
		added.clear();
	}

	private Task add(Task t) {
		(ticking ? added : tasks).add(t);
		return t;
	}

	@Override
	public Handle later(Object owner, int ticks, Runnable action) {
		return sequence(owner).wait(ticks).run(action).start();
	}

	@Override
	public Handle every(Object owner, int intervalTicks, Runnable action) {
		return add(new Repeating(owner, Math.max(1, intervalTicks), action));
	}

	@Override
	public Sequence sequence(Object owner) {
		return new SequenceImpl(owner);
	}

	@Override
	public void cancel(Object owner) {
		for (Task t : tasks) if (t.owner == owner) t.done = true;
		for (Task t : added) if (t.owner == owner) t.done = true;
	}

	@Override
	public boolean isBusy(Object owner) {
		for (Task t : tasks) if (t.owner == owner && !t.done) return true;
		for (Task t : added) if (t.owner == owner && !t.done) return true;
		return false;
	}

	private abstract static class Task implements Handle {
		final Object owner;
		boolean done;

		Task(Object owner) {
			this.owner = owner;
		}

		abstract void step();

		@Override
		public void cancel() {
			done = true;
		}

		@Override
		public boolean isDone() {
			return done;
		}
	}

	private static final class Repeating extends Task {
		private final int interval;
		private final Runnable action;
		private int wait;

		Repeating(Object owner, int interval, Runnable action) {
			super(owner);
			this.interval = interval;
			this.action = action;
			this.wait = interval;
		}

		@Override
		void step() {
			if (--wait > 0) return;
			wait = interval;
			action.run();
		}
	}

	private sealed interface Step permits Run, Wait, WaitUntil {
	}

	private record Run(Runnable action) implements Step {
	}

	private record Wait(int ticks) implements Step {
	}

	private record WaitUntil(BooleanSupplier condition, int timeout) implements Step {
	}

	private final class SequenceImpl extends Task implements Sequence {
		private final List<Step> steps = new ArrayList<>();
		private Runnable onTimeout, onFinish;
		private int index;
		/** Ticks left on the current wait; -1 when the current step hasn't started. */
		private int remaining = -1;

		SequenceImpl(Object owner) {
			super(owner);
		}

		@Override
		public Sequence run(Runnable action) {
			steps.add(new Run(action));
			return this;
		}

		@Override
		public Sequence wait(int ticks) {
			steps.add(new Wait(Math.max(0, ticks)));
			return this;
		}

		@Override
		public Sequence waitUntil(BooleanSupplier condition, int timeoutTicks) {
			steps.add(new WaitUntil(condition, Math.max(1, timeoutTicks)));
			return this;
		}

		@Override
		public Sequence onTimeout(Runnable action) {
			onTimeout = action;
			return this;
		}

		@Override
		public Sequence onFinish(Runnable action) {
			onFinish = action;
			return this;
		}

		@Override
		public Handle start() {
			return add(this);
		}

		/** Runs steps until one has to wait for a later tick. */
		@Override
		void step() {
			while (!done && index < steps.size()) {
				Step step = steps.get(index);
				switch (step) {
					case Run r -> {
						r.action.run();
						next();
					}
					case Wait w -> {
						if (remaining == -1) remaining = w.ticks;
						else remaining--;
						if (remaining > 0) return;
						next();
					}
					case WaitUntil u -> {
						if (u.condition.getAsBoolean()) {
							next();
							continue;
						}
						if (remaining == -1) remaining = u.timeout;
						if (--remaining <= 0) {
							done = true;
							if (onTimeout != null) onTimeout.run();
						}
						return;
					}
				}
			}
			if (!done) {
				done = true;
				if (onFinish != null) onFinish.run();
			}
		}

		private void next() {
			index++;
			remaining = -1;
		}
	}
}
