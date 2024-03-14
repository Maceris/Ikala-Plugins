package com.ikalagaming.factory.quest;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;

/**
 * The types of requirement that must be satisfied for a quest to be considered completed.
 *
 * @author Ches Burks
 */
public enum RequirementType {
    /** Break a specific type of block. */
    BREAK_BLOCK,
    /** The player just has to click on a checkbox on the quest. */
    CHECKBOX,
    /** The player has to craft a specific thing. */
    CRAFT,
    /** The player has to interact with a specific kind of entity. */
    INTERACT_ENTITY,
    /** The player has to reach a specific location (position, structure, biome, dimension). */
    LOCATION,
    /** The player has to have a certain item in their inventory. */
    RETRIEVE,
    /** The player has to use a specific kind of item. */
    USE_ITEM;

    private static String getLocalizationKey(@NonNull RequirementType type) {
        return switch (type) {
            case BREAK_BLOCK -> "REQUIREMENT_TYPE_BREAK_BLOCK";
            case CHECKBOX -> "REQUIREMENT_TYPE_CHECKBOX";
            case CRAFT -> "REQUIREMENT_TYPE_CRAFT";
            case INTERACT_ENTITY -> "REQUIREMENT_TYPE_INTERACT_ENTITY";
            case LOCATION -> "REQUIREMENT_TYPE_LOCATION";
            case RETRIEVE -> "REQUIREMENT_TYPE_RETRIEVE";
            case USE_ITEM -> "REQUIREMENT_TYPE_USE_ITEM";
        };
    }

    @Override
    public String toString() {
        return SafeResourceLoader.getString(
                getLocalizationKey(this), FactoryPlugin.getResourceBundle());
    }
}
