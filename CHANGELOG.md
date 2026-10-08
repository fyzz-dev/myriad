# Changelog

Myriad follows [semantic versioning](https://semver.org) for the public API (`dev.myriad.api`): while the major
version is 0, a **minor** release may change the API, and anything removed is deprecated for at least one minor
release first with its replacement in the javadoc; a **patch** release never changes it. `dev.myriad.impl` is internal
and may change in any release. Addons declare the oldest core they support in `fabric.mod.json`
(`"depends": {"myriad": ">=0.1.0"}`) and can check `Myriad.isAtLeast("0.2.0")` for newer features.

## 0.2.4

No API changes.

- Rotations: the yaw sent while a module holds one (an aura, Elytra Fly's lane) turns from the last yaw sent, never
  more than half a turn. It used to be kept near the camera's, so looking round past the opposite way jumped it a
  whole turn in one packet, which Grim flags (AimModulo360); handing back to the camera afterwards could too.
- Dev console: `mouse <button> press|release|click` and `scroll <notches>`, sent through vanilla's input handler like
  real input.

## 0.2.3

Additions to the highlight API only; addons built for 0.2.x keep working.

- Addons can bring their own highlight fills: `HighlightStyle.Fill.custom(shader)` takes a fragment shader from the
  addon's assets that imports `myriad:highlight_fill.glsl` and defines `customFill`, which returns the colour of a pixel
  inside a highlight (it gets the pixel's position in the highlight, the highlight's colour with any gradient, the
  style's opacity and dot settings as a scale, the GUI scale and time). Outlines, glow and shared edges are drawn
  around it as for the built-in fills; each custom fill in use adds one pass over its highlights' insides. The
  example addon's Logo ESP fills entities with a drifting grid of Myriad logos this way.
- Highlights come in two layers, each with its own silhouette mask: shapes first, entities drawn over them. Before,
  one mask held only the nearest thing per pixel, so a wall of highlighted chests hid the highlighted entities behind
  it.
- Shapes that look alike share one highlight per frame, however many there are, and retained meshes can be
  highlighted without re-sending them: `HighlightEvent.Shapes.mesh(mesh, style, palette...)` draws a `WorldMesh`'s
  faces into the mask with the highlight id applied at draw time, each vertex picking its colour from the palette
  (`Shapes.paletteColor(i)`). `ChunkCache.highlight(event, style, palette...)` does that for every chunk mesh, and
  `ChunkCache.setMeshesVisible(false)` keeps them from being drawn as boxes. Measured: ~590,000 highlighted stone blocks
  at about 1,000 FPS, the same as with nothing highlighted.
- `HighlightEvent.Shapes.block(pos, style, color, grow)` grows the shape a little, so a highlight of the block under the
  crosshair sits in front of other highlights of the same block instead of flickering against them.

## 0.2.2

No API changes.

- Highlights only connect when they look alike (same style and colour): touching ones still share one outline (a vein
  of ores, a double chest, a portal), but where highlights that look different touch, the one added later outlines
  itself across the boundary. Before, any two touching highlights merged, so a block highlight next to a Blocks or
  Storage highlight lost the edge they shared, and two touching ESP targets in different colours had no line between
  them. A highlight added by a low-priority listener draws over the others. Up to 65,536 highlights a frame.

## 0.2.1

Desktop placement for many addons, and sideways scrolling. The API only gains additions: two default methods and two
constructors, so addons built for 0.2.0 keep working.

- New addons no longer crowd one workspace. A workspace holds as many windows as fit side by side at a readable
  width; an addon's windows go beside the ones of the same categories while there's room, and otherwise onto a
  workspace of their own (continuing onto the next empty one), with one notification per addon ("Boze added 7
  windows to workspaces 2 and 3"). The first-run desktop is laid out the same way. Only windows the desktop hasn't
  seen are placed, so your arrangement is never moved.
- Columns never get narrower than a readable width: when they don't all fit, the row scrolls sideways (Shift + mouse
  wheel, or the wheel over a gap), and focusing or opening a window scrolls it into view.
- `LayoutState` has `scroll(amount)` and `reveal(window)` (default methods: layouts that don't scroll ignore them).
- `HighlightSettings` and `BoxStyle` take whether Through Walls starts on, for highlighting things you can already see
  (like the block you're looking at).

## 0.2.0

Adds highlights to the API (a minor release: addons built for 0.1.x keep working).

- Highlights (new API): `HighlightEvent.Entity` and `HighlightEvent.Shapes` outline the exact silhouette of entities
  (armour, held items and cut-outs included) and of boxes or block shapes, with an optional glow, a solid or dotted
  fill, a gradient, and through walls or only where visible; `HighlightStyle` describes the look and
  `HighlightSettings` adds the standard settings for one. Silhouettes come from vanilla's entity outline target, so
  they cost no extra geometry, and the shader runs only over the screen area the highlights cover, in two passes whose
  work grows with the width in pixels. Measured with 20 creepers on screen (1261×1030, uncapped): ~1130 FPS without,
  ~1230 with a 2 px outline (no difference within noise), ~1025 with a 24 px glow and dot fill (about +0.09 ms a frame).
  Nothing runs while nothing listens. While highlights are in use, vanilla's glowing effect is drawn by the same shader,
  and vanilla's entity outlines now depth-test against each other (the nearest wins where two overlap).
- Highlight outlines and glows get thinner with distance (`HighlightStyle.distanceScaling()`, on by default): full
  width within 10 blocks, down to half width at 40 blocks and beyond, never below a pixel.
- Through-walls highlights work with occlusion culling: entities that Sodium (hidden chunk sections) or EntityCulling
  (traced as hidden) would skip are still drawn while a through-walls highlight wants them, and only those. Tested with
  Sodium 0.9.2, Iris 1.11.4 and EntityCulling 1.11.2 together, with and without the Complementary Unbound shader
  pack (through walls and depth-tested both draw correctly under it; Iris logs once that it has no program for the
  highlight passes, which is what lets them run unchanged).
- `EntityRenderEvent.Nametag` now fires for players and mobs: it hooked `shouldShowName`, which living entities
  override without calling up, so cancelling it only ever hid tags on non-living entities. Cancelling also hides the
  score line under the name.
- `BoxStyle` takes a visibility condition, for modules that only show box options in some modes.
- The Master layout is gone; Dwindle and Columns cover it. Workspaces saved with Master open as Dwindle.
- Updates: an addon whose `contact.sources` names Myriad's own repository is now looked for in a repository named
  after its mod id under the same owner, so Essentials 0.1.0 (which shipped pointing at Myriad's repository) finds
  its releases at `fyzz-dev/myriad-essentials`. Before, it was silently skipped.
- Check now only says "Up to date" for what it actually checked, and names any addon it couldn't check and why;
  an addon with no GitHub repository says so on its card.

## 0.1.2

No API changes.

- Updates: Myriad checks GitHub for new releases of itself and every addon whose `contact.sources` is its GitHub
  repository (or that names one with `"custom": {"myriad": {"updates": "owner/repo"}}`). The Addons window shows the
  new version on the card, with **Update** and **Update all**, the bar shows a count, and a toast says so once per
  launch; `.update` does the same from chat. The download is checked (size and SHA-256 against GitHub, mod id,
  version, and its Minecraft and Myriad dependencies) and swapped into `mods/` when the game exits, keeping the jar
  it replaced in `myriad/updates/old/`; the next launch confirms it went in. Turn the launch check off at the top
  of the Addons window. On Windows, where a running jar can't be moved, a hidden PowerShell does the swap once the
  game has exited; that path has not been tried on a Windows install yet.

## 0.1.1

Fixes and the repository split; no API changes.

- Dwindle is the default layout for the first workspace too (it already was for the others); the first-run desktop
  splits the largest window each time, so the stock windows come up as a grid rather than ever thinner slivers.
- Essentials has moved to its own repository, [myriad-essentials](https://github.com/fyzz-dev/myriad-essentials),
  with its own versions and releases; the Grim test server and suite went with it. This repository is core and the
  example addon, and `example-addon/` is a code sample now: it still builds and runs, but isn't released.
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
