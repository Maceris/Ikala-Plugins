package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.WaterHandle;
import com.ikalagaming.graphics.scene.WaterBody;
import com.ikalagaming.graphics.scene.WaterTile;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A {@link GateBenchmark} run's water: the island's sea and fountain, the hills' lakes, the test
 * tiles from the {@code .water} option, and the ripples and waves painted on them. Its wind, flow
 * and what it holds can be changed from the debug window while the world is kept. The lakes are
 * laid out with the world on its worker; everything else is on the render thread.
 */
@Slf4j
public final class GateWater {

    /** The {@code .waterBody} value for the ocean, the default. */
    private static final String WATER_BODY_OCEAN = "ocean";

    /** The lakes' water body's name. */
    private static final String LAKE_BODY = "lake";

    /** The water bodies the debug window offers, in order, see {@link #setWaterBody}. */
    public static final List<String> WATER_BODY_CHOICES =
            List.of(WATER_BODY_OCEAN, LAKE_BODY, "green", "muddy");

    /** The {@code .waterBody} option's values, by what the ocean holds. */
    private static final Map<String, WaterBody> WATER_BODY_NAMES =
            Map.of(
                    WATER_BODY_OCEAN,
                    WaterBody.OCEAN,
                    LAKE_BODY,
                    WaterBody.ofChlorophyll(1),
                    "green",
                    WaterBody.GREEN,
                    "muddy",
                    WaterBody.MUDDY);

    /** How many cells across the ocean's and the lakes' tiles are, of a meter. */
    static final int OCEAN_CELLS = WaterTile.MAX_CELLS;

    /** How far the wind blows over a lake, at most, in meters. */
    private static final float LAKE_FETCH = 100;

    /** The lakes' wind, its speed 10 m up and the way it blows, in degrees from +x toward +z. */
    private static final float LAKE_WIND = 3;

    private static final float LAKE_WIND_DEGREES = 30;

    /** How far from the middle a lake to look at may be, along x and z, in meters. */
    private static final int LAKE_VIEW_REACH = 96;

    /** How far around each spot painting waves along the loop reaches, in meters. */
    private static final float PAINT_RADIUS = 4;

    /** How fast the loop's channel flows unless {@code .loopSpeed} says, in meters a second. */
    private static final float DEFAULT_LOOP_SPEED = 1;

    /**
     * What the fountain's pools hold: garden pool water, its dissolved organic matter tinting it
     * like weak tea and fine sediment clouding it, so the water shows as a body; the run's wind
     * blows over it.
     */
    private static final WaterBody FOUNTAIN_WATER = new WaterBody(1, 0.4f, 2.5f);

    /** How deep the fountain's pools are, in meters: a full water block's 7/8. */
    private static final double FOUNTAIN_POOL_DEPTH = 0.875;

    /** How fast the fountain's water drifts outward, in meters a second. */
    private static final double FOUNTAIN_FLOW = 0.4;

    /** The test splash's radius, in meters, and its ripples' height, in meters. */
    private static final float SPLASH_RADIUS = 1;

    private static final float SPLASH_STRENGTH = 0.06f;

    /** Where on the sea the test splash drops without a loop, along z: off the island's shore. */
    private static final double SPLASH_SEA_Z = 44;

    /** How far apart the test water tiles are, in meters: the huts' grid in HUTS. */
    private static final double WATER_SPACING = 12;

    /** How many cells across each test water tile is, of a meter. */
    private static final int WATER_CELLS = 6;

    /** How deep the test water tiles' columns are, in meters above the ground. */
    private static final float[] WATER_DEPTHS = {0.5f, 2, 6};

    /** What the test water tiles' rows hold. */
    private static final WaterBody[] WATER_BODIES = {
        WaterBody.OCEAN, WaterBody.GREEN, WaterBody.MUDDY
    };

    /** Values in the {@code .ripples} option. */
    private static final int RIPPLE_VALUES = 5;

    /** Nanoseconds in a second. */
    private static final double NANOS_PER_SECOND = 1e9;

    /**
     * A test water tile the run placed, kept to change its wind and flow.
     *
     * @param still The tile without flow.
     * @param fetch The most fetch the wind has over it, in meters: a pool's width, or infinite for
     *     the open sea.
     * @param handle Its handle, set once placed.
     * @param followsBody Whether it holds the run's water body, which the debug window changes.
     */
    private record PlacedWater(
            WaterTile still, float fetch, WaterHandle[] handle, boolean followsBody) {}

