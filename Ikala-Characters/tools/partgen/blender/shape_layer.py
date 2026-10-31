"""Shape a garment cut out of a generated figure (see fit_layer.py) into one that fits over the
mannequin: nothing of it inside the body or nearer than a gap, nor, for a layer worn under others,
further out than a reach, smooth, and within a triangle budget.

TRELLIS.2 makes limbs a little thinner than the mannequin's, so parts of a garment come out
inside the body, like sleeves inside the arms. Every vertex nearer the body than the gap is
pushed out along the body's surface normal to the gap, the pushed ones smoothed so they don't
crease, and pushed again. A layer worn under others, like a shirt under a jacket, is also kept
within a reach of the body, every vertex further out pulled in to it, so it stays inside the
gap the layers over it keep (a jacket's gap is more than a shirt's reach) and its folds don't
poke through them; its wrinkles still show in its baked normals. The garment is then rebuilt
and reduced to its triangle budget: reducing TRELLIS's tangled mesh directly tears it into
spikes and webs, so it is first given a little thickness inward, rebuilt as a clean voxel mesh,
and its inner surface (the side facing the body) dropped, leaving one shell to reduce. That
loses TRELLIS's texture layout, which the bake replaces. Its open edges are relaxed out of the
voxels' steps, small holes torn in it are filled, and it is reduced with Ikala-Graphics' simplifier given its classpath (see
SimplifyMesh.java), whose triangles fold over far less than Blender's decimation's slivers when
pushed or carried to another body. It is then pushed once more, without smoothing, as reducing
can tuck some back in.

    blender -b --python shape_layer.py -- --garment <garment.glb> --body <male.blend>
        --out <folder> --gap 0.004 --triangles 2000 [--reach 0.012]
        [--simplifier <Ikala-Graphics jar>;<Ikala-Core jar>] [--java java]

writes shaped.glb, pushed.glb (the garment pushed out at full detail, before it is rebuilt,
only the cloth that started outside the body), rebuilt.glb (rebuilt at full detail, before it
is reduced; the bake takes normals from both), shape.json and renders preview_6.png and
preview_7.png: the garment on the
mannequin from the front and side, in a flat color until it is baked.
"""

import argparse
import json
import math
import os
import subprocess
import sys
import tempfile

import bmesh
import bpy
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bodies import load_body, nearest, tree, world_vertices  # noqa: E402
from fit_layer import (SIDE, SIDE_GROW, grown, import_figure, on_painted,  # noqa: E402
                       read_mask, render)

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
# The limbs whose garment parts are turned onto the mannequin's bones, each from the body out
LIMBS = (("upperarm.L", "forearm.L"), ("upperarm.R", "forearm.R"), ("thigh.L", "shin.L"),
         ("thigh.R", "shin.R"))
