package com.ikalagaming.factory.world;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

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

    private TagRegistry tagRegistry = new TagRegistry();

    /** Used to store and look materials up by name. */
    private Map<String, Material> materials = new HashMap<>();

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
        if (hasMaterial(name)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "MAT_DUPLICATE", FactoryPlugin.getResourceBundle(), name));
            return false;
        }
        if (parentName != null && !hasMaterial(parentName)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "MAT_MISSING_PARENT",
                            FactoryPlugin.getResourceBundle(),
                            name,
                            parentName));
            return false;
        }

        Material parent = null;
        if (parentName != null) {
            parent = findMaterial(parentName).orElse(null);
        }

        var result = new Material(name, parent);

        if (parent != null) {
            result.tags().addAll(parent.tags());
        }
        if (materialTags != null) {
            for (String tagName : materialTags) {
                Optional<Tag> maybeTag = tagRegistry.findTag(tagName);
                if (maybeTag.isEmpty()) {
                    log.warn(
                            SafeResourceLoader.getStringFormatted(
                                    "TAG_MISSING", FactoryPlugin.getResourceBundle(), tagName));
                    return false;
                }

                result.tags().add(maybeTag.get());
            }
        }
        result.deduplicateTags();

        materials.put(name, result);

        return true;
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
     * Add a tag to the list of tags. If the tag already exists, this will fail.
     *
     * @param tag The name of the tag.
     * @return Whether we successfully added the tag.
     */
    public boolean addTag(@NonNull String tag) {
        return tagRegistry.addTag(tag, null);
    }

    /**
     * Add a tag to the list of tags. If the tag already exists, or the parent does not, this will
     * fail.
     *
     * @param tag The name of the tag.
     * @param parentName The name of the parent tag, or null if there is no parent tag.
     * @return Whether we successfully added the tag.
     */
    public boolean addTag(@NonNull String tag, String parentName) {
        return tagRegistry.addTag(tag, parentName);
    }

    /**
     * Look through the list of materials and search for one with the given name.
     *
     * @param materialName The name of the material to look for.
     * @return The tag with the given name.
     * @throws NullPointerException If the material name is null.
     */
    public Optional<Material> findMaterial(@NonNull String materialName) {
        if (materials.containsKey(materialName)) {
            return Optional.of(materials.get(materialName));
        }
        log.error(
                SafeResourceLoader.getString("MATERIAL_MISSING", FactoryPlugin.getResourceBundle()),
                materialName);
        return Optional.empty();
    }

    /**
     * Look through the list of tags and search for one with the given name.
     *
     * @param tagName The name of the tag to look for.
     * @return The tag with the given name.
     * @throws NullPointerException If the tag name is null.
     */
    public Optional<Tag> findTag(@NonNull String tagName) {
        return tagRegistry.findTag(tagName);
    }

    /**
     * Fetch an unmodifiable copy of the list of material names that currently exist.
     *
     * @return An unmodifiable copy of the material names.
     */
    public List<String> getMaterialNames() {
        return List.copyOf(materials.keySet());
    }

    /**
     * Fetch an unmodifiable copy of the list of materials that currently exist.
     *
     * @return An unmodifiable copy of the material values.
     */
    public List<Material> getMaterials() {
        return List.copyOf(materials.values());
    }

    /**
     * Fetch an unmodifiable copy of the list of tag names that currently exist.
     *
     * @return An unmodifiable copy of the tag names.
     */
    public List<String> getTagNames() {
        return tagRegistry.getTagNames();
    }

    /**
     * Fetch an unmodifiable copy of the list of tags that currently exist.
     *
     * @return An unmodifiable copy of the tag values.
     */
    public List<Tag> getTags() {
        return tagRegistry.getTags();
    }

    /**
     * Checks if the specified material exists.
     *
     * @param material The material we are looking for.
     * @return Whether the material exists.
     */
    public boolean hasMaterial(@NonNull String material) {
        return materials.containsKey(material);
    }

    /**
     * Check whether the new or exsting tag is more specific, and keep that one in the material.
     *
     * @param material The material we are interested in.
     * @param newTag The tag that we might want to add to the material.
     * @param oldTag The tag that is already in the materials tag list.
     */
    private void keepMoreSpecificTag(
            @NonNull Material material, @NonNull Tag newTag, @NonNull Tag oldTag) {
        boolean oldIsParent = false;
        Tag parent = newTag.parent();
        while (parent != null) {
            if (parent.equals(newTag)) {
                oldIsParent = true;
                break;
            }
            parent = parent.parent();
        }
        if (oldIsParent) {
            material.tags().remove(oldTag);
            material.tags().add(newTag);
        }
        // NOTE(ches) otherwise, do nothing and keep the old one.
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
     * @param material The material we are checking.
     * @param tag The name of the tag we are looking for.
     * @return True if the material has the tag, false if it does not or either tag or material
     *     don't exist.
     */
    public boolean materialHasTag(@NonNull Material material, @NonNull String tag) {
        return Tag.containsTag(tag, material.tags());
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
        if (!tagRegistry.tagExists(tag) || !materials.containsKey(materialName)) {
            return false;
        }

        return materialHasTag(materials.get(materialName), tag);
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

                this.addMaterial(entry.getKey(), tagNames, parent);
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
