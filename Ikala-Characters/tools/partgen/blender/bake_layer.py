"""Bake a weighted garment's textures (see weight_layer.py), and make its colors recolorable.

The garment was rebuilt while being shaped, so it is unwrapped afresh and baked as hats are
(fit_accessory.py's bake: base color through emission, normals, and an ORM map of occlusion with
the mannequin left out, roughness and metalness), from the garment pushed out at full detail:
shape_layer.py's pushed.glb lies where the rebuilt garment does, where the cut figure, which
doesn't, gave blotchy, jagged normals. Only its faces facing the garment's way bake, and the
occlusion comes from the garment itself, since TRELLIS's sheets have an inner side; metalness
is cleared, as garments are cloth (see cloth_orm). TRELLIS.2's colors can't be trusted everywhere, though:
where the garment came from inside the figure's body, like sleeves over thin arms, TRELLIS
painted the bare mannequin's gray, and it makes up its own colors for the back (a cream shirt
came out tan behind). So the picked image colors the whole garment: what the front camera saw
exactly as it was generated, and the sides and back with the image's color at the same place,
blurred over the garment alone so buttons and pockets don't print through to the back and the
background and bare body don't bleed in. Wrinkles still come from the baked normals. Texels the
image doesn't reach take the garment's typical color, as TRELLIS's is often dark there. The base
color is then split into tint groups.

    blender -b --python bake_layer.py -- --weighted <weighted.blend> --figure <garment.glb>
        --image <picked.png> --painted <painted.png> --guide <guide.json> --out <folder>
        --name <id> [--size 1024]

writes baked.glb (the garment with its textures, skinned to the rig), <id>_masks/ with the tint
mask, bake.json (the tint groups' default colors) and renders preview_8.png and preview_9.png:
the garment on the mannequin from the front and back.
"""

import argparse
import json
import os
import pathlib
import sys

import bmesh
import bpy
import numpy as np
from mathutils.bvhtree import BVHTree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fit_accessory as hat  # noqa: E402
from fit_layer import import_figure, render  # noqa: E402

# How much a texel must face the front camera to take the image's color, as the size of its
# normal's share pointing at the camera; the image's color fades in up to FRONT_FULL
FRONT_START = 0.15
FRONT_FULL = 0.45
# The blur that keeps the image's details off the back and sides, in image pixels
BLUR = 12
# How far bake rays reach, as a share of the garment's size: about 2 cm on a 2 m garment,
# between the pushed garment it bakes from and the rebuilt one, which lie within millimeters
BAKE_REACH = 0.01
# How far outside the painted area a texel may land and still take the image's color, in image
# pixels: shaping and TRELLIS's small errors move the garment's outline a little
REACH = 20
# How far a baked normal may lean from the garment's own, in degrees. Folds in the cloth lean
# less; more comes from pushed.glb's crumpled patches, where TRELLIS's sleeves lay inside the
# arm, and from rays at UV island edges finding the next surface over: dark, jagged marks
MAX_LEAN = 40
# A face of pushed.glb bakes only if it faces about the way the garment does where it lies:
# the least share of its normal along the garment's. Less is TRELLIS's inner sheet, or cloth
# folded over where pushing crumpled it, like sleeves that lay inside the arm
SAME_WAY = 0.3


def read_image(path):
    """An image's colors as floats, rows from the top, sRGB."""
    image = bpy.data.images.load(os.path.abspath(path))
    width, height = image.size
    pixels = np.empty(width * height * 4, dtype=np.float32)
    image.pixels.foreach_get(pixels)
    return np.flipud(pixels.reshape(height, width, 4)[..., :3])


def blurred(rgb, radius):
    """A box blur of an image, run twice, by running sums."""
    out = rgb.astype(np.float64)
    for _ in range(2):
        for axis in (0, 1):
            padded = np.concatenate([np.repeat(out.take([0], axis), radius + 1, axis), out,
                                     np.repeat(out.take([-1], axis), radius, axis)], axis)
            sums = np.cumsum(padded, axis)
            upper = sums.take(range(2 * radius + 1, sums.shape[axis]), axis)
            lower = sums.take(range(0, sums.shape[axis] - 2 * radius - 1), axis)
            out = (upper - lower) / (2 * radius + 1)
    return out