    /**
     * A lake water tile and what it covers.
     *
     * @param tile The tile.
     * @param mask Which of its cells hold lake.
     * @param count How many do.
     * @param sumX Their x added up, for their middle.
     * @param sumZ Their z added up.
     */
    private record LakeTile(
            @NonNull WaterTile tile, boolean @NonNull [] mask, int count, long sumX, long sumZ) {}

    /** The context everything is made under. */
    private final GraphicsContext graphics;

    /** The run's options. */
    private final GateOptions options;

    /** The view distance, in sections. */
    private final int radius;

    /** The test water tiles the run placed, removed when it ends. */
    private final List<WaterHandle> testWater = new ArrayList<>();

    /** The water tiles that take the run's wind and flow, see {@link #setWater}. */
    private final List<PlacedWater> placedWater = new ArrayList<>();

    /** The lakes' water tiles, laid out with the world, placed with its water. */
    private final List<WaterTile> lakeTiles = new ArrayList<>();

    /**
     * The corners of the lake tiles looked at when the world was laid out, with lake or not, as
     * {@link WorldStream#tileKey} gives them.
     */
    private final Set<Long> lakeTileKeys = new HashSet<>();

    /** The placed lake tiles, by their corners' {@link WorldStream#tileKey}. */
    private final Map<Long, PlacedWater> lakeWater = new HashMap<>();

    /** Where a lake is near the world's middle, x and z, or null for none. */
    private int[] lakeSpot;

    /** The flowing path out at sea, from the {@code .loop} option, or null for none. */
    private WaterLoop waterLoop;

    /** What the run's water holds and its wind, null until placed. */
    private WaterBody waterBody;

    /** Its name, one of {@link #WATER_BODY_CHOICES}. */
    private String waterBodyName;

    /** How fast the sea flows, in meters a second, and which way, radians from +x toward +z. */
    private float currentSpeed;

    private float currentDirection;

    /** How fast the loop's channel flows in its middle, in meters a second. */
    private float loopSpeed;

    /**
     * How tall the waves painted along the loop are, as a share of the wind's waves in its own
     * direction, 0 for none.
     */
    private float loopWaves;

    /**
     * The test ripples from the {@code .ripples} option: x, z, radius, strength and how many
     * seconds apart; null until read, empty for none.
     */
    private double[] testRipples;

    /** When the test water was last disturbed, from {@link System#nanoTime()}. */
    private long lastRippleNanos;

    /**
     * Set up a run's water.
     *
     * @param graphics The context everything is made under.
     * @param options The run's options.
     * @param radius The view distance, in sections.
     */
    GateWater(@NonNull GraphicsContext graphics, @NonNull GateOptions options, int radius) {
        this.graphics = graphics;
        this.options = options;
        this.radius = radius;
    }

    /**
     * Place the world's water: the island's sea and fountain, and the lakes laid out with it.
     *
     * @param world The world.
     */
    void place(@NonNull SyntheticTerrain world) {
        placeOcean(world);
        placeFountain(world);
        for (WaterTile tile : lakeTiles) {
            placeLakeTile(tile);
        }
    }

