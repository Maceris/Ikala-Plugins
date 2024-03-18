package com.ikalagaming.factory.crafting;

import lombok.Getter;
import lombok.NonNull;

/** A liquid or gas that is consumed and/or produced by a recipe. */
@Getter
public class IngredientFluid extends Ingredient {

    /** The name of the required liquid or gas. */
    @NonNull private final String fluidName;

    /** The amount of fluid, in milliliters. */
    private final long amount;

    public IngredientFluid(@NonNull String fluidName, long amount) {
        super(IngredientType.FLUID);
        this.fluidName = fluidName;
        this.amount = amount;
    }
}
