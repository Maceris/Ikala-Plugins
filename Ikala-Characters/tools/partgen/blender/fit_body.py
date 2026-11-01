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
from fit_layer import (MAX_TURN, apply, cut_garment, first_guess, icp,  # noqa: E402
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
# How far toward the camera, as the cosine of the angle, a candidate turn must keep the mesh's
# own front to be preferred
MIN_FACING = 0.3
# The grid, in cells per side, a mesh's front outline is compared with its image's on
OUTLINE_GRID = 128
# How much an isolated part's front outline must overlap its image's, as a share of both
# together, to count as placed right
ISOLATED_OVERLAP = 0.5
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


def plan_slots():
    """The body plan's slots by name."""
    with open(PLAN, encoding="utf-8") as file:
        # Definitions may start with // comment lines
        plan = json.loads("".join(line for line in file if not line.lstrip().startswith("//")))
    return {slot["id"]: slot for slot in plan["slots"]}


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
    column = ((points[:, 0] - guide.get("middleX", 0.0)) / guide["view"] + 0.5) * OUTLINE_GRID
    row = ((guide["middleHeight"] - points[:, 2]) / guide["view"] + 0.5) * OUTLINE_GRID
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
    ROLLS ways about it, each way round end to end. Each is refined by ICP onto the slots' skin;
    of those fitting nearly as well as the best (within TURN_SLACK), and keeping the mesh's own
    front (which TRELLIS takes from the image) toward the camera if any do, the one whose front
    outline best overlaps the image's wins, the image having been painted over the slots from
    the front.
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
            # TRELLIS keeps the image's front as the mesh's (glTF +Z, Blender -Y once
            # imported); the camera looks at the body's front, along +Y
            facing = float(-(m[:3, :3] @ np.array([0.0, -1.0, 0.0]))[1] / scale)
            candidates.append((m, residual, float(overlap), facing))
    best = min(c[1] for c in candidates)
    fitting = [c for c in candidates if c[1] <= best * TURN_SLACK]
    # A part seen from the front often has the same outline turned round, like a shin
    m = max([c for c in fitting if c[3] >= MIN_FACING] or fitting, key=lambda c: c[2])[0]
    return m, [{"residual_mm": round(c[1] * 1000, 1), "overlap": round(c[2], 3),
                "facing": round(c[3], 2)} for c in candidates]


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
    if args.isolated:
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
    matched, turns = match_limbs(lined_up, surface_samples(body, body_faces), armature, surface)
    set_points(figure, matched)
    after = {name: slot_fit(matched, surface, segments, bones) for name, bones in slots.items()}

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
