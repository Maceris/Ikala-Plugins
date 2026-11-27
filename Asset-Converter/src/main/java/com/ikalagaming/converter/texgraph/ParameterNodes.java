package com.ikalagaming.converter.texgraph;

import static com.ikalagaming.converter.texgraph.CoreNodes.param;
import static com.ikalagaming.converter.texgraph.CoreNodes.pin;
import static com.ikalagaming.converter.texgraph.CoreNodes.type;
import static com.ikalagaming.converter.texgraph.TexturePins.COLOR;
import static com.ikalagaming.converter.texgraph.TexturePins.MASK;
import static com.ikalagaming.converter.texgraph.TexturePins.SCALAR;
import static com.ikalagaming.converter.texgraph.TexturePins.TEXTURE;

import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import lombok.NonNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Nodes that read the graph's parameters: one Parameter node per kind, and Switch nodes, which pass
 * on one of two inputs as a switch parameter says and never evaluate the other.
 */
public final class ParameterNodes {
    /** The category they are listed under. */
    static final String PARAMETERS = "Parameters";

    /** The node parameter naming the graph parameter read. */
    public static final String NAME = "name";

    /** A switch's input used when on. */
    static final String ON = "on";

    /** A switch's input used when off. */
    static final String OFF = "off";

    /** What Parameter node types start with, then the kind. */
    public static final String PARAMETER_PREFIX = "parameter/";

    /** What Switch node types start with, then the pin type. */
    public static final String SWITCH_PREFIX = "switch/";

    /** Not made. */
    private ParameterNodes() {}

    /**
     * Add the parameter nodes to a library.
     *
     * @param library The library.
     * @return The library.
     */
    public static TextureLibrary register(@NonNull TextureLibrary library) {
        parameter(library, GraphParameter.Kind.SCALAR, "Scalar Parameter", "value", SCALAR);
        parameter(library, GraphParameter.Kind.COLOR, "Color Parameter", "color", COLOR);
        parameter(library, GraphParameter.Kind.SWITCH, "Switch Parameter", "value", MASK);
        parameter(library, GraphParameter.Kind.CHOICE, "Choice Parameter", "index", SCALAR);
        parameter(library, GraphParameter.Kind.SEED, "Seed Parameter", "value", SCALAR);
        final TextureParameter textureNode = new TextureParameter();
        library.register(textureNode.type(), textureNode);
        switchNode(library, SCALAR, "Switch");
        switchNode(library, COLOR, "Switch Color");
        return library;
    }

    /**
     * The Parameter node type of a kind.
     *
     * @param kind The kind.
     * @return The type's ID.
     */
    public static String typeOf(GraphParameter.@NonNull Kind kind) {
        return PARAMETER_PREFIX + kind.id;
    }

    /**
     * Register a Parameter node that makes one plane from the parameter's value.
     *
     * @param library The library.
     * @param kind The kind it reads.
     * @param title Its title.
     * @param output Its output's name.
     * @param outputType Its output's pin type.
     */
    private static void parameter(
            TextureLibrary library,
            GraphParameter.Kind kind,
            String title,
            String output,
            String outputType) {
        library.register(
                type(
                        typeOf(kind),
                        title,
                        PARAMETERS,
                        List.of(),
                        List.of(pin(output, outputType)),
                        List.of(param(NAME, ""))),
                new Reader(kind) {
                    @Override
                    void evaluate(NodeContext context, String value) {
                        final float[] fill =
                                switch (kind) {
                                    case COLOR -> GraphParameter.color(value);
                                    case SWITCH -> new float[] {"true".equals(value) ? 1 : 0};
                                    case CHOICE -> new float[] {choiceIndex(context, value)};
                                    case SEED ->
                                            new float[] {
                                                CoreNodes.unit(
                                                        Evaluator.mix(Long.parseLong(value.trim())))
                                            };
                                    default -> new float[] {Float.parseFloat(value.trim())};
                                };
                        context.output(
                                output,
                                Plane.filled(context.getWidth(), context.getHeight(), fill));
                    }
                });
    }

    /**
     * A choice's index among its options.
     *
     * @param context The node's context.
     * @param value The chosen option.
     * @return Its index.
     */
    private static float choiceIndex(NodeContext context, String value) {
        final String choices = context.text(Evaluator.READ_CHOICES);
        int index = 0;
        for (String choice : choices.split("\n", -1)) {
            if (choice.equals(value)) {
                return index;
            }
            ++index;
        }
        return 0;
    }

    /** A node reading a graph parameter of one kind, checking it is there and of that kind. */
    private abstract static class Reader implements TextureNode {
        /** The kind it reads. */
        private final GraphParameter.Kind kind;

