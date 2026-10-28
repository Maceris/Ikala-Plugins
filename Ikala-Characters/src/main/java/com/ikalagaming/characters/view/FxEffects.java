package com.ikalagaming.characters.view;

import com.ikalagaming.characters.definition.Fx;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.graphics.EffectHandle;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Particles;
import com.ikalagaming.graphics.scene.ParticleEffectException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The graphics particle effects that {@link Fx} definitions name, loaded once each for a plugin and
 * shared by every character that wears them. Loaded effects follow their files while graphics hot
 * reload is on. Safe from any thread.
 *
 * @author Ches Burks
 */
@Slf4j
public final class FxEffects implements AutoCloseable {
    /** The plugin whose graphics context loads the effects. */
    private final String plugin;

    /** Loaded effects by file; null for a file that didn't load, so it isn't tried again. */
    private final Map<Path, EffectHandle> loaded = new HashMap<>();

    /** Guards the loaded effects. */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Start with nothing loaded.
     *
     * @param plugin The plugin whose graphics context loads the effects.
     */
    public FxEffects(@NonNull String plugin) {
        this.plugin = plugin;
    }

    /**
     * The particles API of the plugin's graphics context.
     *
     * @return The API.
     */
    public Particles particles() {
        return GraphicsManager.forPlugin(plugin).particles();
    }

    /**
     * The loaded effect a definition names, loading it the first time.
     *
     * @param fx The definition.
     * @param registry The loaded definitions, which find its file.
     * @return The effect, or null if its file isn't on disk or isn't an effect.
     */
    public EffectHandle effect(@NonNull Fx fx, @NonNull CharacterRegistry registry) {
        Path file = registry.asset(fx.id().namespace(), fx.effect());
        if (file == null) {
            log.warn("{} names {}, but its namespace has no folder on disk", fx.id(), fx.effect());
            return null;
        }
        lock.lock();
        try {
            if (loaded.containsKey(file)) {
                return loaded.get(file);
            }
            EffectHandle handle = null;
            try {
                handle = particles().loadEffect(file);
            } catch (ParticleEffectException | UncheckedIOException e) {
                log.warn("Couldn't load the effect {} for {}: {}", file, fx.id(), e.getMessage());
            }
            loaded.put(file, handle);
            return handle;
        } finally {
            lock.unlock();
        }
    }

    /** Unload every effect. Emitters spawned from them keep their last versions. */
    @Override
    public void close() {
        lock.lock();
        try {
            if (!GraphicsManager.forPlugin(plugin).isClosed()) {
                Particles particles = particles();
                loaded.values().forEach(particles::unloadEffect);
            }
            loaded.clear();
        } finally {
            lock.unlock();
        }
    }
}
