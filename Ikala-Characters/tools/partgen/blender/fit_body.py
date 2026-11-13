"""Line a generated body part's figure up on the mannequin it was painted on.

A body part, like a mechanical forearm, is painted over a render of the mannequin and made into a
mesh of the whole figure by TRELLIS.2, as clothing is (see fit_layer.py). The figure is lined up
the same way, by trimmed ICP onto the body, which the rest of the figure, the bare mannequin,
holds in place. Its limbs are then turned onto the mannequin's (shape_layer.py's match_limbs), as
TRELLIS bends the figure's arms its own way, each turn kept only where it brings the limb nearer
the mannequin's skin.

How closely each of the part's slots lies on the mannequin's skin is reported, before and after
turning the limbs: a part much bulkier than the limb is fine, but one off to the side of it means
the line-up failed.

The part is then cut out of the figure in 3D, at its outer seams (those of its slots that no
other slot it fills shares): each seam's ring, written by the mannequin build beside the body as
<variant>_seams.json, gives a plane, and the part is what lies on its side of every such plane and
nearest its slots' bones, or nearest a neighbor's bones but close to the ring, like the top of a
shoulder above a whole arm. A cut from the image, as clothing's is, would lose a limb's sides,
seen edge on, and bridge the gap between the legs.

    blender -b --python fit_body.py -- --mesh <figure.glb> --body <male.blend> --out <folder>
        --slots upperArm.L,lowerArm.L,hand.L

With --armor the mesh is a piece of armor worn over the slots, like a gauntlet: it is turned
along them as an isolated part is, but sized and placed by its picture rather than fitted to
their skin (see solve_armor), not slid to the seams, and nothing is cut off, so part.glb is the
whole piece (see partgen.py's fit_armor).

writes aligned.glb (the figure on the body, its limbs turned onto the mannequin's), part.glb (the
part cut out of it), fit.json and renders preview_0.png and preview_1.png: the figure on the
mannequin from the front and side, and preview_2.png and preview_3.png: the part alone.
"""

import argparse
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bodies import load_body, nearest, tree, world_vertices  # noqa: E402
from fit_layer import (MAX_TURN, apply, cut_garment, first_guess, guide_pixels, icp,  # noqa: E402
                       import_figure, render, turn_degrees)
from shape_layer import (bone_segments, match_limbs, owners, points_of, set_points,  # noqa: E402
                         surface_samples)

# The body plan, for each slot's bones
PLAN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "src", "main",
                    "resources", "characters", "ikala", "bodyPlan", "biped.json")
# A guide view narrower than this, in meters, is framed on part of the body, not the whole of it
WHOLE_VIEW = 2.0
# Pixels lighter than this, out of 255, are a framed image's white background
BACKGROUND = 245
# The share, in percent, of a framed image's figure pixels and a mesh's points left out at each
# end when measuring how far they reach
OUTLIER = 0.5
# An isolated part's mesh is tried turned this many ways about its long axis, each way round
# end to end, with this many of its points, before ICP chooses
ROLLS = 12
TURN_SAMPLES = 2000
# Candidate turns fitting the slots' skin up to this many times worse than the best still count
# when the image's outline chooses among them
TURN_SLACK = 1.5
# How far toward the image's up, as the cosine of the angle, a candidate turn must keep the
# mesh's own up to count, if any do
MIN_UPRIGHT = 0.3
# Candidates whose outline overlaps the image's within this much of the best count as as good
OVERLAP_SLACK = 0.03
# The grid, in cells per side, a mesh's front outline is compared with its image's on
OUTLINE_GRID = 128
# How much an isolated part's front outline must overlap its image's, as a share of both
# together, to count as placed right
ISOLATED_OVERLAP = 0.5
# Armor's open end is found with this many rays cast in along it from each end, every other one
# this far off its axis as a share of its median radius
OPEN_RAYS = 8
OPEN_RADIUS = 0.4
# Ends whose rays got within this share of the length as far in as each other are both open
OPEN_MARGIN = 0.2
# Armor's shading is compared with its picture's over this many of its vertices
SHADE_SAMPLES = 20000
# Armor turns overlapping their picture within this of the best are told apart by which way they
# stick out
SIDED_SLACK = 0.1
# How far past each outer seam's plane an isolated part must reach, in meters, so the part has
# its whole edge there to join the ring by
SEAM_REACH = 0.005
SEAM_SECTORS = 12
SEAM_WIDE = 2.0
SEAM_DEPTH = 0.03
# The least share of a slide along the slots' axis that crosses a seam's plane
SLIDE_SLANT = 0.3
# How far a figure's point may be from a slot's bones, in meters, to count as the slot's: the
# slot's own limb, not what hangs near it
SLOT_REACH = 0.15
# How far from a seam ring's middle, as a share of its radius, a figure's point nearest a
# neighbor's bones may be and still be the part's, on the part's side of the seam
RING_REACH = 1.5


def read_plan():
    """The body plan."""
    with open(PLAN, encoding="utf-8") as file:
        # Definitions may start with // comment lines
        return json.loads("".join(line for line in file if not line.lstrip().startswith("//")))


def plan_slots():
    """The body plan's slots by name."""
    return {slot["id"]: slot for slot in read_plan()["slots"]}


