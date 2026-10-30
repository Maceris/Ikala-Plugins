"""Shape a garment cut out of a generated figure (see fit_layer.py) into one that fits over the
mannequin: nothing of it inside the body or nearer than a gap, smooth, and within a triangle
budget.

TRELLIS.2 makes limbs a little thinner than the mannequin's, so parts of a garment come out
inside the body, like sleeves inside the arms. Every vertex nearer the body than the gap is
pushed out along the body's surface normal to the gap, the pushed ones smoothed so they don't
crease, and pushed again. The garment is then rebuilt and reduced to its triangle budget: reducing
TRELLIS's tangled mesh directly tears it into spikes and webs, so it is first given a little
thickness inward, rebuilt as a clean voxel mesh, and its inner surface (the side facing the
body) dropped, leaving one shell to reduce. That loses TRELLIS's texture layout, which the bake
replaces. It is then pushed once more, as reducing can tuck some back in.

    blender -b --python shape_layer.py -- --garment <garment.glb> --body <male.blend>
        --out <folder> --gap 0.004 --triangles 2000

writes shaped.glb, pushed.glb (the garment pushed out at full detail, before it is rebuilt, for
the bake), shape.json and renders preview_6.png and preview_7.png: the garment on the
mannequin from the front and side, in a flat color until it is baked.
"""

import argparse
import json
import math
import os
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bodies import load_body, nearest, tree, world_vertices  # noqa: E402
from fit_layer import import_figure, render  # noqa: E402

# How many times pushed vertices are smoothed and pushed again
ROUNDS = 3
# How strongly each smoothing pass pulls a vertex toward its neighbors
SMOOTH = 0.5
# A vertex this far inside the gap after shaping counts as too near, in meters
SLACK = 0.001
# The voxel size the garment is rebuilt at, in meters, and the thickness it is given first, in
# voxels, so its thinnest parts survive
VOXEL = 0.006
THICKNESS = 2.5
# The previews' colors: the garment, unbaked, and the body
GARMENT_COLOR = (0.85, 0.45, 0.15, 1)
BODY_COLOR = (0.7, 0.7, 0.72, 1)



def push_out(points, surface, gap):
    """Move points nearer the body than the gap out along its normal to the gap. Gives the moved
    points and which moved."""
    moved = points.copy()
    pushed = np.zeros(len(points), dtype=bool)
    for i, p in enumerate(points):
        location, normal, _, distance = surface.find_nearest(Vector(p))
        offset = Vector(p) - location
        signed = distance if offset.dot(normal) >= 0 else -distance
        if signed < gap:
            moved[i] = location + normal * gap
            pushed[i] = True
    return moved, pushed


def set_points(obj, points):
    obj.data.vertices.foreach_set("co", points.astype(np.float32).ravel())
    obj.data.update()


def smooth(obj, which):
    """Pull some vertices toward their neighbors' middle."""
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    mesh.verts.ensure_lookup_table()
    verts = [mesh.verts[i] for i in np.flatnonzero(which)]
    bmesh.ops.smooth_vert(mesh, verts=verts, factor=SMOOTH, use_axis_x=True, use_axis_y=True,
                          use_axis_z=True)
    mesh.to_mesh(obj.data)
    mesh.free()


def points_of(obj):
    co = np.empty(len(obj.data.vertices) * 3)
    obj.data.vertices.foreach_get("co", co)
    return co.reshape(-1, 3)


def shape(obj, surface, gap):
    """Push and smooth until nothing is nearer the body than the gap. Gives how many moved."""
    total = np.zeros(len(obj.data.vertices), dtype=bool)
    for _ in range(ROUNDS):
        points, pushed = push_out(points_of(obj), surface, gap)
        set_points(obj, points)
        total |= pushed
        smooth(obj, total)
    points, pushed = push_out(points_of(obj), surface, gap)
    set_points(obj, points)
    return int((total | pushed).sum())


