"""Carry a baked garment fitted to the male mannequin (see bake_layer.py) over to another body
type, through the base mesh cages (see tools/blender/body_cages.py).

The garment is bound to the male cage with a Surface Deform modifier, and the cage given the
other body's shape, so the garment follows it, keeping its vertices and texture layout, so the
same textures and tint mask fit. Where the cage bunches up, like the armpits, the bust and the
crotch, faces fold over, and a folded face is culled when drawn, a hole: around those, the
garment takes its neighbors' move instead of its own (see untangle). It is then pushed out from the new body to the gap, and in to
the reach for a layer worn under others, as shape_layer.py does, without smoothing, and takes
that body's weights, as weight_layer.py does.

    blender -b --python variant_layer.py -- --baked <baked.glb> --body <female.blend>
        --from-cage <male_cage.npz> --to-cage <female_cage.npz> --gap 0.004 --name <id>
        --out <file.glb> --preview <folder> [--reach 0.012]

writes the garment skinned to the new body's rig, variant.json and renders preview_10.png and
preview_11.png in the preview folder: the garment on the new body from the front and back.
"""

import argparse
import json
import os
import sys

import bmesh
import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fit_accessory as hat  # noqa: E402
from bodies import body_meshes, nearest, tree, world_vertices  # noqa: E402
from fit_layer import render  # noqa: E402
from shape_layer import parse_cut, points_of, shape, tucked_band  # noqa: E402
from weight_layer import transfer_weights, weight_source  # noqa: E402

# How many rings of neighbors around a folded face take their neighbors' move too
UNTANGLE_RINGS = 2
# How many times the moves are averaged over those, and how many times folds are looked for
# again after pushing (pushing can fold a face too)
UNTANGLE_ROUNDS = 30
UNTANGLE_PASSES = 8
# Faces still folded after that, as pushing folds the same slivers again, are collapsed into
# their shortest edge if smaller than this, in square meters
FOLD_AREA = 4e-4


def cage(name, data):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([tuple(p) for p in data["vertices"]], [],
                     [tuple(f) for f in data["faces"]])
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def import_garment(path, name):
    """A garment from a glb alone, without any skin or weights it had, named as given."""
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=os.path.abspath(path))
    imported = set(bpy.data.objects) - before
    # The biggest mesh: the glTF importer brings a small sphere along to draw bones with
    garment = max((o for o in imported if o.type == "MESH"), key=lambda o: len(o.data.vertices))
    world = garment.matrix_world.copy()
    garment.parent = None
    garment.matrix_world = world
    garment.modifiers.clear()
    garment.vertex_groups.clear()
    for obj in imported - {garment}:
        bpy.data.objects.remove(obj)
    hat.select_only(garment)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    garment.name = name
    garment.data.name = name
    return garment


def carry(garment, from_cage, to_cage):
    """Move a garment from one body to another with their cages. Surface Deform won't bind a
    mesh imported from glTF to a target ("No valid target mesh") though it binds the same
    geometry made afresh, so a plain copy with the same vertices is carried, and its vertices
    copied back."""
    points = np.empty(len(garment.data.vertices) * 3)
    garment.data.vertices.foreach_get("co", points)
    plain = cage("carried", {"vertices": points.reshape(-1, 3),
                             "faces": [tuple(p.vertices) for p in garment.data.polygons]})
    start = cage("cage", from_cage)
    deform = plain.modifiers.new("cage", "SURFACE_DEFORM")
    deform.target = start
    bpy.context.view_layer.update()
    with bpy.context.temp_override(object=plain, active_object=plain):
        bpy.ops.object.surfacedeform_bind(modifier=deform.name)
    if not deform.is_bound:
        raise SystemExit("The garment didn't bind to the cage")
    start.data.vertices.foreach_set("co", to_cage.astype(np.float32).ravel())
    start.data.update()
    moved, _ = world_vertices(plain)
    garment.data.vertices.foreach_set("co", moved.astype(np.float32).ravel())
    garment.data.update()
    bpy.data.objects.remove(plain)
    bpy.data.objects.remove(start)


def face_normals(obj):
    obj.data.update()
    normals = np.empty(len(obj.data.polygons) * 3)
    obj.data.polygons.foreach_get("normal", normals)
    return normals.reshape(-1, 3)


