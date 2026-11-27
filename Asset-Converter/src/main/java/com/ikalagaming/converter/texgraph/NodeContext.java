package com.ikalagaming.converter.texgraph;

import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import lombok.Getter;
import lombok.NonNull;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * What a node sees while it is evaluated: its inputs, already converted to its pins' types, its
 * parameters, the graph's size and its own seed, and where its outputs go.
 */
public final class NodeContext {
    /**
     * The node's type. -- GETTER -- The node's type.
     *
     * @return The type.
     */
    @Getter private final NodeTypes.NodeType type;

    /**
     * Pixels across, the graph's width. -- GETTER -- Pixels across.
     *
     * @return The width.
     */
    @Getter private final int width;

    /**
     * Pixels down, the graph's height. -- GETTER -- Pixels down.
     *
     * @return The height.
     */
    @Getter private final int height;

    /**
     * The node's seed, from the graph's seed and the node's ID. -- GETTER -- The node's seed.
     *
     * @return The seed.
     */
    @Getter private final long seed;

    /**
     * The folder relative file names start from, or null. -- GETTER -- The folder.
     *
     * @return The folder, or null when the graph isn't saved anywhere.
     */
    @Getter private final @Nullable Path folder;

    /** The parameters, with defaults filled in. */
    private final Map<String, String> params;

    /** Gives an input's plane, or null if nothing is linked into it. */
    private final Function<String, Plane> inputs;

    /** The outputs, by pin. */
    private final Map<String, Plane> outputs = new HashMap<>();

    /**
     * Set up the context.
     *
     * @param type The node's type.
     * @param width Pixels across.
     * @param height Pixels down.
     * @param seed The node's seed.
     * @param folder Where relative file names start, or null.
     * @param params The parameters, with defaults filled in.
     * @param inputs Gives an input's plane, converted, or null if nothing is linked into it.
     */
    NodeContext(
            NodeTypes.NodeType type,
            int width,
            int height,
            long seed,
            @Nullable Path folder,
            Map<String, String> params,
            Function<String, Plane> inputs) {
        this.type = type;
        this.width = width;
        this.height = height;
        this.seed = seed;
        this.folder = folder;
        this.params = params;
        this.inputs = inputs;
    }

    /**
     * Whether something is linked into an input.
     *
     * @param name The input.
     * @return True if linked.
     */
    public boolean linked(@NonNull String name) {
        return inputs.apply(name) != null;
    }

    /**
     * An input, as its pin's type: what is linked into it, or zero (opaque black for a color) if
     * nothing is.
     *
     * @param name The input.
     * @return The plane.
     * @throws IllegalArgumentException If the node has no such input.
     */
    public Plane input(@NonNull String name) {
        final NodeTypes.Pin pin = type.input(name);
        if (pin == null) {
            throw new IllegalArgumentException(type.id() + " has no input " + name);
        }
        final Plane plane = inputs.apply(name);
        return plane != null ? plane : TexturePins.unlinked(pin.type(), width, height);
    }

    /**
     * A parameter, as text.
     *
     * @param name The parameter.
     * @return Its value.
     * @throws IllegalArgumentException If the node has no such parameter.
     */
    public String text(@NonNull String name) {
        final String value = params.get(name);
        if (value == null) {
            throw new IllegalArgumentException(type.id() + " has no parameter " + name);
        }
        return value;
    }

    /**
     * A parameter, as a number.
     *
     * @param name The parameter.
     * @return Its value.
     * @throws TextureNode.NodeException If it isn't a number.
     */
    public float number(@NonNull String name) {
        final String value = text(name).trim();
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new TextureNode.NodeException(name + " isn't a number: " + value);
        }
    }

    /**
     * A parameter, as a whole number.
     *
     * @param name The parameter.
     * @return Its value.
     * @throws TextureNode.NodeException If it isn't a whole number.
     */
    public int integer(@NonNull String name) {
        final String value = text(name).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new TextureNode.NodeException(name + " isn't a whole number: " + value);
        }
    }

    /**
     * A parameter, as a switch: true, yes, on or 1.
     *
     * @param name The parameter.
     * @return Its value.
     */
    public boolean flag(@NonNull String name) {
        return switch (text(name).trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            default -> false;
        };
    }

    /**
     * Set an output.
     *
     * @param name The output.
     * @param plane Its plane, with as many channels as the pin's type carries.
     * @throws IllegalArgumentException If the node has no such output, or the plane doesn't fit.
     */
    public void output(@NonNull String name, @NonNull Plane plane) {
        final NodeTypes.Pin pin = type.output(name);
        if (pin == null) {
            throw new IllegalArgumentException(type.id() + " has no output " + name);
        }
        if (plane.getChannels() != TexturePins.channels(pin.type())) {
            throw new IllegalArgumentException(
                    type.id() + "." + name + " is a " + pin.type() + ", not " + plane);
        }
        outputs.put(name, plane);
    }

    /**
     * A new plane at the graph's size.
     *
     * @param channels Floats per pixel.
     * @return The plane, zeros.
     */
    public Plane plane(int channels) {
        return new Plane(width, height, channels);
    }

    /**
     * The outputs set.
     *
     * @return The outputs, by pin.
     */
    Map<String, Plane> outputs() {
        return outputs;
    }
}
