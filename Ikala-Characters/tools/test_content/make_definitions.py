"""Write character definitions for the test art: a part per mesh, and a few designs.

The test art is the generated mannequin and the modular RPG characters, exported as glTF into the
asset editor's test content (see tools/blender). This reads their meshes and writes, beside them:

    part/mannequin.json   a body part per mannequin mesh, one per biped slot
    part/rpg_human.json   the RPG body, heads, hands and feet as body parts, and hair, armor,
                          boots, gloves and helmets as layers, male and female meshes together
    design/test.json      a mannequin, an RPG man with a hand flame and an RPG woman with an aura
    effects/*.effect.yml  graphics particle effects: a flame, motes, sparks, embers and dust
    fx/test.json          an effect definition for each
    animationSet/test.json the biped's actions, from the RPG clips and the mannequin's walk
    animStyle/test.json   a style per RPG weapon, overriding combat idles, attacks and casts
    item/test.json        a held item per RPG weapon (weapons/weapons.glb, see export_weapons.py)

all in the "test" namespace. Load them in the editor by listing the folder in Ikala-Characters'
config, as test=<folder>. Mesh paths are relative to the folder, as file.glb#mesh name.

    python make_definitions.py <test_content/characters folder>
"""

import argparse
import json
import pathlib
import re
import struct

NAMESPACE = "test"
PLAN = "ikala:biped"
TEMPLATE = "ikala:mat/standard"

MANNEQUIN = "mannequin/mannequin.glb"
RPG_FILES = {"male": "rpg_human/rpg_male.glb", "female": "rpg_human/rpg_female.glb"}

# Where the RPG kit's pieces go on the biped, by the word their mesh names end in
RPG_BODY = {
    "body": ["torso", "pelvis", "upperArm.L", "upperArm.R", "lowerArm.L", "lowerArm.R",
             "upperLeg.L", "upperLeg.R", "lowerLeg.L", "lowerLeg.R"],
    "head": ["head"],
    "hands": ["hand.L", "hand.R"],
    "feet": ["foot.L", "foot.R"],
}
RPG_LAYERS = {
    "hair": ["hair"],
    "armor": ["jacket", "pants"],
    "boots": ["boots"],
    "gloves": ["gloves"],
    "helmet": ["helmet"],
}

# Which palette channel each tint mask channel takes
SKIN = {"r": "skin"}
HAIR = {"r": "hair"}
COSTUME = {"r": "primary", "g": "secondary", "b": "accent"}


def read_meshes(path):
    """The meshes in a .glb, as (name, sorted material names)."""
    data = path.read_bytes()
    length = struct.unpack_from("<I", data, 12)[0]
    gltf = json.loads(data[20:20 + length])
    materials = gltf.get("materials", [])
    meshes = []
    for mesh in gltf["meshes"]:
        names = sorted({materials[p["material"]]["name"]
                        for p in mesh["primitives"] if "material" in p})
        meshes.append((mesh["name"], names))
    return meshes


def material_slots(names, channels):
    """Material slots for some material names, all tinted the same way."""
    return [{"name": name, "template": TEMPLATE, "channels": channels} for name in names]


def mannequin_parts(root):
    """A body part per mannequin mesh, filling the slot named in the mesh's name."""
    parts = []
    for mesh, materials in read_meshes(root / MANNEQUIN):
        slot = mesh.removeprefix("mannequin_")
        parts.append({
            "id": f"{NAMESPACE}:part/mannequin/{slot.lower()}",
            "plan": PLAN,
            "kind": "body",
            "fills": [slot],
            "meshes": {"*": f"{MANNEQUIN}#{mesh}"},
            "materialSlots": material_slots(materials, SKIN),
            "tags": ["mannequin"],
        })
    return parts


def rpg_piece(name, variant):
    """What an RPG mesh is: its piece name without the variant, like head_2 or light_armor."""
    prefix = f"human_{variant}_"
    return name[len(prefix):] if name.startswith(prefix) else None


