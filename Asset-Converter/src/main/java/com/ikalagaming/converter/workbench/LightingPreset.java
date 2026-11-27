package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.LightHandle;
import com.ikalagaming.graphics.Lights;
import com.ikalagaming.graphics.scene.LightUnits;
import com.ikalagaming.graphics.scene.PhysicalSky;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Lighting to judge an asset under, in physical units: a sun, a sky, point lights and a fixed
 * exposure, so two assets compare under the same light. Read from {@code *.lighting.yml} files in
 * the converter's data folder, so a new one needs no code:
 *
 * <pre>
 * name: Torch cave
 * order: 4              # where it is listed, lowest first
 * sun:                  # leave out for none
 *   around: 40          # degrees, turning from +z toward +x, as the lighting panel shows
 *   up: 70              # degrees above the horizon
 *   color: [1, 0.97, 0.92]
 *   lux: 100000
 * sky: physical         # physical (lit by the sun), ambient, or none
 * ambient:              # for an ambient sky: a luminance from every direction
 *   color: [1, 1, 1]
 *   luminance: 3000     # cd/m^2
 * glow: 0.002           # a physical sky's own light, airglow and starlight, in lux
 * stars: true           # whether a physical sky shows stars
 * lights:               # point lights, from the middle of what is shown, in meters
 *   - at: [0.8, 1.2, 0.6]
 *     color: [1, 0.6, 0.3]
 *     candela: 60
 *     range: 6          # meters; leave out to follow the intensity
 * exposure: 5           # EV100
 * </pre>
 *
 * @param name What it is called.
 * @param order Where it is listed, lowest first.
 * @param sun The sun or moon, or null for none.
 * @param sky What lights the scene from every direction.
 * @param ambient The ambient sky's color, linear.
 * @param ambientLuminance The ambient sky's luminance, in cd/m^2.
 * @param glow A physical sky's own light, in lux.
 * @param stars Whether a physical sky shows stars.
 * @param lights The point lights.
 * @param exposure The exposure, in EV100.
 */
