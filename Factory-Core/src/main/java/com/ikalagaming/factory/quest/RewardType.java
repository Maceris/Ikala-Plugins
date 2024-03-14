package com.ikalagaming.factory.quest;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;

/**
 * The types of rewards that can be given after completing a quest.
 *
 * @author Ches Burks
 */
public enum RewardType {
    /** The player has a choice between a list of items. */
    CHOICE,
    /** Runs a command or script. */
    COMMAND,
    /** Give the player an item. */
    ITEM,
    /** Unlock a recipe. */
    RECIPE;

    private static String getLocalizationKey(@NonNull RewardType type) {
        return switch (type) {
            case CHOICE -> "REWARD_TYPE_CHOICE";
            case COMMAND -> "REWARD_TYPE_COMMAND";
            case ITEM -> "REWARD_TYPE_ITEM";
            case RECIPE -> "REWARD_TYPE_RECIPE";
        };
    }

    @Override
    public String toString() {
        return SafeResourceLoader.getString(
                getLocalizationKey(this), FactoryPlugin.getResourceBundle());
    }
}
