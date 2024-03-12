package com.ikalagaming.factory.world;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class MaterialRegistry {

    private final TagRegistry tagRegistry;

    /** Used to store and look materials up by name. */
    private final Map<String, Material> materials = new HashMap<>();

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
     * Checks if the specified material exists.
     *
     * @param material The material we are looking for.
     * @return Whether the material exists.
     */
    public boolean hasMaterial(@NonNull String material) {
        return materials.containsKey(material);
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
}
