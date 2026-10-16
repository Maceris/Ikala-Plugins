package com.ikalagaming.converter.scene;

import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A scene read from a model file, as the file has it: a tree of nodes with their transforms, the
 * meshes nodes refer to (each stored once, however many nodes use it), and the materials with the
 * textures they name. Nothing here is on the GPU; {@link ScenePlacement} puts it there.
 *
 * @param file The file it was read from.
 * @param meshes Every mesh, in local space.
 * @param materials Every material.
 * @param root The root node.
 * @param unitScale The scale applied to the root so one unit is one meter, from the file's unit.
 * @param upAxis The axis the file says is up: 0 for x, 1 for y, 2 for z.
 * @param warnings Things that were skipped or guessed while reading.
 * @param readMillis How long reading took.
 */
public record ImportedScene(
        @NonNull Path file,
        @NonNull List<Mesh> meshes,
        @NonNull List<ImportedMaterial> materials,
        @NonNull Node root,
        double unitScale,
        int upAxis,
        @NonNull List<String> warnings,
        long readMillis) {

    /**
     * A mesh, in the space of the nodes that use it.
     *
     * @param name Its name in the file.
     * @param data The vertices and indices, ready to register.
     * @param material Its material, an index into the scene's materials.
     */
    public record Mesh(@NonNull String name, @NonNull MeshData data, int material) {
        /**
         * How many triangles it has.
         *
         * @return The count.
         */
        public int triangles() {
            return data.getIndices().length / 3;
        }
    }

    /** What a texture is for. */
    public enum TextureSlot {
        /** Base color, with opacity in alpha. */
        BASE_COLOR,
        /** A tangent space normal map. */
        NORMAL,
        /**
         * Specular, which metal-rough scenes like Bistro use for packed occlusion, roughness and
         * metalness.
         */
        SPECULAR,
        /** Emitted light. */
        EMISSIVE,
        /** Opacity on its own. */
        OPACITY,
        /** Roughness on its own. */
        ROUGHNESS,
        /** Metalness on its own. */
        METALNESS,
        /** Ambient occlusion on its own. */
        OCCLUSION
    }

    /**
     * A texture a material names.
     *
     * @param named The path as the file has it, often from the machine it was made on.
     * @param found The file it was found as, or null if it couldn't be found.
     */
    public record TextureRef(@NonNull String named, @Nullable Path found) {}

    /**
     * A material as the file describes it.
     *
     * @param name Its name in the file.
     * @param color The diffuse or base color, sRGB, with opacity in alpha.
     * @param roughness The roughness, or NaN if the file doesn't say.
     * @param metallic The metalness, or NaN if the file doesn't say.
     * @param emissive The emitted color, as the file gives it, black for none.
     * @param textures The textures it names, by what they are for.
     */
    public record ImportedMaterial(
            @NonNull String name,
            @NonNull Vector4f color,
            float roughness,
            float metallic,
            @NonNull Vector3f emissive,
            @NonNull Map<TextureSlot, TextureRef> textures) {
        /** Copy the textures so they can't change. */
        public ImportedMaterial {
            textures =
                    textures.isEmpty()
                            ? Map.of()
                            : Collections.unmodifiableMap(new EnumMap<>(textures));
        }
    }

    /** A node of the scene's tree. */
    public static final class Node {
        /** Its name in the file. */
        private final String name;

        /** Its transform relative to its parent. */
        private final Matrix4d local;

        /** Its transform in the world, with the unit scale and up axis applied. */
        private final Matrix4d world;

        /** The meshes it draws, as indices into the scene's meshes. */
        private final int[] meshes;

        /** Its parent, or null for the root. */
        private final Node parent;

        /** Its children. */
        private final List<Node> children = new ArrayList<>();

        /**
         * Make a node.
         *
         * @param name Its name.
         * @param local Its transform relative to its parent.
         * @param parent Its parent, or null for the root.
         * @param parentWorld The parent's world transform, or the root's correction for the root.
         * @param meshes The meshes it draws.
         */
        Node(
                @NonNull String name,
                @NonNull Matrix4dc local,
                @Nullable Node parent,
                @NonNull Matrix4dc parentWorld,
                int @NonNull [] meshes) {
            this.name = name;
            this.local = new Matrix4d(local);
            this.world = new Matrix4d(parentWorld).mul(local);
            this.parent = parent;
            this.meshes = meshes.clone();
        }

        /**
         * Its name in the file.
         *
         * @return The name.
         */
        public String name() {
            return name;
        }

        /**
         * Its transform relative to its parent.
         *
         * @return The transform. Don't change it.
         */
        public Matrix4dc local() {
            return local;
        }

        /**
         * Its transform in the world, with the unit scale and up axis applied.
         *
         * @return The transform. Don't change it.
         */
        public Matrix4dc world() {
            return world;
        }

        /**
         * The meshes it draws.
         *
         * @return Indices into the scene's meshes. Don't change them.
         */
        public int[] meshes() {
            return meshes;
        }

        /**
         * Its parent.
         *
         * @return The parent, or null for the root.
         */
        public @Nullable Node parent() {
            return parent;
        }

        /**
         * Its children.
         *
         * @return The children. Don't change them.
         */
        public List<Node> children() {
            return children;
        }

        /**
         * Add a child, while reading.
         *
         * @param child The child.
         */
        void addChild(@NonNull Node child) {
            children.add(child);
        }
    }

    /**
     * How many nodes there are, the root included.
     *
     * @return The count.
     */
    public int nodeCount() {
        return count(root);
    }

    /**
     * Count a node and everything below it.
     *
     * @param node The node.
     * @return The count.
     */
    private static int count(Node node) {
        int total = 1;
        for (Node child : node.children()) {
            total += count(child);
        }
        return total;
    }

    /**
     * How many triangles are drawn, counting a mesh once for every node that draws it.
     *
     * @return The count.
     */
    public long drawnTriangles() {
        return drawnTriangles(root);
    }

    /**
     * Count the triangles a node and everything below it draw.
     *
     * @param node The node.
     * @return The count.
     */
    private long drawnTriangles(Node node) {
        long total = 0;
        for (int mesh : node.meshes()) {
            total += meshes.get(mesh).triangles();
        }
        for (Node child : node.children()) {
            total += drawnTriangles(child);
        }
        return total;
    }

    /**
     * How many triangles the meshes have, each counted once.
     *
     * @return The count.
     */
    public long uniqueTriangles() {
        long total = 0;
        for (Mesh mesh : meshes) {
            total += mesh.triangles();
        }
        return total;
    }

    /**
     * The world bounds of a node's own meshes and everything below it.
     *
     * @param node The node.
     * @param min Set to the smallest corner.
     * @param max Set to the largest corner.
     * @return True if there was anything to bound.
     */
    public boolean bounds(@NonNull Node node, @NonNull Vector3d min, @NonNull Vector3d max) {
        min.set(Double.POSITIVE_INFINITY);
        max.set(Double.NEGATIVE_INFINITY);
        addBounds(node, min, max, new Vector3d());
        return min.x <= max.x;
    }

    /**
     * Grow bounds by a node's meshes and its children's.
     *
     * @param node The node.
     * @param min The smallest corner so far.
     * @param max The largest corner so far.
     * @param corner Scratch.
     */
    private void addBounds(Node node, Vector3d min, Vector3d max, Vector3d corner) {
        for (int index : node.meshes()) {
            final MeshData data = meshes.get(index).data();
            for (int c = 0; c < 8; ++c) {
                corner.set(
                        (c & 1) == 0 ? data.getAabbMin().x : data.getAabbMax().x,
                        (c & 2) == 0 ? data.getAabbMin().y : data.getAabbMax().y,
                        (c & 4) == 0 ? data.getAabbMin().z : data.getAabbMax().z);
                node.world().transformPosition(corner);
                min.min(corner);
                max.max(corner);
            }
        }
        for (Node child : node.children()) {
            addBounds(child, min, max, corner);
        }
    }
}
