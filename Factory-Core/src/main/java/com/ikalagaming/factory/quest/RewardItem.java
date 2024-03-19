package com.ikalagaming.factory.quest;

import com.ikalagaming.factory.item.ItemStack;

import lombok.Getter;
import lombok.NonNull;

/**
 * Gives the player an item.
 *
 * @author Ches Burks
 */
@Getter
public class RewardItem extends Reward {

    /** The item to give the player. */
    @NonNull private final ItemStack item;

    /**
     * Specifies a reward.
     *
     * @param item The item that is given.
     */
    public RewardItem(@NonNull ItemStack item) {
        super(RewardType.ITEM);
        this.item = item;
    }

    @Override
    public String toString() {
        return String.format("Reward[type=%s, item=%s]", type, item);
    }
}