    /**
     * In the island recipe, fill the sea with water tiles out to the view distance: every cell
     * whose ground is under {@link SyntheticTerrain#SEA_LEVEL}, the bottom at the ground's top. The
     * {@code .waterBody} option picks what it holds: ocean (the default), green or muddy; {@code
     * .wind=speed[,degrees[,fetch]]} sets its wind, blowing that many degrees from +x toward +z
     * over a fetch in meters, the open sea if none; {@code .current=speed[,degrees]} makes the sea
     * flow that many meters a second, that many degrees from +x toward +z, except on the {@code
     * .loop=circle|infinity} path out at sea, which flows along itself, {@code .loopSpeed=speed} in
     * its middle. All of these can be changed while the world is kept, see {@link #setWater}.
     *
     * @param world The world.
     */
    private void placeOcean(@NonNull SyntheticTerrain world) {
        if (world.getRecipe() != SyntheticTerrain.Recipe.ISLAND) {
            return;
        }
        final String name = options.get("waterBody", WATER_BODY_OCEAN);
        WaterBody body = WATER_BODY_NAMES.get(name.trim().toLowerCase(Locale.ROOT));
        if (body == null) {
            log.warn(
                    "The benchmark's water body must be one of {}, not {}",
                    WATER_BODY_NAMES.keySet(),
                    name);
            body = WaterBody.OCEAN;
        }
        final String wind = options.get("wind");
        if (wind != null) {
            final String[] parts = wind.split(",");
            final float speed = Float.parseFloat(parts[0].trim());
            final float direction =
                    parts.length > 1
                            ? (float) Math.toRadians(Double.parseDouble(parts[1].trim()))
                            : 0;
            final float fetch =
                    parts.length > 2 ? Float.parseFloat(parts[2].trim()) : Float.POSITIVE_INFINITY;
            body = body.withWind(speed, direction, fetch);
        }
        final String swell = options.get("swell");
        if (swell != null) {
            body = body.withSwell(parseSwell(swell));
        }
        waterBody = body;
        waterBodyName =
                WATER_BODY_NAMES.containsKey(name.trim().toLowerCase(Locale.ROOT))
                        ? name.trim().toLowerCase(Locale.ROOT)
                        : WATER_BODY_CHOICES.getFirst();
        waterLoop = loopOption();
        final String current = options.get("current");
        if (current != null) {
            final String[] parts = current.split(",");
            currentSpeed = Float.parseFloat(parts[0].trim());
            currentDirection =
                    parts.length > 1
                            ? (float) Math.toRadians(Double.parseDouble(parts[1].trim()))
                            : 0;
        }
        loopSpeed =
                Float.parseFloat(
                        options.get("loopSpeed", Float.toString(DEFAULT_LOOP_SPEED)).trim());
        loopWaves = Float.parseFloat(options.get("loopWaves", "0").trim());
        final int reach = radius * SyntheticTerrain.SECTION;
        final int tiles = (2 * reach + OCEAN_CELLS - 1) / OCEAN_CELLS;
        final int corners = OCEAN_CELLS + 1;
        for (int tileZ = 0; tileZ < tiles; ++tileZ) {
            for (int tileX = 0; tileX < tiles; ++tileX) {
                final int startX = -reach + tileX * OCEAN_CELLS;
                final int startZ = -reach + tileZ * OCEAN_CELLS;
                final float[] heights = new float[corners * corners];
                final float[] bottoms = new float[corners * corners];
                final boolean[] mask = new boolean[OCEAN_CELLS * OCEAN_CELLS];
                boolean any = false;
                for (int z = 0; z < corners; ++z) {
                    for (int x = 0; x < corners; ++x) {
                        heights[z * corners + x] = (float) SyntheticTerrain.SEA_LEVEL;
                        bottoms[z * corners + x] =
                                (float) Math.floor(world.islandHeight(startX + x, startZ + z)) + 1;
                    }
                }
                for (int z = 0; z < OCEAN_CELLS; ++z) {
                    for (int x = 0; x < OCEAN_CELLS; ++x) {
                        final boolean water =
                                Math.floor(world.islandHeight(startX + x, startZ + z)) + 1
                                        < SyntheticTerrain.SEA_LEVEL;
                        mask[z * OCEAN_CELLS + x] = water;
                        any |= water;
                    }
                }
                if (any) {
                    placeWater(
                            new WaterTile(
                                    new Vector3d(startX, 0, startZ),
                                    1,
                                    OCEAN_CELLS,
                                    OCEAN_CELLS,
                                    heights,
                                    bottoms,
                                    mask,
                                    body),
                            Float.POSITIVE_INFINITY);
                }
            }
        }
    }

