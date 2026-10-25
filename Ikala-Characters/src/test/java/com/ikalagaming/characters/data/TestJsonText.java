package com.ikalagaming.characters.data;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link JsonText reading JSON text}.
 *
 * @author Ches Burks
 */
class TestJsonText {

    /** Comments are allowed. */
    @Test
    void testComments() {
        List<JsonText.ParseError> errors = new ArrayList<>();
        JsonElement json =
                JsonText.parse("// a comment\n{ /* inline */ \"a\": 1 # hash\n}", errors::add);
        Assertions.assertTrue(errors.isEmpty(), errors.toString());
        Assertions.assertEquals(1, json.getAsJsonObject().get("a").getAsInt());
    }

    /** Repeated keys are reported where they are, and parsing carries on. */
    @Test
    void testDuplicateKeys() {
        List<JsonText.ParseError> errors = new ArrayList<>();
        JsonElement json = JsonText.parse("{\n  \"a\": 1,\n  \"a\": 2\n}", errors::add);
        Assertions.assertNotNull(json);
        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals(3, errors.get(0).position().line());
        Assertions.assertTrue(errors.get(0).message().contains("'a'"));
    }

    /** Broken JSON is reported with its position and no tree. */
    @Test
    void testSyntaxError() {
        List<JsonText.ParseError> errors = new ArrayList<>();
        Assertions.assertNull(JsonText.parse("{\n  \"a\": [1, 2\n}", errors::add));
        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals(3, errors.get(0).position().line());
    }

    /** Text after the value is an error. */
    @Test
    void testTrailingText() {
        List<JsonText.ParseError> errors = new ArrayList<>();
        Assertions.assertNull(JsonText.parse("{} {}", errors::add));
        Assertions.assertEquals(1, errors.size());
    }

    /** Paths are found on the line their value starts on. */
    @Test
    void testLocate() {
        String text =
                "{\n  \"slots\": [\n    { \"id\": \"head\" },\n    { \"id\": \"torso\",\n"
                        + "      \"bones\": [\"chest\",\n        \"spine\"] }\n  ]\n}";
        Assertions.assertEquals(1, JsonText.locate(text, "$").line());
        Assertions.assertEquals(2, JsonText.locate(text, "$.slots").line());
        Assertions.assertEquals(3, JsonText.locate(text, "$.slots[0].id").line());
        Assertions.assertEquals(5, JsonText.locate(text, "$.slots[1].bones").line());
        Assertions.assertEquals(6, JsonText.locate(text, "$.slots[1].bones[1]").line());
        Assertions.assertEquals(JsonText.Position.UNKNOWN, JsonText.locate(text, "$.slots[2]"));
    }
}
