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
    python partgen.py fit <batch>
        Kept meshes fitted onto the mannequin: glbs and part definitions in the content folder.
    python partgen.py status <batch>

Every stage takes --only <id,...> to limit it, and --all to skip waiting for reviews. Paths come
from config.json beside this file; batches live in <work>/<batch>/. A brief is:

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

Accessories can be fitted on head_top sockets so far. Clothing is lined up on the mannequin, cut
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
               "an A pose, arms out and down, seen from the front, {subject}, plain white "
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
# Body slots a layer's mask never grows into unless the layer covers them
BARE_SLOTS = ("head", "hand.L", "hand.R", "foot.L", "foot.R")

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
GARMENT_SURE = 60
# Gaps up to this wide in the painted area are closed for the garment's back, in pixels (odd)
BACK_CLOSE = 61


def load_config():
    return json.loads((TOOLS / "config.json").read_text(encoding="utf8"))


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
    return socket, rules[socket["type"]]


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
    for key in ("maskGrow", "maskDown", "gap"):
        rule[key] = max(r.get(key, 0) for _, r in found)
    rule["triangles"] = sum(r["triangles"] for _, r in found)
    return outfit, dict(rule, **brief.get("fit", {}))


def rule_of(brief):
    """Where the brief's part goes, and its rule, by its kind."""
    if brief["kind"] == "accessory":
        return socket_of(brief)
    if brief["kind"] == "layer":
        return layer_of(brief)
    raise SystemExit("Briefs are accessories or layers, not " + brief["kind"])


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


def guides(config):
    """The folder of guides rendered from the mannequin, rendered the first time."""
    folder = pathlib.Path(config["work"]) / "guides" / GUIDE_VARIANT
    source = folder / "guide.json"
    if not source.exists():
        body = pathlib.Path(config["mannequin"]).with_suffix(".blend")
        result = subprocess.run(
            [config["blender"], "-b", "--factory-startup", "--python",
             str(TOOLS / "blender" / "render_guides.py"), "--", "--body", str(body),
             "--out", str(folder), "--size", str(SDXL_SIZE)],
            capture_output=True, text=True)
        if result.returncode != 0 or not source.exists():
            raise SystemExit("Rendering the guides failed:\n" + result.stdout[-2000:]
                             + result.stderr[-2000:])
    return folder


def guide_inputs(config, layer, rule):
    """The images that paint a layer's clothing, in ComfyUI's input folder: the mannequin's
    render, its depth, and a mask of where the layer may paint. Gives their names there."""
    from PIL import Image, ImageChops, ImageFilter
    folder = guides(config)
    guide = json.loads((folder / "guide.json").read_text(encoding="utf8"))
    inputs = pathlib.Path(config["comfy"]) / "input"
    names = {}
    for kind in ("gray", GUIDE_MAP):
        names[kind] = "partgen_guide_%s_%s.png" % (GUIDE_VARIANT, kind)
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
    # they would differ from the render and be taken for clothing
    kept_bare = {guide["slots"].index(slot) + 1 for slot in BARE_SLOTS
                 if slot in guide["slots"] and slot not in layer["covers"]}
    bare = slots.point(lambda v: 255 if v in kept_bare else 0)
    hang = ImageChops.subtract(hang, bare)
    names["mask"] = "partgen_mask_%s_%s.png" % (GUIDE_VARIANT, layer["id"])
    hang.convert("RGB").save(inputs / names["mask"])
    return names


