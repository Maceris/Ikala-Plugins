package com.ikalagaming.factory.registry;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.FactoryStrings;
import com.ikalagaming.factory.item.ItemDefinition;
import com.ikalagaming.factory.world.BlockDefinition;
import com.ikalagaming.launcher.PluginFolder;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvValidationException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Loads the game's definitions from a data folder into registries: {@value #TAGS_FILE}, {@value
 * #MATERIALS_FILE}, {@value #BLOCKS_FILE} and {@value #ITEMS_FILE}. They are shared content that
 * the server, the client and the tools all read, so they live in Factory-Core's data folder,
 * alongside the world generation data that names the blocks.
 *
 * <p>Load order matters, since each kind refers to the ones before it: tags, then materials, then
 * blocks (which register their items), then items. {@link #loadAll} does that.
 */
@Slf4j
public class DefinitionLoader {

    /** The tag definitions. */
    public static final String TAGS_FILE = "tags.yml";

    /** The material definitions. */
    public static final String MATERIALS_FILE = "materials.yml";

    /** The block definitions. */
    public static final String BLOCKS_FILE = "blocks.csv";

    /** The item definitions. */
    public static final String ITEMS_FILE = "items.csv";

    /** The headers that we expect in the blocks csv file. */
    private static final String[] BLOCK_HEADERS = {
        "mod_name", "identifier", "material", "tags", "register_item"
    };

    /** The headers that we expect in the items csv file. */
    private static final String[] ITEM_HEADERS = {"mod_name", "identifier", "material", "tags"};

    /** Where several tags in one cell are split. */
    private static final String TAG_SEPARATOR = ",";

    /**
     * Factory-Core's data folder, where the game's definitions live.
     *
     * @return The folder.
     */
    public static Path dataFolder() {
        return PluginFolder.getResource(
                        FactoryPlugin.PLUGIN_NAME, PluginFolder.ResourceType.DATA, "")
                .toPath();
    }

    /**
     * Load every definition from a data folder, in order.
     *
     * @param dataFolder The folder holding the definition files.
     * @param registries Where to register them.
     */
    public static void loadAll(@NonNull Path dataFolder, @NonNull Registries registries) {
        loadTags(dataFolder, registries.getTagRegistry());
        loadMaterials(dataFolder, registries.getMaterialRegistry());
        loadBlocks(dataFolder, registries.getBlockRegistry(), registries.getItemRegistry());
        loadItems(dataFolder, registries.getItemRegistry());
    }

    /**
     * The block IDs a data folder defines, for checking world generation data against.
     *
     * @param dataFolder The folder holding the definition files.
     * @return The block IDs, or empty if the folder defines none.
     */
    public static Set<String> blockIds(@NonNull Path dataFolder) {
        if (!Files.isRegularFile(dataFolder.resolve(BLOCKS_FILE))) {
            return Set.of();
        }
        Registries registries = new Registries();
        loadAll(dataFolder, registries);
        return new TreeSet<>(registries.getBlockRegistry().getNames());
    }

    /**
     * An optional cell: empty means not given.
     *
     * @param cell The cell's text.
     * @return The text, or null if the cell is empty.
     */
    private static String optional(String cell) {
        return cell == null || cell.isBlank() ? null : cell.trim();
    }

    /**
     * Split a cell of tags.
     *
     * @param tagsRaw The cell, possibly empty.
     * @return The tags.
     */
    private static List<String> parseTags(String tagsRaw) {
        if (tagsRaw == null || tagsRaw.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Stream.of(tagsRaw.split(TAG_SEPARATOR)).map(String::trim).toList());
    }

    /**
     * Load and process blocks from disk.
     *
     * @param dataFolder The folder holding the definition files.
     * @param blockRegistry Where we are registering the blocks.
     * @param itemRegistry Where we are registering the corresponding block items.
     */
    public static void loadBlocks(
            @NonNull Path dataFolder,
            @NonNull BlockRegistry blockRegistry,
            @NonNull ItemRegistry itemRegistry) {
        final File blocks = dataFolder.resolve(BLOCKS_FILE).toFile();
        try (var stream = new FileInputStream(blocks);
                var streamReader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                var fileReader = new BufferedReader(streamReader);
                var csvReader = new CSVReaderHeaderAware(fileReader)) {

            String[] results = csvReader.readNext(BLOCK_HEADERS);
            while (results != null) {
                var modName = results[0];
                var identifier = results[1];
                var material = optional(results[2]);
                var tags = parseTags(results[3]);
                boolean registerItem = Boolean.parseBoolean(results[4]);

                var combinedName = RegistryConstants.combineName(modName, identifier);
                var definition = new BlockDefinition(modName, identifier, material, tags);
                blockRegistry.register(combinedName, definition);

                if (registerItem) {
                    var itemDefinition = new ItemDefinition(modName, identifier, material, tags);
                    itemRegistry.register(combinedName, itemDefinition);
                }

                results = csvReader.readNext(BLOCK_HEADERS);
            }
            log.debug(FactoryStrings.format("LOADED_BLOCKS"));
        } catch (IOException | CsvValidationException | NullPointerException e) {
            log.warn(FactoryStrings.format("BLOCK_INVALID_STRUCTURE"), e);
        }
    }

    /**
     * Load and process items from disk.
     *
     * @param dataFolder The folder holding the definition files.
     * @param itemRegistry Where we are registering items.
     */
    public static void loadItems(@NonNull Path dataFolder, @NonNull ItemRegistry itemRegistry) {
        final File items = dataFolder.resolve(ITEMS_FILE).toFile();
        try (var stream = new FileInputStream(items);
                var streamReader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                var fileReader = new BufferedReader(streamReader);
                var csvReader = new CSVReaderHeaderAware(fileReader)) {

            String[] results = csvReader.readNext(ITEM_HEADERS);
            while (results != null) {
                var modName = results[0];
                var identifier = results[1];
                var material = optional(results[2]);
                var tags = parseTags(results[3]);

                var combinedName = RegistryConstants.combineName(modName, identifier);
                var itemDefinition = new ItemDefinition(modName, identifier, material, tags);
                itemRegistry.register(combinedName, itemDefinition);

                results = csvReader.readNext(ITEM_HEADERS);
            }
            log.debug(FactoryStrings.format("LOADED_ITEMS"));
        } catch (IOException | CsvValidationException | NullPointerException e) {
            log.warn(FactoryStrings.format("ITEM_INVALID_STRUCTURE"), e);
        }
    }

    /**
     * Load and process materials from disk.
     *
     * @param dataFolder The folder holding the definition files.
     * @param materialRegistry Where we are registering materials.
     */
    public static void loadMaterials(
            @NonNull Path dataFolder, @NonNull MaterialRegistry materialRegistry) {
        Map<String, Object> materialMap = loadYaml(dataFolder.resolve(MATERIALS_FILE).toFile());
        if (materialMap.isEmpty()) {
            return;
        }
        processMaterials(materialMap, materialRegistry);
        log.debug(FactoryStrings.format("LOADED_MATERIALS"));
    }

    /**
     * Load the tags from files.
     *
     * @param dataFolder The folder holding the definition files.
     * @param tagRegistry Where we are registering tags.
     */
    public static void loadTags(@NonNull Path dataFolder, @NonNull TagRegistry tagRegistry) {
        Map<String, Object> tagMap = loadYaml(dataFolder.resolve(TAGS_FILE).toFile());
        if (tagMap.isEmpty()) {
            return;
        }
        processTags(tagMap, null, tagRegistry);
        log.debug(FactoryStrings.format("LOADED_TAGS"));
    }

    /**
     * Load a map structure based on the name of a data resource.
     *
     * @param file The file to load from.
     * @return The contents of the yaml file, or an empty map in the case of an error.
     */
    private static Map<String, Object> loadYaml(@NonNull File file) {
        Yaml yaml = new Yaml();
        Map<String, Object> results;

        try (InputStream stream = new FileInputStream(file)) {
            results = yaml.load(stream);
            if (results == null) {
                log.warn(FactoryStrings.format("FILE_EMPTY", file.getAbsolutePath()));
                return new HashMap<>();
            }
        } catch (IOException e) {
            log.warn(FactoryStrings.format("FILE_NOT_FOUND", file.getAbsolutePath()));
            return new HashMap<>();
        }

        return results;
    }

    /**
     * Process a map from snakeyaml and populate the material list with the results.
     *
     * @param map The nested map structure output by snakeyaml.
     * @param materialRegistry The material registry to register materials with.
     */
    private static void processMaterials(
            @NonNull Map<String, Object> map, @NonNull MaterialRegistry materialRegistry) {
        try {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (!(value instanceof Map<?, ?>)) {
                    log.warn(FactoryStrings.format("MAT_INVALID_STRUCTURE"));
                    return;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> contents = (Map<String, Object>) value;

                @SuppressWarnings("unchecked")
                List<String> tagNames = (List<String>) contents.get("tags");
                String parent = (String) contents.get("parent");

                materialRegistry.addMaterial(entry.getKey(), tagNames, parent);
            }
        } catch (Exception e) {
            log.warn(FactoryStrings.format("MAT_INVALID_STRUCTURE"));
        }
    }

    /**
     * Process a map from snakeyaml and populate the tag list with the results.
     *
     * @param map The nested map structure output by snakeyaml.
     * @param parent The name of the parent tag, which is null for root tags.
     * @param tagRegistry The tag registry to register tags with.
     */
    private static void processTags(
            @NonNull Map<String, Object> map, String parent, @NonNull TagRegistry tagRegistry) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            final String tagName = entry.getKey();
            tagRegistry.addTag(tagName, parent);

            Object value = entry.getValue();
            if (value instanceof Map<?, ?> child) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) child;
                processTags(cast, tagName, tagRegistry);
            }
        }
    }

    /** Private constructor so that this class is not instantiated. */
    private DefinitionLoader() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
