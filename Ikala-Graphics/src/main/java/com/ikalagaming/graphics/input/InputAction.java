package com.ikalagaming.graphics.input;

import lombok.NonNull;

import java.util.regex.Pattern;

/**
 * Something a player does with a key, like jumping or opening chat, which they can bind to any key.
 * Plugins register their own through {@code GraphicsContext.registerAction}, and the Controls
 * screen lists them by category.
 *
 * @param id A namespaced ID like {@code lotomation:jump}, which settings save bindings under.
 * @param category The category to list it under, as shown to the player.
 * @param name What it does, as shown to the player.
 * @param defaultBinding The key it starts on.
 */
public record InputAction(
        @NonNull String id,
        @NonNull String category,
        @NonNull String name,
        @NonNull KeyBinding defaultBinding) {

    /** What an ID looks like. */
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /**
     * Check the ID.
     *
     * @param id The ID.
     * @param category The category.
     * @param name The name.
     * @param defaultBinding The default binding.
     * @throws IllegalArgumentException If the ID isn't namespaced lower case.
     */
    public InputAction {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Action IDs look like namespace:name in lower case, not " + id);
        }
    }
}
