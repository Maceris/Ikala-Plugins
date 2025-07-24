package com.ikalagaming.factory.item;

import lombok.*;

@Getter
@Setter
@With
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ItemStack {
    /** The maximum value that a stack can reach. */
    public static final int MAX_STACK_SIZE = 2_000_000_000;

    /**
     * Check if two item stacks are the same type of item. If either stack is null, checks that they
     * both are.
     *
     * @param stack1 The first stack.
     * @param stack2 The second stack.
     * @return Whether the stacks are of the same item.
     */
    public static boolean isSameType(ItemStack stack1, ItemStack stack2) {
        if (stack1 == null || stack2 == null) {
            return stack1 == stack2;
        }
        return Item.isSameType(stack1.getItem(), stack2.getItem());
    }

    /**
     * The item that we have a stack of.
     *
     * @return The item.
     */
    private final @NonNull Item item;

    /**
     * The current number of items in the stack.
     *
     * @return The current stack size.
     */
    private int count = 1;

    @Override
    public String toString() {
        return String.format("ItemStack[item=%s, count=%s]", item, count);
    }
}
