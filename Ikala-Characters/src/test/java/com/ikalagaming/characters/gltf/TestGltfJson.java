package com.ikalagaming.characters.gltf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Missing or misshapen glTF JSON fields give errors naming the field, not Gson exceptions. */
class TestGltfJson {

    /**
     * Parse a JSON object.
     *
     * @param text The JSON.
     * @return The object.
     */
    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void readsFieldsOfTheRightShape() {
        JsonObject object = json("{\"n\": 3, \"f\": 1.5, \"s\": \"x\", \"b\": true, \"a\": [1]}");
        assertEquals(3, GltfJson.integer(object, "n"));
        assertEquals(1.5f, GltfJson.number(object, "f"));
        assertEquals("x", GltfJson.string(object, "s"));
        assertTrue(GltfJson.bool(object, "b"));
        assertEquals(1, GltfJson.array(object, "a").size());
    }

    @Test
    void namesMissingAndMisshapenFields() {
        JsonObject object = json("{\"s\": \"x\", \"n\": null, \"o\": 4}");
        IllegalArgumentException missing =
                assertThrows(
                        IllegalArgumentException.class, () -> GltfJson.integer(object, "count"));
        assertTrue(missing.getMessage().contains("\"count\""));
        assertThrows(IllegalArgumentException.class, () -> GltfJson.integer(object, "n"));
        IllegalArgumentException shape =
                assertThrows(IllegalArgumentException.class, () -> GltfJson.integer(object, "s"));
        assertTrue(shape.getMessage().contains("\"s\""));
        assertThrows(IllegalArgumentException.class, () -> GltfJson.object(object, "o"));
        assertThrows(IllegalArgumentException.class, () -> GltfJson.array(object, "s"));
        assertThrows(IllegalArgumentException.class, () -> GltfJson.string(object, "o"));
        assertThrows(IllegalArgumentException.class, () -> GltfJson.bool(object, "s"));
    }

    @Test
    void accessorWithoutCountIsACleanError(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("bad.gltf");
        Files.writeString(
                file,
                "{\"asset\": {\"version\": \"2.0\"}, \"accessors\": [{\"type\": \"VEC3\"}]}",
                StandardCharsets.UTF_8);
        GltfFile gltf = GltfFile.read(file);
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> gltf.count(0));
        assertTrue(error.getMessage().contains("\"count\""));
    }
}
