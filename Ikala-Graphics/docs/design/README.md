# Ikala-Graphics design notes

Why Ikala-Graphics is built the way it is: the decisions behind each part, and the reasons for them. The
[Ikala-Graphics README](../../README.md) says what exists and how to use it. These notes say why it was built that
way, so later work doesn't undo a choice without knowing what it was for. Plans and loose ends live in the
"Ikala-Graphics: Vulkan-Only Architecture" Claude doc, and git history has the step-by-step record.

| Note | Covers |
| --- | --- |
| [Architecture](Architecture.md) | Scope, layering, plugin ownership, threading, world precision, GPU-driven drawing, MoltenVK, the asset pipeline |
| [Terrain and LOD](Terrain%20and%20LOD.md) | Section baking, face groups and coverage masks, the runtime LOD DAG, transparency and volume LODs |
| [Lighting](Lighting.md) | Clustered lights, shadow budgets and caching, sky light, the occupancy volume, TAA, shadows through glass |
| [Particles](Particles.md) | GPU particles, lighting once per particle, volumetric smoke, weather, moving emitters |
| [Water](Water.md) | Water tiles, the bio-optical model, waves, flow, sides, underwater, spray and rain rings |
| [Retained UI](Retained%20UI.md) | The retained layer over IkGui, layout, themes, specs, scripting, ownership |
| [Preview Views](Preview%20Views.md) | Small scenes drawn into textures, one a frame, and what they leave alone |
| [Graph Editor](Graph%20Editor.md) | Editing node graphs whose types the caller gives: linking, history, the file format |
| [Performance](Performance.md) | Frame pacing, GPU memory, garbage collection, streaming, and the rules each found |

Every note keeps to Graphics' own rule: Graphics knows nothing about blocks, items or other game concepts, so a
scene like Bistro works the same as a voxel world. Lotomation's side (which cells hold water or snow, block looks)
is in Lotomation's `docs/design`.
