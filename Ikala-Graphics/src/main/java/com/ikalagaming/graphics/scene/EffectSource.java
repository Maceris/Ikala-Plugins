package com.ikalagaming.graphics.scene;

import lombok.NonNull;

/**
 * Where an emitter gets its effect: one that stays as it is, or a loaded file's, which changes when
 * the file is reloaded. Emitters keep their source, so a reload reaches every emitter spawned from
 * the file at once, and an unloaded file's emitters keep its last version.
 */
public final class EffectSource {

    /** The effect now. */
    private volatile ParticleEffect effect;

    /**
     * A source with an effect.
     *
     * @param effect The effect.
     */
    public EffectSource(@NonNull ParticleEffect effect) {
        this.effect = effect;
    }

    /**
     * The effect now.
     *
     * @return The effect.
     */
    public ParticleEffect get() {
        return effect;
    }

    /**
     * Change the effect, as when its file is reloaded.
     *
     * @param newEffect The new effect.
     */
    void set(@NonNull ParticleEffect newEffect) {
        effect = newEffect;
    }
}
