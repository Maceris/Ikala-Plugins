"""Make a cape part from a template cape and a picture of a cape seen from behind.

Run it headless with Blender 5 or later, which partgen.py does:

    blender -b --factory-startup --python fit_cape.py -- --image <picked.png> --mask <cut.png>
        --mannequin <mannequin.glb> --plan <json> --rule <json> --length <meters>
        --name <mesh name> --out <part.glb> [--chain-scale <times>] [--preview <folder>]
        [--tints <json>] [--report <json>]

Generated capes come out of TRELLIS.2 shaped round a body it imagined, which never matches the
mannequin, so a cape is made from a template instead: a sheet that rides over the shoulders from
the base of the neck and hangs down the back, --length meters from the shoulders, widening from
the shoulders' width to the rule's "flare" times it at the hem and curving round the sides by
"wrap" meters. Each row hangs back, whole, until it is "clear" meters behind the body, never
less far back than the row above, so it falls straight from the shoulder blades and the seat;
the collar is pushed out over the shoulders. The arms swing in front of a cape, so they don't
count. The picture is projected onto it from behind,
its cape's outline stretched over the template's, and the template is cut away wherever the
picture shows background (by the mask cut_out.py makes of it), so a ragged hem or a pointed collar comes through. The sheet is
thickened so both sides show.

The collar is weighted to the chest, and the rest to the cape chain bone by bone (see
fit_extension.weight), the chain made long enough to reach the hem: the part's chainLength,
which --chain-scale sets instead for another body, to match the first body's.
"""
import argparse
import json
import os
import pathlib
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Vector
from mathutils.kdtree import KDTree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fit_accessory  # noqa: E402
import fit_extension  # noqa: E402

# The template's columns across and rows down, and how many rows the collar takes
COLUMNS = 32
ROWS = 56
COLLAR_ROWS = 4
# How far down from the base of the neck the shoulders' top is, and how far forward of the
# chain's head the collar starts, in meters
SHOULDER_DROP = 0.1
COLLAR_FORWARD = 0.1
# A grid square is kept when this many of its corners are on the cape; the others then move onto
# the cape's outline
KEEP_CORNERS = 2
# How thick the sheet is made, so both sides show
THICKNESS = 0.006
# The mannequin's meshes are named for their slots after this, with seam caps' names holding
# CAP_INFIX; a cape hangs clear of all but the arms
MANNEQUIN_PREFIX = "mannequin_"
CAP_INFIX = "_cap_"
ARMS = ("Arm", "hand")
# Defaults for the rule's shape
FLARE = 1.6
WRAP = 0.12
CLEAR = 0.02
OFFSET = 0.06


def bone_head(armature, name):
    return np.array(armature.matrix_world @ armature.data.bones[name].head_local)


def template(armature, chain, length, rule):
    """The template's points, rows from the collar down, columns from the character's right to
    its left, in world space."""
    neck = bone_head(armature, "neck")
    left = bone_head(armature, "upperarm.L")
    right = bone_head(armature, "upperarm.R")
    shoulders = np.linalg.norm(left - right)
    top = neck[2] - SHOULDER_DROP * 0.5
    bottom = top - length
    lengths = fit_extension.polyline_lengths(chain)
    flare = rule.get("flare", FLARE)
    wrap = rule.get("wrap", WRAP)
    offset = rule.get("offset", OFFSET)
    points = np.zeros((ROWS + 1, COLUMNS + 1, 3))
    for r in range(ROWS + 1):
        share = r / ROWS
        z = top - share * length
        # Behind the chain where it reaches this height, or its head above it
        if z >= chain[0][2]:
            center = chain[0].copy()
        else:
            down = (chain[0][2] - z)
            center, _ = fit_extension.along(chain, lengths, down)
            center = center.copy()
        width = shoulders * (1 + (flare - 1) * share)
        for c in range(COLUMNS + 1):
            across = (c / COLUMNS - 0.5)
            x = center[0] + across * width
            # Curves round the sides, more as it widens
            y = center[1] + offset - wrap * (2 * across) ** 2
            if r < COLLAR_ROWS:
                # The collar comes forward over the shoulders to the base of the neck
                pull = 1 - r / COLLAR_ROWS
                y -= COLLAR_FORWARD * pull
            points[r, c] = (x, y, z)
    return points, top, bottom


def body_behind(mannequin):
    """The mannequin's body that a cape hangs behind and rests on: its torso, pelvis, legs and
    head, without the arms and hands, which swing in front of it, the seam caps or the glTF
    importer's bone shapes."""
    return [obj for obj in mannequin
            if obj.name.startswith(MANNEQUIN_PREFIX) and CAP_INFIX not in obj.name
            and not any(part in obj.name for part in ARMS)]


