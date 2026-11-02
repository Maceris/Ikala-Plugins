package com.ikalagaming.characters.design;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.FxUse;
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
 * Tests for {@link WearRules}, and the look and design checks that use them, on the test content's
 * rule parts.
 *
 * @author Ches Burks
 */
class TestWearRules {
    /** A long coat, which is cloth. */
    private static final Id COAT = Id.parse("ikala:part/biped/coat_long");

    /** A cape, which is cloth too. */
    private static final Id CAPE = Id.parse("ikala:part/biped/cape_cloth");

    /** A jetpack, a back detail. */
    private static final Id JETPACK = Id.parse("ikala:part/biped/jetpack");

    /** Wings, another back detail. */
    private static final Id WINGS = Id.parse("ikala:part/biped/wings");

    /** A full helm, covering the hair, the face socket and the face sliders. */
    private static final Id FULL_HELM = Id.parse("ikala:part/biped/full_helm");

    /** A visor, which needs the iron helmet. */
    private static final Id VISOR = Id.parse("ikala:part/biped/visor");

    /** The iron helmet. */
    private static final Id IRON_HELM = Id.parse("ikala:part/biped/helmet_iron");

    /** The test content's definitions. */
    private static CharacterRegistry registry;

    /** The test plan. */
    private static BodyPlan plan;

    /** Problems loading the test content. */
    private static List<Problem> loadProblems;

    /**
     * Load the test content.
     *
     * @throws URISyntaxException If the content can't be found.
     */
    @BeforeAll
    static void load() throws URISyntaxException {
        Path content = Path.of(TestWearRules.class.getResource("/content/ikala").toURI());
        CharacterLoader.Result result =
                CharacterLoader.load(List.of(new ContentSource("ikala", content, "test")));
        registry = result.registry();
        loadProblems = result.problems();
        plan = registry.get(BodyPlan.class, Id.parse("ikala:biped"));
    }

    /**
     * A part from the test content.
     *
     * @param id Its ID.
     * @return The part.
     */
    private static Part part(Id id) {
        Part found = registry.get(Part.class, id);
        Assertions.assertNotNull(found, id + " should load");
        return found;
    }

    /** The rule parts load without problems, their pairs included. */
    @Test
    void ruleParts() {
        String messages =
                loadProblems.stream()
                        .filter(p -> p.severity() == Problem.Severity.ERROR)
                        .map(Problem::message)
                        .collect(Collectors.joining("\n"));
        Assertions.assertEquals("", messages);
        Assertions.assertEquals(
                Id.parse("ikala:part/biped/claw_r"),
                part(Id.parse("ikala:part/biped/claw_l")).pair());
    }

    /** Parts sharing an exclusive tag clash, either way round; others and a part itself don't. */
    @Test
    void clash() {
        Assertions.assertTrue(WearRules.clash(plan, part(COAT), part(CAPE)));
        Assertions.assertTrue(WearRules.clash(plan, part(CAPE), part(COAT)));
        Assertions.assertTrue(WearRules.clash(plan, part(JETPACK), part(WINGS)));
        Assertions.assertFalse(WearRules.clash(plan, part(COAT), part(JETPACK)));
        Assertions.assertFalse(WearRules.clash(plan, part(COAT), part(COAT)));
        Assertions.assertEquals(
                "cloth", WearRules.sharedExclusiveTag(plan, part(COAT), part(CAPE)));
    }

    /** The later of two clashing parts stays, and parts lose what they require. */
    @Test
    void settle() {
        Map<String, Id> worn = new LinkedHashMap<>();
        worn.put("jacket", COAT);
        worn.put("cape", CAPE);
        worn.put("face", VISOR);
        Map<String, Id> settled = WearRules.settle(plan, worn, id -> registry.get(Part.class, id));
        Assertions.assertEquals(List.of("cape"), List.copyOf(settled.keySet()));

        worn.put("helmet", IRON_HELM);
        settled = WearRules.settle(plan, worn, id -> registry.get(Part.class, id));
        Assertions.assertEquals(List.of("cape", "face", "helmet"), List.copyOf(settled.keySet()));
    }

    /** A full helm hides the hair layer and the face socket, and locks the face sliders. */
    @Test
    void fullHelm() {
        Part helm = part(FULL_HELM);
        Assertions.assertEquals(java.util.Set.of("hair"), WearRules.hiddenLayers(List.of(helm)));
        Assertions.assertTrue(
                WearRules.hides(plan.socket("face"), WearRules.hiddenSockets(List.of(helm))));
        Assertions.assertFalse(
                WearRules.hides(plan.socket("back"), WearRules.hiddenSockets(List.of(helm))));
        Slider nose = registry.get(Slider.class, Id.parse("ikala:slider/biped/nose"));
        Slider height = registry.get(Slider.class, Id.parse("ikala:slider/biped/height"));
        Assertions.assertTrue(WearRules.locks(helm, nose));
        Assertions.assertFalse(WearRules.locks(helm, height));
    }

