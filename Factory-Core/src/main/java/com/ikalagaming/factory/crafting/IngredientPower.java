package com.ikalagaming.factory.crafting;

import lombok.Getter;

/** Power that is consumed and/or produced by a recipe. */
@Getter
public class IngredientPower extends Ingredient {

    /** The amount of power required, in joules. */
    private final long amount;

    /**
     * The amount of power required, in joules.
     *
     * @param amount The amount of power in joules.
     */
    public IngredientPower(long amount) {
        super(IngredientType.POWER);
        this.amount = amount;
    }
}
