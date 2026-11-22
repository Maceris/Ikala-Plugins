package com.ikalagaming.factory.world;

import lombok.NonNull;

import java.util.Locale;
import java.util.Optional;

/**
 * How a block stops things moving through it. Shared data, not looks: the server and clients must
 * agree on it, since movement is predicted. Boxes on the 1/16 m grid come later, for slabs, posts
 * and the like.
 */
public enum Collision {
    /** The whole block is solid. */
    FULL,
    /** Nothing stops at it, like tufts of grass and fluids. */
    NONE;

    /**
     * The collision a definition names, by its lowercase name.
     *
     * @param name The name, like {@code "full"}.
     * @return The collision, or empty if the name isn't one.
     */
    public static Optional<Collision> named(@NonNull String name) {
        final String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (Collision collision : values()) {
            if (collision.name().toLowerCase(Locale.ROOT).equals(wanted)) {
                return Optional.of(collision);
            }
        }
        return Optional.empty();
    }
}
