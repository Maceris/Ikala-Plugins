package com.ikalagaming.graphics.vulkan;

/**
 * The deformation field's layout and packing, see {@code deform.glsl}, which has the same math;
 * keep them in step. The field covers {@link #WINDOW} meters around the camera at {@link
 * #TEXELS_PER_METER} texels a meter, {@link #SIZE} texels a side, addressed by world texel modulo
 * the size, so it scrolls with the camera without moving anything: only the texels newly in the
 * window are cleared.
 *
 * <p>A texel is one uint: how deep it is pressed in its high 16 bits, in {@link #DEPTH_UNIT} meters
 * biased by {@link #DEPTH_BIAS}, so a raised rim is below the bias; and in its low 16 bits the top
 * of the surface it was pressed into, in {@link #HEIGHT_STEPS} steps a meter, wrapped to 16 bits,
 * which tells stacked surfaces apart. Zero is untouched. Stamps write with {@code atomicMax}, so
 * the deepest press wins, standing still doesn't dig deeper, and their order doesn't matter.
 */
public final class DeformMath {

    /** Texels along a side of the field. */
    public static final int SIZE = 2048;

    /** Texels a meter: 3.125 cm a texel. */
    public static final int TEXELS_PER_METER = 32;

    /** The width of the window the field covers, in meters. */
    public static final double WINDOW = (double) SIZE / TEXELS_PER_METER;

    /** The depth unit, in meters: 0.1 mm. */
    public static final double DEPTH_UNIT = 1e-4;

    /** The depth that packs as zero, so a raised rim up to this many units fits below it. */
    public static final int DEPTH_BIAS = 8192;

    /** The most a press can be, in units, past the bias. */
    public static final int MAX_DEPTH_UNITS = 0xFFFF - DEPTH_BIAS;

    /** Steps a meter of a surface top's height, as the game's: 1/16 m. */
    public static final int HEIGHT_STEPS = 16;

    /**
     * Columns along a side of the column table, which covers the window and a margin, by world
     * column modulo this; each column holds up to {@link #SLOTS} surfaces.
     */
    public static final int TABLE_SIZE = 128;

    /** Surfaces a column of the table holds, highest first. */
    public static final int SLOTS = 4;

    /** How far above or below a surface's span a stamp may be and still press it, in meters. */
    public static final double STAMP_REACH = 0.25;

    /** Static helpers only. */
    private DeformMath() {}

    /**
     * Pack a press.
     *
     * @param depth How deep, in meters; negative for a raised rim.
     * @param surfaceTop The top of the surface pressed, in meters.
     * @return The texel, never zero.
     */
    public static int pack(double depth, double surfaceTop) {
        final int units = (int) Math.clamp(Math.round(depth / DEPTH_UNIT) + DEPTH_BIAS, 1, 0xFFFF);
        return (units << 16) | heightKey(surfaceTop);
    }

    /**
     * A surface top's height as the field keys it.
     *
     * @param top The top, in meters.
     * @return Its steps, wrapped to 16 bits.
     */
    public static int heightKey(double top) {
        return (int) Math.round(top * HEIGHT_STEPS) & 0xFFFF;
    }

    /**
     * How deep a texel is pressed.
     *
     * @param texel The texel.
     * @return The depth, in meters; zero for untouched, negative for a rim.
     */
    public static double depth(int texel) {
        return texel == 0 ? 0 : ((texel >>> 16) - DEPTH_BIAS) * DEPTH_UNIT;
    }

    /**
     * A texel's place in the field.
     *
     * @param texelX The world texel along x.
     * @param texelZ The world texel along z.
     * @return The index into the field.
     */
    public static int index(long texelX, long texelZ) {
        return (int) (Math.floorMod(texelZ, SIZE) * SIZE + Math.floorMod(texelX, SIZE));
    }

    /**
     * Fill a texel back in a time: its press moves toward nothing at a rate.
     *
     * @param texel The texel.
     * @param rate How fast, in meters a second.
     * @param seconds How long.
     * @return The texel after, zero once filled.
     */
    public static int refill(int texel, double rate, double seconds) {
        if (texel == 0) {
            return 0;
        }
        final int units = (texel >>> 16) - DEPTH_BIAS;
        final int step = (int) Math.ceil(rate * seconds / DEPTH_UNIT);
        final int after = units > 0 ? Math.max(0, units - step) : Math.min(0, units + step);
        return after == 0 ? 0 : ((after + DEPTH_BIAS) << 16) | (texel & 0xFFFF);
    }

    /**
     * How deep a footprint presses at a point, by its shape's distance: full depth over its inner
     * part, easing to nothing at its edge, then a raised rim easing up and back down across {@code
     * rimWidth} past it.
     *
     * @param s How far out the point is, 1 at the edge, by the shape's own measure.
     * @param edge How far out the press starts easing, 0 to 1.
     * @param depth How deep it presses, in meters.
     * @param rim How far out the rim reaches past the edge, by the same measure.
     * @param rimHeight How high the rim rises, in meters.
     * @return The depth, in meters; negative on the rim; zero outside.
     */
    public static double profile(
            double s, double edge, double depth, double rim, double rimHeight) {
        if (s <= edge) {
            return depth;
        }
        if (s < 1) {
            final double t = (s - edge) / (1 - edge);
            return depth * (1 - t * t * (3 - 2 * t));
        }
        if (rim > 0 && s < 1 + rim) {
            final double t = (s - 1) / rim;
            return -rimHeight * Math.sin(Math.PI * t);
        }
        return 0;
    }
}
