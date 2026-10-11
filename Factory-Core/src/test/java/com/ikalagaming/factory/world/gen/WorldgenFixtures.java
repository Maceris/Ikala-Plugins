package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.WorldgenCompiler;
import com.ikalagaming.factory.world.gen.data.WorldgenData;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** The test data set, and ways to build small data sets from text. */
public final class WorldgenFixtures {

    /** The fixture world type. */
    public static final String WORLD = "test:world";

    /** A seed for tests that only need one. */
    public static final long SEED = 20261010L;

    /** The blocks the fixture uses. */
    public static final Set<String> BLOCKS =
            Set.of(
                    "test:stone",
                    "test:grass",
                    "test:dirt",
                    "test:moss",
                    "test:sand",
                    "test:basalt",
                    "test:deepstone",
                    "test:water");

    /**
     * The fixture's data folder.
     *
     * @return The folder holding {@code mods/test/worldgen}.
     */
    public static Path folder() {
        try {
            return Path.of(WorldgenFixtures.class.getResource("/worldgen/fixture").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Load and compile the fixture.
     *
     * @param seed The world seed.
     * @return The compiler, with nothing compiled yet.
     */
    public static WorldgenCompiler compiler(long seed) {
        return new WorldgenCompiler(WorldgenData.load(List.of(folder())), seed, BLOCKS);
    }

    /**
     * The fixture's generator.
     *
     * @param seed The world seed.
     * @return The generator.
     */
    public static WorldGenerator generator(long seed) {
        final WorldgenCompiler compiler = compiler(seed);
        final WorldType type =
                compiler.worldType(WORLD, null)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                compiler.getDiagnostics().toString()));
        return new WorldGenerator(type, seed);
    }

    /** Builds a data set from text, kind by kind. */
    public static final class Text {
        /** The files by kind and ID. */
        private final Map<Kind, Map<String, String>> files = new EnumMap<>(Kind.class);

        /**
         * Add a file.
         *
         * @param kind The kind.
         * @param id The ID.
         * @param text The contents.
         * @return This, for chaining.
         */
        public Text add(Kind kind, String id, String text) {
            files.computeIfAbsent(kind, k -> new TreeMap<>()).put(id, text);
            return this;
        }

        /**
         * Compile every file.
         *
         * @return The compiler, after compiling everything.
         */
        public WorldgenCompiler compileAll() {
            WorldgenCompiler compiler =
                    new WorldgenCompiler(WorldgenData.fromText(files), SEED, null);
            compiler.compileAll();
            return compiler;
        }
    }

    private WorldgenFixtures() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
