package com.ikalagaming.factory.crafting;

import com.ikalagaming.factory.item.ItemStack;

import lombok.NonNull;

/**
 * A transformation performed on recipe inputs, which specifies how they are affected by crafting.
 * This is especially useful for doing things like consuming durability, charge, having a random
 * chance of keeping the item, etc.
 */
@FunctionalInterface
public interface TransformItem {
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
     * @throws IllegalArgumentException If amount is negative.
     */
    static TransformItem consume(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException();
        }
        if (amount == 0) {
            return REUSE;
        }

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
    static TransformItem replace(@NonNull ItemStack item) {
        return stack -> item;
    }

    /**
     * Transform an item stack. The item will be replaced by the output of this function. This could
     * return null if it's consumed, a modified form of the input item, or any arbitrary item stack.
     *
     * @param item The input item to modify.
     * @return What the input item should be replaced with as part of crafting.
     */
    ItemStack transform(@NonNull ItemStack item);
}