# The share of a bone's length, around its head, over which a turn blends in, so the garment
# bends at the joint rather than tearing
BLEND_FROM = -0.1
BLEND_TO = 0.25
# Where along a limb's bone, as shares of its length, the two slices lie whose middles give the
# way a sleeve or the limb runs: clear of the joints, where an outfit's hips or a jacket's
# shoulders would pull the middle aside
SLICES = ((0.3, 0.45), (0.6, 0.75))
# Faces that turning the limbs stretches to more than STRETCH times as long, and by more than
# STRETCH_MIN meters, spanned two limbs that moved apart, like a web between the ankles, and are
# dropped
STRETCH = 2.0
STRETCH_MIN = 0.01
# Loose pieces with fewer faces than this share of the garment's are dropped before it is
# rebuilt: specks the cut left in a jacket's open front would merge into a sheet across it
CRUMB = 0.01
# A garment's open edge within HEM_DEPTH meters of a sleeve's or leg's end, along the limb's
# last bone, is its hem, which is trimmed straight across where all but HEM_LEVEL percent of
# that edge lies past: the cut leaves it torn into teeth where the figure's legs and sleeves run
# into the bare feet and hands. Edges further up are holes and seams, not the hem
HEM_FROM = 0.5
HEM_DEPTH = 0.06
HEM_LEVEL = 10
# Extra room at a sleeve's or leg's end, growing to HEM_EASE meters over its last HEM_SPAN: the
# body's skin around an ankle or wrist bends with the foot or hand, and would come out through
# a hem fitted as closely as the rest of the leg
HEM_EASE = 0.012
HEM_SPAN = 0.06
# With the head bare, how far up the neck bone, as a share of its length, a garment may reach: a
# collar at its base stays, but generated figures run a jacket's collar up the back of the neck,
# and pushed out of it, it wraps the neck to the chin
NECK = "neck"
NECK_KEEP = 0.35
# The previews' colors: the garment, unbaked, and the body
# The rebuilt garment's open edges, its hems, cuffs, neckline and front, follow the voxels in
# steps, which reducing keeps as a jagged rim: they are relaxed along themselves first, this many
# rounds of Taubin smoothing (a step toward the neighbors' middle, then a slightly bigger one back,
# so loops like a cuff don't shrink)
EDGE_ROUNDS = 12
# Open loops shorter than this, in meters, are holes and get filled: pushing tears the crumpled
# cloth where TRELLIS's sleeves lay inside the arm. A garment's openings, its neck, cuffs, hems
# and front, are all longer
HOLE_PERIMETER = 0.15
# Cloth that started further inside the body than this, in meters, like TRELLIS's sleeves inside
# the arms, is left out of pushed.glb: both sides of TRELLIS's sheet were pushed onto the same
# surface there and interleave, which baked into blotches. The bake takes the rebuilt shell's
# normals there instead
CRUMPLE_DEPTH = 0.006
# Where a garment cut at a seam runs on past it, tucked under the layer beyond, like a shirt's
# hem inside pants: how near the body it keeps, in meters, inside that layer's own gap
TUCKED_GAP = 0.001
TUCKED_REACH = 0.002
EDGE_TOWARD = 0.5
EDGE_BACK = -0.53
GARMENT_COLOR = (0.85, 0.45, 0.15, 1)
BODY_COLOR = (0.7, 0.7, 0.72, 1)



def bone_segments(armature):
    """Each deforming bone's head and tail at rest, in world space."""
    return {bone.name: (np.array(armature.matrix_world @ bone.head_local),
                        np.array(armature.matrix_world @ bone.tail_local))
            for bone in armature.data.bones if bone.use_deform}


def segment_distances(points, head, tail):
    along = tail - head
    share = np.clip((points - head) @ along / max(along @ along, 1e-12), 0, 1)
    return np.linalg.norm(points - head - np.outer(share, along), axis=1)


def owners(points, segments):
    """The bone nearest each point."""
    names = np.array(list(segments))
    return names[np.stack([segment_distances(points, *segments[n]) for n in names], 1).argmin(1)]


def with_beyond(armature, names):
    """Bones and every bone under them, like a hand's fingers."""
    out = set(names)
    for name in names:
        if name in armature.data.bones:
            out |= {bone.name for bone in armature.data.bones[name].children_recursive}
    return out


def turn_between(a, b):
    """The rotation matrix turning unit vector a onto unit vector b."""
    axis = np.cross(a, b)
    sine, cosine = np.linalg.norm(axis), float(a @ b)
    if sine < 1e-9:
        return np.eye(3)
    axis /= sine
    k = np.array([[0, -axis[2], axis[1]], [axis[2], 0, -axis[0]], [-axis[1], axis[0], 0]])
    return np.eye(3) + sine * k + (1 - cosine) * (k @ k)


def limb_axis(points, nearest, name, share):
    """Where a limb segment's points run: the middle of those a fifth to a third along the bone,
    and the way from there to the middle of those two thirds to four fifths along, the way a
    tube runs, however wide it is. None if either slice is nearly empty."""
    own = nearest == name
    near = own & (share > SLICES[0][0]) & (share < SLICES[0][1])
    far = own & (share > SLICES[1][0]) & (share < SLICES[1][1])
    if near.sum() < 20 or far.sum() < 20:
        return None
    start = points[near].mean(0)
    direction = points[far].mean(0) - start
    return start, direction / np.linalg.norm(direction)


def surface_samples(points, faces, steps=4):
    """Points spread over a mesh's triangles, on a grid of barycentric steps: the mannequin's
    skin is low-poly, a few vertices to a slice of an arm."""
    grid = [(i / steps, j / steps) for i in range(steps + 1) for j in range(steps + 1 - i)]
    a, b, c = points[faces[:, 0]], points[faces[:, 1]], points[faces[:, 2]]
    return np.concatenate([a + u * (b - a) + v * (c - a) for u, v in grid])