def blurred_over(rgb, mask, radius):
    """A blur of an image counting only the pixels in a mask, so nothing outside it bleeds in."""
    weight = blurred(mask[..., None].astype(np.float64), radius)
    return blurred(rgb * mask[..., None], radius) / np.maximum(weight, 1e-6)


def grown(mask, pixels):
    """A mask grown outward by some pixels, square, by shifting it."""
    out = mask.copy()
    for _ in range(pixels):
        step = out.copy()
        step[1:] |= out[:-1]
        step[:-1] |= out[1:]
        step[:, 1:] |= out[:, :-1]
        step[:, :-1] |= out[:, 1:]
        out = step
    return out


def surface_maps(low, size):
    """Each texel's place on the garment and its normal, in world space, baked from the low-poly
    mesh's own geometry. Gives float arrays of rows from the bottom, as Blender's images are."""
    tree = low.data.materials[0].node_tree
    output = next(n for n in tree.nodes if n.type == "OUTPUT_MATERIAL")
    surface = output.inputs["Surface"].links[0].from_socket if output.inputs["Surface"].links \
        else None
    geometry = tree.nodes.new("ShaderNodeNewGeometry")
    emission = tree.nodes.new("ShaderNodeEmission")
    tree.links.new(emission.outputs["Emission"], output.inputs["Surface"])
    maps = []
    for socket in ("Position", "Normal"):
        tree.links.new(geometry.outputs[socket], emission.inputs["Color"])
        image = bpy.data.images.new("map_" + socket, size, size, alpha=False, float_buffer=True)
        image.colorspace_settings.name = "Non-Color"
        node = tree.nodes.new("ShaderNodeTexImage")
        node.image = image
        tree.nodes.active = node
        hat.select_only(low)
        bpy.ops.object.bake(type="EMIT", margin=8)
        values = np.empty(size * size * 4, dtype=np.float32)
        image.pixels.foreach_get(values)
        maps.append(values.reshape(-1, 4)[:, :3].copy())
        tree.nodes.remove(node)
    if surface is not None:
        tree.links.new(surface, output.inputs["Surface"])
    tree.nodes.remove(emission)
    tree.nodes.remove(geometry)
    return maps


def paint_from_image(base_image, covered, position, normal, picked, painted, guide):
    """Color the baked base color from the picked image: what faced the front camera exactly,
    the rest with the image blurred over the garment. Gives how many texels took the image
    exactly, and how many took it at all."""
    pixels = np.array(base_image.pixels[:]).reshape(-1, 4)
    size = guide["size"]
    column = np.floor((position[:, 0] / guide["view"] + 0.5) * size).astype(int)
    row = np.floor(((guide["middleHeight"] - position[:, 2]) / guide["view"] + 0.5) * size)
    row = row.astype(int)
    # Texels the bake from pushed.glb missed still have their place, baked from the garment itself
    on_image = (column >= 0) & (column < size) & (row >= 0) & (row < size)
    near = grown(painted, REACH)
    on_image[on_image] &= near[row[on_image], column[on_image]]
    inside = np.zeros_like(on_image)
    inside[on_image] = painted[row[on_image], column[on_image]]
    exact = np.zeros_like(pixels[:, :3])
    soft = np.zeros_like(pixels[:, :3])
    exact[on_image] = picked[row[on_image], column[on_image]]
    # Blurred wide enough to reach past the outline, so texels just outside take the garment's
    soft[on_image] = blurred_over(picked, painted, max(BLUR, REACH))[row[on_image],
                                                                     column[on_image]]

    # The body faces -Y, toward the front camera
    facing = np.clip((-normal[:, 1] - FRONT_START) / (FRONT_FULL - FRONT_START), 0, 1)
    facing[~inside] = 0
    # Texels too far outside the outline for the image to reach take the garment's typical
    # color rather than the bake's, which is TRELLIS's and often dark there, or missing
    typical = np.median(picked[painted], axis=0) if painted.any() else pixels[covered, :3].mean(0)
    rest = np.where(on_image[:, None], soft, typical)
    pixels[:, :3] = exact * facing[:, None] + rest * (1 - facing[:, None])
    base_image.pixels.foreach_set(pixels.ravel().astype(np.float32))
    return int((facing > 0).sum()), int(on_image.sum())