def plan_forms():
    """The body plan's clothing forms, thinnest first, as (name, share): shells over the body
    that clothing is fitted to, each that share of every slot's radius out, the thickest also the
    most a body part on a clothed slot may stand out."""
    return [(form["id"], float(form["share"])) for form in read_plan().get("forms", [])]


def slot_radii(variant):
    """Each body slot's radius on a variant, in meters: its own if it gives one, else the
    reference body's."""
    plan = read_plan()
    own = next((v.get("radii", {}) for v in plan["variants"] if v["id"] == variant), {})
    return {slot["id"]: float(own.get(slot["id"], slot.get("radius", 0.0)))
            for slot in plan["slots"]}


def clothed_slots():
    """The body slots clothing goes over."""
    return {slot["id"] for slot in read_plan()["slots"] if slot.get("clothed")}


def form_for(shares, forms):
    """The thinnest form whose share is at least the largest of some slots' shares, the thickest
    if none is; None for no slots."""
    if not shares:
        return None
    most = max(shares.values())
    return next((name for name, share in forms if most <= share), forms[-1][0] if forms else None)


def slot_bones(names):
    """Each named slot's bones, from the body plan."""
    slots = plan_slots()
    unknown = [name for name in names if name not in slots]
    if unknown:
        raise SystemExit("The body plan has no slots %s" % ", ".join(unknown))
    return {name: slots[name]["bones"] for name in names}


def outer_seams(names):
    """The seams of some slots that no other of them shares, each with the slots across it."""
    slots = plan_slots()
    counts = {}
    for name in names:
        for seam in slots[name].get("seams", []):
            counts[seam] = counts.get(seam, 0) + 1
    return {seam: [s for s in slots if s not in names and seam in slots[s].get("seams", [])]
            for seam, count in counts.items() if count == 1}


def seam_plane(ring, inside):
    """A seam ring's plane: its middle, its normal pointing away from a point on the part's side,
    and its radius."""
    ring = np.asarray(ring)
    middle = ring.mean(0)
    _, _, axes = np.linalg.svd(ring - middle)
    normal = axes[2]
    if normal @ (inside - middle) > 0:
        normal = -normal
    return middle, normal, float(np.linalg.norm(ring - middle, axis=1).mean())


def cut_part(figure, names, seams, segments):
    """Cut a part filling some slots out of the lined-up figure at its outer seams. Gives the
    part, how many faces it has, and its seams' planes."""
    slots = plan_slots()
    own_bones = {b for name in names for b in slots[name]["bones"] if b in segments}
    points = points_of(figure)
    nearest_bone = owners(points, segments)
    # A point on the part's side: the middle of its own bones
    inside = np.mean([np.mean(segments[b], axis=0) for b in own_bones], axis=0)
    planes = {}
    on_side = np.ones(len(points), dtype=bool)
    near_ring = np.zeros(len(points), dtype=bool)
    for seam, across in outer_seams(names).items():
        if seam not in seams:
            raise SystemExit("The mannequin has no seam ring '%s'" % seam)
        middle, normal, radius = seam_plane(seams[seam], inside)
        planes[seam] = {"middle": middle.round(4).tolist(), "normal": normal.round(4).tolist(),
                        "radius": round(radius, 4)}
        on_side &= (points - middle) @ normal < 0
        neighbor_bones = {b for s in across for b in slots[s]["bones"] if b in segments}
        near_ring |= (np.isin(nearest_bone, list(neighbor_bones))
                      & (np.linalg.norm(points - middle, axis=1) < radius * RING_REACH))
    keep = on_side & (np.isin(nearest_bone, list(own_bones)) | near_ring)
    part, faces = cut_garment(figure, keep)
    part.name = "part"
    return part, faces, planes


def image_shown(image_path):
    """Which of an image's pixels show something, not its white background, top row first."""
    image = bpy.data.images.load(os.path.abspath(image_path))
    width, height = image.size
    pixels = np.empty(width * height * 4, dtype=np.float32)
    image.pixels.foreach_get(pixels)
    # Blender's images start at the bottom row
    rgb = np.flipud(pixels.reshape(height, width, 4)[..., :3])
    return rgb.mean(-1) * 255 < BACKGROUND


def framed_guess(points, guide, image_path, body):
    """A first guess at where a figure made from a framed image (one showing part of the body)
    goes: its front outline (x across, z up) scaled to the image's figure, by the mean of the two
    ways, and moved onto it, as deep as the body there. The whole-body guess stands the figure on
    its feet, which a framed one doesn't show."""
    shown = image_shown(image_path)
    # Extents by percentiles, so specks the image or the mesh has, away from the figure, don't
    # stretch them
    rows, columns = np.nonzero(shown)
    first_row, last_row = np.percentile(rows, [OUTLIER, 100 - OUTLIER])
    first_column, last_column = np.percentile(columns, [OUTLIER, 100 - OUTLIER])
    size, view = guide["size"], guide["view"]
    middle_x = guide.get("middleX", 0.0)
    left = middle_x + (first_column / size - 0.5) * view
    right = middle_x + ((last_column + 1) / size - 0.5) * view
    top = guide["middleHeight"] - (first_row / size - 0.5) * view
    bottom = guide["middleHeight"] - ((last_row + 1) / size - 0.5) * view
    low = np.percentile(points, OUTLIER, axis=0)
    high = np.percentile(points, 100 - OUTLIER, axis=0)
    span = high - low
    scale = ((right - left) / max(span[0], 1e-9) + (top - bottom) / max(span[2], 1e-9)) / 2
    inside = (body[:, 0] > left) & (body[:, 0] < right) & (body[:, 2] > bottom) \
        & (body[:, 2] < top)
    depth = float(np.median(body[inside, 1])) if inside.any() else 0.0
    target = np.array([(left + right) / 2, depth, (top + bottom) / 2])
    m = np.eye(4)
    m[:3, :3] *= scale
    m[:3, 3] = target - scale * (low + high) / 2
    return m


