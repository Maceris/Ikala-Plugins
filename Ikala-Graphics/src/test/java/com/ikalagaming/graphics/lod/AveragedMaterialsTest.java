package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.vulkan.ToneMapMath;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for far level of detail blending its materials' looks per vertex. */
class AveragedMaterialsTest {

    /** Options for a test build, on two threads, no visibility. */
    private static final DagBuilder.Options OPTIONS =
            new DagBuilder.Options(0.5, false, 2, false, 0, true);

    /** How far apart two values may be after a round trip through a byte. */
    private static final double BYTE_STEP = 1.0 / 255;

    /** Sections along each side of the test terrain. */
    private static final int COLUMNS = 4;

    /** Cells along each side of a section. */
    private static final int SECTION = 16;

    /** How high the test terrain's bumps reach. */
    private static final double HEIGHT = 8;

    /** How many materials the test terrain's cells are checkered over. */
    private static final int MATERIALS = 6;

    /** A projected texture's repeat, in world units. */
    private static final double PROJECTED_REPEAT = 1;

    /**
     * Averages that give every material its own color, its index spread over red and blue.
     *
     * @return The averages.
     */
    private static MaterialAverages spread() {
        return material ->
                new float[] {
                    (material % 4) / 4f,
                    0.5f,
                    (material % 3) / 3f,
                    0.25f + material % 2 * 0.5f,
                    0,
                    0.5f
                };
    }

    /**
     * A bumpy height field of four sections, its cells' materials checkered over six, so groups
     * meet many material borders.
     *
     * @return The sections.
     */
    private static List<LodMesh> checkered() {
        final List<LodMesh> meshes = new ArrayList<>();
        for (int sx = 0; sx < COLUMNS; ++sx) {
            for (int sz = 0; sz < COLUMNS; ++sz) {
                final LodMesh.Builder builder =
                        LodMesh.builder(new double[] {sx * SECTION, 0, sz * SECTION});
                final int[][] index = new int[SECTION + 1][SECTION + 1];
                for (int x = 0; x <= SECTION; ++x) {
                    for (int z = 0; z <= SECTION; ++z) {
                        final double wx = sx * SECTION + x;
                        final double wz = sz * SECTION + z;
                        index[x][z] =
                                builder.vertex(
                                        x,
                                        (float)
                                                (HEIGHT
                                                        * Math.sin(wx * 0.31)
                                                        * Math.cos(wz * 0.23)),
                                        z);
                    }
                }
                for (int x = 0; x < SECTION; ++x) {
                    for (int z = 0; z < SECTION; ++z) {
                        final int material = (sx * SECTION + x + sz * SECTION + z) % MATERIALS;
                        builder.triangle(index[x][z], index[x][z + 1], index[x + 1][z], material);
                        builder.triangle(
                                index[x + 1][z], index[x][z + 1], index[x + 1][z + 1], material);
                    }
                }
                meshes.add(builder.build());
            }
        }
        return meshes;
    }

    /** A named averaged corner keeps its normal, material, shade and spread. */
    @Test
    void testNamedRoundTrip() {
        final int[] out = new int[Attributes.VALUES];
        Attributes.namedAverage(new float[] {0, 0, 3}, 4711, 1.3f, 0.8, out, 0);
        assertTrue(Attributes.isAveraged(out, 0));
        assertTrue(Attributes.isNamedAverage(out, 0));
        assertFalse(Attributes.isShell(out, 0));
        assertEquals(4711, Attributes.namedMaterial(out, 0));
        assertEquals(1.3, Attributes.namedShade(out, 0), 2 * Attributes.MAX_SHADE * BYTE_STEP);
        assertEquals(0.8, Attributes.namedSpread(out, 0), BYTE_STEP);
        final float[] normal = new float[3];
        Attributes.decodeDirection(out[0], normal);
        assertEquals(1, normal[2], 1e-3);
    }

    /**
     * Built as a model's, far groups' corners name the material they mostly blend, one of the
     * input's, with a shade, and its colors come back close to what the corners would carry.
     */
    @Test
    void testNamedCornersNameTheirMaterials() {
        final List<LodMesh> terrain = checkered();
        final MaterialAverages averages = spread();
        final LodDag dag =
                DagBuilder.build(terrain, OPTIONS.withAverages(averages).withNamed(true));
        int named = 0;
        final java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (LodDag.Cluster cluster : dag.clusters()) {
            final int[] attributes = cluster.attributes();
            for (int at = 0; at + Attributes.VALUES <= attributes.length; at += Attributes.VALUES) {
                if (!Attributes.textured(attributes, at / Attributes.PER_TRIANGLE)
                        || !Attributes.isAveraged(attributes, at)) {
                    continue;
                }
                assertTrue(Attributes.isNamedAverage(attributes, at), "A colored corner");
                final int material = Attributes.namedMaterial(attributes, at);
                assertTrue(material >= 0 && material < MATERIALS, "Material " + material);
                final float shade = Attributes.namedShade(attributes, at);
                assertTrue(shade > 0 && shade <= Attributes.MAX_SHADE, "Shade " + shade);
                final float spread = Attributes.namedSpread(attributes, at);
                assertTrue(spread > 0 && spread <= 1, "Spread " + spread);
                seen.add(material);
                ++named;
            }
        }
        assertTrue(named > 0, "No named corners");
        assertTrue(seen.size() > 1, "Corners name only " + seen);
        // Built as a world's, none name materials
        final LodDag plain = DagBuilder.build(terrain, OPTIONS.withAverages(averages));
        for (LodDag.Cluster cluster : plain.clusters()) {
            final int[] attributes = cluster.attributes();
            for (int at = 0; at + Attributes.VALUES <= attributes.length; at += Attributes.VALUES) {
                assertFalse(Attributes.isNamedAverage(attributes, at));
            }
        }
    }

