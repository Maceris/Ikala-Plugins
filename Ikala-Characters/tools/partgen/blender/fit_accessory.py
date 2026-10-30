"""Fit a generated mesh onto the mannequin as a rigid accessory on one socket.

Run it headless with Blender 5 or later, which partgen.py does:

    blender -b --factory-startup --python fit_accessory.py -- --mesh <generated.glb>
        --mannequin <mannequin.glb> --bone <socket bone> --rule <json> --name <mesh name>
        --out <part.glb> --preview <folder>

The generated mesh is cleaned of thin slivers, sized and placed by the socket type's rule, then a
low-poly copy is unwrapped and baked from it: base color, a normal map, and an ORM map
(ambient occlusion, then the generated roughness and metalness). The copy is weighted entirely
to the socket's bone, so it moves rigidly with it, and written with the mannequin's armature so
it loads onto the rig like any other part.

The base color is split into up to four color groups and made recolorable: each group's base color
turns white, so the tint the shader multiplies in replaces it, and a tint mask beside the glb
(<part>_masks/<image>.png) picks the groups, one per channel. Each group's color is written to --tints as the default for its mask channel, which
brings the part's own look back when a design leaves the palette channel uncolored. The preview
folder gets front and side renders on the mannequin, in those default colors.
"""
import argparse
import json
import math
import pathlib
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Vector

# A part of the generated mesh this thin across, and this many times longer, is a sliver
SLIVER_THICKNESS = 0.003
SLIVER_ASPECT = 10
# A loose part this many times longer than its second size is a needle, however thick
NEEDLE_ASPECT = 8
# Needles attached to the surface, as fractions of the mesh's size: neighbors within the reach
# at the same height (heights stretched by the flatten factor) all inside the width make a
# vertex thin, and a connected run of thin vertices is a needle if it is longer than the length,
# stays within the width of a straight line, and leans less than the tilt (degrees)
NEEDLE_REACH = 0.03
NEEDLE_WIDTH = 0.02
NEEDLE_FLATTEN = 20
NEEDLE_LENGTH = 0.2
NEEDLE_TILT = 15
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
# How far ambient occlusion looks for cover, as a share of the part's size
OCCLUSION_REACH = 0.15
# What texels the bake doesn't reach hold
UNBAKED = (1.0, 0.0, 1.0, 1.0)
# The node group the glTF exporter reads occlusion from
GLTF_OUTPUT = "glTF Material Output"

# Tint masks: how much lightness counts against hue when grouping colors, how many texels to
# group from, the mean spread (in Lab units) a group may have, the smallest share of the texels
# a group keeps, how close (in Lab units) two groups may be before they merge, and the lightness
# percentile (give or take TINT_BAND) of a group's colors taken as its color: above the median,
# since generated base colors carry baked shadow, but below highlights like a light brim edge
MASK_CHANNELS = ("r", "g", "b", "a")
# Beside a part's glb, the folder of tint masks Ikala-Characters looks for (CharacterFiles)
MASK_FOLDER_SUFFIX = "_masks"
TINT_LIGHTNESS = 0.35
TINT_SAMPLE = 20000
TINT_SPREAD = 10.0
TINT_SMALLEST = 0.03
TINT_ALIKE = 12.0
TINT_REFERENCE = 80
TINT_BAND = 5
# How much of the texture's lightness a tinted group keeps, as a power of its lightness against
# the group's color: 0 makes the tint replace the base color outright, as a design's color is
# meant to (the normal and ORM maps still carry the shape and wear); 1 keeps it all. Generated
# base colors carry baked shading, which kept would leave a recolored crown darker than its brim
TINT_DETAIL = 0.0
# Relative luminance of linear sRGB
LUMINANCE = np.array([0.2126, 0.7152, 0.0722])
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


def is_needle(points, size, narrow):
    """Whether a run of thin vertices is a needle: long, straight (close to its own best line)
    and upright."""
    if len(points) < 3:
        return False
    centered = points - points.mean(0)
    _, spread, axes = np.linalg.svd(centered, full_matrices=False)
    axis = axes[0]
    along = centered @ axis
    off_line = np.linalg.norm(centered - np.outer(along, axis), axis=1).max()
    return (along.max() - along.min() > size * NEEDLE_LENGTH and off_line < narrow
            and abs(axis[2]) > math.cos(math.radians(NEEDLE_TILT)))


