package com.ikalagaming.factory.item;

import com.ikalagaming.factory.kvt.NodeType;

import lombok.NonNull;

import java.util.List;

/**
 * Defines an item.
 *
 * @param modName The name of the mod the item belongs to, used for the fully qualified name.
 * @param itemName The name of the item, at least unique within the mod.
 * @param tags Tags that are applied to the item by default.
 * @param attributes A list of {@link Attribute attributes} that we expect the item to have.
 */
public record ItemDefinition(
        @NonNull String modName,
        @NonNull String itemName,
        @NonNull List<String> tags,
        @NonNull List<Attribute> attributes) {
    /**
     * Values that we expect to exist on the {@link com.ikalagaming.factory.kvt.KVT KVT} data.
     *
     * @param name The key name, using dot notation for specifying deeper structures.
     * @param type The type of the key.
     */
    public record Attribute(@NonNull String name, @NonNull NodeType type) {}
}
