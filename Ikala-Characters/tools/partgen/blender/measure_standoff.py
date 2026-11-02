"""Measure how far a body part stands out from the mannequin over each slot it fills.

Run by partgen.py in Blender:
    blender -b --factory-startup --python measure_standoff.py -- \
        --part <part.glb> --mannequin <male.glb> --slots lowerArm.L,hand.L --out <standoff.json>

Each of the part's vertices counts for the slot whose mannequin mesh is nearest it, and how far it
stands out is its signed distance from that mesh (inside is below 0). A slot's standoff is the
STANDOFF_SHARE percentile of its vertices', kept between 0 and the thickest clothing form's
offset: clothing over the part is pushed out by it, so a few lumps a sleeve would drape over don't
make the whole sleeve as big as the lumps.
"""

import argparse
import json
import os
import sys

import bpy
import numpy as np
from mathutils.bvhtree import BVHTree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fit_body import plan_forms  # noqa: E402

# The share of a slot's vertices its standoff covers
STANDOFF_SHARE = 0.95


def slot_trees(mannequin, slots):
    """The mannequin's mesh for each slot, as BVH trees in world space."""
    trees = {}
    for slot in slots:
        obj = next((o for o in mannequin if o.name == "mannequin_" + slot), None)
        if obj is None:
            raise SystemExit("The mannequin has no mesh for the slot " + slot)
        world = obj.matrix_world
        trees[slot] = BVHTree.FromPolygons([world @ v.co for v in obj.data.vertices],
                                           [list(p.vertices) for p in obj.data.polygons])
    return trees


def standoffs(part_meshes, trees):
    """Each slot's standoff in meters, for the slots some of the part's vertices are nearest."""
    found = {slot: [] for slot in trees}
    for obj in part_meshes:
        world = obj.matrix_world
        for vertex in obj.data.vertices:
            point = world @ vertex.co
            best = None
            for slot, tree in trees.items():
                location, normal, _, distance = tree.find_nearest(point)
                if location is not None and (best is None or distance < best[0]):
                    sign = 1.0 if (point - location).dot(normal) >= 0 else -1.0
                    best = (distance, slot, sign * distance)
            if best is not None:
                found[best[1]].append(best[2])
    forms = plan_forms()
    most = forms[-1][1] if forms else 0.0
    return {slot: round(float(np.clip(np.percentile(values, STANDOFF_SHARE * 100), 0.0, most)), 4)
            for slot, values in found.items() if values}


def import_meshes(path):
    """Import a glb, giving its new mesh objects, caps left out."""
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    return [o for o in bpy.data.objects
            if o not in before and o.type == "MESH" and "_cap_" not in o.name]


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--part", required=True, help="The body part's glb.")
    parser.add_argument("--mannequin", required=True, help="The mannequin's glb it was fitted on.")
    parser.add_argument("--slots", required=True, help="Comma-separated slots the part fills.")
    parser.add_argument("--out", required=True, help="Where to write the standoffs, as JSON.")
    args = parser.parse_args(argv)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    mannequin = import_meshes(args.mannequin)
    trees = slot_trees(mannequin, [s for s in args.slots.split(",") if s])
    measured = standoffs(import_meshes(args.part), trees)
    print("[measure_standoff]", json.dumps(measured))
    with open(args.out, "w", encoding="utf-8") as file:
        json.dump(measured, file)


if __name__ == "__main__":
    main()
