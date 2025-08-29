package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.frontend.Buffer;
import com.ikalagaming.graphics.frontend.BufferUtil;
import com.ikalagaming.graphics.frontend.Material;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.Synchronized;

import java.util.*;

/** A cache of materials so that we can reuse them. */
public class MaterialCache {

    /** The default material. */
    public static final Material DEFAULT_MATERIAL = new Material();

    /**
     * The actual cache of materials.
     *
     * @return The list of materials in the cache. This should not be modified directly, it will
     *     break a lot of things.
     * @see #addMaterial(Material)
     * @see #removeMaterial(Material)
     */
    private final List<Material> materialsList;

    private final Map<Material, Integer> materialLookup;

    @Getter @Setter private boolean dirty;

    @Getter private final Buffer materialBuffer;

    /** Set up a new cache with a default material. */
    public MaterialCache() {
        materialsList = Collections.synchronizedList(new ArrayList<>());
        materialLookup = Collections.synchronizedMap(new HashMap<>());
        addMaterial(DEFAULT_MATERIAL);
        dirty = true;
        materialBuffer = BufferUtil.INSTANCE.createBuffer(Buffer.Type.SHADER_STORAGE);
    }

    /**
     * Add a new material to the cache and give an index to the material.
     *
     * @param material The material to add to the cache.
     * @see #getMaterialIndex(Material)
     */
    @Synchronized
    public void addMaterial(@NonNull Material material) {
        final int assignedIndex = materialsList.size();
        materialsList.add(material);
        materialLookup.put(material, assignedIndex);
        dirty = true;
    }

    /**
     * Remove a material from the cache. This will require any material indexes and buffers to need
     * regenerating, and is expensive, should generally be avoided. The default material cannot be
     * removed.
     *
     * @param material The material to remove.
     */
    @Synchronized
    public void removeMaterial(@NonNull Material material) {
        if (material == MaterialCache.DEFAULT_MATERIAL) {
            return;
        }
        materialsList.remove(material);
        materialLookup.clear();
        for (int i = 0; i < materialsList.size(); ++i) {
            materialLookup.put(materialsList.get(i), i);
        }
        dirty = true;
    }

    /**
     * Fetch the material at a given index. If an invalid index is provided, the default material is
     * returned.
     *
     * @param index The index to look up.
     * @return The material at that index.
     */
    @Synchronized
    public Material getMaterial(int index) {
        if (index < 0 || index > materialsList.size()) {
            return MaterialCache.DEFAULT_MATERIAL;
        }
        return materialsList.get(index);
    }

    /**
     * Fetch the number of materials in the cache.
     *
     * @return The total number of materials.
     */
    @Synchronized
    public int getMaterialCount() {
        return materialsList.size();
    }

    /**
     * Fetch the index of a material. Returns 0 if the material is not found.
     *
     * @param material The material to look for.
     * @return The index of the material, or 0 if not found.
     */
    @Synchronized
    public int getMaterialIndex(Material material) {
        return Optional.ofNullable(material).map(materialLookup::get).orElse(0);
    }
}
