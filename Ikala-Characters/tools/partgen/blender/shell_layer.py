"""Build a garment from the mannequin's own surface, in place of shape_layer.py's rebuilt figure.

TRELLIS.2's garments are crumpled two-sided sheets, and however they are pushed, rebuilt and
reduced, they keep holes, ragged edges and loose cloth, like a jacket's lapels. Clothing for games
is made the other way round: the body's surface pushed out a little and cut along clean lines,
with the cloth's detail in its textures. So the garment is the mannequin's skin in the slots
the layer covers (never its head, hands or feet), welded into one surface and subdivided once, pushed
out by the layer's gap, and further onto the TRELLIS garment where that stands off the body (a
jacket's lapels, a loose shirt), no further than the layer's reach. It is then cut where the
picked image painted the garment: front faces by its painted area, back faces by the back's,
sides by either, along the line where the area's blurred edge crosses half, so the cut runs
smoothly through faces rather than stepping along them, and at a seam, given the layer's cut.
Its edges are relaxed, and it is reduced only if it is well over budget. The bake still takes
the TRELLIS garment's normals and the picked image's colors (shape_layer.py's pushed.glb and
rebuilt.glb), and the topology keeps the body's rings, which bend cleanly at the joints.

    blender -b --python shell_layer.py -- --garment <garment.glb> --body <male.blend>
        --painted <painted.png> --painted-back <painted_back.png> --guide <guide.json>
        --slots torso,pelvis,... --out <folder> --gap 0.004 --triangles 2000 [--reach 0.012]
        [--cut waist,above,-0.04] [--flap 0.07,0.006] [--simplifier <jars>]

writes shaped.glb (replacing shape_layer.py's) and shell.json.
"""

import argparse
import json
import os
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import shape_layer as shaping  # noqa: E402
from bodies import body_meshes, load_body, tree  # noqa: E402
from fit_layer import import_figure, read_mask  # noqa: E402

# How far out along the skin's normal the shell may move onto the TRELLIS garment, in meters
CONFORM_REACH = 0.06
# A hit counts only if the skin nearest it lies within this share of the way from where the ray
# started: a ray from an arm's inner side otherwise finds the torso's cloth beside it
CONFORM_OWN = 0.5
# How many times the moves out are averaged with their neighbors', so the shell stays smooth
CONFORM_ROUNDS = 6
# How many times folded faces' vertices are drawn back halfway to the gap
UNFOLD_ROUNDS = 8
# How much of the painted area's edge is blurred before the cut, in image pixels (about 2 mm
# each), so the cut line runs smoothly
CUT_BLUR = 3
# How far a side face looks to the front or back before it takes only that side's painted area:
# the share of its normal along the camera's axis
SIDE = 0.35
# The painted areas grown for sides, which the front camera sees edge-on, in image pixels
SIDE_GROW = 8
# How far apart, along a limb, its hem is looked for on the painted area, in meters
HEM_STEP = 0.005
# How far over the triangle budget the shell may be before it is reduced, as a share
OVER_BUDGET = 0.25
# The slots clothing never covers, which the shell leaves out
BARE = ("head", "hand.L", "hand.R", "foot.L", "foot.R")


def skin_shell(slots):
    """The mannequin's skin in some slots, joined in world space and welded, as one object,
    subdivided once."""
    mesh = bmesh.new()
    for obj in body_meshes():
        if obj.name.split("_", 1)[-1] not in slots:
            continue
        part = bmesh.new()
        part.from_mesh(obj.data)
        part.transform(obj.matrix_world)
        data = bpy.data.meshes.new("part")
        part.to_mesh(data)
        part.free()
        mesh.from_mesh(data)
        bpy.data.meshes.remove(data)
    bmesh.ops.remove_doubles(mesh, verts=mesh.verts[:], dist=1e-5)
    data = bpy.data.meshes.new("garment")
    mesh.to_mesh(data)
    mesh.free()
    shell = bpy.data.objects.new("garment", data)
    bpy.context.scene.collection.objects.link(shell)
    bpy.ops.object.select_all(action="DESELECT")
    shell.select_set(True)
    bpy.context.view_layer.objects.active = shell
    subdivide = shell.modifiers.new("subdivide", "SUBSURF")
    subdivide.levels = 1
    subdivide.boundary_smooth = "PRESERVE_CORNERS"
    bpy.ops.object.modifier_apply(modifier=subdivide.name)
    return shell


