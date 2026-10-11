package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.gen.data.Diagnostic;
import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.WorldgenCompiler;
import com.ikalagaming.factory.world.gen.data.WorldgenData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for loading and checking world generation data.
 *
 * @author Ches Burks
 */
class TestWorldgenData {

    /**
     * Find a diagnostic.
     *
     * @param compiler The compiler, after compiling.
     * @param code The message key.
     * @return The first diagnostic with that key.
     */
    private static Diagnostic find(WorldgenCompiler compiler, String code) {
        return compiler.getDiagnostics().stream()
                .filter(d -> d.code().equals(code))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError(code + " not in " + compiler.getDiagnostics()));
    }

    /** The fixture data set has no problems at all. */
    @Test
    void testFixtureIsClean() {
        WorldgenCompiler compiler = WorldgenFixtures.compiler(WorldgenFixtures.SEED);
        compiler.compileAll();
        assertEquals(List.of(), compiler.getDiagnostics());
        assertTrue(compiler.worldType(WorldgenFixtures.WORLD, null).isPresent());
    }

    /** An unknown type names the file, the field, and what types exist. */
    @Test
    void testUnknownType() {
        WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(
                                Kind.DENSITY,
                                "t:a",
                                "{type:\"add\", args:[N;{type:\"constant\", value:1.0},"
                                        + " {type:\"blob\"}]}")
                        .compileAll();
        Diagnostic problem = find(compiler, "WORLDGEN_UNKNOWN_TYPE");
        assertEquals("density/t:a", problem.file());
        assertEquals("args[1].type", problem.field());
        assertTrue(problem.message().contains("blob"), problem.message());
        assertTrue(compiler.density("t:a", null).isEmpty(), "Rejected");
    }

    /** A missing reference is an error in the file that makes it. */
    @Test
    void testMissingReference() {
        WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(Kind.DENSITY, "t:a", "{type:\"ref\", id:\"t:nowhere\"}")
                        .compileAll();
        Diagnostic problem = find(compiler, "WORLDGEN_MISSING_REFERENCE");
        assertEquals("density/t:a", problem.file());
        assertEquals("id", problem.field());
    }

    /** Reference cycles are found, through parameters too. */
    @Test
    void testCycles() {
        WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(Kind.DENSITY, "t:a", "{type:\"ref\", id:\"t:b\"}")
                        .add(Kind.DENSITY, "t:b", "{type:\"neg\", arg:\"t:a\"}")
                        .add(Kind.DENSITY, "t:c", "{type:\"parameter\", id:\"t:p\"}")
                        .add(Kind.PARAMETER, "t:p", "{density:\"t:c\"}")
                        .compileAll();
        final long cycles =
                compiler.getDiagnostics().stream()
                        .filter(d -> "WORLDGEN_REFERENCE_CYCLE".equals(d.code()))
                        .count();
        assertEquals(2, cycles, compiler.getDiagnostics().toString());
        assertTrue(compiler.density("t:a", null).isEmpty());
        assertTrue(compiler.parameter("t:p", null).isEmpty());
    }

    /** Values out of range are rejected. */
    @Test
    void testOutOfRange() {
        WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(
                                Kind.NOISE,
                                "t:n",
                                "{type:\"simplex\", fractal:{octaves:99}, wavelength:-1.0}")
                        .compileAll();
        assertEquals("fractal.octaves", find(compiler, "WORLDGEN_BAD_OCTAVES").field());
        assertEquals("wavelength", find(compiler, "WORLDGEN_OUT_OF_RANGE").field());
    }

    /** Trees nested too deep are rejected rather than overflowing the stack. */
    @Test
    void testTooDeep() {
        StringBuilder text = new StringBuilder();
        final int levels = WorldgenCompiler.MAX_DEPTH + 5;
        for (int i = 0; i < levels; ++i) {
            text.append("{type:\"neg\", arg:");
        }
        text.append("1.0");
        text.append("}".repeat(levels));
        WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(Kind.DENSITY, "t:deep", text.toString())
                        .compileAll();
        find(compiler, "WORLDGEN_TOO_DEEP");
    }

    /** Block IDs are checked for format and, when a list is given, existence. */
    @Test
    void testBlocks() {
        WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(Kind.FLUIDS, "t:f", "{type:\"level\", level:0.0, fluid:\"Not An ID\"}")
                        .compileAll();
        assertEquals("fluid", find(compiler, "WORLDGEN_BAD_BLOCK_ID").field());

        WorldgenCompiler known =
                new WorldgenCompiler(
                        WorldgenData.fromText(
                                java.util.Map.of(
                                        Kind.FLUIDS,
                                        java.util.Map.of(
                                                "t:f",
                                                "{type:\"level\", level:0.0, fluid:\"t:lava\"}"))),
                        WorldgenFixtures.SEED,
                        java.util.Set.of("t:water"));
        known.compileAll();
        find(known, "WORLDGEN_UNKNOWN_BLOCK");
    }

    /** A misspelled field is only a warning; the file still loads. */
    @Test
    void testUnknownFieldWarns() {
        WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(Kind.DENSITY, "t:a", "{type:\"constant\", value:1.0, valeu:2.0}")
                        .compileAll();
        Diagnostic problem = find(compiler, "WORLDGEN_UNKNOWN_FIELD");
        assertEquals(Diagnostic.Severity.WARNING, problem.severity());
        assertEquals("valeu", problem.field());
        assertFalse(compiler.hasErrors());
        assertTrue(compiler.density("t:a", null).isPresent());
    }

    /** Text that isn't KVT is reported, not thrown. */
    @Test
    void testParseError() {
        WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(Kind.DENSITY, "t:a", "{type:\"add\", args:[N;1.0, 2.0]}")
                        .add(Kind.DENSITY, "t:b", "{oops")
                        .compileAll();
        assertEquals(
                2,
                compiler.getDiagnostics().stream()
                        .filter(d -> "WORLDGEN_PARSE_FAILED".equals(d.code()))
                        .count());
    }

    /**
     * Later data folders replace earlier files with the same ID, and IDs come from paths.
     *
     * @param base A temporary folder.
     * @throws IOException If the files can't be written.
     */
    @Test
    void testOverrideOrder(@TempDir Path base) throws IOException {
        final Path first = base.resolve("first/mods/m/worldgen/density/sub");
        final Path second = base.resolve("second/mods/m/worldgen/density/sub");
        Files.createDirectories(first);
        Files.createDirectories(second);
        Files.writeString(first.resolve("value.kvt"), "1.0");
        Files.writeString(first.resolve("value.kvt"), "{type:\"constant\", value:1.0}");
        Files.writeString(second.resolve("value.kvt"), "{type:\"constant\", value:2.0}");
        WorldgenCompiler compiler =
                new WorldgenCompiler(
                        WorldgenData.load(List.of(base.resolve("first"), base.resolve("second"))),
                        WorldgenFixtures.SEED,
                        null);
        assertEquals(2.0, compiler.density("m:sub/value", null).orElseThrow().value(0, 0, 0, null));
    }

    /** Biome climate names match parameters by full ID or unambiguous short name. */
    @Test
    void testClimateNames() {
        WorldType type =
                WorldgenFixtures.compiler(WorldgenFixtures.SEED)
                        .worldType(WorldgenFixtures.WORLD, null)
                        .orElseThrow();
        // cavern names test:enclosure in full, the others use short names
        final int cavern = 2;
        final int enclosure = 2;
        assertEquals(12.0f, type.climates().get(cavern)[enclosure].min());
        assertEquals(0.2f, type.climates().get(0)[0].max());
    }
}
