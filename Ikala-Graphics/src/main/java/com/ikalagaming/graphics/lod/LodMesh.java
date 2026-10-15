package com.ikalagaming.graphics.lod;

import com.ikalagaming.graphics.bake.BakedVertex;
import com.ikalagaming.graphics.bake.SectionBaker;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.Getter;
import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * A triangle mesh as level of detail building sees it: positions welded so that vertices in the
 * same place are one vertex, a material per triangle, and a lock flag per vertex. Simplification
 * works on geometry only, and appearance is found again afterwards by mapping back to the input
 * (see {@link Correspondence}). Textured triangles also keep each corner's normal, tangent and
 * texture coordinates (see {@link Attributes}), which the leaves are drawn with and simplified
 * levels take again from them; projected triangles have none.
 *
 * <p>Positions are floats relative to a {@code double} origin, so a mesh far from the world's
 * origin keeps its precision. Everything is in flat arrays.
 */
public final class LodMesh {

    /** Coordinates in a position. */
    static final int AXES = 3;

    /** Vertices in a triangle. */
    static final int CORNERS = 3;

    /** How many vertices and triangles a builder has room for at first. */
    private static final int INITIAL_CAPACITY = 64;

    /** Bytes in one baked position component. */
    private static final int SHORT_BYTES = Short.BYTES;

    /** The mask of an unsigned short. */
    private static final int UNSIGNED_SHORT_MASK = 0xFFFF;

    /**
     * Where positions are measured from, in the world.
     *
     * @return The origin, as x, y, z. Don't modify it.
     */
    @Getter private final double[] origin;

    /** The positions, x, y, z per vertex. */
    final float[] positions;

    /** How many vertices there are. */
    @Getter private final int vertexCount;

    /** The triangles, three vertex indices each. */
    final int[] triangles;

    /** How many triangles there are. */
    @Getter private final int triangleCount;

    /** The material of each triangle. */
    final int[] materials;

    /** How visible each triangle is from outside, 0 to 1 (see {@link Visibility}). */
    final float[] visibility;

    /** Whether each vertex must not move or be removed. */
    final boolean[] locked;

    /**
     * Each triangle's corners' attributes, {@link Attributes#PER_TRIANGLE} ints a triangle, zeros
     * for projected triangles; or null if every triangle is projected.
     */
    final int[] attributes;

    /**
     * How far it is from the surface it stands for, in world units: 0 for full detail, more for a
     * coarse stand-in. Its leaves carry it as their error, so what is built on them counts from it.
     * -- GETTER -- The leaf error.
     *
     * @return The error.
     */
    @Getter private final double leafError;

    /**
     * Create a mesh from arrays it takes ownership of.
     *
     * @param origin Where positions are measured from.
     * @param positions The positions, x, y, z per vertex.
     * @param vertexCount How many vertices there are.
     * @param triangles The triangles, three indices each.
     * @param triangleCount How many triangles there are.
     * @param materials The material of each triangle.
     * @param visibility How visible each triangle is, 0 to 1.
     * @param locked Whether each vertex is locked.
     * @param attributes Each triangle's corners' attributes, or null if every triangle is
     *     projected.
     * @param leafError How far it is from the surface it stands for.
     */
    LodMesh(
            double @NonNull [] origin,
            float @NonNull [] positions,
            int vertexCount,
            int @NonNull [] triangles,
            int triangleCount,
            int @NonNull [] materials,
            float @NonNull [] visibility,
            boolean @NonNull [] locked,
            int[] attributes,
            double leafError) {
        this.origin = origin;
        this.positions = positions;
        this.vertexCount = vertexCount;
        this.triangles = triangles;
        this.triangleCount = triangleCount;
        this.materials = materials;
        this.visibility = visibility;
        this.locked = locked;
        this.attributes = attributes;
        this.leafError = leafError;
    }

    /**
     * Whether any triangle is textured, with attributes kept.
     *
     * @return True if it has attributes.
     */
    public boolean hasAttributes() {
        return attributes != null;
    }

    /**
     * Whether a triangle is textured: its corners' attributes are kept.
     *
     * @param triangle The triangle.
     * @return True if it is.
     */
    public boolean isTextured(int triangle) {
        return attributes != null && Attributes.textured(attributes, triangle);
    }

