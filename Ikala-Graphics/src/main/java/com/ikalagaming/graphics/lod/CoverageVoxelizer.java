package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.Arrays;
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
     * How far leaves shade each other within a voxel's own medium, in voxels: as far past its stop
     * as a volume's shadow starts, see shadow_volume.frag's SHADOW_OFFSET.
     */
    private static final double SELF_SHADOW_REACH = 1.5;

    /** How far off its surface a hit's shadow ray starts, in voxels, so it misses that surface. */
    private static final double SHADOW_START = 1e-4;

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
        final int n = triangles.length / TRIANGLE_VALUES;
        final double[] origin = new double[AXES];
        Arrays.fill(origin, Double.POSITIVE_INFINITY);
        for (int i = 0; i < triangles.length; ++i) {
            origin[i % AXES] = Math.min(origin[i % AXES], triangles[i]);
        }
        for (int axis = 0; axis < AXES; ++axis) {
            // A voxel of room below, for the blur to reach into
            origin[axis] = n == 0 ? 0 : (Math.floor(origin[axis] / voxelSize) - 1) * voxelSize;
        }
        final long[] candidates = candidates(triangles, origin, voxelSize);
        final TriangleBvh bvh = new TriangleBvh(triangles);
        final int[] stack = new int[TriangleBvh.STACK_SIZE];
        final double[] start = new double[AXES];
        final double[] ray = new double[AXES];
        final double[] along = new double[1];
        final double[] normal = new double[AXES];
        final double[] hitPoint = new double[AXES];
        final double[] shadowRay = new double[AXES];
        final double[] shadowAlong = new double[1];
        final int count = candidates.length;
        final long[] keys = new long[count];
        final float[] density = new float[count];
        final float[] sggx = new float[count * CoverageVolume.SGGX_VALUES];
        final float[] color = new float[count * CoverageVolume.COLOR_VALUES];
        final float[] roughness = new float[count];
        final float[] metallic = new float[count];
        final double[] matrix = new double[CoverageVolume.SGGX_VALUES];
        final double[] look = new double[MaterialAverages.VALUES];
        final float[][] lookOf = new float[n][];
        final float[] coverageOf = new float[n];
        for (int t = 0; t < n; ++t) {
            float[] average = averages == null ? null : averages.average(materials[t]);
            lookOf[t] = average == null ? DEFAULT_LOOK : average;
            coverageOf[t] = averages == null ? 1 : averages.coverage(materials[t]);
        }
        int kept = 0;
        for (long key : candidates) {
            final SplittableRandom random = new SplittableRandom(seed ^ key * 0x9E3779B97F4A7C15L);
            int hits = 0;
            int lit = 0;
            Arrays.fill(matrix, 0);
            Arrays.fill(look, 0);
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
                final int t = bvh.firstHit(start, ray, stack, along);
                if (t < 0 || random.nextDouble() >= coverageOf[t]) {
                    continue;
                }
                ++hits;
                faceNormal(triangles, t, normal);
                lit +=
                        escapes(
                                        bvh,
                                        stack,
                                        start,
                                        ray,
                                        along[0],
                                        normal,
                                        voxelSize,
                                        random,
                                        coverageOf,
                                        hitPoint,
                                        shadowRay,
                                        shadowAlong)
                                ? 1
                                : 0;
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
            roughness[kept] = (float) (look[MaterialAverages.ROUGHNESS] / hits);
            metallic[kept] = (float) (look[MaterialAverages.METALLIC] / hits);
            density[kept] =
                    (float)
                            solveDensity(
                                    sggx,
                                    kept * CoverageVolume.SGGX_VALUES,
                                    Math.min(MOST_OPAQUE, (double) hits / RAYS),
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
                Arrays.copyOf(metallic, kept));
    }

    /**
     * The voxels triangles reach, and those next to them, which blurred rays can hit them from.
     *
     * @param triangles The triangles' corners.
     * @param origin Where the grid starts.
     * @param voxelSize How wide a voxel is.
     * @return Their keys, in increasing order.
     */
    private static long[] candidates(
            float @NonNull [] triangles, double @NonNull [] origin, double voxelSize) {
        final TreeSet<Long> reached = new TreeSet<>();
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
        final long[] out = new long[reached.size()];
        int i = 0;
        for (long key : reached) {
            out[i++] = key;
        }
        return out;
    }

    /**
     * Whether light gets from a hit {@link #SELF_SHADOW_REACH} voxels in a random direction past
     * the triangles around it, a cut out triangle in the way letting it by with the chance of its
     * coverage.
     *
     * @param bvh The triangles.
     * @param stack Scratch for queries.
     * @param start Where the ray that hit started.
     * @param ray The ray that hit, its length its reach.
     * @param share How far along it the hit was.
     * @param normal The hit triangle's unit normal.
     * @param voxelSize How wide a voxel is.
     * @param random The random numbers.
     * @param coverageOf Each triangle's coverage.
     * @param point Scratch for the hit.
     * @param shadow Scratch for the shadow ray.
     * @param along Scratch for the shadow ray's hit.
     * @return True if the light gets out.
     */
    private static boolean escapes(
            @NonNull TriangleBvh bvh,
            int @NonNull [] stack,
            double @NonNull [] start,
            double @NonNull [] ray,
            double share,
            double @NonNull [] normal,
            double voxelSize,
            @NonNull SplittableRandom random,
            float @NonNull [] coverageOf,
            double @NonNull [] point,
            double @NonNull [] shadow,
            double @NonNull [] along) {
        randomDirection(random, shadow);
        final double side =
                Math.signum(shadow[0] * normal[0] + shadow[1] * normal[1] + shadow[2] * normal[2]);
        for (int axis = 0; axis < AXES; ++axis) {
            // Off the surface on the side the ray leaves by
            point[axis] =
                    start[axis]
                            + ray[axis] * share
                            + side * normal[axis] * SHADOW_START * voxelSize;
            shadow[axis] *= SELF_SHADOW_REACH * voxelSize;
        }
        final int blocker = bvh.firstHit(point, shadow, stack, along);
        return blocker < 0 || random.nextDouble() >= coverageOf[blocker];
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
