"""Bake a weighted body part's textures (see weight_body.py), and make its colors recolorable.

The part was rebuilt as a solid while being shaped, so it is unwrapped afresh and baked as
clothing is (bake_layer.py, after fit_accessory.py's bake: base color through emission, normals
and an ORM map), from the part fit_body.py cut out of TRELLIS.2's figure, which lies where the
solid does. Only that figure's faces looking the part's way bake, as TRELLIS's surfaces have an
inner side. Unlike clothing, a body part is what the figure was made of, so TRELLIS's colors and
metalness stand on its sides and back: a mechanical arm's steel stays metal. Only what faces the
front camera takes the picked image's colors exactly.

The base color is then split into tint groups. One that is about the mannequin's gray and not
metal is the skin TRELLIS kept from the mannequin, which the part file gives the character's skin
color, so it matches the body beside it.

The part's caps (see shape_body.py) take its material, and the texture coordinates of the ring
vertices they close, so a cap colors like the end of the part. They are named for the character
plugin's assembler, <id>_cap_<seam>.

    blender -b --python bake_body.py -- --weighted <weighted.blend> --figure <part.glb>
        --image <picked.png> --painted <painted.png> --guide <guide.json> --out <folder>
        --name <id> --slots lowerArm.L [--size 1024]

writes baked.glb (the part and its caps with their textures, skinned to the rig), <id>_masks/
with the tint mask, bake.json (the tint groups' default colors, and which is skin) and renders
preview_8.png and preview_9.png: the part on the mannequin from the front and back.
"""

import argparse
import json
import os
import pathlib
import sys

import bpy
import numpy as np
from mathutils.bvhtree import BVHTree
from mathutils.kdtree import KDTree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "blender"))
import fit_accessory as hat  # noqa: E402
from add_caps import CAP_INFIX  # noqa: E402
from bake_layer import (FRONT_FULL, FRONT_START, face_out, outer_faces, read_image,  # noqa: E402
                        surface_maps)
from fit_layer import guide_pixels, import_figure, render  # noqa: E402

# How far bake rays reach, as a share of the part's size: about 3 cm on a 50 cm forearm. Unlike a
# garment, a body part isn't pushed onto the figure's surface, so the solid it was rebuilt into
# can lie a few centimeters from the cut figure, where TRELLIS's sheets were thin
BAKE_REACH = 0.06
# A tint group is the mannequin's skin when its default color has less than SKIN_CHROMA between
# its strongest and weakest channels (out of 255), lies within SKIN_BAND of the mannequin's gray
# in lightness (SKIN_GRAY, the guide render's middle; shadowed bone is darker), and its texels
# average less than SKIN_METAL metalness: gray steel is gray too, but TRELLIS gives it more
# metal (0.6-0.7 on the first limbs, against 0.44 for a beast hand's gray skin)
SKIN_CHROMA = 24
SKIN_GRAY = 128
SKIN_BAND = 30
SKIN_METAL = 0.5
# A texel this near the unbaked marker color, as the distance between their colors, was missed:
# the bake's margin and filtering blend the marker into the texels at UV islands' edges
MARKER_BLEND = 0.5
# Darker texels blended with it lean magenta, red and blue above green by this much, and lie
# within MARKER_NEAR texels of one the bake missed outright: a part that is purple itself
# doesn't lie beside missed texels everywhere
MARKER_LEAN = 0.12
MARKER_NEAR = 3
# How far, in texels, island colors are spread into the empty space around the islands, so
# texture filtering at their edges blends in the part's colors rather than the marker
SPREAD = 16


def near(mask, size, texels):
    """A square mask grown by some texels each way."""
    grid = mask.reshape(size, size)
    out = grid.copy()
    for _ in range(texels):
        step = out.copy()
        step[1:] |= out[:-1]
        step[:-1] |= out[1:]
        step[:, 1:] |= out[:, :-1]
        step[:, :-1] |= out[:, 1:]
        out = step
    return out.ravel()


def fill_missed(images, covered, position):
    """Give the texels the bake missed, on the part's UV islands, the colors of the nearest baked
    texel on the part's surface, in each of some images, the first the base color. Gives how
    many were filled."""
    islands = np.linalg.norm(position, axis=1) > 1e-6
    color = np.array(images[0].pixels[:]).reshape(-1, 4)[:, :3]
    size = int(round(np.sqrt(len(color))))
    covered &= np.linalg.norm(color - np.array(hat.UNBAKED[:3]), axis=1) > MARKER_BLEND
    lean = np.minimum(color[:, 0], color[:, 2]) - color[:, 1]
    covered &= ~((lean > MARKER_LEAN) & near(islands & ~covered, size, MARKER_NEAR))
    missed = np.flatnonzero(islands & ~covered)
    baked = np.flatnonzero(covered)
    if not len(missed) or not len(baked):
        return 0
    tree = KDTree(len(baked))
    for k, index in enumerate(baked):
        tree.insert(position[index], k)
    tree.balance()
    source = np.array([baked[tree.find(position[index])[1]] for index in missed])
    for image in images:
        pixels = np.array(image.pixels[:]).reshape(-1, 4)
        pixels[missed] = pixels[source]
        image.pixels.foreach_set(pixels.ravel().astype(np.float32))
        # Packed already by the bake: pack again so the export takes these pixels
        if image.packed_file is not None:
            image.pack()
    covered[missed] = True
    return int(len(missed))


