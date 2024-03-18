package com.ikalagaming.factory.crafting;

import lombok.Getter;
import lombok.NonNull;

/** A liquid that is consumed and/or produced by a recipe. */
@Getter
public class IngredientLiquid extends Ingredient {

    /** The name of the required liquid. */
    @NonNull private final String liquidName;

    /** The amount of liquid, in milliliters. */
    private final long amount;

    public IngredientLiquid(@NonNull String liquidName, long amount) {
        super(IngredientType.LIQUID);
        this.liquidName = liquidName;
        this.amount = amount;
    }
}