def skin_distance(points, surface):
    """The median distance of some points from a surface (a BVH tree), inside or out."""
    if not len(points):
        return 0.0
    return float(np.median([surface.find_nearest(Vector(p))[3] for p in points]))


def match_limbs(points, body, armature, surface=None):
    """Turn a garment's sleeves and legs onto the mannequin's limbs. TRELLIS holds the figure's
    arms its own way, bent at the elbow where the mannequin's are straight, which lining the
    whole figure up can't fix: sleeves then cross the arm at the elbow, and pushing them out of
    it tears them in two. Each limb's bones, from the body out, find which way the garment
    points and the body's skin nearest them run, and turn the garment's (and those beyond, like
    a cuff over the hand) about the bone's head to run the way the skin does, blending in around
    the joint, then move them across the limb so they run around its middle, as a sleeve hung
    off to one side passes through the arm too. With the body's surface (a BVH tree), a turn is
    kept only if it brings the bone's points nearer the skin, for figures whose limbs should lie
    on it, like a body part's: a small turn from a noisy direction would only move a limb that
    was already right. Gives the moved points and each turn in degrees, 0 for one not kept."""
    segments = bone_segments(armature)
    body_nearest = owners(body, segments)
    moved = points.copy()
    turns = {}
    for chain in LIMBS:
        for k, name in enumerate(chain):
            if name not in segments:
                continue
            head, tail = segments[name]
            length = np.linalg.norm(tail - head)
            along = (tail - head) / length
            nearest = owners(moved, segments)
            share = (moved - head) @ along / length
            garment = limb_axis(moved, nearest, name, share)
            skin = limb_axis(body, body_nearest, name, (body - head) @ along / length)
            if garment is None or skin is None:
                continue
            rotation = turn_between(garment[1], skin[1])
            # After the turn, how far across the limb the garment's middle lies from the skin's
            across = head + (garment[0] - head) @ rotation.T - skin[0]
            across -= (across @ skin[1]) * skin[1]
            # This bone, those after it in the limb and those under them, like the hand, turn whole
            # from a little before the joint; the bone before it blends in near the joint
            follows = list(with_beyond(armature, chain[k:]))
            if k > 0:
                follows.append(chain[k - 1])
            weight = np.clip((share - BLEND_FROM) / (BLEND_TO - BLEND_FROM), 0, 1)
            weight[~np.isin(nearest, follows)] = 0
            turned = head + (moved - head) @ rotation.T - across
            candidate = moved + weight[:, None] * (turned - moved)
            if surface is not None:
                own = (nearest == name) & (share > SLICES[0][0]) & (share < SLICES[1][1])
                if skin_distance(candidate[own], surface) >= skin_distance(moved[own], surface):
                    turns[name] = 0.0
                    continue
            moved = candidate
            turns[name] = round(float(np.degrees(np.arccos(np.clip(garment[1] @ skin[1], -1,
                                                                   1)))), 1)
    return moved, turns


def level_hems(obj, armature):
    """Trim each sleeve's and leg's end straight across: drop what lies past the HEM_LEVEL
    percentile of its open edge, along the limb's last bone. Gives how far each moved, in
    millimeters."""
    segments = bone_segments(armature)
    points = points_of(obj)
    nearest = owners(points, segments)
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    mesh.verts.ensure_lookup_table()
    edge = np.array([v.is_boundary for v in mesh.verts])
    drop = np.zeros(len(points), dtype=bool)
    trims = {}
    for chain in LIMBS:
        name = chain[-1]
        if name not in segments:
            continue
        head, tail = segments[name]
        length = np.linalg.norm(tail - head)
        share = (points - head) @ ((tail - head) / length) / length
        limb = np.isin(nearest, list(with_beyond(armature, [name])))
        hem = limb & edge & (share > HEM_FROM)
        if hem.sum() < 10:
            continue
        hem &= share > share[hem].max() - HEM_DEPTH / length
        level = np.percentile(share[hem], HEM_LEVEL)
        past = limb & (share > level)
        drop |= past
        trims[name] = round(float((share[hem].max() - level) * length * 1000), 1)
    bmesh.ops.delete(mesh, geom=[mesh.verts[i] for i in np.flatnonzero(drop)], context="VERTS")
    mesh.to_mesh(obj.data)
    mesh.free()
    return trims


