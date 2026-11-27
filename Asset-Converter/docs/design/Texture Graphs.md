# Texture graphs

Texture graphs (`com.ikalagaming.converter.texgraph`) are authoring sources that bake to the PNG maps the game
already reads, so the game, `BlockLooks` and resource packs need no change. They are edited in the workbench's
Graph panel, on Ikala-Graphics' graph editor, and baked from there or from `bin/bakeTextures`.

## Evaluation is on the CPU, in pixels

A graph evaluates on the CPU at its tile size, any width and height. A 16x16 tile is 256 pixels per node, so a large
graph re-evaluates well inside a frame. Sizes and parameters (noise size, tile size) are in pixels, the unit pixel
art is drawn in. Every plane wraps at its edges and every noise is periodic over the tile, so tiles never show seams.

Each node's outputs are cached under a key: a hash of its type and version, its parameters, the graph's size, its
seed, what it reads from outside (an Image node's file bytes), and the keys of what is linked into it. An edit
changes the keys downstream of it, and only those nodes run again. A node's seed is the graph's seed mixed with the
node's ID, so adding a node doesn't reshuffle the others.

Nodes must be deterministic. The same graph bakes the same pixels every run, which the golden hash test holds to.

## Pin types convert where it is safe

Scalar, mask, height, color (linear RGBA), texture, vector and coordinates. Scalars, masks and heights convert to
each other without asking (a mask clamps), a scalar becomes a gray color, and coordinates become a vector. A texture
becomes a color only through a Sample node, which the editor puts in when one is linked into a color. Color to
scalar never happens by itself: Luminance and Split say which.

## The Output takes faces, and the model says where they go

There is one Output per way a block texture is laid out: the same tile on every face, sides and ends, or sides,
top and bottom. Each takes base color, height, occlusion, roughness, metallic, emissive and mask for each of its
faces. Which tile a face goes on is read from the look's model, from the UV spans of its up, down and side faces,
and the bake refuses an Output whose faces don't fit the model, so the editor never guesses a layout. Normals are
made per face tile, so neighboring faces never bleed into each other.

Each map's file is fixed by what it holds: base color, emissive and mask are 8-bit sRGB, height is 16-bit, and the
ORM and normal maps are linear. A map is written only if something is linked for it, and a file whose pixels
wouldn't change is left alone, so baking a folder doesn't churn files that are already right.

## Normals match the old tool exactly

Height to Normal and the bake's normal maps do what the old `blockNormals` tool did, in the same float operations
in the same order: heights spread to 0..1 between the tile's lowest and highest point, holes (alpha below 128)
given the tile's mean, and a Sobel filter that wraps within the tile, green up. The Image node's brightness is the
stored sRGB values weighed as the eye sees them, as the tool took it, not a luminance of linear values, which
differs in the last bits and would change rounded normals. The migrated block graphs bake every existing color and
normal map pixel for pixel, and a test holds them to it.

## Swatches read what validation evaluated

The graph panel evaluates every node to find errors, so each node's swatch is that output, and showing swatches
costs nothing more. Swatches share one atlas texture with a 64 pixel cell per node, scaled to fit, and stripe values
outside 0..1, red above and blue below, so clipping shows. The shown node and an Output's maps are made at full
size. The seed grid evaluates the shown node under nine seeds with an evaluator each, so its cache doesn't fight
the graph's. Measure averages a node's output as linear albedo, with luminance as the overall figure, to check by
hand against physical references.

## Parameters are values a graph exposes

A graph's parameters (scalar with a range and step, color, switch, choice, texture, seed) have a group and an order,
so every details panel lists them the same way; instances, when they come, override their values. Parameter nodes read one
by name, one node type per kind, so their pins are fixed. The evaluator adds the value a node reads to its
parameters before hashing, so changing a parameter evaluates again only the nodes that read it and what is below
them. A Switch names a switch parameter and declares the input it won't read, which is then neither evaluated nor
part of its key: the branch it turns off costs nothing.

Shared parameter files (`*.texparams.yml`) hold values many graphs read, like UE's material parameter collections.
A graph lists the ones it reads; its own parameters of the same name win. Files are read again when they change on
disk, so a bake picks up a new value. Changed in the graph panel, shared values apply to the open graph at once and
are written on Save shared files, which then bakes the graphs that read them: for now those in the folder above the
open graph's and below it, until the asset index knows every graph that does.

## The library is sized in pixels and wraps

Noises (value, gradient, cell, simplex, ridged and white, each with octaves and a domain warp; white is one
random value per cell, a pixel at size 1, the speckle pixel art is shaded with), patterns (bricks, planks,
tiles, panel seams, rivets, stripes, checker, gradients, wood grain, scratches), operations (levels, curves, remap,
threshold, blend, blur, edge detect, distance, slope blur, warp, offset, transform), coordinates, channels and color
nodes. Sizes are in pixels, every operation that moves pixels wraps, and every noise is periodic, so anything built
from them tiles. Simplex noise isn't periodic on its own lattice, so it is read on a torus in four dimensions, each
axis a circle as long as its cells. Patterns of pieces put the gap on each piece's far side and give a random value
per piece from the node's seed, so a graph can shade bricks or planks one by one. Operations that move pixels come
for scalars and for colors, since a color doesn't turn into a scalar by itself.

## Palettes are where pixel art lives

A palette index pin carries an index per pixel and the palette it indexes, and reads as color through it. Quantize
maps colors to a given palette, Palette to a few of the image's own colors (median cut, darkest first), both with no
dither, ordered (4x4 Bayer) or error diffusion (Floyd and Steinberg, wrapping). Ordered dither moves colors by the
palette's own spacing, its colors' usual distance to their nearest neighbor; moving them by the full 0..1 range throws a
palette of close tones into a checkerboard of far apart ones. Colors are matched, dithered,
blended in gradient maps and turned in HSV on their sRGB values, as an artist picks and compares them; everything
else stays linear. The Viewer's pixel readout names the index under the mouse.

## Expression covers the gaps

A one-line formula over four scalar inputs and the pixel's position, parsed once per evaluation into a tree. Its
errors name the character, and show on the node like any other.

## Starter graphs

The editor's data folder holds starter graphs made from nodes alone, the old starterTextures set rebuilt: stone,
dirt, grass, oak leaves, oak log, water, sand, concrete, steel, bronze, glass and a grass tuft. Each follows the old
generator's formula (a flat color varied a few percent by white noise, sparse specks from a second white noise, and
the shapes drawn with Expression and Cells), in the colors the block textures were later tuned to, so their averages
match the blocks' within about a percent; only where the random pixels fall differs. The Graph toolbar
opens one as a new graph, unsaved, to change and save somewhere with a bake target. They aren't a game look's
source: the block textures stay painted.

