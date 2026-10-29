"""Export the modular RPG characters' weapons as glTF meshes for held items.

The weapons live in System G6's IK rig file (animations_bake_human_male.blend), each parented to a
weapon socket bone (wep_pos_L or wep_pos_R). Run it headless with Blender 3.3 or later:

    blender -b animations_bake_human_male.blend --python export_weapons.py -- --out <folder>

Each weapon is written with its grip at the origin, its blade, shaft or limbs along +Y and its
flat side facing +Z, like the one-handed sword sits on its socket bone in the pack, scaled like the
characters (see export_rpg.py), and skinned to a single bone named grip, so the character importer
reads it like any part. The pack places the other weapons wherever suited the poses they were made
for, often well away from the hand, so each is moved by a grip and tip measured in the sword's
frame (GRIPS). The pack has no weapon textures, so each gets a plain color. The .blend is never
saved.
"""

import argparse
import os
import sys

import math

import bpy
from mathutils import Matrix, Vector

# The body mesh whose height sets the scale, as export_rpg.py's default body parts do
BODY = "human_male_body_WIP"

# Plain colors by weapon, linear RGB, with metalness
COLORS = {
    "main_hand_sword": ((0.55, 0.57, 0.6), 0.9),
    "two_handed_sword": ((0.5, 0.52, 0.56), 0.9),
    "shield": ((0.35, 0.18, 0.08), 0.0),
    "bow": ((0.3, 0.16, 0.06), 0.0),
    "crossbow": ((0.28, 0.15, 0.06), 0.0),
    "staff": ((0.25, 0.13, 0.05), 0.0),
    "wand": ((0.6, 0.45, 0.15), 0.6),
    "grimoire": ((0.25, 0.05, 0.05), 0.0),
}

# Where each weapon is held and which way it points, in glTF axes in its socket bone's space, after
# scaling: (grip, tip). Measured from each mesh's long axis; see the module comment.
GRIPS = {
    "main_hand_sword": ((0, 0, 0), (0.05, 0.8, 0)),
    "two_handed_sword": ((-0.38, 0.1, 0.68), (-1.86, 0.7, 0.23)),
    "staff": ((-0.22, 0.02, 0.74), (-0.83, 0.27, 0.55)),
    "crossbow": ((-0.31, 0.43, -0.1), (-0.31, 0.43, 0.67)),
    "bow": ((0.43, 0.45, 0.04), (0.3, 0.47, 0.87)),
    "shield": ((0.4, 0.45, -0.34), (0.55, 0.72, -0.08)),
    "grimoire": ((0.46, 0.56, 0.02), (0.43, 0.57, 0.16)),
    "wand": ((-0.38, 0.43, 0.02), (-0.32, 0.43, 0.38)),
}

# Blender's z-up axes to glTF's y-up ones are (x, y, z) -> (x, z, -y); this undoes that, so a
# point at (x, y, z) in a bone's space comes out of the exporter at (x, y, z).
TO_EXPORTER = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))


def weapon_name(obj):
    """A weapon's name without the pack's suffixes, like main_hand_sword."""
    name = obj.name.split(".")[0]
    for suffix in ("_old", "_2"):
        if name.endswith(suffix):
            name = name[: -len(suffix)]
    return name


def gltf_points(mesh):
    """A mesh's vertices in glTF axes, from the Blender coordinates TO_EXPORTER made."""
    return [Vector((v.co.x, v.co.z, -v.co.y)) for v in mesh.vertices]


def thinnest(points, along):
    """The direction across an axis that the points spread least along."""
    side = along.orthogonal().normalized()
    other = along.cross(side).normalized()
    middle = sum(points, Vector()) / len(points)
    best, best_spread = side, float("inf")
    for step in range(180):
        angle = math.pi * step / 180
        direction = side * math.cos(angle) + other * math.sin(angle)
        spread = sum(((p - middle).dot(direction)) ** 2 for p in points)
        if spread < best_spread:
            best, best_spread = direction, spread
    return best


