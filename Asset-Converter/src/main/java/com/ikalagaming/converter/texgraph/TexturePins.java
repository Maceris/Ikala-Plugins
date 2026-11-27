package com.ikalagaming.converter.texgraph;

import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.ui.graph.model.PinTypes;

import lombok.NonNull;

/**
 * The pin types of texture graphs, how many channels each carries, and the conversions made without
 * a node. Color to palette index is never automatic; it waits for a Quantize node.
 */
public final class TexturePins {
    /** One float per pixel. */
    public static final String SCALAR = "scalar";

    /** 0 to 1 per pixel. */
    public static final String MASK = "mask";

    /** A height per pixel, higher brighter. */
    public static final String HEIGHT = "height";

    /** Linear RGBA. */
    public static final String COLOR = "color";

    /** An image at its own size, made into a color by sampling it. */
    public static final String TEXTURE = "texture";

    /** Three floats per pixel: normals, directions, flow. */
    public static final String VECTOR = "vector";

    /** A position in the tile per pixel, 0 to 1 across and down, wrapping at the edge. */
    public static final String COORDINATES = "coordinates";

    /** An index per pixel into a palette the plane carries; reads as color through it. */
    public static final String PALETTE = "palette";

    /** The node that samples a texture into a color, put in when one is linked into a color. */
    public static final String SAMPLE = "texture/sample";

    /** Not made. */
    private TexturePins() {}

    /**
     * The pin types and conversions, for a graph.
     *
     * @return The types.
     */
    public static PinTypes types() {
        return new PinTypes()
                .register(
                        new PinTypes.PinType(SCALAR, "Scalar", Color.rgba(0.62f, 0.64f, 0.68f, 1)))
                .register(new PinTypes.PinType(MASK, "Mask", Color.rgba(0.95f, 0.95f, 0.95f, 1)))
                .register(
                        new PinTypes.PinType(HEIGHT, "Height", Color.rgba(0.55f, 0.75f, 0.45f, 1)))
                .register(new PinTypes.PinType(COLOR, "Color", Color.rgba(1.0f, 0.78f, 0.25f, 1)))
                .register(
                        new PinTypes.PinType(TEXTURE, "Texture", Color.rgba(0.85f, 0.45f, 0.3f, 1)))
                .register(new PinTypes.PinType(VECTOR, "Vector", Color.rgba(0.45f, 0.6f, 1.0f, 1)))
                .register(
                        new PinTypes.PinType(
                                COORDINATES, "Coordinates", Color.rgba(0.75f, 0.5f, 0.95f, 1)))
                .register(
                        new PinTypes.PinType(
                                PALETTE, "Palette index", Color.rgba(0.95f, 0.45f, 0.75f, 1)))
                .convertDirectly(PALETTE, COLOR)
                .convertDirectly(PALETTE, SCALAR)
                .convertDirectly(SCALAR, MASK)
                .convertDirectly(SCALAR, COLOR)
                .convertDirectly(SCALAR, HEIGHT)
                .convertDirectly(MASK, SCALAR)
                .convertDirectly(MASK, HEIGHT)
                .convertDirectly(HEIGHT, SCALAR)
                .convertDirectly(COORDINATES, VECTOR)
                .convertWith(TEXTURE, COLOR, SAMPLE);
    }

    /**
     * How many floats a pin type carries per pixel.
     *
     * @param type The pin type.
     * @return The channel count.
     * @throws IllegalArgumentException If the type isn't a texture graph's.
     */
    public static int channels(@NonNull String type) {
        return switch (type) {
            case SCALAR, MASK, HEIGHT, PALETTE -> 1;
            case COORDINATES -> 2;
            case VECTOR -> 3;
            case COLOR, TEXTURE -> 4;
            default -> throw new IllegalArgumentException("Not a texture graph pin type: " + type);
        };
    }

    /**
     * What an input reads when nothing is linked into it: zero, or opaque black for a color.
     *
     * @param type The input's type.
     * @param width Pixels across.
     * @param height Pixels down.
     * @return The plane.
     */
    public static Plane unlinked(@NonNull String type, int width, int height) {
        return switch (type) {
            case COLOR, TEXTURE -> Plane.filled(width, height, 0, 0, 0, 1);
            default -> new Plane(width, height, channels(type));
        };
    }

    /**
     * Change a plane from one type to another, as a link that converts directly does.
     *
     * @param plane The plane.
     * @param from Its type.
     * @param to The type wanted.
     * @return The plane as that type; the same plane if nothing changes.
     */
    public static Plane convert(@NonNull Plane plane, @NonNull String from, @NonNull String to) {
        if (from.equals(to) || PinTypes.ANY.equals(from) || PinTypes.ANY.equals(to)) {
            return plane;
        }
        if (PALETTE.equals(from) && COLOR.equals(to)) {
            return throughPalette(plane);
        }
        if (MASK.equals(to)) {
            return plane.map((value, channel) -> Math.clamp(value, 0, 1));
        }
        if (COLOR.equals(to) && plane.getChannels() == 1) {
            final Plane out = new Plane(plane.getWidth(), plane.getHeight(), 4);
            for (int y = 0; y < plane.getHeight(); ++y) {
                for (int x = 0; x < plane.getWidth(); ++x) {
                    final float value = plane.get(x, y, 0);
                    out.set(x, y, 0, value);
                    out.set(x, y, 1, value);
                    out.set(x, y, 2, value);
                    out.set(x, y, 3, 1);
                }
            }
            return out;
        }
        if (VECTOR.equals(to) && plane.getChannels() == 2) {
            final Plane out = new Plane(plane.getWidth(), plane.getHeight(), 3);
            for (int y = 0; y < plane.getHeight(); ++y) {
                for (int x = 0; x < plane.getWidth(); ++x) {
                    out.set(x, y, 0, plane.get(x, y, 0));
                    out.set(x, y, 1, plane.get(x, y, 1));
                }
            }
            return out;
        }
        // Scalars, masks and heights share their one channel
        return plane;
    }

    /**
     * Palette indices as the colors they name; an index past the palette, or a plane without one,
     * is opaque black.
     *
     * @param plane The indices.
     * @return The colors.
     */
    public static Plane throughPalette(@NonNull Plane plane) {
        final float[] palette = plane.getPalette();
        final int size = palette == null ? 0 : palette.length / 4;
        final Plane out = new Plane(plane.getWidth(), plane.getHeight(), 4);
        for (int y = 0; y < plane.getHeight(); ++y) {
            for (int x = 0; x < plane.getWidth(); ++x) {
                final int index = Math.round(plane.get(x, y, 0));
                if (index < 0 || index >= size) {
                    out.set(x, y, 3, 1);
                    continue;
                }
                for (int c = 0; c < 4; ++c) {
                    out.set(x, y, c, palette[index * 4 + c]);
                }
            }
        }
        return out;
    }
}
