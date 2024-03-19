package com.ikalagaming.factory.quest;

import com.ikalagaming.factory.item.ItemStack;

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

    /** The choices that the player has. */
    @NonNull private final List<ItemStack> choices;

    /**
     * Specifies a reward.
     *
     * @param choices All the items that the player can choose between.
     */
    public RewardChoice(@NonNull List<ItemStack> choices) {
        super(RewardType.CHOICE);

        this.choices = List.copyOf(choices);
    }

    @Override
    public String toString() {
        return String.format(
                "Reward[type=%s, choices=%s]", type, Arrays.toString(choices.toArray()));
    }
}
