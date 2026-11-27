package com.ikalagaming.converter.texgraph;

import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import lombok.Getter;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * Evaluates texture graphs on the CPU, node by node as their outputs are asked for. Each node's
 * outputs are cached by a key made from its type, parameters, the graph's size, its seed and the
 * keys of what is linked into it, so after an edit only the nodes downstream of it run again. Not
 * thread safe; one evaluator per graph being edited.
 */
public final class Evaluator {
    /** The key of a node that couldn't be evaluated, which nothing downstream caches against. */
    private static final long FAILED = 0;

    /** The library the nodes come from. */
    private final TextureLibrary library;

    /** Each node's last outputs, by node ID. */
    private final Map<String, Cached> cache = new HashMap<>();

    /**
     * Each node's error from the last evaluation, by node ID. -- GETTER -- The errors.
     *
     * @return The errors, by node ID.
     */
    @Getter private final Map<String, String> errors = new LinkedHashMap<>();

    /**
     * How many nodes ran, rather than came from the cache, since this evaluator was made. -- GETTER
     * -- How many nodes ran.
     *
     * @return The count.
     */
    @Getter private long runs;

    /**
     * A node's cached outputs.
     *
     * @param key The key they were made with.
     * @param outputs The outputs, by pin.
     */
    private record Cached(long key, Map<String, Plane> outputs) {}

