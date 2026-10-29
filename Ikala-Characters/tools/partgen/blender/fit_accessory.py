"""Fit a generated mesh onto the mannequin as a rigid accessory on one socket.

Run it headless with Blender 3.3 or later, which partgen.py does:

    blender -b --factory-startup --python fit_accessory.py -- --mesh <generated.glb>
        --mannequin <mannequin.glb> --bone <socket bone> --rule <json> --name <mesh name>
        --out <part.glb> --preview <folder>

The generated mesh is cleaned of thin slivers, sized and placed by the socket type's rule, then a
low-poly copy is unwrapped and baked from it: base color and a normal map. The copy is weighted
entirely to the socket's bone, so it moves rigidly with it, and written with the mannequin's
armature so it loads onto the rig like any other part. Metallic and roughness are averaged into
factors for now. The preview folder gets front and side renders on the mannequin.
"""
import argparse
import json
import math
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Vector

# A part of the generated mesh this thin across, and this many times longer, is a sliver
SLIVER_THICKNESS = 0.003
SLIVER_ASPECT = 10
# Fewer faces than this is a speck
SPECK_FACES = 12
# Slices the mesh is cut into to find its crown, and the points a slice needs to count
CROWN_SLICES = 40
SLICE_POINTS = 30
# The brim is looked for in this lower fraction of the height, and the crown measured in this
# band above it, as fractions of the height
BRIM_REGION = 0.4
CROWN_BAND = (0.08, 0.35)
# Bake rays reach this far from the low-poly surface, as a fraction of the part's size
BAKE_REACH = 0.08
# Voxels across the part's largest side when remeshing it closed before decimating
REMESH_VOXELS = 160
DECIMATE_PASSES = 10


def vertices(obj):
    """An object's vertices in world space, as an n by 3 array."""
    count = len(obj.data.vertices)
    co = np.empty(count * 3)
    obj.data.vertices.foreach_get("co", co)
    co = co.reshape(-1, 3)
    m = np.array(obj.matrix_world)
    return co @ m[:3, :3].T + m[:3, 3]


def select_only(*objects):
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = objects[0]


def import_glb(path):
    """Import a glb and give the objects it added."""
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    return [obj for obj in bpy.data.objects if obj not in before]


def generated_mesh(path):
    """The generated glb as one mesh object, with its transforms applied."""
    meshes = [obj for obj in import_glb(path) if obj.type == "MESH"]
    for obj in meshes:
        select_only(obj)
        bpy.ops.object.parent_clear(type="CLEAR_KEEP_TRANSFORM")
    select_only(*meshes)
    if len(meshes) > 1:
        bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    obj.name = "high"
    return obj


def weld(obj):
    """Join vertices TRELLIS.2 split along its texture seams; UVs stay, since Blender keeps them
    per face corner."""
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    before = len(bm.verts)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    print("[fit] welded %d vertices to %d" % (before, len(bm.verts)))
    bm.to_mesh(obj.data)
    bm.free()


def drop_slivers(obj):
    """Remove loose parts that are needles, sheets or specks, and give the mesh back whole."""
    select_only(obj)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.mesh.separate(type="LOOSE")
    bpy.ops.object.mode_set(mode="OBJECT")
    pieces = list(bpy.context.selected_objects)
    kept = []
    for piece in pieces:
        co = vertices(piece)
        size = co.max(0) - co.min(0)
        thin = size.min() < SLIVER_THICKNESS and size.max() > SLIVER_ASPECT * size.min()
        if thin or len(piece.data.polygons) < SPECK_FACES:
            bpy.data.objects.remove(piece)
        else:
            kept.append(piece)
    print("[fit] kept %d of %d loose parts" % (len(kept), len(pieces)))
    select_only(*kept)
    if len(kept) > 1:
        bpy.ops.object.join()
    whole = bpy.context.view_layer.objects.active
    whole.name = "high"
    return whole


def width_at(points, z, band):
    """Side to side width, and center, of the points within band of a height, or None if too few
    are there. The extremes are left out, so a stray point can't widen it."""
    near = points[np.abs(points[:, 2] - z) < band]
    if len(near) < SLICE_POINTS:
        return None
    left, right = np.percentile(near[:, 0], [1, 99])
    lo, hi = np.percentile(near, [1, 99], axis=0)
    return right - left, (lo + hi) / 2


def section(obj, z):
    """Where an object's edges cross a height, as points; works on meshes with few vertices."""
    co = vertices(obj)
    count = len(obj.data.edges)
    ends = np.empty(count * 2, dtype=np.int64)
    obj.data.edges.foreach_get("vertices", ends)
    a, b = co[ends[0::2]], co[ends[1::2]]
    crossing = (a[:, 2] - z) * (b[:, 2] - z) <= 0
    a, b = a[crossing], b[crossing]
    dz = b[:, 2] - a[:, 2]
    t = np.where(np.abs(dz) > 1e-9, (z - a[:, 2]) / np.where(dz == 0, 1, dz), 0.5)
    return a + (b - a) * t[:, None]


