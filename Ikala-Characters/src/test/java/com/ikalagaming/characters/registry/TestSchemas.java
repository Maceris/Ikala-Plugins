package com.ikalagaming.characters.registry;

import com.ikalagaming.characters.definition.Kind;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Checks that the JSON Schemas shipped in the plugin's resources match the definition records.
 *
 * @author Ches Burks
 */
class TestSchemas {

    /**
     * Every kind's shipped schema is what the records generate now. If this fails after changing a
     * definition record, run the plugin's writeSchemas Gradle task and commit the result.
     *
     * @throws IOException If a schema can't be read.
     */
    @Test
    void testShippedSchemasAreCurrent() throws IOException {
        Map<Kind, String> generated = Schemas.generate();
        for (Kind kind : Kind.values()) {
            String resource = Schemas.RESOURCE_FOLDER + "/" + Schemas.fileName(kind);
            try (InputStream stream =
                    TestSchemas.class.getClassLoader().getResourceAsStream(resource)) {
                Assertions.assertNotNull(stream, resource + " is missing; run writeSchemas");
                String shipped =
                        new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                                .replace("\r\n", "\n");
                Assertions.assertEquals(
                        generated.get(kind),
                        shipped,
                        resource + " is out of date; run the writeSchemas task");
            }
        }
    }
}
