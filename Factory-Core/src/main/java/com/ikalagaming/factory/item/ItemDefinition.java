package com.ikalagaming.factory.item;

import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.NodeType;
import com.ikalagaming.factory.registry.RegistryConstants;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines an item.
 *
 * @param modName The name of the mod the item belongs to, used for the fully qualified name.
 * @param itemName The name of the item, at least unique within the mod.
 * @param material The material that the item is primarily composed of. May be null if we don't care
 *     or can't reasonably define that.
 * @param tags Tags that are applied to the item by default.
 * @param attributes A list of {@link Attribute attributes} that we expect the item to have.
 */
public record ItemDefinition(
        @NonNull String modName,
        @NonNull String itemName,
        String material,
        @NonNull List<String> tags,
        @NonNull List<Attribute> attributes) {

    public ItemDefinition(
            @NonNull String modName,
            @NonNull String itemName,
            String material,
            @NonNull List<String> tags) {
        this(modName, itemName, material, tags, new ArrayList<>());
    }

    public ItemDefinition(@NonNull String modName, @NonNull String itemName, String material) {
        this(modName, itemName, material, new ArrayList<>(), new ArrayList<>());
    }

    public ItemDefinition(@NonNull String modName, @NonNull String itemName) {
        this(modName, itemName, null, new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Values that we expect to exist on the {@link com.ikalagaming.factory.kvt.KVT KVT} data.
     *
     * @param name The key name, using dot notation for specifying deeper structures.
     * @param type The type of the key.
     */
    public record Attribute(@NonNull String name, @NonNull NodeType type) {}

    public Item instantiate() {
        return new Item(
                RegistryConstants.combineName(modName, itemName), material, tags, new Node());
    }
}
