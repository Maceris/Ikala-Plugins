# Terrain and LOD

## Sections are baked into meshes

Each 16^3 section is baked into one mesh with hidden faces removed. To graphics, the world is a few thousand static
meshes with bounding boxes and no grid.

**Why:** instancing every block doesn't scale. A modest view distance holds tens of millions of blocks, and
per-instance culling can't remove the individual faces between neighbors.

| The game (knows the grid) | Graphics (knows meshes) |
| --- | --- |
| Which block is in each cell, its rotation and neighbors | CPU-side copies of bakeable mesh data |
| Which faces of each placement are hidden | Merges placements into one compact GPU mesh on a worker |
| Bakes static blocks; moving ones (doors, machines) are instances | Stores, culls and draws the results like any mesh |
| Triggers rebakes on edits, boundary neighbors included | Swaps in the new mesh only once it is resident |

### Face groups are derived, not authored

When a mesh is registered, graphics labels each triangle lying in a face plane of a given box (within a tolerance,
inside that face's rectangle) with that direction; every other triangle is "none" and is never removed.

- The box is a parameter ("against [0,1]^3"), so graphics needs no notion of a block.
- Modders never write face groups: grass, people and animals come out as all "none" by themselves. An optional
  override section in the format covers edge cases such as a decal set just into a face.
- Animated and skinned meshes are never baked, so their face groups don't matter.
- Results are cached on disk by a hash of the mesh bytes.
- A material can name top and bottom variants (grass top, dirt bottom). Each face takes the variant for the way it
  points in the world after rotation; a triangle on no face of its box goes by its normal. Coarse LOD levels
  inherit them, so far terrain can be textured by projection with one texture per material.

### Coverage masks decide hidden faces

Each labeled face is rasterized onto a 16 x 16 grid, a 256-bit mask. A face is hidden when its mask is contained
in the neighbor's opposite mask and the neighbor's material blocks sight.

| Mesh | Its faces can be removed | It hides neighbors' faces |
| --- | --- | --- |
| Full stone cube | All 6 | All 6 |
| Crossed-quad grass | None | None |
| Slab | Bottom and half-height sides | Bottom only |
| Leaves (cutout) | All 6 | None: not opaque |
| Animated door | Not baked: an instance | Not baked |

- Partial faces need no special case: two slabs side by side hide each other's half faces.
- Rotating a mask is a lookup table, so baked placements use the 24 axis-aligned rotations; anything else is an
  instance.
- Opaque faces hide neighbors; translucent faces follow their own rules (glass next to glass); cutout faces hide
  only the same cutout material, so there are no faces inside a blob of leaves but stone shows through their holes.

### The compose API is one call per section over flat arrays

So crossing the plugin boundary stays cheap. `faceMasks` gives 6 bits per placement; `userData` gives a few bytes
per placement written into vertices and not interpreted by graphics (tint, sway). Output has opaque, cutout and
translucent buckets; translucent geometry is sorted.

The baked vertex is 24 bytes: int16 positions in 1/1024 steps from the section origin, octahedral normal, UV,
bindless material index and user bytes. Its first 8 bytes read as R16G16B16A16_SNORM, for ray tracing builds.

If composing ever becomes the bottleneck, the next step is a generic grid helper (a 16^3 palette array plus a
palette-to-mesh table) that also culls neighbors. Rotations, multi-block structures and connected textures still
belong to the game.

### Edits are drawn in the frame they're made

An edit rebakes its section (and a neighbor if on a boundary). A section bakes in about 0.4 ms, so a rebake near the
observer is baked on the render thread; farther ones bake on workers, with the old mesh visible until the new one
is resident.

## Runtime LOD: a Nanite-style cluster DAG built at runtime

Baked sections are the leaves of a cluster DAG built at runtime; large meshes and whole scenes (the asset
workbench, LOD models) use the same builder and format.

- **Simplifier** (Liu, Zhang and Yuksel 2025): virtual edges merge a section's disconnected blocks; materials go
  up the levels by correspondence rather than UVs; collapse costs are weighted by visibility from hemisphere rays.
  Far groups bake texture pages where texels stay under their error and blend materials per vertex otherwise.
- **Grouping by graph partition, not a staggered spatial grid:** both were built in phase 3, and the grid's groups
  ranged from 1 to 187 clusters and locked five times as many corners.
- **Measured, saturated errors:** the simplifier's quadric estimate under-bounded groups by 2 to 8 times, so it only
  orders collapses. The measured, saturated error is what lets each cluster pick its cut alone.
- **Region cells (LodStore):** one DAG over the loaded area would need a near-global rebuild on every load. Cells
  lock their boundary planes, so the planes' polylines stay at full detail until a parent spans both sides: a
  floor on far cuts. Three attempts to lower it are parked as patches, and simplifying the planes was dropped
  (2026-10-25). Coarse far data can come from a plugin as leaves that carry their own error.
- **Drawing:** 16-byte vertices on one int32 grid of 1/1024 steps from the world's origin, so a vertex two clusters
  share encodes the same in both (no cracks from encoding); flat normals from screen-space derivatives; textures
  projected along each triangle's main axis. The GPU picks the cut per cluster with Nanite's parallel rule
  (`LodCullMath` mirrors it in Java), culls it with the instances, and draws with `drawIndexedIndirectCount`.
- **Live worlds** (`Lods.fromSections()`, `LiveLod`): one per plugin, fed by its section bakes. An edit draws in the
  frame it's made through the section's own blocks, and reaches the LOD seconds later.
- **Streaming and caching:** units are cluster groups, wanted while their error projects over half the threshold,
  parents first; a world draws once its wanted units are in, or after 2 s. Cells and whole DAGs are cached on disk
  by content hash; bump `LodStore.BUILDER_VERSION` whenever building changes its output.
- **The gate** (`-Dikala.benchmark.gate=true`): GPU p95 within 8 ms, no cracks in the checked cuts, edits drawn
  within 0.25 s, and a cache reload that builds nothing. First builds vary up to 2 times run to run, so compare
  back to back.
- **Judge LOD looks at about 4 to 10 px of error;** 32 px is too coarse.

Tried and set aside (2026-10-13): free section borders, error-budgeted halving and cut-out leaves in a second live
world. Lossy per-section levels were disproved, since they crack. A 2 to 4 px threshold is nearly free if memory
ever needs it. The graphics-side coarse rings were shelved (2026-10-15).

## Transparency, cutout and sparse geometry become volumes far away

Surface simplification only works where there is a surface to approximate. Glass in front of stone, scattered
blocks in a field and distant grass all behave statistically like a participating medium, so content that isn't a
surface at viewing scale switches to a coarse volume. Prior work: Loubet and Neyret's hybrid mesh-volume LoDs
(Eurographics 2017) and SGGX microflakes (Heitz et al. 2015).

- Opaque, cutout and translucent geometry are simplified in separate hierarchies; virtual edges never join glass to
  stone. A glass pane is planar, so it simplifies well alone.
- Cutout turns opaque at the levels where per-pixel holes stop showing, with coverage-preserving alpha mips below
  that: a distant canopy as a solid blob looks right.
- A group that can't keep its area or coverage at the target error becomes a volume (extinction by color, albedo,
  a normal distribution). Volumes exist only at distance, so they're small on screen: each rasterizes its box and
  ray-marches a few steps through a low-resolution 3D texture, depth-tested and blended. No general ray tracing.
- The triangle voxelizer is shared with the lighting occupancy volume.

As built: past 96 m, a section's cutout and translucent blocks draw from a 16^3 brick of 1 m voxels (`VolumeBricks`).
Cutouts became solid voxels drawn as block faces, since far canopies read as solid; see-through voxels go through
the transparency volume. Bricks cast sun and sky shadows for sections whose meshes are off the GPU. Farther,
coarse bricks of 2 and 4 m voxels stand in for 8 or 64 sections. Inside LOD groups, parts finer than the group
become coverage volumes (SGGX, drawn as shells when small on screen), so far foliage keeps its outline. Meshes only
needed nearby stay off the GPU and region-cell bodies page to disk, which is how radius 96 loads in under a minute
from the cache and walks within the GPU budget.

Translucent surfaces cast no opaque sun shadow and don't write depth; their shadows go through the moment shadow
maps (see [Lighting](Lighting.md#shadows-through-glass)).
