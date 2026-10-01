package dev.myriad.api.event;

/** Handle returned by {@link EventBus#listen}; call {@link #unsubscribe()} to stop listening. */
@FunctionalInterface
public interface Subscription {
	void unsubscribe();
}
