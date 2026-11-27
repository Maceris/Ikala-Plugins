# Preview host

The workbench shows the open file through a `PreviewHost`. A host draws what every viewer shares: a toolbar with
the kind of viewer, the file's name and a Capture button, then the viewer's own controls on the same line, and the
viewer's content below. The properties panel shows the viewer's details. A `Viewer` supplies only its content, its
controls, its properties and, if it can, a capture. Showing a viewer closes the one before, which releases what it
owned, like its texture. Opening a file brings its panel's tab to the front.

There are two hosts: the Viewer panel's, for images and containers, and the 3D view panel's, for scenes. An image and
a scene can be open at once, so an image shown in the viewer can be put on a material in the scene. The properties
panel shows whichever of the two panels shows, or the one opened or clicked last when both do.

## Viewers

| Viewer | Shows | Notes |
|---|---|---|
| `TextureViewer` | images and DDS textures | channels, mip level, exposure, 3x3 tiling, the pixel under the mouse, Measure |
| `ContainerViewer` | `.ika` containers | header, metadata, sections with checksums, problems; a picked section's bytes in the properties panel; a mesh or material section shown in the 3D view |
| `Swatch` | pixels made on the CPU, like a graph node's output | a texture viewer without its toolbar controls; new pixels replace the old ones and keep the view |
| `SceneView` | models, materials, looks, shapes, context scenes, the particle stage | its own preview view; see below |

## The 3D view

`SceneView` draws its scene into a preview view of its own (see Ikala-Graphics `docs/design/Preview Views.md`), so the
world and the renderer's settings are left alone. Its toolbar picks the scene, the view mode, supersampling, the
lighting preset and A/B; its properties hold the scene's details, lighting, overlays, selection, materials, level of
detail and export.

What it opens:

- **Models** read by assimp, as before.
- **`.material.yml` and material containers** on a sphere, through the material editor's file loading.
- **Mesh containers** standing on a floor.
- **Block looks** (`looks/block/*.kvt`), in the first context scene with a place for the block.
- **Scenes from the list**: the shapes and shader ball to try materials on, the particle stage, and the context
  scenes.

## The graph panel

The Graph panel holds Ikala-Graphics' graph editor on a small demo set of math and color nodes (constant, color,
checker, add, multiply, lerp, a Gray conversion and an output), opening and saving `*.graph.yml`. It lets the editor
be used and checked in the workbench before texture graphs (E5) give it real nodes; their node set replaces the
demo's. The properties panel shows the selected node's parameters, and the demo's check flags unlinked inputs and a
second output.

## Decisions

- **The container viewer replaced the asset inspector window.** Browsing was already the workbench's job, so
  `.ika` files open from its file list like any other asset, and the sample containers are written from the Files
  panel.
- **Captures are drawn on the CPU, not read back from the screen.** A texture capture shades the image's stored
  pixels the way the view does (channel, exposure, sRGB decoding and encoding, normal rebuild, tiling) and writes an
  8 bit PNG at the image's own size. It doesn't depend on the window's size or zoom, so check scripts get the same
  file every run. Textures whose pixels can't be read back, some DDS formats, can't be captured. The 3D view's capture
  is the preview's own image, once it has drawn 16 frames.
- **Measure is the alpha-weighted average linear color, with its luminance.** sRGB images are decoded first, so the
  number compares with measured albedos (most real materials lie between 0.04 and 0.9). It replaces the albedo
  targets and hover checks the plan dropped: a number to read when wanted, not a judgment.
- **Swatches are linear half floats,** so values above 1 and below the 8 bit step survive, and the view encodes them
  to sRGB to show.
- **The 3D view is drawn every frame it shows,** as the world was: materials, textures and lights change in many
  places, and the editor draws nothing else.
- **Lighting presets are files** (`data/lighting/*.lighting.yml`): a sun, a sky (physical, ambient or none), point
  lights placed from the middle of what is shown, and a fixed EV100, so two assets compare under the same light and a
  new preset needs no code. The pre-exposure follows the preset's light. Point lights need a range: one left to follow
  its intensity barely reaches past a meter at a lamp's candela.
- **The plain sky follows the ambient light.** The renderer draws a sky with neither a map nor the physical sky at a
  fixed color, which blew out to white at a cave's exposure; the 3D view sets its preview's plain sky to the ambient
  light's luminance, so no sky light means a black sky. The world's plain sky is unchanged.
- **A/B pins a frozen image.** B is a capture loaded back as a texture, so it keeps the camera and lighting it was
  pinned with: pin, change something, compare. A live B, a second preview holding the pinned version, is not built.
- **Game content is read as files.** Factory-Core is a soft dependency: when it is loaded, `BlockContent` reads looks
  with its KVT parser and finds IDs with its content-folder rules, from the installed content folders and the folder of
  every look file opened (the editor doesn't load the game's content plugins itself). A look's model, texture, normal
  map and render material go into an ordinary imported scene, so the scene's own texture loading does the rest. Blocks
  that can't be found are gray cubes, named in the status. No hook in the game is needed, and the look hub (E8) edits
  the same files.
- **Context scenes are files too** (`data/context/*.context.yml`): cells on the 1 m grid, filled or one by one, each a
  block by ID or `subject`, the block looked at. They are listed only while game content can be read.
- **Small textures are pixel art.** Imported scenes' textures of 128 pixels or less are sampled sharp, as blocks are in
  the game.
- **The workbench has no tool bar of shortcuts.** The path and file buttons are in the Files panel, scenes are picked in
  the 3D view, and the top bar holds only Back, Reset layout and the status.
