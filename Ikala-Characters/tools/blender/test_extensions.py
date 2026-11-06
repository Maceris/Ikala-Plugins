"""Make hand-made test parts for the biped's extension chains: a tail, a pair of wings and a cape,
built from simple shapes along the chains' bones and weighted to them, for trying chain parts in
the editor before generated ones exist.

The tail is made on a chain 1.3 times the rig's length (its part says "chainLength": [1.3]), so it
tests putting a longer mesh back on the rig's chain. Run once per mannequin; each writes
extensions/test_extensions_<variant>.glb beside the mannequins' folder:

    blender -b <test_content/characters>/mannequin/male.blend --python test_extensions.py
    blender -b <test_content/characters>/mannequin/female.blend --python test_extensions.py

Blender's axes: z is up, the character faces -y, so +y is behind it and +x is its left.
"""

import math
import pathlib

import bpy
from mathutils import Vector

# How much longer the test tail's chain is than the rig's
TAIL_LENGTH = 1.3
# The tail's thickness at its root and its tip, and the sides around it
TAIL_ROOT_RADIUS = 0.055
TAIL_TIP_RADIUS = 0.012
TAIL_SIDES = 8
# Rings along each bone, from its head
RINGS_PER_BONE = 3
# How far below each wing joint the membrane's trailing edge hangs, root to tip
WING_DROP = [0.45, 0.55, 0.5, 0.3, 0.08]
# The cape's width at the top and the bottom, its columns, and how far it hangs behind its chain
CAPE_TOP_WIDTH = 0.42
CAPE_BOTTOM_WIDTH = 0.62
CAPE_COLUMNS = 8
CAPE_BACK = 0.09
# Two-sided sheets are two layers this far apart, each facing out
SHEET_THICKNESS = 0.008
# Near a joint, vertices blend with the bone before over this share of the bone
JOINT_BLEND = 0.25

COLORS = {"tail": (0.55, 0.3, 0.15), "wing": (0.25, 0.22, 0.3), "cape": (0.6, 0.08, 0.1)}


def armature():
    return next(o for o in bpy.data.objects if o.type == "ARMATURE")


def joints(arm, names, scale=1.0):
    """The world positions of a chain's joints: each bone's head, then the last bone's tail, with
    every bone made `scale` times longer."""
    bones = [arm.data.bones[name] for name in names]
    points = [arm.matrix_world @ bones[0].head_local]
    for bone in bones:
        along = (arm.matrix_world @ bone.tail_local) - (arm.matrix_world @ bone.head_local)
        points.append(points[-1] + along * scale)
    return points


def joint_weights(names, root, index, t):
    """Weights for a point a share t along bone `index` of a chain: the bone itself, blended with
    the bone before it (or the chain's root) near its head."""
    bone = names[index]
    before = names[index - 1] if index > 0 else root
    if t >= JOINT_BLEND:
        return {bone: 1.0}
    share = 0.5 * (1 - t / JOINT_BLEND)
    return {before: share, bone: 1 - share}


