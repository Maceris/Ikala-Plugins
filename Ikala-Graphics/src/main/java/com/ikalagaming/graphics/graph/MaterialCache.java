/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.frontend.Material;

import lombok.Getter;
import lombok.NonNull;

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
     * @return The list of materials in the cache.
     */
    private final List<Material> materialsList;

    /** Set up a new cache with a default material. */
    public MaterialCache() {
        materialsList = Collections.synchronizedList(new ArrayList<>());
        Material defaultMaterial = new Material();
        materialsList.add(defaultMaterial);
    }

    /**
     * Add a new material to the cache and give an index to the material. If we can find a valid
     * material that matches the provided one, we reuse that one instead of adding a duplicate one.
     *
     * @param material The material to add to the cache.
     * @return The index of the newly added material, which is only valid within this cache
     *     instance.
     */
    public int addMaterial(@NonNull Material material) {
        for (int i = 0; i < materialsList.size(); ++i) {
            if (material.equals(materialsList.get(i))) {
                return i;
            }
        }
        final int assignedIndex = materialsList.size();
        materialsList.add(material);
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
}
