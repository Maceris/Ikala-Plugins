package com.ikalagaming.factory.crafting;

import java.util.List;

/**
 * Represents a crafting recipe.
 *
 * @author Ches Burks
 */
public record Recipe(List<Ingredient> inputs, List<Ingredient> outputs) {}