def seam_frame(seams, seam):
    """A seam ring's middle and the normal of its plane, pointing up."""
    ring = np.asarray(seams[seam])
    _, _, axes = np.linalg.svd(ring - ring.mean(0))
    return ring.mean(0), (axes[2] if axes[2][2] >= 0 else -axes[2])


def parse_cut(text, body):
    """A --cut argument, "seam,above|below,offset", with the seams of the body's .blend beside
    it: (seams, seam, keep, offset), or None."""
    if not text:
        return None
    seam, keep, offset = text.split(",")
    with open(os.path.splitext(os.path.abspath(body))[0] + "_seams.json",
              encoding="utf-8") as file:
        return json.load(file)["seams"], seam, keep, float(offset)


def tucked_band(points, cut, gap, reach):
    """The gap and reach for each point of a garment cut at a seam (see parse_cut): what lies
    past the seam itself, like a shirt's hem tucked into pants, keeps to TUCKED_GAP and
    TUCKED_REACH, inside the layer beyond the seam; the rest keeps the garment's own."""
    if cut is None:
        return gap, reach
    seams, seam, keep, _ = cut
    middle, up = seam_frame(seams, seam)
    side = (points - middle) @ up
    past = side < 0 if keep == "above" else side > 0
    return (np.where(past, TUCKED_GAP, gap),
            None if reach is None else np.where(past, TUCKED_REACH, reach))


def cut_at_seam(obj, armature, seams, seam, keep, offset):
    """Cut a garment straight across at one of the mannequin's seam rings, moved up by an
    offset, keeping what lies above or below it, so layers worn together meet at a firm line
    (see layers.json's "cut"). The arms, hanging past the waist in the A pose, stay whole. Gives
    how many faces went."""
    middle, up = seam_frame(seams, seam)
    middle = middle + np.array([0.0, 0.0, offset])
    arms = list(with_beyond(armature, [chain[0] for chain in LIMBS if "arm" in chain[0]]))
    segments = bone_segments(armature)
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    bmesh.ops.bisect_plane(mesh, geom=mesh.verts[:] + mesh.edges[:] + mesh.faces[:],
                           plane_co=Vector(middle), plane_no=Vector(up))
    middles = np.array([f.calc_center_median() for f in mesh.faces]).reshape(-1, 3)
    on_arm = np.isin(owners(middles, segments), arms)
    above = (middles - middle) @ up > 0
    drop = ~above if keep == "above" else above
    gone = [f for f, out in zip(mesh.faces, drop & ~on_arm) if out]
    bmesh.ops.delete(mesh, geom=gone, context="FACES")
    mesh.to_mesh(obj.data)
    mesh.free()
    return len(gone)


def drop_stretched(obj, before):
    """Drop the faces whose edges limb matching stretched (see STRETCH), from the points before
    it. Gives how many were dropped."""
    after = points_of(obj)
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    stretched = []
    for face in mesh.faces:
        for edge in face.edges:
            a, b = (v.index for v in edge.verts)
            was = np.linalg.norm(before[a] - before[b])
            now = np.linalg.norm(after[a] - after[b])
            if now > STRETCH * was and now - was > STRETCH_MIN:
                stretched.append(face)
                break
    bmesh.ops.delete(mesh, geom=stretched, context="FACES_ONLY")
    mesh.to_mesh(obj.data)
    mesh.free()
    return len(stretched)


def recut(obj, painted, guide, armature=None):
    """Drop the rebuilt garment's faces that look toward the front camera from where the picked
    image shows no garment (its painted area, grown a little for the outline): rebuilding joins
    specks the cut left, like bits of the bare chest in a jacket's open front, into a sheet
    across it. Sleeves and legs, given the armature, are left alone: match_limbs has turned them
    off the image's, and the image's outline cut slits along their sides. Gives how many were
    dropped."""
    allowed = grown(painted, SIDE_GROW)
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    faces = [f for f in mesh.faces if f.normal.y < -SIDE]
    middles = np.array([f.calc_center_median() for f in faces]).reshape(-1, 3)
    outside = ~on_painted(middles, allowed, guide)
    if armature is not None and len(middles):
        limbs = with_beyond(armature, [chain[0] for chain in LIMBS])
        outside &= ~np.isin(owners(middles, bone_segments(armature)), list(limbs))
    drop = [f for f, out in zip(faces, outside) if out]
    bmesh.ops.delete(mesh, geom=drop, context="FACES")
    mesh.to_mesh(obj.data)
    mesh.free()
    return len(drop)