    /**
     * What a graph is evaluated with.
     *
     * @param width Pixels across.
     * @param height Pixels down.
     * @param seed The graph's seed, which each node's seed is made from.
     * @param folder Where relative file names start, or null when the graph isn't saved.
     */
    public record Settings(int width, int height, long seed, @Nullable Path folder) {
        /**
         * Check the settings.
         *
         * @throws IllegalArgumentException If a size isn't positive.
         */
        public Settings {
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException(
                        "A texture is at least 1x1, not " + width + "x" + height);
            }
        }
    }

    /**
     * Make an evaluator.
     *
     * @param library The node types and what they do.
     */
    public Evaluator(@NonNull TextureLibrary library) {
        this.library = library;
    }

    /**
     * One evaluation: keys worked out so far and nodes being visited, to catch loops.
     *
     * @param graph The graph.
     * @param settings The settings.
     * @param keys Each node's key, worked out once a pass.
     * @param visiting The nodes being evaluated, outermost first.
     */
    private record Pass(
            Graph graph, Settings settings, Map<String, Long> keys, Set<String> visiting) {}

    /**
     * A node's output.
     *
     * @param graph The graph.
     * @param settings The size, seed and folder.
     * @param node The node.
     * @param pin The output.
     * @return The plane, or null if the node couldn't be evaluated (see {@link #getErrors}).
     */
    public @Nullable Plane output(
            @NonNull Graph graph,
            @NonNull Settings settings,
            @NonNull String node,
            @NonNull String pin) {
        final Pass pass = new Pass(graph, settings, new HashMap<>(), new HashSet<>());
        final Map<String, Plane> outputs = evaluate(pass, node);
        return outputs == null ? null : outputs.get(pin);
    }

    /**
     * What a node's input reads, converted to the input's type, as the node would see it; for nodes
     * like the Output that the evaluator doesn't run.
     *
     * @param graph The graph.
     * @param settings The size, seed and folder.
     * @param node The node.
     * @param pin The input.
     * @return The plane, or null if nothing is linked into it or what is couldn't be evaluated.
     */
    public @Nullable Plane input(
            @NonNull Graph graph,
            @NonNull Settings settings,
            @NonNull String node,
            @NonNull String pin) {
        final Pass pass = new Pass(graph, settings, new HashMap<>(), new HashSet<>());
        return read(pass, node, pin);
    }

    /** Forget every cached output, as when the files a graph reads may have changed. */
    public void clear() {
        cache.clear();
    }

    /**
     * Where an input's value comes from: the output it is linked to, followed back through reroutes
     * and named reroutes.
     *
     * @param graph The graph.
     * @param node The node.
     * @param pin The input.
     * @return The real output's node and pin, or null if it leads to nothing.
     */
    static @Nullable String[] source(Graph graph, String node, String pin) {
        final Set<String> seen = new HashSet<>();
        Graph.Link link = graph.linkInto(node, pin);
        while (link != null) {
            final Graph.Node from = graph.node(link.fromNode());
            if (from == null || !seen.add(from.getId())) {
                return null;
            }
            switch (from.getType()) {
                case NodeTypes.REROUTE -> link = graph.linkInto(from.getId(), "in");
                case NodeTypes.RECEIVE -> {
                    final Graph.Node send =
                            sendFor(graph, graph.param(from.getId(), NodeTypes.NAME));
                    link = send == null ? null : graph.linkInto(send.getId(), "in");
                }
                default -> {
                    return new String[] {link.fromNode(), link.fromPin()};
                }
            }
        }
        return null;
    }

    /**
     * The send node with a name.
     *
     * @param graph The graph.
     * @param name The name.
     * @return The first such send, by ID, or null.
     */
    private static @Nullable Graph.Node sendFor(Graph graph, @Nullable String name) {
        Graph.Node found = null;
        for (Graph.Node node : graph.nodes()) {
            if (NodeTypes.SEND.equals(node.getType())
                    && name != null
                    && name.equals(graph.param(node.getId(), NodeTypes.NAME))
                    && (found == null || node.getId().compareTo(found.getId()) < 0)) {
                found = node;
            }
        }
        return found;
    }

    /**
     * Read an input in a pass.
     *
     * @param pass The pass.
     * @param node The node.
     * @param pin The input.
     * @return The converted plane, or null if unlinked or failed.
     */
    private @Nullable Plane read(Pass pass, String node, String pin) {
        final String[] from = source(pass.graph(), node, pin);
        if (from == null) {
            return null;
        }
        final Map<String, Plane> outputs = evaluate(pass, from[0]);
        if (outputs == null || outputs.get(from[1]) == null) {
            return null;
        }
        final NodeTypes.Pin target = pass.graph().inputPin(node, pin);
        final NodeTypes.Pin made = pass.graph().outputPin(from[0], from[1]);
        if (target == null || made == null) {
            return outputs.get(from[1]);
        }
        return TexturePins.convert(outputs.get(from[1]), made.type(), target.type());
    }

    /**
     * A node's outputs, from the cache or by running it.
     *
     * @param pass The pass.
     * @param id The node.
     * @return The outputs, or null if it couldn't be evaluated.
     */
    private @Nullable Map<String, Plane> evaluate(Pass pass, String id) {
        final long key = keyOf(pass, id);
        if (key == FAILED) {
            return null;
        }
        final Cached cached = cache.get(id);
        if (cached != null && cached.key() == key) {
            return cached.outputs();
        }
        final Graph graph = pass.graph();
        final NodeTypes.NodeType type = graph.typeOf(id);
        final TextureNode node = type == null ? null : library.get(type.id());
        if (node == null) {
            errors.put(id, "This node doesn't make a texture");
            return null;
        }
        // An input that failed has its own error; this node reads it as failed too
        for (NodeTypes.Pin input : type.inputs()) {
            final String[] from = source(graph, id, input.name());
            if (from != null && evaluate(pass, from[0]) == null) {
                errors.put(id, "Input " + input.name() + " has an error");
                return null;
            }
        }
        final Settings settings = pass.settings();
        final NodeContext context =
                new NodeContext(
                        type,
                        settings.width(),
                        settings.height(),
                        nodeSeed(settings.seed(), id),
                        settings.folder(),
                        paramsOf(graph, id, type),
                        pin -> read(pass, id, pin));
        try {
            node.evaluate(context);
        } catch (TextureNode.NodeException e) {
            errors.put(id, e.getMessage());
            cache.remove(id);
            return null;
        }
        ++runs;
        errors.remove(id);
        final Map<String, Plane> outputs = Collections.unmodifiableMap(context.outputs());
        cache.put(id, new Cached(key, outputs));
        return outputs;
    }

    /**
     * A node's key: a hash of everything its outputs depend on.
     *
     * @param pass The pass.
     * @param id The node.
     * @return The key, or {@link #FAILED} for a node in a loop or of an unknown type.
     */
    private long keyOf(Pass pass, String id) {
        final Long known = pass.keys().get(id);
        if (known != null) {
            return known;
        }
        final Graph graph = pass.graph();
        final NodeTypes.NodeType type = graph.typeOf(id);
        if (type == null) {
            return FAILED;
        }
        if (!pass.visiting().add(id)) {
            errors.put(id, "This node is in a loop");
            return FAILED;
        }
        final Settings settings = pass.settings();
        final Map<String, String> params = paramsOf(graph, id, type);
        final TextureNode node = library.get(type.id());
        final StringBuilder text =
                new StringBuilder(type.id())
                        .append('/')
                        .append(type.version())
                        .append('/')
                        .append(settings.width())
                        .append('x')
                        .append(settings.height())
                        .append('/')
                        .append(nodeSeed(settings.seed(), id))
                        .append('/')
                        .append(params)
                        .append('/')
                        .append(node == null ? "" : node.fingerprint(params, settings.folder()));
        long key = hash(text.toString());
        for (NodeTypes.Pin input : type.inputs()) {
            final String[] from = source(graph, id, input.name());
            long inputKey = 1;
            if (from != null) {
                inputKey = keyOf(pass, from[0]);
                if (inputKey == FAILED) {
                    pass.visiting().remove(id);
                    pass.keys().put(id, FAILED);
                    return FAILED;
                }
                inputKey = mix(inputKey ^ hash(from[1]));
            }
            key = mix(key * 31 + inputKey);
        }
        pass.visiting().remove(id);
        if (key == FAILED) {
            key = 1;
        }
        pass.keys().put(id, key);
        return key;
    }

    /**
     * A node's parameters with the defaults of those not set, sorted by name.
     *
     * @param graph The graph.
     * @param id The node.
     * @param type Its type.
     * @return The parameters.
     */
    private static Map<String, String> paramsOf(Graph graph, String id, NodeTypes.NodeType type) {
        final Map<String, String> params = new TreeMap<>();
        for (NodeTypes.Param param : type.params()) {
            params.put(param.name(), graph.param(id, param.name()));
        }
        return params;
    }

    /**
     * A node's seed: the graph's seed mixed with the node's ID, so adding a node doesn't change the
     * others.
     *
     * @param graphSeed The graph's seed.
     * @param id The node's ID.
     * @return The seed.
     */
    public static long nodeSeed(long graphSeed, @NonNull String id) {
        return mix(graphSeed ^ hash(id));
    }

    /**
     * A 64-bit hash of text: FNV-1a over its UTF-8 bytes, then mixed.
     *
     * @param text The text.
     * @return The hash.
     */
    static long hash(@NonNull String text) {
        return hashBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A 64-bit hash of bytes: FNV-1a, then mixed.
     *
     * @param bytes The bytes.
     * @return The hash.
     */
    static long hashBytes(byte @NonNull [] bytes) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : bytes) {
            hash ^= b & 0xFF;
            hash *= 0x100000001b3L;
        }
        return mix(hash);
    }

    /**
     * Scramble the bits of a number, SplitMix64's finalizer.
     *
     * @param value The number.
     * @return The scrambled number.
     */
    static long mix(long value) {
        long z = value + 0x9e3779b97f4a7c15L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
