package com.ikalagaming.graphics.backend.vulkan;

import java.util.Objects;

/**
 * The set of queue families that we care about.
 *
 * @param graphics The graphics family.
 * @param present The present family.
 * @param transfer The transfer family.
 * @param roomForSeparateTransferQueue We either have a dedicated graphics family for transfer, or
 *     at least a queue count large enough for a separate transfer queue in the graphics family.
 */
public record QueueFamilyIndices(
        int graphics, int present, int transfer, boolean roomForSeparateTransferQueue) {

    /** Indicates a missing index. */
    public static final int MISSING = -1;

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        QueueFamilyIndices o = (QueueFamilyIndices) other;
        return present == o.present && graphics == o.graphics && transfer == o.transfer;
    }

    @Override
    public int hashCode() {
        return Objects.hash(graphics, present, transfer);
    }

    @Override
    public String toString() {
        return "QueueFamilyIndices{"
                + "transfer="
                + transfer
                + ", present="
                + present
                + ", graphics="
                + graphics
                + '}';
    }

    /**
     * Checks if all the indices are valid values.
     *
     * @return If all indices have values.
     */
    public boolean hasAllValues() {
        return graphics != MISSING && present != MISSING && transfer != MISSING;
    }
}
