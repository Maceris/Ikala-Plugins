package com.ikalagaming.factory.quest;

import lombok.AllArgsConstructor;
import lombok.NonNull;

/**
 * A reward for a quest being completed.
 *
 * @author Ches Burks
 */
@AllArgsConstructor
public abstract class Reward {
    /** The type of reward. */
    @NonNull public final RewardType type;
}