def measure_crown(points, low, height):
    """The crown of headwear: the band just above the brim, where the head goes. The brim is the
    widest slice of the lower part (the bottom edge of a brimless hat); the crown's width is the
    median across the band above it, so a stray drooping edge can't decide it. Gives the width,
    the center, and the height below which lies the brim."""
    band = height / CROWN_SLICES
    slices = []
    for k in range(CROWN_SLICES):
        z = low + band * (k + 0.5)
        found = width_at(points, z, band / 2)
        if found is not None:
            slices.append(found + (z,))
    lower = [s for s in slices if s[2] < low + height * BRIM_REGION]
    brim_z = max(lower, key=lambda s: s[0])[2]
    above = [s for s in slices
             if brim_z + height * CROWN_BAND[0] <= s[2] <= brim_z + height * CROWN_BAND[1]]
    if not above:
        above = [s for s in slices if s[2] >= brim_z]
    width = float(np.median([s[0] for s in above]))
    center = np.mean([s[1] for s in above], axis=0)
    return width, center, brim_z + height * CROWN_BAND[0]


def place_bottom_crown(high, mannequin, armature, rule):
    """Size and place headwear: bottom above a bone, crown a little wider than the head. A hat too
    shallow to cover the top of the head from there grows until it does, as caps and sun hats
    are wider than the head; sitting lower would only drop its top further."""
    bones = armature.data.bones
    brow = (armature.matrix_world @ bones[rule["bottomAbove"]].head_local).z
    head = next((obj for obj in mannequin if "head" in obj.name.lower()), None)
    if head is None:
        raise SystemExit("The mannequin has no head mesh among %s" % [o.name for o in mannequin])
    head_top = vertices(head)[:, 2].max()

    points = vertices(high)
    low, high_z = points[:, 2].min(), points[:, 2].max()
    crown_width, crown_center, crown_z = measure_crown(points, low, high_z - low)

    bottom = brow + rule["bottomOffset"]
    ring = section(head, bottom)
    if len(ring) < 3:
        raise SystemExit("The mannequin's head doesn't reach %.3f m" % bottom)
    head_width = ring[:, 0].max() - ring[:, 0].min()
    head_center = (ring.min(0) + ring.max(0)) / 2
    scale = head_width * rule["clearance"] / crown_width
    covering = (head_top + rule["topClearance"] - bottom) / (high_z - low)
    if covering > scale:
        print("[fit] too shallow at %.3f, grown to cover the head" % scale)
        scale = covering
    print("[fit] head %.3f m wide at %.3f m, crown %.3f, scale %.3f" % (
        head_width, bottom, crown_width, scale))

    high.scale = (scale, scale, scale)
    high.location = (head_center[0] - crown_center[0] * scale,
                     head_center[1] - crown_center[1] * scale,
                     bottom - low * scale)
    select_only(high)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    pull_in_brim(high, head_center, crown_width * scale / 2, bottom + (crown_z - low) * scale,
                 head_width * rule["maxWidth"] / 2)


def pull_in_brim(obj, center, crown, top, limit):
    """Pull the brim, what lies below the crown's narrowest height and outside it, toward the
    crown, so it reaches at most limit from the center. Image models draw brims far wider than
    they are worn, and shrinking the whole hat instead would leave the crown too small."""
    co = np.empty(len(obj.data.vertices) * 3)
    obj.data.vertices.foreach_get("co", co)
    co = co.reshape(-1, 3)
    offset = co[:, :2] - center[:2]
    radius = np.linalg.norm(offset, axis=1)
    brim = co[:, 2] < top
    widest = radius[brim].max() if brim.any() else 0
    if widest <= limit:
        return
    outside = brim & (radius > crown)
    pulled = crown + (radius[outside] - crown) * (limit - crown) / (widest - crown)
    co[outside, :2] = center[:2] + offset[outside] * (pulled / radius[outside])[:, None]
    obj.data.vertices.foreach_set("co", co.ravel())
    obj.data.update()
    print("[fit] pulled the brim in from %.3f m to %.3f m across" % (widest * 2, limit * 2))


def apply_modifier(obj, kind, **settings):
    select_only(obj)
    modifier = obj.modifiers.new(kind, kind)
    for key, value in settings.items():
        setattr(modifier, key, value)
    bpy.ops.object.modifier_apply(modifier=modifier.name)


