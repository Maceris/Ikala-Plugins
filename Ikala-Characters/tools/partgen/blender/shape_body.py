"""Shape a body part cut out of its figure (see fit_body.py) into a closed solid that meets its
neighbors at its seams.

A body part replaces the body, so unlike clothing it isn't pushed out of it. TRELLIS.2's meshes
are sheets with two sides, so the cut part's faces are first turned to look away from its slots'
bones, its open ends and holes filled, and it is rebuilt as a voxel solid, keeping only the
outer surface, then reduced to its triangle budget.

At each outer seam (see fit_body.py) the part welds to its neighbor, unless the brief leaves the
seam open:
- weld: the solid is cut WELD_INSETS short of the seam's plane (the first that leaves an edge
  going round the limb: a cut through an instep or a cuff's slanted rim may not), and that
  edge bridged to the
  mannequin's own ring vertices, taking their positions, normals and skin weights, so the seam is
  the neighbor's exactly. A cap closing the ring is added beside it, as on the mannequin (see
  tools/blender/add_caps.py), for when the neighbor across is open.
- open: the solid keeps its own closed end, like a skeleton arm's bone at the shoulder.

    blender -b --python shape_body.py -- --part <part.glb> --body <male.blend> --out <folder>
        --slots lowerArm.L --triangles 1500 [--open wrist.L,elbow.L] [--voxel 0.005]

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
from bodies import load_body, merged, nearest, tree  # noqa: E402
from fit_body import (clothed_slots, form_for, outer_seams, plan_forms, plan_slots,  # noqa: E402
                      seam_plane, slot_radii)
from fit_layer import import_figure, render  # noqa: E402
from shape_layer import bone_segments, points_of, reduce, set_points  # noqa: E402

# The voxel size the part is rebuilt at, in meters, and the thickness its sheets are given first,
# in voxels, so their thinnest parts survive
VOXEL = 0.005
THICKNESS = 2.0
# How far short of a welded seam's plane the solid may be cut, in meters, for the band that
# bridges it to the ring, the nearest first
WELD_INSETS = (0.01, 0.02, 0.03, 0.04)
# The vertex group the ring vertices are in, which the weights keep exactly
SEAM_GROUP = "seam"
PART_COLOR = (0.85, 0.45, 0.15, 1)
# A part's clothing form is the thinnest whose shell holds this share of its surface over each
# slot
FORM_SHARE = 0.98
# How far past a form's shell that share may stand and still count as in it, as a share of
# the slot's radius
FORM_TOLERANCE = 0.01
# Past the thickest form over a clothed slot: rein the part in (or reject it if that is too much),
# let it be as it is because it is worn bare, or reject it
BEYOND = ("clamp", "bare", "reject")
# Reining in a part that stands out past the thickest form over a clothed slot scales how far it
# stands out down, as far as this share of it unless --rein-least says otherwise; one needing
# more is rejected unless it is worn bare
REIN_LEAST = 0.5
# Reining in scales so this share of each clothed slot's points fits, and what is left past the
# limit, lumps and spikes, is clamped
REIN_SHARE = 0.95
# How many times the reining in is averaged with neighbors', so it eases across slots' seams
REIN_SMOOTH = 8
# How far inside the limit clamping starts to ease points in, in meters
CLAMP_KNEE = 0.01
# How far a leg's sole may be from the floor, the mannequin's lowest point, before it is stretched
FLOOR_TOLERANCE = 0.005
# How much a leg may be stretched or shrunk to meet the floor; past this the fit is wrong, not
# the leg's length, and the part is rejected
FLOOR_RANGE = (0.8, 1.25)
# How far past the wrist a hand may reach, as shares of the mannequin hand's reach
HAND_RANGE = (0.75, 1.3)
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


def orient_out(mesh, segments, bones):
    """Turn a solid's faces all one way round, then outward: each connected piece whose faces
    mostly look toward the nearest of some bones, by area, is turned over. face_out() would turn
    each face on its own, and so turn over the faces of every hollow, like the back of a knee,
    between plates or under a hoof: their back faces then show as holes and folds."""
    bmesh.ops.recalc_face_normals(mesh, faces=mesh.faces)
    seen = set()
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
        middles = np.array([f.calc_center_median() for f in piece])
        feet = nearest_on_bones(middles, segments, bones)
        normals = np.array([f.normal * f.calc_area() for f in piece])
        if np.einsum("ij,ij->", normals, middles - feet) < 0:
            bmesh.ops.reverse_faces(mesh, faces=piece)


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


def cut_short(data, plane, inset):
    """Cut mesh data inset short of a seam's plane, dropping what lies past it."""
    middle, normal = np.array(plane["middle"]), np.array(plane["normal"])
    mesh = bmesh.new()
    mesh.from_mesh(data)
    geom = list(mesh.verts) + list(mesh.edges) + list(mesh.faces)
    bmesh.ops.bisect_plane(mesh, geom=geom, plane_co=Vector(middle - normal * inset),
                           plane_no=Vector(normal), clear_outer=True, dist=1e-6)
    mesh.to_mesh(data)
    mesh.free()


