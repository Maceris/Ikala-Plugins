package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.vulkan.LodBakes;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Tests for building with far groups baked into texture tiles. */
class BakingTest {

    /** The tile's side, in texels. */
    private static final int TILE = 64;

    /**
     * Options that bake with texels as wide as the error, so baking starts while the sphere is
     * still fine enough for these tests' bounds on normals and texture coordinates.
     */
    private static final DagBuilder.Options OPTIONS =
            AttributesTest.OPTIONS.withBakeTile(TILE).withBakeDensity(1);

    /**
     * Far groups of a textured sphere bake: each cluster gets the tile its layout at its error
     * needs, texels within the error unless it took a tile larger than the options' to fit, and is
     * laid out in it inside the gaps and without overlapping, their normals still point out from
     * the sphere, and their tangents are at right angles to them.
     */
    @Test
    void testFarGroupsBakeIntoTiles() {
        final LodDag dag = DagBuilder.build(List.of(AttributesTest.sphere()), OPTIONS);
        int baked = 0;
        final float[] normal = new float[3];
        final float[] tangent = new float[3];
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (cluster.producer() < 0
                    || dag.groups().get(cluster.producer()).bakedClusters() == 0) {
                continue;
            }
            ++baked;
            final LodDag.Group group = dag.groups().get(cluster.producer());
            int tile = -1;
            for (int i = 0; i < group.outputs().length; ++i) {
                if (group.outputs()[i] == cluster.id()) {
                    tile = group.bakeTiles()[i];
                }
            }
            final int width = LodDag.tileWidth(tile);
            final int height = LodDag.tileHeight(tile);
            assertTrue(
                    width > 0 && height > 0 && width <= 2 * TILE && height <= 2 * TILE,
                    width + " by " + height);
            final double texel = cluster.error();
            final BakeLayout layout = BakeLayout.at(cluster.corners(), texel, 2 * TILE);
            assertTrue(layout.width() == width && layout.height() == height);
            assertTrue(
                    Math.max(width, height) > TILE || layout.texelSize() <= texel * (1 + 1e-9),
                    "Texel " + layout.texelSize() + " for error " + cluster.error());
            final double marginU = (BakeLayout.GUTTER - 1e-3) / width;
            final double marginV = (BakeLayout.GUTTER - 1e-3) / height;
            final int[] attributes = cluster.attributes();
            final float[] corners = cluster.corners();
            final int triangles = cluster.triangleCount();
            final double[] uv = new double[triangles * 6];
            for (int t = 0; t < triangles; ++t) {
                for (int c = 0; c < 3; ++c) {
                    final int at = t * Attributes.PER_TRIANGLE + c * Attributes.VALUES;
                    final float[] place = Attributes.textureCoordinates(attributes, at);
                    // Half floats near 1 are coarse, so allow a little
                    assertTrue(place[0] >= marginU - 1e-3 && place[0] <= 1 - marginU + 1e-3);
                    assertTrue(place[1] >= marginV - 1e-3 && place[1] <= 1 - marginV + 1e-3);
                    uv[t * 6 + c * 2] = place[0];
                    uv[t * 6 + c * 2 + 1] = place[1];
                    Attributes.decodeDirection(attributes[at], normal);
                    Attributes.decodeDirection(Attributes.tangentBits(attributes[at + 1]), tangent);
                    final int p = t * 9 + c * 3;
                    final double length =
                            Math.sqrt(
                                    corners[p] * corners[p]
                                            + corners[p + 1] * corners[p + 1]
                                            + corners[p + 2] * corners[p + 2]);
                    final double out =
                            (normal[0] * corners[p]
                                            + normal[1] * corners[p + 1]
                                            + normal[2] * corners[p + 2])
                                    / length;
                    assertTrue(out > 0.8, "Normal off by " + out);
                    final double across =
                            normal[0] * tangent[0]
                                    + normal[1] * tangent[1]
                                    + normal[2] * tangent[2];
                    assertTrue(Math.abs(across) < 0.05, "Tangent off by " + across);
                }
            }
            for (int a = 0; a < triangles; ++a) {
                for (int b = a + 1; b < triangles; ++b) {
                    assertFalse(
                            BakeLayout.overlap(uv, a * 6, uv, b * 6),
                            "Cluster " + cluster.id() + " triangles " + a + " and " + b);
                }
            }
        }
        assertTrue(baked > 0, "Nothing baked");
    }

    /**
     * Two texels across the error lay each baked cluster out at half its error, in a tile up to
     * twice as wide, unless even that would be wider than the largest tile, and start baking no
     * sooner than one texel does.
     */
    @Test
    void testDensityHalvesTexels() {
        final LodDag dag =
                DagBuilder.build(List.of(AttributesTest.sphere()), OPTIONS.withBakeDensity(2));
        int baked = 0;
        for (LodDag.Group group : dag.groups()) {
            for (int i = 0; i < group.bakeTiles().length; ++i) {
                final LodDag.Cluster cluster = dag.clusters().get(group.outputs()[i]);
                final int width = LodDag.tileWidth(group.bakeTiles()[i]);
                final int height = LodDag.tileHeight(group.bakeTiles()[i]);
                final BakeLayout layout =
                        BakeLayout.at(cluster.corners(), cluster.error() / 2, 2 * TILE);
                assertTrue(layout.width() == width && layout.height() == height);
                assertTrue(
                        Math.max(width, height) > TILE
                                || layout.texelSize() <= cluster.error() / 2 * (1 + 1e-9),
                        "Texel " + layout.texelSize() + " for error " + cluster.error());
                ++baked;
            }
        }
        assertTrue(baked > 0, "Nothing baked");
        int firstDense = Integer.MAX_VALUE;
        for (LodDag.Group group : dag.groups()) {
            if (group.bakedClusters() > 0) {
                firstDense = Math.min(firstDense, group.level());
            }
        }
        final LodDag sparse = DagBuilder.build(List.of(AttributesTest.sphere()), OPTIONS);
        int firstSparse = Integer.MAX_VALUE;
        for (LodDag.Group group : sparse.groups()) {
            if (group.bakedClusters() > 0) {
                firstSparse = Math.min(firstSparse, group.level());
            }
        }
        assertTrue(firstDense >= firstSparse, firstDense + " before " + firstSparse);
    }

    /**
     * Every texel of a baked tile stands for a point on one of its group's children, within the
     * group's error of the point on the baked triangle it lies on, and where the child still has
     * the sphere's own texture coordinates they are the sphere's mapping at that point.
     */
    @Test
    void testTexelsMapToTheirChildren() {
        final LodDag dag = DagBuilder.build(List.of(AttributesTest.sphere()), OPTIONS);
        int mapped = 0;
        int checkedUv = 0;
        int texels = 0;
        for (LodDag.Group group : dag.groups()) {
            for (int i = 0; i < group.bakeTiles().length; ++i) {
                final LodDag.Cluster cluster = dag.clusters().get(group.outputs()[i]);
                final BakeTexels bake = dag.bakeOf(cluster.id());
                assertTrue(
                        bake != null
                                && bake.width() == LodDag.tileWidth(group.bakeTiles()[i])
                                && bake.height() == LodDag.tileHeight(group.bakeTiles()[i]));
                texels += bake.width() * bake.height();
                for (int texel = 0; texel < bake.width() * bake.height(); ++texel) {
                    final int child = bake.childCluster(texel);
                    if (child == BakeTexels.EMPTY) {
                        continue;
                    }
                    ++mapped;
                    boolean isChild = false;
                    for (int c : group.children()) {
                        isChild |= c == child;
                    }
                    assertTrue(isChild, "Texel " + texel + " maps outside its group");
                    final LodDag.Cluster source = dag.clusters().get(child);
                    final double[] there =
                            point(source.corners(), bake.childTriangle(texel), bake, texel, true);
                    final double[] here =
                            point(cluster.corners(), bake.ownTriangle(texel), bake, texel, false);
                    final double apart =
                            Math.sqrt(
                                    (there[0] - here[0]) * (there[0] - here[0])
                                            + (there[1] - here[1]) * (there[1] - here[1])
                                            + (there[2] - here[2]) * (there[2] - here[2]));
                    // Gap texels reach a little past their triangle
                    assertTrue(
                            apart <= 2 * cluster.error() + 1e-3,
                            "Texel " + texel + " is " + apart + " away");
                    final boolean childBaked =
                            source.producer() >= 0
                                    && dag.groups().get(source.producer()).bakedClusters() > 0;
                    final double length =
                            Math.sqrt(
                                    there[0] * there[0]
                                            + there[1] * there[1]
                                            + there[2] * there[2]);
                    if (!childBaked && Math.abs(there[1] / length) < 0.9) {
                        final int at = bake.childTriangle(texel) * Attributes.PER_TRIANGLE;
                        double u = 0;
                        for (int c = 0; c < 3; ++c) {
                            u +=
                                    bake.childWeight(texel, c)
                                            * Attributes.textureCoordinates(
                                                    source.attributes(),
                                                    at + c * Attributes.VALUES)[0];
                        }
                        double expected = Math.atan2(there[2], there[0]) / (2 * Math.PI);
                        if (expected < 0) {
                            expected += 1;
                        }
                        final double du = Math.abs(expected - u);
                        assertTrue(
                                Math.min(du, 1 - du) < 0.05,
                                "Texel " + texel + " u " + u + " for " + expected);
                        ++checkedUv;
                    }
                }
            }
        }
        assertTrue(mapped > texels / 4, mapped + " of " + texels + " texels mapped");
        assertTrue(checkedUv > 0);
    }

    /**
     * Planning a world's bakes takes a texel over a baked child through to what the child's own
     * tile samples there, so every tile samples the sphere's own material, at the sphere's mapping
     * of the texel's point.
     */
    @Test
    void testBakesOverBakedChildrenSampleTheSource() {
        final LodDag dag = DagBuilder.build(List.of(AttributesTest.sphere()), OPTIONS);
        final MaterialCache cache = new MaterialCache();
        final LodBakes.Plan plan = LodBakes.plan(dag, cache);
        assertTrue(plan != null);
        int checked = 0;
        for (LodBakes.Baked baked : plan.clusters().values()) {
            final LodDag.Cluster cluster = baked.cluster();
            final BakeTexels bake = dag.bakeOf(cluster.id());
            for (int texel = 0; texel < baked.width() * baked.height(); ++texel) {
                final int at = texel * LodBakes.RECORD_SIZE;
                final int slot = baked.records().getInt(at);
                if (slot == -1) {
                    continue;
                }
                assertTrue(
                        baked.sources().get(slot) == cache.getMaterial(0),
                        "Texel " + texel + " samples something else");
                final int child = bake.childCluster(texel);
                if (plan.of(child) == null) {
                    continue;
                }
                final double[] here =
                        point(cluster.corners(), bake.ownTriangle(texel), bake, texel, false);
                final double length =
                        Math.sqrt(here[0] * here[0] + here[1] * here[1] + here[2] * here[2]);
                if (Math.abs(here[1] / length) >= 0.9) {
                    continue;
                }
                double expected = Math.atan2(here[2], here[0]) / (2 * Math.PI);
                if (expected < 0) {
                    expected += 1;
                }
                final double u = baked.records().getFloat(at + Integer.BYTES);
                final double du = Math.abs(expected - u);
                assertTrue(
                        Math.min(du, 1 - du) < 0.05,
                        "Cluster "
                                + cluster.id()
                                + " texel "
                                + texel
                                + " u "
                                + u
                                + " for "
                                + expected);
                ++checked;
            }
        }
        assertTrue(checked > 0, "No texel over a baked child");
    }

    /** A baked cluster needs an emission page only when a material it samples gives off light. */
    @Test
    void testEmittingSourcesMakeEmissiveBakes() {
        final LodDag dag = DagBuilder.build(List.of(AttributesTest.sphere()), OPTIONS);
        final MaterialCache dark = new MaterialCache();
        final LodBakes.Plan darkPlan = LodBakes.plan(dag, dark);
        assertTrue(darkPlan != null);
        for (LodBakes.Baked baked : darkPlan.clusters().values()) {
            assertFalse(baked.emissive(), "Cluster " + baked.cluster().id());
        }
        // The sphere's material, index 3, glowing
        final MaterialCache glowing = new MaterialCache();
        Material material = null;
        while (glowing.getMaterialIndex(material) != 3) {
            material = new Material();
            glowing.addMaterial(material);
        }
        material.getEmissive().set(2, 1, 0);
        final LodBakes.Plan glowingPlan = LodBakes.plan(dag, glowing);
        assertTrue(glowingPlan != null);
        for (LodBakes.Baked baked : glowingPlan.clusters().values()) {
            assertTrue(baked.emissive(), "Cluster " + baked.cluster().id());
            assertTrue(
                    baked.bytes()
                            == (long) baked.width()
                                    * baked.height()
                                    * (LodBakes.BYTES_PER_TEXEL
                                            + LodBakes.EMISSION_BYTES_PER_TEXEL));
        }
    }

    /**
     * Small cubes floating just off the sphere, which simplification takes away, are laid onto the
     * baked tiles as splats: some sample the cubes' material, each covers some of its texel, and
     * splats are in order of texel.
     */
    @Test
    void testRemovedPartsAreSplatted() {
        final LodMesh.Builder cubes = LodMesh.builder(new double[3]);
        for (int i = 0; i < SPLAT_CUBES; ++i) {
            // Around the sphere's upper half, just off its surface
            final double theta = Math.PI * (0.15 + 0.3 * (i % 4) / 4.0);
            final double phi = 2 * Math.PI * i / SPLAT_CUBES;
            final double r = SPHERE_RADIUS + CUBE_LIFT;
            cube(
                    cubes,
                    (float) (r * Math.sin(theta) * Math.cos(phi)),
                    (float) (r * Math.cos(theta)),
                    (float) (r * Math.sin(theta) * Math.sin(phi)));
        }
        final LodDag dag =
                DagBuilder.build(List.of(AttributesTest.sphere(), cubes.build()), OPTIONS);
        final MaterialCache cache = new MaterialCache();
        Material material = null;
        while (cache.getMaterialIndex(material) != CUBE_MATERIAL) {
            material = new Material();
            cache.addMaterial(material);
        }
        final LodBakes.Plan plan = LodBakes.plan(dag, cache);
        assertTrue(plan != null);
        int cubeSplats = 0;
        for (LodBakes.Baked baked : plan.clusters().values()) {
            int last = -1;
            for (int s = 0; s < baked.splatCount(); ++s) {
                final int at = s * LodBakes.SPLAT_SIZE;
                final int texel = baked.splats().getInt(at);
                assertTrue(texel >= last, "Splats out of order");
                last = texel;
                assertTrue(baked.splats().getFloat(at + Integer.BYTES) > 0, "Empty splat");
                final int slot = baked.splats().getInt(at + 4 * Integer.BYTES);
                if (baked.sources().get(slot) == material) {
                    ++cubeSplats;
                }
            }
        }
        assertTrue(cubeSplats > 0, "No splat samples the cubes");
    }

    /** How many cubes float over the sphere. */
    private static final int SPLAT_CUBES = 24;

    /** The test sphere's radius. */
    private static final double SPHERE_RADIUS = 8;

    /** How far off the sphere the cubes float. */
    private static final double CUBE_LIFT = 0.05;

    /** The cubes' side. */
    private static final float CUBE_SIDE = 0.6f;

    /** The cubes' material. */
    private static final int CUBE_MATERIAL = 5;

    /**
     * Add a closed cube, its faces outward, centered on a point.
     *
     * @param builder The mesh.
     * @param x Its center's x.
     * @param y Its center's y.
     * @param z Its center's z.
     */
    private static void cube(LodMesh.Builder builder, float x, float y, float z) {
        final int[] v = new int[8];
        final float half = CUBE_SIDE / 2;
        for (int i = 0; i < 8; ++i) {
            v[i] =
                    builder.vertex(
                            x + ((i & 1) != 0 ? half : -half),
                            y + ((i & 2) != 0 ? half : -half),
                            z + ((i & 4) != 0 ? half : -half));
        }
        final int[][] faces = {
            {0, 2, 3, 1}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 4, 6, 2}, {1, 3, 7, 5}
        };
        for (int[] f : faces) {
            builder.triangle(v[f[0]], v[f[1]], v[f[2]], CUBE_MATERIAL);
            builder.triangle(v[f[0]], v[f[2]], v[f[3]], CUBE_MATERIAL);
        }
    }

    /**
     * The point a texel's weights give on a triangle.
     *
     * @param corners The triangles' corners.
     * @param triangle The triangle.
     * @param bake The mapping.
     * @param texel The texel.
     * @param child Whether to use its child weights, or its own.
     * @return The point.
     */
    private static double[] point(
            float[] corners, int triangle, BakeTexels bake, int texel, boolean child) {
        final double[] out = new double[3];
        for (int c = 0; c < 3; ++c) {
            final float w = child ? bake.childWeight(texel, c) : bake.ownWeight(texel, c);
            for (int axis = 0; axis < 3; ++axis) {
                out[axis] += w * corners[triangle * 9 + c * 3 + axis];
            }
        }
        return out;
    }
}
