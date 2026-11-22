package com.ikalagaming.factory.save;

import lombok.NonNull;

import java.util.Locale;

/**
 * How a save is hosted, from its {@value Saves#SERVER_FILE}: whether the server opens a port, and
 * where. See the Save File Format design doc.
 *
 * @param listen Who can connect.
 * @param bind The address to listen on; empty for every address.
 * @param port The UDP port to listen on.
 */
public record Hosting(@NonNull Listen listen, @NonNull String bind, int port) {

    /** The port a server listens on unless its save says otherwise. */
    public static final int DEFAULT_PORT = 25_640;

    /** Single player: no port. */
    public static final Hosting SINGLE_PLAYER = new Hosting(Listen.OFF, "", DEFAULT_PORT);

    /** Who can connect. */
    public enum Listen {
        /** Nobody: single player. */
        OFF,
        /** Players on the local network. */
        LAN,
        /** Anyone who can reach the port. */
        PUBLIC;

        /**
         * The setting named in a save, by its lowercase name.
         *
         * @param name The name, like {@code "lan"}.
         * @param fallback The setting if the name is not one of these.
         * @return The setting.
         */
        public static Listen named(String name, @NonNull Listen fallback) {
            if (name == null) {
                return fallback;
            }
            for (Listen listen : values()) {
                if (listen.name()
                        .toLowerCase(Locale.ROOT)
                        .equals(name.trim().toLowerCase(Locale.ROOT))) {
                    return listen;
                }
            }
            return fallback;
        }
    }

    /**
     * Hosting settings.
     *
     * @param listen Who can connect.
     * @param bind The address to listen on; empty for every address.
     * @param port The UDP port to listen on.
     * @throws IllegalArgumentException If the port is out of range.
     */
    public Hosting {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("No port " + port);
        }
    }

    /**
     * These settings, listening publicly if they were off, as for a dedicated server.
     *
     * @return The settings.
     */
    public Hosting listening() {
        return listen == Listen.OFF ? new Hosting(Listen.PUBLIC, bind, port) : this;
    }

    /**
     * These settings on another port.
     *
     * @param newPort The port.
     * @return The settings.
     */
    public Hosting withPort(int newPort) {
        return new Hosting(listen == Listen.OFF ? Listen.LAN : listen, bind, newPort);
    }
}
