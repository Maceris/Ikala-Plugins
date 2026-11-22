# Architecture

Ikala-Graphics is a Vulkan-only renderer with a fixed pipeline and open content. It owns the data structures,
APIs and render passes; other plugins supply assets and place them in scenes. OpenGL was removed entirely for
macOS: Apple stopped supporting it, so macOS runs through MoltenVK.

## Scope

Graphics is responsible for:

- Meshes, textures, materials and their lifetimes, in a binary asset format it mandates (`.ika`).
- Scenes: instances, lights, cameras, and the GPU-driven drawing of all of them.
- Runtime processing of supplied content: baking sections, building LOD hierarchies, streaming.
- A fixed set of render passes, configured by whichever plugin owns graphics (a game client, the editor).
- Post-process filters that follow its specification, which plugins may supply.
- The retained-mode UI layer over IkGui, which plugins extend.

Graphics is not responsible for game concepts: blocks, chunks, items, recipes or world generation. The game talks
to it in meshes, placements, heights and other plain data. It also offers no plugin-defined render passes: the
pipeline is "take it or leave it", which is what lets every pass rely on every other pass's outputs.

Constraints the design was built around:

- Plugins load, unload and hot-reload at runtime, each with its own class loader.
- Rendering is single-threaded; uploads and requests may come from any thread.
- The world is procedural, data-driven and editable, with very little static data coarser than one mesh.

## Layering: everything public, stability by package

With one graphics API there was nothing left for a frontend/backend split to abstract, so it became layers:

| Layer | Contents | Stability |
| --- | --- | --- |
| Public API | `AssetRegistry`, `Scene`, materials, `RenderConfig`, filters, handles | Stable: avoid breaking changes |
| Renderer | GPU scene, section baking, LOD, culling, the fixed passes | Public, may change between versions |
| Device | `VulkanState`, allocators, `DeletionQueue`, `PerFrameData` | Public, may change between versions |

- Visible is not the same as stable. Only `com.ikalagaming.graphics.vulkan.*` is internal; nothing is hidden just
  to hide it.
- Raw handles are exposed freely (a `Texture` carries its `VkImage`, view, bindless index, format and layout).
  The real risks in Vulkan are lifetime and synchronization, so the safe path is made the easy one: destruction
  goes through `DeletionQueue`, uploads through the upload path.
- Single-implementation interfaces (`Instance`, `State`, `Pipeline`) were dropped once OpenGL left.
- Vulkan types stay out of public API signatures, because `javac` can need the types of other overloads or
  supertypes of a method you call. A plugin that wants raw Vulkan adds `compileOnly "org.lwjgl:lwjgl-vulkan"`;
  LWJGL is shared, so it costs nothing at runtime. The API's real value is correctness (staging, layout
  transitions, bindless registration and deletion done for you), not avoiding a dependency.

## Plugins hand graphics data, never code

Every resource has an owning plugin, and plugins hand graphics data, never code. This is what makes unloading and
hot reload safe.

**Why:** Ikala-Core gives each plugin its own `PluginClassLoader`. If graphics keeps any object whose class came
from an unloaded plugin (a lambda, a listener, a subclass, a callback), that class loader can never be freed.
Every hot reload then leaks Metaspace, and the old plugin's code can still be called.

- Requests carry graphics-owned types: byte buffers, and records whose classes belong to Ikala-Graphics.
- Calls return handles (an index plus a generation), not objects. A handle whose owner unloaded fails cleanly
  instead of crashing the GPU.
- A plugin works through `GraphicsManager.forPlugin(name)`. A context belongs to one loaded instance of the
  plugin, with resources tracked under a key unique to it: Ikala-Core delivers `PluginUnloaded` asynchronously,
  so a reloaded plugin may already be running when the old instance's unload arrives.
- Completion is reported by handle state or a `CompletableFuture`, never a stored plugin callback.
- `PluginDisabled` removes the plugin's instances, lights, filters and UI; `PluginUnloaded` releases its meshes,
  textures and materials through `DeletionQueue`, which waits for frames in flight. Dependents unload first, so
  a placement of another plugin's mesh (which holds a reference) is gone before the owner goes.
- Baked output holds references to its sources, so a stale section never points at a freed bindless slot.
- Plugin asset data is untrusted GPU input: indices and material ids are bounds-checked at load, since a bad
  index can lose the device.
- Plugins name each other's assets by namespaced string (`plugin:asset`), resolved to handles at load, which keeps
  saves and hot reload correct.

## Threading

One render thread records all Vulkan commands; any thread can make requests, and nothing blocks the frame.

