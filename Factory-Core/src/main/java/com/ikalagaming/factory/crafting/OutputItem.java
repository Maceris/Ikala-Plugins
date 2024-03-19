package com.ikalagaming.factory.crafting;

import com.ikalagaming.factory.item.ItemStack;

import lombok.Getter;
import lombok.NonNull;

/** An item that is produced by a recipe. */
@Getter
public class OutputItem extends Ingredient {

    /** The item produced by the recipe. */
    @NonNull private final ItemStack itemStack;

    /**
     * Create a new item ingredient.
     *
     * @param itemStack The item stack that is produced.
     */
    public OutputItem(@NonNull ItemStack itemStack) {
        super(IngredientType.ITEM);
        this.itemStack = itemStack;
    }
}
