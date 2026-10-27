package com.ikalagaming.graphics.vulkan;

/**
 * Wind waves, see {@code water_waves.comp} and {@code water.glsl}: the spectrum the wind raises,
 * spread over directions, and its sampling into the periodic 1D profiles the surface is built from,
 * after Water Surface Wavelets (Jeschke et al. 2018). The surface's height at a point is the sum
 * over 16 directions of each direction's amplitude times its profile at the point's distance along
 * it; each profile sums the spectrum's waves, at whole harmonics of the profile's length so it
 * tiles exactly, each with a fixed random phase, and each direction's profile turns every wave's
 * phase by its own amount, so no two directions share a profile and no crest pattern repeats across
 * them. Keep in step with the shaders.
 *
 * <p>The spectrum is JONSWAP (Hasselmann et al. 1973) for a wind blowing over a fetch, becoming
 * Pierson-Moskowitz (1964) for a fully developed sea: S(w) = alpha g^2 w^-5 exp(-5/4 (wp / w)^4)
 * gamma^r. Waves go as w^2 = g k + (tension / density) k^3, so the smallest ones are capillary.
 * Directions spread as (2 / pi) cos^2 of the angle from the wind, none against it.
 */
public final class WaveMath {

    /** Gravity, in meters a second squared. */
    public static final double GRAVITY = 9.81;

    /** Water's surface tension over its density, in cubic meters a second squared. */
    public static final double TENSION = 7.28e-5;

    /** Phillips' constant for a fully developed sea (Pierson-Moskowitz). */
    public static final double PM_ALPHA = 0.0081;

    /** A fully developed sea's peak frequency times the wind speed over gravity. */
    public static final double PM_PEAK = 0.855;

    /** JONSWAP's alpha: this times (U^2 / (F g))^0.22. */
    public static final double JONSWAP_ALPHA = 0.076;

    /** The power in JONSWAP's alpha. */
    public static final double JONSWAP_ALPHA_POWER = 0.22;

    /** JONSWAP's peak frequency: this times (g^2 / (U F))^(1/3). */
    public static final double JONSWAP_PEAK = 22;

    /** JONSWAP's peak enhancement for a sea still growing. */
    public static final double JONSWAP_GAMMA = 3.3;

    /** The peak's width below and above the peak frequency. */
    public static final double SIGMA_BELOW = 0.07;

    public static final double SIGMA_ABOVE = 0.09;

    /** How sharply the spectrum falls below its peak: exp(-5/4 (wp / w)^4). */
    public static final double PEAK_SHAPE = 1.25;

    /**
     * The depths each climate's profiles are worked out for, in meters: deep water, then shallower,
     * where waves are shorter, slower and taller. Water between two is drawn from both.
     */
    public static final double[] DEPTH_LEVELS = {Double.POSITIVE_INFINITY, 3, 1};

    /** How many depths a climate's profiles are worked out for. */
    public static final int DEPTHS = DEPTH_LEVELS.length;

    /**
     * How long a current's two phases take to come round, in seconds: the surface's pattern is
     * carried by the flow for half of it before fading out for the other phase's, so it never
     * stretches far, see {@code water.glsl}.
     */
    public static final double FLOW_CYCLE = 4;

    /** How far a wave's height may be from deep water's and still count as the same, a share. */
    public static final double SAME_HEIGHT = 0.02;

    /**
     * The share of their energy some waves may have in waves shallow water changes, and still be
     * drawn from deep water's profiles there.
     */
    public static final double SAME_AS_DEEP = 0.01;

    /** Monahan and O'Muircheartaigh's whitecap coverage's factor, see {@link #whitecapCoverage}. */
    public static final double WHITECAP_SCALE = 2.95e-6;

    /** Its power of the wind's speed. */
    public static final double WHITECAP_POWER = 3.52;

    /**
     * How much of the whitecaps' coverage is foam still being made by breaking crests, stage A in
     * Monahan and Lu's (1990) terms, rather than the foam they leave behind, stage B: measurements
     * put stage B at 1.5 to 40 times stage A (Callaghan et al. 2012; Yang et al.); this takes the
     * middle of that, about 7.7 times.
     */
    public static final double ACTIVE_WHITECAP_SHARE = 0.115;

