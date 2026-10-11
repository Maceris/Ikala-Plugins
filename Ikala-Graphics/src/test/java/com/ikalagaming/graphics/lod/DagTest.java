package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.benchmark.BlockMeshes;
import com.ikalagaming.graphics.benchmark.SyntheticSections;
import com.ikalagaming.graphics.benchmark.SyntheticTerrain;

import org.junit.jupiter.api.Test;

import java.util.*;

/** Tests for the DAG builder, grouping, cut selection and the crack check. */
class DagTest {

    /** The seed for the synthetic terrain. */
    private static final long SEED = 12_345;

    /** The block sources, built without a GPU. */
    private static final BakeSource[] SOURCES = BlockMeshes.sources(BlockMeshes.create());

    /** Screen pixels per unit at distance 1: 1080 pixels high, 60 degrees. */
    private static final double PIXELS = 1080 / (2 * Math.tan(Math.toRadians(30)));

    /** Section size. */
    private static final int SECTION = SyntheticTerrain.SECTION;

    /**
     * Bake some columns of synthetic terrain into one opaque mesh per section.
     *
     * @param columns Columns along x and z.
     * @return The meshes, each with its section's origin.
     */
    static List<LodMesh> terrain(int columns) {
        final int[] materials = new int[SyntheticTerrain.BLOCK_TYPES];
        for (int block = 0; block < materials.length; ++block) {
            materials[block] = block;
        }
        return SyntheticSections.bake(
                SyntheticTerrain.Recipe.TERRAIN, SEED, columns, SOURCES, materials);
    }

    /**
     * A bumpy height field cut into sections, each with its own origin.
     *
     * @param columns Sections along x and z.
     * @return The meshes.
     */
    static List<LodMesh> heightField(int columns) {
        List<LodMesh> meshes = new ArrayList<>();
        for (int sx = 0; sx < columns; ++sx) {
            for (int sz = 0; sz < columns; ++sz) {
                LodMesh.Builder builder =
                        LodMesh.builder(new double[] {sx * SECTION, 0, sz * SECTION});
                int[][] index = new int[SECTION + 1][SECTION + 1];
                for (int x = 0; x <= SECTION; ++x) {
                    for (int z = 0; z <= SECTION; ++z) {
                        final double wx = sx * SECTION + x;
                        final double wz = sz * SECTION + z;
                        index[x][z] =
                                builder.vertex(
                                        x,
                                        (float) (3 * Math.sin(wx * 0.31) * Math.cos(wz * 0.23)),
                                        z);
                    }
                }
                for (int x = 0; x < SECTION; ++x) {
                    for (int z = 0; z < SECTION; ++z) {
                        builder.triangle(index[x][z], index[x][z + 1], index[x + 1][z], 0);
                        builder.triangle(index[x + 1][z], index[x][z + 1], index[x + 1][z + 1], 0);
                    }
                }
                meshes.add(builder.build());
            }
        }
        return meshes;
    }

    /** Options for a test build, on two threads. */
    private static final DagBuilder.Options OPTIONS =
            new DagBuilder.Options(0.5, false, 2, false, 0);

    /** The same, with the input's open border locked at every level. */
    private static final DagBuilder.Options LOCKED_RIM =
            new DagBuilder.Options(0.5, true, 2, false, 0);

    /** A height field builds up to a root, roughly halving each level. */
    @Test
    void testBuildsToTheRoot() {
        final LodDag dag = DagBuilder.build(heightField(4), OPTIONS);
        assertTrue(dag.getLevels() >= 4, dag.getLevels() + " levels");
        final List<LodStats.Level> levels = LodStats.levels(dag);
        assertTrue(levels.get(0).halving() < 0.7, "Level 0 kept " + levels.get(0).halving());
    }

    /** A group's error is at least its children's, and its sphere holds theirs. */
    @Test
    void testErrorsAndSpheresAreSaturated() {
        final LodDag dag = DagBuilder.build(terrain(2), OPTIONS);
        for (LodDag.Group group : dag.groups()) {
            for (int child : group.children()) {
                final LodDag.Cluster c = dag.clusters().get(child);
                assertTrue(group.error() >= c.error(), "Group " + group.id());
                final double[] g = group.bound();
                final double[] s = c.bound();
                final double d =
                        Math.sqrt(
                                (g[0] - s[0]) * (g[0] - s[0])
                                        + (g[1] - s[1]) * (g[1] - s[1])
                                        + (g[2] - s[2]) * (g[2] - s[2]));
                assertTrue(d + s[3] <= g[3] + 1e-6, "Group " + group.id() + " sphere");
            }
        }
    }