def drop_needles(obj):
    """Remove the straight vertical needles generated meshes can carry, attached to the surface.
    A thin vertex has its neighbors at the same height crowded into a narrow cross-section,
    where on a surface they spread out along it. A connected run of thin vertices is a needle
    only if it is also long, straight and upright, which thin parts made on purpose rarely all
    are: tassels sway and are short, grating bars lie mostly level, a crown's point is short."""
    from mathutils.kdtree import KDTree
    co = vertices(obj)
    size = (co.max(0) - co.min(0)).max()
    radius, narrow = size * NEEDLE_REACH, size * NEEDLE_WIDTH
    tree = KDTree(len(co))
    for i, p in enumerate(co):
        tree.insert((p[0], p[1], p[2] * NEEDLE_FLATTEN), i)  # neighbors come from a thin slice
    tree.balance()
    thin = np.zeros(len(co), dtype=bool)
    for i, p in enumerate(co):
        near = co[[index for _, index, _ in tree.find_range((p[0], p[1], p[2] * NEEDLE_FLATTEN),
                                                            radius)]]
        thin[i] = (near[:, :2].max(0) - near[:, :2].min(0)).max() < narrow

    bm = bmesh.new()
    bm.from_mesh(obj.data)
    bm.verts.ensure_lookup_table()
    seen, doomed = set(), []
    for start in (v for v in bm.verts if thin[v.index]):
        if start.index in seen:
            continue
        run, stack = [], [start]
        seen.add(start.index)
        while stack:
            v = stack.pop()
            run.append(v)
            for edge in v.link_edges:
                other = edge.other_vert(v)
                if thin[other.index] and other.index not in seen:
                    seen.add(other.index)
                    stack.append(other)
        if is_needle(np.array([v.co for v in run]), size, narrow):
            doomed += run
    bmesh.ops.delete(bm, geom=doomed, context="VERTS")
    bm.to_mesh(obj.data)
    bm.free()
    print("[fit] dropped %d needle vertices" % len(doomed))


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
        thin, middle, long = np.sort(co.max(0) - co.min(0))
        sheet = thin < SLIVER_THICKNESS and long > SLIVER_ASPECT * thin
        needle = long > NEEDLE_ASPECT * middle
        if sheet or needle or len(piece.data.polygons) < SPECK_FACES:
            bpy.data.objects.remove(piece)
        else:
            kept.append(piece)
            print("[fit] kept a part of %d faces, %.3f x %.3f x %.3f" % (
                len(piece.data.polygons), thin, middle, long))
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
    # A brief can nudge the size after review
    scale *= rule.get("scale", 1.0)
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


def metal_roughness_image(high):
    """The generated texture's metal-roughness image, which reaches the BSDF through a color
    split rather than directly as the base color and normals do, or None."""
    for slot in high.material_slots:
        tree = slot.material.node_tree if slot.material else None
        if tree is None:
            continue
        for node in tree.nodes:
            if node.type != "TEX_IMAGE" or node.image is None:
                continue
            linked = {link.to_node.type for link in tree.links if link.from_node == node}
            if linked and not linked & {"BSDF_PRINCIPLED", "NORMAL_MAP"}:
                return node
    return None