    /**
     * Copy a triangle's corners' attributes.
     *
     * @param triangle The triangle.
     * @param out Where they go, {@link Attributes#PER_TRIANGLE} ints.
     * @param at Where in it.
     */
    public void attributes(int triangle, int @NonNull [] out, int at) {
        if (attributes == null) {
            Arrays.fill(out, at, at + Attributes.PER_TRIANGLE, 0);
            return;
        }
        System.arraycopy(
                attributes, triangle * Attributes.PER_TRIANGLE, out, at, Attributes.PER_TRIANGLE);
    }

    /**
     * A vertex's coordinate.
     *
     * @param vertex The vertex.
     * @param axis 0 for x, 1 for y, 2 for z.
     * @return The coordinate, relative to the origin.
     */
    public float position(int vertex, int axis) {
        return positions[vertex * AXES + axis];
    }

    /**
     * One corner of a triangle.
     *
     * @param triangle The triangle.
     * @param corner 0, 1 or 2.
     * @return The vertex.
     */
    public int corner(int triangle, int corner) {
        return triangles[triangle * CORNERS + corner];
    }

    /**
     * A triangle's material.
     *
     * @param triangle The triangle.
     * @return The material.
     */
    public int material(int triangle) {
        return materials[triangle];
    }

    /**
     * How visible a triangle is from outside.
     *
     * @param triangle The triangle.
     * @return From 0, hidden, to 1, open.
     */
    public float visibility(int triangle) {
        return visibility[triangle];
    }

    /**
     * Whether a vertex is locked.
     *
     * @param vertex The vertex.
     * @return True if it must not move or be removed.
     */
    public boolean isLocked(int vertex) {
        return locked[vertex];
    }

    /**
     * Lock or unlock a vertex.
     *
     * @param vertex The vertex.
     * @param lock Whether it must not move or be removed.
     */
    public void setLocked(int vertex, boolean lock) {
        locked[vertex] = lock;
    }

    /**
     * The total area of the triangles.
     *
     * @return The area.
     */
    public double area() {
        double area = 0;
        for (int t = 0; t < triangleCount; ++t) {
            area += Geometry.triangleArea(this, t);
        }
        return area;
    }

    /**
     * Start building a mesh.
     *
     * @param origin Where positions are measured from, in the world.
     * @return The builder.
     */
    public static Builder builder(double @NonNull [] origin) {
        return new Builder(origin);
    }

    /**
     * Read a mesh's positions and triangles, all one material.
     *
     * @param mesh The mesh.
     * @param material The material every triangle gets.
     * @return The welded mesh, with its origin at the world's.
     */
    public static LodMesh fromMeshData(@NonNull MeshData mesh, int material) {
        Builder builder = builder(new double[AXES]);
        final float[] data = mesh.getVertexData();
        final int stride = MeshData.VERTEX_SIZE_IN_FLOATS;
        int[] welded = new int[mesh.getVertexCount()];
        for (int v = 0; v < welded.length; ++v) {
            welded[v] =
                    builder.vertex(data[v * stride], data[v * stride + 1], data[v * stride + 2]);
        }
        final int[] indices = mesh.getIndices();
        for (int i = 0; i + 2 < indices.length; i += CORNERS) {
            builder.triangle(
                    welded[indices[i]], welded[indices[i + 1]], welded[indices[i + 2]], material);
        }
        return builder.build();
    }

    /**
     * Read a baked section mesh, every triangle projected. Each triangle's material is its first
     * vertex's material index.
     *
     * @param bucket The baked mesh.
     * @param sectionOrigin Where the section's positions are measured from, in the world.
     * @return The welded mesh, with its origin at the section's.
     */
    public static LodMesh fromBaked(
            SectionBaker.@NonNull Bucket bucket, double @NonNull [] sectionOrigin) {
        return fromBaked(bucket, sectionOrigin, material -> false);
    }

