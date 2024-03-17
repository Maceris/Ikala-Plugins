package com.ikalagaming.factory.crafting;

import lombok.AllArgsConstructor;
import lombok.NonNull;

/** Anything that is consumed or produced as part of a crafting recipe. */
@AllArgsConstructor
public abstract class Ingredient {
    @NonNull public final IngredientType type;
}
