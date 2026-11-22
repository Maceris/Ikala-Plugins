package com.ikalagaming.graphics.benchmark;

import lombok.NonNull;

import java.util.List;
import java.util.Map;

/** The ready-made {@link GateBenchmark} runs menus offer. */
public final class GatePresets {

    /**
     * A ready-made run to pick from a menu: a world, its view distance, and the options its scene
     * takes, as the startup properties name them without the {@code ikala.benchmark.} prefix, with
     * places to look from. Weather, particle effects, fog, the water and the sky can all be changed
     * from the debug window while its world is kept, so one preset covers each kind of landscape.
     *
     * @param id What it is called in code and scripts.
     * @param name What menus show for it.
     * @param description What it shows, a line for menus.
     * @param recipe The world.
     * @param radius The view distance, in sections.
     * @param lod Whether to draw with level of detail.
     * @param options The scene's options, like {@code weather} or {@code fog}.
     * @param views Places to look from, the first taken when the world is ready; runs add more they
     *     find, like a lake on the hills.
     */
    public record Preset(
            @NonNull String id,
            @NonNull String name,
            @NonNull String description,
            SyntheticTerrain.@NonNull Recipe recipe,
            int radius,
            boolean lod,
            @NonNull Map<String, String> options,
            @NonNull List<GateViews.View> views) {}

    /** The physical sky, with height fog and haze, as the landscapes start. */
    private static final Map<String, String> HAZY =
            Map.of(GateOptions.PHYSICAL_SKY, "true", "fog", "0.01,0.05,20", "haze", "true");

    /** {@link #HAZY}, with the world streamed around the camera. */
    private static final Map<String, String> HAZY_STREAMED =
            Map.of(
                    GateOptions.PHYSICAL_SKY,
                    "true",
                    "fog",
                    "0.01,0.05,20",
                    "haze",
                    "true",
                    "stream",
                    "true");

    /** The ready-made runs, in the order menus show them. */
    public static final List<Preset> PRESETS =
            List.of(
                    new Preset(
                            "island",
                            "Island",
                            "A hilly island in the sea: wind waves, a figure eight path offshore, a"
                                    + " stepped fountain, a glass wall and smoke, and a glass dome"
                                    + " on the sea floor",
                            SyntheticTerrain.Recipe.ISLAND,
                            8,
                            false,
                            Map.of(
                                    "fountain",
                                    "true",
                                    "glassTest",
                                    "true",
                                    "wind",
                                    "6,30",
                                    "loop",
                                    "infinity",
                                    "loopSpeed",
                                    "0",
                                    "seaReach",
                                    "512"),
                            List.of(
                                    new GateViews.View("Shore", 0, 6, 70, 0.15f, 0),
                                    new GateViews.View("Fountain", 0, 16, 16, 0.45f, 0),
                                    new GateViews.View("Glass and water", 0, 15, 20, 0.3f, 0),
                                    new GateViews.View("Figure eight", 0, 55, 130, 1.0f, 0),
                                    new GateViews.View("Under the sea", 0, -4, 72, -0.1f, 0))),
                    new Preset(
                            "rough-sea",
                            "Rough sea",
                            "The island in a gale, 15 m/s with a long swell, green water along its"
                                    + " coast, and the sea out a kilometer each way",
                            SyntheticTerrain.Recipe.ISLAND,
                            8,
                            false,
                            Map.of(
                                    "wind",
                                    "15,30",
                                    "swell",
                                    "2,13,60",
                                    "coastWater",
                                    "green",
                                    "seaReach",
                                    "1024",
                                    GateOptions.PHYSICAL_SKY,
                                    "true",
                                    "sunUp",
                                    "35"),
                            List.of(
                                    new GateViews.View("Offshore", 0, 30, 200, 0.12f, 0),
                                    new GateViews.View("From above", 0, 150, 500, 0.3f, 0),
                                    new GateViews.View("Coast", 40, 8, 90, 0.1f, 0))),
                    new Preset(
                            "hills",
                            "Hills",
                            "Rolling hills with trees, huts with glass, lakes and caves, under a"
                                    + " hazy physical sky",
                            SyntheticTerrain.Recipe.TERRAIN,
                            12,
                            false,
                            HAZY,
                            List.of()),
                    new Preset(
                            "hills-stream",
                            "Hills, streaming",
                            "The hills out to 512 m with level of detail, generated around the"
                                    + " camera as it moves: fly anywhere",
                            SyntheticTerrain.Recipe.TERRAIN,
                            32,
                            true,
                            HAZY_STREAMED,
                            List.of()),
                    new Preset(
                            "caves",
                            "Caves",
                            "Rock full of caves under a solid roof",
                            SyntheticTerrain.Recipe.CAVES,
                            8,
                            false,
                            Map.of(),
                            List.of()),
                    new Preset(
                            "glass",
                            "Glass",
                            "Walls of clear and tinted glass, many layers deep: the worst case for"
                                    + " transparency",
                            SyntheticTerrain.Recipe.GLASS,
                            8,
                            false,
                            Map.of(),
                            List.of()),
                    new Preset(
                            "jumble",
                            "Jumble",
                            "Hills of mixed block shapes, drawn with level of detail: the worst"
                                    + " case for culling and level of detail on real shapes",
                            SyntheticTerrain.Recipe.JUMBLE,
                            16,
                            true,
                            Map.of(),
                            List.of()));

    /** Constants only. */
    private GatePresets() {}
}
