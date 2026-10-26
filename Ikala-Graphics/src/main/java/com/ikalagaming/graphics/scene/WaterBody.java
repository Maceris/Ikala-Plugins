package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.vulkan.WaterMath;

/**
 * What a body of water holds, which sets its color and clarity: the bio-optical model of Ma et al.
 * 2016, see {@link WaterMath#optics}. Pure water absorbs red most and scatters blue most;
 * phytoplankton absorb blue and red and turn water green; dissolved organic matter (CDOM) absorbs
 * blue and turns it yellow-brown; suspended sediment scatters every color and turns it milky brown.
 *
 * <pre>
 * WaterBody pond = WaterBody.ofChlorophyll(3);
 * WaterBody river = new WaterBody(1, 0.4f, 25);
 * </pre>
 *
 * @param chlorophyll The phytoplankton's chlorophyll concentration, in milligrams a cubic meter:
 *     about 0.05 in the clearest ocean, 0.5 in coastal water, 5 to 50 in green lakes.
 * @param cdom The dissolved organic matter's absorption at 440 nm, per meter: about 0.01 in the
 *     open ocean, 0.1 to 1 in coastal water, up to 10 in brown bog water.
 * @param sediment The suspended mineral sediment's concentration, in grams a cubic meter: 0 in the
 *     open ocean, 1 to 10 in coastal water, 10 to 100 or more in muddy rivers.
 */
public record WaterBody(float chlorophyll, float cdom, float sediment) {

    /** Clear open ocean, deep blue. */
    public static final WaterBody OCEAN = ofChlorophyll(0.05f);

    /** Green lake water, rich in phytoplankton. */
    public static final WaterBody GREEN = ofChlorophyll(8);

    /** A muddy river, thick with sediment and some dissolved organic matter. */
    public static final WaterBody MUDDY = new WaterBody(1, 0.5f, 40);

    /**
     * Check every concentration is a number and not negative.
     *
     * @throws IllegalArgumentException If one isn't.
     */
    public WaterBody {
        if (chlorophyll < 0 || cdom < 0 || sediment < 0) {
            throw new IllegalArgumentException(
                    "Water concentrations must not be negative: chlorophyll "
                            + chlorophyll
                            + ", cdom "
                            + cdom
                            + ", sediment "
                            + sediment);
        }
    }

    /**
     * Water whose color comes from its phytoplankton, the open ocean and most lakes (case 1 water):
     * its dissolved organic matter follows the chlorophyll, absorbing a fifth as much at 440 nm as
     * the phytoplankton do, as Mobley's classic case 1 model has it, and it holds no sediment.
     *
     * @param chlorophyll The chlorophyll concentration, in milligrams a cubic meter.
     * @return The body.
     */
    public static WaterBody ofChlorophyll(float chlorophyll) {
        final double phytoplankton =
                WaterMath.phytoplanktonAbsorption(
                        Math.max(0, chlorophyll), WaterMath.SPECIFIC_AT_440);
        return new WaterBody(chlorophyll, (float) (WaterMath.CDOM_SHARE * phytoplankton), 0);
    }
}
