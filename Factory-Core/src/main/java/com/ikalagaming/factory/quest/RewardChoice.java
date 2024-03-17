package com.ikalagaming.factory.quest;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * A choice between several different types of items.
 *
 * @author Ches Burks
 */
@Getter
@Slf4j
public class RewardChoice extends Reward {

    // TODO(ches) Allow stacks, item metadata - FACT-7
    /** The choices that the player has. */
    @NonNull private final List<String> items;

    /**
     * Specifies a reward.
     *
     * @param items All the items that the player can choose between.
     */
    public RewardChoice(@NonNull List<String> items) {
        super(RewardType.CHOICE);

        this.items = List.copyOf(items);
    }

    @Override
    public String toString() {
        return String.format("Reward[type=%s, choices=%s]", type, Arrays.toString(items.toArray()));
    }
}
