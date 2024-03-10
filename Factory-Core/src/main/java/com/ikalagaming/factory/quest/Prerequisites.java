package com.ikalagaming.factory.quest;

import lombok.NonNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * The quests that must be completed before another quest is enabled for completing or viewing.
 *
 * @author Ches Burks
 * @param quests The list of quest names.
 * @param logic The logic for determining which quests are required.
 */
public record Prerequisites(@NonNull String[] quests, @NonNull Logic logic) {

    /**
     * The logic that is used to calculate if enough prerequisites are met. Only really used if
     * there are more than 1 prerequisites.
     */
    public enum Logic {
        /** All of the listed prerequisites must be met before the quest is enabled. */
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }

        Prerequisites other = (Prerequisites) o;
        return Arrays.equals(quests, other.quests) && logic == other.logic;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(logic);
        result = 31 * result + Arrays.hashCode(quests);
        return result;
    }

    @Override
    public String toString() {
        return "Prerequisites["
                + "quests="
                + Arrays.toString(quests)
                + ", logic="
                + logic.name()
                + ']';
    }
}
