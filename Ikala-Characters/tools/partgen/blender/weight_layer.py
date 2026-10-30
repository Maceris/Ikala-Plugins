"""Give a shaped garment (see shape_layer.py) the mannequin's skin weights, and check how it
holds up when the body moves.

The weights come from the body under it: each garment vertex takes the weights of the nearest
point of the body's surface, blended across that face, for every bone, the girth and muscle
bones the sliders use among them, unsmoothed: smoothing made the garment lag the body at bent
joints (jeans seated showed 10% of the body they cover with three rounds, 4% with none). They are
limited to the four strongest per vertex, as characters are drawn with.

The check poses the rig a few ways (arms down, forward and up, and seated with the knees bent)
and measures, in each, how much of the body the garment covered at rest now shows through it,
and how much of the garment ends up inside the body.

    blender -b --python weight_layer.py -- --garment <shaped.glb> --body <male.blend> --out <folder>

writes weighted.glb (the garment skinned to the rig, at rest), weights.json and renders
pose_0.png to pose_3.png of the garment in each pose.
"""

import argparse
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Matrix, Quaternion, Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bodies import world_vertices  # noqa: E402
from fit_layer import import_figure, render  # noqa: E402

# Bones each vertex may follow, as characters are drawn with
INFLUENCES = 4
# How far out from the body a garment counts as covering it, in meters
COVER_REACH = 0.05
# The test poses: each bone turned about a world axis, in degrees. Blender space: Z up, the body
# facing -Y, its left side on +X; the arms rest 45 degrees below straight out
POSES = [
    ("arms down", {"upperarm.L": ("Y", 40), "upperarm.R": ("Y", -40)}),
    ("arms forward", {"upperarm.L": ("Z", -70), "upperarm.R": ("Z", 70)}),
    ("arms up", {"upperarm.L": ("Y", -70), "upperarm.R": ("Y", 70)}),
    ("seated", {"thigh.L": ("X", -80), "thigh.R": ("X", -80),
                "shin.L": ("X", 90), "shin.R": ("X", 90)}),
]
GARMENT_COLOR = (0.85, 0.45, 0.15, 1)
BODY_COLOR = (0.7, 0.7, 0.72, 1)


def weight_source(bodies):
    """One mesh of the whole body at rest with its weights, joined from copies of its slots."""
    copies = []
    for obj in bodies:
        copy = obj.copy()
        copy.data = obj.data.copy()
        copy.modifiers.clear()
        copy.parent = None
        copy.matrix_world = obj.matrix_world
        bpy.context.scene.collection.objects.link(copy)
        copies.append(copy)
    bpy.ops.object.select_all(action="DESELECT")
    for copy in copies:
        copy.select_set(True)
    bpy.context.view_layer.objects.active = copies[0]
    bpy.ops.object.join()
    source = bpy.context.view_layer.objects.active
    source.name = "weight_source"
    return source


def transfer_weights(garment, source, armature):
    """Give the garment the body's weights, smoothed, at most INFLUENCES per vertex, and skin it
    to the armature."""
    for group in source.vertex_groups:
        garment.vertex_groups.new(name=group.name)
    transfer = garment.modifiers.new("weights", "DATA_TRANSFER")
    transfer.object = source
    transfer.use_vert_data = True
    transfer.data_types_verts = {"VGROUP_WEIGHTS"}
    transfer.vert_mapping = "POLYINTERP_NEAREST"
    transfer.layers_vgroup_select_src = "ALL"
    transfer.layers_vgroup_select_dst = "NAME"
    bpy.ops.object.select_all(action="DESELECT")
    garment.select_set(True)
    bpy.context.view_layer.objects.active = garment
    bpy.ops.object.modifier_apply(modifier=transfer.name)
    bpy.ops.object.mode_set(mode="WEIGHT_PAINT")
    bpy.ops.object.vertex_group_limit_total(group_select_mode="ALL", limit=INFLUENCES)
    bpy.ops.object.vertex_group_normalize_all(group_select_mode="ALL", lock_active=False)
    bpy.ops.object.vertex_group_clean(group_select_mode="ALL", limit=0.001)
    bpy.ops.object.mode_set(mode="OBJECT")
    garment.parent = armature
    skin = garment.modifiers.new("Armature", "ARMATURE")
    skin.object = armature


