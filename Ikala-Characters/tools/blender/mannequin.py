"""Build the test mannequin for the ikala:biped plan and export it as glTF.

The mannequin is a plain 2 m figure in a T pose, one mesh per body slot, so every
slot can be swapped and every seam ring checked. Its armature is built from the
rig definition, so the bone names, parents and kinds can't drift from it; this
script only adds where each bone sits. It also authors two procedural clips,
idle and walk.

Run it headless with Blender 3.3 or later:

    blender -b --factory-startup --python mannequin.py -- --rig <rig/biped.json> --out <folder>

Blender is Z up with the figure facing -Y and its left side on +X. The glTF
export turns that into Y up, facing +Z.
"""

import argparse
import json
import math
import os
import sys

import bmesh
import bpy
from mathutils import Quaternion, Vector

# Frames per second for the clips.
FPS = 30

# Vertices around each ring of a tube. Even, so seam rings match from either side.
RING_VERTICES = 12

# How fast the walk moves, in meters per second.
WALK_SPEED = 1.4

# How long one walk cycle (two steps) lasts, in seconds.
WALK_CYCLE = 1.0


def mirror(point):
    """The same point on the character's right side."""
    return (-point[0], point[1], point[2])


def bone_layout():
    """Where each bone's head and tail are, in meters, for a 2 m neutral figure.

    Left side bones are listed once and mirrored to the right.
    """
    layout = {
        "root": ((0, 0, 0), (0, 0, 0.1)),
        "hips": ((0, 0, 0.98), (0, 0, 1.08)),
        "spine": ((0, 0, 1.08), (0, 0, 1.26)),
        "chest": ((0, 0, 1.26), (0, 0, 1.46)),
        "neck": ((0, 0, 1.50), (0, 0, 1.62)),
        "head": ((0, 0, 1.62), (0, 0, 1.86)),
        "jaw": ((0, -0.01, 1.68), (0, -0.09, 1.63)),
        "lip.upper": ((0, -0.095, 1.685), (0, -0.11, 1.685)),
        "lip.lower": ((0, -0.09, 1.665), (0, -0.105, 1.665)),
        "face.brow": ((0, -0.09, 1.78), (0, -0.11, 1.78)),
        "face.nose": ((0, -0.10, 1.72), (0, -0.125, 1.72)),
        "face.chin": ((0, -0.085, 1.625), (0, -0.105, 1.625)),
        "face.cranium": ((0, 0, 1.62), (0, 0, 1.86)),
        "face.jaw": ((0, -0.01, 1.68), (0, -0.09, 1.63)),
        "girth.chest": ((0, 0, 1.26), (0, 0, 1.46)),
        "girth.waist": ((0, 0, 1.08), (0, 0, 1.26)),
        "girth.hips": ((0, 0, 0.98), (0, 0, 1.08)),
        "belly": ((0, -0.07, 1.12), (0, -0.13, 1.12)),
        "socket.head_top": ((0, 0, 1.98), (0, 0, 2.03)),
        "socket.face": ((0, -0.12, 1.74), (0, -0.17, 1.74)),
        "socket.back": ((0, 0.14, 1.36), (0, 0.19, 1.36)),
        "socket.hip_back": ((0, 0.13, 0.98), (0, 0.18, 0.98)),
        "socket.belt_front": ((0, -0.13, 0.98), (0, -0.18, 0.98)),
        "socket.chest_emblem": ((0, -0.13, 1.36), (0, -0.18, 1.36)),
        "socket.aura_center": ((0, 0, 1.0), (0, 0, 1.05)),
    }
    left = {
        "eye.L": ((0.034, -0.085, 1.745), (0.034, -0.11, 1.745)),
        "clavicle.L": ((0.02, -0.01, 1.45), (0.17, 0, 1.46)),
        "upperarm.L": ((0.17, 0, 1.46), (0.45, 0, 1.46)),
        "forearm.L": ((0.45, 0, 1.46), (0.71, 0, 1.46)),
        "hand.L": ((0.71, 0, 1.46), (0.80, 0, 1.46)),
        "thumb.01.L": ((0.725, -0.025, 1.45), (0.75, -0.05, 1.45)),
        "thumb.02.L": ((0.75, -0.05, 1.45), (0.77, -0.065, 1.45)),
        "thumb.03.L": ((0.77, -0.065, 1.45), (0.79, -0.075, 1.45)),
        "fingers.01.L": ((0.80, 0, 1.46), (0.85, 0, 1.46)),
        "fingers.02.L": ((0.85, 0, 1.46), (0.89, 0, 1.46)),
        "thigh.L": ((0.095, 0, 0.97), (0.095, 0, 0.52)),
        "shin.L": ((0.095, 0, 0.52), (0.095, 0.02, 0.09)),
        "foot.L": ((0.095, 0.02, 0.09), (0.095, -0.09, 0.025)),
        "toe.L": ((0.095, -0.09, 0.025), (0.095, -0.16, 0.025)),
        "brow.L": ((0.035, -0.095, 1.785), (0.035, -0.115, 1.785)),
        "lid.upper.L": ((0.034, -0.085, 1.752), (0.034, -0.105, 1.752)),
        "lid.lower.L": ((0.034, -0.085, 1.738), (0.034, -0.105, 1.738)),
        "mouth.corner.L": ((0.025, -0.09, 1.675), (0.025, -0.105, 1.675)),
        "face.cheek.L": ((0.05, -0.075, 1.71), (0.05, -0.095, 1.71)),
        "bust.L": ((0.085, -0.06, 1.36), (0.085, -0.12, 1.36)),
        "girth.upperarm.L": ((0.17, 0, 1.46), (0.45, 0, 1.46)),
        "muscle.upperarm.L": ((0.31, 0, 1.46), (0.36, 0, 1.46)),
        "girth.forearm.L": ((0.45, 0, 1.46), (0.71, 0, 1.46)),
        "girth.thigh.L": ((0.095, 0, 0.97), (0.095, 0, 0.52)),
        "muscle.thigh.L": ((0.095, 0, 0.75), (0.095, 0, 0.70)),
        "girth.shin.L": ((0.095, 0, 0.52), (0.095, 0.02, 0.09)),
        "socket.shoulder.L": ((0.22, 0, 1.53), (0.22, 0, 1.58)),
        "socket.grip.L": ((0.775, -0.02, 1.44), (0.775, -0.07, 1.44)),
        "socket.forearm_mount.L": ((0.58, 0, 1.51), (0.58, 0, 1.56)),
        "wing.01.L": ((0.06, 0.12, 1.40), (0.25, 0.22, 1.50)),
        "wing.02.L": ((0.25, 0.22, 1.50), (0.50, 0.30, 1.62)),
        "wing.03.L": ((0.50, 0.30, 1.62), (0.80, 0.36, 1.70)),
        "wing.04.L": ((0.80, 0.36, 1.70), (1.10, 0.40, 1.74)),
    }
    for name, (head, tail) in left.items():
        layout[name] = (head, tail)
        layout[name[:-2] + ".R"] = (mirror(head), mirror(tail))
    cape = [(0, 0.12, 1.48), (0, 0.14, 1.28), (0, 0.15, 1.06), (0, 0.16, 0.84), (0, 0.17, 0.62)]
    for i in range(4):
        layout["cape.0%d" % (i + 1)] = (cape[i], cape[i + 1])
    tail = [(0, 0.10, 0.96), (0, 0.22, 0.90), (0, 0.34, 0.82), (0, 0.46, 0.72), (0, 0.58, 0.60),
            (0, 0.68, 0.48), (0, 0.76, 0.36)]
    for i in range(6):
        layout["tail.0%d" % (i + 1)] = (tail[i], tail[i + 1])
    return layout


