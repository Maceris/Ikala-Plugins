package com.ikalagaming.converter.index;

import com.ikalagaming.converter.texgraph.CoreNodes;
import com.ikalagaming.converter.texgraph.SharedParameters;
import com.ikalagaming.converter.texgraph.SubgraphNodes;
import com.ikalagaming.converter.texgraph.TextureGraph;
import com.ikalagaming.converter.texgraph.TextureInstance;

import lombok.NonNull;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The asset types the editor itself knows, with no game's content in them: textures, models and
 * render materials, the sources they are made from, and texture graphs, instances and shared
 * parameter files.
 */
public final class EditorTypes {
    /** The texture type's name. */
    public static final String TEXTURE = "texture";

    /** An image file's type: a PNG, JPEG, TGA or HDR picture, under any root. */
    public static final String IMAGE = "image";

    /** The model type's name. */
    public static final String MODEL = "model";

    /** The render material type's name. */
    public static final String RENDER_MATERIAL = "render material";

    /** The texture graph type's name. */
    public static final String TEXTURE_GRAPH = "texture graph";

    /** The texture instance type's name. */
    public static final String TEXTURE_INSTANCE = "texture instance";

    /** A shipped file's extension. */
    private static final String IKA = ".ika";

    /** A texture's map files, which belong to the texture of the name before them. */
    private static final Set<String> MAPS = Set.of("normal", "orm", "height", "emissive", "mask");

    /** Model source extensions. */
    private static final Set<String> MODEL_SOURCES =
            Set.of(".obj", ".fbx", ".gltf", ".glb", ".blend", ".dae");

    /** Image extensions. */
    private static final Set<String> IMAGES = Set.of(".png", ".jpg", ".jpeg", ".tga", ".hdr");

    /** Not made. */
    private EditorTypes() {}

    /**
     * The editor's types, in the order they are tried.
     *
     * @return The types.
     */
    public static List<AssetType> all() {
        return List.of(
                shipped(TEXTURE, "textures", List.of(IKA, ".png")),
                shipped(MODEL, "models", List.of(IKA)),
                shipped(RENDER_MATERIAL, "render_materials", List.of(IKA)),
                new TextureGraphs(),
                new Instances(),
                suffix("shared parameters", SharedParameters.SUFFIX),
                new Sources(
                        "material source", ".material.yml", "render_materials", RENDER_MATERIAL),
                new ModelSources(),
                new Images());
    }

