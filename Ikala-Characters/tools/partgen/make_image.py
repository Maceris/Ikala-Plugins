"""Make images from a prompt with the local SDXL through ComfyUI, outside any partgen batch.

    python make_image.py "a red wooden treasure chest with iron bands" [--count 4] [--out <folder>]
    python make_image.py --more <folder> [--count 4]

Each image is saved as <seed>.png in the folder (default <work>/scratch/images, from
config.json), with prompt.json beside them. ComfyUI is started if it isn't running, offline
(its web page can't reach the internet and its paid API nodes are off), and stopped again after.
Images already in the folder are never overwritten. Options:

    --more <folder>     more images like a folder's earlier ones: the same prompt, negative,
                        steps, guidance and sketch from its prompt.json, carrying on from the
                        seed after its highest image
    --negative <text>   what to keep out (default: partgen's negative prompt for objects)
    --style             wrap the prompt in partgen's isolated product render style, which makes
                        images TRELLIS.2 turns into meshes well
    --seed <n>          the first seed (default 2000); each image takes the next
    --count <n>         how many images (default 1)
    --steps, --cfg      sampling steps (default 30) and guidance (default 6.0)
    --sketch <image>    a drawing to steer the shape: dark lines on a light background
    --sketch-strength, --sketch-end
                        how hard the sketch steers (default 0.7) and for what share of the
                        steps (default 0.7)

Run it with any Python that has Pillow, such as TRELLIS.2's. To turn an image into a mesh:

    <trellis>/.venv/Scripts/python.exe trellis_run.py --trellis <trellis> <image.png> <out.glb>
"""
import argparse
import json
import pathlib
import re
import types

import partgen

# The settings file written beside the images, which --more reads back
SETTINGS = "prompt.json"


def seeds_in(folder):
    """The seeds of the images already in a folder."""
    return [int(p.stem) for p in folder.glob("*.png") if re.fullmatch(r"\d+", p.stem)]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("prompt", nargs="?")
    parser.add_argument("--more", metavar="FOLDER")
    parser.add_argument("--negative", default=partgen.NEGATIVE)
    parser.add_argument("--style", action="store_true")
    parser.add_argument("--seed", type=int, default=partgen.FIRST_SEED)
    parser.add_argument("--count", type=int, default=1)
    parser.add_argument("--steps", type=int, default=partgen.SDXL_STEPS)
    parser.add_argument("--cfg", type=float, default=partgen.SDXL_CFG)
    parser.add_argument("--sketch")
    parser.add_argument("--sketch-strength", type=float, default=partgen.SKETCH_STRENGTH)
    parser.add_argument("--sketch-end", type=float, default=partgen.SKETCH_END)
    parser.add_argument("--out")
    args = parser.parse_args()
    if (args.prompt is None) == (args.more is None):
        parser.error("give a prompt, or --more <folder> to carry on from earlier images")

    config = partgen.load_config()
    if args.more:
        out = pathlib.Path(args.more)
        if not (out / SETTINGS).exists():
            parser.error("%s has no %s to carry on from" % (out, SETTINGS))
        settings = json.loads((out / SETTINGS).read_text(encoding="utf8"))
        if settings.get("checkpoint") not in (None, config["checkpoint"]):
            print("Note: these were made with %s; config.json now names %s" % (
                settings["checkpoint"], config["checkpoint"]))
        # The saved prompt already has any style wrapped in
        prompt, negative = settings["prompt"], settings["negative"]
        steps, cfg = settings["steps"], settings["cfg"]
        sketch_file = settings.get("sketch")
        strength = settings.get("sketchStrength") or partgen.SKETCH_STRENGTH
        end = settings.get("sketchEnd") or partgen.SKETCH_END
        first = max(seeds_in(out), default=partgen.FIRST_SEED - 1) + 1
    else:
        out = pathlib.Path(args.out or pathlib.Path(config["work"]) / "scratch" / "images")
        prompt = partgen.STYLE.format(subject=args.prompt) if args.style else args.prompt
        negative, steps, cfg = args.negative, args.steps, args.cfg
        sketch_file = str(pathlib.Path(args.sketch).resolve()) if args.sketch else None
        strength, end = args.sketch_strength, args.sketch_end
        first = args.seed
        earlier = out / SETTINGS
        if earlier.exists() and seeds_in(out):
            if json.loads(earlier.read_text(encoding="utf8")).get("prompt") != prompt:
                parser.error("%s holds images from another prompt; use another --out" % out)
    seeds = list(range(first, first + args.count))
    taken = sorted(set(seeds) & set(seeds_in(out)) if out.exists() else [])
    if taken:
        parser.error("%s already has images for seeds %s; use --more %s to add to it, or "
                     "another --out or --seed" % (out, taken, out))
    out.mkdir(parents=True, exist_ok=True)

    sketch = None
    if sketch_file:
        # The batch code names the prepared sketch after a batch and brief
        sketch = partgen.prepare_sketch(config, types.SimpleNamespace(name="scratch"),
                                        {"id": "make_image", "sketch": sketch_file})
    with partgen.Comfy(config) as comfy:
        for seed in seeds:
            workflow = partgen.sdxl_workflow(config["checkpoint"], prompt, negative, seed,
                                             "make_image")
            workflow["5"]["inputs"].update(steps=steps, cfg=cfg)
            if sketch:
                workflow = partgen.control_workflow(workflow, config, sketch,
                                                    partgen.SKETCH_MODE, strength, end)
            path = out / ("%d.png" % seed)
            path.write_bytes(comfy.run(workflow)[0])
            print("wrote", path)
    (out / SETTINGS).write_text(json.dumps(
        {"prompt": prompt, "negative": negative, "steps": steps, "cfg": cfg,
         "checkpoint": config["checkpoint"], "sketch": sketch_file,
         "sketchStrength": strength if sketch else None,
         "sketchEnd": end if sketch else None}, indent=2), encoding="utf8")


if __name__ == "__main__":
    main()
