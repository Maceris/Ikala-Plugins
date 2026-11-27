package com.ikalagaming.converter.texgraph;

import static com.ikalagaming.converter.texgraph.CoreNodes.param;
import static com.ikalagaming.converter.texgraph.CoreNodes.pin;
import static com.ikalagaming.converter.texgraph.CoreNodes.type;
import static com.ikalagaming.converter.texgraph.TexturePins.HEIGHT;
import static com.ikalagaming.converter.texgraph.TexturePins.MASK;
import static com.ikalagaming.converter.texgraph.TexturePins.SCALAR;

import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import lombok.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * Pattern generators, sized in pixels, the unit pixel art is drawn in, and wrapping at the tile's
 * edges: bricks, planks, tiles, wood grain, rivets, panel seams, stripes, checker, gradients and
 * scratches. Laid out patterns give a mask (1 on the piece, 0 in the gaps), a random value per
 * piece from the node's seed, and a height that bevels toward the gaps.
 */
public final class PatternNodes {
    /** The category they are listed under. */
    static final String PATTERNS = "Patterns";

    /** The mask output. */
    static final String MASK_OUT = "mask";

    /** The per-piece random value output. */
    static final String ID = "id";

    /** The height output. */
    static final String HEIGHT_OUT = "height";

    /** The value output. */
    static final String VALUE = "value";

    /** Not made. */
    private PatternNodes() {}

