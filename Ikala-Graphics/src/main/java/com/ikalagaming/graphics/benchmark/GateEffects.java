package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.EmitterHandle;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.MediumHandle;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.MediumVolume;
import com.ikalagaming.graphics.scene.ParticleEffect;
import com.ikalagaming.graphics.scene.ParticleSamples;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * A {@link GateBenchmark} run's fog, smoke, particle effects and weather: placed from its options
 * when the world is shown, changed from the debug window while the world is kept, and taken away
 * when it is released. Render thread only.
 */
@Slf4j
public final class GateEffects {

    /** The {@code .weather} option's value for rain. */
    public static final String WEATHER_RAIN = "rain";

    /** The {@code .weather} option's value for snow. */
    public static final String WEATHER_SNOW = "snow";

    /** The {@code .weather} option's value for none. */
    public static final String WEATHER_NONE = "none";

    /** The weathers the debug window offers, in order. */
    public static final List<String> WEATHERS = List.of(WEATHER_NONE, WEATHER_RAIN, WEATHER_SNOW);

    /** Where the fog's anisotropy is in the {@code .fog} option, after three other values. */
    private static final int FOG_ANISOTROPY_PART = 3;

    /** How far in front of the camera effects are placed, in meters. */
    private static final double EFFECTS_AHEAD = 8;

    /** Where the test sparks are from the fountain, in meters. */
    private static final Vector3d SPARKS_OFFSET = new Vector3d(6, 0, 0);

    /** Where the test smoke puffs are from the fountain, in meters. */
    private static final Vector3d PUFFS_OFFSET = new Vector3d(-6, 0, 0);

    /** Where the test thick smoke, drawn in the fog volume, is from the fountain, in meters. */
    private static final Vector3d THICK_SMOKE_OFFSET = new Vector3d(0, 0, -8);

    /** Where the test fire is from the fountain, in meters. */
    private static final Vector3d FIRE_OFFSET = new Vector3d(0, 0, 6);

    /** Where the test weather's box is centered, from the camera, in meters: a little above it. */
    private static final Vector3d WEATHER_OFFSET = new Vector3d(0, 2, 0);

    /** How tall the test smoke column is, half of it, in meters. */
    private static final float SMOKE_HALF_HEIGHT = 20;

    /** The test smoke column's radius, in meters. */
    private static final float SMOKE_RADIUS = 4;

    /** How dense the test smoke is, per meter. */
    private static final float SMOKE_DENSITY = 0.4f;

    /** How much of the light the test smoke stops it scatters: white smoke, like steam. */
    private static final Vector3fc SMOKE_ALBEDO = new Vector3f(0.9f);

    /** The test smoke's drift, up and along x, in meters a second. */
    private static final Vector3fc SMOKE_WIND = new Vector3f(1, 3, 0);

    /** Where the glass test's smoke stands along z: behind the fountain. */
    private static final double GLASS_TEST_SMOKE_Z = -10;

    /** Where the glowing sphere is from the smoke, and its radius, in meters. */
    private static final Vector3d GLOW_OFFSET = new Vector3d(-25, 5, 0);

    private static final float GLOW_RADIUS = 6;

    /** The glowing sphere's light per unit of density: an orange glow, as over a fire. */
    private static final Vector3fc GLOW_EMISSION = new Vector3f(4, 1.5f, 0.3f);

    /** How fast the glowing sphere's noise rises, in meters a second. */
    private static final Vector3fc GLOW_DRIFT = new Vector3f(0, 1, 0);

    /** Where the fog bank is from the smoke, and its half size, in meters. */
    private static final Vector3d BANK_OFFSET = new Vector3d(0, -12, 40);

    private static final Vector3fc BANK_HALF_SIZE = new Vector3f(40, 5, 15);

    /** How dense the fog bank is, per meter. */
    private static final float BANK_DENSITY = 0.08f;

    /** The context everything is made under. */
    private final GraphicsContext graphics;

