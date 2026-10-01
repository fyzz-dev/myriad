package dev.myriad.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler. The method must return {@code void} and take exactly one parameter,
 * the event type. Handlers for a supertype also receive subtypes.
 *
 * <pre>{@code
 * @Subscribe
 * private void onTick(TickEvent.Pre event) { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
	/** Higher runs first. See {@link Priority}. */
	int priority() default Priority.NORMAL;

	/** If false (default) the handler is skipped once an earlier handler cancelled the event. */
	boolean receiveCancelled() default false;
}
