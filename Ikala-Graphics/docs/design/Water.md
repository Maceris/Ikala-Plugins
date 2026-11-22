# Water

In the game, water stays game logic like Minecraft's: blocks with levels and slopes that spread, no fluid simulation
or mass conservation. Graphics makes it look right.

## API: height-field tiles, content-agnostic

A plugin hands in water tiles (up to 64 by 64 cells): corner heights, a mask of where water is, optional bottom
heights, a flow per corner (`withFlows`) and a `WaterBody`. Levels and slopes are just heights. Disturbances
(`water().disturb`, painted waves) add ripples and waves. Where a plugin uses tiles, water leaves the section
meshes, LOD and volume bricks.

## Optics: a bio-optical model, not artist colors

A `WaterBody` holds chlorophyll, dissolved organic matter (CDOM) and sediment, and absorption and scattering at red,
green and blue come from Ma et al. 2016's formulas (`WaterMath`, with tests). Clear ocean, green lakes and muddy
rivers then come from real concentrations.

- The column's light uses the deep reflectance 0.33 b_b / (a + b_b) reached with depth (Maritorena et al. 1994).
  The fog's scattering octaves were tried first and made muddy water dark gray where it should be bright tan.
  Particles backscatter 1.8% (Petzold).
- The surface is a microfacet BRDF with Fresnel, environment and sun reflections, and refraction of the scene
  behind; waves too small to draw become roughness (Bruneton, Neyret and Holzschuch 2010), so far water stays
  sharp without shimmering.

## Waves

- **Wind spectrum:** JONSWAP, becoming Pierson-Moskowitz for a fully developed sea; 128 waves at whole harmonics of
  a 160 m profile, cos^2 spreading over 16 directions. Tiles sharing a wind share a climate (at most 4).
- **Long waves get their own profile:** a 1280 m profile per direction (2048 samples) holds every harmonic from
  640 m down to 85 m, and every layer adds it. A rough sea's peak is 125 to 200 m long, so with only the 160 m
  profile a 14 m/s sea drew 2.65 m significant height against 4.2 m. Making the one profile longer would have
  needed 4 times the samples for the same shortest wave. Where the camera stands along a direction is kept to
  1280 m, a whole number of short profiles, so it serves both.
- **Swell is a second spectrum, not a stronger wind:** a raised wind only makes steeper chop. A `WaterBody.Swell`
  (height, period, direction, spread) is sampled at the long profiles' harmonics with a narrow JONSWAP (gamma 7),
  scaled to its height exactly, and spread as a wrapped normal, nearly all in one or two directions. It gets its
  own profiles, because its direction shares differ from the wind's: folding it into the wind's profiles would
  divide by the wind's share, which is zero upwind. Periods are kept to 8 to 20 s, what the long profiles hold.
- **The wave grid leaves swell alone:** the grid's wind band holds shares of the wind's strongest direction, and
  swell from another way has no share there. Swell near shores is calmed only by the edge calm, and it doesn't
  reflect. A body may have swell and no wind.
- **Swell code is compiled out when no climate has one** (a specialization constant, and the swell profiles' pass
  is skipped): compiled in but unused, it cost the surface 5% (1.28 to 1.35 ms on the island gate), from the
  unrolled 16-direction loops growing, not from the work.
- **Each direction has its own profile.** Sharing one, shifted, made straight crest lines in a lattice and swells
  2.5 times too tall.
- **Band-limited layers** (all, 0.25, 1 and 4 m): vertices ride the 4 m layer, and normals use the layer a pixel's
  footprint can show, with the rest turned into roughness. A pixel shades one layer, picked at random by its blend,
  and TAA blends them.
- **Wavelet amplitude grid** (Jeschke et al. 2018): 128 m at 1 m cells around the camera, 16 directions, a wind band
  (blocked and reflected by shores, rebuilt by the wind in 20 s) and a ripple band (rings dying away in 6 s).
- **The wind band holds each direction as a share of the climate's strongest direction**, not of that direction's
  own amplitude. Otherwise a direction the wind gives nothing (upwind) stays at nothing: shore reflections back
  upwind vanished and painted waves couldn't run against the wind. Angular diffusion acts on the departure from the
  wind's spreading, or open water loses height in the grid.
- **Depth and flow** (Jeschke and Wojtan 2023): profiles at three depths (deep, 3 m, 1 m), wavenumbers from the
  dispersion relation, shoaling capped at 1.6; a depth whose waves match deep water's reads deep water's profiles.
  Currents use a two-phase flow map blended in both shaders; picking a phase per pixel left grain. Ripples advect
  through a clamped Catmull-Rom spline, because bilinear steps blurred a 1 m ring away in a 1 m/s current.
  `waveTime` reaches shaders wrapped to one flow cycle; anything counting cycles uses `sheetTime`.
- **Choppy waves and foam:** vertices move sideways with lambda 1, normals go through the inverse Jacobian. Foam is
  calibrated to measurements, not a fixed threshold: total coverage follows Monahan and O'Muircheartaigh
  (2.95e-6 U^3.52); breaking crests take 11.5% of it, and the grid's foam channel takes the rest, injected where the
  long-wave Jacobian crosses its threshold and fading over 4 s. Use the tanh form of the normal CDF (a logistic's
  tail is 8 times too heavy at 3 sigma) and clamp it to 8 sigma (some drivers overflow to NaN).

## Sides, underwater and transparency

- Sides are faces of the water's volume, 1 cm off what they stand against, closing banks, steps and waterfall tops.
  A streaking, aerated falling-sheet look was tried and rejected for looking like an extra layer.
- A view through a side refracts in, crosses the water to where it leaves the tile's box, and refracts out. The
  water draws its surfaces, copies the scene, then draws its sides, so sides show water behind them; a far-first
  two-layer scheme made every far pixel shade twice under grazing waves.
- Under water, the fog volume fills with the water's medium, but only while the camera is under water: froxels are
  too coarse for the water's edge, and from above the surface puts its own column on. The fog keeps extinction and
  transmittance by color for this; air, haze and smoke stay gray.
- Transparency behind water: the water draws its depth into a gate first, and the transparent stages run twice
  (`TransparencyChain`): what lies behind the gate before the water copies the scene, then what's in front.

## Spray, splashes and rain rings

- The wave grid's step records hits: spray where crests break, mist and droplets where falling water lands,
  droplets where water is disturbed. The next frame's splash pass spawns them, one frame late and unseen.
- `WaterEffects` are scene-wide defaults a plugin can replace; per-body effects weren't needed.
- Small drops against bright water mostly show what they reflect, so splashes read faintly from far; kept physical.
- Rain rings: drops landing are counted per grid cell into a smoothed rain rate, so cover needs no test (water under a
  roof gets no drops), and the surface draws procedural rings at that rate.
