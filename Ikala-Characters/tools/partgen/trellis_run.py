"""Turn images into textured meshes with TRELLIS.2, loading the models once for all of them.

Run it with TRELLIS.2's own Python, which partgen.py does:

    <trellis>/.venv/Scripts/python.exe trellis_run.py --trellis <trellis folder>
        --type 512 [--seed 0] [--faces 200000] [--texture 2048] <image> <out.glb> [<image> <out.glb>...]

Images with transparency skip background removal. Others go through BiRefNet with the MIT
ZhengPeng7 weights, not the RMBG-2.0 weights TRELLIS.2's config names (non-commercial, gated).
Each glb comes out in TRELLIS.2's space: a 1 m box around the origin, +Y up, facing +Z.
"""
import argparse
import os
import sys
import time


def main():
    parser = argparse.ArgumentParser(description="Images to meshes with TRELLIS.2.")
    parser.add_argument("--trellis", required=True, help="The TRELLIS.2 folder.")
    parser.add_argument("--type", default="512", choices=["512", "1024", "1024_cascade", "1536_cascade"])
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--faces", type=int, default=200000, help="Faces the glb is decimated to.")
    parser.add_argument("--texture", type=int, default=2048, help="The glb's texture size.")
    parser.add_argument("pairs", nargs="+", help="Image and glb paths, in pairs.")
    args = parser.parse_args()
    if len(args.pairs) % 2:
        parser.error("images and glb paths come in pairs")

    os.environ.setdefault("ATTN_BACKEND", "xformers")
    os.environ.setdefault("SPARSE_ATTN_BACKEND", "xformers")
    os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")
    os.environ.setdefault("HF_HUB_DISABLE_SYMLINKS_WARNING", "1")
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

    for image_path, out in zip(args.pairs[0::2], args.pairs[1::2]):
        start = time.perf_counter()
        torch.cuda.reset_peak_memory_stats()
        mesh = pipeline.run(Image.open(image_path), seed=args.seed, pipeline_type=args.type)[0]
        mesh.simplify(16777216)  # nvdiffrast's limit
        glb = o_voxel.postprocess.to_glb(
            vertices=mesh.vertices, faces=mesh.faces, attr_volume=mesh.attrs, coords=mesh.coords,
            attr_layout=mesh.layout, voxel_size=mesh.voxel_size,
            aabb=[[-0.5, -0.5, -0.5], [0.5, 0.5, 0.5]], decimation_target=args.faces,
            texture_size=args.texture, remesh=True, remesh_band=1, remesh_project=0, verbose=False)
        os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
        glb.export(out)
        print("[trellis] %s in %.0f s, peak %.2f GB" % (
            out, time.perf_counter() - start, torch.cuda.max_memory_allocated() / 1024 ** 3), flush=True)
        torch.cuda.empty_cache()


if __name__ == "__main__":
    main()
