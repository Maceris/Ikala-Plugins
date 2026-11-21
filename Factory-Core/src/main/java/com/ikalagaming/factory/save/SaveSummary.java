package com.ikalagaming.factory.save;

import lombok.NonNull;

import java.util.List;

/**
 * What a save's {@value Saves#SAVE_FILE} says about it, enough to list it and pick it.
 *
 * @param folder The save's folder name, which is also how it is found again.
 * @param name The name the player gave it.
 * @param created When it was created, in milliseconds since the epoch.
 * @param lastPlayed When it was last opened, in milliseconds since the epoch.
 * @param gameVersion The version of the game that last wrote it.
 * @param devBuild Whether a development build last wrote it.
 * @param worlds The folder names of its worlds, the first being where players start.
 * @param worldType The world type of the first world, or empty if it can't be read.
 * @param seed The seed of the first world, or zero if it can't be read.
 */
public record SaveSummary(
        @NonNull String folder,
        @NonNull String name,
        long created,
        long lastPlayed,
        @NonNull String gameVersion,
        boolean devBuild,
        @NonNull List<String> worlds,
        @NonNull String worldType,
        long seed) {

    /**
     * A summary, copying the world list.
     *
     * @param folder The save's folder name.
     * @param name The name the player gave it.
     * @param created When it was created.
     * @param lastPlayed When it was last opened.
     * @param gameVersion The version of the game that last wrote it.
     * @param devBuild Whether a development build last wrote it.
     * @param worlds The folder names of its worlds.
     * @param worldType The world type of the first world.
     * @param seed The seed of the first world.
     */
    public SaveSummary {
        worlds = List.copyOf(worlds);
    }
}
