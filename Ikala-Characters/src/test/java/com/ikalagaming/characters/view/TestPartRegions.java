package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.gltf.ImportedCharacter;

import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for {@link PartRegions}.
 *
 * @author Ches Burks
 */
class TestPartRegions {
    /**
     * A square facing up, of two triangles, moved entirely by one bone.
     *
     * @param name Its name.
     * @param height How high it is.
     * @param minX Its left edge.
     * @param maxX Its right edge.
     * @param bone The bone moving it.
     * @return The part.
     */
    private static ImportedCharacter.Part square(
            String name, float height, float minX, float maxX, int bone) {
        float[][] corners = {{minX, -1}, {minX, 1}, {maxX, 1}, {maxX, -1}};
        float[] vertices = new float[4 * 14];
        int[] joints = new int[4 * 4];
        float[] weights = new float[4 * 4];
        for (int i = 0; i < 4; ++i) {
            vertices[i * 14] = corners[i][0];
            vertices[i * 14 + 1] = height;
            vertices[i * 14 + 2] = corners[i][1];
            vertices[i * 14 + 4] = 1;
            joints[i * 4] = bone;
            weights[i * 4] = 1;
        }
        return new ImportedCharacter.Part(
                name,
                vertices,
                joints,
                weights,
                new int[] {0, 1, 2, 0, 2, 3},
                -1,
                new Vector3f(minX, height, -1),
                new Vector3f(maxX, height, 1));
    }

    /** A wearable part just outside a base region hides it; one far away hides nothing. */
    @Test
    void testCovering() {
        // The base: two squares side by side, moved by different bones, so two regions
        List<ImportedCharacter.Part> parts =
                List.of(
                        square("left", 0, -2, 0, 0),
                        square("right", 0, 0, 2, 1),
                        square("cover", 0.05f, -2.5f, 0.5f, 0),
                        square("far", 1, -2, 2, 0));
        Rig rig =
                new Rig(
                        Id.parse("test:rig/two"),
                        List.of(
                                new Rig.Bone("a", null, Rig.BoneKind.DEFORM),
                                new Rig.Bone("b", "a", Rig.BoneKind.DEFORM)));
        ImportedCharacter character =
                new ImportedCharacter(
                        "test", rig, Map.of(), parts, List.of(), List.of(), List.of());
        PartSlots slots =
                new PartSlots(
                        List.of(
                                new PartSlots.Slot(
                                        "body", List.of(List.of(0, 1)), List.of("body"), false, 0),
                                new PartSlots.Slot(
                                        "armor",
                                        List.of(List.of(2), List.of(3)),
                                        List.of("cover", "far"),
                                        true,
                                        -1)));
        PartRegions regions = PartRegions.of(character, slots);

        // The left square is region 0 and is covered; the right one, region 1, isn't
        Assertions.assertEquals(1L, regions.hidden(List.of(2)));
        Assertions.assertEquals(0L, regions.hidden(List.of(3)));
        Assertions.assertEquals(0L, regions.hidden(List.of(0, 1)));
        Assertions.assertArrayEquals(new int[] {0, 0, 0, 0}, regions.regions().get(0));
        Assertions.assertArrayEquals(new int[] {1, 1, 1, 1}, regions.regions().get(1));
        Assertions.assertNull(regions.regions().get(2));
    }
}
