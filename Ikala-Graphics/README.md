# Ikala-Graphics

This plugin adds graphics functionality backed by LWJGL libraries.

See src/main/resources/licenses for licenses.

## Features

The renderer is Vulkan 1.3 only. Plugins use it through a `GraphicsContext`, from
`GraphicsManager.forPlugin(pluginName)`. Everything made through a context belongs to that plugin
and is released when the plugin unloads, so a plugin never frees GPU resources by hand and a
reloaded plugin starts clean. Each part of the API is a method on the context:

| Call          | What it provides                                                                                               |
|---------------|----------------------------------------------------------------------------------------------------------------|
| `textures()`  | Load textures (`load(path, colorSpace)`, DDS or stb images) as handles; color or data, switchable later.       |
| `meshes()`    | Register meshes and models for drawing, or `registerBakeable` for meshes that sections place.                  |
| `instances()` | Place, move, rotate, scale and remove models in the world, set their materials and animations.                 |
| `lights()`    | Point and spot lights with color, intensity, range, cone and whether they cast shadows.                        |
| `sections()`  | Terrain: many placements of bakeable meshes per 16^3 section, baked into a few compact meshes.                 |
| `lods()`      | Level of detail worlds, from a prebuilt DAG (`add`) or built live from the plugin's sections (`fromSections`). |
| `debug()`     | Debug shapes (lines, arrows, boxes, spheres, text), for one frame or kept until removed.                       |
| `ui()`        | The retained mode UI: surfaces, themes, YAML specs, scripts and tools.                                         |

World positions are doubles on the CPU and relative to the camera on the GPU, so worlds can be
large without jitter. Handles are safe to keep after what they name is gone; using a stale handle
does nothing.

### Immediate mode UI (IkGui)

