"""Make character parts in reviewed batches: written briefs, concept images, generated meshes,
and parts fitted to the biped that the character editor loads.

A batch is a folder with a manifest of briefs and what has been made and chosen for each. Each
command does one stage for every brief ready for it, and the review page records the choices
between stages, so expensive steps only run on what was kept:

    python partgen.py new <batch> <briefs.json> [--keep]
        Start a batch, or add briefs to one, from a JSON list of briefs.
    python partgen.py review <batch> [--port 8765]
        A page for keeping briefs, picking images, and keeping meshes and parts.
    python partgen.py images <batch> [--count 4] [--more]
        Concept images from SDXL through ComfyUI, for kept briefs that have none (or more).
    python partgen.py meshes <batch> [--type 512]
        Meshes through TRELLIS.2 from picked images, and renders of them for review.
    python partgen.py fit <batch> [--forms] [--standoff]
        Kept meshes fitted onto the mannequin: glbs and part definitions in the content folder.
        A body part is held to the body plan's extents (legs meet the floor, hands are hand
        sized, nothing stands out past the bulkiest clothing form) and given the clothing form
        it fits in, and how far it stands out over each slot it fills; a layer gets form targets,
        pushing it out over each body slot as far as the body part worn there stands out.
        --forms only gives layers already fitted their targets again, and --standoff only
        measures body parts already fitted again.
    python partgen.py capture <batch> [--redo]
        Fitted parts in the character editor, on each bare mannequin: front, side, back and a
        close-up, and dressed in a layer over them, rendered headless (no window) into
        <batch>/<id>/editor/ and shown on the review page under Parts. Needs the "editor"
        folder in config.json.
    python partgen.py status <batch>

Every stage takes --only <id,...> to limit it, and --all to skip waiting for reviews. Paths come
from config.json beside this file, which isn't committed: copy config.example.json to it and fill
in this computer's folders. Batches live in <work>/<batch>/. A brief is:

    {"id": "adventurer_hat", "kind": "accessory", "socket": "head_top",
     "description": "wide-brimmed weathered brown leather adventurer hat with a dark band",
     "tags": ["hat"], "fit": {"maxWidth": 2.6}}

Clothing names the body plan layer it goes in instead of a socket:

    {"id": "linen_shirt", "kind": "layer", "layer": "shirt",
     "description": "loose cream linen shirt with long sleeves and a wooden button placket"}

An outfit that fills several layers at once, like a bodysuit, lists them as "layers" instead,
such as ["shirt", "pants", "gloves"]; the character editor takes off whatever it overlaps.

Its images are painted over a render of the male mannequin, only where the layer covers the
body (grown a little for looser clothing), steered by the render's depth, so the bare body and
the pose stay as they are; see blender/render_guides.py and the layer's rule in layers.json.

"fit" is optional: values that replace the socket type's rule from sockets.json for this brief,
like a wider brim limit for a sun hat or a scale nudge after review. "sketch" is an optional
drawing, relative to the briefs file, that steers the shape where words don't: lines on a plain
background, any color; "sketchStrength" (default 0.7) is how hard, and "sketchEnd" (default 0.7)
the share of the steps it steers. Too strong and the image traces the drawing's lines instead of
making a real object of them.

A body part, like an alternative limb, names the body plan slots it fills instead, neighbors on
the left side (the right side is the left's mirror, by the slots' "mirrorOf"):

    {"id": "skeleton_arm", "kind": "body", "slots": ["upperArm.L", "lowerArm.L", "hand.L"],
     "seams": {"shoulder.L": "open"},
     "description": "a bare skeleton arm of old yellowed bone"}

"seams" says, for each of its outer seams (those of its slots that no other of its slots shares),
whether it welds to the mannequin's ring there ("weld", the default) or ends in its own closed
shape ("open", for skeletal or mechanical ends), where the welded neighbor shows a cap. Its images
are painted only over its slots, grown by the rule in bodyparts.json for its group of slots but
never past an outer seam's line, so the neighboring body stays the mannequin's.

Over the body plan's clothed slots (torso, pelvis, arms and legs, not hands, feet or the head), a
body part must fit under clothing: within the bulkiest form, a share of each slot's radius. One
standing out further is reined in toward the body, and rejected if that would take too much. A
part meant to be worn bare, like an arm covered in spikes, says "bare": true; it is left as it
is, and clothing covering its slots can't be worn with it.

A part on one of the body plan's extension chains, like a tail or a wing, names its chain (the
left one for wings; the right is mirrored from it) and how long it is, in meters along its chain,
since an object pictured on its own has no size of its own:

    {"id": "fox_tail", "kind": "extension", "chain": "tail", "length": 0.9,
     "description": "a bushy orange fox tail with a white tip"}

Its images picture it on its own, steered by the depth of a plain stand-in along the chain at
rest (a tapered tube for a tail, a flat membrane for a wing), seen as extensions.json's rule for
the chain says, so it comes out in the chain's pose; "length" (the rule's by default) sets the
stand-in's length. Fitting lines its mesh up along the chain (see blender/fit_extension.py),
scaled to its length, which sets the part's chainLength, and writes a wing's right side as the
left's mirror. Where the picture put the root end the other way round, a brief's "fit" can
point "root" at it (in TRELLIS.2's space: +X right, +Y up, +Z toward the viewer). A cape is a
part on the "cape" chain too, pictured on its own from behind; TRELLIS.2 makes capes round a body
it imagines, so instead of a mesh a cape takes its rule's "template": a sheet hung from the
mannequin's shoulders down its back, its picture projected on and cut to the picture's outline
(blender/fit_cape.py, with the cape cut out of the picture by cut_out.py). Its part is clothing in
the plan's cape layer, which moves with the chain.

Armor worn as clothing, in the gloves, boots, shoulderPads and helmet layers, is pictured on its
own as an object rather than painted over the figure, and fitted by its rule in armor.json: over
a limb's slots like an isolated body part, on a socket's body surface, or round the head; then
pushed out of the skin, weighted, baked and given form targets (see fit_armor). A layer covering
both hands or feet gets one part of the piece and its mirror; one on one side, like
shoulderPads.L, is briefed on the left and gets a part per side, paired. A helmet brief may say
what it hides, as {"hides": {"layers": ["hair"], "regions": ["scalp", "ears"]}}.

Accessories are fitted on the face, head_top, back, hip, shoulder and forearm sockets. Clothing
is lined up on the mannequin, cut
out of its figure, shaped over the body, weighted to the rig, baked, carried over to the female
body, and written as a layer part. Run it with any Python that has Pillow, such as TRELLIS.2's.
"""
import argparse
import datetime
import json
import os
import pathlib
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request

TOOLS = pathlib.Path(__file__).resolve().parent
PLAN = TOOLS.parent.parent / "src/main/resources/characters/ikala/bodyPlan/biped.json"
PLAN_ID = "ikala:biped"
TEMPLATE = "ikala:mat/standard"
# Editor captures: the mannequin design per body variant, unless config.json's "captureDesigns"
# names others, and the shots of each: (name, framed on the part, pitch, yaw), radians, yaw 0 at
# the front and pi/2 at the character's left
CAPTURE_DESIGNS = {"male": "test:design/mannequin_male",
                   "female": "test:design/mannequin_female"}
CAPTURE_VIEWS = [("front", False, 0.1, 0.0), ("side", False, 0.1, 1.5708),
                 ("back", False, 0.1, 3.1416), ("close", True, 0.25, 0.6)]
# Body parts are also shown under clothing: these layers, where they cover the part, unless
# config.json's "captureLayers" names others, from the front and close up. Accessories are shown
# over the thickest body part for the slot their socket rests on and these layers over it, to see
# them move out onto what is worn
CAPTURE_LAYERS = ["gen:part/pants/blue_jeans", "gen:part/jacket/navy_blazer"]
CAPTURE_DRESSED_VIEWS = [("front", False, 0.1, 0.0), ("close", True, 0.25, 0.6)]
# Accessories dressed are seen from every side, since they sit on the back or sides as often
CAPTURE_DRESSED_ACCESSORY_VIEWS = CAPTURE_VIEWS
CAPTURE_FOLDER = "editor"
CAPTURE_SCRIPT = "captures.iks"
# The shots' size in pixels, unless config.json's "captureSize" says otherwise
CAPTURE_SIZE = 1024
CAPTURE_SETTLE_FRAMES = 30
CAPTURE_START_FRAMES = 180
CAPTURE_START_SECONDS = 90
CAPTURE_BRIEF_SECONDS = 60
# Log lines that mean the capture script stopped
CAPTURE_FAILURES = ("Script failed", "stopping it", "Could not start the startup script")
# The palette channels a generated part's tint mask channels take, biggest color group first
MASK_PALETTE = {"r": "primary", "g": "secondary", "b": "tertiary", "a": "accent"}
MANIFEST = "manifest.json"

# Every concept image starts with this, so they come out alike and easy to cut out. "Game asset"
# makes SDXL draw sheets of many objects, so this asks for one isolated product render instead.
STYLE = ("isolated 3D product render of {subject}, one object only, plain white background, "
         "soft even studio light, clean readable shapes, distinct flat material colors, PBR")
NEGATIVE = ("sprite sheet, collection, set, grid, multiple views, several objects, person, face, "
            "mannequin, hands, cropped, cut off, text, watermark, logo, frame, border, busy "
            "background, scenery, floor, shadow, blurry, lowres, deformed")

# Clothing is shown on the mannequin, so the garment can be cut out of the mesh by how far it
# sits outside the body. The bare parts stay smooth and gray, and the head featureless.
LAYER_STYLE = ("full body 3D render of a smooth featureless gray mannequin standing straight in "
               "an A pose, arms out and down, seen from the {view}, {subject}, plain white "
               "background, soft even studio light, clean readable shapes, distinct flat "
               "material colors, PBR")
LAYER_NEGATIVE = ("hair, face, eyes, skin texture, shoes, hat, accessories, extra clothing, "
                  "sprite sheet, multiple views, several people, cropped, cut off, text, "
                  "watermark, logo, frame, border, busy background, scenery, floor, shadow, "
                  "blurry, lowres, deformed")
# The prompt for an outfit filling several layers, like a bodysuit
OUTFIT_PROMPT = "wearing only {description}"
# The body type clothing is generated and fitted on; others take it through the base mesh cages
GUIDE_VARIANT = "male"
# Which guide steers clothing images, and ControlNet Union's mode for it; they are painted
# over the guide's plain render, inside a mask
GUIDE_MAP = "depth"
GUIDE_MODE = "depth"
SKETCH_MODE = "hed/pidi/scribble/ted"
# The palette channel a clothing tint group takes once its layers' own channels are used
LAYER_EXTRA_CHANNEL = "accent"
# The palette channel a body part's skin-like tint group takes
SKIN_CHANNEL = "skin"
# Body slots a layer's mask never grows into unless the layer covers them
BARE_SLOTS = ("head", "hand.L", "hand.R", "foot.L", "foot.R")
# Room left around a brief's painted area when the review page zooms in on it, as a share of its
# size on each side
REVIEW_MARGIN = 0.15
# A body part's guides are framed on its slots with this much to spare around them, in meters
FRAME_PAD = 0.15
# The limbs a body part's guides are framed on whole
LIMB_CHAINS = (("upperArm.L", "lowerArm.L", "hand.L"), ("upperLeg.L", "lowerLeg.L", "foot.L"))
# This computer's folders, not committed, and the template for it
CONFIG = "config.json"
CONFIG_EXAMPLE = "config.example.json"
# How far a garment built from the mannequin's skin lets its baked normals lean, in degrees:
# its smooth surface shows the crumples of TRELLIS's cloth as blotches past that
SHELL_LEAN = 15
# The seam rings of the mannequin the guides are rendered from, beside its glb
SEAMS = "%s_seams.json"
# The side body parts are generated on, as their slot names end; the other side is its mirror
BODY_SIDE = ".L"
# Body parts fill a small share of the image, so the style doesn't call the figure a mannequin:
# asked for loudly, the gray body wins over the part, which comes out as plain gray limb
BODY_STYLE = ("full body 3D render of a figure standing straight in an A pose, arms out and "
              "down, seen from the front, {subject}, plain white background, soft even studio "
              "light, clean readable shapes, distinct flat material colors, PBR")
BODY_NEGATIVE = ("clothing, sleeve, glove, shoe, extra limbs, extra arms, extra fingers, "
                 + LAYER_NEGATIVE)
# An isolated body part (a rule's "isolated") is painted over a render of its slots alone, as an
# object on its own, which TRELLIS meshes with all its detail rather than as a slice of a figure;
# its rule's "piece" names what it is
ISOLATED_STYLE = ("isolated 3D product render of {piece}, on its own, not attached to a body, "
                  "made as ({subject}:1.4), seen from the front, one object only, plain white "
                  "background, soft even studio light, clean readable shapes, distinct flat "
                  "material colors, PBR")
ISOLATED_NEGATIVE = ("person, body, torso, head, face, full figure, mannequin, clothing, sleeve, "
                     "glove, pair, both legs, two legs, two arms, hips, pelvis, waist, "
                     "extra limbs, extra fingers, sprite sheet, multiple views, several objects, "
                     "cropped, text, watermark, logo, frame, border, busy background, scenery, "
                     "floor, shadow, blurry, lowres, deformed")
# Armor pictured on its own is a glove or a boot, which the isolated parts' negative keeps out
ARMOR_NEGATIVE = ISOLATED_NEGATIVE.replace("clothing, sleeve, glove, ", "")
# Where generated armor's meshes go in the content folder
ARMOR_FOLDER = "armor"
# Blender is run headless, without the user's settings, and exits 1 when a script raises: without
# --python-exit-code it exits 0, and a failed step looked done
BLENDER_FLAGS = ("-b", "--factory-startup", "--python-exit-code", "1")
# Each leg's slots, which a layer with "legGap" keeps the gap between
LEG_SLOTS = (("upperLeg.L", "lowerLeg.L", "foot.L"), ("upperLeg.R", "lowerLeg.R", "foot.R"))

SDXL_STEPS = 30
SDXL_CFG = 6.0
SDXL_SIZE = 1024
# Seeds count up from here. 1003 turned eight of ten SDXL subjects into pattern sheets.
FIRST_SEED = 2000
VIEWS = 3
# How hard a sketch steers the image, and for what share of the steps; past that the prompt
# finishes the details
SKETCH_STRENGTH = 0.7
SKETCH_END = 0.7
# Pixels darker than this (on light paper) are a sketch's lines; lighter fills are left out
SKETCH_LINE = 96
# Room left around a sketch's lines, as a share of their size
SKETCH_MARGIN = 0.15
REVIEW_FIELDS = ("brief", "pick", "mesh", "part")
# A pixel differing from the guide's render by more than this (RGB distance) was painted
PAINTED = 25
# Gaps up to this wide in the painted area are closed, in pixels (odd)
PAINT_CLOSE = 13
# Painted pieces smaller than this share of the biggest are dropped, like redrawn bare skin
PAINT_CRUMB = 0.05
# A repainted pixel with less color than this (RGB max less min) is still the bare mannequin,
# when the garment is colored; when it isn't, only if also this near the render's lightness
BARE_CHROMA = 12
BARE_LIGHTNESS = 28
# A garment is colored when the median color (RGB max less min) of the pixels surely its own,
# differing from the render by more than GARMENT_SURE, is at least this
COLORED = 18
# How wide, in pixels, a bare region inside a garment's painted area must be to be taken out of
# it again (odd, for the image filters)
BARE_OPEN = 9
# The most color (max channel minus min, of 255) the mannequin's bare skin shows in an image
NEUTRAL_CHROMA = 7
GARMENT_SURE = 60
# Gaps up to this wide in the painted area are closed for the garment's back, in pixels (odd)
BACK_CLOSE = 61


