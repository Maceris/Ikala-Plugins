package com.ikalagaming.graphics.scene;

/**
 * A particle effect file that can't be read as an effect: not YAML, an unknown key, or a value of
 * the wrong kind or out of range. The message says where, like {@code sparks.effect.yml: lifetime:
 * expected a number or [min, max]}.
 */
public class ParticleEffectException extends RuntimeException {

    /**
     * Report a problem.
     *
     * @param message What is wrong and where.
     */
    public ParticleEffectException(String message) {
        super(message);
    }

    /**
     * Report a problem with its cause.
     *
     * @param message What is wrong and where.
     * @param cause What went wrong underneath.
     */
    public ParticleEffectException(String message, Throwable cause) {
        super(message, cause);
    }
}
