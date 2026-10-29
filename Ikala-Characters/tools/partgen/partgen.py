"""Make character parts from a written brief: concept images, a generated mesh, and a part fitted
to the biped that the character editor loads.

Each step is its own command, so the choices between them stay with a person:

    python partgen.py images <brief.json> [--count 4] [--seed 1000]
        Concept images from SDXL through ComfyUI, and a contact sheet of them.
    python partgen.py mesh <brief.json> --pick <seed> [--type 512]
        A textured mesh from the picked image, through TRELLIS.2, with renders.
    python partgen.py fit <brief.json>
        The mesh fitted onto the mannequin as a part: a glb and a part definition in the
        content folder, and preview renders.

Paths come from config.json beside this file. Work files go in <work>/<brief id>/; finished
parts go in the content folder, which the editor loads as the config's namespace. A brief is
JSON:

    {"id": "adventurer_hat", "kind": "accessory", "socket": "head_top",
     "description": "wide-brimmed weathered brown leather adventurer hat with a brass-buckled band",
     "tags": ["hat"]}

Only accessories on head_top sockets can be fitted so far. Run it with any Python that has
Pillow, such as TRELLIS.2's.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image

TOOLS = pathlib.Path(__file__).resolve().parent
PLAN = TOOLS.parent.parent / "src/main/resources/characters/ikala/bodyPlan/biped.json"
PLAN_ID = "ikala:biped"
TEMPLATE = "ikala:mat/standard"

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
SHEET_THUMB = 256


def load_config():
    return json.loads((TOOLS / "config.json").read_text(encoding="utf8"))


def load_json_with_comments(path):
    """Read JSON whose lines may start with // comments, like the definitions."""
    text = path.read_text(encoding="utf8")
    return json.loads(re.sub(r"(?m)^\s*//.*$", "", text))


def load_brief(path):
    brief = json.loads(pathlib.Path(path).read_text(encoding="utf8"))
    if not re.fullmatch(r"[a-z0-9_]+", brief["id"]):
        raise SystemExit("A brief's id is lowercase letters, digits and underscores")
    return brief


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


def work_dir(config, brief):
    folder = pathlib.Path(config["work"]) / brief["id"]
    folder.mkdir(parents=True, exist_ok=True)
    return folder


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


def contact_sheet(paths, out, labels):
    """Thumbnails side by side with their labels, for picking."""
    from PIL import ImageDraw
    sheet = Image.new("RGB", (SHEET_THUMB * len(paths), SHEET_THUMB + 20), "white")
    draw = ImageDraw.Draw(sheet)
    for i, (path, label) in enumerate(zip(paths, labels)):
        image = Image.open(path).convert("RGB")
        image.thumbnail((SHEET_THUMB, SHEET_THUMB))
        sheet.paste(image, (i * SHEET_THUMB, 20))
        draw.text((i * SHEET_THUMB + 4, 4), label, fill="black")
    sheet.save(out)
    print("contact sheet", out)


def stitch(paths, out):
    images = [Image.open(p).convert("RGB") for p in paths]
    sheet = Image.new("RGB", (sum(i.width for i in images), max(i.height for i in images)))
    x = 0
    for image in images:
        sheet.paste(image, (x, 0))
        x += image.width
    sheet.save(out)
    print("renders", out)


def command_images(config, brief, args):
    socket, rule = socket_of(brief)
    subject = rule["prompt"].format(description=brief["description"])
    prompt = STYLE.format(subject=subject)
    folder = work_dir(config, brief) / "images"
    folder.mkdir(exist_ok=True)
    seeds = list(range(args.seed, args.seed + args.count))
    paths = []
    with Comfy(config) as comfy:
        for seed in seeds:
            workflow = sdxl_workflow(config["checkpoint"], prompt, NEGATIVE, seed,
                                     "partgen/" + brief["id"])
            path = folder / ("%d.png" % seed)
            path.write_bytes(comfy.run(workflow)[0])
            paths.append(path)
            print("image", path)
    (folder / "prompt.json").write_text(json.dumps(
        {"prompt": prompt, "negative": NEGATIVE, "steps": SDXL_STEPS, "cfg": SDXL_CFG,
         "checkpoint": config["checkpoint"]}, indent=2), encoding="utf8")
    contact_sheet(paths, work_dir(config, brief) / "images.png", [str(s) for s in seeds])


def command_mesh(config, brief, args):
    folder = work_dir(config, brief)
    image = folder / "images" / ("%d.png" % args.pick)
    if not image.exists():
        raise SystemExit("No image " + str(image))
    out = folder / ("mesh_%d_%s.glb" % (args.pick, args.type))
    trellis = pathlib.Path(config["trellis"])
    subprocess.run([str(trellis / ".venv/Scripts/python.exe"), str(TOOLS / "trellis_run.py"),
                    "--trellis", str(trellis), "--type", args.type, str(image), str(out)],
                   check=True)
    (folder / "picked.json").write_text(json.dumps(
        {"image": image.name, "mesh": out.name, "type": args.type}, indent=2), encoding="utf8")


def command_fit(config, brief, args):
    folder = work_dir(config, brief)
    picked = json.loads((folder / "picked.json").read_text(encoding="utf8"))
    socket, rule = socket_of(brief)
    content = pathlib.Path(config["content"])
    relative = "accessory/%s.glb" % brief["id"]
    out = content / relative
    out.parent.mkdir(parents=True, exist_ok=True)
    renders = folder / "fit"
    renders.mkdir(exist_ok=True)
    subprocess.run([config["blender"], "-b", "--factory-startup", "--python",
                    str(TOOLS / "blender/fit_accessory.py"), "--",
                    "--mesh", str(folder / picked["mesh"]), "--mannequin", config["mannequin"],
                    "--bone", socket["bone"], "--rule", json.dumps(rule), "--name", brief["id"],
                    "--out", str(out), "--preview", str(renders)], check=True)
    stitch([renders / ("preview_%d.png" % i) for i in range(3)], folder / "fit.png")

    namespace = config["namespace"]
    part = {
        "id": "%s:part/%s/%s" % (namespace, socket["type"].replace(".", "_"), brief["id"]),
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
    header = ("// %s, from %s\n// Written by Ikala-Characters/tools/partgen/partgen.py\n"
              % (brief["description"], picked["image"]))
    definition.write_text(header + json.dumps([part], indent=2) + "\n", encoding="utf8")
    print("part", definition)


def main():
    parser = argparse.ArgumentParser(description="Make character parts from a brief.")
    commands = parser.add_subparsers(dest="command", required=True)
    images = commands.add_parser("images", help="Concept images for a brief.")
    images.add_argument("brief")
    images.add_argument("--count", type=int, default=4)
    images.add_argument("--seed", type=int, default=1000)
    mesh = commands.add_parser("mesh", help="A mesh from a picked image.")
    mesh.add_argument("brief")
    mesh.add_argument("--pick", type=int, required=True, help="The picked image's seed.")
    mesh.add_argument("--type", default="512", choices=["512", "1024", "1024_cascade"])
    fit = commands.add_parser("fit", help="Fit the mesh onto the biped as a part.")
    fit.add_argument("brief")
    args = parser.parse_args()

    config = load_config()
    brief = load_brief(args.brief)
    {"images": command_images, "mesh": command_mesh, "fit": command_fit}[args.command](
        config, brief, args)


if __name__ == "__main__":
    sys.exit(main())