def principal(points):
    """Points' middle and the direction they spread most along."""
    middle = points.mean(0)
    return middle, np.linalg.svd(points - middle, full_matrices=False)[2][0]


def span(points, middle, axis):
    """How far points reach along an axis, leaving out OUTLIER percent at each end."""
    low, high = np.percentile((points - middle) @ axis, [OUTLIER, 100 - OUTLIER])
    return high - low


def front_outline(points, guide):
    """Which cells of an OUTLINE_GRID grid over the guide's view points fall in, seen from the
    front, grown a cell so a mesh's vertices cover its outline."""
    cells = np.zeros((OUTLINE_GRID, OUTLINE_GRID), dtype=bool)
    column, row = guide_pixels(points, guide, OUTLINE_GRID)
    inside = (column >= 0) & (column < OUTLINE_GRID) & (row >= 0) & (row < OUTLINE_GRID)
    cells[row[inside].astype(int), column[inside].astype(int)] = True
    grown = cells.copy()
    grown[1:] |= cells[:-1]
    grown[:-1] |= cells[1:]
    grown[:, 1:] |= cells[:, :-1]
    grown[:, :-1] |= cells[:, 1:]
    return grown


def image_outline(image_path):
    """An image's shown pixels on the OUTLINE_GRID grid: a cell shows if any of its pixels do."""
    shown = image_shown(image_path)
    step = shown.shape[0] // OUTLINE_GRID
    return shown[:step * OUTLINE_GRID, :step * OUTLINE_GRID] \
        .reshape(OUTLINE_GRID, step, OUTLINE_GRID, step).any((1, 3))


def solve_turn(points, slot_points, slot_surface, guide, image_path):
    """Where an isolated part's mesh goes: TRELLIS turns an object on its own as it pleases, so
    its long axis is laid along that of the mannequin's slots, scaled to their length, and turned
    ROLLS ways about it, each way round end to end. Each is refined by ICP onto the slots' skin.
    Of those fitting nearly as well as the best (within TURN_SLACK), those keeping the mesh's own
    up (+Z) along the image's count, if any do: TRELLIS keeps the image's up, though it turns
    the mesh about it as it pleases (an arm painted slanting across comes out slanting front to
    back). Of those, the ones whose front outline overlaps the image's (painted over the slots
    from the front) about as well as the best are as good, and the one keeping the mesh's up
    and front (glTF +Z, which TRELLIS often takes from the image's front) the most toward the
    image's wins: a tube like a hoofed leg has the same outline end to end, and a shin turned
    round.
    Gives the fit and each candidate's residual and overlap."""
    rng = np.random.default_rng(0)
    sample = points[rng.choice(len(points), min(TURN_SAMPLES, len(points)), replace=False)]
    mesh_middle, mesh_axis = principal(sample)
    slot_middle, slot_axis = principal(slot_points)
    scale = span(slot_points, slot_middle, slot_axis) \
        / max(span(sample, mesh_middle, mesh_axis), 1e-9)
    picture = image_outline(image_path)
    candidates = []
    for end in (1, -1):
        onto = np.array(Vector(mesh_axis * end).rotation_difference(Vector(slot_axis))
                        .to_matrix())
        for k in range(ROLLS):
            rotation = np.array(Matrix.Rotation(2 * math.pi * k / ROLLS, 3,
                                                Vector(slot_axis))) @ onto
            m = np.eye(4)
            m[:3, :3] = scale * rotation
            m[:3, 3] = slot_middle - scale * rotation @ mesh_middle
            m, _, residual = icp(sample, slot_surface, m, scaled=False)
            outline = front_outline(apply(m, sample), guide)
            overlap = (outline & picture).sum() / max((outline | picture).sum(), 1)
            # The mesh's front (glTF +Z, Blender -Y once imported) toward the camera, which looks
            # at the body's front along +Y
            facing = float(-(m[:3, :3] @ np.array([0.0, -1.0, 0.0]))[1] / scale)
            # Its up along the image's, turned by the guide's roll
            roll = math.radians(guide.get("roll", 0.0))
            upright = float((m[:3, :3] @ np.array([0.0, 0.0, 1.0]))
                            @ np.array([math.sin(roll), 0.0, math.cos(roll)]) / scale)
            candidates.append((m, residual, float(overlap), facing, upright))
    best = min(c[1] for c in candidates)
    fitting = [c for c in candidates if c[1] <= best * TURN_SLACK]
    fitting = [c for c in fitting if c[4] >= MIN_UPRIGHT] or fitting
    widest = max(c[2] for c in fitting)
    m = max((c for c in fitting if c[2] >= widest - OVERLAP_SLACK),
            key=lambda c: c[3] + c[4])[0]
    return m, [{"residual_mm": round(c[1] * 1000, 1), "overlap": round(c[2], 3),
                "facing": round(c[3], 2), "upright": round(c[4], 2)} for c in candidates]


