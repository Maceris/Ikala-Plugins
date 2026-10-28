package com.ikalagaming.graphics.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Reading and writing particle effects as YAML. */
class ParticleEffectLoaderTest {

    /** How close floats must come back from a round trip, after degrees and radians. */
    private static final float TOLERANCE = 1e-5f;

    /**
     * Read an effect from text.
     *
     * @param yaml The YAML.
     * @return The effect.
     */
    private static ParticleEffect read(String yaml) {
        return ParticleEffectLoader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.effect.yml");
    }

    /**
     * The message reading some text fails with.
     *
     * @param yaml The YAML.
     * @return The message.
     */
    private static String failure(String yaml) {
        return assertThrows(ParticleEffectException.class, () -> read(yaml)).getMessage();
    }

    /** Every key reads into its field, spread from degrees. */
    @Test
    void testFullEffect() {
        final ParticleEffect effect =
                read(
                        """
                        rate: 300
                        burst: 20
                        lifetime: [0.4, 0.9]
                        speed: [3, 6]
                        direction: [1, 0, 0]
                        spread: 90
                        spawnRadius: 0.2
                        acceleration: [0, -9.81, 0]
                        drag: 0.3
                        size: [0.06, 0.02]
                        color:
                          start: [1, 0.6, 0.2, 1]
                          end: [1, 0.2, 0.05, 0]
                        emissive: 6
                        lit: false
                        anisotropy: -0.3
                        collision: Bounce
                        bounce: 0.5
                        friction: 0.1
                        volumetric: true
                        spawnBox: [30, 20, 30]
                        aroundCamera: true
                        stretch: 0.02
                        splashCount: 3
                        splash:
                          rate: 0
                          lifetime: 0.3
                          size: [0.05, 0.1]
                        follow: 0.5
                        inheritVelocity: 0.25
                        """);
        assertEquals(300, effect.getRate());
        assertEquals(20, effect.getBurst());
        assertEquals(0.4f, effect.getLifetimeMin());
        assertEquals(0.9f, effect.getLifetimeMax());
        assertEquals(3, effect.getSpeedMin());
        assertEquals(6, effect.getSpeedMax());
        assertEquals(new Vector3f(1, 0, 0), effect.getDirection());
        assertEquals(Math.PI / 2, effect.getSpread(), TOLERANCE);
        assertEquals(0.2f, effect.getSpawnRadius());
        assertEquals(new Vector3f(0, -9.81f, 0), effect.getAcceleration());
        assertEquals(0.3f, effect.getDrag());
        assertEquals(0.06f, effect.getSizeStart());
        assertEquals(0.02f, effect.getSizeEnd());
        assertEquals(new Vector4f(1, 0.6f, 0.2f, 1), effect.getColorStart());
        assertEquals(new Vector4f(1, 0.2f, 0.05f, 0), effect.getColorEnd());
        assertEquals(6, effect.getEmissive());
        assertFalse(effect.isLit());
        assertEquals(-0.3f, effect.getAnisotropy());
        assertEquals(ParticleEffect.Collision.BOUNCE, effect.getCollision());
        assertEquals(0.5f, effect.getBounce());
        assertEquals(0.1f, effect.getFriction());
        assertTrue(effect.isVolumetric());
        assertEquals(new Vector3f(30, 20, 30), effect.getSpawnBox());
        assertTrue(effect.isAroundCamera());
        assertEquals(0.02f, effect.getStretch());
        assertEquals(3, effect.getSplashCount());
        assertNotNull(effect.getSplash());
        assertEquals(0.3f, effect.getSplash().getLifetimeMax());
        assertEquals(0.1f, effect.getSplash().getSizeEnd());
        assertEquals(0.5f, effect.getFollow());
        assertEquals(0.25f, effect.getInheritVelocity());
        assertTrue(effect.needsLink());
        assertFalse(ParticleEffect.builder().build().needsLink());
        // Following the emitter takes a link of its own; inheriting its velocity doesn't
        assertTrue(ParticleEffect.builder().follow(1).build().needsLink());
        assertFalse(ParticleEffect.builder().inheritVelocity(1).build().needsLink());
    }

    /** Missing keys default, one number stands for both ends, and one color for both. */
    @Test
    void testDefaultsAndShortForms() {
        final ParticleEffect defaults = ParticleEffect.builder().build();
        final ParticleEffect effect =
                read("rate: 5\nlifetime: 2\nsize: 0.5\ncolor: [0.1, 0.2, 0.3, 0.4]\n");
        assertEquals(5, effect.getRate());
        assertEquals(2, effect.getLifetimeMin());
        assertEquals(2, effect.getLifetimeMax());
        assertEquals(0.5f, effect.getSizeStart());
        assertEquals(0.5f, effect.getSizeEnd());
        assertEquals(new Vector4f(0.1f, 0.2f, 0.3f, 0.4f), effect.getColorStart());
        assertEquals(effect.getColorStart(), effect.getColorEnd());
        assertEquals(defaults.getSpeedMin(), effect.getSpeedMin());
        assertEquals(defaults.getDirection(), effect.getDirection());
        assertEquals(defaults, read(""));
        final ParticleEffect startOnly = read("color:\n  start: [1, 1, 1, 1]\n");
        assertEquals(defaults.getColorEnd(), startOnly.getColorEnd());
    }