    /**
     * Add the pattern nodes to a library.
     *
     * @param library The library.
     * @return The library.
     */
    public static TextureLibrary register(@NonNull TextureLibrary library) {
        register(
                library,
                pieces("pattern/bricks", "Bricks"),
                List.of(
                        param("width", "8"),
                        param("height", "4"),
                        param("gap", "1"),
                        param("shift", "0.5"),
                        param("bevel", "1")),
                context ->
                        courses(
                                context,
                                context.number("width"),
                                context.number("height"),
                                context.number("gap"),
                                context.number("shift"),
                                false));
        register(
                library,
                pieces("pattern/planks", "Planks"),
                List.of(
                        param("width", "4"),
                        param("length", "16"),
                        param("gap", "1"),
                        param("shift", "0.37"),
                        param("bevel", "1")),
                context ->
                        courses(
                                context,
                                context.number("length"),
                                context.number("width"),
                                context.number("gap"),
                                context.number("shift"),
                                true));
        register(
                library,
                pieces("pattern/tiles", "Tiles"),
                List.of(param("size", "8"), param("gap", "1"), param("bevel", "1")),
                context ->
                        courses(
                                context,
                                context.number("size"),
                                context.number("size"),
                                context.number("gap"),
                                0,
                                false));
        library.register(
                type(
                        "pattern/panel_seams",
                        "Panel Seams",
                        PATTERNS,
                        List.of(),
                        List.of(pin(MASK_OUT, MASK), pin(HEIGHT_OUT, HEIGHT)),
                        List.of(param("size", "16"), param("width", "1"), param("depth", "0.5"))),
                context -> {
                    final int size = Math.max(1, Math.round(context.number("size")));
                    final int width = Math.max(1, Math.round(context.number("width")));
                    final float depth = context.number("depth");
                    final Plane mask = context.plane(1);
                    final Plane height = context.plane(1);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            final boolean seam =
                                    Math.floorMod(x, size) < width
                                            || Math.floorMod(y, size) < width;
                            mask.set(x, y, 0, seam ? 0 : 1);
                            height.set(x, y, 0, seam ? 1 - depth : 1);
                        }
                    }
                    context.output(MASK_OUT, mask);
                    context.output(HEIGHT_OUT, height);
                });
        library.register(
                type(
                        "pattern/rivets",
                        "Rivets",
                        PATTERNS,
                        List.of(),
                        List.of(pin(MASK_OUT, MASK), pin(HEIGHT_OUT, HEIGHT)),
                        List.of(param("spacing", "8"), param("radius", "1"), param("inset", "2"))),
                context -> {
                    final float spacing = Math.max(1, context.number("spacing"));
                    final float radius = Math.max(0.5f, context.number("radius"));
                    final float inset = context.number("inset");
                    final Plane mask = context.plane(1);
                    final Plane height = context.plane(1);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            final float dx = wrapped(x + 0.5f - inset - 0.5f, spacing);
                            final float dy = wrapped(y + 0.5f - inset - 0.5f, spacing);
                            final float distance = (float) Math.sqrt(dx * dx + dy * dy);
                            final float dome =
                                    distance > radius
                                            ? 0
                                            : (float)
                                                    Math.sqrt(
                                                            1
                                                                    - distance
                                                                            * distance
                                                                            / (radius * radius));
                            mask.set(x, y, 0, distance <= radius ? 1 : 0);
                            height.set(x, y, 0, dome);
                        }
                    }
                    context.output(MASK_OUT, mask);
                    context.output(HEIGHT_OUT, height);
                });
        library.register(
                type(
                        "pattern/stripes",
                        "Stripes",
                        PATTERNS,
                        List.of(),
                        List.of(pin(MASK_OUT, MASK)),
                        List.of(
                                param("period", "4"),
                                param("width", "2"),
                                param("direction", "horizontal"))),
                context -> {
                    final int period = Math.max(1, Math.round(context.number("period")));
                    final int width = Math.round(context.number("width"));
                    final String direction =
                            context.text("direction").trim().toLowerCase(Locale.ROOT);
                    final Plane mask = context.plane(1);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            final int along =
                                    switch (direction) {
                                        case "horizontal" -> y;
                                        case "vertical" -> x;
                                        case "diagonal" -> x + y;
                                        default ->
                                                throw new TextureNode.NodeException(
                                                        "Stripes run horizontal, vertical or"
                                                                + " diagonal, not "
                                                                + direction);
                                    };
                            mask.set(x, y, 0, Math.floorMod(along, period) < width ? 1 : 0);
                        }
                    }
                    context.output(MASK_OUT, mask);
                });
        library.register(
                type(
                        "pattern/checker",
                        "Checker",
                        PATTERNS,
                        List.of(),
                        List.of(pin(MASK_OUT, MASK)),
                        List.of(param("size", "4"))),
                context -> {
                    final int size = Math.max(1, Math.round(context.number("size")));
                    final Plane mask = context.plane(1);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            mask.set(x, y, 0, (x / size + y / size) % 2 == 0 ? 1 : 0);
                        }
                    }
                    context.output(MASK_OUT, mask);
                });
        library.register(
                type(
                        "pattern/gradient",
                        "Gradient",
                        PATTERNS,
                        List.of(),
                        List.of(pin(VALUE, SCALAR)),
                        List.of(param("kind", "down"))),
                context -> {
                    final String kind = context.text("kind").trim().toLowerCase(Locale.ROOT);
                    final int width = context.getWidth();
                    final int height = context.getHeight();
                    final Plane out = context.plane(1);
                    for (int y = 0; y < height; ++y) {
                        for (int x = 0; x < width; ++x) {
                            final float u = (x + 0.5f) / width;
                            final float v = (y + 0.5f) / height;
                            final float value =
                                    switch (kind) {
                                        case "down" -> v;
                                        case "across" -> u;
                                        case "radial" ->
                                                Math.min(
                                                        1,
                                                        (float) Math.hypot(u - 0.5f, v - 0.5f) * 2);
                                        case "ridge" -> 1 - Math.abs(v * 2 - 1);
                                        default ->
                                                throw new TextureNode.NodeException(
                                                        "A gradient runs down, across, radial or"
                                                                + " ridge, not "
                                                                + kind);
                                    };
                            out.set(x, y, 0, value);
                        }
                    }
                    context.output(VALUE, out);
                });
        library.register(
                type(
                        "pattern/wood_grain",
                        "Wood Grain",
                        PATTERNS,
                        List.of(),
                        List.of(pin(VALUE, SCALAR)),
                        List.of(param("lines", "4"), param("wobble", "0.3"), param("size", "8"))),
                context -> {
                    final float lines = Math.max(1, Math.round(context.number("lines")));
                    final float wobble = context.number("wobble");
                    final Plane bend =
                            Noise.make(
                                    Noise.Kind.GRADIENT,
                                    context.getWidth(),
                                    context.getHeight(),
                                    context.number("size"),
                                    2,
                                    0.5f,
                                    context.getSeed());
                    final Plane out = context.plane(1);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            final float across = (x + 0.5f) / context.getWidth() * lines;
                            final double phase = across + wobble * (bend.get(x, y, 0) - 0.5f) * 2;
                            out.set(x, y, 0, (float) (0.5 + 0.5 * Math.cos(2 * Math.PI * phase)));
                        }
                    }
                    context.output(VALUE, out);
                });
        library.register(
                type(
                        "pattern/scratches",
                        "Scratches",
                        PATTERNS,
                        List.of(),
                        List.of(pin(MASK_OUT, MASK)),
                        List.of(param("count", "6"), param("length", "6"))),
                context -> {
                    final int count = Math.clamp(context.integer("count"), 0, 4096);
                    final float length = Math.max(1, context.number("length"));
                    final int width = context.getWidth();
                    final int height = context.getHeight();
                    final Plane mask = context.plane(1);
                    long bits = context.getSeed();
                    for (int i = 0; i < count; ++i) {
                        bits = Evaluator.mix(bits + i);
                        final float startX = CoreNodes.unit(bits) * width;
                        final float startY = CoreNodes.unit(Evaluator.mix(bits)) * height;
                        final double angle =
                                CoreNodes.unit(Evaluator.mix(bits ^ 0xA5A5L)) * Math.PI;
                        final int steps = Math.max(1, Math.round(length * 2));
                        for (int s = 0; s <= steps; ++s) {
                            final float t = length * s / steps;
                            final int px =
                                    Math.floorMod(
                                            (int) Math.floor(startX + Math.cos(angle) * t), width);
                            final int py =
                                    Math.floorMod(
                                            (int) Math.floor(startY + Math.sin(angle) * t), height);
                            mask.set(px, py, 0, 1);
                        }
                    }
                    context.output(MASK_OUT, mask);
                });
        return library;
    }

    /**
     * The type of a pattern of pieces: a mask, an id per piece and a height.
     *
     * @param id The type's ID.
     * @param title Its title.
     * @return The type, without parameters yet.
     */
    private static PieceType pieces(String id, String title) {
        return new PieceType(id, title);
    }

    /**
     * A pieces pattern's ID and title, given its parameters when registered.
     *
     * @param id The type's ID.
     * @param title Its title.
     */
    private record PieceType(String id, String title) {}

    /**
     * Register a pieces pattern.
     *
     * @param library The library.
     * @param pieces Its ID and title.
     * @param params Its parameters.
     * @param node What it does.
     */
    private static void register(
            TextureLibrary library,
            PieceType pieces,
            List<NodeTypes.Param> params,
            TextureNode node) {
        library.register(
                type(
                        pieces.id(),
                        pieces.title(),
                        PATTERNS,
                        List.of(),
                        List.of(pin(MASK_OUT, MASK), pin(ID, SCALAR), pin(HEIGHT_OUT, HEIGHT)),
                        params),
                node);
    }

    /**
     * Rows of pieces, each row shifted along from the one above: bricks, tiles, or planks when
     * turned to run down.
     *
     * @param context The node's context.
     * @param pieceLength A piece's length along its row, in pixels.
     * @param across A row's thickness, in pixels.
     * @param gap The gap between pieces, in pixels.
     * @param shift How far each row moves along, as a part of a piece; planks shift by random.
     * @param down Whether rows run down the tile rather than across.
     */
    private static void courses(
            NodeContext context,
            float pieceLength,
            float across,
            float gap,
            float shift,
            boolean down) {
        final int width = context.getWidth();
        final int height = context.getHeight();
        final int length = Math.max(1, Math.round(pieceLength));
        final int thick = Math.max(1, Math.round(across));
        final int gapPixels = Math.max(0, Math.round(gap));
        final float bevel = Math.max(0, context.number("bevel"));
        final int alongSize = down ? height : width;
        final int acrossSize = down ? width : height;
        final int rows = Math.max(1, Math.round((float) acrossSize / thick));
        final int perRow = Math.max(1, Math.round((float) alongSize / length));
        final Plane mask = context.plane(1);
        final Plane id = context.plane(1);
        final Plane bump = context.plane(1);
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                final int along = down ? y : x;
                final int side = down ? x : y;
                final int row = Math.min(rows - 1, side * rows / acrossSize);
                final int rowStart = row * acrossSize / rows;
                final int rowEnd = (row + 1) * acrossSize / rows;
                final float rowShift =
                        down
                                ? CoreNodes.unit(Evaluator.mix(context.getSeed() ^ row)) * shift
                                : row * shift;
                final int offset = Math.round(rowShift * alongSize / perRow);
                final int shifted = Math.floorMod(along + offset, alongSize);
                final int piece = Math.min(perRow - 1, shifted * perRow / alongSize);
                final int pieceStart = piece * alongSize / perRow;
                final int pieceEnd = (piece + 1) * alongSize / perRow;
                // Distance to the piece's edges, with the gap on its far sides
                final int toEnd =
                        Math.min(shifted - pieceStart, pieceEnd - gapPixels - 1 - shifted);
                final int toSide = Math.min(side - rowStart, rowEnd - gapPixels - 1 - side);
                final int edge = Math.min(toEnd, toSide);
                final boolean inGap = edge < 0;
                mask.set(x, y, 0, inGap ? 0 : 1);
                id.set(
                        x,
                        y,
                        0,
                        CoreNodes.unit(Evaluator.mix(context.getSeed() ^ (row * 7919L + piece))));
                bump.set(
                        x,
                        y,
                        0,
                        inGap ? 0 : bevel == 0 ? 1 : Math.min(1, (edge + 1) / (bevel + 1)));
            }
        }
        context.output(MASK_OUT, mask);
        context.output(ID, id);
        context.output(HEIGHT_OUT, bump);
    }

    /**
     * A distance folded into a repeating span, to the nearest repeat.
     *
     * @param value The distance.
     * @param span The span.
     * @return From -span/2 to span/2.
     */
    private static float wrapped(float value, float span) {
        final float folded = value - span * (float) Math.floor(value / span);
        return folded > span / 2 ? folded - span : folded;
    }
}