`IkGui` is a Java port of [Dear ImGui](https://github.com/ocornut/imgui) (Omar Cornut, MIT), with
its API and behavior: windows, docking, tables, menus, popups, drag and drop, text input, plots and
the rest, plus `IkGuiDemo`. It draws through its own Vulkan stage with signed distance field
shapes and bindless textures. Use it for tools and debug views:

```java
public class MyWindow extends GuiWindow {
    public MyWindow() { super("My Window", 0); }

    @Override
    public void draw(int width, int height) {
        IkGui.begin(title, windowOpen, windowFlags);
        IkGui.text("Hello");
        IkGui.end();
    }
}

WindowManager windows = GraphicsManager.getWindowManager();
windows.addWindow(context, "my-window", new MyWindow());
windows.show("my-window"); // windows start hidden
```

Its behavior is checked against a port of the
[Dear ImGui test suite](https://github.com/ocornut/imgui_test_engine) (MIT); the test engine
itself isn't copied.

### Retained mode UI

Game UI is a tree of nodes on `Surface`s, laid out by a flexbox-style engine (rows, columns, grids,
overlays, scrolling, sizes that fit, grow or take a share) and drawn through IkGui. A UI can be
built three ways, all making the same nodes:

- **Java**, through `context.ui().surface(id)` and node builders.
- **YAML specs** (`context.ui().loadSpec(path)` then `open(spec, bindings)`): templates with
  parameters, imports, `{name}` bindings to live values, `@KEY` translations and `$token` theme
  references.
- **IkalaScript**, through `context.ui().scripts()`, for flat event scripts with `file#label` entry
  points.

Themes (`loadTheme`, `useTheme`, `addStyles`) give tokens, variants and per-type defaults with
hovered, held, focused, selected and disabled states. Plugins add tools to the editor's menu with
`registerTool`. The `ui.automation` package drives UIs with real input from tests, finding nodes
by selectors.

A plugin's drawing that throws, such as an `Immediate` node's content, a `GuiWindow` or the
toolbar, doesn't end the frame or the editor: IkGui is unwound to where it was, the error is logged
once with the plugin's name, and what threw stops drawing (a surface or window is hidden until its
plugin shows it again, a toolbar is removed). Actions fired from the UI and posted changes were
already run this way.

### Scene rendering

- **Meshes and instances:** models are uploaded into a shared geometry arena and drawn by
  GPU-driven culling, resulting in a few indirect draws per pass regardless of object count.
- **Sections:** a plugin hands over flat arrays of placements, positions, rotations and the faces
  their neighbors hide (worked out with `Faces`), to be baked into a HLOD. Bakes run on workers,
  and a rebaked section keeps drawing its old bake until the new one is ready.
- **Level of detail:** `lods().fromSections()` makes a live LOD world that the plugin's sections
  feed as they bake. Far away, a section's opaque regions are drawn from simplified clusters instead
  of its own meshes. Built cells are cached on disk (see `lod-cache-folder`, `lod-cache-max-mb`).
- **Volume bricks:** past the volume distance (96 m), a section's cut out and see-through meshes,
  like leaves, glass and water, are drawn from a 16^3 voxel brick instead of meshes. Farther out,
  coarse bricks with 2 m or 4 m voxels each stand in for 8 or 64 sections, so foliage reaches the
  horizon at large view distances (`GraphicsSettings.coarseBricks`, on by default).
- **Lighting:** physically based, with a sun, sky light, point and spot lights, shadows,
  transparency and anti-aliasing (see below). `GraphicsSettings` holds the switches and budgets.
- **Fog and smoke:** the scene's `Fog` is height fog of a density, color (albedo), falloff, base
  height and anisotropy, optionally with the physical sky's haze so far land fades into the sky.
  Plugins place local media, like smoke, fog banks or steam, through `graphics.media()`: a
  `MediumVolume` is a box, sphere or cylinder with a soft edge, a density, albedo, emission,
  anisotropy, and noise drifting with the wind. `add()` returns a `MediumHandle` to `update()` or
  `remove()` it; a plugin's volumes are removed when it unloads, and a scene holds up to 256.

  ```java
  MediumHandle smoke = graphics.media().add(
          MediumVolume.of(MediumVolume.Shape.CYLINDER, chimneyTop, new Vector3f(1.5f, 6, 0))
                  .withNoise(2, 4, 0.7f, new Vector3f(0, 2, 0)));
  ```
- **Debug views:** the Graphics Debug window shows frame timings, lights, LOD colorings, volume
  sources, G-buffer views and benchmark controls.

## Rendering techniques

What the renderer does and the work it builds on.

**Pipeline**

- Deferred shading into a G-buffer, linear HDR lighting, tone mapped once with
  [Khronos PBR Neutral](https://github.com/KhronosGroup/ToneMapping). Colors are authored in sRGB
  and decoded exactly: color textures load as `_SRGB` formats the GPU decodes and filters in linear
  light, data textures such as normal maps as `_UNORM`, and either can be switched while loaded.
- DDS textures load with their stored mip levels, block compressed (BC1 to BC7) as they are; their
  average colors, kept for distant drawing, come from a small level decoded on the CPU.
- Bindless textures and materials in storage buffers; one fixed set of global bindings.
- GPU-driven culling: a compute pass culls instances, sections and LOD clusters by frustum and
  two-phase Hi-Z occlusion against a depth pyramid, and writes indirect draws with counts.
- Normals and tangents packed as octahedral directions (Cigolle et al., *A Survey of Efficient
  Representations for Independent Unit Vectors*, JCGT 2014).

**Terrain sections**

- Faces hidden by neighbors are dropped by 16x16 coverage masks per face, by material class (opaque,
  cut out, translucent), before baking. Background: Lysenko, *Meshing in a Minecraft Game* (0fps,
  2012).
- Per-face material variants, chosen by the face's direction after rotation.

**Level of detail**

- A cluster DAG cut per frame by projected error, after Nanite (Karis, Stubbe and Wihlidal,
  *Nanite: A Deep Dive*, SIGGRAPH 2021) and batched multiresolution meshes (Ponchio, dissertation,
  2008). Clusters are grouped by a METIS-style graph partition (Karypis and Kumar 1998), and group
  borders are locked so levels never crack.
- A group's error is the two-sided distance between its output
  and its children, plus the children's largest, and spheres grow to hold the children's
  (Ponchio's saturation), so each cluster can decide on its own whether it is in the cut.
- Live worlds keep the DAG as an octree of region cells: a 64 m cell builds its own DAG with its
  boundary planes locked, a parent carries on from its children's top clusters, and a change
  rebuilds only its cell and those above it. Cell grids are offset differently at each level, so
  every area ends up under one root. Near the camera, a section whose newest bake the LOD doesn't
  show yet draws its own blocks until the rebuilt cells land.
- Simplification after Liu, Zhang and Yuksel, *Simplifying Textured Triangle Meshes in the Wild*
  (ACM TOG 44(6), 2025): edge collapses with quadric error (Garland and Heckbert 1997), virtual
  edges joining nearby parts, and correspondence to carry materials and attributes up the levels.
  Ideas also from [meshoptimizer](https://github.com/zeux/meshoptimizer) (Kapoulkine).
- Collapse costs weighted by how visible each face is from outside, from fixed hemisphere rays
  (Hoppe 1997), so hidden faces simplify freely.
- Exact planar merging of coplanar faces before building, with Delaunay flips to remove slivers.
- Textured meshes keep normals, tangents and texture coordinates across UV seams; textures are
  projected along each triangle's main axis.
- Far groups, once one texture repeat drops under the error threshold, blend their input's color,
  roughness, metallic and specular per vertex instead of keeping a material per triangle.
- Clusters are streamed on and off the GPU by distance, with a disk cache of built cells.

**Distant leaves, glass and water**

- Per-section voxel bricks in a 3D atlas, walked a voxel at a time (Amanatides and Woo, *A Fast
  Voxel Traversal Algorithm for Ray Tracing*, Eurographics 1987). Solid voxels draw as textured
  faces with a depth pass; see-through voxels go through the transparency volume.
- Surface samples spread by Roberts' R2 low-discrepancy sequence (2018).
- Hashed alpha testing past the first mips, so distant leaves keep their coverage (Wyman and McGuire,
  *Hashed Alpha Testing*, I3D 2017).
- Ideas for the brick format and volumetric shadows from Smolder (Mueller, SIGGRAPH 2026).
- Coverage volumes: parts finer than a group's resolution (far foliage, thin rods, scattered blocks)
  leave its triangles for a voxel volume of SGGX microflakes (Heitz et al. 2015), after Loubet and
  Neyret's hybrid mesh-volume LoDs (Eurographics 2017) and their isotropic self-shadowing (2018).
  Volumes small on screen draw as convex-hull shells whose coverage follows the view angle.
- Coarse bricks for far sections, downsampled from theirs: a coarse voxel is solid when at least half
  its voxels are, and otherwise holds their average extinction, which keeps the light a ray loses
  crossing it. Each level starts where its voxels project under the LOD's pixel threshold, with a
  margin at ring edges, and old bricks stay drawn until their replacements are in.

**Lighting and shadows**

- The Disney principled BRDF (Burley, *Physically-Based Shading at Disney*, SIGGRAPH 2012).
- Ambient specular by the split sum (Karis, *Real Shading in Unreal Engine 4*, SIGGRAPH 2013) with
  multiple-scattering compensation.
- Clustered light culling, 16x9x24 clusters (Olsson, Billeter and Assarsson, *Clustered Deferred
  and Forward Shading*, HPG 2012).
- Cascaded sun shadows with PCF; the middle and far cascades are kept in place in the world and
  redrawn only where something changes.
- Point light shadows from a light tree: nearby lights share cube maps, within a per-frame budget.
- A top-down sky occlusion map dims ambient light under cover.
- An occupancy volume around the camera (which voxels hold something static) for ambient
  occlusion and for marching rays toward lights without shadow maps. Visibility is estimated as a
  ratio (Heitz, Hill and McGuire, *Combining Analytic Direct Illumination and Stochastic Shadows*,
  I3D 2018).

**Transparency and anti-aliasing**

- Order-independent transparency weighted by a voxel transmittance volume (Drobot 2025), after
  weighted blended OIT (McGuire and Bavoil, JCGT 2013).
- Temporal anti-aliasing: Halton(2,3) jitter, depth-based reprojection, Catmull-Rom history, and
  variance clipping in YCoCg (Karis, *High Quality Temporal Supersampling*, SIGGRAPH 2014; Yang, Liu
  and Salvi, *A Survey of Temporal Antialiasing Techniques*, 2020). Depth rejection of history taps
  is from VRRT (Olejnik, SIGGRAPH 2026). Glass and water make history more reactive.

**Volumetric fog**

- Froxels of 16 px tiles by 128 log slices (Wronski, *Volumetric Fog*, SIGGRAPH 2014), lit by the
  sun through its cascades, the sky, and the clustered point and spot lights with their shadows,
  with a Henyey-Greenstein phase; jittered and blended with the last frame's volume.
- Energy-conserving front-to-back integration (Hillaire, *Physically Based and Unified Volumetric
  Rendering in Frostbite*, SIGGRAPH 2015). Opaque surfaces, the sky, translucent surfaces and volume
  bricks are each fogged at their own depth, so blending translucent layers over one another stays
  right.
- Media shade themselves analytically, without marching: sunlight is dimmed by each shape's chord
  toward the sun and the height fog's closed form, and sky light by how deep inside a volume a
  point is. Light scattered more than once follows Wrenninge's octaves (*Oz: The Great and
  Volumetric*, SIGGRAPH 2013), weighted by albedo and the volume's thickness. `FogMath` has the
  math in Java, with tests.

**Planned**: a particle system; stochastic lightcuts for light visibility (Yuksel 2019).

## Graphics Manager

The `GraphicsManager` class provides utilities for managing graphical 
windows.


**Icon**

The icon defaults to `textures/game_icon.png`, but can be changed in the configuration.

**Filters**

The filter stage is optional (`RenderConfig.builder(config).withFilter()`). It runs one full-screen
fragment shader over the lit scene before the UI is drawn. The Graphics Debug window's filter view
picks which shader runs:

- *Default*, which copies the scene unchanged;
- debug views of one G-buffer texture: *Base color*, *Normals*, *Tangents*, *Material index* or
  *Depth*;
- *All (split)*, every G-buffer texture side by side.

Picking a debug view turns the stage on if it was off. The shaders are in `shaders/filters`, and
`default.frag` lists the inputs every filter gets: the lit scene as `screenTexture`, the camera's
matrices, the screen size, and the G-buffer textures' bindless slots. There is no API yet for a
plugin to add its own filter, and the `filters-folder` config entry isn't read.

## Gate benchmark

The gate benchmark measures how long the renderer takes to draw a made-up world at a view distance.
It builds a deterministic synthetic terrain out to a radius, bakes every section with something in it,
waits for everything to be on the GPU, then turns the camera once around while recording how long each
frame takes on the GPU (from timestamps around each render stage) and on the CPU. It passes if the GPU's
95th percentile frame is within 8 ms. With `-Dikala.benchmark.gate=true` it also checks level of detail
cuts for cracks from several views, times edits until they are drawn, and reloads the world from the
level of detail cache.

It can be run from the Benchmark section of the Graphics Debug window, or from the command line.

**Command line options**

These are Java system properties, given before `-jar`:

| Option | Default | What it does |
| --- | --- | --- |
| `-Dikala.benchmark=<recipe>` | not set | Runs the benchmark at startup. `<recipe>` is the world: `TERRAIN` (hills, caves, plants and trees, tuned to the expected load), `CHECKERBOARD` (a block in every other cell, the worst case for face culling), `CAVES` (rock full of caves under a solid roof, the worst case for occlusion) `GLASS` (walls of clear and tinted glass every few blocks, the worst case for transparency) or `JUMBLE` (the hills, their top six blocks an even mix of cubes, slabs, stairs of every shape and facing, glass panes, lecterns and chests, the worst case for face culling and level of detail on real shapes). |
| `-Dikala.benchmark.radius=<sections>` | `12` | The view distance, in 16-block sections. |
| `-Dikala.benchmark.lights=<count>` | `0` | Scatters this many torches (point lights reaching 10 m) over the ground, the same ones every run, to measure many lights. |
| `-Dikala.benchmark.lod=true` | `false` | Draws far sections through level of detail, built live from the sections, and frees their own opaque meshes on the GPU. Without it every section's meshes stay on the GPU, which dense worlds like `JUMBLE` outgrow past a small radius (the run fails with sections that didn't fit in the baked geometry buffers). |
| `-Dikala.benchmark.average=<true or false>` | `true` | With level of detail, whether far groups blend their materials' looks per vertex. |
| `-Dikala.benchmark.keep=true` | `false` | Keeps the world once the run finishes, so you can fly around it, and hides plugin menus that would cover it. Without this, the app closes when the run finishes. |
| `-Dikala.benchmark.gate=true` | `false` | Runs the full gate after measuring: crack checks, timed edits and a reload from the cache. |
| `-Dikala.benchmark.walk=<m/s>` | `0` | Walks the camera along x while it turns, at this speed, to measure streaming. |
| `-Dikala.benchmark.cracks=<views>` | `4` | With the gate, checks only the first this many views for cracks, 0 to skip them; each takes minutes at a large radius. |
| `-Dikala.benchmark.editCracks=false` | `true` | With the gate, skips the crack check over the edits; the edits are still made and timed. A run that checks nothing reports "Cracks: not checked". |
| `-Dikala.benchmark.coarseBricks=false` | `true` | Turns coarse volume bricks off, for comparing. |
| `-Dikala.benchmark.fog=<density>[,<falloff>,<base>,<anisotropy>]` | not set | Turns the scene's fog on with this density (per meter) and, optionally, height falloff (per meter), base height and anisotropy. |
| `-Dikala.benchmark.haze=true` | `false` | Adds the physical sky's haze to the fog; it only shows with a physical sky (`-Dikala.benchmark.physicalSky=true`). |
| `-Dikala.benchmark.smoke=<x>,<y>,<z>` | not set | Places test media there: a column of white smoke drifting up, a glowing orange sphere 25 m along -x and a fog bank in front. |
| `-Dikala.vulkan.validation=false` | `true` | Not a benchmark option: runs without the Vulkan validation layers. Their checks can cost milliseconds of CPU a frame, so time the CPU without them, and keep them for correctness runs. |

For example, from the asset editor's folder:

```sh
java -Dikala.benchmark=TERRAIN -Dikala.benchmark.radius=12 -jar Ikala-Core-0.6.0.jar
```

**Results**

Results are logged, shown in the Graphics Debug window, and added to `benchmark-results.txt` in the
working directory. Each entry has:
- the world, radius, light count, seed and resolution;
- the load: sections in range, sections with content, placements and triangles;
- the average, 95th percentile and worst time for the whole GPU frame, the CPU, and each render stage;
- whether it passed.

**In the Graphics Debug window**

- **Frame timing** shows each render stage's GPU time and the CPU time, over the last 120 frames, at any
  time. *Wait* is how long a frame waits for its fence and swapchain image before recording (where vsync
  and a GPU running behind show), and *Between frames* is the rest of the main loop.
- **Lights** shows how many lights there are, how they were sorted into clusters (the busiest cluster, and
  whether any had more lights than it can list), and a *Cluster heat map* that tints each pixel by how many
  lights can reach it.
- **Benchmark** picks the recipe, radius and number of lights, and has Run and Stop buttons.
  - *Keep world* leaves the finished world in place, and *Remove world* takes it away.
  - *Hide plugin UI* hides plugin menus so the scene behind them can be seen.
