"""Give a shaped body part (see shape_body.py) skin weights, and check its seams hold in poses.

A body part's weights come from the mannequin slots it replaces, robustly, after Abdrashitov et
al.'s weight inpainting (SIGGRAPH Asia 2023): a vertex near those slots' skin and facing the
same way takes the weights at the nearest point of it, blended across that face; the rest, like
a mechanical arm's plates standing well out from it, are filled in smoothly from the vertices
around them that have weights, so nothing takes a far bone's weights by being nearest to the
wrong limb. A piece with no weights anywhere takes its nearest bone among the slots'. The ring
vertices welded to the mannequin's seams keep the ring's weights exactly, so the seam moves as
the neighbor does. Weights are limited to the four strongest per vertex, as characters are drawn
with.

The check poses the rig (arms down, forward and up, and seated) and measures how far each welded
seam's vertices move from the neighbor's ring: a seam that holds stays at 0.

    blender -b --python weight_body.py -- --shaped <shaped.blend> --out <folder>
        --slots lowerArm.L

writes weighted.glb (the part and its caps, skinned to the rig), weighted.blend, weights.json
and renders pose_0.png to pose_3.png of the part in each pose.
"""

import argparse
import json
import os
import sys

import bpy
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "blender"))
from add_caps import CAP_INFIX  # noqa: E402
from fit_body import plan_slots  # noqa: E402
from fit_layer import render  # noqa: E402
from shape_body import SEAM_GROUP  # noqa: E402
from shape_layer import bone_segments  # noqa: E402
from weight_layer import POSES, pose  # noqa: E402

# How near the mannequin's skin a vertex must be, in meters, to take its weights
REACH = 0.03
# How much a vertex must face the way the skin does, as the dot of their normals
FACING = 0.5
# Rounds of smoothing that fill in the vertices without weights
FILL_ROUNDS = 400
# Bones each vertex may follow, as characters are drawn with
INFLUENCES = 4
# The mannequin's slot meshes' name prefix, as mannequin.py names them
PREFIX = "mannequin_"
PART_COLOR = (0.85, 0.45, 0.15, 1)
BODY_COLOR = (0.7, 0.7, 0.72, 1)


def world_mesh(obj):
    """An object's vertices in world space, its triangles, and their normals."""
    obj.data.calc_loop_triangles()
    points = np.array([obj.matrix_world @ v.co for v in obj.data.vertices])
    triangles = np.array([t.vertices[:] for t in obj.data.loop_triangles])
    return points, triangles


def weights_of(obj, bones):
    """An object's weights, one row per vertex and a column per bone."""
    out = np.zeros((len(obj.data.vertices), len(bones)))
    column = {name: k for k, name in enumerate(bones)}
    names = {g.index: g.name for g in obj.vertex_groups}
    for vertex in obj.data.vertices:
        for entry in vertex.groups:
            name = names[entry.group]
            if name in column:
                out[vertex.index, column[name]] = entry.weight
    return out


def source_weights(slot_objects, bones):
    """The slots' skin as one surface: a tree of its triangles, its vertices' weights, and the
    triangles' vertex indices into them."""
    points, triangles, weights, offset = [], [], [], 0
    for obj in slot_objects:
        p, t = world_mesh(obj)
        points.append(p)
        triangles.append(t + offset)
        weights.append(weights_of(obj, bones))
        offset += len(p)
    points, triangles = np.concatenate(points), np.concatenate(triangles)
    surface = BVHTree.FromPolygons([tuple(p) for p in points], [tuple(t) for t in triangles])
    return surface, points, triangles, np.concatenate(weights)


def barycentric(point, a, b, c):
    """A point's barycentric coordinates in a triangle."""
    v0, v1, v2 = b - a, c - a, point - a
    d00, d01, d11 = v0 @ v0, v0 @ v1, v1 @ v1
    d20, d21 = v2 @ v0, v2 @ v1
    denominator = d00 * d11 - d01 * d01
    if abs(denominator) < 1e-15:
        return np.array([1.0, 0.0, 0.0])
    v = (d11 * d20 - d01 * d21) / denominator
    w = (d00 * d21 - d01 * d20) / denominator
    return np.clip(np.array([1 - v - w, v, w]), 0, 1)