def cut_loops(data, plane, inset):
    """The open loops on a cut inset short of a seam's plane, each with its way round the seam's
    axis (None if it doesn't go round)."""
    middle, normal = np.array(plane["middle"]), np.array(plane["normal"])
    on_cut = []
    for loop in open_loops(data):
        points = np.array([data.vertices[i].co for i in loop])
        if np.all(np.abs((points - middle) @ normal + inset) < 1e-3):
            on_cut.append((loop, around(points, middle, normal)))
    return on_cut


def trim(obj, plane):
    """Cut the part short of a seam's plane, dropping what lies past it, by the first of
    WELD_INSETS that leaves one edge, going round the seam's axis; else by the first that leaves
    one going round among others (or the last). A part already welded, carried to another body
    by variant_body.py, has a band bridging it to its old ring, folded where a wide cut met a
    narrow ring: a cut across the band may meet it twice, and the loop that doesn't go round
    stayed open. Gives the inset, which bridge() needs."""
    clean, rounds = None, None
    for inset in WELD_INSETS:
        trial = obj.data.copy()
        cut_short(trial, plane, inset)
        loops = cut_loops(trial, plane, inset)
        bpy.data.meshes.remove(trial)
        if any(way is not None for _, way in loops):
            rounds = inset if rounds is None else rounds
            if len(loops) == 1:
                clean = inset
                break
    inset = clean or rounds or WELD_INSETS[-1]
    cut_short(obj.data, plane, inset)
    return inset


def bridge(obj, seam, plane, ring, inset):
    """Bridge the part's edge where it was trimmed inset short of a seam to the ring: the edge
    loop on the cut that goes round the limb, any other openings the cut made being filled. It
    only adds vertices, so indices found before stay good. Gives the ring vertices' indices, in
    the ring's order."""
    middle, normal = np.array(plane["middle"]), np.array(plane["normal"])
    on_cut = cut_loops(obj.data, plane, inset)
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


def close_strays(obj, rings):
    """Fill every opening the part has but its welded rings. A cut across a band that a carry
    to another body folded meets it more than once, and filling those other loops as the cut
    is bridged leaves some open while the bridge is still being built. Gives how many were
    filled."""
    kept = [set(indices) for indices in rings]
    strays = [loop for loop in open_loops(obj.data) if set(loop) not in kept]
    if not strays:
        return 0
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    mesh.verts.ensure_lookup_table()
    verts = {mesh.verts[i] for loop in strays for i in loop}
    edges = [e for e in mesh.edges if e.is_boundary and all(v in verts for v in e.verts)]
    bmesh.ops.holes_fill(mesh, edges=edges, sides=0)
    mesh.to_mesh(obj.data)
    mesh.free()
    return len(strays)


def mark_rings(part, welded):
    """Put the welded rings' vertices in the SEAM_GROUP vertex group, with the rings' weights
    and normals, from (indices, ring) pairs, each ring's entries in its indices' order."""
    seam_group = part.vertex_groups.get(SEAM_GROUP) or part.vertex_groups.new(name=SEAM_GROUP)
    normals = [Vector(n) for n in np.array([v.normal for v in part.data.vertices])]
    for indices, ring in welded:
        seam_group.add(indices, 1.0, "REPLACE")
        for index, entry in zip(indices, ring):
            normals[index] = Vector(entry["normal"])
            for bone, weight in entry["weights"].items():
                group = part.vertex_groups.get(bone) or part.vertex_groups.new(name=bone)
                group.add([index], weight, "REPLACE")
    part.data.normals_split_custom_set_from_vertices(normals)


