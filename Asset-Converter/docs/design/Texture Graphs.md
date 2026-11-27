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

## The file

`*.texgraph.yml`: the graph's settings first (format, size, seed, the content folder it bakes to relative to the
file, the texture's name under its textures folder, the model whose UVs give the layout, the shared parameter files
it reads, and its parameters, one line each without their defaults), then the graph as `GraphFile` writes it. Graph files for the base game's blocks live in Lotomation's `art/textures/block/`.
