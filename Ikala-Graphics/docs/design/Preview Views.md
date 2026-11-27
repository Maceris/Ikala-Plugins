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

A view that asks every frame, like the asset editor's 3D view, never settles, so a capture doesn't wait for the
preview to settle: it waits its own 16 drawn frames from when it was asked for.

## What a preview sees

All placed models live in one instance table. Each instance carries a view ID in bits 16-31 of its flags, 0 for the
world, and the cull shader keeps only the instances of the view being drawn. Previews' instances are also left out of
the world's ray queries and its sky occlusion map. A preview's scene shares the world's material cache, so the GPU's
one material list covers both.

Other things that belong to a view carry its ID the same way, so a tool can show in a preview what it used to show in
the world:

- **Lights.** A preview's scene has its own light registry; `previews().lights(preview)` places point and spot lights
  in it.
- **Debug shapes.** `debug().add(shape, preview)` draws a shape in that preview only. A preview runs the debug stage
  while `setDrawsDebug` is on, which also draws its scene's own debug visualizers (normals, wireframe).
- **Level of detail worlds.** `lods().add(dag, preview)` puts a DAG in a preview. The LOD renderer's prepare step
  draws, and streams for, only the drawn view's worlds, and the preview pipeline runs the LOD bake and cull stages.
  Instances of LOD models stay the world's.
- **Particles.** The GPU particle pool simulates and draws in one view at a time, the world's unless
  `previews().particles(preview)` hands it to a preview, until that preview closes. Two views can't share it: the pool
  follows one camera and its particles have no view of their own.
- **Filter views.** `PreviewView.setFilterView` shows a g-buffer view in place of the lit scene, like roughness, from
  the scene's own `filterView`, so the world's choice is untouched. The roughness and metallic views read the material
  table, as the lighting pass does, with a texture's values in their place where the g-buffer holds them.
- **Supersampling.** `setSupersampled` turns TAA off for the preview's scene and draws it up to twice as large each
  way, as far as the render targets allow; the copy into its texture filters it down. A view as large as the window
  gains little.

## What a preview leaves alone

`RenderConfig.preview` builds a pipeline without the stages that stream, cache or simulate the game world: sections,
occupancy, deformation, water and volume bricks. Those systems keep camera-anchored state (the sun's cached cascades,
the sky map, the occupancy clipmap, fog history) that a preview's camera would push out of place. Level of detail and
particles run only for what belongs to the preview, above. A preview's sun is drawn whole, without the cache. TAA and the depth pyramid remember which view
they were last built from, so a preview never blends or occludes with another view's history. Previews use a fixed
exposure so two compare under the same light.

## Known costs

- The sky's image-based lighting is one global set; previews with different skies make it recompute when the drawn
  view changes.
- A preview larger than the window's render targets is drawn at the targets' size and stretched.
