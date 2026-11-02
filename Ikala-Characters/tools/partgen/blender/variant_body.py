"""Carry a baked body part fitted to the male mannequin (see bake_body.py) over to another body
type, through the base mesh cages, as clothing is (variant_layer.py): the part follows the cage
given the other body's shape, keeping its vertices, so its textures and tint mask fit. With
--mirror it is first mirrored across the body onto the other side, as body parts are generated
for the left side only; without the cages it stays on the male.

Its welded seams can't just follow, though: each body's rings are its own, with different vertex
counts (the female's shoulder has 13 where the male's has 10). So the carried part is cut short
of each welded seam again and bridged to the new body's ring, as shape_body.py does, the
bridge taking texture coordinates from the edge it starts at. It then takes the new body's
weights (weight_body.py) and its caps are made again (add_caps.py) and dressed (bake_body.py).

    blender -b --python variant_body.py -- --baked <baked.glb> --body <female.blend>
        [--from-cage <male_cage.npz> --to-cage <female_cage.npz>] [--mirror]
        --slots lowerArm.L --name <id> --out <file.glb> --preview <folder> [--open wrist.L]

where the slots and open seams are the ones the result fills, on its side (lowerArm.R when
mirrored).

writes the part and its caps, skinned to the new body's rig, variant.json and renders
preview_10.png and preview_11.png in the preview folder: the part on the new body from the front
and back.
"""

import argparse
import json
import os
import sys

import bmesh
import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "blender"))
import fit_accessory as hat  # noqa: E402
from add_caps import CAP_INFIX, make_cap, open_loops  # noqa: E402
from bake_body import dress_caps  # noqa: E402
from bodies import body_meshes, merged, nearest, tree  # noqa: E402
from fit_body import outer_seams, plan_forms, plan_slots, seam_plane  # noqa: E402
from fit_layer import render  # noqa: E402
from shape_body import (bridge, close_strays, form_of, mannequin_ring, mark_rings,  # noqa: E402
                        orient_out, trim)
from shape_layer import bone_segments  # noqa: E402
from variant_layer import carry, face_normals, import_garment, untangle  # noqa: E402
from weight_body import PREFIX, skin_part  # noqa: E402

# Rounds of unfolding the faces the cage carry folds over, as variant_layer.py does
UNTANGLE_PASSES = 8
# Vertices this close together are one, in meters: glTF splits them along UV seams, which would
# leave the part open along every seam (texture coordinates live on face corners, so welding
# keeps them)
WELD = 1e-6


