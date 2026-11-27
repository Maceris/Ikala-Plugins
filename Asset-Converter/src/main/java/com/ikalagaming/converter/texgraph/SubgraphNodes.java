package com.ikalagaming.converter.texgraph;

import static com.ikalagaming.converter.texgraph.CoreNodes.param;
import static com.ikalagaming.converter.texgraph.CoreNodes.pin;
import static com.ikalagaming.converter.texgraph.TexturePins.COLOR;
import static com.ikalagaming.converter.texgraph.TexturePins.COORDINATES;
import static com.ikalagaming.converter.texgraph.TexturePins.HEIGHT;
import static com.ikalagaming.converter.texgraph.TexturePins.MASK;
import static com.ikalagaming.converter.texgraph.TexturePins.PALETTE;
import static com.ikalagaming.converter.texgraph.TexturePins.SCALAR;
import static com.ikalagaming.converter.texgraph.TexturePins.TEXTURE;
import static com.ikalagaming.converter.texgraph.TexturePins.VECTOR;

import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import lombok.NonNull;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * Subgraphs: a texture graph placed in another as one node. The placed graph's Input nodes become
 * the node's inputs and its Output nodes its outputs, top to bottom as they stand, and its own
 * graph parameters the node's parameters. A graph names a subgraph by its file's path relative to
 * the graph's own file, which is the node's type, like {@code subgraph:../subgraphs/mortar.texgraph
 * .yml}, so subgraphs are sources beside the graphs and never ship.
 *
 * <p>A subgraph node evaluates the placed graph at the outer graph's size and seed (moved by its
 * seed variation), with its inputs fed to the Input nodes. Each graph parameter the placed graph
 * reads is, in order: the node's own value for it, if set; the outer graph's parameter of that
 * name, if it has one of that kind; the placed graph's own value. So a subgraph made from part of a
 * graph reads the same parameters it did and makes the same pixels.
 *
 * <p>A link to a pin the placed graph no longer has, or a value for a parameter it no longer has,
 * stays and shows as an error on the node, never as a silent unlink.
 */
public final class SubgraphNodes {
    /** Where placed subgraphs and the Input and Output nodes are listed. */
    public static final String CATEGORY = "Subgraphs";

    /** What a placed subgraph's node type starts with, then the path to its file. */
    public static final String TYPE_PREFIX = "subgraph:";

    /** What Input node types start with, then the pin type. */
    public static final String INPUT_PREFIX = "subgraph/input/";

    /** What Output node types start with, then the pin type. */
    public static final String OUTPUT_PREFIX = "subgraph/output/";

    /** The Input and Output nodes' parameter naming the pin they make. */
    public static final String NAME = "name";

    /** A placed subgraph's parameter moving its seed, so two placements can differ. */
    public static final String VARIATION = "seed variation";

    /** An Input node's output. */
    public static final String INPUT_OUT = "value";

    /** An Output node's input. */
    public static final String OUTPUT_IN = "value";

    /** The pin types subgraphs can take and give. */
    public static final List<String> PIN_TYPES =
            List.of(SCALAR, MASK, HEIGHT, COLOR, VECTOR, COORDINATES, PALETTE, TEXTURE);

