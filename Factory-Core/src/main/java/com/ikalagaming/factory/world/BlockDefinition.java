package com.ikalagaming.factory.world;

import lombok.NonNull;

import java.util.List;

/**
 * Defines a block.
 *
 * @param modName The name of the mod the item belongs to, used for the fully qualified name.
 * @param blockName The name of the block, at least unique within the mod.
 * @param material The material that the block is primarily composed of. May be null if we don't
 *     care or can't reasonably define that.
 * @param tags The tags that are applied to the block by default.
 * @param collision How the block stops movement.
 */
public record BlockDefinition(
        @NonNull String modName,
        @NonNull String blockName,
        String material,
        @NonNull List<String> tags,
        @NonNull Collision collision) {

    /**
     * Defines a solid block.
     *
     * @param modName The name of the mod the item belongs to.
     * @param blockName The name of the block.
     * @param material The material the block is mostly made of, or null.
     * @param tags The tags that are applied to the block by default.
     */
    public BlockDefinition(
            @NonNull String modName,
            @NonNull String blockName,
            String material,
            @NonNull List<String> tags) {
        this(modName, blockName, material, tags, Collision.FULL);
    }
}