def conform(shell, figure, surface, gap, reach):
    """Push the shell out along its normals by the gap, and further onto the TRELLIS garment
    where that stands off the skin (see CONFORM_REACH, CONFORM_OWN), no further than the reach,
    the moves smoothed. gap and reach are one for all vertices or one for each. Gives how many
    vertices moved onto the garment."""
    from mathutils.bvhtree import BVHTree
    garment = BVHTree.FromPolygons([figure.matrix_world @ v.co for v in figure.data.vertices],
                                   [tuple(p.vertices) for p in figure.data.polygons])
    shell.data.update()
    count = len(shell.data.vertices)
    points = np.empty(count * 3)
    shell.data.vertices.foreach_get("co", points)
    points = points.reshape(-1, 3)
    normals = np.empty(count * 3)
    shell.data.vertices.foreach_get("normal", normals)
    normals = normals.reshape(-1, 3)
    gaps = np.broadcast_to(np.asarray(gap, dtype=float), count).copy()
    out = gaps.copy()
    onto = np.zeros(count, dtype=bool)
    for i in range(count):
        start = Vector(points[i]) + Vector(normals[i]) * 1e-4
        hit = garment.ray_cast(start, Vector(normals[i]), CONFORM_REACH)
        if hit[0] is None or hit[3] <= gaps[i]:
            continue
        location, _, _, _ = surface.find_nearest(hit[0])
        if (location - Vector(points[i])).length > CONFORM_OWN * hit[3]:
            continue
        out[i] = hit[3]
        onto[i] = True
    edges = np.empty(len(shell.data.edges) * 2, dtype=np.int64)
    shell.data.edges.foreach_get("vertices", edges)
    edges = edges.reshape(-1, 2)
    neighbors = np.maximum(np.bincount(edges.ravel(), minlength=count), 1)
    for _ in range(CONFORM_ROUNDS):
        total = np.zeros(count)
        np.add.at(total, edges[:, 0], out[edges[:, 1]])
        np.add.at(total, edges[:, 1], out[edges[:, 0]])
        out = np.maximum(gaps, (out + total / neighbors) / 2)
    if reach is not None:
        out = np.minimum(out, np.broadcast_to(np.asarray(reach, dtype=float), count))
    # Moving out unevenly, like onto a shoulder pad, can fold faces over, a hole when drawn:
    # those faces' vertices are drawn back toward the gap until none is folded
    before = face_normals(shell)
    faces = [list(p.vertices) for p in shell.data.polygons]
    for _ in range(UNFOLD_ROUNDS):
        moved = points + normals * out[:, None]
        shell.data.vertices.foreach_set("co", moved.astype(np.float32).ravel())
        shell.data.update()
        folded = np.flatnonzero((face_normals(shell) * before).sum(1) < 0)
        if not len(folded):
            break
        loose = np.zeros(count, dtype=bool)
        loose[[v for k in folded for v in faces[k]]] = True
        loose[edges[loose[edges].any(1)].ravel()] = True
        out[loose] = (out[loose] + gaps[loose]) / 2
    return int(onto.sum())


def face_normals(obj):
    obj.data.update()
    normals = np.empty(len(obj.data.polygons) * 3)
    obj.data.polygons.foreach_get("normal", normals)
    return normals.reshape(-1, 3)


def blurred(mask, radius):
    """A mask as floats, box-blurred twice by some pixels."""
    out = mask.astype(np.float64)
    for _ in range(2):
        for axis in (0, 1):
            padded = np.concatenate([np.repeat(out.take([0], axis), radius + 1, axis), out,
                                     np.repeat(out.take([-1], axis), radius, axis)], axis)
            sums = np.cumsum(padded, axis)
            out = (sums.take(range(2 * radius + 1, sums.shape[axis]), axis)
                   - sums.take(range(0, sums.shape[axis] - 2 * radius - 1), axis)) / (2 * radius + 1)
    return out


def sample(image, points, guide):
    """An image's value at points seen straight on from the front, bilinear, 0 off it."""
    size = guide["size"]
    x = (points[:, 0] / guide["view"] + 0.5) * size - 0.5
    y = ((guide["middleHeight"] - points[:, 2]) / guide["view"] + 0.5) * size - 0.5
    x0, y0 = np.floor(x).astype(int), np.floor(y).astype(int)
    fx, fy = x - x0, y - y0
    out = np.zeros(len(points))
    for dx, dy, w in ((0, 0, (1 - fx) * (1 - fy)), (1, 0, fx * (1 - fy)), (0, 1, (1 - fx) * fy),
                      (1, 1, fx * fy)):
        cx, cy = x0 + dx, y0 + dy
        inside = (cx >= 0) & (cx < size) & (cy >= 0) & (cy < size)
        out[inside] += w[inside] * image[cy[inside], cx[inside]]
    return out


