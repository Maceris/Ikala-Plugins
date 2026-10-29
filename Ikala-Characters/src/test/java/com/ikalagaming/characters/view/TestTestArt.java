package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.Palette;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.design.DesignCodec;
import com.ikalagaming.characters.registry.BuiltInContent;
import com.ikalagaming.characters.registry.CharacterLoader;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.registry.ContentSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The test art's part and design definitions, from tools/test_content/make_definitions.py, load
 * cleanly and build characters. Skipped when the asset editor's test content isn't there.
 *
 * @author Ches Burks
 */
class TestTestArt {
    /** The test art's folder, relative to the plugin's folder. */
    private static final Path TEST_CONTENT =
            Path.of("../../Ikala-Asset-Editor/test_content/characters");

    /**
     * Load the built-in definitions and the test art's.
     *
     * @return The registry.
     * @throws Exception If the built-in definitions can't be read.
     */
    private static CharacterRegistry load() throws Exception {
        Assumptions.assumeTrue(
                Files.isDirectory(TEST_CONTENT.resolve("part")), "No test art definitions");
        CharacterLoader.Result result =
                BuiltInContent.loadWith(List.of(new ContentSource("test", TEST_CONTENT, "test")));
        String errors =
                result.problems().stream()
                        .filter(p -> p.severity() == Problem.Severity.ERROR)
                        .map(Problem::toString)
                        .collect(Collectors.joining("\n"));
        Assertions.assertEquals("", errors);
        return result.registry();
    }

    /** The designs are valid for players, and survive encoding. */
    @Test
    void testDesigns() throws Exception {
        CharacterRegistry registry = load();
        List<Design> designs = registry.all(Design.class);
        // The generated designs, plus any the editor saved there
        for (String name : List.of("mannequin", "rpg_male", "rpg_female")) {
            Assertions.assertNotNull(
                    registry.get(Design.class, Id.parse("test:design/" + name)), name);
        }
        for (Design design : designs) {
            Assertions.assertEquals(
                    List.of(),
                    design.validate(registry, Slider.CONTEXT_PLAYER),
                    design.id().toString());
            byte[] bytes = DesignCodec.encode(design, registry);
            Assertions.assertEquals(
                    design.parts(), DesignCodec.decode(bytes, design.id(), registry).parts());
        }
    }

    /** The RPG man builds from his parts, with armor masks taking the costume's colors. */
    @Test
    void testAssemble() throws Exception {
        CharacterRegistry registry = load();
        Assumptions.assumeTrue(
                Files.exists(TEST_CONTENT.resolve("mannequin/mannequin.glb")), "No mannequin");
        Design man = registry.get(Design.class, Id.parse("test:design/rpg_male"));
        BodyPlan biped = registry.get(BodyPlan.class, man.plan());
        Map<String, Id> worn = man.worn(biped, id -> registry.get(Part.class, id));
        List<Part> parts = new ArrayList<>();
        worn.values().forEach(id -> parts.add(registry.get(Part.class, id)));
        Id armorId = Id.parse("test:part/rpg/heavy_armor");
        parts.add(registry.get(Part.class, armorId));

        PartAssembler.Assembled assembled =
                PartAssembler.assemble(
                        parts,
                        biped,
                        man.variant(),
                        registry,
                        TEST_CONTENT.resolve("mannequin/mannequin.glb"));
        Assertions.assertNotNull(assembled);
        Assertions.assertEquals(biped.rig(), assembled.character().rig().id());
        for (Id id : worn.values()) {
            Assertions.assertFalse(assembled.partsOf(id).isEmpty(), id.toString());
        }
        List<Integer> armor = assembled.partsOf(armorId);
        Assertions.assertEquals(1, armor.size());
        List<String> channels = biped.allChannels();
        int[] mapped = assembled.channels().get(armor.get(0));
        Assertions.assertEquals(channels.indexOf(Palette.PRIMARY), mapped[0]);
        Assertions.assertEquals(channels.indexOf(Palette.SECONDARY), mapped[1]);
        Assertions.assertEquals(channels.indexOf(Palette.ACCENT), mapped[2]);
        Assertions.assertEquals(PartAssembler.NO_CHANNEL, mapped[3]);
    }

    /** Every weapon item finds its mesh, drawn and sheathed, on the RPG man's skeleton. */
    @Test
    void testWeapons() throws Exception {
        CharacterRegistry registry = load();
        Assumptions.assumeTrue(
                Files.exists(TEST_CONTENT.resolve("weapons/weapons.glb")), "No weapons");
        Design man = registry.get(Design.class, Id.parse("test:design/rpg_male"));
        BodyPlan biped = registry.get(BodyPlan.class, man.plan());
        com.ikalagaming.characters.pose.Skeleton skeleton =
                com.ikalagaming.characters.gltf.GltfImporter.read(
                                TEST_CONTENT.resolve("mannequin/mannequin.glb"))
                        .onRig(
                                registry.get(
                                        com.ikalagaming.characters.definition.Rig.class,
                                        biped.rig()))
                        .skeleton();
        HeldItems held = new HeldItems();
        List<com.ikalagaming.characters.definition.Item> items =
                registry.all(com.ikalagaming.characters.definition.Item.class);
        Assertions.assertEquals(8, items.size());
        for (com.ikalagaming.characters.definition.Item item : items) {
            for (String state : List.of("drawn", "sheathed")) {
                com.ikalagaming.characters.design.Equipment equipment =
                        new com.ikalagaming.characters.design.Equipment(
                                Map.of(
                                        item.slots().get(0),
                                        new com.ikalagaming.characters.design.Equipment.Equipped(
                                                item.id(), state, null, false, Map.of())));
                Assertions.assertEquals(List.of(), equipment.validate(registry, biped.id()));
                com.ikalagaming.characters.design.LookStack.Shown shown =
                        com.ikalagaming.characters.design.LookStack.resolve(
                                man, equipment, registry);
                Assertions.assertEquals(1, shown.held().size(), item.id() + " " + state);
                Assertions.assertFalse(
                        held.meshes(shown.held(), List.of(), biped, skeleton, registry).isEmpty(),
                        item.id() + " " + state);
            }
        }
    }
}
