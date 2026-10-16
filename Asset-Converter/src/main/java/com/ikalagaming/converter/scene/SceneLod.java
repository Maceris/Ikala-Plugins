package com.ikalagaming.converter.scene;

import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.lod.Attributes;
import com.ikalagaming.graphics.lod.LodMesh;

import lombok.NonNull;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Turns part of an imported scene into one mesh for level of detail to build from: every mesh under
 * a node, placed in the world, welded where they touch so clusters can span them. Only opaque
 * meshes go in, since level of detail draws without an alpha test or blending; cut out and
 * translucent ones keep drawing as they are. Every triangle keeps its corners' normals, tangents
 * and texture coordinates, so curved surfaces stay smooth. Untextured ones keep their texture
 * coordinates too: level of detail takes coordinates that don't change across a triangle for a
 * texture that never repeats, which would keep it from ever blending far groups' materials. They
 * can be left out, to see how much their seams hold simplifying back: the triangles are then drawn
 * flat, with their textures projected.
 */
public final class SceneLod {

    /** Floats per vertex in mesh data. */
    private static final int STRIDE = MeshData.VERTEX_SIZE_IN_FLOATS;

    /** Where the normal is in a vertex. */
    private static final int NORMAL = 3;

    /** Where the tangent is in a vertex. */
    private static final int TANGENT = 6;

    /** Where the bitangent is in a vertex. */
    private static final int BITANGENT = 9;

    /** Where the texture coordinates are in a vertex. */
    private static final int UV = 12;

    /** Corners in a triangle. */
    private static final int CORNERS = 3;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners of a box. */
    private static final int BOX_CORNERS = 8;

    /** The low 16 bits of an int. */
    private static final int LOW_16 = 0xFFFF;

    /** Bits in a short. */
    private static final int SHORT_BITS = 16;

    /** The tangent int's bit for a flipped bitangent, as {@link Attributes} keeps it. */
    private static final int FLIP_BIT = 1;

    /**
     * Which material a placed mesh is drawn with.
     *
     * @see ScenePlacement#materialOf(Node, int)
     */
    @FunctionalInterface
    public interface Materials {
        /**
         * The material a node's mesh is drawn with.
         *
         * @param node The node.
         * @param sceneMesh The scene's index of the mesh.
         * @return The material, or null if it isn't drawn.
         */
        Material of(@NonNull Node node, int sceneMesh);
    }

    /**
     * What went in.
     *
     * @param mesh The welded mesh, positions relative to its origin at the middle of what went in.
     * @param meshes How many placed meshes went in.
     * @param skipped How many placed meshes were left out for not being opaque.
     */
    public record Input(@NonNull LodMesh mesh, int meshes, int skipped) {}

    /** Not to be made. */
    private SceneLod() {}

    /**
     * Gather the opaque meshes under a node into one mesh, in the world.
     *
     * @param scene The scene.
     * @param top The node whose meshes, and its children's, go in.
     * @param materials Which material each placed mesh is drawn with.
     * @param materialIndex A material's index in the scene's material cache.
     * @return The mesh, or null if there are no opaque triangles under the node.
     */
    public static Input gather(
            @NonNull ImportedScene scene,
            @NonNull Node top,
            @NonNull Materials materials,
            @NonNull ToIntFunction<Material> materialIndex) {
        return gather(scene, top, materials, materialIndex, true);
    }

