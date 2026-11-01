"""Render a mannequin as guides for generating clothing on it: the body seen from the front at
rest in its A pose, as a plain gray render and as depth and normal maps for ControlNet, with
the camera written down so meshes made from the images can be lined up with the body later.

The camera is orthographic, so the body keeps its proportions in the image, and frames the
whole body with its arms out. Depth and normals are traced straight through each pixel, so the
maps match the render pixel for pixel. --frame narrows the view onto part of the body, and
--only leaves out all but some slots, for a body part painted as an object on its own. --roll
turns the camera about its view, so the body shows turned that many degrees counterclockwise:
a leg standing straight up, painted on its own, TRELLIS takes for one of a pair, and makes
both, or the hips too; slanted like an arm in the A pose, it makes the one.

    blender -b --python render_guides.py -- --body <male.blend> --out <folder> [--size 1024]
        [--frame x,z,view] [--only <slot>,<slot>] [--roll degrees]

writes gray.png, depth.png (near is white), normal.png (ControlNet's colors), slots.png (which
body slot each pixel shows: the red channel is its place in guide.json's "slots" list plus one,
0 for the background) and guide.json.
"""

import argparse
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Euler, Matrix, Vector
from mathutils.bvhtree import BVHTree

# How much of the image's height and width the view covers, in meters, and where its middle is
VIEW = 2.3
MIDDLE_HEIGHT = 1.02
# Across the body, the view's middle; --frame moves and narrows it onto part of the body
MIDDLE_X = 0.0
# The camera stands this far in front of the body, looking along +Y (the body faces -Y)
DISTANCE = 6.0
# How far the camera is turned about its view, in degrees: the body shows turned this much
# counterclockwise
ROLL = 0.0
# Depth is spread over this range in front of the body's nearest point, in meters
DEPTH_RANGE = 0.6
GRAY = (0.62, 0.62, 0.64, 1)


def slot_of(obj):
    """The body slot a mannequin mesh is, by its name, like mannequin_torso."""
    return obj.name.replace("mannequin_", "", 1)


def body_mesh(only=None):
    """The mannequin's skin and eyes at rest, or only some slots of it, merged, in world space,
    with each object's name and where its triangles start."""
    for armature in bpy.data.armatures:
        armature.pose_position = "REST"
    depsgraph = bpy.context.evaluated_depsgraph_get()
    points, faces, offset = [], [], 0
    names, starts, face_count = [], [], 0
    for obj in bpy.data.objects:
        if obj.type != "MESH" or (only and slot_of(obj) not in only):
            continue
        evaluated = obj.evaluated_get(depsgraph)
        mesh = evaluated.to_mesh()
        mesh.calc_loop_triangles()
        co = np.empty(len(mesh.vertices) * 3)
        mesh.vertices.foreach_get("co", co)
        m = np.array(obj.matrix_world)
        co = co.reshape(-1, 3) @ m[:3, :3].T + m[:3, 3]
        tris = np.empty(len(mesh.loop_triangles) * 3, dtype=np.int64)
        mesh.loop_triangles.foreach_get("vertices", tris)
        evaluated.to_mesh_clear()
        points.append(co)
        faces.append(tris.reshape(-1, 3) + offset)
        offset += len(co)
        names.append(obj.name)
        starts.append(face_count)
        face_count += len(tris) // 3
    return np.concatenate(points), np.concatenate(faces), names, np.array(starts)


def pixel_to_world(i, j, size):
    """Where the middle of pixel (column i, row j) is on the image plane: world x and z."""
    across = ((i + 0.5) / size - 0.5) * VIEW
    up = (0.5 - (j + 0.5) / size) * VIEW
    roll = math.radians(ROLL)
    x = MIDDLE_X + across * math.cos(roll) + up * math.sin(roll)
    z = MIDDLE_HEIGHT - across * math.sin(roll) + up * math.cos(roll)
    return x, z


def trace(points, faces, size):
    """Each pixel's nearest surface along the view: its distance from the camera plane, normal
    and triangle, or NaN and -1 where the ray misses."""
    tree = BVHTree.FromPolygons([tuple(p) for p in points], [tuple(f) for f in faces])
    depth = np.full((size, size), np.nan)
    normal = np.zeros((size, size, 3))
    face = np.full((size, size), -1, dtype=np.int64)
    forward = Vector((0, 1, 0))
    for j in range(size):
        for i in range(size):
            x, z = pixel_to_world(i, j, size)
            hit = tree.ray_cast(Vector((x, -DISTANCE, z)), forward)
            if hit[0] is not None:
                depth[j, i] = hit[3]
                face[j, i] = hit[2]
                n = hit[1]
                if n.dot(forward) > 0:  # a back face; the surface faces the other way
                    n = -n
                normal[j, i] = n
    return depth, normal, face


def save(path, array):
    """Write an image from floats in 0..1, gray or RGB, through Blender."""
    height, width = array.shape[:2]
    rgba = np.ones((height, width, 4), dtype=np.float32)
    if array.ndim == 2:
        rgba[..., :3] = array[..., None]
    else:
        rgba[..., :3] = array
    image = bpy.data.images.new(os.path.basename(path), width, height, alpha=False)
    # Blender's images start at the bottom row
    image.pixels.foreach_set(np.flipud(rgba).ravel())
    image.filepath_raw = path
    image.file_format = "PNG"
    image.save()