    /**
     * The flowing path out at sea, from the {@code .loop=circle|infinity} option, see {@link
     * WaterLoop}. Only the island's sea has one.
     *
     * @return The loop, or null for none.
     */
    private @Nullable WaterLoop loopOption() {
        final String value = options.get("loop");
        if (value == null) {
            return null;
        }
        try {
            return new WaterLoop(WaterLoop.Shape.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            log.warn("The benchmark's loop must be circle or infinity, not {}", value);
            return null;
        }
    }

    /**
     * In the island recipe, with the {@code .fountain} option, fill the pools on the island's
     * stepped stone fountain, see {@link SyntheticTerrain#fountainTier}, each drifting outward, so
     * water falls off every side of each past the stone onto the pool below, and off the outermost
     * onto the ground: the water's sides seen from all around, with stone behind them.
     *
     * @param world The world.
     */
    private void placeFountain(@NonNull SyntheticTerrain world) {
        if (world.getRecipe() != SyntheticTerrain.Recipe.ISLAND
                || !world.isFountain()
                || waterBody == null) {
            return;
        }
        final int[] sizes = SyntheticTerrain.FOUNTAIN_SIZES;
        for (int tier = 0; tier < sizes.length; ++tier) {
            final int size = sizes[tier];
            final int inner = tier + 1 < sizes.length ? sizes[tier + 1] : 0;
            // On the tier's top block, as deep as a full water block
            final double surface =
                    world.fountainBase()
                            + SyntheticTerrain.FOUNTAIN_STEP * tier
                            + 1
                            + FOUNTAIN_POOL_DEPTH;
            final int start = -size / 2;
            final int corners = size + 1;
            final float[] heights = new float[corners * corners];
            final float[] bottoms = new float[corners * corners];
            final float[] flows = new float[2 * corners * corners];
            final boolean[] mask = new boolean[size * size];
            Arrays.fill(heights, (float) surface);
            for (int z = 0; z < corners; ++z) {
                for (int x = 0; x < corners; ++x) {
                    final int at = z * corners + x;
                    bottoms[at] = (float) Math.floor(world.islandHeight(start + x, start + z)) + 1;
                    final double length = Math.hypot(start + x, start + z);
                    if (length > 0) {
                        flows[2 * at] = (float) (FOUNTAIN_FLOW * (start + x) / length);
                        flows[2 * at + 1] = (float) (FOUNTAIN_FLOW * (start + z) / length);
                    }
                }
            }
            for (int z = 0; z < size; ++z) {
                for (int x = 0; x < size; ++x) {
                    final int wx = start + x;
                    final int wz = start + z;
                    // Leave out where the next pool up stands
                    mask[z * size + x] =
                            inner == 0
                                    || Math.max(Math.abs(wx + 0.5), Math.abs(wz + 0.5))
                                            > inner / 2.0;
                }
            }
            placeWater(
                    new WaterTile(
                                    new Vector3d(start, 0, start),
                                    1,
                                    size,
                                    size,
                                    heights,
                                    bottoms,
                                    mask,
                                    FOUNTAIN_WATER)
                            .withFlows(flows),
                    size);
        }
    }

    /**
     * Place a water tile with the run's wind and flow, remembering it so they can change.
     *
     * @param still The tile, without flow.
     * @param fetch The most fetch the wind has over it, in meters: a pool's width, which keeps its
     *     waves to ripples, or infinite for the open sea.
     */
    private void placeWater(@NonNull WaterTile still, float fetch) {
        // The sea and lakes take the run's water body; water with its own flow keeps its own
        final PlacedWater placed =
                new PlacedWater(still, fetch, new WaterHandle[1], still.flows() == null);
        placed.handle()[0] = graphics.water().add(withWaterSettings(placed));
        if (placed.handle()[0] != null) {
            testWater.add(placed.handle()[0]);
            placedWater.add(placed);
        }
    }

    /**
     * A placed tile as the run's wind and flow have it now.
     *
     * @param placed The tile.
     * @return It with the run's body and wind, and its flow.
     */
    private WaterTile withWaterSettings(@NonNull PlacedWater placed) {
        final WaterTile still = placed.still();
        if (still.flows() != null) {
            // Water with its own flow, like the fountain's, keeps it
            return still.withBody(withRunWind(still.body(), placed.fetch()));
        }
        final int cornersX = still.cellsX() + 1;
        final int corners = cornersX * (still.cellsZ() + 1);
        final WaterLoop path = waterLoop;
        if (currentSpeed == 0 && (path == null || loopSpeed == 0)) {
            return still.withBody(withRunWind(still.body(), placed.fetch()));
        }
        final float[] flows = new float[2 * corners];
        final int originX = (int) Math.floor(still.origin().x());
        final int originZ = (int) Math.floor(still.origin().z());
        final float seaX = (float) (currentSpeed * Math.cos(currentDirection));
        final float seaZ = (float) (currentSpeed * Math.sin(currentDirection));
        for (int i = 0; i < corners; ++i) {
            final int x = originX + i % cornersX;
            final int z = originZ + i / cornersX;
            final boolean onPath = path != null && path.coversCorner(x, z);
            flows[2 * i] = onPath ? path.flowAt(x, z, loopSpeed, false) : seaX;
            flows[2 * i + 1] = onPath ? path.flowAt(x, z, loopSpeed, true) : seaZ;
        }
        return still.withBody(withRunWind(still.body(), placed.fetch())).withFlows(flows);
    }

    /**
     * The {@code .swell} option: height in meters, period in seconds, the way it travels in degrees
     * and, optionally, its spread in degrees.
     *
     * @param value The option.
     * @return The swell.
     */
    private static WaterBody.Swell parseSwell(@NonNull String value) {
        final String[] parts = value.split(",");
        final float height = Float.parseFloat(parts[0].trim());
        final float period = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 12;
        final float direction =
                parts.length > 2 ? (float) Math.toRadians(Double.parseDouble(parts[2].trim())) : 0;
        final float spread =
                parts.length > 3
                        ? (float) Math.toRadians(Double.parseDouble(parts[3].trim()))
                        : WaterBody.Swell.NONE.spread();
        return new WaterBody.Swell(height, period, direction, spread);
    }

    /**
     * What a placed tile's water holds, under the run's wind.
     *
     * @param own What the tile was placed holding.
     * @param fetch The most fetch the wind has over the tile, in meters.
     * @return It, with the run's wind, over no more than that fetch.
     */
    private WaterBody withRunWind(@NonNull WaterBody own, float fetch) {
        return own.withWind(
                waterBody.windSpeed(),
                waterBody.windDirection(),
                Math.min(waterBody.fetch(), fetch));
    }

    /**
     * Change the run's wind and flow while its world is kept, from the debug window. Render thread
     * only.
     *
     * @param windSpeed The wind's speed 10 m up, in meters a second.
     * @param windDegrees The way it blows, in degrees from +x toward +z.
     * @param seaCurrent How fast the sea flows, in meters a second.
     * @param currentDegrees The way it flows, in degrees from +x toward +z.
     * @param channelSpeed How fast the loop's path flows in its middle, in meters a second;
     *     negative runs it the other way.
     * @param pathWaves How tall the waves painted along the loop are, as a share of the wind's
     *     waves in its own direction, 0 for none.
     */
    public void setWater(
            float windSpeed,
            float windDegrees,
            float seaCurrent,
            float currentDegrees,
            float channelSpeed,
            float pathWaves) {
        if (waterBody == null) {
            return;
        }
        loopWaves = Math.max(0, pathWaves);
        final WaterBody body =
                waterBody.withWind(
                        Math.max(0, windSpeed),
                        (float) Math.toRadians(windDegrees),
                        waterBody.fetch());
        final float direction = (float) Math.toRadians(currentDegrees);
        if (body.equals(waterBody)
                && seaCurrent == currentSpeed
                && direction == currentDirection
                && channelSpeed == loopSpeed) {
            return;
        }
        waterBody = body;
        currentSpeed = seaCurrent;
        currentDirection = direction;
        loopSpeed = channelSpeed;
        for (PlacedWater placed : placedWater) {
            graphics.water().update(placed.handle()[0], withWaterSettings(placed));
        }
    }

    /**
     * The run's water settings, for the debug window: the wind's speed and degrees, the sea's
     * current's speed and degrees, the loop's speed, and its painted waves' height.
     *
     * @return The settings, or null if the run placed no sea or lakes.
     */
    public @Nullable float[] getWater() {
        if (waterBody == null) {
            return null;
        }
        return new float[] {
            waterBody.windSpeed(),
            (float) Math.toDegrees(waterBody.windDirection()),
            currentSpeed,
            (float) Math.toDegrees(currentDirection),
            loopSpeed,
            loopWaves
        };
    }

    /**
     * What the run's water holds, see {@link #WATER_BODY_CHOICES}.
     *
     * @return Its name, or null if the run placed no sea or lakes.
     */
    public @Nullable String getWaterBody() {
        return waterBody == null ? null : waterBodyName;
    }

    /**
     * Change what the run's sea or lakes hold, keeping their wind; the fountain keeps its own
     * water. Render thread only.
     *
     * @param name One of {@link #WATER_BODY_CHOICES}.
     */
    public void setWaterBody(@NonNull String name) {
        final WaterBody base = WATER_BODY_NAMES.get(name);
        if (base == null || waterBody == null || name.equals(waterBodyName)) {
            return;
        }
        waterBodyName = name;
        waterBody =
                base.withWind(waterBody.windSpeed(), waterBody.windDirection(), waterBody.fetch())
                        .withSwell(waterBody.swell());
        for (int i = 0; i < placedWater.size(); ++i) {
            final PlacedWater placed = placedWater.get(i);
            if (!placed.followsBody()) {
                continue;
            }
            final PlacedWater changed =
                    new PlacedWater(
                            placed.still().withBody(base), placed.fetch(), placed.handle(), true);
            placedWater.set(i, changed);
            graphics.water().update(changed.handle()[0], withWaterSettings(changed));
        }
    }

    /**
     * Whether the sea has a flowing path, for the debug window.
     *
     * @return Whether there is one.
     */
    public boolean hasWaterLoop() {
        return waterLoop != null;
    }

    /**
     * Drop a ring of ripples on the water: on the flowing path if there is one, else on the sea in
     * front of the island. Render thread only.
     */
    public void splashTestWater() {
        final WaterLoop path = waterLoop;
        final Vector3d at =
                path != null
                        ? new Vector3d(
                                path.getSplashX(), SyntheticTerrain.SEA_LEVEL, path.getSplashZ())
                        : new Vector3d(0, SyntheticTerrain.SEA_LEVEL, SPLASH_SEA_Z);
        graphics.water().disturb(at, SPLASH_RADIUS, SPLASH_STRENGTH);
    }

    /**
     * Lay out the lakes' water tiles, for a world whose lakes are left to them, see {@link
     * SyntheticTerrain#lakeBottom}: tiles of the ocean's size over the view distance, each lake
     * column's water from its bottom up to {@link SyntheticTerrain#LAKE_SURFACE}, and a lake near
     * the middle to look at. On the worker that lays out the world, before anything is placed.
     *
     * @param world The world.
     */
    void layOutLakes(@NonNull SyntheticTerrain world) {
        lakeTiles.clear();
        lakeTileKeys.clear();
        lakeSpot = null;
        if (!world.isWaterTiles()) {
            return;
        }
        final int reach = radius * SyntheticTerrain.SECTION;
        // On the grid of tile corners a streamed world adds to, see WorldStream
        final int first = Math.floorDiv(-reach, OCEAN_CELLS);
        final int last = Math.floorDiv(reach - 1, OCEAN_CELLS);
        // The tile near the middle with the most lake, to look at
        int most = 0;
        long lakeCells = 0;
        for (int tileZ = first; tileZ <= last; ++tileZ) {
            for (int tileX = first; tileX <= last; ++tileX) {
                final int startX = tileX * OCEAN_CELLS;
                final int startZ = tileZ * OCEAN_CELLS;
                lakeTileKeys.add(WorldStream.tileKey(startX, startZ));
                final LakeTile lake = lakeTile(world, startX, startZ);
                if (lake == null) {
                    continue;
                }
                final boolean near =
                        Math.abs(startX + OCEAN_CELLS / 2) <= LAKE_VIEW_REACH
                                && Math.abs(startZ + OCEAN_CELLS / 2) <= LAKE_VIEW_REACH;
                if (near && lake.count() > most) {
                    most = lake.count();
                    lakeSpot =
                            nearestLakeCell(
                                    lake.mask(),
                                    startX,
                                    startZ,
                                    lake.sumX() / lake.count(),
                                    lake.sumZ() / lake.count());
                }
                lakeCells += lake.count();
                lakeTiles.add(lake.tile());
            }
        }
        log.info(
                "Gate benchmark lakes: {} tiles, {} cells; spot {} holds water: {}",
                lakeTiles.size(),
                lakeCells,
                lakeSpot == null ? "none" : lakeSpot[0] + "," + lakeSpot[1],
                lakeSpot != null
                        && world.lakeBottom(lakeSpot[0], lakeSpot[1]) != SyntheticTerrain.NO_LAKE);
    }

    /**
     * The lake water tile with its corner at a place: each lake column's water from its bottom up
     * to {@link SyntheticTerrain#LAKE_SURFACE}. On any thread.
     *
     * @param world The world.
     * @param startX The tile's corner along x, in blocks.
     * @param startZ Along z.
     * @return The tile, or null if no lake is in it.
     */
    @Nullable private static LakeTile lakeTile(@NonNull SyntheticTerrain world, int startX, int startZ) {
        final float surface = (float) SyntheticTerrain.LAKE_SURFACE;
        final int corners = OCEAN_CELLS + 1;
        final int[] cellBottoms = new int[OCEAN_CELLS * OCEAN_CELLS];
        final boolean[] mask = new boolean[OCEAN_CELLS * OCEAN_CELLS];
        int count = 0;
        long sumX = 0;
        long sumZ = 0;
        for (int z = 0; z < OCEAN_CELLS; ++z) {
            for (int x = 0; x < OCEAN_CELLS; ++x) {
                final int bottom = world.lakeBottom(startX + x, startZ + z);
                cellBottoms[z * OCEAN_CELLS + x] = bottom;
                if (bottom == SyntheticTerrain.NO_LAKE) {
                    continue;
                }
                mask[z * OCEAN_CELLS + x] = true;
                ++count;
                sumX += startX + x;
                sumZ += startZ + z;
            }
        }
        if (count == 0) {
            return null;
        }
        final float[] heights = new float[corners * corners];
        final float[] bottoms = new float[corners * corners];
        Arrays.fill(heights, surface);
        for (int z = 0; z < corners; ++z) {
            for (int x = 0; x < corners; ++x) {
                // The lowest of the lake cells touching the corner
                float bottom = surface;
                for (int cz = z - 1; cz <= z; ++cz) {
                    for (int cx = x - 1; cx <= x; ++cx) {
                        if (cx < 0 || cz < 0 || cx >= OCEAN_CELLS || cz >= OCEAN_CELLS) {
                            continue;
                        }
                        final int cell = cellBottoms[cz * OCEAN_CELLS + cx];
                        if (cell != SyntheticTerrain.NO_LAKE) {
                            bottom = Math.min(bottom, cell);
                        }
                    }
                }
                bottoms[z * corners + x] = bottom;
            }
        }
        final WaterTile tile =
                new WaterTile(
                        new Vector3d(startX, 0, startZ),
                        1,
                        OCEAN_CELLS,
                        OCEAN_CELLS,
                        heights,
                        bottoms,
                        mask,
                        WATER_BODY_NAMES.get(LAKE_BODY));
        return new LakeTile(tile, mask, count, sumX, sumZ);
    }

    /**
     * The lake water tile with its corner at a place, for a streamed world to add. On any thread.
     *
     * @param world The world.
     * @param startX The tile's corner along x, in blocks.
     * @param startZ Along z.
     * @return The tile, or null if no lake is in it or the world has no lake tiles.
     */
    @Nullable static WaterTile lakeWaterTile(@NonNull SyntheticTerrain world, int startX, int startZ) {
        if (!world.isWaterTiles()) {
            return null;
        }
        final LakeTile lake = lakeTile(world, startX, startZ);
        return lake == null ? null : lake.tile();
    }

    /**
     * The lake cell of a tile nearest a point, for a view to look down at water.
     *
     * @param mask Which of the tile's cells hold lake.
     * @param startX The tile's corner along x.
     * @param startZ Along z.
     * @param x The point's x.
     * @param z Its z.
     * @return The cell's x and z.
     */
    private static int[] nearestLakeCell(
            boolean @NonNull [] mask, int startX, int startZ, long x, long z) {
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int cz = 0; cz < OCEAN_CELLS; ++cz) {
            for (int cx = 0; cx < OCEAN_CELLS; ++cx) {
                if (!mask[cz * OCEAN_CELLS + cx]) {
                    continue;
                }
                final long dx = startX + cx - x;
                final long dz = startZ + cz - z;
                if (dx * dx + dz * dz < bestDistance) {
                    bestDistance = dx * dx + dz * dz;
                    best = new int[] {startX + cx, startZ + cz};
                }
            }
        }
        return best;
    }

