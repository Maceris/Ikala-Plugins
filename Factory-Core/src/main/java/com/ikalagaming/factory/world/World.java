package com.ikalagaming.factory.world;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.item.ItemDefinition;
import com.ikalagaming.factory.world.registry.*;
import com.ikalagaming.util.SafeResourceLoader;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvValidationException;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Stream;

/**
 * Tracks the state of the world.
 *
 * @author Ches Burks
 */
@Slf4j
public class World {
    /** The number of blocks per side of a chunk. */
    public static final int CHUNK_WIDTH = 16;

    /**
     * The minimum y level of the world. Blocks can exist on this level, but nothing can be below
     * it.
     */
    public static final int WORLD_HEIGHT_MIN = -512;

    /**
     * The maximum y level of the world. Blocks can exist on this level, but nothing can be above
     * it.
     */
    public static final int WORLD_HEIGHT_MAX = 512;

    /** The total height of the world in blocks. */
    public static final int WORLD_HEIGHT_TOTAL = WORLD_HEIGHT_MAX - WORLD_HEIGHT_MIN;

    /** The headers that we expect in the blocks csv file. */
    private static final String[] BLOCK_HEADERS = {
        "mod_name", "identifier", "material", "tags", "register_item"
    };

    /** The headers that we expect in the items csv file. */
    private static final String[] ITEM_HEADERS = {"mod_name", "identifier", "material", "tags"};

    /** Stores all the tags. */
    @Getter private final TagRegistry tagRegistry;

    /** Stores all the materials. */
    @Getter private final MaterialRegistry materialRegistry;

    /** Stores all the item definitions. */
    @Getter private final ItemRegistry itemRegistry;

    /** Stores all the block definitions. */
    @Getter private final BlockRegistry blockRegistry;

    public World() {
        tagRegistry = new TagRegistry();
        materialRegistry = new MaterialRegistry(tagRegistry);
        itemRegistry = new ItemRegistry(tagRegistry, materialRegistry);
        blockRegistry = new BlockRegistry(tagRegistry, materialRegistry);
    }

    /**
     * Load and process blocks from disk.
     *
     * @return Whether we successfully loaded all information.
     */
    private boolean loadBlocks() {
        try (var stream = this.getClass().getResourceAsStream("/blocks.csv");
                var streamReader =
                        new InputStreamReader(
                                Objects.requireNonNull(stream), StandardCharsets.UTF_8);
                var fileReader = new BufferedReader(streamReader);
                var csvReader = new CSVReaderHeaderAware(fileReader)) {

            String[] results = csvReader.readNext(BLOCK_HEADERS);
            while (results != null) {
                var modName = results[0];
                var identifier = results[1];
                var material = results[2];
                var tagsRaw = results[3];
                boolean registerItem = Boolean.parseBoolean(results[4]);
                List<String> tags = new ArrayList<>();

                if (tagsRaw != null && tagsRaw.isEmpty()) {
                    var tagsParsed = Stream.of(tagsRaw.split(",")).map(String::trim).toList();
                    tags.addAll(tagsParsed);
                }

                var combinedName = RegistryConstants.combineName(modName, identifier);
                var definition = new BlockDefinition(modName, identifier, material, tags);
                blockRegistry.register(combinedName, definition);

                if (registerItem) {
                    var itemDefinition = new ItemDefinition(modName, identifier, material, tags);
                    itemRegistry.register(combinedName, itemDefinition);
                }

                results = csvReader.readNext(BLOCK_HEADERS);
            }

        } catch (IOException | CsvValidationException | NullPointerException e) {
            log.warn(
                    SafeResourceLoader.getString(
                            "BLOCK_INVALID_STRUCTURE", FactoryPlugin.getResourceBundle()),
                    e);
            return false;
        }
        return true;
    }

    /**
     * Load all the information from files.
     *
     * @return Whether we successfully loaded all information.
     */
    public boolean loadDefaultConfigurations() {
        // Short-circuiting will stop execution if one fails
        return loadTags() && loadMaterials() && loadBlocks() && loadItems();
    }

