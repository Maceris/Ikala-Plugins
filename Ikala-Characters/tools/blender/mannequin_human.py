"""Build realistic mannequins for the ikala:biped plan from MakeHuman bodies, and export them.

A male and a female body come from MPFB (MakeHuman for Blender; its base mesh, targets and the
male1591 and female1605 game proxies are CC0), each as the low-poly proxy plus eyes. They are
put in the rig's T pose, the male scaled to 2 m and the female by the same factor, and given
the ikala:rig/biped armature: its bones placed from MPFB's own rig and MakeHuman's face
landmarks, its weights carried over from MPFB's and then shared out to the shape bones (girth,
muscle, bust, belly and the face) by zone, so the plan's sliders work. Each body is cut into
the plan's body slots along seam planes; the two sides of a cut share its edge loop exactly,
and every loop is written down for fitting generated body parts to later. The idle and walk
clips come from mannequin.py.

Run it with Blender 5 or later with the MPFB extension and the MakeHuman system asset pack:

    blender -b --python mannequin_human.py -- --rig <rig/biped.json> --plan <bodyPlan/biped.json>
        --out <folder>

which writes male.glb, female.glb, their .blend files and male_seams.json, female_seams.json.
Blender is Z up with the figure facing -Y and its left side on +X; the glTF export turns that
into Y up, facing +Z.
"""

import argparse
import json
import math
import os
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mannequin as procedural  # noqa: E402  (the rig, clips and export are shared)

from bl_ext.blender_org.mpfb.services.humanservice import HumanService  # noqa: E402
from bl_ext.blender_org.mpfb.services.locationservice import LocationService  # noqa: E402
from bl_ext.blender_org.mpfb.services.targetservice import TargetService  # noqa: E402

# The bodies, by the variant they are for: MakeHuman's gender value and the proxy used
BODIES = {"male": (1.0, "male1591"), "female": (0.0, "female1605")}
EYES = "low-poly"

# The male stands this tall; the female is scaled by the same factor
MALE_HEIGHT = 2.0

# MakeHuman's macro settings besides gender: a young adult of middling build
MACRO = {"age": 0.5, "muscle": 0.5, "weight": 0.5, "proportions": 0.8, "height": 0.5,
         "cupsize": 0.5, "firmness": 0.5}

# MPFB's game engine rig bones, by the ikala:biped bone their weights go to
WEIGHT_MAP = {"Root": "root", "pelvis": "hips", "spine_01": "spine", "spine_02": "chest",
              "spine_03": "chest", "neck_01": "neck", "head": "face.cranium"}
for _s, _side in (("l", "L"), ("r", "R")):
    WEIGHT_MAP.update({
        "clavicle_" + _s: "clavicle." + _side, "upperarm_" + _s: "upperarm." + _side,
        "lowerarm_" + _s: "forearm." + _side, "hand_" + _s: "hand." + _side,
        "thigh_" + _s: "thigh." + _side, "calf_" + _s: "shin." + _side,
        "foot_" + _s: "foot." + _side, "ball_" + _s: "toe." + _side})
    for _k in (1, 2, 3):
        WEIGHT_MAP["thumb_0%d_%s" % (_k, _s)] = "thumb.0%d.%s" % (_k, _side)
        for _finger in ("index", "middle", "ring", "pinky"):
            # The rig has one mitten for the four fingers, in two segments
            WEIGHT_MAP["%s_0%d_%s" % (_finger, _k, _s)] = "fingers.0%d.%s" % (min(_k, 2), _side)

# Vertices a skin vertex may be weighted to, as the skinned vertex format holds
MAX_INFLUENCES = 4

# How a limb segment's weight is shared with its shape bones: a plateau over the middle of the
# segment (fractions along the bone) goes to the girth bone, and a bump around the muscle's
# middle goes half to the muscle bone, so the joints still bend on the main bones
PLATEAU = (0.15, 0.35, 0.65, 0.85)
MUSCLE_AT, MUSCLE_SPREAD = 0.5, 0.18


def macro(gender):
    values = TargetService.get_default_macro_info_dict()
    values.update(MACRO)
    values["gender"] = gender
    return values


