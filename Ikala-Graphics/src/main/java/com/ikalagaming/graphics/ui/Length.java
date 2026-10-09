package com.ikalagaming.graphics.ui;

import lombok.NonNull;

/**
 * A length made of up to three parts that are added together: UI units, a fraction of the parent,
 * and ems.
 *
 * <ul>
 *   <li>UI units are pixels at a UI scale of 1, so they grow with the screen's DPI and the user's
 *       UI scale setting.
 *   <li>Percent is a fraction of the parent's content size on the same axis, where 1 is all of it.
 *   <li>Ems are multiples of the node's font size, so they follow text size.
 * </ul>
 *
 * For example {@code Length.percent(0.5f).plus(Length.u(-8))} is half the parent, less 8 units.
 *
 * @param units The UI units.
 * @param percent The fraction of the parent, where 1 is all of it.
 * @param em The multiple of the font size.
 */
public record Length(float units, float percent, float em) {
    /** No length at all. */
    public static final Length ZERO = new Length(0, 0, 0);

    /**
     * A length in UI units.
     *
     * @param units The number of UI units.
     * @return The length.
     */
    public static Length u(float units) {
        return new Length(units, 0, 0);
    }

    /**
     * A fraction of the parent's content size.
     *
     * @param fraction The fraction, where 1 is the whole parent.
     * @return The length.
     */
    public static Length percent(float fraction) {
        return new Length(0, fraction, 0);
    }

    /**
     * A multiple of the font size.
     *
     * @param em The number of ems.
     * @return The length.
     */
    public static Length em(float em) {
        return new Length(0, 0, em);
    }

    /**
     * Add two lengths.
     *
     * @param other The length to add.
     * @return The sum.
     */
    public Length plus(@NonNull Length other) {
        return new Length(units + other.units, percent + other.percent, em + other.em);
    }

    /**
     * Whether part of this length depends on the parent's size.
     *
     * @return True if the percent part is not zero.
     */
    public boolean isRelative() {
        return percent != 0;
    }

    /**
     * Convert to pixels.
     *
     * @param parent The parent's content size on this axis, in pixels.
     * @param scale The UI scale, pixels per UI unit.
     * @param fontSize The node's font size, in pixels.
     * @return The length in pixels.
     */
    public float resolve(float parent, float scale, float fontSize) {
        return units * scale + percent * parent + em * fontSize;
    }
}
