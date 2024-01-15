package com.ikalagaming.factory.quest;

import com.ikalagaming.factory.crafting.Recipe;

import lombok.NonNull;

/**
 * Unlocks a recipe for the player.
 *
 * @author Ches Burks
 *
 */
public class RewardRecipe extends Reward {

	/**
	 * The recipe to unlock.
	 */
	@NonNull
	public final Recipe recipe;

	/**
	 * Specifies a reward.
	 *
	 * @param recipe The recipe to unlock.
	 */
	public RewardRecipe(@NonNull Recipe recipe) {
		super(RewardType.RECIPE);
		this.recipe = recipe;
	}
}
