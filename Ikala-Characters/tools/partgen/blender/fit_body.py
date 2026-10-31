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
import os
import sys

import bpy
import numpy as np
from mathutils import Matrix

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bodies import load_body, nearest, tree, world_vertices  # noqa: E402
from fit_layer import (MAX_TURN, cut_garment, first_guess, icp, import_figure,  # noqa: E402
                       render, turn_degrees)
from shape_layer import (bone_segments, match_limbs, owners, points_of, set_points,  # noqa: E402
                         surface_samples)

# The body plan, for each slot's bones
PLAN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "src", "main",
                    "resources", "characters", "ikala", "bodyPlan", "biped.json")
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
    args = parser.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)
    slots = slot_bones([s for s in args.slots.split(",") if s])

    (body, body_faces), _ = load_body(args.body)
    surface = tree(body, body_faces)
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    segments = bone_segments(armature)
    figure = import_figure(args.mesh)
    points, _ = world_vertices(figure)

    m = first_guess(points, body)
    m, rounds, residual = icp(points, surface, m)
    turn = turn_degrees(m)
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

    variant = os.path.splitext(os.path.basename(args.body))[0]
    with open(os.path.join(os.path.dirname(os.path.abspath(args.body)),
                           variant + "_seams.json"), encoding="utf-8") as file:
        seams = json.load(file)["seams"]
    part, faces, planes = cut_part(figure, list(slots), seams, segments)

    report = {
        "rounds": rounds,
        "scale": round(float(np.cbrt(np.linalg.det(m[:3, :3]))), 4),
        "turn_degrees": turn,
        "fitted_median_mm": round(residual * 1000, 1),
        "ok": bool(max(abs(a) for a in turn) <= MAX_TURN),
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