def make_object(name, points, faces, weights, color):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([tuple(p) for p in points], [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    for vertex, weighted in enumerate(weights):
        for group_name, weight in weighted.items():
            group = obj.vertex_groups.get(group_name) or obj.vertex_groups.new(name=group_name)
            group.add([vertex], weight, "REPLACE")
    material = bpy.data.materials.new(name + "_material")
    material.diffuse_color = (*color, 1)
    nodes = material.node_tree.nodes if material.node_tree else None
    if nodes is not None:
        shader = nodes.get("Principled BSDF")
        if shader is not None:
            shader.inputs["Base Color"].default_value = (*color, 1)
            shader.inputs["Roughness"].default_value = 0.7
    mesh.materials.append(material)
    arm = armature()
    obj.parent = arm
    modifier = obj.modifiers.new("armature", "ARMATURE")
    modifier.object = arm
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    return obj


def frame(direction):
    """Two directions across a bone's direction."""
    side = direction.cross(Vector((0, 0, 1)))
    if side.length < 1e-4:
        side = direction.cross(Vector((1, 0, 0)))
    side.normalize()
    return side, direction.cross(side).normalized()


def tail(arm):
    names = ["tail.01", "tail.02", "tail.03", "tail.04", "tail.05", "tail.06"]
    points = joints(arm, names, TAIL_LENGTH)
    total = sum((points[i + 1] - points[i]).length for i in range(len(names)))
    verts, weights, rings = [], [], []
    walked = 0.0
    for index in range(len(names)):
        start, end = points[index], points[index + 1]
        direction = (end - start).normalized()
        side, up = frame(direction)
        for step in range(RINGS_PER_BONE):
            t = step / RINGS_PER_BONE
            center = start.lerp(end, t)
            share = (walked + (end - start).length * t) / total
            radius = TAIL_ROOT_RADIUS + (TAIL_TIP_RADIUS - TAIL_ROOT_RADIUS) * share
            ring = []
            for k in range(TAIL_SIDES):
                angle = 2 * math.pi * k / TAIL_SIDES
                ring.append(len(verts))
                verts.append(center + (side * math.cos(angle) + up * math.sin(angle)) * radius)
                weights.append(joint_weights(names, "hips", index, t))
            rings.append(ring)
        walked += (end - start).length
    tip = len(verts)
    verts.append(points[-1])
    weights.append({names[-1]: 1.0})
    faces = []
    for a, b in zip(rings, rings[1:]):
        for k in range(TAIL_SIDES):
            n = (k + 1) % TAIL_SIDES
            faces.append((a[k], a[n], b[n], b[k]))
    last = rings[-1]
    for k in range(TAIL_SIDES):
        faces.append((last[k], last[(k + 1) % TAIL_SIDES], tip))
    # The root ring is left open: it sits inside the body
    return make_object("test_tail", verts, faces, weights, COLORS["tail"])


def sheet(name, rows, row_weights, color):
    """A two-sided sheet through rows of points: two layers, each facing out, so it shows from
    both sides with back faces culled."""
    verts, weights, faces = [], [], []
    columns = len(rows[0])
    for offset in (SHEET_THICKNESS / 2, -SHEET_THICKNESS / 2):
        base = len(verts)
        for r, row in enumerate(rows):
            # Across the row and down the rows give the sheet's facing
            across = row[-1] - row[0]
            down = (rows[min(r + 1, len(rows) - 1)][0] - rows[max(r - 1, 0)][0])
            normal = across.cross(down).normalized()
            for c, point in enumerate(row):
                verts.append(point + normal * offset)
                weights.append(row_weights[r][c])
        for r in range(len(rows) - 1):
            for c in range(columns - 1):
                a = base + r * columns + c
                quad = (a, a + 1, a + columns + 1, a + columns)
                faces.append(quad if offset > 0 else tuple(reversed(quad)))
    return make_object(name, verts, faces, weights, color)


def wing(arm, side):
    names = [f"wing.0{i}.{side}" for i in range(1, 5)]
    points = joints(arm, names)
    leading, trailing, row_weights = [], [], []
    for index, point in enumerate(points):
        leading.append(point)
        trailing.append(point - Vector((0, 0, WING_DROP[index])))
        bone = min(index, len(names) - 1)
        weight = joint_weights(names, "chest", bone, 0.0 if index < len(names) else 1.0)
        row_weights.append([weight, weight])
    rows = [[lead, trail] for lead, trail in zip(leading, trailing)]
    if side == "R":
        rows = [list(reversed(row)) for row in rows]
    return sheet(f"test_wing_{side.lower()}", rows, row_weights, COLORS["wing"])


def cape(arm):
    names = ["cape.01", "cape.02", "cape.03", "cape.04"]
    points = joints(arm, names)
    chest = arm.matrix_world @ arm.data.bones["chest"].head_local
    rows, row_weights = [], []
    # The collar rides on the shoulders, in front of the chain's top
    collar_z = points[0].z + 0.08
    collar = [Vector((x, chest.y + 0.05, collar_z))
              for x in spread(CAPE_TOP_WIDTH * 0.85, CAPE_COLUMNS)]
    rows.append(collar)
    row_weights.append([{"chest": 1.0}] * CAPE_COLUMNS)
    count = len(points)
    for index, point in enumerate(points):
        share = index / (count - 1)
        width = CAPE_TOP_WIDTH + (CAPE_BOTTOM_WIDTH - CAPE_TOP_WIDTH) * share
        back = point.y + CAPE_BACK
        rows.append([Vector((x, back + 0.04 * (x / width) ** 2, point.z))
                     for x in spread(width, CAPE_COLUMNS)])
        bone = min(index, len(names) - 1)
        if index == 0:
            weight = {"chest": 0.6, names[0]: 0.4}
        else:
            weight = joint_weights(names, "chest", bone, 0.0 if index < len(names) else 1.0)
        row_weights.append([weight] * CAPE_COLUMNS)
    # Rows run left to right as seen from behind, so the outer layer faces back
    rows = [list(reversed(row)) for row in rows]
    return sheet("test_cape", rows, row_weights, COLORS["cape"])


def spread(width, count):
    return [-width / 2 + width * i / (count - 1) for i in range(count)]


def export(path, objects):
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects:
        obj.select_set(True)
    arm = armature()
    arm.select_set(True)
    bpy.context.view_layer.objects.active = arm
    options = {
        "filepath": str(path),
        "export_format": "GLB",
        "use_selection": True,
        "export_yup": True,
        "export_skins": True,
        "export_def_bones": False,
        "export_animations": False,
        "export_colors": False,
        "export_extras": False,
        "export_tangents": True,
    }
    known = {p.identifier for p in bpy.ops.export_scene.gltf.get_rna_type().properties}
    bpy.ops.export_scene.gltf(**{key: value for key, value in options.items() if key in known})
    print("wrote", path)


def main():
    blend = pathlib.Path(bpy.data.filepath)
    variant = blend.stem
    arm = armature()
    # Mannequin files are saved with a clip playing; the parts are made at rest
    arm.animation_data_clear()
    arm.data.pose_position = "REST"
    made = [tail(arm), wing(arm, "L"), wing(arm, "R"), cape(arm)]
    out = blend.parent.parent / "extensions" / f"test_extensions_{variant}.glb"
    out.parent.mkdir(parents=True, exist_ok=True)
    export(out, made)


main()