def seam_reach(moved, plane):
    """How a part reaches toward a seam: each point's distance past its plane, its offset across
    the seam's axis, and which points lie within SEAM_WIDE of the ring's radius of the axis."""
    middle, normal, radius = plane
    along = (moved - middle) @ normal
    across = (moved - middle) - np.outer(along, normal)
    return along, across, np.linalg.norm(across, axis=1) < radius * SEAM_WIDE


def reach_seams(points, m, names, seams, segments, axis):
    """Move an isolated part's fit along its slots' long axis until it reaches SEAM_REACH past
    each outer seam's plane all the way round: its image was painted up to the seams' lines,
    but after being scaled to its slots' length it may stop short of one or end slanted, and
    shape_body.py needs its whole edge there to join the ring. ICP, too, slides a part along a
    limb about as freely as it likes.

    First the part's furthest point near each seam's axis is brought to its plane: a part with
    one outer seam slides there, back as well as forward; one with a seam at each end, like a
    shin between knee and ankle, stretches or shrinks along the axis to meet both. Then each of
    SEAM_SECTORS sectors round each axis must reach, sliding on if not, counting only points
    within SEAM_DEPTH of the plane: a sector the part's end leaves open, as between bones, has
    only points further along the limb. It moves along the slots' axis, not a seam's normal: a
    shoulder's ring faces the torso while the arm hangs down from it.

    Last, the part's end at each seam is centered across the ring's axis (the mean of the
    seams' offsets, for a part with more than one).

    Gives the fit, how far its end moved at each seam, in millimeters, back being negative,
    and how far it moved across to center it."""
    slots = plan_slots()
    own_bones = {b for name in names for b in slots[name]["bones"] if b in segments}
    inside = np.mean([np.mean(segments[b], axis=0) for b in own_bones], axis=0)
    planes = {seam: seam_plane(seams[seam], inside) for seam in outer_seams(names)}
    moved = apply(m, points)
    # Each seam's way along the axis, the part's end that way, and how far the end must move
    ends = {}
    for seam, plane in planes.items():
        along, _, wide = seam_reach(moved, plane)
        if not wide.any():
            continue
        slant = float(axis @ plane[1])
        way = 1.0 if slant > 0 else -1.0
        reach = moved[wide] @ axis
        need = (SEAM_REACH - float(along[wide].max())) / max(abs(slant), SLIDE_SLANT)
        ends[seam] = (way, float(reach.max() if way > 0 else reach.min()), need)
    ways = sorted(e[0] for e in ends.values())
    if len(ends) == 2 and ways == [-1.0, 1.0]:
        (_, top, rise), = [e for e in ends.values() if e[0] > 0]
        (_, bottom, drop), = [e for e in ends.values() if e[0] < 0]
        scale = (top + rise - bottom + drop) / max(top - bottom, 1e-6)
        shift = top + rise - scale * top
    else:
        scale = 1.0
        shift = (max((e[0] * e[2] for e in ends.values()), key=abs, default=0.0)
                 if len(ends) == 1 else
                 sum(e[0] * max(e[2], 0.0) for e in ends.values()))
    # Along the axis: u' = scale * u + shift
    step = np.eye(4)
    step[:3, :3] += (scale - 1) * np.outer(axis, axis)
    step[:3, 3] = axis * shift
    m = step @ m
    moved = apply(step, moved)
    slid = {seam: round(float((scale * end + shift - end) * way) * 1000, 1)
            for seam, (way, end, _) in ends.items()}
    for seam, plane in planes.items():
        if seam not in ends:
            continue
        middle, normal, _ = plane
        along, across, wide = seam_reach(moved, plane)
        near = wide & (along > -SEAM_DEPTH)
        if not near.any():
            continue
        first = np.cross(normal, [1.0, 0.0, 0.0] if abs(normal[0]) < 0.9 else [0.0, 1.0, 0.0])
        first /= np.linalg.norm(first)
        angle = np.arctan2(across[near] @ np.cross(normal, first), across[near] @ first)
        sector = ((angle + np.pi) / (2 * np.pi) * SEAM_SECTORS).astype(int) % SEAM_SECTORS
        reach = np.full(SEAM_SECTORS, -np.inf)
        np.maximum.at(reach, sector, along[near])
        # Sectors the part leaves empty there, like between bones, have nothing to reach with
        short = SEAM_REACH - float(reach[np.isfinite(reach)].min())
        if short > 0:
            way = axis if axis @ normal > 0 else -axis
            nudge = way * short / max(float(way @ normal), SLIDE_SLANT)
            moved = moved + nudge
            m = m.copy()
            m[:3, 3] += nudge
            slid[seam] = round(slid[seam] + float(np.linalg.norm(nudge)) * 1000, 1)
    # Its end at each seam, centered on the ring's axis: ICP lines a big hand up with the
    # mannequin's hand and may leave its wrist off to one side of the wrist's ring
    offsets = []
    for seam, plane in planes.items():
        along, across, wide = seam_reach(moved, plane)
        near = wide & (along > -SEAM_DEPTH)
        if seam in ends and near.any():
            offsets.append(across[near].mean(0))
    centered = 0.0
    if offsets:
        offset = np.mean(offsets, axis=0)
        m = m.copy()
        m[:3, 3] -= offset
        centered = round(float(np.linalg.norm(offset)) * 1000, 1)
    return m, slid, centered