def select_only(*objects):
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = objects[0]


def vertices(obj):
    co = np.empty(len(obj.data.vertices) * 3)
    obj.data.vertices.foreach_get("co", co)
    co = co.reshape(-1, 3)
    m = np.array(obj.matrix_world)
    return co @ m[:3, :3].T + m[:3, 3]


def group_center(obj, name):
    """The mean world position of a vertex group's members."""
    group = obj.vertex_groups[name]
    co = vertices(obj)
    members = [v.index for v in obj.data.vertices for g in v.groups if g.group == group.index]
    return Vector(co[members].mean(0))


def smooth_ramp(x, a, b):
    """0 below a, 1 above b, smooth between."""
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def make_body(gender, proxy_name):
    """An MPFB human with the game engine rig, its proxy and eyes fitted and rigged."""
    bpy.ops.wm.read_factory_settings(use_empty=True)
    basemesh = HumanService.create_human(macro_detail_dict=macro(gender), scale=0.1)
    TargetService.bake_targets(basemesh)
    rig = HumanService.add_builtin_rig(basemesh, "game_engine", import_weights=True)
    data = LocationService.get_user_data()
    proxy = HumanService.add_mhclo_asset(
        os.path.join(data, "proxymeshes", proxy_name, proxy_name + ".proxy"), basemesh,
        asset_type="Proxymeshes", subdiv_levels=0, material_type="NONE")
    eyes = HumanService.add_mhclo_asset(
        os.path.join(data, "eyes", EYES, EYES + ".mhclo"), basemesh, asset_type="Eyes",
        subdiv_levels=0, material_type="NONE")
    for obj in (proxy, eyes):
        select_only(obj)
        # MPFB's objects go once the pose is baked, so these stand on their own
        bpy.ops.object.parent_clear(type="CLEAR_KEEP_TRANSFORM")
        for modifier in list(obj.modifiers):
            if modifier.type != "ARMATURE":
                bpy.ops.object.modifier_apply(modifier=modifier.name)
    return basemesh, rig, proxy, eyes


def landmarks(basemesh):
    """Face and body points from MakeHuman's helper joints and named groups, before posing."""
    points = {name: group_center(basemesh, "joint-" + name)
              for name in ("jaw", "mouth", "head", "head-2", "neck", "l-eye", "l-upperlid",
                           "l-lowerlid", "r-eye", "r-upperlid", "r-lowerlid")}
    co = vertices(basemesh)
    lips = basemesh.vertex_groups["lips"].index
    in_lips = np.array([any(g.group == lips for g in v.groups) for v in basemesh.data.vertices])
    points["lips"] = co[in_lips]
    nipple = basemesh.vertex_groups["nipple"].index
    in_nipple = np.array([any(g.group == nipple for g in v.groups)
                          for v in basemesh.data.vertices])
    nipples = co[in_nipple]
    points["nipple.L"] = Vector(nipples[nipples[:, 0] > 0].mean(0))
    points["nipple.R"] = Vector(nipples[nipples[:, 0] < 0].mean(0))
    return points


def point_bone(rig, name, direction):
    """Turn a pose bone about its head so it points along an armature space direction."""
    bone = rig.pose.bones[name]
    bpy.context.view_layer.update()
    head = bone.head.copy()
    turn = (bone.tail - bone.head).normalized().rotation_difference(Vector(direction))
    bone.matrix = (Matrix.Translation(head) @ turn.to_matrix().to_4x4()
                   @ Matrix.Translation(-head) @ bone.matrix)
    bpy.context.view_layer.update()


def twist_bone(rig, name, degrees):
    """Turn a pose bone about its own length."""
    bone = rig.pose.bones[name]
    bpy.context.view_layer.update()
    head = bone.head.copy()
    axis = (bone.tail - bone.head).normalized()
    turn = Matrix.Rotation(math.radians(degrees), 4, axis)
    bone.matrix = Matrix.Translation(head) @ turn @ Matrix.Translation(-head) @ bone.matrix
    bpy.context.view_layer.update()


