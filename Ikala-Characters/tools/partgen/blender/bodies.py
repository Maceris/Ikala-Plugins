"""Reading the mannequins in Blender for fitting clothing: their bodies at rest as triangles in
world space, and how far points are from them. Blender space: Z up, the body facing -Y, meters.
"""

import os

import bpy
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree


def world_vertices(obj):
    """An object's evaluated vertices in world space, and its triangles."""
    depsgraph = bpy.context.evaluated_depsgraph_get()
    evaluated = obj.evaluated_get(depsgraph)
    mesh = evaluated.to_mesh()
    mesh.calc_loop_triangles()
    co = np.empty(len(mesh.vertices) * 3)
    mesh.vertices.foreach_get("co", co)
    co = co.reshape(-1, 3)
    m = np.array(obj.matrix_world)
    co = co @ m[:3, :3].T + m[:3, 3]
    tris = np.empty(len(mesh.loop_triangles) * 3, dtype=np.int64)
    mesh.loop_triangles.foreach_get("vertices", tris)
    evaluated.to_mesh_clear()
    return co, tris.reshape(-1, 3)


def merged(objects):
    """Vertices and triangles of several objects together, in world space."""
    points, faces, offset = [], [], 0
    for obj in objects:
        co, tris = world_vertices(obj)
        points.append(co)
        faces.append(tris + offset)
        offset += len(co)
    return np.concatenate(points), np.concatenate(faces)


def load_body(path, slots=()):
    """A mannequin's skin at rest, from its .blend file, and the part of it in some slots.

    The files are saved with a clip playing; clothing is fitted to the rest A pose."""
    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(path))
    for armature in bpy.data.armatures:
        armature.pose_position = "REST"
    bodies = [o for o in bpy.data.objects if o.type == "MESH" and "eye" not in o.name.lower()]
    chosen = [o for o in bodies if any(s.lower() in o.name.lower() for s in slots)]
    return merged(bodies), (merged(chosen) if chosen else None)


def tree(points, faces):
    return BVHTree.FromPolygons([tuple(p) for p in points], [tuple(f) for f in faces])


def nearest(points, surface):
    """The nearest point on a surface (a BVH tree) to each point, and how far outside it each
    point is, negative inside."""
    closest = np.empty_like(points)
    signed = np.empty(len(points))
    for i, p in enumerate(points):
        location, normal, _, distance = surface.find_nearest(Vector(p))
        closest[i] = location
        signed[i] = distance if (Vector(p) - location).dot(normal) >= 0 else -distance
    return closest, signed


def signed_distances(points, body_points, body_faces):
    """How far outside a body each point is, negative inside."""
    return nearest(points, tree(body_points, body_faces))[1]


def normals(points, faces):
    """Area-weighted vertex normals."""
    n = np.zeros_like(points)
    a, b, c = points[faces[:, 0]], points[faces[:, 1]], points[faces[:, 2]]
    face = np.cross(b - a, c - a)
    for k in range(3):
        np.add.at(n, faces[:, k], face)
    length = np.linalg.norm(n, axis=1, keepdims=True)
    return n / np.maximum(length, 1e-12)