def hang_clear(points, body, gap):
    """Move each row below the collar back, whole, until all of it is gap meters behind the
    body, and keep every row at least as far back as the one above, so the cape falls straight
    from the shoulder blades and the seat rather than clinging to the body. Gives how far the
    lowest row moved."""
    tree = fit_accessory.body_tree(body)
    pushed = 0.0
    for r in range(COLLAR_ROWS, points.shape[0]):
        need = 0.0
        for point in points[r]:
            # Down the view from far behind, onto the body's back
            start = Vector((point[0], point[1] + 2.0, point[2]))
            hit = tree.ray_cast(start, Vector((0, -1, 0)))[0]
            if hit is not None:
                need = max(need, hit.y + gap - point[1])
        pushed = max(pushed, need)
        points[r, :, 1] += pushed
    return pushed


def sheet(points, name):
    """A mesh of the template's grid."""
    rows, columns = points.shape[:2]
    faces = []
    for r in range(rows - 1):
        for c in range(columns - 1):
            a = r * columns + c
            # Faces turn out behind, toward a camera looking from behind
            faces.append((a, a + columns, a + columns + 1, a + 1))
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([tuple(p) for p in points.reshape(-1, 3)], [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def cape_outline(image_path, mask_path):
    """The picture, and its cape's mask (white on black, from cut_out.py), rows from the top.
    Gives them and the cape's box (left, top, right, bottom), in pixels."""
    picture = bpy.data.images.load(image_path)
    cut = bpy.data.images.load(mask_path)
    width, height = cut.size
    pixels = np.array(cut.pixels[:], dtype=np.float32).reshape(height, width, -1)
    # Blender's rows start at the bottom
    mask = pixels[::-1, :, 0] > 0.5
    rows, columns = np.nonzero(mask)
    box = (columns.min(), rows.min(), columns.max() + 1, rows.max() + 1)
    print("[cape] picture %dx%d, cape %s, %.0f%% of it" % (width, height, box,
                                                            mask.mean() * 100))
    return picture, mask, box


def project(obj, points, mask, box, size):
    """Give the sheet texture coordinates from the picture seen from behind, its outline
    stretched over the cape's box, and cut it to the picture's outline: faces over the
    background go, and the corners left outside it move onto it, so the edge follows the
    outline rather than the grid's steps."""
    xs = points[..., 0].reshape(-1)
    zs = points[..., 2].reshape(-1)
    left, top, right, bottom = box
    width, height = size
    x_low, x_high, z_low, z_high = xs.min(), xs.max(), zs.min(), zs.max()
    # From behind, the character's left (+x) is the picture's left
    px = left + (x_high - xs) / (x_high - x_low) * (right - left)
    py = top + (z_high - zs) / (z_high - z_low) * (bottom - top)
    columns = np.clip(px, 0, width - 1).astype(int)
    rows = np.clip(py, 0, height - 1).astype(int)
    inside = mask[rows, columns]

    # Faces mostly over the background go
    mesh = obj.data
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bm.faces.ensure_lookup_table()
    bm.verts.ensure_lookup_table()
    gone = [f for f in bm.faces if sum(inside[v.index] for v in f.verts) < KEEP_CORNERS]
    kept = {v.index for f in bm.faces if f not in set(gone) for v in f.verts}
    # The corners of kept faces left outside the outline move to its nearest pixel, before the
    # cut renumbers the vertices
    edge = mask & ~(np.roll(mask, 1, 0) & np.roll(mask, -1, 0)
                    & np.roll(mask, 1, 1) & np.roll(mask, -1, 1))
    edge_rows, edge_columns = np.nonzero(edge)
    tree = KDTree(len(edge_rows))
    for k, (r, c) in enumerate(zip(edge_rows, edge_columns)):
        tree.insert((c + 0.5, r + 0.5, 0), k)
    tree.balance()
    moved = 0
    for vert in bm.verts:
        i = vert.index
        if inside[i] or i not in kept:
            continue
        (c, r, _), _, _ = tree.find((px[i], py[i], 0))
        px[i], py[i] = c, r
        vert.co.x = x_high - (c - left) / (right - left) * (x_high - x_low)
        vert.co.z = z_high - (r - top) / (bottom - top) * (z_high - z_low)
        moved += 1
    uv = np.stack([px / width, 1 - py / height], axis=-1)
    layer = bm.loops.layers.uv.new("UVMap")
    for face in bm.faces:
        for loop in face.loops:
            loop[layer].uv = uv[loop.vert.index]
    bmesh.ops.delete(bm, geom=gone, context="FACES")
    bm.to_mesh(mesh)
    bm.free()
    print("[cape] cut %d of %d faces over the background, moved %d corners onto its outline"
          % (len(gone), (points.shape[0] - 1) * (points.shape[1] - 1), moved))


def material(obj, picture, name):
    mat = bpy.data.materials.new(name)
    tree = mat.node_tree
    bsdf = tree.nodes["Principled BSDF"]
    texture = tree.nodes.new("ShaderNodeTexImage")
    texture.image = picture
    tree.links.new(texture.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.8
    obj.data.materials.append(mat)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description="Make a cape from a template and a picture.")
    parser.add_argument("--image", required=True)
    parser.add_argument("--mask", required=True, help="The cape cut out of the picture, white "
                                                      "on black (see cut_out.py).")
    parser.add_argument("--mannequin", required=True)
    parser.add_argument("--plan", required=True, help="The body plan's chains, as JSON.")
    parser.add_argument("--rule", required=True)
    parser.add_argument("--length", type=float, help="Meters from the shoulders to the hem.")
    parser.add_argument("--chain-scale", type=float,
                        help="The chain length scale, instead of working it out from --length "
                             "on the first body: another body's must match the first's.")
    parser.add_argument("--name", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--preview")
    parser.add_argument("--tints")
    parser.add_argument("--report")
    args = parser.parse_args(argv)
    rule = json.loads(args.rule)
    plan = json.loads(args.plan)
    plan_chain = next(c for c in plan["extensions"] if c["id"] == "cape")

    bpy.ops.wm.read_factory_settings(use_empty=True)
    added = fit_accessory.import_glb(args.mannequin)
    armature = next(obj for obj in added if obj.type == "ARMATURE")
    mannequin = [obj for obj in added if obj.type == "MESH"]
    bones, root, joints = fit_extension.chain_of(armature, plan_chain)
    rest = fit_extension.polyline_lengths(joints)[-1]
    neck = bone_head(armature, "neck")
    top = neck[2] - SHOULDER_DROP * 0.5
    if args.chain_scale:
        chain_scale = args.chain_scale
        length = top - joints[0][2] + chain_scale * rest
    else:
        length = args.length or rule["length"]
        # The chain reaches from its head down to the hem
        chain_scale = max((length - (top - joints[0][2])) / rest, 0.1)
    scaled = joints[0] + np.concatenate(
        [[np.zeros(3)], np.cumsum(np.diff(joints, axis=0) * chain_scale, axis=0)])

    points, _, _ = template(armature, scaled, length, rule)
    body = body_behind(mannequin)
    pushed = hang_clear(points, body, rule.get("clear", CLEAR))
    print("[cape] hung %.3f m further back to clear the body" % pushed)
    cape = sheet(points, args.name)
    picture, mask, box = cape_outline(args.image, args.mask)
    project(cape, points, mask, box, picture.size)
    # The collar rests on the shoulders
    fit_extension.keep_clear(cape, body, rule.get("clear", CLEAR))
    fit_accessory.apply_modifier(cape, "SOLIDIFY", thickness=THICKNESS, offset=0.0)
    fit_accessory.apply_modifier(cape, "TRIANGULATE")
    fit_accessory.select_only(cape)
    bpy.ops.object.shade_smooth()

    # The picture is its texture: its cape's colors become tint groups
    covered = mask[::-1].reshape(-1)
    out = pathlib.Path(args.out)
    # A new image, not a copy, so it is written under its own name, which its tint masks
    # are found by, rather than the picture's file name
    image = bpy.data.images.new("%s_emit" % args.name, *picture.size, alpha=False)
    image.colorspace_settings.name = "sRGB"
    image.pixels.foreach_set(np.array(picture.pixels[:], dtype=np.float32))
    defaults, original = fit_accessory.tint_masks(
        image, covered, out.with_name(out.stem + fit_accessory.MASK_FOLDER_SUFFIX))
    image.pack()
    material(cape, image, args.name)
    fit_extension.weight(cape, bones, root, scaled)
    fit_extension.attach(cape, armature)
    out.parent.mkdir(parents=True, exist_ok=True)
    fit_accessory.export(str(out), cape, armature)
    if args.tints:
        with open(args.tints, "w", encoding="utf8") as stream:
            json.dump(defaults, stream, indent=2)
    if args.report:
        with open(args.report, "w", encoding="utf8") as stream:
            json.dump({"chainLength": round(chain_scale, 3), "length": round(length, 3),
                       "triangles": len(cape.data.polygons)}, stream, indent=2)
    if args.preview:
        image.pixels.foreach_set(original.ravel().astype(np.float32))
        fit_accessory.preview(args.preview, cape, mannequin)
    print("[cape] %.2f m long, chain x%.3f, %d triangles"
          % (length, chain_scale, len(cape.data.polygons)))


if __name__ == "__main__":
    main()