    /**
     * Place a lake water tile under the lakes' light wind, unless the run has a water body already.
     *
     * @param tile The tile.
     */
    void placeLakeTile(@NonNull WaterTile tile) {
        if (waterBody == null) {
            waterBody =
                    WATER_BODY_NAMES
                            .get(LAKE_BODY)
                            .withWind(
                                    LAKE_WIND,
                                    (float) Math.toRadians(LAKE_WIND_DEGREES),
                                    Float.POSITIVE_INFINITY);
            waterBodyName = LAKE_BODY;
        }
        final int count = placedWater.size();
        placeWater(tile, LAKE_FETCH);
        if (placedWater.size() > count) {
            lakeWater.put(lakeKey(tile), placedWater.getLast());
        }
    }

    /**
     * Take away a placed lake water tile, if there is one at a corner.
     *
     * @param startX The tile's corner along x, in blocks.
     * @param startZ Along z.
     */
    void removeLakeTile(int startX, int startZ) {
        final PlacedWater placed = lakeWater.remove(WorldStream.tileKey(startX, startZ));
        if (placed == null) {
            return;
        }
        placedWater.remove(placed);
        final WaterHandle handle = placed.handle()[0];
        if (handle != null) {
            testWater.remove(handle);
            graphics.water().remove(handle);
        }
    }