    /**
     * A type of shipped file with IDs: under its folder in a content folder.
     *
     * @param name The type's name.
     * @param kind Its folder.
     * @param extensions Its extensions.
     * @return The type.
     */
    private static AssetType shipped(String name, String kind, List<String> extensions) {
        return new AssetType() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String kind() {
                return kind;
            }

            @Override
            public List<String> extensions() {
                return extensions;
            }

            @Override
            public boolean matches(AssetIndex.@NonNull Root root, @NonNull String relative) {
                return root.content()
                        && AssetIndex.idOf(root, relative, this) != null
                        && !isMap(relative);
            }
        };
    }

    /**
     * Whether a file is one of a texture's maps, like {@code stone.normal.png}.
     *
     * @param relative Its path.
     * @return True if it is.
     */
    static boolean isMap(String relative) {
        final String name = relative.substring(relative.lastIndexOf('/') + 1);
        final String[] parts = name.split("\\.");
        return parts.length >= 3 && MAPS.contains(parts[parts.length - 2]);
    }

    /**
     * A type of file by its name's ending, with no references.
     *
     * @param name The type's name.
     * @param suffix The ending.
     * @return The type.
     */
    private static AssetType suffix(String name, String suffix) {
        return new AssetType() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean matches(AssetIndex.@NonNull Root root, @NonNull String relative) {
                return relative.endsWith(suffix);
            }
        };
    }

    /**
     * The path after a folder named in a path, like {@code block/stone} after {@code
     * render_materials} in {@code art/render_materials/block/stone.material.yml}.
     *
     * @param relative The path.
     * @param folder The folder's name.
     * @param ending What to take off the end.
     * @return The rest, or null if the folder isn't in it.
     */
    static @Nullable String after(String relative, String folder, String ending) {
        final String withSlashes = "/" + relative;
        final int at = withSlashes.lastIndexOf("/" + folder + "/");
        if (at < 0 || !relative.toLowerCase(Locale.ROOT).endsWith(ending)) {
            return null;
        }
        final String rest = withSlashes.substring(at + folder.length() + 2);
        return rest.substring(0, rest.length() - ending.length());
    }

    /** Sources that make a shipped file of the same path under its type's folder. */
    private static final class Sources implements AssetType {
        /** The type's name. */
        private final String name;

        /** What a source's name ends with. */
        private final String suffix;

        /** The folder the made file's path follows. */
        private final String folder;

        /** The made file's type. */
        private final String made;

        /**
         * Make the type.
         *
         * @param name The type's name.
         * @param suffix What a source's name ends with.
         * @param folder The folder the made file's path follows.
         * @param made The made file's type.
         */
        Sources(String name, String suffix, String folder, String made) {
            this.name = name;
            this.suffix = suffix;
            this.folder = folder;
            this.made = made;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean matches(AssetIndex.@NonNull Root root, @NonNull String relative) {
            return relative.endsWith(suffix);
        }

        @Override
        public List<AssetIndex.Ref> references(
                @NonNull Path file, AssetIndex.@NonNull Root root, @NonNull String relative) {
            final String path = after(relative, folder, suffix);
            return path == null ? List.of() : List.of(AssetIndex.Ref.makes(made, path));
        }
    }

    /** Model sources, which make the model of the same path. */
    private static final class ModelSources implements AssetType {
        @Override
        public String name() {
            return "model source";
        }

        @Override
        public boolean matches(AssetIndex.@NonNull Root root, @NonNull String relative) {
            return MODEL_SOURCES.contains(extension(relative));
        }

        @Override
        public List<AssetIndex.Ref> references(
                @NonNull Path file, AssetIndex.@NonNull Root root, @NonNull String relative) {
            final String path = after(relative, "models", extension(relative));
            return path == null ? List.of() : List.of(AssetIndex.Ref.makes(MODEL, path));
        }
    }

    /** Images that aren't shipped textures: painted maps, skyboxes, references. */
    private static final class Images implements AssetType {
        @Override
        public String name() {
            return IMAGE;
        }

        @Override
        public boolean matches(AssetIndex.@NonNull Root root, @NonNull String relative) {
            return IMAGES.contains(extension(relative));
        }
    }

    /**
     * A file's extension, from its last dot, in lower case.
     *
     * @param relative The path.
     * @return The extension with its dot, or empty.
     */
    static String extension(String relative) {
        final int dot = relative.lastIndexOf('.');
        return dot < 0 ? "" : relative.substring(dot).toLowerCase(Locale.ROOT);
    }

    /**
     * Read a YAML file as a map.
     *
     * @param file The file.
     * @return The map.
     * @throws IOException If it can't be read or isn't a map.
     */
    static Map<?, ?> yaml(Path file) throws IOException {
        final Object document =
                new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
        if (!(document instanceof Map<?, ?> map)) {
            throw new IOException("it isn't a map");
        }
        return map;
    }

    /**
     * A file a graph names relative to itself.
     *
     * @param file The graph file.
     * @param name The name.
     * @return The file, or null if the name isn't a path.
     */
    static @Nullable Path near(Path file, String name) {
        try {
            return file.getParent().resolve(name).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * What a graph or instance bakes to in its content folder, as {@code makes} references.
     *
     * @param file The graph or instance file.
     * @param data The content folder, relative to the file, or null.
     * @param texture The texture's name, or null.
     * @param out Gets the references.
     */
    static void makes(
            Path file, @Nullable String data, @Nullable String texture, List<AssetIndex.Ref> out) {
        if (data == null || texture == null) {
            return;
        }
        final Path folder = near(file, data);
        if (folder != null) {
            final Path made = folder.resolve("textures").resolve(texture + IKA).normalize();
            out.add(AssetIndex.Ref.path("makes", made, texture).optional(true));
        }
    }

    /** Texture graphs, subgraphs included: what they place, read, and bake to. */
    private static final class TextureGraphs implements AssetType {
        @Override
        public String name() {
            return TEXTURE_GRAPH;
        }

        @Override
        public boolean matches(AssetIndex.@NonNull Root root, @NonNull String relative) {
            return relative.endsWith(TextureGraph.SUFFIX);
        }

        @Override
        public List<AssetIndex.Ref> references(
                @NonNull Path file, AssetIndex.@NonNull Root root, @NonNull String relative)
                throws IOException {
            final Map<?, ?> map = yaml(file);
            final List<AssetIndex.Ref> refs = new ArrayList<>();
            if (map.get("shared") instanceof List<?> shared) {
                for (Object name : shared) {
                    addPath(refs, "shared parameters", file, String.valueOf(name));
                }
            }
            final String data = text(map.get("data"));
            final String model = text(map.get("model"));
            if (data != null && model != null) {
                final Path folder = near(file, data);
                if (folder != null) {
                    refs.add(
                            AssetIndex.Ref.path(
                                    MODEL, folder.resolve("models").resolve(model + IKA), model));
                }
            }
            if (map.get("nodes") instanceof Map<?, ?> nodes) {
                for (Object value : nodes.values()) {
                    if (!(value instanceof Map<?, ?> node)
                            || !(node.get("type") instanceof String type)) {
                        continue;
                    }
                    if (type.startsWith(SubgraphNodes.TYPE_PREFIX)) {
                        addPath(
                                refs,
                                "subgraph",
                                file,
                                type.substring(SubgraphNodes.TYPE_PREFIX.length()));
                    } else if (CoreNodes.IMAGE.equals(type)
                            && node.get("set") instanceof Map<?, ?> set
                            && text(set.get(CoreNodes.FILE)) != null) {
                        addPath(refs, "image", file, text(set.get(CoreNodes.FILE)));
                    }
                }
            }
            if (map.get("parameters") instanceof Map<?, ?> parameters) {
                for (Object value : parameters.values()) {
                    if (value instanceof Map<?, ?> parameter
                            && "texture".equals(parameter.get("type"))
                            && text(parameter.get("value")) != null) {
                        addPath(refs, "image", file, text(parameter.get("value")));
                    }
                }
            }
            makes(file, data, text(map.get("texture")), refs);
            return refs;
        }
    }

    /** Texture instances: their parent, and what they bake to. */
    private static final class Instances implements AssetType {
        @Override
        public String name() {
            return TEXTURE_INSTANCE;
        }

        @Override
        public boolean matches(AssetIndex.@NonNull Root root, @NonNull String relative) {
            return relative.endsWith(TextureInstance.SUFFIX);
        }

        @Override
        public List<AssetIndex.Ref> references(
                @NonNull Path file, AssetIndex.@NonNull Root root, @NonNull String relative)
                throws IOException {
            final Map<?, ?> map = yaml(file);
            final List<AssetIndex.Ref> refs = new ArrayList<>();
            final String parent = text(map.get("parent"));
            if (parent != null) {
                addPath(refs, "parent", file, parent);
            }
            // The content folder is the nearest one set, up the chain
            final String texture = text(map.get("texture"));
            if (texture != null) {
                final TextureInstance.Chain chain = TextureInstance.chain(file);
                String data = null;
                Path dataBase = null;
                for (int i = chain.instances().size() - 1; i >= 0 && data == null; --i) {
                    data = chain.instances().get(i).instance().getData();
                    dataBase = chain.instances().get(i).file();
                }
                if (data == null && chain.graph() != null && Files.isRegularFile(chain.graph())) {
                    data = text(yaml(chain.graph()).get("data"));
                    dataBase = chain.graph();
                }
                if (dataBase != null) {
                    makes(dataBase, data, texture, refs);
                }
            }
            return refs;
        }
    }

    /**
     * Add a reference to a file named relative to another.
     *
     * @param refs Gets it.
     * @param label What it is.
     * @param file The file naming it.
     * @param name The name.
     */
    private static void addPath(List<AssetIndex.Ref> refs, String label, Path file, String name) {
        final Path target = near(file, name);
        if (target != null) {
            refs.add(AssetIndex.Ref.path(label, target, name));
        }
    }

    /**
     * A YAML value as text.
     *
     * @param value The value.
     * @return Its text, or null if absent or blank.
     */
    static @Nullable String text(@Nullable Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }
}