def slot_fit(points, surface, segments, bones):
    """How far the figure's points nearest a slot's bones lie from the skin: the median and the
    90th percentile, in millimeters, and how many there are."""
    nearest_bone = owners(points, segments)
    own = np.isin(nearest_bone, [b for b in bones if b in segments])
    if not own.any():
        return {"points": 0}
    _, signed = nearest(points[own], surface)
    signed = signed[np.abs(signed) < SLOT_REACH]
    if not len(signed):
        return {"points": 0}
    return {"points": int(len(signed)),
            "median_mm": round(float(np.median(np.abs(signed))) * 1000, 1),
            "p90_mm": round(float(np.percentile(np.abs(signed), 90)) * 1000, 1)}


def picture_points(image_path, guide):
    """Where an image's shown pixels fall on the body, seen from the guide's view: across and up
    in meters, in the guide's own (rolled) frame, like guide_pixels' columns and rows."""
    shown = image_shown(image_path)
    rows, columns = np.nonzero(shown)
    size, view = guide["size"], guide["view"]
    return np.stack([((columns + 0.5) / size - 0.5) * view,
                     (0.5 - (rows + 0.5) / size) * view], axis=1)


def open_end(figure, middle, axis):
    """Which end of a piece of armor along its long axis is open, where the limb goes in: rays
    started a little past each end, OPEN_RAYS of them over a disk round the axis, are cast in
    along it, and the end they get furthest into before meeting the mesh, by their median, is the
    open one (a gauntlet's cuff, not its fingertips; a boot's top, not its toe). Gives +1 or -1
    along the axis and how far in each end's rays got, as fractions of the length."""
    from mathutils.bvhtree import BVHTree
    world = np.array(figure.matrix_world)
    co = np.empty(len(figure.data.vertices) * 3)
    figure.data.vertices.foreach_get("co", co)
    co = co.reshape(-1, 3) @ world[:3, :3].T + world[:3, 3]
    tree = BVHTree.FromPolygons(co.tolist(), [tuple(p.vertices) for p in figure.data.polygons])
    along = (co - middle) @ axis
    low, high = np.percentile(along, [OUTLIER, 100 - OUTLIER])
    length = high - low
    across = (co - middle) - np.outer(along, axis)
    radius = float(np.percentile(np.linalg.norm(across, axis=1), 50)) * OPEN_RADIUS
    side = np.cross(axis, [0.0, 0.0, 1.0] if abs(axis[2]) < 0.9 else [1.0, 0.0, 0.0])
    side /= np.linalg.norm(side)
    other = np.cross(axis, side)
    depths = {}
    for end, start in ((1, high), (-1, low)):
        reach = []
        for k in range(OPEN_RAYS):
            angle = 2 * math.pi * k / OPEN_RAYS
            offset = (side * math.cos(angle) + other * math.sin(angle)) * radius * (k % 2)
            origin = middle + axis * (start + end * 0.05 * length) + offset
            hit = tree.ray_cast(Vector(origin.tolist()), Vector((-end * axis).tolist()),
                                2 * length)
            reach.append((hit[3] if hit[0] is not None else 2 * length) - 0.05 * length)
        depths[end] = float(np.median(reach) / max(length, 1e-9))
    return (1 if depths[1] >= depths[-1] else -1), depths


def slot_frame(slot_points):
    """Slots' middle, long axis (pointing away from the body's middle, the origin side being
    arbitrary otherwise) and length along it, to carry a fit from one body to another."""
    middle, axis = principal(slot_points)
    if axis @ middle < 0:
        axis = -axis
    return {"middle": middle.tolist(), "axis": axis.tolist(),
            "span": float(span(slot_points, middle, axis))}


def carried(m, before, after):
    """A fit on one body's slots carried onto another's: turned from the first's long axis onto
    the second's, scaled by their lengths and moved from the one's middle to the other's."""
    turn = np.array(Vector(before["axis"]).rotation_difference(Vector(after["axis"]))
                    .to_matrix())
    move = np.eye(4)
    move[:3, :3] = turn * after["span"] / before["span"]
    move[:3, 3] = np.array(after["middle"]) - move[:3, :3] @ np.array(before["middle"])
    return move @ m


