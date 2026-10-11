package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.benchmark.SyntheticTerrain;
import com.ikalagaming.graphics.vulkan.LodCullMath;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Random;

/** Tests for the cut the GPU picks, against the CPU's. */
class LodCullMathTest {

    /** Screen pixels per unit at distance 1: 1080 pixels high, 60 degrees. */
    private static final double PIXELS = 1080 / (2 * Math.tan(Math.toRadians(30)));

    /** Section size. */
    private static final int SECTION = SyntheticTerrain.SECTION;

    /** How many random views to compare. */
    private static final int VIEWS = 200;

    /** How close, relative to the threshold, a projected error must be to go either way. */
    private static final double ROUNDING = 1e-4;

    /** Options for a test build, on two threads. */
    private static final DagBuilder.Options OPTIONS =
            new DagBuilder.Options(0.5, false, 2, false, 0);

    /** A single DAG of terrain. */
    private static LodDag single;

    /** The same terrain built in region cells, one section each. */
    private static LodDag cells;

    /** Build the DAGs once. */
    @BeforeAll
    static void build() {
        final List<LodMesh> sections = DagTest.terrain(2);
        single = DagBuilder.build(sections, OPTIONS);
        final LodStore store = new LodStore(new double[3], SECTION, 1, 4, OPTIONS);
        for (LodMesh mesh : sections) {
            final double[] origin = mesh.getOrigin();
            store.put(
                    new LodStore.SectionPos(
                            Math.floorDiv((int) origin[0], SECTION),
                            Math.floorDiv((int) origin[1], SECTION),
                            Math.floorDiv((int) origin[2], SECTION)),
                    mesh);
        }
        store.rebuild();
        cells = store.snapshot();
    }

    /** A packed entry holds the cluster's spheres and errors as floats. */
    @Test
    void testPackingRoundTrips() {
        final ByteBuffer out =
                ByteBuffer.allocate(LodCullMath.CLUSTER_ENTRY_SIZE).order(ByteOrder.nativeOrder());
        int roots = 0;
        for (LodDag.Cluster cluster : single.clusters()) {
            out.position(0);
            LodCullMath.putCluster(out, single, cluster.id(), LodCullMath.NO_MESH, 7);
            assertSphere(
                    cluster.tight(), LodCullMath.sphere(out, LodCullMath.CLUSTER_TIGHT_OFFSET));
            assertSphere(
                    cluster.bound(), LodCullMath.sphere(out, LodCullMath.CLUSTER_BOUND_OFFSET));
            assertSphere(
                    single.parentBound(cluster.id()),
                    LodCullMath.sphere(out, LodCullMath.CLUSTER_PARENT_BOUND_OFFSET));
            assertEquals((float) cluster.error(), out.getFloat(LodCullMath.CLUSTER_INFO_OFFSET), 0);
            final float parent = out.getFloat(LodCullMath.CLUSTER_INFO_OFFSET + Integer.BYTES);
            if (single.consumer(cluster.id()) < 0) {
                assertEquals(Float.POSITIVE_INFINITY, parent);
                ++roots;
            } else {
                assertEquals((float) single.parentError(cluster.id()), parent, 0);
            }
            assertEquals(
                    LodCullMath.NO_MESH,
                    out.getInt(LodCullMath.CLUSTER_INFO_OFFSET + 2 * Integer.BYTES));
            assertEquals(7, out.getInt(LodCullMath.CLUSTER_INFO_OFFSET + 3 * Integer.BYTES));
        }
        assertTrue(roots > 0);
    }

    /**
     * Check a packed sphere.
     *
     * @param expected The sphere in doubles.
     * @param actual The packed one.
     */
    private static void assertSphere(double[] expected, float[] actual) {
        for (int i = 0; i < actual.length; ++i) {
            assertEquals((float) expected[i], actual[i], 0);
        }
    }

    /** The float cut agrees with the CPU's, but for clusters within rounding of the threshold. */
    @Test
    void testAgreesWithCpuCut() {
        assertAgrees(single, 2);
        assertAgrees(cells, 2);
    }