def t_pose(rig, meshes):
    """Raise the arms straight out to the sides, palms down and thumbs forward, and bake the
    pose into the meshes."""
    for s, out in (("l", 1), ("r", -1)):
        for name in ("upperarm_", "lowerarm_", "hand_"):
            point_bone(rig, name + s, (out, 0, 0))
        # Turn the forearm until the thumb's root is in front of the hand's axis
        hand, thumb = rig.pose.bones["hand_" + s], rig.pose.bones["thumb_01_" + s]
        offset = thumb.head - hand.head
        across = Vector((0, offset.y, offset.z))
        angle = math.degrees(math.atan2(across.z, -across.y))
        twist_bone(rig, "lowerarm_" + s, out * angle)
    joints = {b.name: (rig.matrix_world @ b.head, rig.matrix_world @ b.tail)
              for b in rig.pose.bones}
    for obj in meshes:
        for modifier in list(obj.modifiers):
            if modifier.type == "ARMATURE":
                select_only(obj)
                bpy.ops.object.modifier_apply(modifier=modifier.name)
    return joints


def surface(skin, x, z, side, band=0.02):
    """The y of the body's front (side -1) or back (+1) surface near a point."""
    near = skin[(np.abs(skin[:, 0] - x) < band) & (np.abs(skin[:, 2] - z) < band)]
    return float(near[:, 1].min() if side < 0 else near[:, 1].max())