    /**
     * A lake tile's key, from its corner.
     *
     * @param tile The tile.
     * @return The key.
     */
    private static long lakeKey(@NonNull WaterTile tile) {
        return WorldStream.tileKey(
                (int) Math.floor(tile.origin().x()), (int) Math.floor(tile.origin().z()));
    }

    /**
     * Where a lake is near the world's middle, for a view to look down at it.
     *
     * @return Its x and z, or null for none.
     */
    @Nullable int[] lakeSpot() {
        return lakeSpot;
    }

    /**
     * The corners of the lake tiles looked at when the world was laid out, for a stream to leave
     * alone.
     *
     * @return The corners' {@link WorldStream#tileKey}s.
     */
    Set<Long> lakeTileKeys() {
        return Set.copyOf(lakeTileKeys);
    }

    /**
     * Place test water from the {@code .water=x,y,z} option: a grid of 6 m tiles with a corner
     * there, on the ground at y, 12 m apart: clear ocean, green and muddy water from front to back,
     * 0.5, 2 and 6 m deep from left to right, so their colors can be compared.
     */
    void placeGrid() {
        final Vector3d corner = options.point("water");
        if (corner == null) {
            return;
        }
        for (int row = 0; row < WATER_BODIES.length; ++row) {
            for (int column = 0; column < WATER_DEPTHS.length; ++column) {
                final WaterHandle handle =
                        graphics.water()
                                .add(
                                        WaterTile.flat(
                                                new Vector3d(corner)
                                                        .add(
                                                                column * WATER_SPACING,
                                                                0,
                                                                row * WATER_SPACING),
                                                1,
                                                WATER_CELLS,
                                                WATER_CELLS,
                                                WATER_DEPTHS[column],
                                                WATER_BODIES[row]));
                if (handle != null) {
                    testWater.add(handle);
                }
            }
        }
    }

