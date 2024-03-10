package com.ikalagaming.rpg;

import lombok.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles flags, which are strings that can either be set to a number.
 *
 * @author Ches Burks
 */
public class FlagManager {
    private static Map<String, Integer> flags = Collections.synchronizedMap(new HashMap<>());

    /**
     * Unsets a flag.
     *
     * @param flag The flag to clear.
     */
    public static void clearFlag(@NonNull String flag) {
        flags.remove(flag);
    }

    /**
     * Fetches the value of a flag, assuming it exists. If a flag does not exist, we will return 0.
     * A flag could possibly be manually set to 0, so one should check if a flag exists before
     * assuming anything about a value of 0.
     *
     * @param flag The flag to look for.
     * @return The value of the flag, or 0 if it does not exist.
     * @see #hasFlag(String)
     */
    public static int getFlag(@NonNull String flag) {
        return flags.getOrDefault(flag, 0);
    }

    /**
     * Checks if a flag exists.
     *
     * @param flag The flag we are looking for.
     * @return True if the flag is set to any value, false if it is not set.
     * @see #getFlag(String)
     */
    public static boolean hasFlag(@NonNull String flag) {
        return flags.containsKey(flag);
    }

    /**
     * Set a flag so that it exists. If the flag does not exist, it will be set to 1. If it already
     * exists, it will be left alone.
     *
     * @param flag The flag we want to set.
     * @see #setFlag(String, int)
     */
    public static void setFlag(@NonNull String flag) {
        flags.computeIfAbsent(flag, ignored -> 1);
    }

    /**
     * Sets the value of a flag.
     *
     * @param flag The flag we want to set.
     * @param value The value to set for the flag.
     * @see #setFlag(String)
     */
    public static void setFlag(@NonNull String flag, int value) {
        flags.put(flag, value);
    }
}