def layout(joints, marks, skin, height):
    """Where each ikala:biped bone's head and tail go, from MPFB's joints and the landmarks."""
    def head(name):
        return Vector(joints[name][0])

    def tail(name):
        return Vector(joints[name][1])

    def forward(point, distance):
        return point + Vector((0, -distance, 0))

    scale = height / 2.0  # the procedural mannequin's sizes were for a 2 m figure
    top = Vector((head("head").x, head("head").y, float(skin[:, 2].max())))
    chest_mid = (head("spine_02").z + head("neck_01").z) / 2
    belly_z = (head("pelvis").z + head("spine_02").z) / 2
    nose_band = skin[(np.abs(skin[:, 0]) < 0.006 * scale)
                     & (skin[:, 2] > marks["mouth"].z) & (skin[:, 2] < marks["l-eye"].z)]
    nose_tip = Vector(nose_band[nose_band[:, 1].argmin()])
    chin_band = skin[(np.abs(skin[:, 0]) < 0.006 * scale)
                     & (skin[:, 2] < marks["mouth"].z - 0.02 * scale)
                     & (skin[:, 2] > marks["mouth"].z - 0.06 * scale)]
    chin = Vector(chin_band[chin_band[:, 1].argmin()])
    lips = marks["lips"]
    mouth = marks["mouth"]
    split = float(np.median(lips[:, 2]))  # the mouth joint isn't always between the lips
    upper = Vector(lips[lips[:, 2] > split].mean(0))
    lower = Vector(lips[lips[:, 2] <= split].mean(0))
    upper.y, lower.y = lips[:, 1].min(), lips[:, 1].min()
    eye = (marks["l-eye"] + marks["r-eye"]) / 2
    face_front = float(skin[(np.abs(skin[:, 2] - eye.z) < 0.02 * scale)
                            & (np.abs(skin[:, 0]) < 0.03 * scale)][:, 1].min())

    bones = {
        "root": ((0, 0, 0), (0, 0, 0.1)),
        "hips": (head("pelvis"), head("spine_01")),
        "spine": (head("spine_01"), head("spine_02")),
        "chest": (head("spine_02"), head("neck_01")),
        "neck": (head("neck_01"), head("head")),
        "head": (head("head"), top),
        "jaw": (marks["jaw"], chin),
        "lip.upper": (upper + Vector((0, 0.012 * scale, 0)), upper),
        "lip.lower": (lower + Vector((0, 0.012 * scale, 0)), lower),
        "face.brow": (Vector((0, face_front + 0.015 * scale, eye.z + 0.035 * scale)),
                      Vector((0, face_front - 0.005 * scale, eye.z + 0.035 * scale))),
        "face.nose": (nose_tip + Vector((0, 0.025 * scale, 0)), nose_tip),
        "face.chin": (chin + Vector((0, 0.02 * scale, 0)), chin),
        "face.cranium": (head("head"), top),
        "face.jaw": (marks["jaw"], chin),
        "girth.chest": (head("spine_02"), head("neck_01")),
        "girth.waist": (head("spine_01"), head("spine_02")),
        "girth.hips": (head("pelvis"), head("spine_01")),
        "socket.head_top": (top, top + Vector((0, 0, 0.05))),
        "socket.face": (Vector((0, face_front, eye.z)), forward(Vector((0, face_front, eye.z)),
                                                                0.05)),
        "socket.aura_center": ((0, 0, height / 2), (0, 0, height / 2 + 0.05)),
    }
    belly_front = surface(skin, 0, belly_z, -1)
    bones["belly"] = (Vector((0, belly_front + 0.06 * scale, belly_z)),
                      Vector((0, belly_front, belly_z)))
    for name, z, side in (("socket.back", chest_mid, 1), ("socket.chest_emblem", chest_mid, -1),
                          ("socket.hip_back", head("pelvis").z, 1),
                          ("socket.belt_front", head("pelvis").z, -1)):
        y = surface(skin, 0, z, side)
        bones[name] = (Vector((0, y, z)), Vector((0, y + 0.05 * side, z)))

    for s, side, out in (("l", "L", 1), ("r", "R", -1)):
        e = marks[s + "-eye"]
        bones["eye." + side] = (e, forward(e, 0.025 * scale))
        for lid, mark in (("lid.upper.", s + "-upperlid"), ("lid.lower.", s + "-lowerlid")):
            bones[lid + side] = (marks[mark], forward(marks[mark], 0.02 * scale))
        brow = Vector((e.x, face_front + 0.005 * scale, e.z + 0.04 * scale))
        bones["brow." + side] = (brow, forward(brow, 0.02 * scale))
        cheek = Vector((e.x + out * 0.012 * scale, face_front + 0.02 * scale,
                        e.z - 0.04 * scale))
        bones["face.cheek." + side] = (cheek, forward(cheek, 0.02 * scale))
        corner_lips = lips[lips[:, 0] * out > 0]
        corner = Vector(corner_lips[(corner_lips[:, 0] * out).argmax()])
        bones["mouth.corner." + side] = (corner, forward(corner, 0.015 * scale))
        nipple = marks["nipple." + side]
        bones["bust." + side] = (nipple + Vector((0, 0.06 * scale, 0)), nipple)

        bones["clavicle." + side] = (head("clavicle_" + s), head("upperarm_" + s))
        bones["upperarm." + side] = (head("upperarm_" + s), head("lowerarm_" + s))
        bones["forearm." + side] = (head("lowerarm_" + s), head("hand_" + s))
        bones["hand." + side] = (head("hand_" + s), head("middle_01_" + s))
        bones["fingers.01." + side] = (head("middle_01_" + s), head("middle_02_" + s))
        bones["fingers.02." + side] = (head("middle_02_" + s), tail("middle_03_" + s))
        for k in (1, 2, 3):
            bones["thumb.0%d.%s" % (k, side)] = (head("thumb_0%d_%s" % (k, s)),
                                                 tail("thumb_0%d_%s" % (k, s)))
        bones["thigh." + side] = (head("thigh_" + s), head("calf_" + s))
        bones["shin." + side] = (head("calf_" + s), head("foot_" + s))
        bones["foot." + side] = (head("foot_" + s), head("ball_" + s))
        bones["toe." + side] = (head("ball_" + s), tail("ball_" + s))
        for limb in ("upperarm", "forearm", "thigh", "shin"):
            bones["girth.%s.%s" % (limb, side)] = bones["%s.%s" % (limb, side)]
        for limb in ("upperarm", "thigh"):
            a, b = (Vector(p) for p in bones["%s.%s" % (limb, side)])
            mid = a.lerp(b, MUSCLE_AT)
            bones["muscle.%s.%s" % (limb, side)] = (mid, mid + (b - a).normalized() * 0.05)

        shoulder = head("upperarm_" + s) + Vector((out * 0.04 * scale, 0, 0))
        above = skin[(np.abs(skin[:, 0] - shoulder.x) < 0.02) & (np.abs(skin[:, 1]) < 0.05)
                     & (np.abs(skin[:, 2] - shoulder.z) < 0.15)]
        shoulder.z = float(above[:, 2].max())
        bones["socket.shoulder." + side] = (shoulder, shoulder + Vector((0, 0, 0.05)))
        palm = head("hand_" + s).lerp(head("middle_01_" + s), 0.6)
        grip = palm + Vector((0, -0.02 * scale, -0.02 * scale))
        bones["socket.grip." + side] = (grip, forward(grip, 0.05))
        mount = head("lowerarm_" + s).lerp(head("hand_" + s), 0.5)
        mount.z += 0.05 * scale
        bones["socket.forearm_mount." + side] = (mount, mount + Vector((0, 0, 0.05)))

    # Wings, cape and tail keep the procedural mannequin's shape, moved onto this back
    old = procedural.bone_layout()
    back = surface(skin, 0, chest_mid, 1)
    shift = back - 0.11 * scale
    for name, (a, b) in old.items():
        if name.startswith(("wing.", "cape.", "tail.")):
            bones[name] = tuple(Vector((p[0] * scale, p[1] * scale + shift, p[2] * scale))
                                for p in (a, b))
    return {name: (tuple(a), tuple(b)) for name, (a, b) in bones.items()}


