package com.ikalagaming.graphics;

import com.ikalagaming.graphics.scene.EmitterRegistry;
import com.ikalagaming.graphics.scene.ParticleClock;
import com.ikalagaming.graphics.scene.ParticleEffect;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3dc;

import javax.annotation.Nullable;

/**
 * Places particle emitters in the scene for one plugin: smoke, sparks, dust and the like, simulated
 * and drawn on the GPU through the transparency passes. Every emitter placed here is owned by the
 * plugin's {@link GraphicsContext}, and is removed automatically when the plugin unloads if it
 * wasn't stopped before then; particles it already spawned live out their lives. A plugin can only
 * change or stop its own emitters; anything else is ignored with a warning.
 *
 * <p>Everything here is safe from any thread, and changes show from the next frame.
 *
 * <pre>
 * EmitterHandle fire = graphics.particles().spawn(sparks, torchTop);
 * graphics.particles().move(fire, newTop);
 * graphics.particles().stop(fire);
 * </pre>
 *
 * @see GraphicsContext#particles()
 * @see ParticleEffect
 */
@Slf4j
public final class Particles {

    /** The context these emitters belong to. */
    private final GraphicsContext context;

    /**
     * Create the particles API for a context.
     *
     * @param context The owning context.
     */
    Particles(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /**
     * Place an emitter. It spawns its effect's burst on the next frame and its rate from then on.
     *
     * @param effect What it spawns.
     * @param position Where it is, in the world.
     * @return Its handle, or null if the scene already holds {@link EmitterRegistry#MAX_EMITTERS}.
     * @throws IllegalStateException If graphics isn't running.
     */
    public @Nullable EmitterHandle spawn(
            @NonNull ParticleEffect effect, @NonNull Vector3dc position) {
        final EmitterHandle handle = registry().add(owner(), effect, position);
        if (handle == null) {
            log.warn(
                    "{} placed a particle emitter past the scene's {}, which isn't drawn",
                    owner(),
                    EmitterRegistry.MAX_EMITTERS);
        }
        return handle;
    }

    /**
     * Move an emitter this plugin owns. Particles it already spawned stay where they are.
     *
     * @param handle The emitter's handle.
     * @param position Where it is now, in the world.
     */
    public void move(@Nullable EmitterHandle handle, @NonNull Vector3dc position) {
        final EmitterRegistry registry = registryOrNull();
        if (registry != null && !registry.move(owner(), handle, position)) {
            ignored(registry, handle, "move");
        }
    }

    /**
     * Change what an emitter this plugin owns spawns from now on.
     *
     * @param handle The emitter's handle.
     * @param effect What it spawns now.
     */
    public void setEffect(@Nullable EmitterHandle handle, @NonNull ParticleEffect effect) {
        final EmitterRegistry registry = registryOrNull();
        if (registry != null && !registry.setEffect(owner(), handle, effect)) {
            ignored(registry, handle, "change");
        }
    }

    /**
     * Spawn extra particles from an emitter this plugin owns, all at once on the next frame.
     *
     * @param handle The emitter's handle.
     * @param particles How many.
     */
    public void burst(@Nullable EmitterHandle handle, int particles) {
        final EmitterRegistry registry = registryOrNull();
        if (particles > 0 && registry != null && !registry.burst(owner(), handle, particles)) {
            ignored(registry, handle, "burst");
        }
    }

    /**
     * Remove an emitter this plugin owns. Its particles live out their lives. The handle, and any
     * copies of it, become stale. Nothing happens if the handle is null or already stale.
     *
     * @param handle The emitter's handle.
     */
    public void stop(@Nullable EmitterHandle handle) {
        final EmitterRegistry registry = registryOrNull();
        if (registry != null && !registry.remove(owner(), handle)) {
            ignored(registry, handle, "stop");
        }
    }

    /**
     * Whether a handle still refers to an emitter.
     *
     * @param handle The handle to check.
     * @return False if the handle is null or stale.
     */
    public boolean isValid(@Nullable EmitterHandle handle) {
        final EmitterRegistry registry = registryOrNull();
        return registry != null && registry.isValid(handle);
    }

    /**
     * The scene's particle time, shared by every plugin: pausing, stepping, slowing and clearing
     * particles, for editors that look at effects closely, and the live particle count.
     *
     * @return The clock.
     * @throws IllegalStateException If graphics isn't running.
     */
    public ParticleClock clock() {
        return scene().getParticleClock();
    }

    /**
     * The key this plugin's emitters are owned under.
     *
     * @return The owner key.
     */
    private String owner() {
        return context.getOwnerKey();
    }

    /**
     * Warn when a change to another plugin's emitter was ignored. Stale handles are ignored
     * quietly.
     *
     * @param registry The registry.
     * @param handle The handle.
     * @param action What was attempted, for the message.
     */
    private void ignored(
            @NonNull EmitterRegistry registry,
            @Nullable EmitterHandle handle,
            @NonNull String action) {
        final String owner = registry.ownerOf(handle);
        if (owner != null && !owner.equals(owner())) {
            log.warn(
                    "{} tried to {} a particle emitter owned by {}, ignoring it",
                    owner(),
                    action,
                    owner);
        }
    }

    /**
     * The scene's emitter registry, if graphics is running.
     *
     * @return The registry, or null.
     */
    private static EmitterRegistry registryOrNull() {
        final Scene scene = GraphicsManager.getScene();
        return scene == null ? null : scene.getEmitterRegistry();
    }

    /**
     * The scene's emitter registry.
     *
     * @return The registry.
     * @throws IllegalStateException If graphics isn't running.
     */
    private static EmitterRegistry registry() {
        return scene().getEmitterRegistry();
    }

    /**
     * The scene.
     *
     * @return The scene.
     * @throws IllegalStateException If graphics isn't running.
     */
    private static Scene scene() {
        final Scene scene = GraphicsManager.getScene();
        if (scene == null) {
            throw new IllegalStateException("Graphics is not running");
        }
        return scene;
    }
}