    /**
     * Compare cuts from random views, failing on any disagreement not explained by rounding.
     *
     * @param dag The DAG.
     * @param columns Columns of sections it covers.
     */
    private static void assertAgrees(LodDag dag, int columns) {
        final double size = columns * SECTION;
        final Random random = new Random(7);
        int compared = 0;
        int rounded = 0;
        for (int v = 0; v < VIEWS; ++v) {
            final LodCut.View view = randomView(random, size);
            final double threshold = 0.25 + random.nextDouble() * 16;
            final boolean[] cpu = LodCut.select(dag, view, threshold);
            final boolean[] gpu = LodCullMath.select(dag, view, threshold);
            for (int i = 0; i < cpu.length; ++i) {
                ++compared;
                if (cpu[i] == gpu[i]) {
                    continue;
                }
                ++rounded;
                assertTrue(
                        nearThreshold(dag, i, view, threshold),
                        "Cluster " + i + " differs in view " + v + " away from the threshold");
            }
        }
        System.out.printf(
                "%d of %d cluster choices went the other way by rounding%n", rounded, compared);
    }

    /** No float cut has cracks or overlaps, so every leaf-to-root path switches once. */
    @Test
    void testFloatCutsAreCrackFree() {
        assertCrackFree(single, 2);
        assertCrackFree(cells, 2);
    }

    /**
     * Check random float cuts for cracks.
     *
     * @param dag The DAG.
     * @param columns Columns of sections it covers.
     */
    private static void assertCrackFree(LodDag dag, int columns) {
        final double size = columns * SECTION;
        final CrackCheck check = new CrackCheck(dag);
        final Random random = new Random(99);
        for (int v = 0; v < VIEWS; ++v) {
            final LodCut.View view = randomView(random, size);
            final double threshold = 0.25 + random.nextDouble() * 16;
            final CrackCheck.Result result = check.check(LodCullMath.select(dag, view, threshold));
            assertTrue(
                    result.isClean(),
                    "View "
                            + v
                            + ": "
                            + result.openEdges().size()
                            + " cracks, "
                            + result.overdrawnEdges().size()
                            + " edges drawn twice");
        }
    }

    /**
     * A view from around and above the terrain.
     *
     * @param random The randomness.
     * @param size The terrain's size along x and z.
     * @return The view.
     */
    private static LodCut.View randomView(Random random, double size) {
        return new LodCut.View(
                random.nextDouble() * 3 * size - size,
                random.nextDouble() * 3 * size - size / 2,
                random.nextDouble() * 3 * size - size,
                PIXELS);
    }

    /**
     * Whether a cluster's own or parent error projects to within rounding of the threshold.
     *
     * @param dag The DAG.
     * @param cluster The cluster.
     * @param view The view.
     * @param threshold The threshold.
     * @return True if rounding could tip it.
     */
    private static boolean nearThreshold(
            LodDag dag, int cluster, LodCut.View view, double threshold) {
        final double[] eye = {
            view.x() - dag.getOrigin()[0],
            view.y() - dag.getOrigin()[1],
            view.z() - dag.getOrigin()[2]
        };
        final double own =
                LodCut.projected(
                        dag.clusters().get(cluster).error(),
                        dag.clusters().get(cluster).bound(),
                        eye,
                        view.pixelsPerUnit());
        final double parent =
                LodCut.projected(
                        dag.parentError(cluster),
                        dag.parentBound(cluster),
                        eye,
                        view.pixelsPerUnit());
        return Math.abs(own - threshold) <= ROUNDING * threshold
                || Math.abs(parent - threshold) <= ROUNDING * threshold;
    }