class Weights:
    """A mesh's skin weights as a matrix, vertices by bones, for sharing out between bones."""

    def __init__(self, obj, names):
        self.names = list(names)
        self.index = {name: i for i, name in enumerate(self.names)}
        self.w = np.zeros((len(obj.data.vertices), len(self.names)))
        self.co = vertices(obj)
        groups = {g.index: g.name for g in obj.vertex_groups}
        for v in obj.data.vertices:
            for g in v.groups:
                target = WEIGHT_MAP.get(groups[g.group])
                if target is not None:
                    self.w[v.index, self.index[target]] += g.weight

    def move(self, source, target, share):
        """Move a share (per vertex, 0 to 1) of one bone's weight to another."""
        s, t = self.index[source], self.index[target]
        moved = self.w[:, s] * share
        self.w[:, s] -= moved
        self.w[:, t] += moved

    def along(self, bone, bones):
        """How far along a bone each vertex lies, 0 at its head and 1 at its tail."""
        a, b = (np.array(p) for p in bones[bone])
        axis = b - a
        return (self.co - a) @ axis / (axis @ axis)

    def near(self, point, radius):
        """A falloff, 1 at a point and fading out over a radius."""
        d = np.linalg.norm(self.co - np.array(point), axis=1)
        return np.exp(-(d / radius) ** 2)

    def write(self, obj):
        """Keep the strongest influences, normalize, and replace the mesh's vertex groups."""
        w = self.w.copy()
        if w.shape[1] > MAX_INFLUENCES:
            weakest = np.argsort(w, axis=1)[:, :-MAX_INFLUENCES]
            np.put_along_axis(w, weakest, 0, axis=1)
        total = w.sum(1, keepdims=True)
        w = np.where(total > 0, w / np.where(total > 0, total, 1), 0)
        obj.vertex_groups.clear()
        for j, name in enumerate(self.names):
            members = np.nonzero(w[:, j] > 1e-4)[0]
            if len(members):
                group = obj.vertex_groups.new(name=name)
                for i in members:
                    group.add([int(i)], float(w[i, j]), "REPLACE")


