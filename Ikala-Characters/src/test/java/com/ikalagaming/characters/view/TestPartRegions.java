package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.gltf.ImportedCharacter;

import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.LinkedHashMap;
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
        return squares(name, height, bone, minX, maxX);
    }

    /**
     * Squares facing up side by side, of two triangles each, moved entirely by one bone.
     *
     * @param name Its name.
     * @param height How high it is.
     * @param bone The bone moving it.
     * @param edges Each square's left and right edges, in turn.
     * @return The part.
     */
    private static ImportedCharacter.Part squares(
            String name, float height, int bone, float... edges) {
        int count = edges.length / 2;
        float[] vertices = new float[count * 4 * 14];
        int[] joints = new int[count * 4 * 4];
        float[] weights = new float[count * 4 * 4];
        int[] indices = new int[count * 6];
        int[] square = {0, 1, 2, 0, 2, 3};
        for (int s = 0; s < count; ++s) {
            float minX = edges[s * 2];
            float maxX = edges[s * 2 + 1];
            float[][] corners = {{minX, -1}, {minX, 1}, {maxX, 1}, {maxX, -1}};
            for (int i = 0; i < 4; ++i) {
                int v = s * 4 + i;
                vertices[v * 14] = corners[i][0];
                vertices[v * 14 + 1] = height;
                vertices[v * 14 + 2] = corners[i][1];
                vertices[v * 14 + 4] = 1;
                joints[v * 4] = bone;
                weights[v * 4] = 1;
            }
            for (int i = 0; i < 6; ++i) {
                indices[s * 6 + i] = s * 4 + square[i];
            }
        }
        return new ImportedCharacter.Part(
                name,
                vertices,
                joints,
                weights,
                indices,
                -1,
                new Vector3f(edges[0], height, -1),
                new Vector3f(edges[edges.length - 1], height, 1));
    }

    /**
     * A character of some parts on a two-bone rig.
     *
     * @param parts Its parts.
     * @return The character.
     */
    private static ImportedCharacter character(List<ImportedCharacter.Part> parts) {
        Rig rig =
                new Rig(
                        Id.parse("test:rig/two"),
                        List.of(
                                new Rig.Bone("a", null, Rig.BoneKind.DEFORM),
                                new Rig.Bone("b", "a", Rig.BoneKind.DEFORM)));
        return new ImportedCharacter("test", rig, Map.of(), parts, List.of(), List.of(), List.of());
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

        // The left square is region 0 and is covered; the right one is only partly covered, so
        // never hidden
        Assertions.assertEquals(1L, regions.hidden(List.of(2)));
        Assertions.assertEquals(0L, regions.hidden(List.of(3)));
        Assertions.assertEquals(0L, regions.hidden(List.of(0, 1)));
        Assertions.assertArrayEquals(new int[] {0, 0, 0, 0}, regions.regions().get(0));
        int never = PartRegions.NEVER_HIDDEN;
        Assertions.assertArrayEquals(
                new int[] {never, never, never, never}, regions.regions().get(1));
        Assertions.assertNull(regions.regions().get(2));
    }

    /**
     * A garment open in the middle, like a jacket's open front, hides the body only where it
     * covers it: the body showing through the opening stays, though one bone moves it all.
     */
    @Test
    void testOpening() {
        List<ImportedCharacter.Part> parts =
                List.of(
                        squares("body", 0, 0, -3, -1, -1, 1, 1, 3),
                        squares("jacket", 0.05f, 0, -3.5f, -1, 1, 3.5f));
        PartRegions regions =
                PartRegions.of(
                        character(parts), new boolean[] {true, false}, new boolean[] {false, true});

        Assertions.assertEquals(1L, regions.hidden(List.of(1)));
        int never = PartRegions.NEVER_HIDDEN;
        // Square by square, four vertices each: the middle one shows through the opening
        Assertions.assertArrayEquals(
                new int[] {0, 0, 0, 0, never, never, never, never, 0, 0, 0, 0},
                regions.regions().get(0));
    }

    /**
     * A jacket worn over a shirt hides only what it covers itself, and the shirt everything it
     * covers, so the body under the jacket's opening hides only while the shirt is worn.
     */
    @Test
    void testLayers() {
        List<ImportedCharacter.Part> parts =
                List.of(
                        squares("body", 0, 0, -3, -1, -1, 1, 1, 3),
                        squares("shirt", 0.03f, 0, -3.5f, 3.5f),
                        squares("jacket", 0.06f, 0, -3.5f, -1, 1, 3.5f));
        PartRegions regions =
                PartRegions.of(
                        character(parts),
                        new boolean[] {true, false, false},
                        new boolean[] {false, true, true});

        int[] body = regions.regions().get(0);
        long sides = 1L << body[0];
        long middle = 1L << body[4];
        Assertions.assertNotEquals(sides, middle);
        Assertions.assertEquals(sides, regions.hidden(List.of(2)));
        Assertions.assertEquals(sides | middle, regions.hidden(List.of(1)));
        Assertions.assertEquals(sides | middle, regions.hidden(List.of(1, 2)));
    }

    /**
     * Past the most regions there can be, the least area's sets of covering parts merge into
     * sets of fewer of the same parts, so they hide less, never more.
     */
    @Test
    void testMerging() {
        final int coatPart = 100;
        final int extra = 5;
        Map<BitSet, Float> areas = new LinkedHashMap<>();
        BitSet coat = new BitSet();
        coat.set(coatPart);
        areas.put(coat, 10f);
        for (int i = 0; i < PartRegions.MAX_REGIONS + extra; ++i) {
            BitSet covering = new BitSet();
            covering.set(i);
            covering.set(coatPart);
            areas.put(covering, 1f + i);
        }
        Map<BitSet, BitSet> merged = PartRegions.merge(areas);

        Assertions.assertEquals(areas.size() - PartRegions.MAX_REGIONS, merged.size());
        for (Map.Entry<BitSet, BitSet> entry : merged.entrySet()) {
            // The smallest went, each into the coat alone, which they all hold
            Assertions.assertEquals(coat, entry.getValue());
            Assertions.assertTrue(entry.getKey().nextSetBit(0) <= extra);
        }
    }
}
