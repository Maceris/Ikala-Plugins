"""Line a generated clothing mesh up on the mannequin it was painted on.

TRELLIS.2 makes the whole figure in the image, the mannequin wearing the garment, in a 1 m box.
Its bare parts lie on the mannequin's skin and the garment stands outside it, so the figure is
lined up by its closest points: first stood as tall as the body, feet on the ground and
centered, then moved, turned a little and scaled by trimmed ICP, which fits only the share of
points nearest the body each round, so the garment standing out doesn't pull it off.

The garment is then cut out of the figure by where the image was painted: the figure was made
from an image painted over a render of the mannequin, only where the garment goes, so every
point of the figure that falls on a painted pixel seen from the front is garment, front and
back, and the rest is the bare mannequin. Faces mostly on the garment are kept, and pieces much
smaller than the biggest dropped.

    blender -b --python fit_layer.py -- --mesh <figure.glb> --body <male.blend> --out <folder>
        [--painted <painted.png> --guide <guide.json>]

writes aligned.glb (the figure on the body; imported into Blender it lands on the mannequin's
.blend), distances.npz (how far outside the body each of its vertices is, negative inside),
fit.json and renders, preview_0.png to preview_3.png: the figure with the body's wireframe over
it from the front and side, then colored by distance: blue on the skin, orange outside it, red
inside it. With a painted area, also garment.glb and preview_4.png and preview_5.png: the
garment on the mannequin from the front and side.
"""

import argparse
import json
import math
import os
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bodies import load_body, nearest, normals, tree, world_vertices  # noqa: E402

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
# Garment pieces with fewer faces than this share of the biggest are dropped
CRUMB = 0.05
# Vertices this close together are one, in the figure's own units
WELD = 1e-5
# Vertices whose normal points this little toward or away from the camera (its share along the
# view) face sideways, and are seen edge on: the painted outline runs a little inside the
# figure's own there, so they take the painted area grown by SIDE_GROW pixels (about 2 cm),
# not so much as to bridge the gap between the legs
SIDE = 0.35
SIDE_GROW = 8


def similarity(source, target, scaled=True):
    """The scale, rotation and translation that best take source points onto target points
    (Umeyama), as a 4x4 matrix; without scaled, only the rotation and translation."""
    mu_s, mu_t = source.mean(0), target.mean(0)
    s, t = source - mu_s, target - mu_t
    covariance = t.T @ s / len(source)
    u, d, vt = np.linalg.svd(covariance)
    flip = np.eye(3)
    if np.linalg.det(u) * np.linalg.det(vt) < 0:
        flip[2, 2] = -1
    rotation = u @ flip @ vt
    scale = np.trace(np.diag(d) @ flip) / (s ** 2).sum(1).mean() if scaled else 1.0
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


def icp(points, surface, m, scaled=True):
    """Refine a fit by trimmed ICP, scaling it too unless told not to. Gives the fit, the rounds
    taken and the fitted share's median distance."""
    rng = np.random.default_rng(0)
    sample = points[rng.choice(len(points), min(SAMPLES, len(points)), replace=False)]
    for rounds in range(1, ROUNDS + 1):
        moved = apply(m, sample)
        closest, signed = nearest(moved, surface)
        distance = np.abs(signed)
        keep = distance <= np.quantile(distance, TRIM)
        step = similarity(moved[keep], closest[keep], scaled)
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
    # TRELLIS splits its meshes at every texture seam; joined up again, pieces and pushes see
    # the surface as one. The texture coordinates stay with each face's corners.
    mesh = bmesh.new()
    mesh.from_mesh(figure.data)
    bmesh.ops.remove_doubles(mesh, verts=mesh.verts, dist=WELD)
    mesh.to_mesh(figure.data)
    mesh.free()
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


def read_mask(path):
    """A black and white image as booleans, rows from the top."""
    image = bpy.data.images.load(os.path.abspath(path))
    width, height = image.size
    pixels = np.empty(width * height * 4, dtype=np.float32)
    image.pixels.foreach_get(pixels)
    # Blender's images start at the bottom row
    return np.flipud(pixels.reshape(height, width, 4)[..., 0] > 0.5)


def on_painted(points, painted, guide):
    """Whether each point, seen straight on from the front, falls on a painted pixel."""
    size = guide["size"]
    column = np.floor(((points[:, 0] - guide.get("middleX", 0.0)) / guide["view"] + 0.5)
                      * size).astype(int)
    row = np.floor(((guide["middleHeight"] - points[:, 2]) / guide["view"] + 0.5) * size)
    row = row.astype(int)
    inside = (column >= 0) & (column < size) & (row >= 0) & (row < size)
    found = np.zeros(len(points), dtype=bool)
    found[inside] = painted[row[inside], column[inside]]
    return found


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