    /**
     * Read a baked section mesh. Each triangle's material is its first vertex's material index, and
     * a triangle of a textured material keeps its corners' attributes.
     *
     * @param bucket The baked mesh.
     * @param sectionOrigin Where the section's positions are measured from, in the world.
     * @param textured Which material indices are textured rather than projected.
     * @return The welded mesh, with its origin at the section's.
     */
    public static LodMesh fromBaked(
            SectionBaker.@NonNull Bucket bucket,
            double @NonNull [] sectionOrigin,
            @NonNull IntPredicate textured) {
        Builder builder = builder(sectionOrigin.clone());
        ByteBuffer vertices = bucket.vertices().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int[] welded = new int[bucket.vertexCount()];
        int[] vertexMaterials = new int[bucket.vertexCount()];
        final float step = 1.0f / BakedVertex.STEPS_PER_UNIT;
        for (int v = 0; v < welded.length; ++v) {
            final int base = v * BakedVertex.SIZE + BakedVertex.POSITION_OFFSET;
            welded[v] =
                    builder.vertex(
                            vertices.getShort(base) * step,
                            vertices.getShort(base + SHORT_BYTES) * step,
                            vertices.getShort(base + 2 * SHORT_BYTES) * step);
            vertexMaterials[v] =
                    vertices.getShort(v * BakedVertex.SIZE + BakedVertex.MATERIAL_OFFSET)
                            & UNSIGNED_SHORT_MASK;
        }
        final int[] indices = bucket.indices();
        final int[] corners = new int[Attributes.PER_TRIANGLE];
        for (int i = 0; i + 2 < indices.length; i += CORNERS) {
            final int material = vertexMaterials[indices[i]];
            if (textured.test(material)) {
                for (int corner = 0; corner < CORNERS; ++corner) {
                    Attributes.fromBaked(
                            vertices, indices[i + corner], corners, corner * Attributes.VALUES);
                }
                builder.triangle(
                        welded[indices[i]],
                        welded[indices[i + 1]],
                        welded[indices[i + 2]],
                        material,
                        1,
                        corners);
            } else {
                builder.triangle(
                        welded[indices[i]],
                        welded[indices[i + 1]],
                        welded[indices[i + 2]],
                        material);
            }
        }
        return builder.build();
    }

    /**
     * Builds a mesh, welding vertices that are in exactly the same place and dropping triangles
     * that use a vertex twice.
     */
    public static final class Builder {
        /** The origin. */
        private final double[] origin;

        /** Positions so far. */
        private float[] positions = new float[AXES * INITIAL_CAPACITY];

        /** Vertices so far. */
        private int vertexCount;

        /** Triangles so far. */
        private int[] triangles = new int[CORNERS * INITIAL_CAPACITY];

        /** Materials so far. */
        private int[] materials = new int[INITIAL_CAPACITY];

        /** Visibility so far. */
        private float[] visibility = new float[INITIAL_CAPACITY];

        /** Triangles so far. */
        private int triangleCount;

        /** Locks so far. */
        private boolean[] locked = new boolean[INITIAL_CAPACITY];

        /** Attributes so far, or null until a textured triangle is added. */
        private int[] attributes;

        /** How far the mesh is from the surface it stands for. */
        private double leafError;

        /**
         * Say how far the mesh is from the surface it stands for, for a coarse stand-in.
         *
         * @param error The error, in world units.
         * @return This builder.
         */
        public Builder leafError(double error) {
            leafError = error;
            return this;
        }

        /** Where each position is, for welding. */
        private final Map<Position, Integer> welding = new HashMap<>();

        /**
         * A position, compared by its exact bits.
         *
         * @param x The x bits.
         * @param y The y bits.
         * @param z The z bits.
         */
        private record Position(int x, int y, int z) {}

        /**
         * Start a mesh.
         *
         * @param origin Where positions are measured from.
         */
        private Builder(double @NonNull [] origin) {
            this.origin = origin;
        }

        /**
         * Find or add a vertex.
         *
         * @param x The x, relative to the origin.
         * @param y The y.
         * @param z The z.
         * @return Its index; the same for every call with the same position.
         */
        public int vertex(float x, float y, float z) {
            // +0 makes -0 and 0 the same position
            final Position key =
                    new Position(
                            Float.floatToIntBits(x + 0f),
                            Float.floatToIntBits(y + 0f),
                            Float.floatToIntBits(z + 0f));
            Integer existing = welding.get(key);
            if (existing != null) {
                return existing;
            }
            if ((vertexCount + 1) * AXES > positions.length) {
                positions = Arrays.copyOf(positions, positions.length * 2);
                locked = Arrays.copyOf(locked, locked.length * 2);
            }
            positions[vertexCount * AXES] = x;
            positions[vertexCount * AXES + 1] = y;
            positions[vertexCount * AXES + 2] = z;
            welding.put(key, vertexCount);
            return vertexCount++;
        }

