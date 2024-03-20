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
    ItemMatchCondition MATCH_NAME =
            (recipe, actual) ->
                    Objects.equals(
                            recipe.getItemStack().getItem().getName(), actual.getItem().getName());

    /** Only check the material. */
    ItemMatchCondition MATCH_MATERIAL =
            (recipe, actual) ->
                    Objects.equals(
                            recipe.getItemStack().getItem().getMaterial(),
                            actual.getItem().getMaterial());

    /** Check that any of the tags in the condition are on the item. */
    ItemMatchCondition MATCH_ANY_TAGS =
            (recipe, actual) ->
                    recipe.getItemStack().getItem().getTags().stream()
                            .anyMatch(actual.getItem().getTags()::contains);

    /** Checks that all the tags in the condition are on the item. */
    ItemMatchCondition MATCH_ALL_TAGS =
            (recipe, actual) ->
                    actual.getItem()
                            .getTags()
                            .containsAll(recipe.getItemStack().getItem().getTags());

    boolean matches(@NonNull InputItem recipeItem, @NonNull ItemStack actualItem);
}
