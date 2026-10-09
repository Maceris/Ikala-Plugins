package com.ikalagaming.graphics.ui;

import lombok.NonNull;

/** How a node decides its size along one axis. */
public sealed interface Sizing {
    /** No minimum. */
    Length NO_MIN = Length.ZERO;

    /** No maximum, as a length too large to ever be reached. */
    Length NO_MAX = Length.u(Float.MAX_VALUE);

    /**
     * Exactly a length, which may be a fraction of the parent.
     *
     * @param length The size.
     */
    record Fixed(@NonNull Length length) implements Sizing {}

    /**
     * As big as the content, clamped.
     *
     * @param min The smallest size.
     * @param max The largest size.
     */
    record Fit(@NonNull Length min, @NonNull Length max) implements Sizing {}

    /**
     * At least as big as the content, then a share of whatever space the parent has left, clamped.
     * Siblings share by weight. Outside a row or column it fills the parent.
     *
     * @param weight How big a share to take relative to other growing siblings.
     * @param min The smallest size.
     * @param max The largest size.
     */
    record Grow(float weight, @NonNull Length min, @NonNull Length max) implements Sizing {
        /**
         * Create a growing size.
         *
         * @param weight How big a share to take, which must be positive.
         * @param min The smallest size.
         * @param max The largest size.
         */
        public Grow {
            if (weight <= 0) {
                throw new IllegalArgumentException("Grow weights must be positive: " + weight);
            }
        }
    }

    /**
     * Exactly a length.
     *
     * @param length The size.
     * @return The sizing.
     */
    static Sizing fixed(@NonNull Length length) {
        return new Fixed(length);
    }

    /**
     * Exactly some UI units.
     *
     * @param units The size in UI units.
     * @return The sizing.
     */
    static Sizing fixed(float units) {
        return new Fixed(Length.u(units));
    }

    /**
     * A fraction of the parent.
     *
     * @param fraction The fraction, where 1 is the whole parent.
     * @return The sizing.
     */
    static Sizing percent(float fraction) {
        return new Fixed(Length.percent(fraction));
    }

    /**
     * As big as the content.
     *
     * @return The sizing.
     */
    static Sizing fit() {
        return new Fit(NO_MIN, NO_MAX);
    }

    /**
     * As big as the content, clamped.
     *
     * @param min The smallest size.
     * @param max The largest size.
     * @return The sizing.
     */
    static Sizing fit(@NonNull Length min, @NonNull Length max) {
        return new Fit(min, max);
    }

    /**
     * Take an equal share of the leftover space.
     *
     * @return The sizing.
     */
    static Sizing grow() {
        return new Grow(1, NO_MIN, NO_MAX);
    }

    /**
     * Take a weighted share of the leftover space.
     *
     * @param weight The share relative to growing siblings.
     * @return The sizing.
     */
    static Sizing grow(float weight) {
        return new Grow(weight, NO_MIN, NO_MAX);
    }

    /**
     * Take a weighted share of the leftover space, clamped.
     *
     * @param weight The share relative to growing siblings.
     * @param min The smallest size.
     * @param max The largest size.
     * @return The sizing.
     */
    static Sizing grow(float weight, @NonNull Length min, @NonNull Length max) {
        return new Grow(weight, min, max);
    }
}
