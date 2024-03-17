package com.ikalagaming.factory.item;

import com.ikalagaming.factory.world.Material;

import lombok.NonNull;

import java.util.List;

/**
 * Represents an item in the game.
 *
 * @author Ches Burks
 */
public class Item {
    /** The regex pattern that matches valid mod names. */
    public static final String MOD_NAME_FORMAT = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,31}";

    /** The regex pattern that matches valid item names. */
    public static final String ITEM_NAME_FORMAT = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}";

    /** Used to separate the mod name and item name in the fully qualified format. */
    public static final String NAME_SEPARATOR = ":";

    /**
     * The regex pattern that matches the fully qualified item name format, which combines mod and
     * item name.
     */
    public static final String FULLY_QUALIFIED_NAME_FORMAT =
            MOD_NAME_FORMAT + NAME_SEPARATOR + ITEM_NAME_FORMAT;

    /**
     * Combine the mod and item name into the fully qualified format.
     *
     * @param modName The name of the mod the item belongs to.
     * @param itemName The name of the item.
     * @return The fully qualified name.
     */
    public static String combineName(@NonNull String modName, @NonNull String itemName) {
        return String.format("%s%s%s", modName, NAME_SEPARATOR, itemName);
    }

    private String modName;

    private String itemName;

    private Material material;

    private List<String> tags;
}
