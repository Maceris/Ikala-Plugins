package com.ikalagaming.converter.texgraph;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * A texture graph with some of its values changed, as its {@code *.texinst.yml} file holds it: the
 * parent it changes (a graph, or another instance, by a path relative to the file) and only what it
 * overrides, one line each:
 *
 * <pre>
 * texture instance: 1
 * parent: planks.texgraph.yml
 * seed: 7
 * texture: block/birch_planks
 * parameters:
 *   tint: '#c8b48a'
 * </pre>
 *
 * Overrides apply from the graph down the chain of instances, the nearest winning. A parameter can
 * be one of the graph's own or one it reads from a shared file. An instance bakes its graph with
 * its values, and must name its own texture so it never overwrites its parent's maps.
 */
public final class TextureInstance {
    /** What an instance file's name ends with. */
    public static final String SUFFIX = ".texinst.yml";

    /** The format's version, written first. */
    public static final int FORMAT = 1;

    /** The key holding the format's version. */
    static final String FORMAT_KEY = "texture instance";

    /** The key naming the parent. */
    static final String PARENT = "parent";

    /** The key holding the parameter values. */
    static final String PARAMETERS = "parameters";

    /**
     * The parent, a graph or instance file, relative to this file. -- GETTER -- The parent. --
     * SETTER -- Set the parent.
     *
     * @param parent The path.
     * @return The path.
     */
    @Getter @Setter private @Nullable String parent;

    /**
     * The seed, or null to keep the parent's. -- GETTER -- The seed. -- SETTER -- Set the seed.
     *
     * @param seed The seed.
     * @return The seed, or null.
     */
    @Getter @Setter private @Nullable Long seed;

    /**
     * The content folder, relative to this file, or null to keep the parent's. -- GETTER -- The
     * folder. -- SETTER -- Set the folder.
     *
     * @param data The folder.
     * @return The folder, or null.
     */
    @Getter @Setter private @Nullable String data;

    /**
     * The folder of textures maps bake under, relative to this file, or null to keep the parent's.
     * -- GETTER -- The folder. -- SETTER -- Set the folder.
     *
     * @param maps The folder.
     * @return The folder, or null.
     */
    @Getter @Setter private @Nullable String maps;

    /**
     * The texture's name, or null to keep the parent's. -- GETTER -- The name. -- SETTER -- Set the
     * name.
     *
     * @param texture The name.
     * @return The name, or null.
     */
    @Getter @Setter private @Nullable String texture;

    /**
     * The model giving the layout, or null to keep the parent's. -- GETTER -- The name. -- SETTER
     * -- Set the name.
     *
     * @param model The name.
     * @return The name, or null.
     */
    @Getter @Setter private @Nullable String model;

    /** Parameter values overridden, by name, sorted. */
    private final Map<String, String> values = new TreeMap<>();

    /**
     * The parameter values overridden.
     *
     * @return Them, by name, sorted.
     */
    public Map<String, String> getValues() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Override a parameter's value.
     *
     * @param name The parameter.
     * @param value Its value.
     */
    public void setValue(@NonNull String name, @NonNull String value) {
        values.put(name, value);
    }

    /**
     * Stop overriding a parameter, so it is the parent's again.
     *
     * @param name The parameter.
     * @return True if it was overridden.
     */
    public boolean removeValue(@NonNull String name) {
        return values.remove(name) != null;
    }

    /**
     * Write the file's text: the parent, then only what is overridden.
     *
     * @return The YAML.
     */
    public String write() {
        final StringBuilder out = new StringBuilder();
        out.append(FORMAT_KEY).append(": ").append(FORMAT).append('\n');
        if (parent != null) {
            out.append(PARENT).append(": ").append(GraphParameter.quote(parent)).append('\n');
        }
        if (seed != null) {
            out.append("seed: ").append(seed).append('\n');
        }
        line(out, "data", data);
        line(out, "maps", maps);
        line(out, "texture", texture);
        line(out, "model", model);
        if (!values.isEmpty()) {
            out.append(PARAMETERS).append(":\n");
            for (Map.Entry<String, String> value : values.entrySet()) {
                out.append("  ")
                        .append(value.getKey())
                        .append(": ")
                        .append(GraphParameter.quote(value.getValue()))
                        .append('\n');
            }
        }
        return out.toString();
    }

    /**
     * Write one text setting, if set.
     *
     * @param out Where.
     * @param key The setting.
     * @param value Its value, or null.
     */
    private static void line(StringBuilder out, String key, @Nullable String value) {
        if (value != null) {
            out.append(key).append(": ").append(GraphParameter.quote(value)).append('\n');
        }
    }

