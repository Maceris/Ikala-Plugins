"""Carry a baked garment fitted to the male mannequin (see bake_layer.py) over to another body
type, through the base mesh cages (see tools/blender/body_cages.py).

The garment is bound to the male cage with a Surface Deform modifier, and the cage given the
other body's shape, so the garment follows it, keeping its vertices and texture layout, so the
same textures and tint mask fit; a garment shell_layer.py built from the mannequin's skin is
instead built afresh from this body's skin where the carried one covers it, with its own
textures and tint mask baked from the carried one (see shell_variant). Where the cage bunches up, like the armpits, the bust and the
crotch, faces fold over, and a folded face is culled when drawn, a hole: around those, the
garment takes its neighbors' move instead of its own (see untangle). It is then pushed out from the new body to the gap, and in to
the reach for a layer worn under others, as shape_layer.py does, without smoothing, and takes
that body's weights, as weight_layer.py does.

    blender -b --python variant_layer.py -- --baked <baked.glb> --body <female.blend>
        --from-cage <male_cage.npz> --to-cage <female_cage.npz> --gap 0.004 --name <id>
        --out <file.glb> --preview <folder> [--reach 0.012]

writes the garment skinned to the new body's rig, variant.json and renders preview_10.png and
preview_11.png in the preview folder: the garment on the new body from the front and back.
"""

import argparse
import json
import os
import sys

import bmesh
import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fit_accessory as hat  # noqa: E402
from bodies import body_meshes, nearest, tree, world_vertices  # noqa: E402
from fit_layer import render  # noqa: E402
from shape_layer import parse_cut, points_of, shape, tucked_band  # noqa: E402
from weight_layer import transfer_weights, weight_source  # noqa: E402

# The texture size a garment built afresh on this body bakes to
SHELL_TEXTURE = 1024
# How many times the share of a vertex the carried garment covers is averaged with its
# neighbors', so the cut runs smoothly
COVER_ROUNDS = 2
# How far bake rays reach from a garment built afresh to the carried one, as a share of its size:
# the two lie apart where the cut or the conform differ, more than a reshaped garment does
SHELL_BAKE_REACH = 0.03
# How far below red and blue a baked texel's green may fall before it counts as missed
MARKED = 0.15

# How many rings of neighbors around a folded face take their neighbors' move too
UNTANGLE_RINGS = 2
# How many times the moves are averaged over those, and how many times folds are looked for
# again after pushing (pushing can fold a face too)
UNTANGLE_ROUNDS = 30
UNTANGLE_PASSES = 8
# Faces still folded after that, as pushing folds the same slivers again, are collapsed into
# their shortest edge if smaller than this, in square meters
FOLD_AREA = 4e-4


def cage(name, data):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([tuple(p) for p in data["vertices"]], [],
                     [tuple(f) for f in data["faces"]])
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def import_garment(path, name):
    """A garment from a glb alone, without any skin or weights it had, named as given."""
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=os.path.abspath(path))
    imported = set(bpy.data.objects) - before
    # The biggest mesh: the glTF importer brings a small sphere along to draw bones with
    garment = max((o for o in imported if o.type == "MESH"), key=lambda o: len(o.data.vertices))
    world = garment.matrix_world.copy()
    garment.parent = None
    garment.matrix_world = world
    garment.modifiers.clear()
    garment.vertex_groups.clear()
    for obj in imported - {garment}:
        bpy.data.objects.remove(obj)
    hat.select_only(garment)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    garment.name = name
    garment.data.name = name
    return garment


