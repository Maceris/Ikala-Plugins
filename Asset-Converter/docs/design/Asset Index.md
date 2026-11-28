# Asset index

The workbench keeps one index of every file under a list of roots (`AssetIndex`, run by `AssetIndexService`): each
file's type, its ID where it has one, what it references, what references it, which copy of an ID wins, and the
references that resolve to nothing. The browser, thumbnails and reference viewer read the same index.

## Roots are a list the user keeps

A root is a content folder, a plugin's `data` folder whose files have IDs (`textures/block/stone.ika` in the
Lotomation plugin is the texture `lotomation:block/stone`, and `overrides/<ns>/` replaces another namespace's file),
or any other folder, like an unshipped `art` folder. The editor only knows the installed plugins' content folders
on its own, and the game's plugin isn't installed in the editor, so the roots are a list kept in `gui.ini`
(`[Saved][Asset roots]`), started from the installed content folders and changed in the file list, which also jumps to
any of them. Content roots are read in the list's order, a later one's file winning over an earlier one's with the
same ID, as the game reads its folders.

The saved list is read when the workbench is first drawn, not when it is made: plugins are made before `gui.ini` is
read, so saved state read then is always empty.

## Types know files; the index knows none

An `AssetType` matches files, says where IDs of it live (`kind()` and `extensions()`), and reads a file's references.
`EditorTypes` are the editor's own: textures, models and render materials, the sources that make them
(`.material.yml`, model files), texture graphs and instances, shared parameter files and images. `GameTypes` read the
game's content through Factory-Core, and are registered only while it is loaded: water looks (`looks/water`, a
biome's water optics, naming nothing), block looks (their model as the game reads it, `cube_all` when none is named
and none for a fluid, whose shape is made in code; their texture and render material), blocks (their game material,
and the look of the same ID), game materials (their parent) and every other content file. The first type matching
a file wins, so water looks come before block looks; files none matches are of type `file`.

A reference is by ID, resolved through the content roots as `ContentFolders.find` does (last root first, its own
files or its `overrides/<ns>/`); by path, as a graph names its subgraphs, shared files and images; or a file a source
makes, looked for under the made type's folder in any content root. A source's made file not existing yet isn't a
problem; any other reference resolving to nothing is, as is a file whose references can't be read.

## Scans are incremental and in the background

A scan walks the roots (skipping `.git`, `.gradle`, `build` and `out`) and reuses an earlier snapshot's entry for a file
whose root, size and time are unchanged, so after the first scan only changed files are read. Each scan is saved to
`<cache>/asset-index.yml`, which the next run starts from. Scans run on one low-priority thread: soon after a
`WatchService` sees a change in any watched folder, and every 10 seconds anyway, as watching can miss changes on
some drives. A `Snapshot` never changes, so the UI reads the latest one without locks.

## In the workbench

The file list shows the roots (jump, add the folder shown, remove it) and how much is indexed; the Problems panel,
beside the Outliner, lists every problem, a click opening its file; and the properties of any open file end with its
references: its type and ID, every folder providing that ID with the winner marked, what it uses (missing ones in the
error color) and what uses it, each a link to the file.

## The Assets panel

