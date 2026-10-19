package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;

/**
 * Turns triangles finer than a resolution into a {@link CoverageVolume}, after Loubet and Neyret,
 * <i>Hybrid mesh-volume LoDs for all-scale pre-filtering of complex 3D assets</i> (2017), §5: each
 * voxel near the triangles casts rays a voxel long, from origins spread over the voxel and blurred
 * past it, in every direction, and how often they hit sets how much the voxel stops light. Blurring
 * the origins keeps a surface's coverage from flickering with where the grid happens to cut it.
 *
 * <p>Each hit adds its surface's look and an SGGX matrix for its facing and roughness (Heitz et
 * al., <i>The SGGX Microflake Distribution</i>, 2015, §4.3), which average linearly. A hit on a cut
 * out material passes with the chance of its average coverage, so leaves with holes stop as much
 * light as their texture covers. The density is then solved so the voxel's attenuation over a
 * voxel, averaged over every direction, is the share of rays that hit (§5.4).
 *
 * <p>Leaves in a voxel shade each other, which a voxel drawn as one medium can't show: its shadows
 * start {@link #SELF_SHADOW_REACH} voxels past where it stops light, so it doesn't shadow itself.
 * So each hit also casts a ray that far in a random direction, and the share that gets out scales
 * the voxel's color, as Loubet and Neyret's isotropic self-shadowing scales the albedo of light
 * scattered once (<i>A new microflake model with microscopic self-shadowing for accurate volume
 * downsampling</i>, 2018, §4.3). Without it far foliage is brighter and flatter than its leaves.
 *
 * <p>A voxel that is mostly one surface, like a stretch of wall or a single leaf, isn't a
 * scattering of independent flakes: every ray crossing the voxel through the surface meets it, and
 * the surface's roughness says nothing about which ways it faces. Rays a voxel long from inside it
 * cross such a surface only about half the time, and roughness spreads its flakes' facing nearly
 * evenly, so a wall would stop about half the light from every side. So where the triangle hits'
 * normals agree ({@link #COHERENT}), the voxel's flakes lie flat along their main direction, and
 * its density is set from how much of the voxel the surface covers seen along it, a cut out
 * material by its coverage: a wall then stops what crosses it face on and lets through what runs
 * along it. A voxel the surface only reaches through the origins' blur, which lines through the
 * voxel itself miss, keeps its density from its hits.
 */
public final class CoverageVoxelizer {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = 9;

    /** Rays per voxel. */
    static final int RAYS = 64;

    /** How far ray origins are blurred past their voxel, as a standard deviation in voxels. */
    private static final double BLUR = 0.6;

    /** Directions the density is solved over, spread evenly on the sphere. */
    private static final int DIRECTIONS = 64;

    /** Steps of the density's bisection. */
    private static final int STEPS = 40;

    /**
     * The most a voxel's attenuation over itself can be, so a voxel every ray hits keeps a finite
     * density: what is left gets through.
     */
    private static final double MOST_OPAQUE = 0.999;

    /**
     * How much of its triangle hits' normals one direction must hold, as a share of their outer
     * products' trace, for a voxel to be one surface rather than scattered parts: a plane holds all
     * of it, flakes facing every way a third, two crossed strips a half.
     */
    static final double COHERENT = 0.9;

    /** Lines a surface voxel's coverage along its normal is measured with. */
    private static final int ALONG_RAYS = 32;

    /** Steps of the power iteration that finds a surface voxel's main direction. */
    private static final int POWER_STEPS = 32;

    /**
     * How far leaves shade each other within a voxel's own medium, in voxels: as far past its stop
     * as a volume's shadow starts, see shadow_volume.frag's SHADOW_OFFSET.
     */
    private static final double SELF_SHADOW_REACH = 1.5;

    /** Rec. 709 luminance weights. */
    private static final double LUMA_RED = 0.2126;

    /** Rec. 709 luminance weight of green. */
    private static final double LUMA_GREEN = 0.7152;

    /** Rec. 709 luminance weight of blue. */
    private static final double LUMA_BLUE = 0.0722;

    /** How far apart samples of source volumes along a ray are, in their finest voxels. */
    private static final double SOURCE_STEP = 0.5;

    /** How far off its surface a hit's shadow ray starts, in voxels, so it misses that surface. */
    private static final double SHADOW_START = 1e-4;