def spread(images, covered, size):
    """Spread the covered texels' colors outward over the rest, SPREAD texels, in each of some
    images, so texture filtering at UV islands' edges finds the part's colors."""
    reached = covered.reshape(size, size).copy()
    pixels = [np.array(image.pixels[:]).reshape(size, size, 4) for image in images]
    for _ in range(SPREAD):
        grown = reached.copy()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            source = np.roll(reached, (dy, dx), axis=(0, 1))
            take = source & ~grown
            for image in pixels:
                image[take] = np.roll(image, (dy, dx), axis=(0, 1))[take]
            grown |= take
        reached = grown
    for image, values in zip(images, pixels):
        image.pixels.foreach_set(values.ravel().astype(np.float32))
        if image.packed_file is not None:
            image.pack()


def paint_front(base_image, covered, position, normal, picked, painted, guide):
    """Give the texels facing the front camera, where the image was painted, the image's color
    exactly, fading in with how squarely they face it; the rest keep the bake's. Gives how many
    took it."""
    pixels = np.array(base_image.pixels[:]).reshape(-1, 4)
    size = guide["size"]
    column, row = (np.floor(v).astype(int) for v in guide_pixels(position, guide))
    on_image = covered & (column >= 0) & (column < size) & (row >= 0) & (row < size)
    inside = np.zeros_like(on_image)
    inside[on_image] = painted[row[on_image], column[on_image]]
    # The body faces -Y, toward the front camera
    facing = np.clip((-normal[:, 1] - FRONT_START) / (FRONT_FULL - FRONT_START), 0, 1)
    facing[~inside] = 0
    exact = np.zeros_like(pixels[:, :3])
    exact[inside] = picked[row[inside], column[inside]]
    pixels[:, :3] = exact * facing[:, None] + pixels[:, :3] * (1 - facing[:, None])
    base_image.pixels.foreach_set(pixels.ravel().astype(np.float32))
    return int((facing > 0).sum())


def tint_groups(defaults, mask_image, orm_image, covered):
    """Each tint group's default color, its share of the baked texels and their average
    metalness and roughness, by mask channel."""
    mask = np.array(mask_image.pixels[:]).reshape(-1, 4)
    orm = np.array(orm_image.pixels[:]).reshape(-1, 4)
    groups = {}
    for k, channel in enumerate(hat.MASK_CHANNELS):
        members = (mask[:, k] > 0.5) & covered
        if channel not in defaults or not members.any():
            continue
        groups[channel] = {"color": defaults[channel],
                           "share": round(float(members.sum() / max(covered.sum(), 1)), 3),
                           "metal": round(float(orm[members, 2].mean()), 3),
                           "rough": round(float(orm[members, 1].mean()), 3)}
    return groups


def skin_channel(groups):
    """The biggest tint group that is the mannequin's skin (see SKIN_CHROMA), or None. A part of
    one group is all part, never skin: skin TRELLIS kept from the mannequin only ever borders the
    part's own colors, and a single gray group is the part's matte plates or steel."""
    if len(groups) < 2:
        return None
    best = None
    for channel, group in groups.items():
        rgb = [int(group["color"][i:i + 2], 16) for i in (1, 3, 5)]
        gray = max(rgb) - min(rgb) < SKIN_CHROMA and abs(sum(rgb) / 3 - SKIN_GRAY) < SKIN_BAND
        if gray and group["metal"] < SKIN_METAL:
            if best is None or group["share"] > groups[best]["share"]:
                best = channel
    return best


