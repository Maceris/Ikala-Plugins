package com.ikalagaming.factory.quest;

import lombok.Getter;
import lombok.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * A choice between several different types of items.
 *
 * @author Ches Burks
 */
@Getter
public class RewardChoice extends Reward {

    /** The choices that the player has. */
    @NonNull public final List<String> items;

    /**
     * Specifies a reward.
     *
     * @param items All the items that the player can choose between.
     */
    public RewardChoice(@NonNull String... items) {
        super(RewardType.CHOICE);
        this.items = List.of(items);
    }

    @Override
    public String toString() {
        return String.format("Reward[type=%s, choices=%s]", type, Arrays.toString(items.toArray()));
    }
}
