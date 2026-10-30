"""Check that clothing fitted to the male mannequin carries over to the female through the base
mesh cages (see tools/blender/body_cages.py), and render how it sits.

A test shirt is made from the male body's torso and arms, pushed out along the normals by a set
gap. It is bound to the male cage with a Surface Deform modifier, the cage takes the female's
shape, and the moved shirt is measured against the female body: how far outside it each vertex
is. The same is measured for the shirt left as it was, which is what using the male's clothing
on the female unchanged would do.

    blender -b --python cage_transfer_check.py -- --mannequins <folder> --cages <folder>
        --out <folder>

The cages are usually in the mannequin folder too.
"""

import argparse
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bodies import load_body, normals, signed_distances, world_vertices  # noqa: E402

# How far the test shirt sits outside the male body, in meters
GAP = 0.01
# The body slots the test shirt covers, matched against the mannequin's mesh names
SHIRT_SLOTS = ("torso", "upperarm", "lowerarm")


def make_object(name, points, faces):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([tuple(p) for p in points], [], [tuple(f) for f in faces])
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def summary(name, d):
    p = np.percentile(d, [1, 5, 50, 95, 99])
    result = {
        "median_mm": round(float(p[2]) * 1000, 1),
        "p1_mm": round(float(p[0]) * 1000, 1), "p5_mm": round(float(p[1]) * 1000, 1),
        "p95_mm": round(float(p[3]) * 1000, 1), "p99_mm": round(float(p[4]) * 1000, 1),
        "inside_pct": round(float((d < 0).mean()) * 100, 2),
        "within_3mm_of_gap_pct": round(float((np.abs(d - GAP) < 0.003).mean()) * 100, 1),
    }
    print("[check] %-28s %s" % (name, result))
    return result


def render(path, body, garment, view):
    """A plain render of the body with the garment over it."""
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    body_obj = make_object("body", *body)
    garment_obj = make_object("garment", *garment)
    for obj, color in ((body_obj, (0.75, 0.75, 0.75, 1)), (garment_obj, (0.2, 0.45, 0.9, 1))):
        material = bpy.data.materials.new(obj.name)
        material.diffuse_color = color
        obj.data.materials.append(material)
        for polygon in obj.data.polygons:
            polygon.use_smooth = True
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.color_type = "MATERIAL"
    scene.display.shading.light = "STUDIO"
    scene.render.resolution_x, scene.render.resolution_y = 700, 900
    camera = bpy.data.objects.new("camera", bpy.data.cameras.new("camera"))
    scene.collection.objects.link(camera)
    scene.camera = camera
    target = Vector((0, 0, 1.2))
    angle = math.radians(view)
    camera.location = target + Vector((math.sin(angle) * 4.2, -math.cos(angle) * 4.2, 0.1))
    camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()
    camera.data.lens = 50
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--mannequins", required=True)
    parser.add_argument("--cages", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)

    male_body, male_shirt = load_body(os.path.join(args.mannequins, "male.blend"), SHIRT_SLOTS)
    female_body, _ = load_body(os.path.join(args.mannequins, "female.blend"))
    shirt = male_shirt[0] + normals(*male_shirt) * GAP
    cages = {name: np.load(os.path.join(args.cages, name + "_cage.npz"))
             for name in ("male", "female")}
    results = {}

    # The cages should sit on the bodies they came from
    for name, body in (("male", male_body), ("female", female_body)):
        results["cage_on_" + name] = summary(
            "%s cage vs %s body" % (name, name),
            signed_distances(cages[name]["vertices"], *body))
    results["shirt_on_male"] = summary("shirt vs male body",
                                       signed_distances(shirt, *male_body))
    results["unchanged_on_female"] = summary("shirt unchanged vs female",
                                             signed_distances(shirt, *female_body))

    # Bind to the male cage, then give the cage the female's shape
    bpy.ops.wm.read_factory_settings(use_empty=True)
    cage_obj = make_object("cage", cages["male"]["vertices"], cages["male"]["faces"])
    shirt_obj = make_object("shirt", shirt, male_shirt[1])
    modifier = shirt_obj.modifiers.new("cage", "SURFACE_DEFORM")
    modifier.target = cage_obj
    bpy.context.view_layer.objects.active = shirt_obj
    with bpy.context.temp_override(object=shirt_obj, active_object=shirt_obj):
        bpy.ops.object.surfacedeform_bind(modifier=modifier.name)
    if not modifier.is_bound:
        raise SystemExit("The shirt didn't bind to the cage")
    cage_obj.data.vertices.foreach_set("co", cages["female"]["vertices"].ravel())
    cage_obj.data.update()
    moved, _ = world_vertices(shirt_obj)
    results["moved_on_female"] = summary("shirt moved vs female",
                                         signed_distances(moved, *female_body))

    with open(os.path.join(args.out, "cage_transfer.json"), "w", encoding="utf-8") as file:
        json.dump(results, file, indent=1)
    for view in (0, 90):
        render(os.path.join(args.out, "moved_%d.png" % view), female_body,
               (moved, male_shirt[1]), view)
        render(os.path.join(args.out, "unchanged_%d.png" % view), female_body,
               (shirt, male_shirt[1]), view)
    print("[check] wrote", args.out)


if __name__ == "__main__":
    main()