    /**
     * Gather the opaque meshes under a node into one mesh, in the world.
     *
     * @param scene The scene.
     * @param top The node whose meshes, and its children's, go in.
     * @param materials Which material each placed mesh is drawn with.
     * @param materialIndex A material's index in the scene's material cache.
     * @param keepAttributes Whether triangles keep their normals, tangents and texture coordinates.
     * @return The mesh, or null if there are no opaque triangles under the node.
     */
    public static Input gather(
            @NonNull ImportedScene scene,
            @NonNull Node top,
            @NonNull Materials materials,
            @NonNull ToIntFunction<Material> materialIndex,
            boolean keepAttributes) {
        final double[] origin = middle(scene, top);
        final LodMesh.Builder builder = LodMesh.builder(origin);
        int placed = 0;
        int skipped = 0;
        boolean any = false;
        final Deque<Node> open = new ArrayDeque<>();
        open.push(top);
        while (!open.isEmpty()) {
            final Node node = open.pop();
            node.children().forEach(open::push);
            for (int sceneMesh : node.meshes()) {
                final Material material = materials.of(node, sceneMesh);
                if (material == null
                        || material.getTransparency() != Material.Transparency.OPAQUE) {
                    ++skipped;
                    continue;
                }
                ++placed;
                any |=
                        add(
                                builder,
                                scene.meshes().get(sceneMesh).data(),
                                node.world(),
                                origin,
                                materialIndex.applyAsInt(material),
                                keepAttributes);
            }
        }
        return any ? new Input(builder.build(), placed, skipped) : null;
    }

    /**
     * Turn a model's opaque meshes into one mesh for level of detail to build a model from (see
     * {@code Lods#addModel}), in the model's own frame, each drawn with its own material.
     *
     * @param meshes The model's meshes.
     * @param materialIndex A material's index in the scene's material cache.
     * @param keepAttributes Whether triangles keep their normals, tangents and texture coordinates.
     * @return The mesh, its origin at the middle of the model's bounds, or null if nothing in it is
     *     opaque.
     */
    public static LodMesh ofModel(
            @NonNull List<MeshData> meshes,
            @NonNull ToIntFunction<Material> materialIndex,
            boolean keepAttributes) {
        final double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        final double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (MeshData data : meshes) {
            final float[] v = data.getVertexData();
            for (int i = 0; i < data.getVertexCount(); ++i) {
                for (int axis = 0; axis < AXES; ++axis) {
                    low[axis] = Math.min(low[axis], v[i * STRIDE + axis]);
                    high[axis] = Math.max(high[axis], v[i * STRIDE + axis]);
                }
            }
        }
        final double[] origin = new double[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            origin[axis] = low[axis] <= high[axis] ? (low[axis] + high[axis]) / 2 : 0;
        }
        final LodMesh.Builder builder = LodMesh.builder(origin);
        boolean any = false;
        final Matrix4d identity = new Matrix4d();
        for (MeshData data : meshes) {
            final Material material = data.getMaterial();
            if (material != null && material.getTransparency() != Material.Transparency.OPAQUE) {
                continue;
            }
            any |=
                    add(
                            builder,
                            data,
                            identity,
                            origin,
                            material == null ? 0 : materialIndex.applyAsInt(material),
                            keepAttributes);
        }
        return any ? builder.build() : null;
    }