def drop_crumbs(obj):
    """Drop the garment's loose pieces smaller than CRUMB of it. Gives how many faces went."""
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    seen = set()
    crumbs = []
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
        if len(piece) < CRUMB * len(mesh.faces):
            crumbs += piece
    bmesh.ops.delete(mesh, geom=crumbs, context="FACES")
    mesh.to_mesh(obj.data)
    mesh.free()
    return len(crumbs)


def drop_bare(obj, armature, bare):
    """Drop a garment's vertices nearest some bones, or those under them: the head, hands or feet
    a layer leaves bare, which a sleeve or leg longer than the mannequin's limb reaches over once
    it is turned onto it, and with the head, the upper neck past NECK_KEEP. Gives how many were
    dropped."""
    segments = bone_segments(armature)
    points = points_of(obj)
    nearest = owners(points, segments)
    over = np.isin(nearest, list(with_beyond(armature, bare)))
    for name in bare:
        bone = armature.data.bones.get(name)
        if bone is not None and bone.parent is not None and bone.parent.name == NECK \
                and NECK in segments:
            start, end = segments[NECK]
            share = (points - start) @ (end - start) / ((end - start) @ (end - start))
            over |= (nearest == NECK) & (share > NECK_KEEP)
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    mesh.verts.ensure_lookup_table()
    bmesh.ops.delete(mesh, geom=[mesh.verts[i] for i in np.flatnonzero(over)], context="VERTS")
    mesh.to_mesh(obj.data)
    mesh.free()
    return int(over.sum())


def hem_ease(points, armature):
    """How much extra room each point of a garment gets for being near a sleeve's or leg's end:
    up to HEM_EASE at the end, along the limb's last bone, less over HEM_SPAN before it."""
    segments = bone_segments(armature)
    nearest = owners(points, segments)
    ease = np.zeros(len(points))
    for chain in LIMBS:
        name = chain[-1]
        if name not in segments:
            continue
        head, tail = segments[name]
        along = (points - head) @ ((tail - head) / np.linalg.norm(tail - head))
        limb = np.isin(nearest, list(with_beyond(armature, [name])))
        if not limb.any():
            continue
        end = along[limb].max()
        ease[limb] = HEM_EASE * np.clip(1 - (end - along[limb]) / HEM_SPAN, 0, 1)
    return ease


def push_out(points, surface, gap, reach=None):
    """Move points nearer the body than the gap out along its normal to the gap, and with a
    reach, points further out than it in toward the body to it. The gap and reach are one for
    all points or one for each. Gives the moved points and which moved."""
    moved = points.copy()
    pushed = np.zeros(len(points), dtype=bool)
    gaps = np.broadcast_to(np.asarray(gap, dtype=float), len(points))
    reaches = None if reach is None else np.broadcast_to(np.asarray(reach, dtype=float),
                                                         len(points))
    for i, p in enumerate(points):
        location, normal, _, distance = surface.find_nearest(Vector(p))
        offset = Vector(p) - location
        signed = distance if offset.dot(normal) >= 0 else -distance
        if signed < gaps[i]:
            # Straight out, not onto the nearest point plus the gap: snapping there slides
            # points sideways, bunching neighbors that share a nearest point until faces fold
            moved[i] = Vector(p) + normal * (gaps[i] - signed)
            pushed[i] = True
        elif reaches is not None and signed > reaches[i]:
            moved[i] = location + offset * (reaches[i] / distance)
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