def share_out(weights, bones, marks, scale):
    """Share the main bones' weights out to the shape bones, by zone."""
    def plateau(t):
        return smooth_ramp(t, PLATEAU[0], PLATEAU[1]) * (1 - smooth_ramp(t, PLATEAU[2],
                                                                           PLATEAU[3]))

    for side in "LR":
        for limb, muscle in (("upperarm", True), ("forearm", False), ("thigh", True),
                             ("shin", False)):
            main = "%s.%s" % (limb, side)
            t = weights.along(main, bones)
            zone = plateau(t)
            if muscle:
                bump = np.exp(-((t - MUSCLE_AT) / MUSCLE_SPREAD) ** 2) * 0.5
                weights.move(main, "muscle.%s.%s" % (limb, side), zone * bump)
                weights.move(main, "girth.%s.%s" % (limb, side),
                             zone * (1 - bump) / np.maximum(1 - zone * bump, 1e-6))
            else:
                weights.move(main, "girth.%s.%s" % (limb, side), zone)
        weights.move("chest", "bust." + side,
                     weights.near(bones["bust." + side][1], 0.08 * scale))
        weights.move("face.cranium", "face.cheek." + side,
                     weights.near(bones["face.cheek." + side][1], 0.025 * scale))
        weights.move("face.cranium", "brow." + side,
                     weights.near(bones["brow." + side][1], 0.015 * scale))
        weights.move("face.cranium", "mouth.corner." + side,
                     weights.near(bones["mouth.corner." + side][1], 0.01 * scale))
        for lid in ("lid.upper.", "lid.lower."):
            weights.move("face.cranium", lid + side,
                         weights.near(bones[lid + side][0], 0.008 * scale))

    for main, girth in (("chest", "girth.chest"), ("spine", "girth.waist"),
                        ("hips", "girth.hips")):
        weights.move(main, girth, plateau(weights.along(main, bones)))
    for source in ("spine", "girth.waist"):
        weights.move(source, "belly", weights.near(bones["belly"][1], 0.1 * scale))

    # The jaw takes the face below the mouth, ahead of its hinge; its chin, lips and shape
    # bone take their own parts of that
    co = weights.co
    jaw = np.array(bones["jaw"][0])
    below = 1 - smooth_ramp(co[:, 2], marks["mouth"].z - 0.01 * scale,
                            marks["mouth"].z + 0.005 * scale)
    ahead = 1 - smooth_ramp(co[:, 1], jaw[1] - 0.01 * scale, jaw[1] + 0.02 * scale)
    weights.move("face.cranium", "jaw", below * ahead)
    weights.move("jaw", "face.chin", weights.near(bones["face.chin"][1], 0.025 * scale))
    weights.move("jaw", "face.jaw", np.full(len(co), 0.6))
    weights.move("jaw", "lip.lower", weights.near(bones["lip.lower"][1], 0.012 * scale))
    weights.move("face.cranium", "lip.upper", weights.near(bones["lip.upper"][1], 0.012 * scale))
    weights.move("face.cranium", "face.nose", weights.near(bones["face.nose"][1], 0.025 * scale))
    weights.move("face.cranium", "face.brow", weights.near(bones["face.brow"][1], 0.03 * scale))


def scale_to(objects, factor):
    """Scale objects about the origin, and stand them on the ground."""
    for obj in objects:
        obj.matrix_world = Matrix.Scale(factor, 4) @ obj.matrix_world
        select_only(obj)
        bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)


def seam_planes(bones, skin, scale):
    """Each seam's plane: a point, a normal pointing toward the second named slot, and the
    region of the body (by side) it cuts in."""
    def bone_head(name):
        return Vector(bones[name][0])

    neck_a, neck_b = (Vector(p) for p in bones["neck"])
    planes = {
        "neck": (neck_a.lerp(neck_b, 0.4), (neck_b - neck_a).normalized(), 0),
        "waist": (bone_head("spine"), Vector((0, 0, 1)), 0),
    }
    crotch = skin[(np.abs(skin[:, 0]) < 0.01 * scale)
                  & (skin[:, 2] < bone_head("hips").z) & (skin[:, 2] > bone_head("hips").z
                                                          - 0.3 * scale)]
    # Low enough below the crotch that the cut goes round the thigh alone
    crotch_z = float(crotch[:, 2].min()) - 0.03 * scale
    for side, out in (("L", 1), ("R", -1)):
        wrist = min(bone_head("hand." + side).x * out, bone_head("thumb.01." + side).x * out)
        planes["shoulder." + side] = (bone_head("upperarm." + side), Vector((out, 0, 0)), out)
        planes["elbow." + side] = (bone_head("forearm." + side), Vector((out, 0, 0)), out)
        planes["wrist." + side] = (Vector((out * (wrist - 0.015 * scale), 0,
                                           bone_head("hand." + side).z)),
                                   Vector((out, 0, 0)), out)
        planes["hip." + side] = (Vector((0, 0, crotch_z)), Vector((0, 0, -1)), out)
        planes["knee." + side] = (bone_head("shin." + side), Vector((0, 0, -1)), out)
        ankle = bone_head("foot." + side) + Vector((0, 0, 0.02 * scale))
        planes["ankle." + side] = (ankle, Vector((0, 0, -1)), out)
    return planes