def painted_field(points, normals, front, back, guide):
    """How much each point lies on the painted area, 0 to 1: the front's for points facing the
    front camera, the back's for points facing away, the larger of both grown for the sides,
    blended by how far the normal turns."""
    front_value = sample(blurred(front, CUT_BLUR), points, guide)
    back_value = sample(blurred(back, CUT_BLUR), points, guide)
    side_value = np.maximum(sample(blurred(shaping.grown(front, SIDE_GROW), CUT_BLUR), points,
                                   guide),
                            sample(blurred(shaping.grown(back, SIDE_GROW), CUT_BLUR), points,
                                   guide))
    # The body faces -Y, toward the front camera
    toward = np.clip(-normals[:, 1] / SIDE, 0, 1)
    away = np.clip(normals[:, 1] / SIDE, 0, 1)
    side = 1 - toward - away
    return toward * front_value + away * back_value + side * side_value


def limb_owned(points, armature):
    """Which points lie nearest a limb's bones (see shape_layer.py's LIMBS) or those under them."""
    limbs = shaping.with_beyond(armature, [bone for chain in shaping.LIMBS for bone in chain])
    return np.isin(shaping.owners(points, shaping.bone_segments(armature)), list(limbs))


def cut_limbs(shell, armature, painted, guide):
    """Cut each sleeve and leg straight across where its painted area ends, walking out along the
    limb's bones from the body: the painted area sees a limb's sides edge-on, and cutting them by
    it tore slits and left the hems in teeth. Gives each cut limb's hem, in meters from its root."""
    segments = shaping.bone_segments(armature)
    hems = {}
    mesh = bmesh.new()
    mesh.from_mesh(shell.data)
    for chain in shaping.LIMBS:
        if any(bone not in segments for bone in chain):
            continue
        along = 0.0
        hem = None
        for bone in chain:
            head, tail = (np.asarray(p, dtype=float) for p in segments[bone])
            length = np.linalg.norm(tail - head)
            steps = np.arange(0, length, HEM_STEP)
            line = head + np.outer(steps / length, tail - head)
            covered = sample(painted, line, guide) >= 0.5
            if not covered.all():
                first = int(np.argmin(covered))
                hem = (bone, line[first], (tail - head) / length, along + steps[first])
                break
            along += length
        if hem is None:
            continue
        bone, point, direction, distance = hem
        beyond = shaping.with_beyond(armature, [bone])
        cut = bmesh.ops.bisect_plane(mesh, geom=mesh.verts[:] + mesh.edges[:] + mesh.faces[:],
                                     plane_co=Vector(point), plane_no=Vector(direction))
        middles = np.array([f.calc_center_median() for f in mesh.faces]).reshape(-1, 3)
        owner = shaping.owners(middles, segments)
        past = ((middles - point) @ direction > 0) & np.isin(owner, list(beyond))
        bmesh.ops.delete(mesh, geom=[f for f, out in zip(mesh.faces, past) if out],
                         context="FACES")
        hems[chain[-1]] = round(float(distance), 3)
    mesh.to_mesh(shell.data)
    mesh.free()
    return hems