    /**
     * Each group's stored error covers how far it really is from the input. Distances are sampled,
     * and missing input is charged to the group nearest it, so a few groups may come out slightly
     * over; none may be far over.
     */
    @Test
    void testErrorsBoundTheDistance() {
        final LodDag dag =
                DagBuilder.build(terrain(2), new DagBuilder.Options(0.5, false, 2, true, 12));
        final Fidelity fidelity = Fidelity.measure(dag);
        int under = 0;
        for (LodDag.Group group : dag.groups()) {
            final double distance = fidelity.groupDistance(group.id());
            assertTrue(
                    distance <= 1.5 * group.error() + 1e-4,
                    "Group " + group.id() + ": " + distance + " against " + group.error());
            if (distance > group.error() + 1e-4) {
                ++under;
            }
        }
        assertTrue(under * 10 <= dag.groups().size(), under + " groups under-bounded");
    }

    /** No cut of baked block terrain has a crack, with the rim simplified. */
    @Test
    void testRandomCutsHaveNoCracks() {
        assertCrackFree(DagBuilder.build(terrain(2), OPTIONS), 2);
    }

    /**
     * No cut of a larger terrain has a crack, with visibility weights. Slits in the input whose
     * sides fall in two groups only show up here: a group closing its side, or collapsing the tip
     * of an ear against a shared border, leaves a crack next to the other group's coarser level.
     */
    @Test
    void testLargerTerrainHasNoCracks() {
        assertCrackFree(
                DagBuilder.build(terrain(4), new DagBuilder.Options(0.5, false, 2, false, 12)), 4);
    }

    /** The check accepts cuts of a DAG with its rim locked too. */
    @Test
    void testLockedRimCutsHaveNoCracks() {
        assertCrackFree(DagBuilder.build(terrain(2), LOCKED_RIM), 2);
    }

    /**
     * With the rim unlocked, terrain simplifies past the floor a locked rim leaves at full
     * resolution.
     */
    @Test
    void testUnlockedRimSimplifiesFurther() {
        final List<LodMesh> sections = terrain(2);
        final LodDag locked = DagBuilder.build(sections, LOCKED_RIM);
        final LodDag unlocked = DagBuilder.build(sections, OPTIONS);
        final int lockedTop = locked.trianglesAt(locked.getLevels() - 1);
        final int unlockedTop = unlocked.trianglesAt(unlocked.getLevels() - 1);
        System.out.printf(
                "Top level: %d triangles with the rim locked, %d unlocked (%s; %s)%n",
                lockedTop, unlockedTop, locked.getStopReason(), unlocked.getStopReason());
        assertTrue(unlockedTop * 2 < lockedTop, unlockedTop + " against " + lockedTop);
    }

    /**
     * Check 200 random cuts of a DAG for cracks, from views around and above it.
     *
     * @param dag The DAG.
     * @param columns Columns of sections it covers along x and z.
     */
    private static void assertCrackFree(LodDag dag, int columns) {
        final double size = columns * SECTION;
        final CrackCheck check = new CrackCheck(dag);
        Random random = new Random(99);
        for (int cut = 0; cut < 200; ++cut) {
            final LodCut.View view =
                    new LodCut.View(
                            random.nextDouble() * 3 * size - size,
                            random.nextDouble() * 3 * size - size / 2,
                            random.nextDouble() * 3 * size - size,
                            PIXELS);
            final double threshold = 0.25 + random.nextDouble() * 16;
            final boolean[] drawn = LodCut.select(dag, view, threshold);
            final CrackCheck.Result result = check.check(drawn);
            assertTrue(
                    result.isClean(),
                    "Cut "
                            + cut
                            + ": "
                            + result.openEdges().size()
                            + " cracks, "
                            + result.overdrawnEdges().size()
                            + " edges drawn twice");
        }
    }

