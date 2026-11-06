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
        for (String name :
                List.of(
                        "mannequin_male",
                        "mannequin_female",
                        "fighter",
                        "caster",
                        "winged",
                        "caped")) {
            Assertions.assertNotNull(
                    registry.get(Design.class, Id.parse("test:design/" + name)), name);
        }
        for (Design design : designs) {
            Assertions.assertEquals(
                    List.of(),
                    design.validate(registry, Slider.CONTEXT_PLAYER),
                    design.id().toString());
            byte[] bytes = DesignCodec.encode(design, registry);
            Design decoded = DesignCodec.decode(bytes, design.id(), registry);
            Assertions.assertEquals(design.parts(), decoded.parts());
            Assertions.assertEquals(design.layers(), decoded.layers());
            Assertions.assertEquals(design.extensions(), decoded.extensions());
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

    /**
     * The parts on extension chains go on their chains: wings and a cape can't be worn together,
     * and the tail, made on a chain 1.3 times the rig's, is put back on the rig's chain so posing
     * with its longer chain puts every vertex where it was made.
     */
    @Test
    void testExtensions() throws Exception {
        CharacterRegistry registry = load();
        Assumptions.assumeTrue(
                Files.exists(TEST_CONTENT.resolve("extensions/test_extensions_male.glb")),
                "No extension parts");
        Design winged = registry.get(Design.class, Id.parse("test:design/winged"));
        BodyPlan biped = registry.get(BodyPlan.class, winged.plan());
        Map<String, Id> worn = winged.worn(biped, id -> registry.get(Part.class, id));
        Assertions.assertEquals(
                Id.parse("test:part/extension/wing_l"), worn.get("wing.L"), worn.toString());
        Assertions.assertEquals(Id.parse("test:part/extension/tail"), worn.get("tail"));

        // Wings and a cape both take the back
        Design both =
                new Design(
                        winged.id(),
                        winged.plan(),
                        winged.variant(),
                        null,
                        Map.of(),
                        winged.parts(),
                        Map.of("cape", Id.parse("test:part/extension/cape")),
                        null,
                        Map.of(),
                        List.of(),
                        null,
                        winged.extensions());
        Assertions.assertFalse(both.validate(registry, Slider.CONTEXT_PLAYER).isEmpty());

        Part tail = registry.get(Part.class, Id.parse("test:part/extension/tail"));
        float[] lengths = tail.chainLengths(biped);
        Assertions.assertNotNull(lengths);
        Assertions.assertEquals(biped.extension("tail").bones().size(), lengths.length);
        ImportedCharacter made =
                com.ikalagaming.characters.gltf.GltfImporter.read(
                                TEST_CONTENT.resolve("extensions/test_extensions_male.glb"))
                        .onRig(
                                registry.get(
                                        com.ikalagaming.characters.definition.Rig.class,
                                        biped.rig()));
        ImportedCharacter.Part original =
                made.parts().stream()
                        .filter(p -> "test_tail".equals(p.name()))
                        .findFirst()
                        .orElseThrow();
        com.ikalagaming.characters.pose.Skeleton skeleton = made.skeleton();
        Map<String, float[]> chains = Map.of("tail", lengths);
        ImportedCharacter.Part onRig = PartAssembler.onRigChain(original, skeleton, biped, chains);
        // On the rig's chain the tail is shorter than as made
        Assertions.assertTrue(
                onRig.min().y() > original.min().y() + 0.1f,
                "The tail didn't get shorter: " + onRig.min() + " " + original.min());

        // Posing with the longer chain puts it back
        com.ikalagaming.characters.pose.Proportions longer =
                com.ikalagaming.characters.pose.Proportions.neutral(skeleton)
                        .withChains(biped, chains);
        com.ikalagaming.characters.pose.PoseSolver solver =
                new com.ikalagaming.characters.pose.PoseSolver(skeleton);
        solver.solve(new com.ikalagaming.characters.pose.Pose(skeleton), longer);
        float[] palette =
                new float[skeleton.size() * com.ikalagaming.characters.pose.Skeleton.MATRIX_FLOATS];
        solver.writePalette(longer, palette, 0);
        float[] before = original.vertices();
        float[] after = onRig.vertices();
        float worst = 0;
        for (int v = 0; v * 14 < after.length; ++v) {
            org.joml.Vector3f skinned = new org.joml.Vector3f();
            for (int k = 0; k < 4; ++k) {
                float weight = onRig.weights()[v * 4 + k];
                if (weight > 0) {
                    org.joml.Matrix4f skin =
                            new org.joml.Matrix4f()
                                    .set(
                                            palette,
                                            onRig.joints()[v * 4 + k]
                                                    * com.ikalagaming.characters.pose.Skeleton
                                                            .MATRIX_FLOATS);
                    skinned.add(
                            skin.transformPosition(
                                            new org.joml.Vector3f(
                                                    after[v * 14],
                                                    after[v * 14 + 1],
                                                    after[v * 14 + 2]))
                                    .mul(weight));
                }
            }
            worst =
                    Math.max(
                            worst,
                            skinned.distance(
                                    before[v * 14], before[v * 14 + 1], before[v * 14 + 2]));
        }
        Assertions.assertTrue(worst < 1e-4f, "Off by " + worst + " m");
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
