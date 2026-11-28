package com.ikalagaming.converter.index;

import com.ikalagaming.converter.content.BlockContent;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeStringSerialization;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The game's asset types, read through Factory-Core: block looks, blocks and game materials, and
 * every other content file. Only made while Factory-Core is loaded; see {@link
 * com.ikalagaming.converter.content.GameContent}.
 */
public final class GameTypes {
    /** The look type's name. */
    public static final String LOOK = "look";

    /** The block type's name. */
    public static final String BLOCK = "block";

    /** The game material type's name. */
    public static final String GAME_MATERIAL = "game material";

    /** A content file's extension. */
    private static final String KVT = ".kvt";

    /** Not made. */
    private GameTypes() {}

    /**
     * The game's types, tried before the editor's.
     *
     * @return The types.
     */
    public static List<AssetType> all() {
        return List.of(
                new Kvt(LOOK, "looks") {
                    @Override
                    List<AssetIndex.Ref> references(Node root, String namespace) {
                        final List<AssetIndex.Ref> refs = new ArrayList<>();
                        final String model = text(root, "model");
                        refs.add(
                                AssetIndex.Ref.id(
                                        EditorTypes.MODEL,
                                        EditorTypes.MODEL,
                                        model == null || BlockContent.FLUID.equals(model)
                                                ? BlockContent.DEFAULT_MODEL
                                                : model));
                        final String texture = text(root, "texture");
                        if (texture != null) {
                            refs.add(
                                    AssetIndex.Ref.id(
                                            EditorTypes.TEXTURE,
                                            EditorTypes.TEXTURE,
                                            stripMap(texture)));
                        }
                        final String material = text(root, "material");
                        if (material != null) {
                            refs.add(
                                    AssetIndex.Ref.id(
                                            "material", EditorTypes.RENDER_MATERIAL, material));
                        }
                        return refs;
                    }
                },
                new Kvt(BLOCK, "blocks") {
                    @Override
                    List<AssetIndex.Ref> references(Node root, String namespace) {
                        return List.of();
                    }

                    @Override
                    public List<AssetIndex.Ref> references(
                            @NonNull Path file,
                            AssetIndex.@NonNull Root root,
                            @NonNull String relative)
                            throws IOException {
                        final List<AssetIndex.Ref> refs =
                                new ArrayList<>(super.references(file, root, relative));
                        final String id = AssetIndex.idOf(root, relative, this);
                        final Node node = read(file);
                        final String material = text(node, "material");
                        if (material != null && id != null) {
                            refs.add(
                                    AssetIndex.Ref.id(
                                            "material", GAME_MATERIAL, qualify(material, id)));
                        }
                        if (id != null) {
                            // Its look has its ID, under looks/block
                            final int colon = id.indexOf(':');
                            refs.add(
                                    AssetIndex.Ref.id(
                                                    LOOK,
                                                    LOOK,
                                                    id.substring(0, colon)
                                                            + ":block/"
                                                            + id.substring(colon + 1))
                                            .optional(true));
                        }
                        return refs;
                    }
                },
                new Kvt(GAME_MATERIAL, "materials") {
                    @Override
                    List<AssetIndex.Ref> references(Node root, String namespace) {
                        return List.of();
                    }

                    @Override
                    public List<AssetIndex.Ref> references(
                            @NonNull Path file,
                            AssetIndex.@NonNull Root root,
                            @NonNull String relative)
                            throws IOException {
                        final String id = AssetIndex.idOf(root, relative, this);
                        final String parent = text(read(file), "parent");
                        return parent == null || id == null
                                ? List.of()
                                : List.of(
                                        AssetIndex.Ref.id(
                                                "parent", GAME_MATERIAL, qualify(parent, id)));
                    }
                },
                new AssetType() {
                    @Override
                    public String name() {
                        return "content";
                    }

                    @Override
                    public boolean matches(
                            AssetIndex.@NonNull Root root, @NonNull String relative) {
                        return root.content() && relative.endsWith(KVT);
                    }
                });
    }

    /**
     * A name written without a namespace, in the namespace of the file naming it.
     *
     * @param name The name.
     * @param id The naming file's ID.
     * @return The ID.
     */
    static String qualify(String name, String id) {
        return name.contains(":") ? name : id.substring(0, id.indexOf(':') + 1) + name;
    }

    /**
     * A texture ID without a map named after it, like {@code #normal}.
     *
     * @param texture The ID.
     * @return The texture's ID.
     */
    static String stripMap(String texture) {
        final int hash = texture.indexOf('#');
        return hash < 0 ? texture : texture.substring(0, hash);
    }

    /**
     * Read a content file.
     *
     * @param file The file.
     * @return Its tree.
     * @throws IOException If it can't be read or isn't a tree.
     */
    static Node read(Path file) throws IOException {
        return TreeStringSerialization.fromString(Files.readString(file))
                .orElseThrow(() -> new IOException("it isn't a key value tree"));
    }

    /**
     * Text in a tree.
     *
     * @param node The tree.
     * @param key The key.
     * @return The text, or null if missing or not text.
     */
    static @Nullable String text(Node node, String key) {
        final Object value = node.hasChild(key) ? node.get(key) : null;
        return value instanceof String text ? text : null;
    }

    /** A content file type with IDs under a folder. */
    private abstract static class Kvt implements AssetType {
        /** The type's name. */
        private final String name;

        /** Its folder. */
        private final String kind;

        /**
         * Make the type.
         *
         * @param name The type's name.
         * @param kind Its folder.
         */
        Kvt(String name, String kind) {
            this.name = name;
            this.kind = kind;
        }

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
            return List.of(KVT);
        }

        @Override
        public boolean matches(AssetIndex.@NonNull Root root, @NonNull String relative) {
            return root.content() && AssetIndex.idOf(root, relative, this) != null;
        }

        @Override
        public List<AssetIndex.Ref> references(
                @NonNull Path file, AssetIndex.@NonNull Root root, @NonNull String relative)
                throws IOException {
            return references(read(file), root.namespace());
        }

        /**
         * What a file's tree references.
         *
         * @param root The tree.
         * @param namespace The file's folder's namespace.
         * @return The references.
         */
        abstract List<AssetIndex.Ref> references(Node root, String namespace);
    }
}