def untangle(obj, start, start_normals):
    """Unfold the faces that turned over since the garment lay at the start points: their
    vertices and some rings of neighbors (UNTANGLE_RINGS) move as their neighbors do, by
    averaging how far each moved, the rest held where they are. A patch moved alike keeps the
    start's shape, which had no folds. The glTF garment is split into separate vertices along
    its UV seams; those lying together move as one, or the seams would open. Gives how many
    faces were folded."""
    folded = (face_normals(obj) * start_normals).sum(1) < 0
    if not folded.any():
        return 0
    _, welded = np.unique(np.round(start, 6), axis=0, return_inverse=True)
    welded = welded.ravel()
    count = welded.max() + 1
    edges = welded[np.array([e.vertices[:] for e in obj.data.edges])]
    edges = np.unique(np.sort(edges[edges[:, 0] != edges[:, 1]], axis=1), axis=0)
    loose = np.zeros(count, dtype=bool)
    loose[welded[[v for k in np.flatnonzero(folded) for v in obj.data.polygons[k].vertices]]] = True
    for _ in range(UNTANGLE_RINGS):
        loose[edges[loose[edges].any(1)].ravel()] = True
    points = np.empty(len(start) * 3)
    obj.data.vertices.foreach_get("co", points)
    move = np.zeros((count, 3))
    move[welded] = points.reshape(-1, 3) - start
    neighbors = np.bincount(edges.ravel(), minlength=count)[:, None]
    for _ in range(UNTANGLE_ROUNDS):
        total = np.zeros_like(move)
        np.add.at(total, edges[:, 0], move[edges[:, 1]])
        np.add.at(total, edges[:, 1], move[edges[:, 0]])
        move[loose] = (total / np.maximum(neighbors, 1))[loose]
    obj.data.vertices.foreach_set("co", (start + move[welded]).astype(np.float32).ravel())
    obj.data.update()
    return int(folded.sum())


def collapse_folds(obj, start_normals):
    """Collapse the faces still folded over against the start (see untangle) and smaller than
    FOLD_AREA into their shortest edges, welding the glTF's UV-seam twins first so the seams
    stay closed. Gives how many were collapsed and how many folded faces are left."""
    folded = (face_normals(obj) * start_normals).sum(1) < 0
    small = folded & (np.array([p.area for p in obj.data.polygons]) < FOLD_AREA)
    if not small.any():
        return 0, int(folded.sum())
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    mesh.faces.ensure_lookup_table()
    shortest = {min(mesh.faces[i].edges, key=lambda e: e.calc_length())
                for i in np.flatnonzero(small)}
    bmesh.ops.remove_doubles(mesh, verts=mesh.verts[:], dist=1e-6)
    shortest = [e for e in shortest if e.is_valid]
    bmesh.ops.collapse(mesh, edges=shortest, uvs=True)
    mesh.to_mesh(obj.data)
    mesh.free()
    obj.data.update()
    return len(shortest), int(folded.sum() - small.sum())


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    for name in ("--baked", "--body", "--from-cage", "--to-cage", "--name", "--out",
                 "--preview"):
        parser.add_argument(name, required=True)
    parser.add_argument("--gap", type=float, required=True)
    parser.add_argument("--reach", type=float)
    parser.add_argument("--bare", default="", help="Comma-separated bones the layer leaves bare.")
    parser.add_argument("--cut", help="The layer's cut (see shape_layer.py), to keep its tucked "
                        "part tucked on this body.")
    args = parser.parse_args(argv)
    cut = parse_cut(args.cut, args.body)

    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(args.body))
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    armature.animation_data_clear()
    armature.data.pose_position = "REST"
    bodies = body_meshes()
    garment = import_garment(args.baked, args.name)
    start, _ = world_vertices(garment)
    start_normals = face_normals(garment)
    carry(garment, np.load(args.from_cage), np.load(args.to_cage)["vertices"])

    surface_points, surface_faces = [], []
    offset = 0
    for obj in bodies:
        co, tris = world_vertices(obj)
        surface_points.append(co)
        surface_faces.append(tris + offset)
        offset += len(co)
    surface = tree(np.concatenate(surface_points), np.concatenate(surface_faces))
    _, before_push = nearest(world_vertices(garment)[0], surface)
    folded = []
    for _ in range(UNTANGLE_PASSES):
        folded.append(untangle(garment, start, start_normals))
        gap, reach = tucked_band(points_of(garment), cut, args.gap, args.reach)
        pushed = shape(garment, surface, gap, reach, rounds=0, armature=armature)
        if not folded[-1]:
            break
    collapsed, left = collapse_folds(garment, start_normals)
    _, after_push = nearest(world_vertices(garment)[0], surface)

    source = weight_source(bodies)
    limb_fixed = transfer_weights(garment, source, armature,
                                  [b for b in args.bare.split(",") if b], bodies)
    bpy.data.objects.remove(source)
    hat.export(args.out, garment, armature)

    report = {
        "inside_before_pct": round(float((before_push < 0).mean()) * 100, 2),
        "pushed_pct": round(pushed / max(1, len(before_push)) * 100, 1),
        "min_gap_mm": round(float(after_push.min()) * 1000, 1),
        "max_gap_mm": round(float(after_push.max()) * 1000, 1),
        "folded_faces": folded,
        "folds_collapsed": collapsed,
        "folded_left": left,
        "limb_weights_fixed": limb_fixed,
    }
    print("[variant_layer]", json.dumps(report))
    os.makedirs(args.preview, exist_ok=True)
    with open(os.path.join(args.preview, "variant.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)
    for k, view in enumerate((0, 180)):
        render(os.path.join(args.preview, "preview_%d.png" % (k + 10)), view, "TEXTURE")


if __name__ == "__main__":
    main()
