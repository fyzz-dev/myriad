# Changelog

Myriad follows [semantic versioning](https://semver.org) for the public API (`dev.myriad.api`): while the major
version is 0, a **minor** release may change the API, and anything removed is deprecated for at least one minor
release first with its replacement in the javadoc; a **patch** release never changes it. `dev.myriad.impl` is internal
and may change in any release. Addons declare the oldest core they support in `fabric.mod.json`
(`"depends": {"myriad": ">=0.1.0"}`) and can check `Myriad.isAtLeast("0.2.0")` for newer features.

## Unreleased

- Desktop shortcuts (`mod+1`, `mod+K`, `mod+W`, ...) work while the console window is focused; it only keeps the keys
  it types with.

- Windows of an addon that registers its modules after startup (the Boze bridge) come back where they were: a saved
  window whose modules aren't there yet at load is kept aside and revived, on its workspace and in its place in the
  tiling, once they arrive.

## 0.1.0

First release: the window manager, modules and settings, the event bus, the services (rotations, inventory, placing,
breaking, building, containers, tasks, packet limits, tick speed), themes, HUD, commands, profiles, Essentials and
the example addon.

Everything below is in the README's *Writing an addon* section.

### Boze bridge

- [myriad-boze](https://github.com/fyzz-dev/myriad-boze), a separate addon: every module of the Boze client in
  Myriad's menu, in the shared categories, with settings, keybinds and on/off state in sync both ways. Boze keeps its
  config and handles the keys. The API changes below are for it.

### API

- `Module.isMirror()`: a module that stands in for another mod's feature. Myriad neither saves it with the profile nor
  toggles it on its keybind. `Module.setEnabledSilently` is public API now (it was marked internal).
- Registries are no longer frozen after startup, so an addon can register once something it waits for is ready; the
  menu places windows for modules registered late when it next opens. `Registry.freeze()` and `isFrozen()` are
  deprecated and unused.

### Compatibility and the API contract

- Service options (`Placement.Options`, `Breaking.Options`, `Rotations.Options`, `Building.Options`) are built from
  presets with `with…` methods instead of positional constructors, so new options can't break addons.
- Interfaces only core implements are `@ApiStatus.NonExtendable`; event constructors and lifecycle hooks are
  `@ApiStatus.Internal`. A build test fails if any public API signature exposes `dev.myriad.impl`.
- `Myriad.version()` and `Myriad.isAtLeast(...)`.
- Releases are published to a public maven, `https://fyzz-dev.github.io/myriad`, so addons build without
  `publishToMavenLocal`.

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
  hooks, and has a handler Profiler (Profiler panel, `.profiler`) that shows milliseconds per module per second.
- Packet counters are lock-free; attack charge, threats and friend lookups are cached; collision shapes cost one
  volatile read unless a module listens.
- Measured on the Grim test server with twelve mobs nearby: with ESP, Tracers, Nametags, Storage, Blocks, Kill Aura,
  Offhand, Hole ESP, Trajectories and Block Search all on, the Profiler shows about 8 ms per second in handlers in
  total (under half a millisecond per tick), Offhand's threat check the largest at 3.7 ms/s. A JFR recording of the
  self-test puts Myriad frames in 7% of samples (9% before this release), nearly all of it HUD text rendering.

### Config

- Settings a module doesn't know are kept on save, a settings version is never lowered, files record their format,
  each session keeps a `.bak`, and broken files are restored from it. `AddonStorage` reads and writes versioned data.

### Dev tooling

- `selftest` and `respawn` script steps, the Essentials Grim suite (`tools/grim-test/suite`), API-only javadoc.

### Essentials

- Simpler settings (Offhand, Auto Armor, Auto Eat), no Grim flag when turning No Durability off mid-flight, modules
  rebuilt on the events above.