    /** How long the foam breaking crests leave takes to fade to 1/e, in seconds. */
    public static final double FOAM_LIFETIME = 4;

    /** The most of the sea whitecaps cover, past the fit's measured winds. */
    public static final double MAX_WHITECAP_COVERAGE = 0.2;

    /**
     * The cubic term of the tanh form of the normal distribution's cumulative function, see {@link
     * #normalCdf}.
     */
    private static final double NORMAL_CDF_CUBIC = 0.044_715;

    /** Steps of the bisection that inverts {@link #normalCdf}. */
    private static final int NORMAL_CDF_STEPS = 60;

    /** How far out in standard deviations the inverse of {@link #normalCdf} looks. */
    private static final double NORMAL_CDF_REACH = 10;

    /** The foam Jacobian of a climate without whitecaps, which no surface falls under. */
    public static final double NO_FOAM_JACOBIAN = -1000;

    /**
     * How often the water's patterns fixed in the world repeat, in meters: the camera's place is
     * kept to it for the shaders, so it stays precise; a whole number of each pattern's cells.
     */
    public static final double PATTERN_WRAP = 614.4;

    /** How often falling sheets' patterns repeat in time, in seconds. */
    public static final double SHEET_TIME_PERIOD = 512;

    /** The most shoaling raises a wave, short of where it breaks. */
    public static final double MAX_SHOALING = 1.6;

    /** Threads in a workgroup of the profiles' compute shader. */
    public static final int WORKGROUP_SIZE = 64;

    /** The directions the surface sums over, evenly around. */
    public static final int DIRECTIONS = 16;

    /** How many waves each profile sums. */
    public static final int WAVES = 128;

    /** How many samples a profile holds over its length. */
    public static final int PROFILE_SAMPLES = 4096;

    /** A profile's length, in meters: at least twice the longest wave it holds. */
    public static final double PROFILE_LENGTH = 160;

    /** The shortest wave a profile holds, in meters, past which it can't sample them. */
    public static final double SHORTEST_WAVE = 2 * PROFILE_LENGTH / PROFILE_SAMPLES;

    /**
     * The shortest wave each layer of a profile holds, in meters: the first holds them all, the
     * others leave the shorter ones out for surfaces seen from afar, which stand in for them with
     * roughness.
     */
    public static final double[] LAYER_CUTOFFS = {0, 0.25, 1, 4};

    /** How many layers a profile has. */
    public static final int LAYERS = LAYER_CUTOFFS.length;

    /** The profile's longest wave, as a harmonic of its length: half the length. */
    private static final double LONGEST_HARMONIC = 2;

    /** The PCG hash's constants, see {@link #directionPhase}. */
    private static final int PCG_MULTIPLIER = 747_796_405;

    private static final int PCG_INCREMENT = (int) 2891336453L;

    private static final int PCG_SHIFT_BITS = 28;

    private static final int PCG_SHIFT_BASE = 4;

    private static final int PCG_WORD = 277_803_737;

    private static final int PCG_FINAL_SHIFT = 22;

    /** Bits dropped from the hash to make a fraction, keeping 24, a float's precision. */
    private static final int PCG_DROP_BITS = 8;

    /** Steps of the bisection that finds a wavenumber, to well below a float's precision. */
    private static final int BISECTION_STEPS = 60;

    /** The step a group speed's derivative takes, as a share of the wavenumber. */
    private static final double DERIVATIVE_STEP = 1e-6;

    /** A full turn. */
    private static final double TURN = 2 * Math.PI;

    /** Exponent bits mixed into the phase hash. */
    private static final long HASH_MULTIPLIER = 0x9E3779B97F4A7C15L;

