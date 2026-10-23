package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

/**
 * Builds a level of detail DAG the way Nanite does (Karis et al., <i>Nanite: A Deep Dive</i>,
 * 2021): split the input into clusters, then level by level, group clusters, merge each group,
 * simplify it to half with its outer border locked, and split it back into clusters, until one
 * cluster is left or a level stops halving.
 *
 * <p>Input is a list of meshes, each with its own origin: the sections of a world, or one mesh.
 * Leaf clusters never span two input meshes, so editing a section only dirties its own leaves.
 * Groups in a level are built in parallel.
 *
 * <p>Locked vertices: those a group shares with clusters in other groups of the same level, so
 * clusters of different levels meet exactly along group borders, which is what {@link CrackCheck}
 * tests. The input's open border (edges with one triangle, such as the rim of the loaded area) is
 * simplified like any other edge unless asked to lock it: nothing is drawn beyond it, and when what
 * lies beyond loads, the sections along it are rebaked and their groups rebuilt anyway.
 */
@Slf4j
public final class DagBuilder {

    /** Levels at most, as a safety net. */
    /** How far apart a projected triangle's texture repeats: once a unit, as a block's does. */
    private static final double PROJECTED_REPEAT = 1;

    /** The look of a material with no average known: mid grey, half rough, not metal. */
    private static final float[] UNKNOWN_LOOK = {0.18f, 0.18f, 0.18f, 0.5f, 0, 0.5f};

    private static final int MAX_LEVELS = 32;

    /** Runs of edges per worker the face contacts are searched in, so uneven runs even out. */
    private static final int RUNS_PER_THREAD = 4;

    /** A level whose triangles are at least this share of the last has stopped halving. */
    private static final double STALLED = 0.95;

    /**
     * How much longer a simplified group's edges are than its input's: half the triangles over the
     * same area, so each twice the area.
     */
    private static final double EDGE_GROWTH = Math.sqrt(2);

    /**
     * How far apart samples are when measuring a group's error, in edges of its simplified mesh, so
     * each group takes about as many samples whatever its level.
     */
    private static final double MEASURE_SPACING = 0.5;

    /** The default visibility reach, in leaf edges. */
    private static final double DEFAULT_VISIBILITY_REACH = 12;

    /** Nanoseconds in a millisecond. */
    private static final double NANOS_PER_MILLI = 1e6;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = Clusters.TRIANGLE_VALUES;

    /** The low 16 bits of an int. */
    private static final int LOW_16 = 0xFFFF;

    /**
     * The resolution a group is taken to reach, as a multiple of its children's error, for telling
     * what in it is finer than that: each level about doubles the error.
     */
    private static final double VOLUME_RESOLUTION_STEP = 2;

    /**
     * How wide a group's volume's voxels are, as a multiple of its middle child's error. Its error
     * is a voxel, and the group's outputs share their error, so wider voxels would keep its
     * surfaces finer for longer.
     */
    private static final double VOXEL_STEP = 1;

    /**
     * About how many voxels a surface takes per its area over a voxel's face: those it passes
     * through, and as many again that the blurred rays reach it from.
     */
    private static final double VOXELS_PER_FACE_AREA = 2;

    /**
     * How to build.
     *
     * @param virtualEdgeFactor r for virtual edges as a multiple of each group's average edge
     *     length; 0 for none.
     * @param lockOpenBorder Whether the input's open border is locked at every level, keeping it at
     *     full resolution.
     * @param threads Worker threads for building groups.
     * @param keepLineage Whether to keep where every triangle came from, for {@link Fidelity}. It
     *     holds every group's collapse history.
     * @param visibilityReach How far a leaf triangle's rays must get to count as seen from outside,
     *     as a multiple of the leaves' average edge length; 0 to treat every triangle as open (see
     *     {@link Visibility}).
     * @param mergePlanar Whether to merge each input's coplanar triangles of a material first,
     *     which leaves the surface exactly as it was with fewer triangles (see {@link
     *     PlanarMerge}).
     * @param averages What each material looks like averaged, so groups far enough out that
     *     textures can't be told apart blend their input's look per vertex instead of keeping one
     *     material a triangle (see {@link AverageTransfer}); null to keep materials at every level.
     * @param bakeTile The side, in texels, of the texture tile an output cluster of a baking group
     *     is expected to need; 0 never to bake. A group of textured triangles bakes once a tile
     *     that size is fine enough for its error (see {@link #bakes}), and then simplifies its
     *     geometry alone, with no seams or material borders held. Each cluster's tile is then as
     *     small as its error allows, with sides up to twice this.
     * @param bakeDensity Texels across a baked cluster's error: 1 for texels as wide as the error,
     *     which then cover as many pixels as the error threshold where the cluster is drawn; more
     *     for sharper tiles, at that squared in texels.
     * @param cutOut Which materials have holes cut out by their textures, like leaves, or null for
     *     none. A group holding such triangles neither blends its look per vertex nor bakes, either
     *     of which would fill the holes, so it keeps its textures and their seams.
     * @param volumes Whether groups turn what is finer than their resolution into coverage volumes
     *     (see {@link MacroSurfaces} and {@link CoverageVoxelizer}) where the voxels cost less than
     *     the triangles they stand for.
     * @param named Whether blended corners name the material they mostly blend and a shade against
     *     its average, rather than carry a color, as a model's must: its instances may draw it with
     *     other materials (see {@link Attributes#namedAverage}).
     */
    public record Options(
            double virtualEdgeFactor,
            boolean lockOpenBorder,
            int threads,
            boolean keepLineage,
            double visibilityReach,
            boolean mergePlanar,
            MaterialAverages averages,
            int bakeTile,
            double bakeDensity,
            IntPredicate cutOut,
            boolean volumes,
            boolean named) {

        /** Texels across a baked cluster's error, unless asked otherwise. */
        public static final double DEFAULT_BAKE_DENSITY = 2;

        /**
         * Options with no cut out materials.
         *
         * @param virtualEdgeFactor r for virtual edges, as a multiple of the average edge length.
         * @param lockOpenBorder Whether the input's open border is locked at every level.
         * @param threads Worker threads.
         * @param keepLineage Whether to keep where every triangle came from.
         * @param visibilityReach How far a leaf triangle's rays must get to count as seen.
         * @param mergePlanar Whether to merge each input's coplanar triangles first.
         * @param averages What each material looks like averaged, or null.
         * @param bakeTile The side of the tile a baked cluster is expected to need, 0 never to
         *     bake.
         * @param bakeDensity Texels across a baked cluster's error.
         */
        public Options(
                double virtualEdgeFactor,
                boolean lockOpenBorder,
                int threads,
                boolean keepLineage,
                double visibilityReach,
                boolean mergePlanar,
                MaterialAverages averages,
                int bakeTile,
                double bakeDensity) {
            this(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    averages,
                    bakeTile,
                    bakeDensity,
                    null,
                    false);
        }

        /**
         * Options that bake at the default density.
         *
         * @param virtualEdgeFactor r for virtual edges, as a multiple of the average edge length.
         * @param lockOpenBorder Whether the input's open border is locked at every level.
         * @param threads Worker threads.
         * @param keepLineage Whether to keep where every triangle came from.
         * @param visibilityReach How far a leaf triangle's rays must get to count as seen.
         * @param mergePlanar Whether to merge each input's coplanar triangles first.
         * @param averages What each material looks like averaged, or null.
         * @param bakeTile The side of the tile a baked cluster is expected to need, 0 never to
         *     bake.
         */
        public Options(
                double virtualEdgeFactor,
                boolean lockOpenBorder,
                int threads,
                boolean keepLineage,
                double visibilityReach,
                boolean mergePlanar,
                MaterialAverages averages,
                int bakeTile) {
            this(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    averages,
                    bakeTile,
                    DEFAULT_BAKE_DENSITY);
        }

        /**
         * Options that never bake.
         *
         * @param virtualEdgeFactor r for virtual edges, as a multiple of the average edge length.
         * @param lockOpenBorder Whether the input's open border is locked at every level.
         * @param threads Worker threads.
         * @param keepLineage Whether to keep where every triangle came from.
         * @param visibilityReach How far a leaf triangle's rays must get to count as seen.
         * @param mergePlanar Whether to merge each input's coplanar triangles first.
         * @param averages What each material looks like averaged, or null.
         */
        public Options(
                double virtualEdgeFactor,
                boolean lockOpenBorder,
                int threads,
                boolean keepLineage,
                double visibilityReach,
                boolean mergePlanar,
                MaterialAverages averages) {
            this(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    averages,
                    0);
        }

        /**
         * Options that keep materials at every level.
         *
         * @param virtualEdgeFactor r for virtual edges, as a multiple of the average edge length.
         * @param lockOpenBorder Whether the input's open border is locked at every level.
         * @param threads Worker threads.
         * @param keepLineage Whether to keep where every triangle came from.
         * @param visibilityReach How far a leaf triangle's rays must get to count as seen.
         * @param mergePlanar Whether to merge each input's coplanar triangles first.
         */
        public Options(
                double virtualEdgeFactor,
                boolean lockOpenBorder,
                int threads,
                boolean keepLineage,
                double visibilityReach,
                boolean mergePlanar) {
            this(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    null);
        }

        /**
         * Options that merge coplanar triangles first.
         *
         * @param virtualEdgeFactor r for virtual edges, as a multiple of the average edge length.
         * @param lockOpenBorder Whether the input's open border is locked at every level.
         * @param threads Worker threads.
         * @param keepLineage Whether to keep where every triangle came from.
         * @param visibilityReach How far a leaf triangle's rays must get to count as seen.
         */
        public Options(
                double virtualEdgeFactor,
                boolean lockOpenBorder,
                int threads,
                boolean keepLineage,
                double visibilityReach) {
            this(virtualEdgeFactor, lockOpenBorder, threads, keepLineage, visibilityReach, true);
        }

        /**
         * The same options, on some other number of threads.
         *
         * @param count The threads.
         * @return The options.
         */
        public Options withThreads(int count) {
            return new Options(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    count,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    averages,
                    bakeTile,
                    bakeDensity,
                    cutOut,
                    volumes,
                    named);
        }

        /**
         * The same options, averaging materials far out by these averages, or not if null.
         *
         * @param materialAverages The averages, or null.
         * @return The options.
         */
        public Options withAverages(MaterialAverages materialAverages) {
            return new Options(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    materialAverages,
                    bakeTile,
                    bakeDensity,
                    cutOut,
                    volumes,
                    named);
        }

        /**
         * The same options, merging coplanar triangles or not.
         *
         * @param merge Whether to merge them.
         * @return The options.
         */
        public Options withMergePlanar(boolean merge) {
            return new Options(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    merge,
                    averages,
                    bakeTile,
                    bakeDensity,
                    cutOut,
                    volumes,
                    named);
        }

        /**
         * The same options, baking far textured groups into tiles of a size, or never if 0.
         *
         * @param tile The tile's side, in texels.
         * @return The options.
         */
        public Options withBakeTile(int tile) {
            return new Options(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    averages,
                    tile,
                    bakeDensity,
                    cutOut,
                    volumes,
                    named);
        }

        /**
         * The same options, baking with some other number of texels across a cluster's error.
         *
         * @param density The texels, at least 1.
         * @return The options.
         */
        public Options withBakeDensity(double density) {
            return new Options(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    averages,
                    bakeTile,
                    density,
                    cutOut,
                    volumes,
                    named);
        }

        /**
         * Options with no volumes.
         *
         * @param virtualEdgeFactor r for virtual edges, as a multiple of the average edge length.
         * @param lockOpenBorder Whether the input's open border is locked at every level.
         * @param threads Worker threads.
         * @param keepLineage Whether to keep where every triangle came from.
         * @param visibilityReach How far a leaf triangle's rays must get to count as seen.
         * @param mergePlanar Whether to merge each input's coplanar triangles first.
         * @param averages What each material looks like averaged, or null.
         * @param bakeTile The side of the tile a baked cluster is expected to need, 0 never to
         *     bake.
         * @param bakeDensity Texels across a baked cluster's error.
         * @param cutOut Which materials are cut out, or null for none.
         */
        public Options(
                double virtualEdgeFactor,
                boolean lockOpenBorder,
                int threads,
                boolean keepLineage,
                double visibilityReach,
                boolean mergePlanar,
                MaterialAverages averages,
                int bakeTile,
                double bakeDensity,
                IntPredicate cutOut) {
            this(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    averages,
                    bakeTile,
                    bakeDensity,
                    cutOut,
                    false);
        }

        /**
         * Options as given, blended corners carrying colors.
         *
         * @param virtualEdgeFactor See the record.
         * @param lockOpenBorder See the record.
         * @param threads See the record.
         * @param keepLineage See the record.
         * @param visibilityReach See the record.
         * @param mergePlanar See the record.
         * @param averages See the record.
         * @param bakeTile See the record.
         * @param bakeDensity See the record.
         * @param cutOut See the record.
         * @param volumes See the record.
         */
        public Options(
                double virtualEdgeFactor,
                boolean lockOpenBorder,
                int threads,
                boolean keepLineage,
                double visibilityReach,
                boolean mergePlanar,
                MaterialAverages averages,
                int bakeTile,
                double bakeDensity,
                IntPredicate cutOut,
                boolean volumes) {
            this(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    averages,
                    bakeTile,
                    bakeDensity,
                    cutOut,
                    volumes,
                    false);
        }

        /**
         * The same options, blended corners naming materials, or carrying colors.
         *
         * @param turn Whether they name materials.
         * @return The options.
         */
        public Options withNamed(boolean turn) {
            return new Options(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    averages,
                    bakeTile,
                    bakeDensity,
                    cutOut,
                    volumes,
                    turn);
        }

        /**
         * The same options, turning what is finer than each group's resolution into volumes, or
         * not.
         *
         * @param turn Whether to.
         * @return The options.
         */
        public Options withVolumes(boolean turn) {
            return new Options(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    averages,
                    bakeTile,
                    bakeDensity,
                    cutOut,
                    turn,
                    named);
        }

        /**
         * The same options, with some materials cut out.
         *
         * @param materials Which materials have holes cut out by their textures, or null for none.
         * @return The options.
         */
        public Options withCutOut(IntPredicate materials) {
            return new Options(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    mergePlanar,
                    averages,
                    bakeTile,
                    bakeDensity,
                    materials,
                    volumes,
                    named);
        }

        /**
         * Whether a material has holes cut out by its texture.
         *
         * @param material The material.
         * @return True if it does.
         */
        public boolean isCutOut(int material) {
            return cutOut != null && cutOut.test(material);
        }

        /**
         * The defaults: r half the leaves' edge length, the open border simplified, every core but
         * one, the lineage kept, and visibility out to 12 leaf edges, about 13 blocks on block
         * terrain.
         *
         * @return The options.
         */
        public static Options defaults() {
            return new Options(
                    0.5,
                    false,
                    Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                    true,
                    DEFAULT_VISIBILITY_REACH);
        }
    }

