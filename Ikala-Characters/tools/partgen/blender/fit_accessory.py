"""Fit a generated mesh onto the mannequin as a rigid accessory on one socket.

Run it headless with Blender 5 or later, which partgen.py does:

    blender -b --factory-startup --python fit_accessory.py -- --mesh <generated.glb>
        --mannequin <mannequin.glb> --bone <socket bone> --rule <json> --name <mesh name>
        --out <part.glb> --preview <folder>

The generated mesh is cleaned of thin slivers, sized and placed by the socket type's rule, then a
low-poly copy is unwrapped and baked from it: base color, a normal map, and an ORM map
(ambient occlusion, then the generated roughness and metalness). The copy is weighted entirely
to the socket's bone, so it moves rigidly with it, and written with the mannequin's armature so
it loads onto the rig like any other part.

The base color is split into up to four color groups and made recolorable: each group's base color
turns white, so the tint the shader multiplies in replaces it, and a tint mask beside the glb
(<part>_masks/<image>.png) picks the groups, one per channel. Each group's color is written to --tints as the default for its mask channel, which
brings the part's own look back when a design leaves the palette channel uncolored. The preview
folder gets front and side renders on the mannequin, in those default colors.

Armor worn as clothing (see armor.json and partgen.py's fit_armor) comes this way too, with the
body slots it covers in --slots: its low-poly copy is pushed out of the skin to the rule's "gap",
a piece over a limb ("fit": "limb", already lined up by fit_body.py --armor) is weighted from the
skin of those slots so it bends with them rather than to one bone, a helmet ("fit": "enclose") is
put round the head, and --both-sides adds the piece's mirror, weighted from the other side's skin,
as one mesh with it, for a layer covering both hands or feet.
"""
import argparse
import json
import math
import os
import pathlib
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from shell_layer import conform, push_faces, skin_shell  # noqa: E402

# A part of the generated mesh this thin across, and this many times longer, is a sliver
SLIVER_THICKNESS = 0.003
SLIVER_ASPECT = 10
# A loose part this many times longer than its second size is a needle, however thick
NEEDLE_ASPECT = 8
# Needles attached to the surface, as fractions of the mesh's size: neighbors within the reach
# at the same height (heights stretched by the flatten factor) all inside the width make a
# vertex thin, and a connected run of thin vertices is a needle if it is longer than the length,
# stays within the width of a straight line, and leans less than the tilt (degrees)
NEEDLE_REACH = 0.03
NEEDLE_WIDTH = 0.02
NEEDLE_FLATTEN = 20
NEEDLE_LENGTH = 0.2
NEEDLE_TILT = 15
# Fewer faces than this is a speck
SPECK_FACES = 12
# Turning an accessory to face as its picture does: how many turns about the vertical are tried,
# the grid outlines are compared on, and how far a pixel's color must be from the picture's
# border color (summed over red, green and blue, 0 to 765) to count as the object
TURNS = 36
TURN_GRID = 48
PICTURE_CONTRAST = 45
# Turns whose overlap is within this of the best count as ties, and the smallest of them wins:
# a nearly symmetric object's outline barely changes when it turns round, so a large turn needs
# a clearly better match
TURN_TOLERANCE = 0.03
# Spans are measured between these percentiles of an accessory's points, so a strap end or a
# stray bit doesn't count as its size
SPAN_PERCENTILES = (2, 98)
# Resting an accessory on the body, the share of its points let come nearer than the gap, so a
# strap end pressing in doesn't hold the rest of it away
CONTACT_PERCENTILE = 3
# How far out an accessory starts before it is moved in onto the body, in meters
DROP_START = 0.5
# Which way each side an accessory may sit on points, in Blender's space (the mannequin faces -Y)
SIDES = {"front": (0, -1, 0), "back": (0, 1, 0), "up": (0, 0, 1)}
# Slices the mesh is cut into to find its crown, and the points a slice needs to count
CROWN_SLICES = 40
SLICE_POINTS = 30
# The brim is looked for in this lower fraction of the height, and the crown measured in this
# band above it, as fractions of the height
BRIM_REGION = 0.4
CROWN_BAND = (0.08, 0.35)
# Armor weighted from the skin takes each vertex's weights from this many nearest skin vertices,
# and keeps this many bones
SKIN_NEIGHBORS = 4
SKIN_BONES = 4
# A skin shell is cut across the limb this percent of the generated piece short of its end
# toward the body, leaving out stray bits past it
SHELL_END_PERCENTILE = 1
# A skin shell is split flat at most this many times
SHELL_MAX_LEVELS = 2
# The skin kept near a piece is closed up by this many rings of faces
NEAR_CLOSE = 2
# A skin shell's bake rays start this far beyond its reach out, in meters
SHELL_BAKE_MARGIN = 0.015
# Pushing armor out of the skin spreads each round's moves over the mesh with this many rounds
# of averaging, and is done this many times
PUSH_SPREAD = 8
PUSH_ROUNDS = 4
# Bake rays reach this far from the low-poly surface, as a fraction of the part's size
BAKE_REACH = 0.08
# How far ambient occlusion looks for cover, as a share of the part's size
OCCLUSION_REACH = 0.15
# What texels the bake doesn't reach hold
UNBAKED = (1.0, 0.0, 1.0, 1.0)
# And this color in a second bake, to find the texels only partly baked
UNBAKED_AGAIN = (0.0, 1.0, 0.0, 1.0)
# Baked colors are spread this many texels over the partly baked ones beside them
BAKE_GROW = 8
# A tangent-space normal map's flat normal, for texels the bake doesn't reach
FLAT_NORMAL = (0.5, 0.5, 1.0, 1.0)
# The node group the glTF exporter reads occlusion from
GLTF_OUTPUT = "glTF Material Output"

# Tint masks: how much lightness counts against hue when grouping colors, how many texels to
# group from, the mean spread (in Lab units) a group may have, the smallest share of the texels
# a group keeps, how close (in Lab units) two groups may be before they merge, and the lightness
# percentile (give or take TINT_BAND) of a group's colors taken as its color: above the median,
# since generated base colors carry baked shadow, but below highlights like a light brim edge
MASK_CHANNELS = ("r", "g", "b", "a")
# Beside a part's glb, the folder of tint masks Ikala-Characters looks for (CharacterFiles)
MASK_FOLDER_SUFFIX = "_masks"
TINT_LIGHTNESS = 0.35
TINT_SAMPLE = 20000
TINT_SPREAD = 10.0
TINT_SMALLEST = 0.03
TINT_ALIKE = 12.0
TINT_REFERENCE = 80
TINT_BAND = 5
# How much of the texture's lightness a tinted group keeps, as a power of its lightness against
# the group's color: 0 makes the tint replace the base color outright, as a design's color is
# meant to (the normal and ORM maps still carry the shape and wear); 1 keeps it all. Generated
# base colors carry baked shading, which kept would leave a recolored crown darker than its brim
TINT_DETAIL = 0.0
# Relative luminance of linear sRGB
LUMINANCE = np.array([0.2126, 0.7152, 0.0722])
# Voxels across the part's largest side when remeshing it closed before decimating
REMESH_VOXELS = 160
DECIMATE_PASSES = 10


def vertices(obj):
    """An object's vertices in world space, as an n by 3 array."""
    count = len(obj.data.vertices)
    co = np.empty(count * 3)
    obj.data.vertices.foreach_get("co", co)
    co = co.reshape(-1, 3)
    m = np.array(obj.matrix_world)
    return co @ m[:3, :3].T + m[:3, 3]


def select_only(*objects):
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = objects[0]


def import_glb(path):
    """Import a glb and give the objects it added."""
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    return [obj for obj in bpy.data.objects if obj not in before]


def generated_mesh(path):
    """The generated glb as one mesh object, with its transforms applied."""
    meshes = [obj for obj in import_glb(path) if obj.type == "MESH"]
    for obj in meshes:
        select_only(obj)
        bpy.ops.object.parent_clear(type="CLEAR_KEEP_TRANSFORM")
    select_only(*meshes)
    if len(meshes) > 1:
        bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    obj.name = "high"
    return obj


