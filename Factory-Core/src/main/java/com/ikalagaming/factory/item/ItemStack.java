package com.ikalagaming.factory.item;

import lombok.*;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ItemStack {
    /**
     * Check if two item stacks are the same type of item. If either is null, checks whether they
     * both are.
     *
     * @param stack1 The first stack.
     * @param stack2 The second stack.
     * @return Whether the stacks are of the same item.
     */
    public static boolean isSameType(@NonNull ItemStack stack1, @NonNull ItemStack stack2) {
        return stack1.getItem().equals(stack2.getItem());
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
