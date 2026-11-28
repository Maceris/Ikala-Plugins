package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ikalagaming.factory.kvt.KvtSchemas;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeStringSerialization;
import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.TypeRegistry;
import com.ikalagaming.factory.world.gen.data.WorldgenSchemas;
import com.ikalagaming.factory.world.gen.data.WorldgenTypes;
import com.ikalagaming.util.schema.Schema;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** World generation's schemas: every type has one, and Lotomation's files read through them. */
class TestWorldgenSchemas {
    /** Lotomation's content folder, beside Ikala-Plugins. */
    private static final Path LOTOMATION =
            Path.of("../../Lotomation/plugins/Lotomation/data").toAbsolutePath().normalize();

    @Test
    void everyRegisteredTypeHasASchema() {
        WorldgenTypes.ensureRegistered();
        for (TypeRegistry<?> registry :
                List.of(
                        WorldgenTypes.DENSITY,
                        WorldgenTypes.NOISE,
                        WorldgenTypes.CONDITION,
                        WorldgenTypes.FLUIDS)) {
            for (String type : registry.names()) {
                assertTrue(registry.schemaOf(type).isPresent(), registry.name() + " " + type);
            }
        }
    }

    @Test
    void misspelledNestedFieldsAreFoundByPath() {
        WorldgenTypes.ensureRegistered();
        final Node root =
                TreeStringSerialization.fromString(
                                "{type:\"add\", args:[N;{type:\"constant\", value:2.0},"
                                        + " {type:\"constant\", valu:1.0}]}")
                        .orElseThrow();
        final Schema.Values values =
                Schema.of("file", WorldgenSchemas.root(Kind.DENSITY))
                        .read(Map.of("density", KvtSchemas.raw(root)));
        assertEquals(
                List.of("density.args[1].valu", "density.args[1].value"),
                values.problems().stream().map(Schema.Problem::field).sorted().toList(),
                values.problems().toString());
    }

    @Test
    void lotomationsWorldgenReadsThroughTheSchemasWithoutProblems() throws Exception {
        assumeTrue(Files.isDirectory(LOTOMATION), "Lotomation isn't beside Ikala-Plugins");
        WorldgenTypes.ensureRegistered();
        int read = 0;
        for (Kind kind : Kind.values()) {
            final Path folder = LOTOMATION.resolve("worldgen").resolve(kind.getFolder());
            if (!Files.isDirectory(folder)) {
                continue;
            }
            final Schema file = Schema.of("file", WorldgenSchemas.root(kind));
            try (Stream<Path> files = Files.walk(folder)) {
                for (Path path : files.filter(f -> f.toString().endsWith(".kvt")).toList()) {
                    final Node root =
                            TreeStringSerialization.fromString(Files.readString(path))
                                    .orElseThrow();
                    final Schema.Values values =
                            file.read(Map.of(kind.getFolder(), KvtSchemas.raw(root)));
                    assertEquals(List.of(), values.problems(), path.toString());
                    ++read;
                }
            }
        }
        assertTrue(read > 30, "Read " + read);
    }
}
