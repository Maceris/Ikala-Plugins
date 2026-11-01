"""Turn images into textured meshes with TRELLIS.2, loading the models once for all of them.

Run it with TRELLIS.2's own Python, which partgen.py does:

    <trellis>/.venv/Scripts/python.exe trellis_run.py --trellis <trellis folder>
        [--type 512] [--seed 0] [--faces 200000] [--texture 2048] [--online]
        [--figure <image> ...] [--tries 4] <image> <out.glb> [<image> <out.glb>...]

It runs offline: the models load from Hugging Face's local cache without asking Hugging Face for
newer versions, which by default it would on every run, sending any stored login token. Pass
--online to download models the cache doesn't have yet, or to update them.

Images with transparency skip background removal. Others go through BiRefNet with the MIT
ZhengPeng7 weights, not the RMBG-2.0 weights TRELLIS.2's config names (non-commercial, gated).
Each glb comes out in TRELLIS.2's space: a 1 m box around the origin, +Y up, facing +Z, with
<out>.json beside it giving the seed it was made with.

Some seeds hang curtains of long thin streaks off a figure, from its arms and body or from the
top of TRELLIS.2's volume, and some pose a limb their own way, like an arm hung straight down.
On a figure (images named with --figure, like partgen's clothing and body parts, painted on the
mannequin) both show in its front outline against the image's: curtains add outline the image
doesn't have, filling in the space under the arms and between the legs, and a limb posed
elsewhere leaves the two barely overlapping. Such a mesh is made again with the next seed, up to
--tries times, and the closest to the image kept. Objects on their own, like hats, aren't
checked.
"""
import argparse
import json
import os

import numpy as np
from PIL import Image, ImageFilter
import sys
import time


# The size of the grid outlines are compared on
GRID = 128
# How much outline a figure's mesh may have beyond its image's, as a share of the image's, before
# it counts as curtains: clean figures measure about 0.5 (the two outlines are scaled a little
# differently), curtained ones 1 or more
CURTAINS = 0.8
# How much a figure mesh's front outline must overlap its image's, as a share of both
# together: good figures measure 0.23 to 0.53, an arm TRELLIS hung straight down 0.05
MIN_OVERLAP = 0.15
# Seeds tried at most for a figure with curtains or posed its own way
TRIES = 4


def square(mask):
    """A mask cropped to what it holds and padded square, as a GRID by GRID mask."""
    rows, columns = np.flatnonzero(mask.any(1)), np.flatnonzero(mask.any(0))
    crop = mask[rows[0]:rows[-1] + 1, columns[0]:columns[-1] + 1]
    side = max(crop.shape)
    padded = np.zeros((side, side), dtype=np.uint8)
    top, left = (side - crop.shape[0]) // 2, (side - crop.shape[1]) // 2
    padded[top:top + crop.shape[0], left:left + crop.shape[1]] = crop * 255
    return np.asarray(Image.fromarray(padded).resize((GRID, GRID), Image.BILINEAR)) > 64


def grown(mask, pixels):
    """A mask grown by some pixels each way."""
    image = Image.fromarray(mask.astype(np.uint8) * 255)
    return np.asarray(image.filter(ImageFilter.MaxFilter(2 * pixels + 1))) > 0


def outline_check(vertices, image):
    """How a figure mesh's front outline compares with its image's: how much of it lies beyond
    the image's, as a share of the image's (curtains raise it), and how much the two overlap,
    as a share of both together (a limb TRELLIS posed its own way, like an arm hung straight
    down, lowers it). The mesh is +Y up and faces +Z, so its x runs across the image and y up
    it; the image's figure is what isn't its white background."""
    picture = square(np.asarray(image.convert("L")) < 240)
    x, y = vertices[:, 0], vertices[:, 1]
    width, height = np.ptp(x), np.ptp(y)
    size = 1024
    scale = (size - 1) / max(width, height, 1e-9)
    grid = np.zeros((int(height * scale) + 1, int(width * scale) + 1), dtype=bool)
    grid[((y.max() - y) * scale).astype(int), ((x - x.min()) * scale).astype(int)] = True
    mesh = square(grown(grid, 2))
    extra = float((mesh & ~grown(picture, 3)).sum() / max(picture.sum(), 1))
    overlap = float((mesh & picture).sum() / max((mesh | picture).sum(), 1))
    return extra, overlap


def badness(check):
    """How far an outline check is from passing, 1 or less if it passes."""
    extra, overlap = check
    return max(extra / CURTAINS, MIN_OVERLAP / max(overlap, 1e-6))