def vertex_shades(figure):
    """How light each of a figure's vertices is in its own texture (the base color image wired
    into its material's BSDF), from 0 to 1, or None if it has none."""
    image = None
    for slot in figure.material_slots:
        tree = slot.material.node_tree if slot.material else None
        for link in tree.links if tree else []:
            if link.from_node.type == "TEX_IMAGE" and link.to_socket.name == "Base Color" \
                and link.from_node.image is not None:
                image = link.from_node.image
    uv_layer = figure.data.uv_layers.active
    if image is None or uv_layer is None:
        return None
    width, height = image.size
    pixels = np.empty(width * height * 4, dtype=np.float32)
    image.pixels.foreach_get(pixels)
    shade = pixels.reshape(height, width, 4)[..., :3].mean(-1)
    loops = len(figure.data.loops)
    owner = np.empty(loops, dtype=np.int64)
    figure.data.loops.foreach_get("vertex_index", owner)
    uv = np.empty(loops * 2)
    uv_layer.data.foreach_get("uv", uv)
    uv = uv.reshape(-1, 2)
    column = np.clip((uv[:, 0] % 1.0) * width, 0, width - 1).astype(int)
    row = np.clip((uv[:, 1] % 1.0) * height, 0, height - 1).astype(int)
    shades = np.zeros(len(figure.data.vertices))
    shades[owner] = shade[row, column]
    return shades


def shade_match(points, shades, guide, image_path):
    """How well a placed figure's own shading, seen from the guide's view (the nearest point in
    each cell of an OUTLINE_GRID grid), follows its picture's there: their correlation over the
    cells both show, from -1 to 1. Tells a piece's front from its back, which outlines can't."""
    column, row = guide_pixels(points, guide, OUTLINE_GRID)
    inside = (column >= 0) & (column < OUTLINE_GRID) & (row >= 0) & (row < OUTLINE_GRID)
    cell = row[inside].astype(int) * OUTLINE_GRID + column[inside].astype(int)
    depth = points[inside, 1]
    # Nearest the camera, which looks along +Y: the smallest y in each cell
    order = np.lexsort((depth, cell))
    first = np.ones(len(order), dtype=bool)
    first[1:] = cell[order][1:] != cell[order][:-1]
    seen = np.full(OUTLINE_GRID * OUTLINE_GRID, np.nan)
    seen[cell[order][first]] = shades[inside][order][first]
    image = bpy.data.images.load(os.path.abspath(image_path), check_existing=True)
    width, height = image.size
    pixels = np.empty(width * height * 4, dtype=np.float32)
    image.pixels.foreach_get(pixels)
    light = np.flipud(pixels.reshape(height, width, 4)[..., :3].mean(-1))
    shown = light * 255 < BACKGROUND
    step = height // OUTLINE_GRID
    cut = (slice(0, step * OUTLINE_GRID), slice(0, step * OUTLINE_GRID))
    total = np.where(shown, light, 0)[cut].reshape(OUTLINE_GRID, step, OUTLINE_GRID, step) \
        .sum((1, 3))
    count = shown[cut].reshape(OUTLINE_GRID, step, OUTLINE_GRID, step).sum((1, 3))
    picture = np.where(count > step * step / 2, total / np.maximum(count, 1), np.nan).ravel()
    both = ~np.isnan(seen) & ~np.isnan(picture)
    if both.sum() < 8 or seen[both].std() < 1e-6 or picture[both].std() < 1e-6:
        return 0.0
    return float(np.corrcoef(seen[both], picture[both])[0, 1])


def lopsided(points, middle, axis):
    """Which way points stick out from a long axis through their middle, and how far: the mean of
    their offsets across it, each weighted by its own length, so a foot off a shin or a thumb
    off a hand counts and a round tube cancels out."""
    across = (points - middle) - np.outer((points - middle) @ axis, axis)
    return (across * np.linalg.norm(across, axis=1, keepdims=True)).mean(0)