def rpg_parts(root):
    """A part per RPG piece, with the male and female meshes of it together."""
    pieces = {}
    for variant, file in RPG_FILES.items():
        for mesh, materials in read_meshes(root / file):
            piece = rpg_piece(mesh, variant)
            if piece is None:
                continue
            entry = pieces.setdefault(piece, {"meshes": {}, "materials": []})
            entry["meshes"][variant] = f"{file}#{mesh}"
            entry["materials"] += [m for m in materials if m not in entry["materials"]]

    parts = []
    for piece, entry in sorted(pieces.items()):
        # The last word before a number or "default" says what it is
        kind_word = re.sub(r"(_\d+|_default)$", "", piece).split("_")[-1]
        if kind_word in RPG_BODY:
            kind, fills = "body", RPG_BODY[kind_word]
            channels = SKIN
        elif kind_word in RPG_LAYERS:
            kind, fills = "layer", RPG_LAYERS[kind_word]
            channels = HAIR if kind_word == "hair" else COSTUME
        else:
            print("skipping", piece)
            continue
        parts.append({
            "id": f"{NAMESPACE}:part/rpg/{piece}",
            "plan": PLAN,
            "kind": kind,
            "fills": fills,
            "meshes": entry["meshes"],
            "materialSlots": material_slots(entry["materials"], channels),
            "tags": ["rpg"],
        })
    return parts


# Graphics particle effects, in Ikala-Graphics' YAML format (see ParticleEffectLoader), small
# enough to sit on a character. Unlit and glowing, so they read against any lighting. The
# flame takes on half the hand's speed, and the motes go along with the character.
EFFECTS = {
    "flame": """rate: 60
lifetime: [0.3, 0.6]
speed: [0.3, 0.6]
direction: [0, 1, 0]
spread: 20
spawnRadius: 0.03
acceleration: [0, 1.5, 0]
inheritVelocity: 0.5
size: [0.08, 0.02]
color:
  start: [1, 0.55, 0.2, 1]
  end: [1, 0.15, 0.02, 0]
emissive: 4
lit: false
""",
    "motes": """rate: 25
lifetime: [1.5, 2.5]
speed: [0.1, 0.3]
direction: [0, 1, 0]
spread: 60
spawnRadius: 0.5
drag: 0.5
follow: 1
size: [0.03, 0.01]
color:
  start: [0.6, 0.8, 1, 0.9]
  end: [0.6, 0.8, 1, 0]
emissive: 3
lit: false
""",
    "sparks": """rate: 30
lifetime: [0.2, 0.5]
speed: [1, 2.5]
direction: [0, 1, 0]
spread: 70
acceleration: [0, -9.81, 0]
size: [0.02, 0.01]
color:
  start: [1, 0.8, 0.4, 1]
  end: [1, 0.3, 0.05, 0]
emissive: 6
lit: false
collision: bounce
""",
    "embers": """rate: 15
lifetime: [1, 2]
speed: [0.2, 0.5]
direction: [0, 1, 0]
spread: 40
acceleration: [0, 0.4, 0]
drag: 0.3
size: [0.03, 0.01]
color:
  start: [1, 0.5, 0.15, 1]
  end: [0.8, 0.1, 0, 0]
emissive: 3
lit: false
""",
    "trail": """rate: 600
lifetime: [0.12, 0.2]
speed: [0, 0.05]
size: [0.05, 0.0]
color:
  start: [0.8, 0.9, 1, 0.8]
  end: [0.6, 0.8, 1, 0]
emissive: 3
lit: false
""",
    "dust": """rate: 20
burst: 6
lifetime: [0.4, 0.8]
speed: [0.3, 0.8]
direction: [0, 1, 0]
spread: 80
spawnRadius: 0.05
acceleration: [0, -0.5, 0]
drag: 2
size: [0.06, 0.15]
color:
  start: [0.55, 0.5, 0.45, 0.5]
  end: [0.55, 0.5, 0.45, 0]
""",
}

