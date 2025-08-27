package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.frontend.Buffer;
import com.ikalagaming.graphics.frontend.BufferUtil;
import com.ikalagaming.graphics.frontend.Material;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A cache of materials so that we can reuse them. */
@Getter
public class MaterialCache {
    /** The default index to use for materials. */
    public static final int DEFAULT_MATERIAL_INDEX = 0;

    /**
     * The actual cache of materials.
     *
     * @return The list of materials in the cache. This should not be modified directly.
     * @see #addMaterial(Material)
     */
    private final List<Material> materialsList;

    @Setter private boolean dirty;

    @Setter private Buffer materialBuffer;

    /** Set up a new cache with a default material. */
    public MaterialCache() {
        materialsList = Collections.synchronizedList(new ArrayList<>());
        Material defaultMaterial = new Material();
        materialsList.add(defaultMaterial);
        dirty = true;
        materialBuffer = BufferUtil.INSTANCE.createBuffer(Buffer.Type.SHADER_STORAGE);
    }

    /**
     * Add a new material to the cache and give an index to the material. Will allow duplicates.
     *
     * @param material The material to add to the cache.
     * @return The index of the newly added material, which is only valid within this cache
     *     instance.
     * @see #getMaterialIndex(Material)
     */
    public int addMaterial(@NonNull Material material) {
        final int assignedIndex = materialsList.size();
        materialsList.add(material);
        dirty = true;
        return assignedIndex;
    }

    /**
     * Fetch the material at a given index. If an invalid index is provided, the default material is
     * returned.
     *
     * @param index The index to look up.
     * @return The material at that index.
     */
    public Material getMaterial(int index) {
        if (index < 0 || index > materialsList.size()) {
            return materialsList.get(MaterialCache.DEFAULT_MATERIAL_INDEX);
        }
        return materialsList.get(index);
    }

    /**
     * Fetch the index of a material. Returns -1 if the material is not found.
     *
     * @param material The material to look for.
     * @return The index of the material, or -1 if not found.
     */
    public int getMaterialIndex(@NonNull Material material) {
        for (int i = 0; i < materialsList.size(); ++i) {
            if (material.equals(materialsList.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