def cut_into_slots(body, eyes, plan, armature, planes):
    """Cut the body along the seam planes and give each piece to the slot whose bones weigh
    most in it. Returns the slot meshes and each seam's shared loop."""
    bm = bmesh.new()
    bm.from_mesh(body.data)
    loops = {}
    for name, (point, normal, side) in planes.items():
        faces = [f for f in bm.faces if side == 0 or f.calc_center_median().x * side > 0]
        if name == "neck" or name == "waist":
            faces = [f for f in faces if all(abs(v.co.x) < 0.2 for v in f.verts)]
        geom = list({e for f in faces for e in f.edges}) + faces + \
            list({v for f in faces for v in f.verts})
        result = bmesh.ops.bisect_plane(bm, geom=geom, plane_co=point, plane_no=normal,
                                        dist=1e-5)
        cut = [e for e in result["geom_cut"] if isinstance(e, bmesh.types.BMEdge)]
        loops[name] = cut
        print("[mannequin] seam %s: %d faces in reach, %d edges cut" % (name, len(faces),
                                                                        len(cut)))
    all_cut = list({e for edges in loops.values() for e in edges if e.is_valid})
    seam_points = {name: [tuple(v.co) for v in {v for e in edges if e.is_valid for v in e.verts}]
                   for name, edges in loops.items()}
    bmesh.ops.split_edges(bm, edges=all_cut)
    bm.to_mesh(body.data)
    bm.free()

    select_only(body)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.mesh.separate(type="LOOSE")
    bpy.ops.object.mode_set(mode="OBJECT")
    pieces = list(bpy.context.selected_objects)
    print("[mannequin] %d pieces" % len(pieces))

    slot_bones = {slot["id"]: set(slot["bones"]) for slot in plan["slots"]}
    by_slot = {}
    for piece in pieces:
        totals = {}
        groups = {g.index: g.name for g in piece.vertex_groups}
        for v in piece.data.vertices:
            for g in v.groups:
                totals[groups[g.group]] = totals.get(groups[g.group], 0) + g.weight
        score = {slot: sum(totals.get(b, 0) for b in bones) for slot, bones in slot_bones.items()}
        by_slot.setdefault(max(score, key=score.get), []).append(piece)
    by_slot.setdefault("head", []).append(eyes)

    meshes = {}
    for slot, objs in by_slot.items():
        select_only(*objs)
        if len(objs) > 1:
            bpy.ops.object.join()
        obj = bpy.context.view_layer.objects.active
        obj.name = obj.data.name = "mannequin_" + slot
        # Cutting blends both ends' weights into the new vertices, so limit them again
        bpy.ops.object.vertex_group_limit_total(limit=MAX_INFLUENCES)
        bpy.ops.object.vertex_group_normalize_all(lock_active=False)
        meshes[slot] = obj
    missing = sorted(set(slot_bones) - set(meshes))
    print("[mannequin] slots: %s%s" % (
        ", ".join("%s %d" % (s, len(m.data.polygons)) for s, m in sorted(meshes.items())),
        ("; missing " + ", ".join(missing)) if missing else ""))
    return meshes, seam_points


