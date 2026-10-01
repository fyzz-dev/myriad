package dev.myriad.api.registry;

import dev.myriad.api.util.MyriadId;

/** Anything that lives in a {@link Registry}. */
public interface Identified {
	MyriadId id();
}
