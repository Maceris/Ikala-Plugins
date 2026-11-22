package com.ikalagaming.converter.scene;

import com.ikalagaming.converter.ConverterScripts;
import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.graphics.asset.AssetMetadata;
import com.ikalagaming.graphics.asset.AssetWriter;
import com.ikalagaming.graphics.asset.MeshSection;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Exports meshes from an imported scene as one mesh in an asset container with a {@link
 * MeshSection}, for a game to load as a model, such as a block's shape. Every mesh under the chosen
 * node goes in, placed where the scene puts it, and can be moved so a different point is the
 * model's origin. Materials aren't exported: the game chooses them.
 */
public final class MeshExport {

    /** The exporter named in the container's metadata. */
    public static final String EXPORTER = "Ikala Asset Editor mesh export";

    /** The metadata key naming the node exported. */
    public static final String KEY_NODE = "node";

    /**
     * How finely model vertices are meant to line up, in steps per meter, for the grid check. Block
     * models keep a 1/16 m grid.
     */
    public static final int GRID_STEPS = 16;

    /** How far off a grid line a vertex may be and still count as on it, in grid steps. */
    private static final double GRID_TOLERANCE = 1e-3;

    /** Floats per vertex. */
    private static final int STRIDE = MeshData.VERTEX_SIZE_IN_FLOATS;

    /** Where the normal starts in a vertex. */
    private static final int NORMAL = 3;

    /** Where the tangent starts in a vertex. */
    private static final int TANGENT = 6;

    /** Where the bitangent starts in a vertex. */
    private static final int BITANGENT = 9;

    /**
     * What an export made.
     *
     * @param mesh The mesh, in the model's space.
     * @param offGrid How many vertices are off the 1/{@value #GRID_STEPS} m grid.
     */
    public record Result(@NonNull MeshData mesh, int offGrid) {
        /**
         * How many triangles the mesh has.
         *
         * @return The count.
         */
        public int triangles() {
            return mesh.getIndices().length / 3;
        }
    }

    /**
     * Gather every mesh under a node into one mesh, in the scene's space moved by an offset.
     *
     * @param scene The scene.
     * @param top The node whose meshes, and its children's, go in.
     * @param offset Added to every position, so the model's origin can be somewhere else.
     * @return The mesh and its grid check, or null if there are no triangles under the node.
     */
    public static Result gather(
            @NonNull ImportedScene scene, @NonNull Node top, @NonNull Vector3dc offset) {
        final List<float[]> vertexParts = new ArrayList<>();
        final List<int[]> indexParts = new ArrayList<>();
        int vertexCount = 0;
        int indexCount = 0;
        final Deque<Node> open = new ArrayDeque<>();
        open.push(top);
        while (!open.isEmpty()) {
            final Node node = open.pop();
            node.children().forEach(open::push);
            final Matrix4d world = new Matrix4d(node.world());
            final Matrix3d linear = world.get3x3(new Matrix3d());
            final Matrix3d normals = new Matrix3d(linear).invert().transpose();
            // A mirroring transform turns triangles inside out unless their winding flips too
            final boolean mirrored = linear.determinant() < 0;
            for (int sceneMesh : node.meshes()) {
                final MeshData data = scene.meshes().get(sceneMesh).data();
                final float[] vertices =
                        transform(data.getVertexData(), world, linear, normals, offset);
                final int[] indices = data.getIndices().clone();
                for (int i = 0; i < indices.length; i += 3) {
                    if (mirrored) {
                        final int swap = indices[i + 1];
                        indices[i + 1] = indices[i + 2];
                        indices[i + 2] = swap;
                    }
                }
                for (int i = 0; i < indices.length; ++i) {
                    indices[i] += vertexCount;
                }
                vertexParts.add(vertices);
                indexParts.add(indices);
                vertexCount += data.getVertexCount();
                indexCount += indices.length;
            }
        }
        if (indexCount == 0) {
            return null;
        }
        final float[] vertices = new float[vertexCount * STRIDE];
        final int[] indices = new int[indexCount];
        int at = 0;
        for (float[] part : vertexParts) {
            System.arraycopy(part, 0, vertices, at, part.length);
            at += part.length;
        }
        at = 0;
        for (int[] part : indexParts) {
            System.arraycopy(part, 0, indices, at, part.length);
            at += part.length;
        }
        final Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        final Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        int offGrid = 0;
        for (int v = 0; v < vertexCount; ++v) {
            final int base = v * STRIDE;
            final Vector3f position =
                    new Vector3f(vertices[base], vertices[base + 1], vertices[base + 2]);
            min.min(position);
            max.max(position);
            if (!onGrid(position.x) || !onGrid(position.y) || !onGrid(position.z)) {
                ++offGrid;
            }
        }
        return new Result(
                new MeshData(min, max, vertexCount, vertices, indices, 0, new byte[0]), offGrid);
    }

