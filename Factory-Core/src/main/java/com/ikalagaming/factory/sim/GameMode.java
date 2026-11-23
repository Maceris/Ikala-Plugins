package com.ikalagaming.factory.sim;

import java.util.Locale;
import java.util.Optional;

/**
 * How a player plays, which decides how they move. Saved in their player file, and changed by the
 * server, for example with {@code /gamemode}.
 */
public enum GameMode {
    /** Walks, with gravity and blocks in the way. Health comes later. */
    SURVIVAL("s"),
    /** Like survival, but a double tap of jump toggles flight that hovers and still collides. */
    CREATIVE("c"),
    /** A free camera: flies through everything. */
    SPECTATOR("sp");

    /** The short name {@code /gamemode} takes. */
    private final String shortName;

    /**
     * A game mode.
     *
     * @param shortName The short name.
     */
    GameMode(String shortName) {
        this.shortName = shortName;
    }

    /**
     * The mode's name in files and commands.
     *
     * @return The name, in lower case.
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The mode with a name, short name or number, as {@code /gamemode} takes it: {@code creative},
     * {@code c} or {@code 1}; spectator's short name is {@code sp}.
     *
     * @param name The name.
     * @return The mode, if the name is one.
     */
    public static Optional<GameMode> named(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        final String text = name.strip().toLowerCase(Locale.ROOT);
        for (GameMode mode : values()) {
            if (mode.id().equals(text)
                    || mode.shortName.equals(text)
                    || Integer.toString(mode.ordinal()).equals(text)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    /**
     * The mode with an ordinal, as messages carry it.
     *
     * @param ordinal The ordinal.
     * @return The mode.
     * @throws IllegalArgumentException If there's no such mode.
     */
    public static GameMode byOrdinal(int ordinal) {
        final GameMode[] modes = values();
        if (ordinal < 0 || ordinal >= modes.length) {
            throw new IllegalArgumentException("Unknown game mode " + ordinal);
        }
        return modes[ordinal];
    }
}
