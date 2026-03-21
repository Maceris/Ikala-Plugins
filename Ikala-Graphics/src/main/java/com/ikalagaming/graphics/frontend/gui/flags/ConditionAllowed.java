package com.ikalagaming.graphics.frontend.gui.flags;

import com.ikalagaming.graphics.frontend.gui.enums.Condition;

import lombok.NonNull;

public class ConditionAllowed {
    /**
     * It's not specified what conditions are allowed. {@link Condition#NONE} or {@link
     * Condition#ALWAYS} will always happen.
     */
    public static final int NONE = 0;

    /**
     * Reserved bit for {@link Condition#ALWAYS}. {@link Condition#NONE} or {@link Condition#ALWAYS}
     * will always happen.
     */
    public static final int ALWAYS = 1;

    /** We can still set a condition of {@link Condition#ONCE}. */
    public static final int ONCE = 1 << 1;

    /** We can still set a condition of {@link Condition#FIRST_USE_EVER}. */
    public static final int FIRST_USE_EVER = 1 << 2;

    /** We can still set a condition of {@link Condition#APPEARING}. */
    public static final int APPEARING = 1 << 3;

    // Combined flags
    public static final int ALL = ALWAYS | ONCE | FIRST_USE_EVER | APPEARING;

    /**
     * Check a condition against condition flags.
     *
     * @param condition The condition. None or Always will always resolve.
     * @param conditionAllowed Flags for what conditions are allowed.
     * @return Whether an action should resolve.
     */
    public static boolean shouldResolve(@NonNull Condition condition, final int conditionAllowed) {
        return switch (condition) {
            case NONE, ALWAYS -> true;
            case ONCE -> (conditionAllowed & ONCE) != 0;
            case FIRST_USE_EVER -> (conditionAllowed & FIRST_USE_EVER) != 0;
            case APPEARING -> (conditionAllowed & APPEARING) != 0;
        };
    }

    /**
     * Return the updated flags after a condition resolves. Flips off the flag for conditional
     * cases, ignores None and Always.
     *
     * @param condition The condition that resolved.
     * @param oldConditionAllowed The condition flags before teh condition resolved.
     * @return What the flags should be now that it has resolved.
     */
    public static int updateFlags(@NonNull Condition condition, final int oldConditionAllowed) {
        return switch (condition) {
            case NONE, ALWAYS -> oldConditionAllowed;
            case ONCE -> oldConditionAllowed & (~ONCE);
            case FIRST_USE_EVER -> oldConditionAllowed & (~FIRST_USE_EVER);
            case APPEARING -> oldConditionAllowed & (~APPEARING);
        };
    }

    /** Private constructor so this is not instantiated. */
    private ConditionAllowed() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
