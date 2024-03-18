package com.ikalagaming.factory.crafting;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class IngredientGas extends Ingredient {

    /** The name of the required gas. */
    @NonNull private final String gasName;

    /** The amount of gas, in milliliters. */
    private final long amount;

    public IngredientGas(@NonNull String gasName, long amount) {
        super(IngredientType.GAS);
        this.gasName = gasName;
        this.amount = amount;
    }
}