# Effect definitions' tags, by effect
EFFECT_TAGS = {"flame": ["hand"], "motes": ["aura"], "sparks": ["part"], "embers": ["aura"],
               "dust": ["event"], "trail": ["item"]}


def fx_definitions():
    """An effect definition for each particle effect."""
    return [
        {
            "id": f"{NAMESPACE}:fx/{name}",
            "effect": f"effects/{name}.effect.yml",
            "tags": EFFECT_TAGS[name],
        }
        for name in EFFECTS
    ]


def write_effects(root):
    """Write the particle effect files."""
    for name, text in EFFECTS.items():
        path = root / "effects" / f"{name}.effect.yml"
        path.parent.mkdir(parents=True, exist_ok=True)
        header = "# Written by Ikala-Characters/tools/test_content/make_definitions.py\n"
        path.write_text(header + text, encoding="utf8")
        print("wrote", path)


def designs():
    """A mannequin, an RPG man with a hand flame and an RPG woman with an aura."""
    mannequin_slots = ["head", "torso", "pelvis", "upperArm.L", "upperArm.R", "lowerArm.L",
                       "lowerArm.R", "hand.L", "hand.R", "upperLeg.L", "upperLeg.R",
                       "lowerLeg.L", "lowerLeg.R", "foot.L", "foot.R"]
    rpg = {
        "torso": f"{NAMESPACE}:part/rpg/body_default",
        "head": f"{NAMESPACE}:part/rpg/head_default",
        "hand.L": f"{NAMESPACE}:part/rpg/hands_default",
        "foot.L": f"{NAMESPACE}:part/rpg/feet_default",
    }
    return [
        {
            "id": f"{NAMESPACE}:design/mannequin",
            "plan": PLAN,
            "variant": "neutral",
            "parts": {slot: f"{NAMESPACE}:part/mannequin/{slot.lower()}"
                      for slot in mannequin_slots},
        },
        {
            "id": f"{NAMESPACE}:design/rpg_male",
            "plan": PLAN,
            "variant": "male",
            "style": f"{NAMESPACE}:style/weapon/two_handed_melee",
            "parts": rpg,
            "layers": {"hair": f"{NAMESPACE}:part/rpg/hair_1"},
            "fx": [{"fx": f"{NAMESPACE}:fx/flame", "bone": "hand.R", "offset": [0, 0.08, 0]}],
        },
        {
            "id": f"{NAMESPACE}:design/rpg_female",
            "plan": PLAN,
            "variant": "female",
            "parts": rpg,
            "layers": {"hair": f"{NAMESPACE}:part/rpg/hair_1"},
            "fx": [{"fx": f"{NAMESPACE}:fx/motes", "socket": "aura_center", "color": "primary"}],
        },
    ]


RPG_CLIPS = RPG_FILES["male"]


def clip(name, file=RPG_CLIPS, **settings):
    """A clip use: a clip in a file, with any settings like loop or speed."""
    return {"clip": f"{file}#{name}", **settings}


def action(*clips, **settings):
    """An action playing some clips, with any settings like mask or fade."""
    return {"clips": list(clips), **settings}


def once(*names):
    """An action that plays one of some RPG clips once, picked at random."""
    return action(*(clip(name, loop=False) for name in names))


def footsteps(left, right):
    """Footstep events at two times, each with a puff of dust at its foot."""
    dust = f"fx:{NAMESPACE}:fx/dust"
    return [
        {"at": left, "event": "foot.L.down"}, {"at": left, "event": dust, "bone": "foot.L"},
        {"at": right, "event": "foot.R.down"}, {"at": right, "event": dust, "bone": "foot.R"},
    ]


