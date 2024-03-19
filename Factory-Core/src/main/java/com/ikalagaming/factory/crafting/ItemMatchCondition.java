package com.ikalagaming.factory.crafting;

import com.ikalagaming.factory.item.ItemStack;

import lombok.NonNull;

import java.util.Objects;

/** Used to check if an ingredient matches a recipe. */
@FunctionalInterface
public interface ItemMatchCondition {
    /**
     * Check for an exact match. The tags, material, KVT data, everything has to be exactly the
     * same.
     */
    ItemMatchCondition EXACT = (recipe, actual) -> recipe.getItemStack().equals(actual);

    /** Only check the item name. */
    ItemMatchCondition NAME_ONLY =
            (recipe, actual) ->
                    Objects.equals(
                            recipe.getItemStack().getItem().getName(), actual.getItem().getName());

    boolean matches(@NonNull InputItem recipeItem, @NonNull ItemStack actualItem);
}