def carry(garment, from_cage, to_cage):
    """Move a garment from one body to another with their cages. Surface Deform won't bind a
    mesh imported from glTF to a target ("No valid target mesh") though it binds the same
    geometry made afresh, so a plain copy with the same vertices is carried, and its vertices
    copied back."""
    points = np.empty(len(garment.data.vertices) * 3)
    garment.data.vertices.foreach_get("co", points)
    plain = cage("carried", {"vertices": points.reshape(-1, 3),
                             "faces": [tuple(p.vertices) for p in garment.data.polygons]})
    start = cage("cage", from_cage)
    deform = plain.modifiers.new("cage", "SURFACE_DEFORM")
    deform.target = start
    bpy.context.view_layer.update()
    with bpy.context.temp_override(object=plain, active_object=plain):
        bpy.ops.object.surfacedeform_bind(modifier=deform.name)
    if not deform.is_bound:
        raise SystemExit("The garment didn't bind to the cage")
    start.data.vertices.foreach_set("co", to_cage.astype(np.float32).ravel())
    start.data.update()
    moved, _ = world_vertices(plain)
    garment.data.vertices.foreach_set("co", moved.astype(np.float32).ravel())
    garment.data.update()
    bpy.data.objects.remove(plain)
    bpy.data.objects.remove(start)


def face_normals(obj):
    obj.data.update()
    normals = np.empty(len(obj.data.polygons) * 3)
    obj.data.polygons.foreach_get("normal", normals)
    return normals.reshape(-1, 3)


def untangle(obj, start, start_normals):
    """Unfold the faces that turned over since the garment lay at the start points: their
    vertices and some rings of neighbors (UNTANGLE_RINGS) move as their neighbors do, by
    averaging how far each moved, the rest held where they are. A patch moved alike keeps the
    start's shape, which had no folds. The glTF garment is split into separate vertices along
    its UV seams; those lying together move as one, or the seams would open. Gives how many
    faces were folded."""
    folded = (face_normals(obj) * start_normals).sum(1) < 0
    if not folded.any():
        return 0
    _, welded = np.unique(np.round(start, 6), axis=0, return_inverse=True)
    welded = welded.ravel()
    count = welded.max() + 1
    edges = welded[np.array([e.vertices[:] for e in obj.data.edges])]
    edges = np.unique(np.sort(edges[edges[:, 0] != edges[:, 1]], axis=1), axis=0)
    loose = np.zeros(count, dtype=bool)
    loose[welded[[v for k in np.flatnonzero(folded) for v in obj.data.polygons[k].vertices]]] = True
    for _ in range(UNTANGLE_RINGS):
        loose[edges[loose[edges].any(1)].ravel()] = True
    points = np.empty(len(start) * 3)
    obj.data.vertices.foreach_get("co", points)
    move = np.zeros((count, 3))
    move[welded] = points.reshape(-1, 3) - start
    neighbors = np.bincount(edges.ravel(), minlength=count)[:, None]
    for _ in range(UNTANGLE_ROUNDS):
        total = np.zeros_like(move)
        np.add.at(total, edges[:, 0], move[edges[:, 1]])
        np.add.at(total, edges[:, 1], move[edges[:, 0]])
        move[loose] = (total / np.maximum(neighbors, 1))[loose]
    obj.data.vertices.foreach_set("co", (start + move[welded]).astype(np.float32).ravel())
    obj.data.update()
    return int(folded.sum())


def collapse_folds(obj, start_normals):
    """Collapse the faces still folded over against the start (see untangle) and smaller than
    FOLD_AREA into their shortest edges, welding the glTF's UV-seam twins first so the seams
    stay closed. Gives how many were collapsed and how many folded faces are left."""
    folded = (face_normals(obj) * start_normals).sum(1) < 0
    small = folded & (np.array([p.area for p in obj.data.polygons]) < FOLD_AREA)
    if not small.any():
        return 0, int(folded.sum())
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    mesh.faces.ensure_lookup_table()
    shortest = {min(mesh.faces[i].edges, key=lambda e: e.calc_length())
                for i in np.flatnonzero(small)}
    bmesh.ops.remove_doubles(mesh, verts=mesh.verts[:], dist=1e-6)
    shortest = [e for e in shortest if e.is_valid]
    bmesh.ops.collapse(mesh, edges=shortest, uvs=True)
    mesh.to_mesh(obj.data)
    mesh.free()
    obj.data.update()
    return len(shortest), int(folded.sum() - small.sum())


