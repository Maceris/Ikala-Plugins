package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.registry.CharacterLoader;
import com.ikalagaming.characters.registry.ContentSource;

import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link PartForms}.
 *
 * @author Ches Burks
 */
class TestPartForms {
    /** Floats per vertex in a part. */
    private static final int STRIDE = 14;

    /** How near two floats must be to count as equal. */
    private static final float EPSILON = 1e-5f;

    /**
     * Two vertices at the origin and at x = 1, with form targets.
     *
     * @param targets The targets by slot, 6 floats each.
     * @return The part.
     */
    private static ImportedCharacter.Part part(Map<String, float[]> targets) {
        float[] vertices = new float[2 * STRIDE];
        vertices[STRIDE] = 1;
        return new ImportedCharacter.Part(
                "jeans",
                vertices,
                new int[8],
                new float[8],
                new int[0],
                -1,
                new Vector3f(),
                new Vector3f(1, 0, 0),
                targets);
    }

    /**
     * Two targets: the first vertex owned by the shin, pushed 0.06 up, with the thigh owning half
     * of it; the second owned by the thigh, pushed 0.06 forward.
     *
     * @return The targets.
     */
    private static Map<String, float[]> targets() {
        Map<String, float[]> targets = new LinkedHashMap<>();
        targets.put("lowerLeg.L", new float[] {0, 0.06f, 0, 0, 0, 0});
        targets.put("upperLeg.L", new float[] {0, 0.03f, 0, 0, 0, 0.06f});
        return targets;
    }

    /** Over a bulky slot the whole move; nothing elsewhere. */
    @Test
    void oneSlot() {
        ImportedCharacter.Part moved =
                PartForms.displace(part(targets()), Map.of("lowerLeg.L", 1f));
        Assertions.assertEquals(0.06f, moved.vertices()[1], EPSILON);
        Assertions.assertEquals(0, moved.vertices()[STRIDE + 2], EPSILON);
        Assertions.assertEquals(0.06f, moved.max().y, EPSILON);
    }

    /** A vertex shared by two slots takes the larger scaled move, not the sum. */
    @Test
    void largestNotSum() {
        ImportedCharacter.Part moved =
                PartForms.displace(part(targets()), Map.of("lowerLeg.L", 1f / 3, "upperLeg.L", 1f));
        // Shin padded: a third of 0.06; half-owned by the bulky thigh: half of 0.06
        Assertions.assertEquals(0.03f, moved.vertices()[1], EPSILON);
        Assertions.assertEquals(0.06f, moved.vertices()[STRIDE + 2], EPSILON);
    }

    /** A part without targets, or no amounts for its slots, comes back as it was. */
    @Test
    void untouched() {
        ImportedCharacter.Part plain = part(Map.of());
        Assertions.assertSame(plain, PartForms.displace(plain, Map.of("lowerLeg.L", 1f)));
        ImportedCharacter.Part targeted = part(targets());
        Assertions.assertSame(targeted, PartForms.displace(targeted, Map.of("hand.L", 1f)));
    }

    /**
     * Amounts are each form's share of the bulkiest form's offset, none for the first.
     *
     * @throws URISyntaxException If the test content can't be found.
     */
    @Test
    void amounts() throws URISyntaxException {
        Path content = Path.of(TestPartForms.class.getResource("/content/ikala").toURI());
        BodyPlan plan =
                CharacterLoader.load(List.of(new ContentSource("ikala", content, "test")))
                        .registry()
                        .get(BodyPlan.class, Id.parse("ikala:biped"));
        Map<String, Float> amounts =
                PartForms.amounts(plan, Map.of("lowerLeg.L", "padded", "hand.L", "tight"));
        Assertions.assertEquals(1, amounts.size());
        Assertions.assertEquals(1f / 3, amounts.get("lowerLeg.L"), EPSILON);
    }
}