    /**
     * Read a file's text into this instance, which should be empty.
     *
     * @param text The YAML.
     * @return Problems met.
     * @throws IllegalArgumentException If the text isn't an instance.
     */
    public List<String> read(@NonNull String text) {
        final Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        if (!(document instanceof Map<?, ?> root) || !root.containsKey(FORMAT_KEY)) {
            throw new IllegalArgumentException(
                    "Not a texture instance: it has no '" + FORMAT_KEY + "'");
        }
        final List<String> problems = new ArrayList<>();
        if (root.get(FORMAT_KEY) instanceof Number version && version.intValue() > FORMAT) {
            problems.add(
                    "Written by a newer editor, format " + version + "; some parts may be lost");
        }
        parent = text(root, PARENT);
        if (parent == null) {
            problems.add("An instance names its parent");
        }
        if (root.get("seed") instanceof Number number) {
            seed = number.longValue();
        } else if (root.containsKey("seed")) {
            problems.add("seed is a whole number");
        }
        data = text(root, "data");
        maps = text(root, "maps");
        texture = text(root, "texture");
        model = text(root, "model");
        values.clear();
        if (root.get(PARAMETERS) instanceof Map<?, ?> map) {
            map.forEach((name, value) -> values.put(String.valueOf(name), String.valueOf(value)));
        } else if (root.containsKey(PARAMETERS)) {
            problems.add("parameters are name: value lines");
        }
        return problems;
    }

