package dev.myriad.api.ui;

import dev.myriad.api.registry.Identified;
import dev.myriad.api.util.MyriadId;

import java.util.function.Consumer;

/** A named theme preset; applying it writes values into the live {@link ThemeSettings}. */
public record Theme(MyriadId id, String name, Consumer<ThemeSettings> apply) implements Identified {
}
