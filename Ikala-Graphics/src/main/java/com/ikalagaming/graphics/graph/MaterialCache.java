package com.ikalagaming.graphics.graph;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.Synchronized;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * The materials the renderer knows about, each at an index that shaders and GPU tables refer to.
 *
 * <p>A material keeps its index for as long as it is in the cache. Indices end up in places that
 * aren't rewritten when the cache changes, like the instance table's material overrides and the
 * vertices of baked sections, so removing a material never moves another. Its slot reads as the
 * default material until a later material takes it, the lowest free slot first.
 */
public class MaterialCache {

    /** The default material, always at index 0. */
    public static final Material DEFAULT_MATERIAL = new Material();

    /** The material in each slot, null where one was removed and nothing has taken it since. */
    private final List<Material> slots = new ArrayList<>();

    /** Each material's slot. */
    private final Map<Material, Integer> indices = new HashMap<>();

    /** The slots freed by removals, to reuse lowest first. */
    private final TreeSet<Integer> freeSlots = new TreeSet<>();

    /**
     * Whether the materials changed since the renderer last looked. -- GETTER -- Whether the
     * materials changed. -- SETTER -- Note whether the materials changed.
     *
     * @param dirty Whether they changed.
     * @return True if they changed.
     */
    @Getter @Setter private volatile boolean dirty;

    /** Set up a cache holding only the default material. */
    public MaterialCache() {
        addMaterial(DEFAULT_MATERIAL);
        dirty = true;
    }

    /**
     * Add a material, giving it an index. Adding one that is already in the cache does nothing.
     *
     * @param material The material to add.
     * @return Its index.
     * @see #getMaterialIndex(Material)
     */
    @Synchronized
    public int addMaterial(@NonNull Material material) {
        final Integer existing = indices.get(material);
        if (existing != null) {
            return existing;
        }
        final int index;
        if (freeSlots.isEmpty()) {
            index = slots.size();
            slots.add(material);
        } else {
            index = freeSlots.pollFirst();
            slots.set(index, material);
        }
        indices.put(material, index);
        dirty = true;
        return index;
    }

    /**
     * Remove a material. No other material's index changes; its slot reads as the default material
     * until a later material takes it. Whatever still refers to the removed material's index should
     * stop doing so first, or it will show whatever takes the slot. The default material can't be
     * removed.
     *
     * @param material The material to remove.
     */
    @Synchronized
    public void removeMaterial(@NonNull Material material) {
        if (material == DEFAULT_MATERIAL) {
            return;
        }
        final Integer index = indices.remove(material);
        if (index == null) {
            return;
        }
        slots.set(index, null);
        freeSlots.add(index);
        dirty = true;
    }

    /**
     * Fetch the material at an index.
     *
     * @param index The index.
     * @return The material there, or the default material if the index is out of range or its
     *     material was removed.
     */
    @Synchronized
    public Material getMaterial(int index) {
        if (index < 0 || index >= slots.size()) {
            return DEFAULT_MATERIAL;
        }
        final Material material = slots.get(index);
        return material == null ? DEFAULT_MATERIAL : material;
    }

    /**
     * How many indices are in use, which is one more than the highest. Slots of removed materials
     * count, so every index handed out stays inside it.
     *
     * @return The number of slots.
     */
    @Synchronized
    public int getMaterialCount() {
        return slots.size();
    }

    /**
     * Fetch the index of a material.
     *
     * @param material The material to look for.
     * @return Its index, or 0, the default material, if it is null or not in the cache.
     */
    @Synchronized
    public int getMaterialIndex(@Nullable Material material) {
        if (material == null) {
            return 0;
        }
        final Integer index = indices.get(material);
        return index == null ? 0 : index;
    }

    /**
     * Forget every material, when the scene is torn down. The cache should not be used afterward.
     * Material textures belong to the plugins that loaded them, so they are not deleted here.
     */
    @Synchronized
    public void cleanup() {
        slots.clear();
        indices.clear();
        freeSlots.clear();
    }
}