def build(gender_name, gender, proxy_name, rig_definition, plan, factor=None):
    """One mannequin. Gives the scale factor used."""
    basemesh, mpfb_rig, proxy, eyes = make_body(gender, proxy_name)
    marks = landmarks(basemesh)
    joints = t_pose(mpfb_rig, [proxy, eyes])
    skin = vertices(proxy)
    if factor is None:
        factor = MALE_HEIGHT / float(skin[:, 2].max() - skin[:, 2].min())
    ground = float(skin[:, 2].min())

    def place(p):
        return Vector(((p[0]) * factor, p[1] * factor, (p[2] - ground) * factor))

    joints = {k: (place(a), place(b)) for k, (a, b) in joints.items()}
    for key, value in list(marks.items()):
        marks[key] = (np.column_stack([value[:, 0], value[:, 1], value[:, 2] - ground]) * factor
                      if isinstance(value, np.ndarray) else place(value))
    for obj in (proxy, eyes):
        obj.matrix_world = Matrix.Translation((0, 0, -ground * factor)) @ obj.matrix_world
    scale_to([proxy, eyes], factor)
    skin = vertices(proxy)
    height = float(skin[:, 2].max())
    print("[mannequin] %s: %.3f m, %d triangles" % (
        gender_name, height, sum(len(p.vertices) - 2 for p in proxy.data.polygons)))

    bones = layout(joints, marks, skin, height)
    for obj in (basemesh, mpfb_rig):
        bpy.data.objects.remove(obj)
    armature = procedural.build_armature(rig_definition, bones)

    scale = height / 2.0
    names = [b["name"] for b in rig_definition["bones"]]
    for obj, side_eyes in ((proxy, False), (eyes, True)):
        weights = Weights(obj, names)
        if side_eyes:
            weights.w[:] = 0
            left = weights.co[:, 0] > 0
            weights.w[left, weights.index["eye.L"]] = 1
            weights.w[~left, weights.index["eye.R"]] = 1
        else:
            share_out(weights, bones, marks, scale)
        weights.write(obj)
        obj.parent = armature
        modifier = obj.modifiers.new("Armature", "ARMATURE")
        modifier.object = armature

    skin_material = bpy.data.materials.new("mannequin")
    skin_material.diffuse_color = (0.6, 0.6, 0.62, 1)
    eye_material = bpy.data.materials.new("mannequin_eyes")
    eye_material.diffuse_color = (0.9, 0.9, 0.9, 1)
    for obj, material in ((proxy, skin_material), (eyes, eye_material)):
        obj.data.materials.clear()
        obj.data.materials.append(material)
        for polygon in obj.data.polygons:
            polygon.use_smooth = True

    meshes, seams = cut_into_slots(proxy, eyes, plan, armature,
                                   seam_planes(bones, skin, scale))
    armature.animation_data_create()
    procedural.stash(armature, [procedural.make_idle(armature), procedural.make_walk(armature)])
    return factor, seams


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description="Build the realistic biped mannequins.")
    parser.add_argument("--rig", required=True, help="The ikala:rig/biped definition.")
    parser.add_argument("--plan", required=True, help="The ikala:biped body plan.")
    parser.add_argument("--out", required=True, help="The folder to write to.")
    parser.add_argument("--only", choices=sorted(BODIES), help="Build one body.")
    args = parser.parse_args(argv)
    rig_definition = procedural.read_rig(args.rig)
    plan = procedural.read_rig(args.plan)  # the same // comment rule
    out = os.path.abspath(args.out)
    os.makedirs(out, exist_ok=True)

    factor = None
    for name in ("male", "female"):
        gender, proxy_name = BODIES[name]
        if args.only and args.only != name:
            if factor is None:
                # The female's scale comes from the male's height, so measure him anyway
                factor = measure_male_factor()
            continue
        factor, seams = build(name, gender, proxy_name, rig_definition, plan, factor)
        bpy.context.scene.render.fps = procedural.FPS
        path = os.path.join(out, name + ".glb")
        procedural.export(path)
        bpy.ops.wm.save_as_mainfile(filepath=os.path.join(out, name + ".blend"))
        with open(os.path.join(out, name + "_seams.json"), "w", encoding="utf-8") as file:
            json.dump({"note": "Blender space: Z up, facing -Y, meters", "seams": seams}, file,
                      indent=1)
        print("[mannequin] wrote", path)


def measure_male_factor():
    gender, proxy_name = BODIES["male"]
    basemesh, rig, proxy, eyes = make_body(gender, proxy_name)
    t_pose(rig, [proxy, eyes])
    skin = vertices(proxy)
    return MALE_HEIGHT / float(skin[:, 2].max() - skin[:, 2].min())


if __name__ == "__main__":
    main()
