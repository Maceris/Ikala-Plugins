package com.ikalagaming.factory.crafting;

import lombok.Getter;

/** Power that is consumed and/or produced by a recipe. */
@Getter
public class IngredientPower extends Ingredient {

    /** The amount of power required. */
    private final long amount;

    public IngredientPower(long amount) {
        super(IngredientType.POWER);
        this.amount = amount;
    }
}