    /** The shown look drops the earlier cloth piece and effects on covered sockets. */
    @Test
    void shownLook() {
        FxUse onFace =
                new FxUse(
                        Id.parse("ikala:fx/embers"),
                        null,
                        "face",
                        null,
                        null,
                        1,
                        1,
                        1,
                        FxUse.ALWAYS,
                        null,
                        false);
        Design design =
                new Design(
                        Id.parse("ikala:design/test"),
                        Id.parse("ikala:biped"),
                        "male",
                        null,
                        Map.of(),
                        Map.of(),
                        new LinkedHashMap<>(
                                Map.of("jacket", COAT, "cape", CAPE, "helmet", FULL_HELM)),
                        null,
                        Map.of(),
                        List.of(onFace));
        LookStack.Shown shown = LookStack.resolve(design, new Equipment(Map.of()), registry);
        Assertions.assertFalse(
                shown.worn().containsValue(COAT) && shown.worn().containsValue(CAPE),
                "Only one cloth piece is worn");
        Assertions.assertTrue(shown.worn().containsValue(FULL_HELM));
        Assertions.assertTrue(shown.fx().isEmpty(), "The face socket's effect is covered");
    }

    /** Designs wearing two cloth pieces are reported. */
    @Test
    void designChecks() {
        Design design =
                new Design(
                        Id.parse("ikala:design/test"),
                        Id.parse("ikala:biped"),
                        "male",
                        null,
                        Map.of(),
                        Map.of(),
                        Map.of("jacket", COAT, "cape", CAPE),
                        null,
                        Map.of(),
                        List.of());
        String problems =
                design.validate(registry, Slider.CONTEXT_PLAYER).stream()
                        .map(Problem::message)
                        .collect(Collectors.joining("\n"));
        Assertions.assertTrue(problems.contains("'cloth'"), problems);
    }

    /**
     * A layer takes the thickest form of the body parts under it, falling back to thinner meshes.
     */
    @Test
    void forms() {
        Part gauntlets = part(Id.parse("ikala:part/biped/gauntlets"));
        Part claw = part(Id.parse("ikala:part/biped/claw_l"));
        Part stump = part(Id.parse("ikala:part/biped/forearm_stump"));
        Assertions.assertEquals("bulky", WearRules.formFor(plan, gauntlets, List.of(stump, claw)));
        Assertions.assertNull(WearRules.formFor(plan, gauntlets, List.of(stump)));
        // A coat covers the torso, not the hands, so the claws don't change it
        Assertions.assertNull(WearRules.formFor(plan, part(COAT), List.of(claw)));

        // No bulky mesh, so the padded one; no form, or the bare body's, the plain one
        Assertions.assertEquals(
                "gauntlets.glb#padded",
                com.ikalagaming.characters.view.PartAssembler.meshFor(
                        gauntlets, "male", "bulky", plan));
        Assertions.assertEquals(
                "gauntlets.glb#plain",
                com.ikalagaming.characters.view.PartAssembler.meshFor(
                        gauntlets, "male", null, plan));
        Assertions.assertEquals(
                "gauntlets.glb#plain",
                com.ikalagaming.characters.view.PartAssembler.meshFor(
                        gauntlets, "male", "tight", plan));
        Assertions.assertEquals(
                List.of("padded"),
                com.ikalagaming.characters.view.PartAssembler.formsOf(gauntlets, "male", plan));

        Design design =
                new Design(
                        Id.parse("ikala:design/test"),
                        Id.parse("ikala:biped"),
                        "male",
                        null,
                        Map.of(),
                        Map.of("hand.L", Id.parse("ikala:part/biped/claw_l")),
                        Map.of("gloves", gauntlets.id()),
                        null,
                        Map.of(),
                        List.of());
        LookStack.Shown shown = LookStack.resolve(design, new Equipment(Map.of()), registry);
        Assertions.assertEquals(Map.of("gloves", "bulky"), shown.forms());
        Assertions.assertEquals(Map.of("hand.L", "bulky"), shown.slotForms());
    }

    /** Each slot takes its body part's form; layers and thin body parts add none. */
    @Test
    void slotForms() {
        Part claw = part(Id.parse("ikala:part/biped/claw_l"));
        Part stump = part(Id.parse("ikala:part/biped/forearm_stump"));
        Part gauntlets = part(Id.parse("ikala:part/biped/gauntlets"));
        Assertions.assertEquals(
                Map.of("hand.L", "bulky"),
                WearRules.slotForms(plan, List.of(stump, claw, gauntlets)));
        Assertions.assertEquals(Map.of(), WearRules.slotForms(plan, List.of(stump, gauntlets)));
    }

    /** A place's mirror is the slot's, else .L and .R swapped, else itself. */
    @Test
    void mirrored() {
        Assertions.assertEquals("hand.R", Part.mirrored(plan, "hand.L"));
        Assertions.assertEquals("shoulderPads.L", Part.mirrored(plan, "shoulderPads.R"));
        Assertions.assertEquals("helmet", Part.mirrored(plan, "helmet"));
    }
}
