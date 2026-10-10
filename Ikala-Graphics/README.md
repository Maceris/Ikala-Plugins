# Ikala-Graphics

This plugin adds graphics functionality backed by LWJGL libraries.

See src/main/resources/licenses for licenses.

## Graphics Manager

The `GraphicsManager` class provides utilities for managing graphical 
windows.


**Icon**

The icon defaults to `textures/game_icon.png`, but can be changed in the configuration.

**Filters**
Filters can be applied to the graphics. The folder where the filters are found defaults to `shaders/filters` but can be configured.
These should have a uniform `sampler2D screenTexture` where the texture is passed in to be modified.

## Gate benchmark

The gate benchmark measures how long the renderer takes to draw a made-up world at a view distance.
It builds a deterministic synthetic terrain out to a radius, bakes every section with something in it,
waits for everything to be on the GPU, then turns the camera once around while recording how long each
frame takes on the GPU (from timestamps around each render stage) and on the CPU. It passes if the GPU's
95th percentile frame is within 8 ms.

It can be run from the Benchmark section of the Graphics Debug window, or from the command line.

**Command line options**

These are Java system properties, given before `-jar`:

| Option | Default | What it does |
| --- | --- | --- |
| `-Dikala.benchmark=<recipe>` | not set | Runs the benchmark at startup. `<recipe>` is the world: `TERRAIN` (hills, caves, plants and trees, tuned to the expected load), `CHECKERBOARD` (a block in every other cell, the worst case for face culling) or `CAVES` (rock full of caves under a solid roof, the worst case for occlusion). |
| `-Dikala.benchmark.radius=<sections>` | `12` | The view distance, in 16-block sections. |
| `-Dikala.benchmark.keep=true` | `false` | Keeps the world once the run finishes, so you can fly around it, and hides plugin menus that would cover it. Without this, the app closes when the run finishes. |

For example, from the asset editor's folder:

```sh
java -Dikala.benchmark=TERRAIN -Dikala.benchmark.radius=12 -jar Ikala-Core-0.6.0.jar
```

**Results**

Results are logged, shown in the Graphics Debug window, and added to `benchmark-results.txt` in the
working directory. Each entry has:
- the world, radius, seed and resolution;
- the load: sections in range, sections with content, placements and triangles;
- the average, 95th percentile and worst time for the whole GPU frame, the CPU, and each render stage;
- whether it passed.

**In the Graphics Debug window**

- **Frame timing** shows each render stage's GPU time and the CPU time, over the last 120 frames, at any
  time.
- **Benchmark** picks the recipe and radius and has Run and Stop buttons.
  - *Keep world* leaves the finished world in place, and *Remove world* takes it away.
  - *Hide plugin UI* hides plugin menus so the scene behind them can be seen.
