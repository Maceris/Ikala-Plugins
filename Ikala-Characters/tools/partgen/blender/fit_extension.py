"""Fit a generated mesh onto one of the mannequin's extension chains, like a tail or a wing.

Run it headless with Blender 5 or later, which partgen.py does:

    blender -b --factory-startup --python fit_extension.py -- --mesh <generated.glb>
        --mannequin <mannequin.glb> --chain <tail|wing.L> --rule <json> --length <meters>
        --name <mesh name> --out <part.glb> [--preview <folder>] [--tints <json>]
        [--report <json>] [--mirror <other side's name>] [--chain-scale <times>]

The generated mesh is lined up with the chain: its middle line (the middles of slices across its
longest axis, or their top edges with the rule's "line": "top", like a wing's leading edge, or
their sides nearest the camera with "front", like a cape's back seen from behind)
runs from the end the rule's "root" direction points to (in TRELLIS.2's space: +X
the image's right, +Y up, +Z toward the viewer) out to the other, and is turned so that line lies
along the chain at rest and its front (TRELLIS.2's +Z) faces the way the guide camera looked from
(the rule's "view"). It is scaled so the line is the brief's --length long, which makes the chain
that many times the rig's: the part's chainLength. Then it is bent along the chain, each slice
moved from its place on the line to the same share of the way along the chain, so a curled tail
follows the chain's own curve; a flat part, like a wing, is only turned (the rule's "bend":
false). A part hanging off the body, like a cape, whose chain runs inside the body, is moved the
rule's "offset" meters toward the camera, and then pushed out to "clear" meters off the body
wherever it is nearer, like a collar over the shoulders; with "behind", what lies further toward
the body's front than that many meters past the chain is dropped first, like the front panels of
a cloak.

A low-poly copy is unwrapped and baked from it as accessories are (see fit_accessory.py): base
color, normals, ORM, and tint masks. Each vertex is weighted to the chain bone it lies along,
blended with the bone before or after it near a joint, and to the chain's root bone near its
start, so the springs and wing poses bend it smoothly. With --mirror, the other side's copy is
written too, mirrored across the body's middle and weighted to the other side's bones, beside
--out, named as it is with the other side's name in place of --name.

The report gives the chain length scale and how well the line fit.
"""
import argparse
import json
import math
import os
import pathlib
import shutil
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fit_accessory  # noqa: E402

# Slices taken across the mesh's longest axis for its middle line
SLICES = 32
# Slices at each end left out of the middle line, since an end's slice is a sliver of the tip
END_SLICES = 1
# With a rule's "line": "top", the highest share of each slice's points, in percent, whose middle
# is the line
TOP_SHARE = 10
# Near a joint, a vertex blends with the bone across it over this share of a bone's length
JOINT_BLEND = 0.25
# The directions guide cameras look along, in Blender's space (see render_guides.py)
LOOKS = {"front": (0, 1, 0), "back": (0, -1, 0), "left": (-1, 0, 0)}
# The texture size baked to, unless the rule says
TEXTURE = 1024
# Loose pieces whose boxes come within this share of the mesh's size count as one group (see
# keep_one)
TOUCHING = 0.02
# Faces whose texture is clearer than this, on average, are dropped before fitting: TRELLIS.2
# wraps fur in a nearly clear white shell, which would otherwise hide the fur's colors from the bake
CLEAR = 0.5
# Unless that would leave less than this share of the faces
CLEAR_KEEP = 0.1