    /**
     * A sea's waves as one profile samples them: wavenumbers, the amplitude of each, and their
     * fixed random phases.
     *
     * @param wavenumbers Each wave's wavenumber, per meter, a whole harmonic of the profile.
     * @param amplitudes Each wave's amplitude, in meters, from the spectrum around it.
     * @param phases Each wave's phase at time 0, radians.
     * @param frequencies Each wave's angular frequency, radians a second: in deep water it follows
     *     from the wavenumber, and in shallow water the wavenumber follows from it.
     */
    public record Waves(
            double[] wavenumbers, double[] amplitudes, double[] phases, double[] frequencies) {

        /**
         * Waves in deep water, their frequencies from their wavenumbers.
         *
         * @param wavenumbers Each wave's wavenumber.
         * @param amplitudes Each wave's amplitude.
         * @param phases Each wave's phase at time 0.
         */
        public Waves(double[] wavenumbers, double[] amplitudes, double[] phases) {
            this(wavenumbers, amplitudes, phases, deepFrequencies(wavenumbers));
        }

        /**
         * Each wavenumber's frequency in deep water.
         *
         * @param wavenumbers The wavenumbers.
         * @return The frequencies.
         */
        private static double[] deepFrequencies(double[] wavenumbers) {
            final double[] frequencies = new double[wavenumbers.length];
            for (int i = 0; i < wavenumbers.length; ++i) {
                frequencies[i] = frequency(wavenumbers[i]);
            }
            return frequencies;
        }
    }

    /** Static helpers only. */
    private WaveMath() {}

    /**
     * How fast a wave of a wavenumber turns, in deep water: w^2 = g k + (tension / density) k^3.
     *
     * @param wavenumber The wavenumber, per meter.
     * @return The angular frequency, radians a second.
     */
    public static double frequency(double wavenumber) {
        return Math.sqrt(GRAVITY * wavenumber + TENSION * wavenumber * wavenumber * wavenumber);
    }

    /**
     * How fast a wave of a wavenumber turns in water of a depth: w^2 = (g k + (tension / density)
     * k^3) tanh(k h), so waves in water shallower than about half their length slow down.
     *
     * @param wavenumber The wavenumber, per meter.
     * @param depth The water's depth, in meters; infinite for deep water.
     * @return The angular frequency, radians a second.
     */
    public static double frequency(double wavenumber, double depth) {
        if (!Double.isFinite(depth)) {
            return frequency(wavenumber);
        }
        return frequency(wavenumber) * Math.sqrt(Math.tanh(wavenumber * depth));
    }

    /**
     * The wavenumber a wave of some frequency has in water of a depth, solving the dispersion
     * relation by bisection: shallow water makes the same wave shorter.
     *
     * @param frequency The angular frequency, radians a second.
     * @param depth The water's depth, in meters; infinite for deep water.
     * @return The wavenumber, per meter.
     */
    public static double wavenumberAt(double frequency, double depth) {
        double low = 0;
        double high = frequency * frequency / GRAVITY;
        while (frequency(high, depth) < frequency) {
            high *= 2;
        }
        for (int i = 0; i < BISECTION_STEPS; ++i) {
            final double middle = (low + high) / 2;
            if (frequency(middle, depth) < frequency) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) / 2;
    }

    /**
     * How fast a wave's energy travels in water of a depth, dw / dk, worked out numerically.
     *
     * @param wavenumber The wavenumber, per meter.
     * @param depth The depth, in meters; infinite for deep water.
     * @return The group speed, in meters a second.
     */
    public static double groupSpeed(double wavenumber, double depth) {
        final double step = wavenumber * DERIVATIVE_STEP;
        return (frequency(wavenumber + step, depth) - frequency(wavenumber - step, depth))
                / (2 * step);
    }

    /**
     * How much a wave from deep water grows coming into water of a depth: its energy flux kept as
     * its group speed falls, sqrt(cg deep / cg), up to {@link #MAX_SHOALING}, short of where it
     * would break.
     *
     * @param deepWavenumber Its wavenumber in deep water, per meter.
     * @param depth The depth, in meters.
     * @return The factor on its height.
     */
    public static double shoaling(double deepWavenumber, double depth) {
        if (!Double.isFinite(depth)) {
            return 1;
        }
        final double shallow = wavenumberAt(frequency(deepWavenumber), depth);
        final double factor =
                Math.sqrt(
                        groupSpeed(deepWavenumber, Double.POSITIVE_INFINITY)
                                / groupSpeed(shallow, depth));
        return Math.min(MAX_SHOALING, factor);
    }