    /** The run's options. */
    private final GateOptions options;

    /** The run's world, or null until it is laid out. */
    private final Supplier<SyntheticTerrain> world;

    /** The test medium volumes the run placed, removed when it ends. */
    private final List<MediumHandle> testMedia = new ArrayList<>();

    /** The test particle emitters the run placed, stopped when it ends. */
    private final List<EmitterHandle> testEmitters = new ArrayList<>();

    /** The weather falling, see {@link #setWeather}: none, {@link #WEATHER_RAIN} or snow. */
    private String weather = WEATHER_NONE;

    /** The weather's emitter, or null for none. */
    private EmitterHandle weatherEmitter;

    /** The particle effects placed from the debug window, see {@link #setEffects}. */
    private final List<EmitterHandle> effectEmitters = new ArrayList<>();

    /** The moving test emitters from the {@code .fxTest} option, or null for none. */
    private FxTest fxTest;

    /**
     * Set up a run's effects.
     *
     * @param graphics The context everything is made under.
     * @param options The run's options.
     * @param world The run's world, or null until it is laid out.
     */
    GateEffects(
            @NonNull GraphicsContext graphics,
            @NonNull GateOptions options,
            @NonNull Supplier<SyntheticTerrain> world) {
        this.graphics = graphics;
        this.options = options;
        this.world = world;
    }

    /**
     * Turn the scene's fog on from the options: {@code .fog} gives its density per meter, then,
     * comma separated, its height falloff per meter, base height and phase anisotropy; {@code
     * .haze=true} adds the physical sky's air and haze.
     *
     * @param fog The scene's fog.
     */
    void placeFog(@NonNull Fog fog) {
        final String value = options.get("fog");
        if (value == null) {
            return;
        }
        final String[] parts = value.split(",");
        fog.setActive(true);
        fog.setDensity(Float.parseFloat(parts[0].trim()));
        if (parts.length > 1) {
            fog.setHeightFalloff(Float.parseFloat(parts[1].trim()));
        }
        if (parts.length > 2) {
            fog.setBaseHeight(Float.parseFloat(parts[2].trim()));
        }
        if (parts.length > FOG_ANISOTROPY_PART) {
            fog.setAnisotropy(Float.parseFloat(parts[FOG_ANISOTROPY_PART].trim()));
        }
        fog.setHaze(options.flag("haze"));
    }

    /**
     * Place test medium volumes from the options: {@code .smoke=x,y,z} puts a column of gray smoke
     * drifting up there, a glowing orange sphere beside it and a flat fog bank in front, for
     * looking at smoke with the fog, the lights and transparency.
     */
    void placeTestMedia() {
        final Vector3d at = options.point("smoke");
        if (at == null) {
            return;
        }
        final MediumVolume smoke =
                MediumVolume.of(
                                MediumVolume.Shape.CYLINDER,
                                at,
                                new Vector3f(SMOKE_RADIUS, SMOKE_HALF_HEIGHT, 0))
                        .withMedium(SMOKE_DENSITY, SMOKE_ALBEDO, new Vector3f())
                        .withNoise(
                                MediumVolume.DEFAULT_EDGE * 2,
                                MediumVolume.DEFAULT_NOISE_SCALE,
                                0.7f,
                                SMOKE_WIND);
        final MediumVolume glow =
                MediumVolume.of(
                                MediumVolume.Shape.SPHERE,
                                new Vector3d(at).add(GLOW_OFFSET),
                                new Vector3f(GLOW_RADIUS))
                        .withMedium(SMOKE_DENSITY / 2, new Vector3f(0.5f), GLOW_EMISSION)
                        .withNoise(
                                GLOW_RADIUS / 2,
                                MediumVolume.DEFAULT_NOISE_SCALE,
                                MediumVolume.DEFAULT_NOISE_STRENGTH,
                                GLOW_DRIFT);
        final MediumVolume bank =
                MediumVolume.of(
                                MediumVolume.Shape.BOX,
                                new Vector3d(at).add(BANK_OFFSET),
                                BANK_HALF_SIZE)
                        .withMedium(BANK_DENSITY, new Vector3f(0.9f), new Vector3f())
                        .withNoise(
                                MediumVolume.DEFAULT_EDGE * 4,
                                MediumVolume.DEFAULT_NOISE_SCALE * 3,
                                MediumVolume.DEFAULT_NOISE_STRENGTH,
                                new Vector3f(0.5f, 0, 0));
        for (MediumVolume volume : List.of(smoke, glow, bank)) {
            addMedium(volume);
        }
    }

