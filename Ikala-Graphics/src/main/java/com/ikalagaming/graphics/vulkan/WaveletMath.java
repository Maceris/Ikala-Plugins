package com.ikalagaming.graphics.vulkan;

/**
 * The wave amplitude grid around the camera, after Water Surface Wavelets (Jeschke et al. 2018),
 * see {@code water_wavelets.comp}: for each cell and each of the {@link WaveMath#DIRECTIONS}
 * directions, how strong the waves going that way are. Two bands: the wind band is a factor on the
 * wind's own waves, 1 on open water, which land blocks and shores reflect and which the wind builds
 * back toward 1 behind them; the ripple band is the height of ripples that disturbances start,
 * which spread out, reflect and die away. Each step moves every direction's amplitude along it at
 * the band's speed (semi-Lagrangian), spreads a little into the neighboring directions so wave
 * fronts stay connected, and where the way back reaches land takes the amplitude of the direction
 * that reflects into this one. Past the grid the wind band is 1 and there are no ripples. Keep in
 * step with the shader.
 */
public final class WaveletMath {

    /** Cells along each side of the grid. */
    public static final int GRID_SIZE = 128;

    /** How wide a cell is, in meters. */
    public static final double CELL_SIZE = 1;

    /** The bands each cell holds per direction: the wind's factor, then ripples. */
    public static final int BANDS = 2;

    /** Which band is the wind's factor. */
    public static final int WIND_BAND = 0;

    /** Which band is ripples. */
    public static final int RIPPLE_BAND = 1;

    /**
     * Values a cell holds: each band's amplitude in each direction, then the share of it foam
     * covers and how hard it rains on it, in drops a square meter a second.
     */
    public static final int CELL_VALUES = BANDS * WaveMath.DIRECTIONS + 2;

    /**
     * Where in a cell the foam breaking crests left is, the share of the cell it covers, after the
     * bands.
     */
    public static final int FOAM_VALUE = BANDS * WaveMath.DIRECTIONS;

    /**
     * How fast the wind band's waves carry their amplitude, in meters a second: the group speed of
     * waves some 30 m long, a moderate sea's.
     */
    public static final double WIND_SPEED = 3.5;

    /** How fast ripples carry theirs: the group speed of ripples some 30 cm long. */
    public static final double RIPPLE_SPEED = 0.35;

    /** How long the wind takes to build its waves back behind land, in seconds. */
    public static final double WIND_RECOVERY = 20;

    /** How long ripples take to die down to 1 / e, in seconds. */
    public static final double RIPPLE_LIFETIME = 6;

    /** How much of the amplitude a shore sends back. */
    public static final double REFLECTION = 0.7;

    /** How fast amplitude spreads into the neighboring directions, per second. */
    public static final double ANGULAR_DIFFUSION = 0.5;

    /** The most disturbances taken in a frame; more wait for the next. */
    public static final int MAX_DISTURBANCES = 64;

    /** The most wave paints a step takes in. */
    public static final int MAX_PAINTS = 256;

    /** How narrowly a paint's waves keep to its way: the power on the cosine. */
    public static final int PAINT_SPREAD = 8;

    /** The shortest and longest ripples, in meters. */
    public static final double SHORTEST_RIPPLE = 0.08;

    public static final double LONGEST_RIPPLE = 1.5;

    /** How many ripple waves the ripple profiles sum. */
    public static final int RIPPLE_WAVES = 48;

    /**
     * How much of a paint's strength a direction gets: cos^{@link #PAINT_SPREAD} of the angle to
     * the paint's way, none past a right angle; mirrors {@code water_wavelets.comp}.
     *
     * @param direction The direction.
     * @param paintDirection The paint's way, in radians from +x toward +z.
     * @return The share, 0 to 1.
     */
    public static double paintShare(int direction, double paintDirection) {
        final double[] way = direction(direction);
        final double cos = way[0] * Math.cos(paintDirection) + way[1] * Math.sin(paintDirection);
        return Math.pow(Math.max(0, cos), PAINT_SPREAD);
    }

