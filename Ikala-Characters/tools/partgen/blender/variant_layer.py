"""Carry a baked garment fitted to the male mannequin (see bake_layer.py) over to another body
type, through the base mesh cages (see tools/blender/body_cages.py).

The garment is bound to the male cage with a Surface Deform modifier, and the cage given the
other body's shape, so the garment follows it, keeping its vertices and texture layout, so the
same textures and tint mask fit. It is then pushed out from the new body to the gap, as
shape_layer.py does, and takes that body's weights, as weight_layer.py does.

    blender -b --python variant_layer.py -- --baked <baked.glb> --body <female.blend>
        --from-cage <male_cage.npz> --to-cage <female_cage.npz> --gap 0.004 --name <id>
        --out <file.glb> --preview <folder>

writes the garment skinned to the new body's rig, variant.json and renders preview_10.png and
preview_11.png in the preview folder: the garment on the new body from the front and back.
"""

import argparse
import json
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fit_accessory as hat  # noqa: E402
from bodies import nearest, tree, world_vertices  # noqa: E402
from fit_layer import render  # noqa: E402
from shape_layer import shape  # noqa: E402
from weight_layer import transfer_weights, weight_source  # noqa: E402


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


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    for name in ("--baked", "--body", "--from-cage", "--to-cage", "--name", "--out",
                 "--preview"):
        parser.add_argument(name, required=True)
    parser.add_argument("--gap", type=float, required=True)
    args = parser.parse_args(argv)

    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(args.body))
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    armature.animation_data_clear()
    armature.data.pose_position = "REST"
    bodies = [o for o in bpy.data.objects if o.type == "MESH" and "eye" not in o.name.lower()]
    garment = import_garment(args.baked, args.name)
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
    pushed = shape(garment, surface, args.gap)
    _, after_push = nearest(world_vertices(garment)[0], surface)

    source = weight_source(bodies)
    transfer_weights(garment, source, armature)
    bpy.data.objects.remove(source)
    hat.export(args.out, garment, armature)

    report = {
        "inside_before_pct": round(float((before_push < 0).mean()) * 100, 2),
        "pushed_pct": round(pushed / max(1, len(before_push)) * 100, 1),
        "min_gap_mm": round(float(after_push.min()) * 1000, 1),
    }
    print("[variant_layer]", json.dumps(report))
    os.makedirs(args.preview, exist_ok=True)
    with open(os.path.join(args.preview, "variant.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)
    for k, view in enumerate((0, 180)):
        render(os.path.join(args.preview, "preview_%d.png" % (k + 10)), view, "TEXTURE")


if __name__ == "__main__":
    main()
