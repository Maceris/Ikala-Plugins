package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gives a simplified mesh averaged corners ({@link Attributes#averaged}): each position a smooth
 * normal, from all the output faces around it, so the vertex there is shared and far surfaces shade
 * softly, and a blend of the color, roughness, metallic and specular of the input surface around
 * it. The blend weighs each input triangle within the footprint by its area, how visible it is, how
 * far it faces the way the corner does, and how near it is, so a corner takes the look of the
 * surface it stands for whatever materials that surface was made of, and what is hidden or faces
 * away counts for little. Its roughness widens by how far the input's normals there differed (see
 * {@link Roughening}), so a bumpy surface flattened far out keeps its highlights broad.
 */
final class AverageTransfer {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners in a triangle. */
    private static final int CORNERS = 3;

    /** Floats in a triangle's corners. */
    private static final int TRIANGLE_VALUES = CORNERS * AXES;

    /** The weight an input triangle seen not at all keeps, against 1 for an open one. */
    private static final double MIN_VISIBILITY_WEIGHT = 0.05;

    /** Bits of each cell coordinate in a cell's key. */
    private static final int KEY_BITS = 21;

    /** The low {@link #KEY_BITS} bits. */
    private static final int KEY_MASK = (1 << KEY_BITS) - 1;

    /** The smallest total weight a blend is trusted with. */
    private static final double MIN_WEIGHT = 1e-12;

    /** The input triangles' corners. */
    private final List<double[][]> corners = new ArrayList<>();

    /** Each input triangle's unit normal. */
    private final List<double[]> normals = new ArrayList<>();

    /** Each input triangle's weight before facing and distance: area times visibility. */
    private final List<Double> weights = new ArrayList<>();

    /** Each input triangle's values. */
    private final List<float[]> values = new ArrayList<>();

    /** Each input triangle's material, which named corners tally. */
    private final IntArrayList materials = new IntArrayList();

    /**
     * What materials look like averaged, if corners name the material they mostly blend and their
     * shade against it, else null for corners that carry the blend's color.
     */
    private final MaterialAverages named;

    /**
     * Start a transfer.
     *
     * @param named What materials look like averaged, for corners that name materials, or null for
     *     corners that carry colors.
     */
    AverageTransfer(MaterialAverages named) {
        this.named = named;
    }

    /** How far around a corner the input is blended from, set by {@link #transfer}. */
    private double radius;

    /** The input triangles reaching each grid cell, cells a radius wide. */
    private final Map<Long, IntArrayList> cells = new HashMap<>();

    /**
     * Add an input triangle.
     *
     * @param triangle Its corners, 9 floats from {@code at}.
     * @param at Where they start.
     * @param visibility How visible it is from outside, 0 to 1.
     * @param material The material its look is mostly of.
     * @param average Its {@link MaterialAverages#VALUES} values.
     */
    void add(
            float @NonNull [] triangle,
            int at,
            float visibility,
            int material,
            float @NonNull [] average) {
        final float[] copy = new float[TRIANGLE_VALUES];
        System.arraycopy(triangle, at, copy, 0, TRIANGLE_VALUES);
        final double[] normal = new double[AXES];
        final double area = normalOf(copy, normal);
        if (area <= 0) {
            return;
        }
        final double[][] points = new double[CORNERS][AXES];
        for (int c = 0; c < CORNERS; ++c) {
            for (int axis = 0; axis < AXES; ++axis) {
                points[c][axis] = copy[c * AXES + axis];
            }
        }
        corners.add(points);
        normals.add(normal);
        weights.add(area * (MIN_VISIBILITY_WEIGHT + (1 - MIN_VISIBILITY_WEIGHT) * visibility));
        values.add(average.clone());
        materials.addInt(material);
    }

    /**
     * Put every input triangle in the cells its bounds reach, grown by the radius, so a corner
     * looks only in its own cell.
     */
    private void index() {
        cells.clear();
        final int[] lo = new int[AXES];
        final int[] hi = new int[AXES];
        for (int index = 0; index < corners.size(); ++index) {
            final double[][] points = corners.get(index);
            for (int axis = 0; axis < AXES; ++axis) {
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                for (double[] point : points) {
                    min = Math.min(min, point[axis]);
                    max = Math.max(max, point[axis]);
                }
                lo[axis] = cellOf(min - radius);
                hi[axis] = cellOf(max + radius);
            }
            for (int x = lo[0]; x <= hi[0]; ++x) {
                for (int y = lo[1]; y <= hi[1]; ++y) {
                    for (int z = lo[2]; z <= hi[2]; ++z) {
                        final int at = index;
                        cells.computeIfAbsent(key(x, y, z), k -> new IntArrayList()).addInt(at);
                    }
                }
            }
        }
    }

    /**
     * Whether nothing was added.
     *
     * @return True if there is no input.
     */
    boolean isEmpty() {
        return corners.isEmpty();
    }

    /**
     * Work out every triangle's corners' attributes for a mesh.
     *
     * @param mesh The simplified mesh, in the same space as the input.
     * @param footprint How far around each corner the input is blended from, above 0.
     * @return {@link Attributes#PER_TRIANGLE} ints a triangle.
     */
    int[] transfer(@NonNull LodMesh mesh, double footprint) {
        radius = footprint;
        index();
        final int triangles = mesh.getTriangleCount();
        // One normal a position, whatever vertex holds it, so every corner there gets the same
        // attributes and the vertex is shared
        final Map<Position, double[]> sums = new HashMap<>();
        final double[][] faceNormals = new double[triangles][AXES];
        final float[] triangle = new float[TRIANGLE_VALUES];
        final Position[] positions = new Position[triangles * CORNERS];
        for (int t = 0; t < triangles; ++t) {
            for (int c = 0; c < CORNERS; ++c) {
                final int v = mesh.corner(t, c);
                for (int axis = 0; axis < AXES; ++axis) {
                    triangle[c * AXES + axis] = (float) mesh.position(v, axis);
                }
                positions[t * CORNERS + c] =
                        new Position(
                                triangle[c * AXES], triangle[c * AXES + 1], triangle[c * AXES + 2]);
            }
            final double area = normalOf(triangle, faceNormals[t]);
            for (int c = 0; c < CORNERS; ++c) {
                final double[] sum =
                        sums.computeIfAbsent(positions[t * CORNERS + c], k -> new double[AXES]);
                for (int axis = 0; axis < AXES; ++axis) {
                    sum[axis] += area * faceNormals[t][axis];
                }
            }
        }
        final Map<Position, int[]> done = new HashMap<>();
        final int[] out = new int[triangles * Attributes.PER_TRIANGLE];
        for (int t = 0; t < triangles; ++t) {
            for (int c = 0; c < CORNERS; ++c) {
                final Position position = positions[t * CORNERS + c];
                final int at = t * Attributes.PER_TRIANGLE + c * Attributes.VALUES;
                int[] corner = done.get(position);
                if (corner == null) {

                    final double[] normal = unit(sums.get(position));
                    // Faces around it that cancel out, as on something thin: its own face's
                    corner = cornerAt(position, normal != null ? normal : faceNormals[t]);
                    if (normal != null) {
                        done.put(position, corner);
                    }
                }
                System.arraycopy(corner, 0, out, at, Attributes.VALUES);
            }
        }
        return out;
    }

    /**
     * One position's averaged attributes.
     *
     * @param position The position.
     * @param normal Its unit normal.
     * @return Its {@link Attributes#VALUES} ints.
     */
    private int[] cornerAt(@NonNull Position position, double @NonNull [] normal) {
        final double[] at = {position.x(), position.y(), position.z()};
        final float[] blend = new float[MaterialAverages.VALUES];
        final Blend how = blendAt(at, normal, blend);
        final int[] corner = new int[Attributes.VALUES];
        final float[] facing = {(float) normal[0], (float) normal[1], (float) normal[2]};
        if (named == null) {
            Attributes.averaged(facing, blend, corner, 0);
            return corner;
        }
        // The material blended most, and how bright the blend is against its average
        final float[] average = named.average(how.material());
        final double own =
                average == null ? 0 : CoverageVoxelizer.luminance(average, MaterialAverages.RED);
        final double blended = CoverageVoxelizer.luminance(blend, MaterialAverages.RED);
        final float shade = own > MIN_WEIGHT ? (float) (blended / own) : 1;
        Attributes.namedAverage(facing, how.material(), shade, how.spread(), corner, 0);
        return corner;
    }

    /**
     * A direction made unit length.
     *
     * @param direction The direction.
     * @return It at unit length, or null if it has none.
     */
    private static double[] unit(double @NonNull [] direction) {
        final double length =
                Math.sqrt(
                        direction[0] * direction[0]
                                + direction[1] * direction[1]
                                + direction[2] * direction[2]);
        if (length <= MIN_WEIGHT) {
            return null;
        }
        return new double[] {direction[0] / length, direction[1] / length, direction[2] / length};
    }

    /**
     * A vertex position, by its coordinates' exact values.
     *
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     */
    private record Position(float x, float y, float z) {}

    /**
     * What else a blend found, beside its values.
     *
     * @param material The material weighing most in it.
     * @param spread How much the blended normals agree, the length of their weighted mean, 1 when
     *     they all point the same way.
     */
    private record Blend(int material, double spread) {}

    /**
     * Blend the input around a point.
     *
     * @param point The point.
     * @param normal Which way the corner faces, unit length.
     * @param out Receives the {@link MaterialAverages#VALUES} values.
     * @return The material weighing most, and the normals' spread.
     */
    private Blend blendAt(
            double @NonNull [] point, double @NonNull [] normal, float @NonNull [] out) {
        final IntArrayList near =
                cells.get(key(cellOf(point[0]), cellOf(point[1]), cellOf(point[2])));
        final double[] sum = new double[MaterialAverages.VALUES];
        final double[] normalSum = new double[AXES];
        double total = 0;
        final Map<Integer, Double> byMaterial = new HashMap<>();
        int nearest = -1;
        double nearestDistance = Double.MAX_VALUE;
        if (near != null) {
            final double[] closest = new double[AXES];
            for (int i = 0; i < near.size(); ++i) {
                final int s = near.getInt(i);
                final double[][] source = corners.get(s);
                Geometry.closestOnTriangle(point, source[0], source[1], source[2], closest);
                final double distance = Math.sqrt(Geometry.distanceSquared(point, closest));
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = s;
                }
                if (distance >= radius) {
                    continue;
                }
                final double[] n = normals.get(s);
                final double facing = n[0] * normal[0] + n[1] * normal[1] + n[2] * normal[2];
                if (facing <= 0) {
                    continue;
                }
                final double fall = 1 - (distance / radius) * (distance / radius);
                final double weight = weights.get(s) * facing * fall * fall;
                final float[] value = values.get(s);
                for (int k = 0; k < sum.length; ++k) {
                    sum[k] += weight * value[k];
                }
                for (int axis = 0; axis < AXES; ++axis) {
                    normalSum[axis] += weight * n[axis];
                }
                total += weight;
                byMaterial.merge(materials.getInt(s), weight, Double::sum);
            }
        }
        if (total > MIN_WEIGHT) {
            for (int k = 0; k < sum.length; ++k) {
                out[k] = (float) (sum[k] / total);
            }
            final double meanLength =
                    Math.sqrt(
                                    normalSum[0] * normalSum[0]
                                            + normalSum[1] * normalSum[1]
                                            + normalSum[2] * normalSum[2])
                            / total;
            out[MaterialAverages.ROUGHNESS] =
                    (float) Roughening.widen(out[MaterialAverages.ROUGHNESS], meanLength);
            int most = materials.getInt(0);
            double weighs = -1;
            for (Map.Entry<Integer, Double> e : byMaterial.entrySet()) {
                if (e.getValue() > weighs || e.getValue() == weighs && e.getKey() < most) {
                    weighs = e.getValue();
                    most = e.getKey();
                }
            }
            return new Blend(most, meanLength);
        }
        // Nothing near faces this way: take the nearest, or failing that, anything
        final int source = nearest >= 0 ? nearest : 0;
        System.arraycopy(values.get(source), 0, out, 0, out.length);
        return new Blend(materials.getInt(source), 1);
    }

    /**
     * A triangle's unit normal and area.
     *
     * @param triangle Its corners.
     * @param out Receives the unit normal, if it has area.
     * @return The area, 0 if it has none.
     */
    private static double normalOf(float @NonNull [] triangle, double @NonNull [] out) {
        final double ux = triangle[3] - triangle[0];
        final double uy = triangle[4] - triangle[1];
        final double uz = triangle[5] - triangle[2];
        final double vx = triangle[6] - triangle[0];
        final double vy = triangle[7] - triangle[1];
        final double vz = triangle[8] - triangle[2];
        final double nx = uy * vz - uz * vy;
        final double ny = uz * vx - ux * vz;
        final double nz = ux * vy - uy * vx;
        final double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length <= 0) {
            return 0;
        }
        out[0] = nx / length;
        out[1] = ny / length;
        out[2] = nz / length;
        return length / 2;
    }

    /**
     * The grid cell a coordinate is in.
     *
     * @param value The coordinate.
     * @return The cell's index along that axis.
     */
    private int cellOf(double value) {
        return (int) Math.floor(value / radius);
    }

    /**
     * A cell's key.
     *
     * @param x The cell's x.
     * @param y The cell's y.
     * @param z The cell's z.
     * @return The key.
     */
    private static long key(int x, int y, int z) {
        return ((long) (x & KEY_MASK) << (2 * KEY_BITS))
                | ((long) (y & KEY_MASK) << KEY_BITS)
                | (z & KEY_MASK);
    }
}