The Assets panel (`AssetBrowser`) shows the index: the roots and their folders as a tree beside a `VirtualGrid` of
tiles or rows, so only what is in view is made. It finds files by type, namespace, folder and fuzzy text
(`AssetSearch`: the query's letters in order in the name or ID, runs, word starts and early matches first), and keeps
collections (`AssetCollections`) in the state folder: saved searches, which find again each time, and hand-picked sets
of files. A click selects a tile, which is what a slot's "use selected" will take; a double click opens it; a drag
carries its path as `asset-file`; right-click adds it to a set or takes it out. Tags wait for something to give them.

Textures and images have thumbnails (`Thumbnails`), made on the CPU in the background as their tiles are shown: tiled
3x3 at a whole-number scale, pixel-sharp, so seams show, or one tile shrunk when that is too big. A thumbnail's file
name hashes the file's path, size and time, those of what it uses, and a version, so a change makes a new one; using
one marks it, and the folder keeps under 64 MiB by dropping the least recently used. Tiles upload their thumbnail
when first drawn, and a new search lets go of those no longer shown.

Materials, models and looks are rendered (`ThumbnailStudio`): a second `SceneView`, never drawn in a panel, at the
thumbnail's size, supersampled and without the debug outline, shows one file at a time from a queue (the newest asked
first, a tile scrolled away dropping its request) and captures it into the same cache once its textures are in. A
thumbnail frames its subject, not the floor around it: a material's ball fills the square, and anything else is
framed whole, by its bounding sphere, so a box's corners aren't cut off. A model stands on its floor and a look is its
block. A render
material holds only how a surface reflects, its color coming from the texture a look pairs it with, so it is shown on
the sphere, untextured, in the average color of the texture of a look that uses it (found in the index): averaged in
linear light without cut-out pixels, opaque, and given as sRGB, as base colors are. A texture laid out for a block's
faces looks wrong wrapped on a ball; the average is a quick reference to the color instead. One no look uses is
shown plain. A render that fails isn't asked for again until its key changes.

## Slots

A slot (`AssetSlot`) is the widget for an asset reference, drawn in place by panels that draw with IkGui each frame,
like the material editor. It shows the file it names with its thumbnail, or the loaded texture, and its name; a file
that isn't there is named in red with a red outline. It takes a file dragged from the Assets panel, one found with
its picker (a fuzzy search of the index), or the Assets panel's selection (the `<` arrow), and only files of its
types in the index. Open opens the file in the workbench, and Clear empties the slot.

The material editor's four texture slots take textures and images. A texture file holds a normal map beside its
colors, so the normal slot names a texture file with the `#normal` mark, as looks do. The editor keeps the file each
slot names apart from the texture loaded, so a texture that is missing or fails to load still shows, and saving keeps
its name. A material saved on another drive than its textures names them by file name only, as no path leads there.

## The look editor

Opening a block's look shows it in its context scene in the 3D view, with a strip above the picture (`LookEditor`):
the look's model, texture and render material in slots side by side, and Save and Revert. Those slots take only files
with a content ID, which the look names. A change builds the scene again from the look as edited, keeping the camera
and lighting, so it shows before it is saved. Each slot's Details button picks what the Look section of the properties
shows: the model's ID and file; the texture's picture, and Open graph when a texture graph makes it; or the render
material's values, edited on the material the scene draws, so they show at once. Unsaved values are kept when the
scene is built again with the same material.

Saving writes only the look's keys that changed, in place, keeping its layout, comments and the keys as written (a
fluid's `model:"fluid"` is read as the default cube but stays as it is). An edited render material is written to its
`.ika`, and to the art source that makes it, if one does, with only the values that differ from the defaults, as
hand-written sources are. Thumbnails follow on save, as their keys change with the files. Opening a look teaches the
game's content lookup the index's content roots, so a look's IDs resolve in any of them, not only installed plugins
and folders files were opened from.

## The References panel

The References panel (`ReferenceViewer`) answers "what uses this" and "what does this use" for one file at a time,
picked with Show references on a tile's menu or in an open file's properties. It is a read-only graph in the graph
editor: the file in the middle, what uses it on the left, what it uses on the right, each a node with its thumbnail.
The middle node has a pin per reference, named by what the reference is (`model`, `texture`, `material`, numbered when
one repeats), as an input takes one link; the other nodes' pin says what kind of file they are. Users are sorted by
type, so looks sit together, and a side longer than 8 wraps into more columns, out from the middle; past 40 a side,
the toolbar counts the rest. A click on a node puts its file in the middle, Back returns to the one before, and Open
opens the middle file in the workbench. A missing reference is a red node titled with the name that failed to
resolve. A file several folders provide says in a bubble which it overrides, or which overrides it. The graph is
rebuilt when the index changes, and framed to fit the panel when a file is shown or the panel changes size.
