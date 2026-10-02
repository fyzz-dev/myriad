# Myriad

An addon-first utility client for Minecraft **1.21.4** (Fabric, Yarn), with a UI modelled on the
[Hyprland](https://hyprland.org) tiling window manager.

The core of Myriad is the *system*, not the features. The core jar ships **no modules at all**. Every stock module
lives in `myriad-essentials`, which is built exactly like a third-party addon and uses only the public API. Remove
that jar and Myriad still boots, with an empty module list.

```
core/           mod id "myriad"             the platform: API, event bus, config, commands, renderer, menu
omarchy/        mod id "myriad-omarchy"     optional: a theme that follows your Omarchy system theme
essentials/     mod id "myriad-essentials"  the stock modules + HUD elements (an ordinary addon)
example-addon/  mod id "myriad-example"     the reference addon: a complete feature set, written to be read
```

## Building and running

Requires JDK 21+.

```bash
./gradlew build                          # core/, essentials/, example-addon/ → */build/libs/*.jar
./gradlew :core:test                     # event bus, settings, layout unit tests
./gradlew :core:runClient                # core only (no modules)
./gradlew :essentials:runClient          # core + essentials
./gradlew :example-addon:runClient       # everything
./gradlew :example-addon:runClient -PopenDesktop   # …and open the menu on the title screen
./gradlew publishToMavenLocal            # publish dev.myriad:myriad(-essentials) for addons outside this repo
```

To install, put `myriad-<v>.jar` in `mods/` (with Fabric API), then add `myriad-essentials-<v>.jar` and any other
addons next to it. On [Omarchy](https://omarchy.org), add `myriad-omarchy-<v>.jar` too.

## The menu

Press **Right Shift** (rebindable) in game or on the title screen to open the Myriad menu.

- **Workspaces 1–9** each tile their windows with a layout. **Columns** is the default: one window per module
  category, side by side, with each module's settings unfolding inline. **Dwindle** (each new window splits the
  focused one, Hyprland's default) and **Master** are also built in, and addons can register more layouts.
- Windows can be **tiled**, **floating** or **fullscreen**. Drag the gaps between tiles to resize them.
- The **HUD workspace** (the icon after 9 in the bar) holds the in-game HUD. Its windows are drawn in game without
  window decorations. Drag them to move them; they snap to edges and to the centre, and anchor to the nearest screen
  third. Right-click one for its settings. The **HUD** window there lists every element like a category lists
  modules: click one to add or remove it, unfold it for its options. Delete removes the hovered element, and the
  launcher can also add and remove them.
- The **launcher** (`Alt+Space`) fuzzy-searches modules (Enter toggles, Shift+Enter or right-click opens settings),
  panels, categories, themes, profiles and workspaces.
- **Themes**: a theme holds the whole look: gaps, borders (gradient, optional rotation), rounding, blur, shadows,
  inactive dimming and opacity, animation curves and speeds (Hyprland-style), the bar, fonts, and every colour. Pick
  one in the Theme panel and edit it there; edits save into the active theme as you make them.
  - **Presets**: Myriad, Tokyo Night and Gruvbox. Editing a preset saves your changes on top of it (it shows
    *Edited*), and **Reset** brings the original back.
  - **Your themes**: **New** or **Duplicate** makes a theme you can rename and give an author. Each one is a file in
    `.minecraft/myriad/themes/`. To share it, send the `.json`; the other person drops it in that folder and presses
    **Reload**.
  - **Omarchy**: with the `myriad-omarchy` addon there's also an "Omarchy" theme that always matches your system
    theme, switching whenever you run `omarchy-theme-set`. It's what a fresh install starts with on Omarchy.
  - **Theme roles**: colours come from a palette (accent, secondary, red, green, yellow, blue, magenta, cyan) that
    modules use as defaults, so ESP, tracers, categories, notifications and the module list follow the theme. A
    colour you change on a module is saved with that module's settings as an override.
  - **Preferences** (UI scale, pausing singleplayer, mod key, vim keys) are yours, not the theme's, and stay put when
    you switch.

Default shortcuts follow [Omarchy](https://omarchy.org)'s tiling bindings. The mod key stands in for Super, which
Hyprland keeps for itself, and defaults to **Alt** (change it in Theme → Preferences):

| Keys | Action |
|---|---|
| `mod+Space` | launcher (Ctrl+j/k to move, Enter to run, Shift+Enter for the alternate action) |
| `mod+Return` / `mod+K` | console / keybindings |
| `mod+W` / `mod+T` / `mod+F` | close / toggle floating / full screen |
| `mod+J` / `mod+L` | toggle split / toggle workspace layout (Columns, Dwindle, Master) |
| `mod+1..9` / `mod+Shift+1..9` | switch workspace / move window there |
| `mod+Tab`, `mod+Shift+Tab`, `mod+Ctrl+Tab`, `mod+scroll` | next / previous / former workspace |
| `mod+S` / `mod+Shift+S` | toggle the HUD workspace (Omarchy's scratchpad) / move window to the HUD |
| `mod+Arrows` / `mod+Shift+Arrows` | focus / swap in that direction |
| `mod+-` / `mod+=` | shrink / grow window |
| `mod+Shift+Space` / `mod+Shift+Backspace` | toggle the bar / toggle gaps |
| `mod+LMB drag` / `mod+RMB drag` | move (floating) or swap (tiled) / resize: tiled windows move their nearest edges, floating windows their nearest corner |

With Omarchy, Hyprland itself takes `Alt+Tab`. Use `mod+scroll` instead, or pick another mod key.

**Inside a window** everything is reachable from the keyboard. `j`/`k` (or ↓/↑) move the selection, and `h`/`l` (or
←/→) adjust a value or fold/unfold a section. `Enter`/`Space` activate, `o` unfolds a module's settings, `gg`/`G`
jump to the top/bottom, `Ctrl+d`/`Ctrl+u` move half a page, and `/` focuses the filter. Esc leaves a text field, or
closes the menu.

Every binding can be changed in the Keybinds panel.

## Commands

Prefix `.` (change with `.prefix`). Commands autocomplete in chat and in the console panel.
`.toggle <module>`, `.bind <module> <key|none>`, `.set <module> [setting] [value]`, `.reset <module>`,
`.profile [load|delete|save]`, `.friend add|remove|list`, `.theme <preset>`, `.addons`, `.panic`, `.help`, `.menu`,
`.modules` (click one to toggle it), `.binds`, `.say <message>` (sends text starting with the prefix as chat),
`.reload` (re-read the profile from disk), `.disconnect`, and `.fakeplayer add|remove|clear|list` (client-side
dummies for testing).

Setting ids are scoped to their group. When two groups share a name, use `group.setting`, for example
`.set esp colors.players #FF00FFAA`.

## Config

Config lives in `.minecraft/myriad/`. Module state and the menu layout belong to the active **profile**
(`profiles/<name>/modules.json`, `ui.json`). `myriad.json` holds global options and key actions, and `friends.json`
holds friends. Entries for modules or windows whose addon isn't installed are kept on save, so removing an addon and
adding it back later loses nothing.

---

## Writing an addon

An addon is an ordinary Fabric mod with a `myriad` entrypoint. Two projects get you going:

- [**myriad-addon-template**](https://github.com/fyzz-dev/myriad-addon-template) is the project to start from: a module, a mixin-driven module and
  a HUD element, wired up and building.
- [`example-addon/`](example-addon) is the reference. It's a complete feature set (waypoints with a module, command,
  window, HUD element and saved data, plus Auto Tool, Chat Timestamps and a settings showcase) written to be read.
  Its [README](example-addon/README.md) maps each file to the pattern it shows.

### Setting up

1. In this repository, run `./gradlew publishToMavenLocal`. It publishes `dev.myriad:myriad` and
   `dev.myriad:myriad-essentials` (with sources) to `~/.m2`. Run it again after pulling Myriad changes.
2. Create your addon from the template repository (or clone it), then rename things: the mod id `my-addon` in
   `fabric.mod.json`, `settings.gradle` and the mixin config's file name, the package, and the `MyAddon` class.
3. `./gradlew runClient` starts the game with Myriad, Essentials and your addon, with the menu open.
   `./gradlew build` puts your jar in `build/libs/`.

The template's `build.gradle` pulls Myriad from `mavenLocal()` with `modImplementation`. Essentials is on the dev
runtime only (`modLocalRuntime`), so you can test next to the stock modules without depending on them.
`fabric.mod.json` declares `"entrypoints": { "myriad": [...] }` and `"depends": { "myriad": ">=0.1.0" }`.

Inside this repository, `essentials` and `example-addon` are built the same way and may only use `dev.myriad.api`:
the build fails if either references `dev.myriad.impl` (`./gradlew checkApiOnly`).

### The entrypoint

```java
public final class MyAddon implements MyriadAddon {
    @Override
    public void initialize(AddonContext ctx) {
        MyStore store = new MyStore(ctx.storage());              // shared state: create it here, pass it on
        ctx.registerModules(new MyModule(store), new Other());   // menu order
        ctx.registerCommand(new MyCommand(store));
        ctx.registerPanel("My Window", "\uf0ae", () -> new MyPanel(store));
        ctx.registerHud("My Element", "\uf05a", MyHud::new);
        ctx.registerKeyAction("Do Thing", Keybind.NONE, () -> {});
        ctx.events().subscribe(new AlwaysOnListener());
    }
}
```

Myriad runs each phase for every addon in turn: `registerCategories`, then `initialize`, then config load, then
`postInitialize` (the place to look for optional integrations). If an addon throws, it is marked **failed**,
everything it registered is removed, and the game keeps running. The Addons panel shows why it failed.

### Conventions

Following these keeps addons consistent with each other and with the stock modules:

- **Categories.** Use the shared ones (`Categories.COMBAT`, `MOVEMENT`, `RENDER`, `PLAYER`, `WORLD`, `MISC`)
  whenever they fit. Those windows hold modules from every addon; a module's tooltip and expanded card show which
  addon it comes from. Register your own category (in `registerCategories`) only for things that fit nowhere else.
- **Shared state** lives in objects you create in `initialize` and pass to constructors, not in static fields.
- **Colours** default to theme roles (`SettingColor.role(Mode.ACCENT)`, `RED`, `TEXT`, …) so they follow the
  player's theme. A colour the player changes is saved as an override.
- **Reaching a module** from a mixin, another module or another addon: `Modules.active(MyModule.class)` returns
  it while it's on, or null (off, not installed, or Myriad not started yet). Don't keep a static `INSTANCE`.
  For a module from an addon you don't compile against, look it up by id:
  `Myriad.modules().get(MyriadId.of("other-addon", "their_module"))`.
- **Mixins** stay thin. Put the logic in a static method on the module that starts with `Modules.active(...)`, and
  have the mixin call it. Prefix handler names with your mod id. Prefer an event over a mixin when one exists. For
  hooks into other mods (Sodium, Iris), set `"plugin": "dev.myriad.api.mixin.CompatMixinPlugin"` in your mixin config
  and put them under a `compat.<mod id>` package.
- **Don't depend on Essentials.** Players can remove it. Everything general it used to keep to itself is in the
  core API now (below).

### Modules

Modules are subscribed to the event bus only while enabled, so their `@Subscribe` methods run only while they're on.

```java
public final class MyModule extends Module {
    private final DoubleSetting range = sgGeneral.doubleSetting("Range").defaultValue(4.5).range(0, 6).build();
    private final SettingGroup sgRender = settings.group("Render");          // later groups start folded
    private final ColorSetting color = sgRender.color("Color").defaultValue(SettingColor.role(SettingColor.Mode.RED)).build();

    public MyModule() { super(Categories.COMBAT, "My Module", "Does a thing."); }

    @Subscribe
    private void onTick(TickEvent.Post e) { ... }

    @Override
    public String hudInfo() { return "mode"; }    // shown in the module list
}
```

**Settings**: bool, int, double (`range` for the hard limits, `sliderRange` for the slider), enum, string, string
list, color (fixed, rainbow, or a theme role), keybind, action button, registry lists (blocks, items, entity
types, status effects, any registry), a single registry entry (`item`, `block`, `registry`), a block position
(`blockPos`, with a "Here" button), a runtime dropdown (`choice`), other modules (`modules`, e.g. "pause while
these are on") and a file (`file`, with the system file picker). `visible(...)` hides a setting until it matters, and `onChanged(...)` reacts
to changes. For a new type, extend `Setting<T>` and register an editor with
`ctx.settingWidgets().register(MySetting.class, s -> widget, stacked)`. See the example's `RangeSetting`.

### Events

Annotate a method with `@Subscribe(priority = Priority.HIGH, receiveCancelled = false)`. Handlers for a
supertype also receive its subtypes. A cancelled event is still dispatched, but handlers skip it unless they set
`receiveCancelled`. `ctx.events().listen(Event.class, e -> …)` returns a `Subscription`, and
`ctx.events().subscribe(object)` subscribes any object's `@Subscribe` methods. A handler that keeps throwing is
logged against its addon and then disabled.

Core events: `TickEvent.Pre/Post`, `Render2DEvent` (with a `canvas()` in GUI pixels), `Render3DEvent`,
`PacketEvent.Send/Receive` (cancellable and replaceable, with bundles split into their packets),
`MovementPacketsEvent` (rewrite what's sent), `PlayerMoveEvent` (change this tick's movement: speed, flight),
`InputEvent` (press movement keys for one tick), `MouseLookEvent` (scale or cancel turning),
`BlockBreakEvent.Start/Progress` (take over block breaking), `BlockBrokenEvent` (you broke a block),
`InteractEvent.Block/Item/EntityTarget` (cancellable right-clicks), `ItemUseEvent.Finished/Stopped`, `AttackEvent`
(cancellable, before any attack goes out), `EntityEvent.Added/Removed`, `BlockUpdateEvent` (server block changes, old
and new state), `ChunkEvent.Loaded/Unloaded`, `ContainerEvent.Opened/Loaded/SlotUpdated/Closed`, `ChatReceiveEvent`
(hide or change incoming chat), `ChatSendEvent`, `ItemTooltipEvent` (add tooltip lines), `CameraEvent`, `KeyEvent`,
`MouseButtonEvent`, `MouseScrollEvent`, `CharEvent`, `ScreenOpenEvent`, `WorldEvent.Join/Leave`, `ModuleToggleEvent`,
`WindowResizeEvent`, `GameReadyEvent` and `ShutdownEvent`.

### Services and helpers

Shared services keep addons from fighting over the same state:

| | |
|---|---|
| `Myriad.rotations()` | per-tick server-side rotation requests; the highest priority wins. Pass a callback to act once the rotation has been sent |
| `Myriad.inventory()` | server slot tracking, `select`, silent swaps, `hold(owner, slot, ticks)`; `bestInHotbar(score)`, `count`, `moveToHotbar`, `ensureInHotbar`; `move(from, to)`, `swapWithOffhand`, `quickMove`, `drop` |
| `Myriad.placement()` | placing blocks: neighbour clicks, rotation, silent swap, cooldowns; `clickTargets(pos)` and `place(hit, …)` when the clicked face matters (stairs, logs, slabs) |
| `Myriad.containers()` | `open(pos, timeout)` a chest/barrel/shulker and get a `View` once its contents arrive: `find`, `count`, `quickMove`, `swapWithHotbar`, `drop`, `close` |
| `Myriad.tasks()` | work over ticks: `later`, `every`, and `sequence` (run / wait / waitUntil with timeouts). A module's tasks are cancelled when it's disabled |
| `Myriad.server()` | TPS, ping, address, lag (`isLagging(ms)`), rubberbands (`rubberbandedWithin(ms)`), packets per second, totem pops |
| `Myriad.chat(text, id)` | a chat line that replaces the previous one with the same id (progress, status) |
| `Myriad.friends()`, `Myriad.notifications()` | friends list; toasts |
| `Myriad.ui().confirm(...)` | a yes/no dialog |
| `ctx.storage()` | your addon's own folder with atomic JSON reads and writes |

Helpers, so addons don't each re-derive them (`dev.myriad.api.*`):

| | |
|---|---|
| `combat.Targets`, `combat.TargetSettings` | finding entities to attack or highlight with shared rules (hostile, neutral-when-angry, friends, invisibles, walls), sorted by distance, health or angle. `TargetSettings` is the standard "Targets" settings group |
| `combat.Damage` | explosion, crystal, bed, anchor, fall and melee damage after armour, enchantments and effects; against a predicted position, with chosen blocks treated as air |
| `util.Entities` | `kind`, `isHostile`, `isFriend`, `ping`/`gameMode` of a player, `intersects(box)`, render distance, render-interpolated boxes, `predict(entity, ticks)` |
| `util.BlockInfo`, `util.ItemInfo` | unbreakable, instant-break, blast-resistant, storage, clickable blocks; enchantment levels, food, shulker contents, durability, weapon/tool/armour slot |
| `util.Reach` | reach and line of sight from the eyes, `aimPoint(entity)`, `hitFor(pos)` (the face to click to open or break a block) |
| `util.Interactions` | attack, use, interact with a block, start/continue breaking, swing; `shouldPause(eating, mining)`; attack charge, item-use and jump cooldowns |
| `util.Packets` | `send`, `sendSilently` (skips events, for your own tricks), `sendSequenced` (block and item actions) |
| `util.Mining` | mining speed and progress as the server computes them; fastest tool slot |
| `util.Positions` | `sphere`, `cube`, `box` of block positions (nearest first), neighbours, face centres |
| `util.MathUtil` | angles to a point, angle differences, look and ground direction vectors, closest point on a box, lerp/map/snap |
| `util.Movement` | which way the movement keys point, horizontal speed, setting speed along the input |
| `util.Slots` | player inventory indexes ↔ screen slot ids |
| `util.Timer`, `util.RateCounter`, `util.Format`, `util.Texts` | delays and cooldowns; events per second; distances, durations, compact numbers; clickable chat (run a Myriad command, copy, hover) |
| `util.Async`, `util.Http` | a shared worker pool (and a hop back to the render thread); GET/POST with JSON |
| `util.FakePlayers` | client-side dummy players for testing combat and render features |
| `render.Renderer3D` | boxes, real block shapes, single faces, lines, circles, tracers |
| `render.WorldLabel`, `render.FadeMap`, `render.RenderStates`, `render.PlayerHeads` | labels on world positions (text and item icons); highlights that fade in and out; the entity behind a render state in renderer mixins; players' faces |
| `ui.ThemePalette`, `ui.Theme` | build a theme from a terminal palette; themes that follow something live, explain problems, or replace old ids |
| `mixin.CompatMixinPlugin` | mixins that apply only when another mod is (or isn't) installed: put them in `compat.<mod id>` (or `compat.no_<mod id>`) |
| `command.arguments.Arguments` | argument types: `blockPos` (with `~`), `item`, `block`, `entityType`, any registry, `enumValue`, `duration`, `choice`/`suggesting`, `module`, `player` |

### Building bigger addons

How the pieces fit the kinds of addons people build:

- **A schematic printer** walks the blocks it still needs with `Positions.sphere` (nearest first), gets materials
  into the hotbar with `inventory().ensureInHotbar`, and places with `placement().place(hit, slot, options)`, choosing
  the face from `placement().clickTargets(pos)` to get orientation right; on strict servers it rotates first and places
  from the `rotations()` callback. `BlockUpdateEvent` confirms what the server actually placed, `FadeMap` with
  `Renderer3D.blockShape` shows the plan, `tasks()` paces it, and it pauses while `server().isLagging(...)` or right
  after a rubberband. A `FileSetting` picks the schematic and a `BlockPosSetting` its origin. Integrations with other
  mods (Litematica, Baritone) belong in the addon: keep any code touching their classes in its own class, only load it
  after `FabricLoader.getInstance().isModLoaded("litematica")`, and put mixins into them under `compat.litematica`.
- **A storage manager** finds containers as chunks arrive (`ChunkEvent.Loaded`, `BlockInfo.isStorage`), opens them
  with `containers().open(pos)`, reads them in `ContainerEvent.Loaded`/`SlotUpdated` (including ones the player opens
  by hand), and runs multi-step jobs with `tasks().sequence(...)`. It saves what it learns with `ctx.storage()`, says
  where an item is with `ItemTooltipEvent`, reads shulkers with `ItemInfo.contents`, and marks chests with
  `WorldLabel.Segment.item(...)` icons.
- **A crystal PvP suite** picks targets with `TargetSettings`, scores placements with `Damage.crystal` against
  `Entities.predict` positions (treating blocks it's about to break as air), breaks crystals the tick they appear with
  `EntityEvent.Added` + `Interactions.attack`, checks `BlockInfo.isBlastResistant` and `Entities.intersects` for
  placements, rotates through `rotations()`, swaps with `inventory()`, pauses with `Interactions.shouldPause`, and
  checks `Reach` before every action. `AttackEvent` lets separate modules react to the same hit, and `.fakeplayer`
  gives you someone to test against in singleplayer.

### UI

- A `Panel` draws on a `Canvas`, which provides SDF rounded rects, gradient borders, shadows, frosted backdrops and
  TTF text. Glyphs from the bundled Nerd Font (`"\uf041"` etc.) work as icons.
- Extend `WidgetPanel` to build panels from widgets: `Toggle`, `Slider`, `Dropdown`, `TextField`, `ColorPicker`,
  `KeybindButton`, `RegistryPicker`, `Collapsible`, `HBox`/`VBox`, `ScrollView`, and `SettingsView.build(settings)`.
  Call `rebuild()` when the structure changes.
- **HUD elements** extend `HudPanel`, which provides a title, an icon, a settings group with Scale, `alignRight()`
  and `preview()` (no world, e.g. arranging the HUD from the title screen). For text lines, extend `TextHudPanel` and
  implement `lines(out)`. It sizes and aligns the text, and adds the colour options every stock element has. Use
  `HudStyle` and `ItemHud` directly for custom drawing.
- Panels can declare their own `settings`, which are saved with the window. Open one with
  `Myriad.ui().openPanel(id)`. Top-bar widgets extend `BarWidget`.
- Add a `contact` block (`homepage`, `sources`, `issues`) to your fabric.mod.json and the Addons panel links to it.

**3D.** Call `Renderer3D.box/line/tracer` from a `Render3DEvent` handler. Everything queued is drawn in one batch;
`Renderer3D.lineWidth` applies to the lines you queue after it. For labels over the world, use `WorldLabel` or
`Projection.toScreen(pos)` in a `Render2DEvent` handler and draw on `event.canvas()`. To draw with the canvas inside
any vanilla screen or tooltip, call `Myriad.ui().draw(drawContext, canvas -> …)`.

## Credits

The code layout borrows ideas from [Meteor Client](https://github.com/MeteorDevelopment). The UI
borrows from Hyprland.

Bundled fonts: Noto Sans (SIL OFL 1.1) and JetBrains Mono Nerd Font (SIL OFL 1.1). The license texts are in
`core/src/main/resources/assets/myriad/fonts/`.