    /** A full turn. */
    private static final double TURN = 2 * Math.PI;

    /** Static helpers only. */
    private WaveletMath() {}

    /**
     * A direction's unit vector along the water, x and z.
     *
     * @param direction The direction, 0 to {@link WaveMath#DIRECTIONS}.
     * @return Its x and z.
     */
    public static double[] direction(int direction) {
        final double angle = TURN * direction / WaveMath.DIRECTIONS;
        return new double[] {Math.cos(angle), Math.sin(angle)};
    }

    /**
     * The direction whose waves a shore reflects into a direction: a shore of some outward normal
     * mirrors directions about itself, and mirroring is its own inverse, so the incoming direction
     * is the outgoing one mirrored, to the nearest of the {@link WaveMath#DIRECTIONS}.
     *
     * @param direction The direction the reflected waves go.
     * @param normalX The shore's normal along x, pointing out of the land, unit length with z.
     * @param normalZ Along z.
     * @return The direction they came in from.
     */
    public static int reflectedFrom(int direction, double normalX, double normalZ) {
        final double[] d = direction(direction);
        final double dot = d[0] * normalX + d[1] * normalZ;
        final double x = d[0] - 2 * dot * normalX;
        final double z = d[1] - 2 * dot * normalZ;
        final double angle = Math.atan2(z, x);
        final int nearest = (int) Math.round(angle / TURN * WaveMath.DIRECTIONS);
        return Math.floorMod(nearest, WaveMath.DIRECTIONS);
    }

    /**
     * How much of a disturbance's ripples go in a direction at a point: outward from its middle,
     * fading to none at its edge, so they leave as a ring.
     *
     * @param offsetX The point less the disturbance's middle, along x, in meters.
     * @param offsetZ Along z.
     * @param radius The disturbance's radius.
     * @param direction The direction.
     * @return The share, 0 to 1.
     */
    public static double disturbanceShare(
            double offsetX, double offsetZ, double radius, int direction) {
        final double distance = Math.hypot(offsetX, offsetZ);
        if (radius <= 0 || distance >= radius) {
            return 0;
        }
        final double fade = 1 - distance / radius;
        if (distance <= 0) {
            return fade;
        }
        final double[] d = direction(direction);
        final double outward = (d[0] * offsetX + d[1] * offsetZ) / distance;
        return fade * Math.max(0, outward);
    }

    /**
     * The ripples a disturbance starts: an even spread of wavelengths from {@link #SHORTEST_RIPPLE}
     * to {@link #LONGEST_RIPPLE}, at whole harmonics of a profile, with amplitudes that sum to a
     * height variance of one square meter, so a ripple band's amplitude is its ripples' root mean
     * square height.
     *
     * @param seed What picks their phases.
     * @return The ripples.
     */
    public static WaveMath.Waves ripples(long seed) {
        final double fundamental = TURN / WaveMath.PROFILE_LENGTH;
        final double lowest = WaveMath.PROFILE_LENGTH / LONGEST_RIPPLE;
        final double highest = WaveMath.PROFILE_LENGTH / SHORTEST_RIPPLE;
        final double[] wavenumbers = new double[RIPPLE_WAVES];
        final double[] amplitudes = new double[RIPPLE_WAVES];
        final double[] phases = new double[RIPPLE_WAVES];
        final double each = Math.sqrt(2.0 / RIPPLE_WAVES);
        for (int i = 0; i < RIPPLE_WAVES; ++i) {
            final double harmonic =
                    Math.rint(lowest * Math.pow(highest / lowest, i / (double) (RIPPLE_WAVES - 1)));
            wavenumbers[i] = harmonic * fundamental;
            amplitudes[i] = each;
            phases[i] = TURN * WaveMath.unitHash(seed, i);
        }
        return new WaveMath.Waves(wavenumbers, amplitudes, phases);
    }
}
