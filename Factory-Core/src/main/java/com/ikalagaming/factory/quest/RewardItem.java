package com.ikalagaming.factory.quest;

import lombok.NonNull;

/**
 * Gives the player an item.
 *
 * @author Ches Burks
 */
public class RewardItem extends Reward {

    /** The item to give the player. */
    @NonNull public final String itemName;

    /**
     * Specifies a reward.
     *
     * @param itemName The full name of the item (like "mod:item" format).
     */
    public RewardItem(@NonNull String itemName) {
        super(RewardType.ITEM);
        this.itemName = itemName;
    }
}
