package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.registry.CharacterLoader;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.registry.ContentSource;
import com.ikalagaming.characters.view.PartSlots;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for the wear rules in {@link CharacterLook}, on the test content's rule parts: clashes,
 * requirements, pairs, memory of what was taken off, availability and what worn parts hide.
 *
 * @author Ches Burks
 */
class TestLookRules {
    /** The slots, each with its parts, one mesh per part. */
    private static final List<Map.Entry<String, List<String>>> LAYOUT =
            List.of(
                    Map.entry("lowerArm.R", List.of("forearm_stump")),
                    Map.entry("hand.L", List.of("claw_l")),
                    Map.entry("hand.R", List.of("claw_r")),
                    Map.entry("jacket", List.of("coat_long")),
                    Map.entry("cape", List.of("cape_cloth")),
                    Map.entry("helmet", List.of("helmet_iron", "full_helm")),
                    Map.entry("face", List.of("visor")),
                    Map.entry("hair", List.of("hair_short")),
                    Map.entry("back", List.of("jetpack")),
                    Map.entry("back2", List.of("wings")));

    /** Slot positions in {@link #LAYOUT}. */
    private static final int STUMP = 0;

    private static final int HAND_L = 1;

    private static final int HAND_R = 2;

    private static final int JACKET = 3;

    private static final int CAPE = 4;

    private static final int HELMET = 5;

    private static final int FACE = 6;

    private static final int HAIR = 7;

    private static final int BACK = 8;

    private static final int BACK_2 = 9;

    /** The test content's definitions. */
    private static CharacterRegistry registry;

    /** The test plan. */
    private static BodyPlan plan;

    /**
     * Load the test content.
     *
     * @throws URISyntaxException If the content can't be found.
     */
    @BeforeAll
    static void load() throws URISyntaxException {
        Path content = Path.of(TestLookRules.class.getResource("/content/ikala").toURI());
        registry =
                CharacterLoader.load(List.of(new ContentSource("ikala", content, "test")))
                        .registry();
        plan = registry.get(BodyPlan.class, Id.parse("ikala:biped"));
    }

    /**
     * A look over {@link #LAYOUT}, every slot empty to start and allowed to be.
     *
     * @param variants The variants to offer.
     * @return The look.
     */
    private static CharacterLook look(List<BodyPlan.Variant> variants) {
        List<PartSlots.Slot> slots = new ArrayList<>();
        List<List<Part>> parts = new ArrayList<>();
        List<List<Set<String>>> fills = new ArrayList<>();
        int mesh = 0;
        for (Map.Entry<String, List<String>> slot : LAYOUT) {
            List<List<Integer>> options = new ArrayList<>();
            List<Part> defined = new ArrayList<>();
            List<Set<String>> filled = new ArrayList<>();
            for (String name : slot.getValue()) {
                Part part = registry.get(Part.class, Id.parse("ikala:part/biped/" + name));
                Assertions.assertNotNull(part, name);
                options.add(List.of(mesh++));
                defined.add(part);
                Set<String> places = new LinkedHashSet<>(part.fills());
                places.add(slot.getKey());
                filled.add(places);
            }
            slots.add(new PartSlots.Slot(slot.getKey(), options, slot.getValue(), true, -1));
            parts.add(defined);
            fills.add(filled);
        }
        return new CharacterLook(
                new PartSlots(slots),
                registry.all(Slider.class),
                variants,
                List.of(),
                fills,
                parts,
                plan);
    }

    /**
     * A look with every part available.
     *
     * @return The look.
     */
    private static CharacterLook look() {
        return look(List.of());
    }

    /** The second of two parts sharing an exclusive tag takes the first off, and it comes back. */
    @Test
    void exclusiveTagsAndMemory() {
        CharacterLook look = look();
        look.choose(BACK, 0);
        look.choose(BACK_2, 0);
        Assertions.assertEquals(CharacterLook.EMPTY, look.choice(BACK));
        // Taking the wings off puts the jetpack back
        look.choose(BACK_2, CharacterLook.EMPTY);
        Assertions.assertEquals(0, look.choice(BACK));
    }