def weld(obj):
    """Join vertices TRELLIS.2 split along its texture seams; UVs stay, since Blender keeps them
    per face corner."""
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    before = len(bm.verts)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    print("[fit] welded %d vertices to %d" % (before, len(bm.verts)))
    bm.to_mesh(obj.data)
    bm.free()


def is_needle(points, size, narrow):
    """Whether a run of thin vertices is a needle: long, straight (close to its own best line)
    and upright."""
    if len(points) < 3:
        return False
    centered = points - points.mean(0)
    _, spread, axes = np.linalg.svd(centered, full_matrices=False)
    axis = axes[0]
    along = centered @ axis
    off_line = np.linalg.norm(centered - np.outer(along, axis), axis=1).max()
    return (along.max() - along.min() > size * NEEDLE_LENGTH and off_line < narrow
            and abs(axis[2]) > math.cos(math.radians(NEEDLE_TILT)))


def drop_needles(obj):
    """Remove the straight vertical needles generated meshes can carry, attached to the surface.
    A thin vertex has its neighbors at the same height crowded into a narrow cross-section,
    where on a surface they spread out along it. A connected run of thin vertices is a needle
    only if it is also long, straight and upright, which thin parts made on purpose rarely all
    are: tassels sway and are short, grating bars lie mostly level, a crown's point is short."""
    from mathutils.kdtree import KDTree
    co = vertices(obj)
    size = (co.max(0) - co.min(0)).max()
    radius, narrow = size * NEEDLE_REACH, size * NEEDLE_WIDTH
    tree = KDTree(len(co))
    for i, p in enumerate(co):
        tree.insert((p[0], p[1], p[2] * NEEDLE_FLATTEN), i)  # neighbors come from a thin slice
    tree.balance()
    thin = np.zeros(len(co), dtype=bool)
    for i, p in enumerate(co):
        near = co[[index for _, index, _ in tree.find_range((p[0], p[1], p[2] * NEEDLE_FLATTEN),
                                                            radius)]]
        thin[i] = (near[:, :2].max(0) - near[:, :2].min(0)).max() < narrow

    bm = bmesh.new()
    bm.from_mesh(obj.data)
    bm.verts.ensure_lookup_table()
    seen, doomed = set(), []
    for start in (v for v in bm.verts if thin[v.index]):
        if start.index in seen:
            continue
        run, stack = [], [start]
        seen.add(start.index)
        while stack:
            v = stack.pop()
            run.append(v)
            for edge in v.link_edges:
                other = edge.other_vert(v)
                if thin[other.index] and other.index not in seen:
                    seen.add(other.index)
                    stack.append(other)
        if is_needle(np.array([v.co for v in run]), size, narrow):
            doomed += run
    bmesh.ops.delete(bm, geom=doomed, context="VERTS")
    bm.to_mesh(obj.data)
    bm.free()
    print("[fit] dropped %d needle vertices" % len(doomed))


def drop_slivers(obj):
    """Remove loose parts that are needles, sheets or specks, and give the mesh back whole."""
    select_only(obj)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.mesh.separate(type="LOOSE")
    bpy.ops.object.mode_set(mode="OBJECT")
    pieces = list(bpy.context.selected_objects)
    kept = []
    for piece in pieces:
        co = vertices(piece)
        thin, middle, long = np.sort(co.max(0) - co.min(0))
        sheet = thin < SLIVER_THICKNESS and long > SLIVER_ASPECT * thin
        needle = long > NEEDLE_ASPECT * middle
        if sheet or needle or len(piece.data.polygons) < SPECK_FACES:
            bpy.data.objects.remove(piece)
        else:
            kept.append(piece)
            print("[fit] kept a part of %d faces, %.3f x %.3f x %.3f" % (
                len(piece.data.polygons), thin, middle, long))
    print("[fit] kept %d of %d loose parts" % (len(kept), len(pieces)))
    select_only(*kept)
    if len(kept) > 1:
        bpy.ops.object.join()
    whole = bpy.context.view_layer.objects.active
    whole.name = "high"
    return whole


def width_at(points, z, band):
    """Side to side width, and center, of the points within band of a height, or None if too few
    are there. The extremes are left out, so a stray point can't widen it."""
    near = points[np.abs(points[:, 2] - z) < band]
    if len(near) < SLICE_POINTS:
        return None
    left, right = np.percentile(near[:, 0], [1, 99])
    lo, hi = np.percentile(near, [1, 99], axis=0)
    return right - left, (lo + hi) / 2


def section(obj, z):
    """Where an object's edges cross a height, as points; works on meshes with few vertices."""
    co = vertices(obj)
    count = len(obj.data.edges)
    ends = np.empty(count * 2, dtype=np.int64)
    obj.data.edges.foreach_get("vertices", ends)
    a, b = co[ends[0::2]], co[ends[1::2]]
    crossing = (a[:, 2] - z) * (b[:, 2] - z) <= 0
    a, b = a[crossing], b[crossing]
    dz = b[:, 2] - a[:, 2]
    t = np.where(np.abs(dz) > 1e-9, (z - a[:, 2]) / np.where(dz == 0, 1, dz), 0.5)
    return a + (b - a) * t[:, None]


def measure_crown(points, low, height):
    """The crown of headwear: the band just above the brim, where the head goes. The brim is the
    widest slice of the lower part (the bottom edge of a brimless hat); the crown's width is the
    median across the band above it, so a stray drooping edge can't decide it. Gives the width,
    the center, and the height below which lies the brim."""
    band = height / CROWN_SLICES
    slices = []
    for k in range(CROWN_SLICES):
        z = low + band * (k + 0.5)
        found = width_at(points, z, band / 2)
        if found is not None:
            slices.append(found + (z,))
    lower = [s for s in slices if s[2] < low + height * BRIM_REGION]
    brim_z = max(lower, key=lambda s: s[0])[2]
    above = [s for s in slices
             if brim_z + height * CROWN_BAND[0] <= s[2] <= brim_z + height * CROWN_BAND[1]]
    if not above:
        above = [s for s in slices if s[2] >= brim_z]
    width = float(np.median([s[0] for s in above]))
    center = np.mean([s[1] for s in above], axis=0)
    return width, center, brim_z + height * CROWN_BAND[0]


def place_bottom_crown(high, mannequin, armature, rule):
    """Size and place headwear: bottom above a bone, crown a little wider than the head. A hat too
    shallow to cover the top of the head from there grows until it does, as caps and sun hats
    are wider than the head; sitting lower would only drop its top further."""
    bones = armature.data.bones
    brow = (armature.matrix_world @ bones[rule["bottomAbove"]].head_local).z
    head = next((obj for obj in mannequin if "head" in obj.name.lower()), None)
    if head is None:
        raise SystemExit("The mannequin has no head mesh among %s" % [o.name for o in mannequin])
    head_top = vertices(head)[:, 2].max()

    points = vertices(high)
    low, high_z = points[:, 2].min(), points[:, 2].max()
    crown_width, crown_center, crown_z = measure_crown(points, low, high_z - low)

    bottom = brow + rule["bottomOffset"]
    ring = section(head, bottom)
    if len(ring) < 3:
        raise SystemExit("The mannequin's head doesn't reach %.3f m" % bottom)
    head_width = ring[:, 0].max() - ring[:, 0].min()
    head_center = (ring.min(0) + ring.max(0)) / 2
    scale = head_width * rule["clearance"] / crown_width
    covering = (head_top + rule["topClearance"] - bottom) / (high_z - low)
    if covering > scale:
        print("[fit] too shallow at %.3f, grown to cover the head" % scale)
        scale = covering
    # A brief can nudge the size after review
    scale *= rule.get("scale", 1.0)
    print("[fit] head %.3f m wide at %.3f m, crown %.3f, scale %.3f" % (
        head_width, bottom, crown_width, scale))

    high.scale = (scale, scale, scale)
    high.location = (head_center[0] - crown_center[0] * scale,
                     head_center[1] - crown_center[1] * scale,
                     bottom - low * scale)
    select_only(high)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    pull_in_brim(high, head_center, crown_width * scale / 2, bottom + (crown_z - low) * scale,
                 head_width * rule["maxWidth"] / 2)