def pose(armature, turns):
    """Turn bones about world axes from rest; with none, back to rest."""
    for bone in armature.pose.bones:
        bone.rotation_mode = "QUATERNION"
        bone.rotation_quaternion = Quaternion()
    for name, (axis, degrees) in turns.items():
        bone = armature.pose.bones[name]
        rest = bone.bone.matrix_local.to_3x3()
        world = Matrix.Rotation(math.radians(degrees), 3, axis)
        bone.rotation_quaternion = (rest.inverted() @ world @ rest).to_quaternion()
    bpy.context.view_layer.update()


def surfaces(bodies, garment):
    """The body and garment as they are now posed: points, triangles, normals and trees."""
    points, faces, offset = [], [], 0
    for obj in bodies:
        co, tris = world_vertices(obj)
        points.append(co)
        faces.append(tris + offset)
        offset += len(co)
    body_points, body_faces = np.concatenate(points), np.concatenate(faces)
    garment_points, garment_faces = world_vertices(garment)
    body_tree = BVHTree.FromPolygons([tuple(p) for p in body_points],
                                     [tuple(f) for f in body_faces])
    garment_tree = BVHTree.FromPolygons([tuple(p) for p in garment_points],
                                        [tuple(f) for f in garment_faces])
    return body_points, body_faces, garment_points, body_tree, garment_tree


def vertex_normals(points, faces):
    n = np.zeros_like(points)
    a, b, c = points[faces[:, 0]], points[faces[:, 1]], points[faces[:, 2]]
    face = np.cross(b - a, c - a)
    for k in range(3):
        np.add.at(n, faces[:, k], face)
    return n / np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-12)


def covered(body_points, body_faces, garment_tree):
    """Which body vertices have the garment over them, looking out along the body's normal."""
    normals = vertex_normals(body_points, body_faces)
    hit = np.zeros(len(body_points), dtype=bool)
    for i, (p, n) in enumerate(zip(body_points, normals)):
        location = garment_tree.ray_cast(Vector(p), Vector(n), COVER_REACH)[0]
        hit[i] = location is not None
    return hit


def inside(garment_points, body_tree):
    """Which garment vertices are inside the body."""
    out = np.zeros(len(garment_points), dtype=bool)
    for i, p in enumerate(garment_points):
        location, normal, _, _ = body_tree.find_nearest(Vector(p))
        out[i] = (Vector(p) - location).dot(normal) < 0
    return out


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--garment", required=True)
    parser.add_argument("--body", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)

    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(args.body))
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    armature.data.pose_position = "POSE"
    # The saved clips play through animation tracks, which would override the test poses
    armature.animation_data_clear()
    pose(armature, {})
    bodies = [o for o in bpy.data.objects if o.type == "MESH" and "eye" not in o.name.lower()]
    garment = import_figure(args.garment)
    garment.name = "garment"
    bpy.ops.object.select_all(action="DESELECT")
    garment.select_set(True)
    bpy.context.view_layer.objects.active = garment
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    source = weight_source(bodies)
    transfer_weights(garment, source, armature)
    bpy.data.objects.remove(source)

    body_points, body_faces, garment_points, body_tree, garment_tree = surfaces(bodies, garment)
    at_rest = covered(body_points, body_faces, garment_tree)
    report = {"covered_body_vertices": int(at_rest.sum()), "poses": {}}

    flat = bpy.data.materials.new("garment")
    flat.diffuse_color = GARMENT_COLOR
    garment.data.materials.clear()
    garment.data.materials.append(flat)
    skin = bpy.data.materials.new("body")
    skin.diffuse_color = BODY_COLOR
    for obj in bodies:
        obj.data.materials.clear()
        obj.data.materials.append(skin)

    for k, (name, turns) in enumerate(POSES):
        pose(armature, turns)
        body_points, body_faces, garment_points, body_tree, garment_tree = surfaces(
            bodies, garment)
        now = covered(body_points, body_faces, garment_tree)
        shows = at_rest & ~now
        report["poses"][name] = {
            "body_showing_pct": round(float(shows.sum() / max(1, at_rest.sum())) * 100, 2),
            "garment_inside_pct": round(float(inside(garment_points, body_tree).mean()) * 100,
                                        2),
        }
        render(os.path.join(args.out, "pose_%d.png" % k), 30, "MATERIAL")
    print("[weight_layer]", json.dumps(report))
    with open(os.path.join(args.out, "weights.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)

    pose(armature, {})
    bpy.ops.object.select_all(action="DESELECT")
    armature.select_set(True)
    garment.select_set(True)
    bpy.ops.export_scene.gltf(filepath=os.path.join(args.out, "weighted.glb"),
                              export_format="GLB", use_selection=True, export_skins=True,
                              export_animations=False)


if __name__ == "__main__":
    main()