    /**
     * How much of the light a voxel must stop over its width to be taken as stopping all of it:
     * nearly solid voxels made solid change little to look at, and walks end in them at once rather
     * than carrying the rest of the light on through the voxels behind.
     */
    private static final double OPAQUE_ENOUGH = 0.9;

    /** The smoothest a voxel's microflakes are, by projected area across a surface. */
    private static final double MIN_SPREAD = 0.05;

    /** The golden angle, for spreading directions. */
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    /** How finely triangles are sampled to find the voxels they reach, in voxels. */
    private static final double SAMPLE_SPACING = 0.5;

    /** The look of a material with no average: mid gray, rough, not metal. */
    private static final float[] DEFAULT_LOOK = {0.18f, 0.18f, 0.18f, 0.8f, 0, 0.5f};

    /** The directions the density is solved over. */
    private static final double[][] SPHERE = sphere();

    /**
     * Voxelize triangles.
     *
     * @param triangles Their corners, 9 floats each, in the DAG's frame.
     * @param materials Each triangle's material.
     * @param averages What each material looks like, and how much of it its texture covers; null
     *     for mid gray, fully covered.
     * @param voxelSize How wide a voxel is.
     * @param seed Where the rays' random numbers start, so the same input gives the same volume.
     * @return The volume, with no voxels if nothing stops a ray.
     */
    public static @NonNull CoverageVolume voxelize(
            float @NonNull [] triangles,
            int @NonNull [] materials,
            MaterialAverages averages,
            double voxelSize,
            long seed) {
        return voxelize(triangles, materials, averages, List.of(), voxelSize, seed);
    }