def export_outside(obj, start, path):
    """Export a copy of the garment without the faces of cloth that started deeper inside the
    body than CRUMPLE_DEPTH, given each vertex's distance outside it before being pushed."""
    copy = obj.copy()
    copy.data = obj.data.copy()
    bpy.context.scene.collection.objects.link(copy)
    mesh = bmesh.new()
    mesh.from_mesh(copy.data)
    deep = start < -CRUMPLE_DEPTH
    bmesh.ops.delete(mesh, geom=[f for f in mesh.faces if any(deep[v.index] for v in f.verts)],
                     context="FACES")
    mesh.to_mesh(copy.data)
    mesh.free()
    bpy.ops.object.select_all(action="DESELECT")
    copy.select_set(True)
    bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", use_selection=True)
    bpy.data.meshes.remove(copy.data)


def points_of(obj):
    co = np.empty(len(obj.data.vertices) * 3)
    obj.data.vertices.foreach_get("co", co)
    return co.reshape(-1, 3)


def shape(obj, surface, gap, reach=None, rounds=ROUNDS, armature=None):
    """Push and smooth until nothing is nearer the body than the gap, nor further than the reach
    if there is one, both eased at sleeves' and legs' ends given the armature. A reduced mesh
    takes no smoothing rounds: its vertices lie far apart, so pulling one toward its neighbors'
    middle moves it a long way, and folds faces over, which are culled when drawn, leaving
    holes. Gives how many moved."""
    if armature is not None:
        ease = hem_ease(points_of(obj), armature)
        gap = gap + ease
        reach = None if reach is None else reach + ease
    total = np.zeros(len(obj.data.vertices), dtype=bool)
    for _ in range(rounds):
        points, pushed = push_out(points_of(obj), surface, gap, reach)
        set_points(obj, points)
        total |= pushed
        smooth(obj, total)
    points, pushed = push_out(points_of(obj), surface, gap, reach)
    set_points(obj, points)
    return int((total | pushed).sum())


def rebuild(obj, surface, reach=None, armature=None):
    """Rebuild a garment shell as a clean mesh: thickened inward, remeshed in voxels, and its
    faces that look toward the body or lie inside it dropped, and with a reach, those standing
    further out than it by more than a voxel, the reach eased at sleeves' and legs' ends as
    shape() eases it given the armature. Gives how many triangles it has."""
    # TRELLIS's meshes are sheets with two sides, half their faces looking toward the body, and
    # thickening "inward" would thicken those outward, past the garment and into a garment's
    # surfaces lying close by, joining pants' legs between the thighs. Every face is turned to
    # look away from the body first, so thickening always goes toward it
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    toward_body = []
    for face in mesh.faces:
        middle = face.calc_center_median()
        location, _, _, _ = surface.find_nearest(middle)
        if face.normal.dot(middle - location) < 0:
            toward_body.append(face)
    bmesh.ops.reverse_faces(mesh, faces=toward_body)
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
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    inner = []
    middles = np.array([face.calc_center_median() for face in mesh.faces]).reshape(-1, 3)
    # Hems stand out further, by up to HEM_EASE: left at the plain reach, the back of a leg's
    # hem was cut away above the heel
    limits = None if reach is None else np.broadcast_to(
        reach + VOXEL + (hem_ease(middles, armature) if armature is not None else 0.0),
        len(middles))
    shell = BVHTree.FromBMesh(mesh)
    for k, face in enumerate(mesh.faces):
        middle = face.calc_center_median()
        location, normal, _, distance = surface.find_nearest(middle)
        toward = location - middle
        # A garment within a reach stands out less than the thickness, so its inner side ends
        # up inside the body, where it can face any way
        if toward.dot(normal) > 0:
            inner.append(face)
        elif face.normal.dot(toward) > 0 and not faces_garment(face, middle, surface, shell):
            inner.append(face)
        # Rebuilding joins a garment's surfaces lying close together, like pants' legs, with a
        # web between the thighs: nothing of a garment within a reach stands that far out
        elif limits is not None and distance > limits[k]:
            inner.append(face)
    bmesh.ops.delete(mesh, geom=inner, context="FACES")
    mesh.to_mesh(obj.data)
    mesh.free()
    obj.data.calc_loop_triangles()
    return len(obj.data.loop_triangles)


def open_loops(mesh):
    """A mesh's open edges, in loops: lists of edges."""
    left = {e for e in mesh.edges if e.is_boundary}
    loops = []
    while left:
        edge = left.pop()
        loop = [edge]
        vertex = edge.verts[1]
        while True:
            following = [e for e in vertex.link_edges if e in left]
            if not following:
                break
            edge = following[0]
            left.discard(edge)
            loop.append(edge)
            vertex = edge.other_vert(vertex)
        loops.append(loop)
    return loops


