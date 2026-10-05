package dev.myriad.impl.event;

import dev.myriad.api.event.Cancellable;
import dev.myriad.api.event.EventBus;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Event bus in the spirit of Orbit (which Meteor uses), with the rough edges filed off:
 * <ul>
 *     <li>handlers for a supertype receive subtypes,</li>
 *     <li>a cancelled event keeps dispatching (handlers opt in with {@code receiveCancelled}),</li>
 *     <li>no per-package lambda factory registration: {@code privateLookupIn} works for every mod in Knot,</li>
 *     <li>a throwing handler is logged with its owning addon and disabled if it keeps failing.</li>
 * </ul>
 */
public final class MyriadEventBus implements EventBus {
	private static final Logger LOG = LoggerFactory.getLogger("Myriad/EventBus");
	private static final int MAX_FAILURES = 50;
	private static final int LOGGED_FAILURES = 3;
	private static final Listener[] EMPTY = new Listener[0];

	private final Object lock = new Object();
	/** Listeners keyed by the exact event type they declared. */
	private final Map<Class<?>, List<Listener>> byType = new HashMap<>();
	/** Listener objects (instances or classes for static handlers) -> their listeners. */
	private final Map<Object, List<Listener>> byOwner = new IdentityHashMap<>();
	/** Flattened, sorted listeners per concrete event class. Replaced wholesale on change. */
	private volatile Map<Class<?>, Listener[]> dispatch = new ConcurrentHashMap<>();
	private final Map<Method, MethodHandle> factories = new ConcurrentHashMap<>();
	private final Map<Class<?>, List<Handler>> instanceHandlers = new ConcurrentHashMap<>();
	private final Map<Class<?>, List<Handler>> staticHandlers = new ConcurrentHashMap<>();
	private long order;
	private volatile Function<Class<?>, String> ownerResolver = Class::getName;

	public void setOwnerResolver(Function<Class<?>, String> resolver) {
		this.ownerResolver = resolver;
	}

	@Override
	public void subscribe(Object listener) {
		subscribeOwner(listener, listener.getClass(), false);
	}

	@Override
	public void subscribe(Class<?> type) {
		subscribeOwner(type, type, true);
	}

	@Override
	public void unsubscribe(Object listener) {
		synchronized (lock) {
			List<Listener> ls = byOwner.remove(listener);
			if (ls == null) return;
			for (Listener l : ls) {
				// An event being dispatched right now holds its own copy of the listeners: skip this one there too.
				l.disabled = true;
				List<Listener> list = byType.get(l.type);
				if (list != null) list.remove(l);
			}
			invalidate();
		}
	}

	@Override
	public void unsubscribe(Class<?> type) {
		unsubscribe((Object) type);
	}

