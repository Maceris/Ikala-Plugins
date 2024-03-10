package com.ikalagaming.factory.quest;

import lombok.NonNull;

import java.util.List;

/**
 * A choice between several different types of items.
 *
 * @author Ches Burks
 */
public class RewardChoice extends Reward {

    /** The choices that the player has. */
    @NonNull public final List<String> items;

    /**
     * Specifies a reward.
     *
     * @param items All of the items that the player can choose between.
     */
    public RewardChoice(@NonNull String... items) {
        super(RewardType.CHOICE);
        this.items = List.of(items);
    }
}