    /**
     * Deep water's waves as they are in water of a depth: the same frequencies and phases, so they
     * turn in step, with the wavenumbers the depth gives them, rounded to whole harmonics of the
     * profile, and the heights shoaling gives them.
     *
     * @param deep The waves in deep water.
     * @param depth The depth, in meters; infinite for deep water.
     * @return The waves there.
     */
    public static Waves atDepth(Waves deep, double depth) {
        if (!Double.isFinite(depth)) {
            return deep;
        }
        final double fundamental = TURN / PROFILE_LENGTH;
        final int count = deep.wavenumbers().length;
        final double[] wavenumbers = new double[count];
        final double[] amplitudes = new double[count];
        for (int i = 0; i < count; ++i) {
            final double k = wavenumberAt(deep.frequencies()[i], depth);
            wavenumbers[i] = Math.max(1, Math.rint(k / fundamental)) * fundamental;
            amplitudes[i] = deep.amplitudes()[i] * shoaling(deep.wavenumbers()[i], depth);
        }
        return new Waves(wavenumbers, amplitudes, deep.phases(), deep.frequencies());
    }

    /**
     * How much of some waves' energy is in waves that shallow water changes: a different
     * wavenumber, or a height off by more than {@link #SAME_HEIGHT}. Where it is little, the
     * shallow water's profiles can be deep water's.
     *
     * @param deep The waves in deep water.
     * @param shallow The same waves in shallow water, see {@link #atDepth}.
     * @return The share of the deep waves' energy, 0 to 1.
     */
    public static double changedShare(Waves deep, Waves shallow) {
        double total = 0;
        double changed = 0;
        for (int i = 0; i < deep.wavenumbers().length; ++i) {
            final double amplitude = deep.amplitudes()[i];
            final double energy = amplitude * amplitude;
            total += energy;
            if (deep.wavenumbers()[i] != shallow.wavenumbers()[i]
                    || Math.abs(shallow.amplitudes()[i] - amplitude) > SAME_HEIGHT * amplitude) {
                changed += energy;
            }
        }
        return total > 0 ? changed / total : 0;
    }

    /**
     * Which depth's profiles each of {@link #DEPTH_LEVELS} can use: its own, or deep water's where
     * no more than {@link #SAME_AS_DEEP} of the energy is in waves it changes.
     *
     * @param atDepths The waves at each depth, deep water's first.
     * @return The level each depth reads.
     */
    public static int[] levelsUsed(Waves[] atDepths) {
        final int[] levels = new int[atDepths.length];
        for (int level = 1; level < atDepths.length; ++level) {
            levels[level] = changedShare(atDepths[0], atDepths[level]) > SAME_AS_DEEP ? level : 0;
        }
        return levels;
    }

    /**
     * The spectrum's peak frequency for a wind over a fetch, no lower than a fully developed sea's.
     *
     * @param windSpeed The wind's speed 10 m up, in meters a second.
     * @param fetch How far it has blown over the water, in meters; infinite for the open sea.
     * @return The peak's angular frequency.
     */
    public static double peakFrequency(double windSpeed, double fetch) {
        final double developed = PM_PEAK * GRAVITY / windSpeed;
        if (!Double.isFinite(fetch)) {
            return developed;
        }
        final double growing =
                JONSWAP_PEAK * Math.cbrt(GRAVITY * GRAVITY / (windSpeed * Math.max(fetch, 1)));
        return Math.max(developed, growing);
    }