def prompt_for(brief):
    where, rule = rule_of(brief)
    subject = rule["prompt"].format(description=brief["description"])
    return (LAYER_STYLE if brief["kind"] == "layer" else STYLE).format(subject=subject)


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
            negative = LAYER_NEGATIVE if brief["kind"] == "layer" else NEGATIVE
            folder = batch.brief_folder(brief["id"]) / "images"
            folder.mkdir(exist_ok=True)
            start = max(brief["images"], default=FIRST_SEED - 1) + 1
            seeds = list(range(start, start + args.count))
            # Clothing follows the mannequin's guide; anything may follow a sketch instead
            control, mode, strength, end, painted = None, None, None, None, None
            if brief["kind"] == "layer":
                layer, rule = layer_of(brief)
                painted = guide_inputs(config, layer, rule)
                control, mode = painted[GUIDE_MAP], GUIDE_MODE
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
        return b["review"]["pick"] is not None and (
            b["mesh"] is None or b["mesh"]["image"] != b["review"]["pick"]
            or b["mesh"]["type"] != args.type)

    briefs = ready(batch.load(), args, wanted)
    if not briefs:
        return
    pairs, renders = [], []
    for brief in briefs:
        folder = batch.brief_folder(brief["id"])
        pick = brief["review"]["pick"]
        glb = folder / ("mesh_%d_%s.glb" % (pick, args.type))
        pairs += [str(folder / "images" / ("%d.png" % pick)), str(glb)]
        renders += [str(glb), str(folder / ("views_%d_%s" % (pick, args.type)))]
    trellis = pathlib.Path(config["trellis"])
    subprocess.run([str(trellis / ".venv/Scripts/python.exe"), str(TOOLS / "trellis_run.py"),
                    "--trellis", str(trellis), "--type", args.type] + pairs, check=True)
    subprocess.run([config["blender"], "-b", "--factory-startup", "--python",
                    str(TOOLS / "blender/render_meshes.py"), "--"] + renders, check=True,
                   stdout=subprocess.DEVNULL)
    for brief in briefs:
        pick = brief["review"]["pick"]

        def record(entry, pick=pick):
            entry["mesh"] = {"image": pick, "type": args.type,
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
    layer, rule = layer_of(brief)
    allowed = pathlib.Path(config["comfy"]) / "input" / guide_inputs(config, layer, rule)["mask"]
    gray = np.asarray(Image.open(guides(config) / "gray.png").convert("RGB"), dtype=float)
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
    differs &= ~bare
    differs &= np.asarray(Image.open(allowed).convert("L")) > 127
    mask = Image.fromarray(differs.astype(np.uint8) * 255).filter(ImageFilter.MedianFilter(5))
    mask = mask.filter(ImageFilter.MaxFilter(PAINT_CLOSE))
    mask = mask.filter(ImageFilter.MinFilter(PAINT_CLOSE))
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
    path = out / "painted.png"
    Image.fromarray(painted.astype(np.uint8) * 255).save(path)
    # A garment's back is whole where its front is open, like a jacket's neckline: gaps are
    # closed further for what faces away from the camera
    back = Image.open(path).filter(ImageFilter.MaxFilter(BACK_CLOSE))
    back.filter(ImageFilter.MinFilter(BACK_CLOSE)).save(out / "painted_back.png")
    return path


def run_blender(config, script, log, *arguments):
    """Run one of the blender/ scripts headless, its output in a log. Gives whether it worked."""
    with open(log, "w", encoding="utf8") as stream:
        result = subprocess.run(
            [config["blender"], "-b", "--factory-startup", "--python",
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
            [config["blender"], "-b", "--factory-startup", "--python",
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
            [config["blender"], "-b", "--factory-startup", "--python",
             str(TOOLS / "blender/shape_layer.py"), "--",
             "--garment", str(out / "garment.glb"), "--body", str(body), "--out", str(out),
             "--gap", str(rule["gap"]), "--triangles", str(rule["triangles"])],
            stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode != 0 or not (out / "shape.json").exists():
        print("Shaping failed for %s; see %s" % (brief["id"], folder / "shape.log"))
        return
    report["shape"] = json.loads((out / "shape.json").read_text(encoding="utf8"))
    weighted = run_blender(config, "weight_layer.py", folder / "weights.log",
                           "--garment", out / "shaped.glb", "--body", body, "--out", out)
    if not weighted or not (out / "weights.json").exists():
        print("Weighting failed for %s; see %s" % (brief["id"], folder / "weights.log"))
        return
    report["poses"] = json.loads((out / "weights.json").read_text(encoding="utf8"))["poses"]
    image = folder / "images" / ("%d.png" % brief["review"]["pick"])
    baked = run_blender(config, "bake_layer.py", folder / "bake.log",
                        "--weighted", out / "weighted.blend", "--figure", out / "pushed.glb",
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


def write_layer_part(config, batch, brief, out, rule):
    """Put a baked garment in the content folder as a layer part: the male mesh, a female one
    carried over through the body cages, each with its tint mask beside it (found by the file's
    name), and the part's definition. Other body types wear the male's. Gives the part's ID, or
    None if the female mesh failed."""
    import shutil
    folder = batch.brief_folder(brief["id"])
    content = pathlib.Path(config["content"])
    mannequins = pathlib.Path(config["mannequin"]).parent
    name = brief["id"]
    meshes = {}
    for variant, glb in ((GUIDE_VARIANT, "layer/%s.glb" % name),
                         ("female", "layer/%s_female.glb" % name)):
        target = content / glb
        target.parent.mkdir(parents=True, exist_ok=True)
        if variant == GUIDE_VARIANT:
            shutil.copyfile(out / "baked.glb", target)
        elif not run_blender(config, "variant_layer.py", folder / "variant.log",
                             "--baked", out / "baked.glb", "--body", mannequins / "female.blend",
                             "--from-cage", mannequins / ("%s_cage.npz" % GUIDE_VARIANT),
                             "--to-cage", mannequins / "female_cage.npz", "--gap", rule["gap"],
                             "--name", name, "--out", target, "--preview", out):
            print("The female mesh failed for %s; see %s" % (name, folder / "variant.log"))
            return None
        masks = target.with_name(target.stem + "_masks")
        shutil.rmtree(masks, ignore_errors=True)
        shutil.copytree(out / (name + "_masks"), masks)
        meshes["*" if variant == GUIDE_VARIANT else variant] = "%s#%s" % (glb, name)
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
    definition = content / "part" / ("%s.json" % name)
    definition.parent.mkdir(parents=True, exist_ok=True)
    header = ("// %s, batch %s, image %d\n"
              "// Written by Ikala-Characters/tools/partgen/partgen.py\n"
              % (brief["description"], batch.name, brief["mesh"]["image"]))
    definition.write_text(header + json.dumps([part], indent=2) + "\n", encoding="utf8")
    return part_id


def command_fit(config, batch, args):
    briefs = ready(batch.load(), args, lambda b: b["mesh"] is not None and kept(b, "mesh", args)
                   and (args.redo or b["part"] is None))
    for brief in briefs:
        if brief["kind"] == "layer":
            fit_layer(config, batch, brief)
            continue
        folder = batch.brief_folder(brief["id"])
        socket, rule = socket_of(brief)
        rule = dict(rule, **brief.get("fit", {}))
        content = pathlib.Path(config["content"])
        relative = "accessory/%s.glb" % brief["id"]
        out = content / relative
        out.parent.mkdir(parents=True, exist_ok=True)
        renders = folder / "fit"
        renders.mkdir(exist_ok=True)
        log = folder / "fit.log"
        tints = folder / "tints.json"
        with open(log, "w", encoding="utf8") as stream:
            result = subprocess.run(
                [config["blender"], "-b", "--factory-startup", "--python",
                 str(TOOLS / "blender/fit_accessory.py"), "--",
                 "--mesh", str(folder / brief["mesh"]["file"]), "--mannequin", config["mannequin"],
                 "--bone", socket["bone"], "--rule", json.dumps(rule), "--name", brief["id"],
                 "--out", str(out), "--preview", str(renders), "--tints", str(tints)],
                stdout=stream, stderr=subprocess.STDOUT)
        if result.returncode != 0:
            print("fit failed for %s; see %s" % (brief["id"], log))
            continue

        part_id = "%s:part/%s/%s" % (config["namespace"], socket["type"].replace(".", "_"),
                                     brief["id"])
        part = {
            "id": part_id,
            "plan": PLAN_ID,
            "kind": "accessory",
            "fits": [socket["id"]],
            "rigid": True,
            "meshes": {"*": "%s#%s" % (relative, brief["id"])},
            "materialSlots": [material_slot(brief["id"], tints)],
            "tags": brief.get("tags", []),
        }
        definition = content / "part" / ("%s.json" % brief["id"])
        definition.parent.mkdir(parents=True, exist_ok=True)
        # A placement lined up by hand in the character editor outlives a refit
        placement = saved_placement(definition)
        if placement:
            part["placement"] = placement
        header = ("// %s, batch %s, image %d\n"
                  "// Written by Ikala-Characters/tools/partgen/partgen.py\n"
                  % (brief["description"], batch.name, brief["mesh"]["image"]))
        definition.write_text(header + json.dumps([part], indent=2) + "\n", encoding="utf8")

        def record(entry, part_id=part_id):
            entry["part"] = part_id
            entry["review"]["part"] = None

        batch.update(brief["id"], record)
        print("part", part_id)


def command_status(config, batch, args):
    manifest = batch.load()
    print("%-28s %-6s %-7s %-6s %-6s %-6s" % ("brief", "brief", "images", "pick", "mesh", "part"))
    for b in manifest["briefs"]:
        review = b["review"]
        mesh = review["mesh"] or ("made" if b["mesh"] else "-")
        part = review["part"] or ("made" if b["part"] else "-")
        print("%-28s %-6s %-7d %-6s %-6s %-6s" % (b["id"], review["brief"] or "-", len(b["images"]),
                                                  review["pick"] or "-", mesh, part))


def command_review(config, batch, args):
    import review
    review.serve(batch, args.port)


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
    fit = stage("fit", "Fit kept meshes onto the biped.")
    fit.add_argument("--redo", action="store_true", help="Fit again briefs that have a part.")
    status = commands.add_parser("status", help="What each brief has.")
    status.add_argument("batch")
    args = parser.parse_args()

    config = load_config()
    batch = Batch(config, args.batch)
    {"new": command_new, "review": command_review, "images": command_images,
     "meshes": command_meshes, "fit": command_fit, "status": command_status}[args.command](
        config, batch, args)


if __name__ == "__main__":
    sys.exit(main())