def animation_sets():
    """The biped's actions: the RPG clips, which retarget onto the biped, and the mannequin's walk.

    The RPG clips play in place, so the speeds are guesses at how fast they look like moving."""
    return [{
        "id": f"{NAMESPACE}:anim/biped",
        "plan": PLAN,
        "actions": {
            "idle": action(clip("idle")),
            "idle.combat": action(clip("idle_combat_unarmed")),
            "move": action(
                clip("walk", MANNEQUIN, speed=1.4, events=footsteps(0.0, 0.5)),
                clip("run", speed=4.5)),
            "jump": once("jump"),
            "death": once("death"),
            "cast": once("cast_unarmed_magic", "cast_unarmed_magic_2"),
            "channel": action(clip("channel_unarmed_magic")),
            "cast.buff": once("power_up", "power_up_2", "power_up_3"),
            "use.potion": action(clip("drink_potion", loop=False), mask="upperBody"),
            "gather": action(clip("gathering")),
            "emote.pray": once("pray"),
        },
    }]


# Weapon styles: the RPG clip suffix, and which actions its clips play
RPG_WEAPONS = {
    "main_hand_melee": ["idle.combat", "attack.melee", "channel"],
    "dual_wield_melee": ["idle.combat", "attack.melee", "channel"],
    "two_handed_melee": ["idle.combat", "attack.melee", "channel"],
    "two_handed_staff": ["idle.combat", "cast", "channel"],
    "main_hand_wand": ["idle.combat", "cast", "channel"],
    "two_handed_bow": ["idle.combat", "attack.ranged", "channel"],
    "two_handed_crossbow": ["idle.combat", "attack.ranged", "channel"],
    "off_hand_shield": ["block"],
}


def anim_styles(root):
    """A style per RPG weapon, from the clips named after it, like cast_two_handed_bow_2."""
    names = read_clips(root / RPG_CLIPS)
    styles = []
    for weapon, actions in RPG_WEAPONS.items():
        found = {
            "idle.combat": [n for n in names if n == f"idle_combat_{weapon}"],
            "channel": [n for n in names if n == f"channel_{weapon}"],
        }
        casts = [n for n in names if re.fullmatch(f"cast_{weapon}(_[0-9]+)?", n)]
        overrides = {}
        for name in actions:
            if name in found:
                if found[name]:
                    overrides[name] = action(clip(found[name][0]))
            elif casts:
                # Attacks, casts and blocks are all the RPG's cast clips for that weapon
                overrides[name] = once(*casts)
        styles.append({
            "id": f"{NAMESPACE}:style/weapon/{weapon}",
            "plan": PLAN,
            "actions": overrides,
            "tags": ["weapon"],
        })
    return styles


def read_clips(path):
    """The clip names in a .glb."""
    data = path.read_bytes()
    length = struct.unpack_from("<I", data, 12)[0]
    gltf = json.loads(data[20:20 + length])
    return [animation["name"] for animation in gltf.get("animations", [])]


WEAPONS = "weapons/weapons.glb"

# The RPG weapons: the equipment slots they go in, the hand they are drawn in, where they are
# sheathed, the style they bring, and a trail along the blade in the mesh's space, if any
# How weapons sit in each socket, as {"offset": [x, y, z] meters, "rotate": [x, y, z] degrees}.
# Weapons are exported with their grip at the origin and their blade, shaft or limbs along +Y (see
# export_weapons.py). The grip sockets point forward (+Y) with +Z up; the back and hip sockets point
# backward (+Y) with +Z up, and +X is the character's left.
POINT_FORWARD = {}
STAND_UP = {"rotate": [90, 0, 0]}
BACK_BLADE_DOWN = {"offset": [-0.12, 0.08, 0.25], "rotate": [-90, 0, -30]}
BACK_SHAFT = {"offset": [0, 0.08, 0], "rotate": [-90, 0, -30]}
BACK_UP = {"offset": [0, 0.1, 0], "rotate": [90, 0, 30]}
BACK_FLAT = {"offset": [0, 0.1, 0], "rotate": [90, 180, 0]}
HIP_BLADE_DOWN = {"offset": [-0.2, 0.02, 0.1], "rotate": [-90, 0, -15]}
HIP_FLAT = {"offset": [0.2, 0.05, 0], "rotate": [90, 180, 0]}