@Slf4j
public record LightingPreset(
        @NonNull String name,
        int order,
        Sun sun,
        @NonNull Sky sky,
        @NonNull Vector3f ambient,
        float ambientLuminance,
        float glow,
        boolean stars,
        @NonNull List<Point> lights,
        float exposure) {

    /** The end of a preset file's name. */
    public static final String SUFFIX = ".lighting.yml";

    /** The folder in the converter's data folder that holds the presets. */
    public static final String FOLDER = "lighting";

    /** The illuminance a magnitude 0 star gives, in lux. */
    static final float MAGNITUDE_ZERO_LUX = 2.54e-6f;

    /** The night sky's glow's color, a little blue, luminance 1. */
    private static final Vector3f GLOW_COLOR = new Vector3f(0.9f, 0.95f, 1.1f);

    /** Keys in a preset file. */
    private static final String KEY_COLOR = "color";

    /** What lights the scene from every direction. */
    public enum Sky {
        /** Earth's atmosphere, lit by the sun, and its own glow. */
        PHYSICAL,
        /** One luminance from every direction. */
        AMBIENT,
        /** Nothing: only the sun and the point lights. */
        NONE
    }

    /**
     * A sun, or the moon.
     *
     * @param around The degrees it turns from +z toward +x.
     * @param up The degrees it is above the horizon.
     * @param color Its color, linear.
     * @param lux Its illuminance.
     */
    public record Sun(float around, float up, @NonNull Vector3f color, float lux) {
        /**
         * The way its light travels, down for a sun overhead.
         *
         * @return The direction, normalized.
         */
        public Vector3f direction() {
            final double turn = Math.toRadians(around);
            final double height = Math.toRadians(up);
            return new Vector3f(
                    (float) (Math.cos(height) * Math.sin(turn)),
                    (float) -Math.sin(height),
                    (float) (Math.cos(height) * Math.cos(turn)));
        }
    }

    /**
     * A point light.
     *
     * @param at Where it is, from the middle of what is shown, in meters.
     * @param color Its color, linear.
     * @param candela Its intensity.
     * @param range How far it reaches, in meters, or NaN to follow the intensity.
     */
    public record Point(
            @NonNull Vector3f at, @NonNull Vector3f color, float candela, float range) {}

    /**
     * Read every preset in a folder, listed in their order then by name. Files that can't be read
     * are left out, with a warning.
     *
     * @param folder The folder.
     * @return The presets.
     */
    public static List<LightingPreset> loadAll(@NonNull Path folder) {
        final List<LightingPreset> presets = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return presets;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file :
                    files.filter(f -> f.getFileName().toString().endsWith(SUFFIX)).toList()) {
                try {
                    presets.add(parse(Files.readString(file)));
                } catch (IOException | IllegalArgumentException | YAMLException e) {
                    log.warn("Could not read the lighting preset {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Could not list the lighting presets in {}", folder, e);
        }
        presets.sort(
                Comparator.comparingInt(LightingPreset::order).thenComparing(LightingPreset::name));
        return presets;
    }

    /**
     * Read a preset.
     *
     * @param text The YAML.
     * @return The preset.
     * @throws IllegalArgumentException If something is missing or of the wrong kind.
     */
    public static LightingPreset parse(@NonNull String text) {
        final Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        if (!(document instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("a preset is a map of settings");
        }
        final String name = string(map, "name");
        final Sun sun =
                map.get("sun") instanceof Map<?, ?> s
                        ? new Sun(
                                number(s, "around", 0),
                                number(s, "up", 45),
                                color(s, KEY_COLOR),
                                number(s, "lux", 0))
                        : null;
        final Sky sky;
        try {
            sky =
                    Sky.valueOf(
                            String.valueOf(map.containsKey("sky") ? map.get("sky") : "none")
                                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("sky is physical, ambient or none", e);
        }
        Vector3f ambient = new Vector3f(1);
        float luminance = 0;
        if (map.get("ambient") instanceof Map<?, ?> a) {
            ambient = color(a, KEY_COLOR);
            luminance = number(a, "luminance", 0);
        }
        final List<Point> lights = new ArrayList<>();
        if (map.get("lights") instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> light)) {
                    throw new IllegalArgumentException("each light is a map");
                }
                lights.add(
                        new Point(
                                vector(light, "at"),
                                color(light, KEY_COLOR),
                                number(light, "candela", 0),
                                number(light, "range", Float.NaN)));
            }
        }
        return new LightingPreset(
                name,
                (int) number(map, "order", 0),
                sun,
                sky,
                ambient,
                luminance,
                number(map, "glow", 0),
                Boolean.TRUE.equals(map.get("stars")),
                List.copyOf(lights),
                number(map, "exposure", LightUnits.DEFAULT_EV100));
    }

    /**
     * Light a scene by the preset: its sun, sky and exposure, and its point lights around a middle.
     * Lights placed by an earlier preset should be removed first.
     *
     * @param scene The scene.
     * @param lights Places the point lights in the scene.
     * @param middle The middle of what is shown.
     * @return The point lights placed.
     */
    public List<LightHandle> apply(
            @NonNull Scene scene, @NonNull Lights lights, @NonNull Vector3dc middle) {
        if (sun != null) {
            scene.getSceneLights()
                    .setDirLight(
                            new DirectionalLight(
                                    new Vector3f(sun.color()), sun.direction(), sun.lux()));
        } else {
            scene.getSceneLights()
                    .setDirLight(new DirectionalLight(new Vector3f(1), new Vector3f(0, -1, 0), 0));
        }
        switch (sky) {
            case PHYSICAL -> {
                final PhysicalSky physical = new PhysicalSky();
                physical.setGlow(new Vector3f(GLOW_COLOR).mul(glow / (float) Math.PI));
                physical.setStarIlluminance(stars ? MAGNITUDE_ZERO_LUX : 0);
                scene.setPhysicalSky(physical);
                scene.getSceneLights().setAmbientLight(new AmbientLight(new Vector3f(1), 0));
            }
            case AMBIENT -> {
                scene.setPhysicalSky(null);
                scene.getSceneLights()
                        .setAmbientLight(new AmbientLight(new Vector3f(ambient), ambientLuminance));
            }
            case NONE -> {
                scene.setPhysicalSky(null);
                scene.getSceneLights().setAmbientLight(new AmbientLight(new Vector3f(1), 0));
            }
        }
        scene.setExposureValue(exposure);
        scene.setPreExposure(LightUnits.preExposure(illuminance()));
        final List<LightHandle> placed = new ArrayList<>();
        for (Point point : this.lights) {
            final Vector3d at = new Vector3d(point.at()).add(middle);
            placed.add(lights.point(at, point.color(), point.candela(), point.range()));
        }
        return placed;
    }

    /**
     * About how much light reaches a lit surface, in lux, for the pre-exposure: the sun, the sky
     * and the glow. Point lights count by what they give a meter away.
     *
     * @return The illuminance.
     */
    float illuminance() {
        float lux = sun == null ? 0 : sun.lux();
        if (sky == Sky.AMBIENT) {
            lux += ambientLuminance * (float) Math.PI;
        }
        if (sky == Sky.PHYSICAL) {
            lux += glow;
        }
        for (Point point : lights) {
            lux += point.candela();
        }
        return lux;
    }

    /**
     * A string setting.
     *
     * @param map The settings.
     * @param key The setting.
     * @return Its value.
     * @throws IllegalArgumentException If it is missing.
     */
    private static String string(Map<?, ?> map, String key) {
        final Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is missing");
        }
        return String.valueOf(value);
    }

    /**
     * A number setting.
     *
     * @param map The settings.
     * @param key The setting.
     * @param fallback What a missing setting is.
     * @return Its value.
     * @throws IllegalArgumentException If it isn't a number.
     */
    private static float number(Map<?, ?> map, String key, float fallback) {
        final Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " is a number");
        }
        return number.floatValue();
    }

    /**
     * A color setting: three numbers, linear; white if missing.
     *
     * @param map The settings.
     * @param key The setting.
     * @return Its value.
     */
    private static Vector3f color(Map<?, ?> map, String key) {
        return map.containsKey(key) ? vector(map, key) : new Vector3f(1);
    }

    /**
     * A setting of three numbers.
     *
     * @param map The settings.
     * @param key The setting.
     * @return Its value.
     * @throws IllegalArgumentException If it is missing or isn't three numbers.
     */
    private static Vector3f vector(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof List<?> list)
                || list.size() != 3
                || !(list.get(0) instanceof Number x)
                || !(list.get(1) instanceof Number y)
                || !(list.get(2) instanceof Number z)) {
            throw new IllegalArgumentException(key + " is three numbers");
        }
        return new Vector3f(x.floatValue(), y.floatValue(), z.floatValue());
    }
}