def transfer(part, source, bones):
    """The part's weights from the slots' skin where it is near and faces the same way. Gives
    the weights and which vertices took them."""
    surface, points, triangles, weights = source
    count = len(part.data.vertices)
    out = np.zeros((count, len(bones)))
    known = np.zeros(count, dtype=bool)
    for vertex in part.data.vertices:
        p = part.matrix_world @ vertex.co
        location, normal, index, distance = surface.find_nearest(p)
        if location is None or distance > REACH:
            continue
        if Vector(vertex.normal).dot(normal) < FACING:
            continue
        corners = triangles[index]
        share = barycentric(np.array(location), *points[corners])
        out[vertex.index] = share @ weights[corners]
        known[vertex.index] = out[vertex.index].sum() > 1e-6
    return out, known


def fill(part, weights, known):
    """Fill in the weights of vertices without them, as the average of their neighbors', round
    after round, the known ones held."""
    edges = np.array([e.vertices[:] for e in part.data.edges])
    neighbors = np.zeros(len(weights))
    np.add.at(neighbors, edges[:, 0], 1)
    np.add.at(neighbors, edges[:, 1], 1)
    for _ in range(FILL_ROUNDS):
        sums = np.zeros_like(weights)
        np.add.at(sums, edges[:, 0], weights[edges[:, 1]])
        np.add.at(sums, edges[:, 1], weights[edges[:, 0]])
        average = sums / np.maximum(neighbors, 1)[:, None]
        weights[~known] = average[~known]
    return weights


def limited(weights):
    """Weights with only the INFLUENCES strongest kept per vertex, summing to 1."""
    out = np.zeros_like(weights)
    strongest = np.argsort(-weights, axis=1)[:, :INFLUENCES]
    rows = np.arange(len(weights))[:, None]
    out[rows, strongest] = weights[rows, strongest]
    return out / np.maximum(out.sum(1, keepdims=True), 1e-12)