def drop_clear(high):
    """Drop faces whose base color texture is mostly see-through, like the white shell TRELLIS.2
    wraps fur in, so the bake and the rebuilt surface see what is under it."""
    material = next((s.material for s in high.material_slots if s.material), None)
    image = None
    if material is not None and material.node_tree is not None:
        bsdf = next((n for n in material.node_tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
        # TRELLIS.2's glb is opaque, so its clearness is only in the base color image's alpha
        color = bsdf.inputs["Base Color"] if bsdf is not None else None
        source = color.links[0].from_node if color is not None and color.links else None
        image = getattr(source, "image", None)
    if image is None or not high.data.uv_layers:
        return
    width, height = image.size
    pixels = np.array(image.pixels[:], dtype=np.float32).reshape(height, width, 4)
    uv = np.empty(len(high.data.loops) * 2)
    high.data.uv_layers.active.data.foreach_get("uv", uv)
    uv = uv.reshape(-1, 2)
    columns = np.clip((uv[:, 0] % 1) * width, 0, width - 1).astype(int)
    rows = np.clip((uv[:, 1] % 1) * height, 0, height - 1).astype(int)
    loop_alpha = pixels[rows, columns, 3]
    starts = np.empty(len(high.data.polygons), dtype=np.int64)
    counts = np.empty(len(high.data.polygons), dtype=np.int64)
    high.data.polygons.foreach_get("loop_start", starts)
    high.data.polygons.foreach_get("loop_total", counts)
    face_alpha = np.add.reduceat(loop_alpha, starts) / counts
    clear = face_alpha < CLEAR
    if clear.sum() > len(clear) * (1 - CLEAR_KEEP) or not clear.any():
        print("[fit] %d of %d faces see-through; kept them all" % (clear.sum(), len(clear)))
        return
    mesh = bmesh.new()
    mesh.from_mesh(high.data)
    mesh.faces.ensure_lookup_table()
    bmesh.ops.delete(mesh, geom=[mesh.faces[i] for i in np.flatnonzero(clear)],
                     context="FACES")
    mesh.to_mesh(high.data)
    mesh.free()
    print("[fit] dropped %d of %d see-through faces" % (clear.sum(), len(clear)))


def chain_of(armature, plan_chain):
    """The chain's bones' names and its root's, and its joints at rest in world space: each
    bone's head, then the last bone's tip, as far past its head along the chain as the bone
    before is long."""
    bones = [armature.data.bones[name] for name in plan_chain["bones"]]
    world = armature.matrix_world
    joints = [np.array(world @ bone.head_local) for bone in bones]
    if len(joints) > 1:
        step = joints[-1] - joints[-2]
    else:
        step = np.array(world @ bones[0].tail_local) - joints[0]
    joints.append(joints[-1] + step)
    return [b.name for b in bones], plan_chain["root"], np.array(joints)


def polyline_lengths(points):
    """Each point's distance along a polyline from its start."""
    steps = np.linalg.norm(np.diff(points, axis=0), axis=1)
    return np.concatenate([[0.0], np.cumsum(steps)])


def along(points, lengths, distance):
    """The point at a distance along a polyline, and the segment it is on."""
    distance = float(np.clip(distance, 0, lengths[-1]))
    segment = int(np.clip(np.searchsorted(lengths, distance, side="right") - 1, 0,
                          len(points) - 2))
    span = max(lengths[segment + 1] - lengths[segment], 1e-9)
    t = (distance - lengths[segment]) / span
    return points[segment] + t * (points[segment + 1] - points[segment]), segment


def project(points, lengths, at):
    """For each point, its distance along a polyline where it is nearest, and that place."""
    best = np.full(len(at), np.inf)
    distance = np.zeros(len(at))
    nearest = np.zeros_like(at)
    for s in range(len(points) - 1):
        a, b = points[s], points[s + 1]
        d = b - a
        span = max(float(d @ d), 1e-12)
        t = np.clip((at - a) @ d / span, 0, 1)
        place = a + t[:, None] * d
        gap = np.linalg.norm(at - place, axis=1)
        closer = gap < best
        best[closer] = gap[closer]
        distance[closer] = lengths[s] + t[closer] * math.sqrt(span)
        nearest[closer] = place[closer]
    return distance, nearest


def middle_line(points, axis, top=None):
    """A mesh's middle line: the middles of slices across an axis, from one end to the other. With
    an upward direction, each slice's top edge instead, like a wing's leading edge, where its arm
    runs."""
    along_axis = points @ axis
    edges = np.linspace(along_axis.min(), along_axis.max(), SLICES + 1)
    middles = []
    for i in range(END_SLICES, SLICES - END_SLICES):
        inside = (along_axis >= edges[i]) & (along_axis <= edges[i + 1])
        if inside.sum() < 3:
            continue
        slice_points = points[inside]
        if top is not None:
            height = slice_points @ top
            slice_points = slice_points[height >= np.percentile(height, 100 - TOP_SHARE)]
        middles.append(slice_points.mean(0))
    return np.array(middles)


def frame(first, second):
    """A right-handed frame whose first axis is along first and second near second."""
    x = first / np.linalg.norm(first)
    y = second - (second @ x) * x
    if np.linalg.norm(y) < 1e-6:
        y = np.cross(x, [0, 0, 1]) if abs(x[2]) < 0.9 else np.cross(x, [1, 0, 0])
    y /= np.linalg.norm(y)
    return np.stack([x, y, np.cross(x, y)], axis=1)


def line_up(high, joints, rule, length):
    """Turn, scale, move and bend the generated mesh along the chain's joints, made length
    meters long. Gives the chain length scale and the middle line's fit, in meters."""
    # TRELLIS.2's glb comes in Blender's space: its +Y up is Blender's +Z, its +Z (toward the
    # viewer) Blender's -Y, and its +X stays
    points = fit_accessory.vertices(high)
    centered = points - points.mean(0)
    axis = np.linalg.svd(centered, full_matrices=False)[2][0]
    # Blender's +Z is TRELLIS.2's up, the picture's, and its -Y TRELLIS.2's toward the viewer
    edges = {"top": (0.0, 0.0, 1.0), "front": (0.0, -1.0, 0.0)}
    top = np.array(edges[rule["line"]]) if rule.get("line") in edges else None
    line = middle_line(points, axis, top)
    if len(line) < 3:
        raise SystemExit("The mesh is too small to find its middle line")
    root = np.array(rule.get("root", (0, 0, 0)), dtype=float)
    root = np.array([root[0], -root[2], root[1]])
    if root @ (line[0] - line[-1]) < 0:
        line = line[::-1]
    line_lengths = polyline_lengths(line)
    # A part only turned, like a wing, is measured root to tip: the line along a feathered or
    # plated edge zigzags from tip to tip, two or three times as long as the wing, which shrank it
    bend = rule.get("bend", True)
    span = line_lengths[-1] if bend else float(np.linalg.norm(line[-1] - line[0]))
    scale = length / span

    chain_lengths = polyline_lengths(joints)
    chain_scale = length / chain_lengths[-1]
    # The chain made the part's length, bone by bone from its head
    scaled = joints[0] + np.concatenate(
        [[np.zeros(3)], np.cumsum(np.diff(joints, axis=0) * chain_scale, axis=0)])

    # Turn: the line's root-to-tip along the chain's, and the mesh's front toward the camera
    look = np.array(LOOKS[rule.get("view", "front")], dtype=float)
    source = frame(line[-1] - line[0], np.array([0.0, -1.0, 0.0]))
    target = frame(scaled[-1] - scaled[0], -look)
    turn = target @ source.T
    # A part hanging off the body, like a cape, sits the rule's "offset" meters toward the camera
    # from the chain, which runs inside the body
    shift = -look * rule.get("offset", 0.0)
    moved = ((points - line[0]) * scale) @ turn.T + scaled[0] + shift
    line = ((line - line[0]) * scale) @ turn.T + scaled[0] + shift
    scaled = scaled + shift
    line_lengths = polyline_lengths(line)
    scaled_lengths = polyline_lengths(scaled)

    # Bend: each point moves as its place on the line moves to the same share along the chain.
    # A flat part, like a wing, whose line is noisy, is only turned (the rule's "bend": false)
    bent = moved.copy()
    if bend:
        distance, nearest = project(line, line_lengths, moved)
        share = distance / line_lengths[-1]
        for i in range(len(moved)):
            place, _ = along(scaled, scaled_lengths, share[i] * scaled_lengths[-1])
            bent[i] = moved[i] - nearest[i] + place
    misfit = [float(np.linalg.norm(along(scaled, scaled_lengths, s * scaled_lengths[-1])[0]
                                   - along(line, line_lengths, s * line_lengths[-1])[0]))
              for s in np.linspace(0, 1, 11)]
    high.data.vertices.foreach_set("co", bent.ravel().astype(np.float32))
    high.data.update()
    # The chain itself, without the offset, for weighting
    scaled = scaled - shift
    print("[fit] line %.3f m long, %.3f m root to tip, scaled x%.3f; chain x%.3f; line off the "
          "chain by %.3f m at most before bending"
          % (line_lengths[-1] / scale, span, scale, chain_scale, max(misfit)))
    return chain_scale, scaled, max(misfit)


def keep_one(high):
    """Keep only the biggest group of the mesh's loose pieces, by faces, where pieces whose boxes
    come within TOUCHING of each other group together: TRELLIS.2 sometimes makes a pair of wings
    from one, and a wing's feathers or plates are loose pieces of their own. Gives the mesh."""
    fit_accessory.select_only(high)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.mesh.separate(type="LOOSE")
    bpy.ops.object.mode_set(mode="OBJECT")
    pieces = list(bpy.context.selected_objects)
    boxes = []
    for piece in pieces:
        co = fit_accessory.vertices(piece)
        boxes.append((co.min(0), co.max(0)))
    size = max(float((b[1] - b[0]).max()) for b in boxes)
    margin = size * TOUCHING
    group = list(range(len(pieces)))

    def root(i):
        while group[i] != i:
            group[i] = group[group[i]]
            i = group[i]
        return i

    for i in range(len(pieces)):
        for j in range(i + 1, len(pieces)):
            low_i, high_i = boxes[i]
            low_j, high_j = boxes[j]
            if (low_i <= high_j + margin).all() and (low_j <= high_i + margin).all():
                group[root(i)] = root(j)
    faces = {}
    for i, piece in enumerate(pieces):
        faces[root(i)] = faces.get(root(i), 0) + len(piece.data.polygons)
    best = max(faces, key=faces.get)
    kept = [p for i, p in enumerate(pieces) if root(i) == best]
    for i, piece in enumerate(pieces):
        if root(i) != best:
            bpy.data.objects.remove(piece)
    print("[fit] kept %d of %d groups of loose pieces, %d of %d faces"
          % (1, len(faces), faces[best], sum(faces.values())))
    fit_accessory.select_only(*kept)
    if len(kept) > 1:
        bpy.ops.object.join()
    whole = bpy.context.view_layer.objects.active
    whole.name = "high"
    return whole


def keep_behind(high, chain, look, reach):
    """Drop faces further toward the front of the body than reach meters past the chain, like the
    front panels of a cloak TRELLIS.2 wrapped round a body it imagined: a cape hangs behind.
    Gives how many were dropped."""
    points = fit_accessory.vertices(high)
    lengths = polyline_lengths(chain)
    _, nearest = project(chain, lengths, points)
    behind = (points - nearest) @ -np.asarray(look, dtype=float)
    mesh = bmesh.new()
    mesh.from_mesh(high.data)
    mesh.faces.ensure_lookup_table()
    front = [face for face in mesh.faces
             if max(behind[v.index] for v in face.verts) < -reach]
    bmesh.ops.delete(mesh, geom=front, context="FACES")
    mesh.to_mesh(high.data)
    mesh.free()
    print("[fit] dropped %d faces in front of the body" % len(front))
    return len(front)


def keep_clear(high, mannequin, gap):
    """Push every vertex nearer the body than gap meters, or inside it, out to gap meters off
    its surface, like a cape's collar over the shoulders. Gives how many moved."""
    tree = fit_accessory.body_tree(mannequin)
    points = fit_accessory.vertices(high)
    moved = 0
    for i, point in enumerate(points):
        found, normal, _, _ = tree.find_nearest(Vector(point.tolist()))
        if found is None:
            continue
        out = Vector(point.tolist()) - found
        if out.dot(normal) < gap:
            points[i] = np.array(found + normal * gap)
            moved += 1
    high.data.vertices.foreach_set("co", points.ravel().astype(np.float32))
    high.data.update()
    print("[fit] pushed %d of %d vertices %.3f m clear of the body" % (moved, len(points), gap))
    return moved


def weight(low, bones, root, scaled):
    """Weight each vertex to the chain bone it lies along, blended with its neighbor near a
    joint, and with the root bone near the chain's start."""
    points = fit_accessory.vertices(low)
    lengths = polyline_lengths(scaled)
    distance, _ = project(scaled, lengths, points)
    groups = {name: low.vertex_groups.new(name=name) for name in [root] + bones}
    for i, d in enumerate(distance):
        _, bone = along(scaled, lengths, d)
        span = lengths[bone + 1] - lengths[bone]
        t = (d - lengths[bone]) / max(span, 1e-9)
        weights = {bones[bone]: 1.0}
        if t < JOINT_BLEND:
            before = bones[bone - 1] if bone > 0 else root
            share = 0.5 * (1 - t / JOINT_BLEND)
            weights = {before: share, bones[bone]: 1 - share}
        elif t > 1 - JOINT_BLEND and bone + 1 < len(bones):
            share = 0.5 * (1 - (1 - t) / JOINT_BLEND)
            weights = {bones[bone + 1]: share, bones[bone]: 1 - share}
        for name, w in weights.items():
            groups[name].add([i], w, "REPLACE")


def attach(low, armature):
    modifier = low.modifiers.new("Armature", "ARMATURE")
    modifier.object = armature
    world = low.matrix_world.copy()
    low.parent = armature
    low.matrix_parent_inverse = armature.matrix_world.inverted()
    low.matrix_world = world


def mirrored_copy(low, name):
    """The other side's copy: mirrored across the body's middle, its faces turned back out, and
    weighted to the other side's bones."""
    copy = low.copy()
    copy.data = low.data.copy()
    copy.name = copy.data.name = name
    bpy.context.scene.collection.objects.link(copy)
    copy.parent = None
    copy.modifiers.clear()
    points = fit_accessory.vertices(copy)
    points[:, 0] *= -1
    copy.data.vertices.foreach_set("co", points.ravel().astype(np.float32))
    mesh = bmesh.new()
    mesh.from_mesh(copy.data)
    bmesh.ops.reverse_faces(mesh, faces=mesh.faces)
    mesh.to_mesh(copy.data)
    mesh.free()
    for group in copy.vertex_groups:
        if group.name.endswith(".L"):
            group.name = group.name[:-2] + ".R"
        elif group.name.endswith(".R"):
            group.name = group.name[:-2] + ".L"
    return copy


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description="Fit a generated mesh along a chain.")
    parser.add_argument("--mesh", required=True)
    parser.add_argument("--mannequin", required=True)
    parser.add_argument("--plan", required=True, help="The body plan's JSON, for its chains.")
    parser.add_argument("--chain", required=True)
    parser.add_argument("--rule", required=True, help="The chain's rule, as JSON.")
    parser.add_argument("--length", type=float, help="Meters along the chain.")
    parser.add_argument("--chain-scale", type=float,
                        help="How many times the rig's chain it is long, instead of --length: "
                             "for another body's fit, which must match the first's chainLength.")
    parser.add_argument("--name", required=True, help="The part's mesh and material name.")
    parser.add_argument("--out", required=True)
    parser.add_argument("--preview", help="A folder for preview renders.")
    parser.add_argument("--tints", help="Where to write the default tint colors, as JSON.")
    parser.add_argument("--report", help="Where to write what the fit found, as JSON.")
    parser.add_argument("--mirror", help="The other side's mesh name, to write its copy too.")
    args = parser.parse_args(argv)
    rule = json.loads(args.rule)
    plan = json.loads(args.plan)
    plan_chain = next((c for c in plan["extensions"] if c["id"] == args.chain), None)
    if plan_chain is None:
        raise SystemExit("The plan has no chain " + args.chain)

    bpy.ops.wm.read_factory_settings(use_empty=True)
    added = fit_accessory.import_glb(args.mannequin)
    armature = next(obj for obj in added if obj.type == "ARMATURE")
    mannequin = [obj for obj in added if obj.type == "MESH"]
    bones, root, joints = chain_of(armature, plan_chain)
    if args.chain_scale:
        length = args.chain_scale * polyline_lengths(joints)[-1]
    elif args.length:
        length = args.length
    else:
        raise SystemExit("Give --length or --chain-scale")

    high = fit_accessory.generated_mesh(args.mesh)
    fit_accessory.weld(high)
    drop_clear(high)
    # Thin spikes, like the pin a pictured tail stands on, unless the part is thin itself
    if not rule.get("keepThin", False):
        fit_accessory.drop_needles(high)
    high = fit_accessory.drop_slivers(high)
    if rule.get("onePiece"):
        high = keep_one(high)
    chain_scale, scaled, misfit = line_up(high, joints, rule, length)
    if "behind" in rule:
        keep_behind(high, scaled, LOOKS[rule.get("view", "front")], rule["behind"])
    if rule.get("clear"):
        keep_clear(high, mannequin, rule["clear"])
    low = fit_accessory.low_poly(high, args.name, rule["triangles"])
    base, covered = fit_accessory.bake(high, low, args.name, rule.get("texture", TEXTURE),
                                       mannequin)
    out = pathlib.Path(args.out)
    defaults, original = fit_accessory.tint_masks(
        base, covered, out.with_name(out.stem + fit_accessory.MASK_FOLDER_SUFFIX))
    base.pack()
    weight(low, bones, root, scaled)
    attach(low, armature)
    out.parent.mkdir(parents=True, exist_ok=True)
    fit_accessory.export(str(out), low, armature)
    if args.mirror:
        other = mirrored_copy(low, args.mirror)
        attach(other, armature)
        # Named like --out with the other side's name, so each body's copy keeps its own file
        other_out = out.with_name(out.name.replace(args.name, args.mirror, 1))
        fit_accessory.export(str(other_out), other, armature)
        # Its tint masks are found beside its own file; mirroring kept the texture layout
        masks = out.with_name(out.stem + fit_accessory.MASK_FOLDER_SUFFIX)
        shutil.copytree(masks, other_out.with_name(other_out.stem
                                                   + fit_accessory.MASK_FOLDER_SUFFIX),
                        dirs_exist_ok=True)
        other.hide_render = True
    if args.tints:
        with open(args.tints, "w", encoding="utf8") as stream:
            json.dump(defaults, stream, indent=2)
    if args.report:
        with open(args.report, "w", encoding="utf8") as stream:
            json.dump({"chainLength": round(chain_scale, 3), "lineMisfit": round(misfit, 4),
                       "triangles": len(low.data.polygons)}, stream, indent=2)
    if args.preview:
        base.pixels.foreach_set(original.ravel().astype(np.float32))
        high.hide_render = True
        fit_accessory.preview(args.preview, low, mannequin)


if __name__ == "__main__":
    main()