def read_rig(path):
    """Read the rig definition, skipping the // comment lines it may start with."""
    with open(path, encoding="utf-8") as file:
        text = "\n".join(line for line in file if not line.lstrip().startswith("//"))
    return json.loads(text)


def build_armature(rig, layout):
    """Make the armature, with every bone in the rig at its layout position."""
    names = [bone["name"] for bone in rig["bones"]]
    missing = [name for name in names if name not in layout]
    extra = [name for name in layout if name not in names]
    if missing or extra:
        raise SystemExit("The layout and rig disagree. Missing: %s. Not in the rig: %s"
                         % (missing, extra))

    data = bpy.data.armatures.new("ikala_biped")
    armature = bpy.data.objects.new("ikala_biped", data)
    bpy.context.scene.collection.objects.link(armature)
    bpy.context.view_layer.objects.active = armature
    bpy.ops.object.mode_set(mode="EDIT")
    for bone in rig["bones"]:
        edit = data.edit_bones.new(bone["name"])
        head, tail = layout[bone["name"]]
        edit.head = head
        edit.tail = tail
        edit.roll = 0
        # Sockets and shape bones carry no animation weights of their own here, but every
        # bone is exported so the palette matches the rig
        edit.use_deform = bone.get("kind", "deform") != "socket"
        if "parent" in bone:
            edit.parent = data.edit_bones[bone["parent"]]
    bpy.ops.object.mode_set(mode="OBJECT")
    return armature