    /**
     * With a section hidden and its cell and those above it stale, cuts have no cracks or overlaps
     * but the hole where the section was, which the section fills by drawing itself.
     */
    @Test
    void testOverrideLeavesOnlyTheSectionsHole() {
        final LodStore store = new LodStore(new double[3], SECTION, 1, 4, OPTIONS);
        for (LodMesh mesh : DagTest.terrain(2)) {
            final double[] origin = mesh.getOrigin();
            store.put(
                    new LodStore.SectionPos(
                            Math.floorDiv((int) origin[0], SECTION),
                            Math.floorDiv((int) origin[1], SECTION),
                            Math.floorDiv((int) origin[2], SECTION)),
                    mesh);
        }
        store.rebuild();
        final LodDag dag = store.snapshot();
        // Slots as the renderer hands them out, and every entry in snapshot order
        final java.util.Map<LodStore.CellKey, Integer> cellSlots = new java.util.HashMap<>();
        for (LodStore.CellKey key : store.cellKeys()) {
            cellSlots.put(key, cellSlots.size());
        }
        final java.util.Map<LodStore.SectionPos, Integer> sectionSlots = new java.util.HashMap<>();
        final ByteBuffer entries =
                ByteBuffer.allocate(dag.clusters().size() * LodCullMath.CLUSTER_ENTRY_SIZE)
                        .order(ByteOrder.nativeOrder());
        LodStore.SectionPos hiddenSection = null;
        int at = 0;
        for (LodStore.CellKey key : store.cellKeys()) {
            for (LodStore.PieceCluster piece : store.piece(key)) {
                int section = LodCullMath.NONE;
                if (piece.section() != null) {
                    section =
                            sectionSlots.computeIfAbsent(piece.section(), k -> sectionSlots.size());
                    if (hiddenSection == null) {
                        hiddenSection = piece.section();
                    }
                }
                entries.position(at * LodCullMath.CLUSTER_ENTRY_SIZE);
                LodCullMath.putCluster(
                        entries,
                        piece.cluster(),
                        piece.parentError(),
                        piece.parentBound(),
                        0,
                        0,
                        new LodCullMath.Place(
                                0,
                                cellSlots.get(key),
                                piece.parentCell() == null
                                        ? LodCullMath.NONE
                                        : cellSlots.get(piece.parentCell()),
                                section));
                ++at;
            }
        }
        final int[] flags = new int[cellSlots.size()];
        for (LodStore.CellKey key = store.cellOf(hiddenSection);
                cellSlots.containsKey(key);
                key = key.parent()) {
            flags[cellSlots.get(key)] = LodCullMath.FLAG_STALE;
        }
        final int[] hidden = new int[sectionSlots.size() / LodCullMath.BITS_PER_WORD + 1];
        final int slot = sectionSlots.get(hiddenSection);
        hidden[slot / LodCullMath.BITS_PER_WORD] |= 1 << (slot % LodCullMath.BITS_PER_WORD);

        final float[] min = {
            hiddenSection.x() * SECTION, hiddenSection.y() * SECTION, hiddenSection.z() * SECTION
        };
        final CrackCheck check = new CrackCheck(dag);
        final Random random = new Random(3);
        int holeEdges = 0;
        for (int v = 0; v < VIEWS; ++v) {
            final LodCut.View view = randomView(random, 2 * SECTION);
            final double threshold = 0.25 + random.nextDouble() * 16;
            final boolean[] drawn =
                    LodCullMath.select(
                            entries, at, dag.getOrigin(), view, threshold, flags, hidden);
            final CrackCheck.Result result = check.check(drawn);
            assertTrue(result.overdrawnEdges().isEmpty(), "View " + v + " draws an edge twice");
            for (Grouping.Edge edge : result.openEdges()) {
                assertTrue(
                        onBox(edge.a(), min) && onBox(edge.b(), min),
                        "View " + v + " has a crack away from the hidden section: " + edge);
                ++holeEdges;
            }
        }
        assertTrue(holeEdges > 0, "The hidden section leaves a hole");
    }

    /**
     * Whether a corner is in or on a section's box.
     *
     * @param corner The corner.
     * @param min The box's minimum corner.
     * @return True if it is.
     */
    private static boolean onBox(Grouping.Corner corner, float[] min) {
        final float[] at = {
            Float.intBitsToFloat(corner.x()),
            Float.intBitsToFloat(corner.y()),
            Float.intBitsToFloat(corner.z())
        };
        for (int axis = 0; axis < at.length; ++axis) {
            if (at[axis] < min[axis] || at[axis] > min[axis] + SECTION) {
                return false;
            }
        }
        return true;
    }
}
