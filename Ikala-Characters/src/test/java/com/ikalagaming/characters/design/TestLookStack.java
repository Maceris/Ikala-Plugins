package com.ikalagaming.characters.design;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.Design;
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

/**
 * Tests for {@link LookStack}, on the test knight and items.
 *
 * @author Ches Burks
 */
class TestLookStack {
    /** The sword. */
    private static final Id SWORD = Id.parse("ikala:item/sword");

    /** The zombie style, which the sword and the knight both use. */
    private static final Id ZOMBIE = Id.parse("ikala:style/zombie");

    /** The test content's definitions. */
    private static CharacterRegistry registry;

    /** The test knight. */
    private static Design knight;

    /**
     * Load the test content.
     *
     * @throws URISyntaxException If the content can't be found.
     */
    @BeforeAll
    static void load() throws URISyntaxException {
        Path content = Path.of(TestLookStack.class.getResource("/content/ikala").toURI());
        registry =
                CharacterLoader.load(List.of(new ContentSource("ikala", content, "test")))
                        .registry();
        knight = registry.get(Design.class, Id.parse("ikala:design/knight"));
    }

    /**
     * What the knight shows with some equipment.
     *
     * @param slots What is in each slot.
     * @return What it shows.
     */
    private static LookStack.Shown shown(Map<String, Equipment.Equipped> slots) {
        return LookStack.resolve(knight, new Equipment(slots), registry);
    }

    /** Held items go to the first socket their state names that has room. */
    @Test
    void testHeld() {
        Map<String, Equipment.Equipped> slots = new LinkedHashMap<>();
        slots.put("mainHand", new Equipment.Equipped(SWORD, "sheathed", null, false, Map.of()));
        slots.put("offHand", new Equipment.Equipped(SWORD, null, null, false, Map.of()));
        LookStack.Shown shown = shown(slots);
        Assertions.assertEquals(2, shown.held().size());
        Assertions.assertEquals("back", shown.held().get(0).socket().id());
        Assertions.assertNotNull(shown.held().get(0).attach());
        Assertions.assertEquals("grip.L", shown.held().get(1).socket().id());
        Assertions.assertNull(shown.held().get(1).attach());
        // Item styles come before the design's, one per item
        Assertions.assertEquals(List.of(ZOMBIE, ZOMBIE, ZOMBIE), shown.styles());
        // The design's embers, then each sword's sparks and trail
        Assertions.assertEquals(5, shown.fx().size());
        // The sheathed sword's trail moves onto the back, turned as the sword is there
        com.ikalagaming.characters.definition.FxUse trail = shown.fx().get(2);
        Assertions.assertEquals("back", trail.socket());
        Assertions.assertEquals(0, trail.offset().y(), 1e-5f);
        Assertions.assertEquals(-0.1f, trail.offset().z(), 1e-5f);
        Assertions.assertEquals(-0.7f, trail.to().y(), 1e-5f);
        Assertions.assertEquals("grip.L", shown.fx().get(4).socket());

        // Only one item fits on the back
        slots.put("offHand", new Equipment.Equipped(SWORD, "sheathed", null, false, Map.of()));
        Assertions.assertEquals(1, shown(slots).held().size());
    }

    /** Worn items replace the design's layer, and hidden ones show what is under them. */
    @Test
    void testWorn() {
        Id helm = Id.parse("ikala:item/iron_helm");
        LookStack.Shown plain = shown(Map.of());
        Assertions.assertEquals(
                Id.parse("ikala:part/biped/helmet_iron"), plain.worn().get("helmet"));
        Assertions.assertEquals(List.of(ZOMBIE), plain.styles());

        LookStack.Shown hidden =
                shown(Map.of("head", new Equipment.Equipped(helm, null, null, true, Map.of())));
        Assertions.assertEquals(plain.worn(), hidden.worn());
        LookStack.Shown helmed =
                shown(
                        Map.of(
                                "head",
                                new Equipment.Equipped(
                                        helm,
                                        null,
                                        null,
                                        false,
                                        Map.of(
                                                "primary",
                                                com.ikalagaming.characters.data.Color.parse(
                                                        "#ff0000")))));
        Assertions.assertEquals(
                Id.parse("ikala:part/biped/helmet_iron"), helmed.worn().get("helmet"));
        Assertions.assertTrue(helmed.dyes().containsKey(Id.parse("ikala:part/biped/helmet_iron")));
    }
}
