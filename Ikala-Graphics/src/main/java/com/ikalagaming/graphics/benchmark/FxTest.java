package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.EmitterHandle;
import com.ikalagaming.graphics.Particles;
import com.ikalagaming.graphics.scene.ParticleEffect;

import lombok.NonNull;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Emitters that move as a character's effects do, for the gate's {@code .fxTest=x,y,z} option: a
 * blade swung round a circle as fast as a sword's tip, leaving a trail swept along its line between
 * frames; sparks thrown off its tip with its velocity; an aura going round the other way whose
 * particles go along with it; and a bar of particles spinning in place, its spawn box and direction
 * turned with it. Each spins about a point every frame.
 */
public final class FxTest {

    /** How far from the middle the blade's base and the aura go round, in meters. */
    private static final double RADIUS = 2.5;

    /** How long the blade is, in meters. */
    private static final double BLADE_LENGTH = 1.2;

    /** How many turns a second they make: about 20 m/s at the blade's tip. */
    private static final double TURNS_PER_SECOND = 0.9;

    /** How high above the given point everything is, in meters. */
    private static final double HEIGHT = 1.5;

    /** How far up the spinning bar is from the given point, in meters. */
    private static final double BAR_HEIGHT = 4;

    /** Gravity, in meters a second squared. */
    private static final Vector3f GRAVITY = new Vector3f(0, -9.81f, 0);

    /** A pale blue glowing trail, which stays where it is spawned and fades fast. */
    private static final ParticleEffect TRAIL =
            ParticleEffect.builder()
                    .rate(4000)
                    .lifetime(0.25f, 0.35f)
                    .speed(0, 0)
                    .size(0.06f, 0.01f)
                    .colors(new Vector4f(0.4f, 0.7f, 1, 0.8f), new Vector4f(0.2f, 0.4f, 1, 0))
                    .emissive(4)
                    .lit(false)
                    .build();

    /** Orange sparks thrown off the tip, starting with its velocity. */
    private static final ParticleEffect TIP_SPARKS =
            ParticleEffect.builder()
                    .rate(200)
                    .lifetime(0.4f, 0.8f)
                    .speed(0.5f, 1.5f)
                    .spread((float) Math.PI)
                    .acceleration(GRAVITY)
                    .size(0.04f, 0.01f)
                    .colors(new Vector4f(1, 0.6f, 0.2f, 1), new Vector4f(1, 0.2f, 0.05f, 0.5f))
                    .emissive(6)
                    .lit(false)
                    .stretch(0.02f)
                    .inheritVelocity(1)
                    .build();

    /** A purple aura whose motes go along with it as it moves. */
    private static final ParticleEffect AURA =
            ParticleEffect.builder()
                    .rate(300)
                    .lifetime(0.8f, 1.2f)
                    .speed(0.1f, 0.4f)
                    .spread((float) Math.PI)
                    .spawnRadius(0.4f)
                    .size(0.08f, 0)
                    .colors(new Vector4f(0.7f, 0.3f, 1, 0.9f), new Vector4f(0.5f, 0.2f, 1, 0))
                    .emissive(3)
                    .lit(false)
                    .follow(1)
                    .build();

    /** A thin bar of green particles shooting out along its length, turned with the emitter. */
    private static final ParticleEffect BAR =
            ParticleEffect.builder()
                    .rate(400)
                    .lifetime(0.5f, 0.7f)
                    .speed(1, 1.5f)
                    .direction(new Vector3f(1, 0, 0))
                    .spread(0.05f)
                    .spawnBox(new Vector3f(1.5f, 0.05f, 0.05f))
                    .size(0.05f, 0.02f)
                    .colors(new Vector4f(0.3f, 1, 0.4f, 0.9f), new Vector4f(0.1f, 1, 0.2f, 0))
                    .emissive(3)
                    .lit(false)
                    .build();

    /** The point everything goes round. */
    private final Vector3d middle = new Vector3d();

    /** The emitters: the trail, the sparks, the aura and the bar; null where the scene was full. */
    private final EmitterHandle trail;

    private final EmitterHandle sparks;
    private final EmitterHandle aura;
    private final EmitterHandle bar;

    /** When the emitters were placed, from {@link System#nanoTime()}. */
    private final long startNanos = System.nanoTime();

    /**
     * Place the emitters.
     *
     * @param particles The particles API.
     * @param at The point they go round, on the ground.
     */
    public FxTest(@NonNull Particles particles, @NonNull Vector3dc at) {
        middle.set(at).add(0, HEIGHT, 0);
        trail = particles.spawn(TRAIL, middle);
        sparks = particles.spawn(TIP_SPARKS, middle);
        aura = particles.spawn(AURA, middle);
        bar = particles.spawn(BAR, new Vector3d(middle).add(0, BAR_HEIGHT - HEIGHT, 0));
    }

    /**
     * Move the emitters for this frame.
     *
     * @param particles The particles API.
     */
    public void step(@NonNull Particles particles) {
        final double seconds = (System.nanoTime() - startNanos) / 1e9;
        final double angle = 2 * Math.PI * TURNS_PER_SECOND * seconds;
        final Vector3d way = new Vector3d(Math.cos(angle), 0, Math.sin(angle));
        final Vector3d base = new Vector3d(way).mul(RADIUS).add(middle);
        final Vector3d tip = new Vector3d(way).mul(RADIUS + BLADE_LENGTH).add(middle);
        particles.moveLine(trail, base, tip);
        particles.move(sparks, tip);
        particles.move(aura, new Vector3d(way).mul(-RADIUS).add(middle));
        particles.orient(bar, new Quaternionf().rotateY((float) -angle));
    }

    /**
     * The emitters placed, to stop when the run ends.
     *
     * @return Those the scene had room for.
     */
    public List<EmitterHandle> handles() {
        final List<EmitterHandle> handles = new ArrayList<>();
        for (EmitterHandle handle : new EmitterHandle[] {trail, sparks, aura, bar}) {
            if (handle != null) {
                handles.add(handle);
            }
        }
        return handles;
    }
}