    /** An averaged corner keeps its normal, color, roughness, metallic and specular. */
    @Test
    void testRoundTrip() {
        final float[] look = {0.02f, 0.5f, 0.9f, 0.3f, 1, 0.45f};
        final int[] out = new int[Attributes.VALUES];
        Attributes.averaged(new float[] {0, 2, 0}, look, out, 0);
        assertTrue(Attributes.isAveraged(out, 0));
        final float[] back = new float[MaterialAverages.VALUES];
        Attributes.averageOf(out, 0, back);
        // The color as sRGB, as textures hold it, so precision is finest in the darks
        for (int c = 0; c < 3; ++c) {
            assertEquals(
                    ToneMapMath.encode(look[c]),
                    ToneMapMath.encode(back[c]),
                    BYTE_STEP,
                    "Channel " + c);
        }
        for (int k = 3; k < look.length; ++k) {
            assertEquals(look[k], back[k], BYTE_STEP, "Value " + k);
        }
        final float[] normal = new float[3];
        Attributes.decodeDirection(out[0], normal);
        assertEquals(1, normal[1], 1e-3);
    }

    /**
     * Without averages, nothing is averaged; with them, only groups whose children's error is past
     * a texture repeat are, never the leaves, and their colors lie within the input's.
     */
    @Test
    void testFarGroupsAverage() {
        final List<LodMesh> terrain = checkered();
        final LodDag plain = DagBuilder.build(terrain, OPTIONS);
        for (LodDag.Cluster cluster : plain.clusters()) {
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                assertFalse(averaged(cluster, t), "Cluster " + cluster.id() + " without averages");
            }
        }
        final LodDag dag = DagBuilder.build(terrain, OPTIONS.withAverages(spread()));
        int averagedClusters = 0;
        final float[] look = new float[MaterialAverages.VALUES];
        for (LodDag.Cluster cluster : dag.clusters()) {
            boolean any = false;
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                if (!averaged(cluster, t)) {
                    continue;
                }
                any = true;
                for (int c = 0; c < LodMesh.CORNERS; ++c) {
                    Attributes.averageOf(
                            cluster.attributes(),
                            t * Attributes.PER_TRIANGLE + c * Attributes.VALUES,
                            look);
                    assertTrue(look[0] <= 0.75 + BYTE_STEP, "Red " + look[0]);
                    assertTrue(look[2] <= 2 / 3.0 + BYTE_STEP, "Blue " + look[2]);
                    assertEquals(0.5, look[1], 2 * BYTE_STEP, "Green");
                }
            }
            if (any) {
                ++averagedClusters;
                assertTrue(cluster.level() >= 2, "Averaged at level " + cluster.level());
            }
        }
        assertTrue(averagedClusters > 0, "Something far out was averaged");
        // Groups whose children are averaged had children past a repeat
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (cluster.level() == 0) {
                continue;
            }
            boolean any = false;
            for (int t = 0; t < cluster.triangleCount() && !any; ++t) {
                any = averaged(cluster, t);
            }
            if (any) {
                assertTrue(
                        cluster.error() >= PROJECTED_REPEAT,
                        "Cluster " + cluster.id() + " error " + cluster.error());
            }
        }
    }

    /**
     * Whether a cluster's triangle is averaged.
     *
     * @param cluster The cluster.
     * @param t The triangle.
     * @return True if its first corner is.
     */
    private static boolean averaged(LodDag.Cluster cluster, int t) {
        return Attributes.textured(cluster.attributes(), t)
                && Attributes.isAveraged(cluster.attributes(), t * Attributes.PER_TRIANGLE);
    }

    /**
     * Cut out triangles are kept apart: no cluster mixes them with the rest, they are never
     * averaged, which would fill their holes, and the rest still are far out.
     */
    @Test
    void testCutOutKeptApart() {
        final int cut = MATERIALS - 1;
        final LodDag dag =
                DagBuilder.build(
                        checkered(), OPTIONS.withAverages(spread()).withCutOut(m -> m == cut));
        int restAveraged = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            boolean anyCut = false;
            boolean anyRest = false;
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                final boolean isCut = cluster.materials()[t] == cut;
                anyCut |= isCut;
                anyRest |= !isCut;
                if (isCut) {
                    assertFalse(averaged(cluster, t), "Cluster " + cluster.id() + " averaged");
                } else if (averaged(cluster, t)) {
                    ++restAveraged;
                }
            }
            assertFalse(anyCut && anyRest, "Cluster " + cluster.id() + " mixes cut out and not");
        }
        assertTrue(restAveraged > 0, "Nothing averaged");
    }
}