    /**
     * A text setting.
     *
     * @param root The file's map.
     * @param key The setting.
     * @return Its text, or null if absent or blank.
     */
    private static @Nullable String text(Map<?, ?> root, String key) {
        final Object value = root.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    /**
     * An instance file read.
     *
     * @param file The file, absolute.
     * @param instance What it holds.
     */
    public record Link(@NonNull Path file, @NonNull TextureInstance instance) {}

    /**
     * An instance's way to its graph.
     *
     * @param graph The graph file at the top, or null if it couldn't be found.
     * @param instances The instances from the graph down to the one asked for.
     * @param problems What went wrong on the way.
     */
    public record Chain(
            @Nullable Path graph, @NonNull List<Link> instances, @NonNull List<String> problems) {
        /**
         * The instance asked for.
         *
         * @return The last link.
         */
        public Link last() {
            return instances.getLast();
        }

        /**
         * The files read, for telling when one changed.
         *
         * @return The instance files, then the graph's.
         */
        public List<Path> files() {
            final List<Path> files = new ArrayList<>();
            instances.forEach(link -> files.add(link.file()));
            if (graph != null) {
                files.add(graph);
            }
            return files;
        }
    }

    /**
     * Follow an instance file up to its graph.
     *
     * @param file The instance file.
     * @return The chain.
     */
    public static Chain chain(@NonNull Path file) {
        final List<Link> links = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        final Set<Path> seen = new LinkedHashSet<>();
        Path at = file.toAbsolutePath().normalize();
        while (true) {
            if (!seen.add(at)) {
                problems.add("The instances' parents go round in a loop at " + at.getFileName());
                return new Chain(null, List.of(), problems);
            }
            final TextureInstance instance = new TextureInstance();
            try {
                for (String problem : instance.read(Files.readString(at))) {
                    problems.add(at.getFileName() + ": " + problem);
                }
            } catch (IOException | IllegalArgumentException e) {
                problems.add("Can't read " + at.getFileName() + ": " + e.getMessage());
                return new Chain(null, List.of(), problems);
            }
            links.addFirst(new Link(at, instance));
            if (instance.getParent() == null) {
                return new Chain(null, List.of(), problems);
            }
            final Path parentFile;
            try {
                parentFile =
                        at.getParent().resolve(instance.getParent()).toAbsolutePath().normalize();
            } catch (InvalidPathException e) {
                problems.add(at.getFileName() + ": the parent isn't a file name");
                return new Chain(null, List.of(), problems);
            }
            if (parentFile.toString().endsWith(TextureGraph.SUFFIX)) {
                return new Chain(parentFile, List.copyOf(links), problems);
            }
            if (!parentFile.toString().endsWith(SUFFIX)) {
                problems.add(at.getFileName() + ": a parent is a graph or an instance");
                return new Chain(null, List.of(), problems);
            }
            at = parentFile;
        }
    }

    /**
     * Read the graph at the top of a chain.
     *
     * @param chain The chain.
     * @param problems Gets what went wrong.
     * @return The graph, or null if it couldn't be read.
     */
    public static @Nullable TextureGraph readGraph(
            @NonNull Chain chain, @NonNull List<String> problems) {
        if (chain.graph() == null) {
            return null;
        }
        final TextureGraph graph = new TextureGraph(TextureLibraries.standard());
        try {
            for (String problem :
                    graph.read(Files.readString(chain.graph()), chain.graph().getParent())) {
                problems.add(chain.graph().getFileName() + ": " + problem);
            }
        } catch (IOException | IllegalArgumentException e) {
            problems.add("Can't read " + chain.graph().getFileName() + ": " + e.getMessage());
            return null;
        }
        return graph;
    }

    /**
     * A graph with a chain's values: a copy of the graph with each instance's overrides applied,
     * from the graph down.
     *
     * @param chain The chain.
     * @param graph The chain's graph, as read or as an editor has it now.
     * @param upTo How many of the chain's instances to apply, from the graph down.
     * @param problems Gets values for parameters the graph doesn't have.
     * @return The copy.
     */
    public static TextureGraph resolve(
            @NonNull Chain chain,
            @NonNull TextureGraph graph,
            int upTo,
            @NonNull List<String> problems) {
        final TextureGraph applied = graph.copy();
        final Path graphFolder = Objects.requireNonNull(chain.graph()).getParent();
        for (int i = 0; i < Math.min(upTo, chain.instances().size()); ++i) {
            final Link link = chain.instances().get(i);
            link.instance().applyTo(applied, graphFolder, link.file().getParent(), problems);
        }
        return applied;
    }

    /**
     * Apply this instance's overrides to a graph.
     *
     * @param target The graph, changed.
     * @param graphFolder The graph file's folder, which its relative paths start from.
     * @param folder This instance file's folder, which its relative paths start from.
     * @param problems Gets values for parameters the graph doesn't have.
     */
    void applyTo(TextureGraph target, Path graphFolder, Path folder, List<String> problems) {
        if (seed != null) {
            target.setSeed(seed);
        }
        if (data != null) {
            target.setData(rebase(graphFolder, folder, data));
        }
        if (maps != null) {
            target.setMaps(rebase(graphFolder, folder, maps));
        }
        if (texture != null) {
            target.setTexture(texture);
        }
        if (model != null) {
            target.setModel(model);
        }
        final Map<String, GraphParameter> readable =
                target.resolveParameters(graphFolder, new ArrayList<>());
        final Map<String, GraphParameter> shared = new LinkedHashMap<>(target.getSharedOverrides());
        for (Map.Entry<String, String> value : values.entrySet()) {
            final GraphParameter own = target.parameter(value.getKey());
            if (own != null) {
                target.putParameter(own.withValue(value.getValue()));
            } else if (readable.containsKey(value.getKey())) {
                shared.put(
                        value.getKey(), readable.get(value.getKey()).withValue(value.getValue()));
            } else {
                problems.add("The graph has no parameter " + value.getKey());
            }
        }
        target.setSharedOverrides(shared);
    }

    /**
     * A path relative to one folder, as relative to another.
     *
     * @param to The folder it should start from.
     * @param from The folder it starts from.
     * @param path The path.
     * @return It relative to the first folder, in forward slashes.
     */
    public static String rebase(Path to, Path from, String path) {
        return to.toAbsolutePath()
                .normalize()
                .relativize(from.resolve(path).toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    /**
     * The overrides that make one graph into another, as the instance file at a place would hold
     * them: what differs, and nothing else.
     *
     * @param parent The parent's path, relative to the file.
     * @param folder The instance file's folder.
     * @param graphFolder The graph's folder.
     * @param base The graph with the parents' values.
     * @param edited The graph with this instance's values too.
     * @return The instance.
     */
    public static TextureInstance between(
            @NonNull String parent,
            @NonNull Path folder,
            @NonNull Path graphFolder,
            @NonNull TextureGraph base,
            @NonNull TextureGraph edited) {
        final TextureInstance instance = new TextureInstance();
        instance.setParent(parent);
        if (base.getSeed() != edited.getSeed()) {
            instance.setSeed(edited.getSeed());
        }
        if (!Objects.equals(base.getData(), edited.getData()) && edited.getData() != null) {
            instance.setData(rebase(folder, graphFolder, edited.getData()));
        }
        if (!Objects.equals(base.getMaps(), edited.getMaps()) && edited.getMaps() != null) {
            instance.setMaps(rebase(folder, graphFolder, edited.getMaps()));
        }
        if (!Objects.equals(base.getTexture(), edited.getTexture())) {
            instance.setTexture(edited.getTexture());
        }
        if (!Objects.equals(base.getModel(), edited.getModel())) {
            instance.setModel(edited.getModel());
        }
        final Map<String, GraphParameter> before =
                base.resolveParameters(graphFolder, new ArrayList<>());
        final Map<String, GraphParameter> after =
                edited.resolveParameters(graphFolder, new ArrayList<>());
        for (Map.Entry<String, GraphParameter> entry : after.entrySet()) {
            final GraphParameter was = before.get(entry.getKey());
            if (was != null && !was.value().equals(entry.getValue().value())) {
                instance.setValue(entry.getKey(), entry.getValue().value());
            }
        }
        return instance;
    }

    /**
     * Bake an instance file: its graph with its values.
     *
     * @param file The instance file.
     * @return What the bake did.
     */
    public static Baker.Result bake(@NonNull Path file) {
        final Chain chain = chain(file);
        final List<String> problems = new ArrayList<>(chain.problems());
        final TextureGraph graph = problems.isEmpty() ? readGraph(chain, problems) : null;
        if (graph == null || !problems.isEmpty()) {
            return new Baker.Result(List.of(), List.of(), problems);
        }
        if (chain.last().instance().getTexture() == null) {
            return new Baker.Result(
                    List.of(),
                    List.of(),
                    List.of(
                            "An instance names its own texture, so it doesn't bake over its parent's"));
        }
        final TextureGraph applied = resolve(chain, graph, chain.instances().size(), problems);
        if (!problems.isEmpty()) {
            return new Baker.Result(List.of(), List.of(), problems);
        }
        return Baker.bake(
                applied, new Evaluator(applied.getLibrary()), chain.graph().getParent(), null);
    }
}