def face_out(normal_image):
    """Turn a baked normal map's texels that point into the surface back out, and lean none
    more than MAX_LEAN. TRELLIS's meshes are sheets with two sides, half their faces looking
    inward, and a bake ray that lands on one of those bakes its normal reversed, which shades
    flat and pale, like a hole. Gives how many texels were reversed."""
    pixels = np.array(normal_image.pixels[:]).reshape(-1, 4)
    tangent = pixels[:, :3] * 2 - 1
    reversed_ = tangent[:, 2] < 0
    tangent[reversed_] = -tangent[reversed_]
    across = np.linalg.norm(tangent[:, :2], axis=1)
    lean = np.arctan2(across, tangent[:, 2]) > np.radians(MAX_LEAN)
    tangent[lean, :2] *= (np.sin(np.radians(MAX_LEAN)) / np.maximum(across[lean], 1e-6))[:, None]
    tangent[lean, 2] = np.cos(np.radians(MAX_LEAN))
    pixels[:, :3] = (tangent + 1) / 2
    normal_image.pixels.foreach_set(pixels.ravel().astype(np.float32))
    # The bake packed the map already; pack it again so the export takes these pixels
    normal_image.pack()
    return int(reversed_.sum())


def fill_normals(normal_image, covered, low, path, size):
    """Bake normals from the rebuilt garment into the texels the figure didn't cover, where
    shape_layer.py left out cloth that started inside the body. Gives how many texels took
    them."""
    rebuilt = import_figure(path)
    tree = low.data.materials[0].node_tree
    active = tree.nodes.active
    image = hat.new_image(normal_image.name + "_rebuilt", size, "Non-Color", hat.FLAT_NORMAL)
    node = hat.bake_into("NORMAL", rebuilt, low, tree, image)
    tree.nodes.remove(node)
    tree.nodes.active = active
    pixels = np.array(normal_image.pixels[:]).reshape(-1, 4)
    pixels[~covered] = np.array(image.pixels[:]).reshape(-1, 4)[~covered]
    normal_image.pixels.foreach_set(pixels.ravel().astype(np.float32))
    bpy.data.images.remove(image)
    bpy.data.objects.remove(rebuilt)
    return int((~covered).sum())


def outer_faces(high, low):
    """Keep only the faces of the figure baked from that face the way the garment does where they
    lie (see SAME_WAY). Gives how many were dropped."""
    garment = low.matrix_world
    tree = BVHTree.FromPolygons([garment @ v.co for v in low.data.vertices],
                                [tuple(p.vertices) for p in low.data.polygons])
    mesh = bmesh.new()
    mesh.from_mesh(high.data)
    mesh.transform(high.matrix_world)
    drop = []
    for face in mesh.faces:
        _, normal, _, _ = tree.find_nearest(face.calc_center_median())
        if normal is not None and face.normal.dot(normal) < SAME_WAY:
            drop.append(face)
    bmesh.ops.delete(mesh, geom=drop, context="FACES")
    mesh.transform(high.matrix_world.inverted())
    mesh.to_mesh(high.data)
    mesh.free()
    return len(drop)


