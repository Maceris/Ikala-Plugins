package com.ikalagaming.factory.registry;

import com.ikalagaming.factory.FactoryStrings;
import com.ikalagaming.factory.item.ItemDefinition;
import com.ikalagaming.factory.kvt.KvtSchemas;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeStringSerialization;
import com.ikalagaming.factory.world.BlockDefinition;
import com.ikalagaming.factory.world.Collision;
import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads the game's definitions from content folders ({@link ContentFolders}) into registries, one
 * KVT file per definition: tags, materials, blocks and items. They are rules, which the server, the
 * client and the tools all read.
 *
 * <ul>
 *   <li>{@code tags/<name>.kvt}: {@code {parent:"cold"}}, or {@code {}} for a tag with no parent.
 *   <li>{@code materials/<name>.kvt}: {@code {parent:"metal"}} or {@code {tags:[T;"solid"]}}.
 *   <li>{@code blocks/<id>.kvt}: {@code {material:"wood", tags:[T;"solid"], item:true,
 *       collision:"full"}}, every key optional; {@code item} (default true) also registers the
 *       block's item, and {@code collision} is {@code full} (the default) or {@code none}.
 *   <li>{@code items/<id>.kvt}: {@code {material:"bronze", tags:[T;...]}}.
 * </ul>
 *
 * <p>Blocks and items are namespaced, {@code lotomation:stone}. Tag and material names are shared
 * by every plugin, so mods agree on what {@code solid} or {@code bronze} means; a later folder's
 * file with the same name replaces an earlier one.
 *
 * <p>Load order matters, since each kind refers to the ones before it: tags, then materials, then
 * blocks (which register their items), then items. {@link #loadAll} does that.
 */
@Slf4j
public class DefinitionLoader {

    /** The folder of tag definitions. */
    public static final String TAGS_FOLDER = "tags";

    /** The folder of material definitions. */
    public static final String MATERIALS_FOLDER = "materials";

    /** The folder of block definitions. */
    public static final String BLOCKS_FOLDER = "blocks";

    /** The folder of item definitions. */
    public static final String ITEMS_FOLDER = "items";

    /** The extension of definition files. */
    public static final String EXTENSION = ".kvt";

    /** The key of a tag's or material's parent. */
    public static final String KEY_PARENT = "parent";

    /** The key of a definition's tags. */
    public static final String KEY_TAGS = "tags";

    /** The key of a block's or item's material. */
    public static final String KEY_MATERIAL = "material";

    /** The key of whether a block registers its item. */
    public static final String KEY_ITEM = "item";

    /** The key of a block's collision. */
    public static final String KEY_COLLISION = "collision";

    /**
     * Load every definition from some content folders, in order.
     *
     * @param folders The content folders, in load order.
     * @param registries Where to register them.
     */
    public static void loadAll(@NonNull List<Path> folders, @NonNull Registries registries) {
        loadTags(folders, registries.getTagRegistry());
        loadMaterials(folders, registries.getMaterialRegistry());
        loadBlocks(folders, registries.getBlockRegistry(), registries.getItemRegistry());
        loadItems(folders, registries.getItemRegistry());
    }

    /**
     * The block IDs some content folders define, for checking world generation data against.
     *
     * @param folders The content folders, in load order.
     * @return The block IDs, or empty if the folders define none.
     */
    public static Set<String> blockIds(@NonNull List<Path> folders) {
        return new TreeSet<>(
                ContentFolders.files(folders, BLOCKS_FOLDER, EXTENSION).files().keySet());
    }

    /**
     * Load and register blocks, and the items of those that have one.
     *
     * @param folders The content folders, in load order.
     * @param blockRegistry Where we are registering the blocks.
     * @param itemRegistry Where we are registering the corresponding block items.
     */
    public static void loadBlocks(
            @NonNull List<Path> folders,
            @NonNull BlockRegistry blockRegistry,
            @NonNull ItemRegistry itemRegistry) {
        final ContentFolders.Listing listing =
                ContentFolders.files(folders, BLOCKS_FOLDER, EXTENSION);
        warnSkipped(listing);
        for (Map.Entry<String, Path> entry : listing.files().entrySet()) {
            final String id = entry.getKey();
            final Optional<Node> root = read(entry.getValue());
            if (root.isEmpty()) {
                continue;
            }
            try {
                final String modName = id.substring(0, id.indexOf(':'));
                final String name = id.substring(id.indexOf(':') + 1);
                // An unknown collision only warns, and the block is full
                final Schema.Values values =
                        values(root.get(), ContentSchemas.BLOCK, id, KEY_COLLISION);
                final String material = values.text(KEY_MATERIAL);
                final List<String> tags = values.texts(KEY_TAGS);
                final Collision collision =
                        Collision.named(values.text(KEY_COLLISION)).orElse(Collision.FULL);
                blockRegistry.register(
                        id, new BlockDefinition(modName, name, material, tags, collision));
                if (values.flag(KEY_ITEM)) {
                    itemRegistry.register(
                            id, new ItemDefinition(modName, name, material, new ArrayList<>(tags)));
                }
            } catch (IllegalArgumentException e) {
                log.warn(
                        "{} {}: {}",
                        FactoryStrings.format("BLOCK_INVALID_STRUCTURE"),
                        entry.getValue(),
                        e.getMessage());
            }
        }
        log.debug(FactoryStrings.format("LOADED_BLOCKS"));
    }

    /**
     * Load and register items that aren't blocks.
     *
     * @param folders The content folders, in load order.
     * @param itemRegistry Where we are registering items.
     */
    public static void loadItems(@NonNull List<Path> folders, @NonNull ItemRegistry itemRegistry) {
        final ContentFolders.Listing listing =
                ContentFolders.files(folders, ITEMS_FOLDER, EXTENSION);
        warnSkipped(listing);
        for (Map.Entry<String, Path> entry : listing.files().entrySet()) {
            final String id = entry.getKey();
            final Optional<Node> root = read(entry.getValue());
            if (root.isEmpty()) {
                continue;
            }
            try {
                final Schema.Values values = values(root.get(), ContentSchemas.ITEM, id);
                itemRegistry.register(
                        id,
                        new ItemDefinition(
                                id.substring(0, id.indexOf(':')),
                                id.substring(id.indexOf(':') + 1),
                                values.text(KEY_MATERIAL),
                                values.texts(KEY_TAGS)));
            } catch (IllegalArgumentException e) {
                log.warn(
                        "{} {}: {}",
                        FactoryStrings.format("ITEM_INVALID_STRUCTURE"),
                        entry.getValue(),
                        e.getMessage());
            }
        }
        log.debug(FactoryStrings.format("LOADED_ITEMS"));
    }

    /**
     * Load and register materials, each after its parent.
     *
     * @param folders The content folders, in load order.
     * @param materialRegistry Where we are registering materials.
     */
    public static void loadMaterials(
            @NonNull List<Path> folders, @NonNull MaterialRegistry materialRegistry) {
        final Map<String, Node> materials = shared(folders, MATERIALS_FOLDER);
        for (String name : parentsFirst(materials)) {
            try {
                final Schema.Values values =
                        values(materials.get(name), ContentSchemas.MATERIAL, name);
                materialRegistry.addMaterial(name, values.texts(KEY_TAGS), values.text(KEY_PARENT));
            } catch (IllegalArgumentException e) {
                log.warn(
                        "{} {}: {}",
                        FactoryStrings.format("MAT_INVALID_STRUCTURE"),
                        name,
                        e.getMessage());
            }
        }
        log.debug(FactoryStrings.format("LOADED_MATERIALS"));
    }

    /**
     * Load and register tags, each after its parent.
     *
     * @param folders The content folders, in load order.
     * @param tagRegistry Where we are registering tags.
     */
    public static void loadTags(@NonNull List<Path> folders, @NonNull TagRegistry tagRegistry) {
        final Map<String, Node> tags = shared(folders, TAGS_FOLDER);
        for (String name : parentsFirst(tags)) {
            try {
                tagRegistry.addTag(
                        name, values(tags.get(name), ContentSchemas.TAG, name).text(KEY_PARENT));
            } catch (IllegalArgumentException e) {
                log.warn("Tag {} is malformed: {}", name, e.getMessage());
            }
        }
        log.debug(FactoryStrings.format("LOADED_TAGS"));
    }

    /**
     * Definitions whose names every plugin shares, such as tags: each folder's files by name, a
     * later folder's in place of an earlier one's.
     *
     * @param folders The content folders, in load order.
     * @param kind The kind's folder.
     * @return The parsed files by name, sorted.
     */
    private static SortedMap<String, Node> shared(
            @NonNull List<Path> folders, @NonNull String kind) {
        final SortedMap<String, Node> out = new TreeMap<>();
        for (Path folder : folders) {
            final ContentFolders.Listing listing =
                    ContentFolders.files(List.of(folder), kind, EXTENSION);
            warnSkipped(listing);
            for (Map.Entry<String, Path> entry : listing.files().entrySet()) {
                final String name = entry.getKey().substring(entry.getKey().indexOf(':') + 1);
                read(entry.getValue()).ifPresent(root -> out.put(name, root));
            }
        }
        return out;
    }

    /**
     * Names in an order where each comes after its parent, otherwise sorted.
     *
     * @param definitions The definitions by name, which may name a parent.
     * @return The names.
     */
    private static List<String> parentsFirst(@NonNull Map<String, Node> definitions) {
        final List<String> out = new ArrayList<>();
        final Set<String> visited = new HashSet<>();
        for (String name : new TreeSet<>(definitions.keySet())) {
            addParentsFirst(name, definitions, visited, out);
        }
        return out;
    }

    /**
     * Add a name after its parent.
     *
     * @param name The name.
     * @param definitions The definitions by name.
     * @param visited Names already added or being added, which stops at cycles.
     * @param out The order so far.
     */
    private static void addParentsFirst(
            @NonNull String name,
            @NonNull Map<String, Node> definitions,
            @NonNull Set<String> visited,
            @NonNull List<String> out) {
        if (!definitions.containsKey(name) || !visited.add(name)) {
            return;
        }
        final Node root = definitions.get(name);
        if (root.hasChild(KEY_PARENT) && root.get(KEY_PARENT) instanceof String parent) {
            addParentsFirst(parent, definitions, visited, out);
        }
        out.add(name);
    }

    /**
     * Read a definition file.
     *
     * @param file The file.
     * @return Its tree, or empty if it can't be read, with a warning.
     */
    private static Optional<Node> read(@NonNull Path file) {
        final String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn(FactoryStrings.format("FILE_NOT_FOUND", file));
            return Optional.empty();
        }
        Optional<Node> root;
        try {
            root = TreeStringSerialization.fromString(text);
        } catch (RuntimeException e) {
            root = Optional.empty();
        }
        if (root.isEmpty()) {
            log.warn("Couldn't read {}, it isn't valid KVT", file);
        }
        return root;
    }

    /**
     * Warn about files that were skipped.
     *
     * @param listing The files of a kind.
     */
    private static void warnSkipped(@NonNull ContentFolders.Listing listing) {
        for (ContentFolders.Skipped skipped : listing.skipped()) {
            log.warn("Skipped {}: {}", skipped.file(), skipped.problem());
        }
    }

    /**
     * A definition's values, read through its schema, warning about unknown fields.
     *
     * @param root The definition's tree.
     * @param schema Its kind's schema.
     * @param name The definition, for warnings.
     * @param lenient Fields whose bad values only warn, the default used in their place.
     * @return The values.
     * @throws IllegalArgumentException If another field's value is bad or a required one missing.
     */
    private static Schema.Values values(
            @NonNull Node root,
            @NonNull Schema schema,
            @NonNull String name,
            @NonNull String... lenient) {
        final Schema.Values values = KvtSchemas.read(root, schema);
        final List<String> errors = new ArrayList<>();
        for (Schema.Problem problem : values.problems()) {
            if (!problem.error() || List.of(lenient).contains(problem.field())) {
                log.warn("{} {}: {}", schema.name(), name, problem.message());
            } else {
                errors.add(problem.message());
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        return values;
    }

    /** Private constructor so that this class is not instantiated. */
    private DefinitionLoader() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