def low_poly(high, name, triangles):
    """A closed, decimated, triangulated, unwrapped copy. Generated meshes have open edges and
    overlapping layers that stall decimation and fold it into spikes, so the copy is remeshed
    into one closed surface first."""
    low = high.copy()
    low.data = high.data.copy()
    low.name = name
    low.data.name = name
    bpy.context.scene.collection.objects.link(low)
    voxel = max(low.dimensions) / REMESH_VOXELS
    # Generated shells can be thinner than a voxel, which remeshes into a sieve that decimation
    # then collapses, so they are thickened to a couple of voxels first
    apply_modifier(low, "SOLIDIFY", thickness=voxel * 2, offset=0.0)
    apply_modifier(low, "REMESH", mode="VOXEL", voxel_size=voxel, adaptivity=0.0)
    # Collapsing stops short of a low target, so it goes again until close
    for _ in range(DECIMATE_PASSES):
        faces = len(low.data.polygons)
        if faces <= triangles * 1.05:
            break
        apply_modifier(low, "DECIMATE", ratio=triangles / faces)
    apply_modifier(low, "TRIANGULATE")
    # Smooth, so the normal map is baked against the normals the game interpolates
    select_only(low)
    bpy.ops.object.shade_smooth()
    low.data.materials.clear()
    while low.data.uv_layers:
        low.data.uv_layers.remove(low.data.uv_layers[0])
    low.data.uv_layers.new(name="UVMap")
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.02)
    bpy.ops.object.mode_set(mode="OBJECT")
    print("[fit] low poly: %d triangles" % len(low.data.polygons))
    return low