def covered_field(shell, carried):
    """How much the carried garment covers each vertex of the bare skin shell, 0 or 1 averaged
    over neighbors (see COVER_ROUNDS): whether a ray out along its normal, from a little inside,
    finds the garment within the shell's reach."""
    import shell_layer
    from mathutils import Vector
    from mathutils.bvhtree import BVHTree
    garment = BVHTree.FromPolygons([carried.matrix_world @ v.co for v in carried.data.vertices],
                                   [tuple(p.vertices) for p in carried.data.polygons])
    shell.data.update()
    count = len(shell.data.vertices)
    points = points_of(shell)
    normals = np.empty(count * 3)
    shell.data.vertices.foreach_get("normal", normals)
    normals = normals.reshape(-1, 3)
    field = np.zeros(count)
    for i in range(count):
        start = Vector(points[i]) - Vector(normals[i]) * 0.01
        hit = garment.ray_cast(start, Vector(normals[i]), shell_layer.CONFORM_REACH + 0.01)
        field[i] = hit[0] is not None
    edges = np.empty(len(shell.data.edges) * 2, dtype=np.int64)
    shell.data.edges.foreach_get("vertices", edges)
    edges = edges.reshape(-1, 2)
    neighbors = np.maximum(np.bincount(edges.ravel(), minlength=count), 1)
    for _ in range(COVER_ROUNDS):
        total = np.zeros(count)
        np.add.at(total, edges[:, 0], field[edges[:, 1]])
        np.add.at(total, edges[:, 1], field[edges[:, 0]])
        field = (field + total / neighbors) / 2
    return field


def fill_missed(pixels, covered):
    """Give the texels a bake missed the value of the nearest covered one before them, in
    reading order, or after them for those at the start. Changes the pixels in place."""
    index = np.where(covered, np.arange(len(covered)), -1)
    index = np.maximum.accumulate(index)
    first = np.argmax(covered)
    index[index < 0] = first
    pixels[~covered] = pixels[index[~covered]]


def bake_mask(carried, shell, source, name, covered):
    """Bake a tint mask image through the carried garment onto the shell, its color channels
    and then its alpha (the fourth tint group), as an RGBA image."""
    tree = shell.data.materials[0].node_tree
    active = tree.nodes.active
    channels = []
    for output in ("Color", "Alpha"):
        hat.emit(carried, lambda t, bsdf, output=output: image_node(t, source).outputs[output])
        image = hat.new_image("%s_mask_%s" % (name, output), SHELL_TEXTURE, "Non-Color")
        node = hat.bake_into("EMIT", carried, shell, tree, image)
        tree.nodes.remove(node)
        channels.append(np.array(image.pixels[:]).reshape(-1, 4))
    tree.nodes.active = active
    pixels = np.concatenate([channels[0][:, :3], channels[1][:, :1]], axis=1)
    fill_missed(pixels, covered)
    mask = bpy.data.images.new("%s_emit_mask" % name, SHELL_TEXTURE, SHELL_TEXTURE, alpha=True)
    mask.colorspace_settings.name = "Non-Color"
    mask.pixels.foreach_set(pixels.ravel().astype(np.float32))
    return mask


def image_node(tree, image):
    node = tree.nodes.new("ShaderNodeTexImage")
    node.image = image
    return node


