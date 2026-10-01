package dev.myriad.api.addon;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import java.util.List;
import java.util.Optional;

/** A discovered addon. Metadata comes straight from its Fabric {@link ModContainer}. */
public final class Addon {
	private final ModContainer container;
	private final MyriadAddon entrypoint;
	private AddonState state = AddonState.LOADING;
	private Throwable failure;

	public Addon(ModContainer container, MyriadAddon entrypoint) {
		this.container = container;
		this.entrypoint = entrypoint;
	}

	public String id() {
		return metadata().getId();
	}

	public String name() {
		return metadata().getName();
	}

	public String version() {
		return metadata().getVersion().getFriendlyString();
	}

	public String description() {
		return metadata().getDescription();
	}

	public List<String> authors() {
		return metadata().getAuthors().stream().map(Person::getName).toList();
	}

	public Optional<String> iconPath() {
		return metadata().getIconPath(64);
	}

	public ModMetadata metadata() {
		return container.getMetadata();
	}

	public ModContainer container() {
		return container;
	}

	public MyriadAddon entrypoint() {
		return entrypoint;
	}

	public AddonState state() {
		return state;
	}

	public Throwable failure() {
		return failure;
	}

	public void markLoaded() {
		if (state == AddonState.LOADING) state = AddonState.LOADED;
	}

	public void markFailed(Throwable t) {
		state = AddonState.FAILED;
		failure = t;
	}

	public boolean isCore() {
		return id().equals("myriad");
	}

	@Override
	public String toString() {
		return name() + " " + version();
	}
}
