"""Line a generated clothing mesh up on the mannequin it was painted on.

TRELLIS.2 makes the whole figure in the image, the mannequin wearing the garment, in a 1 m box.
Its bare parts lie on the mannequin's skin and the garment stands outside it, so the figure is
lined up by its closest points: first stood as tall as the body, feet on the ground and
centered, then moved, turned a little and scaled by trimmed ICP, which fits only the share of
points nearest the body each round, so the garment standing out doesn't pull it off.

    blender -b --python fit_layer.py -- --mesh <figure.glb> --body <male.blend> --out <folder>

writes aligned.glb (the figure on the body; imported into Blender it lands on the mannequin's
.blend), distances.npz (how
far outside the body each of its vertices is, negative inside), fit.json and renders,
preview_0.png to preview_3.png: the figure with the body's wireframe over it from the front and
side, then colored by distance: blue on the skin, orange outside it, red inside it.
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

# How many vertices ICP fits with, and how many rounds at most
SAMPLES = 20000
ROUNDS = 60
# The share of points nearest the body each round fits to
TRIM = 0.6
# Rounds stop when the fit moves points less than this, in meters
SETTLED = 1e-4
# Points this close to the body count as skin, for the report and the colors, in meters
SKIN = 0.008
# How far a figure may turn about each axis while fitting, in degrees; more means a bad fit
MAX_TURN = 20


def similarity(source, target):
    """The scale, rotation and translation that best take source points onto target points
    (Umeyama), as a 4x4 matrix."""
    mu_s, mu_t = source.mean(0), target.mean(0)
    s, t = source - mu_s, target - mu_t
    covariance = t.T @ s / len(source)
    u, d, vt = np.linalg.svd(covariance)
    flip = np.eye(3)
    if np.linalg.det(u) * np.linalg.det(vt) < 0:
        flip[2, 2] = -1
    rotation = u @ flip @ vt
    scale = np.trace(np.diag(d) @ flip) / (s ** 2).sum(1).mean()
    m = np.eye(4)
    m[:3, :3] = scale * rotation
    m[:3, 3] = mu_t - scale * rotation @ mu_s
    return m


def apply(m, points):
    return points @ m[:3, :3].T + m[:3, 3]


def first_guess(points, body):
    """Stand the figure as tall as the body, feet on the ground, centered over it."""
    low, high = points.min(0), points.max(0)
    body_low, body_high = body.min(0), body.max(0)
    scale = (body_high[2] - body_low[2]) / (high[2] - low[2])
    m = np.eye(4)
    m[:3, :3] *= scale
    middle = (low + high) / 2
    body_middle = (body_low + body_high) / 2
    m[:3, 3] = [body_middle[0] - scale * middle[0], body_middle[1] - scale * middle[1],
                body_low[2] - scale * low[2]]
    return m


def icp(points, surface, m):
    """Refine a fit by trimmed ICP. Gives the fit, the rounds taken and the fitted share's
    median distance."""
    rng = np.random.default_rng(0)
    sample = points[rng.choice(len(points), min(SAMPLES, len(points)), replace=False)]
    for rounds in range(1, ROUNDS + 1):
        moved = apply(m, sample)
        closest, signed = nearest(moved, surface)
        distance = np.abs(signed)
        keep = distance <= np.quantile(distance, TRIM)
        step = similarity(moved[keep], closest[keep])
        m = step @ m
        if np.abs(apply(step, moved[keep]) - moved[keep]).max() < SETTLED:
            break
    moved = apply(m, sample)
    _, signed = nearest(moved, surface)
    distance = np.abs(signed)
    return m, rounds, float(np.median(distance[distance <= np.quantile(distance, TRIM)]))


def turn_degrees(m):
    """How far a fit turns, about each axis, in degrees."""
    rotation = m[:3, :3] / np.cbrt(np.linalg.det(m[:3, :3]))
    angles = Matrix(rotation.tolist()).to_euler("XYZ")
    return [round(math.degrees(a), 2) for a in angles]


def import_figure(path):
    """The figure's meshes from its glb, joined into one object."""
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=os.path.abspath(path))
    meshes = [o for o in set(bpy.data.objects) - before if o.type == "MESH"]
    bpy.ops.object.select_all(action="DESELECT")
    for obj in meshes:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    if len(meshes) > 1:
        bpy.ops.object.join()
    figure = bpy.context.view_layer.objects.active
    figure.name = "figure"
    return figure