    /**
     * The spectrum's energy at a frequency: the sea surface's height variance per unit of angular
     * frequency, JONSWAP over a fetch, Pierson-Moskowitz on the open sea.
     *
     * @param frequency The angular frequency, radians a second.
     * @param windSpeed The wind's speed 10 m up, in meters a second.
     * @param fetch How far it has blown over the water, in meters; infinite for the open sea.
     * @return The variance density, in square meters per radian a second.
     */
    public static double spectrum(double frequency, double windSpeed, double fetch) {
        if (frequency <= 0 || windSpeed <= 0) {
            return 0;
        }
        final double peak = peakFrequency(windSpeed, fetch);
        final boolean developed = !Double.isFinite(fetch) || peak <= PM_PEAK * GRAVITY / windSpeed;
        final double alpha =
                developed
                        ? PM_ALPHA
                        : Math.max(
                                PM_ALPHA,
                                JONSWAP_ALPHA
                                        * Math.pow(
                                                windSpeed * windSpeed / (fetch * GRAVITY),
                                                JONSWAP_ALPHA_POWER));
        final double ratio = peak / frequency;
        double value =
                alpha
                        * GRAVITY
                        * GRAVITY
                        / Math.pow(frequency, 5)
                        * Math.exp(-PEAK_SHAPE * ratio * ratio * ratio * ratio);
        if (!developed) {
            final double sigma = frequency <= peak ? SIGMA_BELOW : SIGMA_ABOVE;
            final double offset = (frequency - peak) / (sigma * peak);
            value *= Math.pow(JONSWAP_GAMMA, Math.exp(-0.5 * offset * offset));
        }
        return value;
    }

    /**
     * The spectrum's energy at a wavenumber: its energy at the wave's frequency times how fast the
     * frequency changes with wavenumber.
     *
     * @param wavenumber The wavenumber, per meter.
     * @param windSpeed The wind's speed, in meters a second.
     * @param fetch The fetch, in meters; infinite for the open sea.
     * @return The variance density, in square meters per unit wavenumber.
     */
    public static double wavenumberSpectrum(double wavenumber, double windSpeed, double fetch) {
        final double frequency = frequency(wavenumber);
        final double slope = (GRAVITY + 3 * TENSION * wavenumber * wavenumber) / (2 * frequency);
        return spectrum(frequency, windSpeed, fetch) * slope;
    }

    /**
     * How much of the waves' energy goes in a direction: (2 / pi) cos^2 of the angle from the wind,
     * none against it, summing to 1 around.
     *
     * @param angle The direction, radians.
     * @param windAngle The way the wind blows, radians.
     * @return The share per radian.
     */
    public static double spreading(double angle, double windAngle) {
        final double cos = Math.cos(angle - windAngle);
        return cos > 0 ? 2 / Math.PI * cos * cos : 0;
    }

    /**
     * Each of the surface's directions' amplitude: the square root of the spreading times the angle
     * each direction stands for, so the directions' energies sum to the spectrum's.
     *
     * @param windAngle The way the wind blows, radians, from +x toward +z.
     * @return The amplitudes, direction b at angle 2 pi b / {@link #DIRECTIONS}.
     */
    public static double[] directionAmplitudes(double windAngle) {
        final double[] amplitudes = new double[DIRECTIONS];
        final double step = TURN / DIRECTIONS;
        for (int b = 0; b < DIRECTIONS; ++b) {
            amplitudes[b] = Math.sqrt(spreading(b * step, windAngle) * step);
        }
        return amplitudes;
    }

    /**
     * Sample a sea into waves for a profile: wavenumbers spread evenly in their log from the
     * longest wave the profile holds, half its length, to the shortest, rounded to whole harmonics
     * so the profile tiles, each with the amplitude sqrt(2 S(k) dk) of the band it stands for.
     *
     * @param windSpeed The wind's speed, in meters a second.
     * @param fetch The fetch, in meters; infinite for the open sea.
     * @param seed What picks the phases.
     * @return The waves, as many as distinct harmonics, up to {@link #WAVES}.
     */
    public static Waves sample(double windSpeed, double fetch, long seed) {
        final double fundamental = TURN / PROFILE_LENGTH;
        final double lowest = LONGEST_HARMONIC;
        final double highest = PROFILE_LENGTH / SHORTEST_WAVE;
        final double[] harmonics = new double[WAVES];
        int count = 0;
        for (int i = 0; i < WAVES; ++i) {
            final double n =
                    Math.rint(lowest * Math.pow(highest / lowest, i / (double) (WAVES - 1)));
            if (count == 0 || n > harmonics[count - 1]) {
                harmonics[count++] = n;
            }
        }
        final double[] wavenumbers = new double[count];
        final double[] amplitudes = new double[count];
        final double[] phases = new double[count];
        for (int i = 0; i < count; ++i) {
            // The band from halfway to the harmonic below to halfway to the one above
            final double low = i == 0 ? harmonics[i] - 0.5 : (harmonics[i - 1] + harmonics[i]) / 2;
            final double high =
                    i == count - 1 ? harmonics[i] + 0.5 : (harmonics[i] + harmonics[i + 1]) / 2;
            final double k = harmonics[i] * fundamental;
            wavenumbers[i] = k;
            amplitudes[i] =
                    Math.sqrt(
                            2
                                    * wavenumberSpectrum(k, windSpeed, fetch)
                                    * (high - low)
                                    * fundamental);
            phases[i] = TURN * unitHash(seed, i);
        }
        return new Waves(wavenumbers, amplitudes, phases);
    }

