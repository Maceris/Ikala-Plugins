"""Write character definitions for the test art: a part per mesh, and a few designs.

The test art is the generated mannequin and the modular RPG characters, exported as glTF into the
asset editor's test content (see tools/blender). This reads their meshes and writes, beside them:

    part/mannequin.json   a body part per mannequin mesh, one per biped slot
    part/rpg_human.json   the RPG body, heads, hands and feet as body parts, and hair, armor,
                          boots, gloves and helmets as layers, male and female meshes together
    design/test.json      a mannequin, an RPG man and an RPG woman

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


def designs():
    """A mannequin, an RPG man and an RPG woman."""
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
            "parts": rpg,
            "layers": {"hair": f"{NAMESPACE}:part/rpg/hair_1"},
        },
        {
            "id": f"{NAMESPACE}:design/rpg_female",
            "plan": PLAN,
            "variant": "female",
            "parts": rpg,
            "layers": {"hair": f"{NAMESPACE}:part/rpg/hair_1"},
        },
    ]


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


if __name__ == "__main__":
    main()
