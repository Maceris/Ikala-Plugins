package com.ikalagaming.factory.quest;

import lombok.NonNull;

/**
 * Executes a script.
 *
 * @author Ches Burks
 *
 */
public class RewardCommand extends Reward {

	/**
	 * The script to run.
	 */
	@NonNull
	public final String script;

	/**
	 * Specifies a reward.
	 *
	 * @param script The script to execute.
	 */
	public RewardCommand(@NonNull String script) {
		super(RewardType.COMMAND);
		this.script = script;
	}
}
