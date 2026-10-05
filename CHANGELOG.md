# Changelog

Myriad follows [semantic versioning](https://semver.org) for the public API (`dev.myriad.api`): while the major
version is 0, a **minor** release may change the API, and anything removed is deprecated for at least one minor
release first with its replacement in the javadoc; a **patch** release never changes it. `dev.myriad.impl` is internal
and may change in any release. Addons declare the oldest core they support in `fabric.mod.json`
(`"depends": {"myriad": ">=0.2.0"}`) and can check `Myriad.isAtLeast("0.3.0")` for newer features.

## 0.2.0

The release that makes core something to build on. Everything here is in the README's *Writing an addon* section.

### Compatibility and the API contract

- Service options (`Placement.Options`, `Breaking.Options`, `Rotations.Options`, `Building.Options`) are built from
  presets with `with…` methods instead of positional constructors, so new options can't break addons.
- Interfaces only core implements are `@ApiStatus.NonExtendable`; event constructors and lifecycle hooks are
  `@ApiStatus.Internal`. A build test fails if any public API signature exposes `dev.myriad.impl`.
- `Myriad.version()` and `Myriad.isAtLeast(...)`.

### Anti-cheat profile

- `Myriad.antiCheat()`: Auto (detects Grim's pings, or servers in the known list), Grim or Vanilla, set in the
  Profiles panel or with `.anticheat`. The services' `forServer()` presets and inventory click timing follow it.
  `.anticheat known add|remove` edits the known servers.

### Events

- `CollisionShapeEvent`, `PlaySoundEvent`, `TeleportEvent`, `HealthEvent`, `DisconnectEvent`,
  `EntityEvent.TotemPopped/Died`.
- `BlockRenderEvent`, `EntityRenderEvent.Visible/Nametag/Model` and `ContainerScreenEvent.SlotDrawn/Tooltip/Click`,
  so X-ray, chams, entity hiding, custom nametags, slot icons, content previews and inventory tricks need no mixins.
- `@Subscribe(inGame = true)` handlers only run with a player in a world; `@Subscribe(packets = ...)` handlers are
  dispatched only for those packet classes.

### Services and helpers

- `combat.Threats`, `combat.Crystals`, `combat.Trajectory` (`simulate`, `aimAt`), `world.Holes` (`around`, `scan`,
  `surround`, `city`), `render.BoxStyle`, `render.EntityGroups` (from Essentials), `ItemInfo` armour and elytra
  helpers, `Movement.baseSpeed`, `util.Ticks`, `util.TickCached`.
- `ServerStats.reconnect()` and `lastAddress()`, `Myriad.runCommand`, `AddonContext.settings` (addon-wide settings,
  saved per profile, shown in the Addons panel), `Module.conflictsWith`.
- Setting types: `enumSet`, `intList`, `sound`. `.toggle <module> on|off`.
- `ChunkCache.async()` computes chunks on the worker pool.

### Performance

- The event bus dispatches packet events through a per-packet-class table, keeps `ListenerFlag`s for the hottest
  hooks, and has a handler Profiler (Profiler panel, `.profile`) that shows milliseconds per module per second.
- Packet counters are lock-free; attack charge, threats and friend lookups are cached; collision shapes cost one
  volatile read unless a module listens.

### Config

- Settings a module doesn't know are kept on save, a settings version is never lowered, files record their format,
  each session keeps a `.bak`, and broken files are restored from it. `AddonStorage` reads and writes versioned data.

### Dev tooling

- `selftest` and `respawn` script steps, the Essentials Grim suite (`tools/grim-test/suite`), API-only javadoc.

### Essentials

- Simpler settings (Offhand, Auto Armor, Auto Eat), no Grim flag when turning No Durability off mid-flight, modules
  rebuilt on the events above.

## 0.1.0

First release: the window manager, modules and settings, the event bus, the services (rotations, inventory, placing,
breaking, building, containers, tasks, packet limits, tick speed), themes, HUD, commands, profiles, Essentials and
the example addon.
