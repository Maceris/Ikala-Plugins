package com.ikalagaming.factory.crafting;

import com.ikalagaming.factory.item.ItemStack;

import lombok.Getter;
import lombok.NonNull;

/** An item that is consumed and/or produced by a recipe. */
@Getter
public class IngredientItem extends Ingredient {

    /** The name of the item involved in the recipe. */
    @NonNull private final ItemStack itemStack;

    /**
     * Create a new item ingredient, generally only called directly for outputs.
     *
     * @param itemStack The item stack that is involved.
     * @see InputItem
     */
    public IngredientItem(@NonNull ItemStack itemStack) {
        super(IngredientType.ITEM);
        this.itemStack = itemStack;
    }
}