    /**
     * The significant wave height a sea's waves add up to, four times the root of their height
     * variance, which sums half the square of each amplitude.
     *
     * @param waves The waves.
     * @return The height, in meters.
     */
    public static double significantHeight(Waves waves) {
        double variance = 0;
        for (double amplitude : waves.amplitudes()) {
            variance += amplitude * amplitude / 2;
        }
        return 4 * Math.sqrt(variance);
    }

    /**
     * The variance of the sum the choppy waves' Jacobian is made of, a k cos (k p + phase), over
     * the waves at least some long: half of each one's (a k)^2, the same as the slope's along one
     * direction.
     *
     * @param waves The waves, with their full amplitudes.
     * @param shortest The shortest wave counted, in meters; 0 for all.
     * @return The variance, for a direction's whole share of the waves.
     */
    public static double jacobianVariance(Waves waves, double shortest) {
        double variance = 0;
        for (int i = 0; i < waves.wavenumbers().length; ++i) {
            if (TURN / waves.wavenumbers()[i] < shortest) {
                continue;
            }
            final double slope = waves.amplitudes()[i] * waves.wavenumbers()[i];
            variance += slope * slope / 2;
        }
        return variance;
    }

    /**
     * How much of the sea whitecaps cover in a wind, as Monahan and O'Muircheartaigh (1980) fit it:
     * {@link #WHITECAP_SCALE} U^{@link #WHITECAP_POWER}, U the wind 10 m up in meters a second, up
     * to {@link #MAX_WHITECAP_COVERAGE}.
     *
     * @param windSpeed The wind's speed, in meters a second.
     * @return The share covered, 0 to 1.
     */
    public static double whitecapCoverage(double windSpeed) {
        if (windSpeed <= 0) {
            return 0;
        }
        return Math.min(
                MAX_WHITECAP_COVERAGE, WHITECAP_SCALE * Math.pow(windSpeed, WHITECAP_POWER));
    }

    /**
     * Where the choppy waves' Jacobian breaks into foam for a wind's waves, so that breaking crests
     * cover as much of the sea as actively breaking whitecaps do, {@link #ACTIVE_WHITECAP_SHARE} of
     * {@link #whitecapCoverage}; the foam they leave, the wave grid's, makes up the rest. The
     * surface squeezes by J = 1 - T, T = sum of a k cos (k p + phase) over every wave, which is
     * about normal with the variance of the slope along one direction, sum of (a k)^2 / 2; foam is
     * where J falls under the threshold, as often as whitecaps cover. Measured coverage, rather
     * than a fixed threshold, keeps the foam right where a profile leaves out the longest waves.
     * The inverse of {@link #normalCdf}, which the water shader uses too.
     *
     * @param waves The wind's waves, deep water's.
     * @param windSpeed The wind's speed, in meters a second.
     * @return The Jacobian foam starts under, or {@link #NO_FOAM_JACOBIAN} for a calm.
     */
    public static double foamJacobian(Waves waves, double windSpeed) {
        final double coverage = whitecapCoverage(windSpeed) * ACTIVE_WHITECAP_SHARE;
        final double variance = jacobianVariance(waves, 0);
        if (coverage <= 0 || variance <= 0) {
            return NO_FOAM_JACOBIAN;
        }
        return 1 - Math.sqrt(variance) * inverseNormalCdf(1 - coverage);
    }

