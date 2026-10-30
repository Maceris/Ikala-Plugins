"""Write each mannequin's MakeHuman base mesh as a cage, for carrying fitted clothing from one
body type to another.

The male and female mannequins come from different game proxies (see mannequin.py), so their
vertices don't line up. Their MakeHuman base meshes do: both are the same mesh with different
targets applied. This builds each body as mannequin.py does, poses its base mesh the same way
(the A pose, scaled with the male's factor and stood on the ground), and writes its body
vertices and faces. Clothing bound to one cage with a Surface Deform modifier follows when the
cage takes the other's vertex positions.

Run it with Blender 5 or later, with the MPFB extension and the MakeHuman system asset pack:
    blender -b --python body_cages.py -- --out <mannequin folder>
which writes male_cage.npz and female_cage.npz beside the mannequins: "vertices" (Blender
space, Z up, facing -Y, meters, like the mannequins' .blend files) and "faces" (triangles).
"""

import argparse
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mannequin  # noqa: E402  (beside this script; it needs bpy and MPFB loaded first)

# The base mesh's vertex group holding the body itself, not MakeHuman's helper geometry
BODY_GROUP = "body"


def body_triangles(basemesh, keep):
    """The base mesh's faces whose vertices are all in keep, as triangles of old indices."""
    triangles = []
    for polygon in basemesh.data.polygons:
        ids = list(polygon.vertices)
        if all(keep[i] for i in ids):
            for k in range(1, len(ids) - 1):
                triangles.append((ids[0], ids[k], ids[k + 1]))
    return np.array(triangles, dtype=np.int32)


def cage(gender, proxy_name, factor):
    """One body's posed base mesh and the scale factor used, as the mannequin is built."""
    basemesh, rig, proxy, eyes = mannequin.make_body(gender, proxy_name)
    groups = {g.name: g.index for g in basemesh.vertex_groups}
    if BODY_GROUP not in groups:
        raise SystemExit("The base mesh has no '%s' group; it has %s" % (BODY_GROUP,
                                                                          sorted(groups)[:40]))
    keep = np.zeros(len(basemesh.data.vertices), dtype=bool)
    for v in basemesh.data.vertices:
        if any(g.group == groups[BODY_GROUP] and g.weight > 0 for g in v.groups):
            keep[v.index] = True

    mannequin.a_pose(rig, [proxy, eyes, basemesh])
    skin = mannequin.vertices(proxy)
    if factor is None:
        factor = mannequin.MALE_HEIGHT / float(skin[:, 2].max() - skin[:, 2].min())
    ground = float(skin[:, 2].min())
    co = mannequin.vertices(basemesh)
    co[:, 2] -= ground
    co *= factor

    triangles = body_triangles(basemesh, keep)
    used = np.flatnonzero(keep)
    remap = -np.ones(len(keep), dtype=np.int64)
    remap[used] = np.arange(len(used))
    print("[cages] %s: %d body vertices of %d, %d triangles, %.3f m tall" % (
        "male" if gender == 1.0 else "female", len(used), len(keep), len(triangles),
        float(co[used, 2].max())))
    return co[used].astype(np.float32), remap[triangles].astype(np.int32), factor


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description="Write the mannequins' base mesh cages.")
    parser.add_argument("--out", required=True, help="The mannequin folder.")
    args = parser.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)
    factor = None
    shapes = {}
    for name in ("male", "female"):
        gender, proxy_name = mannequin.BODIES[name]
        vertices, faces, factor = cage(gender, proxy_name, factor)
        shapes[name] = (vertices.shape, faces.shape)
        np.savez(os.path.join(args.out, name + "_cage.npz"), vertices=vertices, faces=faces)
    if shapes["male"] != shapes["female"]:
        raise SystemExit("The cages don't match: %s" % shapes)
    print("[cages] wrote", args.out)


if __name__ == "__main__":
    main()
