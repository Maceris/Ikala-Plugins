package com.ikalagaming.characters.design;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.ColorCode;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.definition.Item;
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
 * Tests for {@link Equipment} validation and {@link EquipmentCodec}, on the test content's items.
 *
 * @author Ches Burks
 */
class TestEquipment {
    /** The biped plan. */
    private static final Id BIPED = Id.parse("ikala:biped");

    /** The sword. */
    private static final Id SWORD = Id.parse("ikala:item/sword");

    /** The fancy sword, which only goes in the main hand. */
    private static final Id FANCY = Id.parse("ikala:item/fancy_sword");

    /** The helmet. */
    private static final Id HELM = Id.parse("ikala:item/iron_helm");

    /** The test content's definitions. */
    private static CharacterRegistry registry;

    /**
     * Load the test content.
     *
     * @throws URISyntaxException If the content can't be found.
     */
    @BeforeAll
    static void load() throws URISyntaxException {
        Path content = Path.of(TestEquipment.class.getResource("/content/ikala").toURI());
        CharacterLoader.Result result =
                CharacterLoader.load(List.of(new ContentSource("ikala", content, "test")));
        Assertions.assertTrue(
                result.problems().isEmpty(),
                result.problems().stream().map(Problem::toString).collect(Collectors.joining()));
        registry = result.registry();
    }

    /**
     * An equipped item in its first state, its own look, undyed.
     *
     * @param item The item.
     * @return The equipped item.
     */
    private static Equipment.Equipped plain(Id item) {
        return new Equipment.Equipped(item, null, null, false, Map.of());
    }

    /**
     * The messages of equipment's problems on the biped.
     *
     * @param slots What is in each slot.
     * @return The messages, joined.
     */
    private static String problems(Map<String, Equipment.Equipped> slots) {
        return new Equipment(slots)
                .validate(registry, BIPED).stream()
                        .map(Problem::message)
                        .collect(Collectors.joining("\n"));
    }

    /** Items load with their looks. */
    @Test
    void testItems() {
        Item sword = registry.get(Item.class, SWORD);
        Item.Look look = sword.lookOn(BIPED);
        Assertions.assertTrue(look.held());
        Assertions.assertEquals("drawn", look.firstState());
        Assertions.assertFalse(registry.get(Item.class, HELM).lookOn(BIPED).held());
    }

    /** Validation names what is wrong. */
    @Test
    void testProblems() {
        Assertions.assertEquals(
                "", problems(Map.of("mainHand", plain(SWORD), "head", plain(HELM))));
        Assertions.assertTrue(problems(Map.of("feet", plain(SWORD))).contains("no equipment slot"));
        Assertions.assertTrue(
                problems(Map.of("mainHand", plain(HELM))).contains("doesn't go in mainHand"));
        Assertions.assertTrue(
                problems(
                                Map.of(
                                        "offHand",
                                        new Equipment.Equipped(
                                                SWORD, null, FANCY, false, Map.of())))
                        .contains("has no look for offHand"));
        Assertions.assertTrue(
                problems(Map.of("mainHand", plain(SWORD).withState("thrown")))
                        .contains("no state 'thrown'"));
        Assertions.assertTrue(
                problems(
                                Map.of(
                                        "mainHand",
                                        new Equipment.Equipped(
                                                SWORD,
                                                null,
                                                null,
                                                false,
                                                Map.of("skin", Color.parse("#ffffff")))))
                        .contains("can't be dyed in 'skin'"));
        Assertions.assertTrue(
                problems(Map.of("mainHand", plain(Id.parse("ikala:item/none"))))
                        .contains("No loaded item"));
    }

    /** Equipment survives encoding once its colors are on codes. */
    @Test
    void testRoundTrip() {
        Map<String, Equipment.Equipped> slots = new LinkedHashMap<>();
        Color red = ColorCode.toColor(ColorCode.encode(Color.parse("#7a2e2e")));
        slots.put("mainHand", new Equipment.Equipped(SWORD, "drawn", FANCY, false, Map.of()));
        slots.put(
                "offHand",
                new Equipment.Equipped(SWORD, "sheathed", null, false, Map.of("accent", red)));
        slots.put("head", new Equipment.Equipped(HELM, null, null, true, Map.of()));
        Equipment equipment = new Equipment(slots);
        Assertions.assertTrue(equipment.validate(registry, BIPED).isEmpty());

        byte[] bytes = EquipmentCodec.encode(equipment, BIPED, registry);
        Assertions.assertTrue(bytes.length < 24, "Took " + bytes.length + " bytes");
        Equipment back = EquipmentCodec.decode(bytes, BIPED, registry);
        Assertions.assertEquals(slots.get("mainHand"), back.slots().get("mainHand"));
        Assertions.assertEquals(slots.get("offHand"), back.slots().get("offHand"));
        Assertions.assertEquals(slots.get("head"), back.slots().get("head"));

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        EquipmentCodec.decode(
                                java.util.Arrays.copyOf(bytes, bytes.length - 1), BIPED, registry));
    }
}