def shell_variant(args, carried, surface, armature, bodies, cut):
    """Build the garment afresh from this body's skin (see shell_layer.py) where the garment
    carried over from the other body covers it, rather than keep the carried mesh, which the
    cages fold at the shoulders and armpits, and bake its textures and tint mask from the
    carried one. Gives the new garment and its report."""
    import bake_layer
    import shell_layer
    shell = shell_layer.skin_shell(set(args.shell_slots.split(",")) - set(shell_layer.BARE))
    field = covered_field(shell, carried)
    gap, reach = tucked_band(points_of(shell), cut, args.gap, args.reach)
    shell_layer.conform(shell, carried, surface, gap, reach)
    uncovered = shell_layer.cut_by_field(shell, field)
    cut_faces = shell_layer.shaping.cut_at_seam(shell, armature, *cut) if cut is not None else 0
    shell_layer.shaping.drop_crumbs(shell)
    shell_layer.shaping.relax_edges(shell)
    if args.flap:
        import flaps
        flaps.add_flap(shell, *(float(x) for x in args.flap.split(",")))
        shell_layer.shaping.relax_edges(shell)
    if args.cuffs:
        import flaps
        flaps.add_flap(shell, *(float(x) for x in args.cuffs.split(",")), at="cuffs")
        shell_layer.shaping.relax_edges(shell)
    shell.data.calc_loop_triangles()
    if args.triangles and len(shell.data.loop_triangles) > args.triangles * (
            1 + shell_layer.OVER_BUDGET):
        shell_layer.shaping.reduce(shell, args.triangles, args.simplifier, args.java)
    gap, reach = tucked_band(points_of(shell), cut, args.gap, args.reach)
    shape(shell, surface, gap, reach, rounds=0, armature=armature)
    gap, _ = tucked_band(points_of(shell), cut, args.gap, args.reach)
    shell_layer.push_faces(shell, surface, gap)
    shell.name = args.name
    shell.data.name = args.name
    for polygon in shell.data.polygons:
        polygon.use_smooth = True

    hat.select_only(shell)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(island_margin=0.003)
    bpy.ops.object.mode_set(mode="OBJECT")
    hat.BAKE_REACH = SHELL_BAKE_REACH
    # The part's material slot names the garment's material; the carried one has that name
    for slot in carried.material_slots:
        if slot.material is not None:
            slot.material.name = args.name + "_carried"
    base, covered = hat.bake(carried, shell, args.name, SHELL_TEXTURE, bodies)
    # The bake's padding blends the missed texels' marker into those beside them: a garment's
    # recolorable base is near white, so a texel whose green falls well below its red and blue
    # took some of the marker
    color = np.array(base.pixels[:]).reshape(-1, 4)
    covered &= ~((color[:, 0] - color[:, 1] > MARKED) & (color[:, 2] - color[:, 1] > MARKED))
    for image in (base, bpy.data.images["%s_normal" % args.name]):
        pixels = np.array(image.pixels[:]).reshape(-1, 4)
        fill_missed(pixels, covered)
        image.pixels.foreach_set(pixels.ravel().astype(np.float32))
    bake_layer.face_out(bpy.data.images["%s_normal" % args.name])
    bake_layer.cloth_orm(bpy.data.images["%s_orm" % args.name], shell, bodies + [carried],
                         SHELL_TEXTURE)
    base.pack()
    if args.masks:
        # The tint mask, baked through the carried garment's own
        source = bpy.data.images.load(os.path.abspath(args.masks))
        source.colorspace_settings.name = "Non-Color"
        source.alpha_mode = "CHANNEL_PACKED"
        mask = bake_mask(carried, shell, source, args.name, covered)
        folder = os.path.join(args.preview, os.path.basename(os.path.splitext(args.out)[0])
                              + hat.MASK_FOLDER_SUFFIX)
        os.makedirs(folder, exist_ok=True)
        mask.filepath_raw = os.path.join(folder, os.path.basename(args.masks))
        mask.file_format = "PNG"
        mask.save()
    shell.data.calc_loop_triangles()
    return shell, {"built_afresh": True, "uncovered_faces": uncovered, "cut_faces": cut_faces,
                   "triangles": len(shell.data.loop_triangles),
                   "baked_pct": round(float(covered.mean()) * 100, 1)}


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    for name in ("--baked", "--body", "--from-cage", "--to-cage", "--name", "--out",
                 "--preview"):
        parser.add_argument(name, required=True)
    parser.add_argument("--gap", type=float, required=True)
    parser.add_argument("--reach", type=float)
    parser.add_argument("--bare", default="", help="Comma-separated bones the layer leaves bare.")
    parser.add_argument("--cut", help="The layer's cut (see shape_layer.py), to keep its tucked "
                        "part tucked on this body.")
    parser.add_argument("--shell-slots", help="Build the garment afresh from this body's skin in "
                        "these slots (see shell_variant), for a garment shell_layer.py built.")
    parser.add_argument("--masks", help="The tint mask image to bake onto a garment built afresh.")
    parser.add_argument("--triangles", type=int, help="The budget of a garment built afresh.")
    parser.add_argument("--flap", help="width,lift of its flap (see flaps.py).")
    parser.add_argument("--cuffs", help="width,lift of its cuffs (see flaps.py).")
    parser.add_argument("--simplifier", help="Classpath for reducing it (see SimplifyMesh.java).")
    parser.add_argument("--java", default="java")
    args = parser.parse_args(argv)
    cut = parse_cut(args.cut, args.body)

    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(args.body))
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    armature.animation_data_clear()
    armature.data.pose_position = "REST"
    bodies = body_meshes()
    garment = import_garment(args.baked, args.name)
    start, _ = world_vertices(garment)
    start_normals = face_normals(garment)
    carry(garment, np.load(args.from_cage), np.load(args.to_cage)["vertices"])

    surface_points, surface_faces = [], []
    offset = 0
    for obj in bodies:
        co, tris = world_vertices(obj)
        surface_points.append(co)
        surface_faces.append(tris + offset)
        offset += len(co)
    surface = tree(np.concatenate(surface_points), np.concatenate(surface_faces))
    if args.shell_slots:
        built, report = shell_variant(args, garment, surface, armature, bodies, cut)
        bpy.data.objects.remove(garment)
        source = weight_source(bodies)
        report["limb_weights_fixed"] = transfer_weights(
            built, source, armature, [b for b in args.bare.split(",") if b], bodies)
        bpy.data.objects.remove(source)
        hat.export(args.out, built, armature)
        _, end = nearest(world_vertices(built)[0], surface)
        report["min_gap_mm"] = round(float(end.min()) * 1000, 1)
        report["max_gap_mm"] = round(float(end.max()) * 1000, 1)
        print("[variant_layer]", json.dumps(report))
        os.makedirs(args.preview, exist_ok=True)
        with open(os.path.join(args.preview, "variant.json"), "w", encoding="utf-8") as file:
            json.dump(report, file, indent=1)
        return
    _, before_push = nearest(world_vertices(garment)[0], surface)
    folded = []
    for _ in range(UNTANGLE_PASSES):
        folded.append(untangle(garment, start, start_normals))
        gap, reach = tucked_band(points_of(garment), cut, args.gap, args.reach)
        pushed = shape(garment, surface, gap, reach, rounds=0, armature=armature)
        if not folded[-1]:
            break
    collapsed, left = collapse_folds(garment, start_normals)
    _, after_push = nearest(world_vertices(garment)[0], surface)

    source = weight_source(bodies)
    limb_fixed = transfer_weights(garment, source, armature,
                                  [b for b in args.bare.split(",") if b], bodies)
    bpy.data.objects.remove(source)
    hat.export(args.out, garment, armature)

    report = {
        "inside_before_pct": round(float((before_push < 0).mean()) * 100, 2),
        "pushed_pct": round(pushed / max(1, len(before_push)) * 100, 1),
        "min_gap_mm": round(float(after_push.min()) * 1000, 1),
        "max_gap_mm": round(float(after_push.max()) * 1000, 1),
        "folded_faces": folded,
        "folds_collapsed": collapsed,
        "folded_left": left,
        "limb_weights_fixed": limb_fixed,
    }
    print("[variant_layer]", json.dumps(report))
    os.makedirs(args.preview, exist_ok=True)
    with open(os.path.join(args.preview, "variant.json"), "w", encoding="utf-8") as file:
        json.dump(report, file, indent=1)
    for k, view in enumerate((0, 180)):
        render(os.path.join(args.preview, "preview_%d.png" % (k + 10)), view, "TEXTURE")


if __name__ == "__main__":
    main()