def dress_bridges(part, rings):
    """Give the faces that bridge to the rings texture coordinates: each corner the coordinates
    its vertex has in the faces it had before, a ring vertex those of the nearest vertex on the
    edge it was bridged from."""
    ring_vertices = {i for indices in rings for i in indices}
    layer = part.data.uv_layers.active.data
    uv_of = {}
    bridging = []
    for polygon in part.data.polygons:
        if ring_vertices & set(polygon.vertices):
            bridging.append(polygon)
            continue
        for vertex, loop in zip(polygon.vertices, polygon.loop_indices):
            uv_of.setdefault(vertex, tuple(layer[loop].uv))
    points = {v.index: v.co for v in part.data.vertices}
    for polygon in bridging:
        known = [v for v in polygon.vertices if v in uv_of]
        for vertex, loop in zip(polygon.vertices, polygon.loop_indices):
            if vertex in uv_of:
                layer[loop].uv = uv_of[vertex]
            elif known:
                layer[loop].uv = uv_of[min(known, key=lambda v: (points[v] - points[vertex])
                                           .length)]


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    for name in ("--baked", "--body", "--slots", "--name", "--out", "--preview"):
        parser.add_argument(name, required=True)
    parser.add_argument("--from-cage", help="The cage of the body the part was fitted to.")
    parser.add_argument("--to-cage", help="The cage of the body it goes to.")
    parser.add_argument("--mirror", action="store_true",
                        help="Mirror it onto the other side first.")
    parser.add_argument("--open", default="", help="Comma-separated seams left open.")
    args = parser.parse_args(argv)
    names = [s for s in args.slots.split(",") if s]
    left_open = {s for s in args.open.split(",") if s}

    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(args.body))
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    armature.animation_data_clear()
    armature.data.pose_position = "REST"
    segments = bone_segments(armature)
    slots = plan_slots()
    bones = [b for n in names for b in slots[n]["bones"] if b in segments]
    with open(os.path.splitext(os.path.abspath(args.body))[0] + "_seams.json",
              encoding="utf-8") as file:
        rings = json.load(file)["seams"]

    part = import_garment(args.baked, args.name)
    mesh = bmesh.new()
    mesh.from_mesh(part.data)
    bmesh.ops.remove_doubles(mesh, verts=mesh.verts, dist=WELD)
    if args.mirror:
        # Across the body's middle, x = 0 (Blender's x runs to the body's left), faces turned
        # back to look out
        for vertex in mesh.verts:
            vertex.co.x = -vertex.co.x
        bmesh.ops.reverse_faces(mesh, faces=mesh.faces)
    mesh.to_mesh(part.data)
    mesh.free()
    folded = []
    if args.from_cage and args.to_cage:
        start = np.array([part.matrix_world @ v.co for v in part.data.vertices])
        start_normals = face_normals(part)
        carry(part, np.load(args.from_cage), np.load(args.to_cage)["vertices"])
        for _ in range(UNTANGLE_PASSES):
            folded.append(untangle(part, start, start_normals))
            if not folded[-1]:
                break

    # The rings it was welded to went with the carry or the mirror; each welded seam is cut and
    # bridged to this body's, on this side
    inside = np.mean([np.mean(segments[b], axis=0) for b in bones], axis=0)
    planes = {}
    for seam in outer_seams(names):
        if seam in left_open:
            continue
        middle, normal, radius = seam_plane(rings[seam], inside)
        planes[seam] = {"middle": middle, "normal": normal, "radius": radius}
        planes[seam]["inset"] = trim(part, planes[seam])
    welded = {}
    for seam, plane in planes.items():
        slot = next(n for n in names if seam in slots[n].get("seams", []))
        ring = mannequin_ring(bpy.data.objects[PREFIX + slot], plane["middle"])
        welded[seam] = (bridge(part, seam, plane, ring, plane["inset"]), ring)
    dress_bridges(part, [indices for indices, _ in welded.values()])
    close_strays(part, [indices for indices, _ in welded.values()])
    mesh = bmesh.new()
    mesh.from_mesh(part.data)
    orient_out(mesh, segments, bones)
    mesh.to_mesh(part.data)
    mesh.free()
    for polygon in part.data.polygons:
        polygon.use_smooth = True
    mark_rings(part, welded.values())

    slot_objects = [bpy.data.objects[PREFIX + n] for n in names]
    _, _, empty = skin_part(part, slot_objects, armature, bones)
    caps = []
    for seam, (indices, _) in welded.items():
        loop = next(lp for lp in open_loops(part.data) if set(lp) == set(indices))
        cap = make_cap(part, loop, seam, armature)
        cap.name = cap.data.name = args.name + CAP_INFIX + seam
        caps.append(cap)
    dress_caps(part, caps)

    ring_error = max((float(np.max(np.linalg.norm(
        np.array([part.data.vertices[i].co for i in indices])
        - np.array([r["co"] for r in ring]), axis=1)))
        for indices, ring in welded.values()), default=0.0)
    # The clothing form this body's version fits in, against this body's skin
    skin = merged([o for o in body_meshes() if o.name.startswith(PREFIX)])
    count = len(part.data.vertices)
    co = np.empty(count * 3)
    part.data.vertices.foreach_get("co", co)
    _, signed = nearest(co.reshape(-1, 3), tree(*skin))
    report = {"welded": sorted(welded), "open": sorted(left_open), "folded_faces": folded,
              "form": form_of(signed, plan_forms()),
              "stands_out_mm": {"p95": round(float(np.percentile(signed, 95)) * 1000, 1)},
              "ring_error_mm": round(ring_error * 1000, 3),
              "open_edges": sorted(len(lp) for lp in open_loops(part.data)),
              "nearest_bone": int(empty.sum())}
    print("[variant_body]", json.dumps(report))
    os.makedirs(args.preview, exist_ok=True)
    with open(os.path.join(args.preview, "variant.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)

    hat.select_only(part, armature, *caps)
    options = {"filepath": os.path.abspath(args.out), "export_format": "GLB",
               "use_selection": True, "export_yup": True, "export_skins": True,
               "export_def_bones": False, "export_animations": False, "export_colors": False,
               "export_extras": False, "export_tangents": True,
               "export_image_format": "AUTO"}
    known = {p.identifier for p in bpy.ops.export_scene.gltf.get_rna_type().properties}
    bpy.ops.export_scene.gltf(**{k: v for k, v in options.items() if k in known})

    replaced = {PREFIX + n for n in names}
    for obj in bpy.data.objects:
        if obj.type == "MESH" and obj is not part:
            obj.hide_render = obj.name in replaced or CAP_INFIX in obj.name
    for k, view in enumerate((0, 180)):
        render(os.path.join(args.preview, "preview_%d.png" % (k + 10)), view, "TEXTURE")


if __name__ == "__main__":
    main()