## Subgraphs are graphs placed as one node

A texture graph with Input and Output nodes (one type of each per pin type, each named) can be placed in another
graph as one node: its inputs and outputs are the Input and Output nodes, top to bottom as they stand, and its
parameters its own graph parameters. Subgraph files are authoring sources like any graph, never shipped, and a graph
names one by its path relative to the graph's own file, which is the node's type (`subgraph:../subgraphs/mortar
.texgraph.yml`), as it names shared parameter files. So each graph's library is its own: the bake, `BakeTextures` and
`BuildTextures` read every graph with a library of its own, and Save As to another folder rewrites the paths. The node
search offers the subgraphs in the graph's folder and below it, and in a `subgraphs` folder beside any of its
ancestors.

A placed subgraph runs at the outer graph's size and with its seed, moved only by the node's `seed variation`, with
its inputs fed to the Input nodes. Each parameter the subgraph reads is the node's own value if set, else the outer
graph's parameter of that name and kind, else the subgraph's own. That is what lets Make subgraph (the node menu)
take part of a graph out without changing a pixel: the nodes keep their IDs inside, so their seeds, and they read the
same parameters. A selection that feeds an outside node feeding it back is refused, since the placed node would be in
a loop. The node's cache key holds a fingerprint of the subgraph file and everything it reads (shared parameter files,
nested subgraphs, images), and the editor checks subgraph files every second, so an edit to one, saved anywhere,
shows in every swatch reading it.

A link to a pin the subgraph no longer has, or a value for a parameter it dropped, is kept: the node's type gets the
lost pin or parameter back from the graph using it, and evaluating it is an error naming what was lost. Nothing is
unlinked behind the user's back. A subgraph placing itself, at any depth, is an error on the node.

Relative file names inside a subgraph, like an Image node's, start from the subgraph's folder; Make subgraph doesn't
rewrite them, so a subgraph made into another folder may need them fixed.

## What ships is converted from PNGs

Graphs bake PNG maps, and a separate step turns a texture's PNG maps, baked or painted, into the one `.ika` a game
ships (`TextureConvert`, a `TEXR` section in Ikala-Graphics' `TextureSection`). Keeping the two apart lets painted
textures and graphs go the same way, and lets a PNG be looked at and diffed before it ships. A graph's `maps` setting
puts its PNGs in an unshipped source folder beside it, while `data` still names the content folder its model is
found in. The texture file is uncompressed RGBA8, since block compression works on 4x4 blocks and would smear
16 pixel art, with every mip level made when it is written: colors averaged in linear light, normals renormalized.
The game uploads those levels as they are. A converted file is left alone when its maps wouldn't change, compared as
pixels rather than bytes, so a new converter version in the metadata doesn't rewrite every file.

`BuildTextures` (`bin/buildTextures`) bakes every graph under a sources folder and converts every texture there, or
with `--uses` bakes only the graphs reading a shared parameter file; Lotomation's block textures are built that way,
and a test fails when a block graph and the `.ika` shipped from it disagree.

## The file

`*.texgraph.yml`: the graph's settings first (format, size, seed, the content folder it bakes to relative to the
file, the folder of textures its maps bake under when not the content folder's, the texture's name under its
textures folder, the model whose UVs give the layout, the shared parameter files
it reads, and its parameters, one line each without their defaults), then the graph as `GraphFile` writes it. A
placed subgraph's node type is `subgraph:` and the file's path relative to the graph. Graph files for the base game's blocks live in Lotomation's `art/textures/block/`.