def average_metal_roughness(high):
    """Metallic and roughness averaged over the generated texture's used texels."""
    for slot in high.material_slots:
        tree = slot.material.node_tree if slot.material else None
        if tree is None:
            continue
        bsdf = next((n for n in tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
        for node in tree.nodes:
            if node.type != "TEX_IMAGE" or node.image is None:
                continue
            linked = {link.to_node.type for link in tree.links if link.from_node == node}
            if "BSDF_PRINCIPLED" in linked or "NORMAL_MAP" in linked:
                continue  # base color or normals; the metal-roughness image goes through a separate
            px = np.array(node.image.pixels[:]).reshape(-1, 4)
            used = px[px[:, :3].max(1) > 0]
            if len(used):
                return float(used[:, 2].mean()), float(used[:, 1].mean())
        if bsdf is not None:
            return bsdf.inputs["Metallic"].default_value, bsdf.inputs["Roughness"].default_value
    return 0.0, 0.6


def enable_gpu():
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    prefs = bpy.context.preferences.addons["cycles"].preferences
    for kind in ("OPTIX", "CUDA"):
        try:
            prefs.compute_device_type = kind
            prefs.get_devices()
            if any(d.type == kind for d in prefs.devices):
                for device in prefs.devices:
                    device.use = device.type == kind
                scene.cycles.device = "GPU"
                return
        except TypeError:
            continue
    scene.cycles.device = "CPU"


def emit_base_color(obj):
    """Show each material's base color as emission, so an emission bake copies the base color
    as it is. A diffuse color bake leaves out the metallic share, which turns gold black."""
    for slot in obj.material_slots:
        if slot.material is None or not slot.material.use_nodes:
            continue
        tree = slot.material.node_tree
        bsdf = next((n for n in tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
        output = next((n for n in tree.nodes if n.type == "OUTPUT_MATERIAL"), None)
        if bsdf is None or output is None:
            continue
        emission = tree.nodes.new("ShaderNodeEmission")
        base = bsdf.inputs["Base Color"]
        if base.links:
            tree.links.new(base.links[0].from_socket, emission.inputs["Color"])
        else:
            emission.inputs["Color"].default_value = base.default_value
        tree.links.new(emission.outputs["Emission"], output.inputs["Surface"])


def bake(high, low, name, size):
    """Bake base color and normals from the generated mesh onto the low-poly copy."""
    enable_gpu()
    scene = bpy.context.scene
    scene.cycles.samples = 8
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    low.data.materials.append(material)
    tree = material.node_tree
    bsdf = tree.nodes["Principled BSDF"]

    metallic, roughness = average_metal_roughness(high)
    emit_base_color(high)
    extent = max(low.dimensions)
    images = {}
    for kind, color in (("EMIT", "sRGB"), ("NORMAL", "Non-Color")):
        image = bpy.data.images.new("%s_%s" % (name, kind.lower()), size, size, alpha=False)
        image.colorspace_settings.name = color
        node = tree.nodes.new("ShaderNodeTexImage")
        node.image = image
        tree.nodes.active = node
        select_only(low)
        high.select_set(True)
        bpy.context.view_layer.objects.active = low
        bpy.ops.object.bake(type=kind, use_selected_to_active=True,
                            cage_extrusion=extent * BAKE_REACH * 0.5,
                            max_ray_distance=extent * BAKE_REACH, margin=8)
        image.pack()
        images[kind] = node

    tree.links.new(images["EMIT"].outputs["Color"], bsdf.inputs["Base Color"])
    normal_map = tree.nodes.new("ShaderNodeNormalMap")
    tree.links.new(images["NORMAL"].outputs["Color"], normal_map.inputs["Color"])
    tree.links.new(normal_map.outputs["Normal"], bsdf.inputs["Normal"])
    tree.nodes.active = images["EMIT"]  # previews show the active image
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = roughness
    print("[fit] baked %d px, metallic %.2f, roughness %.2f" % (size, metallic, roughness))


def rig(low, armature, bone):
    """Weight every vertex to the socket's bone and put the mesh on the armature."""
    if bone not in armature.data.bones:
        raise SystemExit("The mannequin has no bone " + bone)
    group = low.vertex_groups.new(name=bone)
    group.add(range(len(low.data.vertices)), 1.0, "REPLACE")
    modifier = low.modifiers.new("Armature", "ARMATURE")
    modifier.object = armature
    world = low.matrix_world.copy()
    low.parent = armature
    low.matrix_parent_inverse = armature.matrix_world.inverted()
    low.matrix_world = world


def export(path, low, armature):
    select_only(low, armature)
    options = {
        "filepath": path,
        "export_format": "GLB",
        "use_selection": True,
        "export_yup": True,
        "export_skins": True,
        "export_def_bones": False,
        "export_animations": False,
        "export_colors": False,
        "export_extras": False,
        "export_image_format": "AUTO",
    }
    known = {p.identifier for p in bpy.ops.export_scene.gltf.get_rna_type().properties}
    bpy.ops.export_scene.gltf(**{key: value for key, value in options.items() if key in known})
    print("[fit] wrote", path)


def preview(folder, low, mannequin):
    """Front and side renders around the part, on the mannequin."""
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.light = "STUDIO"
    scene.display.shading.color_type = "TEXTURE"
    scene.render.resolution_x = scene.render.resolution_y = 512
    scene.world = bpy.data.worlds.new("preview")
    scene.world.color = (0.8, 0.8, 0.8)
    for obj in bpy.data.objects:
        if obj.type == "MESH":
            obj.hide_render = obj not in mannequin and obj != low
    points = vertices(low)
    center = Vector(((points.min(0) + points.max(0)) / 2).tolist())
    size = float((points.max(0) - points.min(0)).max())
    camera = bpy.data.objects.new("camera", bpy.data.cameras.new("camera"))
    camera.data.type = "ORTHO"
    camera.data.ortho_scale = size * 2.2
    scene.collection.objects.link(camera)
    scene.camera = camera
    for i, angle in enumerate((0, 60, 180)):
        a = math.radians(angle)
        camera.location = center + Vector((math.sin(a), -math.cos(a), 0.25)) * 4
        camera.rotation_euler = (center - camera.location).to_track_quat("-Z", "Y").to_euler()
        scene.render.filepath = "%s/preview_%d.png" % (folder, i)
        bpy.ops.render.render(write_still=True)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description="Fit a generated mesh as a socket accessory.")
    parser.add_argument("--mesh", required=True)
    parser.add_argument("--mannequin", required=True)
    parser.add_argument("--bone", required=True, help="The socket's bone.")
    parser.add_argument("--rule", required=True, help="The socket type's rule, as JSON.")
    parser.add_argument("--name", required=True, help="The part's mesh and material name.")
    parser.add_argument("--out", required=True)
    parser.add_argument("--preview", help="A folder for preview renders.")
    args = parser.parse_args(argv)
    rule = json.loads(args.rule)

    bpy.ops.wm.read_factory_settings(use_empty=True)
    added = import_glb(args.mannequin)
    armature = next(obj for obj in added if obj.type == "ARMATURE")
    mannequin = [obj for obj in added if obj.type == "MESH"]

    high = generated_mesh(args.mesh)
    weld(high)
    high = drop_slivers(high)
    if rule["anchor"] == "bottom" and rule["fit"] == "crown":
        place_bottom_crown(high, mannequin, armature, rule)
    else:
        raise SystemExit("No fitting for anchor %s, fit %s yet" % (rule["anchor"], rule["fit"]))
    low = low_poly(high, args.name, rule["triangles"])
    bake(high, low, args.name, rule["texture"])
    rig(low, armature, args.bone)
    export(args.out, low, armature)
    if args.preview:
        high.hide_render = True
        preview(args.preview, low, mannequin)


main()
