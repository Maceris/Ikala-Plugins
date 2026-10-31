"""Close the mannequins' body slots at their seams with caps, for parts that don't weld there.

A body part either welds to its neighbor at a seam, its edge being the seam ring vertex for vertex,
or is open there, ending in a shape of its own, like a skeleton arm's bone at the shoulder. Where a
welded part meets an open one, the welded part shows a cap closing its ring (see the character
plugin's PartAssembler). Each slot mesh's caps are separate meshes in the same file, named
<mesh>_cap_<seam>: a fan from the ring to a middle standing out a little past it, so a closed limb
ends rounded, skinned like the ring with the middle taking its average weights.

mannequin.py adds caps as it builds the mannequins; this adds them to mannequins already built:

    blender -b --python add_caps.py -- <male.blend> [<female.blend> ...]

which re-exports each beside it as a .glb and saves the .blend. The seam rings come from the
<variant>_seams.json beside each.
"""

import json
import os
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Vector

# What joins a slot mesh's name to a seam's in its cap's name; the character plugin finds caps by it
CAP_INFIX = "_cap_"
# How far the cap's middle stands out past its ring, as a share of the ring's radius
BULGE = 0.3
# How far a slot mesh's open edge loop's middle may be from a seam ring's, in meters, to be it
MATCH = 0.02
# Bones each vertex may follow, as characters are drawn with
INFLUENCES = 4
# The slot meshes' name prefix, as mannequin.py names them
PREFIX = "mannequin_"


def export(path):
    """Write everything as a binary glTF file, with all bones and every clip."""
    options = {
        "filepath": path,
        "export_format": "GLB",
        "export_yup": True,
        "export_skins": True,
        "export_def_bones": False,
        "export_animations": True,
        "export_nla_strips": True,
        "export_force_sampling": True,
        "export_frame_range": False,
        "export_colors": False,
        "export_extras": False,
    }
    known = {p.identifier for p in bpy.ops.export_scene.gltf.get_rna_type().properties}
    bpy.ops.export_scene.gltf(**{key: value for key, value in options.items() if key in known})


def open_loops(mesh):
    """A mesh's open edge loops, each as its vertex indices in order around it."""
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bm.verts.ensure_lookup_table()
    next_of = {}
    for edge in bm.edges:
        if edge.is_boundary:
            a, b = (v.index for v in edge.verts)
            next_of.setdefault(a, []).append(b)
            next_of.setdefault(b, []).append(a)
    bm.free()
    loops, seen = [], set()
    for start in next_of:
        if start in seen:
            continue
        loop, previous, current = [start], None, start
        seen.add(start)
        while True:
            ahead = [v for v in next_of[current] if v != previous and v not in seen]
            if not ahead:
                break
            previous, current = current, ahead[0]
            loop.append(current)
            seen.add(current)
        if len(loop) >= 3:
            loops.append(loop)
    return loops


def make_cap(obj, loop, seam, armature):
    """A cap closing one of a slot mesh's open edge loops, as a new object beside it."""
    world = obj.matrix_world
    ring = np.array([world @ obj.data.vertices[i].co for i in loop])
    middle = ring.mean(0)
    _, _, axes = np.linalg.svd(ring - middle)
    normal = axes[2]
    # Out of the slot: away from the rest of its mesh
    body = np.array([world @ v.co for v in obj.data.vertices]).mean(0)
    if normal @ (middle - body) < 0:
        normal = -normal
    radius = np.linalg.norm(ring - middle, axis=1).mean()
    tip = middle + normal * radius * BULGE

    name = obj.name + CAP_INFIX + seam
    mesh = bpy.data.meshes.new(name)
    points = [tuple(p) for p in ring] + [tuple(tip)]
    count = len(ring)
    faces = []
    for k in range(count):
        a, b = k, (k + 1) % count
        face = (a, b, count)
        # Wind the fan to face out of the slot
        edge_a, edge_b = ring[b] - ring[a], tip - ring[a]
        if np.cross(edge_a, edge_b) @ normal < 0:
            face = (b, a, count)
        faces.append(face)
    mesh.from_pydata(points, [], faces)
    mesh.update()
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    for material in obj.data.materials:
        mesh.materials.append(material)
    if obj.data.uv_layers:
        source = obj.data.uv_layers.active.data
        uv_of = {}
        for polygon in obj.data.polygons:
            for vertex, loop_index in zip(polygon.vertices, polygon.loop_indices):
                uv_of.setdefault(vertex, tuple(source[loop_index].uv))
        uvs = [uv_of.get(i, (0.0, 0.0)) for i in loop]
        center_uv = tuple(np.mean(uvs, axis=0))
        layer = mesh.uv_layers.new(name=obj.data.uv_layers.active.name)
        for polygon in mesh.polygons:
            for vertex, loop_index in zip(polygon.vertices, polygon.loop_indices):
                layer.data[loop_index].uv = uvs[vertex] if vertex < count else center_uv

    cap = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(cap)
    names = {g.index: g.name for g in obj.vertex_groups}
    totals = {}
    for k, index in enumerate(loop):
        for entry in obj.data.vertices[index].groups:
            group = cap.vertex_groups.get(names[entry.group]) \
                or cap.vertex_groups.new(name=names[entry.group])
            group.add([k], entry.weight, "REPLACE")
            totals[names[entry.group]] = totals.get(names[entry.group], 0.0) + entry.weight
    strongest = sorted(totals.items(), key=lambda item: -item[1])[:INFLUENCES]
    share = sum(weight for _, weight in strongest) or 1.0
    for group_name, weight in strongest:
        cap.vertex_groups[group_name].add([count], weight / share, "REPLACE")
    cap.parent = armature
    cap.matrix_parent_inverse = armature.matrix_world.inverted()
    skin = cap.modifiers.new("Armature", "ARMATURE")
    skin.object = armature
    return cap


def add_caps(meshes, seams, armature):
    """Cap every open edge loop of the slot meshes that lies on a seam ring. Gives the caps."""
    rings = {name: np.array(points).mean(0) for name, points in seams.items()}
    caps = []
    for slot, obj in meshes.items():
        for loop in open_loops(obj.data):
            middle = np.array([obj.matrix_world @ obj.data.vertices[i].co for i in loop]).mean(0)
            seam, distance = min(((name, np.linalg.norm(middle - ring))
                                  for name, ring in rings.items()), key=lambda item: item[1])
            if distance > MATCH:
                continue
            caps.append(make_cap(obj, loop, seam, armature))
            print("[caps] %s: %s, %d vertices" % (slot, seam, len(loop)))
    return caps


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    for path in argv:
        path = os.path.abspath(path)
        bpy.ops.wm.open_mainfile(filepath=path)
        variant = os.path.splitext(os.path.basename(path))[0]
        with open(os.path.join(os.path.dirname(path), variant + "_seams.json"),
                  encoding="utf-8") as file:
            seams = json.load(file)["seams"]
        for obj in [o for o in bpy.data.objects if CAP_INFIX in o.name]:
            bpy.data.objects.remove(obj)
        armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
        meshes = {o.name[len(PREFIX):]: o for o in bpy.data.objects
                  if o.type == "MESH" and o.name.startswith(PREFIX)}
        caps = add_caps(meshes, seams, armature)
        print("[caps] %s: %d caps" % (variant, len(caps)))
        export(os.path.splitext(path)[0] + ".glb")
        bpy.ops.wm.save_as_mainfile(filepath=path)


if __name__ == "__main__":
    main()
