package com.ikalagaming.factory.world;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;

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

    /** Stores all the tags. */
    @Getter private TagRegistry tagRegistry = new TagRegistry();

    /** Stores all the materials. */
    @Getter private MaterialRegistry materialRegistry = new MaterialRegistry(tagRegistry);

    /**
     * Create a new material record without any tags or parent material.
     *
     * @param name The name of the material.
     * @return Whether we successfully created a material.
     */
    public boolean addMaterial(@NonNull String name) {
        return this.addMaterial(name, null, null);
    }

    /**
     * Create a new material record without any parent material.
     *
     * @param name The name of the material.
     * @param materialTags The names of all the tags that apply to this material. If any of these do
     *     not already exist, creating the material will fail.
     * @return Whether we successfully created a material.
     */
    public boolean addMaterial(@NonNull String name, @NonNull List<String> materialTags) {
        return this.addMaterial(name, materialTags, null);
    }

    /**
     * Create a new material record.
     *
     * @param name The name of the material.
     * @param materialTags The names of all the tags that apply to this material. If any of these do
     *     not already exist, creating the material will fail.
     * @param parentName The name of the parent material, which must already exist if not null.
     * @return Whether we successfully created a material.
     */
    public boolean addMaterial(@NonNull String name, List<String> materialTags, String parentName) {
        return materialRegistry.addMaterial(name, materialTags, parentName);
    }

    /**
     * Create a new material record without any tags.
     *
     * @param name The name of the material.
     * @param parentName The name of the parent material, which must already exist if not null.
     * @return Whether we successfully created a material.
     */
    public boolean addMaterial(@NonNull String name, @NonNull String parentName) {
        return this.addMaterial(name, null, parentName);
    }

    /**
     * Look through the list of materials and search for one with the given name.
     *
     * @param materialName The name of the material to look for.
     * @return The tag with the given name.
     * @throws NullPointerException If the material name is null.
     */
    public Optional<Material> findMaterial(@NonNull String materialName) {
        return materialRegistry.findMaterial(materialName);
    }

    /**
     * Fetch an unmodifiable copy of the list of materials that currently exist.
     *
     * @return An unmodifiable copy of the material values.
     */
    public List<Material> getMaterials() {
        return materialRegistry.getMaterials();
    }

    /**
     * Checks if the specified material exists.
     *
     * @param material The material we are looking for.
     * @return Whether the material exists.
     */
    public boolean hasMaterial(@NonNull String material) {
        return materialRegistry.hasMaterial(material);
    }

    /**
     * Load all the information from files.
     *
     * @return Whether we successfully loaded all information.
     */
    public boolean loadDefaultConfigurations() {
        // Short-circuiting will stop execution if one fails
        return loadTags() && loadMaterials();
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
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) yaml.load(stream);
        if (map == null) {
            log.warn(
                    SafeResourceLoader.getString("FILE_EMPTY", FactoryPlugin.getResourceBundle()),
                    resourceName);
            return new HashMap<>();
        }
        return map;
    }

    /**
     * Checks if the material has the given tag.
     *
     * @param materialName The material name we are checking against.
     * @param tag The name of the tag we are looking for.
     * @return True if the material has the tag, false if it does not or either tag or material
     *     don't exist.
     */
    public boolean materialHasTag(@NonNull String materialName, @NonNull String tag) {
        return materialRegistry.materialHasTag(materialName, tag);
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

    /**
     * Checks if the specified tag exists.
     *
     * @param tag The tag we are looking for.
     * @return Whether the tag exists.
     */
    public boolean tagExists(@NonNull String tag) {
        return tagRegistry.tagExists(tag);
    }
}