def render_gray(path, size, only=None):
    """A plain studio render from the guide camera, gray body (or some slots of it) on white."""
    scene = bpy.context.scene
    material = bpy.data.materials.new("guide")
    material.diffuse_color = GRAY
    for obj in bpy.data.objects:
        if obj.type == "MESH":
            obj.hide_render = bool(only) and slot_of(obj) not in only
            obj.data.materials.clear()
            obj.data.materials.append(material)
            obj.data.polygons.foreach_set("use_smooth", [True] * len(obj.data.polygons))
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.light = "STUDIO"
    scene.display.shading.color_type = "MATERIAL"
    scene.display.shading.background_type = "VIEWPORT"
    scene.display.shading.background_color = (1, 1, 1)
    scene.world = scene.world or bpy.data.worlds.new("World")
    scene.render.film_transparent = False
    scene.view_settings.view_transform = "Standard"
    scene.render.resolution_x = scene.render.resolution_y = size
    scene.render.resolution_percentage = 100
    camera = bpy.data.objects.new("guide_camera", bpy.data.cameras.new("guide_camera"))
    scene.collection.objects.link(camera)
    camera.data.type = "ORTHO"
    camera.data.ortho_scale = VIEW
    # Looking along +Y, turned about it by the roll
    camera.matrix_world = (Matrix.Translation((MIDDLE_X, -DISTANCE, MIDDLE_HEIGHT))
                           @ Matrix.Rotation(math.radians(ROLL), 4, "Y")
                           @ Euler((math.pi / 2, 0, 0)).to_matrix().to_4x4())
    scene.camera = camera
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--body", required=True, help="The mannequin's .blend file.")
    parser.add_argument("--out", required=True)
    parser.add_argument("--size", type=int, default=1024)
    parser.add_argument("--frame", help="x,z,view: the middle and size of a view framed on part "
                                        "of the body, in meters, instead of the whole body.")
    parser.add_argument("--only", help="Comma-separated slots to render on their own, the rest "
                                       "of the body left out, like a forearm and hand.")
    parser.add_argument("--roll", type=float, default=0.0,
                        help="Degrees to turn the camera about its view; the body shows turned "
                             "that much counterclockwise.")
    args = parser.parse_args(argv)
    only = set(args.only.split(",")) if args.only else None
    global MIDDLE_X, MIDDLE_HEIGHT, VIEW, ROLL
    if args.frame:
        MIDDLE_X, MIDDLE_HEIGHT, VIEW = (float(v) for v in args.frame.split(","))
    ROLL = args.roll
    os.makedirs(args.out, exist_ok=True)
    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(args.body))

    points, faces, names, starts = body_mesh(only)
    depth, normal, face = trace(points, faces, args.size)
    slots = [slot_of(bpy.data.objects[name]) for name in names]
    owner = np.searchsorted(starts, face, side="right")
    save(os.path.join(args.out, "slots.png"), np.where(face >= 0, owner / 255.0, 0))
    hit = ~np.isnan(depth)
    near = np.nanmin(depth)
    # Near is white, fading to black DEPTH_RANGE behind the nearest point; the background black
    depth_image = np.where(hit, np.clip(1 - (depth - near) / DEPTH_RANGE, 0, 1), 0)
    save(os.path.join(args.out, "depth.png"), depth_image)
    # ControlNet's normal maps face the camera: x right in red, y up in green, toward the
    # viewer in blue; seen from the front that is world x, z and -y, turned by the roll
    roll = math.radians(ROLL)
    n = np.stack([normal[..., 0] * math.cos(roll) - normal[..., 2] * math.sin(roll),
                  normal[..., 0] * math.sin(roll) + normal[..., 2] * math.cos(roll),
                  -normal[..., 1]], axis=-1)
    normal_image = np.where(hit[..., None], n * 0.5 + 0.5, np.array([0.5, 0.5, 1.0]))
    save(os.path.join(args.out, "normal.png"), normal_image)
    render_gray(os.path.join(args.out, "gray.png"), args.size, only)

    with open(os.path.join(args.out, "guide.json"), "w", encoding="utf-8") as file:
        json.dump({
            "body": os.path.abspath(args.body),
            "note": "Blender space: Z up, the body faces -Y; an orthographic front view",
            "size": args.size, "view": VIEW, "middleHeight": MIDDLE_HEIGHT,
            "middleX": MIDDLE_X, "roll": ROLL,
            "pixel": "across = ((column + 0.5) / size - 0.5) * view; up = (0.5 - (row + 0.5) / "
                     "size) * view; x = middleX + across * cos(roll) + up * sin(roll); z = "
                     "middleHeight - across * sin(roll) + up * cos(roll)",
            "covered": float(hit.mean()),
            "slots": slots,
            "only": sorted(only) if only else None,
        }, file, indent=1)
    print("[guides] wrote %s: body covers %.0f%% of the image" % (args.out, hit.mean() * 100))


if __name__ == "__main__":
    main()
