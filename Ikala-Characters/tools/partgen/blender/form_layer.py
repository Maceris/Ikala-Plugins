"""Give a garment fitted to a body (see bake_layer.py and variant_layer.py) its clothing form
targets: for each body slot it lies over, a morph target named "form:<slot>" moving each vertex
out far enough to clear the body plan's bulkiest form, times how much that slot owns the vertex
(1 over the slot, easing to 0 within TAPER of it). The character code scales each slot's target
by the form of the body part worn there (see PartForms.java), so jeans over a robot leg stand out
around the leg and stay snug at the waist. Its vertices, weights, texture layout, textures and
tint mask stay as they are.

Each vertex moves straight out from its nearest point on the body. Where those directions part,
as in an armpit or the crotch, the moves are averaged with the neighbors' a few times, over
vertices welded by position so the mesh's UV seams move as one and don't open. Where the form's
shell meets itself, as an arm's and the chest's do in the armpit, pushing a vertex out from one
puts it into the other, so those are pushed out to the layer's gap again (see shape_layer.py).

    blender -b --python form_layer.py -- --mesh <garment.glb> --body <male.blend>
        --offset 0.06 --gap 0.004 --out <file.glb> --preview <folder>

writes the garment with its targets, form.json and renders preview_20.png and preview_21.png in
the preview folder: the garment pushed out over every slot, from the front and back.
"""

import argparse
import json
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fit_accessory as hat  # noqa: E402
from bodies import body_meshes, nearest, tree, world_vertices  # noqa: E402
from fit_layer import render  # noqa: E402
from shape_layer import shape  # noqa: E402

# What the targets' names start with, then the slot (see GltfImporter.FORM_TARGET_PREFIX)
TARGET_PREFIX = "form:"
# What the mannequin's slot meshes' names start with, then the slot
SLOT_PREFIX = "mannequin_"
# How far into a neighboring slot a slot's share of the move eases to nothing, in meters
TAPER = 0.08
# The least share a slot must have of some vertex to get a target
LEAST_SHARE = 0.01
# How many times each vertex's move is averaged with its neighbors'
SMOOTH_ROUNDS = 4
# How close vertices must be to count as one when welding by position, in meters
WELD = 1e-5


def welded(points, faces):
    """Each vertex's index among the distinct positions, and those positions' neighbor pairs."""
    keys = np.round(points / WELD).astype(np.int64)
    _, index = np.unique(keys, axis=0, return_inverse=True)
    index = index.reshape(-1)
    pairs = np.concatenate([faces[:, [0, 1]], faces[:, [1, 2]], faces[:, [2, 0]]])
    pairs = index[pairs]
    pairs = pairs[pairs[:, 0] != pairs[:, 1]]
    return index, np.unique(np.sort(pairs, axis=1), axis=0)


def push_out(points, faces, surface, offset):
    """How far each vertex moves to stand offset further out from the body."""
    closest, signed = nearest(points, surface)
    away = points - closest
    length = np.linalg.norm(away, axis=1, keepdims=True)
    # Inside the body, the way out is the other way
    away *= np.sign(signed)[:, None] / np.maximum(length, 1e-9)
    index, pairs = welded(points, faces)
    count = index.max() + 1
    move = np.zeros((count, 3))
    np.add.at(move, index, away * offset)
    move /= np.maximum(np.bincount(index, minlength=count), 1)[:, None]
    for _ in range(SMOOTH_ROUNDS):
        total = move.copy()
        neighbors = np.ones(count)
        np.add.at(total, pairs[:, 0], move[pairs[:, 1]])
        np.add.at(total, pairs[:, 1], move[pairs[:, 0]])
        np.add.at(neighbors, pairs.ravel(), 1)
        move = total / neighbors[:, None]
    # Averaging shortens the moves where directions part; give them their length back
    length = np.linalg.norm(move, axis=1, keepdims=True)
    move *= offset / np.maximum(length, 1e-9)
    return move[index]


def import_skinned(path):
    """A garment from a glb with its weights, its shape keys dropped. The file's armature is
    dropped too: it is the same rig, and exporting with the body's keeps the rest pose exact."""
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=os.path.abspath(path))
    imported = set(bpy.data.objects) - before
    # The biggest mesh: the glTF importer brings a small sphere along to draw bones with
    garment = max((o for o in imported if o.type == "MESH"), key=lambda o: len(o.data.vertices))
    world = garment.matrix_world.copy()
    garment.parent = None
    garment.matrix_world = world
    garment.modifiers.clear()
    for obj in imported - {garment}:
        bpy.data.objects.remove(obj)
    if garment.data.shape_keys:
        garment.shape_key_clear()
    hat.select_only(garment)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    return garment


