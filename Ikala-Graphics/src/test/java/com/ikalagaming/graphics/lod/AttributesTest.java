package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for keeping textured triangles' attributes through level of detail. */
class AttributesTest {

    /** Rings and segments of the test sphere. */
    private static final int RINGS = 24;

    /** Segments around the test sphere. */
    private static final int SEGMENTS = 48;

    /** The sphere's radius. */
    private static final double RADIUS = 8;

    /** The material of the sphere. */
    private static final int MATERIAL = 3;

    /** Options for a test build: two threads, the open border simplified, no visibility. */
    private static final DagBuilder.Options OPTIONS =
            new DagBuilder.Options(0.5, false, 2, false, 0, false);

    /**
     * A point on the sphere.
     *
     * @param ring Its ring, 0 at the top.
     * @param segment Its segment, 0 to SEGMENTS, the last one the seam again.
     * @return Its position, which is also its normal times the radius.
     */
    private static double[] point(int ring, int segment) {
        final double theta = Math.PI * ring / RINGS;
        final double phi = 2 * Math.PI * segment / SEGMENTS;
        return new double[] {
            RADIUS * Math.sin(theta) * Math.cos(phi),
            RADIUS * Math.cos(theta),
            RADIUS * Math.sin(theta) * Math.sin(phi)
        };
    }

    /**
     * A corner's attributes on the sphere: the outward normal, the tangent along the segments, and
     * texture coordinates from the segment and ring.
     *
     * @param ring Its ring.
     * @param segment Its segment.
     * @param out Where they go.
     * @param at Where in it.
     */
    private static void attributes(int ring, int segment, int[] out, int at) {
        final double[] p = point(ring, segment);
        final double phi = 2 * Math.PI * segment / SEGMENTS;
        out[at] =
                Attributes.encodeDirection(new float[] {(float) p[0], (float) p[1], (float) p[2]});
        out[at + 1] =
                Attributes.encodeDirection(
                        new float[] {(float) -Math.sin(phi), 0, (float) Math.cos(phi)});
        final float u = (float) segment / SEGMENTS;
        final float v = (float) ring / RINGS;
        out[at + 2] = (Float.floatToFloat16(u) & 0xFFFF) | (Float.floatToFloat16(v) << Short.SIZE);
    }

    /**
     * A UV sphere, textured, with a seam where u wraps from 1 to 0: the seam's vertices are in the
     * same places on both sides but carry different texture coordinates.
     *
     * @return The mesh.
     */
    private static LodMesh sphere() {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int[][] vertex = new int[RINGS + 1][SEGMENTS + 1];
        for (int ring = 0; ring <= RINGS; ++ring) {
            for (int segment = 0; segment <= SEGMENTS; ++segment) {
                final double[] p = point(ring, segment);
                vertex[ring][segment] = builder.vertex((float) p[0], (float) p[1], (float) p[2]);
            }
        }
        final int[] corners = new int[Attributes.PER_TRIANGLE];
        for (int ring = 0; ring < RINGS; ++ring) {
            for (int segment = 0; segment < SEGMENTS; ++segment) {
                final int[][] quad = {
                    {ring, segment},
                    {ring + 1, segment},
                    {ring + 1, segment + 1},
                    {ring, segment + 1}
                };
                for (int[] triangle : new int[][] {{0, 1, 2}, {0, 2, 3}}) {
                    for (int c = 0; c < LodMesh.CORNERS; ++c) {
                        final int[] at = quad[triangle[c]];
                        attributes(at[0], at[1], corners, c * Attributes.VALUES);
                    }
                    builder.triangle(
                            vertex[quad[triangle[0]][0]][quad[triangle[0]][1]],
                            vertex[quad[triangle[1]][0]][quad[triangle[1]][1]],
                            vertex[quad[triangle[2]][0]][quad[triangle[2]][1]],
                            MATERIAL,
                            1,
                            corners);
                }
            }
        }
        return builder.build();
    }

    /**
     * A packed texture coordinate.
     *
     * @param attributes Corners' attributes.
     * @param at Where the corner starts.
     * @param which 0 for u, 1 for v.
     * @return The coordinate.
     */
    private static float uv(int[] attributes, int at, int which) {
        final int packed = attributes[at + 2];
        return Float.float16ToFloat((short) (which == 0 ? packed : packed >>> Short.SIZE));
    }

    /**
     * Simplified levels keep their textured triangles' attributes: every corner's normal points out
     * from the sphere's center, its texture coordinates are where the sphere's mapping puts it, and
     * no triangle spans the seam.
     */
    @Test
    void testAttributesSurviveSimplification() {
        final LodDag dag = DagBuilder.build(java.util.List.of(sphere()), OPTIONS);
        int checked = 0;
        int levels = 0;
        final float[] normal = new float[3];
        for (LodDag.Cluster cluster : dag.clusters()) {
            levels = Math.max(levels, cluster.level());
            final float[] corners = cluster.corners();
            final int[] attributes = cluster.attributes();
            assertEquals(
                    cluster.triangleCount() * Attributes.PER_TRIANGLE,
                    attributes.length,
                    "Cluster " + cluster.id() + " keeps attributes");
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                float minU = Float.MAX_VALUE;
                float maxU = -Float.MAX_VALUE;
                for (int c = 0; c < LodMesh.CORNERS; ++c) {
                    final int at = t * Attributes.PER_TRIANGLE + c * Attributes.VALUES;
                    final int p = t * Clusters.TRIANGLE_VALUES + c * LodMesh.CORNERS;
                    final double x = corners[p];
                    final double y = corners[p + 1];
                    final double z = corners[p + 2];
                    final double length = Math.sqrt(x * x + y * y + z * z);
                    Attributes.decodeDirection(attributes[at], normal);
                    final double alignment =
                            (normal[0] * x + normal[1] * y + normal[2] * z) / length;
                    assertTrue(
                            alignment > 0.9,
                            "Cluster " + cluster.id() + " normal off by " + alignment);
                    final float u = uv(attributes, at, 0);
                    final float v = uv(attributes, at, 1);
                    minU = Math.min(minU, u);
                    maxU = Math.max(maxU, u);
                    // Away from the poles, where u is undefined, v follows the height
                    final double expectedV = Math.acos(Math.clamp(y / length, -1, 1)) / Math.PI;
                    assertEquals(expectedV, v, 0.08, "Cluster " + cluster.id() + " v");
                    if (Math.abs(y / length) < 0.9) {
                        double expectedU = Math.atan2(z, x) / (2 * Math.PI);
                        if (expectedU < 0) {
                            expectedU += 1;
                        }
                        final double du = Math.abs(expectedU - u);
                        assertTrue(
                                Math.min(du, 1 - du) < 0.08,
                                "Cluster " + cluster.id() + " u " + u + " for " + expectedU);
                    }
                    ++checked;
                }
                assertTrue(
                        maxU - minU < 0.5,
                        "Cluster " + cluster.id() + " triangle " + t + " spans the seam");
            }
        }
        assertTrue(levels >= 2, "Simplified at least twice, got " + levels);
        assertTrue(checked > 0);
    }

    /** Untextured meshes keep no attributes at any level. */
    @Test
    void testProjectedKeepsNone() {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int a = builder.vertex(0, 0, 0);
        final int b = builder.vertex(1, 0, 0);
        final int c = builder.vertex(0, 0, 1);
        builder.triangle(a, b, c, MATERIAL);
        final LodDag dag = DagBuilder.build(java.util.List.of(builder.build()), OPTIONS);
        for (LodDag.Cluster cluster : dag.clusters()) {
            assertEquals(0, cluster.attributes().length);
        }
    }
}