    /** Moving one shared corner of a drawn cluster is caught as a crack. */
    @Test
    void testBrokenBorderIsCaught() {
        final LodDag dag = DagBuilder.build(heightField(2), OPTIONS);
        // The leaves: a cut with many clusters sharing corners
        final boolean[] drawn = new boolean[dag.clusters().size()];
        for (LodDag.Cluster cluster : dag.clusters()) {
            drawn[cluster.id()] = cluster.level() == 0;
        }
        // Made before the damage, so it knows the DAG as built
        final CrackCheck check = new CrackCheck(dag);
        assertTrue(check.check(drawn).isClean());
        // Find a corner one drawn cluster shares with another, and nudge it in one of them
        Map<Grouping.Corner, Integer> owner = new HashMap<>();
        for (int i = 0; i < drawn.length; ++i) {
            if (!drawn[i]) {
                continue;
            }
            final float[] corners = dag.clusters().get(i).corners();
            for (int at = 0; at < corners.length; at += 3) {
                final Integer other = owner.putIfAbsent(Grouping.Corner.of(corners, at), i);
                if (other != null && other != i) {
                    final Grouping.Corner target = Grouping.Corner.of(corners, at);
                    for (int k = 0; k < corners.length; k += 3) {
                        if (Grouping.Corner.of(corners, k).equals(target)) {
                            corners[k + 1] += 0.01f;
                        }
                    }
                    assertFalse(check.check(drawn).isClean());
                    return;
                }
            }
        }
        throw new AssertionError("No shared corner found");
    }

    /**
     * Locked borders don't stay locked on the way up, so they can't collect dense cruft (Karis et
     * al. 2021). A border can stay locked for one more level, since the next level's group borders
     * follow cluster borders, some of which were group borders; but few stay locked two levels up.
     * The share kept one level up is logged.
     */
    @Test
    void testLockedCornersAreFreedAbove() {
        final LodDag dag = DagBuilder.build(heightField(8), OPTIONS);
        List<Set<Grouping.Corner>> lockedPerLevel = new ArrayList<>();
        for (int level = 0; level < dag.getLevels(); ++level) {
            Map<Grouping.Corner, Integer> owners = new HashMap<>();
            for (LodDag.Group group : dag.groups()) {
                if (group.level() != level) {
                    continue;
                }
                for (int child : group.children()) {
                    final float[] corners = dag.clusters().get(child).corners();
                    for (int at = 0; at < corners.length; at += 3) {
                        owners.merge(
                                Grouping.Corner.of(corners, at),
                                group.id(),
                                (a, b) -> a.equals(b) ? a : -1);
                    }
                }
            }
            Set<Grouping.Corner> locked = new HashSet<>();
            owners.forEach(
                    (corner, owner) -> {
                        if (owner < 0) {
                            locked.add(corner);
                        }
                    });
            lockedPerLevel.add(locked);
        }
        for (int level = 0; level + 2 < lockedPerLevel.size(); ++level) {
            final Set<Grouping.Corner> here = lockedPerLevel.get(level);
            if (here.size() < 20) {
                continue;
            }
            Set<Grouping.Corner> next = new HashSet<>(here);
            next.retainAll(lockedPerLevel.get(level + 1));
            Set<Grouping.Corner> both = new HashSet<>(next);
            both.retainAll(lockedPerLevel.get(level + 2));
            final double oneUp = (double) next.size() / here.size();
            final double twoUp = (double) both.size() / here.size();
            System.out.printf(
                    "Level %d: %d locked, %.1f%% still locked one level up, %.1f%% two%n",
                    level, here.size(), 100 * oneUp, 100 * twoUp);
            assertTrue(twoUp < 0.25, "Level " + level + " kept " + twoUp);
        }
    }

    /**
     * Build terrain and log the numbers: levels, group spread, locked share, build times, and edit
     * footprints for one block and for spheres of radius 8, 16 and 32.
     */
    @Test
    void testComparisonLogged() {
        final List<LodMesh> sections = terrain(4);
        final long start = System.nanoTime();
        final LodDag dag = DagBuilder.build(sections, DagBuilder.Options.defaults());
        final double millis = (System.nanoTime() - start) / 1e6;
        List<LodStats.Footprint> footprints = new ArrayList<>();
        footprints.add(
                LodStats.footprint(dag, "one block, section 0", LodStats.sectionLeaves(dag, 0)));
        footprints.add(
                LodStats.footprint(
                        dag,
                        "one block, middle",
                        LodStats.sectionLeaves(dag, sections.size() / 2)));
        for (int radius : new int[] {8, 16, 32}) {
            footprints.add(
                    LodStats.footprint(
                            dag,
                            "sphere radius " + radius,
                            LodStats.sphereLeaves(dag, new double[] {32, 24, 32}, radius, true)));
        }
        System.out.print(
                LodStats.report(
                        "Terrain on "
                                + sections.size()
                                + " sections, built in "
                                + Math.round(millis)
                                + " ms",
                        dag,
                        footprints));
    }
}
