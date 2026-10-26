package com.ikalagaming.characters.design;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.ColorCode;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.registry.CharacterLoader;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.registry.ContentSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tests for {@link Design} validation and {@link DesignCodec}.
 *
 * @author Ches Burks
 */
class TestDesign {
    /** The test knight's ID. */
    private static final Id KNIGHT = Id.parse("ikala:design/knight");

    /** The height slider's ID. */
    private static final String HEIGHT = "ikala:slider/biped/height";

    /** The test content's definitions. */
    private static CharacterRegistry registry;

    /**
     * Load the test content.
     *
     * @throws URISyntaxException If the content can't be found.
     */
    @BeforeAll
    static void load() throws URISyntaxException {
        Path content = Path.of(TestDesign.class.getResource("/content/ikala").toURI());
        CharacterLoader.Result result =
                CharacterLoader.load(List.of(new ContentSource("ikala", content, "test")));
        Assertions.assertTrue(
                result.problems().isEmpty(),
                result.problems().stream().map(Problem::toString).collect(Collectors.joining()));
        registry = result.registry();
    }

    /**
     * A design like the knight with some fields changed.
     *
     * @param sliders The slider values.
     * @param parts The body parts.
     * @param layers The layer parts.
     * @param colors The colors.
     * @return The design.
     */
    private static Design design(
            Map<String, List<Float>> sliders,
            Map<String, Id> parts,
            Map<String, Id> layers,
            Map<String, Color> colors) {
        return new Design(
                Id.parse("ikala:design/test"),
                Id.parse("ikala:biped"),
                "male",
                sliders,
                parts,
                layers,
                null,
                colors);
    }

    /**
     * The messages of a design's problems for players.
     *
     * @param design The design.
     * @return The messages, joined.
     */
    private static String problems(Design design) {
        return design.validate(registry, Slider.CONTEXT_PLAYER).stream()
                .map(Problem::message)
                .collect(Collectors.joining("\n"));
    }

    /** The test knight loads, is valid for players, and wears the variant's default forearm. */
    @Test
    void testKnight() {
        Design knight = registry.get(Design.class, KNIGHT);
        Assertions.assertNotNull(knight);
        Assertions.assertEquals("", problems(knight));

        BodyPlan biped = registry.get(BodyPlan.class, knight.plan());
        Map<String, Id> worn = knight.worn(biped, id -> registry.get(Part.class, id));
        Assertions.assertEquals(
                Map.of(
                        "lowerArm.R", Id.parse("ikala:part/biped/forearm_stump"),
                        "helmet", Id.parse("ikala:part/biped/helmet_iron"),
                        "lowerArm.L", Id.parse("ikala:part/biped/forearm_robot")),
                worn);
    }

    /** Validation names what is wrong. */
    @Test
    void testProblems() {
        Id stump = Id.parse("ikala:part/biped/forearm_stump");
        Id helmet = Id.parse("ikala:part/biped/helmet_iron");
        Assertions.assertTrue(
                problems(design(Map.of(), Map.of("tail", stump), Map.of(), Map.of()))
                        .contains("no body slot 'tail'"));
        Assertions.assertTrue(
                problems(design(Map.of(), Map.of("head", stump), Map.of(), Map.of()))
                        .contains("doesn't fill 'head'"));
        Assertions.assertTrue(
                problems(design(Map.of(), Map.of(), Map.of("hair", helmet), Map.of()))
                        .contains("doesn't fill 'hair'"));
        // The stump also fills hand.R, so another part there clashes
        Map<String, Id> both = new LinkedHashMap<>();
        both.put("lowerArm.R", stump);
        both.put("hand.R", stump);
        Assertions.assertTrue(
                problems(design(Map.of(), both, Map.of(), Map.of())).contains("already filled"));
        // Players get -1 to 1 on height, NPCs -2 to 2
        Design tall = design(Map.of(HEIGHT, List.of(1.5f)), Map.of(), Map.of(), Map.of());
        Assertions.assertTrue(problems(tall).contains("outside"));
        Assertions.assertTrue(tall.validate(registry, Slider.CONTEXT_NPC).isEmpty());
        Assertions.assertTrue(
                problems(design(Map.of(HEIGHT, List.of(0f, 0f)), Map.of(), Map.of(), Map.of()))
                        .contains("takes 1 values"));
        Assertions.assertTrue(
                problems(
                                design(
                                        Map.of(),
                                        Map.of(),
                                        Map.of(),
                                        Map.of("fur", Color.parse("#123456"))))
                        .contains("no palette channel 'fur'"));
    }

    /** A design survives encoding once its values are on the encoding's steps. */
    @Test
    void testRoundTrip() {
        Design knight = registry.get(Design.class, KNIGHT);
        byte[] bytes = DesignCodec.encode(knight, registry);
        Assertions.assertTrue(bytes.length < 40, "The knight took " + bytes.length + " bytes");
        Design back = DesignCodec.decode(bytes, KNIGHT, registry);

        Assertions.assertEquals(knight.plan(), back.plan());
        Assertions.assertEquals(knight.variant(), back.variant());
        Assertions.assertEquals(knight.parts(), back.parts());
        Assertions.assertEquals(knight.layers(), back.layers());
        Assertions.assertEquals(knight.palette(), back.palette());
        Assertions.assertEquals(DesignCodec.snap(0.25f), back.sliders().get(HEIGHT).get(0));
        Assertions.assertEquals(
                ColorCode.toColor(ColorCode.encode(Color.parse("#3355aa"))),
                back.colors().get("primary"));

        // Snapped values come back exactly, so a decoded design encodes to the same bytes
        Assertions.assertArrayEquals(bytes, DesignCodec.encode(back, registry));
        Assertions.assertEquals(DesignCodec.hash(bytes), DesignCodec.hash(bytes.clone()));
    }

    /** Broken bytes are refused rather than read as something else. */
    @Test
    void testBrokenBytes() {
        byte[] bytes = DesignCodec.encode(registry.get(Design.class, KNIGHT), registry);
        byte[] shorter = java.util.Arrays.copyOf(bytes, bytes.length - 1);
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DesignCodec.decode(shorter, KNIGHT, registry));
        byte[] longer = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> DesignCodec.decode(longer, KNIGHT, registry));
        byte[] newer = bytes.clone();
        newer[0] = (byte) (DesignCodec.FORMAT + 1);
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> DesignCodec.decode(newer, KNIGHT, registry));
    }
}
