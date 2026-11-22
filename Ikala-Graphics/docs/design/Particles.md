# Particles

Particles are for visual effects (smoke, fire, sparks, dust, magic), weather and water spray: nothing game logic
reads back, so they live entirely on the GPU.

## API: definitions are data

`graphics.particles()` is owned per plugin like `media()`. Effects are built in Java or loaded from YAML a plugin
ships, and reload in place. A definition is a list of modules (spawn rate or bursts, lifetime, velocity cone,
forces, size, color and alpha over life, flipbook, collision, lighting, emissive). `spawn(effect, position)`
returns an `EmitterHandle`; a plugin's emitters go when it unloads. Being data keeps unload safe (see
[Architecture](Architecture.md#plugins-hand-graphics-data-never-code)).

## Simulation and drawing

- One GPU pool with a global cap and per-effect caps; a compute pass spawns, updates and compacts into indirect
  draws. Far effects spawn fewer; emitters out of view or range are skipped.
- Collisions use the depth buffer on screen, the occupancy volume near the camera (things behind it too) and the sky
  occlusion map (rain and snow under roofs). Particle collisions see only baked sections through the occupancy
  volume, not placed instances; weather sees instances through the sky map.
- **Each particle is lit once a frame in compute** (sun through the cascades, sky with occlusion, clustered lights
  with shadows), so large overlapping billboards stay cheap. Billboards draw through the order-independent
  transparency, so nothing is sorted.
- **Volumetric particles:** smoke is splatted as soft balls into the fog's froxels and lit as fog, with
  self-shadowing toward the sun and sky in closed form. Back-lit thick smoke shows a bright rim and a dark middle,
  as real smoke does; billboards can't shade themselves. This reuses the fog (Frostbite's approach).
- The color pass is the cost, not simulation (0.01 to 0.03 ms each step): 98 smoke puffs 0.8 to 3 m across, seen
  from 5 m, add about 2 ms of fill. A per-fragment early out made no difference, because the cost is fragments
  launched and blended, not shaded. The fix, when needed, is lower-resolution drawing of large particles or the
  zero transmittance depth.

## Weather

Rain and snow fill a box that follows the camera, wrapping around in it. None spawn under cover; landing on the
highest thing in the sky occlusion map counts as a hit, and splashes spawn from recorded hits in their own pass.

Rain needs about 20,000 drops a second in a 24 m box to read at all. Drops are 3 cm wide and scatter evenly: a drop
shows the light from all around it, and scattering forward made rain vanish with the sun behind the camera.
Streaks count as fully reactive for TAA.

Emitters needing a box, the camera or splashes hold one of 15 links, a per-frame table their particles look up.
The water's effects take links 16 to 19, so plugins keep all 15.

## Moving emitters

For character effects on bones and weapons. A frame's spawns spread along the emitter's move since last frame (not
past 4 m, a teleport, or its first frame). `moveLine` spawns along a segment and so sweeps between frames; real
ribbons can wait. `orient` turns the spawn box and direction, not acceleration. An effect's `follow` share of the
emitter's move is applied through its link (translation only); `inheritVelocity` needs no link.

## Particle viewer

A workbench preview on a stage with a floor, walls, switchable fog and glass in front of and behind the emitter,
for checking an effect alone. Its Measure button repeats a run exactly (fixed steps, seeds, camera). A full effect
editor comes only if prototyping calls for it.