def main():
    parser = argparse.ArgumentParser(description="Images to meshes with TRELLIS.2.")
    parser.add_argument("--trellis", required=True, help="The TRELLIS.2 folder.")
    parser.add_argument("--type", default="512", choices=["512", "1024", "1024_cascade", "1536_cascade"])
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--faces", type=int, default=200000, help="Faces the glb is decimated to.")
    parser.add_argument("--texture", type=int, default=2048, help="The glb's texture size.")
    parser.add_argument("--online", action="store_true",
                        help="Let Hugging Face download or update models, rather than only use "
                             "the local cache.")
    parser.add_argument("--figure", action="append", default=[],
                        help="An image of a whole figure, checked for curtains; may repeat.")
    parser.add_argument("--tries", type=int, default=TRIES,
                        help="Seeds tried at most for a figure with curtains.")
    parser.add_argument("pairs", nargs="+", help="Image and glb paths, in pairs.")
    args = parser.parse_args()
    if len(args.pairs) % 2:
        parser.error("images and glb paths come in pairs")

    os.environ.setdefault("ATTN_BACKEND", "xformers")
    os.environ.setdefault("SPARSE_ATTN_BACKEND", "xformers")
    os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")
    os.environ.setdefault("HF_HUB_DISABLE_SYMLINKS_WARNING", "1")
    # Before Hugging Face's libraries load, as they read these once
    if not args.online:
        os.environ["HF_HUB_OFFLINE"] = "1"
        os.environ["TRANSFORMERS_OFFLINE"] = "1"
    os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")
    sys.path.insert(0, args.trellis)

    import torch
    from PIL import Image
    from trellis2.pipelines import Trellis2ImageTo3DPipeline
    from trellis2.pipelines import rembg

    original = rembg.BiRefNet
    rembg.BiRefNet = lambda **kwargs: original(model_name="ZhengPeng7/BiRefNet")
    import o_voxel

    start = time.perf_counter()
    pipeline = Trellis2ImageTo3DPipeline.from_pretrained("microsoft/TRELLIS.2-4B")
    pipeline.low_vram = True
    pipeline.cuda()
    print("[trellis] loaded in %.0f s" % (time.perf_counter() - start), flush=True)

    figures = {os.path.abspath(f) for f in args.figure}
    for image_path, out in zip(args.pairs[0::2], args.pairs[1::2]):
        start = time.perf_counter()
        torch.cuda.reset_peak_memory_stats()
        free = torch.cuda.mem_get_info()[0]
        held = torch.cuda.memory_allocated()
        figure = os.path.abspath(image_path) in figures
        best = None
        for seed in range(args.seed, args.seed + (args.tries if figure else 1)):
            mesh = pipeline.run(Image.open(image_path), seed=seed, pipeline_type=args.type)[0]
            mesh.simplify(16777216)  # nvdiffrast's limit
            glb = o_voxel.postprocess.to_glb(
                vertices=mesh.vertices, faces=mesh.faces, attr_volume=mesh.attrs,
                coords=mesh.coords, attr_layout=mesh.layout, voxel_size=mesh.voxel_size,
                aabb=[[-0.5, -0.5, -0.5], [0.5, 0.5, 0.5]], decimation_target=args.faces,
                texture_size=args.texture, remesh=True, remesh_band=1, remesh_project=0,
                verbose=False)
            check = outline_check(np.asarray(glb.vertices), Image.open(image_path)) \
                if figure else None
            if best is None or (check is not None and badness(check) < badness(best[2])):
                best = (glb, seed, check)
            if check is None or badness(check) <= 1:
                break
            print("[trellis] %s: seed %d's outline doesn't match the image's (%.2f beyond it, "
                  "%.2f overlap): curtains or a limb posed its own way; trying the next"
                  % (os.path.basename(image_path), seed, *check), flush=True)
            del mesh
            torch.cuda.empty_cache()
        glb, seed, check = best
        if check is not None and badness(check) > 1:
            print("[trellis] %s: no seed tried matched the image's outline; keeping seed %d's"
                  % (os.path.basename(image_path), seed), flush=True)
        os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
        glb.export(out)
        with open(out + ".json", "w", encoding="utf-8") as file:
            json.dump({"seed": seed, "type": args.type,
                       "extraOutline": check and check[0], "overlap": check and check[1]},
                      file)
        print("[trellis] %s in %.0f s, seed %d, peak %.2f GB" % (
            out, time.perf_counter() - start, seed, torch.cuda.max_memory_allocated() / 1024 ** 3),
            flush=True)
        short = torch.cuda.max_memory_allocated() - held - free
        if short > 0:
            print("[trellis] %s needed %.1f GB more GPU memory than was free; other programs "
                  "held some. Meshes made short of memory ran slowly and hung curtains of "
                  "streaks even from good seeds: free the GPU and make it again"
                  % (os.path.basename(image_path), short / 1024 ** 3), flush=True)
        torch.cuda.empty_cache()


if __name__ == "__main__":
    main()
