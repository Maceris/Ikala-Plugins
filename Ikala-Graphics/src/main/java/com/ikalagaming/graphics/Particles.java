package com.ikalagaming.graphics;

import com.ikalagaming.graphics.scene.EffectLibrary;
import com.ikalagaming.graphics.scene.EffectSource;
import com.ikalagaming.graphics.scene.EmitterRegistry;
import com.ikalagaming.graphics.scene.ParticleClock;
import com.ikalagaming.graphics.scene.ParticleEffect;
import com.ikalagaming.graphics.scene.ParticleEffectException;
import com.ikalagaming.graphics.scene.ParticleEffectLoader;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3dc;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * Places particle emitters in the scene for one plugin: smoke, sparks, dust and the like, simulated
 * and drawn on the GPU through the transparency passes. Every emitter placed here is owned by the
 * plugin's {@link GraphicsContext}, and is removed automatically when the plugin unloads if it
 * wasn't stopped before then; particles it already spawned live out their lives. A plugin can only
 * change or stop its own emitters; anything else is ignored with a warning.
 *
 * <p>Effects are built in Java or loaded from YAML files (see {@link ParticleEffectLoader} for the
 * format). Emitters spawned from a loaded file follow it when it changes, while hot reload is on,
 * for editing effects while watching them.
 *
 * <p>Everything here is safe from any thread, and changes show from the next frame.
 *
 * <pre>
 * EffectHandle sparks = graphics.particles().loadEffect(Path.of("effects/sparks.effect.yml"));
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
     * Place an emitter of an effect this plugin loaded. It follows the file when it is reloaded.
     *
     * @param effect The loaded effect.
     * @param position Where it is, in the world.
     * @return Its handle, or null if the effect handle is stale or another plugin's, or the scene
     *     already holds {@link EmitterRegistry#MAX_EMITTERS}.
     * @throws IllegalStateException If graphics isn't running.
     */
    public @Nullable EmitterHandle spawn(
            @Nullable EffectHandle effect, @NonNull Vector3dc position) {
        final EffectSource source = scene().getEffectLibrary().sourceFor(owner(), effect);
        if (source == null) {
            log.warn("{} tried to spawn from a stale or unowned particle effect", owner());
            return null;
        }
        final EmitterHandle handle = registry().add(owner(), source, position);
        if (handle == null) {
            log.warn(
                    "{} placed a particle emitter past the scene's {}, which isn't drawn",
                    owner(),
                    EmitterRegistry.MAX_EMITTERS);
        }
        return handle;
    }

    /**
     * Load an effect from a YAML file, owned by this plugin until it unloads or the effect is
     * unloaded.
     *
     * @param file The file, see {@link ParticleEffectLoader}.
     * @return Its handle.
     * @throws ParticleEffectException If the file isn't an effect.
     * @throws UncheckedIOException If it can't be read.
     * @throws IllegalStateException If graphics isn't running.
     */
    public EffectHandle loadEffect(@NonNull Path file) {
        return scene().getEffectLibrary().load(owner(), file);
    }

    /**
     * The effect a loaded file holds now.
     *
     * @param effect The loaded effect's handle.
     * @return The effect, or null if the handle is stale.
     */
    public @Nullable ParticleEffect effectOf(@Nullable EffectHandle effect) {
        final Scene scene = GraphicsManager.getScene();
        return scene == null ? null : scene.getEffectLibrary().effectOf(effect);
    }

    /**
     * Why a loaded file's last edit didn't load, if it didn't, for showing while editing it. Its
     * emitters keep the last version that loaded.
     *
     * @param effect The loaded effect's handle.
     * @return The problem, or null if the last edit loaded or the handle is stale.
     */
    public @Nullable String effectError(@Nullable EffectHandle effect) {
        final Scene scene = GraphicsManager.getScene();
        return scene == null ? null : scene.getEffectLibrary().errorOf(effect);
    }

    /**
     * Forget an effect this plugin loaded. Its emitters keep its last version.
     *
     * @param effect The loaded effect's handle.
     */
    public void unloadEffect(@Nullable EffectHandle effect) {
        final Scene scene = GraphicsManager.getScene();
        if (scene == null) {
            return;
        }
        final EffectLibrary library = scene.getEffectLibrary();
        if (!library.unload(owner(), effect)) {
            final String owner = library.ownerOf(effect);
            if (owner != null && !owner.equals(owner())) {
                log.warn(
                        "{} tried to unload a particle effect loaded by {}, ignoring it",
                        owner(),
                        owner);
            }
        }
    }

    /**
     * Check loaded effect files for changes about twice a second, and reload changed ones, for
     * editing effects while watching them. Shared by every plugin; meant for tools like the editor.
     *
     * @param enabled Whether to check.
     * @throws IllegalStateException If graphics isn't running.
     */
    public void setHotReload(boolean enabled) {
        scene().getEffectLibrary().setHotReload(enabled);
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
