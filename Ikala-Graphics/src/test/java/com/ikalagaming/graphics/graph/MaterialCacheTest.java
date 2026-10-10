package com.ikalagaming.graphics.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** Material indices stay put for as long as their materials are in the cache. */
class MaterialCacheTest {

    @Test
    void theDefaultMaterialIsAlwaysFirst() {
        MaterialCache cache = new MaterialCache();
        assertEquals(1, cache.getMaterialCount());
        assertEquals(0, cache.getMaterialIndex(MaterialCache.DEFAULT_MATERIAL));
        assertEquals(0, cache.getMaterialIndex(null));
        assertEquals(0, cache.getMaterialIndex(new Material()), "Not in the cache");
        cache.removeMaterial(MaterialCache.DEFAULT_MATERIAL);
        assertSame(MaterialCache.DEFAULT_MATERIAL, cache.getMaterial(0));
    }

    @Test
    void removingAMaterialMovesNoOther() {
        MaterialCache cache = new MaterialCache();
        Material a = new Material();
        Material b = new Material();
        Material c = new Material();
        assertEquals(1, cache.addMaterial(a));
        assertEquals(2, cache.addMaterial(b));
        assertEquals(3, cache.addMaterial(c));

        cache.removeMaterial(a);
        assertEquals(2, cache.getMaterialIndex(b));
        assertEquals(3, cache.getMaterialIndex(c));
        assertSame(c, cache.getMaterial(3));
        assertEquals(0, cache.getMaterialIndex(a), "Removed");
        // The empty slot reads as the default, and still counts
        assertSame(MaterialCache.DEFAULT_MATERIAL, cache.getMaterial(1));
        assertEquals(4, cache.getMaterialCount());
    }

    @Test
    void freedSlotsAreReusedLowestFirst() {
        MaterialCache cache = new MaterialCache();
        Material a = new Material();
        Material b = new Material();
        Material c = new Material();
        cache.addMaterial(a);
        cache.addMaterial(b);
        cache.addMaterial(c);
        cache.removeMaterial(c);
        cache.removeMaterial(a);

        Material d = new Material();
        Material e = new Material();
        Material f = new Material();
        assertEquals(1, cache.addMaterial(d));
        assertEquals(3, cache.addMaterial(e));
        assertEquals(4, cache.addMaterial(f), "No free slots left, so a new one");
        assertEquals(5, cache.getMaterialCount());
        assertEquals(2, cache.getMaterialIndex(b));
    }

    @Test
    void addingTwiceKeepsOneIndex() {
        MaterialCache cache = new MaterialCache();
        Material a = new Material();
        assertEquals(1, cache.addMaterial(a));
        assertEquals(1, cache.addMaterial(a));
        assertEquals(2, cache.getMaterialCount());
        // Removing it once removes it
        cache.removeMaterial(a);
        assertEquals(0, cache.getMaterialIndex(a));
        cache.removeMaterial(a);
        assertEquals(2, cache.getMaterialCount());
    }

    @Test
    void indicesOutOfRangeReadAsTheDefault() {
        MaterialCache cache = new MaterialCache();
        cache.addMaterial(new Material());
        assertSame(MaterialCache.DEFAULT_MATERIAL, cache.getMaterial(-1));
        assertSame(MaterialCache.DEFAULT_MATERIAL, cache.getMaterial(2));
        assertSame(MaterialCache.DEFAULT_MATERIAL, cache.getMaterial(100));
    }
}
