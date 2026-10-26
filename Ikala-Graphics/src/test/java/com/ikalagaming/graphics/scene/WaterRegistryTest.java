package com.ikalagaming.graphics.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.WaterHandle;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Water tiles, and the registry plugins place them in. */
class WaterRegistryTest {

    /** Who owns the tiles. */
    private static final String OWNER = "test";

    /** Someone else. */
    private static final String OTHER = "other";

    /** A small flat tile. */
    private static final WaterTile POND =
            WaterTile.flat(new Vector3d(4, 60, -8), 1, 4, 3, 0.9f, WaterBody.OCEAN);

    /** A flat tile has a height at every corner, water in every cell, and copies its arrays. */
    @Test
    void testTile() {
        assertEquals(5 * 4, POND.heights().length);
        assertEquals(0.9f, POND.heightAt(4, 3));
        assertTrue(POND.isWater(3, 2));
        assertEquals(12, POND.waterCells());
        POND.heights()[0] = 99;
        assertEquals(0.9f, POND.heightAt(0, 0), "handed out as a copy");

        final boolean[] mask = new boolean[2 * 2];
        mask[1] = true;
        final WaterTile masked =
                new WaterTile(new Vector3d(), 2, 2, 2, new float[9], null, mask, WaterBody.MUDDY);
        mask[2] = true;
        assertEquals(1, masked.waterCells(), "copied in");
        assertTrue(masked.isWater(1, 0));
        assertFalse(masked.isWater(0, 1));
        assertEquals(WaterBody.GREEN, masked.withBody(WaterBody.GREEN).body());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WaterTile(
                                new Vector3d(), 1, 2, 2, new float[8], null, null, POND.body()));
        assertThrows(
                IllegalArgumentException.class,
                () -> WaterTile.flat(new Vector3d(), 0, 2, 2, 0, POND.body()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WaterTile.flat(
                                new Vector3d(), 1, WaterTile.MAX_CELLS + 1, 2, 0, POND.body()));
        assertThrows(IllegalArgumentException.class, () -> new WaterBody(-1, 0, 0));
    }

    /** Every change bumps the version, so the renderer rebuilds only when something changed. */
    @Test
    void testVersions() {
        final WaterRegistry registry = new WaterRegistry();
        final List<WaterTile> tiles = new ArrayList<>();
        final long empty = registry.snapshotIfChanged(-1, tiles);
        assertTrue(tiles.isEmpty());
        final WaterHandle handle = registry.add(OWNER, POND);
        assertNotNull(handle);
        final long added = registry.snapshotIfChanged(empty, tiles);
        assertNotEquals(empty, added);
        assertEquals(List.of(POND), tiles);
        tiles.clear();
        assertEquals(added, registry.snapshotIfChanged(added, tiles));
        assertTrue(tiles.isEmpty(), "left alone when nothing changed");

        final WaterTile muddy = POND.withBody(WaterBody.MUDDY);
        assertTrue(registry.update(OWNER, handle, muddy));
        final long updated = registry.snapshotIfChanged(added, tiles);
        assertNotEquals(added, updated);
        assertEquals(List.of(muddy), tiles);
        assertTrue(registry.remove(OWNER, handle));
        assertNotEquals(updated, registry.snapshotIfChanged(updated, tiles));
        assertTrue(tiles.isEmpty());
    }

    /** Only the owner changes its tiles, stale handles do nothing, and unloading removes them. */
    @Test
    void testOwnership() {
        final WaterRegistry registry = new WaterRegistry();
        final WaterHandle mine = registry.add(OWNER, POND);
        final WaterHandle theirs = registry.add(OTHER, POND);
        assertFalse(registry.update(OTHER, mine, POND));
        assertFalse(registry.remove(OTHER, mine));
        assertEquals(OWNER, registry.ownerOf(mine));
        assertTrue(registry.remove(OWNER, mine));
        assertFalse(registry.isValid(mine));
        assertNull(registry.ownerOf(mine));
        final WaterHandle reused = registry.add(OWNER, POND);
        assertEquals(mine.slot(), reused.slot());
        assertFalse(registry.update(OWNER, mine, POND), "a stale handle");
        assertEquals(1, registry.removeAllOwnedBy(OTHER));
        assertFalse(registry.isValid(theirs));
        assertEquals(1, registry.getCount());
    }
}