def load_config():
    """This computer's folders and settings, from config.json beside this file."""
    path = TOOLS / CONFIG
    if not path.exists():
        raise SystemExit("There is no %s: copy %s to it and fill in this computer's folders."
                         % (path, CONFIG_EXAMPLE))
    config = json.loads(path.read_text(encoding="utf8"))
    config.pop("_comment", None)
    unfilled = [key for key, value in config.items()
                if isinstance(value, str) and value.startswith("<")]
    if unfilled:
        raise SystemExit("%s still has placeholders for %s." % (path, ", ".join(unfilled)))
    return config


def load_json_with_comments(path):
    """Read JSON whose lines may start with // comments, like the definitions."""
    text = path.read_text(encoding="utf8")
    return json.loads(re.sub(r"(?m)^\s*//.*$", "", text))


def socket_of(brief):
    """The brief's socket in the body plan, and its type's fitting rule."""
    plan = load_json_with_comments(PLAN)
    socket = next((s for s in plan["sockets"] if s["id"] == brief["socket"]), None)
    if socket is None:
        raise SystemExit("The biped has no socket " + brief["socket"])
    rules = json.loads((TOOLS / "sockets.json").read_text(encoding="utf8"))
    if socket["type"] not in rules:
        raise SystemExit("No fitting rule for the socket type " + socket["type"] + " yet")
    rule = dict(rules[socket["type"]])
    # A rule can differ for one socket of its type, like a pouch at the back of the belt
    rule.update(rule.pop("bySocket", {}).get(socket["id"], {}))
    return socket, rule


def layers_of(brief):
    """The layer ids a brief fills: its "layer", or its "layers" for an outfit like a bodysuit
    that fills several at once, listed under the first."""
    ids = brief.get("layers") or [brief.get("layer")]
    if not ids or None in ids:
        raise SystemExit("A layer brief names its \"layer\", or \"layers\": " + brief["id"])
    return ids


def layer_of(brief):
    """Where the brief's clothing goes, as one layer covering every body slot its layers cover,
    and its rule from layers.json. An outfit filling several layers takes the first one's rule,
    with the most room any of them gives, their triangle budgets together, and a prompt that
    doesn't ask for any part of the body it covers to be bare."""
    plan = load_json_with_comments(PLAN)
    rules = json.loads((TOOLS / "layers.json").read_text(encoding="utf8"))
    found = []
    for layer_id in layers_of(brief):
        layer = next((l for l in plan["layers"] if l["id"] == layer_id), None)
        if layer is None:
            raise SystemExit("The biped has no layer %s" % layer_id)
        if layer_id not in rules:
            raise SystemExit("No rule for the layer " + layer_id + " yet")
        found.append((layer, rules[layer_id]))
    if len(found) == 1:
        layer, rule = found[0]
        return layer, dict(rule, **brief.get("fit", {}))
    covers = []
    for layer, _ in found:
        covers += [slot for slot in layer.get("covers", []) if slot not in covers]
    outfit = {"id": "+".join(layer["id"] for layer, _ in found), "covers": covers}
    rule = dict(found[0][1])
    rule["prompt"] = OUTFIT_PROMPT
    # An outfit runs across the lines its layers would stop at
    rule.pop("cut", None)
    for key in ("maskGrow", "maskDown", "gap"):
        rule[key] = max(r.get(key, 0) for _, r in found)
    rule["legGap"] = any(r.get("legGap", False) for _, r in found)
    # An outfit stays within a reach only if every layer it stands for does
    rule.pop("reach", None)
    if all("reach" in r for _, r in found):
        rule["reach"] = max(r["reach"] for _, r in found)
    rule["triangles"] = sum(r["triangles"] for _, r in found)
    return outfit, dict(rule, **brief.get("fit", {}))


def armor_of(brief):
    """Where a layer brief's armor goes and its rule from armor.json, or None if its layer has
    no armor rule (it is clothing, see layer_of). A rule fitting on a socket's surface starts from
    sockets.json's rule for the socket's type. Its "slots", named without the side, are the left
    side's: armor on one side is briefed on the left, and the right is its mirror."""
    ids = layers_of(brief)
    rules = json.loads((TOOLS / "armor.json").read_text(encoding="utf8"))
    rules.pop("_comment", None)
    if len(ids) != 1 or (ids[0] not in rules and other_side(ids[0]) not in rules):
        return None
    layer_id = ids[0]
    if layer_id not in rules:
        raise SystemExit("%s is in %s: armor on one side is briefed on the left, and the right "
                         "is mirrored from it, so brief %s instead"
                         % (brief["id"], layer_id, other_side(layer_id)))
    plan = load_json_with_comments(PLAN)
    layer = next((l for l in plan["layers"] if l["id"] == layer_id), None)
    if layer is None:
        raise SystemExit("The biped has no layer %s" % layer_id)
    rule = dict(rules[layer_id])
    if rule["fit"] == "surface":
        socket = next((s for s in plan["sockets"] if s["id"] == rule["socket"]), None)
        if socket is None:
            raise SystemExit("The biped has no socket " + rule["socket"])
        base = json.loads((TOOLS / "sockets.json").read_text(encoding="utf8"))[socket["type"]]
        rule = dict(base, **rule)
        rule["bone"] = socket["bone"]
    rule.update(brief.get("fit", {}))
    known = {s["id"] for s in plan["slots"]}
    covers = [s if s in known else s + BODY_SIDE for s in rule["slots"]]
    part = {"id": "armor_" + brief["id"], "layer": layer, "group": "armor_" + layer_id,
            "covers": covers, "keepsOut": [s for s in known if s not in covers],
            "isolated": rule["fit"] == "limb", "roll": rule.get("roll", 0)}
    return part, rule


def outer_seams(slots):
    """The seams of the given body plan slots that only one of them has: where a part filling
    them meets the rest of the body."""
    seams = [seam for slot in slots for seam in slot["seams"]]
    return [seam for seam in dict.fromkeys(seams) if seams.count(seam) == 1]


def body_of(brief):
    """Where the brief's body part goes, as the slots it fills with the modes of its outer seams
    and the slots its images must leave alone, and its rule from bodyparts.json, picked by its
    slots' names without the side."""
    plan = load_json_with_comments(PLAN)
    named = brief.get("slots")
    if not named or not isinstance(named, list):
        raise SystemExit("A body brief lists the body plan \"slots\" it fills: " + brief["id"])
    slots = []
    for slot_id in named:
        slot = next((s for s in plan["slots"] if s["id"] == slot_id), None)
        if slot is None:
            raise SystemExit("The biped has no slot %s (in %s); its slots are %s"
                             % (slot_id, brief["id"], ", ".join(s["id"] for s in plan["slots"])))
        if not slot_id.endswith(BODY_SIDE):
            raise SystemExit("%s fills %s: body parts are generated on the left side only, and "
                             "the right side is mirrored from it through the slots' mirrorOf, so "
                             "brief %s instead" % (brief["id"], slot_id,
                                                   slot.get("mirrorOf", "the left side")))
        if slot in slots:
            raise SystemExit("%s lists the slot %s twice" % (brief["id"], slot_id))
        slots.append(slot)
    # The slots must hang together, each reached from the first through seams they share
    joined = [slots[0]]
    for slot in joined:
        joined += [s for s in slots if s not in joined and set(s["seams"]) & set(slot["seams"])]
    if len(joined) < len(slots):
        raise SystemExit("The slots of %s aren't neighbors: %s shares no seam with %s"
                         % (brief["id"], ", ".join(s["id"] for s in slots if s not in joined),
                            ", ".join(s["id"] for s in joined)))
    outer = outer_seams(slots)
    seams = {seam: "weld" for seam in outer}
    for seam, mode in brief.get("seams", {}).items():
        if seam not in outer:
            raise SystemExit("%s can't say how its seam %s joins: its outer seams are %s"
                             % (brief["id"], seam, ", ".join(outer)))
        if mode not in ("weld", "open"):
            raise SystemExit("The seam %s of %s is \"weld\" or \"open\", not %s"
                             % (seam, brief["id"], json.dumps(mode)))
        seams[seam] = mode
    rules = json.loads((TOOLS / "bodyparts.json").read_text(encoding="utf8"))
    rules.pop("_comment", None)
    sideless = sorted(slot_id[:-len(BODY_SIDE)] for slot_id in named)
    group = next((name for name, rule in rules.items() if sorted(rule["slots"]) == sideless), None)
    if group is None:
        raise SystemExit("No body part rule fills %s yet (in %s); the rules fill %s"
                         % (" + ".join(sideless), brief["id"],
                            "; ".join(" + ".join(r["slots"]) for r in rules.values())))
    rule = dict(rules[group], **brief.get("fit", {}))
    part = {"id": "body_" + brief["id"], "group": group, "covers": named, "seams": seams,
            "keepsOut": [s["id"] for s in plan["slots"] if s["id"] not in named],
            "isolated": bool(rule.get("isolated")), "roll": rule.get("roll", 0)}
    return part, rule


def extension_of(brief):
    """Where the brief's part goes, as its body plan extension chain with the chain's length,
    and its rule from extensions.json, picked by the chain's name without its side. Wings are
    briefed on the left; the right is the left's mirror."""
    plan = load_json_with_comments(PLAN)
    name = brief.get("chain")
    chain = next((c for c in plan.get("extensions", []) if c["id"] == name), None)
    if chain is None:
        raise SystemExit("%s names the chain %s; the biped's chains are %s"
                         % (brief["id"], json.dumps(name),
                            ", ".join(c["id"] for c in plan.get("extensions", []))))
    if name.endswith(".R"):
        raise SystemExit("%s is on %s: chains with sides are generated on the left only, and the "
                         "right is mirrored from it, so brief %s.L instead"
                         % (brief["id"], name, name[:-2]))
    rules = json.loads((TOOLS / "extensions.json").read_text(encoding="utf8"))
    rules.pop("_comment", None)
    kind = name.rsplit(".", 1)[0] if name.endswith(".L") else name
    if kind not in rules:
        raise SystemExit("No rule for the chain %s yet (in %s); the rules are for %s"
                         % (name, brief["id"], ", ".join(rules)))
    rule = dict(rules[kind], **brief.get("fit", {}))
    length = brief.get("length", rule["length"])
    if not isinstance(length, (int, float)) or length <= 0:
        raise SystemExit("A brief's \"length\" is meters above 0: " + brief["id"])
    part = {"id": "chain_" + brief["id"], "chain": name, "length": float(length),
            "scale": float(length) / rule["length"]}
    return part, rule


def rule_of(brief):
    """Where the brief's part goes, and its rule, by its kind."""
    if brief["kind"] == "accessory":
        return socket_of(brief)
    if brief["kind"] == "layer":
        return armor_of(brief) or layer_of(brief)
    if brief["kind"] == "body":
        return body_of(brief)
    if brief["kind"] == "extension":
        return extension_of(brief)
    raise SystemExit("Briefs are accessories, layers, body parts or extensions, not "
                     + brief["kind"])


class Batch:
    """A batch folder and its manifest. Every change reads the manifest fresh and replaces it
    whole, so the review page and a running command don't undo each other's choices."""

    def __init__(self, config, name):
        if not re.fullmatch(r"[a-z0-9_]+", name):
            raise SystemExit("A batch name is lowercase letters, digits and underscores")
        self.folder = pathlib.Path(config["work"]) / name
        self.name = name

    @property
    def path(self):
        return self.folder / MANIFEST

    def load(self):
        if not self.path.exists():
            raise SystemExit("No batch " + str(self.folder))
        return json.loads(self.path.read_text(encoding="utf8"))

    def save(self, manifest):
        temp = self.path.with_suffix(".tmp")
        temp.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf8")
        for _ in range(20):
            try:
                os.replace(temp, self.path)
                return
            except PermissionError:  # the page may be reading it on Windows
                time.sleep(0.05)
        raise SystemExit("Couldn't write " + str(self.path))

    def update(self, brief_id, change):
        """Change one brief's entry with change(entry), on the newest manifest."""
        manifest = self.load()
        entry = next(b for b in manifest["briefs"] if b["id"] == brief_id)
        change(entry)
        self.save(manifest)

    def brief_folder(self, brief_id):
        folder = self.folder / brief_id
        folder.mkdir(parents=True, exist_ok=True)
        return folder


def ready(manifest, args, wanted):
    """The briefs a stage works on: those the filter takes, limited by --only."""
    only = set(args.only.split(",")) if args.only else None
    chosen = [b for b in manifest["briefs"] if (only is None or b["id"] in only) and wanted(b)]
    if not chosen:
        print("Nothing is ready for this stage")
    return chosen


def kept(entry, field, args):
    return args.all or entry["review"][field] == "keep"


class Comfy:
    """A ComfyUI server for the length of a command, started if one isn't running already. One
    it starts runs offline: its web page can't reach the internet and its paid API nodes are off.
    One already running keeps however it was started."""

    def __init__(self, config):
        self.root = pathlib.Path(config["comfy"])
        self.url = "http://127.0.0.1:%d" % config["comfyPort"]
        self.port = config["comfyPort"]
        self.process = None

    def __enter__(self):
        if self.alive():
            return self
        log = open(self.root / "partgen_server.log", "w", encoding="utf8")
        self.process = subprocess.Popen(
            [str(self.root / ".venv/Scripts/python.exe"), "main.py", "--port", str(self.port),
             "--disable-auto-launch", "--offline"],
            cwd=self.root, stdout=log, stderr=subprocess.STDOUT)
        for _ in range(180):
            if self.alive():
                return self
            if self.process.poll() is not None:
                raise SystemExit("ComfyUI stopped; see " + str(self.root / "partgen_server.log"))
            time.sleep(1)
        raise SystemExit("ComfyUI didn't start")

    def __exit__(self, *exc):
        if self.process is not None:
            self.process.terminate()
            self.process.wait(30)

    def alive(self):
        try:
            urllib.request.urlopen(self.url + "/system_stats", timeout=2).read()
            return True
        except OSError:
            return False

    def run(self, workflow):
        """Queue a workflow and give the images it saved, once it is done."""
        body = json.dumps({"prompt": workflow}).encode("utf8")
        request = urllib.request.Request(self.url + "/prompt", body,
                                         {"Content-Type": "application/json"})
        prompt_id = json.loads(urllib.request.urlopen(request).read())["prompt_id"]
        while True:
            history = json.loads(urllib.request.urlopen(
                "%s/history/%s" % (self.url, prompt_id)).read())
            if prompt_id in history:
                entry = history[prompt_id]
                if entry.get("status", {}).get("status_str") == "error":
                    raise SystemExit("ComfyUI failed: " + json.dumps(entry["status"]))
                break
            time.sleep(1)
        images = []
        for output in entry["outputs"].values():
            for image in output.get("images", []):
                query = urllib.parse.urlencode(image)
                images.append(urllib.request.urlopen(self.url + "/view?" + query).read())
        return images


