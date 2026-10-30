"""Copy a skinned glTF binary keeping only its skeleton and animation clips.

Meshes, materials, textures and images are dropped, along with the buffer data only they used, so
a character kit's clips can stay as test animations without its characters. The first skin stays,
which is what Ikala-Characters makes the skeleton from, and every animation keeps its channels.

    python clips_only.py <in.glb> <out.glb>
"""

import argparse
import json
import pathlib
import struct

GLB_MAGIC = 0x46546C67
JSON_CHUNK = 0x4E4F534A
BIN_CHUNK = 0x004E4942


def read_glb(path):
    """The JSON and binary chunks of a glTF binary."""
    data = path.read_bytes()
    magic, version, _ = struct.unpack_from("<III", data, 0)
    if magic != GLB_MAGIC or version != 2:
        raise SystemExit(f"{path} isn't a glTF 2 binary")
    offset = 12
    gltf, binary = None, b""
    while offset < len(data):
        length, kind = struct.unpack_from("<II", data, offset)
        chunk = data[offset + 8:offset + 8 + length]
        if kind == JSON_CHUNK:
            gltf = json.loads(chunk.decode("utf8"))
        elif kind == BIN_CHUNK:
            binary = chunk
        offset += 8 + length
    return gltf, binary


def write_glb(path, gltf, binary):
    """Write a glTF binary, padding the chunks to four bytes."""
    text = json.dumps(gltf, separators=(",", ":")).encode("utf8")
    text += b" " * (-len(text) % 4)
    binary += b"\0" * (-len(binary) % 4)
    total = 12 + 8 + len(text) + (8 + len(binary) if binary else 0)
    out = struct.pack("<III", GLB_MAGIC, 2, total) + struct.pack("<II", len(text), JSON_CHUNK) + text
    if binary:
        out += struct.pack("<II", len(binary), BIN_CHUNK) + binary
    path.write_bytes(out)


def clips_only(gltf, binary):
    """Strip everything but the skeleton and clips, and pack the buffer data they still use."""
    for node in gltf.get("nodes", []):
        node.pop("mesh", None)
        node.pop("skin", None)
    for key in ("meshes", "materials", "textures", "images", "samplers"):
        gltf.pop(key, None)
    gltf["skins"] = gltf.get("skins", [])[:1]

    used = []
    for skin in gltf["skins"]:
        if "inverseBindMatrices" in skin:
            used.append(skin["inverseBindMatrices"])
    for animation in gltf.get("animations", []):
        for sampler in animation["samplers"]:
            used += [sampler["input"], sampler["output"]]
    used = sorted(set(used))
    accessors = {old: new for new, old in enumerate(used)}

    views, packed = [], bytearray()
    view_map = {}
    new_accessors = []
    for old in used:
        accessor = dict(gltf["accessors"][old])
        view_index = accessor["bufferView"]
        if view_index not in view_map:
            view = dict(gltf["bufferViews"][view_index])
            start = view.get("byteOffset", 0)
            packed += b"\0" * (-len(packed) % 4)
            chunk = binary[start:start + view["byteLength"]]
            view["byteOffset"] = len(packed)
            view["buffer"] = 0
            view.pop("target", None)
            packed += chunk
            view_map[view_index] = len(views)
            views.append(view)
        accessor["bufferView"] = view_map[view_index]
        new_accessors.append(accessor)

    for skin in gltf["skins"]:
        if "inverseBindMatrices" in skin:
            skin["inverseBindMatrices"] = accessors[skin["inverseBindMatrices"]]
    for animation in gltf.get("animations", []):
        for sampler in animation["samplers"]:
            sampler["input"] = accessors[sampler["input"]]
            sampler["output"] = accessors[sampler["output"]]
    gltf["accessors"] = new_accessors
    gltf["bufferViews"] = views
    gltf["buffers"] = [{"byteLength": len(packed)}]
    # Extensions the dropped materials and textures used are gone with them
    gltf.pop("extensionsUsed", None)
    gltf.pop("extensionsRequired", None)
    return gltf, bytes(packed)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("source", type=pathlib.Path, help="the skinned glTF binary")
    parser.add_argument("out", type=pathlib.Path, help="where to write the clips")
    args = parser.parse_args()
    gltf, binary = clips_only(*read_glb(args.source))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    write_glb(args.out, gltf, binary)
    print(f"wrote {args.out}: {len(gltf['nodes'])} nodes, {len(gltf.get('animations', []))} clips,"
          f" {args.out.stat().st_size // 1024} KiB")


if __name__ == "__main__":
    main()
