package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.Model;

import lombok.NonNull;

/**
 * A handle to something placed in the scene: one copy of a model at a position. Holding a handle
 * does not keep the instance alive: it is removed explicitly through {@link
 * Instances#remove(InstanceHandle)}, or when the plugin that placed it is unloaded. A handle whose
 * instance was removed is stale, and changing it does nothing.
 *
 * @param slot The instance's slot in the renderer's instance table.
 * @param generation How many times the slot had been freed when this instance took it. A stale
 *     handle has an older generation than the instance currently in the slot.
 * @param model The model that is placed.
 * @see Instances
 */
public record InstanceHandle(int slot, int generation, @NonNull Model model) {

    @Override
    public boolean equals(Object other) {
        // The slot and generation identify an instance; the model only describes it
        return other instanceof InstanceHandle handle
                && slot == handle.slot
                && generation == handle.generation;
    }

    @Override
    public int hashCode() {
        return 31 * slot + generation;
    }
}