def dress_caps(low, caps):
    """Give the caps the part's material, and each cap vertex the texture coordinates of the
    part's vertex at the same place (its middle, their average)."""
    uv_of = {}
    layer = low.data.uv_layers.active.data
    for polygon in low.data.polygons:
        for vertex, loop in zip(polygon.vertices, polygon.loop_indices):
            uv_of.setdefault(vertex, tuple(layer[loop].uv))
    points = [low.matrix_world @ v.co for v in low.data.vertices]
    tree = BVHTree.FromPolygons(points, [tuple(p.vertices) for p in low.data.polygons])
    for cap in caps:
        cap.data.materials.clear()
        cap.data.materials.append(low.data.materials[0])
        mesh = cap.data
        corners = []
        for vertex in mesh.vertices:
            location, _, face, _ = tree.find_nearest(cap.matrix_world @ vertex.co)
            nearest = min(low.data.polygons[face].vertices,
                          key=lambda v: (points[v] - location).length)
            corners.append(uv_of.get(nearest, (0.0, 0.0)))
        middle = len(mesh.vertices) - 1
        corners[middle] = tuple(np.mean(corners[:middle], axis=0))
        uvs = mesh.uv_layers.new(name=low.data.uv_layers.active.name) \
            if not mesh.uv_layers else mesh.uv_layers.active
        for polygon in mesh.polygons:
            for vertex, loop in zip(polygon.vertices, polygon.loop_indices):
                uvs.data[loop].uv = corners[vertex]


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    for name in ("--weighted", "--figure", "--image", "--painted", "--guide", "--out", "--name"):
        parser.add_argument(name, required=True)
    parser.add_argument("--slots", required=True, help="Comma-separated slots the part fills.")
    parser.add_argument("--size", type=int, default=1024)
    args = parser.parse_args(argv)
    out = pathlib.Path(args.out)

    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(args.weighted))
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    # As bake_layer.py: baked at rest, where the cut figure lies
    armature.data.pose_position = "REST"
    low = bpy.data.objects["part"]
    caps = [o for o in bpy.data.objects if o.name.startswith("part" + CAP_INFIX)]
    low.name = low.data.name = args.name
    for cap in caps:
        cap.name = cap.data.name = args.name + cap.name[len("part"):]
    hidden = [o for o in bpy.data.objects
              if o.type == "MESH" and o is not low and o not in caps]
    low.data.materials.clear()
    hat.select_only(low)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(island_margin=0.003)
    bpy.ops.object.mode_set(mode="OBJECT")

    hat.BAKE_REACH = BAKE_REACH
    high = import_figure(args.figure)
    inner = outer_faces(high, low)
    for cap in caps:
        cap.hide_render = True
    base_image, covered = hat.bake(high, low, args.name, args.size, hidden)
    reversed_ = face_out(bpy.data.images["%s_normal" % args.name])
    position, normal = surface_maps(low, args.size)
    maps = [base_image, bpy.data.images["%s_normal" % args.name],
            bpy.data.images["%s_orm" % args.name]]
    filled = fill_missed(maps, covered, position)
    spread(maps, covered, args.size)
    with open(args.guide, encoding="utf-8") as file:
        guide = json.load(file)
    picked = read_image(args.image)
    painted = read_image(args.painted)[..., 0] > 0.5
    front = paint_front(base_image, covered, position, normal, picked, painted, guide)
    defaults, original = hat.tint_masks(base_image, covered, out / (args.name + "_masks"))
    groups = tint_groups(defaults, bpy.data.images[base_image.name + "_mask"],
                         bpy.data.images["%s_orm" % args.name], covered)
    skin = skin_channel(groups)
    base_image.pack()
    bpy.data.objects.remove(high)
    dress_caps(low, caps)

    report = {"front_texels": front, "reversed_normals": reversed_, "inner_faces": inner,
              "filled_texels": filled, "defaults": defaults, "groups": groups,
              "skin": skin, "caps": [c.name for c in caps], "size": args.size}
    print("[bake_body]", json.dumps(report))
    with open(out / "bake.json", "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)
    hat.select_only(low, armature, *caps)
    options = {"filepath": str(out / "baked.glb"), "export_format": "GLB",
               "use_selection": True, "export_yup": True, "export_skins": True,
               "export_def_bones": False, "export_animations": False, "export_colors": False,
               "export_extras": False, "export_tangents": True,
               "export_image_format": "AUTO"}
    known = {p.identifier for p in bpy.ops.export_scene.gltf.get_rna_type().properties}
    bpy.ops.export_scene.gltf(**{k: v for k, v in options.items() if k in known})

    # The exported base color is white where it is recolorable; preview the original colors
    base_image.pixels.foreach_set(original.ravel().astype(np.float32))
    replaced = {"mannequin_" + s for s in args.slots.split(",") if s}
    for obj in hidden:
        # The slots the part replaces, and the mannequin's caps, stay out of the preview
        obj.hide_render = obj.name in replaced or CAP_INFIX in obj.name
    for k, view in enumerate((0, 180)):
        render(str(out / ("preview_%d.png" % (k + 8))), view, "TEXTURE")


if __name__ == "__main__":
    main()
