package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A {@link GateBenchmark} run's scene options: what its preset or caller set, else the startup
 * property of the same name after {@code ikala.benchmark.}. Also applies the startup properties
 * that change the renderer's settings rather than the run's scene.
 */
@Slf4j
final class GateOptions {

    /** The option that lights the scene with the physical sky. */
    static final String PHYSICAL_SKY = "physicalSky";

    /** Values in a point option: x, y and z. */
    private static final int POINT_VALUES = 3;

    /** Bytes in a mebibyte. */
    private static final double BYTES_PER_MIB = 1024.0 * 1024.0;

    /** The options set for the run, which come before the startup properties. */
    private final Map<String, String> options = new HashMap<>();

    /**
     * Set options for the run, over any set before.
     *
     * @param set The options, by name.
     */
    void putAll(@NonNull Map<String, String> set) {
        options.putAll(set);
    }

    /**
     * One of the run's options: what was set for it, else the startup property of that name.
     *
     * @param key The option, the property's name after {@code ikala.benchmark.}.
     * @return Its value, or null if neither sets it.
     */
    @Nullable String get(@NonNull String key) {
        final String value = options.get(key);
        return value != null ? value : System.getProperty(GateBenchmark.PROPERTY + "." + key);
    }

    /**
     * One of the run's options, or a default.
     *
     * @param key The option.
     * @param fallback What it is if nothing sets it.
     * @return Its value.
     */
    String get(@NonNull String key, @NonNull String fallback) {
        final String value = get(key);
        return value != null ? value : fallback;
    }

    /**
     * Whether an option is set to true.
     *
     * @param key The option.
     * @return True if it says true, ignoring case.
     */
    boolean flag(@NonNull String key) {
        return Boolean.parseBoolean(get(key));
    }

