package com.ikalagaming.factory.render.looks;

import com.ikalagaming.factory.kvt.KvtSchemas;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeStringSerialization;
import com.ikalagaming.factory.registry.ContentSchemas;
import com.ikalagaming.graphics.scene.WaterBody;
import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How water looks in each biome, from {@code looks/water/<biome>.kvt} in content folders: what it
 * holds, in real units, and how far the wind has blown over it. Biomes without a file use {@code
 * lotomation:default}. The wind itself is one for the whole world until there is weather.
 *
 * <pre>
 * /* looks/water/ocean.kvt in the Lotomation plugin's data folder *&#47;
 * {chlorophyll:0.05F, cdom:0.01F, sediment:0.0F}
 * </pre>
 *
 * {@code fetch} is in meters; leaving it out means the open sea, where the wind has blown far
 * enough that its waves are fully grown. Safe from any thread.
 */
@Slf4j
public final class WaterLooks {

    /** The folder under a content folder holding water looks. */
    public static final String FOLDER = "looks/water";

    /** The look for biomes that don't have one. */
    public static final String DEFAULT = "lotomation:default";

    /** The world's wind speed 10 m up, in meters a second, until weather exists. */
    public static final float WIND_SPEED = 8;

    /** The way the world's wind blows, in radians from +x toward +z. */
    public static final float WIND_DIRECTION = 0.6f;

    /** Used when even the default look is missing: clear coastal water on the open sea. */
    static final WaterBody FALLBACK =
            WaterBody.ofChlorophyll(0.5f)
                    .withWind(WIND_SPEED, WIND_DIRECTION, Float.POSITIVE_INFINITY);

    private static final String KEY_CHLOROPHYLL = "chlorophyll";
    private static final String KEY_CDOM = "cdom";
    private static final String KEY_SEDIMENT = "sediment";
    private static final String KEY_FETCH = "fetch";

    /** The folders to search, in order. */
    private final List<Path> roots;

    /** Bodies read so far, by biome; empty for biomes with no file. */
    private final Map<String, Optional<WaterBody>> bodies = new ConcurrentHashMap<>();

    /**
     * Water looks from some folders.
     *
     * @param roots The content folders, in load order.
     */
    public WaterLooks(@NonNull List<Path> roots) {
        this.roots = List.copyOf(roots);
    }

    /**
     * What water in a biome holds, with the world's wind over it.
     *
     * @param biome The biome's name, like {@code lotomation:ocean}.
     * @return Its body, or the default's, or a fallback.
     */
    public WaterBody bodyFor(@NonNull String biome) {
        return bodies.computeIfAbsent(biome, this::read)
                .or(() -> bodies.computeIfAbsent(DEFAULT, this::read))
                .orElse(FALLBACK);
    }

    /**
     * Read a biome's water look.
     *
     * @param biome The biome.
     * @return Its body, or empty if it has no file or the file is broken, which is logged.
     */
    private Optional<WaterBody> read(@NonNull String biome) {
        final Optional<Path> found =
                BlockLooks.find(roots, biome, FOLDER, BlockLooks.LOOK_EXTENSION);
        if (found.isPresent()) {
            final Path file = found.get();
            try {
                final Optional<Node> tree =
                        TreeStringSerialization.fromString(
                                Files.readString(file, StandardCharsets.UTF_8));
                if (tree.isEmpty()) {
                    log.warn("The water look {} isn't a KVT tree", file);
                    return Optional.empty();
                }
                return Optional.of(parse(tree.get()));
            } catch (IOException | IllegalArgumentException e) {
                log.warn("Couldn't read the water look {}", file, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * A body from a look's tree.
     *
     * @param tree The tree.
     * @return The body, in the world's wind.
     * @throws IllegalArgumentException If a value isn't a number.
     */
    static WaterBody parse(@NonNull Node tree) {
        final Schema.Values values = KvtSchemas.read(tree, ContentSchemas.WATER_LOOK);
        for (Schema.Problem problem : values.problems()) {
            if (problem.error()) {
                throw new IllegalArgumentException(problem.message());
            }
            log.warn("Water look: {}", problem.message());
        }
        return new WaterBody(
                        (float) values.number(KEY_CHLOROPHYLL),
                        (float) values.number(KEY_CDOM),
                        (float) values.number(KEY_SEDIMENT))
                .withWind(WIND_SPEED, WIND_DIRECTION, (float) values.number(KEY_FETCH));
    }
}
