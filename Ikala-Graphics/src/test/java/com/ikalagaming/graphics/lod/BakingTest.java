package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Tests for building with far groups baked into texture tiles. */
class BakingTest {

    /** The tile's side, in texels. */
    private static final int TILE = 64;

    /**
     * Far groups of a textured sphere bake: each cluster gets a power of two tile, the smallest
     * whose texels are within its error, and is laid out in it inside the gaps and without
     * overlapping, their normals still point out from the sphere, and their tangents are at right
     * angles to them.
     */
    @Test
    void testFarGroupsBakeIntoTiles() {
        final LodDag dag =
                DagBuilder.build(
                        List.of(AttributesTest.sphere()),
                        AttributesTest.OPTIONS.withBakeTile(TILE));
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
            assertTrue(
                    Integer.bitCount(tile) == 1
                            && tile >= DagBuilder.MIN_BAKE_TILE
                            && tile <= 2 * TILE,
                    "Tile " + tile);
            final BakeLayout layout = BakeLayout.of(cluster.corners(), tile);
            assertTrue(
                    tile == 2 * TILE || layout.texelSize() <= cluster.error(),
                    "Texel " + layout.texelSize() + " for error " + cluster.error());
            if (tile > DagBuilder.MIN_BAKE_TILE && tile <= TILE) {
                // The next tile down wasn't fine enough
                assertTrue(
                        BakeLayout.of(cluster.corners(), tile / 2).texelSize() > cluster.error());
            }
            final double margin = (BakeLayout.GUTTER - 1e-3) / tile;
            final int[] attributes = cluster.attributes();
            final float[] corners = cluster.corners();
            final int triangles = cluster.triangleCount();
            final double[] uv = new double[triangles * 6];
            for (int t = 0; t < triangles; ++t) {
                for (int c = 0; c < 3; ++c) {
                    final int at = t * Attributes.PER_TRIANGLE + c * Attributes.VALUES;
                    final float[] place = Attributes.textureCoordinates(attributes, at);
                    // Half floats near 1 are coarse, so allow a little
                    assertTrue(place[0] >= margin - 1e-3 && place[0] <= 1 - margin + 1e-3);
                    assertTrue(place[1] >= margin - 1e-3 && place[1] <= 1 - margin + 1e-3);
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
     * Every texel of a baked tile stands for a point on one of its group's children, within the
     * group's error of the point on the baked triangle it lies on, and where the child still has
     * the sphere's own texture coordinates they are the sphere's mapping at that point.
     */
    @Test
    void testTexelsMapToTheirChildren() {
        final LodDag dag =
                DagBuilder.build(
                        List.of(AttributesTest.sphere()),
                        AttributesTest.OPTIONS.withBakeTile(TILE));
        int mapped = 0;
        int checkedUv = 0;
        int texels = 0;
        for (LodDag.Group group : dag.groups()) {
            for (int i = 0; i < group.bakeTiles().length; ++i) {
                final LodDag.Cluster cluster = dag.clusters().get(group.outputs()[i]);
                final BakeTexels bake = dag.bakeOf(cluster.id());
                assertTrue(bake != null && bake.tile() == group.bakeTiles()[i]);
                texels += bake.tile() * bake.tile();
                for (int texel = 0; texel < bake.tile() * bake.tile(); ++texel) {
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
