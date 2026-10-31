"""Folded flaps on a garment built from the mannequin's skin (see shell_layer.py): a jacket's
lapels and collar.

TRELLIS.2 makes a jacket's lapels as crumpled sheets, which pushing and rebuilding left as cruft
in its open front, and which the skin shell, cut along the opening, leaves out. Clothing for games
makes them a flap of the garment folded back over itself along the opening. So the flap is the
shell's surface within a width of its neck opening (the open loop reaching highest: the neckline
and, for an open jacket, its front), measured along the surface, copied, cut along the line at
that width so its edge runs smoothly through faces, and lifted off the shell, by nothing at the
fold, where it meets the opening's edge, to the lift a little way in. It tapers to nothing toward
the opening's lowest point, where a jacket's lapels meet its top button. The flap is part of the
garment's mesh, so it bakes, takes weights and tints with the rest.
"""

import heapq

import bmesh
import bpy
import numpy as np

import shape_layer as shaping
import shell_layer

# The share of the opening's height, from its lowest point up, over which the flap's width
# grows to full
TAPER = 0.35
# How far from the fold, as a share of the width, the flap rises to its full lift
RISE = 0.25
# How far below the highest open edge the neck opening's top may be, in meters
NECK_REACH = 0.1
# The longest open loop that can be a sleeve's end, in meters
CUFF_LENGTH = 0.45


def neck_opening(mesh):
    """The longest open loop reaching within NECK_REACH of the highest, as a set of its
    vertices, or None: small tears near the collar can reach higher than the neckline."""
    loops = shaping.open_loops(mesh)
    if not loops:
        return None
    top = max(v.co.z for loop in loops for e in loop for v in e.verts)
    near = [loop for loop in loops if max(v.co.z for e in loop for v in e.verts) > top - NECK_REACH]
    best = max(near, key=lambda loop: sum(e.calc_length() for e in loop))
    return {v for e in best for v in e.verts}


def surface_distances(mesh, sources, limit):
    """Each vertex's distance from some vertices along the mesh's edges, up to a limit;
    infinity past it."""
    mesh.verts.ensure_lookup_table()
    distance = np.full(len(mesh.verts), np.inf)
    queue = [(0.0, v.index) for v in sources]
    for _, index in queue:
        distance[index] = 0.0
    heapq.heapify(queue)
    while queue:
        d, index = heapq.heappop(queue)
        if d > distance[index] or d > limit:
            continue
        vertex = mesh.verts[index]
        for edge in vertex.link_edges:
            other = edge.other_vert(vertex)
            nd = d + edge.calc_length()
            if nd < distance[other.index]:
                distance[other.index] = nd
                heapq.heappush(queue, (nd, other.index))
    return distance


def sleeve_ends(mesh):
    """The open loops at the sleeves' ends, as one set of their vertices: on each side, the
    loop reaching furthest out (the arms hang out to the sides in the A pose), if it is short
    enough to be a cuff. Empty for none."""
    ends = set()
    for side in (1, -1):
        loops = [loop for loop in shaping.open_loops(mesh)
                 if sum(e.calc_length() for e in loop) < CUFF_LENGTH
                 and side * np.mean([v.co.x for e in loop for v in e.verts]) > 0]
        if loops:
            best = max(loops, key=lambda loop: side * np.mean(
                [v.co.x for e in loop for v in e.verts]))
            ends |= {v for e in best for v in e.verts}
    return ends


def add_flap(obj, width, lift, at="neck"):
    """Fold a flap back over the garment along its neck opening ("neck", see the module's
    notes), or a band along its sleeves' ends ("cuffs"), the same width all round. Gives how
    many faces the flap has."""
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    mesh.verts.ensure_lookup_table()
    opening = neck_opening(mesh) if at == "neck" else sleeve_ends(mesh)
    if not opening:
        mesh.free()
        return 0
    heights = np.array([v.co.z for v in opening])
    low, high = heights.min(), heights.max()
    distance = surface_distances(mesh, opening, width * 1.5)
    z = np.array([v.co.z for v in mesh.verts])
    if at == "neck":
        widths = width * np.clip((z - low) / max(TAPER * (high - low), 1e-6), 0, 1)
    else:
        widths = np.full(len(z), width)
    # The faces a flap could cover, copied into a mesh of their own
    region = [f for f in mesh.faces if all(distance[v.index] <= width * 1.2 for v in f.verts)]
    if not region:
        mesh.free()
        return 0
    flap = bmesh.new()
    copies = {}
    for face in region:
        for vertex in face.verts:
            if vertex.index not in copies:
                copies[vertex.index] = flap.verts.new(vertex.co)
        flap.faces.new([copies[v.index] for v in face.verts])
    flap.verts.index_update()
    order = sorted(copies, key=lambda i: copies[i].index)
    normals = np.array([mesh.verts[i].normal[:] for i in order])
    mesh.free()

    data = bpy.data.meshes.new("flap")
    flap.to_mesh(data)
    flap.free()
    piece = bpy.data.objects.new("flap", data)
    bpy.context.scene.collection.objects.link(piece)
    # Kept where the distance from the opening is under the width, cut where it crosses
    d = distance[order]
    w = widths[order]
    field = 0.5 + (w - d) / max(2 * width, 1e-6)
    field[w <= 0] = 0.0
    # The cut splits edges; the lift needs each vertex's distance and normal after it, so the
    # values ride along as positions of a copy
    shell_layer.cut_by_field(piece, field)
    shaping.drop_crumbs(piece)
    points = shaping.points_of(piece)
    near = _nearest_indices(points, np.array([v.co[:] for v in obj.data.vertices]))
    after_d = distance[near]
    after_n = np.array([obj.data.vertices[i].normal[:] for i in near])
    rise = np.clip(after_d / max(RISE * width, 1e-6), 0, 1)
    rise = rise * rise * (3 - 2 * rise)
    lifted = points + after_n * (lift * rise)[:, None]
    piece.data.vertices.foreach_set("co", lifted.astype(np.float32).ravel())
    piece.data.update()
    faces = len(piece.data.polygons)

    # Joined to the garment and welded along the fold
    bpy.ops.object.select_all(action="DESELECT")
    piece.select_set(True)
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.join()
    mesh = bmesh.new()
    mesh.from_mesh(obj.data)
    bmesh.ops.remove_doubles(mesh, verts=mesh.verts[:], dist=1e-5)
    mesh.to_mesh(obj.data)
    mesh.free()
    obj.data.update()
    return faces


def _nearest_indices(points, targets):
    """The index of the nearest target to each point."""
    from mathutils.kdtree import KDTree
    tree = KDTree(len(targets))
    for i, p in enumerate(targets):
        tree.insert(p, i)
    tree.balance()
    return np.array([tree.find(p)[1] for p in points])
