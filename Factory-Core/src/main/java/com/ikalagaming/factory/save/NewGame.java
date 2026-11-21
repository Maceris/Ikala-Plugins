package com.ikalagaming.factory.save;

import lombok.NonNull;

/**
 * The choices for a new game, as the player made them.
 *
 * @param name The name the player gave the save. Blank picks a default name.
 * @param worldType The world type for the first world, such as {@code lotomation:overworld}.
 * @param seedText The seed as typed: blank for a random seed, a whole number to use as is, or any
 *     other text, which is hashed into a seed.
 */
public record NewGame(@NonNull String name, @NonNull String worldType, @NonNull String seedText) {}