    /**
     * Voxelize triangles together with finer volumes, as a parent region cell does with the volumes
     * its children made from leaves it doesn't have. A ray crossing a source volume is stopped in
     * it with the chance its optical depth there gives, before any triangle behind, and then takes
     * on the source voxel's microflakes and look. Sources' colors already carry their own
     * self-shadowing, so a hit in one only adds what lies past their reach.
     *
     * @param triangles Their corners, 9 floats each, in the DAG's frame.
     * @param materials Each triangle's material.
     * @param averages What each material looks like, and how much of it its texture covers; null
     *     for mid gray, fully covered.
     * @param sources The finer volumes, in the same frame, with voxels no wider than these.
     * @param voxelSize How wide a voxel is.
     * @param seed Where the rays' random numbers start, so the same input gives the same volume.
     * @return The volume, with no voxels if nothing stops a ray.
     */
    public static @NonNull CoverageVolume voxelize(
            float @NonNull [] triangles,
            int @NonNull [] materials,
            MaterialAverages averages,
            @NonNull List<CoverageVolume> sources,
            double voxelSize,
            long seed) {
        final int n = triangles.length / TRIANGLE_VALUES;
        final Sources medium = new Sources(sources);
        final double[] origin = new double[AXES];
        Arrays.fill(origin, Double.POSITIVE_INFINITY);
        for (int i = 0; i < triangles.length; ++i) {
            origin[i % AXES] = Math.min(origin[i % AXES], triangles[i]);
        }
        medium.lowest(origin);
        final boolean empty = n == 0 && medium.isEmpty();
        for (int axis = 0; axis < AXES; ++axis) {
            // A voxel of room below, for the blur to reach into
            origin[axis] = empty ? 0 : (Math.floor(origin[axis] / voxelSize) - 1) * voxelSize;
        }
        final TreeSet<Long> reached = new TreeSet<>();
        candidates(triangles, origin, voxelSize, reached);
        medium.candidates(origin, voxelSize, reached);
        final long[] candidates = reached.stream().mapToLong(Long::longValue).toArray();
        final TriangleBvh bvh = n == 0 ? null : new TriangleBvh(triangles);
        final Shadows shadows = new Shadows(bvh, triangles, medium);
        final int[] stack = new int[TriangleBvh.STACK_SIZE];
        final double[] start = new double[AXES];
        final double[] ray = new double[AXES];
        final double[] along = new double[1];
        final double[] normal = new double[AXES];
        final double[] point = new double[AXES];
        final int[] stoppedIn = new int[2];
        final int count = candidates.length;
        final long[] keys = new long[count];
        final float[] density = new float[count];
        final float[] sggx = new float[count * CoverageVolume.SGGX_VALUES];
        final float[] color = new float[count * CoverageVolume.COLOR_VALUES];
        final float[] roughness = new float[count];
        final float[] metallic = new float[count];
        final int[] mainMaterial = new int[count];
        final float[] shade = new float[count];
        final java.util.Map<Integer, Integer> hitsBy = new java.util.HashMap<>();
        final double[] matrix = new double[CoverageVolume.SGGX_VALUES];
        // Hits' normals, not spread by roughness, for whether they are one surface
        final double[] facing = new double[CoverageVolume.SGGX_VALUES];
        final double[] look = new double[MaterialAverages.VALUES];
        final float[][] lookOf = new float[n][];
        final float[] coverageOf = new float[n];
        for (int t = 0; t < n; ++t) {
            float[] average = averages == null ? null : averages.average(materials[t]);
            lookOf[t] = average == null ? DEFAULT_LOOK : average;
            coverageOf[t] = averages == null ? 1 : averages.coverage(materials[t]);
        }
        shadows.coverageOf = coverageOf;
        final double reach = SELF_SHADOW_REACH * voxelSize;
        int kept = 0;
        for (long key : candidates) {
            final SplittableRandom random = new SplittableRandom(seed ^ key * 0x9E3779B97F4A7C15L);
            int hits = 0;
            int triangleHits = 0;
            int lit = 0;
            Arrays.fill(facing, 0);
            Arrays.fill(matrix, 0);
            Arrays.fill(look, 0);
            hitsBy.clear();
            for (int r = 0; r < RAYS; ++r) {
                for (int axis = 0; axis < AXES; ++axis) {
                    start[axis] =
                            origin[axis]
                                    + (CoverageVolume.coordinate(key, axis)
                                                    + random.nextDouble()
                                                    + BLUR * random.nextGaussian())
                                            * voxelSize;
                }
                randomDirection(random, ray);
                for (int axis = 0; axis < AXES; ++axis) {
                    ray[axis] *= voxelSize;
                }
                final int t = bvh == null ? -1 : bvh.firstHit(start, ray, stack, along);
                final double share = t < 0 ? 1 : along[0];
                // The sources' medium in front of the triangle stops the ray first, with the
                // chance its optical depth there gives
                if (!medium.isEmpty()) {
                    final double threshold = -Math.log(1 - random.nextDouble());
                    final double stop = medium.stop(start, ray, share, threshold, stoppedIn);
                    if (stop >= 0) {
                        ++hits;
                        final CoverageVolume source = sources.get(stoppedIn[0]);
                        final int voxel = stoppedIn[1];
                        hitsBy.merge(source.material(voxel), 1, Integer::sum);
                        for (int k = 0; k < CoverageVolume.SGGX_VALUES; ++k) {
                            matrix[k] += source.sggx(voxel, k);
                        }
                        for (int c = 0; c < CoverageVolume.COLOR_VALUES; ++c) {
                            look[MaterialAverages.RED + c] += source.color(voxel, c);
                        }
                        look[MaterialAverages.ROUGHNESS] += source.roughness(voxel);
                        look[MaterialAverages.METALLIC] += source.metallic(voxel);
                        look[MaterialAverages.SPECULAR] += DEFAULT_LOOK[MaterialAverages.SPECULAR];
                        for (int axis = 0; axis < AXES; ++axis) {
                            point[axis] = start[axis] + ray[axis] * stop;
                        }
                        // Only what lies past the source's own self-shadowing
                        lit +=
                                shadows.escapes(
                                                point,
                                                null,
                                                SELF_SHADOW_REACH * source.voxelSize(),
                                                reach,
                                                voxelSize,
                                                random)
                                        ? 1
                                        : 0;
                        continue;
                    }
                }
                if (t < 0 || random.nextDouble() >= coverageOf[t]) {
                    continue;
                }
                ++hits;
                hitsBy.merge(materials[t], 1, Integer::sum);
                faceNormal(triangles, t, normal);
                ++triangleHits;
                addSurface(facing, normal, 0);
                for (int axis = 0; axis < AXES; ++axis) {
                    point[axis] = start[axis] + ray[axis] * share;
                }
                lit += shadows.escapes(point, normal, 0, reach, voxelSize, random) ? 1 : 0;
                final float[] average = lookOf[t];
                final double alpha =
                        average[MaterialAverages.ROUGHNESS] * average[MaterialAverages.ROUGHNESS];
                addSurface(matrix, normal, Math.max(MIN_SPREAD, alpha));
                for (int k = 0; k < MaterialAverages.VALUES; ++k) {
                    look[k] += average[k];
                }
            }
            if (hits == 0) {
                continue;
            }
            keys[kept] = key;
            for (int k = 0; k < CoverageVolume.SGGX_VALUES; ++k) {
                sggx[kept * CoverageVolume.SGGX_VALUES + k] = (float) (matrix[k] / hits);
            }
            // Light scattered once leaves as much as gets past the voxel's own leaves
            final double shadowing = (double) lit / hits;
            for (int c = 0; c < CoverageVolume.COLOR_VALUES; ++c) {
                color[kept * CoverageVolume.COLOR_VALUES + c] =
                        (float) (look[MaterialAverages.RED + c] / hits * shadowing);
            }
            // The material most of its hits were on, and how bright it is against that one's look
            int main = -1;
            int most = 0;
            for (java.util.Map.Entry<Integer, Integer> entry : hitsBy.entrySet()) {
                if (entry.getValue() > most || entry.getValue() == most && entry.getKey() < main) {
                    main = entry.getKey();
                    most = entry.getValue();
                }
            }
            mainMaterial[kept] = main;
            final float[] mainLook = main < 0 || averages == null ? null : averages.average(main);
            final double own = luminance(color, kept * CoverageVolume.COLOR_VALUES);
            final double reference =
                    luminance(mainLook == null ? DEFAULT_LOOK : mainLook, MaterialAverages.RED);
            shade[kept] = (float) (reference <= 0 ? 1 : Math.clamp(own / reference, 0, 1));
            roughness[kept] = (float) (look[MaterialAverages.ROUGHNESS] / hits);
            metallic[kept] = (float) (look[MaterialAverages.METALLIC] / hits);
            // Mostly one surface: flat along it, as dense as it covers the voxel seen along its
            // normal
            final double[] surface = 2 * triangleHits >= hits ? mainDirection(facing) : null;
            final double covered =
                    surface == null
                            ? 0
                            : coveredAlong(
                                    bvh,
                                    coverageOf,
                                    origin,
                                    voxelSize,
                                    key,
                                    surface,
                                    random,
                                    stack,
                                    along);
            if (covered > 0) {
                final int at = kept * CoverageVolume.SGGX_VALUES;
                final double[] flat = new double[CoverageVolume.SGGX_VALUES];
                addSurface(flat, surface, MIN_SPREAD);
                for (int k = 0; k < CoverageVolume.SGGX_VALUES; ++k) {
                    sggx[at + k] = (float) flat[k];
                }
                final double stopped = covered >= OPAQUE_ENOUGH ? MOST_OPAQUE : covered;
                density[kept] =
                        (float)
                                (-Math.log(1 - Math.min(stopped, MOST_OPAQUE))
                                        / (CoverageVolume.projectedArea(sggx, at, surface)
                                                * voxelSize));
                ++kept;
                continue;
            }
            density[kept] =
                    (float)
                            solveDensity(
                                    sggx,
                                    kept * CoverageVolume.SGGX_VALUES,
                                    (double) hits / RAYS >= OPAQUE_ENOUGH
                                            ? MOST_OPAQUE
                                            : (double) hits / RAYS,
                                    voxelSize);
            ++kept;
        }
        return new CoverageVolume(
                origin,
                voxelSize,
                Arrays.copyOf(keys, kept),
                Arrays.copyOf(density, kept),
                Arrays.copyOf(sggx, kept * CoverageVolume.SGGX_VALUES),
                Arrays.copyOf(color, kept * CoverageVolume.COLOR_VALUES),
                Arrays.copyOf(roughness, kept),
                Arrays.copyOf(metallic, kept),
                Arrays.copyOf(mainMaterial, kept),
                Arrays.copyOf(shade, kept));
    }