def slot_owners(points, segments, names, slots):
    """Which of some slots each point belongs to, by the nearest of the slots' bones: indices
    into names."""
    best = np.full(len(points), np.inf)
    owner = np.zeros(len(points), dtype=int)
    for i, name in enumerate(names):
        bones = [b for b in slots[name]["bones"] if b in segments]
        if not bones:
            continue
        feet = nearest_on_bones(points, segments, bones)
        distance = np.linalg.norm(points - feet, axis=1)
        closer = distance < best
        best[closer] = distance[closer]
        owner[closer] = i
    return owner


def slot_standing(signed, owner, names, radii, share=FORM_SHARE):
    """How far out each slot's points stand, as that share of them do (FORM_SHARE unless given),
    as a share of the slot's radius; slots with too few points left out."""
    standing = {}
    for i, name in enumerate(names):
        mine = signed[owner == i]
        if len(mine) >= 10 and radii.get(name, 0) > 0:
            standing[name] = float(np.percentile(mine, share * 100)) / radii[name]
    return standing


def form_of(signed, forms, owner=None, names=None, radii=None):
    """The thinnest clothing form a surface fits in over its slots: the first whose share of
    each slot's radius is at least as far out as all but FORM_SHARE of the slot's points, over
    the clothed slots if it has any, else all of them."""
    if owner is None:
        raise ValueError("form_of needs the points' slots")
    standing = slot_standing(signed, owner, names, radii)
    clothed = {s: v for s, v in standing.items() if s in clothed_slots()}
    return form_for(clothed or standing, [(n, s + FORM_TOLERANCE) for n, s in forms])


def edge_pairs(part):
    """The part's mesh edges, as vertex index pairs."""
    pairs = np.empty(len(part.data.edges) * 2, dtype=int)
    part.data.edges.foreach_get("vertices", pairs)
    return pairs.reshape(-1, 2)


def rein_in(part, surface, owner, names, radii, limit_share):
    """Scale how far a part stands out from the body over its clothed slots down so REIN_SHARE of
    each slot's points are within limit_share of its radius, the scale eased across seams by
    averaging it with neighbors'. Gives each clothed slot's scale and how many
    points moved; the scales are 1 where nothing needed it."""
    clothed = clothed_slots()
    points = points_of(part)
    closest, signed = nearest(points, surface)
    standing = slot_standing(signed, owner, names, radii, REIN_SHARE)
    scales = {name: min(1.0, limit_share / standing[name]) if standing.get(name, 0) > 0 else 1.0
              for name in names if name in clothed}
    if all(scale >= 1.0 for scale in scales.values()):
        return scales, 0
    per_point = np.array([scales.get(names[i], 1.0) for i in owner])
    pairs = edge_pairs(part)
    for _ in range(REIN_SMOOTH):
        total = per_point.copy()
        count = np.ones(len(per_point))
        np.add.at(total, pairs[:, 0], per_point[pairs[:, 1]])
        np.add.at(total, pairs[:, 1], per_point[pairs[:, 0]])
        np.add.at(count, pairs.ravel(), 1)
        # Never more than the slot's own, so its points still come within the limit
        per_point = np.minimum(total / count,
                               np.array([scales.get(names[i], 1.0) for i in owner]))
    moving = (signed > 0) & (per_point < 1.0 - 1e-6)
    direction = points - closest
    length = np.linalg.norm(direction, axis=1, keepdims=True)
    direction = direction / np.maximum(length, 1e-9)
    points[moving] = closest[moving] + direction[moving] * (signed * per_point)[moving, None]
    set_points(part, points)
    return scales, int(moving.sum())


def soft_clamp(signed, limit):
    """How far out points should be so none goes past the limit: those within CLAMP_KNEE of it
    and beyond ease toward it, so spikes shorten smoothly rather than being cut flat."""
    knee = limit - CLAMP_KNEE
    over = np.maximum(signed - knee, 0.0)
    return np.where(signed > knee, knee + CLAMP_KNEE * (1 - np.exp(-over / CLAMP_KNEE)), signed)


