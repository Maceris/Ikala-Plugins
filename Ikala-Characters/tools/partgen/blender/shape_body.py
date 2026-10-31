"""Shape a body part cut out of its figure (see fit_body.py) into a closed solid that meets its
neighbors at its seams.

A body part replaces the body, so unlike clothing it isn't pushed out of it. TRELLIS.2's meshes
are sheets with two sides, so the cut part's faces are first turned to look away from its slots'
bones, its open ends and holes filled, and it is rebuilt as a voxel solid, keeping only the
outer surface, then reduced to its triangle budget.

At each outer seam (see fit_body.py) the part welds to its neighbor, unless the brief leaves the
seam open:
- weld: the solid is cut WELD_INSET short of the seam's plane, and that edge bridged to the
  mannequin's own ring vertices, taking their positions, normals and skin weights, so the seam is
  the neighbor's exactly. A cap closing the ring is added beside it, as on the mannequin (see
  tools/blender/add_caps.py), for when the neighbor across is open.
- open: the solid keeps its own closed end, like a skeleton arm's bone at the shoulder.

    blender -b --python shape_body.py -- --part <part.glb> --body <male.blend> --out <folder>
        --slots lowerArm.L --triangles 1500 [--open wrist.L,elbow.L]

writes shaped.glb (the part and its caps, unskinned), shaped.blend (the mannequin with the part
and its caps, the ring vertices in a "seam" vertex group with the ring's weights, for
weight_body.py), shape.json and renders preview_4.png and preview_5.png: the part on the
mannequin from the front and side.
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
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "blender"))
from add_caps import CAP_INFIX, make_cap, open_loops  # noqa: E402
from bodies import load_body, nearest, tree  # noqa: E402
from fit_body import outer_seams, plan_slots, seam_plane  # noqa: E402
from fit_layer import import_figure, render  # noqa: E402
from shape_layer import bone_segments, points_of, reduce  # noqa: E402

# The voxel size the part is rebuilt at, in meters, and the thickness its sheets are given first,
# in voxels, so their thinnest parts survive
VOXEL = 0.005
THICKNESS = 2.0
# How far short of a welded seam's plane the solid is cut, in meters, for the band that bridges
# it to the ring
WELD_INSET = 0.01
# The vertex group the ring vertices are in, which the weights keep exactly
SEAM_GROUP = "seam"
PART_COLOR = (0.85, 0.45, 0.15, 1)
BODY_COLOR = (0.7, 0.7, 0.72, 1)


def nearest_on_bones(points, segments, bones):
    """The nearest point on any of some bones to each point."""
    best = np.full(len(points), np.inf)
    out = np.zeros_like(points)
    for bone in bones:
        head, tail = segments[bone]
        along = tail - head
        share = np.clip((points - head) @ along / max(along @ along, 1e-12), 0, 1)
        foot = head + np.outer(share, along)
        distance = np.linalg.norm(points - foot, axis=1)
        closer = distance < best
        best[closer] = distance[closer]
        out[closer] = foot[closer]
    return out


def face_out(mesh, segments, bones):
    """Turn a bmesh's faces to look away from the nearest of some bones."""
    middles = np.array([f.calc_center_median() for f in mesh.faces])
    feet = nearest_on_bones(middles, segments, bones)
    inward = [f for f, middle, foot in zip(mesh.faces, middles, feet)
              if f.normal.dot(Vector(middle - foot)) < 0]
    bmesh.ops.reverse_faces(mesh, faces=inward)