    /**
     * The direction hits' normals mostly face, if one does: the main eigenvector of their outer
     * products' sum, found by power iteration, where its eigenvalue holds at least {@link
     * #COHERENT} of the trace.
     *
     * @param facing The sum, SGGX values: xx, yy, zz, xy, xz, yz.
     * @return The unit direction, or null if the normals don't agree enough.
     */
    static double[] mainDirection(double @NonNull [] facing) {
        final double trace = facing[0] + facing[1] + facing[2];
        if (trace <= 0) {
            return null;
        }
        // Not along any axis, so it isn't at right angles to the answer
        double[] v = {1, 0.7, 0.4};
        double length = 0;
        for (int step = 0; step < POWER_STEPS; ++step) {
            final double[] w = {
                facing[0] * v[0] + facing[3] * v[1] + facing[4] * v[2],
                facing[3] * v[0] + facing[1] * v[1] + facing[5] * v[2],
                facing[4] * v[0] + facing[5] * v[1] + facing[2] * v[2]
            };
            length = Math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]);
            if (length <= 0) {
                return null;
            }
            v = new double[] {w[0] / length, w[1] / length, w[2] / length};
        }
        return length / trace >= COHERENT ? v : null;
    }

    /**
     * How much of a voxel surfaces cover seen along a direction: the share of lines along it,
     * through random points of the voxel and clipped to it, that hit a triangle, a cut out one with
     * the chance of its coverage.
     *
     * @param bvh The triangles.
     * @param coverageOf Each triangle's coverage.
     * @param origin The grid's origin.
     * @param voxelSize How wide a voxel is.
     * @param key The voxel.
     * @param direction The direction, unit length.
     * @param random The voxel's random numbers.
     * @param stack Scratch for the queries.
     * @param along Scratch for the queries.
     * @return The share, 0 to 1.
     */
    private static double coveredAlong(
            @NonNull TriangleBvh bvh,
            float @NonNull [] coverageOf,
            double @NonNull [] origin,
            double voxelSize,
            long key,
            double @NonNull [] direction,
            @NonNull SplittableRandom random,
            int @NonNull [] stack,
            double @NonNull [] along) {
        int hits = 0;
        final double[] point = new double[AXES];
        final double[] start = new double[AXES];
        final double[] ray = new double[AXES];
        for (int r = 0; r < ALONG_RAYS; ++r) {
            // Where the line through a random point of the voxel enters and leaves it
            double enter = Double.NEGATIVE_INFINITY;
            double leave = Double.POSITIVE_INFINITY;
            for (int axis = 0; axis < AXES; ++axis) {
                final double low = origin[axis] + CoverageVolume.coordinate(key, axis) * voxelSize;
                point[axis] = low + random.nextDouble() * voxelSize;
                if (direction[axis] == 0) {
                    continue;
                }
                final double a = (low - point[axis]) / direction[axis];
                final double b = (low + voxelSize - point[axis]) / direction[axis];
                enter = Math.max(enter, Math.min(a, b));
                leave = Math.min(leave, Math.max(a, b));
            }
            for (int axis = 0; axis < AXES; ++axis) {
                start[axis] = point[axis] + direction[axis] * enter;
                ray[axis] = direction[axis] * (leave - enter);
            }
            final int t = bvh.firstHit(start, ray, stack, along);
            if (t >= 0 && random.nextDouble() < coverageOf[t]) {
                ++hits;
            }
        }
        return (double) hits / ALONG_RAYS;
    }

    /**
     * A linear color's luminance (Rec. 709).
     *
     * @param values The values.
     * @param at Where its red is.
     * @return The luminance.
     */
    static double luminance(float @NonNull [] values, int at) {
        return LUMA_RED * values[at] + LUMA_GREEN * values[at + 1] + LUMA_BLUE * values[at + 2];
    }

    /** Finer volumes a voxelization takes in, sampled along rays at half their finest voxel. */
    private static final class Sources {
        /** The volumes. */
        private final List<CoverageVolume> volumes;

        /** How far apart samples along a ray are. */
        private final double step;

        /** Scratch for a sample's position. */
        private final double[] at = new double[AXES];

        /** Scratch for a ray's unit direction. */
        private final double[] unit = new double[AXES];

        /**
         * Keep the volumes.
         *
         * @param volumes The volumes.
         */
        Sources(@NonNull List<CoverageVolume> volumes) {
            this.volumes = volumes;
            double finest = Double.POSITIVE_INFINITY;
            for (CoverageVolume volume : volumes) {
                finest = Math.min(finest, volume.voxelSize());
            }
            step = finest * SOURCE_STEP;
        }

        /**
         * Whether there are none.
         *
         * @return True if there are no voxels to sample.
         */
        boolean isEmpty() {
            for (CoverageVolume volume : volumes) {
                if (volume.size() > 0) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Lower a corner to below every source voxel.
         *
         * @param low The corner, lowered in place.
         */
        void lowest(double @NonNull [] low) {
            for (CoverageVolume volume : volumes) {
                for (int v = 0; v < volume.size(); ++v) {
                    for (int axis = 0; axis < AXES; ++axis) {
                        low[axis] =
                                Math.min(
                                        low[axis],
                                        volume.origin()[axis]
                                                + CoverageVolume.coordinate(volume.keyOf(v), axis)
                                                        * volume.voxelSize());
                    }
                }
            }
        }

        /**
         * Add the voxels of a grid that the source voxels reach, and those next to them.
         *
         * @param origin Where the grid starts.
         * @param voxelSize How wide its voxels are.
         * @param out Receives their keys.
         */
        void candidates(double @NonNull [] origin, double voxelSize, @NonNull Set<Long> out) {
            final long[] lo = new long[AXES];
            final long[] hi = new long[AXES];
            for (CoverageVolume volume : volumes) {
                final double size = volume.voxelSize();
                for (int v = 0; v < volume.size(); ++v) {
                    for (int axis = 0; axis < AXES; ++axis) {
                        final double min =
                                volume.origin()[axis]
                                        + CoverageVolume.coordinate(volume.keyOf(v), axis) * size;
                        lo[axis] =
                                Math.max(
                                        0, (long) Math.floor((min - origin[axis]) / voxelSize) - 1);
                        hi[axis] = (long) Math.floor((min + size - origin[axis]) / voxelSize) + 1;
                    }
                    for (long x = lo[0]; x <= hi[0]; ++x) {
                        for (long y = lo[1]; y <= hi[1]; ++y) {
                            for (long z = lo[2]; z <= hi[2]; ++z) {
                                out.add(CoverageVolume.key(x, y, z));
                            }
                        }
                    }
                }
            }
        }

        /**
         * Walk a ray through the sources, up to a share of it, to where its optical depth reaches a
         * threshold.
         *
         * @param start Where it starts.
         * @param ray Where it goes, its length its reach.
         * @param upTo The share of it walked.
         * @param threshold The optical depth it stops at.
         * @param stoppedIn Receives the source and voxel it stops in.
         * @return The share of the ray it stops at, or -1 if it gets through.
         */
        double stop(
                double @NonNull [] start,
                double @NonNull [] ray,
                double upTo,
                double threshold,
                int @NonNull [] stoppedIn) {
            final double length = toUnit(ray);
            double depth = 0;
            for (double d = step / 2; d < upTo * length; d += step) {
                for (int axis = 0; axis < AXES; ++axis) {
                    at[axis] = start[axis] + unit[axis] * d;
                }
                for (int s = 0; s < volumes.size(); ++s) {
                    final CoverageVolume volume = volumes.get(s);
                    final int v = volume.indexAt(at);
                    if (v < 0) {
                        continue;
                    }
                    depth += volume.density(v) * volume.projectedArea(v, unit) * step;
                    if (depth >= threshold) {
                        stoppedIn[0] = s;
                        stoppedIn[1] = v;
                        return d / length;
                    }
                }
            }
            return -1;
        }

        /**
         * The optical depth along a ray between two distances.
         *
         * @param start Where it starts.
         * @param direction Its unit direction.
         * @param from Where the stretch starts, as a distance.
         * @param to Where it ends.
         * @return The optical depth.
         */
        double depth(
                double @NonNull [] start, double @NonNull [] direction, double from, double to) {
            double depth = 0;
            for (double d = from + step / 2; d < to; d += step) {
                for (int axis = 0; axis < AXES; ++axis) {
                    at[axis] = start[axis] + direction[axis] * d;
                }
                for (CoverageVolume volume : volumes) {
                    final int v = volume.indexAt(at);
                    if (v >= 0) {
                        depth += volume.density(v) * volume.projectedArea(v, direction) * step;
                    }
                }
            }
            return depth;
        }

        /**
         * Put a ray's unit direction in {@link #unit}.
         *
         * @param ray The ray.
         * @return Its length.
         */
        private double toUnit(double @NonNull [] ray) {
            final double length = Math.sqrt(ray[0] * ray[0] + ray[1] * ray[1] + ray[2] * ray[2]);
            for (int axis = 0; axis < AXES; ++axis) {
                unit[axis] = ray[axis] / length;
            }
            return length;
        }
    }

    /** Shadow rays from hits, through the triangles and the sources' medium. */
    private static final class Shadows {
        /** The triangles, or null for none. */
        private final TriangleBvh bvh;

        /** Their corners. */
        private final float[] triangles;

        /** The sources' medium. */
        private final Sources medium;

        /** Each triangle's coverage, set before any ray is cast. */
        private float[] coverageOf;

        /** Scratch for queries. */
        private final int[] stack = new int[TriangleBvh.STACK_SIZE];

        /** Scratch for a ray's direction. */
        private final double[] direction = new double[AXES];

        /** Scratch for where a ray starts. */
        private final double[] from = new double[AXES];

        /** Scratch for a ray's reach. */
        private final double[] segment = new double[AXES];

        /** Scratch for a hit's share. */
        private final double[] along = new double[1];

        /**
         * Keep what shadows.
         *
         * @param bvh The triangles, or null for none.
         * @param triangles Their corners.
         * @param medium The sources' medium.
         */
        Shadows(TriangleBvh bvh, float @NonNull [] triangles, @NonNull Sources medium) {
            this.bvh = bvh;
            this.triangles = triangles;
            this.medium = medium;
        }

        /**
         * Whether light gets from a hit to a reach in a random direction, past the triangles, a cut
         * out triangle in the way letting it by with the chance of its coverage, and through the
         * sources' medium with the chance its optical depth gives.
         *
         * @param point The hit.
         * @param normal The hit triangle's unit normal, or null for a hit in a source.
         * @param skip How far along the ray nothing counts, already shaded for.
         * @param reach How far the ray goes.
         * @param voxelSize How wide a voxel is.
         * @param random The random numbers.
         * @return True if the light gets out.
         */
        boolean escapes(
                double @NonNull [] point,
                double[] normal,
                double skip,
                double reach,
                double voxelSize,
                @NonNull SplittableRandom random) {
            if (skip >= reach) {
                return true;
            }
            randomDirection(random, direction);
            final double side =
                    normal == null
                            ? 0
                            : Math.signum(
                                    direction[0] * normal[0]
                                            + direction[1] * normal[1]
                                            + direction[2] * normal[2]);
            for (int axis = 0; axis < AXES; ++axis) {
                // Off the surface on the side the ray leaves by
                from[axis] =
                        point[axis]
                                + direction[axis] * skip
                                + (normal == null ? 0 : side * normal[axis])
                                        * SHADOW_START
                                        * voxelSize;
                segment[axis] = direction[axis] * (reach - skip);
            }
            if (bvh != null) {
                final int blocker = bvh.firstHit(from, segment, stack, along);
                if (blocker >= 0 && random.nextDouble() < coverageOf[blocker]) {
                    return false;
                }
            }
            if (medium.isEmpty()) {
                return true;
            }
            return random.nextDouble() < Math.exp(-medium.depth(point, direction, skip, reach));
        }
    }

    /**
     * The voxels triangles reach, and those next to them, which blurred rays can hit them from.
     *
     * @param triangles The triangles' corners.
     * @param origin Where the grid starts.
     * @param voxelSize How wide a voxel is.
     * @param reached Receives their keys.
     */
    private static void candidates(
            float @NonNull [] triangles,
            double @NonNull [] origin,
            double voxelSize,
            @NonNull Set<Long> reached) {
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        final double[] point = new double[AXES];
        for (int t = 0; t < triangles.length / TRIANGLE_VALUES; ++t) {
            for (int axis = 0; axis < AXES; ++axis) {
                a[axis] = triangles[t * TRIANGLE_VALUES + axis];
                b[axis] = triangles[t * TRIANGLE_VALUES + AXES + axis];
                c[axis] = triangles[t * TRIANGLE_VALUES + 2 * AXES + axis];
            }
            Hausdorff.sample(
                    a,
                    b,
                    c,
                    SAMPLE_SPACING * voxelSize,
                    true,
                    point,
                    (p, area) -> {
                        final long x = (long) Math.floor((p[0] - origin[0]) / voxelSize);
                        final long y = (long) Math.floor((p[1] - origin[1]) / voxelSize);
                        final long z = (long) Math.floor((p[2] - origin[2]) / voxelSize);
                        for (int dx = -1; dx <= 1; ++dx) {
                            for (int dy = -1; dy <= 1; ++dy) {
                                for (int dz = -1; dz <= 1; ++dz) {
                                    if (x + dx >= 0 && y + dy >= 0 && z + dz >= 0) {
                                        reached.add(CoverageVolume.key(x + dx, y + dy, z + dz));
                                    }
                                }
                            }
                        }
                    });
        }
    }

    /**
     * Add a surface's SGGX matrix: its normal's projected area 1, and its roughness's across it, S
     * = n n^T + s^2 (I - n n^T).
     *
     * @param matrix The sum so far: xx, yy, zz, xy, xz, yz.
     * @param n The unit normal.
     * @param spread The projected area across the surface.
     */
    private static void addSurface(double @NonNull [] matrix, double @NonNull [] n, double spread) {
        final double s2 = spread * spread;
        final double keep = 1 - s2;
        matrix[0] += keep * n[0] * n[0] + s2;
        matrix[1] += keep * n[1] * n[1] + s2;
        matrix[2] += keep * n[2] * n[2] + s2;
        matrix[3] += keep * n[0] * n[1];
        matrix[4] += keep * n[0] * n[2];
        matrix[5] += keep * n[1] * n[2];
    }

    /**
     * The density at which a voxel's attenuation over its width, averaged over every direction, is
     * the chance a ray hit: 1 - mean(exp(-density sigma(w) width)) = hit chance, by bisection,
     * since the attenuation only grows with the density.
     *
     * @param sggx The SGGX values.
     * @param at Where the voxel's start.
     * @param hitChance The share of rays that hit.
     * @param width The voxel's width.
     * @return The density.
     */
    static double solveDensity(float @NonNull [] sggx, int at, double hitChance, double width) {
        double low = 0;
        double high = 1 / width;
        while (attenuation(sggx, at, high, width) < hitChance) {
            high *= 2;
        }
        for (int step = 0; step < STEPS; ++step) {
            final double middle = (low + high) / 2;
            if (attenuation(sggx, at, middle, width) < hitChance) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) / 2;
    }

    /**
     * How much light a voxel stops over its width, averaged over every direction.
     *
     * @param sggx The SGGX values.
     * @param at Where the voxel's start.
     * @param density Its density.
     * @param width Its width.
     * @return The share stopped.
     */
    static double attenuation(float @NonNull [] sggx, int at, double density, double width) {
        double through = 0;
        for (double[] w : SPHERE) {
            through += Math.exp(-density * CoverageVolume.projectedArea(sggx, at, w) * width);
        }
        return 1 - through / SPHERE.length;
    }

    /**
     * Directions spread evenly over the sphere, on a Fibonacci spiral.
     *
     * @return The unit directions.
     */
    private static double[][] sphere() {
        final double[][] out = new double[DIRECTIONS][];
        for (int i = 0; i < DIRECTIONS; ++i) {
            final double y = 1 - 2 * (i + 0.5) / DIRECTIONS;
            final double r = Math.sqrt(1 - y * y);
            final double phi = i * GOLDEN_ANGLE;
            out[i] = new double[] {r * Math.cos(phi), y, r * Math.sin(phi)};
        }
        return out;
    }

    /**
     * A random unit direction, uniform over the sphere.
     *
     * @param random The random numbers.
     * @param out Receives it.
     */
    private static void randomDirection(@NonNull SplittableRandom random, double @NonNull [] out) {
        final double y = 2 * random.nextDouble() - 1;
        final double r = Math.sqrt(1 - y * y);
        final double phi = 2 * Math.PI * random.nextDouble();
        out[0] = r * Math.cos(phi);
        out[1] = y;
        out[2] = r * Math.sin(phi);
    }

    /**
     * A triangle's unit normal.
     *
     * @param triangles The corners.
     * @param t The triangle.
     * @param out Receives the normal, up if it has no area.
     */
    private static void faceNormal(float @NonNull [] triangles, int t, double @NonNull [] out) {
        final int base = t * TRIANGLE_VALUES;
        final double ux = triangles[base + 3] - triangles[base];
        final double uy = triangles[base + 4] - triangles[base + 1];
        final double uz = triangles[base + 5] - triangles[base + 2];
        final double vx = triangles[base + 6] - triangles[base];
        final double vy = triangles[base + 7] - triangles[base + 1];
        final double vz = triangles[base + 8] - triangles[base + 2];
        out[0] = uy * vz - uz * vy;
        out[1] = uz * vx - ux * vz;
        out[2] = ux * vy - uy * vx;
        final double length = Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
        if (length <= 0) {
            out[0] = 0;
            out[1] = 1;
            out[2] = 0;
            return;
        }
        for (int axis = 0; axis < AXES; ++axis) {
            out[axis] /= length;
        }
    }

    /** Static helpers only. */
    private CoverageVoxelizer() {}
}