    /**
     * The normal distribution's cumulative function, in the tanh form, 0.5 (1 + tanh(sqrt(2 / pi)
     * (x + 0.044715 x^3))), as the water shaders have it: close in the tail, where foam's chances
     * lie, unlike the logistic one, whose tail is several times too heavy there.
     *
     * @param x How many standard deviations.
     * @return The chance of falling under it.
     */
    public static double normalCdf(double x) {
        return 0.5 * (1 + Math.tanh(Math.sqrt(2 / Math.PI) * (x + NORMAL_CDF_CUBIC * x * x * x)));
    }

    /**
     * The inverse of {@link #normalCdf}, by bisection.
     *
     * @param chance The chance, 0 to 1.
     * @return How many standard deviations fall under it with that chance.
     */
    public static double inverseNormalCdf(double chance) {
        double low = -NORMAL_CDF_REACH;
        double high = NORMAL_CDF_REACH;
        for (int i = 0; i < NORMAL_CDF_STEPS; ++i) {
            final double middle = (low + high) / 2;
            if (normalCdf(middle) < chance) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) / 2;
    }

    /**
     * The slope variance of the waves a layer leaves out, which a surface seen through that layer
     * stands in for with roughness: half the square of each left out wave's slope amplitude, k a.
     *
     * @param waves The waves.
     * @param layer The layer.
     * @return The variance of the slope along one direction.
     */
    public static double missingSlopeVariance(Waves waves, int layer) {
        double variance = 0;
        for (int i = 0; i < waves.wavenumbers().length; ++i) {
            final double k = waves.wavenumbers()[i];
            if (TURN / k < LAYER_CUTOFFS[layer]) {
                final double slope = k * waves.amplitudes()[i];
                variance += slope * slope / 2;
            }
        }
        return variance;
    }

    /**
     * A direction's profile's height at a distance along it: the sum of its layer's waves at a
     * time, each turned by the direction's own phase for it.
     *
     * @param waves The waves.
     * @param direction Which direction, 0 to {@link #DIRECTIONS}.
     * @param layer Which layer: waves shorter than its cutoff are left out.
     * @param distance How far along the profile, in meters.
     * @param time The time, in seconds.
     * @return The height, in meters.
     */
    public static double profile(
            Waves waves, int direction, int layer, double distance, double time) {
        double sum = 0;
        for (int i = 0; i < waves.wavenumbers().length; ++i) {
            final double k = waves.wavenumbers()[i];
            if (TURN / k >= LAYER_CUTOFFS[layer]) {
                sum +=
                        waves.amplitudes()[i]
                                * Math.cos(
                                        k * distance
                                                + phaseAt(waves, i, time)
                                                + directionPhase(direction, i));
            }
        }
        return sum;
    }

    /**
     * How much a direction's profile turns a wave's phase, from a 32-bit hash of the two, as {@code
     * water_waves.comp} works it out (Jarzynski and Olano's PCG hash).
     *
     * @param direction The direction.
     * @param wave The wave's index.
     * @return The phase, 0 to 2 pi.
     */
    public static double directionPhase(int direction, int wave) {
        int state = (direction * WAVES + wave) * PCG_MULTIPLIER + PCG_INCREMENT;
        int word = ((state >>> ((state >>> PCG_SHIFT_BITS) + PCG_SHIFT_BASE)) ^ state) * PCG_WORD;
        word = (word >>> PCG_FINAL_SHIFT) ^ word;
        return TURN * (word >>> PCG_DROP_BITS) / (double) (1 << (Integer.SIZE - PCG_DROP_BITS));
    }

    /**
     * A wave's phase at a time, kept to one turn so it stays precise in floats however long the
     * game runs.
     *
     * @param waves The waves.
     * @param index Which wave.
     * @param time The time, in seconds.
     * @return The phase, 0 to 2 pi.
     */
    public static double phaseAt(Waves waves, int index, double time) {
        final double phase = waves.phases()[index] - waves.frequencies()[index] * time;
        return phase - TURN * Math.floor(phase / TURN);
    }

    /**
     * A number from 0 to 1 from a seed and an index, the same every run.
     *
     * @param seed The seed.
     * @param index The index.
     * @return The number.
     */
    public static double unitHash(long seed, long index) {
        long h = (seed + index) * HASH_MULTIPLIER;
        h ^= h >>> 31;
        h *= HASH_MULTIPLIER;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }
}