	@Override
	public boolean isSubscribed(Object listener) {
		synchronized (lock) {
			return byOwner.containsKey(listener);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <E> Subscription listen(Class<E> event, int priority, Consumer<? super E> handler) {
		Listener l = new Listener(event, priority, false, (Consumer<Object>) handler, ownerResolver.apply(handler.getClass()));
		synchronized (lock) {
			l.order = order++;
			byType.computeIfAbsent(event, k -> new ArrayList<>()).add(l);
			invalidate();
		}
		return () -> {
			synchronized (lock) {
				l.disabled = true;
				List<Listener> list = byType.get(event);
				if (list != null && list.remove(l)) invalidate();
			}
		};
	}

	@Override
	public <E> E post(E event) {
		Listener[] listeners = listenersFor(event.getClass());
		if (listeners.length == 0) return event;
		Cancellable cancellable = event instanceof Cancellable c ? c : null;
		for (Listener l : listeners) {
			if (l.disabled) continue;
			if (cancellable != null && cancellable.isCancelled() && !l.receiveCancelled) continue;
			try {
				l.invoker.accept(event);
			} catch (Throwable t) {
				onFailure(l, event, t);
			}
		}
		return event;
	}

	@Override
	public boolean hasListeners(Class<?> event) {
		return listenersFor(event).length > 0;
	}

	private Listener[] listenersFor(Class<?> eventClass) {
		Map<Class<?>, Listener[]> snapshot = dispatch;
		Listener[] cached = snapshot.get(eventClass);
		if (cached != null) return cached;
		synchronized (lock) {
			Listener[] computed = compute(eventClass);
			if (snapshot == dispatch) snapshot.put(eventClass, computed);
			return computed;
		}
	}

	private Listener[] compute(Class<?> eventClass) {
		List<Listener> out = new ArrayList<>();
		for (Map.Entry<Class<?>, List<Listener>> e : byType.entrySet()) {
			if (e.getKey().isAssignableFrom(eventClass)) out.addAll(e.getValue());
		}
		if (out.isEmpty()) return EMPTY;
		out.sort(Comparator.<Listener>comparingInt(l -> -l.priority).thenComparingLong(l -> l.order));
		return out.toArray(EMPTY);
	}

	private void invalidate() {
		dispatch = new ConcurrentHashMap<>();
	}

	private void subscribeOwner(Object owner, Class<?> klass, boolean statics) {
		synchronized (lock) {
			if (byOwner.containsKey(owner)) return;
		}
		List<Listener> found = new ArrayList<>();
		String ownerName = ownerResolver.apply(klass);
		for (Handler h : handlers(klass, statics)) {
			found.add(new Listener(h.event, h.priority, h.receiveCancelled, createInvoker(h.method, statics ? null : owner), ownerName));
		}
		synchronized (lock) {
			if (byOwner.containsKey(owner)) return;
			for (Listener l : found) {
				l.order = order++;
				byType.computeIfAbsent(l.type, k -> new ArrayList<>()).add(l);
			}
			byOwner.put(owner, found);
			if (!found.isEmpty()) invalidate();
		}
	}

	/** The @Subscribe methods of a class and its superclasses, found once per class (modules re-subscribe on every enable). */
	private List<Handler> handlers(Class<?> klass, boolean statics) {
		Map<Class<?>, List<Handler>> cache = statics ? staticHandlers : instanceHandlers;
		return cache.computeIfAbsent(klass, k -> {
			List<Handler> list = new ArrayList<>();
			for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
				for (Method m : c.getDeclaredMethods()) {
					Subscribe sub = m.getAnnotation(Subscribe.class);
					if (sub == null) continue;
					if (Modifier.isStatic(m.getModifiers()) != statics) continue;
					if (m.getParameterCount() != 1 || m.getReturnType() != void.class || m.getParameterTypes()[0].isPrimitive()) {
						throw new IllegalArgumentException("Invalid @Subscribe method " + c.getName() + "#" + m.getName()
							+ ": must be void and take exactly one event parameter");
					}
					list.add(new Handler(m, m.getParameterTypes()[0], sub.priority(), sub.receiveCancelled()));
				}
			}
			return List.copyOf(list);
		});
	}

	private record Handler(Method method, Class<?> event, int priority, boolean receiveCancelled) {
	}

	@SuppressWarnings("unchecked")
	private Consumer<Object> createInvoker(Method method, Object instance) {
		try {
			MethodHandle factory = factories.computeIfAbsent(method, MyriadEventBus::lambdaFactory);
			return instance == null ? (Consumer<Object>) factory.invoke() : (Consumer<Object>) factory.invoke(instance);
		} catch (Throwable t) {
			// Fall back to a plain method handle; slower but works even when the lambda spin is refused.
			try {
				method.setAccessible(true);
				MethodHandle mh = MethodHandles.lookup().unreflect(method);
				MethodHandle bound = instance == null ? mh : mh.bindTo(instance);
				return event -> {
					try {
						bound.invoke(event);
					} catch (RuntimeException | Error e) {
						throw e;
					} catch (Throwable e) {
						throw new RuntimeException(e);
					}
				};
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Cannot access handler " + method, e);
			}
		}
	}

	private static MethodHandle lambdaFactory(Method method) {
		try {
			Class<?> owner = method.getDeclaringClass();
			MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
			MethodHandle target = lookup.unreflect(method);
			boolean isStatic = Modifier.isStatic(method.getModifiers());
			MethodType factoryType = isStatic ? MethodType.methodType(Consumer.class) : MethodType.methodType(Consumer.class, owner);
			CallSite site = LambdaMetafactory.metafactory(lookup, "accept", factoryType,
				MethodType.methodType(void.class, Object.class), target,
				MethodType.methodType(void.class, method.getParameterTypes()[0]));
			return site.getTarget();
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	/** Told of every handler failure (dev tooling: the self-test attributes them to the module under test). */
	private volatile java.util.function.BiConsumer<String, Throwable> failureHook;

	public void setFailureHook(java.util.function.BiConsumer<String, Throwable> hook) {
		failureHook = hook;
	}

	private void onFailure(Listener l, Object event, Throwable t) {
		var hook = failureHook;
		if (hook != null) hook.accept(l.owner + " handling " + event.getClass().getSimpleName(), t);
		int n = ++l.failures;
		if (n <= LOGGED_FAILURES) {
			LOG.error("Handler from '{}' threw while handling {}", l.owner, event.getClass().getSimpleName(), t);
		}
		if (n >= MAX_FAILURES) {
			l.disabled = true;
			LOG.error("Disabled a handler from '{}' for {} after {} failures", l.owner, l.type.getSimpleName(), n);
		}
	}

	private static final class Listener {
		final Class<?> type;
		final int priority;
		final boolean receiveCancelled;
		final Consumer<Object> invoker;
		final String owner;
		long order;
		int failures;
		volatile boolean disabled;

		Listener(Class<?> type, int priority, boolean receiveCancelled, Consumer<Object> invoker, String owner) {
			this.type = type;
			this.priority = priority;
			this.receiveCancelled = receiveCancelled;
			this.invoker = invoker;
			this.owner = owner;
		}
	}
}
