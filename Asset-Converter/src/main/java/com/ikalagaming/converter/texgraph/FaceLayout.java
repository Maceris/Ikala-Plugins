package com.ikalagaming.converter.texgraph;

import com.ikalagaming.converter.texgraph.OutputNodes.Face;
import com.ikalagaming.converter.texgraph.OutputNodes.Side;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * How a model lays its texture out: how many tiles across it is, and which tile its up, down and
 * side faces show, worked out from the model's own UVs. Baking checks an Output's faces against it
 * and places each face's maps on its tile, so the editor never guesses a layout.
 *
 * @param tiles How many tiles across the texture is.
 * @param tileOf The tile each kind of face shows; kinds the model doesn't have are absent.
 */
public record FaceLayout(int tiles, @NonNull Map<Side, Integer> tileOf) {
    /** A face whose normal is closer than this to straight up or down is a top or bottom. */
    private static final float FLAT = 0.9f;

    /** The offset of the normal in a vertex. */
    private static final int NORMAL = 3;

    /** The offset of the texture coordinates in a vertex. */
    private static final int UV = 12;

    /** A layout of one tile on every face, for when there is no model. */
    public static final FaceLayout ONE_TILE =
            new FaceLayout(1, Map.of(Side.UP, 0, Side.DOWN, 0, Side.SIDE, 0));

    /**
     * Work out a model's layout from its UVs.
     *
     * @param mesh The model.
     * @return The layout.
     * @throws IllegalArgumentException If faces of one kind show more than one tile, or a face
     *     spans tiles.
     */
    public static FaceLayout of(@NonNull MeshData mesh) {
        final float[] vertices = mesh.getVertexData();
        final int[] indices = mesh.getIndices();
        final int stride = MeshData.VERTEX_SIZE_IN_FLOATS;
        final Map<Side, float[]> spans = new EnumMap<>(Side.class);
        for (int t = 0; t + 2 < indices.length; t += 3) {
            float normalY = 0;
            float low = Float.MAX_VALUE;
            float high = -Float.MAX_VALUE;
            for (int k = 0; k < 3; ++k) {
                final int base = indices[t + k] * stride;
                normalY += vertices[base + NORMAL + 1] / 3;
                final float u = vertices[base + UV];
                low = Math.min(low, u);
                high = Math.max(high, u);
            }
            final Side side = normalY > FLAT ? Side.UP : normalY < -FLAT ? Side.DOWN : Side.SIDE;
            final float[] span = spans.computeIfAbsent(side, s -> new float[] {2, -1});
            span[0] = Math.min(span[0], low);
            span[1] = Math.max(span[1], high);
        }
        if (spans.isEmpty()) {
            return ONE_TILE;
        }
        // All faces of a kind show one tile between them, so the narrowest kind's span is a tile.
        // Spans, not single faces: a stair's small faces still add up to their whole tile
        float narrowest = Float.MAX_VALUE;
        for (float[] span : spans.values()) {
            if (span[1] - span[0] > 1e-4f) {
                narrowest = Math.min(narrowest, span[1] - span[0]);
            }
        }
        final int tiles = narrowest == Float.MAX_VALUE ? 1 : Math.max(1, Math.round(1 / narrowest));
        final Map<Side, Integer> tileOf = new EnumMap<>(Side.class);
        for (Map.Entry<Side, float[]> entry : spans.entrySet()) {
            final float[] span = entry.getValue();
            final int first = (int) Math.floor(span[0] * tiles + 1e-3f);
            final int last = (int) Math.ceil(span[1] * tiles - 1e-3f) - 1;
            if (first != last) {
                throw new IllegalArgumentException(
                        "The model's "
                                + entry.getKey().name().toLowerCase(java.util.Locale.ROOT)
                                + " faces show more than one of its "
                                + tiles
                                + " tiles");
            }
            tileOf.put(entry.getKey(), Math.floorMod(first, tiles));
        }
        return new FaceLayout(tiles, tileOf);
    }

    /**
     * Which tile each of an Output's faces goes on, checking the faces fit the model.
     *
     * @param faces The Output's faces.
     * @return Each face's tile, in the faces' order.
     * @throws IllegalArgumentException If the faces don't fit this layout.
     */
    public List<Integer> place(@NonNull List<Face> faces) {
        final List<Integer> tiles = new ArrayList<>();
        final Map<Integer, Face> used = new java.util.HashMap<>();
        for (Face face : faces) {
            Integer tile = null;
            for (Side side : face.sides()) {
                final Integer shown = tileOf.get(side);
                if (shown == null) {
                    continue;
                }
                if (tile != null && !tile.equals(shown)) {
                    throw new IllegalArgumentException(
                            "The model shows different tiles where the Output's "
                                    + face.label()
                                    + " face expects one");
                }
                tile = shown;
            }
            if (tile == null) {
                tile = faces.size() == 1 ? 0 : -1;
            }
            if (tile < 0) {
                throw new IllegalArgumentException(
                        "The model has no faces for the Output's " + face.label() + " face");
            }
            final Face other = used.put(tile, face);
            if (other != null) {
                throw new IllegalArgumentException(
                        "The model shows one tile for the Output's "
                                + other.label()
                                + " and "
                                + face.label()
                                + " faces; use an Output with fewer faces");
            }
            tiles.add(tile);
        }
        if (used.size() != this.tiles) {
            throw new IllegalArgumentException(
                    "The model's texture is "
                            + this.tiles
                            + " tiles across, but the Output has "
                            + faces.size()
                            + " faces");
        }
        return tiles;
    }
}
