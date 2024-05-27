package com.ikalagaming.graphics.backend.vulkan;

/**
 * The set of queue families that we care about.
 *
 * @param graphics The graphics family.
 * @param present The present family.
 */
public record QueueFamilyIndices(int graphics, int present) {
    public static final int MISSING = -1;

    /**
     * Checks if all the indices are valid values.
     *
     * @return If all indices have values.
     */
    public boolean hasAllValues() {
        return graphics != MISSING && present != MISSING;
    }
}
