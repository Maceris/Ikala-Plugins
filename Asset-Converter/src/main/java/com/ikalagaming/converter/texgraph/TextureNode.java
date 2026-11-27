package com.ikalagaming.converter.texgraph;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * What a texture graph node does: reads its inputs and parameters and writes its outputs, every
 * pixel at once, on the CPU. Nodes must be deterministic: the same inputs, parameters, size and
 * seed always give the same outputs, which is what lets the evaluator cache them and bakes match
 * byte for byte.
 */
@FunctionalInterface
public interface TextureNode {
    /**
     * Work out the node's outputs.
     *
     * @param context The inputs, parameters and size, and where the outputs go.
     * @throws NodeException If the node can't be evaluated, such as a missing file.
     */
    void evaluate(@NonNull NodeContext context);

    /**
     * Something that changes when what the node reads from outside the graph changes, such as an
     * image file's bytes, so the cache knows to evaluate it again. Most nodes read nothing outside
     * and give an empty string.
     *
     * @param params The node's parameters, with defaults filled in.
     * @param folder The folder the graph's relative file names start from, or null.
     * @return The fingerprint.
     */
    default String fingerprint(@NonNull Map<String, String> params, Path folder) {
        return "";
    }

    /**
     * The graph parameter the node reads, whose kind and value the evaluator then adds to its
     * parameters as {@link Evaluator#READ_KIND} and {@link Evaluator#READ_VALUE}, so a change to it
     * evaluates the node again.
     *
     * @param params The node's own parameters.
     * @return The graph parameter's name, or null for none.
     */
    default @Nullable String parameterRead(@NonNull Map<String, String> params) {
        return null;
    }

    /**
     * Inputs the node won't read with these parameters, which are then neither evaluated nor part
     * of its key, like the branch a switch turns off.
     *
     * @param params The node's parameters, with what it reads of the graph's.
     * @return The inputs' names.
     */
    default Set<String> unused(@NonNull Map<String, String> params) {
        return Set.of();
    }

    /** Why a node couldn't be evaluated, shown on the node. */
    final class NodeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Make the exception.
         *
         * @param message What is wrong, for the node's error.
         */
        public NodeException(String message) {
            super(message);
        }
    }
}
