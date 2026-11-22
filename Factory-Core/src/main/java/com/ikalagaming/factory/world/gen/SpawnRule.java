package com.ikalagaming.factory.world.gen;

/**
 * How a world type finds where new players start: the {@code spawn} field of a world type file,
 * such as {@code spawn:{anchor:{type:"floor", min_air:3}, origin:[I;0,0,0], radius:4096,
 * y_range:[I;-64,320]}}. See {@link SpawnFinder}.
 *
 * @param minAir How many blocks of air a spot needs above its floor.
 * @param originX Where the search starts, on the x axis.
 * @param originY Where the search starts, on the y axis. Used by the fallback when a column has no
 *     surface.
 * @param originZ Where the search starts, on the z axis.
 * @param radius How far from the origin the search may go, in blocks.
 * @param minY The lowest floor the search considers.
 * @param maxY The highest floor the search considers.
 */
public record SpawnRule(
        int minAir, int originX, int originY, int originZ, int radius, int minY, int maxY) {

    /** The anchor type the search supports: a floor to stand on. */
    public static final String FLOOR = "floor";

    /** How much air a floor needs above it when the rule doesn't say. */
    public static final int DEFAULT_MIN_AIR = 3;

    /** How far the search may go when the rule doesn't say. */
    public static final int DEFAULT_RADIUS = 4096;

    /** The lowest floor considered when the rule doesn't say. */
    public static final int DEFAULT_MIN_Y = -64;

    /** The highest floor considered when the rule doesn't say. */
    public static final int DEFAULT_MAX_Y = 320;

    /** The rule for world types that don't have one. */
    public static final SpawnRule DEFAULT =
            new SpawnRule(DEFAULT_MIN_AIR, 0, 0, 0, DEFAULT_RADIUS, DEFAULT_MIN_Y, DEFAULT_MAX_Y);
}