    /**
     * With the {@code .glassTest} option on the island, a column of gray smoke behind the fountain,
     * to see through its water.
     *
     * @param terrain The world.
     */
    void placeGlassTestSmoke(@NonNull SyntheticTerrain terrain) {
        if (!terrain.isGlassTest() || terrain.getRecipe() != SyntheticTerrain.Recipe.ISLAND) {
            return;
        }
        addMedium(
                MediumVolume.of(
                                MediumVolume.Shape.CYLINDER,
                                new Vector3d(
                                        0,
                                        terrain.fountainBase() + SMOKE_HALF_HEIGHT,
                                        GLASS_TEST_SMOKE_Z),
                                new Vector3f(SMOKE_RADIUS / 2, SMOKE_HALF_HEIGHT, 0))
                        .withMedium(SMOKE_DENSITY, SMOKE_ALBEDO, new Vector3f())
                        .withNoise(
                                MediumVolume.DEFAULT_EDGE,
                                MediumVolume.DEFAULT_NOISE_SCALE,
                                0.7f,
                                SMOKE_WIND));
    }

    /**
     * Add a test medium volume, kept to remove when the run ends.
     *
     * @param volume The volume.
     */
    private void addMedium(@NonNull MediumVolume volume) {
        final MediumHandle handle = graphics.media().add(volume);
        if (handle != null) {
            testMedia.add(handle);
        }
    }

    /**
     * Place test particle emitters from the options: {@code .particles=x,y,z} puts a fountain
     * there, glowing sparks 6 m along x, rising smoke puffs 6 m along -x, thick smoke in the fog
     * volume 8 m along -z and a fire 6 m along z, for looking at particles with the fog, the lights
     * and transparency; {@code .weather=rain} or {@code snow} makes it rain or snow around the
     * camera; {@code .fxTest=x,y,z} places emitters moving as a character's effects do there, see
     * {@link FxTest}.
     */
    void placeTestParticles() {
        final Vector3d fx = options.point("fxTest");
        if (fx != null) {
            fxTest = new FxTest(graphics.particles(), fx);
            testEmitters.addAll(fxTest.handles());
        }
        final String startWeather = options.get("weather");
        if (startWeather != null) {
            setWeather(startWeather);
        }
        final Vector3d at = options.point("particles");
        if (at == null) {
            return;
        }
        for (EmitterHandle handle : placeEffectSet(at)) {
            if (handle != null) {
                testEmitters.add(handle);
            }
        }
    }

    /**
     * Place the test particle effects around a point: a fountain there, sparks, smoke puffs, thick
     * smoke in the fog volume and a fire around it.
     *
     * @param at The point.
     * @return Their emitters, null where the scene was full.
     */
    private EmitterHandle[] placeEffectSet(@NonNull Vector3dc at) {
        return new EmitterHandle[] {
            graphics.particles().spawn(ParticleSamples.FOUNTAIN, at),
            graphics.particles().spawn(ParticleSamples.SPARKS, new Vector3d(at).add(SPARKS_OFFSET)),
            graphics.particles().spawn(ParticleSamples.SMOKE, new Vector3d(at).add(PUFFS_OFFSET)),
            graphics.particles()
                    .spawn(ParticleSamples.THICK_SMOKE, new Vector3d(at).add(THICK_SMOKE_OFFSET)),
            graphics.particles().spawn(ParticleSamples.FIRE, new Vector3d(at).add(FIRE_OFFSET))
        };
    }

