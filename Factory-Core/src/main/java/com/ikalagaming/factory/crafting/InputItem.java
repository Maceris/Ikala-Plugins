package com.ikalagaming.factory.crafting;

import com.ikalagaming.factory.item.ItemStack;

import lombok.Getter;
import lombok.NonNull;

/** The item input to a recipe. */
@Getter
public class InputItem extends Ingredient {

    /** The item required by the recipe. */
    @NonNull private final ItemStack itemStack;

    /** The transformation to apply to the ingredient. */
    private final TransformItem transformation;

    /**
     * Create an ingredient input for items which consumes all the input.
     *
     * @param itemStack The item stack that is the input.
     */
    public InputItem(@NonNull ItemStack itemStack) {
        this(itemStack, TransformItem.CONSUME_ALL);
    }

    /**
     * Create an ingredient input for items with arbitrary transformation of the input stack.
     *
     * @param itemStack The item stack that is the input.
     * @param transformation The transformation to perform on the input.
     */
    public InputItem(@NonNull ItemStack itemStack, @NonNull TransformItem transformation) {
        super(IngredientType.ITEM);
        this.itemStack = itemStack;
        this.transformation = transformation;
    }
}