def solidify(obj, segments, bones):
    """Rebuild the part as a closed voxel solid, keeping its outer surface. Gives how many
    triangles it has."""
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    face_out(mesh, segments, bones)
    boundary = [e for e in mesh.edges if e.is_boundary]
    bmesh.ops.holes_fill(mesh, edges=boundary, sides=0)
    mesh.to_mesh(obj.data)
    mesh.free()
    bpy.context.view_layer.objects.active = obj
    thicken = obj.modifiers.new("thicken", "SOLIDIFY")
    thicken.thickness = VOXEL * THICKNESS
    thicken.offset = -1
    bpy.ops.object.modifier_apply(modifier=thicken.name)
    remesh = obj.modifiers.new("remesh", "REMESH")
    remesh.mode = "VOXEL"
    remesh.voxel_size = VOXEL
    bpy.ops.object.modifier_apply(modifier=remesh.name)
    # Inner walls come out as closed surfaces of their own, inside the outer one
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    pieces, seen = [], set()
    for start in mesh.faces:
        if start.index in seen:
            continue
        piece, stack = [], [start]
        seen.add(start.index)
        while stack:
            face = stack.pop()
            piece.append(face)
            for edge in face.edges:
                for other in edge.link_faces:
                    if other.index not in seen:
                        seen.add(other.index)
                        stack.append(other)
        pieces.append(piece)
    outer = max(pieces, key=lambda p: sum(f.calc_area() for f in p))
    inner = [f for p in pieces if p is not outer for f in p]
    bmesh.ops.delete(mesh, geom=inner, context="FACES")
    mesh.to_mesh(obj.data)
    mesh.free()
    obj.data.calc_loop_triangles()
    return len(obj.data.loop_triangles)


def mannequin_ring(slot_object, middle):
    """The mannequin slot mesh's edge loop at a seam: its vertices' positions, normals and
    weights by bone, in order around it."""
    loops = open_loops(slot_object.data)
    world = slot_object.matrix_world
    centers = [np.mean([world @ slot_object.data.vertices[i].co for i in loop], axis=0)
               for loop in loops]
    loop = loops[int(np.argmin([np.linalg.norm(c - middle) for c in centers]))]
    mesh = slot_object.data
    normals = np.zeros((len(mesh.vertices), 3))
    for corner, normal in zip(mesh.loops, mesh.corner_normals):
        normals[corner.vertex_index] += np.array(normal.vector)
    names = {g.index: g.name for g in slot_object.vertex_groups}
    ring = []
    for index in loop:
        vertex = mesh.vertices[index]
        normal = world.to_3x3() @ Vector(normals[index])
        ring.append({"co": np.array(world @ vertex.co),
                     "normal": np.array(normal.normalized()),
                     "weights": {names[g.group]: g.weight for g in vertex.groups}})
    return ring


def angles_about(points, middle, normal):
    """Each point's angle about an axis through the middle along the normal."""
    u = np.cross(normal, [0, 0, 1] if abs(normal[2]) < 0.9 else [1, 0, 0])
    u /= np.linalg.norm(u)
    v = np.cross(normal, u)
    offsets = np.asarray(points) - middle
    return np.arctan2(offsets @ v, offsets @ u)


def unwrapped(angles):
    """A loop's angles, each step taken the short way round, so they run on past 2 pi."""
    steps = np.diff(angles, append=angles[:1])
    steps = (steps + np.pi) % (2 * np.pi) - np.pi
    return np.concatenate([[angles[0]], angles[0] + np.cumsum(steps)[:-1]]), steps.sum()


def around(points, middle, normal):
    """A loop's points, in order, turned to run the positive way round the axis, starting
    nearest angle 0, with their unwrapped angles from 0 to 2 pi. None if it doesn't go round."""
    angles, total = unwrapped(angles_about(points, middle, normal))
    if abs(total) < np.pi:
        return None
    order = list(range(len(points)))
    if total < 0:
        order.reverse()
        angles = -angles[::-1]
    start = int(np.argmin(np.mod(angles, 2 * np.pi)))
    order = order[start:] + order[:start]
    angles = np.concatenate([angles[start:], angles[:start] + 2 * np.pi])
    return order, angles - angles[0] + np.mod(angles[0], 2 * np.pi)


def trim(obj, plane):
    """Cut the part WELD_INSET short of a seam's plane, dropping what lies past it."""
    middle, normal = np.array(plane["middle"]), np.array(plane["normal"])
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    geom = list(mesh.verts) + list(mesh.edges) + list(mesh.faces)
    bmesh.ops.bisect_plane(mesh, geom=geom, plane_co=Vector(middle - normal * WELD_INSET),
                           plane_no=Vector(normal), clear_outer=True, dist=1e-6)
    mesh.to_mesh(obj.data)
    mesh.free()