def clamp_to(part, surface, limit):
    """Pull a part's vertices further out from the body than about the limit back in along the
    line to their nearest body point, easing as soft_clamp() does. Gives how many moved."""
    points = points_of(part)
    closest, signed = nearest(points, surface)
    wanted = soft_clamp(signed, limit)
    moving = wanted < signed - 1e-6
    direction = points - closest
    length = np.linalg.norm(direction, axis=1, keepdims=True)
    direction = direction / np.maximum(length, 1e-9)
    points[moving] = closest[moving] + direction[moving] * wanted[moving, None]
    set_points(part, points)
    return int(moving.sum())


def ring_mask(part):
    """Which of a part's vertices are on its welded seam rings (mark_rings' group), which must
    not move, or the weld opens."""
    mask = np.zeros(len(part.data.vertices), dtype=bool)
    group = part.vertex_groups.get(SEAM_GROUP)
    if group is not None:
        for vertex in part.data.vertices:
            if any(g.group == group.index for g in vertex.groups):
                mask[vertex.index] = True
    return mask


def reach_floor(part, names, rings, inside, floor):
    """Stretch or shrink a part that ends a leg so its sole meets the floor, along the up axis
    below the seam it starts at, so the seam ring stays where it is. Gives the scale, or None if
    the part doesn't end a leg or already meets the floor (the given height)."""
    if not any(name.startswith("foot.") for name in names):
        return None
    seams = outer_seams(names)
    # The seam furthest from the foot is where the part starts
    top = max((seam_plane(rings[s], inside)[0][2], s) for s in seams)[0]
    points = points_of(part)
    lowest = float(points[:, 2].min())
    if abs(lowest - floor) <= FLOOR_TOLERANCE or top - lowest < 1e-3:
        return None
    scale = (top - floor) / (top - lowest)
    below = (points[:, 2] < top) & ~ring_mask(part)
    points[below, 2] = top + (points[below, 2] - top) * scale
    set_points(part, points)
    return round(float(scale), 3)


def hand_length(part, names, rings, inside, segments, bones, mannequin):
    """Scale a part that ends an arm along the hand's bone, about the wrist, so it reaches
    HAND_RANGE times as far past the wrist as the mannequin's hand does. Gives the scale, or None
    if the part doesn't end an arm or is already in range."""
    hands = [name for name in names if name.startswith("hand.")]
    if not hands:
        return None
    wrist = "wrist." + hands[0].split(".")[1]
    if wrist not in rings:
        return None
    middle = np.array(seam_plane(rings[wrist], inside)[0])
    bone = next((b for b in bones if b.startswith("hand")), None)
    if bone is None:
        return None
    head, tail = (np.array(p) for p in segments[bone])
    axis = (tail - head) / np.linalg.norm(tail - head)
    points = points_of(part)
    reach = float(np.max((points - middle) @ axis))
    wanted = float(np.max((mannequin - middle) @ axis))
    low, high = HAND_RANGE
    if reach <= 0 or wanted <= 0 or low * wanted <= reach <= high * wanted:
        return None
    scale = min(max(reach, low * wanted), high * wanted) / reach
    along = (points - middle) @ axis
    past = (along > 0) & ~ring_mask(part)
    points[past] += np.outer(along[past] * (scale - 1), axis)
    set_points(part, points)
    return round(scale, 3)


