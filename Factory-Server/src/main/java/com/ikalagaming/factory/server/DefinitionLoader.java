package com.ikalagaming.factory.server;

import com.ikalagaming.factory.FactoryServerPlugin;
import com.ikalagaming.factory.item.ItemDefinition;
import com.ikalagaming.factory.registry.*;
import com.ikalagaming.factory.world.BlockDefinition;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.util.SafeResourceLoader;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvValidationException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class DefinitionLoader {

    /** The headers that we expect in the blocks csv file. */
    private static final String[] BLOCK_HEADERS = {
        "mod_name", "identifier", "material", "tags", "register_item"
    };

    /** The headers that we expect in the items csv file. */
    private static final String[] ITEM_HEADERS = {"mod_name", "identifier", "material", "tags"};

    /**
     * Load and process blocks from disk.
     *
     * @param blockRegistry Where we are registering the blocks.
     * @param itemRegistry Where we are registering the corresponding block items.
     */
    public static void loadBlocks(
            @NonNull BlockRegistry blockRegistry, @NonNull ItemRegistry itemRegistry) {
        File blocks =
                PluginFolder.getResource(
                        FactoryServerPlugin.PLUGIN_NAME,
                        PluginFolder.ResourceType.DATA,
                        "blocks.csv");

        try (var stream = new FileInputStream(blocks);
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
                            "BLOCK_INVALID_STRUCTURE", FactoryServerPlugin.getResourceBundle()),
                    e);
        }
    }

    /**
     * Load and process items from disk.
     *
     * @param itemRegistry Where we are registering items.
     */
    public static void loadItems(@NonNull ItemRegistry itemRegistry) {
        File items =
                PluginFolder.getResource(
                        FactoryServerPlugin.PLUGIN_NAME,
                        PluginFolder.ResourceType.DATA,
                        "items.csv");
        try (var stream = new FileInputStream(items);
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
                            "ITEM_INVALID_STRUCTURE", FactoryServerPlugin.getResourceBundle()),
                    e);
        }
    }

    /**
     * Load and process materials from disk.
     *
     * @param materialRegistry Where we are registering materials.
     */
    public static void loadMaterials(@NonNull MaterialRegistry materialRegistry) {
        File materials =
                PluginFolder.getResource(
                        FactoryServerPlugin.PLUGIN_NAME,
                        PluginFolder.ResourceType.DATA,
                        "materials.yml");
        Map<String, Object> materialMap = loadYaml(materials);
        if (materialMap.isEmpty()) {
            return;
        }
        processMaterials(materialMap, materialRegistry);

        log.debug(
                SafeResourceLoader.getString(
                        "LOADED_MATERIALS", FactoryServerPlugin.getResourceBundle()));
    }

    /**
     * Load the tags from files.
     *
     * @param tagRegistry Where we are registering tags.
     */
    public static void loadTags(@NonNull TagRegistry tagRegistry) {
        File tags =
                PluginFolder.getResource(
                        FactoryServerPlugin.PLUGIN_NAME,
                        PluginFolder.ResourceType.DATA,
                        "tags.yml");

        Map<String, Object> tagMap = loadYaml(tags);
        if (tagMap.isEmpty()) {
            return;
        }
        processTags(tagMap, null, tagRegistry);
        log.debug(
                SafeResourceLoader.getString(
                        "LOADED_TAGS", FactoryServerPlugin.getResourceBundle()));
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
                log.warn(
                        SafeResourceLoader.getString(
                                "FILE_EMPTY", FactoryServerPlugin.getResourceBundle()),
                        file.getAbsolutePath());
                return new HashMap<>();
            }
        } catch (IOException e) {
            log.warn(
                    SafeResourceLoader.getString(
                            "FILE_NOT_FOUND", FactoryServerPlugin.getResourceBundle()),
                    file.getAbsolutePath());
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
                    log.warn(
                            SafeResourceLoader.getString(
                                    "MAT_INVALID_STRUCTURE",
                                    FactoryServerPlugin.getResourceBundle()));
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
                            "MAT_INVALID_STRUCTURE", FactoryServerPlugin.getResourceBundle()));
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