def rebuild(obj, surface):
    """Rebuild a garment shell as a clean mesh: thickened inward, remeshed in voxels, and its
    faces that look toward the body dropped. Gives how many triangles it has."""
    bpy.context.view_layer.objects.active = obj
    thicken = obj.modifiers.new("thicken", "SOLIDIFY")
    thicken.thickness = VOXEL * THICKNESS
    thicken.offset = -1
    bpy.ops.object.modifier_apply(modifier=thicken.name)
    remesh = obj.modifiers.new("remesh", "REMESH")
    remesh.mode = "VOXEL"
    remesh.voxel_size = VOXEL
    bpy.ops.object.modifier_apply(modifier=remesh.name)
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    inner = []
    for face in mesh.faces:
        location, normal, _, _ = surface.find_nearest(face.calc_center_median())
        toward = location - face.calc_center_median()
        if face.normal.dot(normal) < 0 and face.normal.dot(toward) > 0:
            inner.append(face)
    bmesh.ops.delete(mesh, geom=inner, context="FACES")
    mesh.to_mesh(obj.data)
    mesh.free()
    obj.data.calc_loop_triangles()
    return len(obj.data.loop_triangles)


def reduce(obj, triangles):
    """Collapse edges until the mesh has about this many triangles."""
    obj.data.calc_loop_triangles()
    count = len(obj.data.loop_triangles)
    if count <= triangles:
        return count
    modifier = obj.modifiers.new("reduce", "DECIMATE")
    modifier.decimate_type = "COLLAPSE"
    modifier.ratio = triangles / count
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.modifier_apply(modifier=modifier.name)
    obj.data.calc_loop_triangles()
    return len(obj.data.loop_triangles)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--garment", required=True)
    parser.add_argument("--body", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--gap", type=float, required=True)
    parser.add_argument("--triangles", type=int, required=True)
    args = parser.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)

    (body, body_faces), _ = load_body(args.body)
    surface = tree(body, body_faces)
    garment = import_figure(args.garment)
    garment.name = "garment"
    # Work in the garment's own coordinates, which are the body's once its transform is applied
    bpy.ops.object.select_all(action="DESELECT")
    garment.select_set(True)
    bpy.context.view_layer.objects.active = garment
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

    garment.data.calc_loop_triangles()
    before = len(garment.data.loop_triangles)
    _, start = nearest(points_of(garment), surface)
    moved = shape(garment, surface, args.gap)
    # The pushed garment at full detail, which the bake takes normals and texture from: it lies
    # where the rebuilt one does, so bake rays find the right surface
    bpy.ops.object.select_all(action="DESELECT")
    garment.select_set(True)
    bpy.ops.export_scene.gltf(filepath=os.path.join(args.out, "pushed.glb"),
                              export_format="GLB", use_selection=True)
    rebuilt = rebuild(garment, surface)
    triangles = reduce(garment, args.triangles)
    shape(garment, surface, args.gap)
    _, end = nearest(points_of(garment), surface)
    report = {
        "triangles_before": before,
        "triangles_rebuilt": rebuilt,
        "triangles": triangles,
        "inside_before_pct": round(float((start < 0).mean()) * 100, 1),
        "pushed_pct": round(moved / max(1, len(start)) * 100, 1),
        "too_near_pct": round(float((end < args.gap - SLACK).mean()) * 100, 2),
        "min_gap_mm": round(float(end.min()) * 1000, 1),
    }
    print("[shape_layer]", json.dumps(report))
    with open(os.path.join(args.out, "shape.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)

    for polygon in garment.data.polygons:
        polygon.use_smooth = True
    bpy.ops.object.select_all(action="DESELECT")
    garment.select_set(True)
    bpy.ops.export_scene.gltf(filepath=os.path.join(args.out, "shaped.glb"),
                              export_format="GLB", use_selection=True)
    flat = bpy.data.materials.new("garment")
    flat.diffuse_color = GARMENT_COLOR
    garment.data.materials.clear()
    garment.data.materials.append(flat)
    skin = bpy.data.materials.new("body")
    skin.diffuse_color = BODY_COLOR
    for obj in bpy.data.objects:
        if obj.type == "MESH" and obj is not garment:
            obj.data.materials.clear()
            obj.data.materials.append(skin)
    for k, view in enumerate((0, 90)):
        render(os.path.join(args.out, "preview_%d.png" % (k + 6)), view, "MATERIAL")


if __name__ == "__main__":
    main()
