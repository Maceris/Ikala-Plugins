package com.ikalagaming.converter.texgraph;

import static com.ikalagaming.converter.texgraph.CoreNodes.param;
import static com.ikalagaming.converter.texgraph.CoreNodes.pin;

import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The Output nodes, one per way a block's texture is laid out: the same tile on every face, sides
 * and ends for a column, or sides, top and bottom. Each takes one set of maps per face. They don't
 * run; baking reads their inputs.
 */
public final class OutputNodes {
    /** Category of the Output nodes. */
    static final String OUTPUT = "Output";

    /** The Output for the same tile on every face. */
    public static final String ALL = "output/all";

    /** The Output for a column: sides, then both ends. */
    public static final String COLUMN = "output/column";

    /** The Output for sides, then the top, then the bottom. */
    public static final String BOTTOM_TOP = "output/bottom_top";

    /** How steep the normal map's bumps get. */
    public static final String STRENGTH = "strength";

    /** Whether to write a normal map from the height. */
    public static final String NORMALS = "normals";

    /** Whether to write the height as its own file. */
    public static final String HEIGHT_FILE = "height file";

    /** The maps a face can have, in pin order. */
    public enum Map {
        /** Base color, sRGB with alpha for holes. */
        COLOR("color", TexturePins.COLOR),
        /** Height, written 16-bit, and made into the normal map. */
        HEIGHT("height", TexturePins.HEIGHT),
        /** Occlusion, the R of the ORM map. */
        OCCLUSION("occlusion", TexturePins.MASK),
        /** Roughness, the G of the ORM map. */
        ROUGHNESS("roughness", TexturePins.MASK),
        /** Metallic, the B of the ORM map. */
        METALLIC("metallic", TexturePins.MASK),
        /** Emitted light, sRGB. */
        EMISSIVE("emissive", TexturePins.COLOR),
        /** Which material each pixel is, by color. */
        MASK("mask", TexturePins.COLOR);

        /** The pin's name. */
        private final String pin;

        /** The pin's type. */
        private final String type;

        /**
         * Make a map.
         *
         * @param pin The pin's name.
         * @param type The pin's type.
         */
        Map(String pin, String type) {
            this.pin = pin;
            this.type = type;
        }

        /**
         * The pin's name for a face.
         *
         * @param face The face, or null for an Output with one face.
         * @return The pin's name.
         */
        public String pin(@Nullable Face face) {
            return face == null || face == Face.ALL ? pin : face.label + "_" + pin;
        }
    }

    /** Which way a face of a cube points, as the model's UVs are sorted. */
    public enum Side {
        /** Facing up. */
        UP,
        /** Facing down. */
        DOWN,
        /** Facing sideways. */
        SIDE
    }

    /** The faces an Output takes maps for. */
    public enum Face {
        /** Every face. */
        ALL("all", EnumSet.allOf(Side.class)),
        /** The four sides. */
        SIDES("side", EnumSet.of(Side.SIDE)),
        /** The top and bottom of a column. */
        ENDS("end", EnumSet.of(Side.UP, Side.DOWN)),
        /** The top. */
        TOP("top", EnumSet.of(Side.UP)),
        /** The bottom. */
        BOTTOM("bottom", EnumSet.of(Side.DOWN));

        /** The face's name in pins. */
        private final String label;

        /** Which ways it covers. */
        private final Set<Side> sides;

        /**
         * Make a face.
         *
         * @param label The name in pins.
         * @param sides Which ways it covers.
         */
        Face(String label, Set<Side> sides) {
            this.label = label;
            this.sides = sides;
        }

        /**
         * Which ways the face covers.
         *
         * @return The sides.
         */
        public Set<Side> sides() {
            return sides;
        }

        /**
         * The face's name, as pins and messages use it.
         *
         * @return The name.
         */
        public String label() {
            return label;
        }
    }

    /** Not made. */
    private OutputNodes() {}

    /**
     * The faces an Output type takes, in the order a texture lays them out.
     *
     * @param type The Output's type ID.
     * @return The faces, or empty if it isn't an Output.
     */
    public static List<Face> faces(@NonNull String type) {
        return switch (type) {
            case ALL -> List.of(Face.ALL);
            case COLUMN -> List.of(Face.SIDES, Face.ENDS);
            case BOTTOM_TOP -> List.of(Face.SIDES, Face.TOP, Face.BOTTOM);
            default -> List.of();
        };
    }

    /**
     * Whether a type is an Output.
     *
     * @param type The type's ID.
     * @return True for an Output.
     */
    public static boolean isOutput(@NonNull String type) {
        return !faces(type).isEmpty();
    }

    /**
     * Add the Output nodes to a library.
     *
     * @param library The library.
     * @return The library.
     */
    public static TextureLibrary register(@NonNull TextureLibrary library) {
        output(library, ALL, "Output");
        output(library, COLUMN, "Output (sides, ends)");
        output(library, BOTTOM_TOP, "Output (sides, top, bottom)");
        return library;
    }

    /**
     * Register an Output type.
     *
     * @param library The library.
     * @param id The type's ID.
     * @param title Its title.
     */
    private static void output(TextureLibrary library, String id, String title) {
        final List<NodeTypes.Pin> inputs = new ArrayList<>();
        for (Face face : faces(id)) {
            for (Map map : Map.values()) {
                inputs.add(pin(map.pin(face), map.type));
            }
        }
        library.register(
                CoreNodes.type(
                        id,
                        title,
                        OUTPUT,
                        inputs,
                        List.of(),
                        List.of(
                                param(STRENGTH, "1.5"),
                                param(NORMALS, "true"),
                                param(HEIGHT_FILE, "true"))),
                context -> {
                    // Nothing to work out: baking reads the inputs
                });
    }
}