    /**
     * What one group's build made, before ids are assigned.
     *
     * @param children The clusters it was made from.
     * @param level The highest level of its children.
     * @param corners Each output cluster's corners.
     * @param materials Each output cluster's materials.
     * @param visibility Each output cluster's visibility.
     * @param tight Each output cluster's tight sphere.
     * @param error The group's saturated error.
     * @param bound The group's saturated sphere.
     * @param simplifyError How far its output is from its base geometry, measured.
     * @param quadricError The simplifier's estimate of it.
     * @param trianglesIn Triangles in.
     * @param trianglesOut Triangles out.
     * @param borderEdges Edges between two locked vertices.
     * @param edges All edges.
     * @param virtualEdges Virtual edges added.
     * @param rejectedFlips Collapses rejected for flipping.
     * @param rejectedLinks Collapses rejected for breaking a border another group keeps.
     * @param nanos How long it took.
     * @param lineage Where its merged triangles came from, or null if not kept.
     * @param faces Each output cluster's triangles as faces of the merged mesh, or null if not
     *     kept.
     * @param bases Each output cluster's base triangles, packed by {@link #base}.
     * @param bakeTiles Each output cluster's tile, packed by {@link LodDag#packTile}, if it bakes.
     * @param bakes Each output cluster's tile mapping, if it bakes.
     * @param volumeKinds Whether each child's triangles would go to a volume, by child id.
     * @param volume The volume of its last output, or null if it made none.
     */
    private record Built(
            int[] children,
            int level,
            List<float[]> corners,
            List<int[]> materials,
            List<float[]> visibility,
            List<int[]> attributes,
            List<double[]> tight,
            double error,
            double[] bound,
            double simplifyError,
            double quadricError,
            int trianglesIn,
            int trianglesOut,
            int borderEdges,
            int edges,
            int virtualEdges,
            int rejectedFlips,
            int rejectedLinks,
            long nanos,
            Lineage.Group lineage,
            List<int[]> faces,
            List<long[]> bases,
            int[] bakeTiles,
            List<BakeTexels> bakes,
            Map<Integer, byte[]> volumeKinds,
            CoverageVolume volume) {}

    /** The options. */
    private final Options options;

    /**
     * The base triangles each cluster stands for, by cluster id, each packed by {@link #base}: the
     * triangles of the clusters the build started from, which are the leaves, or a parent cell's
     * inputs. When a group splits its output into clusters, each of its base triangles goes to the
     * output cluster nearest it, so the clusters share them out exactly. A group's error is
     * measured against its base triangles rather than added up level by level, so it stays the true
     * distance from them. Written between levels only, so the workers can read it while they build.
     */
    private final List<long[]> basis = new ArrayList<>();

    /** The DAG being built. */
    private final LodDag dag;

    /** Corners on the input's open border, never moved. */
    private final Set<Grouping.Corner> openBorder = new HashSet<>();

    /** The leaves' average edge length, which virtual edge radii scale from. */
    private double leafEdge;

    /** Stop once this many clusters or fewer are left. */
    private int stopAt = 1;

    /**
     * The minimum corner of the region cell being built, relative to the DAG's origin, or null when
     * not building a cell.
     */
    private double[] cellMin;

    /** The maximum corner of the region cell being built, or null. */
    private double[] cellMax;

    /**
     * Set up a build.
     *
     * @param origin The DAG's origin.
     * @param options How to build.
     */
    private DagBuilder(double @NonNull [] origin, @NonNull Options options) {
        this.options = options;
        dag = new LodDag(origin);
        if (options.keepLineage()) {
            dag.setLineage(new Lineage(dag));
        }
    }