        /**
         * Find a vertex without adding one.
         *
         * @param x The x, relative to the origin.
         * @param y The y.
         * @param z The z.
         * @return Its index, or -1 if there is none there.
         */
        public int find(float x, float y, float z) {
            final Integer existing =
                    welding.get(
                            new Position(
                                    Float.floatToIntBits(x + 0f),
                                    Float.floatToIntBits(y + 0f),
                                    Float.floatToIntBits(z + 0f)));
            return existing == null ? -1 : existing;
        }

        /**
         * Lock a vertex.
         *
         * @param vertex The vertex.
         */
        public void lock(int vertex) {
            locked[vertex] = true;
        }

        /**
         * Add a triangle, unless it uses a vertex twice.
         *
         * @param a The first corner.
         * @param b The second corner.
         * @param c The third corner.
         * @param material Its material.
         * @return Whether it was added.
         */
        public boolean triangle(int a, int b, int c, int material) {
            return triangle(a, b, c, material, 1);
        }

        /**
         * Add a triangle, unless it uses a vertex twice.
         *
         * @param a The first corner.
         * @param b The second corner.
         * @param c The third corner.
         * @param material Its material.
         * @param seen How visible it is from outside, 0 to 1.
         * @return Whether it was added.
         */
        public boolean triangle(int a, int b, int c, int material, float seen) {
            return triangle(a, b, c, material, seen, null);
        }

        /**
         * Add a triangle, unless it uses a vertex twice.
         *
         * @param a The first corner.
         * @param b The second corner.
         * @param c The third corner.
         * @param material Its material.
         * @param seen How visible it is from outside, 0 to 1.
         * @param corners Its corners' attributes, {@link Attributes#PER_TRIANGLE} ints, for a
         *     textured triangle; or null for a projected one.
         * @return Whether it was added.
         */
        public boolean triangle(int a, int b, int c, int material, float seen, int[] corners) {
            if (a == b || b == c || a == c) {
                return false;
            }
            if (corners != null && attributes == null) {
                attributes = new int[triangles.length / CORNERS * Attributes.PER_TRIANGLE];
            }
            if ((triangleCount + 1) * CORNERS > triangles.length) {
                triangles = Arrays.copyOf(triangles, triangles.length * 2);
                materials = Arrays.copyOf(materials, materials.length * 2);
                visibility = Arrays.copyOf(visibility, visibility.length * 2);
                if (attributes != null) {
                    attributes = Arrays.copyOf(attributes, attributes.length * 2);
                }
            }
            if (corners != null) {
                System.arraycopy(
                        corners,
                        0,
                        attributes,
                        triangleCount * Attributes.PER_TRIANGLE,
                        Attributes.PER_TRIANGLE);
            }
            triangles[triangleCount * CORNERS] = a;
            triangles[triangleCount * CORNERS + 1] = b;
            triangles[triangleCount * CORNERS + 2] = c;
            materials[triangleCount] = material;
            visibility[triangleCount] = seen;
            ++triangleCount;
            return true;
        }

        /**
         * Finish the mesh.
         *
         * @return The mesh.
         */
        public LodMesh build() {
            return new LodMesh(
                    origin,
                    Arrays.copyOf(positions, vertexCount * AXES),
                    vertexCount,
                    Arrays.copyOf(triangles, triangleCount * CORNERS),
                    triangleCount,
                    Arrays.copyOf(materials, triangleCount),
                    Arrays.copyOf(visibility, triangleCount),
                    Arrays.copyOf(locked, vertexCount),
                    attributes == null
                            ? null
                            : Arrays.copyOf(attributes, triangleCount * Attributes.PER_TRIANGLE),
                    leafError);
        }
    }

    /**
     * Roughly how many bytes its arrays hold, for memory reports.
     *
     * @return The bytes.
     */
    public long heldBytes() {
        return ((long) positions.length
                                + triangles.length
                                + materials.length
                                + visibility.length
                                + (attributes == null ? 0 : attributes.length))
                        * Integer.BYTES
                + locked.length;
    }
}
