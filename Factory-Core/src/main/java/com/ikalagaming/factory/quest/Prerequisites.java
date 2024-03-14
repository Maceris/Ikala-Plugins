package com.ikalagaming.factory.quest;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * The quests that must be completed before another quest is enabled for completing or viewing.
 *
 * @author Ches Burks
 */
@RequiredArgsConstructor
@EqualsAndHashCode
public class Prerequisites {
    /**
     * The quests that must be completed before another quest is enabled for completing or viewing.
     */
    @NonNull private final List<String> quests;

    /**
     * The logic that is used to calculate if enough prerequisites are met. Only really relevant if
     * there are more than 1 prerequisite.
     */
    @NonNull private final Logic logic;

    /**
     * The logic that is used to calculate if enough prerequisites are met. Only really used if
     * there are more than 1 prerequisite.
     */
    public enum Logic {
        /** All the listed prerequisites must be met before the quest is enabled. */
        AND,
        /** Any of the listed prerequisites can be met, and the quest can be enabled. */
        OR,
        /**
         * Exactly one of the prerequisites must be met, no more and no less. This indicates the
         * prerequisites are all mutually exclusive.
         */
        XOR
    }

    @Override
    public String toString() {
        return "Prerequisites["
                + "quests="
                + Arrays.toString(quests.toArray())
                + ", logic="
                + logic.name()
                + ']';
    }
}