        /**
         * Make the reader.
         *
         * @param kind The kind it reads.
         */
        Reader(GraphParameter.Kind kind) {
            this.kind = kind;
        }

        @Override
        public @Nullable String parameterRead(@NonNull Map<String, String> params) {
            final String name = params.get(NAME);
            return name == null || name.isBlank() ? null : name.trim();
        }

        @Override
        public void evaluate(@NonNull NodeContext context) {
            final String name = context.text(NAME).trim();
            if (name.isEmpty()) {
                throw new NodeException("Name the " + kind.id + " parameter to read");
            }
            final String read = context.text(Evaluator.READ_KIND);
            if (read.isEmpty()) {
                throw new NodeException("The graph has no parameter " + name);
            }
            if (!kind.id.equals(read)) {
                throw new NodeException(name + " is a " + read + ", not a " + kind.id);
            }
            try {
                evaluate(context, context.text(Evaluator.READ_VALUE));
            } catch (NumberFormatException e) {
                throw new NodeException(name + "'s value isn't a number");
            }
        }

        /**
         * Make the outputs from the parameter's value.
         *
         * @param context The node's context.
         * @param value The value.
         */
        abstract void evaluate(NodeContext context, String value);
    }

    /** Reads a texture parameter's image: its color at the tile's size, and the whole texture. */
    private static final class TextureParameter extends Reader {
        /** Make the node. */
        TextureParameter() {
            super(GraphParameter.Kind.TEXTURE);
        }

        /**
         * The node's type.
         *
         * @return The type.
         */
        NodeTypes.NodeType type() {
            return CoreNodes.type(
                    typeOf(GraphParameter.Kind.TEXTURE),
                    "Texture Parameter",
                    PARAMETERS,
                    List.of(),
                    List.of(pin("color", COLOR), pin("texture", TEXTURE)),
                    List.of(param(NAME, "")));
        }

        @Override
        void evaluate(NodeContext context, String value) {
            final BufferedImage image = CoreNodes.ImageNode.read(context.getFolder(), value);
            final Plane texture = new Plane(image.getWidth(), image.getHeight(), 4);
            for (int y = 0; y < image.getHeight(); ++y) {
                for (int x = 0; x < image.getWidth(); ++x) {
                    final int argb = image.getRGB(x, y);
                    texture.set(x, y, 0, Srgb.decode(argb >> 16 & 0xFF));
                    texture.set(x, y, 1, Srgb.decode(argb >> 8 & 0xFF));
                    texture.set(x, y, 2, Srgb.decode(argb & 0xFF));
                    texture.set(x, y, 3, (argb >>> 24) / (float) Srgb.MAX_BYTE);
                }
            }
            final Plane color = context.plane(4);
            for (int y = 0; y < context.getHeight(); ++y) {
                for (int x = 0; x < context.getWidth(); ++x) {
                    for (int c = 0; c < 4; ++c) {
                        color.set(
                                x,
                                y,
                                c,
                                texture.get(x % image.getWidth(), y % image.getHeight(), c));
                    }
                }
            }
            context.output("color", color);
            context.output("texture", texture);
        }

        @Override
        public String fingerprint(@NonNull Map<String, String> params, Path folder) {
            try {
                final Path file =
                        CoreNodes.ImageNode.resolve(
                                folder, params.getOrDefault(Evaluator.READ_VALUE, ""));
                if (file == null || !Files.isRegularFile(file)) {
                    return "missing";
                }
                return Long.toHexString(Evaluator.hashBytes(Files.readAllBytes(file)));
            } catch (IOException | InvalidPathException e) {
                return "unreadable";
            }
        }
    }

    /**
     * Register a Switch node: passes on its "on" input while a switch parameter is on, else its
     * "off" input, and never evaluates the one it doesn't pass on.
     *
     * @param library The library.
     * @param pinType The type it switches.
     * @param title Its title.
     */
    private static void switchNode(TextureLibrary library, String pinType, String title) {
        library.register(
                type(
                        SWITCH_PREFIX + pinType,
                        title,
                        PARAMETERS,
                        List.of(pin(ON, pinType), pin(OFF, pinType)),
                        List.of(pin("result", pinType)),
                        List.of(param(NAME, ""))),
                new Reader(GraphParameter.Kind.SWITCH) {
                    @Override
                    void evaluate(NodeContext context, String value) {
                        final String chosen = "true".equals(value) ? ON : OFF;
                        context.output("result", context.input(chosen));
                    }

                    @Override
                    public Set<String> unused(@NonNull Map<String, String> params) {
                        if (params.get(Evaluator.READ_KIND) == null) {
                            return Set.of();
                        }
                        return Set.of("true".equals(params.get(Evaluator.READ_VALUE)) ? OFF : ON);
                    }
                });
    }
}