def fill_holes(obj):
    """Fill the garment's open loops shorter than HOLE_PERIMETER, turned to face as the faces
    around them do. Gives how many were filled."""
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    filled = 0
    for loop in open_loops(mesh):
        if sum(e.calc_length() for e in loop) >= HOLE_PERIMETER:
            continue
        around = sum((f.normal for e in loop for f in e.link_faces), Vector())
        made = bmesh.ops.holes_fill(mesh, edges=loop, sides=0)["faces"]
        if not made:
            continue
        made = bmesh.ops.triangulate(mesh, faces=made)["faces"]
        for face in made:
            face.normal_update()
        if sum((f.normal for f in made), Vector()).dot(around) < 0:
            bmesh.ops.reverse_faces(mesh, faces=made)
        filled += 1
    mesh.to_mesh(obj.data)
    mesh.free()
    return filled


def relax_edges(obj):
    """Smooth the garment's open edges along themselves (see EDGE_ROUNDS). Only vertices with two
    open edges move, so corners where edges meet stay. Gives how many vertices moved."""
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    mesh.verts.ensure_lookup_table()
    loop = {}
    for vertex in mesh.verts:
        edges = [e for e in vertex.link_edges if e.is_boundary]
        if len(edges) == 2:
            loop[vertex.index] = [e.other_vert(vertex).index for e in edges]
    if loop:
        moving = np.array(list(loop))
        sides = np.array([loop[v] for v in moving])
        points = np.array([v.co[:] for v in mesh.verts])
        for _ in range(EDGE_ROUNDS):
            for step in (EDGE_TOWARD, EDGE_BACK):
                middle = points[sides].mean(1)
                points[moving] += step * (middle - points[moving])
        for v in moving:
            mesh.verts[v].co = points[v]
        mesh.to_mesh(obj.data)
    mesh.free()
    return len(loop)


