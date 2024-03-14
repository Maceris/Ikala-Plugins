package com.ikalagaming.factory.quest;

import lombok.Getter;
import lombok.NonNull;

/**
 * Executes a script.
 *
 * @author Ches Burks
 */
@Getter
public class RewardCommand extends Reward {

    /** The script to run. */
    @NonNull public final String script;

    /**
     * Specifies a reward.
     *
     * @param script The script to execute.
     */
    public RewardCommand(@NonNull String script) {
        super(RewardType.COMMAND);
        this.script = script;
    }

    @Override
    public String toString() {
        return String.format("Reward[type=%s, script='%s']", type, script);
    }
}