    /** Disturb the water and paint waves along the loop, once a frame while the world is up. */
    void step() {
        rippleTestWater();
        paintTestWater();
    }

    /**
     * Paint waves going along the loop, as the water surface wavelets paper's figure eight does.
     */
    private void paintTestWater() {
        final WaterLoop path = waterLoop;
        if (path == null || loopWaves <= 0 || graphics.isClosed()) {
            return;
        }
        final float reverse = loopSpeed < 0 ? (float) Math.PI : 0;
        for (int i = 0; i < path.paintSpotCount(); ++i) {
            graphics.water()
                    .paintWaves(
                            new Vector3d(
                                    path.paintSpot(i, 0),
                                    SyntheticTerrain.SEA_LEVEL,
                                    path.paintSpot(i, 1)),
                            PAINT_RADIUS,
                            (float) path.paintSpot(i, 2) + reverse,
                            loopWaves);
        }
    }

    /**
     * Disturb the water every so often at one place, from the {@code .ripples=x,z,radius,strength,
     * seconds} option, to watch ripples spread and reflect.
     */
    private void rippleTestWater() {
        if (testRipples == null) {
            final String value = options.get("ripples");
            testRipples = new double[0];
            if (value != null) {
                final String[] parts = value.split(",");
                if (parts.length != RIPPLE_VALUES) {
                    log.warn(
                            "The benchmark's ripples must be x,z,radius,strength,seconds, not {}",
                            value);
                } else {
                    testRipples =
                            Arrays.stream(parts)
                                    .mapToDouble(p -> Double.parseDouble(p.trim()))
                                    .toArray();
                }
            }
        }
        if (testRipples.length == 0 || graphics.isClosed()) {
            return;
        }
        final long now = System.nanoTime();
        if ((now - lastRippleNanos) / NANOS_PER_SECOND < testRipples[RIPPLE_VALUES - 1]) {
            return;
        }
        lastRippleNanos = now;
        graphics.water()
                .disturb(
                        new Vector3d(testRipples[0], SyntheticTerrain.SEA_LEVEL, testRipples[1]),
                        (float) testRipples[2],
                        (float) testRipples[3]);
    }

    /**
     * How many water tiles the run placed.
     *
     * @return The count.
     */
    int tileCount() {
        return testWater.size();
    }

    /**
     * How many of the placed tiles hold the run's water body, see {@link #setWaterBody}.
     *
     * @return The count.
     */
    long followingCount() {
        return placedWater.stream().filter(PlacedWater::followsBody).count();
    }

    /**
     * How many lake tiles are placed.
     *
     * @return The count.
     */
    int lakeCount() {
        return lakeWater.size();
    }

    /**
     * Take away every placed tile, unless the graphics are closed already; the lake tiles laid out
     * stay, for nothing places them again.
     */
    void release() {
        lakeWater.clear();
        if (graphics.isClosed()) {
            return;
        }
        testWater.forEach(graphics.water()::remove);
        testWater.clear();
        placedWater.clear();
        waterBody = null;
        waterLoop = null;
    }
}
