"""Draw the built-in decals: simple emblems whose image is a mask like a tint mask, red for the
fill (a design's first decal color), green for the outline (its second) and alpha for the shape.

    python make_decals.py <folder>

writes star.png, shield.png and bolt.png into the folder (the plugin's characters/ikala/decals).
Needs Pillow.
"""

import math
import pathlib
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

# The image's size, in pixels, and how much larger it is drawn before shrinking, for smooth edges
SIZE = 256
SUPERSAMPLE = 4
# How wide the outline is, as a share of the image
OUTLINE = 0.06


def star(size):
    """A five-pointed star, point up."""
    middle = size / 2
    outer, inner = size * 0.47, size * 0.19
    points = []
    for k in range(10):
        radius = outer if k % 2 == 0 else inner
        angle = math.pi / 2 + k * math.pi / 5
        points.append((middle + radius * math.cos(angle), middle - radius * math.sin(angle)))
    return points


def shield(size):
    """A heater shield: flat top, sides curving to a point."""
    points = [(size * 0.12, size * 0.08), (size * 0.88, size * 0.08)]
    for k in range(1, 17):
        t = k / 16
        x = size * (0.88 - 0.38 * t ** 2.5)
        y = size * (0.08 + 0.87 * t)
        points.append((x, y))
    right = points[2:]
    left = [(size - x, y) for x, y in reversed(right)]
    return points + left[1:]


def bolt(size):
    """A lightning bolt."""
    return [(size * x, size * y) for x, y in
            ((0.58, 0.04), (0.22, 0.54), (0.46, 0.54), (0.36, 0.96),
             (0.80, 0.40), (0.55, 0.40), (0.70, 0.04))]


def draw(shape):
    """The mask image of a shape: its fill red, a band inside its edge green, its shape alpha."""
    big = SIZE * SUPERSAMPLE
    shape_mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(shape_mask).polygon(shape(big), fill=255)
    # The fill is the shape shrunk by the outline's width; the outline is the rest
    width = int(OUTLINE * big) | 1
    fill = shape_mask.filter(ImageFilter.MinFilter(width))
    outline = ImageChops.subtract(shape_mask, fill)
    image = Image.merge("RGBA", (fill, outline, Image.new("L", (big, big), 0), shape_mask))
    return image.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    folder = pathlib.Path(sys.argv[1])
    folder.mkdir(parents=True, exist_ok=True)
    for name, shape in (("star", star), ("shield", shield), ("bolt", bolt)):
        draw(shape).save(folder / ("%s.png" % name))
        print("wrote", folder / ("%s.png" % name))


if __name__ == "__main__":
    main()
