package com.ikalagaming.characters.registry;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.definition.AnimStyle;
import com.ikalagaming.characters.definition.AnimationSet;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Kind;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.definition.Slider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tests for {@link CharacterLoader loading definitions}, using the small biped in the test
 * resources.
 *
 * @author Ches Burks
 */
class TestCharacterLoader {
    /** Where the test content is copied, so tests can change it. */
    @TempDir Path temp;

    /**
     * The test content folder.
     *
     * @return The folder.
     * @throws URISyntaxException If the resource URL is broken.
     */
    private static Path content() throws URISyntaxException {
        return Path.of(TestCharacterLoader.class.getResource("/content/ikala").toURI());
    }

    /**
     * Copy the test content somewhere it can be changed.
     *
     * @return The copy.
     * @throws Exception If it can't be copied.
     */
    private Path copy() throws Exception {
        Path source = content();
        Path target = temp.resolve("ikala");
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path to = target.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(file, to);
                }
            }
        }
        return target;
    }

    /**
     * Write a file, making its folder.
     *
     * @param file The file.
     * @param text Its text.
     * @throws IOException If it can't be written.
     */
    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /**
     * The problems as text, one per line.
     *
     * @param result The load result.
     * @return The text.
     */
    private static String text(CharacterLoader.Result result) {
        return result.problems().stream().map(Problem::toString).collect(Collectors.joining("\n"));
    }

    /** The test content loads with no problems. */
    @Test
    void testLoadsCleanly() throws Exception {
        CharacterLoader.Result result =
                CharacterLoader.load(List.of(new ContentSource("ikala", content(), "test")));
        Assertions.assertTrue(result.problems().isEmpty(), text(result));
        CharacterRegistry registry = result.registry();
        Assertions.assertEquals(1, registry.all(Rig.class).size());
        Assertions.assertEquals(2, registry.all(Slider.class).size());
        Assertions.assertEquals(5, registry.all(Part.class).size());
        BodyPlan biped = registry.get(BodyPlan.class, Id.parse("ikala:biped"));
        Assertions.assertNotNull(biped);
        Assertions.assertEquals(2, biped.matchingSliders(registry.all(Slider.class)).size());
    }

    /** A child gets its parent's fields, with its own on top. */
    @Test
    void testExtends() throws Exception {
        CharacterLoader.Result result =
                CharacterLoader.load(List.of(new ContentSource("ikala", content())));
        Part spiked =
                result.registry().get(Part.class, Id.parse("ikala:part/biped/helmet_iron_spiked"));
        Assertions.assertEquals(Part.PartKind.LAYER, spiked.kind());
        Assertions.assertEquals(List.of("helmet"), spiked.fills());
        Assertions.assertEquals(List.of("hair"), spiked.hidesLayers());
        Assertions.assertEquals(List.of("spiky"), spiked.tags());
    }

    /** Objects merge field by field, and null removes an inherited field. */
    @Test
    void testMerge() {
        var parent =
                com.google.gson.JsonParser.parseString(
                                "{\"id\":\"a:p\",\"m\":{\"x\":1,\"y\":2},\"l\":[1],\"t\":1}")
                        .getAsJsonObject();
        var child =
                com.google.gson.JsonParser.parseString(
                                "{\"id\":\"a:c\",\"m\":{\"y\":3},\"l\":[2],\"t\":null}")
                        .getAsJsonObject();
        Assertions.assertEquals(
                "{\"id\":\"a:c\",\"m\":{\"x\":1,\"y\":3},\"l\":[2]}",
                CharacterLoader.merge(parent, child).toString());
    }

    /** A broken definition drops out with everything that needs it, and the rest still load. */
    @Test
    void testCascade() throws Exception {
        Path root = copy();
        // The material template now has a range on a color, which is an error
        write(
                root.resolve("materialTemplate/metal.json"),
                "{ \"id\": \"ikala:mat/metal\",\n"
                        + "  \"params\": { \"tint\": { \"type\": \"color\", \"range\": [0, 1] } } }");
        CharacterLoader.Result result =
                CharacterLoader.load(List.of(new ContentSource("ikala", root, "mod")));
        String problems = text(result);
        Assertions.assertTrue(
                problems.contains("mod/materialTemplate/metal.json:2:")
                        && problems.contains("error: Only numbers have a range"),
                problems);
        Assertions.assertTrue(
                problems.contains("The materialTemplate ikala:mat/metal has errors"), problems);
        CharacterRegistry registry = result.registry();
        Assertions.assertNull(registry.get(Part.class, Id.parse("ikala:part/biped/forearm_robot")));
        Assertions.assertNotNull(registry.get(Part.class, Id.parse("ikala:part/biped/cape_basic")));
        Assertions.assertNotNull(registry.get(BodyPlan.class, Id.parse("ikala:biped")));
    }

    /** A plan losing its rig takes its parts with it, a level further down. */
    @Test
    void testDeepCascade() throws Exception {
        Path root = copy();
        write(
                root.resolve("rig/biped.json"),
                "{ \"id\": \"ikala:rig/biped\", \"bones\": [ { \"name\": \"root\" },"
                        + " { \"name\": \"root\" } ] }");
        CharacterLoader.Result result =
                CharacterLoader.load(List.of(new ContentSource("ikala", root)));
        Assertions.assertTrue(result.registry().all(BodyPlan.class).isEmpty());
        Assertions.assertTrue(result.registry().all(Part.class).isEmpty());
        Assertions.assertTrue(text(result).contains("The bodyPlan ikala:biped has errors"));
    }

    /** IDs must be in the source's namespace and unique within a kind. */
    @Test
    void testIds() throws Exception {
        Path root = temp.resolve("mod");
        write(
                root.resolve("palette/a.json"),
                "[ { \"id\": \"other:palette/a\", \"colors\": {} },\n"
                        + "  { \"id\": \"mod:palette/b\", \"colors\": {} },\n"
                        + "  { \"id\": \"mod:palette/b\", \"colors\": {} },\n"
                        + "  { \"colors\": {} } ]");
        CharacterLoader.Result result =
                CharacterLoader.load(List.of(new ContentSource("mod", root, "mod")));
        String problems = text(result);
        Assertions.assertTrue(problems.contains("namespace 'mod', not 'other'"), problems);
        Assertions.assertTrue(problems.contains("mod/palette/a.json:3:"), problems);
        Assertions.assertTrue(problems.contains("is already defined in"), problems);
        Assertions.assertTrue(problems.contains("needs an \"id\" string"), problems);
        Assertions.assertEquals(1, result.registry().all(Kind.PALETTE.getType()).size());
    }

    /** Broken JSON and parent loops are reported, not thrown. */
    @Test
    void testBrokenFiles() throws Exception {
        Path root = temp.resolve("mod");
        write(root.resolve("palette/broken.json"), "{ \"id\": \"mod:palette/x\", ");
        write(
                root.resolve("palette/loop.json"),
                "[ { \"id\": \"mod:palette/a\", \"extends\": \"mod:palette/b\" },"
                        + " { \"id\": \"mod:palette/b\", \"extends\": \"mod:palette/a\" } ]");
        CharacterLoader.Result result =
                CharacterLoader.load(List.of(new ContentSource("mod", root, "mod")));
        String problems = text(result);
        Assertions.assertTrue(problems.contains("broken.json"), problems);
        Assertions.assertTrue(problems.contains("loop back"), problems);
        Assertions.assertEquals(0, result.registry().size());
    }

    /** Numeric IDs follow ID order and the hash depends only on content, not file layout. */
    @Test
    void testNumericIdsAndHash() throws Exception {
        CharacterRegistry first =
                CharacterLoader.load(List.of(new ContentSource("ikala", content()))).registry();

        Path root = copy();
        Path more = root.resolve("part/more.json");
        String parts = Files.readString(more, StandardCharsets.UTF_8);
        Files.delete(more);
        Files.delete(root.resolve("part/forearm_robot.json"));
        // The same parts, split differently, in a nested folder, with different formatting
        write(root.resolve("part/nested/all.json"), parts.replace("\n", "\n\n"));
        write(
                root.resolve("part/z.json"),
                Files.readString(
                        content().resolve("part/forearm_robot.json"), StandardCharsets.UTF_8));
        CharacterRegistry second =
                CharacterLoader.load(List.of(new ContentSource("ikala", root))).registry();

        Assertions.assertEquals(first.getContentHash(), second.getContentHash());
        List<Part> all = first.all(Part.class);
        for (int i = 0; i < all.size(); ++i) {
            Assertions.assertEquals(i, first.numericId(Kind.PART, all.get(i).id()));
            Assertions.assertEquals(all.get(i), second.get(Part.class, i));
            if (i > 0) {
                Assertions.assertTrue(all.get(i - 1).id().compareTo(all.get(i).id()) < 0);
            }
        }
        Assertions.assertEquals(-1, first.numericId(Kind.PART, Id.parse("ikala:nope")));

        write(
                root.resolve("palette/extra.json"),
                "{ \"id\": \"ikala:palette/x\", \"colors\": {} }");
        CharacterRegistry third =
                CharacterLoader.load(List.of(new ContentSource("ikala", root))).registry();
        Assertions.assertNotEquals(first.getContentHash(), third.getContentHash());
    }

    /** Warnings are reported once, and don't keep a definition out. */
    @Test
    void testWarnings() throws Exception {
        Path root = copy();
        write(
                root.resolve("palette/odd.json"),
                "{ \"id\": \"ikala:palette/odd\", \"colors\": { \"glow\": \"#ffffff\" } }");
        CharacterLoader.Result result =
                CharacterLoader.load(List.of(new ContentSource("ikala", root)));
        Assertions.assertFalse(result.hasErrors(), text(result));
        Assertions.assertEquals(1, result.problems().size(), text(result));
        Assertions.assertNotNull(
                result.registry()
                        .get(
                                com.ikalagaming.characters.definition.Palette.class,
                                Id.parse("ikala:palette/odd")));
    }

    /** Animation sets and styles are checked against their plan, and repeats are warned about. */
    @Test
    void testAnimationProblems() throws Exception {
        Path root = copy();
        write(
                root.resolve("animationSet/broken.json"),
                """
                {
                  "id": "ikala:anim/broken",
                  "plan": "ikala:biped",
                  "actions": {
                    "idle": { "clips": [ { "clip": "test.glb#idle_2" } ] },
                    "Attack": { "clips": [ { "clip": "test.glb" } ], "mask": "legs" },
                    "hit": {
                      "clips": [ { "clip": "a.glb#b", "rate": 0, "events": [
                        { "at": -1, "event": "fx:ikala:fx/missing" }, { "at": 0, "event": "fx:nope" } ] } ]
                    }
                  },
                  "variants": { "tall": {} }
                }
                """);
        CharacterLoader.Result result =
                CharacterLoader.load(List.of(new ContentSource("ikala", root)));
        String problems = text(result);
        Assertions.assertTrue(problems.contains("ikala:anim/biped comes first"), problems);
        Assertions.assertTrue(problems.contains("lowercase words joined by dots"), problems);
        Assertions.assertTrue(problems.contains("A clip is its file"), problems);
        Assertions.assertTrue(problems.contains("no bone group 'legs'"), problems);
        Assertions.assertTrue(problems.contains("A rate is above 0"), problems);
        Assertions.assertTrue(problems.contains("can't come before 0"), problems);
        Assertions.assertTrue(problems.contains("ikala:fx/missing"), problems);
        Assertions.assertTrue(problems.contains("fx: events name an effect ID"), problems);
        Assertions.assertTrue(problems.contains("no variant 'tall'"), problems);

        CharacterRegistry registry =
                CharacterLoader.load(List.of(new ContentSource("ikala", content()))).registry();
        AnimationSet biped = registry.get(AnimationSet.class, Id.parse("ikala:anim/biped"));
        Assertions.assertTrue(biped.actions().get("move").blendsBySpeed());
        Assertions.assertEquals("test.glb", biped.actions().get("idle").clips().get(0).file());
        Assertions.assertEquals("idle", biped.actions().get("idle").clips().get(0).name());
        Assertions.assertEquals(
                Id.parse("ikala:fx/sparks"),
                biped.actions().get("attack.melee").clips().get(0).events().get(1).effect());
        Assertions.assertNotNull(registry.get(AnimStyle.class, Id.parse("ikala:style/zombie")));
    }

    /** Item looks are checked against their plans' slots, layers and sockets. */
    @Test
    void testItemProblems() throws Exception {
        Path root = copy();
        write(
                root.resolve("item/broken.json"),
                """
                [
                  { "id": "ikala:item/hat", "slots": ["head", "feet"],
                    "looks": { "ikala:biped": { "part": "ikala:part/biped/forearm_stump" } } },
                  { "id": "ikala:item/club", "slots": ["mainHand"],
                    "looks": { "ikala:biped": { "mesh": "club.glb", "states": { "worn": "head_top" },
                      "attach": { "belt": {} }, "style": "ikala:style/missing" } } },
                  { "id": "ikala:item/both", "slots": ["mainHand"],
                    "looks": { "ikala:biped": { "part": "ikala:part/biped/helmet_iron",
                      "mesh": "a.glb#a" }, "biped": {} } }
                ]
                """);
        String problems = text(CharacterLoader.load(List.of(new ContentSource("ikala", root))));
        Assertions.assertTrue(problems.contains("no equipment slot 'feet'"), problems);
        Assertions.assertTrue(problems.contains("isn't a layer part"), problems);
        Assertions.assertTrue(problems.contains("A mesh is its file"), problems);
        Assertions.assertTrue(problems.contains("holds items in 'head_top'"), problems);
        Assertions.assertTrue(problems.contains("socket type 'belt'"), problems);
        Assertions.assertTrue(problems.contains("ikala:style/missing"), problems);
        Assertions.assertTrue(problems.contains("worn as a part or held as a mesh"), problems);
        Assertions.assertTrue(problems.contains("Looks are by plan ID"), problems);
    }
}
