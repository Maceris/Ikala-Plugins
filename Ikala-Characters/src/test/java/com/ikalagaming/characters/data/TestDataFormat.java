package com.ikalagaming.characters.data;

import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Slider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link DataFormat mapping JSON onto records}.
 *
 * @author Ches Burks
 */
class TestDataFormat {

    /** Collects problems as "path: message". */
    private static final class Collect implements Report {
        /** The problems so far. */
        final List<String> problems = new ArrayList<>();

        @Override
        public void error(@NonNull String path, @NonNull String message) {
            problems.add(path + ": " + message);
        }

        @Override
        public void warning(@NonNull String path, @NonNull String message) {
            problems.add("warning " + path + ": " + message);
        }
    }

    /**
     * Parse JSON that must be valid.
     *
     * @param text The text.
     * @return The JSON.
     */
    private static JsonElement json(String text) {
        return JsonText.parse(text, error -> Assertions.fail(error.toString()));
    }

    /** Fields of every supported type read, defaults fill in, and optional fields are empty. */
    @Test
    void testRead() {
        Collect report = new Collect();
        Part part =
                DataFormat.readDefinition(
                        json(
                                """
                                { "id": "ikala:part/a", "plan": "ikala:biped", "kind": "accessory",
                                  "fits": ["back"], "meshes": { "*": "a" },
                                  "sockets": { "back": { "bone": "chest", "offset": [0, 0.5, 1] } } }
                                """),
                        Part.class,
                        report);
        Assertions.assertTrue(report.problems.isEmpty(), report.problems.toString());
        Assertions.assertEquals(Id.parse("ikala:part/a"), part.id());
        Assertions.assertEquals(Part.PartKind.ACCESSORY, part.kind());
        Assertions.assertTrue(part.rigid());
        Assertions.assertFalse(part.mirror());
        Assertions.assertTrue(part.fills().isEmpty());
        Assertions.assertNull(part.extension());
        Assertions.assertEquals(new Vec3(0, 0.5f, 1), part.sockets().get("back").offset());
        Assertions.assertNull(part.sockets().get("back").rotate());
    }

    /** Every problem in a definition is reported, with its path. */
    @Test
    void testProblems() {
        Collect report = new Collect();
        Slider slider =
                DataFormat.readDefinition(
                        json(
                                """
                                { "id": "ikala:slider/a", "vector": "yes", "colour": 1,
                                  "ops": [ { "bones": ["a"], "type": "girth", "min": 1 } ],
                                  "ranges": { "player": [1, -1] } }
                                """),
                        Slider.class,
                        report);
        Assertions.assertNull(slider);
        String all = String.join("\n", report.problems);
        Assertions.assertTrue(all.contains("$.vector: Expected true or false"), all);
        Assertions.assertTrue(all.contains("$.colour: Unknown field 'colour'"), all);
        Assertions.assertTrue(all.contains("$.ops[0].type: Expected one of [length, shape]"), all);
        Assertions.assertTrue(all.contains("$.ops[0]: Missing the required field 'max'"), all);
        Assertions.assertTrue(all.contains("$.ranges.player: Expected a range"), all);
        Assertions.assertEquals(5, report.problems.size(), all);
    }

    /** Whole numbers are checked, and the extends field is left to the loader. */
    @Test
    void testIntegersAndExtends() {
        Collect report = new Collect();
        DataFormat.readDefinition(
                json(
                        """
                        { "id": "ikala:biped", "extends": "ikala:base", "rig": "ikala:rig",
                          "variants": [ { "id": "male" } ],
                          "sockets": [ { "id": "back", "bone": "b", "max": 1.5 } ] }
                        """),
                BodyPlan.class,
                report);
        Assertions.assertEquals(
                List.of("$.sockets[0].max: Expected a whole number, not 1.5"), report.problems);
    }

    /** Writing a record and reading it back gives the same record. */
    @Test
    void testRoundTrip() {
        Collect report = new Collect();
        String text =
                """
                { "id": "ikala:slider/a", "group": "face", "vector": true,
                  "ops": [ { "bones": ["a", "b"], "type": "shape", "min": 0.35, "max": 1.3 } ],
                  "ranges": { "player": [-0.5, 1] } }
                """;
        Slider slider = DataFormat.readDefinition(json(text), Slider.class, report);
        JsonElement written = DataFormat.write(slider);
        Slider again = DataFormat.readDefinition(written, Slider.class, report);
        Assertions.assertTrue(report.problems.isEmpty(), report.problems.toString());
        Assertions.assertEquals(slider, again);
        Assertions.assertFalse(written.getAsJsonObject().has("label"));
    }

    /** Schemas list fields, required ones, defaults and descriptions, and allow extends. */
    @Test
    void testSchema() {
        JsonObject schema = DataFormat.schema(Part.class, "part");
        JsonObject part = schema.getAsJsonObject("$defs").getAsJsonObject("Part");
        JsonObject properties = part.getAsJsonObject("properties");
        Assertions.assertTrue(properties.has("extends"));
        Assertions.assertEquals(
                "true", properties.getAsJsonObject("rigid").get("default").toString());
        Assertions.assertTrue(properties.getAsJsonObject("plan").has("description"));
        Assertions.assertFalse(part.get("additionalProperties").getAsBoolean());
        Assertions.assertTrue(part.get("anyOf").toString().contains("\"kind\""));
        Assertions.assertTrue(schema.getAsJsonObject("$defs").has("MaterialSlot"));
    }
}