    /** Problems say where they are and what was expected. */
    @Test
    void testErrorsSayWhere() {
        assertTrue(failure("colour: [1, 1, 1, 1]").contains("unknown key 'colour'"));
        assertTrue(failure("lifetime: [2, 1]").contains("lifetime:"));
        assertTrue(failure("lifetime: [1, 2, 3]").contains("lifetime: expected"));
        assertTrue(failure("spread: 200").contains("spread: must be from"));
        assertTrue(failure("rate: -1").contains("rate:"));
        assertTrue(failure("rate: fast").contains("rate: expected a number"));
        assertTrue(failure("direction: [0, 1]").contains("direction: expected [x, y, z]"));
        assertTrue(failure("color:\n  start: [1, 1, 1]\n").contains("color.start: expected"));
        assertTrue(failure("color:\n  middle: [1, 1, 1, 1]\n").contains("unknown key 'middle'"));
        assertTrue(failure("color: [1, 1, 1, 2]").contains("opacity must be at most 1"));
        assertTrue(failure("- 1\n- 2\n").contains("expected keys"));
        assertTrue(failure("rate: [").contains("not valid YAML"));
        assertTrue(failure("lit: yes please").contains("lit: expected true or false"));
        assertTrue(failure("anisotropy: 1").contains("anisotropy: must be from"));
        assertTrue(
                failure("collision: splat")
                        .contains("collision: expected one of none, bounce, stick, die"));
        assertTrue(failure("bounce: 2").contains("bounce: must be from"));
        assertTrue(failure("spawnBox: [1, -1, 1]").contains("spawnBox: must not be negative"));
        assertTrue(failure("splash: 3").contains("splash: expected an effect's keys"));
        assertTrue(failure("splash:\n  splash:\n    rate: 1\n").contains("can't have a splash"));
        assertTrue(failure("splash:\n  rait: 1\n").contains("splash: unknown key 'rait'"));
        assertTrue(failure("splashCount: 100").contains("splashCount: must be from"));
        assertTrue(failure("follow: 2").contains("follow: must be from"));
    }

    /** Every sample writes out and reads back the same. */
    @Test
    void testRoundTrip() {
        for (ParticleSamples.Sample sample : ParticleSamples.ALL) {
            final ParticleEffect original = sample.effect();
            final ParticleEffect copy = read(ParticleEffectLoader.write(original));
            assertMatches(original, copy, sample.name());
        }
    }

    /**
     * Check an effect read back matches what was written, its splash too.
     *
     * @param original What was written.
     * @param copy What was read back.
     * @param name The sample's name, for messages.
     */
    private static void assertMatches(ParticleEffect original, ParticleEffect copy, String name) {
        assertEquals(original.getRate(), copy.getRate(), name);
        assertEquals(original.getBurst(), copy.getBurst(), name);
        assertEquals(original.getLifetimeMin(), copy.getLifetimeMin(), name);
        assertEquals(original.getLifetimeMax(), copy.getLifetimeMax(), name);
        assertEquals(original.getSpeedMin(), copy.getSpeedMin(), name);
        assertEquals(original.getSpeedMax(), copy.getSpeedMax(), name);
        assertEquals(original.getDirection(), copy.getDirection(), name);
        assertEquals(original.getSpread(), copy.getSpread(), TOLERANCE, name);
        assertEquals(original.getSpawnRadius(), copy.getSpawnRadius(), name);
        assertEquals(original.getAcceleration(), copy.getAcceleration(), name);
        assertEquals(original.getDrag(), copy.getDrag(), name);
        assertEquals(original.getSizeStart(), copy.getSizeStart(), name);
        assertEquals(original.getSizeEnd(), copy.getSizeEnd(), name);
        assertEquals(original.getColorStart(), copy.getColorStart(), name);
        assertEquals(original.getColorEnd(), copy.getColorEnd(), name);
        assertEquals(original.getEmissive(), copy.getEmissive(), name);
        assertEquals(original.isLit(), copy.isLit(), name);
        assertEquals(original.getAnisotropy(), copy.getAnisotropy(), name);
        assertEquals(original.getCollision(), copy.getCollision(), name);
        assertEquals(original.getBounce(), copy.getBounce(), name);
        assertEquals(original.getFriction(), copy.getFriction(), name);
        assertEquals(original.isVolumetric(), copy.isVolumetric(), name);
        assertEquals(original.getSpawnBox(), copy.getSpawnBox(), name);
        assertEquals(original.isAroundCamera(), copy.isAroundCamera(), name);
        assertEquals(original.getStretch(), copy.getStretch(), name);
        assertEquals(original.getSplashCount(), copy.getSplashCount(), name);
        assertEquals(original.getFollow(), copy.getFollow(), name);
        assertEquals(original.getInheritVelocity(), copy.getInheritVelocity(), name);
        assertEquals(original.getSplash() == null, copy.getSplash() == null, name);
        if (original.getSplash() != null) {
            assertMatches(original.getSplash(), copy.getSplash(), name + " splash");
        }
    }

    /** The writer writes every key the loader knows, and only those. */
    @Test
    void testWriterUsesTheKeys() {
        final Object document =
                new Yaml(new SafeConstructor(new LoaderOptions()))
                        .load(ParticleEffectLoader.write(ParticleSamples.RAIN));
        final Map<?, ?> root = (Map<?, ?>) document;
        assertEquals(ParticleEffectLoader.KEYS, root.keySet());
        // Without a splash, it is left out
        final Map<?, ?> fire =
                (Map<?, ?>)
                        new Yaml(new SafeConstructor(new LoaderOptions()))
                                .load(ParticleEffectLoader.write(ParticleSamples.FIRE));
        assertFalse(fire.containsKey(ParticleEffectLoader.SPLASH));
        final Map<?, ?> color = (Map<?, ?>) root.get(ParticleEffectLoader.COLOR);
        assertTrue(color.containsKey(ParticleEffectLoader.START));
        assertTrue(color.containsKey(ParticleEffectLoader.END));
    }
}