def slot_shares(points, bodies):
    """How much each body slot owns each point: 1 for the slot whose skin is nearest, easing
    smoothly to 0 as a slot's skin lies TAPER further than that. Only slots owning some point at
    least LEAST_SHARE are given."""
    distances = {}
    for obj in bodies:
        if obj.name.startswith(SLOT_PREFIX):
            co, tris = world_vertices(obj)
            distances[obj.name[len(SLOT_PREFIX):]] = np.abs(nearest(points, tree(co, tris))[1])
    nearest_any = np.min(np.stack(list(distances.values())), axis=0)
    shares = {}
    for slot, distance in distances.items():
        x = np.clip(1 - (distance - nearest_any) / TAPER, 0, 1)
        share = x * x * (3 - 2 * x)
        if share.max() >= LEAST_SHARE:
            shares[slot] = share
    return shares


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    for name in ("--mesh", "--body", "--out", "--preview"):
        parser.add_argument(name, required=True)
    parser.add_argument("--offset", type=float, required=True,
                        help="The bulkiest form's offset, in meters.")
    parser.add_argument("--gap", type=float, required=True,
                        help="The layer's gap: the least it may stand off the body.")
    args = parser.parse_args(argv)

    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(args.body))
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    armature.animation_data_clear()
    armature.data.pose_position = "REST"
    bodies = body_meshes()
    garment = import_skinned(args.mesh)

    surface_points, surface_faces = [], []
    start = 0
    for obj in bodies:
        co, tris = world_vertices(obj)
        surface_points.append(co)
        surface_faces.append(tris + start)
        start += len(co)
    surface = tree(np.concatenate(surface_points), np.concatenate(surface_faces))

    rest = np.empty(len(garment.data.vertices) * 3)
    garment.data.vertices.foreach_get("co", rest)
    rest = rest.reshape(-1, 3)
    garment.data.calc_loop_triangles()
    faces = np.array([tuple(t.vertices) for t in garment.data.loop_triangles])
    _, before = nearest(rest, surface)

    # The whole move, over every slot at once
    points = rest + push_out(rest, faces, surface, args.offset)
    garment.data.vertices.foreach_set("co", points.astype(np.float32).ravel())
    garment.data.update()
    _, pushed = nearest(points, surface)
    shape(garment, surface, args.gap, rounds=0, armature=armature)
    points = np.empty(len(garment.data.vertices) * 3)
    garment.data.vertices.foreach_get("co", points)
    points = points.reshape(-1, 3)
    _, after = nearest(points, surface)
    move = points - rest
    garment.data.vertices.foreach_set("co", rest.astype(np.float32).ravel())
    garment.data.update()

    # Each slot's share of the move, as a shape key
    shares = slot_shares(rest, bodies)
    garment.shape_key_add(name="Basis", from_mix=False)
    for slot, share in shares.items():
        key = garment.shape_key_add(name=TARGET_PREFIX + slot, from_mix=False)
        key.data.foreach_set("co", (rest + move * share[:, None]).astype(np.float32).ravel())
        key.value = 0
    garment.parent = armature
    skin = garment.modifiers.new("Armature", "ARMATURE")
    skin.object = armature
    hat.export(args.out, garment, armature)

    report = {
        "offset_mm": round(args.offset * 1000, 1),
        # Each slot's share of the garment, as the percentage of vertices it owns most of
        "slots": {slot: round(float((share > 0.5).mean()) * 100, 1)
                  for slot, share in shares.items()},
        "min_gap_before_mm": round(float(before.min()) * 1000, 1),
        "pushed_back_pct": round(float((pushed < args.gap).mean()) * 100, 2),
        "min_gap_mm": round(float(after.min()) * 1000, 1),
        "max_gap_mm": round(float(after.max()) * 1000, 1),
    }
    print("[form_layer]", json.dumps(report))
    os.makedirs(args.preview, exist_ok=True)
    with open(os.path.join(args.preview, "form.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)
    # The bulkiest look over every slot at once
    for key in garment.data.shape_keys.key_blocks[1:]:
        key.value = 1
    for k, view in enumerate((0, 180)):
        render(os.path.join(args.preview, "preview_%d.png" % (k + 20)), view, "TEXTURE")


if __name__ == "__main__":
    main()