def bridge(obj, seam, plane, ring):
    """Bridge the part's edge where it was trimmed short of a seam to the ring: the edge loop on
    the cut that goes round the limb, any other openings the cut made being filled. It only adds
    vertices, so indices found before stay good. Gives the ring vertices' indices, in the ring's
    order."""
    middle, normal = np.array(plane["middle"]), np.array(plane["normal"])
    on_cut = []
    for loop in open_loops(obj.data):
        points = np.array([obj.data.vertices[i].co for i in loop])
        if np.all(np.abs((points - middle) @ normal + WELD_INSET) < 1e-3):
            on_cut.append((loop, around(points, middle, normal)))
    rounds = [(loop, way) for loop, way in on_cut if way is not None]
    if not rounds:
        raise SystemExit("The part doesn't reach round the seam %s" % seam)
    main_loop, (cut_order, cut_angles) = max(rounds, key=lambda item: len(item[0]))
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    mesh.verts.ensure_lookup_table()
    others = [loop for loop, _ in on_cut if loop is not main_loop]
    if others:
        verts = {mesh.verts[i] for loop in others for i in loop}
        edges = [e for e in mesh.edges if e.is_boundary and all(v in verts for v in e.verts)]
        bmesh.ops.holes_fill(mesh, edges=edges, sides=0)
    mesh.verts.ensure_lookup_table()
    cut = [mesh.verts[main_loop[k]] for k in cut_order]
    ring_way = around(np.array([r["co"] for r in ring]), middle, normal)
    if ring_way is None:
        raise SystemExit("The mannequin's ring at %s doesn't go round its own axis" % seam)
    ring_order, ring_angles = ring_way
    ring_verts = [mesh.verts.new(Vector(ring[k]["co"])) for k in ring_order]
    # Zip the two loops by angle, a triangle at a time, each running once round
    count_cut, count_ring = len(cut), len(ring_verts)
    cut_next = np.append(cut_angles[1:], cut_angles[0] + 2 * np.pi)
    ring_next = np.append(ring_angles[1:], ring_angles[0] + 2 * np.pi)
    i = j = 0
    while i < count_cut or j < count_ring:
        if j >= count_ring or (i < count_cut and cut_next[i] <= ring_next[j]):
            face = (cut[i], cut[(i + 1) % count_cut], ring_verts[j % count_ring])
            i += 1
        else:
            face = (cut[i % count_cut], ring_verts[(j + 1) % count_ring], ring_verts[j])
            j += 1
        try:
            mesh.faces.new(face)
        except ValueError:
            pass
    mesh.verts.index_update()
    indices = [v.index for v in ring_verts]
    mesh.to_mesh(obj.data)
    mesh.free()
    return [indices[list(ring_order).index(k)] for k in range(len(ring))]


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--part", required=True)
    parser.add_argument("--body", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--slots", required=True, help="Comma-separated slots the part fills.")
    parser.add_argument("--triangles", type=int, required=True)
    parser.add_argument("--open", default="", help="Comma-separated seams left open.")
    args = parser.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)
    names = [s for s in args.slots.split(",") if s]
    left_open = {s for s in args.open.split(",") if s}

    (body, body_faces), _ = load_body(args.body)
    surface = tree(body, body_faces)
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    armature.animation_data_clear()
    segments = bone_segments(armature)
    slots = plan_slots()
    bones = [b for name in names for b in slots[name]["bones"] if b in segments]
    with open(os.path.splitext(os.path.abspath(args.body))[0] + "_seams.json",
              encoding="utf-8") as file:
        rings = json.load(file)["seams"]
    seams = outer_seams(names)
    unknown = left_open - set(seams)
    if unknown:
        raise SystemExit("%s aren't seams where the part meets a neighbor; those are %s"
                         % (", ".join(sorted(unknown)), ", ".join(seams)))

    part = import_figure(args.part)
    part.name = part.data.name = "part"
    bpy.ops.object.select_all(action="DESELECT")
    part.select_set(True)
    bpy.context.view_layer.objects.active = part
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    rebuilt = solidify(part, segments, bones)
    triangles = reduce(part, args.triangles)

    inside = np.mean([np.mean(segments[b], axis=0) for b in bones], axis=0)
    planes = {}
    for seam in seams:
        if seam in left_open:
            continue
        middle, normal, radius = seam_plane(rings[seam], inside)
        planes[seam] = {"middle": middle, "normal": normal, "radius": radius}
        trim(part, planes[seam])
    welded = {}
    for seam, plane in planes.items():
        slot = next(n for n in names if seam in slots[n].get("seams", []))
        ring = mannequin_ring(bpy.data.objects["mannequin_" + slot], plane["middle"])
        welded[seam] = (bridge(part, seam, plane, ring), ring)

    # Turn the faces out again, the bridges among them, then copy the rings' normals and weights
    mesh = bmesh.new()
    mesh.from_mesh(part.data)
    face_out(mesh, segments, bones)
    mesh.to_mesh(part.data)
    mesh.free()
    for polygon in part.data.polygons:
        polygon.use_smooth = True
    seam_group = part.vertex_groups.new(name=SEAM_GROUP)
    normals = [Vector(n) for n in np.array([v.normal for v in part.data.vertices])]
    for indices, ring in welded.values():
        seam_group.add(indices, 1.0, "REPLACE")
        for index, entry in zip(indices, ring):
            normals[index] = Vector(entry["normal"])
            for bone, weight in entry["weights"].items():
                group = part.vertex_groups.get(bone) or part.vertex_groups.new(name=bone)
                group.add([index], weight, "REPLACE")
    part.data.normals_split_custom_set_from_vertices(normals)

    caps = []
    for seam, (indices, _) in welded.items():
        loop = next(lp for lp in open_loops(part.data) if set(lp) == set(indices))
        caps.append(make_cap(part, loop, seam, armature))

    points = points_of(part)
    _, signed = nearest(points, surface)
    ring_error = max((float(np.max(np.linalg.norm(
        np.array([part.data.vertices[i].co for i in indices])
        - np.array([r["co"] for r in ring]), axis=1)))
        for indices, ring in welded.values()), default=0.0)
    part.data.calc_loop_triangles()
    report = {
        "triangles_rebuilt": rebuilt,
        "triangles": len(part.data.loop_triangles),
        "triangles_reduced": triangles,
        "welded": sorted(welded),
        "open": sorted(left_open),
        "ring_error_mm": round(ring_error * 1000, 3),
        "stands_out_mm": {"p50": round(float(np.percentile(signed, 50)) * 1000, 1),
                          "p95": round(float(np.percentile(signed, 95)) * 1000, 1)},
        "inside_pct": round(float((signed < -0.005).mean()) * 100, 1),
        # The welded rings, and any holes left, as how many vertices each has
        "open_edges": sorted(len(lp) for lp in open_loops(part.data)),
    }
    print("[shape_body]", json.dumps(report))
    with open(os.path.join(args.out, "shape.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)

    bpy.ops.object.select_all(action="DESELECT")
    for obj in [part] + caps:
        obj.select_set(True)
    bpy.ops.export_scene.gltf(filepath=os.path.join(args.out, "shaped.glb"),
                              export_format="GLB", use_selection=True)
    # glTF keeps vertex groups only on skinned meshes, so the ring's weights go on in a .blend
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(args.out, "shaped.blend"))
    flat = bpy.data.materials.new("part")
    flat.diffuse_color = PART_COLOR
    skin = bpy.data.materials.new("body")
    skin.diffuse_color = BODY_COLOR
    for obj in bpy.data.objects:
        if obj.type != "MESH":
            continue
        if obj is part or obj in caps:
            obj.data.materials.clear()
            obj.data.materials.append(flat)
        else:
            obj.data.materials.clear()
            obj.data.materials.append(skin)
            # The slots the part fills, and the mannequin's caps, are what it replaces
            obj.hide_render = (CAP_INFIX in obj.name
                               or any(obj.name == "mannequin_" + n for n in names))
    for k, view in enumerate((0, 90)):
        render(os.path.join(args.out, "preview_%d.png" % (k + 4)), view, "MATERIAL")


if __name__ == "__main__":
    main()