    /** A part covering a slot takes what it held off, and gives it back when it goes. */
    @Test
    void coveredSlotComesBack() {
        CharacterLook look = look();
        look.setBothSides(false);
        look.choose(HAND_R, 0);
        look.choose(STUMP, 0);
        Assertions.assertEquals(CharacterLook.EMPTY, look.choice(HAND_R));
        look.choose(STUMP, CharacterLook.EMPTY);
        Assertions.assertEquals(0, look.choice(HAND_R));

        // Undo goes back through the memory too
        look.undo();
        Assertions.assertEquals(CharacterLook.EMPTY, look.choice(HAND_R));
        look.choose(STUMP, CharacterLook.EMPTY);
        Assertions.assertEquals(0, look.choice(HAND_R));
    }

    /** A slot the user chose isn't overwritten by memory. */
    @Test
    void userChoiceBeatsMemory() {
        CharacterLook look = look();
        look.choose(BACK, 0);
        look.choose(BACK_2, 0);
        // The user puts the jetpack on again by hand, which takes the wings off
        look.choose(BACK, 0);
        Assertions.assertEquals(CharacterLook.EMPTY, look.choice(BACK_2));
        look.choose(BACK, CharacterLook.EMPTY);
        // The wings were taken off by the jetpack, so they come back
        Assertions.assertEquals(0, look.choice(BACK_2));
    }

    /** Putting a part on puts on what it requires; taking that off takes the part off too. */
    @Test
    void requirements() {
        CharacterLook look = look();
        look.choose(FACE, 0);
        Assertions.assertEquals(0, look.choice(HELMET), "The visor puts the iron helmet on");
        look.choose(HELMET, 1);
        Assertions.assertEquals(CharacterLook.EMPTY, look.choice(FACE), "The visor needs it");
    }

    /** A full helm hides the hair and the face socket, and locks the face sliders. */
    @Test
    void fullHelmHides() {
        CharacterLook look = look();
        look.choose(HAIR, 0);
        int hairMesh = look.slots().slots().get(HAIR).options().get(0).get(0);
        Assertions.assertTrue(look.worn(CharacterLook.NO_PREVIEW, 0).contains(hairMesh));
        look.choose(HELMET, 1);
        Assertions.assertFalse(look.worn(CharacterLook.NO_PREVIEW, 0).contains(hairMesh));
        Assertions.assertEquals(look.part(HELMET, 1), look.hiddenBy(HAIR));
        Assertions.assertEquals(look.part(HELMET, 1), look.hiddenBy(FACE));
        Slider nose = registry.get(Slider.class, Id.parse("ikala:slider/biped/nose"));
        Slider height = registry.get(Slider.class, Id.parse("ikala:slider/biped/height"));
        Assertions.assertEquals(look.part(HELMET, 1), look.lockedBy(nose));
        Assertions.assertNull(look.lockedBy(height));
        // The hair is still chosen, only hidden, so it shows again without the helm
        Assertions.assertEquals(0, look.choice(HAIR));
    }

    /** With both sides on, a pair goes on and off together; off, sides are chosen alone. */
    @Test
    void pairs() {
        CharacterLook look = look();
        look.choose(HAND_L, 0);
        Assertions.assertEquals(0, look.choice(HAND_R));
        look.choose(HAND_R, CharacterLook.EMPTY);
        Assertions.assertEquals(CharacterLook.EMPTY, look.choice(HAND_L));

        look.setBothSides(false);
        look.choose(HAND_L, 0);
        Assertions.assertEquals(CharacterLook.EMPTY, look.choice(HAND_R));
    }

    /** Options the variant doesn't allow aren't available, and switching to it drops them. */
    @Test
    void variantsFilter() {
        BodyPlan.Variant any = new BodyPlan.Variant("any", List.of(), Map.of(), Map.of());
        BodyPlan.Variant cloth = new BodyPlan.Variant("cloth", List.of("cloth"), Map.of(), Map.of());
        CharacterLook look = look(List.of(any, cloth));
        look.choose(HAND_L, 0);
        look.choose(JACKET, 0);
        look.chooseVariant(1);
        Assertions.assertEquals(CharacterLook.EMPTY, look.choice(HAND_L));
        Assertions.assertEquals(0, look.choice(JACKET));
        Assertions.assertTrue(look.available(HAND_L).isEmpty());
        Assertions.assertEquals(List.of(0), look.available(CAPE));
    }
}
