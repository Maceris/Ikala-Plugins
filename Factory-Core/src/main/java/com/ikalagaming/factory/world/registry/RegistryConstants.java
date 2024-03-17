package com.ikalagaming.factory.world.registry;

import lombok.NonNull;

/** Constants used by the various registries. */
public class RegistryConstants {

    /** The regex pattern that matches valid mod names. */
    public static final String MOD_NAME_FORMAT = "[a-z0-9][a-z0-9_.-]{0,31}";

    /** The regex pattern that matches valid resource (block, item) names. */
    public static final String RESOURCE_NAME_FORMAT = "[a-z0-9][a-z0-9_.-]{0,63}";

    /** Used to separate the mod name and resource name in the fully qualified format. */
    public static final String NAME_SEPARATOR = ":";

    /**
     * The regex pattern that matches the fully qualified resource name format, which combines mod
     * and resource (block, item) name.
     */
    public static final String FULLY_QUALIFIED_NAME_FORMAT =
            MOD_NAME_FORMAT + NAME_SEPARATOR + RESOURCE_NAME_FORMAT;

    /**
     * Combine the mod and resource name into the fully qualified format.
     *
     * @param modName The name of the mod the resource belongs to.
     * @param resourceName The name of the resource.
     * @return The fully qualified name.
     */
    public static String combineName(@NonNull String modName, @NonNull String resourceName) {
        return String.format("%s%s%s", modName, NAME_SEPARATOR, resourceName);
    }

    private RegistryConstants() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
