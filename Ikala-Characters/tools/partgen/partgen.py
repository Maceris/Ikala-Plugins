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

"fit" is optional: values that replace the socket type's rule from sockets.json for this brief,
like a wider brim limit for a sun hat or a scale nudge after review. "sketch" is an optional
drawing, relative to the briefs file, that steers the shape where words don't: lines on a plain
background, any color; "sketchStrength" (default 0.7) is how hard.

Only accessories on head_top sockets can be fitted so far. Run it with any Python that has
Pillow, such as TRELLIS.2's.
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
MANIFEST = "manifest.json"

# Every concept image starts with this, so they come out alike and easy to cut out. "Game asset"
# makes SDXL draw sheets of many objects, so this asks for one isolated product render instead.
STYLE = ("isolated 3D product render of {subject}, one object only, plain white background, "
         "soft even studio light, clean readable shapes, distinct flat material colors, PBR")
NEGATIVE = ("sprite sheet, collection, set, grid, multiple views, several objects, person, face, "
            "mannequin, hands, cropped, cut off, text, watermark, logo, frame, border, busy "
            "background, scenery, floor, shadow, blurry, lowres, deformed")

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
REVIEW_FIELDS = ("brief", "pick", "mesh", "part")


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
    """A ComfyUI server for the length of a command, started if one isn't running already."""

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
             "--disable-auto-launch"],
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


def sketch_workflow(workflow, config, image, strength, end):
    """Steer a text to image workflow with a sketch, through ControlNet Union's scribble mode."""
    workflow.update({
        "8": {"class_type": "LoadImage", "inputs": {"image": image}},
        "9": {"class_type": "ControlNetLoader",
              "inputs": {"control_net_name": config["controlnet"]}},
        "10": {"class_type": "SetUnionControlNetType",
               "inputs": {"control_net": ["9", 0], "type": "hed/pidi/scribble/ted"}},
        "11": {"class_type": "ControlNetApplyAdvanced",
               "inputs": {"positive": ["2", 0], "negative": ["3", 0], "control_net": ["10", 0],
                          "image": ["8", 0], "strength": strength, "start_percent": 0.0,
                          "end_percent": end, "vae": ["1", 2]}},
    })
    workflow["5"]["inputs"]["positive"] = ["11", 0]
    workflow["5"]["inputs"]["negative"] = ["11", 1]
    return workflow


def prepare_sketch(config, batch, brief):
    """A brief's sketch as ComfyUI wants it, in its input folder: white lines on black, square,
    the generated size, lines thickened a little. Gives its name there."""
    from PIL import Image, ImageFilter, ImageOps, ImageStat
    sketch = Image.open(brief["sketch"]).convert("L")
    if ImageStat.Stat(sketch).mean[0] > 127:
        sketch = ImageOps.invert(sketch)  # drawn dark on light
    sketch.thumbnail((SDXL_SIZE, SDXL_SIZE))
    square = Image.new("L", (SDXL_SIZE, SDXL_SIZE), 0)
    square.paste(sketch, ((SDXL_SIZE - sketch.width) // 2, (SDXL_SIZE - sketch.height) // 2))
    square = square.filter(ImageFilter.MaxFilter(5))
    name = "partgen_%s_%s.png" % (batch.name, brief["id"])
    square.convert("RGB").save(pathlib.Path(config["comfy"]) / "input" / name)
    return name


def prompt_for(brief):
    socket, rule = socket_of(brief)
    return STYLE.format(subject=rule["prompt"].format(description=brief["description"]))


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
        socket_of(brief)
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
            folder = batch.brief_folder(brief["id"]) / "images"
            folder.mkdir(exist_ok=True)
            start = max(brief["images"], default=FIRST_SEED - 1) + 1
            seeds = list(range(start, start + args.count))
            sketch = prepare_sketch(config, batch, brief) if brief.get("sketch") else None
            strength = brief.get("sketchStrength", SKETCH_STRENGTH)
            for seed in seeds:
                workflow = sdxl_workflow(config["checkpoint"], prompt, NEGATIVE, seed,
                                         "partgen/" + brief["id"])
                if sketch:
                    workflow = sketch_workflow(workflow, config, sketch, strength, SKETCH_END)
                (folder / ("%d.png" % seed)).write_bytes(comfy.run(workflow)[0])
            (folder / "prompt.json").write_text(json.dumps(
                {"prompt": prompt, "negative": NEGATIVE, "steps": SDXL_STEPS, "cfg": SDXL_CFG,
                 "checkpoint": config["checkpoint"], "sketch": brief.get("sketch"),
                 "sketchStrength": strength if sketch else None}, indent=2), encoding="utf8")
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


def command_fit(config, batch, args):
    briefs = ready(batch.load(), args, lambda b: b["mesh"] is not None and kept(b, "mesh", args)
                   and (args.redo or b["part"] is None))
    for brief in briefs:
        if brief["kind"] != "accessory":
            print("Only accessories can be fitted so far; skipped", brief["id"])
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
        with open(log, "w", encoding="utf8") as stream:
            result = subprocess.run(
                [config["blender"], "-b", "--factory-startup", "--python",
                 str(TOOLS / "blender/fit_accessory.py"), "--",
                 "--mesh", str(folder / brief["mesh"]["file"]), "--mannequin", config["mannequin"],
                 "--bone", socket["bone"], "--rule", json.dumps(rule), "--name", brief["id"],
                 "--out", str(out), "--preview", str(renders)],
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
            "materialSlots": [{"name": brief["id"], "template": TEMPLATE}],
            "tags": brief.get("tags", []),
        }
        definition = content / "part" / ("%s.json" % brief["id"])
        definition.parent.mkdir(parents=True, exist_ok=True)
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