def ring_basis(normal):
    """Two axes across a ring, picked the same way every time so seam rings match."""
    normal = Vector(normal).normalized()
    reference = Vector((0, 0, 1)) if abs(normal.z) < 0.9 else Vector((1, 0, 0))
    u = normal.cross(reference).normalized()
    v = normal.cross(u).normalized()
    return u, v


class Ring:
    """A ring of a tube: where it is, which way it faces, its size, and its skin weights."""

    def __init__(self, center, normal, radius, weights):
        self.center = Vector(center)
        self.normal = Vector(normal)
        # A number for round rings, or (across u, across v) for oval ones
        self.radius = radius if isinstance(radius, tuple) else (radius, radius)
        self.weights = weights

    def points(self):
        u, v = ring_basis(self.normal)
        return [self.center
                + u * (self.radius[0] * math.cos(2 * math.pi * k / RING_VERTICES))
                + v * (self.radius[1] * math.sin(2 * math.pi * k / RING_VERTICES))
                for k in range(RING_VERTICES)]


def seams():
    """The seam rings, shared by the meshes on either side, by name."""
    rings = {
        "neck": Ring((0, 0, 1.60), (0, 0, 1), 0.055, {"neck": 0.5, "head": 0.5}),
        "waist": Ring((0, 0, 1.08), (0, 0, 1), (0.13, 0.10), {"spine": 0.5, "hips": 0.5}),
    }
    for side, flip in (("L", 1), ("R", -1)):
        def at(x, y, z):
            return (flip * x, y, z)
        out = (flip, 0, 0)
        rings["shoulder." + side] = Ring(at(0.17, 0, 1.46), out, 0.055,
                                         {"clavicle." + side: 0.5, "upperarm." + side: 0.5})
        rings["elbow." + side] = Ring(at(0.45, 0, 1.46), out, 0.042,
                                      {"upperarm." + side: 0.5, "forearm." + side: 0.5})
        rings["wrist." + side] = Ring(at(0.71, 0, 1.46), out, (0.03, 0.022),
                                      {"forearm." + side: 0.5, "hand." + side: 0.5})
        rings["hip." + side] = Ring(at(0.095, 0, 0.90), (0, 0, -1), 0.075,
                                    {"hips": 0.5, "thigh." + side: 0.5})
        rings["knee." + side] = Ring(at(0.095, 0, 0.52), (0, 0, -1), 0.05,
                                     {"thigh." + side: 0.5, "shin." + side: 0.5})
        rings["ankle." + side] = Ring(at(0.095, 0.02, 0.10), (0, 0, -1), 0.035,
                                      {"shin." + side: 0.5, "foot." + side: 0.5})
    return rings