    /**
     * Add a placed mesh's triangles to a builder, welded to what is there.
     *
     * @param builder The builder.
     * @param data The mesh.
     * @param world Where it is placed.
     * @param origin The builder's origin, in the world.
     * @param index Its material's index.
     * @param keepAttributes Whether triangles keep their normals, tangents and texture coordinates.
     * @return True if any triangle went in.
     */
    private static boolean add(
            LodMesh.@NonNull Builder builder,
            @NonNull MeshData data,
            @NonNull Matrix4dc world,
            double @NonNull [] origin,
            int index,
            boolean keepAttributes) {
        final Vector3d point = new Vector3d();
        final Vector3d normal = new Vector3d();
        final Vector3d tangent = new Vector3d();
        final Vector3d bitangent = new Vector3d();
        final Vector3d cross = new Vector3d();
        final Matrix3d normalMatrix = new Matrix3d();
        final float[] direction = new float[AXES];
        final int[] corners = new int[Attributes.PER_TRIANGLE];
        world.normal(normalMatrix);
        // A mirroring transform turns triangles inside out unless their winding is reversed
        final boolean mirrored = world.determinant3x3() < 0;
        final float[] v = data.getVertexData();
        final int[] welded = new int[data.getVertexCount()];
        for (int i = 0; i < welded.length; ++i) {
            final int at = i * STRIDE;
            world.transformPosition(point.set(v[at], v[at + 1], v[at + 2]));
            welded[i] =
                    builder.vertex(
                            (float) (point.x - origin[0]),
                            (float) (point.y - origin[1]),
                            (float) (point.z - origin[2]));
        }
        boolean any = false;
        final int[] indices = data.getIndices();
        for (int i = 0; i + 2 < indices.length; i += CORNERS) {
            final int a = indices[i];
            final int b = mirrored ? indices[i + 2] : indices[i + 1];
            final int c = mirrored ? indices[i + 1] : indices[i + 2];
            final int[] order = {a, b, c};
            for (int corner = 0; corner < CORNERS; ++corner) {
                final int at = order[corner] * STRIDE;
                normalMatrix.transform(
                        normal.set(v[at + NORMAL], v[at + NORMAL + 1], v[at + NORMAL + 2]));
                world.transformDirection(
                        tangent.set(v[at + TANGENT], v[at + TANGENT + 1], v[at + TANGENT + 2]));
                world.transformDirection(
                        bitangent.set(
                                v[at + BITANGENT], v[at + BITANGENT + 1], v[at + BITANGENT + 2]));
                final int base = corner * Attributes.VALUES;
                corners[base] = encode(normal, direction);
                final boolean flipped = normal.cross(tangent, cross).dot(bitangent) < 0;
                corners[base + 1] =
                        (encode(tangent, direction) & ~FLIP_BIT) | (flipped ? FLIP_BIT : 0);
                corners[base + 2] =
                        (Float.floatToFloat16(v[at + UV]) & LOW_16)
                                | (Float.floatToFloat16(v[at + UV + 1]) << SHORT_BITS);
            }
            any |=
                    keepAttributes
                            ? builder.triangle(welded[a], welded[b], welded[c], index, 1, corners)
                            : builder.triangle(welded[a], welded[b], welded[c], index);
        }
        return any;
    }

    /**
     * Encode a direction as octahedral snorm16s.
     *
     * @param value The direction.
     * @param scratch Three floats to use.
     * @return The packed direction.
     */
    private static int encode(@NonNull Vector3d value, float @NonNull [] scratch) {
        scratch[0] = (float) value.x;
        scratch[1] = (float) value.y;
        scratch[2] = (float) value.z;
        if (scratch[0] == 0 && scratch[1] == 0 && scratch[2] == 0) {
            // A degenerate frame; any direction will do
            scratch[1] = 1;
        }
        return Attributes.encodeDirection(scratch);
    }

    /**
     * The middle of the world bounds of the meshes under a node, to measure positions from so they
     * keep their precision as floats.
     *
     * @param scene The scene.
     * @param top The node.
     * @return The middle, or the world's origin if there are no meshes.
     */
    private static double[] middle(@NonNull ImportedScene scene, @NonNull Node top) {
        final Vector3d min = new Vector3d(Double.MAX_VALUE);
        final Vector3d max = new Vector3d(-Double.MAX_VALUE);
        final Vector3d corner = new Vector3d();
        final Deque<Node> open = new ArrayDeque<>();
        open.push(top);
        boolean any = false;
        while (!open.isEmpty()) {
            final Node node = open.pop();
            node.children().forEach(open::push);
            for (int sceneMesh : node.meshes()) {
                final MeshData data = scene.meshes().get(sceneMesh).data();
                for (int i = 0; i < BOX_CORNERS; ++i) {
                    corner.set(
                            (i & 1) == 0 ? data.getAabbMin().x : data.getAabbMax().x,
                            (i & 2) == 0 ? data.getAabbMin().y : data.getAabbMax().y,
                            (i & 4) == 0 ? data.getAabbMin().z : data.getAabbMax().z);
                    node.world().transformPosition(corner);
                    min.min(corner);
                    max.max(corner);
                    any = true;
                }
            }
        }
        if (!any) {
            return new double[AXES];
        }
        return new double[] {(min.x + max.x) / 2, (min.y + max.y) / 2, (min.z + max.z) / 2};
    }
}
