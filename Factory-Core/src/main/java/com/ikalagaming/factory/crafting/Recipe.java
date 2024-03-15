package com.ikalagaming.factory.crafting;

import java.util.List;

/**
 * Represents a crafting recipe.
 *
 * @author Ches Burks
 */
public record Recipe(
        List<String> inputItems,
        List<String> inputLiquids,
        List<String> inputGases,
        long inputPower,
        List<String> outputItems,
        List<String> outputLiquids,
        List<String> outputGases,
        long outputPower) {}
