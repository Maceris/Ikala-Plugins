package com.ikalagaming.factory.quest;

import com.ikalagaming.factory.crafting.Recipe;

import lombok.Getter;
import lombok.NonNull;

/**
 * Unlocks a recipe for the player.
 *
 * @author Ches Burks
 */
@Getter
public class RewardRecipe extends Reward {

    /** The recipe to unlock. */
    @NonNull private final Recipe recipe;

    /**
     * Specifies a reward.
     *
     * @param recipe The recipe to unlock.
     */
    public RewardRecipe(@NonNull Recipe recipe) {
        super(RewardType.RECIPE);
        this.recipe = recipe;
    }

    @Override
    public String toString() {
        return String.format("Reward[type=%s, recipe=%s]", type, recipe);
    }
}