def cut_by_field(shell, field):
    """Cut the shell along the line where a value per vertex crosses half, splitting the edges
    it crosses and joining the splits across their faces, and drop what lies under half. Gives
    how many faces were dropped."""
    mesh = bmesh.new()
    mesh.from_mesh(shell.data)
    layer = mesh.verts.layers.float.new("painted")
    mesh.verts.ensure_lookup_table()
    for vertex, value in zip(mesh.verts, field):
        vertex[layer] = value
    made = []
    for edge in [e for e in mesh.edges
                 if (e.verts[0][layer] - 0.5) * (e.verts[1][layer] - 0.5) < 0]:
        a, b = edge.verts
        share = (0.5 - a[layer]) / (b[layer] - a[layer])
        _, vertex = bmesh.utils.edge_split(edge, a, share)
        vertex[layer] = 0.5
        made.append(vertex)
    if made:
        bmesh.ops.connect_verts(mesh, verts=made, check_degenerate=True)
    drop = [f for f in mesh.faces if sum(v[layer] for v in f.verts) / len(f.verts) < 0.5]
    bmesh.ops.delete(mesh, geom=drop, context="FACES")
    bmesh.ops.delete(mesh, geom=[v for v in mesh.verts if not v.link_faces], context="VERTS")
    bmesh.ops.triangulate(mesh, faces=mesh.faces[:])
    mesh.to_mesh(shell.data)
    mesh.free()
    return len(drop)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    for name in ("--garment", "--body", "--painted", "--painted-back", "--guide", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--gap", type=float, required=True)
    parser.add_argument("--triangles", type=int, required=True)
    parser.add_argument("--reach", type=float)
    parser.add_argument("--cut", help="seam,above|below,offset (see shape_layer.py).")
    parser.add_argument("--slots", required=True, help="Comma-separated body slots the layer "
                        "covers, which the shell is made of.")
    parser.add_argument("--bare", default="", help="Unused: the slots decide what is covered.")
    parser.add_argument("--flap", help="width,lift: fold a flap back along the neck opening, "
                        "like a jacket's lapels and collar (see flaps.py).")
    parser.add_argument("--simplifier", help="Classpath for reducing (see SimplifyMesh.java).")
    parser.add_argument("--java", default="java")
    args = parser.parse_args(argv)

    (body, body_faces), _ = load_body(args.body)
    surface = tree(body, body_faces)
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    shell = skin_shell(set(args.slots.split(",")) - set(BARE))
    # Where the image painted the garment is read on the skin, before the shell moves out: a
    # jacket's shoulder pad pushes it past the painted outline, which cut a hole there
    with open(args.guide, encoding="utf-8") as file:
        guide = json.load(file)
    shell.data.update()
    points = shaping.points_of(shell)
    normals = np.empty(len(points) * 3)
    shell.data.vertices.foreach_get("normal", normals)
    front, back = read_mask(args.painted), read_mask(args.painted_back)
    field = painted_field(points, normals.reshape(-1, 3), front, back, guide)
    # Sleeves and legs are cut straight across at their hems instead
    field[limb_owned(points, armature)] = 1.0

    figure = import_figure(args.garment)
    cut = shaping.parse_cut(args.cut, args.body)
    gap, reach = shaping.tucked_band(points, cut, args.gap, args.reach)
    onto = conform(shell, figure, surface, gap, reach)
    bpy.data.objects.remove(figure)
    unpainted = cut_by_field(shell, field)
    hems = cut_limbs(shell, armature, blurred(front | back, CUT_BLUR), guide)
    cut_faces = shaping.cut_at_seam(shell, armature, *cut) if cut is not None else 0
    crumbs = shaping.drop_crumbs(shell)
    relaxed = shaping.relax_edges(shell)
    flap = 0
    if args.flap:
        import flaps
        flap = flaps.add_flap(shell, *(float(x) for x in args.flap.split(",")))
        shaping.relax_edges(shell)
    shell.data.calc_loop_triangles()
    built = len(shell.data.loop_triangles)
    triangles = built
    if built > args.triangles * (1 + OVER_BUDGET):
        triangles = shaping.reduce(shell, args.triangles, args.simplifier, args.java)
    gap, reach = shaping.tucked_band(shaping.points_of(shell), cut, args.gap, args.reach)
    shaping.shape(shell, surface, gap, reach, rounds=0, armature=armature)
    _, end = shaping.nearest(shaping.points_of(shell), surface)

    report = {
        "triangles_built": built,
        "triangles": triangles,
        "conformed_vertices": onto,
        "unpainted_faces": unpainted,
        "cut_faces": cut_faces,
        "hems_m": hems,
        "crumb_faces_dropped": crumbs,
        "relaxed_edge_vertices": relaxed,
        "flap_faces": flap,
        "min_gap_mm": round(float(end.min()) * 1000, 1),
        "max_gap_mm": round(float(end.max()) * 1000, 1),
    }
    print("[shell_layer]", json.dumps(report))
    with open(os.path.join(args.out, "shell.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)
    for polygon in shell.data.polygons:
        polygon.use_smooth = True
    bpy.ops.object.select_all(action="DESELECT")
    shell.select_set(True)
    bpy.ops.export_scene.gltf(filepath=os.path.join(args.out, "shaped.glb"),
                              export_format="GLB", use_selection=True)


if __name__ == "__main__":
    main()
