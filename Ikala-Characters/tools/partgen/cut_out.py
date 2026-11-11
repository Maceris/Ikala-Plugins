"""Cut objects out of pictures: write a mask of each picture's object, white on black, with
BiRefNet (ZhengPeng7's MIT weights), the background remover TRELLIS.2 uses.

Run it with TRELLIS.2's own Python, which partgen.py does:

    <trellis>/.venv/Scripts/python.exe cut_out.py --trellis <trellis folder>
        <picture> <mask.png> [<picture> <mask.png>...]

It runs offline, from Hugging Face's local cache, as trellis_run.py does.
"""
import argparse
import os
import sys


def main():
    parser = argparse.ArgumentParser(description="Masks of pictures' objects.")
    parser.add_argument("--trellis", required=True, help="The TRELLIS.2 folder.")
    parser.add_argument("pairs", nargs="+", help="Picture and mask paths, in pairs.")
    args = parser.parse_args()
    if len(args.pairs) % 2:
        parser.error("pictures and mask paths come in pairs")
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")
    sys.path.insert(0, args.trellis)

    from PIL import Image
    from trellis2.pipelines.rembg import BiRefNet

    remover = BiRefNet(model_name="ZhengPeng7/BiRefNet")
    remover.cuda()
    for picture, mask in zip(args.pairs[0::2], args.pairs[1::2]):
        cut = remover(Image.open(picture).convert("RGB"))
        cut.getchannel("A").save(mask)
        print("[cut] wrote", mask, flush=True)


if __name__ == "__main__":
    main()