def mirror_x(obj):
    """Mirror a mesh across the body's middle, keeping its faces turned out: applying a negative
    scale turns its faces back itself (reversing them again, as this once did, left mirrored
    sides inside out, their baked normals shading them dark)."""
    obj.scale = (-1, 1, 1)
    select_only(obj)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)


def picture_outline(path):
    """Which cells of a TURN_GRID grid the object in a picture covers: pixels whose color is
    PICTURE_CONTRAST from the border's (generated pictures have plain, often graded, grounds),
    cropped to the object and centered in a square, top row first."""
    image = bpy.data.images.load(os.path.abspath(path))
    width, height = image.size
    pixels = np.empty(width * height * 4, dtype=np.float32)
    image.pixels.foreach_get(pixels)
    rgb = np.flipud(pixels.reshape(height, width, 4)[..., :3]) * 255
    border = np.concatenate([rgb[0], rgb[-1], rgb[:, 0], rgb[:, -1]])
    ground = np.median(border, axis=0)
    shown = np.abs(rgb - ground).sum(-1) > PICTURE_CONTRAST
    rows, columns = np.nonzero(shown)
    if len(rows) == 0:
        return None
    return square_outline(columns.astype(float), rows.astype(float))


def square_outline(x, y):
    """Points (x right, y down) on a TURN_GRID grid, scaled so their larger span fills it and
    centered, grown a cell so scattered points cover their outline."""
    low_x, high_x = np.percentile(x, [1, 99])
    low_y, high_y = np.percentile(y, [1, 99])
    size = max(high_x - low_x, high_y - low_y, 1e-9)
    column = ((x - (low_x + high_x) / 2) / size + 0.5) * (TURN_GRID - 1)
    row = ((y - (low_y + high_y) / 2) / size + 0.5) * (TURN_GRID - 1)
    inside = (column >= 0) & (column < TURN_GRID) & (row >= 0) & (row < TURN_GRID)
    cells = np.zeros((TURN_GRID, TURN_GRID), dtype=bool)
    cells[row[inside].astype(int), column[inside].astype(int)] = True
    grown = cells.copy()
    grown[1:] |= cells[:-1]
    grown[:-1] |= cells[1:]
    grown[:, 1:] |= cells[:, :-1]
    grown[:, :-1] |= cells[:, 1:]
    return grown