class MeshBuilder:
    """Collects tubes, discs and caps for one mesh, with skin weights per vertex."""

    def __init__(self):
        self.vertices = []
        self.weights = []
        self.faces = []

    def ring(self, ring):
        start = len(self.vertices)
        self.vertices.extend(ring.points())
        self.weights.extend([ring.weights] * RING_VERTICES)
        return start

    def tube(self, rings, cap_start=False, cap_end=False):
        """Join rings in order with quads, optionally closing either end.

    The rings' normals must all point the same way along the tube, or the quads twist.
    """
        starts = [self.ring(ring) for ring in rings]
        for a, b in zip(starts, starts[1:]):
            for k in range(RING_VERTICES):
                n = (k + 1) % RING_VERTICES
                self.faces.append((a + k, a + n, b + n, b + k))
        if cap_start:
            self.cap(starts[0], rings[0])
        if cap_end:
            self.cap(starts[-1], rings[-1])

    def cap(self, start, ring):
        """Close a ring with a fan around its center."""
        center = len(self.vertices)
        self.vertices.append(ring.center.copy())
        self.weights.append(ring.weights)
        for k in range(RING_VERTICES):
            self.faces.append((start + k, start + (k + 1) % RING_VERTICES, center))

    def disc(self, ring):
        """A seam ring on its own, filled in, where a limb meets the side of the body."""
        self.cap(self.ring(ring), ring)

    def build(self, name, armature, material):
        mesh = bpy.data.meshes.new(name)
        mesh.from_pydata([tuple(v) for v in self.vertices], [], self.faces)
        mesh.update()
        work = bmesh.new()
        work.from_mesh(mesh)
        bmesh.ops.recalc_face_normals(work, faces=work.faces)
        work.to_mesh(mesh)
        work.free()
        mesh.materials.append(material)
        for polygon in mesh.polygons:
            polygon.use_smooth = True

        obj = bpy.data.objects.new(name, mesh)
        bpy.context.scene.collection.objects.link(obj)
        obj.parent = armature
        modifier = obj.modifiers.new("Armature", "ARMATURE")
        modifier.object = armature
        groups = {}
        for index, weights in enumerate(self.weights):
            total = sum(weights.values())
            for bone, weight in weights.items():
                if bone not in groups:
                    groups[bone] = obj.vertex_groups.new(name=bone)
                groups[bone].add([index], weight / total, "REPLACE")
        return obj


