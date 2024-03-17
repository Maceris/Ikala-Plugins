package com.ikalagaming.factory.quest;

import lombok.Getter;
import lombok.NonNull;

/**
 * Gives the player an item.
 *
 * @author Ches Burks
 */
@Getter
public class RewardItem extends Reward {

    // TODO(ches) Allow stacks, item metadata - FACT-7
    /** The item to give the player. */
    @NonNull private final String itemName;

    /**
     * Specifies a reward.
     *
     * @param itemName The full name of the item (like "mod:item" format).
     */
    public RewardItem(@NonNull String itemName) {
        super(RewardType.ITEM);
        this.itemName = itemName;
    }

    @Override
    public String toString() {
        return String.format("Reward[type=%s, itemName=%s]", type, itemName);
    }
}
