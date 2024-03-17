package com.ikalagaming.factory.crafting;

import lombok.Getter;
import lombok.NonNull;

/** A liquid that is consumed and/or produced by a recipe. */
@Getter
public class IngredientLiquid extends Ingredient {

    // TODO(ches) allow amounts FACT-9
    /** The name of the required liquid. */
    @NonNull private final String liquidName;

    public IngredientLiquid(@NonNull String liquidName) {
        super(IngredientType.LIQUID);
        this.liquidName = liquidName;
    }
}
