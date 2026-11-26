# Retained UI

All game UI (HUD, a draggable quest graph, factory inventories) is built with a retained-mode layer over IkGui in
Ikala-Graphics, because graphics must control what is drawn when. Graphics defines the API and core library;
other plugins extend it. It replaced the old `gui/component` draft, keeping its two good ideas: layout relative to
the parent, and cached results recomputed only when something changes.

## Two layers, both IkGui

- **Surfaces are IkGui windows,** placed by an anchor layout against the viewport (safe-area insets, screen-shape
  variants). Fixed panels are placed every frame, movable tool windows once, and the HUD is a transparent window
  that takes no input. Each surface is a navigation scope.
- **Panels are surfaces the user owns,** for tools: titled, resizable, dockable, and placed by their anchors only
  the first time IkGui has never seen them. Placing a docked window would undock it, so a panel never sets its
  position once IkGui has its window or saved settings. Surfaces stay undockable, so a game HUD can never be
  dragged into a dock.
- **Content uses a flex layout that runs before submission,** so every node knows its rectangle when submitted. That
  avoids immediate mode's usual one-frame lag on sizes.

Each node is submitted in tree order with one of three renderers:

| Renderer | Used for | Gets | Gives up |
| --- | --- | --- | --- |
| IkGui widget | Forms, settings, editor tools | Real widgets: text input with IME, sliders, tables, popups; the ported test suite | Looks follow the IkGui style |
| Custom item (`itemAdd` + `buttonBehavior` + DrawList) | Inventory slots, skinned buttons, graph nodes | Full visual control while still an IkGui item: focus, navigation, drag and drop, tooltips | Behavior written per widget; never text editing |
| Decoration (DrawList only) | Backgrounds, bars, graph edges | Cheapest | No input or focus |

Containers map onto IkGui too: `Scroll` is a child window, a large `Grid` uses `ListClipper`, and `Canvas` is a child
window of custom items under a pan and zoom transform. Item previews render once into a cached atlas slot, never a
live view.

## Layout and styling

- Units are UI units (pixels at a reference resolution times DPI and user scale), percent of the parent and em;
  lengths combine (`50% - 8u`). Sizing per axis is fixed, percent, fit or grow. Anchors replace compass alignment.
- Measure then arrange, only on dirty subtrees. IkGui widgets measure with the same style values IkGui draws with,
  so fit-to-content matches what is drawn.
- Variants such as `portrait` and `compact` swap style values; a different screen shape never needs different code.
- Theme tokens (`color.panel`, `spacing.medium`) that a plugin or texture pack can swap. Resolution is widget-type
  defaults, then classes in order, then inline values. The only selectors are states and variants: no cascade and no
  descendant selectors, so a node's look never depends on where it sits.
- The default theme matches IkGui's Ikala style exactly (`ThemeLoaderTest` checks it), so a screen looks the same
  until a game supplies its own theme.
- IkGui keeps Dear ImGui's split between color themes and size presets. The Ikala style is ours to change; the Dark,
  Light and Classic themes stay as upstream has them, and the IkGui tests use the upstream ones.
- Font size means line height. Translated UIs should prefer fit-to-content, wrap widths and `grow` with minimums over
  fixed widths, and nothing may assume left-to-right text or one glyph per character (text shaping isn't in yet).

## Describing UIs: Java, YAML specs and IkalaScript

All three build the same tree through one builder graphics owns. YAML specs have templates with parameters, stable
node ids, `{name}` bindings to observable values (instead of polling every frame), `@KEY` resource-bundle text and
`repeat` over lists keyed for reuse. Missing bindings are reported before a spec is shown.

Scripts use the UI through a command queue applied on the render thread: scripts drive UI (`await` a choice instead
of global state like `getLastDialogueSelection()`), UI events run scripts (`script(file.iks#label)`), and tests find
nodes by id or text and inject input at the node rather than at OS coordinates. IkalaScript stays without
procedures: scripts are flat lists of statements with labels, and entry points are `file#label`.

## Ownership and reloading

- Each node records its owning plugin context; a plugin's surfaces and subtrees go when it unloads. Its specs close,
  its node types go, and other plugins' specs built from those types are closed, so no plugin class outlives its
  plugin (a test collects the plugin's class loader to check).
- Scripts and plugins hold handles (owner, node id, generation), never nodes, so a handle whose owner unloaded
  resolves to nothing instead of pinning the old tree. Builders are graphics classes.
- Scripts a plugin starts are terminated when it unloads.
- Prefer data-described widgets (specs, scripts), since they carry no plugin classes; subclass only for real custom
  behavior.

## Navigation

Keyboard, Tab and gamepad navigation come from IkGui's nav system: Tab order is submission order, d-pad movement is
spatial by rectangle, and IDs come from node id paths, so they are stable across rebuilds. Gameplay must ignore input
while UI navigation is active.