def cut_garment(figure, keep):
    """A copy of the figure with only the faces most of whose corners are kept, less pieces
    much smaller than the biggest. Gives it and how many faces it has."""
    garment = figure.copy()
    garment.data = figure.data.copy()
    garment.name = "garment"
    bpy.context.scene.collection.objects.link(garment)
    mesh = bmesh.new()
    mesh.from_mesh(garment.data)
    mesh.faces.ensure_lookup_table()
    dropped = [f for f in mesh.faces if sum(keep[v.index] for v in f.verts) * 2 <= len(f.verts)]
    bmesh.ops.delete(mesh, geom=dropped, context="FACES_ONLY")
    # Pieces: faces joined through shared edges
    seen, pieces = set(), []
    for face in mesh.faces:
        if face in seen:
            continue
        piece, stack = [], [face]
        seen.add(face)
        while stack:
            current = stack.pop()
            piece.append(current)
            for edge in current.edges:
                for other in edge.link_faces:
                    if other not in seen:
                        seen.add(other)
                        stack.append(other)
        pieces.append(piece)
    biggest = max((len(p) for p in pieces), default=0)
    crumbs = [f for p in pieces if len(p) < biggest * CRUMB for f in p]
    bmesh.ops.delete(mesh, geom=crumbs, context="FACES_ONLY")
    loose = [v for v in mesh.verts if not v.link_faces]
    bmesh.ops.delete(mesh, geom=loose, context="VERTS")
    count = len(mesh.faces)
    mesh.to_mesh(garment.data)
    mesh.free()
    return garment, count


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--mesh", required=True)
    parser.add_argument("--body", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--painted", help="Where the figure's image was painted.")
    parser.add_argument("--painted-back",
                        help="The painted area for what faces away, with gaps like an open "
                             "front's neckline closed.")
    parser.add_argument("--guide", help="The guide the image was painted over.")
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
    points, figure_faces = world_vertices(figure)
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

    garment = None
    if args.painted:
        with open(args.guide, encoding="utf-8") as file:
            guide = json.load(file)
        # Where the lined-up figure's vertices are, by the mesh's own vertex order
        painted = read_mask(args.painted)
        keep = on_painted(points, painted, guide)
        facing = normals(points, figure_faces)[:, 1]
        if args.painted_back:
            # What faces away from the camera, as the body faces -Y, and lies behind the body
            # from it: TRELLIS's surfaces are sheets with two sides, so the bare chest in a
            # jacket's open front faces away too, on its inner side
            away = facing > 0
            away[away] = [surface.ray_cast(Vector(p), Vector((0, -1, 0)))[0] is not None
                          for p in points[away]]
            keep[away] = on_painted(points[away], read_mask(args.painted_back), guide)
        # Without this, legs and sleeves lose slits along their sides, which open into holes
        side = np.abs(facing) < SIDE
        keep[side] |= on_painted(points[side], grown(painted, SIDE_GROW), guide)
        garment, faces = cut_garment(figure, keep)
        report["garment_faces"] = faces
        report["garment_pct"] = round(faces / max(1, len(figure.data.polygons)) * 100, 1)
    print("[fit_layer]", json.dumps(report))
    with open(os.path.join(args.out, "fit.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)
    np.savez(os.path.join(args.out, "distances.npz"), signed=signed.astype(np.float32))

    for obj, name in ((figure, "aligned.glb"), (garment, "garment.glb")):
        if obj is None:
            continue
        bpy.ops.object.select_all(action="DESELECT")
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
        bpy.ops.export_scene.gltf(filepath=os.path.join(args.out, name),
                                  export_format="GLB", use_selection=True)
    if garment is not None:
        garment.hide_render = True

    # The figure as made, with the body's wireframe over it, then colored by distance
    bodies = [o for o in bpy.data.objects
              if o.type == "MESH" and o is not figure and o is not garment]
    for obj in bodies:
        obj.display_type = "WIRE"
    for k, view in enumerate((0, 90)):
        render(os.path.join(args.out, "preview_%d.png" % k), view, "TEXTURE")
    for obj in bodies:
        obj.hide_render = True
    color_by_distance(figure, signed)
    for k, view in enumerate((0, 90)):
        render(os.path.join(args.out, "preview_%d.png" % (k + 2)), view, "VERTEX")

    # The garment alone on the gray mannequin
    if garment is not None:
        figure.hide_render = True
        garment.hide_render = False
        for obj in bodies:
            obj.hide_render = False
            obj.display_type = "SOLID"
        for k, view in enumerate((0, 90)):
            render(os.path.join(args.out, "preview_%d.png" % (k + 4)), view, "TEXTURE")


if __name__ == "__main__":
    main()
