# Graph editor

`ui.graph.GraphEditor` edits node graphs on a `Canvas`: texture graphs in the asset editor first, and later
anything else that is a graph, like quests. `ui.graph.model` holds what it edits: `Graph`, `NodeTypes`,
`PinTypes`, `GraphHistory` and `GraphFile`.

## The caller gives the meaning

Graphics knows nothing about textures or quests, so the editor owns no semantics. The caller registers node types
(pins, parameters, a category and a version), pin types (a color and which types convert to which), and a check
that names problems on nodes. The editor keeps to the pins' types and nothing else: a link is made directly, through
a conversion node the caller named, or refused. What a graph computes is the caller's business.

## Every edit goes through the history

`GraphHistory` keeps a copy of the whole graph before each edit. Graphs edited by hand are small, a few hundred
nodes at most, so whole copies cost little and can't miss a field the way per-edit undo code can. A drag changes
the graph as the mouse moves and is recorded once, as one edit, when the button is let go.

## The file is meant to be diffed

`GraphFile` writes YAML a person can read in a diff: nodes keyed by their IDs and sorted, positions rounded to an 8
unit grid, each parameter set away from its default on its own line, and links one per line, sorted. Node IDs come
from the type and a number (`multiply2`) and never change, so moving or editing a node changes only its own lines.
Each node type has a version; reading a node written by an older version calls the caller's upgrader, so files keep
working when a type gains or renames a parameter. A link that would need a conversion is refused when read: the
conversion node was written as a node of its own when the link was made.

## How it sits on the canvas

The canvas lays out and draws the nodes, and pans and zooms. Each node is a `GraphNodeView` that only draws; the
editor reads the mouse for all of them at once, after they are drawn, through the canvas's overlay hook, and draws
links under them through its underlay hook. It takes the mouse for its own drags by making its own item active, so
the canvas doesn't pan, and turns off the canvas's left-drag panning, leaving that drag to the selection box. Pins
are hit by distance to their dots, which reach a little past the node's edge, so a node's body and its pins don't
fight over a click.

Copy and paste go through the system clipboard as the same YAML, behind a header line, so a part of one graph pastes
into another, even in another editor run.

## Tidying belongs to the editor

Reroutes, named reroutes, comment frames, collapsing, hidden pins, comments and errors are the same for every kind
of graph, so the editor provides them and callers register nothing for them. Reroutes and the send and receive pair
of a named reroute are node types the editor registers itself, with pins of the "any" type: they are ordinary nodes
in the graph, the file and the undo history, and nothing special-cases them but the drawing. What flows through them
is found by following links (and a receive's name, to its send) back to a real output, so a reroute takes on the
color and the conversion rules of what is linked into it. A caller that evaluates the graph follows them the same
way.

A comment frame is not a node. It holds the nodes inside it only by where they are: dragging a frame by its title
brings along the nodes whose boxes are inside it when the drag starts, and nothing else ties them together, so
moving a node out of a frame is just moving it. Collapsing, hiding unused pins and comments are a node's own
fields, written only when set.

Colors in the file are `#RRGGBBAA`, the order IkGui packs colors in, so the value in the file and in code match.

Searching and selecting by relation (upstream, downstream, unused) are model queries on `Graph`, so scripts and
tests use them without the editor. Aligning and straightening need node sizes, which only the views know, so they
live on the editor and act on what was last drawn.