def cloth_orm(orm_image, low, hidden, size):
    """Redo the ORM map's occlusion from the garment itself, and clear its metalness. TRELLIS's
    sheets have two sides a few millimeters apart, and an occlusion ray from the inner one finds
    the outer one right away: occlusion baked from pushed.glb came out black in patches. And a
    garment is cloth, where TRELLIS's metalness (0.86 on a navy blazer) shades it as dark metal.
    Gives the mean occlusion over the garment's texels."""
    tree = low.data.materials[0].node_tree
    active = tree.nodes.active
    image = hat.new_image(orm_image.name + "_low", size, "Non-Color", (1, 1, 1, 1))
    node = tree.nodes.new("ShaderNodeTexImage")
    node.image = image
    tree.nodes.active = node
    for obj in hidden:
        obj.hide_render = True
    hat.select_only(low)
    bpy.ops.object.bake(type="AO", margin=8)
    for obj in hidden:
        obj.hide_render = False
    tree.nodes.remove(node)
    tree.nodes.active = active
    occlusion = np.array(image.pixels[:]).reshape(-1, 4)[:, 0]
    pixels = np.array(orm_image.pixels[:]).reshape(-1, 4)
    pixels[:, 0] = occlusion
    pixels[:, 2] = 0
    orm_image.pixels.foreach_set(pixels.ravel().astype(np.float32))
    orm_image.pack()
    bpy.data.images.remove(image)
    return float(occlusion[occlusion < 1].mean()) if (occlusion < 1).any() else 1.0


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    for name in ("--weighted", "--figure", "--image", "--painted", "--guide", "--out", "--name"):
        parser.add_argument(name, required=True)
    parser.add_argument("--rebuilt", help="The rebuilt garment (shape_layer.py's rebuilt.glb), "
                        "for normals where the figure has none.")
    parser.add_argument("--size", type=int, default=1024)
    args = parser.parse_args(argv)
    out = pathlib.Path(args.out)

    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(args.weighted))
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    # The mannequin's saved pose lifts its root bone about 3 cm, which weight_layer.py's reset
    # (rotations only) keeps. Posed, the garment would bake 3 cm from pushed.glb, past the bake
    # rays' reach, and its texels would land 3 cm off in the image
    armature.data.pose_position = "REST"
    low = bpy.data.objects["garment"]
    low.name = args.name
    # The part names its mesh by this, as file.glb#name
    low.data.name = args.name
    bodies = [o for o in bpy.data.objects
              if o.type == "MESH" and o is not low and o.parent is armature]
    low.data.materials.clear()
    hat.select_only(low)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(island_margin=0.003)
    bpy.ops.object.mode_set(mode="OBJECT")

    # A garment's surfaces lie close together, like a leg beside the other or an arm beside the
    # body, so bake rays reach only a little way, not the hat's share of the size
    hat.BAKE_REACH = BAKE_REACH
    high = import_figure(args.figure)
    inner = outer_faces(high, low)
    base_image, covered = hat.bake(high, low, args.name, args.size, bodies)
    filled = 0
    if args.rebuilt:
        filled = fill_normals(bpy.data.images["%s_normal" % args.name], covered, low,
                              args.rebuilt, args.size)
    reversed_ = face_out(bpy.data.images["%s_normal" % args.name])
    occlusion = cloth_orm(bpy.data.images["%s_orm" % args.name], low, bodies + [high],
                          args.size)
    position, normal = surface_maps(low, args.size)
    with open(args.guide, encoding="utf-8") as file:
        guide = json.load(file)
    picked = read_image(args.image)
    painted = read_image(args.painted)[..., 0] > 0.5
    front, replaced = paint_from_image(base_image, covered, position, normal, picked,
                                          painted, guide)
    defaults, original = hat.tint_masks(base_image, covered, out / (args.name + "_masks"))
    base_image.pack()
    bpy.data.objects.remove(high)

    report = {"front_texels": front, "image_texels": replaced, "reversed_normals": reversed_,
              "inner_faces": inner, "rebuilt_normal_texels": filled, "baked_pct": round(float(covered.mean()) * 100, 1),
              "occlusion": round(occlusion, 2), "defaults": defaults, "size": args.size}
    print("[bake_layer]", json.dumps(report))
    with open(out / "bake.json", "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)
    hat.export(str(out / "baked.glb"), low, armature)

    # The exported base color is white where it is recolorable; preview the original colors
    base_image.pixels.foreach_set(original.ravel().astype(np.float32))
    for k, view in enumerate((0, 180)):
        render(str(out / ("preview_%d.png" % (k + 8))), view, "TEXTURE")


if __name__ == "__main__":
    main()
