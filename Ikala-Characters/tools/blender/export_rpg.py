"""Export one of the modular RPG characters as glTF, scaled to a standing height in meters.

The source files are System G6's modular RPG characters (credit optional), whose meshes and
baked clips share one skeleton. Run it headless with Blender 3.3 or later:

    blender -b human_male.blend --python export_rpg.py -- --out <folder> --name rpg_male

What it changes on the way out, in memory only (the .blend is never saved):

- Every collection and object is made visible, so every part is exported.
- Meshes weighted to bones but missing an armature modifier get one, and every mesh is
  parented to the armature, so all of them export as skinned parts.
- Everything is scaled so the default body stands --height meters tall, and the clips'
  translation keys are scaled with it, since applying a scale doesn't touch actions.
- Each clip goes on its own NLA track, which the exporter writes as one animation each.
- Materials from Blender 2.79, which newer versions load without their textures, get a node
  tree with their texture as the base color, found by the pack's naming in --textures.
"""

import argparse
import os
import sys

import bpy

# Meshes whose height sets the scale, by the end of their names
BODY_PARTS = ("_body_default", "_head_default", "_feet_default")


def visible_everything():
    """Show every collection and object, since hidden ones aren't exported."""
    def walk(layer):
        layer.exclude = False
        layer.hide_viewport = False
        for child in layer.children:
            walk(child)

    walk(bpy.context.view_layer.layer_collection)
    for collection in bpy.data.collections:
        collection.hide_viewport = False
        collection.hide_render = False
    for obj in bpy.context.scene.objects:
        obj.hide_set(False)
        obj.hide_viewport = False
        obj.hide_render = False


def find_armature():
    armatures = [o for o in bpy.context.scene.objects if o.type == "ARMATURE"]
    if len(armatures) != 1:
        raise SystemExit("Expected one armature, found %d" % len(armatures))
    return armatures[0]


def skin_meshes(armature):
    """Give every mesh weighted to the armature's bones a modifier and the armature as parent."""
    bones = {bone.name for bone in armature.data.bones}
    meshes = []
    for obj in bpy.context.scene.objects:
        if obj.type != "MESH":
            continue
        if not any(group.name in bones for group in obj.vertex_groups):
            print("Skipping", obj.name, "which isn't weighted to the skeleton")
            continue
        if not any(m.type == "ARMATURE" for m in obj.modifiers):
            modifier = obj.modifiers.new("Armature", "ARMATURE")
            modifier.object = armature
            print("Added an armature modifier to", obj.name)
        if obj.parent != armature:
            world = obj.matrix_world.copy()
            obj.parent = armature
            obj.matrix_parent_inverse = armature.matrix_world.inverted()
            obj.matrix_world = world
        meshes.append(obj)
    return meshes


def standing_height(meshes):
    """The height from the lowest to the highest vertex of the default body parts."""
    low, high = float("inf"), float("-inf")
    for obj in meshes:
        if not obj.name.endswith(BODY_PARTS):
            continue
        for vertex in obj.data.vertices:
            z = (obj.matrix_world @ vertex.co).z
            low = min(low, z)
            high = max(high, z)
    if high <= low:
        raise SystemExit("Couldn't find the default body parts to measure")
    return high - low


def apply_scale(armature, meshes, factor):
    """Scale everything by a factor and apply it, then scale the clips' translations to match."""
    armature.scale = [s * factor for s in armature.scale]
    bpy.context.view_layer.update()
    bpy.ops.object.select_all(action="DESELECT")
    armature.select_set(True)
    for obj in meshes:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = armature
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    for action in bpy.data.actions:
        for curve in action.fcurves:
            if curve.data_path.endswith(".location"):
                for key in curve.keyframe_points:
                    key.co[1] *= factor
                    key.handle_left[1] *= factor
                    key.handle_right[1] *= factor


def stash(armature):
    """Put each clip on its own NLA track, named after the clip without the character's prefix."""
    data = armature.animation_data or armature.animation_data_create()
    data.action = None
    prefix = armature.name + "_"
    for action in sorted(bpy.data.actions, key=lambda a: a.name):
        name = action.name[len(prefix):] if action.name.startswith(prefix) else action.name
        track = data.nla_tracks.new()
        track.name = name
        track.strips.new(name, int(action.frame_range[0]), action)


def texture_file(material_name, folder):
    """Find a material's texture by the pack's naming: mat_X uses tex_X.png, skirts their armor's,
    and some male parts the female texture."""
    base = material_name[len("mat_"):] if material_name.startswith("mat_") else material_name
    if base.endswith("_skirt"):
        base = base[: -len("_skirt")]
    for candidate in (base, base.replace("human_male", "human_female")):
        path = os.path.join(folder, "tex_" + candidate + ".png")
        if os.path.exists(path):
            return path
    return None


def texture_materials(folder):
    """Give old materials, which 2.79 textured through slots that newer versions drop, a node
    tree with their texture as the base color, so the exporter writes it."""
    for material in bpy.data.materials:
        tree = material.node_tree if material.use_nodes else None
        bsdf = None
        if tree is not None:
            bsdf = next((n for n in tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
            if bsdf is not None and bsdf.inputs["Base Color"].is_linked:
                continue
        path = texture_file(material.name, folder)
        if path is None:
            print("No texture found for", material.name)
            continue
        material.use_nodes = True
        tree = material.node_tree
        bsdf = next((n for n in tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
        if bsdf is None:
            bsdf = tree.nodes.new("ShaderNodeBsdfPrincipled")
            output = next(n for n in tree.nodes if n.type == "OUTPUT_MATERIAL")
            tree.links.new(bsdf.outputs["BSDF"], output.inputs["Surface"])
        image = tree.nodes.new("ShaderNodeTexImage")
        image.image = bpy.data.images.load(path, check_existing=True)
        tree.links.new(image.outputs["Color"], bsdf.inputs["Base Color"])
        bsdf.inputs["Roughness"].default_value = 0.8
        bsdf.inputs["Specular"].default_value = 0.3
        print("Textured", material.name, "with", os.path.basename(path))


def export(path):
    options = {
        "filepath": path,
        "export_format": "GLB",
        "export_yup": True,
        "export_skins": True,
        "export_def_bones": False,
        "export_animations": True,
        "export_nla_strips": True,
        "export_force_sampling": True,
        "export_frame_range": False,
        "export_colors": False,
        "export_extras": False,
        "export_image_format": "AUTO",
    }
    known = {p.identifier for p in bpy.ops.export_scene.gltf.get_rna_type().properties}
    bpy.ops.export_scene.gltf(**{key: value for key, value in options.items() if key in known})


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description="Export an RPG character as glTF.")
    parser.add_argument("--out", required=True, help="The folder to write to.")
    parser.add_argument("--name", required=True, help="The file name, without .glb.")
    parser.add_argument("--height", type=float, default=2.0, help="Standing height in meters.")
    parser.add_argument(
        "--textures",
        help="The pack's textures folder, for materials without nodes. Defaults to the textures"
        " folder beside the .blend file.")
    args = parser.parse_args(argv)
    textures = args.textures or os.path.join(os.path.dirname(bpy.data.filepath), "textures")

    visible_everything()
    armature = find_armature()
    meshes = skin_meshes(armature)
    before = standing_height(meshes)
    apply_scale(armature, meshes, args.height / before)
    print("Scaled from %.3f to %.3f units tall" % (before, standing_height(meshes)))
    stash(armature)
    texture_materials(textures)

    out = os.path.abspath(args.out)
    os.makedirs(out, exist_ok=True)
    path = os.path.join(out, args.name + ".glb")
    export(path)
    print("Wrote", path)


main()