def build_meshes(armature):
    """One mesh per body slot, joined at the shared seam rings."""
    material = bpy.data.materials.new("mannequin")
    material.diffuse_color = (0.6, 0.6, 0.62, 1)
    seam = seams()

    head = MeshBuilder()
    head.tube([seam["neck"]]
              + [Ring((0, 0, z), (0, 0, 1), r, {"face.cranium": 1})
                 for z, r in ((1.62, 0.07), (1.66, 0.085), (1.72, 0.095), (1.78, 0.095),
                              (1.84, 0.08), (1.90, 0.055), (1.95, 0.025))],
              cap_end=True)
    head.tube([Ring((0, -0.085, 1.72), (0, -1, 0), 0.016, {"face.nose": 1}),
               Ring((0, -0.12, 1.71), (0, -1, 0), 0.008, {"face.nose": 1})], cap_end=True)
    head.tube([Ring((0, -0.07, 1.64), (0, -1, -0.3), (0.04, 0.02), {"face.chin": 1}),
               Ring((0, -0.095, 1.62), (0, -1, -0.3), (0.03, 0.015), {"face.chin": 1})],
              cap_end=True)
    head.build("mannequin_head", armature, material)

    torso = MeshBuilder()
    torso.tube([seam["waist"],
                Ring((0, 0, 1.18), (0, 0, 1), (0.135, 0.10), {"girth.waist": 1}),
                Ring((0, 0, 1.30), (0, 0, 1), (0.15, 0.11), {"girth.chest": 1}),
                Ring((0, 0, 1.42), (0, 0, 1), (0.17, 0.10), {"girth.chest": 1}),
                Ring((0, 0, 1.47), (0, 0, 1), (0.15, 0.09), {"chest": 1}),
                Ring((0, 0, 1.50), (0, 0, 1), (0.09, 0.07), {"chest": 0.5, "neck": 0.5}),
                seam["neck"]])
    torso.disc(seam["shoulder.L"])
    torso.disc(seam["shoulder.R"])
    torso.build("mannequin_torso", armature, material)

    pelvis = MeshBuilder()
    pelvis.tube([seam["waist"],
                 Ring((0, 0, 1.00), (0, 0, 1), (0.15, 0.11), {"girth.hips": 1}),
                 Ring((0, 0, 0.90), (0, 0, 1), (0.15, 0.10), {"hips": 1})], cap_end=True)
    pelvis.disc(seam["hip.L"])
    pelvis.disc(seam["hip.R"])
    pelvis.build("mannequin_pelvis", armature, material)

    for side, flip in (("L", 1), ("R", -1)):
        def ring(x, y, z, normal, radius, weights):
            return Ring((flip * x, y, z), (flip * normal[0], normal[1], normal[2]), radius,
                        weights)
        out = (1, 0, 0)

        upper = MeshBuilder()
        upper.tube([seam["shoulder." + side],
                    ring(0.31, 0, 1.46, out, 0.05, {"muscle.upperarm." + side: 1}),
                    seam["elbow." + side]])
        upper.build("mannequin_upperArm." + side, armature, material)

        lower = MeshBuilder()
        lower.tube([seam["elbow." + side],
                    ring(0.58, 0, 1.46, out, 0.04, {"girth.forearm." + side: 1}),
                    seam["wrist." + side]])
        lower.build("mannequin_lowerArm." + side, armature, material)

        hand = MeshBuilder()
        hand.tube([seam["wrist." + side],
                   ring(0.76, 0, 1.46, out, (0.045, 0.02), {"hand." + side: 1}),
                   ring(0.82, 0, 1.46, out, (0.04, 0.016), {"fingers.01." + side: 1}),
                   ring(0.88, 0, 1.46, out, (0.03, 0.012), {"fingers.02." + side: 1})],
                  cap_end=True)
        hand.tube([ring(0.73, -0.03, 1.45, (0.4, -1, 0), 0.012, {"thumb.01." + side: 1}),
                   ring(0.765, -0.06, 1.45, (0.6, -1, 0), 0.011, {"thumb.02." + side: 1}),
                   ring(0.79, -0.075, 1.45, (0.8, -0.6, 0), 0.009, {"thumb.03." + side: 1})],
                  cap_start=True, cap_end=True)
        hand.build("mannequin_hand." + side, armature, material)

        down = (0, 0, -1)
        thigh = MeshBuilder()
        thigh.tube([seam["hip." + side],
                    ring(0.095, 0, 0.72, down, 0.07, {"muscle.thigh." + side: 1}),
                    seam["knee." + side]])
        thigh.build("mannequin_upperLeg." + side, armature, material)

        shin = MeshBuilder()
        shin.tube([seam["knee." + side],
                   ring(0.095, 0.01, 0.32, down, 0.048, {"girth.shin." + side: 1}),
                   seam["ankle." + side]])
        shin.build("mannequin_lowerLeg." + side, armature, material)

        forward = (0, -1, 0)
        foot = MeshBuilder()
        foot.tube([seam["ankle." + side],
                   ring(0.095, 0.0, 0.05, (0, -1, -1), (0.04, 0.045), {"foot." + side: 1}),
                   ring(0.095, -0.07, 0.035, forward, (0.04, 0.03), {"foot." + side: 1}),
                   ring(0.095, -0.15, 0.025, forward, (0.035, 0.022), {"toe." + side: 1})],
                  cap_end=True)
        foot.build("mannequin_foot." + side, armature, material)


def rest_rotation(armature, bone):
    """A bone's rest orientation in armature space."""
    return armature.data.bones[bone].matrix_local.to_quaternion()


def local_rotation(armature, bone, axis, degrees):
    """A pose rotation turning a bone about an armature space axis, ignoring its parents' poses."""
    world = Quaternion(Vector(axis), math.radians(degrees))
    rest = rest_rotation(armature, bone)
    return rest.inverted() @ world @ rest


def key_rotation(armature, bone, frame, axis, degrees, base=None):
    """Key a bone's rotation, turning it after an optional base pose rotation."""
    pose_bone = armature.pose.bones[bone]
    rotation = local_rotation(armature, bone, axis, degrees)
    if base is not None:
        rotation = rotation @ base
    pose_bone.rotation_mode = "QUATERNION"
    pose_bone.rotation_quaternion = rotation
    pose_bone.keyframe_insert("rotation_quaternion", frame=frame, group=bone)