    /**
     * Build a DAG.
     *
     * @param inputs The meshes, each with its own origin. Leaf clusters stay within one.
     * @param options How to build.
     * @return The DAG.
     * @throws IllegalArgumentException If there is nothing to build from.
     */
    public static LodDag build(@NonNull List<LodMesh> inputs, @NonNull Options options) {
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("A DAG needs at least one mesh");
        }
        DagBuilder builder = new DagBuilder(inputs.get(0).getOrigin().clone(), options);
        ExecutorService pool =
                Executors.newFixedThreadPool(options.threads(), daemonThreads("LOD build"));
        try {
            builder.leaves(inputs, pool);
            builder.levels(pool);
        } finally {
            pool.shutdownNow();
        }
        return builder.dag;
    }

    /**
     * Build the DAG of one region cell from its sections. The cell's boundary planes are locked
     * (see {@link #lockCellBoundary}), so that whatever is built on the other side of a plane meets
     * this cell exactly at every level. Building stops once few clusters are left, for the parent
     * cell to take further.
     *
     * @param sections The cell's section meshes, each with its own origin.
     * @param origin Where the DAG's positions are measured from.
     * @param cellMin The cell's minimum corner, relative to the origin.
     * @param cellMax The cell's maximum corner, relative to the origin.
     * @param stopAt Stop once this many clusters or fewer are left.
     * @param options How to build.
     * @return The DAG.
     * @throws IllegalArgumentException If there is nothing to build from.
     */
    public static LodDag buildCell(
            @NonNull List<LodMesh> sections,
            double @NonNull [] origin,
            double @NonNull [] cellMin,
            double @NonNull [] cellMax,
            int stopAt,
            @NonNull Options options) {
        final ExecutorService pool =
                Executors.newFixedThreadPool(options.threads(), daemonThreads("LOD build"));
        try {
            return buildCell(sections, origin, cellMin, cellMax, stopAt, options, pool);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Build the DAG of one region cell from its sections, as {@link #buildCell(List, double[],
     * double[], double[], int, Options)} does, its groups on workers shared with other builds.
     *
     * @param sections The cell's section meshes, each with its own origin.
     * @param origin Where the DAG's positions are measured from.
     * @param cellMin The cell's minimum corner, relative to the origin.
     * @param cellMax The cell's maximum corner, relative to the origin.
     * @param stopAt Stop once this many clusters or fewer are left.
     * @param options How to build.
     * @param pool The workers, which stay running.
     * @return The DAG.
     * @throws IllegalArgumentException If there is nothing to build from.
     */
    public static LodDag buildCell(
            @NonNull List<LodMesh> sections,
            double @NonNull [] origin,
            double @NonNull [] cellMin,
            double @NonNull [] cellMax,
            int stopAt,
            @NonNull Options options,
            @NonNull ExecutorService pool) {
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("A cell needs at least one section");
        }
        DagBuilder builder = new DagBuilder(origin.clone(), options);
        builder.cellMin = cellMin.clone();
        builder.cellMax = cellMax.clone();
        builder.stopAt = stopAt;
        builder.leaves(sections, pool);
        builder.levels(pool);
        return builder.dag;
    }

    /**
     * Build the DAG of a parent region cell from its children's top clusters, which become its
     * first clusters, keeping their levels, errors and spheres. Only the parent's own boundary is
     * locked, so the planes between its children simplify from here on.
     *
     * @param tops The children's top clusters, relative to the origin; their ids are ignored.
     * @param volumes The coverage volume each top is, or null where it is triangles; empty if none
     *     is.
     * @param leafEdge The leaves' average edge length, which scales virtual edges and sampling.
     * @param origin Where the DAG's positions are measured from.
     * @param cellMin The cell's minimum corner, relative to the origin.
     * @param cellMax The cell's maximum corner, relative to the origin.
     * @param stopAt Stop once this many clusters or fewer are left.
     * @param options How to build.
     * @return The DAG, whose first clusters are the tops in order.
     * @throws IllegalArgumentException If there is nothing to build from.
     */
    public static LodDag buildParent(
            @NonNull List<LodDag.Cluster> tops,
            @NonNull List<CoverageVolume> volumes,
            double leafEdge,
            double @NonNull [] origin,
            double @NonNull [] cellMin,
            double @NonNull [] cellMax,
            int stopAt,
            @NonNull Options options) {
        final ExecutorService pool =
                Executors.newFixedThreadPool(options.threads(), daemonThreads("LOD build"));
        try {
            return buildParent(
                    tops, volumes, leafEdge, origin, cellMin, cellMax, stopAt, options, pool);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Build the DAG of a parent region cell from its children's top clusters, as {@link
     * #buildParent(List, List, double, double[], double[], double[], int, Options)} does, its
     * groups on workers shared with other builds.
     *
     * @param tops The children's top clusters, relative to the origin; their ids are ignored.
     * @param volumes The coverage volume each top is, or null where it is triangles; empty if none
     *     is.
     * @param leafEdge The leaves' average edge length, which scales virtual edges and sampling.
     * @param origin Where the DAG's positions are measured from.
     * @param cellMin The cell's minimum corner, relative to the origin.
     * @param cellMax The cell's maximum corner, relative to the origin.
     * @param stopAt Stop once this many clusters or fewer are left.
     * @param options How to build.
     * @param pool The workers, which stay running.
     * @return The DAG, whose first clusters are the tops in order.
     * @throws IllegalArgumentException If there is nothing to build from.
     */
    public static LodDag buildParent(
            @NonNull List<LodDag.Cluster> tops,
            @NonNull List<CoverageVolume> volumes,
            double leafEdge,
            double @NonNull [] origin,
            double @NonNull [] cellMin,
            double @NonNull [] cellMax,
            int stopAt,
            @NonNull Options options,
            @NonNull ExecutorService pool) {
        if (tops.isEmpty()) {
            throw new IllegalArgumentException("A parent cell needs at least one cluster");
        }
        DagBuilder builder = new DagBuilder(origin.clone(), options);
        builder.cellMin = cellMin.clone();
        builder.cellMax = cellMax.clone();
        builder.stopAt = stopAt;
        builder.leafEdge = leafEdge;
        builder.dag.setLeafEdge(leafEdge);
        for (LodDag.Cluster top : tops) {
            builder.dag.add(
                    new LodDag.Cluster(
                            builder.dag.clusters().size(),
                            top.level(),
                            top.corners(),
                            top.materials(),
                            top.visibility(),
                            top.attributes(),
                            top.error(),
                            top.bound(),
                            top.tight(),
                            -1,
                            -1));
            final int added = builder.dag.clusters().size() - 1;
            if (added < volumes.size() && volumes.get(added) != null) {
                // A volume stands for leaves this cell doesn't have: groups take it in as it is
                builder.dag.setVolume(added, volumes.get(added));
            }
            if (builder.dag.getLineage() != null) {
                builder.dag.getLineage().addCluster(null);
            }
        }
        builder.levels(pool);
        return builder.dag;
    }

    /**
     * Name the worker threads, make them daemons, and give them the lowest priority, so building in
     * the background never takes the CPU from the render thread.
     *
     * @param prefix The name prefix.
     * @return The factory.
     */
    static ThreadFactory daemonThreads(@NonNull String prefix) {
        final AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + " " + count.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
    }

    /**
     * Merge each input's coplanar triangles, on the workers.
     *
     * @param inputs The meshes.
     * @param pool The workers.
     * @return The merged meshes, in the same order.
     */
    private static List<LodMesh> merged(
            @NonNull List<LodMesh> inputs, @NonNull ExecutorService pool) {
        final List<java.util.concurrent.Future<LodMesh>> futures = new ArrayList<>();
        for (LodMesh mesh : inputs) {
            futures.add(pool.submit(() -> PlanarMerge.merge(mesh)));
        }
        final List<LodMesh> out = new ArrayList<>(inputs.size());
        for (java.util.concurrent.Future<LodMesh> future : futures) {
            try {
                out.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted merging planar triangles", e);
            } catch (java.util.concurrent.ExecutionException e) {
                throw new IllegalStateException("Merging planar triangles failed", e.getCause());
            }
        }
        return out;
    }

    /**
     * Split each input into leaf clusters, work out how visible their triangles are, and find the
     * open border.
     *
     * @param inputs The meshes.
     * @param pool The workers.
     */
    private void leaves(@NonNull List<LodMesh> given, @NonNull ExecutorService pool) {
        final List<LodMesh> inputs = options.mergePlanar() ? merged(given, pool) : given;
        double edgeLength = 0;
        long edgeCount = 0;
        for (int section = 0; section < inputs.size(); ++section) {
            final LodMesh mesh = inputs.get(section);
            final float[] offset = new float[AXES];
            for (int axis = 0; axis < AXES; ++axis) {
                offset[axis] = (float) (mesh.getOrigin()[axis] - dag.getOrigin()[axis]);
            }
            for (int[] triangles : leafClusters(mesh)) {
                final float[] corners = Clusters.corners(mesh, triangles, offset);
                final int[] materials = new int[triangles.length];
                final float[] visibility = new float[triangles.length];
                for (int i = 0; i < triangles.length; ++i) {
                    materials[i] = mesh.material(triangles[i]);
                    visibility[i] = mesh.visibility(triangles[i]);
                }
                final double[] tight = Clusters.tightSphere(corners);
                dag.add(
                        new LodDag.Cluster(
                                dag.clusters().size(),
                                0,
                                corners,
                                materials,
                                visibility,
                                Clusters.attributes(mesh, triangles),
                                mesh.getLeafError(),
                                tight,
                                tight,
                                -1,
                                section));
                if (dag.getLineage() != null) {
                    dag.getLineage().addCluster(null);
                }
            }
            for (int t = 0; t < mesh.getTriangleCount(); ++t) {
                for (int corner = 0; corner < AXES; ++corner) {
                    final int v = mesh.corner(t, corner);
                    final int w = mesh.corner(t, (corner + 1) % AXES);
                    double d = 0;
                    for (int axis = 0; axis < AXES; ++axis) {
                        final double delta = mesh.position(v, axis) - mesh.position(w, axis);
                        d += delta * delta;
                    }
                    edgeLength += Math.sqrt(d);
                    ++edgeCount;
                }
            }
        }
        leafEdge = edgeCount == 0 ? 1 : edgeLength / edgeCount;
        dag.setLeafEdge(leafEdge);
        if (options.visibilityReach() > 0) {
            leafVisibility(options.visibilityReach() * leafEdge, pool);
        }
        if (options.lockOpenBorder()) {
            List<Integer> all = new ArrayList<>();
            for (int i = 0; i < dag.clusters().size(); ++i) {
                all.add(i);
            }
            for (Grouping.Edge edge :
                    rim(all.stream().mapToInt(Integer::intValue).toArray(), true)) {
                openBorder.add(edge.a());
                openBorder.add(edge.b());
            }
        }
    }

    /**
     * Work out how visible every leaf triangle is from outside, against all the leaves, and store
     * it in the leaves.
     *
     * @param reach How far a ray must get to escape, in world units.
     * @param pool The workers.
     */
    private void leafVisibility(double reach, @NonNull ExecutorService pool) {
        final long start = System.nanoTime();
        int total = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            total += cluster.corners().length;
        }
        final float[] all = new float[total];
        int at = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            System.arraycopy(cluster.corners(), 0, all, at, cluster.corners().length);
            at += cluster.corners().length;
        }
        final float[] seen = Visibility.compute(all, reach, pool);
        int t = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            System.arraycopy(seen, t, cluster.visibility(), 0, cluster.triangleCount());
            t += cluster.triangleCount();
        }
        dag.setVisibilityMillis((System.nanoTime() - start) / NANOS_PER_MILLI);
    }

    /**
     * Build level after level.
     *
     * @param pool The workers.
     */
    private void levels(@NonNull ExecutorService pool) {
        int[] active = new int[dag.clusters().size()];
        for (int i = 0; i < active.length; ++i) {
            active[i] = i;
        }
        // Each cluster the build starts from stands for its own triangles
        while (basis.size() < dag.clusters().size()) {
            final int id = basis.size();
            final long[] own = new long[dag.clusters().get(id).triangleCount()];
            for (int t = 0; t < own.length; ++t) {
                own[t] = base(id, t);
            }
            basis.add(own);
        }
        int round = 0;
        while (true) {
            final int top = dag.getLevels() - 1;
            if (active.length <= stopAt) {
                dag.setStopReason(
                        (active.length == 1 ? "one cluster" : active.length + " clusters")
                                + " left at level "
                                + top);
                return;
            }
            if (round >= MAX_LEVELS) {
                dag.setStopReason("reached " + MAX_LEVELS + " levels");
                return;
            }
            final int[][] groups = group(active);
            final Map<Grouping.Corner, Integer> owners = owners(groups);
            if (cellMin != null) {
                lockCellBoundary(owners, active);
            }
            // Corners a group shares with another or with the cell's neighbors, before the locks
            // that only keep borders crack free join them: a part with one of these goes on past
            // its group, so it never folds away
            final Set<Grouping.Corner> shared = new HashSet<>();
            for (Map.Entry<Grouping.Corner, Integer> entry : owners.entrySet()) {
                if (entry.getValue() < 0) {
                    shared.add(entry.getKey());
                }
            }
            final GroupedEdges grouped = groupedEdges(groups);
            final Meetings meetings = lockTJunctions(owners, grouped, pool);
            lockFaceContacts(owners, grouped, pool);
            final List<Grouping.Edge> rim = rim(active, false);
            lockRimNearBorders(owners, rim);
            List<Future<Built>> futures = new ArrayList<>();
            final Map<Grouping.Corner, int[]> sharers = sharers(groups);
            for (int g = 0; g < groups.length; ++g) {
                final int[] group = groups[g];
                final int index = g;
                futures.add(
                        pool.submit(
                                () ->
                                        buildGroup(
                                                group, index, owners, shared, rim, meetings,
                                                sharers)));
            }
            int trianglesIn = 0;
            int trianglesOut = 0;
            IntArrayList next = new IntArrayList();
            // Every group finishes before any is added, since adding writes what the workers read
            final List<Built> builts = new ArrayList<>(futures.size());
            for (Future<Built> future : futures) {
                try {
                    builts.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    dag.setStopReason("interrupted");
                    return;
                } catch (ExecutionException e) {
                    throw new IllegalStateException("A LOD group failed to build", e.getCause());
                }
            }
            for (Built built : builts) {
                trianglesIn += built.trianglesIn();
                trianglesOut += built.trianglesOut();
                add(built, next);
            }
            active = next.toIntArray();
            ++round;
            if (trianglesOut >= STALLED * trianglesIn) {
                dag.setStopReason(
                        String.format(
                                "stopped halving at level %d (%d of %d triangles)",
                                dag.getLevels() - 1, trianglesOut, trianglesIn));
                return;
            }
        }
    }

    /**
     * Lock a region cell's boundary: every corner on one of its six planes. What lies across a
     * plane is built separately, so no group here knows its open edges; the simplifier keeps every
     * collapse from joining two corners on a plane by a new edge, the way closing a slit or an ear
     * would, or moving a corner onto a plane (see {@link Simplifier.Options#withinCell}), so the
     * plane's edges stay exactly as the input has them until the parent cell unlocks them. The
     * corners beside the plane simplify freely.
     *
     * @param owners Which group uses each corner, -1 for locked; the boundary's corners become -1.
     * @param active This level's clusters.
     */
    private void lockCellBoundary(
            @NonNull Map<Grouping.Corner, Integer> owners, int @NonNull [] active) {
        final Set<Grouping.Corner> lock = new HashSet<>();
        for (int id : active) {
            final float[] corners = dag.clusters().get(id).corners();
            for (int at = 0; at < corners.length; at += AXES) {
                if (onCellPlane(corners, at)) {
                    lock.add(Grouping.Corner.of(corners, at));
                }
            }
        }
        lock.forEach(corner -> owners.put(corner, -1));
    }

    /**
     * Which of the cell's planes each vertex of a group's mesh lies on, welded vertices included.
     *
     * @param mesh The mesh.
     * @return A bit per plane for each vertex: min x, y, z, then max x, y, z.
     */
    private int[] cellPlanes(@NonNull LodMesh mesh) {
        final int[] planes = new int[mesh.getVertexCount()];
        for (int v = 0; v < planes.length; ++v) {
            for (int axis = 0; axis < AXES; ++axis) {
                final float value = mesh.position(v, axis);
                if (value == (float) cellMin[axis]) {
                    planes[v] |= 1 << axis;
                }
                if (value == (float) cellMax[axis]) {
                    planes[v] |= 1 << (axis + AXES);
                }
            }
        }
        return planes;
    }

    /**
     * Whether a corner lies on one of the cell's planes.
     *
     * @param corners Corner positions.
     * @param at Where the corner starts.
     * @return True if it does.
     */
    private boolean onCellPlane(float @NonNull [] corners, int at) {
        for (int axis = 0; axis < AXES; ++axis) {
            final float value = corners[at + axis];
            if (value == (float) cellMin[axis] || value == (float) cellMax[axis]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which group uses each corner this level: the group's index, or -1 for corners more than one
     * group uses, which are locked.
     *
     * @param groups The groups.
     * @return The owner of each corner.
     */
    private Map<Grouping.Corner, Integer> owners(int @NonNull [][] groups) {
        Map<Grouping.Corner, Integer> owners = new HashMap<>();
        for (int g = 0; g < groups.length; ++g) {
            final int group = g;
            for (int id : groups[g]) {
                final float[] corners = dag.clusters().get(id).corners();
                for (int i = 0; i < corners.length; i += AXES) {
                    owners.merge(
                            Grouping.Corner.of(corners, i), group, (a, b) -> a.equals(b) ? a : -1);
                }
            }
        }
        return owners;
    }

    /**
     * The groups using each corner more than one group uses.
     *
     * @param groups The groups.
     * @return Each such corner's groups, sorted.
     */
    private Map<Grouping.Corner, int[]> sharers(int @NonNull [][] groups) {
        final Map<Grouping.Corner, int[]> out = new HashMap<>();
        for (int g = 0; g < groups.length; ++g) {
            final int group = g;
            for (int id : groups[g]) {
                final float[] corners = dag.clusters().get(id).corners();
                for (int i = 0; i < corners.length; i += AXES) {
                    out.merge(
                            Grouping.Corner.of(corners, i),
                            new int[] {group},
                            (a, b) -> {
                                if (Arrays.binarySearch(a, group) >= 0) {
                                    return a;
                                }
                                final int[] both = Arrays.copyOf(a, a.length + 1);
                                both[a.length] = group;
                                Arrays.sort(both);
                                return both;
                            });
                }
            }
        }
        out.values().removeIf(groupsThere -> groupsThere.length < 2);
        return out;
    }

    /**
     * A level's edges, each once per triangle using it, in the direction that triangle runs.
     *
     * @param active The level's clusters.
     * @param tags Receives each edge's cluster's index in {@code active}.
     * @param open Receives whether each is open, used by one triangle.
     * @param triangles Receives each edge's triangle, numbered through all of {@code active}'s
     *     triangles in order.
     * @return The edges, x, y, z of the start then the end.
     */
    private List<float[]> edges(
            int @NonNull [] active,
            @NonNull IntArrayList tags,
            @NonNull List<Boolean> open,
            @NonNull IntArrayList triangles) {
        // How many triangles use each edge
        int edgeCount = 0;
        for (int id : active) {
            edgeCount += dag.clusters().get(id).corners().length / AXES;
        }
        final Grouping.EdgeTable uses = new Grouping.EdgeTable(edgeCount);
        for (int id : active) {
            final float[] corners = dag.clusters().get(id).corners();
            for (int t = 0; t < corners.length; t += TRIANGLE_VALUES) {
                for (int corner = 0; corner < AXES; ++corner) {
                    uses.count(corners, t + corner * AXES, t + ((corner + 1) % AXES) * AXES);
                }
            }
        }
        final List<float[]> out = new ArrayList<>(edgeCount);
        int triangle = 0;
        for (int i = 0; i < active.length; ++i) {
            final float[] corners = dag.clusters().get(active[i]).corners();
            for (int t = 0; t < corners.length; t += TRIANGLE_VALUES, ++triangle) {
                for (int corner = 0; corner < AXES; ++corner) {
                    final int p = t + corner * AXES;
                    final int q = t + ((corner + 1) % AXES) * AXES;
                    out.add(
                            new float[] {
                                corners[p],
                                corners[p + 1],
                                corners[p + 2],
                                corners[q],
                                corners[q + 1],
                                corners[q + 2]
                            });
                    tags.addInt(i);
                    open.add(uses.get(corners, p, q) == 1);
                    triangles.addInt(triangle);
                }
            }
        }
        return out;
    }

    /**
     * A level's clusters in group order and their edges, which the locking steps share.
     *
     * @param active The clusters, group by group.
     * @param groupOf Each one's group.
     * @param edges Their edges, each once per triangle using it, see {@link #edges}.
     * @param tags Each edge's cluster's index in {@code active}.
     * @param open Whether each edge is open.
     * @param triangles Each edge's triangle, numbered through all of {@code active}'s triangles.
     */
    private record GroupedEdges(
            int @NonNull [] active,
            int @NonNull [] groupOf,
            @NonNull List<float[]> edges,
            @NonNull IntArrayList tags,
            @NonNull List<Boolean> open,
            @NonNull IntArrayList triangles) {}

    /**
     * Put a level's clusters in group order and find their edges, once for every locking step.
     *
     * @param groups The groups.
     * @return The clusters and edges.
     */
    private GroupedEdges groupedEdges(int @NonNull [][] groups) {
        int total = 0;
        for (int[] group : groups) {
            total += group.length;
        }
        final int[] active = new int[total];
        final int[] groupOf = new int[total];
        int at = 0;
        for (int g = 0; g < groups.length; ++g) {
            for (int id : groups[g]) {
                active[at] = id;
                groupOf[at++] = g;
            }
        }
        final IntArrayList tags = new IntArrayList();
        final List<Boolean> open = new ArrayList<>();
        final IntArrayList triangles = new IntArrayList();
        final List<float[]> edges = edges(active, tags, open, triangles);
        return new GroupedEdges(active, groupOf, edges, tags, open, triangles);
    }

    /**
     * Lock where groups meet without sharing corners: both ends of every edge that shares a stretch
     * of its line with another group's edge. Both groups then keep every point where either's edges
     * end along the line, so neither moves its side off it, nor drops a corner the other's edges
     * still meet. Edges the groups share exactly are locked already, by their corners.
     *
     * @param owners Which group uses each corner; the corners found become -1.
     * @param groups The groups.
     * @return Where groups meet.
     */
    private Meetings lockTJunctions(
            @NonNull Map<Grouping.Corner, Integer> owners,
            @NonNull GroupedEdges grouped,
            @NonNull ExecutorService pool) {
        final int[] groupOf = grouped.groupOf();
        final IntArrayList tags = grouped.tags();
        final List<float[]> edges = grouped.edges();
        final TJunctions index = new TJunctions();
        // Each edge placed once, for filing and for the search, a run of them on each worker
        final TJunctions.Placed[] placed = new TJunctions.Placed[edges.size()];
        final int runs = Math.max(1, Math.min(edges.size(), options.threads() * RUNS_PER_THREAD));
        final List<Future<?>> placing = new ArrayList<>(runs);
        for (int run = 0; run < runs; ++run) {
            final int from = (int) ((long) edges.size() * run / runs);
            final int to = (int) ((long) edges.size() * (run + 1) / runs);
            placing.add(
                    pool.submit(
                            () -> {
                                for (int i = from; i < to; ++i) {
                                    placed[i] =
                                            TJunctions.place(
                                                    start(edges.get(i)), end(edges.get(i)));
                                }
                            }));
        }
        for (Future<?> future : placing) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Meetings(Set.of(), index);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Placing edges failed", e.getCause());
            }
        }
        for (int i = 0; i < edges.size(); ++i) {
            index.add(placed[i], groupOf[tags.getInt(i)], -1, -1);
        }
        final Set<Grouping.Edge> meetings = new HashSet<>();
        for (int i = 0; i < edges.size(); ++i) {
            final float[] edge = edges.get(i);
            if (index.overlapsOther(placed[i], groupOf[tags.getInt(i)])) {
                final Grouping.Corner a = Grouping.Corner.of(edge, 0);
                final Grouping.Corner b = Grouping.Corner.of(edge, AXES);
                owners.put(a, -1);
                owners.put(b, -1);
                meetings.add(Grouping.Edge.of(a, b));
            }
        }
        return new Meetings(meetings, index);
    }

    /**
     * Where a level's groups meet.
     *
     * @param edges The edges that share a stretch of their line with another group's, those shared
     *     exactly too.
     * @param lines Every edge, filed by its line and tagged with its group's index, to find whether
     *     a segment runs along another group's edge. Read only once made, from any thread.
     */
    private record Meetings(@NonNull Set<Grouping.Edge> edges, @NonNull TJunctions lines) {}

    /**
     * Lock where a group's open edge ends against the inside of another group's face, as a slab's
     * side does against a wall: the face's corners, so the face stays where the edge meets it, and
     * the edge's ends, so the edge stays on the face. Where the edge runs along one of the face's
     * edges instead, {@link #lockTJunctions} keeps it.
     *
     * <p>The open edges are searched on the workers, a run of them each, and what they find is
     * locked in edge order, so the result doesn't depend on how they were split.
     *
     * @param owners Which group uses each corner; the corners found become -1.
     * @param groups The groups.
     * @param pool The workers.
     */
    private void lockFaceContacts(
            @NonNull Map<Grouping.Corner, Integer> owners,
            @NonNull GroupedEdges grouped,
            @NonNull ExecutorService pool) {
        final int[] active = grouped.active();
        final int[] groupOf = grouped.groupOf();
        // Every triangle of the level, with its cluster's index in active
        int corners = 0;
        for (int id : active) {
            corners += dag.clusters().get(id).corners().length;
        }
        final float[] all = new float[corners];
        final int[] clusterOf = new int[corners / TRIANGLE_VALUES];
        int at = 0;
        for (int i = 0; i < active.length; ++i) {
            final float[] own = dag.clusters().get(active[i]).corners();
            System.arraycopy(own, 0, all, at, own.length);
            Arrays.fill(clusterOf, at / TRIANGLE_VALUES, (at + own.length) / TRIANGLE_VALUES, i);
            at += own.length;
        }
        final TriangleBvh tree = new TriangleBvh(all);
        final IntArrayList tags = grouped.tags();
        final List<Boolean> open = grouped.open();
        final IntArrayList triangles = grouped.triangles();
        final List<float[]> edges = grouped.edges();
        final int runs = Math.max(1, Math.min(edges.size(), options.threads() * RUNS_PER_THREAD));
        final List<Future<List<Grouping.Corner>>> futures = new ArrayList<>(runs);
        for (int run = 0; run < runs; ++run) {
            final int from = (int) ((long) edges.size() * run / runs);
            final int to = (int) ((long) edges.size() * (run + 1) / runs);
            futures.add(
                    pool.submit(
                            () ->
                                    faceContacts(
                                            from, to, edges, open, tags, triangles, tree, all,
                                            clusterOf, groupOf)));
        }
        for (Future<List<Grouping.Corner>> future : futures) {
            final List<Grouping.Corner> found;
            try {
                found = future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                throw new IllegalStateException("Finding face contacts failed", e.getCause());
            }
            for (Grouping.Corner corner : found) {
                owners.put(corner, -1);
            }
        }
    }

    /**
     * Find where a run of open edges ends against the inside of another group's face, see {@link
     * #lockFaceContacts}. On a worker.
     *
     * @param from The first edge.
     * @param to Past the last edge.
     * @param edges Every edge of the level.
     * @param open Whether each is open.
     * @param tags Each one's cluster's index in the active clusters.
     * @param triangles Each one's own triangle, which its search skips.
     * @param tree Every triangle of the level.
     * @param all Their corners.
     * @param clusterOf Each triangle's cluster's index in the active clusters.
     * @param groupOf Each active cluster's group.
     * @return The corners to lock, in edge order.
     */
    private static List<Grouping.Corner> faceContacts(
            int from,
            int to,
            @NonNull List<float[]> edges,
            @NonNull List<Boolean> open,
            @NonNull IntArrayList tags,
            @NonNull IntArrayList triangles,
            @NonNull TriangleBvh tree,
            float @NonNull [] all,
            int @NonNull [] clusterOf,
            int @NonNull [] groupOf) {
        final List<Grouping.Corner> found = new ArrayList<>();
        final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
        final double touch = TJunctions.TOUCH * TJunctions.TOUCH;
        final double[] point = new double[AXES];
        for (int i = from; i < to; ++i) {
            if (!open.get(i)) {
                continue;
            }
            final float[] edge = edges.get(i);
            final double[] a = start(edge);
            final double[] b = end(edge);
            final int mine = tags.getInt(i);
            final int samples = TJunctions.samples(a, b);
            boolean contact = false;
            for (int k = 0; k < samples; ++k) {
                TJunctions.sample(a, b, k, samples, point);
                if (tree.nearest(point, touch, touch, triangles.getInt(i), scratch) >= touch) {
                    continue;
                }
                final int other = clusterOf[scratch.triangle];
                if (groupOf[other] == groupOf[mine]
                        || onTriangleEdge(all, scratch.triangle, point)) {
                    continue;
                }
                contact = true;
                for (int corner = 0; corner < AXES; ++corner) {
                    found.add(
                            Grouping.Corner.of(
                                    all, scratch.triangle * TRIANGLE_VALUES + corner * AXES));
                }
            }
            if (contact) {
                found.add(Grouping.Corner.of(edge, 0));
                found.add(Grouping.Corner.of(edge, AXES));
            }
        }
        return found;
    }

    /**
     * Whether a point is on one of a triangle's edges.
     *
     * @param corners Every triangle's corners.
     * @param triangle The triangle.
     * @param point The point.
     * @return True if it is within touching distance of one.
     */
    private static boolean onTriangleEdge(
            float @NonNull [] corners, int triangle, double @NonNull [] point) {
        final int base = triangle * TRIANGLE_VALUES;
        for (int corner = 0; corner < AXES; ++corner) {
            final int p = base + corner * AXES;
            final int q = base + ((corner + 1) % AXES) * AXES;
            final double[] from = {corners[p], corners[p + 1], corners[p + 2]};
            final double[] to = {corners[q], corners[q + 1], corners[q + 2]};
            if (Geometry.segmentDistanceSquared(from, to, point, point)
                    < TJunctions.TOUCH * TJunctions.TOUCH) {
                return true;
            }
        }
        return false;
    }

    /**
     * An open edge's start.
     *
     * @param edge The edge, start then end.
     * @return x, y, z.
     */
    private static double[] start(float @NonNull [] edge) {
        return new double[] {edge[0], edge[1], edge[2]};
    }

    /**
     * An open edge's end.
     *
     * @param edge The edge, start then end.
     * @return x, y, z.
     */
    private static double[] end(float @NonNull [] edge) {
        return new double[] {edge[AXES], edge[AXES + 1], edge[AXES + 2]};
    }

    /**
     * A level's open edges: those only one of its triangles uses, optionally less those that edges
     * running the other way cover, which are where parts meet at T-junctions rather than the edge
     * of the input. Ears along a border can be anywhere an edge is open, so guarding them takes
     * every open edge.
     *
     * @param active The level's clusters.
     * @param seamless Whether to leave out edges where parts meet at T-junctions.
     * @return The edges.
     */
    private List<Grouping.Edge> rim(int @NonNull [] active, boolean seamless) {
        List<Grouping.Edge> out = new ArrayList<>();
        final List<Boolean> open = new ArrayList<>();
        final List<float[]> edges = edges(active, new IntArrayList(), open, new IntArrayList());
        // Only a seamless rim needs to know which open edges others cover
        final TJunctions index = seamless ? new TJunctions() : null;
        if (seamless) {
            for (float[] edge : edges) {
                index.add(start(edge), end(edge), 0, -1, -1);
            }
        }
        for (int i = 0; i < edges.size(); ++i) {
            final float[] edge = edges.get(i);
            if (open.get(i) && !(seamless && index.covered(start(edge), end(edge)))) {
                out.add(
                        Grouping.Edge.of(
                                Grouping.Corner.of(edge, 0), Grouping.Corner.of(edge, AXES)));
            }
        }
        return out;
    }

    /**
     * Lock the rim vertices next to corners that groups share. A rim vertex beside a shared corner
     * can be the tip of an ear, the only triangle on its group's side of a border edge the groups
     * share; collapsing the tip would take that triangle and leave the other group's edge facing
     * nothing. Keeping these vertices also stops a rim from collapsing onto the shared corners,
     * while the rest of it still simplifies.
     *
     * @param owners Which group uses each corner, -1 for shared; rim corners to lock become -1.
     * @param rim This level's open edges.
     */
    private static void lockRimNearBorders(
            @NonNull Map<Grouping.Corner, Integer> owners, @NonNull List<Grouping.Edge> rim) {
        List<Grouping.Corner> lock = new ArrayList<>();
        for (Grouping.Edge edge : rim) {
            final boolean aShared = owners.getOrDefault(edge.a(), -1) < 0;
            final boolean bShared = owners.getOrDefault(edge.b(), -1) < 0;
            if (aShared && !bShared) {
                lock.add(edge.b());
            } else if (bShared && !aShared) {
                lock.add(edge.a());
            }
        }
        lock.forEach(corner -> owners.put(corner, -1));
    }

    /**
     * Lock the far end of every open edge of a group's merged mesh that has one end locked, the way
     * {@link #lockRimNearBorders} does for the level's rim. Welding T-junctions leaves open edges
     * the level never had, and an ear's tip on one of them, collapsed, would take the only triangle
     * on the group's side of a locked border edge with it.
     *
     * @param mesh The merged mesh, whose locks change.
     */
    private static void lockEars(@NonNull LodMesh mesh) {
        final LongIntMap uses = new LongIntMap(mesh.getTriangleCount() * AXES);
        final IntArrayList counts = new IntArrayList();
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = mesh.corner(t, corner);
                final int w = mesh.corner(t, (corner + 1) % AXES);
                final long key = LongIntMap.pair(Math.min(v, w), Math.max(v, w));
                final int at = uses.putIfAbsent(key, counts.size());
                if (at == LongIntMap.MISSING) {
                    counts.addInt(1);
                } else {
                    counts.setInt(at, counts.getInt(at) + 1);
                }
            }
        }
        final IntArrayList lock = new IntArrayList();
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = mesh.corner(t, corner);
                final int w = mesh.corner(t, (corner + 1) % AXES);
                if (counts.getInt(uses.get(LongIntMap.pair(Math.min(v, w), Math.max(v, w)))) != 1
                        || mesh.isLocked(v) == mesh.isLocked(w)) {
                    continue;
                }
                lock.addInt(mesh.isLocked(v) ? w : v);
            }
        }
        for (int i = 0; i < lock.size(); ++i) {
            mesh.setLocked(lock.getInt(i), true);
        }
    }

    /**
     * The edges a group must not create: open edges of other groups between two corners it shares
     * with them. A hole or slit in the input can have its sides in two groups, one with the edge
     * a–b and the other with the path a–w–b. If the second closed it with its own a–b, a cut
     * drawing its coarser level next to the first group would leave a–w–b uncovered.
     *
     * @param builder The builder of the group's merged mesh, to find corners by position.
     * @param merged The merged mesh.
     * @param rim The level's open edges.
     * @return The edges, as packed vertex pairs of the merged mesh.
     */
    private static long[] keepOpen(
            LodMesh.@NonNull Builder builder,
            @NonNull LodMesh merged,
            @NonNull List<Grouping.Edge> rim) {
        final LongIntMap own = new LongIntMap(merged.getTriangleCount() * AXES);
        for (int t = 0; t < merged.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = merged.corner(t, corner);
                final int w = merged.corner(t, (corner + 1) % AXES);
                own.putIfAbsent(LongIntMap.pair(Math.min(v, w), Math.max(v, w)), 0);
            }
        }
        final IntArrayList out = new IntArrayList();
        for (Grouping.Edge edge : rim) {
            final int a = find(builder, edge.a());
            final int b = find(builder, edge.b());
            if (a >= 0 && b >= 0) {
                final long key = LongIntMap.pair(Math.min(a, b), Math.max(a, b));
                if (own.get(key) == LongIntMap.MISSING) {
                    out.addInt(Math.min(a, b));
                    out.addInt(Math.max(a, b));
                }
            }
        }
        final long[] keys = new long[out.size() / 2];
        for (int i = 0; i < keys.length; ++i) {
            keys[i] = LongIntMap.pair(out.getInt(2 * i), out.getInt(2 * i + 1));
        }
        return keys;
    }

    /**
     * The merged mesh's edges where its group meets others.
     *
     * @param merged The merged mesh.
     * @param meetings The level's edges where groups meet.
     * @return The edges, as packed vertex pairs of the merged mesh, lower vertex first.
     */
    private static long[] meetingsIn(
            @NonNull LodMesh merged, @NonNull Set<Grouping.Edge> meetings) {
        final LongIntMap out = new LongIntMap(merged.getTriangleCount() * AXES);
        final IntArrayList pairs = new IntArrayList();
        for (int t = 0; t < merged.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = merged.corner(t, corner);
                final int w = merged.corner(t, (corner + 1) % AXES);
                final long key = LongIntMap.pair(Math.min(v, w), Math.max(v, w));
                if (out.get(key) == LongIntMap.MISSING
                        && meetings.contains(
                                Grouping.Edge.of(cornerOf(merged, v), cornerOf(merged, w)))) {
                    out.putIfAbsent(key, 0);
                    pairs.addInt(Math.min(v, w));
                    pairs.addInt(Math.max(v, w));
                }
            }
        }
        final long[] keys = new long[pairs.size() / 2];
        for (int i = 0; i < keys.length; ++i) {
            keys[i] = LongIntMap.pair(pairs.getInt(2 * i), pairs.getInt(2 * i + 1));
        }
        return keys;
    }

    /**
     * The other groups sharing each merged mesh vertex's corner.
     *
     * @param merged The merged mesh.
     * @param index The group's index in its level.
     * @param sharers The groups using each corner more than one uses.
     * @return For each vertex, the other groups, sorted.
     */
    private static int[][] sharersOf(
            @NonNull LodMesh merged, int index, @NonNull Map<Grouping.Corner, int[]> sharers) {
        final int[][] out = new int[merged.getVertexCount()][];
        for (int v = 0; v < out.length; ++v) {
            final int[] there = sharers.get(cornerOf(merged, v));
            if (there == null) {
                out[v] = new int[0];
                continue;
            }
            final IntArrayList others = new IntArrayList(there.length);
            for (int group : there) {
                if (group != index) {
                    others.addInt(group);
                }
            }
            out[v] = others.toIntArray();
        }
        return out;
    }

    /**
     * A merged mesh vertex's corner.
     *
     * @param merged The merged mesh.
     * @param v The vertex.
     * @return Its corner.
     */
    private static Grouping.Corner cornerOf(@NonNull LodMesh merged, int v) {
        return new Grouping.Corner(
                Float.floatToIntBits((float) merged.position(v, 0) + 0f),
                Float.floatToIntBits((float) merged.position(v, 1) + 0f),
                Float.floatToIntBits((float) merged.position(v, 2) + 0f));
    }

    /**
     * The merged mesh's vertex at a corner.
     *
     * @param builder The builder.
     * @param corner The corner.
     * @return The vertex, or -1 if the group has none there.
     */
    private static int find(LodMesh.@NonNull Builder builder, Grouping.@NonNull Corner corner) {
        return builder.find(
                Float.intBitsToFloat(corner.x()),
                Float.intBitsToFloat(corner.y()),
                Float.intBitsToFloat(corner.z()));
    }

    /**
     * Pack a base triangle: its cluster's id and its index there.
     *
     * @param cluster The cluster's id.
     * @param triangle The triangle's index in it.
     * @return The packed triangle.
     */
    private static long base(int cluster, int triangle) {
        return ((long) cluster << Integer.SIZE) | (triangle & 0xFFFFFFFFL);
    }

    /**
     * A packed base triangle's cluster.
     *
     * @param packed The packed triangle.
     * @return The cluster's id.
     */
    private static int baseCluster(long packed) {
        return (int) (packed >>> Integer.SIZE);
    }

    /**
     * A packed base triangle's index in its cluster.
     *
     * @param packed The packed triangle.
     * @return The index.
     */
    private static int baseTriangle(long packed) {
        return (int) packed;
    }

    /**
     * The base triangles some clusters stand for, together.
     *
     * @param clusters The clusters.
     * @return The packed base triangles.
     */
    private long[] basisOf(int @NonNull [] clusters) {
        int total = 0;
        for (int id : clusters) {
            total += basis.get(id).length;
        }
        final long[] out = new long[total];
        int at = 0;
        for (int id : clusters) {
            final long[] own = basis.get(id);
            System.arraycopy(own, 0, out, at, own.length);
            at += own.length;
        }
        return out;
    }

    /**
     * Share a group's base triangles out among its output clusters: each goes to the cluster with
     * the triangle nearest its middle.
     *
     * @param bases The group's packed base triangles.
     * @param outCorners Each output cluster's corners.
     * @return Each output cluster's packed base triangles.
     */
    private List<long[]> shareOut(long @NonNull [] bases, @NonNull List<float[]> outCorners) {
        final List<long[]> out = new ArrayList<>(outCorners.size());
        if (outCorners.size() == 1) {
            out.add(bases);
            return out;
        }
        int total = 0;
        for (float[] corners : outCorners) {
            total += corners.length;
        }
        final float[] all = new float[total];
        final int[] owner = new int[total / TRIANGLE_VALUES];
        int at = 0;
        for (int i = 0; i < outCorners.size(); ++i) {
            final float[] corners = outCorners.get(i);
            System.arraycopy(corners, 0, all, at, corners.length);
            java.util.Arrays.fill(
                    owner, at / TRIANGLE_VALUES, (at + corners.length) / TRIANGLE_VALUES, i);
            at += corners.length;
        }
        final TriangleBvh tree = new TriangleBvh(all);
        final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
        final double[] middle = new double[AXES];
        final List<IntArrayList> chosen = new ArrayList<>(outCorners.size());
        for (int i = 0; i < outCorners.size(); ++i) {
            chosen.add(new IntArrayList());
        }
        for (int b = 0; b < bases.length; ++b) {
            final float[] corners = dag.clusters().get(baseCluster(bases[b])).corners();
            final int t = baseTriangle(bases[b]) * TRIANGLE_VALUES;
            for (int axis = 0; axis < AXES; ++axis) {
                middle[axis] =
                        (corners[t + axis]
                                        + corners[t + AXES + axis]
                                        + corners[t + 2 * AXES + axis])
                                / AXES;
            }
            tree.nearest(middle, Double.POSITIVE_INFINITY, scratch);
            chosen.get(owner[scratch.triangle]).addInt(b);
        }
        for (IntArrayList picks : chosen) {
            final long[] own = new long[picks.size()];
            for (int i = 0; i < own.length; ++i) {
                own[i] = bases[picks.getInt(i)];
            }
            out.add(own);
        }
        return out;
    }

    /**
     * Merge, simplify and split one group. On a worker.
     *
     * @param children The group's clusters.
     * @param index The group's index in its level.
     * @param owners Which group uses each corner.
     * @param shared The corners other groups or cells share.
     * @param rim This level's open edges.
     * @param meetings Where the level's groups meet, see {@link #lockTJunctions}.
     * @param sharers The groups using each corner more than one uses.
     * @return What it made.
     */
    private Built buildGroup(
            int @NonNull [] children,
            int index,
            @NonNull Map<Grouping.Corner, Integer> owners,
            @NonNull Set<Grouping.Corner> shared,
            @NonNull List<Grouping.Edge> rim,
            @NonNull Meetings meetings,
            @NonNull Map<Grouping.Corner, int[]> sharers) {
        final long start = System.nanoTime();
        // Children can be of different levels in a parent cell; the group is above the highest
        int level = 0;
        for (int id : children) {
            level = Math.max(level, dag.clusters().get(id).level());
        }
        // Merge
        final LodMesh.Builder builder = LodMesh.builder(dag.getOrigin());
        int trianglesIn = 0;
        double childError = 0;
        List<double[]> spheres = new ArrayList<>();
        final IntArrayList sourceCluster = new IntArrayList();
        final IntArrayList sourceTriangle = new IntArrayList();
        // Textured triangles' attributes, to give the output's again
        final AttributeTransfer transfer = new AttributeTransfer();
        boolean anyTextured = false;
        for (int id : children) {
            anyTextured |= dag.clusters().get(id).attributes().length > 0;
        }
        // Far enough out that textures can't be told apart, the input's look is blended per
        // vertex instead, and no seam of texture coordinates or materials holds simplifying back
        final boolean averaging = averages(children);
        // Far enough out that a tile per cluster holds the input's look finely enough, the output
        // is baked instead, so nothing about appearance holds simplifying back
        final boolean baking = !averaging && bakes(children);
        final AverageTransfer average =
                averaging ? new AverageTransfer(options.named() ? options.averages() : null) : null;
        final float[] look = new float[MaterialAverages.VALUES];
        for (int id : children) {
            final LodDag.Cluster cluster = dag.clusters().get(id);
            final float[] corners = cluster.corners();
            final int[] attributes = cluster.attributes();
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                final int[] v = new int[AXES];
                final int material = cluster.materials()[t];
                for (int corner = 0; corner < AXES; ++corner) {
                    final int at = t * TRIANGLE_VALUES + corner * AXES;
                    v[corner] = builder.vertex(corners[at], corners[at + 1], corners[at + 2]);
                    final Grouping.Corner key = Grouping.Corner.of(corners, at);
                    if (owners.getOrDefault(key, -1) < 0 || openBorder.contains(key)) {
                        builder.lock(v[corner]);
                    }
                }
                if (averaging) {
                    average.add(
                            corners,
                            t * TRIANGLE_VALUES,
                            cluster.visibility()[t],
                            lookOf(attributes, t, material, look),
                            look);
                }
                if (builder.triangle(
                        v[0], v[1], v[2], cluster.materials()[t], cluster.visibility()[t])) {
                    sourceCluster.addInt(id);
                    sourceTriangle.addInt(t);
                }
            }
            trianglesIn += cluster.triangleCount();
            childError = Math.max(childError, cluster.error());
            spheres.add(cluster.bound());
        }
        LodMesh merged = builder.build();
        // Join the group's parts where they meet at T-junctions, so it simplifies one surface
        final IntArrayList from = new IntArrayList();
        final LodMesh welded = TJunctions.weld(merged, from);
        if (welded != merged) {
            final int[] clusters = sourceCluster.toIntArray();
            final int[] triangles = sourceTriangle.toIntArray();
            sourceCluster.clear();
            sourceTriangle.clear();
            for (int i = 0; i < from.size(); ++i) {
                sourceCluster.addInt(clusters[from.getInt(i)]);
                sourceTriangle.addInt(triangles[from.getInt(i)]);
            }
            merged = welded;
        }
        lockEars(merged);
        // Cut out leaves turn into a volume as soon as one is cheaper than they are, never
        // simplified as triangles first, which would shrink their cards and lose their coverage
        final boolean cutOutVolume = options.volumes() && anyCutOut(children);
        // What would go to a volume rather than stay triangles, see MacroSurfaces
        final Map<Integer, byte[]> volumeKinds =
                childError > 0 || cutOutVolume
                        ? volumeKinds(children, merged, childError, sourceCluster, sourceTriangle)
                        : Map.of();
        // Far enough out, what is finer than the group's resolution leaves its triangles for a
        // coverage volume, where the voxels cost less than the triangles they stand for
        VolumePart volumePart =
                options.volumes() && (childError > 0 || cutOutVolume)
                        ? volumePart(children, merged, volumeKinds, sourceCluster, sourceTriangle)
                        : null;
        // The volume is made before its triangles leave: where nothing in it stops light, they
        // stay triangles, since dropping them would open a hole wherever they met a border
        final long[] allBases = basisOf(children);
        long[] bases = allBases;
        long[] volumeBases = new long[0];
        CoverageVolume volume = null;
        if (volumePart != null) {
            final long[][] split = splitBases(children, allBases, volumePart);
            volume = voxelize(split[1], volumePart.voxelSize(), children);
            if (volume.size() > 0) {
                bases = split[0];
                volumeBases = split[1];
            } else {
                volume = null;
                volumePart = null;
            }
        }
        if (volumePart != null) {
            merged = dropFaces(merged, volumePart.faces(), sourceCluster, sourceTriangle);
        } else if (cutOutVolume) {
            // Not a volume yet: kept as they are
            for (int v = 0; v < merged.getVertexCount(); ++v) {
                merged.setLocked(v, true);
            }
        }
        // Far enough out, small parts standing free of the group's borders go: their look stays in
        // the blend around them, and the measured error counts what is missing
        if (averaging && childError > 0) {
            merged = foldSmallParts(merged, childError, shared, sourceCluster, sourceTriangle);
        }
        // Where textured triangles' appearance breaks, seams' vertices only slide along them, and
        // where seams meet or end they stay put; each side's triangles give the output its
        // attributes again
        final Seams seams =
                anyTextured && !averaging && !baking
                        ? seams(merged, sourceCluster, sourceTriangle)
                        : null;
        if (seams != null) {
            for (int v = 0; v < merged.getVertexCount(); ++v) {
                if (seams.isCorner(v)) {
                    merged.setLocked(v, true);
                }
            }
        }
        if (anyTextured && !averaging) {
            addSources(transfer, seams, sourceCluster, sourceTriangle);
        }
        // Simplify to half, then map materials back
        // Scaled by the group's own edges rather than its level: levels climb faster in region
        // cells, whose parents count up from their highest child, but triangle sizes don't
        final double edge = averageEdge(merged);
        final double radius = options.virtualEdgeFactor() * edge;
        Simplifier.Options simplify =
                Simplifier.Options.toCount(merged.getTriangleCount() / 2, radius)
                        .keepingOpen(keepOpen(builder, merged, rim))
                        .meetingAt(meetingsIn(merged, meetings.edges()))
                        .sharedWith(sharersOf(merged, index, sharers))
                        .withOthersAlong((a, b) -> meetings.lines().coveredByOthers(a, b, index))
                        .followingSeams(seams == null ? new long[0] : seams.edges);
        if (cellMin != null) {
            final int[] planes = cellPlanes(merged);
            for (int v = 0; v < planes.length; ++v) {
                if (planes[v] != 0) {
                    merged.setLocked(v, true);
                }
            }
            simplify =
                    simplify.withinCell(
                            planes,
                            new double[] {
                                cellMin[0],
                                cellMin[1],
                                cellMin[2],
                                cellMax[0],
                                cellMax[1],
                                cellMax[2]
                            });
        }
        final Simplifier.Result result = Simplifier.simplify(merged, simplify);
        Correspondence.assignMaterials(merged, result);
        // Blended over about what the group's error spans, and at least an edge
        final int[] averaged =
                averaging && !average.isEmpty()
                        ? average.transfer(result.mesh(), Math.max(childError, edge))
                        : null;
        // Split
        List<float[]> outCorners = new ArrayList<>();
        List<int[]> outMaterials = new ArrayList<>();
        List<float[]> outVisibility = new ArrayList<>();
        List<int[]> outAttributes = new ArrayList<>();
        List<double[]> outTight = new ArrayList<>();
        List<int[]> outFaces = options.keepLineage() || baking ? new ArrayList<>() : null;
        final float[] noOffset = new float[AXES];
        for (int[] triangles : Clusters.split(result.mesh(), Clusters.MAX_TRIANGLES)) {
            if (triangles.length == 0) {
                continue;
            }
            final float[] corners = Clusters.corners(result.mesh(), triangles, noOffset);
            final int[] materials = new int[triangles.length];
            final float[] visibility = new float[triangles.length];
            for (int i = 0; i < triangles.length; ++i) {
                materials[i] = result.mesh().material(triangles[i]);
                visibility[i] = result.mesh().visibility(triangles[i]);
            }
            final double[] tight = Clusters.tightSphere(corners);
            outCorners.add(corners);
            outMaterials.add(materials);
            outVisibility.add(visibility);
            if (averaged != null) {
                final int[] attributes = new int[triangles.length * Attributes.PER_TRIANGLE];
                for (int i = 0; i < triangles.length; ++i) {
                    System.arraycopy(
                            averaged,
                            triangles[i] * Attributes.PER_TRIANGLE,
                            attributes,
                            i * Attributes.PER_TRIANGLE,
                            Attributes.PER_TRIANGLE);
                }
                outAttributes.add(attributes);
            } else {
                // Each output triangle stays in the chart it started in, so it takes its
                // attributes from that side of any seam
                final int[] charts = new int[triangles.length];
                for (int i = 0; i < triangles.length; ++i) {
                    charts[i] =
                            seams == null
                                    ? AttributeTransfer.ANY_CHART
                                    : seams.charts[result.faceIds()[triangles[i]]];
                }
                final int[] transferred =
                        transfer.isEmpty()
                                ? Attributes.NONE
                                : transfer.transfer(
                                        corners,
                                        materials,
                                        charts,
                                        origins(
                                                result.faceIds(),
                                                triangles,
                                                sourceCluster,
                                                sourceTriangle));
                // A baked cluster's are laid out once the group's error is known, see below
                outAttributes.add(transferred);
            }
            outTight.add(tight);
            spheres.add(tight);
            if (outFaces != null) {
                final int[] faces = new int[triangles.length];
                for (int i = 0; i < triangles.length; ++i) {
                    faces[i] = result.faceIds()[triangles[i]];
                }
                outFaces.add(faces);
            }
        }
        // Measure how far the output is from the base triangles it stands for, and add the bases'
        // own error, which is 0 for leaves: by the triangle inequality that bounds how far the
        // output is from the leaves, without adding up every level's step on the way. Never under
        // a child's, so a cluster's error doesn't fall going up.
        // With a volume, the base triangles under it are its own, and the rest's are measured (the
        // split is made above, with the volume)
        double baseError = 0;
        LodMesh.Builder baseBuilder = LodMesh.builder(dag.getOrigin());
        for (long packed : bases) {
            final LodDag.Cluster base = dag.clusters().get(baseCluster(packed));
            final int t = baseTriangle(packed);
            baseError = Math.max(baseError, base.error());
            final float[] corners = base.corners();
            final int[] v = new int[AXES];
            for (int corner = 0; corner < AXES; ++corner) {
                final int at = t * TRIANGLE_VALUES + corner * AXES;
                v[corner] = baseBuilder.vertex(corners[at], corners[at + 1], corners[at + 2]);
            }
            baseBuilder.triangle(v[0], v[1], v[2], base.materials()[t], base.visibility()[t]);
        }
        final LodMesh baseMesh = baseBuilder.build();
        final double simplifyError =
                result.mesh().getTriangleCount() == 0 || baseMesh.getTriangleCount() == 0
                        ? 0
                        : Hausdorff.between(
                                result.mesh(), baseMesh, MEASURE_SPACING * edge * EDGE_GROWTH);
        // A volume stands for what is in it no closer than a voxel
        final double error =
                Math.max(
                        Math.max(childError, baseError + simplifyError),
                        volume == null ? 0 : volume.voxelSize());
        int[] bakeTiles = baking ? new int[outCorners.size()] : new int[0];
        final List<BakeTexels> bakes = new ArrayList<>();
        final Correspondence map = baking ? new Correspondence(merged, result.history()) : null;
        final List<BakeLayout> layouts = new ArrayList<>();
        final BakeTexels.Source source =
                face -> new int[] {sourceCluster.getInt(face), sourceTriangle.getInt(face)};
        for (int i = 0; i < bakeTiles.length; ++i) {
            final BakeLayout layout = layoutFor(outCorners.get(i), error);
            layouts.add(layout);
            bakeTiles[i] = LodDag.packTile(layout.width(), layout.height());
            outAttributes.set(i, bakedAttributes(outCorners.get(i), outAttributes.get(i), layout));
            bakes.add(
                    BakeTexels.of(
                            outCorners.get(i),
                            layout.uvs(),
                            layout.width(),
                            layout.height(),
                            outFaces.get(i),
                            map,
                            source,
                            dag));
        }
        // What no texel samples, laid onto the texels it stands over
        BakeTexels.splat(bakes, outCorners, layouts, merged, source, dag);
        final List<long[]> outBases =
                outCorners.isEmpty() ? new ArrayList<>() : shareOut(bases, outCorners);
        if (volume != null) {
            // The volume, as a cluster of no triangles after the rest
            outCorners.add(new float[0]);
            outMaterials.add(new int[0]);
            outVisibility.add(new float[0]);
            outAttributes.add(Attributes.NONE);
            final double[] tight = sphereOf(volume);
            outTight.add(tight);
            spheres.add(tight);
            if (outFaces != null) {
                outFaces.add(new int[0]);
            }
            outBases.add(volumeBases);
            if (baking) {
                // Nothing of it bakes
                bakeTiles = Arrays.copyOf(bakeTiles, bakeTiles.length + 1);
            }
        }
        final double[] bound = Clusters.enclose(spheres);
        final int[] edgeCounts = countEdges(merged);
        return new Built(
                children,
                level,
                outCorners,
                outMaterials,
                outVisibility,
                outAttributes,
                outTight,
                error,
                bound,
                simplifyError,
                result.error(),
                trianglesIn,
                result.mesh().getTriangleCount(),
                edgeCounts[0],
                edgeCounts[1],
                result.virtualEdges(),
                result.rejectedFlips(),
                result.rejectedLinks(),
                System.nanoTime() - start,
                options.keepLineage()
                        ? new Lineage.Group(
                                new Correspondence(merged, result.history()),
                                sourceCluster.toIntArray(),
                                sourceTriangle.toIntArray())
                        : null,
                options.keepLineage() ? outFaces : null,
                outBases,
                bakeTiles,
                bakes,
                volumeKinds,
                volume);
    }

    /**
     * The attributes of a baked cluster: each corner keeps the normal it was given, or its
     * triangle's own where it had none, and takes its place in the cluster's bake tile as its
     * texture coordinates, with a tangent along the tile's u.
     *
     * @param corners The cluster's corners.
     * @param transferred The attributes it was given, or {@link Attributes#NONE}.
     * @param layout Its layout in its tile.
     * @return Its attributes.
     */
    private static int[] bakedAttributes(
            float @NonNull [] corners, int @NonNull [] transferred, @NonNull BakeLayout layout) {
        final float[] uv = layout.uvs();
        final int triangles = corners.length / TRIANGLE_VALUES;
        final int[] out = new int[triangles * Attributes.PER_TRIANGLE];
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        final double[] face = new double[AXES];
        final double[] tangent = new double[AXES];
        final double[] bitangent = new double[AXES];
        final float[] normal = new float[AXES];
        final float[] along = new float[AXES];
        for (int t = 0; t < triangles; ++t) {
            final int base = t * TRIANGLE_VALUES;
            for (int axis = 0; axis < AXES; ++axis) {
                a[axis] = corners[base + axis];
                b[axis] = corners[base + AXES + axis];
                c[axis] = corners[base + 2 * AXES + axis];
            }
            Geometry.normal(a, b, c, face);
            normalize(face);
            tangentFrame(a, b, c, uv, t * AXES * 2, face, tangent, bitangent);
            final boolean keepsNormal =
                    Attributes.textured(transferred, t)
                            && !Attributes.isAveraged(transferred, t * Attributes.PER_TRIANGLE);
            for (int corner = 0; corner < AXES; ++corner) {
                final int at = t * Attributes.PER_TRIANGLE + corner * Attributes.VALUES;
                if (keepsNormal) {
                    Attributes.decodeDirection(transferred[at], normal);
                } else {
                    normal[0] = (float) face[0];
                    normal[1] = (float) face[1];
                    normal[2] = (float) face[2];
                }
                // The tangent at right angles to this corner's normal
                final double along0 =
                        tangent[0] * normal[0] + tangent[1] * normal[1] + tangent[2] * normal[2];
                along[0] = (float) (tangent[0] - normal[0] * along0);
                along[1] = (float) (tangent[1] - normal[1] * along0);
                along[2] = (float) (tangent[2] - normal[2] * along0);
                final double cx = normal[1] * along[2] - normal[2] * along[1];
                final double cy = normal[2] * along[0] - normal[0] * along[2];
                final double cz = normal[0] * along[1] - normal[1] * along[0];
                final boolean flipped =
                        cx * bitangent[0] + cy * bitangent[1] + cz * bitangent[2] < 0;
                out[at] = Attributes.encodeDirection(normal);
                out[at + 1] = (Attributes.encodeDirection(along) & ~1) | (flipped ? 1 : 0);
                final int place = (t * AXES + corner) * 2;
                out[at + 2] =
                        (Float.floatToFloat16(uv[place]) & LOW_16)
                                | (Float.floatToFloat16(uv[place + 1]) << Short.SIZE);
            }
        }
        return out;
    }

    /**
     * The directions a triangle's texture coordinates increase along, in the world.
     *
     * @param a Its first corner.
     * @param b Its second.
     * @param c Its third.
     * @param uv Texture coordinates, u and v of each corner.
     * @param at Where the triangle's start in them.
     * @param face Its unnormalized normal, for a fallback.
     * @param tangent Receives the direction of increasing u, normalized.
     * @param bitangent Receives the direction of increasing v, normalized.
     */
    private static void tangentFrame(
            double @NonNull [] a,
            double @NonNull [] b,
            double @NonNull [] c,
            float @NonNull [] uv,
            int at,
            double @NonNull [] face,
            double @NonNull [] tangent,
            double @NonNull [] bitangent) {
        final double du1 = uv[at + 2] - uv[at];
        final double dv1 = uv[at + 3] - uv[at + 1];
        final double du2 = uv[at + 4] - uv[at];
        final double dv2 = uv[at + 5] - uv[at + 1];
        final double det = du1 * dv2 - du2 * dv1;
        for (int axis = 0; axis < AXES; ++axis) {
            final double e1 = b[axis] - a[axis];
            final double e2 = c[axis] - a[axis];
            tangent[axis] = det == 0 ? 0 : (e1 * dv2 - e2 * dv1) / det;
            bitangent[axis] = det == 0 ? 0 : (e2 * du1 - e1 * du2) / det;
        }
        if (Geometry.dot(tangent, tangent) == 0) {
            // No texture area: any direction across the face will do
            for (int axis = 0; axis < AXES; ++axis) {
                tangent[axis] = b[axis] - a[axis];
            }
            bitangent[0] = face[1] * tangent[2] - face[2] * tangent[1];
            bitangent[1] = face[2] * tangent[0] - face[0] * tangent[2];
            bitangent[2] = face[0] * tangent[1] - face[1] * tangent[0];
        }
        normalize(tangent);
        normalize(bitangent);
    }

    /**
     * Scale a vector to unit length, unless it is zero.
     *
     * @param v The vector.
     */
    private static void normalize(double @NonNull [] v) {
        final double length = Math.sqrt(Geometry.dot(v, v));
        if (length > 0) {
            v[0] /= length;
            v[1] /= length;
            v[2] /= length;
        }
    }

    /**
     * Lay a baked cluster out in the smallest tile with the options' density of texels across the
     * cluster's error, so that it looks no blurrier than the cut allows, with sides up to twice the
     * options' tile, for clusters whose many small charts' gaps take up much of it.
     *
     * @param corners The cluster's corners.
     * @param error The cluster's error.
     * @return The layout.
     */
    private BakeLayout layoutFor(float @NonNull [] corners, double error) {
        return BakeLayout.at(corners, error / bakeDensity(), 2 * options.bakeTile());
    }

    /**
     * Texels across a baked cluster's error, at least 1.
     *
     * @return The density.
     */
    private double bakeDensity() {
        return Math.max(1, options.bakeDensity());
    }

    /** The share of a tile charts are packed into, the rest gaps and corners left over. */
    static final double BAKE_PACKING = 0.6;

    /**
     * Whether a group bakes its output: when baking is on and a child was baked, or some of its
     * input is textured and a tile per output cluster gives the options' density of texels across
     * its children's error, which the group's own is never under. Drawn only where its error is
     * under the threshold, a texel then covers no more than that many pixels, so the bake looks no
     * blurrier than the cut allows. Each output cluster stands for about twice the surface of a
     * child, simplified to half.
     *
     * @param children The group's children.
     * @return True if it bakes.
     */
    private boolean bakes(int @NonNull [] children) {
        if (options.bakeTile() <= 0 || anyCutOut(children)) {
            return false;
        }
        double childError = 0;
        double area = 0;
        boolean textured = false;
        for (int id : children) {
            final LodDag.Cluster cluster = dag.clusters().get(id);
            if (cluster.producer() >= 0
                    && dag.groups().get(cluster.producer()).bakedClusters() > 0) {
                // Baked children have only their tiles' charts left to keep; carry on baking
                return true;
            }
            childError = Math.max(childError, cluster.error());
            textured |= cluster.attributes().length > 0;
            area += Clusters.area(cluster.corners());
        }
        if (!textured || childError <= 0) {
            return false;
        }
        final double clusterArea = 2 * area / children.length;
        final double texels = BAKE_PACKING * options.bakeTile() * options.bakeTile();
        return Math.sqrt(clusterArea / texels) * bakeDensity() <= childError;
    }

    /**
     * Take out a merged group's small parts: connected parts no wider than the children's error, by
     * {@link Geometry#partWidths}, which is also what the measured error charges for them, so
     * folding never raises it; and that go on nowhere past the group: no corner shared with another
     * group or the cell's neighbors, and none on the open border. A part locked only where it rests
     * against another group's faces, as a chest on someone else's floor, folds: taking it out whole
     * leaves no crack. The largest part, by area, always stays, so a group never comes out empty.
     * The parts' triangles stay sources for the blended look, so their color lands on the surfaces
     * around them, as far as those face the same way.
     *
     * @param merged The merged group.
     * @param childError The children's largest error.
     * @param shared The corners other groups or cells share.
     * @param sourceCluster Each merged triangle's source cluster, filtered in place.
     * @param sourceTriangle Each merged triangle's index in its cluster, filtered in place.
     * @return The mesh without its small parts, with the same vertices; or the mesh itself if none
     *     went.
     */
    private LodMesh foldSmallParts(
            @NonNull LodMesh merged,
            double childError,
            @NonNull Set<Grouping.Corner> shared,
            @NonNull IntArrayList sourceCluster,
            @NonNull IntArrayList sourceTriangle) {
        final int[] part = VirtualEdges.components(merged);
        final int vertices = merged.getVertexCount();
        final double[] widths = Geometry.partWidths(merged, part);
        final boolean[] locked = new boolean[vertices];
        for (int v = 0; v < vertices; ++v) {
            final int root = part[v];
            final Grouping.Corner corner =
                    new Grouping.Corner(
                            Float.floatToIntBits((float) merged.position(v, 0) + 0f),
                            Float.floatToIntBits((float) merged.position(v, 1) + 0f),
                            Float.floatToIntBits((float) merged.position(v, 2) + 0f));
            locked[root] |= shared.contains(corner) || openBorder.contains(corner);
        }
        final double[] area = new double[vertices];
        int largest = -1;
        for (int t = 0; t < merged.getTriangleCount(); ++t) {
            final int root = part[merged.corner(t, 0)];
            area[root] += Geometry.triangleArea(merged, t);
            if (largest < 0 || area[root] > area[largest]) {
                largest = root;
            }
        }
        final boolean[] fold = new boolean[vertices];
        boolean any = false;
        for (int root = 0; root < vertices; ++root) {
            if (part[root] != root || locked[root] || root == largest || area[root] <= 0) {
                continue;
            }
            if (widths[root] <= childError) {
                fold[root] = true;
                any = true;
            }
        }
        if (!any) {
            return merged;
        }
        final boolean[] drop = new boolean[merged.getTriangleCount()];
        for (int t = 0; t < drop.length; ++t) {
            drop[t] = fold[part[merged.corner(t, 0)]];
        }
        return dropFaces(merged, drop, sourceCluster, sourceTriangle);
    }

    /**
     * A merged group without some of its triangles, with the same vertices and locks.
     *
     * @param merged The merged group.
     * @param drop Which triangles to leave out.
     * @param sourceCluster Each merged triangle's source cluster, filtered in place.
     * @param sourceTriangle Each merged triangle's index in its cluster, filtered in place.
     * @return The mesh without them.
     */
    private static LodMesh dropFaces(
            @NonNull LodMesh merged,
            boolean @NonNull [] drop,
            @NonNull IntArrayList sourceCluster,
            @NonNull IntArrayList sourceTriangle) {
        final int vertices = merged.getVertexCount();
        final LodMesh.Builder kept =
                LodMesh.builder(merged.getOrigin()).leafError(merged.getLeafError());
        for (int v = 0; v < vertices; ++v) {
            final int at =
                    kept.vertex(
                            (float) merged.position(v, 0),
                            (float) merged.position(v, 1),
                            (float) merged.position(v, 2));
            if (at != v) {
                throw new IllegalStateException("Merged vertices weren't distinct");
            }
            if (merged.isLocked(v)) {
                kept.lock(v);
            }
        }
        final int[] clusters = sourceCluster.toIntArray();
        final int[] triangles = sourceTriangle.toIntArray();
        sourceCluster.clear();
        sourceTriangle.clear();
        for (int t = 0; t < merged.getTriangleCount(); ++t) {
            if (drop[t]) {
                continue;
            }
            if (kept.triangle(
                    merged.corner(t, 0),
                    merged.corner(t, 1),
                    merged.corner(t, 2),
                    merged.material(t),
                    merged.visibility(t))) {
                sourceCluster.addInt(clusters[t]);
                sourceTriangle.addInt(triangles[t]);
            }
        }
        return kept.build();
    }

    /**
     * Whether a group blends its input's look per vertex: when averages are given and its
     * children's error is at least as big as the largest texture repeat among their triangles, so a
     * repeat covers no more than the error threshold's pixels where the group is drawn and its
     * texture can't be told apart. A projected triangle's texture repeats every unit; a textured
     * one's every unit of texture coordinates, measured on it; an averaged one has no texture left.
     *
     * @param children The group's children.
     * @return True if it averages.
     */
    private boolean averages(int @NonNull [] children) {
        if (options.averages() == null || anyCutOut(children)) {
            return false;
        }
        double childError = 0;
        double repeat = 0;
        for (int id : children) {
            final LodDag.Cluster cluster = dag.clusters().get(id);
            childError = Math.max(childError, cluster.error());
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                repeat = Math.max(repeat, repeatOf(cluster, t));
            }
        }
        return childError > 0 && childError >= repeat;
    }

    /**
     * Split an input into leaf clusters, cut out triangles apart from the rest, so no cluster mixes
     * them: a cut out cluster is drawn alpha tested, and its groups can't blend or bake.
     *
     * @param mesh The input.
     * @return The triangles of each cluster.
     */
    private int[][] leafClusters(@NonNull LodMesh mesh) {
        if (options.cutOut() == null) {
            return Clusters.split(mesh, Clusters.MAX_TRIANGLES, options.threads());
        }
        final IntArrayList cut = new IntArrayList();
        final IntArrayList rest = new IntArrayList();
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            (options.isCutOut(mesh.material(t)) ? cut : rest).addInt(t);
        }
        if (cut.isEmpty()) {
            return Clusters.split(mesh, Clusters.MAX_TRIANGLES, options.threads());
        }
        final List<int[]> out = new ArrayList<>();
        for (IntArrayList kind : List.of(rest, cut)) {
            if (!kind.isEmpty()) {
                out.addAll(
                        List.of(
                                Clusters.split(
                                        mesh,
                                        Clusters.MAX_TRIANGLES,
                                        options.threads(),
                                        kind.toIntArray())));
            }
        }
        return out.toArray(new int[0][]);
    }

    /**
     * Group a level's clusters, cut out ones apart from the rest, so the rest still blend and bake
     * where cut out triangles would stop them.
     *
     * @param active The level's clusters.
     * @return The groups.
     */
    private int[][] group(int @NonNull [] active) {
        if (options.cutOut() == null) {
            return Grouping.group(dag, active);
        }
        final IntArrayList cut = new IntArrayList();
        final IntArrayList rest = new IntArrayList();
        for (int id : active) {
            (anyCutOut(new int[] {id}) ? cut : rest).addInt(id);
        }
        final List<int[]> out = new ArrayList<>();
        for (IntArrayList kind : List.of(rest, cut)) {
            if (!kind.isEmpty()) {
                out.addAll(List.of(Grouping.group(dag, kind.toIntArray())));
            }
        }
        return out.toArray(new int[0][]);
    }

    /**
     * Whether each of a group's merged triangles would go to a volume rather than stay a triangle:
     * a cut out one once the children's error is past its texture's repeat, so its holes can't be
     * told apart, and any one that isn't on a macro-surface at the resolution the group is about to
     * reach, taken as {@link #VOLUME_RESOLUTION_STEP} times its middle triangle's error. The
     * middle, not the largest: one child carrying a large error up from far below would otherwise
     * make the whole group finer than its resolution, though the rest of it is drawn up close.
     *
     * @param children The group's children.
     * @param merged The merged triangles.
     * @param childError The children's largest error.
     * @param sourceCluster Each merged triangle's source cluster.
     * @param sourceTriangle Each merged triangle's index in its cluster.
     * @return Each child's triangles' kinds, by child id; a triangle split in welding takes the
     *     kind of its most volume-like piece.
     */
    private Map<Integer, byte[]> volumeKinds(
            int @NonNull [] children,
            @NonNull LodMesh merged,
            double childError,
            @NonNull IntArrayList sourceCluster,
            @NonNull IntArrayList sourceTriangle) {
        final boolean[] macro =
                MacroSurfaces.classify(merged, medianError(children) * VOLUME_RESOLUTION_STEP);
        final Map<Integer, byte[]> out = new HashMap<>();
        for (int id : children) {
            out.put(id, new byte[dag.clusters().get(id).triangleCount()]);
        }
        for (int f = 0; f < merged.getTriangleCount(); ++f) {
            final int id = sourceCluster.getInt(f);
            final int t = sourceTriangle.getInt(f);
            final LodDag.Cluster cluster = dag.clusters().get(id);
            byte kind = LodDag.VOLUME_NONE;
            if (options.isCutOut(cluster.materials()[t])
                    && (options.volumes() || childError >= repeatOf(cluster, t))) {
                kind = LodDag.VOLUME_CUT_OUT;
            } else if (!macro[f]) {
                kind = LodDag.VOLUME_GEOMETRY;
            }
            final byte[] kinds = out.get(id);
            kinds[t] = (byte) Math.max(kinds[t], kind);
        }
        return out;
    }

    /**
     * Whether a triangle has an edge between two locked corners: a border another group or cell
     * keeps.
     *
     * @param mesh The mesh.
     * @param f The triangle.
     * @return True if it does.
     */
    private static boolean onBorderEdge(@NonNull LodMesh mesh, int f) {
        for (int corner = 0; corner < AXES; ++corner) {
            if (mesh.isLocked(mesh.corner(f, corner))
                    && mesh.isLocked(mesh.corner(f, (corner + 1) % AXES))) {
                return true;
            }
        }
        return false;
    }

    /**
     * What of a group goes to a volume.
     *
     * @param faces Which merged triangles do.
     * @param corners Every merged triangle's corners, before any left, 9 floats each.
     * @param voxelSize How wide the volume's voxels are.
     */
    private record VolumePart(boolean[] faces, float[] corners, double voxelSize) {}

    /**
     * What of a group goes to a volume, if anything is worth it: its triangles finer than its
     * resolution or cut out past their texture's detail ({@link #volumeKinds}), where the voxels
     * they would take are fewer than they are, or all of it its children's volumes stand for. A
     * volume's voxels are {@link #VOXEL_STEP} times its middle child's error wide, and no finer
     * than its children's volumes', or it would take more voxels than they did. Its error is a
     * voxel, which then raises the group's little past what its children already reach. Cut out
     * leaves go at once, their voxels widened until they cost no more than the leaves do.
     *
     * @param children The group's children.
     * @param merged The merged triangles.
     * @param kinds Each child's triangles' kinds.
     * @param sourceCluster Each merged triangle's source cluster.
     * @param sourceTriangle Each merged triangle's index in its cluster.
     * @return What goes, or null for nothing.
     */
    private VolumePart volumePart(
            int @NonNull [] children,
            @NonNull LodMesh merged,
            @NonNull Map<Integer, byte[]> kinds,
            @NonNull IntArrayList sourceCluster,
            @NonNull IntArrayList sourceTriangle) {
        double voxel = medianError(children) * VOXEL_STEP;
        boolean fromChildren = false;
        for (int id : children) {
            final CoverageVolume own = dag.volumeOf(id);
            if (own != null) {
                fromChildren = true;
                voxel = Math.max(voxel, own.voxelSize());
            }
        }
        final int n = merged.getTriangleCount();
        final boolean[] faces = new boolean[n];
        int count = 0;
        int cutOut = 0;
        double area = 0;
        double cutOutArea = 0;
        for (int f = 0; f < n; ++f) {
            final byte[] own = kinds.get(sourceCluster.getInt(f));
            final byte kind = own == null ? LodDag.VOLUME_NONE : own[sourceTriangle.getInt(f)];
            // A triangle on a border edge, between two locked corners, stays: another group or
            // cell keeps that edge, and leaving it would open the border where the volume doesn't
            // reach
            if (kind != LodDag.VOLUME_NONE && !onBorderEdge(merged, f)) {
                faces[f] = true;
                ++count;
                final double faceArea = Geometry.triangleArea(merged, f);
                area += faceArea;
                if (kind == LodDag.VOLUME_CUT_OUT) {
                    ++cutOut;
                    cutOutArea += faceArea;
                }
            }
        }
        if (cutOut > 0) {
            // Cut out leaves go as soon as voxels cost no more than they do: voxels at least that
            // wide
            voxel = Math.max(voxel, Math.sqrt(VOXELS_PER_FACE_AREA * cutOutArea / cutOut));
        }
        if (voxel <= 0) {
            return null;
        }
        if (count == 0 && !fromChildren) {
            return null;
        }
        // A surface passes through about its area over a voxel's face of voxels, and the blur
        // reaches as many again; cut out leaves' voxels are already sized to pass
        if (!fromChildren && cutOut == 0 && VOXELS_PER_FACE_AREA * area / (voxel * voxel) > count) {
            return null;
        }
        final float[] corners = new float[n * TRIANGLE_VALUES];
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        for (int f = 0; f < n; ++f) {
            Geometry.corners(merged, f, a, b, c);
            for (int axis = 0; axis < AXES; ++axis) {
                corners[f * TRIANGLE_VALUES + axis] = (float) a[axis];
                corners[f * TRIANGLE_VALUES + AXES + axis] = (float) b[axis];
                corners[f * TRIANGLE_VALUES + 2 * AXES + axis] = (float) c[axis];
            }
        }
        return new VolumePart(faces, corners, voxel);
    }

    /**
     * Split a group's base triangles between its volume and the rest: all of its volume children's
     * go to the volume, and each other one to whichever its nearest merged triangle went to.
     *
     * @param children The group's children.
     * @param bases The group's packed base triangles, its children's in order.
     * @param part What goes to the volume.
     * @return The rest's base triangles, then the volume's.
     */
    private long[][] splitBases(
            int @NonNull [] children, long @NonNull [] bases, @NonNull VolumePart part) {
        final IntArrayList rest = new IntArrayList();
        final IntArrayList inVolume = new IntArrayList();
        final TriangleBvh tree = part.faces().length == 0 ? null : new TriangleBvh(part.corners());
        final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
        final double[] middle = new double[AXES];
        int at = 0;
        for (int id : children) {
            final int own = basis.get(id).length;
            final boolean volumeChild = dag.volumeOf(id) != null;
            for (int b = at; b < at + own; ++b) {
                boolean toVolume = volumeChild;
                if (!toVolume && tree != null) {
                    final float[] corners = dag.clusters().get(baseCluster(bases[b])).corners();
                    final int t = baseTriangle(bases[b]) * TRIANGLE_VALUES;
                    for (int axis = 0; axis < AXES; ++axis) {
                        middle[axis] =
                                (corners[t + axis]
                                                + corners[t + AXES + axis]
                                                + corners[t + 2 * AXES + axis])
                                        / AXES;
                    }
                    tree.nearest(middle, Double.POSITIVE_INFINITY, scratch);
                    toVolume = scratch.triangle >= 0 && part.faces()[scratch.triangle];
                }
                (toVolume ? inVolume : rest).addInt(b);
            }
            at += own;
        }
        final long[] restBases = new long[rest.size()];
        for (int i = 0; i < restBases.length; ++i) {
            restBases[i] = bases[rest.getInt(i)];
        }
        final long[] volumeBases = new long[inVolume.size()];
        for (int i = 0; i < volumeBases.length; ++i) {
            volumeBases[i] = bases[inVolume.getInt(i)];
        }
        return new long[][] {restBases, volumeBases};
    }

    /**
     * Voxelize base triangles into a group's volume.
     *
     * @param bases The packed base triangles.
     * @param voxelSize How wide the voxels are.
     * @param children The group's children, whose ids seed the rays, so a build is repeatable.
     * @return The volume.
     */
    private CoverageVolume voxelize(
            long @NonNull [] bases, double voxelSize, int @NonNull [] children) {
        final float[] corners = new float[bases.length * TRIANGLE_VALUES];
        final int[] materials = new int[bases.length];
        for (int b = 0; b < bases.length; ++b) {
            final LodDag.Cluster base = dag.clusters().get(baseCluster(bases[b]));
            final int t = baseTriangle(bases[b]);
            System.arraycopy(
                    base.corners(),
                    t * TRIANGLE_VALUES,
                    corners,
                    b * TRIANGLE_VALUES,
                    TRIANGLE_VALUES);
            materials[b] = base.materials()[t];
        }
        // Children's volumes with no base triangles, as a parent cell's inputs are, stand for
        // leaves
        // only they had: they go in as finer volumes
        final List<CoverageVolume> sources = new ArrayList<>();
        for (int id : children) {
            final CoverageVolume own = dag.volumeOf(id);
            if (own != null && basis.get(id).length == 0) {
                sources.add(own);
            }
        }
        return CoverageVoxelizer.voxelize(
                corners,
                materials,
                options.averages(),
                sources,
                voxelSize,
                Arrays.hashCode(children));
    }

    /**
     * A sphere around a volume's voxels.
     *
     * @param volume The volume.
     * @return x, y, z, radius.
     */
    private static double[] sphereOf(@NonNull CoverageVolume volume) {
        final double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        final double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        final double[] origin = volume.origin();
        for (int v = 0; v < volume.size(); ++v) {
            for (int axis = 0; axis < AXES; ++axis) {
                final double at =
                        origin[axis]
                                + CoverageVolume.coordinate(volume.keyOf(v), axis)
                                        * volume.voxelSize();
                low[axis] = Math.min(low[axis], at);
                high[axis] = Math.max(high[axis], at + volume.voxelSize());
            }
        }
        double radius = 0;
        final double[] center = new double[AXES + 1];
        for (int axis = 0; axis < AXES; ++axis) {
            center[axis] = (low[axis] + high[axis]) / 2;
            radius += (high[axis] - low[axis]) * (high[axis] - low[axis]);
        }
        center[AXES] = Math.sqrt(radius) / 2;
        return center;
    }

    /**
     * The error of a group's middle triangle: its children's errors, each standing for its
     * triangles.
     *
     * @param children The children.
     * @return The error.
     */
    private double medianError(int @NonNull [] children) {
        final double[][] errors = new double[children.length][];
        int total = 0;
        for (int i = 0; i < children.length; ++i) {
            final LodDag.Cluster cluster = dag.clusters().get(children[i]);
            errors[i] = new double[] {cluster.error(), cluster.triangleCount()};
            total += cluster.triangleCount();
        }
        Arrays.sort(errors, (a, b) -> Double.compare(a[0], b[0]));
        double seen = 0;
        for (double[] error : errors) {
            seen += error[1];
            if (seen * 2 >= total) {
                return error[0];
            }
        }
        return 0;
    }

    /**
     * Whether any of a group's children has a triangle of a cut out material.
     *
     * @param children The children.
     * @return True if one does.
     */
    private boolean anyCutOut(int @NonNull [] children) {
        if (options.cutOut() == null) {
            return false;
        }
        for (int id : children) {
            for (int material : dag.clusters().get(id).materials()) {
                if (options.isCutOut(material)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * How far apart, in world units, a triangle's texture repeats.
     *
     * @param cluster Its cluster.
     * @param t The triangle.
     * @return The distance; 0 for an averaged triangle, infinite for texture coordinates that don't
     *     change across it, whose texture never repeats.
     */
    private static double repeatOf(LodDag.@NonNull Cluster cluster, int t) {
        final int[] attributes = cluster.attributes();
        if (!Attributes.textured(attributes, t)) {
            return PROJECTED_REPEAT;
        }
        final int first = t * Attributes.PER_TRIANGLE;
        if (Attributes.isAveraged(attributes, first)) {
            return 0;
        }
        final float[] corners = cluster.corners();
        final int at = t * TRIANGLE_VALUES;
        final double[] u = new double[AXES];
        final double[] v = new double[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            u[axis] = corners[at + AXES + axis] - corners[at + axis];
            v[axis] = corners[at + 2 * AXES + axis] - corners[at + axis];
        }
        final double cx = u[1] * v[2] - u[2] * v[1];
        final double cy = u[2] * v[0] - u[0] * v[2];
        final double cz = u[0] * v[1] - u[1] * v[0];
        final double worldArea = Math.sqrt(cx * cx + cy * cy + cz * cz) / 2;
        final float[][] uv = new float[LodMesh.CORNERS][];
        for (int c = 0; c < LodMesh.CORNERS; ++c) {
            uv[c] = Attributes.textureCoordinates(attributes, first + c * Attributes.VALUES);
        }
        final double uvArea =
                Math.abs(
                                (uv[1][0] - uv[0][0]) * (uv[2][1] - uv[0][1])
                                        - (uv[2][0] - uv[0][0]) * (uv[1][1] - uv[0][1]))
                        / 2;
        if (uvArea <= 0) {
            return worldArea <= 0 ? 0 : Double.POSITIVE_INFINITY;
        }
        return Math.sqrt(worldArea / uvArea);
    }

    /**
     * What an input triangle looks like averaged: an averaged triangle's corners' looks, evened
     * out, or else its material's average. A named averaged corner's look is its material's average
     * times its shade.
     *
     * @param attributes Its cluster's attributes.
     * @param t The triangle.
     * @param material Its material.
     * @param out Receives the {@link MaterialAverages#VALUES} values.
     * @return The material the look is mostly of: a named triangle's first corner's, else its own.
     */
    private int lookOf(int @NonNull [] attributes, int t, int material, float @NonNull [] out) {
        final int first = t * Attributes.PER_TRIANGLE;
        if (Attributes.textured(attributes, t) && Attributes.isAveraged(attributes, first)) {
            Arrays.fill(out, 0);
            final float[] corner = new float[MaterialAverages.VALUES];
            for (int c = 0; c < LodMesh.CORNERS; ++c) {
                final int at = first + c * Attributes.VALUES;
                if (Attributes.isNamedAverage(attributes, at)) {
                    lookOfNamed(attributes, at, corner);
                } else {
                    Attributes.averageOf(attributes, at, corner);
                }
                for (int k = 0; k < out.length; ++k) {
                    out[k] += corner[k] / LodMesh.CORNERS;
                }
            }
            return Attributes.isNamedAverage(attributes, first)
                    ? Attributes.namedMaterial(attributes, first)
                    : material;
        }
        final float[] average = options.averages().average(material);
        System.arraycopy(average == null ? UNKNOWN_LOOK : average, 0, out, 0, out.length);
        return material;
    }

    /**
     * A named averaged corner's look: its material's average, its color times its shade.
     *
     * @param attributes Corners' ints.
     * @param at Where the corner starts.
     * @param out Receives the {@link MaterialAverages#VALUES} values.
     */
    private void lookOfNamed(int @NonNull [] attributes, int at, float @NonNull [] out) {
        final float[] average =
                options.averages().average(Attributes.namedMaterial(attributes, at));
        System.arraycopy(average == null ? UNKNOWN_LOOK : average, 0, out, 0, out.length);
        final float shade = Attributes.namedShade(attributes, at);
        for (int c = 0; c < AXES; ++c) {
            out[MaterialAverages.RED + c] *= shade;
        }
    }

    /**
     * Find a merged group's seams, from its triangles' sources: each corner takes its source
     * triangle's attributes where it lies, which are the source corner's own except where welding
     * T-junctions split a triangle.
     *
     * @param merged The merged group.
     * @param sourceCluster Each merged triangle's source cluster.
     * @param sourceTriangle Each merged triangle's triangle in it.
     * @return The seams.
     */
    private Seams seams(
            @NonNull LodMesh merged,
            @NonNull IntArrayList sourceCluster,
            @NonNull IntArrayList sourceTriangle) {
        final int triangles = merged.getTriangleCount();
        final int[] kinds = new int[triangles];
        final int[] attributes = new int[triangles * Attributes.PER_TRIANGLE];
        final double[] position = new double[AXES];
        final double[] weights = new double[AXES];
        for (int t = 0; t < triangles; ++t) {
            final LodDag.Cluster cluster = dag.clusters().get(sourceCluster.getInt(t));
            final int s = sourceTriangle.getInt(t);
            final int[] source = cluster.attributes();
            if (!Attributes.textured(source, s)) {
                kinds[t] = Seams.KIND_PROJECTED;
                continue;
            }
            if (Attributes.noTextureCoordinates(source[s * Attributes.PER_TRIANGLE + 2])) {
                kinds[t] = Seams.KIND_AVERAGED;
                continue;
            }
            kinds[t] = Seams.KIND_TEXTURED;
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = merged.corner(t, corner);
                for (int axis = 0; axis < AXES; ++axis) {
                    position[axis] = merged.position(v, axis);
                }
                AttributeTransfer.weights(
                        cluster.corners(), s * TRIANGLE_VALUES, position, weights);
                Attributes.blend(
                        source,
                        s * Attributes.PER_TRIANGLE,
                        weights,
                        attributes,
                        t * Attributes.PER_TRIANGLE + corner * Attributes.VALUES);
            }
        }
        return Seams.find(merged, kinds, attributes);
    }

    /**
     * The source triangles output triangles started as: each output triangle keeps the identity of
     * a merged triangle through simplifying, and each merged triangle came from a child's triangle.
     *
     * @param faceIds Which merged triangle each simplified triangle started as.
     * @param triangles The simplified triangles in this cluster.
     * @param sourceCluster Each merged triangle's source cluster.
     * @param sourceTriangle Each merged triangle's triangle in it.
     * @return Their origins.
     */
    private AttributeTransfer.Origins origins(
            int @NonNull [] faceIds,
            int @NonNull [] triangles,
            @NonNull IntArrayList sourceCluster,
            @NonNull IntArrayList sourceTriangle) {
        final float[] corners = new float[triangles.length * TRIANGLE_VALUES];
        final int[] attributes = new int[triangles.length * Attributes.PER_TRIANGLE];
        final int[] materials = new int[triangles.length];
        for (int i = 0; i < triangles.length; ++i) {
            final int f = faceIds[triangles[i]];
            final LodDag.Cluster cluster = dag.clusters().get(sourceCluster.getInt(f));
            final int s = sourceTriangle.getInt(f);
            System.arraycopy(
                    cluster.corners(),
                    s * TRIANGLE_VALUES,
                    corners,
                    i * TRIANGLE_VALUES,
                    TRIANGLE_VALUES);
            if (cluster.attributes().length > 0) {
                System.arraycopy(
                        cluster.attributes(),
                        s * Attributes.PER_TRIANGLE,
                        attributes,
                        i * Attributes.PER_TRIANGLE,
                        Attributes.PER_TRIANGLE);
            }
            materials[i] = cluster.materials()[s];
        }
        return new AttributeTransfer.Origins(corners, attributes, materials);
    }

    /**
     * Give the attribute transfer each textured source triangle once, in the chart its merged
     * triangle is in.
     *
     * @param transfer The transfer.
     * @param seams The group's seams, or null to give every source in no chart.
     * @param sourceCluster Each merged triangle's source cluster.
     * @param sourceTriangle Each merged triangle's triangle in it.
     */
    private void addSources(
            @NonNull AttributeTransfer transfer,
            Seams seams,
            @NonNull IntArrayList sourceCluster,
            @NonNull IntArrayList sourceTriangle) {
        final LongIntMap added = new LongIntMap(sourceCluster.size());
        for (int t = 0; t < sourceCluster.size(); ++t) {
            final int id = sourceCluster.getInt(t);
            final int s = sourceTriangle.getInt(t);
            final LodDag.Cluster cluster = dag.clusters().get(id);
            if (!Attributes.textured(cluster.attributes(), s)
                    || added.putIfAbsent(LongIntMap.pair(id, s), 0) != LongIntMap.MISSING) {
                continue;
            }
            transfer.add(
                    cluster.corners(),
                    s * TRIANGLE_VALUES,
                    cluster.attributes(),
                    s * Attributes.PER_TRIANGLE,
                    cluster.materials()[s],
                    seams == null ? AttributeTransfer.ANY_CHART : seams.charts[t]);
        }
    }

    /**
     * A mesh's average edge length, each edge counted once.
     *
     * @param mesh The mesh.
     * @return The length, or the leaves' if the mesh has no edges.
     */
    private double averageEdge(@NonNull LodMesh mesh) {
        final LongIntMap seen = new LongIntMap(mesh.getTriangleCount() * AXES);
        double total = 0;
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = mesh.corner(t, corner);
                final int w = mesh.corner(t, (corner + 1) % AXES);
                if (seen.putIfAbsent(LongIntMap.pair(Math.min(v, w), Math.max(v, w)), 0)
                        == LongIntMap.MISSING) {
                    double d = 0;
                    for (int axis = 0; axis < AXES; ++axis) {
                        final double delta = mesh.position(v, axis) - mesh.position(w, axis);
                        d += delta * delta;
                    }
                    total += Math.sqrt(d);
                }
            }
        }
        return seen.size() == 0 ? leafEdge : total / seen.size();
    }

    /**
     * Count a mesh's edges, and those between two locked vertices.
     *
     * @param mesh The mesh.
     * @return The locked edges, then all edges.
     */
    private static int[] countEdges(@NonNull LodMesh mesh) {
        final LongIntMap edges = new LongIntMap(mesh.getTriangleCount() * AXES);
        int locked = 0;
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = mesh.corner(t, corner);
                final int w = mesh.corner(t, (corner + 1) % AXES);
                if (edges.putIfAbsent(LongIntMap.pair(Math.min(v, w), Math.max(v, w)), 0)
                                == LongIntMap.MISSING
                        && mesh.isLocked(v)
                        && mesh.isLocked(w)) {
                    ++locked;
                }
            }
        }
        return new int[] {locked, edges.size()};
    }

    /**
     * Add a built group and its clusters to the DAG.
     *
     * @param built The group.
     * @param next Where the new clusters' ids go.
     */
    private void add(@NonNull Built built, @NonNull IntArrayList next) {
        final int level = built.level();
        final int groupId = dag.groups().size();
        final int[] outputs = new int[built.corners().size()];
        for (int i = 0; i < outputs.length; ++i) {
            outputs[i] = dag.clusters().size();
            basis.add(built.bases().get(i));
            dag.add(
                    new LodDag.Cluster(
                            outputs[i],
                            level + 1,
                            built.corners().get(i),
                            built.materials().get(i),
                            built.visibility().get(i),
                            built.attributes().get(i),
                            built.error(),
                            built.bound(),
                            built.tight().get(i),
                            groupId,
                            -1));
            next.addInt(outputs[i]);
            if (i < built.bakes().size()) {
                dag.setBake(outputs[i], built.bakes().get(i));
            }
            if (i == 0) {
                built.volumeKinds().forEach(dag::setVolumeKinds);
            }
            if (built.volume() != null && i == outputs.length - 1) {
                dag.setVolume(outputs[i], built.volume());
            }
            if (dag.getLineage() != null) {
                dag.getLineage().addCluster(built.faces().get(i));
            }
        }
        if (dag.getLineage() != null) {
            dag.getLineage().addGroup(built.lineage());
        }
        dag.add(
                new LodDag.Group(
                        groupId,
                        level,
                        built.children(),
                        outputs,
                        built.error(),
                        built.bound(),
                        built.simplifyError(),
                        built.quadricError(),
                        built.trianglesIn(),
                        built.trianglesOut(),
                        built.borderEdges(),
                        built.edges(),
                        built.virtualEdges(),
                        built.rejectedFlips(),
                        built.rejectedLinks(),
                        built.nanos(),
                        built.bakeTiles()));
    }
}
