package com.ikalagaming.converter.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Reading context scenes. */
class ContextSceneTest {
    @Test
    void fillsAndSingleCells() {
        ContextScene scene =
                ContextScene.parse(
                        """
                        name: Neighbors
                        order: 3
                        blocks:
                          - fill: [[1, -1, 1], [-1, -1, -1]]
                            block: lotomation:grass
                          - at: [0, 0, 0]
                            block: subject
                        """);
        assertEquals("Neighbors", scene.name());
        assertEquals(3, scene.order());
        assertEquals(10, scene.cells().size(), "A 3x3 fill, corners in any order, and one more");
        assertEquals(
                new ContextScene.Cell(-1, -1, -1, "lotomation:grass"), scene.cells().getFirst());
        assertTrue(scene.hasSubject());
    }

    @Test
    void aSceneWithoutTheSubject() {
        ContextScene scene =
                ContextScene.parse("name: Stones\nblocks:\n  - at: [0, 0, 0]\n    block: x:stone");
        assertFalse(scene.hasSubject());
        assertEquals(0, scene.order());
    }

    @Test
    void mistakesAndHugeFillsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ContextScene.parse("blocks: []"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ContextScene.parse("name: x\nblocks:\n  - at: [0, 0]\n    block: subject"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ContextScene.parse("name: x\nblocks:\n  - block: subject"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContextScene.parse(
                                "name: x\nblocks:\n  - fill: [[0, 0, 0], [99, 99, 99]]\n"
                                        + "    block: subject"));
    }
}