    /**
     * Load and process items from disk.
     *
     * @return Whether we successfully loaded all information.
     */
    private boolean loadItems() {
        try (var stream = this.getClass().getResourceAsStream("/items.csv");
                var streamReader =
                        new InputStreamReader(
                                Objects.requireNonNull(stream), StandardCharsets.UTF_8);
                var fileReader = new BufferedReader(streamReader);
                var csvReader = new CSVReaderHeaderAware(fileReader)) {

            String[] results = csvReader.readNext(ITEM_HEADERS);
            while (results != null) {
                var modName = results[0];
                var identifier = results[1];
                var material = results[2];
                var tagsRaw = results[3];
                List<String> tags = new ArrayList<>();

                if (tagsRaw != null && tagsRaw.isEmpty()) {
                    var tagsParsed = Stream.of(tagsRaw.split(",")).map(String::trim).toList();
                    tags.addAll(tagsParsed);
                }

                var combinedName = RegistryConstants.combineName(modName, identifier);

                var itemDefinition = new ItemDefinition(modName, identifier, material, tags);
                itemRegistry.register(combinedName, itemDefinition);

                results = csvReader.readNext(ITEM_HEADERS);
            }

        } catch (IOException | CsvValidationException | NullPointerException e) {
            log.warn(
                    SafeResourceLoader.getString(
                            "ITEM_INVALID_STRUCTURE", FactoryPlugin.getResourceBundle()),
                    e);
            return false;
        }
        return true;
    }

    /**
     * Load and process materials from disk.
     *
     * @return Whether we successfully loaded all information.
     */
    private boolean loadMaterials() {
        Map<String, Object> materialMap = loadYaml("/materials.yml");
        if (materialMap.isEmpty()) {
            return false;
        }
        processMaterials(materialMap);

        log.debug(
                SafeResourceLoader.getString(
                        "LOADED_MATERIALS", FactoryPlugin.getResourceBundle()));
        return true;
    }

    /**
     * Load the tags from files.
     *
     * @return Whether we were successful.
     */
    private boolean loadTags() {
        Map<String, Object> tagMap = loadYaml("/tags.yml");
        if (tagMap.isEmpty()) {
            return false;
        }
        processTags(tagMap, null);
        log.debug(SafeResourceLoader.getString("LOADED_TAGS", FactoryPlugin.getResourceBundle()));
        return true;
    }

    /**
     * Load a map structure based on the name of a data resource.
     *
     * @param resourceName The name of the resource, as a path from the data directory.
     * @return The contents of the yaml file, or an empty map in the case of an error.
     */
    private Map<String, Object> loadYaml(@NonNull String resourceName) {
        Yaml yaml = new Yaml();

        InputStream stream = this.getClass().getResourceAsStream(resourceName);
        Map<String, Object> map = yaml.load(stream);
        if (map == null) {
            log.warn(
                    SafeResourceLoader.getString("FILE_EMPTY", FactoryPlugin.getResourceBundle()),
                    resourceName);
            return new HashMap<>();
        }
        return map;
    }

    /**
     * Process a map from snakeyaml and populate the material list with the results.
     *
     * @param map The nested map structure output by snakeyaml.
     */
    private void processMaterials(@NonNull Map<String, Object> map) {
        try {
            for (Entry<String, Object> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (!(value instanceof Map<?, ?>)) {
                    log.warn(
                            SafeResourceLoader.getString(
                                    "MAT_INVALID_STRUCTURE", FactoryPlugin.getResourceBundle()));
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
            log.warn(
                    SafeResourceLoader.getString(
                            "MAT_INVALID_STRUCTURE", FactoryPlugin.getResourceBundle()));
        }
    }

    /**
     * Process a map from snakeyaml and populate the tag list with the results.
     *
     * @param map The nested map structure output by snakeyaml.
     * @param parent The name of the parent tag, which is null for root tags.
     */
    private void processTags(@NonNull Map<String, Object> map, String parent) {
        for (Entry<String, Object> entry : map.entrySet()) {
            final String tagName = entry.getKey();
            tagRegistry.addTag(tagName, parent);

            Object value = entry.getValue();
            if (value instanceof Map<?, ?> child) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) child;
                processTags(cast, tagName);
            }
        }
    }
}