def to_grip(name, mesh):
    """The matrix, in Blender axes, moving a weapon from its socket bone's space to its grip's:
    grip at the origin, tip along +Y, flat side along +Z."""
    grip, tip = (Vector(v) for v in GRIPS[name])
    up = (tip - grip).normalized()
    flat = thinnest(gltf_points(mesh), up)
    flat = (flat - up * flat.dot(up)).normalized()
    side = up.cross(flat)
    rotation = Matrix((side, up, flat)).to_4x4()
    in_gltf = rotation @ Matrix.Translation(-grip)
    return TO_EXPORTER @ in_gltf @ TO_EXPORTER.inverted()


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description="Export the RPG weapons as glTF.")
    parser.add_argument("--out", required=True, help="The folder to write to.")
    parser.add_argument("--name", default="weapons", help="The file name, without .glb.")
    parser.add_argument("--height", type=float, default=2.0, help="The body's height in meters.")
    args = parser.parse_args(argv)

    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    armature.data.pose_position = "REST"
    bpy.context.view_layer.update()
    body = bpy.data.objects[BODY]
    heights = [(body.matrix_world @ v.co).z for v in body.data.vertices]
    scale = args.height / (max(heights) - min(heights))
    print("Scaling by %.4f" % scale)

    weapons = {}
    for obj in bpy.data.objects:
        if obj.type != "MESH" or not obj.parent_bone.startswith("wep_pos"):
            continue
        name = weapon_name(obj)
        if name in weapons:
            continue
        bone = armature.data.bones[obj.parent_bone]
        to_bone = (armature.matrix_world @ bone.matrix_local).inverted() @ obj.matrix_world
        mesh = obj.data.copy()
        mesh.transform(TO_EXPORTER @ Matrix.Scale(scale, 4) @ to_bone)
        if name in GRIPS:
            mesh.transform(to_grip(name, mesh))
        else:
            print("No grip for", name, "so it stays where the pack put it")
        mesh.materials.clear()
        material = bpy.data.materials.new("weapon_" + name)
        material.use_nodes = True
        bsdf = next(n for n in material.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
        color, metal = COLORS.get(name, ((0.5, 0.5, 0.5), 0.0))
        bsdf.inputs["Base Color"].default_value = (*color, 1)
        bsdf.inputs["Metallic"].default_value = metal
        bsdf.inputs["Roughness"].default_value = 0.4 if metal > 0 else 0.8
        mesh.materials.append(material)
        weapons[name] = mesh
        print("Weapon", name, "from", obj.name, "on", obj.parent_bone)

    # Start from an empty file, so only the weapons and their bone are exported
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj)
    scene = bpy.context.scene
    grip_data = bpy.data.armatures.new("grip")
    rig = bpy.data.objects.new("weapons", grip_data)
    scene.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode="EDIT")
    bone = grip_data.edit_bones.new("grip")
    bone.head = (0, 0, 0)
    bone.tail = (0, 0.1, 0)
    bpy.ops.object.mode_set(mode="OBJECT")
    for name, mesh in weapons.items():
        obj = bpy.data.objects.new(name, mesh)
        mesh.name = name
        scene.collection.objects.link(obj)
        obj.parent = rig
        group = obj.vertex_groups.new(name="grip")
        group.add(range(len(mesh.vertices)), 1.0, "REPLACE")
        modifier = obj.modifiers.new("skin", "ARMATURE")
        modifier.object = rig

    out = os.path.abspath(args.out)
    os.makedirs(out, exist_ok=True)
    path = os.path.join(out, args.name + ".glb")
    options = {
        "filepath": path,
        "export_format": "GLB",
        "export_yup": True,
        "export_skins": True,
        "export_animations": False,
        "export_colors": False,
        "export_extras": False,
    }
    known = {p.identifier for p in bpy.ops.export_scene.gltf.get_rna_type().properties}
    bpy.ops.export_scene.gltf(**{key: value for key, value in options.items() if key in known})
    print("Wrote", path)


main()