def solve_armor(points, slot_points, guide, image_path, figure, body, opens="toward",
                anchor=None):
    """Where a piece of armor's mesh goes, from the picture painted over its slots' guide: like
    an isolated body part's (see solve_turn), its long axis is laid along the slots' and turned
    ROLLS ways about it; but a shell over the skin can't be fitted to it, which fits as well
    either way round and shrinks it, and TRELLIS keeps neither the picture's up nor its front for
    a piece on its own. So its open end (see open_end) goes toward the body, or away from it if
    it opens "away" (a pauldron, capping the shoulder), either end if both are open, and each
    turn is scaled and moved, seen from the guide's view, to spread as far and sit
    where the picture's shown pixels do, as deep as the slots' middle. Of those whose outline
    overlaps the picture's about as well as the best, the one whose own texture is shaded most
    like the picture wins (see shade_match): an outline is the same from the back. With an
    anchor, the piece is then slid along the slots until its end toward the body is that many
    meters past theirs: a pauldron, painted over the whole upper arm, caps the shoulder. Gives
    the fit and each candidate's overlap and shading."""
    rng = np.random.default_rng(0)
    chosen = rng.choice(len(points), min(SHADE_SAMPLES, len(points)), replace=False)
    shades = vertex_shades(figure)
    sample = points[chosen[:TURN_SAMPLES]]
    mesh_middle, mesh_axis = principal(sample)
    slot_middle, slot_axis = principal(slot_points)
    mesh_side = lopsided(sample, mesh_middle, mesh_axis)
    slot_side = lopsided(slot_points, slot_middle, slot_axis)
    # The open end goes toward the body: the slots' end nearer its middle, as a wrist or a knee is
    opening, depths = open_end(figure, mesh_middle, mesh_axis)
    ends = (slot_points - slot_middle) @ slot_axis
    toward = 1 if np.linalg.norm(slot_middle + slot_axis * ends.max() - body.mean(0)) \
        < np.linalg.norm(slot_middle + slot_axis * ends.min() - body.mean(0)) else -1
    print("[fit_body] open end %+d (rays got %.2f / %.2f of the length in), toward the body %+d"
          % (opening, depths[1], depths[-1], toward))
    flat = picture_points(image_path, guide)
    flat_middle = flat.mean(0)
    flat_axes = np.linalg.svd(flat - flat_middle, full_matrices=False)[2]
    flat_span = [span(flat, flat_middle, axis) for axis in flat_axes]
    picture = image_outline(image_path)
    candidates = []
    # Both ends open about as far, as a tube's are, leave it to the picture
    wanted = opening * toward * (-1 if opens == "away" else 1)
    ends = [wanted] if abs(depths[1] - depths[-1]) >= OPEN_MARGIN else [1, -1]
    turns = [np.array(Matrix.Rotation(2 * math.pi * k / ROLLS, 3, Vector(slot_axis)))
             @ np.array(Vector(mesh_axis * end).rotation_difference(Vector(slot_axis))
                        .to_matrix())
             for end in ends for k in range(ROLLS)]
    for rotation in turns:
        turned = (sample - mesh_middle) @ rotation.T
        # Seen from the guide's view, in its frame, with no offset
        column, row = guide_pixels(turned + np.array([guide.get("middleX", 0.0), 0.0,
                                                      guide["middleHeight"]]), guide)
        seen = np.stack([(column / guide["size"] - 0.5) * guide["view"],
                         (0.5 - row / guide["size"]) * guide["view"]], axis=1)
        seen_middle = seen.mean(0)
        scale = float(np.mean([flat_span[i] / max(span(seen, seen_middle, axis), 1e-9)
                               for i, axis in enumerate(flat_axes)]))
        m = np.eye(4)
        m[:3, :3] = scale * rotation
        m[:3, 3] = -scale * rotation @ mesh_middle
        # Move it across and up so its middle, seen, is the picture's, as deep as the slots
        shift = flat_middle - scale * seen_middle
        roll = math.radians(guide.get("roll", 0.0))
        right = np.array(guide.get("right", (1.0, 0.0, 0.0)))
        x = shift[0] * math.cos(roll) + shift[1] * math.sin(roll)
        z = -shift[0] * math.sin(roll) + shift[1] * math.cos(roll)
        m[:3, 3] += right * (guide.get("middleX", 0.0) + x)
        m[2, 3] += guide["middleHeight"] + z
        depth = slot_middle[1] - float(np.median(apply(m, sample)[:, 1]))
        m[1, 3] += depth
        outline = front_outline(apply(m, sample), guide)
        overlap = (outline & picture).sum() / max((outline | picture).sum(), 1)
        shading = 0.0 if shades is None else \
            shade_match(apply(m, points[chosen]), shades[chosen], guide, image_path)
        turned_side = rotation @ mesh_side
        sided = float(turned_side @ slot_side / max(np.linalg.norm(turned_side)
                                                    * np.linalg.norm(slot_side), 1e-12))
        candidates.append((m, float(overlap), shading, sided))
    # Of the turns overlapping about as well as the best, the one sticking out the way the slots
    # do; the picture's shading only reported, for now
    widest = max(c[1] for c in candidates)
    m = max((c for c in candidates if c[1] >= widest - SIDED_SLACK), key=lambda c: c[3])[0]
    if anchor is not None:
        inward = slot_axis * toward
        reach = float(np.percentile(apply(m, sample) @ inward, 100 - OUTLIER))
        shift = float((slot_points @ inward).max()) + anchor - reach
        m[:3, 3] += inward * shift
        print("[fit_body] slid %.3f m toward the body, to %.3f m past the slots" % (shift, anchor))
    return m, [{"overlap": round(c[1], 3), "shading": round(c[2], 3), "sided": round(c[3], 3)}
               for c in candidates]


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--mesh", required=True)
    parser.add_argument("--body", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--slots", required=True, help="Comma-separated slots the part fills.")
    parser.add_argument("--guide", help="The guide the image was painted over, when framed on "
                                        "part of the body (see render_guides.py's --frame).")
    parser.add_argument("--image", help="The picked image, with --guide.")
    parser.add_argument("--isolated", action="store_true",
                        help="The mesh is the part on its own, from an image painted over its "
                             "slots alone (render_guides.py's --only), so its turn is unknown.")
    parser.add_argument("--armor", action="store_true",
                        help="The mesh is armor worn over the slots: lined up, not slid to the "
                             "seams or cut.")
    parser.add_argument("--open-end", default="toward", choices=["toward", "away"],
                        help="Armor: whether its open end goes toward the body (a glove's cuff) "
                             "or away (a pauldron's).")
    parser.add_argument("--anchor", type=float,
                        help="Armor: slide it along the slots until its end toward the body is "
                             "this many meters past theirs.")
    parser.add_argument("--like", help="Armor: the fit.json of the body its picture was painted "
                                       "over; the piece goes where it went there, carried from "
                                       "those slots onto these.")
    args = parser.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)
    slots = slot_bones([s for s in args.slots.split(",") if s])

    (body, body_faces), chosen = load_body(args.body, list(slots))
    surface = tree(body, body_faces)
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    segments = bone_segments(armature)
    figure = import_figure(args.mesh)
    points, _ = world_vertices(figure)

    guide = None
    if args.guide:
        with open(args.guide, encoding="utf-8") as file:
            guide = json.load(file)
    framed = guide is not None and guide["view"] < WHOLE_VIEW
    variant = os.path.splitext(os.path.basename(args.body))[0]
    with open(os.path.join(os.path.dirname(os.path.abspath(args.body)),
                           variant + "_seams.json"), encoding="utf-8") as file:
        seams = json.load(file)["seams"]
    candidates, slid, centered = None, None, None
    if args.armor and args.like:
        with open(args.like, encoding="utf-8") as file:
            like = json.load(file)
        m = carried(np.array(like["matrix"]), like["slotFrame"], slot_frame(chosen[0]))
        rounds, residual = 0, 0.0
    elif args.armor:
        m, candidates = solve_armor(points, chosen[0], guide, args.image, figure, body,
                                    args.open_end, args.anchor)
        rounds, residual = 0, 0.0
    elif args.isolated:
        m, candidates = solve_turn(points, chosen[0], tree(*chosen), guide, args.image)
        m, rounds, residual = icp(points, tree(*chosen), m, scaled=False)
        m, slid, centered = reach_seams(points, m, list(slots), seams, segments,
                                        principal(chosen[0])[1])
    else:
        if framed:
            m = framed_guess(points, guide, args.image, body)
        else:
            m = first_guess(points, body)
        # A framed figure's size comes from its image; trimmed ICP left to scale it shrinks it
        # onto whatever bit of the arm it fits best
        m, rounds, residual = icp(points, surface, m, scaled=not framed)
    turn = turn_degrees(m)
    outline_overlap = None
    if args.isolated:
        outline = front_outline(apply(m, points), guide)
        picture = image_outline(args.image)
        outline_overlap = float((outline & picture).sum() / max((outline | picture).sum(), 1))
    figure.matrix_world = Matrix(m.tolist()) @ figure.matrix_world
    bpy.ops.object.select_all(action="DESELECT")
    figure.select_set(True)
    bpy.context.view_layer.objects.active = figure
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

    lined_up = points_of(figure)
    before = {name: slot_fit(lined_up, surface, segments, bones) for name, bones in slots.items()}
    matched, turns = match_limbs(lined_up, surface_samples(body, body_faces), armature, surface,
                                 [b for bones in slots.values() for b in bones]
                                 if args.isolated else None)
    set_points(figure, matched)
    after = {name: slot_fit(matched, surface, segments, bones) for name, bones in slots.items()}

    if args.armor:
        # Armor is worn whole over its slots, past their seams if it reaches there
        part = figure.copy()
        part.data = figure.data.copy()
        bpy.context.collection.objects.link(part)
        part.name = "part"
        faces, planes = len(part.data.polygons), {}
    else:
        part, faces, planes = cut_part(figure, list(slots), seams, segments)

    report = {
        "rounds": rounds,
        "scale": round(float(np.cbrt(np.linalg.det(m[:3, :3]))), 4),
        "turn_degrees": turn,
        "fitted_median_mm": round(residual * 1000, 1),
        # An isolated part turns any way; it is placed right if its outline matches the image's
        "ok": bool(outline_overlap >= ISOLATED_OVERLAP if args.isolated
                   else max(abs(a) for a in turn) <= MAX_TURN),
        "outline_overlap": outline_overlap and round(outline_overlap, 3),
        "turn_candidates": candidates,
        "slid_to_seams_mm": slid,
        "centered_on_seams_mm": centered,
        "limb_turns_degrees": turns,
        "slots_before": before,
        "slots": after,
        "part_faces": faces,
        "part_pct": round(faces / max(1, len(figure.data.polygons)) * 100, 1),
        "seams": planes,
        "matrix": m.tolist(),
        "slotFrame": slot_frame(chosen[0]),
    }
    print("[fit_body]", json.dumps(report))
    with open(os.path.join(args.out, "fit.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)
    for obj, name in ((figure, "aligned.glb"), (part, "part.glb")):
        bpy.ops.object.select_all(action="DESELECT")
        obj.select_set(True)
        bpy.ops.export_scene.gltf(filepath=os.path.join(args.out, name),
                                  export_format="GLB", use_selection=True)
    part.hide_render = True
    for k, view in enumerate((0, 90)):
        render(os.path.join(args.out, "preview_%d.png" % k), view, "TEXTURE")
    part.hide_render = False
    figure.hide_render = True
    for k, view in enumerate((0, 90)):
        render(os.path.join(args.out, "preview_%d.png" % (k + 2)), view, "TEXTURE")


if __name__ == "__main__":
    main()