def average_metal_roughness(high):
    """Metallic and roughness averaged over the generated texture's used texels."""
    node = metal_roughness_image(high)
    if node is not None:
        px = np.array(node.image.pixels[:]).reshape(-1, 4)
        used = px[px[:, :3].max(1) > 0]
        if len(used):
            return float(used[:, 2].mean()), float(used[:, 1].mean())
    for slot in high.material_slots:
        tree = slot.material.node_tree if slot.material else None
        bsdf = tree and next((n for n in tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
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


def emit(obj, source):
    """Show each material's surface as emission of one of its colors, so an emission bake copies
    that color as it is. A diffuse color bake leaves out the metallic share, which turns gold
    black. source(tree, bsdf) gives the socket to show, or None to show the base color."""
    for slot in obj.material_slots:
        if slot.material is None or slot.material.node_tree is None:
            continue
        tree = slot.material.node_tree
        bsdf = next((n for n in tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
        output = next((n for n in tree.nodes if n.type == "OUTPUT_MATERIAL"), None)
        if bsdf is None or output is None:
            continue
        emission = tree.nodes.new("ShaderNodeEmission")
        socket = source(tree, bsdf)
        base = bsdf.inputs["Base Color"]
        if socket is not None:
            tree.links.new(socket, emission.inputs["Color"])
        elif base.links:
            tree.links.new(base.links[0].from_socket, emission.inputs["Color"])
        else:
            emission.inputs["Color"].default_value = base.default_value
        tree.links.new(emission.outputs["Emission"], output.inputs["Surface"])


def bake_into(kind, high, low, tree, image):
    """Bake one kind of map from the generated mesh into an image of the low-poly copy."""
    node = tree.nodes.new("ShaderNodeTexImage")
    node.image = image
    tree.nodes.active = node
    select_only(low)
    high.select_set(True)
    bpy.context.view_layer.objects.active = low
    extent = max(low.dimensions)
    bpy.ops.object.bake(type=kind, use_selected_to_active=True,
                        cage_extrusion=extent * BAKE_REACH * 0.5,
                        max_ray_distance=extent * BAKE_REACH, margin=8)
    return node


def new_image(name, size, color, fill=(0, 0, 0, 1)):
    image = bpy.data.images.new(name, size, size, alpha=False)
    image.colorspace_settings.name = color
    image.generated_color = fill
    return image


def occlusion_output(tree):
    """The node the glTF exporter takes occlusion from: a group named glTF Material Output."""
    group = bpy.data.node_groups.get(GLTF_OUTPUT)
    if group is None:
        group = bpy.data.node_groups.new(GLTF_OUTPUT, "ShaderNodeTree")
        group.interface.new_socket("Occlusion", in_out="INPUT", socket_type="NodeSocketFloat")
    node = tree.nodes.new("ShaderNodeGroup")
    node.node_tree = group
    return node


def bake(high, low, name, size, hidden):
    """Bake base color, normals and an ORM map (occlusion, roughness, metalness) from the
    generated mesh onto the low-poly copy. The hidden objects, like the mannequin, are left out
    of the occlusion. Gives the base color image and which of its texels the bake covered."""
    enable_gpu()
    scene = bpy.context.scene
    scene.cycles.samples = 8
    material = bpy.data.materials.new(name)  # with a node tree, from Blender 5
    low.data.materials.append(material)
    tree = material.node_tree
    bsdf = tree.nodes["Principled BSDF"]
    for obj in hidden:
        obj.hide_render = True

    # Texels the bake doesn't reach keep this color, so they can be told apart
    base_image = new_image("%s_emit" % name, size, "sRGB", UNBAKED)
    emit(high, lambda tree, bsdf: None)
    base = bake_into("EMIT", high, low, tree, base_image)
    covered = np.abs(np.array(base_image.pixels[:]).reshape(-1, 4)[:, :3]
                     - np.array(UNBAKED[:3])).max(1) > 1e-3
    normals = bake_into("NORMAL", high, low, tree, new_image("%s_normal" % name, size, "Non-Color"))

    metallic, roughness = average_metal_roughness(high)
    orm = np.empty((size * size, 4))
    orm[:] = (1, roughness, metallic, 1)
    source = metal_roughness_image(high)
    if source is not None:
        emit(high, lambda tree, bsdf: next(
            (n for n in tree.nodes if n.type == "TEX_IMAGE" and n.image == source.image),
            source).outputs["Color"])
        values = new_image("%s_mr" % name, size, "Non-Color")
        bake_into("EMIT", high, low, tree, values)
        orm[:, 1:3] = np.array(values.pixels[:]).reshape(-1, 4)[:, 1:3]
    scene.world = scene.world or bpy.data.worlds.new("bake")
    scene.world.light_settings.distance = max(low.dimensions) * OCCLUSION_REACH
    occlusion = new_image("%s_ao" % name, size, "Non-Color")
    bake_into("AO", high, low, tree, occlusion)
    orm[:, 0] = np.array(occlusion.pixels[:]).reshape(-1, 4)[:, 0]
    orm_image = new_image("%s_orm" % name, size, "Non-Color")
    orm_image.pixels.foreach_set(orm.ravel().astype(np.float32))
    orm_image.pack()
    normals.image.pack()
    for obj in hidden:
        obj.hide_render = False

    tree.links.new(base.outputs["Color"], bsdf.inputs["Base Color"])
    normal_map = tree.nodes.new("ShaderNodeNormalMap")
    tree.links.new(normals.outputs["Color"], normal_map.inputs["Color"])
    tree.links.new(normal_map.outputs["Normal"], bsdf.inputs["Normal"])
    orm_node = tree.nodes.new("ShaderNodeTexImage")
    orm_node.image = orm_image
    split = tree.nodes.new("ShaderNodeSeparateColor")
    tree.links.new(orm_node.outputs["Color"], split.inputs["Color"])
    tree.links.new(split.outputs["Green"], bsdf.inputs["Roughness"])
    tree.links.new(split.outputs["Blue"], bsdf.inputs["Metallic"])
    tree.links.new(split.outputs["Red"], occlusion_output(tree).inputs["Occlusion"])
    tree.nodes.active = base  # previews show the active image
    print("[fit] baked %d px, metallic %.2f, roughness %.2f on average, occlusion %.2f" % (
        size, metallic, roughness, float(orm[covered, 0].mean()) if covered.any() else 1))
    return base_image, covered


def srgb_to_linear(c):
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def linear_to_srgb(c):
    c = np.clip(c, 0, 1)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * c ** (1 / 2.4) - 0.055)


def lab(linear):
    """CIE Lab of linear sRGB colors, D65, as an n by 3 array."""
    xyz = linear @ np.array([[0.4124, 0.3576, 0.1805],
                             [0.2126, 0.7152, 0.0722],
                             [0.0193, 0.1192, 0.9505]]).T
    xyz /= np.array([0.9505, 1.0, 1.089])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[:, 1] - 16, 500 * (f[:, 0] - f[:, 1]),
                     200 * (f[:, 1] - f[:, 2])], axis=1)


def kmeans(points, k, rounds=30):
    """Plain k-means with farthest-point starts, so it gives the same groups every run."""
    centers = [points[np.argmin(np.linalg.norm(points - np.median(points, 0), axis=1))]]
    for _ in range(1, k):
        distance = np.min([np.linalg.norm(points - c, axis=1) for c in centers], axis=0)
        centers.append(points[np.argmax(distance)])
    centers = np.array(centers)
    for _ in range(rounds):
        labels = np.argmin(((points[:, None, :] - centers[None]) ** 2).sum(2), axis=1)
        moved = np.array([points[labels == c].mean(0) if (labels == c).any() else centers[c]
                          for c in range(k)])
        if np.allclose(moved, centers, atol=1e-3):
            break
        centers = moved
    return labels, centers


def color_groups(linear):
    """Split texel colors into up to four groups a tint can recolor alike. Lightness counts
    less than hue, so the shading across one cloth stays in one group; the fewest groups that
    keep each one tight are used, and groups too small or too alike are merged."""
    features = lab(linear) * np.array([TINT_LIGHTNESS, 1, 1])
    sample = features[:: max(1, len(features) // TINT_SAMPLE)]
    for k in range(1, len(MASK_CHANNELS) + 1):
        labels, centers = kmeans(sample, k)
        spread = np.linalg.norm(sample - centers[labels], axis=1).mean()
        if spread < TINT_SPREAD:
            break
    labels = np.argmin(((features[:, None, :] - centers[None]) ** 2).sum(2), axis=1)
    while len(centers) > 1:
        counts = np.bincount(labels, minlength=len(centers))
        gaps = np.linalg.norm(centers[:, None] - centers[None], axis=2)
        np.fill_diagonal(gaps, np.inf)
        small = np.argmin(counts)
        if counts[small] < TINT_SMALLEST * len(labels):
            gone = small
        elif gaps.min() < TINT_ALIKE:
            a, b = np.unravel_index(np.argmin(gaps), gaps.shape)
            gone = a if counts[a] < counts[b] else b
        else:
            break
        gaps[gone, gone] = np.inf
        into = np.argmin(gaps[gone])
        labels[labels == gone] = into
        keep = [c for c in range(len(centers)) if c != gone]
        centers = centers[keep]
        labels = np.searchsorted(keep, labels)
        centers = np.array([features[labels == c].mean(0) for c in range(len(centers))])
    # The biggest group takes the first mask channel
    order = np.argsort(-np.bincount(labels, minlength=len(centers)))
    return np.argsort(order)[labels], len(centers)


def tint_masks(image, covered, folder):
    """Make the baked base color recolorable: split it into color groups, turn each group's
    base color white (or gray with some of its lightness, see TINT_DETAIL), and write a mask
    whose channels pick the groups. A group's typical color is its default tint. Gives
    the default colors, by mask channel, and the original pixels."""
    size = image.size[0]
    pixels = np.array(image.pixels[:]).reshape(-1, 4)
    original = pixels.copy()
    linear = srgb_to_linear(pixels[:, :3])
    labels = np.zeros(len(linear), dtype=np.int64)
    found, count = color_groups(linear[covered])
    labels[covered] = found
    if (~covered).any():
        # Texels nothing was baked into follow the nearest covered one in the same row
        index = np.where(covered, np.arange(len(covered)), -1)
        index = np.maximum.accumulate(index)
        index[index < 0] = np.argmax(covered)
        labels[~covered] = labels[index[~covered]]
        linear[~covered] = linear[index[~covered]]
    mask = np.zeros((len(linear), 4))
    defaults = {}
    for group in range(count):
        members = labels == group
        # The group's color, and what detail it keeps as gray: lightness against the color's
        # own. Dividing each channel instead leaves a saturated red with nothing in green or
        # blue, which a green tint then multiplies into black
        lightness = linear @ LUMINANCE
        low, high = np.percentile(lightness[members & covered],
                                  [TINT_REFERENCE - TINT_BAND, TINT_REFERENCE + TINT_BAND])
        near = (lightness[members & covered] >= low) & (lightness[members & covered] <= high)
        reference = linear[members & covered][near].mean(0)
        level = max(float(reference @ LUMINANCE), 1e-4)
        linear[members] = (np.clip(lightness[members] / level, 0, 1) ** TINT_DETAIL)[:, None]
        mask[members, group] = 1
        rgb = np.round(linear_to_srgb(reference) * 255).astype(int)
        defaults[MASK_CHANNELS[group]] = "#%02x%02x%02x" % tuple(rgb)
    pixels[:, :3] = linear_to_srgb(linear)
    image.pixels.foreach_set(pixels.ravel().astype(np.float32))

    folder.mkdir(parents=True, exist_ok=True)
    out = bpy.data.images.new(image.name + "_mask", size, size, alpha=True)
    out.colorspace_settings.name = "Non-Color"
    out.pixels.foreach_set(mask.ravel().astype(np.float32))
    out.filepath_raw = str(folder / (image.name + ".png"))
    out.file_format = "PNG"
    out.save()
    print("[fit] %d tint groups: %s" % (count, defaults))
    return defaults, original


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
        "export_tangents": True,  # normal maps follow them
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
    parser.add_argument("--tints", help="Where to write the default tint colors, as JSON.")
    args = parser.parse_args(argv)
    rule = json.loads(args.rule)

    bpy.ops.wm.read_factory_settings(use_empty=True)
    added = import_glb(args.mannequin)
    armature = next(obj for obj in added if obj.type == "ARMATURE")
    mannequin = [obj for obj in added if obj.type == "MESH"]

    high = generated_mesh(args.mesh)
    weld(high)
    if not rule.get("keepThin", False):
        drop_needles(high)
    high = drop_slivers(high)
    if rule["anchor"] == "bottom" and rule["fit"] == "crown":
        place_bottom_crown(high, mannequin, armature, rule)
    else:
        raise SystemExit("No fitting for anchor %s, fit %s yet" % (rule["anchor"], rule["fit"]))
    low = low_poly(high, args.name, rule["triangles"])
    base, covered = bake(high, low, args.name, rule["texture"], mannequin)
    out = pathlib.Path(args.out)
    defaults, original = tint_masks(base, covered, out.with_name(out.stem + MASK_FOLDER_SUFFIX))
    base.pack()
    rig(low, armature, args.bone)
    export(args.out, low, armature)
    if args.tints:
        with open(args.tints, "w", encoding="utf8") as stream:
            json.dump(defaults, stream, indent=2)
    if args.preview:
        # Previews show the part in its own colors, as it is shown untinted
        base.pixels.foreach_set(original.ravel().astype(np.float32))
        high.hide_render = True
        preview(args.preview, low, mannequin)


main()
