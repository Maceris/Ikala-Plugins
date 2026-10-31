"""Folded flaps on a garment built from the mannequin's skin (see shell_layer.py): a jacket's
lapels and collar.

TRELLIS.2 makes a jacket's lapels as crumpled sheets, which pushing and rebuilding left as cruft
in its open front, and which the skin shell, cut along the opening, leaves out. Clothing for games
makes them a flap of the garment folded back over itself along the opening. So the flap is the
shell's surface within a width of its neck opening (the open loop reaching highest: the neckline
and, for an open jacket, its front), measured along the surface, copied, cut along the line at
that width so its edge runs smoothly through faces, and lifted off the shell, by nothing at the
fold, where it meets the opening's edge, to the lift a little way in. It tapers to nothing toward
the opening's lowest point, where a jacket's lapels meet its top button, and, as tailored
lapels do, narrows to a notch where the lapel meets the collar and stays narrower behind the neck.
Its outer edge turns down to the garment, so it reads as cloth with some thickness rather than a
line drawn on. The flap is part of the garment's mesh, so it bakes, takes weights and tints with
the rest.
"""

import heapq

import bmesh
import bpy
import numpy as np
from mathutils import Vector

import shape_layer as shaping
import shell_layer

# The share of the opening's height, from its lowest point up, over which the flap's width
# grows to full
TAPER = 0.35
# How far from the fold, as a share of the width, the flap rises to its full lift
RISE = 0.25
# Where along the opening's height, as a share from its lowest point, the lapel meets the
# collar: the flap narrows to NOTCH_DEPTH less of its width there, over NOTCH_SPAN either side,
# and above it, behind the neck, keeps COLLAR_SHARE of its width
NOTCH_AT = 0.68
NOTCH_DEPTH = 0.75
NOTCH_SPAN = 0.05
COLLAR_SHARE = 0.55
# The share of the way up to the notch over which a straightened opening blends back
STRAIGHT_BLEND = 0.15
# How far a flap's turned-down outer edge comes back toward the garment, as a share of the lift
RIM = 0.8
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


def straighten(mesh, opening):
    """Straighten a front opening's sides, from its lowest point up to the notch (see NOTCH_AT),
    as seen from the front: the image's outline wobbles there, and the lapels, which follow the
    opening, came out lumpy. Each vertex moves across, at its height, onto the line from the
    lowest point to the side's vertex nearest the notch, blended back to where it was over the
    top STRAIGHT_BLEND of the way."""
    verts = list(opening)
    heights = np.array([v.co.z for v in verts])
    low, high = heights.min(), heights.max()
    bottom = verts[int(heights.argmin())].co.copy()
    notch = low + NOTCH_AT * (high - low)
    for side in (1, -1):
        own = [v for v in verts if side * (v.co.x - bottom.x) > 0 and v.co.z <= notch]
        if len(own) < 3:
            continue
        top = min(own, key=lambda v: abs(v.co.z - notch)).co.copy()
        if top.z - bottom.z < 1e-3:
            continue
        for vertex in own:
            share = (vertex.co.z - bottom.z) / (top.z - bottom.z)
            line_x = bottom.x + share * (top.x - bottom.x)
            keep = np.clip((share - (1 - STRAIGHT_BLEND)) / STRAIGHT_BLEND, 0, 1)
            vertex.co.x = line_x * (1 - keep) + vertex.co.x * keep


def opening_distances(mesh, opening):
    """Each vertex's straight-line distance from the opening's open edges. Measured along the
    mesh's edges instead, it zigzagged across the faces, and the flap's edge came out lumpy."""
    edges = [e for e in mesh.edges if e.is_boundary and all(v in opening for v in e.verts)]
    starts = np.array([e.verts[0].co[:] for e in edges])
    ends = np.array([e.verts[1].co[:] for e in edges])
    points = np.array([v.co[:] for v in mesh.verts])
    span = ends - starts
    length = np.maximum((span * span).sum(1), 1e-12)
    best = np.full(len(points), np.inf)
    for chunk in range(0, len(points), 512):
        p = points[chunk:chunk + 512, None, :]
        share = np.clip(((p - starts) * span).sum(2) / length, 0, 1)
        nearest = starts + share[..., None] * span
        best[chunk:chunk + 512] = np.sqrt(((p - nearest) ** 2).sum(2)).min(1)
    return best


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
    if at == "neck":
        straighten(mesh, opening)
        mesh.to_mesh(obj.data)
        obj.data.update()
    heights = np.array([v.co.z for v in opening])
    low, high = heights.min(), heights.max()
    distance = opening_distances(mesh, opening)
    z = np.array([v.co.z for v in mesh.verts])
    if at == "neck":
        t = (z - low) / max(high - low, 1e-6)
        # Straight, as a lapel's edge runs from the top button
        ramp = np.clip(t / TAPER, 0, 1)
        notch = 1 - NOTCH_DEPTH * np.exp(-((t - NOTCH_AT) / NOTCH_SPAN) ** 2)
        collar = np.where(t > NOTCH_AT, COLLAR_SHARE, 1.0)
        widths = width * ramp * np.minimum(notch, np.maximum(collar, notch * (t <= NOTCH_AT)))
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
    turn_down(piece, after_d, after_n, lift * rise, RISE * width)
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


def turn_down(piece, distance, normals, lifts, near):
    """Turn a flap's outer edge down toward the garment (see RIM): a strip of faces from each
    open edge away from the fold, given each vertex's distance from the opening, the garment's
    normal under it and how far it was lifted, facing away from the opening."""
    mesh = bmesh.new()
    mesh.from_mesh(piece.data)
    mesh.verts.ensure_lookup_table()
    outer = [e for e in mesh.edges if e.is_boundary
             and all(distance[v.index] > near for v in e.verts)]
    if not outer:
        mesh.free()
        return
    made = bmesh.ops.extrude_edge_only(mesh, edges=outer)["geom"]
    new_verts = [g for g in made if isinstance(g, bmesh.types.BMVert)]
    rim_faces = [g for g in made if isinstance(g, bmesh.types.BMFace)]
    # Each new vertex comes from the old one it was extruded from, at the same place
    for vertex in new_verts:
        source = min((e.other_vert(vertex) for e in vertex.link_edges
                      if e.other_vert(vertex).index < len(distance)),
                     key=lambda v: (v.co - vertex.co).length, default=None)
        if source is None:
            continue
        i = source.index
        vertex.co = vertex.co - Vector(normals[i]) * (lifts[i] * RIM)
    for face in rim_faces:
        face.normal_update()
        middle = face.calc_center_median()
        # Away from the opening: along the way the distance grows, which the rim's top edge's
        # vertices show against the flap's faces behind them
        inner = [f for e in face.edges for f in e.link_faces if f is not face]
        if inner:
            behind = sum((f.calc_center_median() for f in inner), Vector()) / len(inner)
            if face.normal.dot(middle - behind) < 0:
                face.normal_flip()
    mesh.to_mesh(piece.data)
    mesh.free()
    piece.data.update()


def _nearest_indices(points, targets):
    """The index of the nearest target to each point."""
    from mathutils.kdtree import KDTree
    tree = KDTree(len(targets))
    for i, p in enumerate(targets):
        tree.insert(p, i)
    tree.balance()
    return np.array([tree.find(p)[1] for p in points])
