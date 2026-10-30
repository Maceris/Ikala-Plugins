"""Make images from a prompt with the local SDXL through ComfyUI, outside any partgen batch.

    python make_image.py "a red wooden treasure chest with iron bands" [--count 4] [--out <folder>]

Each image is saved as <seed>.png in the folder (default <work>/scratch/images, from
config.json), with prompt.json beside them. ComfyUI is started if it isn't running, offline
(its web page can't reach the internet and its paid API nodes are off), and stopped again after.
Options:

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
import types

import partgen


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("prompt")
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

    config = partgen.load_config()
    out = pathlib.Path(args.out or pathlib.Path(config["work"]) / "scratch" / "images")
    out.mkdir(parents=True, exist_ok=True)
    prompt = partgen.STYLE.format(subject=args.prompt) if args.style else args.prompt
    sketch = None
    if args.sketch:
        # The batch code names the prepared sketch after a batch and brief
        sketch = partgen.prepare_sketch(config, types.SimpleNamespace(name="scratch"),
                                        {"id": "make_image", "sketch": args.sketch})
    with partgen.Comfy(config) as comfy:
        for seed in range(args.seed, args.seed + args.count):
            workflow = partgen.sdxl_workflow(config["checkpoint"], prompt, args.negative, seed,
                                             "make_image")
            workflow["5"]["inputs"].update(steps=args.steps, cfg=args.cfg)
            if sketch:
                workflow = partgen.control_workflow(workflow, config, sketch,
                                                    partgen.SKETCH_MODE, args.sketch_strength,
                                                    args.sketch_end)
            path = out / ("%d.png" % seed)
            path.write_bytes(comfy.run(workflow)[0])
            print("wrote", path)
    (out / "prompt.json").write_text(json.dumps(
        {"prompt": prompt, "negative": args.negative, "steps": args.steps, "cfg": args.cfg,
         "checkpoint": config["checkpoint"], "sketch": args.sketch,
         "sketchStrength": args.sketch_strength if sketch else None,
         "sketchEnd": args.sketch_end if sketch else None}, indent=2), encoding="utf8")


if __name__ == "__main__":
    main()
