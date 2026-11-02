"""Make a garment's mesh for a clothing form (see the body plan's "forms"): the garment already
fitted to a body (see bake_layer.py and variant_layer.py), pushed out from that body by the
form's offset, so it goes over body parts that stand out that far. Its vertices only move, so its
texture layout, textures and tint mask stay as they are.

Each vertex moves straight out from its nearest point on the body. Where those directions part,
as in an armpit or the crotch, the moves are averaged with the neighbors' a few times, over
vertices welded by position so the mesh's UV seams move as one and don't open. Where the form's
shell meets itself, as an arm's and the chest's do in the armpit, pushing a vertex out from one
puts it into the other, so those are pushed out to the layer's gap again (see shape_layer.py).

    blender -b --python form_layer.py -- --mesh <garment.glb> --body <male.blend>
        --offset 0.04 --gap 0.004 --name <id> --out <file.glb> --preview <folder> [--bare <bones>]

writes the garment skinned to the body's rig, form.json and renders preview_20.png and
preview_21.png in the preview folder: the garment from the front and back.
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
from variant_layer import import_garment  # noqa: E402
from weight_layer import transfer_weights, weight_source  # noqa: E402

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


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    for name in ("--mesh", "--body", "--name", "--out", "--preview"):
        parser.add_argument(name, required=True)
    parser.add_argument("--offset", type=float, required=True)
    parser.add_argument("--gap", type=float, required=True,
                        help="The layer's gap: the least it may stand off the body.")
    parser.add_argument("--bare", default="", help="Comma-separated bones the layer leaves bare.")
    args = parser.parse_args(argv)

    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(args.body))
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    armature.animation_data_clear()
    armature.data.pose_position = "REST"
    bodies = body_meshes()
    garment = import_garment(args.mesh, args.name)

    surface_points, surface_faces = [], []
    start = 0
    for obj in bodies:
        co, tris = world_vertices(obj)
        surface_points.append(co)
        surface_faces.append(tris + start)
        start += len(co)
    surface = tree(np.concatenate(surface_points), np.concatenate(surface_faces))

    points = np.empty(len(garment.data.vertices) * 3)
    garment.data.vertices.foreach_get("co", points)
    points = points.reshape(-1, 3)
    garment.data.calc_loop_triangles()
    faces = np.array([tuple(t.vertices) for t in garment.data.loop_triangles])
    _, before = nearest(points, surface)
    points = points + push_out(points, faces, surface, args.offset)
    garment.data.vertices.foreach_set("co", points.astype(np.float32).ravel())
    garment.data.update()
    _, pushed = nearest(points, surface)
    shape(garment, surface, args.gap, rounds=0, armature=armature)
    points = np.empty(len(garment.data.vertices) * 3)
    garment.data.vertices.foreach_get("co", points)
    _, after = nearest(points.reshape(-1, 3), surface)

    source = weight_source(bodies)
    limb_fixed = transfer_weights(garment, source, armature,
                                  [b for b in args.bare.split(",") if b], bodies)
    bpy.data.objects.remove(source)
    hat.export(args.out, garment, armature)

    report = {
        "offset_mm": round(args.offset * 1000, 1),
        "min_gap_before_mm": round(float(before.min()) * 1000, 1),
        "pushed_back_pct": round(float((pushed < args.gap).mean()) * 100, 2),
        "min_gap_mm": round(float(after.min()) * 1000, 1),
        "max_gap_mm": round(float(after.max()) * 1000, 1),
        "limb_weights_fixed": limb_fixed,
    }
    print("[form_layer]", json.dumps(report))
    os.makedirs(args.preview, exist_ok=True)
    with open(os.path.join(args.preview, "form.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)
    for k, view in enumerate((0, 180)):
        render(os.path.join(args.preview, "preview_%d.png" % (k + 20)), view, "TEXTURE")


if __name__ == "__main__":
    main()