    /**
     * A point option, {@code x,y,z}.
     *
     * @param key The option.
     * @return The point, or null if it isn't set or isn't three values, which is logged.
     */
    @Nullable Vector3d point(@NonNull String key) {
        final String value = get(key);
        if (value == null) {
            return null;
        }
        final String[] parts = value.split(",");
        if (parts.length != POINT_VALUES) {
            log.warn("The benchmark's {} must be x,y,z, not {}", key, value);
            return null;
        }
        return new Vector3d(
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim()));
    }

    /**
     * How long the measuring turn takes, in seconds: {@link GateBenchmark#TURN_SECONDS}, or the
     * {@code .seconds} option's, so a walk can go farther through the world while it is measured.
     *
     * @return The seconds.
     */
    double measureSeconds() {
        final String value = get("seconds");
        return value == null
                ? GateBenchmark.TURN_SECONDS
                : Math.max(1, Double.parseDouble(value.trim()));
    }

    /**
     * Where the camera turns while measuring: over the world's middle, or where the {@code
     * .turnAt=x,y,z} option says, as under the sea.
     *
     * @param world The world.
     * @return The place, in the world.
     */
    Vector3d turnPosition(@NonNull SyntheticTerrain world) {
        final Vector3d at = point("turnAt");
        return at != null ? at : new Vector3d(0.5, world.cameraY(), 0.5);
    }

    /**
     * Apply the startup properties that change the renderer's settings and debug views, before a
     * run from the startup property starts: the level of detail cache and budget, which effects are
     * drawn, and which debug views show. Render thread only.
     *
     * @param scene The scene, whose debug views they turn on.
     */
    static void applyStartupSettings(@NonNull Scene scene) {
        final String prefix = GateBenchmark.PROPERTY + ".";
        final var settings = GraphicsManager.getSettings();
        final DebugVisualizers debug = scene.getDebugVisualizers();
        // Sections' cut out meshes cyan and volume bricks magenta
        if (Boolean.getBoolean(prefix + "volumeSources")) {
            debug.setVolumeSources(true);
        }
        // Another level of detail cache folder, such as an empty one to build every cell; before
        // starting, which opens the cache
        final String lodCache = System.getProperty(prefix + "lodCache");
        if (lodCache != null) {
            settings.lodCacheDirectory = lodCache;
        }
        // A bigger level of detail cache for test runs, in MiB; players keep the default
        final String lodCacheMb = System.getProperty(prefix + "lodCacheMb");
        if (lodCacheMb != null) {
            settings.lodCacheMaxBytes = (long) (Long.parseLong(lodCacheMb.trim()) * BYTES_PER_MIB);
        }
        // Another GPU budget for level of detail, in MiB, to test cutting coarser for it
        final String lodBudgetMb = System.getProperty(prefix + "lodBudgetMb");
        if (lodBudgetMb != null) {
            settings.lodResidentBytes = (long) (Long.parseLong(lodBudgetMb.trim()) * BYTES_PER_MIB);
        }
        // Whether far level of detail blends its materials' looks, for comparisons
        final String average = System.getProperty(prefix + "average");
        if (average != null) {
            settings.lodAverageMaterials = Boolean.parseBoolean(average.trim());
        }
        // On, far parts finer than their group's resolution become coverage volumes; before
        // starting, which makes the live world
        final String lodVolumes = System.getProperty(prefix + "volumes");
        if (lodVolumes != null) {
            settings.lodVolumes = Boolean.parseBoolean(lodVolumes);
        }
        // Off, every cascade is drawn whole every frame, for comparing against the cache
        final String sunCache = System.getProperty(prefix + "sunCache");
        if (sunCache != null) {
            settings.sunShadowCache = Boolean.parseBoolean(sunCache);
        }
        // Off, far leaves whose meshes are off the GPU cast no sun shadow
        final String coarseBricks = System.getProperty(prefix + "coarseBricks");
        if (coarseBricks != null) {
            settings.coarseBricks = Boolean.parseBoolean(coarseBricks);
        }
        final String translucentShadows = System.getProperty(prefix + "translucentShadows");
        if (translucentShadows != null) {
            settings.translucentShadows = Boolean.parseBoolean(translucentShadows);
        }
        final String volumeShadows = System.getProperty(prefix + "volumeShadows");
        if (volumeShadows != null) {
            settings.volumeShadows = Boolean.parseBoolean(volumeShadows);
        }
        // Off, ambient light isn't dimmed under cover; the view shows sky light in gray
        final String sky = System.getProperty(prefix + "sky");
        if (sky != null) {
            settings.skyOcclusion = Boolean.parseBoolean(sky);
        }
        if (Boolean.getBoolean(prefix + "occupancyView")) {
            debug.setOccupancyView(true);
        }
        // Off, the occupancy volume isn't kept, so nothing reads it
        final String occupancy = System.getProperty(prefix + "occupancy");
        if (occupancy != null) {
            settings.occupancy = Boolean.parseBoolean(occupancy);
        }
        // Off, ambient light isn't dimmed by the occupancy volume; the view shows it in gray
        final String environmentOcclusion = System.getProperty(prefix + "environmentOcclusion");
        if (environmentOcclusion != null) {
            settings.environmentOcclusion = Boolean.parseBoolean(environmentOcclusion);
        }
        final String ao = System.getProperty(prefix + "ao");
        if (ao != null) {
            settings.occupancyAo = Boolean.parseBoolean(ao);
        }
        if (Boolean.getBoolean(prefix + "aoView")) {
            debug.setOccupancyAoView(true);
        }
        if (Boolean.getBoolean(prefix + "rayView")) {
            debug.setOccupancyRayView(true);
        }
        // Off, no temporal anti-aliasing; the view shows each pixel's history
        final String taa = System.getProperty(prefix + "taa");
        if (taa != null) {
            settings.taa = Boolean.parseBoolean(taa);
        }
        if (Boolean.getBoolean(prefix + "taaView")) {
            debug.setTaaView(true);
        }
        // 0 draws every section's own cut out and translucent blocks, with no volumes
        final String volumeDistance = System.getProperty(prefix + "volumeDistance");
        if (volumeDistance != null) {
            settings.volumeDistance = Double.parseDouble(volumeDistance);
        }
        // Level of detail colored by CLUSTERS, GROUPS, LEVELS or CELLS
        final String lodColors = System.getProperty(prefix + "lodColors");
        if (lodColors != null) {
            debug.setLodColors(
                    DebugVisualizers.LodColors.valueOf(lodColors.trim().toUpperCase(Locale.ROOT)));
        }
        // Off, lights without a shadow map shine through walls
        final String rays = System.getProperty(prefix + "lightRays");
        if (rays != null) {
            settings.occupancyLightRays = Boolean.parseBoolean(rays);
        }
        settings.occupancyRaysPerPixel =
                Integer.getInteger(prefix + "raysPerPixel", settings.occupancyRaysPerPixel);
        if (Boolean.getBoolean(prefix + "skyView")) {
            debug.setSkyOcclusionView(true);
        }
    }
}