# The RPG weapons: the equipment slots they go in, the hand they are drawn in (None for either),
# how they sit drawn, where they are sheathed and how, the style they bring, and a trail along the
# blade, if any
RPG_ITEMS = {
    "main_hand_sword": (["mainHand", "offHand"], None, POINT_FORWARD, "hip_back", HIP_BLADE_DOWN,
                        "main_hand_melee", ([0, 0.15, 0], [0, 0.8, 0])),
    "two_handed_sword": (["mainHand"], "grip.R", POINT_FORWARD, "back", BACK_BLADE_DOWN,
                         "two_handed_melee", ([0, 0.3, 0], [0, 1.6, 0])),
    "shield": (["offHand"], "grip.L", STAND_UP, "back", BACK_FLAT, "off_hand_shield", None),
    "bow": (["offHand"], "grip.L", STAND_UP, "back", BACK_UP, "two_handed_bow", None),
    "crossbow": (["mainHand"], "grip.R", POINT_FORWARD, "back", BACK_SHAFT, "two_handed_crossbow",
                 None),
    "staff": (["mainHand"], "grip.R", STAND_UP, "back", BACK_SHAFT, "two_handed_staff", None),
    "wand": (["mainHand"], "grip.R", POINT_FORWARD, "hip_back", HIP_BLADE_DOWN, "main_hand_wand",
             None),
    "grimoire": (["offHand"], "grip.L", STAND_UP, "hip_back", HIP_FLAT, None, None),
}


def items():
    """A held item per RPG weapon, drawn in a hand and sheathed on the back or hip."""
    made = []
    for name, (slots, hand, drawn, sheath, sheathed, style, trail) in RPG_ITEMS.items():
        grip = hand or "grip.1h"
        attach = {sheath: sheathed}
        if drawn:
            attach[grip] = drawn
        look = {
            "mesh": f"{WEAPONS}#{name}",
            "states": {"drawn": grip, "sheathed": sheath},
            "attach": attach,
        }
        if style:
            look["style"] = f"{NAMESPACE}:style/weapon/{style}"
        if trail:
            look["fx"] = [{"fx": f"{NAMESPACE}:fx/trail", "offset": trail[0], "to": trail[1],
                           "when": "acting"}]
        made.append({
            "id": f"{NAMESPACE}:item/{name}",
            "slots": slots,
            "category": name,
            "looks": {PLAN: look},
            "tags": ["weapon"],
        })
    return made


def write(path, value, comment):
    """Write definitions as JSON with a comment saying where they came from."""
    path.parent.mkdir(parents=True, exist_ok=True)
    text = f"// {comment}\n// Written by Ikala-Characters/tools/test_content/make_definitions.py\n"
    path.write_text(text + json.dumps(value, indent=2) + "\n", encoding="utf8")
    print("wrote", path, len(value), "definitions")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("root", type=pathlib.Path, help="the test_content/characters folder")
    root = parser.parse_args().root
    write(root / "part" / "mannequin.json", mannequin_parts(root), "The mannequin's parts")
    write(root / "part" / "rpg_human.json", rpg_parts(root), "The modular RPG characters' parts")
    write(root / "design" / "test.json", designs(), "Designs made of the test parts")
    write(root / "fx" / "test.json", fx_definitions(), "Effects for the test characters")
    write_effects(root)
    write(root / "animationSet" / "test.json", animation_sets(), "The biped's actions")
    write(root / "animStyle" / "test.json", anim_styles(root), "A style per RPG weapon")
    write(root / "item" / "test.json", items(), "A held item per RPG weapon")


if __name__ == "__main__":
    main()
