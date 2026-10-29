"""Render generated meshes from the front, a front corner and the back, for reviewing them.

Run it headless with Blender 3.3 or later, which partgen.py does:

    blender -b --factory-startup --python render_meshes.py -- <mesh.glb> <folder> [<mesh.glb> <folder>...]

Each folder gets view_0.png to view_2.png: textured, studio lit, orthographic, framed to the mesh.
"""
import math
import sys

import bpy
from mathutils import Vector

SIZE = 384
VIEWS = (0, 45, 180)


def render(path, folder):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=path)
    meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    corners = [o.matrix_world @ Vector(c) for o in meshes for c in o.bound_box]
    low = Vector([min(c[i] for c in corners) for i in range(3)])
    high = Vector([max(c[i] for c in corners) for i in range(3)])
    center, size = (low + high) / 2, max(high - low)

    scene = bpy.context.scene
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.light = "STUDIO"
    scene.display.shading.color_type = "TEXTURE"
    scene.render.resolution_x = scene.render.resolution_y = SIZE
    scene.world = bpy.data.worlds.new("review")
    scene.world.color = (0.8, 0.8, 0.8)
    camera = bpy.data.objects.new("camera", bpy.data.cameras.new("camera"))
    camera.data.type = "ORTHO"
    camera.data.ortho_scale = size * 1.2
    scene.collection.objects.link(camera)
    scene.camera = camera
    for i, angle in enumerate(VIEWS):
        a = math.radians(angle)
        # glTF's +Z forward is Blender's -Y, so the front camera sits on -Y
        camera.location = center + Vector((math.sin(a), -math.cos(a), 0.2)) * size * 3
        camera.rotation_euler = (center - camera.location).to_track_quat("-Z", "Y").to_euler()
        scene.render.filepath = "%s/view_%d.png" % (folder, i)
        bpy.ops.render.render(write_still=True)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    for path, folder in zip(argv[0::2], argv[1::2]):
        render(path, folder)


main()