def key_location(armature, bone, frame, offset):
    """Key a bone's location, given as an armature space offset from rest."""
    pose_bone = armature.pose.bones[bone]
    pose_bone.location = rest_rotation(armature, bone).inverted() @ Vector(offset)
    pose_bone.keyframe_insert("location", frame=frame, group=bone)


def arms_down(armature, side, degrees):
    """The rotation that lowers an arm from the T pose by some degrees."""
    flip = 1 if side == "L" else -1
    return local_rotation(armature, "upperarm." + side, (0, 1, 0), flip * degrees)


def make_idle(armature):
    """Two seconds of standing: arms down, breathing, a slight sway of the head."""
    action = bpy.data.actions.new("idle")
    armature.animation_data.action = action
    frames = 2 * FPS
    for frame in (0, frames // 2, frames):
        breath = math.sin(2 * math.pi * frame / frames)
        for side in "LR":
            key_rotation(armature, "upperarm." + side, frame, (0, 1, 0), 0,
                         base=arms_down(armature, side, 72 + 2 * breath))
        key_rotation(armature, "chest", frame, (1, 0, 0), 1.5 * breath)
        key_rotation(armature, "head", frame, (0, 0, 1), 3 * breath)
    return action


def make_walk(armature):
    """One walk cycle of two steps, moving forward at the walk speed."""
    action = bpy.data.actions.new("walk")
    armature.animation_data.action = action
    frames = int(WALK_CYCLE * FPS)
    steps = 8
    for step in range(steps + 1):
        frame = frames * step // steps
        phase = 2 * math.pi * step / steps
        swing = math.sin(phase)
        # Forward is -Y; the root carries the motion, which scales with leg length
        key_location(armature, "root", frame, (0, -WALK_SPEED * WALK_CYCLE * step / steps, 0))
        for side, sign in (("L", 1), ("R", -1)):
            # Turning about +X swings a hanging limb backward, toward +Y
            leg = sign * swing
            key_rotation(armature, "thigh." + side, frame, (1, 0, 0), 25 * leg)
            # The knee bends back while the leg swings forward, as its angle falls
            key_rotation(armature, "shin." + side, frame, (1, 0, 0),
                         40 * max(0.0, -sign * math.cos(phase)))
            key_rotation(armature, "foot." + side, frame, (1, 0, 0), 10 * leg)
            key_rotation(armature, "upperarm." + side, frame, (1, 0, 0), -18 * leg,
                         base=arms_down(armature, side, 75))
        key_rotation(armature, "spine", frame, (0, 0, 1), 4 * swing)
    return action


def stash(armature, actions):
    """Put each action on its own NLA track, which the exporter writes as one animation each."""
    data = armature.animation_data
    data.action = None
    for action in actions:
        track = data.nla_tracks.new()
        track.name = action.name
        track.strips.new(action.name, int(action.frame_range[0]), action)


def export(path):
    """Write everything as a binary glTF file, with all bones and every clip."""
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
    }
    known = {p.identifier for p in bpy.ops.export_scene.gltf.get_rna_type().properties}
    bpy.ops.export_scene.gltf(**{key: value for key, value in options.items() if key in known})


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description="Build and export the biped mannequin.")
    parser.add_argument("--rig", required=True, help="The ikala:rig/biped definition.")
    parser.add_argument("--out", required=True, help="The folder to write mannequin.glb to.")
    args = parser.parse_args(argv)

    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.context.scene.render.fps = FPS

    armature = build_armature(read_rig(args.rig), bone_layout())
    build_meshes(armature)
    armature.animation_data_create()
    stash(armature, [make_idle(armature), make_walk(armature)])

    out = os.path.abspath(args.out)
    os.makedirs(out, exist_ok=True)
    path = os.path.join(out, "mannequin.glb")
    export(path)
    blend = os.path.join(out, "mannequin.blend")
    bpy.ops.wm.save_as_mainfile(filepath=blend)
    print("Wrote", path, "and", blend)


# mannequin_human.py imports this for its rig, clips and export
if __name__ == "__main__":
    main()
