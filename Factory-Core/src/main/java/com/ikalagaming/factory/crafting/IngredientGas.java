package com.ikalagaming.factory.crafting;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class IngredientGas extends Ingredient {

    // TODO(ches) allow amounts FACT-9
    /** The name of the required gas. */
    @NonNull private final String gasName;

    public IngredientGas(@NonNull String gasName) {
        super(IngredientType.GAS);
        this.gasName = gasName;
    }
}
