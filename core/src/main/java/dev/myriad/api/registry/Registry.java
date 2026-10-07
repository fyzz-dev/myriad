package dev.myriad.api.registry;

import dev.myriad.api.util.MyriadId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * An ordered, namespaced registry. Every entry remembers which addon registered it so a failing addon's
 * contributions can be rolled back and the UI can show where things came from.
 */
public class Registry<T extends Identified> implements Iterable<T> {
	private final String name;
	private final Map<MyriadId, T> entries = new LinkedHashMap<>();
	private final Map<T, String> owners = new IdentityHashMap<>();
	private final List<Consumer<T>> addListeners = new CopyOnWriteArrayList<>();
	private final List<Consumer<T>> removeListeners = new CopyOnWriteArrayList<>();
	private boolean frozen;

	public Registry(String name) {
		this.name = name;
	}

	public String name() {
		return name;
	}

	/**
	 * Registers an entry. {@code owner} is the mod id of the registering addon. Most addon code should use the
	 * registrars on {@code AddonContext}, which fill this in.
	 */
	public synchronized T register(T entry, String owner) {
		if (frozen) throw new IllegalStateException("Registry '" + name + "' is frozen");
		MyriadId id = entry.id();
		if (entries.containsKey(id)) throw new IllegalArgumentException("Duplicate " + name + " id: " + id);
		entries.put(id, entry);
		owners.put(entry, owner);
		for (Consumer<T> l : addListeners) l.accept(entry);
		return entry;
	}

	public synchronized boolean unregister(MyriadId id) {
		T removed = entries.remove(id);
		if (removed == null) return false;
		owners.remove(removed);
		for (Consumer<T> l : removeListeners) l.accept(removed);
		return true;
	}

	/** Removes everything {@code owner} registered. Returns the removed entries. */
	public synchronized List<T> unregisterAll(String owner) {
		List<T> removed = new ArrayList<>();
		for (T t : List.copyOf(entries.values())) {
			if (owner.equals(owners.get(t))) {
				unregister(t.id());
				removed.add(t);
			}
		}
		return removed;
	}

	public synchronized Optional<T> get(MyriadId id) {
		return Optional.ofNullable(entries.get(id));
	}

	public synchronized <C extends T> Optional<C> get(Class<C> type) {
		for (T t : entries.values()) if (t.getClass() == type) return Optional.of(type.cast(t));
		return Optional.empty();
	}

	public synchronized boolean contains(MyriadId id) {
		return entries.containsKey(id);
	}

	/** Mod id of the addon that registered {@code entry}, or {@code null}. */
	public synchronized String ownerOf(T entry) {
		return owners.get(entry);
	}

	public synchronized List<T> ownedBy(String owner) {
		List<T> list = new ArrayList<>();
		for (T t : entries.values()) if (owner.equals(owners.get(t))) list.add(t);
		return list;
	}

	public synchronized Collection<T> values() {
		return Collections.unmodifiableList(new ArrayList<>(entries.values()));
	}

	public synchronized int size() {
		return entries.size();
	}

	public void onAdd(Consumer<T> listener) {
		addListeners.add(listener);
	}

	public void onRemove(Consumer<T> listener) {
		removeListeners.add(listener);
	}

	/**
	 * @deprecated Myriad no longer freezes its registries after startup: an addon may register later, when something
	 * it bridges becomes ready. Calling this still makes {@link #register} throw, but nothing in core does. To be
	 * removed in a later minor release.
	 */
	@Deprecated(since = "0.2.0", forRemoval = true)
	public synchronized void freeze() {
		frozen = true;
	}

	/** @deprecated see {@link #freeze()}. */
	@Deprecated(since = "0.2.0", forRemoval = true)
	public synchronized boolean isFrozen() {
		return frozen;
	}

	@Override
	public java.util.Iterator<T> iterator() {
		return values().iterator();
	}
}
