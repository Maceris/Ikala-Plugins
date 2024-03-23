package com.ikalagaming.factory.crafting;

import com.ikalagaming.factory.item.ItemStack;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/** The item input to a recipe. */
@Getter
@Builder
public class InputItem extends Ingredient {

    /** The item required by the recipe. */
    @NonNull private final ItemStack itemStack;

    /** The transformation to apply to the ingredient. */
    @Builder.Default @NonNull private TransformItem transformation = TransformItem.CONSUME_ALL;

    /** The condition we want to use when checking if an item matches this recipe ingredient. */
    @Builder.Default @NonNull private ItemMatchCondition matchCondition = ItemMatchCondition.DEFAULT;

    /**
     * Create an ingredient input for items which consumes all the input.
     *
     * @param itemStack The item stack that is the input.
     */
    public InputItem(@NonNull ItemStack itemStack) {
        this(itemStack, TransformItem.CONSUME_ALL, ItemMatchCondition.DEFAULT);
    }

    /**
     * Create an ingredient input for items with arbitrary transformation of the input stack.
     *
     * @param itemStack The item stack that is the input.
     * @param transformation The transformation to perform on the input.
     */
    public InputItem(@NonNull ItemStack itemStack, @NonNull TransformItem transformation) {
        this(itemStack, transformation, ItemMatchCondition.DEFAULT);
    }

    /**
     * Create an ingredient input for items with arbitrary transformation of the input stack.
     *
     * @param itemStack The item stack that is the input.
     * @param transformation The transformation to perform on the input.
     * @param matchCondition The condition to match on.
     */
    public InputItem(
            @NonNull ItemStack itemStack,
            @NonNull TransformItem transformation,
            @NonNull ItemMatchCondition matchCondition) {
        super(IngredientType.ITEM_INPUT);
        this.itemStack = itemStack;
        this.transformation = transformation;
        this.matchCondition = matchCondition;
    }
}
