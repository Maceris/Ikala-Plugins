package com.ikalagaming.converter.texgraph;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.Map;

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
