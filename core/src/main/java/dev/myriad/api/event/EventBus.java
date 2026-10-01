package dev.myriad.api.event;

import java.util.function.Consumer;

public interface EventBus {
	/** Subscribes every {@link Subscribe} method on {@code listener} (including inherited ones). */
	void subscribe(Object listener);

	/** Subscribes static {@link Subscribe} methods of {@code type}. */
	void subscribe(Class<?> type);

	void unsubscribe(Object listener);

	void unsubscribe(Class<?> type);

	boolean isSubscribed(Object listener);

	default <E> Subscription listen(Class<E> event, Consumer<? super E> handler) {
		return listen(event, Priority.NORMAL, handler);
	}

	<E> Subscription listen(Class<E> event, int priority, Consumer<? super E> handler);

	/** Dispatches {@code event} to every listener of its class or any supertype. Returns the event. */
	<E> E post(E event);

	/** True if anything listens for {@code event} or a supertype (to skip allocating unwatched events). */
	boolean hasListeners(Class<?> event);
}