    /** Move the {@code .fxTest} emitters along, once a frame while the world is up. */
    void step() {
        if (fxTest != null && !graphics.isClosed()) {
            fxTest.step(graphics.particles());
        }
    }

    /**
     * What falls around the camera, see {@link #WEATHERS}.
     *
     * @return The weather.
     */
    public String getWeather() {
        return weather;
    }

    /**
     * Make it rain or snow around the camera, or stop. Render thread only.
     *
     * @param kind {@link #WEATHER_NONE}, {@link #WEATHER_RAIN} or {@link #WEATHER_SNOW}.
     */
    public void setWeather(@NonNull String kind) {
        final String wanted = kind.trim().toLowerCase(Locale.ROOT);
        final ParticleEffect effect =
                switch (wanted) {
                    case WEATHER_RAIN -> ParticleSamples.RAIN;
                    case WEATHER_SNOW -> ParticleSamples.SNOW;
                    case WEATHER_NONE -> null;
                    default -> {
                        log.warn(
                                "The benchmark's weather must be one of {}, not {}",
                                WEATHERS,
                                kind);
                        yield null;
                    }
                };
        if (wanted.equals(weather) || graphics.isClosed()) {
            return;
        }
        if (weatherEmitter != null) {
            graphics.particles().stop(weatherEmitter);
            weatherEmitter = null;
        }
        weather = effect == null ? WEATHER_NONE : wanted;
        if (effect != null) {
            weatherEmitter = graphics.particles().spawn(effect, WEATHER_OFFSET);
        }
    }

    /**
     * Whether the particle effects are placed, see {@link #setEffects}.
     *
     * @return True if they are.
     */
    public boolean hasEffects() {
        return !effectEmitters.isEmpty();
    }

    /**
     * Place the test particle effects on the ground in front of the camera, as {@code .particles}
     * does, or take them away. Render thread only.
     *
     * @param on Whether to place them.
     */
    public void setEffects(boolean on) {
        effectEmitters.forEach(graphics.particles()::stop);
        effectEmitters.clear();
        final SyntheticTerrain terrain = world.get();
        final Scene scene = GraphicsManager.getScene();
        if (!on || terrain == null || scene == null || graphics.isClosed()) {
            return;
        }
        final double yaw = scene.getCamera().getRotation().y;
        final Vector3dc camera = scene.getCamera().getPosition();
        final int x = (int) Math.floor(camera.x() + Math.sin(yaw) * EFFECTS_AHEAD);
        final int z = (int) Math.floor(camera.z() - Math.cos(yaw) * EFFECTS_AHEAD);
        final Vector3d at = new Vector3d(x + 0.5, terrain.top(x, z) + 1, z + 0.5);
        for (EmitterHandle handle : placeEffectSet(at)) {
            if (handle != null) {
                effectEmitters.add(handle);
            }
        }
    }

    /**
     * How many test medium volumes the run placed.
     *
     * @return The count.
     */
    int mediaCount() {
        return testMedia.size();
    }

    /**
     * How many test emitters the run placed from its options, the weather's and the debug window's
     * aside.
     *
     * @return The count.
     */
    int emitterCount() {
        return testEmitters.size();
    }

    /** Take away everything placed, unless the graphics are closed already. */
    void release() {
        if (graphics.isClosed()) {
            return;
        }
        testMedia.forEach(graphics.media()::remove);
        testMedia.clear();
        testEmitters.forEach(graphics.particles()::stop);
        testEmitters.clear();
        fxTest = null;
        if (weatherEmitter != null) {
            graphics.particles().stop(weatherEmitter);
            weatherEmitter = null;
        }
        weather = WEATHER_NONE;
        effectEmitters.forEach(graphics.particles()::stop);
        effectEmitters.clear();
    }
}
