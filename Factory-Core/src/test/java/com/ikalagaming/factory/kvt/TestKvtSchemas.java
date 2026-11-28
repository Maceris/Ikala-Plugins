package com.ikalagaming.factory.kvt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ikalagaming.factory.registry.ContentSchemas;
import com.ikalagaming.util.schema.Schema;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Content files read through their schemas, and written back with only what differs. */
class TestKvtSchemas {
    /** Lotomation's content folder, beside Ikala-Plugins. */
    private static final Path LOTOMATION =
            Path.of("../../Lotomation/plugins/Lotomation/data").toAbsolutePath().normalize();

    private static Schema.Values read(String text, Schema schema) {
        return KvtSchemas.read(TreeStringSerialization.fromString(text).orElseThrow(), schema);
    }

    @Test
    void lotomationsFlatContentReadsWithoutProblems() throws Exception {
        assumeTrue(Files.isDirectory(LOTOMATION), "Lotomation isn't beside Ikala-Plugins");
        int read = 0;
        for (String folder :
                List.of(
                        "tags",
                        "materials",
                        "blocks",
                        ContentSchemas.LOOKS_FOLDER,
                        ContentSchemas.WATER_LOOKS_FOLDER)) {
            final Schema schema = ContentSchemas.forFolder(folder).orElseThrow();
            try (Stream<Path> files = Files.walk(LOTOMATION.resolve(folder))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".kvt")).toList()) {
                    final Schema.Values values = read(Files.readString(file), schema);
                    assertEquals(List.of(), values.problems(), file.toString());
                    ++read;
                }
            }
        }
        assertTrue(read > 50, "Read " + read);
    }

    @Test
    void defaultsAreLeftOutAndTheRestKept() {
        final String text = "/* A rock */\n{material:\"stone\", tags:[T;\"solid\"], glow:3}\n";
        assertEquals(
                "/* A rock */\n"
                        + "{material:\"stone\", tags:[T;\"solid\", \"flammable\"], collision:\"none\","
                        + " glow:3}\n",
                KvtSchemas.write(
                        text,
                        ContentSchemas.BLOCK,
                        Map.of(
                                "material",
                                "stone",
                                "tags",
                                List.of("solid", "flammable"),
                                "collision",
                                "none",
                                "item",
                                true)),
                "The default item is left out, the unknown glow kept");
    }

    @Test
    void numbersKeepTheirTypes() {
        final String text = "{chlorophyll:0.05F, cdom:0.01F, sediment:0.0F}";
        final Schema.Values values = read(text, ContentSchemas.WATER_LOOK);
        final Map<String, Object> changed = new LinkedHashMap<>(values.given());
        changed.put("sediment", 2.5);
        changed.put("fetch", 500.0);
        assertEquals(
                "{chlorophyll:0.05F, cdom:0.01F, sediment:2.5F, fetch:500.0F}",
                KvtSchemas.write(text, ContentSchemas.WATER_LOOK, changed, NodeType.FLOAT),
                "Floats stay floats; new numbers take the given type");
    }
}
