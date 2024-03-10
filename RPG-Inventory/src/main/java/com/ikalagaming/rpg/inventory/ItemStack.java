package com.ikalagaming.rpg.inventory;

import com.ikalagaming.rpg.item.Item;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

/**
 * A stack of stackable items. Non-stackable items are handled differently.
 *
 * @author Ches Burks
 */
@Getter
@Setter(value = AccessLevel.PACKAGE)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
class ItemStack {
    /**
     * Check if two item stacks are the same type of item. If either is null, checks whether they
     * both are.
     *
     * @param stack1 The first stack.
     * @param stack2 The second stack.
     * @return Whether the stacks are of the same item.
     */
    public static boolean isSameType(@NonNull ItemStack stack1, @NonNull ItemStack stack2) {
        if (stack1.item == null || stack2.item == null) {
            // Check if they are both null
            return stack1.item == stack2.item;
        }
        return stack1.item.getID().equals(stack2.item.getID());
    }

    /**
     * The item that is stored in the inventory slot.
     *
     * @return The item that is stacked.
     */
    private Item item;

    /**
     * The current number of items stored in this slot.
     *
     * @return The current stack size.
     */
    private int count;
}