def simplify(obj, triangles, classpath, java="java"):
    """Reduce a mesh to about this many triangles with Ikala-Graphics' simplifier, through
    SimplifyMesh.java beside partgen.py. Its result replaces the mesh, welded."""
    obj.data.calc_loop_triangles()
    points = np.empty(len(obj.data.vertices) * 3, dtype=np.float32)
    obj.data.vertices.foreach_get("co", points)
    corners = np.empty(len(obj.data.loop_triangles) * 3, dtype=np.int32)
    obj.data.loop_triangles.foreach_get("vertices", corners)
    with tempfile.TemporaryDirectory() as folder:
        source, target = os.path.join(folder, "in.bin"), os.path.join(folder, "out.bin")
        with open(source, "wb") as file:
            file.write(np.array([len(points) // 3, len(corners) // 3], dtype="<i4").tobytes())
            file.write(points.astype("<f4").tobytes())
            file.write(corners.astype("<i4").tobytes())
        tool = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                            "SimplifyMesh.java")
        subprocess.run([java, "-cp", classpath, tool, source, target, str(triangles)],
                       check=True)
        data = open(target, "rb").read()
    vertices, faces = (int(n) for n in np.frombuffer(data[:8], "<i4"))
    points = np.frombuffer(data[8:8 + vertices * 12], "<f4").reshape(-1, 3)
    corners = np.frombuffer(data[8 + vertices * 12:], "<i4").reshape(-1, 3)
    old = obj.data
    mesh = bpy.data.meshes.new(old.name)
    mesh.from_pydata(points.tolist(), [], corners.tolist())
    mesh.update()
    obj.data = mesh
    bpy.data.meshes.remove(old)


def faces_garment(face, middle, surface, shell):
    """Whether a face looking toward the body looks at more of the garment first: then it is the
    outside of a sleeve facing the side, or of a leg facing the other, not the inner side
    thickening left, and stays. Dropping those tore slits along sleeves and inner thighs."""
    start = middle + face.normal * VOXEL * 0.1
    garment = shell.ray_cast(start, face.normal)
    if garment[0] is None:
        return False
    body = surface.ray_cast(start, face.normal)
    return body[0] is None or garment[3] < body[3]


def reduce(obj, triangles, classpath=None, java="java"):
    """Collapse edges until the mesh has about this many triangles, with Ikala-Graphics'
    simplifier given its classpath (see SimplifyMesh.java), else Blender's decimation."""
    obj.data.calc_loop_triangles()
    count = len(obj.data.loop_triangles)
    if count <= triangles:
        return count
    if classpath:
        simplify(obj, triangles, classpath, java)
        obj.data.calc_loop_triangles()
        return len(obj.data.loop_triangles)
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
    parser.add_argument("--reach", type=float)
    parser.add_argument("--bare", default="", help="Comma-separated bones the layer leaves bare.")
    parser.add_argument("--painted", help="The picked image's painted area (see partgen.py).")
    parser.add_argument("--guide", help="The guide.json mapping it onto the mannequin.")
    parser.add_argument("--simplifier", help="The classpath of Ikala-Graphics and Ikala-Core, "
                        "to reduce with their simplifier rather than Blender's decimation.")
    parser.add_argument("--java", default="java", help="The java to run it with.")
    parser.add_argument("--cut", help="seam,above|below,offset: cut the garment straight across "
                        "there (see layers.json).")
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

    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    skin = surface_samples(body, body_faces)
    unmatched = points_of(garment)
    matched, turns = match_limbs(unmatched, skin, armature)
    set_points(garment, matched)
    stretched = drop_stretched(garment, unmatched)
    bare = [b for b in args.bare.split(",") if b]
    dropped = drop_bare(garment, armature, bare) if bare else 0
    hems = level_hems(garment, armature)
    cut_faces = 0
    cut = parse_cut(args.cut, args.body)
    if cut is not None:
        cut_faces = cut_at_seam(garment, armature, *cut)
    crumbs = drop_crumbs(garment)
    garment.data.calc_loop_triangles()
    before = len(garment.data.loop_triangles)
    _, start = nearest(points_of(garment), surface)
    moved = shape(garment, surface, *tucked_band(points_of(garment), cut, args.gap, args.reach),
                  armature=armature)
    # The pushed garment at full detail, which the bake takes normals from: it lies where the
    # rebuilt one does, so bake rays find the right surface
    export_outside(garment, start, os.path.join(args.out, "pushed.glb"))
    rebuilt = rebuild(garment, surface, args.reach, armature)
    recut_faces = 0
    if args.painted and args.guide:
        with open(args.guide, encoding="utf-8") as file:
            recut_faces = recut(garment, read_mask(args.painted), json.load(file),
                                armature)
    holes = fill_holes(garment)
    # The rebuilt garment at full detail, which the bake takes normals from: one clean shell
    # within millimeters of the reduced one. pushed.glb keeps finer wrinkles, but where
    # TRELLIS's two-sided sheet lay inside the body both sides were pushed onto the same
    # surface and interleave there, which baked into blotches
    bpy.ops.object.select_all(action="DESELECT")
    garment.select_set(True)
    bpy.ops.export_scene.gltf(filepath=os.path.join(args.out, "rebuilt.glb"),
                              export_format="GLB", use_selection=True)
    relaxed = relax_edges(garment)
    triangles = reduce(garment, args.triangles, args.simplifier, args.java)
    shape(garment, surface, *tucked_band(points_of(garment), cut, args.gap, args.reach),
          rounds=0, armature=armature)
    _, end = nearest(points_of(garment), surface)
    report = {
        "triangles_before": before,
        "triangles_rebuilt": rebuilt,
        "triangles": triangles,
        "inside_before_pct": round(float((start < 0).mean()) * 100, 1),
        "pushed_pct": round(moved / max(1, len(start)) * 100, 1),
        "too_near_pct": round(float((end < args.gap - SLACK).mean()) * 100, 2),
        "min_gap_mm": round(float(end.min()) * 1000, 1),
        "max_gap_mm": round(float(end.max()) * 1000, 1),
        "limb_turns_degrees": turns,
        "bare_dropped": dropped,
        "stretched_dropped": stretched,
        "crumb_faces_dropped": crumbs,
        "recut_faces": recut_faces,
        "cut_faces": cut_faces,
        "holes_filled": holes,
        "relaxed_edge_vertices": relaxed,
        "reduced_by": "simplifier" if args.simplifier else "decimate",
        "hems_trimmed_mm": hems,
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