def sdxl_workflow(checkpoint, prompt, negative, seed, prefix):
    """Text to image with SDXL, in ComfyUI's API format."""
    return {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": checkpoint}},
        "2": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["1", 1]}},
        "3": {"class_type": "CLIPTextEncode", "inputs": {"text": negative, "clip": ["1", 1]}},
        "4": {"class_type": "EmptyLatentImage",
              "inputs": {"width": SDXL_SIZE, "height": SDXL_SIZE, "batch_size": 1}},
        "5": {"class_type": "KSampler",
              "inputs": {"seed": seed, "steps": SDXL_STEPS, "cfg": SDXL_CFG,
                         "sampler_name": "dpmpp_2m", "scheduler": "karras", "denoise": 1.0,
                         "model": ["1", 0], "positive": ["2", 0], "negative": ["3", 0],
                         "latent_image": ["4", 0]}},
        "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
        "7": {"class_type": "SaveImage", "inputs": {"images": ["6", 0], "filename_prefix": prefix}},
    }


def inpaint_workflow(workflow, image, mask):
    """Paint only inside a mask over an image, keeping the rest of it."""
    workflow.update({
        "12": {"class_type": "LoadImage", "inputs": {"image": image}},
        "13": {"class_type": "VAEEncode", "inputs": {"pixels": ["12", 0], "vae": ["1", 2]}},
        "14": {"class_type": "LoadImageMask", "inputs": {"image": mask, "channel": "red"}},
        "15": {"class_type": "SetLatentNoiseMask",
               "inputs": {"samples": ["13", 0], "mask": ["14", 0]}},
    })
    del workflow["4"]
    workflow["5"]["inputs"]["latent_image"] = ["15", 0]
    return workflow


def control_workflow(workflow, config, image, mode, strength, end):
    """Steer a text to image workflow with an image, through one of ControlNet Union's modes:
    a sketch's scribble, or a guide's depth."""
    workflow.update({
        "8": {"class_type": "LoadImage", "inputs": {"image": image}},
        "9": {"class_type": "ControlNetLoader",
              "inputs": {"control_net_name": config["controlnet"]}},
        "10": {"class_type": "SetUnionControlNetType",
               "inputs": {"control_net": ["9", 0], "type": mode}},
        "11": {"class_type": "ControlNetApplyAdvanced",
               "inputs": {"positive": ["2", 0], "negative": ["3", 0], "control_net": ["10", 0],
                          "image": ["8", 0], "strength": strength, "start_percent": 0.0,
                          "end_percent": end, "vae": ["1", 2]}},
    })
    workflow["5"]["inputs"]["positive"] = ["11", 0]
    workflow["5"]["inputs"]["negative"] = ["11", 1]
    return workflow


def scaled_to_fit(size, limit):
    """A size scaled up or down so its longer side is the limit."""
    scale = limit / max(size)
    return max(1, round(size[0] * scale)), max(1, round(size[1] * scale))


