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
 * @param windSpeed The wind's speed 10 m above the water, in meters a second, which raises its
 *     waves: 0 for still water, 3 for a light breeze, 8 for a moderate one, 15 for a gale.
 * @param windDirection The way the wind blows, in radians from +x toward +z.
 * @param fetch How far the wind has blown over the water, in meters, which limits how big its waves
 *     grow: a pond's or a lake's width, or infinite for the open sea.
 */
public record WaterBody(
        float chlorophyll,
        float cdom,
        float sediment,
        float windSpeed,
        float windDirection,
        float fetch) {

    /** Clear open ocean, deep blue, in a moderate breeze. */
    public static final WaterBody OCEAN =
            ofChlorophyll(0.05f).withWind(8, 0, Float.POSITIVE_INFINITY);

    /** Green lake water, rich in phytoplankton, in a light breeze across a kilometer. */
    public static final WaterBody GREEN = ofChlorophyll(8).withWind(4, 0, 1000);

    /** A muddy river, thick with sediment and some dissolved organic matter, nearly still. */
    public static final WaterBody MUDDY = new WaterBody(1, 0.5f, 40).withWind(2, 0, 200);

    /**
     * Still water: no wind.
     *
     * @param chlorophyll The chlorophyll concentration, in milligrams a cubic meter.
     * @param cdom The dissolved organic matter's absorption at 440 nm, per meter.
     * @param sediment The sediment concentration, in grams a cubic meter.
     */
    public WaterBody(float chlorophyll, float cdom, float sediment) {
        this(chlorophyll, cdom, sediment, 0, 0, Float.POSITIVE_INFINITY);
    }

    /**
     * Check every concentration and the wind are numbers and not negative.
     *
     * @throws IllegalArgumentException If one isn't.
     */
    public WaterBody {
        if (windSpeed < 0 || fetch <= 0 || !Float.isFinite(windDirection)) {
            throw new IllegalArgumentException(
                    "Wind must have a speed, a fetch and a direction: "
                            + windSpeed
                            + " m/s over "
                            + fetch
                            + " m toward "
                            + windDirection);
        }
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

    /**
     * The same water in another wind.
     *
     * @param speed The wind's speed 10 m up, in meters a second.
     * @param direction The way it blows, in radians from +x toward +z.
     * @param over How far it has blown over the water, in meters, or infinite for the open sea.
     * @return A changed copy.
     */
    public WaterBody withWind(float speed, float direction, float over) {
        return new WaterBody(chlorophyll, cdom, sediment, speed, direction, over);
    }
}
