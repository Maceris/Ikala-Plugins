package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.ColorCode;
import com.ikalagaming.characters.definition.Palette;
import com.ikalagaming.random.SeededRandom;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The swatch grids the character editor offers, all made of {@link ColorCode}s: a grid of skin
 * tones for the skin channel, and a grid of every hue in light to dark shades, with a row of grays,
 * for the rest. Random colors come from the same ranges.
 *
 * @author Ches Burks
 */
final class Swatches {
    /** Swatches per row. */
    static final int COLUMNS = 12;

    /** The hue steps skin tones span, from reddish to yellowish, one per row. */
    private static final int[] SKIN_HUES = {2, 3, 4};

    /** The saturation steps skin tones go through, pale to deep, one per group of rows. */
    private static final int[] SKIN_SATURATIONS = {3, 5, 7, 9};

    /** The darkest skin tone's brightness step; each row runs from it to the lightest. */
    private static final int SKIN_DARKEST = 12;

    /** The saturation and brightness steps of the general grid's rows, pale to deep and dark. */
    private static final int[][] SHADES = {
        {4, 63}, {7, 63}, {11, 63}, {15, 63}, {15, 50}, {15, 38}, {15, 26}, {15, 16}
    };

    /** The skin tone grid. */
    private static final List<Integer> SKIN = skinTones();

    /** The general grid. */
    private static final List<Integer> GENERAL = general();

    /**
     * The swatches for a channel.
     *
     * @param channel The palette channel.
     * @return The color codes, row by row, {@link #COLUMNS} to a row.
     */
    static List<Integer> forChannel(@NonNull String channel) {
        return Palette.SKIN.equals(channel) ? SKIN : GENERAL;
    }

    /**
     * A random color for a channel, from its swatches.
     *
     * @param channel The palette channel.
     * @param random Where the randomness comes from.
     * @return The color code.
     */
    static int random(@NonNull String channel, @NonNull SeededRandom random) {
        List<Integer> swatches = forChannel(channel);
        return swatches.get(random.nextInt(swatches.size()));
    }

    /**
     * Make the skin tone grid: a row per saturation and hue, dark to light along it.
     *
     * @return The codes.
     */
    private static List<Integer> skinTones() {
        List<Integer> codes = new ArrayList<>();
        for (int saturation : SKIN_SATURATIONS) {
            for (int hue : SKIN_HUES) {
                for (int c = 0; c < COLUMNS; ++c) {
                    int value =
                            SKIN_DARKEST
                                    + Math.round(
                                            c
                                                    * (ColorCode.VALUES - 1f - SKIN_DARKEST)
                                                    / (COLUMNS - 1));
                    codes.add(ColorCode.of(hue, saturation, value));
                }
            }
        }
        return List.copyOf(codes);
    }

    /**
     * Make the general grid: a row of grays, then a row per shade with {@link #COLUMNS} hues.
     *
     * @return The codes.
     */
    private static List<Integer> general() {
        List<Integer> codes = new ArrayList<>();
        for (int g = 0; g < COLUMNS; ++g) {
            codes.add(ColorCode.of(0, 0, Math.round(g * (ColorCode.VALUES - 1f) / (COLUMNS - 1))));
        }
        for (int[] shade : SHADES) {
            for (int h = 0; h < COLUMNS; ++h) {
                int hue = Math.round(h * (float) ColorCode.HUES / COLUMNS) % ColorCode.HUES;
                codes.add(ColorCode.of(hue, shade[0], shade[1]));
            }
        }
        return List.copyOf(codes);
    }

    /** Static methods only. */
    private Swatches() {}
}
