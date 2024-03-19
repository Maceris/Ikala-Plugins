package com.ikalagaming.factory.crafting;

import com.ikalagaming.factory.item.ItemStack;

import lombok.NonNull;

/**
 * A transformation performed on recipe inputs, which specifies how they are affected by crafting.
 */
@FunctionalInterface
public interface TransformItem {
    ItemStack transform(@NonNull ItemStack item);

    /** The item stack is entirely consumed. */
    TransformItem CONSUME_ALL = stack -> null;

    /** The item is left alone. */
    TransformItem REUSE = stack -> stack;

    /**
     * We consume a certain amount of the item. If all the stack is consumed, it is removed.
     *
     * @param amount The maximum amount to consume.
     * @return A transformation that will attempt to remove items up to the specified amount,
     *     leaving either the remainder or null if the whole stack was consumed.
     */
    default TransformItem consume(int amount) {
        return stack -> {
            if (amount >= stack.getCount()) {
                return null;
            }
            return stack.withCount(stack.getCount() - amount);
        };
    }

    /**
     * The item stack is replaced with another.
     *
     * @param item The item to replace the contents with.
     * @return A transformation that will replace the current item with another one.
     */
    default TransformItem replace(@NonNull ItemStack item) {
        return stack -> item;
    }
}
