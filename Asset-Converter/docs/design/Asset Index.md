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
game's content through Factory-Core, and are registered only while it is loaded: looks, blocks (their game material,
and the look of the same ID), game materials (their parent) and every other content file. The first type matching
a file wins; files none matches are of type `file`.

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
