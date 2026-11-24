# Lighting

Direct lighting is fully dynamic, with nothing baked into blocks. Lighting stays physically based: caves come out
very dark, and that is accepted as what reaches them. Artistic touches come only at the end.

## Physical units, pre-exposed

Light is given in physical units: the sun, moon and stars in lux (full sun 100,000), point and spot lights in candela,
and emitting surfaces, particles, media, the ambient sky and the sky's glow in cd/m^2 (a screen a few hundred).
Exposure is a camera's, in EV100 (a sunny day about 14.8), or stops of compensation while it adapts.

Half floats can't hold daylight and moonlight in the same units, so every light is multiplied by the scene's
pre-exposure as it is uploaded, and the exposure divides it back out (`LightUnits`, `Scene.preExposure`). At the
reference pre-exposure, 3/100,000, full sun comes to 3 on the GPU, the scale the shaders were tuned at; a game raises it
for a night lit by the moon. Things kept across frames are stored at the reference scale and scaled when drawn: a level
of detail bake's emission page is multiplied by its page material's emission, which is itself pre-exposed. Particles
take their light when they spawn. Light ranges and their cutoff are measured at the reference scale, so a light reaches
as far whatever the pre-exposure.

## Many lights: clustered culling

Players will place hundreds of torches, lamps and glowing machines. The view frustum is split into a 3D grid of
clusters; a compute pass assigns each light to the clusters it touches, and deferred lighting reads only its
pixel's cluster list. Lights are owner-tagged handles like instances; the game decides which blocks emit light.
Light culling tests every light against every cluster; cull against the view first if counts grow.

## Shadows: a budget, and caching what doesn't move

- **Point and spot lights:** a per-frame budget of cube maps (`GraphicsSettings.pointShadowBudget`, 8 by default),
  spent through a lightcut-style tree on the most important lights, with lights close together sharing a map. What
  doesn't move is cached per face and redrawn only when something in reach changes; what moves draws every frame
  into a second layer.
- **Sun:** the near cascade follows the view; the middle and far cascades stay put in the world and redraw only when
  they step, the sun moves 0.25 degrees, or something inside changes (`ShadowInvalidation`), with moving casters in
  a per-frame layer.
- Lights past the shadow budget would shine through walls, which a block game shows at once, so occupancy rays stop
  them (below).

## Sky light underground

A top-down sky occlusion map of what doesn't move dims sky light under cover, down to
`GraphicsSettings.skyLightFloor`, and stands in for the sun's shadow past the cascades. The sky (a skybox, an HDR map
or the physical sky) lights the scene through a prefiltered cube and spherical harmonics, with occupancy AO along
bent normals and short reflection rays. The physical sky's strength is separate from ambient. Caves whose ceiling
is beyond the view distance get daylight; see the open question on a sky-occlusion input.

## Occupancy volume

Three toroidal clipmap levels of 128^3 voxels (1, 2 and 4 m), voxelized on the GPU from baked meshes and far
sections' bricks. It gives large-scale AO and short visibility rays toward the 8 strongest lights at each pixel
without a shadow map per light. A changed box says what changed (a surface, level of detail, a swap within its
error), and the occupancy volume takes only surfaces, since it is voxelized from sections alone.

The planned next steps (stochastic lightcuts for visibility, then GI by cone tracing through this volume) are in
the plan doc.

## Temporal anti-aliasing

TAA runs on the final HDR color. The planned visibility filter for stochastic lightcuts is a separate pass on a
single channel, because the visibility ratio holds no texture to blur, and the deterministic sun and point shadow
maps stay out of both.

## Shadows through glass

Moment shadow maps for see-through casters, as in Smolder (Mueller 2026): per sun cascade, translucent maps at
1024^2 (RGBA32F moments of depth weighted by absorbance, RGBA16F absorbance per color plus its average),
reconstructed with Peters and Klein's 4-moment Hamburger bound, so a receiver between two panes takes only the
front one. 32-bit moments, because 16-bit needs the optimized quantization; about 72 MB.

- A pane passes (1 - a) + a c per color, the same as the transparent pass shows white light through it; far
  bricks' see-through voxels cover 1 - exp(-extinction * length) the same way, up to their first solid voxel.
- One culled list of translucent meshes in any cascade, drawn into each cascade's maps additively; the near
  cascade every frame, cached ones only when they redraw; nothing while the scene has no see-through material.
- Surfaces, fog, particles, bricks and the water's surface all take the transmittance, so fog behind stained
  glass is colored.
- Under water, the light stage dims the sun along its refracted path by the water's extinction and the sky by
  (a + b_b) / 0.8 per meter, from the water map. Past the water map (128 m) bottoms aren't dimmed on the light's
  way down; far bottoms are mostly hidden by the view path anyway.

## Transparency costs

- Each transparent stage is timed on its own, behind the water too.
- Zero transmittance, as in AVBOIT: a translucent surface, particle or brick with under 1/256 of the light
  reaching it from in front is dropped before it is lit. `OitCullDepth` writes the scene's depth brought forward to
  where each tile's light runs out, and the transparent stage tests against it, so hidden layers aren't shaded.
- Lighting detail falls with the light in front of a surface, as Smolder lowers its lighting rate: AO looks only
  along the normal below 0.5, and below 0.3 nothing reads the occupancy volume. The first wall of glass keeps full
  detail. On the GLASS recipe this took the GPU from 10.0/12.4 to 4.8/5.4 ms.

## Conventions

y is flipped in the projection and depth runs 0 to 1. Colors are authored in sRGB and decoded exactly; lighting is
linear HDR in physical units, pre-exposed on upload, tone mapped once, with partial auto-exposure.