    /** The subgraph files being evaluated or read on this thread, to catch one placing itself. */
    private static final ThreadLocal<Deque<Path>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);

    /** Subgraph files read, by file, kept while none of the files they read change. */
    private static final Map<Path, Loaded> LOADED = new ConcurrentHashMap<>();

    /** Not made. */
    private SubgraphNodes() {}

    /**
     * What a graph shows when placed as a subgraph.
     *
     * @param inputs Its Input nodes' pins, top to bottom.
     * @param outputs Its Output nodes' pins, top to bottom.
     * @param parameters Its own graph parameters, which the node offers.
     * @param reads Every graph parameter it can read, its shared files' too.
     * @param problems What is wrong with it as a subgraph, like two inputs with one name.
     */
    public record Interface(
            @NonNull List<NodeTypes.Pin> inputs,
            @NonNull List<NodeTypes.Pin> outputs,
            @NonNull List<GraphParameter> parameters,
            @NonNull Set<String> reads,
            @NonNull List<String> problems) {
        /** Copy the lists. */
        public Interface {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            parameters = List.copyOf(parameters);
            reads = Set.copyOf(reads);
            problems = List.copyOf(problems);
        }
    }

    /**
     * A subgraph file read, with what it read, so it is read again only once one of them changes.
     *
     * @param graph The graph, or null if it couldn't be read.
     * @param error Why it couldn't be read, or null.
     * @param face What it shows when placed.
     * @param files Each file it read, with its size and time when read.
     */
    private record Loaded(
            @Nullable TextureGraph graph,
            @Nullable String error,
            Interface face,
            Map<Path, String> files) {
        /**
         * Whether none of the files it read have changed.
         *
         * @return True if it can be used as it is.
         */
        boolean current() {
            for (Map.Entry<Path, String> file : files.entrySet()) {
                if (!stamp(file.getKey()).equals(file.getValue())) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Add the Input and Output node types to a library.
     *
     * @param library The library.
     * @return The library.
     */
    public static TextureLibrary register(@NonNull TextureLibrary library) {
        for (String type : PIN_TYPES) {
            final String title = title(type);
            library.register(
                    CoreNodes.type(
                            INPUT_PREFIX + type,
                            "Input " + title,
                            CATEGORY,
                            List.of(),
                            List.of(pin(INPUT_OUT, type)),
                            List.of(param(NAME, "in"))),
                    context -> {
                        final String name = context.text(NAME).trim();
                        if (name.isEmpty()) {
                            throw new TextureNode.NodeException("Name the input");
                        }
                        final Plane fed = context.graphInput(name);
                        context.output(
                                INPUT_OUT,
                                fed != null
                                        ? fed
                                        : TexturePins.unlinked(
                                                type, context.getWidth(), context.getHeight()));
                    });
            library.register(
                    CoreNodes.type(
                            OUTPUT_PREFIX + type,
                            "Output " + title,
                            CATEGORY,
                            List.of(pin(OUTPUT_IN, type)),
                            List.of(),
                            List.of(param(NAME, "out"))),
                    context -> {
                        if (context.text(NAME).isBlank()) {
                            throw new TextureNode.NodeException("Name the output");
                        }
                    });
        }
        return library;
    }

    /**
     * A pin type's name as titles show it.
     *
     * @param type The pin type.
     * @return Its name, capitalized.
     */
    private static String title(String type) {
        return Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }

    /**
     * The node type a graph places a subgraph file with.
     *
     * @param folder The placing graph's folder.
     * @param file The subgraph file.
     * @return The type's ID, with the path relative to the folder, in forward slashes.
     */
    public static String typeFor(@NonNull Path folder, @NonNull Path file) {
        final Path relative =
                folder.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize());
        return TYPE_PREFIX + relative.toString().replace('\\', '/');
    }

    /**
     * The file a subgraph node type places.
     *
     * @param folder The placing graph's folder.
     * @param type The type's ID.
     * @return The file, absolute, or null if the type isn't a subgraph's or its path is bad.
     */
    public static @Nullable Path fileOf(@Nullable Path folder, @NonNull String type) {
        if (folder == null || !type.startsWith(TYPE_PREFIX)) {
            return null;
        }
        try {
            return folder.resolve(type.substring(TYPE_PREFIX.length()))
                    .toAbsolutePath()
                    .normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * What a graph shows when placed as a subgraph.
     *
     * @param texture The graph.
     * @param folder Its file's folder, for its shared parameter files, or null.
     * @return Its pins, parameters and problems.
     */
    public static Interface interfaceOf(@NonNull TextureGraph texture, @Nullable Path folder) {
        final Graph graph = texture.getGraph();
        final List<String> problems = new ArrayList<>();
        final List<Graph.Node> nodes = new ArrayList<>(graph.nodes());
        nodes.sort(
                Comparator.comparingDouble(Graph.Node::getY)
                        .thenComparingDouble(Graph.Node::getX)
                        .thenComparing(Graph.Node::getId));
        final List<NodeTypes.Pin> inputs = new ArrayList<>();
        final List<NodeTypes.Pin> outputs = new ArrayList<>();
        for (Graph.Node node : nodes) {
            final boolean input = node.getType().startsWith(INPUT_PREFIX);
            final boolean output = node.getType().startsWith(OUTPUT_PREFIX);
            if (!input && !output) {
                continue;
            }
            final String name = String.valueOf(graph.param(node.getId(), NAME)).trim();
            final String type =
                    node.getType().substring((input ? INPUT_PREFIX : OUTPUT_PREFIX).length());
            final List<NodeTypes.Pin> pins = input ? inputs : outputs;
            if (name.isEmpty()) {
                problems.add("An " + (input ? "Input" : "Output") + " node has no name");
            } else if (pins.stream().anyMatch(p -> p.name().equals(name))) {
                problems.add("Two " + (input ? "inputs" : "outputs") + " are named " + name);
            } else {
                pins.add(new NodeTypes.Pin(name, type));
            }
        }
        final Set<String> reads =
                new LinkedHashSet<>(texture.resolveParameters(folder, problems).keySet());
        return new Interface(inputs, outputs, texture.getParameters(), reads, problems);
    }

    /**
     * Register the node types of the subgraphs a graph's text places, before the graph is read, so
     * its nodes and links find them. Pins and parameters the text uses that a subgraph no longer
     * has are kept on the type, so those links and values stay and show as errors.
     *
     * @param text The graph's text.
     * @param folder The graph file's folder, or null for an unsaved graph, whose subgraphs can't be
     *     found.
     * @param library Where to register them.
     */
    public static void prepare(
            @NonNull String text, @Nullable Path folder, TextureLibrary library) {
        final Object document;
        try {
            document = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        } catch (RuntimeException e) {
            return;
        }
        if (!(document instanceof Map<?, ?> root)
                || !(root.get("nodes") instanceof Map<?, ?> nodes)) {
            return;
        }
        // What each placed subgraph's nodes use, by type
        final Map<String, String> typeOf = new HashMap<>();
        final Map<String, Uses> uses = new LinkedHashMap<>();
        nodes.forEach(
                (id, value) -> {
                    if (value instanceof Map<?, ?> node
                            && node.get("type") instanceof String type
                            && type.startsWith(TYPE_PREFIX)) {
                        typeOf.put(String.valueOf(id), type);
                        final Uses used = uses.computeIfAbsent(type, t -> new Uses());
                        if (node.get("set") instanceof Map<?, ?> set) {
                            set.keySet().forEach(key -> used.params.add(String.valueOf(key)));
                        }
                    }
                });
        if (root.get("links") instanceof List<?> links) {
            for (Object entry : links) {
                final String link = String.valueOf(entry);
                final int arrow = link.indexOf(" > ");
                if (arrow < 0) {
                    continue;
                }
                final String from = link.substring(0, arrow).trim();
                final String to = link.substring(arrow + 3).trim();
                use(from, to, typeOf, uses, nodes, library, true);
                use(to, from, typeOf, uses, nodes, library, false);
            }
        }
        for (Map.Entry<String, Uses> entry : uses.entrySet()) {
            place(entry.getKey(), folder, library, entry.getValue());
        }
    }

    /**
     * The pins and parameters a graph uses of one placed subgraph, with each pin's type as the
     * other end of its link has it.
     */
    private static final class Uses {
        /** Inputs linked into, with the type linked from. */
        final Map<String, String> inputs = new LinkedHashMap<>();

        /** Outputs linked from, with the type linked to. */
        final Map<String, String> outputs = new LinkedHashMap<>();

        /** Parameters given values. */
        final Set<String> params = new LinkedHashSet<>();
    }

    /**
     * Note one end of a link, if it is a placed subgraph's pin.
     *
     * @param end The end, {@code node.pin}.
     * @param other The link's other end, whose pin type the end takes if the subgraph lost it.
     * @param typeOf Placed subgraphs' types, by node ID.
     * @param uses What each type's nodes use.
     * @param nodes The text's nodes, for the other end's type.
     * @param library The library, for the other end's pin type.
     * @param output Whether the end is the link's start, an output.
     */
    private static void use(
            String end,
            String other,
            Map<String, String> typeOf,
            Map<String, Uses> uses,
            Map<?, ?> nodes,
            TextureLibrary library,
            boolean output) {
        final int dot = end.indexOf('.');
        if (dot < 0 || !typeOf.containsKey(end.substring(0, dot))) {
            return;
        }
        final Uses used = uses.get(typeOf.get(end.substring(0, dot)));
        String pinType = SCALAR;
        final int otherDot = other.indexOf('.');
        if (otherDot > 0
                && nodes.get(other.substring(0, otherDot)) instanceof Map<?, ?> node
                && node.get("type") instanceof String otherType) {
            final NodeTypes.NodeType known = library.getNodeTypes().get(otherType);
            final String otherPin = other.substring(otherDot + 1);
            final NodeTypes.Pin pin =
                    known == null ? null : output ? known.input(otherPin) : known.output(otherPin);
            if (pin != null) {
                pinType = pin.type();
            }
        }
        (output ? used.outputs : used.inputs).putIfAbsent(end.substring(dot + 1), pinType);
    }

    /**
     * Register a placed subgraph's node type.
     *
     * @param type The type's ID.
     * @param folder The placing graph's folder, or null.
     * @param library Where to register it.
     * @param used What the placing graph uses of it.
     */
    private static void place(
            String type, @Nullable Path folder, TextureLibrary library, Uses used) {
        final Path file = fileOf(folder, type);
        final Loaded loaded = file == null ? null : load(file);
        final String error;
        if (folder == null) {
            error = "Save the graph to place subgraphs";
        } else if (loaded == null) {
            error = "The subgraph's path isn't a file name";
        } else {
            error = loaded.error();
        }
        final Interface face =
                loaded == null
                        ? new Interface(List.of(), List.of(), List.of(), Set.of(), List.of())
                        : loaded.face();
        library.register(nodeType(type, face, used), new Placed(file, type, face, error));
    }

    /**
     * Register a placed subgraph's node type again from its file, keeping the pins and parameters a
     * graph still uses that it no longer has, as after the file changed.
     *
     * @param type The type's ID.
     * @param folder The placing graph's folder.
     * @param library Where it is registered.
     * @param graph The placing graph, whose links and values are kept.
     */
    public static void refresh(
            @NonNull String type,
            @Nullable Path folder,
            @NonNull TextureLibrary library,
            @NonNull Graph graph) {
        final Uses used = new Uses();
        for (Graph.Node node : graph.nodes()) {
            if (type.equals(node.getType())) {
                used.params.addAll(node.getParams().keySet());
            }
        }
        for (Graph.Link link : graph.links()) {
            final Graph.Node from = graph.node(link.fromNode());
            final Graph.Node to = graph.node(link.toNode());
            if (from != null && type.equals(from.getType())) {
                final NodeTypes.Pin other = graph.inputPin(link.toNode(), link.toPin());
                used.outputs.putIfAbsent(link.fromPin(), other == null ? SCALAR : other.type());
            }
            if (to != null && type.equals(to.getType())) {
                final NodeTypes.Pin other = graph.outputPin(link.fromNode(), link.fromPin());
                used.inputs.putIfAbsent(link.toPin(), other == null ? SCALAR : other.type());
            }
        }
        place(type, folder, library, used);
    }

    /**
     * A placed subgraph's node type: its pins and parameters, then those the placing graph still
     * uses that it lost.
     *
     * @param type The type's ID.
     * @param face What the subgraph shows.
     * @param used What the placing graph uses.
     * @return The node type.
     */
    private static NodeTypes.NodeType nodeType(String type, Interface face, Uses used) {
        final List<NodeTypes.Pin> inputs = new ArrayList<>(face.inputs());
        used.inputs.forEach(
                (name, pinType) -> {
                    if (inputs.stream().noneMatch(p -> p.name().equals(name))) {
                        inputs.add(new NodeTypes.Pin(name, pinType));
                    }
                });
        final List<NodeTypes.Pin> outputs = new ArrayList<>(face.outputs());
        used.outputs.forEach(
                (name, pinType) -> {
                    if (outputs.stream().noneMatch(p -> p.name().equals(name))) {
                        outputs.add(new NodeTypes.Pin(name, pinType));
                    }
                });
        final List<NodeTypes.Param> params = new ArrayList<>();
        params.add(param(VARIATION, "0"));
        for (GraphParameter parameter : face.parameters()) {
            params.add(param(parameter.name(), ""));
        }
        for (String name : used.params) {
            if (params.stream().noneMatch(p -> p.name().equals(name))) {
                params.add(param(name, ""));
            }
        }
        return CoreNodes.type(type, titleOf(type), CATEGORY, inputs, outputs, params);
    }

    /**
     * A placed subgraph's title: its file's name without the suffix.
     *
     * @param type The type's ID.
     * @return The title.
     */
    public static String titleOf(@NonNull String type) {
        String name = type.substring(type.lastIndexOf('/') + 1);
        if (name.startsWith(TYPE_PREFIX)) {
            name = name.substring(TYPE_PREFIX.length());
        }
        return name.endsWith(TextureGraph.SUFFIX)
                ? name.substring(0, name.length() - TextureGraph.SUFFIX.length())
                : name;
    }

    /**
     * Read a subgraph file, or take it from the files read if none of what it read changed.
     *
     * @param file The file, absolute.
     * @return It as read.
     */
    private static Loaded load(Path file) {
        final Deque<Path> active = ACTIVE.get();
        if (active.contains(file)) {
            return new Loaded(
                    null,
                    "The subgraph places itself",
                    new Interface(List.of(), List.of(), List.of(), Set.of(), List.of()),
                    Map.of());
        }
        final Loaded known = LOADED.get(file);
        if (known != null && known.current()) {
            return known;
        }
        active.push(file);
        try {
            final Loaded read = read(file);
            LOADED.put(file, read);
            return read;
        } finally {
            active.pop();
        }
    }

    /**
     * Read a subgraph file, and the subgraphs it places.
     *
     * @param file The file.
     * @return It as read.
     */
    private static Loaded read(Path file) {
        final Map<Path, String> files = new LinkedHashMap<>();
        files.put(file, stamp(file));
        final Interface none = new Interface(List.of(), List.of(), List.of(), Set.of(), List.of());
        final String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            return new Loaded(null, "Can't read the subgraph " + file.getFileName(), none, files);
        }
        final TextureGraph graph = new TextureGraph(TextureLibraries.standard());
        try {
            graph.read(text, file.getParent());
        } catch (IllegalArgumentException e) {
            return new Loaded(null, file.getFileName() + ": " + e.getMessage(), none, files);
        }
        for (String shared : graph.getShared()) {
            final Path sharedFile = file.getParent().resolve(shared).toAbsolutePath().normalize();
            files.put(sharedFile, stamp(sharedFile));
        }
        // A change to a subgraph it places changes it too
        for (Graph.Node node : graph.getGraph().nodes()) {
            final Path placed = fileOf(file.getParent(), node.getType());
            final Loaded inner = placed == null ? null : LOADED.get(placed);
            if (inner != null) {
                files.putAll(inner.files());
            }
        }
        final Interface face = interfaceOf(graph, file.getParent());
        return new Loaded(graph, null, face, new java.util.TreeMap<>(files));
    }

    /**
     * What tells a file's change: its size and time, or that it is missing.
     *
     * @param file The file.
     * @return The stamp.
     */
    private static String stamp(Path file) {
        try {
            return Files.size(file) + "@" + Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return "missing";
        }
    }

    /** How deep the search looks for subgraph files under a folder. */
    private static final int SEARCH_DEPTH = 4;

    /** The most subgraph files the search lists, so a huge folder can't stall it. */
    private static final int SEARCH_LIMIT = 200;

    /** The folder beside a graph's ancestors whose subgraphs the search lists. */
    public static final String SUBGRAPHS_FOLDER = "subgraphs";

    /**
     * The subgraph files a graph's search offers: those in its folder and below, and in every
     * {@value #SUBGRAPHS_FOLDER} folder beside its ancestors. A subgraph file is a texture graph
     * with an Input or Output node.
     *
     * @param folder The graph's folder.
     * @param self The graph's own file, left out, or null.
     * @return The files, absolute, sorted.
     */
    public static List<Path> near(@NonNull Path folder, @Nullable Path self) {
        final Set<Path> found = new java.util.TreeSet<>();
        final Path start = folder.toAbsolutePath().normalize();
        collect(start, found);
        for (Path above = start; above != null; above = above.getParent()) {
            final Path library = above.resolve(SUBGRAPHS_FOLDER);
            if (Files.isDirectory(library) && !library.startsWith(start)) {
                collect(library, found);
            }
        }
        if (self != null) {
            found.remove(self.toAbsolutePath().normalize());
        }
        return List.copyOf(found);
    }

    /**
     * Add the subgraph files under a folder.
     *
     * @param folder The folder.
     * @param found Gets them.
     */
    private static void collect(Path folder, Set<Path> found) {
        try (java.util.stream.Stream<Path> files = Files.walk(folder, SEARCH_DEPTH)) {
            files.filter(f -> f.toString().endsWith(TextureGraph.SUFFIX))
                    .limit(SEARCH_LIMIT)
                    .forEach(
                            f -> {
                                if (isSubgraph(f)) {
                                    found.add(f.toAbsolutePath().normalize());
                                }
                            });
        } catch (IOException | java.io.UncheckedIOException e) {
            // A folder that can't be listed offers nothing
        }
    }

    /**
     * Whether a texture graph file has an Input or Output node, so it can be placed.
     *
     * @param file The file.
     * @return True if it is a subgraph.
     */
    public static boolean isSubgraph(@NonNull Path file) {
        try {
            final String text = Files.readString(file);
            return text.contains(INPUT_PREFIX) || text.contains(OUTPUT_PREFIX);
        } catch (IOException | java.io.UncheckedIOException e) {
            return false;
        }
    }

    /**
     * What tells a change to a subgraph file, or to anything it reads: the same text while nothing
     * changed.
     *
     * @param file The file, absolute.
     * @return The version.
     */
    public static String version(@NonNull Path file) {
        final Loaded loaded = load(file);
        return loaded.files() + "|" + loaded.error();
    }

    /** Forget every subgraph file read, as a test or a reload wants. */
    public static void forget() {
        LOADED.clear();
    }

    /** What a placed subgraph does: evaluates its file's graph with the node's inputs. */
    private static final class Placed implements TextureNode {
        /** The subgraph file, or null if its path is bad. */
        private final @Nullable Path file;

        /** The type's ID. */
        private final String type;

        /** What the file showed when the type was registered. */
        private final Interface face;

        /** Why the subgraph can't be placed, or null. */
        private final @Nullable String error;

        /**
         * Make the node.
         *
         * @param file The subgraph file, or null.
         * @param type The type's ID.
         * @param face What it showed.
         * @param error Why it can't be placed, or null.
         */
        Placed(@Nullable Path file, String type, Interface face, @Nullable String error) {
            this.file = file;
            this.type = type;
            this.face = face;
            this.error = error;
        }

        @Override
        public Set<String> parametersRead(@NonNull Map<String, String> params) {
            return face.reads();
        }

        @Override
        public String fingerprint(@NonNull Map<String, String> params, Path folder) {
            if (file == null) {
                return "";
            }
            final Deque<Path> active = ACTIVE.get();
            if (active.contains(file)) {
                return "places itself";
            }
            final Loaded loaded = load(file);
            final StringBuilder print = new StringBuilder(loaded.files().toString());
            final TextureGraph graph = loaded.graph();
            if (graph == null) {
                return print.toString();
            }
            active.push(file);
            try {
                // What its nodes read from outside, like images
                final Graph inner = graph.getGraph();
                final List<Graph.Node> nodes = new ArrayList<>(inner.nodes());
                nodes.sort(Comparator.comparing(Graph.Node::getId));
                for (Graph.Node node : nodes) {
                    final NodeTypes.NodeType innerType = inner.typeOf(node.getId());
                    final TextureNode does =
                            innerType == null ? null : innerLibrary(graph).get(innerType.id());
                    if (does == null) {
                        continue;
                    }
                    final Map<String, String> innerParams = new HashMap<>();
                    for (NodeTypes.Param param : innerType.params()) {
                        innerParams.put(param.name(), inner.param(node.getId(), param.name()));
                    }
                    print.append('|')
                            .append(node.getId())
                            .append(':')
                            .append(does.fingerprint(innerParams, file.getParent()));
                }
            } finally {
                active.pop();
            }
            return print.toString();
        }

        @Override
        public void evaluate(@NonNull NodeContext context) {
            if (error != null) {
                throw new NodeException(error);
            }
            for (String problem : face.problems()) {
                throw new NodeException(problem);
            }
            final String name = titleOf(type);
            for (NodeTypes.Pin input : context.getType().inputs()) {
                if (!has(face.inputs(), input.name()) && context.linked(input.name())) {
                    throw new NodeException(input.name() + " is no longer an input of " + name);
                }
            }
            for (NodeTypes.Pin output : context.getType().outputs()) {
                if (!has(face.outputs(), output.name())) {
                    throw new NodeException(output.name() + " is no longer an output of " + name);
                }
            }
            final Set<String> own = new HashSet<>();
            face.parameters().forEach(p -> own.add(p.name()));
            for (NodeTypes.Param param : context.getType().params()) {
                if (!VARIATION.equals(param.name())
                        && !own.contains(param.name())
                        && !context.text(param.name()).isBlank()) {
                    throw new NodeException(param.name() + " is no longer a parameter of " + name);
                }
            }
            final Loaded loaded = load(file);
            if (loaded.graph() == null) {
                throw new NodeException(loaded.error());
            }
            final Deque<Path> active = ACTIVE.get();
            if (active.contains(file)) {
                throw new NodeException("The subgraph places itself");
            }
            active.push(file);
            try {
                evaluate(context, loaded.graph());
            } finally {
                active.pop();
            }
        }

        /**
         * Evaluate the subgraph's outputs.
         *
         * @param context The node's context.
         * @param graph The subgraph.
         */
        private void evaluate(NodeContext context, TextureGraph graph) {
            final Path folder = file.getParent();
            final Map<String, GraphParameter> parameters =
                    new LinkedHashMap<>(graph.resolveParameters(folder, new ArrayList<>()));
            for (Map.Entry<String, GraphParameter> entry : parameters.entrySet()) {
                final GraphParameter parameter = entry.getValue();
                final String set =
                        context.getType().defaultOf(entry.getKey()) == null
                                ? ""
                                : context.text(entry.getKey()).trim();
                if (!set.isEmpty()) {
                    entry.setValue(parameter.withValue(set));
                    continue;
                }
                final String outer = context.text(Evaluator.READ_PREFIX + entry.getKey());
                final int colon = outer.indexOf(':');
                if (colon > 0 && outer.substring(0, colon).equals(parameter.kind().id)) {
                    entry.setValue(parameter.withValue(outer.substring(colon + 1)));
                }
            }
            final Map<String, Plane> fed = new HashMap<>();
            for (NodeTypes.Pin input : face.inputs()) {
                if (context.linked(input.name())) {
                    fed.put(input.name(), context.input(input.name()));
                }
            }
            final long variation;
            try {
                variation = Long.parseLong(context.text(VARIATION).trim());
            } catch (NumberFormatException e) {
                throw new NodeException(VARIATION + " is a whole number");
            }
            // The outer graph's seed unless varied, so a subgraph made from part of a graph makes
            // the same pixels it did: its nodes keep their IDs, and so their seeds
            final long seed =
                    variation == 0
                            ? context.getOuterSeed()
                            : Evaluator.mix(context.getOuterSeed() ^ variation);
            final Evaluator.Settings settings =
                    new Evaluator.Settings(
                            context.getWidth(), context.getHeight(), seed, folder, parameters, fed);
            final Evaluator inner = new Evaluator(innerLibrary(graph));
            final Graph innerGraph = graph.getGraph();
            for (NodeTypes.Pin output : face.outputs()) {
                final Graph.Node node = outputNode(innerGraph, output.name());
                Plane plane = inner.input(innerGraph, settings, node.getId(), OUTPUT_IN);
                if (plane == null) {
                    if (!inner.getErrors().isEmpty()) {
                        final Map.Entry<String, String> first =
                                inner.getErrors().entrySet().iterator().next();
                        throw new NodeException(
                                "In "
                                        + titleOf(type)
                                        + ", "
                                        + first.getKey()
                                        + ": "
                                        + first.getValue());
                    }
                    plane =
                            TexturePins.unlinked(
                                    output.type(), context.getWidth(), context.getHeight());
                }
                context.output(output.name(), plane);
            }
        }

        /**
         * The library a subgraph's graph was read with.
         *
         * @param graph The graph.
         * @return Its library.
         */
        private static TextureLibrary innerLibrary(TextureGraph graph) {
            return graph.getLibrary();
        }

        /**
         * The Output node with a name.
         *
         * @param graph The subgraph.
         * @param name The name.
         * @return The node.
         */
        private static Graph.Node outputNode(Graph graph, String name) {
            for (Graph.Node node : graph.nodes()) {
                if (node.getType().startsWith(OUTPUT_PREFIX)
                        && name.equals(String.valueOf(graph.param(node.getId(), NAME)).trim())) {
                    return node;
                }
            }
            throw new NodeException("The subgraph has no output " + name);
        }

        /**
         * Whether pins have one of a name.
         *
         * @param pins The pins.
         * @param name The name.
         * @return True if one has it.
         */
        private static boolean has(List<NodeTypes.Pin> pins, String name) {
            return pins.stream().anyMatch(p -> p.name().equals(name));
        }
    }
}