def face_as_pictured(high, picture):
    """Turn a generated mesh about the vertical so it faces -Y as its picture showed it: TRELLIS
    keeps a picture's up but turns the object freely about it, so each of TURNS turns is seen
    from the front and the one whose outline best overlaps the picture's is kept, the smallest
    turn among near ties. A picture seen from an angle gives that angle, so pictures should be
    straight on. Gives the turn in degrees and its overlap, or None if the picture shows
    nothing."""
    wanted = picture_outline(picture)
    if wanted is None:
        return None
    points = vertices(high)
    points = points[:: max(1, len(points) // 20000)]
    middle = points.mean(0)
    overlaps = []
    for k in range(TURNS):
        angle = 2 * math.pi * k / TURNS
        c, s = math.cos(angle), math.sin(angle)
        x = (points[:, 0] - middle[0]) * c - (points[:, 1] - middle[1]) * s
        # Seen from -Y: x to the right, up the picture's rows run down
        outline = square_outline(x, -(points[:, 2] - middle[2]))
        overlap = (outline & wanted).sum() / max((outline | wanted).sum(), 1)
        overlaps.append((float(overlap), angle))
    unturned = overlaps[0][0]
    most = max(overlap for overlap, _ in overlaps)
    best = min((pair for pair in overlaps if pair[0] >= most - TURN_TOLERANCE),
               key=lambda pair: min(pair[1], 2 * math.pi - pair[1]))
    high.matrix_world = Matrix.Rotation(best[1], 4, "Z") @ high.matrix_world
    select_only(high)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    print("[fit] turned %.0f degrees to face as pictured, overlap %.3f (best %.3f, unturned %.3f)"
          % (math.degrees(best[1]), best[0], most, unturned))
    return round(math.degrees(best[1])), round(best[0], 3)


def side_direction(rule, armature, bone):
    """Which way, in Blender's space (the mannequin faces -Y), an accessory sits from the body,
    and which way is up for it. The rule's "side" is back, front or up, or "out": along the
    socket bone, which points out of its limb, with up along the limb toward its root, so
    something on a sloping arm sits square on it."""
    if rule["side"] == "out":
        socket = armature.data.bones[bone]
        direction = (armature.matrix_world.to_3x3() @ (socket.tail_local - socket.head_local))
        direction.normalize()
        limb = socket.parent
        up = armature.matrix_world.to_3x3() @ (limb.head_local - limb.tail_local)
    else:
        direction = Vector(SIDES[rule["side"]])
        up = Vector((0, 0, 1)) if abs(direction.z) < 0.5 else Vector((0, 1, 0))
    up = (up - direction * up.dot(direction)).normalized()
    return direction, up


def measure(mannequin, spec, socket_head):
    """A body measurement for sizing, like "torso.width": the named slot's mesh (a slot ending
    in ".?" takes the socket's side), and what of it: width (side to side) or depth (front to
    back) where it crosses the socket's height, height (its whole span up and down), length
    (its span along its longest axis) or girth (twice its median distance from that axis)."""
    slot, what = spec.rsplit(".", 1)
    if slot.endswith(".?"):
        slot = slot[:-1] + ("L" if socket_head.x >= 0 else "R")
    obj = next((o for o in mannequin if o.name == "mannequin_" + slot), None)
    if obj is None:
        raise SystemExit("The mannequin has no slot mesh for " + slot)
    points = vertices(obj)
    if what in ("width", "depth"):
        ring = section(obj, socket_head.z)
        if len(ring) < 3:
            ring = points
        axis = 0 if what == "width" else 1
        return float(ring[:, axis].max() - ring[:, axis].min())
    if what == "height":
        return float(points[:, 2].max() - points[:, 2].min())
    center = points.mean(0)
    _, _, rows = np.linalg.svd(points - center, full_matrices=False)
    along = (points - center) @ rows[0]
    if what == "length":
        return float(along.max() - along.min())
    if what == "girth":
        apart = (points - center) - np.outer(along, rows[0])
        return float(2 * np.median(np.linalg.norm(apart, axis=1)))
    raise SystemExit("Measurements are width, depth, height, length or girth, not " + what)


def body_tree(mannequin):
    """The mannequin's skin as one BVH tree, for casting onto its surface."""
    points, faces = [], []
    start = 0
    for obj in mannequin:
        co = vertices(obj)
        for polygon in obj.data.polygons:
            ids = list(polygon.vertices)
            faces += [(ids[0] + start, ids[k] + start, ids[k + 1] + start)
                      for k in range(1, len(ids) - 1)]
        points.append(co)
        start += len(co)
    return BVHTree.FromPolygons([tuple(p) for p in np.concatenate(points)], faces)


def place_against_body(high, mannequin, armature, bone, rule):
    """Size and place an accessory against the body beside a socket: turned so its outer face,
    the one the picture showed (toward -Y), looks along the rule's side; sized so one of its
    spans is a share of a body measurement; scaled down whole if any span passes its limit; and
    moved so its inner face rests "gap" off the body's surface beside the socket, centered on
    the socket across and up. Gives a report of what was done."""
    head = armature.matrix_world @ armature.data.bones[bone].head_local
    # A socket that sits a little off where its accessories belong, like a belt socket below the
    # belt, can be moved for them
    offset = Vector(rule.get("offset", (0, 0, 0)))
    if head.x < 0:
        # Given for the left side; the right's is its mirror
        offset.x = -offset.x
    head = head + offset
    direction, up = side_direction(rule, armature, bone)
    across = up.cross(direction).normalized()
    # The outer face looks toward -Y and its top toward +Z; turn them to the side and up
    turn = Matrix((across, -direction, up)).transposed().to_4x4()
    high.matrix_world = turn @ high.matrix_world
    select_only(high)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

    # Its spans along its own axes: across, up and out from the body
    axes = {"across": np.array(across), "up": np.array(up), "depth": np.array(direction)}
    points = vertices(high)

    def span(axis):
        low, high_end = np.percentile(points @ axes[axis], SPAN_PERCENTILES)
        return float(high_end - low)

    wanted = rule["size"]
    scale = (measure(mannequin, wanted["of"], head) * wanted["share"]
             / max(span(wanted["axis"]), 1e-6))
    scale *= rule.get("scale", 1.0)
    limited = 1.0
    for limit in rule.get("maxSize", []):
        most = measure(mannequin, limit["of"], head) * limit["share"]
        limited = min(limited, most / max(span(limit["axis"]) * scale, 1e-6))
    scale *= limited
    spans = {axis: round(span(axis) * scale, 4) for axis in axes}
    high.scale = (scale, scale, scale)
    select_only(high)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

    # The body's surface beside the socket: the first hit coming in from outside along the side
    tree = body_tree(mannequin)
    hit, _, _, _ = tree.ray_cast(head + direction * 2.0, -direction)
    surface = hit if hit is not None else head

    # Across and up it centers on the socket, and starts well out from the surface
    points = vertices(high)
    low, high_end = np.percentile(points, SPAN_PERCENTILES, axis=0)
    move = np.array(head) - (low + high_end) / 2
    move -= axes["depth"] * float(move @ axes["depth"])
    inner = float(np.percentile(points @ axes["depth"], SPAN_PERCENTILES[0]))
    start = float(np.array(surface) @ axes["depth"]) + DROP_START
    move += axes["depth"] * (start - inner)
    points = points + move
    # Then it moves in along the side until the body is the gap away from all but a few of its
    # points, so it rests on the body's curve rather than on its one innermost point
    inward = Vector((-axes["depth"]).tolist())
    # Only the outer "contact" share of its depth rests on the body, so the sides of something
    # that wraps around, like a mask, can pass beside the head instead of holding it off
    depth = points @ axes["depth"]
    low, high_end = np.percentile(depth, SPAN_PERCENTILES)
    resting = points[depth >= high_end - rule.get("contact", 1.0) * (high_end - low)]
    reach = []
    for point in resting[:: max(1, len(resting) // 4000)]:
        found, _, _, distance = tree.ray_cast(Vector(point.tolist()), inward)
        if found is not None:
            reach.append(distance)
    gap = rule.get("gap", 0.0)
    travel = (float(np.percentile(reach, CONTACT_PERCENTILE)) if reach else DROP_START) - gap
    move -= axes["depth"] * travel
    high.location = Vector(move.tolist())
    select_only(high)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    report = {"scale": round(scale, 4), "limited": round(limited, 3), "spans": spans,
              "surface": [round(v, 4) for v in surface], "hit": hit is not None}
    print("[fit] against the body:", json.dumps(report))
    return report


def pull_in_brim(obj, center, crown, top, limit):
    """Pull the brim, what lies below the crown's narrowest height and outside it, toward the
    crown, so it reaches at most limit from the center. Image models draw brims far wider than
    they are worn, and shrinking the whole hat instead would leave the crown too small."""
    co = np.empty(len(obj.data.vertices) * 3)
    obj.data.vertices.foreach_get("co", co)
    co = co.reshape(-1, 3)
    offset = co[:, :2] - center[:2]
    radius = np.linalg.norm(offset, axis=1)
    brim = co[:, 2] < top
    widest = radius[brim].max() if brim.any() else 0
    if widest <= limit:
        return
    outside = brim & (radius > crown)
    pulled = crown + (radius[outside] - crown) * (limit - crown) / (widest - crown)
    co[outside, :2] = center[:2] + offset[outside] * (pulled / radius[outside])[:, None]
    obj.data.vertices.foreach_set("co", co.ravel())
    obj.data.update()
    print("[fit] pulled the brim in from %.3f m to %.3f m across" % (widest * 2, limit * 2))


def place_enclose(high, mannequin, armature, rule):
    """Size and place a helmet round the head: centered on the head across and front to back,
    as wide and deep as the head (from the rule's bottom up) times "clearance", its top
    "topClearance" over the head's, and reaching down at least to "bottomOffset" from the
    "bottomAbove" bone. Gives a report of what was done."""
    head = next((obj for obj in mannequin if "head" in obj.name.lower()), None)
    if head is None:
        raise SystemExit("The mannequin has no head mesh among %s" % [o.name for o in mannequin])
    skull = vertices(head)
    bones = armature.data.bones
    bottom = (armature.matrix_world @ bones[rule["bottomAbove"]].head_local).z \
        + rule["bottomOffset"]
    top = float(skull[:, 2].max()) + rule["topClearance"]
    above = skull[skull[:, 2] >= bottom]
    low_end, high_end = above.min(0), above.max(0)
    head_span = high_end - low_end
    head_middle = (low_end + high_end) / 2
    points = vertices(high)
    lowest, highest = np.percentile(points, SPAN_PERCENTILES, axis=0)
    span = highest - lowest
    middle = (lowest + highest) / 2
    scale = max(head_span[0] * rule["clearance"] / max(span[0], 1e-6),
                head_span[1] * rule["clearance"] / max(span[1], 1e-6),
                (top - bottom) / max(span[2], 1e-6))
    scale *= rule.get("scale", 1.0)
    high.scale = (scale, scale, scale)
    high.location = (float(head_middle[0] - middle[0] * scale),
                     float(head_middle[1] - middle[1] * scale),
                     float(top - points[:, 2].max() * scale))
    select_only(high)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    report = {"scale": round(scale, 4), "head": [round(float(v), 4) for v in head_span],
              "bottom": round(float(bottom), 4), "top": round(top, 4)}
    print("[fit] round the head:", json.dumps(report))
    return report


def push_out_of_skin(obj, mannequin, gap):
    """Move a mesh out of the body, so armor shows over it: every vertex inside the body, or
    nearer its skin than gap, needs to go out along the skin's normal to gap from it, and the
    rest of the mesh is carried along, the moves spread over it by PUSH_SPREAD rounds of
    averaging with neighbors, so the piece moves out whole rather than its vertices each snapping
    to the nearest bit of skin and tearing it. That is done PUSH_ROUNDS times, as the carried
    vertices can need moving in turn. Gives how many vertices needed moving at first."""
    tree = body_tree(mannequin)
    co = vertices(obj)
    edges = np.empty(len(obj.data.edges) * 2, dtype=np.int64)
    obj.data.edges.foreach_get("vertices", edges)
    edges = edges.reshape(-1, 2)
    count = np.zeros(len(co))
    np.add.at(count, edges.ravel(), 1)
    count = np.maximum(count, 1)[:, None]
    first = None
    for _ in range(PUSH_ROUNDS):
        need = np.zeros_like(co)
        for i, point in enumerate(co):
            location, normal, _, _ = tree.find_nearest(Vector(point.tolist()))
            if location is None:
                continue
            outside = (Vector(point.tolist()) - location).dot(normal)
            if outside < gap:
                need[i] = np.array(location + normal * gap) - point
        pinned = np.linalg.norm(need, axis=1) > 0
        if first is None:
            first = int(pinned.sum())
        if not pinned.any():
            break
        field = need.copy()
        for _ in range(PUSH_SPREAD):
            total = np.zeros_like(field)
            np.add.at(total, edges[:, 0], field[edges[:, 1]])
            np.add.at(total, edges[:, 1], field[edges[:, 0]])
            field = np.where(pinned[:, None], need, total / count)
        co = co + field
    inverse = np.array(obj.matrix_world.inverted())
    local = co @ inverse[:3, :3].T + inverse[:3, 3]
    obj.data.vertices.foreach_set("co", local.ravel())
    obj.data.update()
    print("[fit] pushed %d of %d vertices out of the skin to %.3f m, %d left after"
          % (first, len(co), gap, int(pinned.sum()) if pinned.any() else 0))
    return first


def add_mirror(low):
    """Join a mirror of a mesh, across the body's middle, to it: the other hand's or foot's copy,
    sharing its texture."""
    other = low.copy()
    other.data = low.data.copy()
    bpy.context.scene.collection.objects.link(other)
    mirror_x(other)
    select_only(low, other)
    bpy.context.view_layer.objects.active = low
    bpy.ops.object.join()


def rig_skin(low, armature, mannequin, slots):
    """Weight each vertex like the skin it covers: from its SKIN_NEIGHBORS nearest vertices of
    the mannequin's meshes for those slots, nearer ones counting more, keeping the SKIN_BONES
    heaviest bones; then put the mesh on the armature."""
    from mathutils.kdtree import KDTree
    skins = [obj for obj in mannequin if obj.name in {"mannequin_" + s for s in slots}]
    if not skins:
        raise SystemExit("The mannequin has no meshes for %s among %s"
                         % (slots, [o.name for o in mannequin]))
    points, weights = [], []
    for obj in skins:
        names = {group.index: group.name for group in obj.vertex_groups}
        points.append(vertices(obj))
        for vertex in obj.data.vertices:
            weights.append({names[g.group]: g.weight for g in vertex.groups if g.weight > 0})
    points = np.concatenate(points)
    kd = KDTree(len(points))
    for i, point in enumerate(points):
        kd.insert(point.tolist(), i)
    kd.balance()
    groups = {}
    for vertex, point in zip(low.data.vertices, vertices(low)):
        blend = {}
        for _, index, distance in kd.find_n(point.tolist(), SKIN_NEIGHBORS):
            share = 1.0 / max(distance, 1e-4)
            for bone, weight in weights[index].items():
                blend[bone] = blend.get(bone, 0.0) + weight * share
        kept = sorted(blend.items(), key=lambda pair: -pair[1])[:SKIN_BONES]
        total = sum(weight for _, weight in kept)
        for bone, weight in kept:
            if bone not in armature.data.bones:
                continue
            if bone not in groups:
                groups[bone] = low.vertex_groups.new(name=bone)
            groups[bone].add([vertex.index], weight / total, "REPLACE")
    modifier = low.modifiers.new("Armature", "ARMATURE")
    modifier.object = armature
    world = low.matrix_world.copy()
    low.parent = armature
    low.matrix_parent_inverse = armature.matrix_world.inverted()
    low.matrix_world = world
    print("[fit] weighted from the skin of %s: %s" % (", ".join(slots), ", ".join(sorted(groups))))


def apply_modifier(obj, kind, **settings):
    select_only(obj)
    modifier = obj.modifiers.new(kind, kind)
    for key, value in settings.items():
        setattr(modifier, key, value)
    bpy.ops.object.modifier_apply(modifier=modifier.name)


def low_poly(high, name, triangles):
    """A closed, decimated, triangulated, unwrapped copy. Generated meshes have open edges and
    overlapping layers that stall decimation and fold it into spikes, so the copy is remeshed
    into one closed surface first."""
    low = high.copy()
    low.data = high.data.copy()
    low.name = name
    low.data.name = name
    bpy.context.scene.collection.objects.link(low)
    voxel = max(low.dimensions) / REMESH_VOXELS
    # Generated shells can be thinner than a voxel, which remeshes into a sieve that decimation
    # then collapses, so they are thickened to a couple of voxels first
    apply_modifier(low, "SOLIDIFY", thickness=voxel * 2, offset=0.0)
    apply_modifier(low, "REMESH", mode="VOXEL", voxel_size=voxel, adaptivity=0.0)
    # Collapsing stops short of a low target, so it goes again until close
    for _ in range(DECIMATE_PASSES):
        faces = len(low.data.polygons)
        if faces <= triangles * 1.05:
            break
        apply_modifier(low, "DECIMATE", ratio=triangles / faces)
    unwrap(low)
    print("[fit] low poly: %d triangles" % len(low.data.polygons))
    return low


def unwrap(low):
    """Triangulate, shade smooth (so the normal map is baked against the normals the game
    interpolates) and unwrap a low-poly mesh afresh, with no materials."""
    apply_modifier(low, "TRIANGULATE")
    select_only(low)
    bpy.ops.object.shade_smooth()
    low.data.materials.clear()
    while low.data.uv_layers:
        low.data.uv_layers.remove(low.data.uv_layers[0])
    low.data.uv_layers.new(name="UVMap")
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.02)
    bpy.ops.object.mode_set(mode="OBJECT")


def skin_low(high, mannequin, slots, rule, name):
    """Armor made from the mannequin's own skin rather than the generated mesh, for a glove or
    a boot, which has to follow the hand or foot as it is, fingers spread and all: the skin of
    its slots and of the "extra" ones above them (shell_layer.py's skin_shell), pushed out by the
    rule's gap and onto the generated piece, lined up over it, where that stands off the skin, as
    far as the rule's "reach" (a flared cuff, a fur fold, a pauldron's plates), its faces kept
    the gap across as well as at their corners (shell_layer.py's push_faces), then cut across
    the limb where the piece ends, both ends. With the shell's "near", only the skin that near
    the piece is kept (see keep_near). The skin is split flat (SHELL_MAX_LEVELS) only as
    far as the rule's "triangles" allow. Its colors and detail are baked from the piece as
    usual."""
    shell_rule = rule["shell"]
    covered = shell_slots(slots, shell_rule, mannequin)
    # The skin as it is never sags inside itself, as smooth subdivision does on a calf or a
    # heel; split flat as many times as the triangle budget allows, to follow the piece closer
    low = skin_shell(set(covered), levels=0)
    if shell_rule.get("near"):
        keep_near(low, high, shell_rule["near"])
    triangles = sum(len(p.vertices) - 2 for p in low.data.polygons)
    levels = 0
    while levels < SHELL_MAX_LEVELS and triangles * 4 ** (levels + 1) <= rule["triangles"]:
        levels += 1
    if levels:
        apply_modifier(low, "SUBSURF", levels=levels, subdivision_type="SIMPLE")
    low.name = name
    low.data.name = name
    surface = body_tree(mannequin)
    onto = conform(low, high, surface, rule.get("gap", 0.0), shell_rule.get("reach"))
    # Faces that sag between their corners are pushed out, then any vertex still inside or
    # nearer than the gap, a crease at an ankle or a knuckle, carrying its neighbors along
    sag = push_faces(low, surface, rule.get("gap", 0.0))
    push_out_of_skin(low, mannequin, rule.get("gap", 0.0))
    # Across the limb where the piece ends, at both ends, leaving out stray bits past them
    points = vertices(low)
    piece = vertices(high)
    middle, axis = points.mean(0), np.linalg.svd(points - points.mean(0),
                                                 full_matrices=False)[2][0]
    # Pointing toward the body's middle, as a wrist's or a knee's end does
    body = np.concatenate([vertices(obj) for obj in mannequin]).mean(0)
    if np.linalg.norm(middle + axis - body) > np.linalg.norm(middle - axis - body):
        axis = -axis
    along = (piece - middle) @ axis
    end = float(np.percentile(along, 100 - SHELL_END_PERCENTILE))
    start = float(np.percentile(along, SHELL_END_PERCENTILE))
    mesh = bmesh.new()
    mesh.from_mesh(low.data)
    for at, facing in ((end, axis), (start, -axis)):
        bmesh.ops.bisect_plane(mesh, geom=mesh.verts[:] + mesh.edges[:] + mesh.faces[:],
                               plane_co=Vector((middle + axis * at).tolist()),
                               plane_no=Vector(facing.tolist()), clear_outer=True)
    mesh.to_mesh(low.data)
    mesh.free()
    unwrap(low)
    print("[fit] skin shell over %s: split %d times, %d triangles, %d vertices onto the piece, "
          "sagging faces pushed out up to %.1f mm, cut %.3f m toward the body and %.3f m away "
          "from the middle" % (", ".join(covered), levels, len(low.data.polygons), onto,
                               sag * 1000, end, -start))
    return low


def shell_slots(slots, shell_rule, mannequin):
    """The slots a skin shell is made of: the piece's own and its shell's "extra" ones, each on
    the sides the piece's slots are on if the mannequin has it sided (a lowerArm.L), else as
    named (the torso)."""
    names = {obj.name for obj in mannequin}
    sides = sorted({s[-2:] for s in slots if s.endswith((".L", ".R"))}) or [""]
    extras = []
    for extra in shell_rule.get("extra", []):
        sided = [extra + side for side in sides if "mannequin_" + extra + side in names]
        extras += sided or [extra]
    return slots + [extra for extra in extras if extra not in slots]


def keep_near(low, high, near):
    """Keep only the faces of a skin shell whose middles lie within near meters of the
    generated piece, as a pauldron covers the top and outside of the arm, not its inside."""
    piece = BVHTree.FromPolygons([high.matrix_world @ v.co for v in high.data.vertices],
                                 [tuple(p.vertices) for p in high.data.polygons])
    mesh = bmesh.new()
    mesh.from_mesh(low.data)
    mesh.faces.ensure_lookup_table()
    world = low.matrix_world
    kept = {face.index for face in mesh.faces
            if piece.find_nearest(world @ face.calc_center_median(), near)[0] is not None}
    # Closed up: grown by NEAR_CLOSE rings of faces and shrunk back as far, which fills holes
    # where the piece stands further off, as a scooped plate's middle does, and smooths the edge
    neighbors = {face.index: {other.index for edge in face.edges for other in edge.link_faces
                              if other is not face} for face in mesh.faces}
    for _ in range(NEAR_CLOSE):
        kept |= {other for index in kept for other in neighbors[index]}
    for _ in range(NEAR_CLOSE):
        kept = {index for index in kept if neighbors[index] <= kept}
    far = [face for face in mesh.faces if face.index not in kept]
    bmesh.ops.delete(mesh, geom=far, context="FACES")
    mesh.to_mesh(low.data)
    mesh.free()
    low.data.update()
    print("[fit] kept the skin within %.3f m of the piece: %d faces left out"
          % (near, len(far)))


def grow_baked(image, covered, size):
    """Spread the baked colors over the texels beside them that the bake only partly reached or
    missed, BAKE_GROW texels deep, each taking the mean of its baked neighbors, and count those
    as baked: island edges and their margins, which kept the unbaked color blended in."""
    pixels = np.array(image.pixels[:]).reshape(size, size, 4)
    done = covered.reshape(size, size).copy()
    grown = 0
    for _ in range(BAKE_GROW):
        total = np.zeros_like(pixels[..., :3])
        count = np.zeros((size, size))
        for axis, step in ((0, 1), (0, -1), (1, 1), (1, -1)):
            shifted = np.roll(pixels[..., :3] * done[..., None], step, axis=axis)
            near = np.roll(done, step, axis=axis)
            # No wrapping round the image's edges
            edge = [slice(None), slice(None)]
            edge[axis] = 0 if step == 1 else -1
            shifted[tuple(edge)] = 0
            near[tuple(edge)] = False
            total += shifted
            count += near
        fill = ~done & (count > 0)
        if not fill.any():
            break
        pixels[fill, :3] = total[fill] / count[fill][:, None]
        done |= fill
        grown += int(fill.sum())
    image.pixels.foreach_set(pixels.ravel().astype(np.float32))
    covered[:] = done.ravel()
    return grown


def fill_unbaked(base, covered):
    """Give the texels a bake missed the covered ones' median color, so a skin shell reaching
    where the generated piece doesn't, like a spread finger, shows its color rather than the
    unbaked one."""
    if covered.all() or not covered.any():
        return
    pixels = np.array(base.pixels[:]).reshape(-1, 4)
    pixels[~covered, :3] = np.median(pixels[covered, :3], axis=0)
    base.pixels.foreach_set(pixels.ravel().astype(np.float32))
    covered[:] = True


def metal_roughness_image(high):
    """The generated texture's metal-roughness image, which reaches the BSDF through a color
    split rather than directly as the base color and normals do, or None."""
    for slot in high.material_slots:
        tree = slot.material.node_tree if slot.material else None
        if tree is None:
            continue
        for node in tree.nodes:
            if node.type != "TEX_IMAGE" or node.image is None:
                continue
            linked = {link.to_node.type for link in tree.links if link.from_node == node}
            if linked and not linked & {"BSDF_PRINCIPLED", "NORMAL_MAP"}:
                return node
    return None


def average_metal_roughness(high):
    """Metallic and roughness averaged over the generated texture's used texels."""
    node = metal_roughness_image(high)
    if node is not None:
        px = np.array(node.image.pixels[:]).reshape(-1, 4)
        used = px[px[:, :3].max(1) > 0]
        if len(used):
            return float(used[:, 2].mean()), float(used[:, 1].mean())
    for slot in high.material_slots:
        tree = slot.material.node_tree if slot.material else None
        bsdf = tree and next((n for n in tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
        if bsdf is not None:
            return bsdf.inputs["Metallic"].default_value, bsdf.inputs["Roughness"].default_value
    return 0.0, 0.6


def enable_gpu():
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    prefs = bpy.context.preferences.addons["cycles"].preferences
    for kind in ("OPTIX", "CUDA"):
        try:
            prefs.compute_device_type = kind
            prefs.get_devices()
            if any(d.type == kind for d in prefs.devices):
                for device in prefs.devices:
                    device.use = device.type == kind
                scene.cycles.device = "GPU"
                return
        except TypeError:
            continue
    scene.cycles.device = "CPU"


def emit(obj, source):
    """Show each material's surface as emission of one of its colors, so an emission bake copies
    that color as it is. A diffuse color bake leaves out the metallic share, which turns gold
    black. source(tree, bsdf) gives the socket to show, or None to show the base color."""
    for slot in obj.material_slots:
        if slot.material is None or slot.material.node_tree is None:
            continue
        tree = slot.material.node_tree
        bsdf = next((n for n in tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
        output = next((n for n in tree.nodes if n.type == "OUTPUT_MATERIAL"), None)
        if bsdf is None or output is None:
            continue
        emission = tree.nodes.new("ShaderNodeEmission")
        socket = source(tree, bsdf)
        base = bsdf.inputs["Base Color"]
        if socket is not None:
            tree.links.new(socket, emission.inputs["Color"])
        elif base.links:
            tree.links.new(base.links[0].from_socket, emission.inputs["Color"])
        else:
            emission.inputs["Color"].default_value = base.default_value
        tree.links.new(emission.outputs["Emission"], output.inputs["Surface"])


def bake_into(kind, high, low, tree, image, cage=None):
    """Bake one kind of map from the generated mesh into an image of the low-poly copy, its rays
    cast in from cage meters out from it, by default a share of its size (see BAKE_REACH)."""
    node = tree.nodes.new("ShaderNodeTexImage")
    node.image = image
    tree.nodes.active = node
    select_only(low)
    high.select_set(True)
    bpy.context.view_layer.objects.active = low
    cage = max(low.dimensions) * BAKE_REACH * 0.5 if cage is None else cage
    bpy.ops.object.bake(type=kind, use_selected_to_active=True, cage_extrusion=cage,
                        max_ray_distance=cage * 2, margin=8)
    return node


def new_image(name, size, color, fill=(0, 0, 0, 1)):
    image = bpy.data.images.new(name, size, size, alpha=False)
    image.colorspace_settings.name = color
    image.generated_color = fill
    return image


def occlusion_output(tree):
    """The node the glTF exporter takes occlusion from: a group named glTF Material Output."""
    group = bpy.data.node_groups.get(GLTF_OUTPUT)
    if group is None:
        group = bpy.data.node_groups.new(GLTF_OUTPUT, "ShaderNodeTree")
        group.interface.new_socket("Occlusion", in_out="INPUT", socket_type="NodeSocketFloat")
    node = tree.nodes.new("ShaderNodeGroup")
    node.node_tree = group
    return node


def bake(high, low, name, size, hidden, cage=None):
    """Bake base color, normals and an ORM map (occlusion, roughness, metalness) from the
    generated mesh onto the low-poly copy, its rays cast in from cage meters out (see
    bake_into). The hidden objects, like the mannequin, are left out of the occlusion. Gives the
    base color image and which of its texels the bake covered."""
    enable_gpu()
    scene = bpy.context.scene
    scene.cycles.samples = 8
    material = bpy.data.materials.new(name)  # with a node tree, from Blender 5
    low.data.materials.append(material)
    tree = material.node_tree
    bsdf = tree.nodes["Principled BSDF"]
    for obj in hidden:
        obj.hide_render = True
    # Cycles clears each image to black before baking unless told not to, which would leave
    # every texel looking baked, and the ones the bake misses black: no occlusion, no roughness
    scene.render.bake.use_clear = False

    # Texels the bake doesn't reach keep this color, so they can be told apart
    base_image = new_image("%s_emit" % name, size, "sRGB", UNBAKED)
    emit(high, lambda tree, bsdf: None)
    base = bake_into("EMIT", high, low, tree, base_image, cage)
    covered = np.abs(np.array(base_image.pixels[:]).reshape(-1, 4)[:, :3]
                     - np.array(UNBAKED[:3])).max(1) > 1e-3
    # A texel only some of whose rays met the mesh is the unbaked color blended in, and still
    # counts as baked above: baked again over another color, it comes out different. Left in,
    # those edge texels were numerous enough to make a tint group of their own, hot pink
    again = new_image("%s_emit_again" % name, size, "sRGB", UNBAKED_AGAIN)
    tree.nodes.remove(bake_into("EMIT", high, low, tree, again, cage))
    covered &= np.abs(np.array(again.pixels[:]).reshape(-1, 4)[:, :3]
                      - np.array(base_image.pixels[:]).reshape(-1, 4)[:, :3]).max(1) < 1e-3
    bpy.data.images.remove(again)
    partly = int((~covered).sum())
    grown = grow_baked(base_image, covered, size)
    print("[fit] %d texels not or only partly baked, %d of them given their neighbors' colors"
          % (partly, grown))
    normals = bake_into("NORMAL", high, low, tree,
                        new_image("%s_normal" % name, size, "Non-Color", FLAT_NORMAL), cage)

    metallic, roughness = average_metal_roughness(high)
    orm = np.empty((size * size, 4))
    orm[:] = (1, roughness, metallic, 1)
    source = metal_roughness_image(high)
    if source is not None:
        emit(high, lambda tree, bsdf: next(
            (n for n in tree.nodes if n.type == "TEX_IMAGE" and n.image == source.image),
            source).outputs["Color"])
        values = new_image("%s_mr" % name, size, "Non-Color")
        bake_into("EMIT", high, low, tree, values, cage)
        orm[covered, 1:3] = np.array(values.pixels[:]).reshape(-1, 4)[covered, 1:3]
    scene.world = scene.world or bpy.data.worlds.new("bake")
    scene.world.light_settings.distance = max(low.dimensions) * OCCLUSION_REACH
    occlusion = new_image("%s_ao" % name, size, "Non-Color")
    bake_into("AO", high, low, tree, occlusion, cage)
    orm[covered, 0] = np.array(occlusion.pixels[:]).reshape(-1, 4)[covered, 0]
    orm_image = new_image("%s_orm" % name, size, "Non-Color")
    orm_image.pixels.foreach_set(orm.ravel().astype(np.float32))
    orm_image.pack()
    normals.image.pack()
    for obj in hidden:
        obj.hide_render = False

    tree.links.new(base.outputs["Color"], bsdf.inputs["Base Color"])
    normal_map = tree.nodes.new("ShaderNodeNormalMap")
    tree.links.new(normals.outputs["Color"], normal_map.inputs["Color"])
    tree.links.new(normal_map.outputs["Normal"], bsdf.inputs["Normal"])
    orm_node = tree.nodes.new("ShaderNodeTexImage")
    orm_node.image = orm_image
    split = tree.nodes.new("ShaderNodeSeparateColor")
    tree.links.new(orm_node.outputs["Color"], split.inputs["Color"])
    tree.links.new(split.outputs["Green"], bsdf.inputs["Roughness"])
    tree.links.new(split.outputs["Blue"], bsdf.inputs["Metallic"])
    tree.links.new(split.outputs["Red"], occlusion_output(tree).inputs["Occlusion"])
    tree.nodes.active = base  # previews show the active image
    print("[fit] baked %d px, metallic %.2f, roughness %.2f on average, occlusion %.2f" % (
        size, metallic, roughness, float(orm[covered, 0].mean()) if covered.any() else 1))
    return base_image, covered


def srgb_to_linear(c):
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def linear_to_srgb(c):
    c = np.clip(c, 0, 1)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * c ** (1 / 2.4) - 0.055)


def lab(linear):
    """CIE Lab of linear sRGB colors, D65, as an n by 3 array."""
    xyz = linear @ np.array([[0.4124, 0.3576, 0.1805],
                             [0.2126, 0.7152, 0.0722],
                             [0.0193, 0.1192, 0.9505]]).T
    xyz /= np.array([0.9505, 1.0, 1.089])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[:, 1] - 16, 500 * (f[:, 0] - f[:, 1]),
                     200 * (f[:, 1] - f[:, 2])], axis=1)


def kmeans(points, k, rounds=30):
    """Plain k-means with farthest-point starts, so it gives the same groups every run."""
    centers = [points[np.argmin(np.linalg.norm(points - np.median(points, 0), axis=1))]]
    for _ in range(1, k):
        distance = np.min([np.linalg.norm(points - c, axis=1) for c in centers], axis=0)
        centers.append(points[np.argmax(distance)])
    centers = np.array(centers)
    for _ in range(rounds):
        labels = np.argmin(((points[:, None, :] - centers[None]) ** 2).sum(2), axis=1)
        moved = np.array([points[labels == c].mean(0) if (labels == c).any() else centers[c]
                          for c in range(k)])
        if np.allclose(moved, centers, atol=1e-3):
            break
        centers = moved
    return labels, centers


def color_groups(linear):
    """Split texel colors into up to four groups a tint can recolor alike. Lightness counts
    less than hue, so the shading across one cloth stays in one group; the fewest groups that
    keep each one tight are used, and groups too small or too alike are merged."""
    features = lab(linear) * np.array([TINT_LIGHTNESS, 1, 1])
    sample = features[:: max(1, len(features) // TINT_SAMPLE)]
    for k in range(1, len(MASK_CHANNELS) + 1):
        labels, centers = kmeans(sample, k)
        spread = np.linalg.norm(sample - centers[labels], axis=1).mean()
        if spread < TINT_SPREAD:
            break
    labels = np.argmin(((features[:, None, :] - centers[None]) ** 2).sum(2), axis=1)
    while len(centers) > 1:
        counts = np.bincount(labels, minlength=len(centers))
        gaps = np.linalg.norm(centers[:, None] - centers[None], axis=2)
        np.fill_diagonal(gaps, np.inf)
        small = np.argmin(counts)
        if counts[small] < TINT_SMALLEST * len(labels):
            gone = small
        elif gaps.min() < TINT_ALIKE:
            a, b = np.unravel_index(np.argmin(gaps), gaps.shape)
            gone = a if counts[a] < counts[b] else b
        else:
            break
        gaps[gone, gone] = np.inf
        into = np.argmin(gaps[gone])
        labels[labels == gone] = into
        keep = [c for c in range(len(centers)) if c != gone]
        centers = centers[keep]
        labels = np.searchsorted(keep, labels)
        centers = np.array([features[labels == c].mean(0) for c in range(len(centers))])
    # The biggest group takes the first mask channel
    order = np.argsort(-np.bincount(labels, minlength=len(centers)))
    return np.argsort(order)[labels], len(centers)


def tint_masks(image, covered, folder):
    """Make the baked base color recolorable: split it into color groups, turn each group's
    base color white (or gray with some of its lightness, see TINT_DETAIL), and write a mask
    whose channels pick the groups. A group's typical color is its default tint. Gives
    the default colors, by mask channel, and the original pixels."""
    size = image.size[0]
    pixels = np.array(image.pixels[:]).reshape(-1, 4)
    original = pixels.copy()
    linear = srgb_to_linear(pixels[:, :3])
    labels = np.zeros(len(linear), dtype=np.int64)
    found, count = color_groups(linear[covered])
    labels[covered] = found
    if (~covered).any():
        # Texels nothing was baked into follow the nearest covered one in the same row
        index = np.where(covered, np.arange(len(covered)), -1)
        index = np.maximum.accumulate(index)
        index[index < 0] = np.argmax(covered)
        labels[~covered] = labels[index[~covered]]
        linear[~covered] = linear[index[~covered]]
    mask = np.zeros((len(linear), 4))
    defaults = {}
    for group in range(count):
        members = labels == group
        # The group's color, and what detail it keeps as gray: lightness against the color's
        # own. Dividing each channel instead leaves a saturated red with nothing in green or
        # blue, which a green tint then multiplies into black
        lightness = linear @ LUMINANCE
        low, high = np.percentile(lightness[members & covered],
                                  [TINT_REFERENCE - TINT_BAND, TINT_REFERENCE + TINT_BAND])
        near = (lightness[members & covered] >= low) & (lightness[members & covered] <= high)
        reference = linear[members & covered][near].mean(0)
        level = max(float(reference @ LUMINANCE), 1e-4)
        linear[members] = (np.clip(lightness[members] / level, 0, 1) ** TINT_DETAIL)[:, None]
        mask[members, group] = 1
        rgb = np.round(linear_to_srgb(reference) * 255).astype(int)
        defaults[MASK_CHANNELS[group]] = "#%02x%02x%02x" % tuple(rgb)
    pixels[:, :3] = linear_to_srgb(linear)
    image.pixels.foreach_set(pixels.ravel().astype(np.float32))

    folder.mkdir(parents=True, exist_ok=True)
    out = bpy.data.images.new(image.name + "_mask", size, size, alpha=True)
    out.colorspace_settings.name = "Non-Color"
    out.pixels.foreach_set(mask.ravel().astype(np.float32))
    out.filepath_raw = str(folder / (image.name + ".png"))
    out.file_format = "PNG"
    out.save()
    print("[fit] %d tint groups: %s" % (count, defaults))
    return defaults, original


def rig(low, armature, bone):
    """Weight every vertex to the socket's bone and put the mesh on the armature."""
    if bone not in armature.data.bones:
        raise SystemExit("The mannequin has no bone " + bone)
    group = low.vertex_groups.new(name=bone)
    group.add(range(len(low.data.vertices)), 1.0, "REPLACE")
    modifier = low.modifiers.new("Armature", "ARMATURE")
    modifier.object = armature
    world = low.matrix_world.copy()
    low.parent = armature
    low.matrix_parent_inverse = armature.matrix_world.inverted()
    low.matrix_world = world


def export(path, low, armature):
    select_only(low, armature)
    options = {
        "filepath": path,
        "export_format": "GLB",
        "use_selection": True,
        "export_yup": True,
        "export_skins": True,
        "export_def_bones": False,
        "export_animations": False,
        "export_colors": False,
        "export_extras": False,
        "export_tangents": True,  # normal maps follow them
        # Morph targets (a layer's form targets) whole: the character code reads no sparse ones
        "export_try_sparse_sk": False,
        "export_morph_normal": False,
        "export_image_format": "AUTO",
    }
    known = {p.identifier for p in bpy.ops.export_scene.gltf.get_rna_type().properties}
    bpy.ops.export_scene.gltf(**{key: value for key, value in options.items() if key in known})
    print("[fit] wrote", path)


def preview(folder, low, mannequin):
    """Front and side renders around the part, on the mannequin."""
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.light = "STUDIO"
    scene.display.shading.color_type = "TEXTURE"
    scene.render.resolution_x = scene.render.resolution_y = 512
    scene.world = bpy.data.worlds.new("preview")
    scene.world.color = (0.8, 0.8, 0.8)
    for obj in bpy.data.objects:
        if obj.type == "MESH":
            obj.hide_render = obj not in mannequin and obj != low
    points = vertices(low)
    center = Vector(((points.min(0) + points.max(0)) / 2).tolist())
    size = float((points.max(0) - points.min(0)).max())
    camera = bpy.data.objects.new("camera", bpy.data.cameras.new("camera"))
    camera.data.type = "ORTHO"
    camera.data.ortho_scale = size * 2.2
    scene.collection.objects.link(camera)
    scene.camera = camera
    for i, angle in enumerate((0, 60, 180)):
        a = math.radians(angle)
        camera.location = center + Vector((math.sin(a), -math.cos(a), 0.25)) * 4
        camera.rotation_euler = (center - camera.location).to_track_quat("-Z", "Y").to_euler()
        scene.render.filepath = "%s/preview_%d.png" % (folder, i)
        bpy.ops.render.render(write_still=True)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description="Fit a generated mesh as a socket accessory.")
    parser.add_argument("--mesh", required=True)
    parser.add_argument("--mannequin", required=True)
    parser.add_argument("--bone", required=True, help="The socket's bone.")
    parser.add_argument("--rule", required=True, help="The socket type's rule, as JSON.")
    parser.add_argument("--name", required=True, help="The part's mesh and material name.")
    parser.add_argument("--out", required=True)
    parser.add_argument("--preview", help="A folder for preview renders.")
    parser.add_argument("--tints", help="Where to write the default tint colors, as JSON.")
    parser.add_argument("--image", help="The picture the mesh was made from, to turn it to face "
                        "as pictured.")
    parser.add_argument("--mirror", action="store_true",
                        help="Mirror the mesh across the body's middle first, for the other "
                             "side's copy of a sided accessory.")
    parser.add_argument("--slots", help="Armor: the body slots it covers, comma-separated; its "
                                        "low-poly copy is pushed out of the skin to the rule's "
                                        "gap, and a limb piece is weighted from their skin.")
    parser.add_argument("--both-sides", action="store_true",
                        help="Armor: add the piece's mirror as one mesh with it.")
    args = parser.parse_args(argv)
    slots = [s for s in (args.slots or "").split(",") if s]
    rule = json.loads(args.rule)

    bpy.ops.wm.read_factory_settings(use_empty=True)
    added = import_glb(args.mannequin)
    armature = next(obj for obj in added if obj.type == "ARMATURE")
    mannequin = [obj for obj in added if obj.type == "MESH"]

    high = generated_mesh(args.mesh)
    weld(high)
    if not rule.get("keepThin", False):
        drop_needles(high)
    high = drop_slivers(high)
    if args.mirror and rule["fit"] != "surface":
        # The other side's copy: mirrored across the body's middle before placing
        mirror_x(high)
    if rule["fit"] == "limb":
        # Lined up on the slots already, by fit_body.py --armor
        pass
    elif rule["fit"] == "enclose":
        if args.image:
            face_as_pictured(high, args.image)
        place_enclose(high, mannequin, armature, rule)
    elif rule["fit"] == "crown":
        place_bottom_crown(high, mannequin, armature, rule)
    elif rule["fit"] == "surface":
        if args.image:
            face_as_pictured(high, args.image)
        if args.mirror:
            mirror_x(high)
        place_against_body(high, mannequin, armature, args.bone, rule)
    else:
        raise SystemExit("No fitting called %s yet" % rule["fit"])
    if rule.get("shell"):
        low = skin_low(high, mannequin, slots, rule, args.name)
        # Weighted from all the skin it was made of
        slots = shell_slots(slots, rule["shell"], mannequin)
    else:
        low = low_poly(high, args.name, rule["triangles"])
        if slots:
            push_out_of_skin(low, mannequin, rule.get("gap", 0.0))
    # A skin shell lies under the piece, which stands off it by up to its reach: its rays start
    # outside all of it, so they meet its outside rather than its inside
    cage = rule["shell"].get("reach", 0.0) + SHELL_BAKE_MARGIN if rule.get("shell") else None
    base, covered = bake(high, low, args.name, rule["texture"], mannequin, cage)
    if rule.get("shell"):
        fill_unbaked(base, covered)
    out = pathlib.Path(args.out)
    defaults, original = tint_masks(base, covered, out.with_name(out.stem + MASK_FOLDER_SUFFIX))
    base.pack()
    if args.both_sides:
        add_mirror(low)
        mirrored = [s[:-2] + (".R" if s.endswith(".L") else ".L") for s in slots
                    if s.endswith((".L", ".R"))]
        slots = slots + [s for s in mirrored if s not in slots]
    if rule["fit"] == "limb":
        rig_skin(low, armature, mannequin, slots)
    else:
        rig(low, armature, args.bone)
    export(args.out, low, armature)
    if args.tints:
        with open(args.tints, "w", encoding="utf8") as stream:
            json.dump(defaults, stream, indent=2)
    if args.preview:
        # Previews show the part in its own colors, as it is shown untinted
        base.pixels.foreach_set(original.ravel().astype(np.float32))
        high.hide_render = True
        preview(args.preview, low, mannequin)


if __name__ == "__main__":
    main()