def main():
    global VOXEL
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--part", required=True)
    parser.add_argument("--body", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--slots", required=True, help="Comma-separated slots the part fills.")
    parser.add_argument("--triangles", type=int, required=True)
    parser.add_argument("--open", default="", help="Comma-separated seams left open.")
    parser.add_argument("--voxel", type=float, default=VOXEL,
                        help="The voxel size the part is rebuilt at, in meters.")
    parser.add_argument("--beyond", default="clamp", choices=BEYOND,
                        help="What to do with a part standing out past the thickest clothing"
                             " form over a clothed slot: rein it in (rejecting it if that is too"
                             " much), leave it as it is because it is worn bare, or reject it.")
    parser.add_argument("--rein-least", type=float, default=REIN_LEAST,
                        help="The least share of how far it stands out that reining it in may"
                             " scale it to before it is rejected.")
    args = parser.parse_args(argv)
    VOXEL = args.voxel
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
        planes[seam]["inset"] = trim(part, planes[seam])
    welded = {}
    for seam, plane in planes.items():
        slot = next(n for n in names if seam in slots[n].get("seams", []))
        ring = mannequin_ring(bpy.data.objects["mannequin_" + slot], plane["middle"])
        welded[seam] = (bridge(part, seam, plane, ring, plane["inset"]), ring)
    close_strays(part, [indices for indices, _ in welded.values()])

    # Turn the faces out again, the bridges among them, then copy the rings' normals and weights
    mesh = bmesh.new()
    mesh.from_mesh(part.data)
    orient_out(mesh, segments, bones)
    mesh.to_mesh(part.data)
    mesh.free()
    for polygon in part.data.polygons:
        polygon.use_smooth = True
    mark_rings(part, welded.values())

    # Extents: legs reach the floor, hands stay hand-sized, and the surface fits in the thickest
    # clothing form, or is clamped into it, or left bare, or rejected
    extents = {"floor_scale": reach_floor(part, names, rings, inside, float(body[:, 2].min()))}
    hand_parts = [o for o in bpy.data.objects if o.type == "MESH" and o.name.startswith(
        "mannequin_hand")]
    mannequin_hands = merged(hand_parts)[0] if hand_parts else np.zeros((0, 3))
    extents["hand_scale"] = (hand_length(part, names, rings, inside, segments, bones,
                                         mannequin_hands) if len(mannequin_hands) else None)
    low, high = FLOOR_RANGE
    if extents["floor_scale"] and not low <= extents["floor_scale"] <= high:
        extents["rejected"] = ("needs stretching x%s to meet the floor, more than %s-%s; the fit"
                               " is likely wrong" % (extents["floor_scale"], low, high))
    # Over clothed slots a part must fit in the thickest form, a share of each slot's radius,
    # unless it is worn bare; it is reined in toward the body if it stands out too far, and
    # rejected if that would take more than halving how far it stands out
    forms = plan_forms()
    radii = slot_radii("male")
    owner = slot_owners(points_of(part), segments, names, slots)
    _, signed = nearest(points_of(part), surface)
    limit = forms[-1][1] if forms else None
    standing = slot_standing(signed, owner, names, radii, REIN_SHARE)
    extents["standing"] = {s: round(v, 3) for s, v in standing.items()}
    clothed = {s: v for s, v in standing.items() if s in clothed_slots()}
    over = {s: v for s, v in clothed.items() if limit is not None and v > limit}
    if extents.get("rejected") or args.beyond == "bare":
        pass
    elif over and args.beyond == "reject":
        extents["rejected"] = "stands out past the %s form over %s" % (
            forms[-1][0], ", ".join("%s (%.2f of its radius)" % i for i in over.items()))
    elif over:
        needed = {s: limit / v for s, v in over.items()}
        if min(needed.values()) < args.rein_least:
            extents["rejected"] = (
                "stands out too far for clothing over %s; reining it in would take scaling it"
                " to %s, past x%s. Regenerate it slimmer, or brief it \"bare\" if it is meant"
                " to be worn without clothing"
                % (", ".join(over), ", ".join("x%.2f" % k for k in needed.values()),
                   args.rein_least))
        else:
            scales, moved = rein_in(part, surface, owner, names, radii, limit)
            extents["reined"] = {s: round(k, 3) for s, k in scales.items() if k < 1.0}
            extents["reined_points"] = moved
            # What smoothing left past the limit is clamped
            point_limit = np.array([limit * radii[names[i]] if names[i] in clothed_slots()
                                    else np.inf for i in owner])
            extents["clamped"] = clamp_to(part, surface, point_limit)
            _, signed = nearest(points_of(part), surface)
    extents["form"] = (None if args.beyond == "bare"
                       else form_of(signed, forms, owner, names, radii))
    extents["bare"] = args.beyond == "bare"

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
        "weld_insets_mm": {seam: round(plane["inset"] * 1000) for seam, plane in planes.items()},
        "open": sorted(left_open),
        "ring_error_mm": round(ring_error * 1000, 3),
        "stands_out_mm": {"p50": round(float(np.percentile(signed, 50)) * 1000, 1),
                          "p95": round(float(np.percentile(signed, 95)) * 1000, 1)},
        "inside_pct": round(float((signed < -0.005).mean()) * 100, 1),
        # The welded rings, and any holes left, as how many vertices each has
        "open_edges": sorted(len(lp) for lp in open_loops(part.data)),
        "extents": extents,
    }
    print("[shape_body]", json.dumps(report))
    with open(os.path.join(args.out, "shape.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)
    if extents.get("rejected"):
        raise SystemExit("[shape_body] rejected: " + extents["rejected"])

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