- Requests (upload, place, remove, rebake, rebuild LOD) go into a multi-producer queue drained once a frame.
- Worker threads decode data straight into a persistently mapped staging ring; the render thread only records
  copies and barriers. One queue is assumed (macOS), so copies go on the graphics queue at the frame's start.
- Resources move `PENDING` to `RESIDENT` to `RETIRING`. The renderer skips anything not resident, so a slow upload
  shows as a delay, never a stall, and a rebuilt resource replaces the old one only once resident.
- Heavy CPU work (baking, simplification, voxelization) runs on worker pools fed by priority queues.

## World coordinates: doubles on the CPU, camera-relative on the GPU

**Why:** the planned cubic-chunk world spans about a billion blocks in every direction. A float has 24 bits of
mantissa: at a million blocks out it steps by about 0.06 blocks, and at a billion by 64. Doubles were chosen over
integer section coordinates because the renderer isn't exclusive to voxel worlds.

- Positions are JOML `Vector3d`; a double stays well under a millimeter precise at a billion units.
- Render space is world space moved so the camera sits at the origin; the view matrix only rotates.
- Model matrices and light positions are made camera-relative in double precision, then converted to float.
  Verified by moving the whole demo a billion units away and getting the same frame pixel for pixel.
- Instance and camera positions are stored as a high and a low float; LOD worlds and instance buffers are
  written relative to the camera each frame.

## GPU-driven geometry

With many tiny meshes, the cost is draw calls, not triangles, so the GPU decides what to draw:

- One geometry arena of a few large buffers holds every mesh; materials live in a storage buffer that names
  textures by bindless index; instance data is a storage buffer with only changed entries uploaded.
- A compute pass culls instances and clusters (frustum, then Hi-Z occlusion on bounding boxes, which handles most
  cave and mountain occlusion without knowing any grid), picks LOD, and writes indirect draws. The CPU issues a
  handful of indirect draws per pass whatever the object count.
- One fixed global binding model: every pipeline, plugin filters included, shares the bindless textures and
  per-frame data, so a plugin shader can use any engine texture by index without its own descriptor pools.
- Path tracing is kept possible: culling only shapes the raster draw lists, the instance table holds every
  instance, and the arena and mesh table are readable by device address.

## Platform: MoltenVK

macOS runs through MoltenVK, so the design avoids what it lacks (as known when written; check current MoltenVK):

| MoltenVK limit | Design response |
| --- | --- |
| Portability extensions needed | `VK_KHR_portability_enumeration`, handle `VK_KHR_portability_subset` |
| No mesh shaders | Cluster culling and LOD selection in compute, then indirect draws |
| No geometry shaders, no wide lines | Debug shapes on instanced or compute paths, 1 pixel lines |
| Lower descriptor-indexing limits | Query the bindless array size; never hardcode it |
| Maybe no `drawIndirectCount` | LOD culling needs it now; the fallback is fixed-size lists with culled slots at instance count 0 |
| No ray tracing | A path-traced mode would be Windows and Linux only; volume cone tracing is the cross-platform GI |
| Single queue | Uploads on the graphics queue at the frame's start |
| No min/max or comparison samplers | Not used |

## Asset pipeline

The asset editor (Ikala-Asset-Editor with Asset-Converter) runs the same graphics code as the game: it loads
binary assets through the runtime loader and builds LODs with the runtime builder. Only importing raw formats
(Assimp) and writing binary files are editor-only.

- One implementation of each step (loader, validator, simplifier, DAG builder), so an asset that looks right in
  the editor looks right in the game. The workbench doubles as the test bed for LOD, lighting and particles.
- `.ika` is a versioned container: a header, a table of sections with FourCC tags, their own versions, a required
  flag and CRC-32C, and 16-byte-aligned little-endian payloads. Readers skip unknown optional sections and reject
  unknown required ones; runtime caches key on the versions.
- Asset files are read into memory, not memory-mapped, because Windows can't overwrite a mapped file until the
  garbage collector unmaps it. Revisit with arena-scoped mappings on Java 22.
- Texture compression is decided at export; check BC support on Apple Silicon before relying on BC alone.

## World generation is a test source, not a dependency

The factory plugin's generator is deterministic, so it can make hard cases and reproducible scenes on purpose,
but the dependency runs game to graphics only. Graphics tests use synthetic fixtures (the gate benchmark's
recipes), and the Worldgen Lab is debug tooling inside the game plugin, run in the asset editor.

## Debug drawing

`context.debug()` draws lines, arrows, boxes, spheres, cones, frustums and labels, either for one frame from any
thread or persistently with handles released at unload, in a stage that costs nothing when off. Graphics Debug
toggles the built-in views (lights, bounds, cascades, normals, LOD, volumes, occupancy).