def prepare_sketch(config, batch, brief):
    """A brief's sketch as ComfyUI wants it, in its input folder: only its lines, white on black,
    square, the generated size, thickened a little. Fills and colored marks are left out, since
    scribble mode would take a filled area for a shape of its own. Gives its name there."""
    from PIL import Image, ImageFilter, ImageStat
    sketch = Image.open(brief["sketch"]).convert("RGBA")
    flat = Image.new("RGBA", sketch.size, "white")
    flat.alpha_composite(sketch)  # a transparent background counts as paper
    sketch = flat.convert("L")
    if ImageStat.Stat(sketch).mean[0] > 127:
        sketch = sketch.point(lambda v: 255 if v < SKETCH_LINE else 0)  # drawn dark on light
    else:
        sketch = sketch.point(lambda v: 255 if v > 255 - SKETCH_LINE else 0)
    # Fill most of the frame however small it was drawn, so the object does too
    box = sketch.getbbox()
    if box:
        margin = int(max(box[2] - box[0], box[3] - box[1]) * SKETCH_MARGIN)
        sketch = sketch.crop((box[0] - margin, box[1] - margin, box[2] + margin, box[3] + margin))
    sketch = sketch.resize(scaled_to_fit(sketch.size, SDXL_SIZE))
    square = Image.new("L", (SDXL_SIZE, SDXL_SIZE), 0)
    square.paste(sketch, ((SDXL_SIZE - sketch.width) // 2, (SDXL_SIZE - sketch.height) // 2))
    square = square.filter(ImageFilter.MaxFilter(5))
    name = "partgen_%s_%s.png" % (batch.name, brief["id"])
    square.convert("RGB").save(pathlib.Path(config["comfy"]) / "input" / name)
    return name


def frame_chain(part):
    """The limb a body part's slots belong to (see LIMB_CHAINS), or its own slots if none."""
    for chain in LIMB_CHAINS:
        if set(part["covers"]) <= set(chain):
            return list(chain)
    return list(part["covers"])


def body_frame(config, framed, roll=0.0):
    """A view framed on some slots, from the whole body's guide: the square around them with
    FRAME_PAD meters to spare, for a camera turned roll degrees about its view. Gives its
    middle (x, z) and size, in meters."""
    from PIL import Image
    import numpy as np
    # Body parts are framed on the front view's guide
    folder = guides(config)
    guide = json.loads((folder / "guide.json").read_text(encoding="utf8"))
    slots = np.asarray(Image.open(folder / "slots.png").getchannel("R"))
    rows, columns = np.nonzero(np.isin(slots, [guide["slots"].index(s) + 1 for s in framed]))
    size, view = guide["size"], guide["view"]
    # The covered pixels' corners in Blender's x and z, after guide.json's "pixel"
    x = np.concatenate([guide.get("middleX", 0.0) + ((columns + c) / size - 0.5) * view
                        for c in (0, 1)])
    z = np.concatenate([guide["middleHeight"] - ((rows + r) / size - 0.5) * view
                        for r in (0, 1)])
    # Across and up the turned view
    turn = np.radians(roll)
    across = x * np.cos(turn) - z * np.sin(turn)
    up = x * np.sin(turn) + z * np.cos(turn)
    middle_across = (across.min() + across.max()) / 2
    middle_up = (up.min() + up.max()) / 2
    side = max(np.ptp(across), np.ptp(up)) + 2 * FRAME_PAD
    return (float(middle_across * np.cos(turn) + middle_up * np.sin(turn)),
            float(-middle_across * np.sin(turn) + middle_up * np.cos(turn)), float(side))


def guides(config, part=None, view="front"):
    """The folder of guides rendered from the mannequin, rendered the first time: of the whole
    body seen from the view, or for a body part, framed on its limb (see frame_chain and
    body_frame), so the part fills much more of the image, and of the figure TRELLIS makes from
    it. A whole limb rather than the part alone, so the body it hangs from shows too and holds the
    figure in its pose: an arm on its own, TRELLIS turns as it pleases. An isolated part's guides
    show its own slots and nothing else, framed on them; fit_body.py works out its turn instead.
    A part on an extension chain gets a stand-in shape along its chain, with nothing else, seen
    as its rule says (see chain_guides)."""
    name = GUIDE_VARIANT if view == "front" else "%s_%s" % (GUIDE_VARIANT, view)
    frame, only, roll = None, None, 0
    if part is not None and part.get("group"):
        framed = part["covers"] if part.get("isolated") else frame_chain(part)
        roll = part.get("roll", 0) if part.get("isolated") else 0
        name = "%s_%s%s%s" % (GUIDE_VARIANT, "only_" if part.get("isolated") else "",
                              re.sub(r"[^a-z0-9]+", "_", "_".join(framed).lower()),
                              "_roll%g" % roll if roll else "")
        frame = body_frame(config, framed, roll)
        only = framed if part.get("isolated") else None
    folder = pathlib.Path(config["work"]) / "guides" / name
    source = folder / "guide.json"
    if not source.exists():
        body = pathlib.Path(config["mannequin"]).with_suffix(".blend")
        result = subprocess.run(
            [config["blender"], *BLENDER_FLAGS, "--python",
             str(TOOLS / "blender" / "render_guides.py"), "--", "--body", str(body),
             "--out", str(folder), "--size", str(SDXL_SIZE)]
            + (["--frame", "%.4f,%.4f,%.4f" % frame] if frame else [])
            + (["--only", ",".join(only)] if only else [])
            + (["--roll", str(roll)] if roll else [])
            + (["--view", view] if view != "front" else []),
            capture_output=True, text=True)
        if result.returncode != 0 or not source.exists():
            raise SystemExit("Rendering the guides failed:\n" + result.stdout[-2000:]
                             + result.stderr[-2000:])
    return folder


def chain_guides(config, part, rule):
    """The folder of guides for a part on an extension chain, rendered the first time: a plain
    stand-in along the chain at rest, made the part's length, alone, seen from the rule's "view"
    and turned by its "roll", framed with FRAME_PAD to spare."""
    view, roll = rule.get("view", "front"), rule.get("roll", 0)
    name = "%s_chain_%s_%s_x%.2f%s" % (GUIDE_VARIANT, re.sub(r"[^a-z0-9]+", "_", part["chain"]
                                                              .lower()),
                                      view, part["scale"], "_roll%g" % roll if roll else "")
    folder = pathlib.Path(config["work"]) / "guides" / name
    source = folder / "guide.json"
    if source.exists():
        return folder
    # A first render, unframed, finds where the stand-in is
    body = pathlib.Path(config["mannequin"]).with_suffix(".blend")
    common = [config["blender"], *BLENDER_FLAGS, "--python",
              str(TOOLS / "blender" / "render_guides.py"), "--", "--body", str(body),
              "--chain", part["chain"], "--chain-scale", "%.4f" % part["scale"], "--view", view]
    scout = folder / "scout"
    for out, size, extra in ((scout, 256, []), (folder, SDXL_SIZE, None)):
        if extra is None:
            # Joined, since a frame starting with a minus would read as an option
            extra = ["--frame=%.4f,%.4f,%.4f" % chain_frame(scout, roll)]
            extra += ["--roll", str(roll)] if roll else []
        result = subprocess.run(common + ["--out", str(out), "--size", str(size)] + extra,
                                capture_output=True, text=True)
        if result.returncode != 0 or not (out / "guide.json").exists():
            raise SystemExit("Rendering the chain guides failed:\n" + result.stdout[-2000:]
                             + result.stderr[-2000:])
    return folder


def chain_frame(scout, roll):
    """A view framed on a stand-in from its unframed render: the square around it, turned roll
    degrees, with FRAME_PAD to spare. Gives its middle (along the view's right, z) and size."""
    from PIL import Image
    import numpy as np
    guide = json.loads((scout / "guide.json").read_text(encoding="utf8"))
    rows, columns = np.nonzero(np.asarray(Image.open(scout / "slots.png").getchannel("R")))
    size, view = guide["size"], guide["view"]
    x = np.concatenate([guide["middleX"] + ((columns + c) / size - 0.5) * view for c in (0, 1)])
    z = np.concatenate([guide["middleHeight"] - ((rows + r) / size - 0.5) * view
                        for r in (0, 1)])
    turn = np.radians(roll)
    across = x * np.cos(turn) - z * np.sin(turn)
    up = x * np.sin(turn) + z * np.cos(turn)
    middle_across = (across.min() + across.max()) / 2
    middle_up = (up.min() + up.max()) / 2
    side = max(np.ptp(across), np.ptp(up)) + 2 * FRAME_PAD
    return (float(middle_across * np.cos(turn) + middle_up * np.sin(turn)),
            float(-middle_across * np.sin(turn) + middle_up * np.cos(turn)), float(side))


def bare_ends(layer):
    """The head, hands and feet a layer leaves bare, which its collar, sleeves and legs must stop
    short of."""
    return [slot for slot in BARE_SLOTS if slot not in layer["covers"]]


def leg_gap(folder, guide):
    """The gap between the mannequin's legs below the crotch in its front render, as a boolean
    array of its pixels: in each row, what lies between one leg and the other. Growing a mask
    or closing gaps bridges it, and the image then draws legs joined down to the knees."""
    from PIL import Image
    import numpy as np
    slots = np.asarray(Image.open(folder / "slots.png").getchannel("R"))
    legs = [np.isin(slots, [guide["slots"].index(s) + 1 for s in leg if s in guide["slots"]])
            for leg in LEG_SLOTS]
    gap = np.zeros(slots.shape, dtype=bool)
    columns = np.arange(slots.shape[1])
    either = legs[0] | legs[1]
    for row in range(slots.shape[0]):
        a, b = (np.flatnonzero(leg[row]) for leg in legs)
        if not len(a) or not len(b):
            continue
        # Between the legs' middles, wherever neither is drawn: a stray pixel of one leg's slot
        # at the other's edge would hide the gap if it went from one's edge to the other's
        low, high = sorted((np.median(a), np.median(b)))
        gap[row] = (columns > low) & (columns < high) & ~either[row]
    return gap


def beyond_seams(config, folder, guide, part):
    """What lies beyond a body part's outer seams in the guide's render, as a boolean array of its
    pixels: for each seam, the half of the image on the far side of its ring's line from the
    part's slot there. The rings are loops in Blender space, nearly flat, so each is seen from the
    front as a line: the one through its middle along its plane, whose normal is the direction
    the loop spreads least in."""
    from PIL import Image
    import numpy as np
    if guide.get("from", "front") != "front":
        raise SystemExit("Seam lines are only worked out on guides seen from the front")
    rings = json.loads((pathlib.Path(config["mannequin"]).parent / (SEAMS % GUIDE_VARIANT))
                       .read_text(encoding="utf8"))["seams"]
    size, view, middle = guide["size"], guide["view"], guide["middleHeight"]
    slots = np.asarray(Image.open(folder / "slots.png").getchannel("R"))
    rows, columns = np.mgrid[0:slots.shape[0], 0:slots.shape[1]]
    # Pixel centers in Blender's x and z, after guide.json's "pixel"
    across = ((columns + 0.5) / size - 0.5) * view
    up = (0.5 - (rows + 0.5) / size) * view
    roll = np.radians(guide.get("roll", 0.0))
    x = guide.get("middleX", 0.0) + across * np.cos(roll) + up * np.sin(roll)
    z = middle - across * np.sin(roll) + up * np.cos(roll)
    beyond = np.zeros(slots.shape, dtype=bool)
    plan = load_json_with_comments(PLAN)
    for seam in part["seams"]:
        if seam not in rings:
            raise SystemExit("The mannequin has no ring for the seam " + seam)
        ring = np.array(rings[seam], dtype=float)
        center = ring.mean(0)
        normal = np.linalg.svd(ring - center)[2][2]
        line = np.array([normal[0], normal[2]])
        if np.linalg.norm(line) < 0.1:
            raise SystemExit("The ring of %s faces the camera, so it has no line to stop at"
                             % seam)
        line /= np.linalg.norm(line)
        side = (x - center[0]) * line[0] + (z - center[2]) * line[1]
        slot = next(s["id"] for s in plan["slots"]
                    if s["id"] in part["covers"] and seam in s["seams"])
        own = slots == guide["slots"].index(slot) + 1
        beyond |= (side < 0) if side[own].mean() > 0 else (side > 0)
    return beyond


def guide_inputs(config, layer, rule):
    """The images that paint a layer's clothing or a body part, in ComfyUI's input folder: the
    mannequin's render, its depth, and a mask of where it may paint. Gives their names there."""
    from PIL import Image, ImageChops, ImageFilter
    folder = guides(config, layer, rule.get("view", "front"))
    guide = json.loads((folder / "guide.json").read_text(encoding="utf8"))
    inputs = pathlib.Path(config["comfy"]) / "input"
    names = {}
    for kind in ("gray", GUIDE_MAP):
        names[kind] = "partgen_guide_%s_%s.png" % (folder.name, kind)
        Image.open(folder / (kind + ".png")).convert("RGB").save(inputs / names[kind])
    # The slots the layer covers, grown outward, then drawn down for clothing that hangs
    covered = {guide["slots"].index(slot) + 1 for slot in layer["covers"]}
    slots = Image.open(folder / "slots.png").getchannel("R")
    mask = slots.point(lambda v: 255 if v in covered else 0)
    for _ in range(rule["maskGrow"] // 4):
        mask = mask.filter(ImageFilter.MaxFilter(9))
    hang = mask
    for step in range(rule.get("maskDown", 0) // 8):
        hang = ImageChops.lighter(hang, ImageChops.offset(mask, 0, (step + 1) * 8))
    # Growing never reaches the head, hands or feet unless the layer covers them; repainted,
    # they would differ from the render and be taken for clothing. A body part leaves all the
    # rest of the body alone
    kept_bare = {guide["slots"].index(slot) + 1 for slot in layer.get("keepsOut", BARE_SLOTS)
                 if slot in guide["slots"] and slot not in layer["covers"]}
    bare = slots.point(lambda v: 255 if v in kept_bare else 0)
    hang = ImageChops.subtract(hang, bare)
    # Nor does it reach past its outer seams, so the body it joins there stays the mannequin's
    if layer.get("seams"):
        beyond = beyond_seams(config, folder, guide, layer)
        hang = ImageChops.subtract(hang, Image.fromarray(beyond.astype("uint8") * 255))
    if rule.get("legGap"):
        gap = Image.fromarray(leg_gap(folder, guide).astype("uint8") * 255)
        hang = ImageChops.subtract(hang, gap)
    names["mask"] = "partgen_mask_%s_%s.png" % (folder.name, layer["id"])
    hang.convert("RGB").save(inputs / names["mask"])
    return names


def chain_input(config, part, rule):
    """The depth of a chain part's stand-in, in ComfyUI's input folder. Gives its name there."""
    from PIL import Image
    folder = chain_guides(config, part, rule)
    name = "partgen_guide_%s_%s.png" % (folder.name, GUIDE_MAP)
    Image.open(folder / (GUIDE_MAP + ".png")).convert("RGB").save(
        pathlib.Path(config["comfy"]) / "input" / name)
    return name


def isolated(brief):
    """Whether a brief is a part painted and meshed on its own (see ISOLATED_STYLE): a body part
    its rule isolates, or a part on an extension chain."""
    if brief["kind"] == "extension":
        return True
    if is_armor(brief):
        return armor_of(brief)[0]["isolated"]
    return brief["kind"] == "body" and body_of(brief)[0]["isolated"]


def is_armor(brief):
    """Whether a brief is armor worn as clothing (see armor_of)."""
    return brief["kind"] == "layer" and armor_of(brief) is not None


def prompt_for(brief):
    where, rule = rule_of(brief)
    if isolated(brief):
        return ISOLATED_STYLE.format(piece=rule["piece"], subject=brief["description"])
    subject = rule["prompt"].format(description=brief["description"])
    if is_armor(brief):
        # Pictured on its own, as an accessory is
        return STYLE.format(subject=subject)
    style = {"layer": LAYER_STYLE, "body": BODY_STYLE}.get(brief["kind"], STYLE)
    return style.format(subject=subject, view=rule.get("view", "front"))


def negative_for(brief):
    if isolated(brief):
        # A rule can keep out what its part tends to drag in, like the whole fox around a tail
        extra = rule_of(brief)[1].get("negative")
        base = ARMOR_NEGATIVE if is_armor(brief) else ISOLATED_NEGATIVE
        return base + (", " + extra if extra else "")
    if is_armor(brief):
        return NEGATIVE
    return {"layer": LAYER_NEGATIVE, "body": BODY_NEGATIVE}.get(brief["kind"], NEGATIVE)


def command_new(config, batch, args):
    briefs_file = pathlib.Path(args.briefs)
    briefs = json.loads(briefs_file.read_text(encoding="utf8"))
    if batch.path.exists():
        manifest = batch.load()
    else:
        batch.folder.mkdir(parents=True, exist_ok=True)
        manifest = {"batch": batch.name, "created": datetime.date.today().isoformat(),
                    "briefs": []}
    known = {b["id"] for b in manifest["briefs"]}
    for brief in briefs:
        if not re.fullmatch(r"[a-z0-9_]+", brief["id"]):
            raise SystemExit("Brief ids are lowercase letters, digits and underscores: "
                             + brief["id"])
        if brief["id"] in known:
            print("already in the batch:", brief["id"])
            continue
        rule_of(brief)
        entry = dict(brief)
        if "sketch" in brief:
            entry["sketch"] = str((briefs_file.parent / brief["sketch"]).resolve())
        entry["review"] = {field: None for field in REVIEW_FIELDS}
        if args.keep:
            entry["review"]["brief"] = "keep"
        entry.update({"notes": {}, "images": [], "mesh": None, "part": None})
        manifest["briefs"].append(entry)
        known.add(brief["id"])
    batch.save(manifest)
    print("batch %s: %d briefs" % (batch.folder, len(manifest["briefs"])))


def command_images(config, batch, args):
    briefs = ready(batch.load(), args, lambda b: kept(b, "brief", args)
                   and (args.more or not b["images"]))
    if not briefs:
        return
    with Comfy(config) as comfy:
        for brief in briefs:
            prompt = prompt_for(brief)
            negative = negative_for(brief)
            folder = batch.brief_folder(brief["id"]) / "images"
            folder.mkdir(exist_ok=True)
            start = max(brief["images"], default=FIRST_SEED - 1) + 1
            seeds = list(range(start, start + args.count))
            # Clothing and body parts follow the mannequin's guide; accessories may follow a
            # sketch instead
            control, mode, strength, end, painted = None, None, None, None, None
            if brief["kind"] in ("layer", "body") and (not is_armor(brief) or isolated(brief)):
                layer, rule = rule_of(brief)
                painted = guide_inputs(config, layer, rule)
                control, mode = painted[GUIDE_MAP], GUIDE_MODE
                strength, end = rule["guideStrength"], rule["guideEnd"]
            elif brief["kind"] == "extension":
                # Pictured on its own, in the pose of a stand-in along its chain
                part, rule = extension_of(brief)
                control, mode = chain_input(config, part, rule), GUIDE_MODE
                strength, end = rule["guideStrength"], rule["guideEnd"]
            elif brief.get("sketch"):
                control, mode = prepare_sketch(config, batch, brief), SKETCH_MODE
                strength = brief.get("sketchStrength", SKETCH_STRENGTH)
                end = brief.get("sketchEnd", SKETCH_END)
            for seed in seeds:
                workflow = sdxl_workflow(config["checkpoint"], prompt, negative, seed,
                                         "partgen/" + brief["id"])
                if control:
                    workflow = control_workflow(workflow, config, control, mode, strength, end)
                if painted:
                    workflow = inpaint_workflow(workflow, painted["gray"], painted["mask"])
                (folder / ("%d.png" % seed)).write_bytes(comfy.run(workflow)[0])
            (folder / "prompt.json").write_text(json.dumps(
                {"prompt": prompt, "negative": negative, "steps": SDXL_STEPS, "cfg": SDXL_CFG,
                 "checkpoint": config["checkpoint"], "sketch": brief.get("sketch"),
                 "control": mode, "controlStrength": strength, "controlEnd": end,
                 "mask": painted and painted["mask"]},
                indent=2), encoding="utf8")
            batch.update(brief["id"], lambda e: e["images"].extend(seeds))
            print("images", brief["id"], seeds)


def command_meshes(config, batch, args):
    def wanted(b):
        return b["review"]["pick"] is not None and not templated(b) and (
            args.redo or b["mesh"] is None or b["mesh"]["image"] != b["review"]["pick"]
            or b["mesh"]["type"] != args.type)

    briefs = ready(batch.load(), args, wanted)
    if not briefs:
        return
    pairs, renders, figures = [], [], []
    for brief in briefs:
        folder = batch.brief_folder(brief["id"])
        pick = brief["review"]["pick"]
        glb = folder / ("mesh_%d_%s.glb" % (pick, args.type))
        image = str(folder / "images" / ("%d.png" % pick))
        pairs += [image, str(glb)]
        renders += [str(glb), str(folder / ("views_%d_%s" % (pick, args.type)))]
        # Clothing and body parts are painted on the mannequin, so TRELLIS makes the figure,
        # which it checks for curtains of streaks; an isolated part is an object on its own
        if brief["kind"] in ("layer", "body") and not isolated(brief) and not is_armor(brief):
            figures += ["--figure", image]
        # A tail or wing is checked for the curtains themselves; a cape's long folds would look
        # like them
        elif brief["kind"] == "extension" and extension_of(brief)[1].get("checkStreaks", True):
            figures += ["--object", image]
    trellis = pathlib.Path(config["trellis"])
    subprocess.run([str(trellis / ".venv/Scripts/python.exe"), str(TOOLS / "trellis_run.py"),
                    "--trellis", str(trellis), "--type", args.type] + figures + pairs,
                   check=True)
    subprocess.run([config["blender"], *BLENDER_FLAGS, "--python",
                    str(TOOLS / "blender/render_meshes.py"), "--"] + renders, check=True,
                   stdout=subprocess.DEVNULL)
    for brief in briefs:
        pick = brief["review"]["pick"]

        made = batch.brief_folder(brief["id"]) / ("mesh_%d_%s.glb.json" % (pick, args.type))
        seed = json.loads(made.read_text(encoding="utf8"))["seed"] if made.exists() else 0

        def record(entry, pick=pick, seed=seed):
            entry["mesh"] = {"image": pick, "type": args.type, "seed": seed,
                             "file": "mesh_%d_%s.glb" % (pick, args.type),
                             "views": "views_%d_%s" % (pick, args.type)}
            entry["review"]["mesh"] = None
            entry["review"]["part"] = None
            entry["part"] = None

        batch.update(brief["id"], record)
        print("mesh", brief["id"])


def saved_placement(definition):
    """The placement an earlier part definition holds, or None."""
    if not definition.exists():
        return None
    text = "\n".join(line for line in definition.read_text(encoding="utf8").splitlines()
                     if not line.lstrip().startswith("//"))
    try:
        parts = json.loads(text)
    except json.JSONDecodeError:
        print("Couldn't read %s; its placement, if any, is lost" % definition)
        return None
    for part in parts if isinstance(parts, list) else [parts]:
        if part.get("placement"):
            return part["placement"]
    return None


def material_slot(name, tints):
    """A part's material slot: its tint mask channels take the costume's palette channels, and
    default to the colors the fit found, which give the part its own look."""
    defaults = json.loads(tints.read_text(encoding="utf8")) if tints.exists() else {}
    slot = {"name": name, "template": TEMPLATE}
    if defaults:
        slot["channels"] = {mask: MASK_PALETTE[mask] for mask in defaults}
        slot["defaults"] = defaults
    return slot


def painted_area(config, brief, out):
    """Where the brief's picked image was painted, as a mask the guide's size: the pixels that
    differ from the mannequin's render, inside the layer's mask, with gaps closed, holes filled
    (a garment shaded like the bare body leaves some) and crumbs dropped. Gives its path."""
    from PIL import Image, ImageDraw, ImageFilter
    import numpy as np
    layer, rule = rule_of(brief)
    allowed = pathlib.Path(config["comfy"]) / "input" / guide_inputs(config, layer, rule)["mask"]
    gray = np.asarray(Image.open(guides(config, layer) / "gray.png").convert("RGB"),
                      dtype=float)
    folder = out.parent
    image = np.asarray(Image.open(folder / "images" / ("%d.png" % brief["review"]["pick"]))
                       .convert("RGB"), dtype=float)
    distance = np.sqrt(((image - gray) ** 2).sum(-1))
    differs = distance > PAINTED
    # Bare body the image repainted stays the mannequin's gray, if shaded differently. A
    # colored garment leaves out everything nearly gray; a gray, white or black one only what is
    # also about as light as the render, since its own color is nearly gray too
    chroma = image.max(-1) - image.min(-1)
    garment = distance > GARMENT_SURE
    colored = garment.any() and np.median(chroma[garment]) >= COLORED
    bare = chroma < BARE_CHROMA
    if not colored:
        bare &= np.abs(image.mean(-1) - gray.mean(-1)) < BARE_LIGHTNESS
    else:
        # The mannequin is gray, so color is the garment's even where it is shaded about as
        # dark as the render there, like a tan leg's inner side
        differs |= ~bare
    differs &= ~bare
    differs &= np.asarray(Image.open(allowed).convert("L")) > 127
    mask = Image.fromarray(differs.astype(np.uint8) * 255).filter(ImageFilter.MedianFilter(5))
    mask = mask.filter(ImageFilter.MaxFilter(PAINT_CLOSE))
    mask = mask.filter(ImageFilter.MinFilter(PAINT_CLOSE))
    # Closing bridges the gap between the legs again
    gap = None
    if rule.get("legGap"):
        guide = json.loads((guides(config, layer) / "guide.json").read_text(encoding="utf8"))
        gap = leg_gap(guides(config, layer), guide)
        mask = Image.fromarray(np.where(gap, 0, np.asarray(mask)).astype(np.uint8))
    # Whatever a flood from the corner can't reach is inside the garment
    flood = mask.copy()
    ImageDraw.floodfill(flood, (0, 0), 128)
    mask = np.where(np.asarray(flood) == 128, 0, 255).astype(np.uint8)
    # Label the pieces by flooding each in turn, then keep the big ones
    # A copy: flood fills don't write into an image sharing the array's memory
    pieces = Image.fromarray(mask).copy()
    sizes = {}
    label = 1
    while True:
        left = np.argwhere(np.asarray(pieces) == 255)
        if not len(left) or label >= 255:
            break
        ImageDraw.floodfill(pieces, (int(left[0][1]), int(left[0][0])), label)
        sizes[label] = int((np.asarray(pieces) == label).sum())
        label += 1
    biggest = max(sizes.values(), default=0)
    keep = [k for k, size in sizes.items() if size >= biggest * PAINT_CRUMB]
    painted = np.isin(np.asarray(pieces), keep)
    # Closing gaps and filling holes fills in bare body a garment frames, like the neck in a
    # shirt's open collar, which a garment built from the skin then kept, ragged. The mannequin
    # is neutral gray, and even an off-white garment has some color, so neutral regions wider
    # than BARE_OPEN pixels come out again
    neutral = Image.fromarray((chroma < NEUTRAL_CHROMA).astype(np.uint8) * 255)
    neutral = neutral.filter(ImageFilter.MinFilter(BARE_OPEN))
    neutral = neutral.filter(ImageFilter.MaxFilter(BARE_OPEN))
    painted &= ~(np.asarray(neutral) > 127)
    path = out / "painted.png"
    Image.fromarray(painted.astype(np.uint8) * 255).save(path)
    # A garment's back is whole where its front is open, like a jacket's neckline: gaps are
    # closed further for what faces away from the camera
    back = Image.open(path).filter(ImageFilter.MaxFilter(BACK_CLOSE))
    back = back.filter(ImageFilter.MinFilter(BACK_CLOSE))
    if gap is not None:
        back = Image.fromarray(np.where(gap, 0, np.asarray(back)).astype(np.uint8))
    back.save(out / "painted_back.png")
    return path


def shell_slots(brief):
    """The body slots a garment built from the mannequin's skin is made of (see
    blender/shell_layer.py): those its layers cover, and the pelvis for tops, which hang past
    the waist."""
    slots = list(layer_of(brief)[0]["covers"])
    if "torso" in slots and "pelvis" not in slots:
        slots.append("pelvis")
    return ",".join(slots)


def flap_args(rule):
    """shell_layer.py's and variant_layer.py's arguments for a layer's flap and cuffs (see
    layers.json)."""
    out = []
    for key in ("flap", "cuffs"):
        if key in rule:
            out += ["--" + key, "%s,%s" % (rule[key]["width"], rule[key]["lift"])]
    return out


def cut_args(rule):
    """shape_layer.py's and variant_layer.py's arguments for a layer's cut (see layers.json)."""
    if "cut" not in rule:
        return []
    cut = rule["cut"]
    return ["--cut", "%s,%s,%s" % (cut["seam"], cut["keep"], cut.get("offset", 0))]


def simplifier_args(config):
    """shape_layer.py's arguments for reducing with Ikala-Graphics' simplifier, given the
    config's "simplifier" jars, or none for Blender's decimation."""
    jars = config.get("simplifier")
    if not jars:
        return []
    return ["--simplifier", os.pathsep.join(jars), "--java", config.get("java", "java")]


def run_blender(config, script, log, *arguments):
    """Run one of the blender/ scripts headless, its output in a log. Gives whether it worked."""
    with open(log, "w", encoding="utf8") as stream:
        result = subprocess.run(
            [config["blender"], *BLENDER_FLAGS, "--python",
             str(TOOLS / "blender" / script), "--"] + [str(a) for a in arguments],
            stdout=stream, stderr=subprocess.STDOUT)
    return result.returncode == 0


def fit_layer(config, batch, brief):
    """Line a clothing figure up on the mannequin it was painted on, cut the garment out of it
    by where the image was painted, shape it to sit outside the body within its triangle budget,
    weight it to the rig, checking it in a few poses, bake its textures and tint masks, and
    write it to the content folder as a layer part, with a female version."""
    folder = batch.brief_folder(brief["id"])
    out = folder / "fit"
    out.mkdir(exist_ok=True)
    body = pathlib.Path(config["mannequin"]).with_suffix(".blend")
    painted = painted_area(config, brief, out)
    with open(folder / "fit.log", "w", encoding="utf8") as stream:
        result = subprocess.run(
            [config["blender"], *BLENDER_FLAGS, "--python",
             str(TOOLS / "blender/fit_layer.py"), "--",
             "--mesh", str(folder / brief["mesh"]["file"]), "--body", str(body), "--out", str(out),
             "--painted", str(painted), "--painted-back", str(out / "painted_back.png"),
             "--guide", str(guides(config) / "guide.json")],
            stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode != 0 or not (out / "fit.json").exists():
        print("Lining up failed for %s; see %s" % (brief["id"], folder / "fit.log"))
        return
    report = json.loads((out / "fit.json").read_text(encoding="utf8"))
    rule = layer_of(brief)[1]
    with open(folder / "shape.log", "w", encoding="utf8") as stream:
        result = subprocess.run(
            [config["blender"], *BLENDER_FLAGS, "--python",
             str(TOOLS / "blender/shape_layer.py"), "--",
             "--garment", str(out / "garment.glb"), "--body", str(body), "--out", str(out),
             "--gap", str(rule["gap"]), "--triangles", str(rule["triangles"]),
             "--bare", ",".join(bare_ends(layer_of(brief)[0])),
             "--painted", str(painted), "--guide", str(guides(config) / "guide.json")]
            + (["--reach", str(rule["reach"])] if "reach" in rule else [])
            + cut_args(rule)
            + simplifier_args(config),
            stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode != 0 or not (out / "shape.json").exists():
        print("Shaping failed for %s; see %s" % (brief["id"], folder / "shape.log"))
        return
    report["shape"] = json.loads((out / "shape.json").read_text(encoding="utf8"))
    if rule.get("shell"):
        # The mannequin's skin, cut where the image painted, replaces the reshaped figure
        shelled = run_blender(
            config, "shell_layer.py", folder / "shell.log",
            "--garment", out / "garment.glb", "--body", body, "--out", out,
            "--painted", painted, "--painted-back", out / "painted_back.png",
            "--guide", guides(config) / "guide.json", "--slots", shell_slots(brief),
            "--gap", rule["gap"], "--triangles", rule["triangles"],
            *(["--reach", rule["reach"]] if "reach" in rule else []),
            *cut_args(rule), *flap_args(rule), *simplifier_args(config))
        if not shelled or not (out / "shell.json").exists():
            print("Building the shell failed for %s; see %s" % (brief["id"],
                                                               folder / "shell.log"))
            return
        report["shell"] = json.loads((out / "shell.json").read_text(encoding="utf8"))
    weighted = run_blender(config, "weight_layer.py", folder / "weights.log",
                           "--garment", out / "shaped.glb", "--body", body, "--out", out,
                           "--bare", ",".join(bare_ends(layer_of(brief)[0])))
    if not weighted or not (out / "weights.json").exists():
        print("Weighting failed for %s; see %s" % (brief["id"], folder / "weights.log"))
        return
    report["poses"] = json.loads((out / "weights.json").read_text(encoding="utf8"))["poses"]
    image = folder / "images" / ("%d.png" % brief["review"]["pick"])
    baked = run_blender(config, "bake_layer.py", folder / "bake.log",
                        "--weighted", out / "weighted.blend", "--figure", out / "pushed.glb",
                        "--rebuilt", out / "rebuilt.glb",
                        *(["--max-lean", SHELL_LEAN] if rule.get("shell") else []),
                        "--image", image, "--painted", painted,
                        "--guide", guides(config) / "guide.json", "--out", out,
                        "--name", brief["id"])
    if not baked or not (out / "bake.json").exists():
        print("Baking failed for %s; see %s" % (brief["id"], folder / "bake.log"))
        return
    report["bake"] = json.loads((out / "bake.json").read_text(encoding="utf8"))
    part_id = write_layer_part(config, batch, brief, out, rule)
    if part_id is None:
        return

    def record(entry):
        entry["lineUp"] = report
        entry["part"] = part_id
        entry["review"]["part"] = None

    batch.update(brief["id"], record)
    print("part", part_id, report)


def layer_slot(name, layers, defaults):
    """A clothing part's material slot. Each layer tints with its own palette channel, so a
    shirt, trousers and a jacket worn together can differ: an outfit's tint groups take its
    layers' channels in order, and any group past them the accent."""
    rules = json.loads((TOOLS / "layers.json").read_text(encoding="utf8"))
    channels = [rules[layer]["palette"] for layer in layers if "palette" in rules[layer]]
    slot = {"name": name, "template": TEMPLATE}
    if defaults:
        masks = sorted(defaults)
        slot["channels"] = {mask: channels[i] if i < len(channels) else LAYER_EXTRA_CHANNEL
                            for i, mask in enumerate(masks)}
        slot["defaults"] = defaults
    return slot


def layer_forms():
    """The body plan's clothing forms past the first, as (name, share of each slot's radius):
    the shells a layer is pushed out to over bulky body parts, the last the bulkiest."""
    return [(form["id"], float(form["share"]))
            for form in load_json_with_comments(PLAN).get("forms", [])[1:]]


def plan_radii(variant):
    """Each body slot's radius on a variant, in meters (see the body plan's slots)."""
    plan = load_json_with_comments(PLAN)
    own = next((v.get("radii", {}) for v in plan["variants"] if v["id"] == variant), {})
    return {slot["id"]: float(own.get(slot["id"], slot.get("radius", 0.0)))
            for slot in plan["slots"]}


def layer_limits(brief):
    """The slots a layer brief's layers cover, and the most it may stand out over them, as a
    share of each slot's radius: the least reach of its layers that give one, or None."""
    plan = load_json_with_comments(PLAN)
    found = [layer for layer in plan["layers"] if layer["id"] in layers_of(brief)]
    slots = []
    for layer in found:
        slots += [s for s in layer["covers"] if s not in slots]
    reaches = [layer["reach"] for layer in found if layer.get("reach") is not None]
    return slots, min(reaches) if reaches else None


def add_form_targets(config, folder, brief, out, rule, variant, target, log_name=None):
    """Give a layer's mesh its clothing form targets in place (see form_layer.py): a move per body
    slot out to the bulkiest form, which the character code scales by how far the body part worn
    there stands out, after pulling a layer others go over in to its reach. Its report (form.json
    in the preview folder) says which slots it really covers and how far it stands out over each.
    Gives whether it worked."""
    forms = layer_forms()
    if not forms or not rule.get("forms", True):
        return True
    mannequins = pathlib.Path(config["mannequin"]).parent
    suffix = "" if variant == GUIDE_VARIANT else "_" + variant
    log = folder / ("%s%s.log" % (log_name or "form", suffix))
    temporary = target.with_name(target.stem + "_forms.glb")
    slots, reach = layer_limits(brief)
    if not run_blender(config, "form_layer.py", log, "--mesh", target,
                       "--body", mannequins / ("%s.blend" % variant),
                       "--share", forms[-1][1], "--radii", json.dumps(plan_radii(variant)),
                       "--slots", ",".join(slots),
                       *(["--reach", reach] if reach is not None else []),
                       "--gap", rule["gap"],
                       "--out", temporary, "--preview", out / ("form" + suffix)):
        print("The %s form targets failed for %s; see %s" % (variant, brief["id"], log))
        temporary.unlink(missing_ok=True)
        return False
    temporary.replace(target)
    return True


def write_layer_part(config, batch, brief, out, rule, only_forms=False):
    """Put a baked garment in the content folder as a layer part: the male mesh, a female one
    carried over through the body cages, each with its clothing form targets (see
    add_form_targets) and its tint mask beside it (found by the file's name), and the part's
    definition. Other body types wear the male's. With only_forms, the meshes already there are
    kept and only given their form targets again. Gives the part's ID, or None if a mesh
    failed."""
    import shutil
    folder = batch.brief_folder(brief["id"])
    content = pathlib.Path(config["content"])
    mannequins = pathlib.Path(config["mannequin"]).parent
    name = brief["id"]
    meshes = {}
    for variant in (GUIDE_VARIANT, "female"):
        suffix = "" if variant == GUIDE_VARIANT else "_" + variant
        glb = "layer/%s%s.glb" % (name, suffix)
        meshes["*" if variant == GUIDE_VARIANT else variant] = "%s#%s" % (glb, name)
        target = content / glb
        target.parent.mkdir(parents=True, exist_ok=True)
        # Meshes for whole forms came before form targets
        for form, _ in layer_forms():
            old = content / ("layer/%s%s_%s.glb" % (name, suffix, form))
            old.unlink(missing_ok=True)
            shutil.rmtree(old.with_name(old.stem + "_masks"), ignore_errors=True)
        if only_forms and target.exists():
            if not add_form_targets(config, folder, brief, out, rule, variant, target):
                return None
            continue
        log = folder / ("variant%s.log" % suffix)
        if variant == GUIDE_VARIANT:
            shutil.copyfile(out / "baked.glb", target)
        elif not run_blender(config, "variant_layer.py", log,
                             "--baked", out / "baked.glb",
                             "--body", mannequins / ("%s.blend" % variant),
                             "--from-cage", mannequins / ("%s_cage.npz" % GUIDE_VARIANT),
                             "--to-cage", mannequins / ("%s_cage.npz" % variant),
                             "--gap", rule["gap"],
                             "--name", name, "--out", target, "--preview", out,
                             "--bare", ",".join(bare_ends(layer_of(brief)[0])),
                             *(["--reach", rule["reach"]] if "reach" in rule else []),
                             *cut_args(rule),
                             *(["--shell-slots", shell_slots(brief), "--masks",
                                out / (name + "_masks") / (name + "_emit.png"),
                                "--triangles", rule["triangles"], *flap_args(rule),
                                *simplifier_args(config)]
                               if rule.get("shell") else [])):
            print("The %s mesh failed for %s; see %s" % (variant, name, log))
            return None
        if not add_form_targets(config, folder, brief, out, rule, variant, target):
            return None
        masks = target.with_name(target.stem + "_masks")
        shutil.rmtree(masks, ignore_errors=True)
        # A variant built afresh bakes its own mask beside the fit; others share the guide's UVs
        own = out / masks.name
        if variant != GUIDE_VARIANT and own.exists():
            shutil.copytree(own, masks)
        else:
            shutil.copytree(out / (name + "_masks"), masks)
    defaults = json.loads((out / "bake.json").read_text(encoding="utf8"))["defaults"]
    layers = layers_of(brief)
    part_id = "%s:part/%s/%s" % (config["namespace"], layers[0], name)
    part = {
        "id": part_id,
        "plan": PLAN_ID,
        "kind": "layer",
        "fills": layers,
        "meshes": meshes,
        "materialSlots": [layer_slot(name, layers, defaults)],
        "tags": brief.get("tags", []),
    }
    # What the guide's mesh really covers and how far it stands out, from its form targets' report
    formed = out / "form" / "form.json"
    if formed.exists():
        report = json.loads(formed.read_text(encoding="utf8"))
        if set(report.get("covers", [])) != set(layer_limits(brief)[0]):
            part["covers"] = report.get("covers", [])
        if report.get("standoff"):
            part["standoff"] = report["standoff"]
    definition = content / "part" / ("%s.json" % name)
    definition.parent.mkdir(parents=True, exist_ok=True)
    header = ("// %s, batch %s, image %d\n"
              "// Written by Ikala-Characters/tools/partgen/partgen.py\n"
              % (brief["description"], batch.name, brief["mesh"]["image"]))
    definition.write_text(header + json.dumps([part], indent=2) + "\n", encoding="utf8")
    return part_id


def other_side(name):
    """A slot's, seam's or bone's name on the other side: .L for .R and the other way."""
    for left, right in ((".L", ".R"), (".R", ".L")):
        if name.endswith(left):
            return name[:-len(left)] + right
    return name


def body_slot(name, defaults, skin, rule):
    """A body part's material slot: its skin-like tint group takes the character's skin color,
    its main other one the rule's palette channel, and any further ones the accent, each showing
    the part's own color until the design colors that channel."""
    slot = {"name": name, "template": TEMPLATE}
    if defaults:
        others = iter([rule.get("palette", LAYER_EXTRA_CHANNEL)])
        slot["channels"] = {mask: SKIN_CHANNEL if mask == skin
                            else next(others, LAYER_EXTRA_CHANNEL)
                            for mask in sorted(defaults)}
        slot["defaults"] = defaults
    return slot


def write_body_part(config, batch, brief, part, rule):
    """Put a fitted body part in the content folder as two body parts, the left (as generated)
    and the right (mirrored, re-welded to the right side's rings by blender/variant_body.py),
    each with a male mesh, a female one and their tint masks, and their definitions. Gives the
    parts' IDs, or None if a mesh failed."""
    import shutil
    folder = batch.brief_folder(brief["id"])
    out = folder / "fit"
    content = pathlib.Path(config["content"])
    mannequins = pathlib.Path(config["mannequin"]).parent
    bake = json.loads((out / "bake.json").read_text(encoding="utf8"))
    cages = ["--from-cage", mannequins / ("%s_cage.npz" % GUIDE_VARIANT),
             "--to-cage", mannequins / "female_cage.npz"]
    ids = []
    definitions = []
    shaped = json.loads((out / "shape.json").read_text(encoding="utf8")).get("extents", {})
    forms = [shaped.get("form")]
    for side in ("l", "r"):
        name = "%s_%s" % (brief["id"], side)
        mirror = side == "r"
        slots = [other_side(s) if mirror else s for s in part["covers"]]
        seams = {other_side(s) if mirror else s: mode for s, mode in part["seams"].items()}
        left_open = ",".join(s for s, mode in seams.items() if mode == "open")
        meshes = {}
        for variant, body, extra in ((GUIDE_VARIANT, GUIDE_VARIANT, []),
                                     ("female", "female", cages)):
            glb = "body/%s%s.glb" % (name, "" if variant == GUIDE_VARIANT else "_female")
            target = content / glb
            target.parent.mkdir(parents=True, exist_ok=True)
            log = folder / ("variant_%s_%s.log" % (side, variant))
            if not run_blender(config, "variant_body.py", log,
                               "--baked", out / "baked.glb",
                               "--body", mannequins / ("%s.blend" % body), *extra,
                               *(["--mirror"] if mirror else []),
                               "--slots", ",".join(slots), "--name", name, "--out", target,
                               "--preview", out / ("%s_%s" % (side, variant)),
                               *(["--open", left_open] if left_open else [])):
                print("The %s %s mesh failed for %s; see %s" % (variant, side, brief["id"], log))
                return None
            variant_report = out / ("%s_%s" % (side, variant)) / "variant.json"
            if variant_report.exists():
                forms.append(json.loads(variant_report.read_text(encoding="utf8")).get("form"))
            masks = target.with_name(target.stem + "_masks")
            shutil.rmtree(masks, ignore_errors=True)
            shutil.copytree(out / (brief["id"] + "_masks"), masks)
            meshes["*" if variant == GUIDE_VARIANT else variant] = "%s#%s" % (glb, name)
        part_id = "%s:part/body/%s" % (config["namespace"], name)
        definition = {
            "id": part_id,
            "plan": PLAN_ID,
            "kind": "body",
            "fills": slots,
            "meshes": meshes,
            "materialSlots": [body_slot(brief["id"], bake["defaults"], bake.get("skin"), rule)],
            "tags": brief.get("tags", []),
            # The other side's part, which the editor puts on along with this one
            "pair": "%s:part/body/%s_%s" % (config["namespace"], brief["id"],
                                          "r" if side == "l" else "l"),
        }
        if any(mode != "weld" for mode in seams.values()):
            definition["seams"] = {s: mode for s, mode in seams.items() if mode != "weld"}
        definitions.append((side, name, definition))
        ids.append(part_id)
    # One form for both sides and bodies, the thickest any of them needs; a part too bulky for
    # clothing that is left bare hides the layers over it instead
    form = None if shaped.get("bare") else thickest_form(f for f in forms if f)
    for side, name, definition in definitions:
        if shaped.get("bare"):
            # Clothing covering its slots can't be worn with it
            definition["bare"] = True
        elif form is not None:
            definition["form"] = form
        path = content / "part" / ("%s.json" % name)
        path.parent.mkdir(parents=True, exist_ok=True)
        header = ("// %s, %s side, batch %s, image %d\n"
                  "// Written by Ikala-Characters/tools/partgen/partgen.py\n"
                  % (brief["description"], "left" if side == "l" else "right", batch.name,
                     brief["mesh"]["image"]))
        path.write_text(header + json.dumps([definition], indent=2) + "\n", encoding="utf8")
    measure_standoffs(config, batch, brief)
    return ids


def measure_standoffs(config, batch, brief):
    """Measure how far each side of a body part stands out over each slot it fills
    (blender/measure_standoff.py, on its guide mesh) and write it into its part definition as
    "standoff", which clothing over it is pushed out by. Gives whether every side worked."""
    folder = batch.brief_folder(brief["id"])
    content = pathlib.Path(config["content"])
    worked = True
    for side in ("l", "r"):
        name = "%s_%s" % (brief["id"], side)
        path = content / "part" / ("%s.json" % name)
        if not path.exists():
            continue
        text = path.read_text(encoding="utf8")
        header = "".join(line for line in text.splitlines(keepends=True)
                         if line.startswith("//"))
        definitions = load_json_with_comments(path)
        definition = definitions[0]
        glb = definition["meshes"]["*"].split("#", 1)[0]
        out = folder / "fit" / ("standoff_%s.json" % side)
        log = folder / ("standoff_%s.log" % side)
        if not run_blender(config, "measure_standoff.py", log, "--part", content / glb,
                           "--mannequin", config["mannequin"],
                           "--slots", ",".join(definition["fills"]), "--out", out):
            print("Measuring %s's standoff failed; see %s" % (name, log))
            worked = False
            continue
        definition["standoff"] = json.loads(out.read_text(encoding="utf8"))
        path.write_text(header + json.dumps(definitions, indent=2) + "\n", encoding="utf8")
        print("standoff", name, json.dumps(definition["standoff"]))
    return worked


def fit_body_part(config, batch, brief):
    """Fit a body part's mesh to the mannequin: line it up and cut it out at its seams
    (blender/fit_body.py), shape it into a solid welded to its welded seams' rings
    (shape_body.py), weight it (weight_body.py) and bake its textures (bake_body.py), all in
    the brief's fit folder. Gives the reports, or None if a step failed."""
    part, rule = body_of(brief)
    fit = dict(rule, **brief.get("fit", {}))
    folder = batch.brief_folder(brief["id"])
    out = folder / "fit"
    out.mkdir(exist_ok=True)
    body = pathlib.Path(config["mannequin"]).with_suffix(".blend")
    slots = ",".join(part["covers"])
    left_open = ",".join(seam for seam, mode in part["seams"].items() if mode == "open")
    steps = [
        ("fit_body.py", "fit.json",
         ["--mesh", folder / brief["mesh"]["file"], "--body", body, "--out", out,
          "--slots", slots, "--guide", guides(config, part) / "guide.json",
          "--image", folder / "images" / ("%d.png" % brief["review"]["pick"])]
         + (["--isolated"] if part["isolated"] else [])),
        ("shape_body.py", "shape.json",
         ["--part", out / "part.glb", "--body", body, "--out", out, "--slots", slots,
          "--triangles", rule["triangles"], "--beyond", beyond_of(brief, rule)]
         # A brief may let a part be reined in further, like a bulky robot leg made to fit pants
         + (["--rein-least", fit["reinLeast"]] if "reinLeast" in fit else [])
         + (["--open", left_open] if left_open else [])
         + (["--voxel", rule["voxel"]] if "voxel" in rule else [])),
        ("weight_body.py", "weights.json",
         ["--shaped", out / "shaped.blend", "--out", out, "--slots", slots]),
        ("bake_body.py", "bake.json",
         ["--weighted", out / "weighted.blend", "--figure", out / "part.glb",
          "--image", folder / "images" / ("%d.png" % brief["review"]["pick"]),
          "--painted", painted_area(config, brief, out), "--guide",
          guides(config, part) / "guide.json", "--out", out, "--name", brief["id"],
          "--slots", slots]),
    ]
    reports = {}
    for script, report, arguments in steps:
        log = folder / (pathlib.Path(script).stem + ".log")
        if not run_blender(config, script, log, *arguments) or not (out / report).exists():
            rejected = None
            if script == "shape_body.py" and (out / report).exists():
                shaped = json.loads((out / report).read_text(encoding="utf8"))
                rejected = shaped.get("extents", {}).get("rejected")
            if rejected:
                # Too big for clothing: left for review, with why
                print("%s rejected: %s" % (brief["id"], rejected))

                def note(entry, rejected=rejected):
                    entry.setdefault("notes", {})["parts"] = "rejected: " + rejected

                batch.update(brief["id"], note)
            else:
                print("%s failed for %s; see %s" % (script, brief["id"], log))
            return None
        reports[pathlib.Path(report).stem] = json.loads((out / report).read_text(encoding="utf8"))
    gaps = [p["seam_gap_mm"] for p in reports["weights"]["poses"].values()]
    extents = reports["shape"].get("extents", {})
    print("body %s: %d triangles, welded %s, open %s, seams move at most %.3f mm in poses;"
          " form %s%s%s%s" % (brief["id"], reports["shape"]["triangles"],
                              reports["shape"]["welded"] or "none",
                              reports["shape"]["open"] or "none", max(gaps, default=0),
                              "bare" if extents.get("bare") else extents.get("form"),
                              ", %s vertices clamped" % extents["clamped"]
                              if extents.get("clamped") else "",
                              ", floor x%s" % extents["floor_scale"]
                              if extents.get("floor_scale") else "",
                              ", hand x%s" % extents["hand_scale"]
                              if extents.get("hand_scale") else ""))
    return reports


def beyond_of(brief, rule):
    """What to do with a body part standing out past the thickest clothing form over a clothed
    slot: nothing for a brief whose part is worn bare ("bare": true, like spiked arms), else
    what its "fit" or rule says, else rein it in, or reject it if that is too much."""
    if brief.get("bare", False):
        return "bare"
    return brief.get("fit", {}).get("beyond", rule.get("beyond", "clamp"))


def thickest_form(names):
    """The thickest of some clothing form names by the body plan's order, None for none."""
    order = [form["id"] for form in load_json_with_comments(PLAN).get("forms", [])]
    known = [n for n in names if n in order]
    return max(known, key=order.index) if known else None


def command_fit(config, batch, args):
    if args.forms:
        # Only the clothing forms' meshes, for layers fitted before forms
        for brief in ready(batch.load(), args, lambda b: b["kind"] == "layer"
                           and b["part"] is not None and not is_armor(b)):
            out = batch.brief_folder(brief["id"]) / "fit"
            part_id = write_layer_part(config, batch, brief, out, layer_of(brief)[1],
                                       only_forms=True)
            print("forms", part_id or "failed for " + brief["id"])
        return
    if args.standoff:
        # Only how far body parts stand out over their slots, for parts fitted before it was kept
        for brief in ready(batch.load(), args,
                           lambda b: b["kind"] == "body" and b["part"] is not None):
            measure_standoffs(config, batch, brief)
        return
    briefs = ready(batch.load(), args, lambda b: (b["review"]["pick"] is not None
                                                  if templated(b) else
                                                  b["mesh"] is not None and kept(b, "mesh", args))
                   and (args.redo or b["part"] is None))
    for brief in briefs:
        if brief["kind"] == "extension":
            ids = fit_extension(config, batch, brief)
            if ids:
                def record(entry, part_id=ids[0]):
                    entry["part"] = part_id
                    entry["review"]["part"] = None

                batch.update(brief["id"], record)
                print("part", " ".join(ids))
            continue
        if is_armor(brief):
            ids = fit_armor(config, batch, brief)
            if ids:
                def record(entry, part_id=ids[0]):
                    entry["part"] = part_id
                    entry["review"]["part"] = None

                batch.update(brief["id"], record)
                print("part", " ".join(ids))
            continue
        if brief["kind"] == "layer":
            if layer_of(brief)[1].get("view", "front") != "front":
                print("Fitting clothing painted from the %s isn't built yet; %s keeps its mesh"
                      % (layer_of(brief)[1]["view"], brief["id"]))
                continue
            fit_layer(config, batch, brief)
            continue
        if brief["kind"] == "body":
            reports = fit_body_part(config, batch, brief)
            if reports is None:
                continue
            part, rule = body_of(brief)
            ids = write_body_part(config, batch, brief, part, rule)
            if ids is not None:
                def record(entry, ids=ids, extents=reports["shape"].get("extents", {})):
                    entry["part"] = ids[0]
                    entry["review"]["part"] = None
                    # The clothing form and size corrections, for the review page
                    entry["extents"] = extents
                    entry.get("notes", {}).pop("parts", None)

                batch.update(brief["id"], record)
                print("part", " ".join(ids))
            continue
        ids = fit_accessory(config, batch, brief)
        if ids:
            def record(entry, part_id=ids[0]):
                entry["part"] = part_id
                entry["review"]["part"] = None

            batch.update(brief["id"], record)
            print("part", " ".join(ids))


def templated(brief):
    """Whether a brief's part is made from a template and its picture alone, with no mesh, like
    a cape (an extension rule's "template")."""
    return brief["kind"] == "extension" and bool(extension_of(brief)[1].get("template"))


def cut_out(config, folder, pick):
    """A mask of the picked picture's object, cut out by BiRefNet (see cut_out.py), made the
    first time. Gives its path."""
    mask = folder / ("cut_%d.png" % pick)
    if not mask.exists():
        trellis = pathlib.Path(config["trellis"])
        subprocess.run([str(trellis / ".venv/Scripts/python.exe"), str(TOOLS / "cut_out.py"),
                        "--trellis", str(trellis), str(folder / "images" / ("%d.png" % pick)),
                        str(mask)], check=True)
    return mask


def fit_extension(config, batch, brief):
    """Fit a brief's mesh along its extension chain (see blender/fit_extension.py and
    extensions.json) on each body variant and write its part: an accessory on the chain, with
    the chain made as long as the brief says. The guide's body is fitted first, from the brief's
    length; the other bodies match its chain length, since a part has one. A wing gets a part for
    each side, the right the left's mirror, paired. Gives the part IDs, the brief's own side
    first, or None if a fit failed."""
    part, rule = extension_of(brief)
    folder = batch.brief_folder(brief["id"])
    content = pathlib.Path(config["content"])
    plan = load_json_with_comments(PLAN)
    chains = {"extensions": [c for c in plan["extensions"]
                             if c["id"] in (part["chain"], other_side(part["chain"]))]}
    sided = part["chain"].endswith(".L")
    names = (["%s_l" % brief["id"], "%s_r" % brief["id"]] if sided else [brief["id"]])
    meshes = [{} for _ in names]
    chain_scale = None
    # A template part, like a cape, is made from its picture, cut out; others from their mesh
    template = bool(rule.get("template"))
    pick = brief["review"]["pick"] if template else brief["mesh"]["image"]
    if template:
        source = ["--image", str(folder / "images" / ("%d.png" % pick)),
                  "--mask", str(cut_out(config, folder, pick))]
        script = "blender/fit_cape.py"
    else:
        source = ["--mesh", str(folder / brief["mesh"]["file"]), "--chain", part["chain"]]
        script = "blender/fit_extension.py"
    for variant in (GUIDE_VARIANT, "female"):
        suffix = "" if variant == GUIDE_VARIANT else "_" + variant
        out = content / "extension" / ("%s%s.glb" % (names[0], suffix))
        out.parent.mkdir(parents=True, exist_ok=True)
        renders = folder / ("fit%s" % suffix)
        renders.mkdir(exist_ok=True)
        log = folder / ("fit%s.log" % suffix)
        report = folder / ("fit%s.json" % suffix)
        mannequin = pathlib.Path(config["mannequin"]).parent / ("%s.glb" % variant)
        size = (["--length", str(part["length"])] if chain_scale is None
                else ["--chain-scale", str(chain_scale)])
        with open(log, "w", encoding="utf8") as stream:
            result = subprocess.run(
                [config["blender"], *BLENDER_FLAGS, "--python", str(TOOLS / script),
                 "--"] + source + [
                 "--mannequin", str(mannequin), "--plan", json.dumps(chains),
                 "--rule", json.dumps(rule), "--name", names[0], "--out", str(out),
                 "--preview", str(renders), "--tints", str(folder / ("tints%s.json" % suffix)),
                 "--report", str(report)] + size
                + (["--mirror", names[1]] if sided else []),
                stdout=stream, stderr=subprocess.STDOUT)
        if result.returncode != 0 or not report.exists():
            print("fit failed for %s on the %s body; see %s" % (brief["id"], variant, log))
            return None
        if chain_scale is None:
            chain_scale = json.loads(report.read_text(encoding="utf8"))["chainLength"]
        for side, name in enumerate(names):
            key = "*" if variant == GUIDE_VARIANT else variant
            # Meshes and their material keep one name on every body, as the material slot names it
            meshes[side][key] = "extension/%s%s.glb#%s" % (name, suffix, name)
    kind = part["chain"].rsplit(".", 1)[0] if sided else part["chain"]
    ids = ["%s:part/%s/%s" % (config["namespace"], kind, name) for name in names]
    tints = folder / "tints.json"
    for side, name in enumerate(names):
        chain = part["chain"] if side == 0 else other_side(part["chain"])
        definition = {"id": ids[side], "plan": PLAN_ID}
        # A chain named like a layer, like the cape, is that layer's place, so its part is
        # clothing in the layer that moves with the chain
        if any(layer["id"] == chain for layer in plan["layers"]):
            definition.update({"kind": "layer", "fills": [chain]})
        else:
            definition.update({"kind": "accessory", "rigid": False})
        definition.update({
            "extension": chain,
            "chainLength": [chain_scale],
            "meshes": meshes[side],
            # Both sides are one baked mesh, mirrored, so they share its material
            "materialSlots": [dict(material_slot(names[0], tints))],
            "tags": brief.get("tags", []),
        })
        if sided:
            definition["pair"] = ids[1 - side]
        path = content / "part" / ("%s.json" % name)
        path.parent.mkdir(parents=True, exist_ok=True)
        header = ("// %s, batch %s, image %d\n"
                  "// Written by Ikala-Characters/tools/partgen/partgen.py\n"
                  % (brief["description"], batch.name, pick))
        path.write_text(header + json.dumps([definition], indent=2) + "\n", encoding="utf8")
    return ids


def fit_accessory(config, batch, brief):
    """Fit a brief's mesh as an accessory on its socket (see blender/fit_accessory.py and
    sockets.json) and write its part. Each body variant gets its own fit on its own mannequin, so
    a smaller head or narrower shoulders get a smaller, nearer accessory. A sided socket (.L/.R)
    gets a part for each side, the other side's mirrored, paired so the editor puts both on
    together. Gives the part IDs, the brief's own side first, or None if a fit failed."""
    folder = batch.brief_folder(brief["id"])
    socket, rule = socket_of(brief)
    rule = dict(rule, **brief.get("fit", {}))
    content = pathlib.Path(config["content"])
    sides = [(socket, False)]
    if socket["id"].endswith((".L", ".R")):
        plan = load_json_with_comments(PLAN)
        mirror = next(s for s in plan["sockets"] if s["id"] == other_side(socket["id"]))
        sides.append((mirror, True))
    ids = []
    parts = []
    for place, mirrored in sides:
        name = brief["id"] if len(sides) == 1 else "%s_%s" % (brief["id"],
                                                              place["id"][-1].lower())
        meshes = {}
        for variant in (GUIDE_VARIANT, "female"):
            suffix = "" if variant == GUIDE_VARIANT else "_" + variant
            relative = "accessory/%s%s.glb" % (name, suffix)
            out = content / relative
            out.parent.mkdir(parents=True, exist_ok=True)
            side = "" if not mirrored else "_" + place["id"][-1].lower()
            renders = folder / ("fit%s%s" % (side, suffix))
            renders.mkdir(exist_ok=True)
            log = folder / ("fit%s%s.log" % (side, suffix))
            # The guide's colors name the part's tints; the other variants' are the same object
            tints = folder / ("tints%s.json" % suffix)
            mannequin = pathlib.Path(config["mannequin"]).parent / ("%s.glb" % variant)
            with open(log, "w", encoding="utf8") as stream:
                result = subprocess.run(
                    [config["blender"], *BLENDER_FLAGS, "--python",
                     str(TOOLS / "blender/fit_accessory.py"), "--",
                     "--mesh", str(folder / brief["mesh"]["file"]),
                     "--mannequin", str(mannequin),
                     "--bone", place["bone"], "--rule", json.dumps(rule), "--name", name,
                     "--out", str(out), "--preview", str(renders), "--tints", str(tints),
                     "--image", str(folder / "images" / ("%d.png" % brief["mesh"]["image"]))]
                    + (["--mirror"] if mirrored else []),
                    stdout=stream, stderr=subprocess.STDOUT)
            if result.returncode != 0:
                print("fit failed for %s on the %s body; see %s" % (name, variant, log))
                return None
            meshes["*" if variant == GUIDE_VARIANT else variant] = "%s#%s" % (relative, name)
        tints = folder / "tints.json"
        part_id = "%s:part/%s/%s" % (config["namespace"], socket["type"].replace(".", "_"), name)
        part = {
            "id": part_id,
            "plan": PLAN_ID,
            "kind": "accessory",
            "fits": [place["id"]],
            "rigid": True,
            "meshes": meshes,
            "materialSlots": [material_slot(name, tints)],
            "tags": brief.get("tags", []),
        }
        ids.append(part_id)
        parts.append((name, part))
    for name, part in parts:
        if len(ids) > 1:
            part["pair"] = next(other for other in ids if other != part["id"])
        definition = content / "part" / ("%s.json" % name)
        definition.parent.mkdir(parents=True, exist_ok=True)
        # A placement lined up by hand in the character editor outlives a refit
        placement = saved_placement(definition)
        if placement:
            part["placement"] = placement
        header = ("// %s, batch %s, image %d\n"
                  "// Written by Ikala-Characters/tools/partgen/partgen.py\n"
                  % (brief["description"], batch.name, brief["mesh"]["image"]))
        definition.write_text(header + json.dumps([part], indent=2) + "\n", encoding="utf8")
    return ids


def armor_part_path(layer_id):
    """The folder name a layer's armor parts' IDs go under: part paths are lowercase."""
    return layer_id.split(".")[0].lower()


def fit_armor(config, batch, brief):
    """Fit a brief's armor and write its parts (see armor.json). Each body variant gets its own fit
    on its own mannequin, as accessories do: a limb piece is lined up on the variant's slots by
    blender/fit_body.py (--armor: no sliding to the seams, nothing cut off), then, like a piece on a
    socket's surface or round the head, cleaned, placed, pushed out of the skin, reduced, baked,
    tinted and weighted by blender/fit_accessory.py, and given the layer's form targets. A piece
    for both sides comes out as one mesh with its mirror; one for a layer on one side gets a part
    per side, the right mirrored, paired. Gives the part IDs, the left's first, or None if a step
    failed."""
    folder = batch.brief_folder(brief["id"])
    part, rule = armor_of(brief)
    out = folder / "fit"
    out.mkdir(exist_ok=True)
    content = pathlib.Path(config["content"])
    mannequins = pathlib.Path(config["mannequin"]).parent
    plan = load_json_with_comments(PLAN)
    layer_id = part["layer"]["id"]
    image = folder / "images" / ("%d.png" % brief["mesh"]["image"])
    one_sided = layer_id.endswith(BODY_SIDE) and not rule.get("bothSides")
    sides = [(layer_id, False)] + ([(other_side(layer_id), True)] if one_sided else [])
    ids, parts = [], []
    for layer, mirrored in sides:
        name = brief["id"] if len(sides) == 1 else "%s_%s" % (brief["id"], layer[-1].lower())
        slots = [other_side(s) if mirrored else s for s in part["covers"]]
        side_brief = dict(brief, layer=layer)
        side_brief.pop("layers", None)
        meshes = {}
        for variant in (GUIDE_VARIANT, "female"):
            suffix = "" if variant == GUIDE_VARIANT else "_" + variant
            relative = "%s/%s%s.glb" % (ARMOR_FOLDER, name, suffix)
            target = content / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            renders = out / (name + suffix)
            renders.mkdir(exist_ok=True)
            mesh = folder / brief["mesh"]["file"]
            if rule["fit"] == "limb" and not mirrored:
                # Lined up on this body's slots, as an isolated body part is
                lined = out / ("limb" + suffix)
                log = folder / ("fit_body%s.log" % suffix)
                if not run_blender(config, "fit_body.py", log, "--mesh", mesh,
                                   "--body", mannequins / ("%s.blend" % variant),
                                   "--out", lined, "--slots", ",".join(part["covers"]),
                                   "--guide", guides(config, part) / "guide.json",
                                   "--image", image, "--isolated", "--armor",
                                   "--open-end", rule.get("openEnd", "toward"),
                                   *(["--anchor", rule["anchor"]] if "anchor" in rule else []),
                                   # The picture was painted over the guide body; the others
                                   # take its fit, carried onto their slots
                                   *([] if variant == GUIDE_VARIANT else
                                     ["--like", out / "limb" / "fit.json"])):
                    print("Lining up %s on the %s body failed; see %s"
                          % (brief["id"], variant, log))
                    return None
                mesh = lined / "part.glb"
            elif rule["fit"] == "limb":
                # The other side's: the first side's line-up, which fit_accessory.py mirrors
                mesh = out / ("limb" + suffix) / "part.glb"
            bone = rule.get("bone", "")
            if rule["fit"] == "surface" and mirrored:
                bone = next(s["bone"] for s in plan["sockets"]
                            if s["id"] == other_side(rule["socket"]))
            log = folder / ("fit_%s%s.log" % (name, suffix))
            if not run_blender(config, "fit_accessory.py", log, "--mesh", mesh,
                               "--mannequin", mannequins / ("%s.glb" % variant),
                               "--bone", bone, "--rule", json.dumps(rule), "--name", name,
                               "--out", target, "--preview", renders,
                               "--tints", out / ("tints_%s%s.json" % (name, suffix)),
                               "--image", image, "--slots", ",".join(slots),
                               *(["--mirror"] if mirrored else []),
                               *(["--both-sides"] if rule.get("bothSides") else [])):
                print("Fitting %s on the %s body failed; see %s" % (name, variant, log))
                return None
            if not add_form_targets(config, folder, side_brief, renders, rule, variant, target,
                                    log_name="form_" + name):
                return None
            meshes["*" if variant == GUIDE_VARIANT else variant] = "%s#%s" % (relative, name)
        layers = [layer]
        slot = material_slot(name, out / ("tints_%s.json" % name))
        if slot.get("channels"):
            # The biggest color group takes the rule's palette channel, the rest the accent
            masks = sorted(slot["channels"])
            slot["channels"] = {mask: rule.get("palette", "primary") if i == 0
                                else LAYER_EXTRA_CHANNEL for i, mask in enumerate(masks)}
        part_id = "%s:part/%s/%s" % (config["namespace"], armor_part_path(layer), name)
        definition = {
            "id": part_id,
            "plan": PLAN_ID,
            "kind": "layer",
            "fills": layers,
            "meshes": meshes,
            "materialSlots": [slot],
            "tags": brief.get("tags", []),
        }
        hides = brief.get("hides", rule.get("hides", {}))
        if hides.get("layers"):
            definition["hidesLayers"] = hides["layers"]
        if hides.get("regions"):
            definition["hidesRegions"] = hides["regions"]
        formed = renders.with_name(name) / "form" / "form.json"
        if formed.exists():
            report = json.loads(formed.read_text(encoding="utf8"))
            if set(report.get("covers", [])) != set(layer_limits(side_brief)[0]):
                definition["covers"] = report.get("covers", [])
            if report.get("standoff"):
                definition["standoff"] = report["standoff"]
        ids.append(part_id)
        parts.append((name, definition))
    for name, definition in parts:
        if len(ids) > 1:
            definition["pair"] = next(other for other in ids if other != definition["id"])
        path = content / "part" / ("%s.json" % name)
        path.parent.mkdir(parents=True, exist_ok=True)
        header = ("// %s, batch %s, image %d\n"
                  "// Written by Ikala-Characters/tools/partgen/partgen.py\n"
                  % (brief["description"], batch.name, brief["mesh"]["image"]))
        path.write_text(header + json.dumps([definition], indent=2) + "\n", encoding="utf8")
    return ids


def command_status(config, batch, args):
    manifest = batch.load()
    print("%-28s %-6s %-7s %-6s %-6s %-6s %-6s" % ("brief", "brief", "images", "pick", "mesh",
                                                   "part", "shots"))
    for b in manifest["briefs"]:
        review = b["review"]
        mesh = review["mesh"] or ("made" if b["mesh"] else "-")
        part = review["part"] or ("made" if b["part"] else "-")
        shots = sum(len(v) for v in (b.get("captures") or {}).values()) or "-"
        print("%-28s %-6s %-7d %-6s %-6s %-6s %-6s" % (b["id"], review["brief"] or "-",
                                                       len(b["images"]), review["pick"] or "-",
                                                       mesh, part, shots))


def dressed_layers(config, brief, slots=None):
    """The layers a body part is shown dressed in: those of config.json's "captureLayers" (else
    CAPTURE_LAYERS) whose body plan layers cover one of its slots (or the slots given), either
    side."""
    plan = load_json_with_comments(PLAN)
    covers = {layer["id"]: set(layer["covers"]) for layer in plan["layers"]}
    slots = set(brief.get("slots", []) if slots is None else slots)
    slots |= {other_side(s) for s in slots}
    content = pathlib.Path(config["content"]) / "part"
    chosen = []
    for part_id in config.get("captureLayers", CAPTURE_LAYERS):
        path = content / (part_id.rsplit("/", 1)[-1] + ".json")
        if not path.exists():
            continue
        fills = load_json_with_comments(path)[0].get("fills", [])
        if any(covers.get(fill, set()) & slots for fill in fills):
            chosen.append(part_id)
    return chosen


def thickest_body(config, slot):
    """The thickest generated body part filling a slot, by its clothing form, with its other
    side's, or nothing if none stands out."""
    plan = load_json_with_comments(PLAN)
    thickness = {form["id"]: form["share"] for form in plan.get("forms", [])}
    thickest = None
    for path in sorted((pathlib.Path(config["content"]) / "part").glob("*.json")):
        part = load_json_with_comments(path)[0]
        if part.get("kind") != "body" or slot not in part.get("fills", []):
            continue
        depth = thickness.get(part.get("form"), 0)
        if depth > 0 and (thickest is None or depth > thickest[0]):
            thickest = (depth, part["id"])
    if not thickest:
        return []
    under = [thickest[1]]
    if thickest[1][-2:] in ("_l", "_r"):
        under.append(thickest[1][:-1] + ("r" if thickest[1].endswith("_l") else "l"))
    return under


def under_accessory(config, brief):
    """What an accessory is shown worn over: the thickest generated body part filling the slot
    its socket rests on (both sides of a sided one), then the capture layers covering that slot.
    Nothing for a socket that doesn't rest on a slot."""
    socket, _ = socket_of(brief)
    rest = socket.get("rests")
    if not rest:
        return []
    return thickest_body(config, rest["slot"]) + dressed_layers(config, brief, {rest["slot"]})


def under_armor(config, brief):
    """What armor is shown worn over: the thickest generated body part on each slot it covers,
    then the capture layers that go under its layer and cover those slots, so it moves out over
    both."""
    part, _ = armor_of(brief)
    plan = load_json_with_comments(PLAN)
    order = {layer["id"]: layer.get("order", 0) for layer in plan["layers"]}
    content = pathlib.Path(config["content"]) / "part"
    under = []
    for slot in part["covers"]:
        under += [i for i in thickest_body(config, slot) if i not in under]
    for part_id in dressed_layers(config, brief, part["covers"]):
        fills = load_json_with_comments(content / (part_id.rsplit("/", 1)[-1] + ".json"))[0] \
            .get("fills", [])
        if all(order.get(fill, 0) < part["layer"].get("order", 0) for fill in fills):
            under.append(part_id)
    return under


def batch_armor(batch, brief):
    """The armor fitted in a body part's batch that covers its slots, either side, one piece a
    layer, to show the part dressed in."""
    slots = set(brief.get("slots", []))
    slots |= {other_side(s) for s in slots}
    chosen, layers = [], set()
    for other in batch.load()["briefs"]:
        if not other.get("part") or not is_armor(other):
            continue
        part, _ = armor_of(other)
        covers = set(part["covers"]) | {other_side(s) for s in part["covers"]}
        if covers & slots and part["layer"]["id"] not in layers:
            layers.add(part["layer"]["id"])
            chosen += worn_ids(other)
    return chosen


def worn_ids(brief):
    """The part IDs a brief's part is worn as: a body part on both sides, others once."""
    part = brief["part"]
    if (brief["kind"] == "body" or is_armor(brief)) and part.endswith("_l"):
        return [part, part[:-2] + "_r"]
    return [part]


def capture_script(config, batch, briefs):
    """An editor script that puts each brief's part on each mannequin and saves its shots: the
    body at rest from CAPTURE_VIEWS, then a close-up framed on the part. Returns the script and
    each brief's shots by variant, as paths in its folder."""
    lines = ["// Editor captures for batch %s" % batch.name,
             "// Written by Ikala-Characters/tools/partgen/partgen.py capture",
             "Object t = ui.test();",
             'converter.openTool("characters/editor");',
             "await(characters.settle());",
             "characters.markers(false);",
             "characters.still(true);"]
    # Part shots are of the character alone; config "captureGui" keeps the editor's panels in them
    if not config.get("captureGui", False):
        lines.append("t.showGui(false);")
    designs = config.get("captureDesigns", CAPTURE_DESIGNS)
    # The design the editor opens on can leave effects drifting for a while
    lines += ["characters.showDesign(%s);" % json.dumps(next(iter(designs.values()))),
              "await(characters.settle());",
              "await(t.frames(%d));" % CAPTURE_START_FRAMES]
    shots = {}
    for brief in briefs:
        folder = batch.brief_folder(brief["id"])
        ids = worn_ids(brief)
        shots[brief["id"]] = {}
        lines.append('converter.log(%s);' % json.dumps("capture brief " + brief["id"]))
        for variant, design in designs.items():
            lines += ["characters.showDesign(%s);" % json.dumps(design),
                      "await(characters.settle());",
                      "characters.bare();"]
            lines += ["characters.wear(%s);" % json.dumps(i) for i in ids]
            names = []
            for view, framed, pitch, yaw in CAPTURE_VIEWS:
                name = "%s/%s_%s.png" % (CAPTURE_FOLDER, variant, view)
                lines += ["characters.frame(%s, %r, %r);"
                          % (json.dumps(ids[0]) if framed else "null", pitch, yaw),
                          # Temporal anti-aliasing and exposure settle after the camera jumps
                          "await(t.frames(%d));" % CAPTURE_SETTLE_FRAMES,
                          "await(t.saveFrame(%s));" % json.dumps((folder / name).as_posix())]
                names.append(name)
            # Then dressed: clothing over a body part shows in its form for the part, and an
            # accessory moves out onto a thick body part and clothing under it
            if brief["kind"] == "body":
                # Under its batch's armor too, which moves out over it
                dressed = dressed_layers(config, brief) + batch_armor(batch, brief)
            elif is_armor(brief):
                dressed = under_armor(config, brief)
            elif brief["kind"] == "accessory":
                dressed = under_accessory(config, brief)
            else:
                dressed = []
            lines += ["characters.wear(%s);" % json.dumps(i) for i in dressed]
            views = (CAPTURE_DRESSED_ACCESSORY_VIEWS
                     if brief["kind"] == "accessory" or is_armor(brief)
                     else CAPTURE_DRESSED_VIEWS)
            for view, framed, pitch, yaw in views if dressed else ():
                name = "%s/%s_dressed_%s.png" % (CAPTURE_FOLDER, variant, view)
                lines += ["characters.frame(%s, %r, %r);"
                          % (json.dumps(ids[0]) if framed else "null", pitch, yaw),
                          "await(t.frames(%d));" % CAPTURE_SETTLE_FRAMES,
                          "await(t.saveFrame(%s));" % json.dumps((folder / name).as_posix())]
                names.append(name)
            if dressed:
                lines.append("converter.log(%s + characters.worn());"
                             % json.dumps("capture worn %s %s: " % (brief["id"], variant)))
            shots[brief["id"]][variant] = names
    lines.append('converter.log("capture done");')
    return "\n".join(lines) + "\n", shots


def stop_tree(process):
    """Stop a process and every process it started. On Windows "java" is often a launcher stub
    (Oracle's javapath) that runs the real JVM as its child, and stopping the stub alone left
    headless editors running and rendering."""
    if os.name == "nt":
        subprocess.run(["taskkill", "/T", "/F", "/PID", str(process.pid)],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    elif process.poll() is None:
        process.terminate()
    try:
        process.wait(20)
    except subprocess.TimeoutExpired:
        process.kill()


def run_editor(config, script, timeout):
    """Run the editor headless on a script until it logs "capture done", fails or times out,
    printing its progress. Only the editor started here is stopped. Returns True if it finished."""
    editor = pathlib.Path(config["editor"])
    core = sorted(editor.glob("Ikala-Core-*.jar"))
    if not core:
        raise SystemExit("No Ikala-Core jar in the editor folder " + str(editor))
    log = editor / "logs" / "latest.log"
    start = log.stat().st_size if log.exists() else 0
    size = config.get("captureSize", CAPTURE_SIZE)
    process = subprocess.Popen(
        [config.get("java", "java"), "-Dikala.headless=%dx%d" % (size, size),
         "-Dikala.test.script=" + str(script), "-jar", core[-1].name],
        cwd=editor, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    finished = False
    deadline = time.time() + timeout
    try:
        while time.time() < deadline and process.poll() is None:
            time.sleep(0.5)
            if not log.exists():
                continue
            if log.stat().st_size < start:  # a new log was started
                start = 0
            with open(log, "rb") as file:
                file.seek(start)
                data = file.read()
            start += len(data)
            text = data.decode("utf8", errors="replace")
            for line in text.splitlines():
                if "Script: capture " in line:
                    print(line.split("Script: ", 1)[1], flush=True)
                if "Script: capture done" in line:
                    finished = True
                elif any(failure in line for failure in CAPTURE_FAILURES):
                    print(line, flush=True)
            if finished or any(failure in text for failure in CAPTURE_FAILURES):
                break
    finally:
        stop_tree(process)
    if not finished and time.time() >= deadline:
        print("The editor didn't finish within %d s" % timeout)
    return finished


def command_capture(config, batch, args):
    if "editor" not in config:
        raise SystemExit("config.json has no \"editor\" folder to capture with; see %s"
                         % CONFIG_EXAMPLE)
    briefs = ready(batch.load(), args, lambda b: b["part"] is not None
                   and (args.redo or not b.get("captures")))
    if not briefs:
        return
    script, shots = capture_script(config, batch, briefs)
    path = (batch.folder / CAPTURE_SCRIPT).resolve()
    path.write_text(script, encoding="utf8")
    for brief in briefs:
        for names in shots[brief["id"]].values():
            for name in names:
                (batch.folder / brief["id"] / name).unlink(missing_ok=True)
    finished = run_editor(config, path, CAPTURE_START_SECONDS + CAPTURE_BRIEF_SECONDS * len(briefs))
    for brief in briefs:
        folder = batch.folder / brief["id"]
        saved = {variant: [n for n in names if (folder / n).exists()]
                 for variant, names in shots[brief["id"]].items()}
        saved = {variant: names for variant, names in saved.items() if names}

        def record(entry, saved=saved):
            entry["captures"] = saved or None
            # So the review page shows new shots, not ones it already loaded
            entry["capturedAt"] = datetime.datetime.now().isoformat(timespec="seconds")

        batch.update(brief["id"], record)
        print("%s: %d shots" % (brief["id"], sum(len(n) for n in saved.values())))
    if not finished:
        raise SystemExit("The capture run stopped early; the editor log is in %s"
                         % (pathlib.Path(config["editor"]) / "logs"))


def zoom_boxes(config, batch):
    """Where each clothing and body brief's images were painted, so the review page can show
    just that: the box around its mask, with REVIEW_MARGIN around it, as shares of the image
    (left, top, right, bottom). Accessories fill their images already."""
    from PIL import Image
    import numpy as np
    boxes = {}
    for brief in batch.load()["briefs"]:
        # Armor is pictured on its own, filling its images as accessories do
        if brief["kind"] not in ("layer", "body") or is_armor(brief):
            continue
        try:
            layer, rule = rule_of(brief)
            mask = (pathlib.Path(config["comfy"]) / "input"
                    / guide_inputs(config, layer, rule)["mask"])
        except SystemExit as error:
            print("No zoom for %s: %s" % (brief["id"], error))
            continue
        painted = np.asarray(Image.open(mask).convert("L")) > 127
        if not painted.any():
            continue
        rows, columns = np.flatnonzero(painted.any(1)), np.flatnonzero(painted.any(0))
        height, width = painted.shape
        top, bottom = rows[0] / height, (rows[-1] + 1) / height
        left, right = columns[0] / width, (columns[-1] + 1) / width
        # Square, so every brief's images are the same shape
        side = min(1.0, max(bottom - top, right - left) * (1 + 2 * REVIEW_MARGIN))
        middle_x, middle_y = (left + right) / 2, (top + bottom) / 2
        left = min(max(middle_x - side / 2, 0.0), 1.0 - side)
        top = min(max(middle_y - side / 2, 0.0), 1.0 - side)
        boxes[brief["id"]] = [round(left, 4), round(top, 4), round(left + side, 4),
                              round(top + side, 4)]
    return boxes


def command_review(config, batch, args):
    import review
    review.serve(batch, args.port, zoom_boxes(config, batch))


def main():
    parser = argparse.ArgumentParser(description="Make character parts in reviewed batches.")
    commands = parser.add_subparsers(dest="command", required=True)

    def stage(name, help):
        command = commands.add_parser(name, help=help)
        command.add_argument("batch")
        command.add_argument("--only", help="Comma-separated brief ids.")
        command.add_argument("--all", action="store_true", help="Don't wait for reviews.")
        return command

    new = commands.add_parser("new", help="Start a batch or add briefs to it.")
    new.add_argument("batch")
    new.add_argument("briefs", help="A JSON list of briefs.")
    new.add_argument("--keep", action="store_true", help="Keep the briefs without reviewing.")
    review = commands.add_parser("review", help="The review page.")
    review.add_argument("batch")
    review.add_argument("--port", type=int, default=8765)
    images = stage("images", "Concept images for kept briefs.")
    images.add_argument("--count", type=int, default=4)
    images.add_argument("--more", action="store_true", help="Add images to briefs that have some.")
    meshes = stage("meshes", "Meshes from picked images.")
    meshes.add_argument("--type", default="512", choices=["512", "1024", "1024_cascade"])
    meshes.add_argument("--redo", action="store_true",
                        help="Make meshes again for briefs that have one from their pick.")
    fit = stage("fit", "Fit kept meshes onto the biped.")
    fit.add_argument("--redo", action="store_true", help="Fit again briefs that have a part.")
    fit.add_argument("--forms", action="store_true",
                     help="Only make the clothing forms' meshes of layers that have a part.")
    fit.add_argument("--standoff", action="store_true",
                     help="Only measure how far body parts that have a part stand out over each"
                          " slot.")
    capture = stage("capture", "Shots of fitted parts in the editor, on each mannequin.")
    capture.add_argument("--redo", action="store_true", help="Capture briefs that have shots again.")
    status = commands.add_parser("status", help="What each brief has.")
    status.add_argument("batch")
    args = parser.parse_args()

    config = load_config()
    batch = Batch(config, args.batch)
    {"new": command_new, "review": command_review, "images": command_images,
     "meshes": command_meshes, "fit": command_fit, "capture": command_capture,
     "status": command_status}[args.command](
        config, batch, args)


if __name__ == "__main__":
    sys.exit(main())
