package com.ikalagaming.factory.crafting;

import lombok.Getter;
import lombok.NonNull;

/** An item that is consumed and/or produced by a recipe. */
@Getter
public class IngredientItem extends Ingredient {

    // TODO(ches) Allow stacks, item metadata - FACT-8
    /** The name of the item involved in the recipe. */
    @NonNull private final String itemName;

    public IngredientItem(@NonNull String itemName) {
        super(IngredientType.ITEM);
        this.itemName = itemName;
    }
}
