package dev.myriad.api.event.events;

import dev.myriad.api.module.Module;

public record ModuleToggleEvent(Module module, boolean enabled) {
}
