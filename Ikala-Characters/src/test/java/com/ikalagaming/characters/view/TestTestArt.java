package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.data.Report;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.Palette;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.design.DesignCodec;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.registry.BuiltInContent;
import com.ikalagaming.characters.registry.CharacterLoader;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.registry.ContentSource;

import com.google.gson.JsonObject;
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
        for (String name : List.of("mannequin_male", "mannequin_female", "fighter", "caster")) {
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

    /** The fighter builds from the mannequin's parts, with skin masks taking the skin color. */
    @Test
    void testAssemble() throws Exception {
        CharacterRegistry registry = load();
        Assumptions.assumeTrue(
                Files.exists(TEST_CONTENT.resolve("mannequin/male.glb")), "No mannequin");
        Design man = registry.get(Design.class, Id.parse("test:design/fighter"));
        BodyPlan biped = registry.get(BodyPlan.class, man.plan());
        Map<String, Id> worn = man.worn(biped, id -> registry.get(Part.class, id));
        List<Part> parts = new ArrayList<>();
        worn.values().forEach(id -> parts.add(registry.get(Part.class, id)));

        PartAssembler.Assembled assembled =
                PartAssembler.assemble(
                        parts,
                        biped,
                        man.variant(),
                        registry,
                        TEST_CONTENT.resolve("mannequin/male.glb"));
        Assertions.assertNotNull(assembled);
        Assertions.assertEquals(biped.rig(), assembled.character().rig().id());
        for (Id id : worn.values()) {
            Assertions.assertFalse(assembled.partsOf(id).isEmpty(), id.toString());
        }
        List<Integer> torso = assembled.partsOf(Id.parse("test:part/mannequin/torso"));
        Assertions.assertFalse(torso.isEmpty());
        List<String> channels = biped.allChannels();
        int[] mapped = assembled.channels().get(torso.get(0));
        Assertions.assertEquals(channels.indexOf(Palette.SKIN), mapped[0]);
        Assertions.assertEquals(PartAssembler.NO_CHANNEL, mapped[1]);
        // Welded neighbors show none of the caps the mannequin's file carries
        Assertions.assertTrue(
                assembled.character().parts().stream()
                        .noneMatch(p -> p.name().contains(PartAssembler.CAP_INFIX)));

        // An upper arm open at the shoulder, like a skeleton arm's, has the torso cap its ring
        Id armId = Id.parse("test:part/mannequin/upperarm.l");
        Part arm = registry.get(Part.class, armId);
        JsonObject json = DataFormat.write(arm).getAsJsonObject();
        JsonObject seams = new JsonObject();
        seams.addProperty("shoulder.L", "open");
        json.add(Part.FIELD_SEAMS, seams);
        List<String> problems = new ArrayList<>();
        Report report =
                new Report() {
                    @Override
                    public void error(String path, String message) {
                        problems.add(path + ": " + message);
                    }

                    @Override
                    public void warning(String path, String message) {
                        problems.add(path + ": " + message);
                    }
                };
        Part openArm = DataFormat.readDefinition(json, Part.class, report);
        Assertions.assertEquals(List.of(), problems);
        parts.replaceAll(part -> part.id().equals(armId) ? openArm : part);
        PartAssembler.Assembled capped =
                PartAssembler.assemble(
                        parts,
                        biped,
                        man.variant(),
                        registry,
                        TEST_CONTENT.resolve("mannequin/male.glb"));
        List<String> caps =
                capped.character().parts().stream()
                        .map(ImportedCharacter.Part::name)
                        .filter(name -> name.contains(PartAssembler.CAP_INFIX))
                        .toList();
        Assertions.assertEquals(List.of("mannequin_torso_cap_shoulder.L"), caps);
    }

    /** Every weapon item finds its mesh, drawn and sheathed, on the fighter's skeleton. */
    @Test
    void testWeapons() throws Exception {
        CharacterRegistry registry = load();
        Assumptions.assumeTrue(
                Files.exists(TEST_CONTENT.resolve("weapons/weapons.glb")), "No weapons");
        Design man = registry.get(Design.class, Id.parse("test:design/fighter"));
        BodyPlan biped = registry.get(BodyPlan.class, man.plan());
        com.ikalagaming.characters.pose.Skeleton skeleton =
                com.ikalagaming.characters.gltf.GltfImporter.read(
                                TEST_CONTENT.resolve("mannequin/male.glb"))
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
