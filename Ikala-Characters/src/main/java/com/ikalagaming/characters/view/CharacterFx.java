package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.AnimationSet;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Fx;
import com.ikalagaming.characters.definition.FxUse;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.pose.PoseSolver;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.graphics.EffectHandle;
import com.ikalagaming.graphics.EmitterHandle;
import com.ikalagaming.graphics.Particles;
import com.ikalagaming.graphics.scene.ParticleEffect;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A character's effects as particle emitters: each {@link FxUse} from its worn parts and its
 * design, placed on its bone or socket and moved there every time the character is posed. A palette
 * channel's color tints an effect, and its scale, rate and intensity tune it, on a copy of the
 * loaded effect, so the graphics plugin only sees plain values. When the effect's file is reloaded,
 * the copy is made again.
 *
 * <p>An effect shows when its {@link FxUse#when} state holds, like {@value FxUse#MOVING}; others
 * are stopped until it does. Animation events named {@code fx:} and an effect ID burst that effect
 * once at the event's bone, from an emitter per effect that spawns nothing on its own.
 *
 * <p>{@link #update} runs on the thread that poses the character; everything is safe from any
 * thread.
 *
 * @author Ches Burks
 */
@Slf4j
public final class CharacterFx implements AutoCloseable {
    /** An effect on a bone. */
    private static final class Placed {
        /** The loaded effect. */
        final EffectHandle effect;

        /** The bone it follows. */
        final int bone;

        /** Its offset in the bone's space. */
        final Vector3f offset;

        /** How it is tuned. */
        final FxUse use;

        /** The linear color that tints it, or null for its own colors. */
        final Vector3f tint;

        /** Its emitter, once spawned. */
        EmitterHandle emitter;

        /** The loaded effect its emitter's copy was made from. */
        ParticleEffect applied;

        /** Whether spawning failed, so it isn't tried every pose. */
        boolean failed;

        /**
         * Place an effect.
         *
         * @param effect The loaded effect.
         * @param bone The bone it follows.
         * @param offset Its offset in the bone's space.
         * @param use How it is tuned.
         * @param tint The linear color that tints it, or null.
         */
        Placed(EffectHandle effect, int bone, Vector3f offset, FxUse use, Vector3f tint) {
            this.effect = effect;
            this.bone = bone;
            this.offset = offset;
            this.use = use;
            this.tint = tint;
        }
    }

    /**
     * How many particles an event bursts from an effect with no burst of its own, per particle a
     * second it spawns.
     */
    private static final float EVENT_BURST_SECONDS = 0.2f;

    /** The particles API. */
    private final Particles particles;

    /** The loaded definitions, for events' effects. */
    private final CharacterRegistry registry;

    /** Loads events' effects. */
    private final FxEffects effects;

    /** The character's skeleton, for events' bones. */
    private final Skeleton skeleton;

    /** The states that hold, like moving or a toggle. */
    private final Set<String> states = new HashSet<>();

    /** Events waiting for the next update, which knows where the bones are. */
    private final List<AnimationSet.Event> events = new ArrayList<>();

    /** An emitter per effect that events burst, spawning nothing on its own. */
    private final Map<EffectHandle, EmitterHandle> bursters = new HashMap<>();

    /** The placed effects. */
    private final List<Placed> placed = new ArrayList<>();

    /** Scratch for an emitter's position from the character's root. */
    private final Vector3f local = new Vector3f();

    /** Scratch for an emitter's position in the world. */
    private final Vector3d world = new Vector3d();

    /** Guards the emitters, so closing doesn't race a pose. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Whether this was closed. */
    private boolean closed;

    /**
     * Place a character's effects. Effects whose definition, file, bone or socket can't be found
     * are left out with a warning. Emitters spawn on the first {@link #update}.
     *
     * @param uses The effects, from the worn parts and then the design.
     * @param parts The worn parts, which may move sockets.
     * @param plan The character's plan.
     * @param skeleton The character's skeleton.
     * @param colors The design's colors by palette channel, sRGB.
     * @param registry The loaded definitions.
     * @param effects Loads the effects.
     */
    public CharacterFx(
            @NonNull List<FxUse> uses,
            @NonNull Collection<Part> parts,
            @NonNull BodyPlan plan,
            @NonNull Skeleton skeleton,
            @NonNull Map<String, Color> colors,
            @NonNull CharacterRegistry registry,
            @NonNull FxEffects effects) {
        particles = effects.particles();
        this.registry = registry;
        this.effects = effects;
        this.skeleton = skeleton;
        for (FxUse use : uses) {
            Fx fx = registry.get(Fx.class, use.fx());
            if (fx == null) {
                log.warn("No loaded effect {}", use.fx());
                continue;
            }
            EffectHandle effect = effects.effect(fx, registry);
            if (effect == null) {
                continue;
            }
            Vector3f offset = toVector(use.offsetOrZero());
            String boneName = use.bone();
            if (use.socket() != null) {
                boneName = socketBone(use.socket(), parts, plan, skeleton, offset);
            }
            int bone = boneName == null ? -1 : skeleton.index(boneName);
            if (bone < 0) {
                log.warn("{} is on {}, which the character doesn't have", use.fx(), boneName);
                continue;
            }
            Color color = use.color() == null ? null : colors.get(use.color());
            placed.add(new Placed(effect, bone, offset, use, color == null ? null : linear(color)));
        }
    }

    /**
     * How many effects were placed.
     *
     * @return The count.
     */
    public int size() {
        return placed.size();
    }

    /**
     * Turn a state on or off, which shows or hides the effects shown in it.
     *
     * @param state The state, like {@value FxUse#MOVING} or a toggle.
     * @param holds Whether it holds.
     */
    public void setState(@NonNull String state, boolean holds) {
        lock.lock();
        try {
            if (holds) {
                states.add(state);
            } else {
                states.remove(state);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Burst an animation event's effect at the next update, if it is an {@code fx:} event.
     *
     * @param event The event.
     */
    public void event(@NonNull AnimationSet.Event event) {
        if (!event.event().startsWith(AnimationSet.Event.FX_PREFIX)) {
            return;
        }
        lock.lock();
        try {
            if (!closed) {
                events.add(event);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Move every emitter to its bone, spawning those not yet spawned and stopping those whose state
     * doesn't hold, make the copies of effects whose files were reloaded again, and burst events'
     * effects.
     *
     * @param solver The solver, holding the character's latest pose.
     * @param position Where the character's root stands.
     * @param facing Which way the character faces.
     */
    public void update(
            @NonNull PoseSolver solver, @NonNull Vector3dc position, @NonNull Quaternionfc facing) {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            for (Placed effect : placed) {
                if (effect.failed) {
                    continue;
                }
                if (!effect.use.shownIn(states)) {
                    if (effect.emitter != null) {
                        particles.stop(effect.emitter);
                        effect.emitter = null;
                        effect.applied = null;
                    }
                    continue;
                }
                ParticleEffect base = particles.effectOf(effect.effect);
                if (base == null) {
                    continue;
                }
                solver.model(effect.bone).transformPosition(effect.offset, local);
                facing.transform(local);
                world.set(position).add(local.x, local.y, local.z);
                if (effect.emitter == null) {
                    effect.emitter = particles.spawn(tune(base, effect.use, effect.tint), world);
                    effect.applied = base;
                    effect.failed = effect.emitter == null;
                    continue;
                }
                if (base != effect.applied) {
                    effect.applied = base;
                    particles.setEffect(effect.emitter, tune(base, effect.use, effect.tint));
                }
                particles.move(effect.emitter, world);
            }
            for (AnimationSet.Event event : events) {
                burst(event, solver, position, facing);
            }
            events.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Burst an event's effect at its bone.
     *
     * @param event The event.
     * @param solver The solver, holding the latest pose.
     * @param position Where the character's root stands.
     * @param facing Which way the character faces.
     */
    private void burst(
            AnimationSet.Event event, PoseSolver solver, Vector3dc position, Quaternionfc facing) {
        Fx fx;
        try {
            fx = registry.get(Fx.class, event.effect());
        } catch (IllegalArgumentException e) {
            return;
        }
        EffectHandle effect = fx == null ? null : effects.effect(fx, registry);
        ParticleEffect base = particles.effectOf(effect);
        if (base == null) {
            return;
        }
        int bone = event.bone() == null ? 0 : Math.max(0, skeleton.index(event.bone()));
        solver.model(bone).getTranslation(local);
        facing.transform(local);
        world.set(position).add(local.x, local.y, local.z);
        EmitterHandle emitter = bursters.get(effect);
        if (emitter == null || !particles.isValid(emitter)) {
            emitter = particles.spawn(base.toBuilder().rate(0).burst(0).build(), world);
            if (emitter == null) {
                return;
            }
            bursters.put(effect, emitter);
        } else {
            particles.teleport(emitter, world);
        }
        int count =
                base.getBurst() > 0
                        ? base.getBurst()
                        : Math.max(1, Math.round(base.getRate() * EVENT_BURST_SECONDS));
        particles.burst(emitter, count);
    }

    /**
     * Tune a copy of an effect: scale its size, speed, pull and spawn area, its rate and burst, and
     * its glow, and tint its colors.
     *
     * @param base The effect.
     * @param use How to tune it.
     * @param tint The linear color to tint it, or null for its own colors.
     * @return The tuned copy.
     */
    public static ParticleEffect tune(
            @NonNull ParticleEffect base, @NonNull FxUse use, Vector3f tint) {
        float scale = use.scale();
        // Unlit particles show their color as it is, so that is how they glow brighter
        float brightness = base.isLit() ? 1 : use.intensity();
        return base.toBuilder()
                .rate(base.getRate() * use.rate())
                .burst(Math.round(base.getBurst() * use.rate()))
                .speedMin(base.getSpeedMin() * scale)
                .speedMax(base.getSpeedMax() * scale)
                .spawnRadius(base.getSpawnRadius() * scale)
                .spawnBox(new Vector3f(base.getSpawnBox()).mul(scale))
                .acceleration(new Vector3f(base.getAcceleration()).mul(scale))
                .sizeStart(base.getSizeStart() * scale)
                .sizeEnd(base.getSizeEnd() * scale)
                .emissive(base.getEmissive() * use.intensity())
                .colorStart(tinted(base.getColorStart(), tint, brightness))
                .colorEnd(tinted(base.getColorEnd(), tint, brightness))
                .build();
    }

    /**
     * A color times a tint and a brightness, keeping its opacity.
     *
     * @param color The color.
     * @param tint The tint, or null for none.
     * @param brightness What to multiply the color by.
     * @return The new color.
     */
    private static Vector4fc tinted(Vector4fc color, Vector3f tint, float brightness) {
        Vector4f result = new Vector4f(color);
        if (tint != null) {
            result.mul(tint.x, tint.y, tint.z, 1);
        }
        return result.mul(brightness, brightness, brightness, 1);
    }

    /**
     * The bone a socket is on, from the plan or moved by a worn part, adding any move's offset.
     * Where several parts move the socket, the one on the bone furthest from the root wins, so a
     * hand stump beats an arm stump.
     *
     * @param socket The socket.
     * @param parts The worn parts.
     * @param plan The plan.
     * @param skeleton The skeleton.
     * @param offset The effect's offset, which a move's offset is added to.
     * @return The bone's name, or null if the plan has no such socket.
     */
    private static String socketBone(
            String socket,
            Collection<Part> parts,
            BodyPlan plan,
            Skeleton skeleton,
            Vector3f offset) {
        Part.SocketMove best = null;
        int bestDepth = -1;
        for (Part part : parts) {
            Part.SocketMove move = part.sockets().get(socket);
            if (move == null) {
                continue;
            }
            int depth = depth(skeleton, skeleton.index(move.bone()));
            if (depth > bestDepth) {
                best = move;
                bestDepth = depth;
            }
        }
        if (best != null) {
            if (best.offset() != null) {
                offset.add(toVector(best.offset()));
            }
            return best.bone();
        }
        BodyPlan.Socket planSocket = plan.socket(socket);
        return planSocket == null ? null : planSocket.bone();
    }

    /**
     * How many bones are above a bone.
     *
     * @param skeleton The skeleton.
     * @param bone The bone, or -1.
     * @return Its depth, or -1 for no bone.
     */
    private static int depth(Skeleton skeleton, int bone) {
        int depth = -1;
        for (int b = bone; b >= 0; b = skeleton.parent(b)) {
            ++depth;
        }
        return depth;
    }

    /**
     * A vector as JOML's.
     *
     * @param vector The vector.
     * @return The copy.
     */
    private static Vector3f toVector(Vec3 vector) {
        return new Vector3f(vector.x(), vector.y(), vector.z());
    }

    /**
     * An sRGB color in linear light.
     *
     * @param color The color.
     * @return Its linear red, green and blue.
     */
    static Vector3f linear(@NonNull Color color) {
        return new Vector3f(
                decode(color.red() / 255f),
                decode(color.green() / 255f),
                decode(color.blue() / 255f));
    }

    /**
     * Decode a value from the sRGB curve.
     *
     * @param encoded The encoded value, 0 to 1.
     * @return The linear value.
     */
    private static float decode(float encoded) {
        return encoded <= 0.040_45f
                ? encoded / 12.92f
                : (float) Math.pow((encoded + 0.055) / 1.055, 2.4);
    }

    /** Stop every emitter. Particles already spawned live out their lives. */
    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            placed.forEach(effect -> particles.stop(effect.emitter));
            bursters.values().forEach(particles::stop);
            bursters.clear();
            events.clear();
        } finally {
            lock.unlock();
        }
    }
}