def color_by_distance(obj, signed):
    """A vertex color for each of a mesh's vertices by how far outside the body it is."""
    colors = np.empty((len(signed), 4), dtype=np.float32)
    colors[:] = (0.95, 0.55, 0.15, 1)          # outside: garment
    colors[np.abs(signed) <= SKIN] = (0.25, 0.45, 0.9, 1)   # on the skin
    colors[signed < -SKIN] = (0.85, 0.15, 0.15, 1)          # inside
    attribute = obj.data.color_attributes.new("distance", "FLOAT_COLOR", "POINT")
    attribute.data.foreach_set("color", colors.ravel())
    return attribute


def render(path, view, color):
    """A plain render of the scene from the front (0) or side (90)."""
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.light = "STUDIO"
    scene.display.shading.color_type = color
    scene.render.resolution_x, scene.render.resolution_y = 700, 900
    camera = scene.camera
    if camera is None:
        camera = bpy.data.objects.new("camera", bpy.data.cameras.new("camera"))
        scene.collection.objects.link(camera)
        scene.camera = camera
    camera.data.type = "ORTHO"
    camera.data.ortho_scale = 2.3
    angle = math.radians(view)
    target = Vector((0, 0, 1.02))
    camera.location = target + Vector((math.sin(angle) * 6, -math.cos(angle) * 6, 0))
    camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--mesh", required=True)
    parser.add_argument("--body", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)

    (body, body_faces), _ = load_body(args.body)
    surface = tree(body, body_faces)
    figure = import_figure(args.mesh)
    points, _ = world_vertices(figure)

    m = first_guess(points, body)
    m, rounds, residual = icp(points, surface, m)
    turn = turn_degrees(m)
    figure.matrix_world = Matrix(m.tolist()) @ figure.matrix_world
    points, _ = world_vertices(figure)
    _, signed = nearest(points, surface)
    report = {
        "rounds": rounds,
        "scale": round(float(np.cbrt(np.linalg.det(m[:3, :3]))), 4),
        "turn_degrees": turn,
        "fitted_median_mm": round(residual * 1000, 1),
        "skin_pct": round(float((np.abs(signed) <= SKIN).mean()) * 100, 1),
        "outside_pct": round(float((signed > SKIN).mean()) * 100, 1),
        "inside_pct": round(float((signed < -SKIN).mean()) * 100, 1),
        "ok": bool(max(abs(a) for a in turn) <= MAX_TURN),
    }
    print("[fit_layer]", json.dumps(report))
    with open(os.path.join(args.out, "fit.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)
    np.savez(os.path.join(args.out, "distances.npz"), signed=signed.astype(np.float32))

    bpy.ops.object.select_all(action="DESELECT")
    figure.select_set(True)
    bpy.context.view_layer.objects.active = figure
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    bpy.ops.export_scene.gltf(filepath=os.path.join(args.out, "aligned.glb"),
                              export_format="GLB", use_selection=True)

    # The figure as made, with the body's wireframe over it, then colored by distance
    bodies = [o for o in bpy.data.objects if o.type == "MESH" and o is not figure]
    for obj in bodies:
        obj.display_type = "WIRE"
    for k, view in enumerate((0, 90)):
        render(os.path.join(args.out, "preview_%d.png" % k), view, "TEXTURE")
    for obj in bodies:
        obj.hide_render = True
    color_by_distance(figure, signed)
    for k, view in enumerate((0, 90)):
        render(os.path.join(args.out, "preview_%d.png" % (k + 2)), view, "VERTEX")


if __name__ == "__main__":
    main()