def skin_part(part, slot_objects, armature, own):
    """Give a part the weights of the slot meshes it replaces (see the module's comment), its
    ring vertices (in SEAM_GROUP) keeping theirs, and skin it to the armature; own are the slots'
    bones, for pieces with no weights. Gives which vertices are the ring's, how many took their
    weights from the skin or the ring, and which took their nearest bone."""
    segments = bone_segments(armature)
    bones = sorted({g.name for o in slot_objects for g in o.vertex_groups}
                   | {g.name for g in part.vertex_groups if g.name != SEAM_GROUP})
    # The ring vertices' weights, already on the part from the weld
    ring = np.zeros(len(part.data.vertices), dtype=bool)
    if SEAM_GROUP in part.vertex_groups:
        seam_index = part.vertex_groups[SEAM_GROUP].index
        for vertex in part.data.vertices:
            ring[vertex.index] = any(g.group == seam_index for g in vertex.groups)
    ring_weights = weights_of(part, bones)

    weights, known = transfer(part, source_weights(slot_objects, bones), bones)
    weights[ring] = ring_weights[ring]
    known |= ring
    taken = int(known.sum())
    weights = fill(part, weights, known)
    # Pieces with no weights anywhere follow their nearest bone among the slots'
    empty = weights.sum(1) < 1e-6
    if empty.any():
        points = np.array([part.matrix_world @ part.data.vertices[i].co
                           for i in np.flatnonzero(empty)])
        distances = []
        for bone in own:
            head, tail = segments[bone]
            along = tail - head
            share = np.clip((points - head) @ along / (along @ along), 0, 1)
            distances.append(np.linalg.norm(points - head - np.outer(share, along), axis=1))
        nearest_bone = np.array(own)[np.argmin(distances, axis=0)]
        for k, index in enumerate(np.flatnonzero(empty)):
            weights[index, bones.index(nearest_bone[k])] = 1.0
    weights = limited(weights)
    weights[ring] = ring_weights[ring]

    for group in list(part.vertex_groups):
        if group.name != SEAM_GROUP:
            part.vertex_groups.remove(group)
    for column, bone in enumerate(bones):
        rows = np.flatnonzero(weights[:, column] > 1e-4)
        if not len(rows):
            continue
        group = part.vertex_groups.new(name=bone)
        for row in rows:
            group.add([int(row)], float(weights[row, column]), "REPLACE")
    part.parent = armature
    part.matrix_parent_inverse = armature.matrix_world.inverted()
    if not any(m.type == "ARMATURE" for m in part.modifiers):
        part.modifiers.new("Armature", "ARMATURE").object = armature
    return ring, taken, empty


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--shaped", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--slots", required=True, help="Comma-separated slots the part fills.")
    args = parser.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)
    names = [s for s in args.slots.split(",") if s]

    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(args.shaped))
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    armature.animation_data_clear()
    armature.data.pose_position = "REST"
    pose(armature, {})
    bpy.context.view_layer.update()
    segments = bone_segments(armature)
    part = bpy.data.objects["part"]
    caps = [o for o in bpy.data.objects if o.name.startswith("part" + CAP_INFIX)]
    slots = plan_slots()
    slot_objects = [bpy.data.objects[PREFIX + n] for n in names]
    own = [b for n in names for b in slots[n]["bones"] if b in segments]

    ring, taken, empty = skin_part(part, slot_objects, armature, own)
    # Each part ring vertex's twin on the neighbor across the seam, the same point at rest
    others = [o for o in bpy.data.objects
              if o.type == "MESH" and o.name.startswith(PREFIX) and CAP_INFIX not in o.name
              and o.name[len(PREFIX):] not in names]
    twins = []
    for index in np.flatnonzero(ring):
        p = part.matrix_world @ part.data.vertices[int(index)].co
        best = min(((o, v.index, (o.matrix_world @ v.co - p).length)
                    for o in others for v in o.data.vertices), key=lambda item: item[2])
        if best[2] < 1e-4:
            twins.append((int(index), best[0], best[1]))

    def evaluated(obj):
        mesh = obj.evaluated_get(bpy.context.evaluated_depsgraph_get()).to_mesh()
        points = np.array([obj.matrix_world @ v.co for v in mesh.vertices])
        obj.evaluated_get(bpy.context.evaluated_depsgraph_get()).to_mesh_clear()
        return points

    flat = bpy.data.materials.new("part")
    flat.diffuse_color = PART_COLOR
    skin = bpy.data.materials.new("body")
    skin.diffuse_color = BODY_COLOR
    for obj in bpy.data.objects:
        if obj.type != "MESH":
            continue
        obj.data.materials.clear()
        obj.data.materials.append(flat if obj is part or obj in caps else skin)
        obj.hide_render = obj in caps or CAP_INFIX in obj.name or obj in slot_objects

    report = {"vertices": len(ring), "from_skin": taken - int(ring.sum()),
              "ring": int(ring.sum()), "twins": len(twins), "filled": len(ring) - taken,
              "nearest_bone": int(empty.sum()), "poses": {}}
    armature.data.pose_position = "POSE"
    for k, (label, turns) in enumerate(POSES):
        pose(armature, turns)
        part_points = evaluated(part)
        cache = {}
        gap = 0.0
        for index, other, twin in twins:
            if other.name not in cache:
                cache[other.name] = evaluated(other)
            gap = max(gap, float(np.linalg.norm(part_points[index] - cache[other.name][twin])))
        report["poses"][label] = {"seam_gap_mm": round(gap * 1000, 3)}
        render(os.path.join(args.out, "pose_%d.png" % k), 30, "MATERIAL")
    pose(armature, {})
    armature.data.pose_position = "REST"
    print("[weight_body]", json.dumps(report))
    with open(os.path.join(args.out, "weights.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)

    bpy.ops.object.select_all(action="DESELECT")
    for obj in [part, armature] + caps:
        obj.select_set(True)
    options = {"filepath": os.path.join(args.out, "weighted.glb"), "export_format": "GLB",
               "use_selection": True, "export_yup": True, "export_skins": True,
               "export_def_bones": False, "export_animations": False}
    known_options = {p.identifier for p in bpy.ops.export_scene.gltf.get_rna_type().properties}
    bpy.ops.export_scene.gltf(**{k: v for k, v in options.items() if k in known_options})
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(args.out, "weighted.blend"))


if __name__ == "__main__":
    main()
