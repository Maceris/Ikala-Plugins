# Preview views

A preview view is a small scene the renderer draws into its own texture, for the GUI: a material on a sphere, a model
on a floor. Tools get them through `context.previews().create(width, height)`; the asset editor's previews,
thumbnails and texture swatches are built on them.

## One preview a frame, on frames without the world

The renderer draws one scene per frame. Every stage writes one shared set of per-frame buffers (camera, lights,
culling lists, draw commands), so a second scene recorded into the same frame would overwrite the first before the
GPU ran it. Drawing several scenes a frame would mean giving every stage per-view buffers and targets, about 30
stages' worth of change.

Previews were wanted for the editor, which draws no world of its own, so a cheaper rule was chosen: on a frame whose
pipeline draws no world, the renderer draws one preview that wants frames instead, through the normal stages, then
copies the final image into the preview's texture. A preview wants frames only after something it shows changed
(`requestFrames`, about 16 frames for TAA to settle), and keeps its last image otherwise. When several want frames,
the one drawn longest ago goes next. While the world is drawn, previews wait.

## What a preview sees

All placed models live in one instance table. Each instance carries a view ID in bits 16-31 of its flags, 0 for the
world, and the cull shader keeps only the instances of the view being drawn. Previews' instances are also left out of
the world's ray queries and its sky occlusion map. A preview's scene shares the world's material cache, so the GPU's
one material list covers both.

## What a preview leaves alone

`RenderConfig.preview` builds a pipeline without the stages that stream, cache or simulate the game world: sections,
level of detail, occupancy, deformation, particles, water and volume bricks. Those systems keep camera-anchored state
(the sun's cached cascades, the sky map, the occupancy clipmap, fog history, the LOD cut) that a preview's camera would
push out of place. A preview's sun is drawn whole, without the cache. TAA and the depth pyramid remember which view
they were last built from, so a preview never blends or occludes with another view's history. Previews use a fixed
exposure so two compare under the same light.

## Known costs

- The sky's image-based lighting is one global set; previews with different skies make it recompute when the drawn
  view changes.
- A preview larger than the window's render targets is drawn at the targets' size and stretched.
