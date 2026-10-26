package com.ikalagaming.graphics.scene;

import lombok.NonNull;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads and writes particle effects as YAML, one effect a file, conventionally named {@code
 * *.effect.yml}. Every key is optional and defaults as {@link ParticleEffect#builder()} does; an
 * unknown key, or a value of the wrong kind or out of range, is an error that says where.
 *
 * <pre>
 * rate: 300                  # particles a second
 * burst: 20                  # spawned at once when placed
 * lifetime: [0.4, 0.9]       # seconds, a range or one number
 * speed: [3, 6]              # meters a second, a range or one number
 * direction: [0, 1, 0]       # the way they start, in the world
 * spread: 180                # degrees from the direction, 0 to 180
 * spawnRadius: 0.2           # meters
 * acceleration: [0, -9.81, 0]
 * drag: 0.3                  # per second
 * size: [0.06, 0.02]         # meters at birth and at death, or one number
 * color:                     # linear red, green, blue and opacity; one color or start and end
 *   start: [1, 0.6, 0.2, 1]
 *   end: [1, 0.2, 0.05, 0]
 * emissive: 6                # light given off, times the color
 * lit: false                 # true for lit by the sun, sky and lights, the color their albedo
 * anisotropy: 0.2            # which way lit ones scatter, -1 back to 1 onward
 * </pre>
 */
public final class ParticleEffectLoader {

    /** Particles a second. */
    public static final String RATE = "rate";

    /** Particles spawned at once. */
    public static final String BURST = "burst";

    /** How long particles live, a number or a range. */
    public static final String LIFETIME = "lifetime";

    /** How fast they start, a number or a range. */
    public static final String SPEED = "speed";

    /** The way they start. */
    public static final String DIRECTION = "direction";

    /** The cone around the direction, in degrees. */
    public static final String SPREAD = "spread";

    /** The ball they appear in. */
    public static final String SPAWN_RADIUS = "spawnRadius";

    /** What pulls them. */
    public static final String ACCELERATION = "acceleration";

    /** How fast the air slows them. */
    public static final String DRAG = "drag";

    /** Their size, a number or start and end. */
    public static final String SIZE = "size";

    /** Their color, one color or start and end. */
    public static final String COLOR = "color";

    /** The color or size at birth. */
    public static final String START = "start";

    /** The color or size at death. */
    public static final String END = "end";

    /** How much light they give off. */
    public static final String EMISSIVE = "emissive";

    /** Whether lights light them. */
    public static final String LIT = "lit";

    /** Which way lit ones scatter. */
    public static final String ANISOTROPY = "anisotropy";

    /** The most anisotropy can be either way, short of all one way. */
    public static final double MAX_ANISOTROPY = 0.95;

    /** Every key allowed at the top of an effect file. */
    public static final Set<String> KEYS =
            Set.of(
                    RATE,
                    BURST,
                    LIFETIME,
                    SPEED,
                    DIRECTION,
                    SPREAD,
                    SPAWN_RADIUS,
                    ACCELERATION,
                    DRAG,
                    SIZE,
                    COLOR,
                    EMISSIVE,
                    LIT,
                    ANISOTROPY);

    /** The widest spread there is, in degrees: every way. */
    public static final double MAX_SPREAD_DEGREES = 180;

    /** Static loading only. */
    private ParticleEffectLoader() {}

    /**
     * Read an effect from a file.
     *
     * @param file The file.
     * @return The effect.
     * @throws ParticleEffectException If the file isn't an effect.
     * @throws UncheckedIOException If it can't be read.
     */
    public static ParticleEffect load(@NonNull Path file) {
        try (InputStream stream = Files.newInputStream(file)) {
            return load(stream, String.valueOf(file.getFileName()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the particle effect " + file, e);
        }
    }

    /**
     * Read an effect.
     *
     * @param stream The YAML.
     * @param source Where it came from, for messages.
     * @return The effect.
     * @throws ParticleEffectException If it isn't an effect.
     */
    public static ParticleEffect load(@NonNull InputStream stream, @NonNull String source) {
        final Object document;
        try {
            document = new Yaml(new SafeConstructor(new LoaderOptions())).load(stream);
        } catch (YAMLException e) {
            throw new ParticleEffectException(source + ": not valid YAML: " + e.getMessage(), e);
        }
        if (document == null) {
            // An empty file is the default effect, a start for writing one
            return ParticleEffect.builder().build();
        }
        if (!(document instanceof Map<?, ?> root)) {
            throw new ParticleEffectException(source + ": expected keys like rate and lifetime");
        }
        for (Object key : root.keySet()) {
            if (!KEYS.contains(String.valueOf(key))) {
                throw new ParticleEffectException(
                        source + ": unknown key '" + key + "', expected one of " + sorted(KEYS));
            }
        }
        final ParticleEffect.ParticleEffectBuilder builder = ParticleEffect.builder();
        final Reader reader = new Reader(source, root);
        reader.number(RATE, 0, Float.MAX_VALUE).ifPresent(builder::rate);
        reader.number(BURST, 0, Integer.MAX_VALUE)
                .ifPresent(burst -> builder.burst(Math.round(burst)));
        reader.range(LIFETIME, 0).ifPresent(range -> builder.lifetime(range[0], range[1]));
        reader.range(SPEED, -Float.MAX_VALUE).ifPresent(range -> builder.speed(range[0], range[1]));
        reader.vector3(DIRECTION).ifPresent(builder::direction);
        reader.number(SPREAD, 0, MAX_SPREAD_DEGREES)
                .ifPresent(degrees -> builder.spread((float) Math.toRadians(degrees)));
        reader.number(SPAWN_RADIUS, 0, Float.MAX_VALUE).ifPresent(builder::spawnRadius);
        reader.vector3(ACCELERATION).ifPresent(builder::acceleration);
        reader.number(DRAG, 0, Float.MAX_VALUE).ifPresent(builder::drag);
        reader.startEnd(SIZE).ifPresent(sizes -> builder.size(sizes[0], sizes[1]));
        reader.colors(COLOR).ifPresent(colors -> builder.colors(colors[0], colors[1]));
        reader.number(EMISSIVE, 0, Float.MAX_VALUE).ifPresent(builder::emissive);
        reader.bool(LIT).ifPresent(builder::lit);
        reader.number(ANISOTROPY, -MAX_ANISOTROPY, MAX_ANISOTROPY).ifPresent(builder::anisotropy);
        return builder.build();
    }

    /**
     * Write an effect as YAML that {@link #load(InputStream, String)} reads back.
     *
     * @param effect The effect.
     * @return The YAML.
     */
    public static String write(@NonNull ParticleEffect effect) {
        final Map<String, Object> root = new LinkedHashMap<>();
        root.put(RATE, effect.getRate());
        root.put(BURST, effect.getBurst());
        root.put(LIFETIME, List.of(effect.getLifetimeMin(), effect.getLifetimeMax()));
        root.put(SPEED, List.of(effect.getSpeedMin(), effect.getSpeedMax()));
        root.put(DIRECTION, list(effect.getDirection()));
        root.put(SPREAD, (float) Math.toDegrees(effect.getSpread()));
        root.put(SPAWN_RADIUS, effect.getSpawnRadius());
        root.put(ACCELERATION, list(effect.getAcceleration()));
        root.put(DRAG, effect.getDrag());
        root.put(SIZE, List.of(effect.getSizeStart(), effect.getSizeEnd()));
        final Map<String, Object> color = new LinkedHashMap<>();
        color.put(START, list(effect.getColorStart()));
        color.put(END, list(effect.getColorEnd()));
        root.put(COLOR, color);
        root.put(EMISSIVE, effect.getEmissive());
        root.put(LIT, effect.isLit());
        root.put(ANISOTROPY, effect.getAnisotropy());
        // Lists of numbers stay on one line, as people write them
        final DumperOptions flow = new DumperOptions();
        flow.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        final StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nested) {
                text.append(entry.getKey()).append(":\n");
                for (Map.Entry<?, ?> inner : nested.entrySet()) {
                    text.append("  ")
                            .append(inner.getKey())
                            .append(": ")
                            .append(new Yaml(flow).dump(inner.getValue()).strip())
                            .append('\n');
                }
            } else {
                text.append(entry.getKey())
                        .append(": ")
                        .append(new Yaml(flow).dump(entry.getValue()).strip())
                        .append('\n');
            }
        }
        return text.toString();
    }

    /**
     * A vector's components as a list.
     *
     * @param vector The vector.
     * @return Its x, y and z.
     */
    private static List<Float> list(Vector3fc vector) {
        return List.of(vector.x(), vector.y(), vector.z());
    }

    /**
     * A vector's components as a list.
     *
     * @param vector The vector.
     * @return Its x, y, z and w.
     */
    private static List<Float> list(Vector4fc vector) {
        return List.of(vector.x(), vector.y(), vector.z(), vector.w());
    }

    /**
     * Keys in order, for messages.
     *
     * @param keys The keys.
     * @return Them sorted, comma separated.
     */
    private static String sorted(Set<String> keys) {
        return String.join(", ", keys.stream().sorted().toList());
    }

    /** Reads typed values out of a file's top level, reporting where a value is wrong. */
    private record Reader(String source, Map<?, ?> root) {

        /**
         * A number in a range.
         *
         * @param key Its key.
         * @param min The least allowed.
         * @param max The most allowed.
         * @return The number, or empty if the key is missing.
         */
        Optional<Float> number(String key, double min, double max) {
            if (!root.containsKey(key)) {
                return Optional.empty();
            }
            return Optional.of(checked(key, root.get(key), min, max));
        }

        /**
         * True or false.
         *
         * @param key Its key.
         * @return The value, or empty if the key is missing.
         */
        Optional<Boolean> bool(String key) {
            if (!root.containsKey(key)) {
                return Optional.empty();
            }
            if (!(root.get(key) instanceof Boolean value)) {
                throw error(key, "expected true or false");
            }
            return Optional.of(value);
        }

        /**
         * A range: one number for both ends, or [min, max] with min at most max.
         *
         * @param key Its key.
         * @param least The least either end may be.
         * @return The ends, or empty if the key is missing.
         */
        Optional<float[]> range(String key, double least) {
            if (!root.containsKey(key)) {
                return Optional.empty();
            }
            final Object value = root.get(key);
            if (value instanceof Number) {
                final float number = checked(key, value, least, Float.MAX_VALUE);
                return Optional.of(new float[] {number, number});
            }
            final float[] ends = numbers(key, value, 2, "a number or [min, max]");
            for (float end : ends) {
                if (end < least) {
                    throw error(key, "must be at least " + least);
                }
            }
            if (ends[0] > ends[1]) {
                throw error(key, "the first number must be no more than the second");
            }
            return Optional.of(ends);
        }

        /**
         * Start and end values: one number for both, or [start, end], neither negative.
         *
         * @param key Its key.
         * @return The values, or empty if the key is missing.
         */
        Optional<float[]> startEnd(String key) {
            if (!root.containsKey(key)) {
                return Optional.empty();
            }
            final Object value = root.get(key);
            if (value instanceof Number) {
                final float number = checked(key, value, 0, Float.MAX_VALUE);
                return Optional.of(new float[] {number, number});
            }
            final float[] ends = numbers(key, value, 2, "a number or [start, end]");
            if (ends[0] < 0 || ends[1] < 0) {
                throw error(key, "must not be negative");
            }
            return Optional.of(ends);
        }

        /**
         * Three numbers.
         *
         * @param key Its key.
         * @return The vector, or empty if the key is missing.
         */
        Optional<Vector3fc> vector3(String key) {
            if (!root.containsKey(key)) {
                return Optional.empty();
            }
            final float[] values = numbers(key, root.get(key), 3, "[x, y, z]");
            return Optional.of(new Vector3f(values[0], values[1], values[2]));
        }

        /**
         * One color for both birth and death, or a map of start and end, each four numbers.
         *
         * @param key Its key.
         * @return The start and end colors, or empty if the key is missing.
         */
        Optional<Vector4fc[]> colors(String key) {
            if (!root.containsKey(key)) {
                return Optional.empty();
            }
            final Object value = root.get(key);
            if (value instanceof Map<?, ?> ends) {
                for (Object end : ends.keySet()) {
                    if (!START.equals(String.valueOf(end)) && !END.equals(String.valueOf(end))) {
                        throw error(key, "unknown key '" + end + "', expected start and end");
                    }
                }
                final ParticleEffect defaults = ParticleEffect.builder().build();
                final Vector4fc start =
                        ends.containsKey(START)
                                ? color(key + "." + START, ends.get(START))
                                : defaults.getColorStart();
                final Vector4fc end =
                        ends.containsKey(END)
                                ? color(key + "." + END, ends.get(END))
                                : defaults.getColorEnd();
                return Optional.of(new Vector4fc[] {start, end});
            }
            final Vector4fc color = color(key, value);
            return Optional.of(new Vector4fc[] {color, new Vector4f(color)});
        }

        /**
         * A color: four numbers, none negative, opacity at most 1.
         *
         * @param key Where it is, for messages.
         * @param value The value.
         * @return The color.
         */
        private Vector4fc color(String key, Object value) {
            final float[] values = numbers(key, value, 4, "[red, green, blue, opacity]");
            for (float component : values) {
                if (component < 0) {
                    throw error(key, "colors must not be negative");
                }
            }
            if (values[3] > 1) {
                throw error(key, "opacity must be at most 1");
            }
            return new Vector4f(values[0], values[1], values[2], values[3]);
        }

        /**
         * A list of a set count of numbers.
         *
         * @param key Where it is, for messages.
         * @param value The value.
         * @param count How many numbers.
         * @param expected What was expected, for messages.
         * @return The numbers.
         */
        private float[] numbers(String key, Object value, int count, String expected) {
            if (!(value instanceof List<?> list) || list.size() != count) {
                throw error(key, "expected " + expected);
            }
            final float[] numbers = new float[count];
            for (int i = 0; i < count; ++i) {
                if (!(list.get(i) instanceof Number number)) {
                    throw error(key, "expected " + expected);
                }
                numbers[i] = number.floatValue();
            }
            return numbers;
        }

        /**
         * A number checked against a range.
         *
         * @param key Where it is, for messages.
         * @param value The value.
         * @param min The least allowed.
         * @param max The most allowed.
         * @return The number.
         */
        private float checked(String key, Object value, double min, double max) {
            if (!(value instanceof Number given)) {
                throw error(key, "expected a number");
            }
            final double number = given.doubleValue();
            if (!Double.isFinite(number) || number < min || number > max) {
                throw error(
                        key,
                        max >= Float.MAX_VALUE
                                ? "must be at least " + min
                                : "must be from " + min + " to " + max);
            }
            return (float) number;
        }

        /**
         * An error at a key.
         *
         * @param key Where.
         * @param message What is wrong.
         * @return The exception.
         */
        private ParticleEffectException error(String key, String message) {
            return new ParticleEffectException(source + ": " + key + ": " + message);
        }
    }
}
