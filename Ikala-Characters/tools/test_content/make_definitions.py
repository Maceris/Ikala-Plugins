"""Write character definitions for the test art: a part per mesh, and a few designs.

The test art is the mannequins, exported as glTF into the asset editor's test content (see
tools/blender), the modular RPG kit's clips (animations/rpg_clips.glb, its skeleton and clips
only, see clips_only.py) and the kit's weapons. This reads them and writes, beside them:

    part/mannequin.json   a body part per mannequin slot, the male's and female's meshes by variant
    part/extensions.json  a tail, a pair of wings and a cape on the biped's extension chains
                          (extensions/test_extensions_<variant>.glb, see test_extensions.py)
    design/test.json      both mannequins, each again with a hand flame and a weapon style or an
                          aura, and with a tail and wings or a cape and a tail
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

# The mannequins from tools/blender/mannequin.py, by variant, and their eyes' material
MANNEQUIN_FILES = {"male": "mannequin/male.glb", "female": "mannequin/female.glb"}
MANNEQUIN_EYES = "mannequin_eyes"
# The hand-made chain parts from tools/blender/test_extensions.py, by variant
EXTENSION_FILES = {"male": "extensions/test_extensions_male.glb",
                   "female": "extensions/test_extensions_female.glb"}
# How much longer the test tail's chain is than the rig's, as test_extensions.py makes it
TAIL_LENGTH = 1.3
# The RPG kit's clips, without its characters, which retarget onto the biped
RPG_CLIPS = "animations/rpg_clips.glb"

# Which palette channel each tint mask channel takes
SKIN = {"r": "skin"}


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


# How seam cap meshes are named: <mesh>_cap_<seam>, see tools/blender/add_caps.py
CAP_INFIX = "_cap_"


def material_slots(names, channels):
    """Material slots for some material names, all tinted the same way."""
    return [{"name": name, "template": TEMPLATE, "channels": channels} for name in names]


def mannequin_parts(root):
    """A body part per mannequin slot, filling the slot named in the mesh's name: the male's and
    female's meshes each for their own variant, and the male's for the rest. The eyes take no
    palette color."""
    parts = []
    for mesh, materials in read_meshes(root / MANNEQUIN_FILES["male"]):
        # Seam caps go with their slot's part (PartAssembler's CAP_INFIX), not parts of their own
        if CAP_INFIX in mesh:
            continue
        slot = mesh.removeprefix("mannequin_")
        meshes = {variant: f"{file}#{mesh}" for variant, file in MANNEQUIN_FILES.items()}
        meshes["*"] = meshes["male"]
        parts.append({
            "id": f"{NAMESPACE}:part/mannequin/{slot.lower()}",
            "plan": PLAN,
            "kind": "body",
            "fills": [slot],
            "meshes": meshes,
            "materialSlots": [{"name": name, "template": TEMPLATE,
                               "channels": {} if name == MANNEQUIN_EYES else SKIN}
                              for name in materials],
            "tags": ["mannequin"],
        })
        # Left and right limbs go on together in the editor unless sides are chosen alone
        if slot.endswith((".L", ".R")):
            other = slot[:-2] + (".R" if slot.endswith(".L") else ".L")
            parts[-1]["pair"] = f"{NAMESPACE}:part/mannequin/{other.lower()}"
    return parts


def extension_parts():
    """The hand-made chain parts: a tail on a longer chain, a wing per side worn as a pair, and a
    cape layer that moves with the cape chain. Wings and capes both take the back, so they can't
    be worn together."""

    def meshes(name):
        found = {variant: f"{file}#{name}" for variant, file in EXTENSION_FILES.items()}
        found["*"] = found["male"]
        return found

    def material(name):
        return [{"name": f"{name}_material", "template": TEMPLATE}]

    tail = {
        "id": f"{NAMESPACE}:part/extension/tail",
        "plan": PLAN,
        "kind": "accessory",
        "extension": "tail",
        "rigid": False,
        "chainLength": [TAIL_LENGTH],
        "meshes": meshes("test_tail"),
        "materialSlots": material("test_tail"),
        "tags": ["test"],
    }
    wings = [
        {
            "id": f"{NAMESPACE}:part/extension/wing_{side.lower()}",
            "plan": PLAN,
            "kind": "accessory",
            "extension": f"wing.{side}",
            "rigid": False,
            "meshes": meshes(f"test_wing_{side.lower()}"),
            "materialSlots": material(f"test_wing_{side.lower()}"),
            "tags": ["test", "backDetail"],
            "pair": f"{NAMESPACE}:part/extension/wing_{other.lower()}",
        }
        for side, other in (("L", "R"), ("R", "L"))
    ]
    cape = {
        "id": f"{NAMESPACE}:part/extension/cape",
        "plan": PLAN,
        "kind": "layer",
        "fills": ["cape"],
        "extension": "cape",
        "meshes": meshes("test_cape"),
        "materialSlots": material("test_cape"),
        "tags": ["test", "cloth", "backDetail"],
    }
    return [tail, *wings, cape]


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
    """A male and a female mannequin, and each again: the man with a hand flame and a weapon
    style, the woman with an aura."""
    mannequin_slots = ["head", "torso", "pelvis", "upperArm.L", "upperArm.R", "lowerArm.L",
                       "lowerArm.R", "hand.L", "hand.R", "upperLeg.L", "upperLeg.R",
                       "lowerLeg.L", "lowerLeg.R", "foot.L", "foot.R"]
    mannequins = [
        {
            "id": f"{NAMESPACE}:design/mannequin_{variant}",
            "plan": PLAN,
            "variant": variant,
            "parts": {slot: f"{NAMESPACE}:part/mannequin/{slot.lower()}"
                      for slot in mannequin_slots},
        }
        for variant in MANNEQUIN_FILES
    ]
    male, female = mannequins
    return mannequins + [
        {
            **male,
            "id": f"{NAMESPACE}:design/fighter",
            "style": f"{NAMESPACE}:style/weapon/two_handed_melee",
            "fx": [{"fx": f"{NAMESPACE}:fx/flame", "bone": "hand.R", "offset": [0, 0.08, 0]}],
        },
        {
            **female,
            "id": f"{NAMESPACE}:design/caster",
            "fx": [{"fx": f"{NAMESPACE}:fx/motes", "socket": "aura_center", "color": "primary"}],
        },
        {
            **male,
            "id": f"{NAMESPACE}:design/winged",
            "extensions": {chain: f"{NAMESPACE}:part/extension/{name}" for chain, name in
                           (("tail", "tail"), ("wing.L", "wing_l"), ("wing.R", "wing_r"))},
        },
        {
            **female,
            "id": f"{NAMESPACE}:design/caped",
            "layers": {"cape": f"{NAMESPACE}:part/extension/cape"},
            "extensions": {"tail": f"{NAMESPACE}:part/extension/tail"},
        },
    ]


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
                clip("walk", MANNEQUIN_FILES["male"], speed=1.4, events=footsteps(0.0, 0.5)),
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
    write(root / "part" / "extensions.json", extension_parts(), "Parts on extension chains")
    write(root / "design" / "test.json", designs(), "Designs made of the test parts")
    write(root / "fx" / "test.json", fx_definitions(), "Effects for the test characters")
    write_effects(root)
    write(root / "animationSet" / "test.json", animation_sets(), "The biped's actions")
    write(root / "animStyle" / "test.json", anim_styles(root), "A style per RPG weapon")
    write(root / "item" / "test.json", items(), "A held item per RPG weapon")


if __name__ == "__main__":
    main()