    /**
     * Whether a coordinate is on the grid.
     *
     * @param value The coordinate, in meters.
     * @return True if it is within the tolerance of a grid line.
     */
    static boolean onGrid(float value) {
        final double steps = value * (double) GRID_STEPS;
        return Math.abs(steps - Math.rint(steps)) <= GRID_TOLERANCE;
    }

    /**
     * Move a mesh's vertices into the scene's space.
     *
     * @param source The vertices, in {@link MeshData}'s layout.
     * @param world The node's transform.
     * @param linear Its rotation and scale, for tangents.
     * @param normals The transform for normals.
     * @param offset Added to positions.
     * @return The moved vertices, a new array.
     */
    private static float[] transform(
            float @NonNull [] source,
            @NonNull Matrix4d world,
            @NonNull Matrix3d linear,
            @NonNull Matrix3d normals,
            @NonNull Vector3dc offset) {
        final float[] out = source.clone();
        final Vector3d scratch = new Vector3d();
        for (int base = 0; base < out.length; base += STRIDE) {
            scratch.set(out[base], out[base + 1], out[base + 2]);
            world.transformPosition(scratch).add(offset);
            store(out, base, scratch);
            direction(out, base + NORMAL, normals, scratch);
            direction(out, base + TANGENT, linear, scratch);
            direction(out, base + BITANGENT, linear, scratch);
        }
        return out;
    }

    /**
     * Turn a direction in place and make it unit length again.
     *
     * @param vertices The vertices.
     * @param at Where the direction starts.
     * @param transform The transform.
     * @param scratch Scratch space.
     */
    private static void direction(
            float @NonNull [] vertices,
            int at,
            @NonNull Matrix3d transform,
            @NonNull Vector3d scratch) {
        scratch.set(vertices[at], vertices[at + 1], vertices[at + 2]);
        transform.transform(scratch);
        if (scratch.lengthSquared() > 0) {
            scratch.normalize();
        }
        store(vertices, at, scratch);
    }

    /**
     * Write a vector into vertices.
     *
     * @param vertices The vertices.
     * @param at Where it goes.
     * @param value The vector.
     */
    private static void store(float @NonNull [] vertices, int at, @NonNull Vector3d value) {
        vertices[at] = (float) value.x;
        vertices[at + 1] = (float) value.y;
        vertices[at + 2] = (float) value.z;
    }

    /**
     * Write a gathered mesh to a container file.
     *
     * @param result The gathered mesh.
     * @param scene The scene it came from, named in the metadata.
     * @param top The node it came from, named in the metadata.
     * @param target The file to write.
     */
    public static void write(
            @NonNull Result result,
            @NonNull ImportedScene scene,
            @NonNull Node top,
            @NonNull Path target) {
        new AssetWriter()
                .metadata(
                        AssetMetadata.of(EXPORTER, ConverterScripts.version())
                                .with(AssetMetadata.SOURCE, scene.file().getFileName().toString())
                                .with(KEY_NODE, top.name()))
                .section(
                        MeshSection.TAG,
                        MeshSection.VERSION,
                        true,
                        MeshSection.encode(result.mesh()))
                .write(target);
    }

    /**
     * Export a node's meshes to a file.
     *
     * @param imported The scene.
     * @param top The node.
     * @param bottomCenter Whether the file's origin is the middle of the model's bottom.
     * @param target The file to write.
     * @return What was written.
     * @throws IllegalArgumentException If there are no triangles to export.
     */
    public static Result export(
            @NonNull ImportedScene imported,
            @NonNull Node top,
            boolean bottomCenter,
            @NonNull Path target) {
        final Result result =
                gather(imported, top, bottomCenter ? new Vector3d(0.5, 0, 0.5) : new Vector3d());
        if (result == null) {
            throw new IllegalArgumentException("there are no triangles under " + top.name());
        }
        write(result, imported, top, target);
        return result;
    }

    /**
     * Describe an export.
     *
     * @param result What was written.
     * @param target Where.
     * @return The description.
     */
    public static String describe(@NonNull Result result, @NonNull Path target) {
        final String bounds =
                String.format(
                        "%.3f, %.3f, %.3f to %.3f, %.3f, %.3f",
                        result.mesh().getAabbMin().x,
                        result.mesh().getAabbMin().y,
                        result.mesh().getAabbMin().z,
                        result.mesh().getAabbMax().x,
                        result.mesh().getAabbMax().y,
                        result.mesh().getAabbMax().z);
        return "Wrote "
                + target.getFileName()
                + ": "
                + result.mesh().getVertexCount()
                + " vertices, "
                + result.triangles()
                + " triangles, bounds "
                + bounds
                + (result.offGrid() == 0
                        ? ", every vertex on the 1/16 m grid"
                        : ", " + result.offGrid() + " vertices off the 1/16 m grid");
    }

    /** Not to be made. */
    private MeshExport() {}
}
