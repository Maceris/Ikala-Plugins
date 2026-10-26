package com.ikalagaming.converter.scene;

import com.ikalagaming.converter.scene.ImportedScene.ImportedMaterial;
import com.ikalagaming.converter.scene.ImportedScene.Mesh;
import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import org.joml.Matrix4d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simple shapes to try materials on, made in memory as scenes the workbench shows like any file: a
 * sphere, a cube or a plane, each standing on a floor so shadows show. Every vertex has a normal,
 * tangents and texture coordinates, so textured materials and normal maps work on them. Also a
 * stage to watch particles on, see {@link #particleStage()}.
 */
public final class PreviewShapes {
    /** The shapes there are. */
    public enum Shape {
        /** A ball one meter across. */
        SPHERE("Sphere"),
        /** A cube one meter on a side. */
        CUBE("Cube"),
        /** A flat square two meters on a side, facing up. */
        PLANE("Plane");

        /** Its name, for buttons. */
        public final String label;

        Shape(String label) {
            this.label = label;
        }
    }

    /** Floats per vertex in mesh data. */
    private static final int STRIDE = MeshData.VERTEX_SIZE_IN_FLOATS;

    /** How many slices the sphere has around. */
    static final int SPHERE_SEGMENTS = 48;

    /** How many bands the sphere has from pole to pole. */
    static final int SPHERE_RINGS = 24;

    /** Half the floor's width. */
    private static final float FLOOR_HALF = 3;

    /** What the particle stage's scene is called, which tells the workbench to run the viewer. */
    public static final Path PARTICLE_STAGE = Path.of("Particle stage");

    /** Half the particle stage's floor's width. */
    private static final float STAGE_HALF = 6;

    /** How tall the stage's walls are. */
    private static final double WALL_HEIGHT = 3;

    /** How thick the stage's walls are. */
    private static final double WALL_THICKNESS = 0.2;

    /** How far behind the emitter the back wall stands, and how far left the side wall. */
    private static final double WALL_DISTANCE = 3.5;

    /** How wide and tall the glass panes are. */
    private static final double PANE_WIDTH = 2;

    private static final double PANE_HEIGHT = 2.4;

    /** How thick the panes are. */
    private static final double PANE_THICKNESS = 0.04;

    /** How far in front of and behind the emitter the panes stand, and off to each side. */
    private static final double PANE_DISTANCE = 1.5;

    private static final double PANE_SIDE = 1;

    /** Not to be made. */
    private PreviewShapes() {}

    /**
     * Make a preview scene.
     *
     * @param shape The shape.
     * @return The scene: the shape, resting on the floor at the origin, with its own material and
     *     the floor's.
     */
    public static ImportedScene scene(@NonNull Shape shape) {
        final List<Mesh> meshes = new ArrayList<>();
        final MeshData data =
                switch (shape) {
                    case SPHERE -> sphere(0.5f);
                    case CUBE -> cube(0.5f);
                    case PLANE -> plane(1, 0);
                };
        meshes.add(new Mesh(shape.label, data, 0));
        meshes.add(new Mesh("Floor", plane(FLOOR_HALF, 0), 1));

        final List<ImportedMaterial> materials = new ArrayList<>();
        materials.add(
                new ImportedMaterial(
                        "Preview",
                        new Vector4f(0.8f, 0.8f, 0.8f, 1),
                        0.5f,
                        0,
                        new Vector3f(),
                        Map.of()));
        materials.add(
                new ImportedMaterial(
                        "Floor",
                        new Vector4f(0.35f, 0.35f, 0.35f, 1),
                        0.8f,
                        0,
                        new Vector3f(),
                        Map.of()));

        final Matrix4d identity = new Matrix4d();
        final Node root = new Node("Preview", identity, null, identity, new int[0]);
        // Lift the shape so it rests on the floor; the plane floats a little above it
        final Matrix4d lift = new Matrix4d().translation(0, shape == Shape.PLANE ? 0.01 : 0.5, 0);
        root.addChild(new Node(shape.label, lift, root, root.world(), new int[] {0}));
        root.addChild(new Node("Floor", identity, root, root.world(), new int[] {1}));
        return new ImportedScene(
                Path.of(shape.label + " preview"),
                meshes,
                materials,
                root,
                List.of(),
                1,
                1,
                List.of(),
                0);
    }

    /**
     * Make the stage particles are watched on: a floor, a wall behind and one to the left, and two
     * panes of blue glass, one in front of the emitter's left side and one behind its right, so
     * particles show in front of, behind and through translucent surfaces. The emitter stands at
     * the origin.
     *
     * @return The scene, named {@link #PARTICLE_STAGE}.
     */
    public static ImportedScene particleStage() {
        final List<Mesh> meshes = new ArrayList<>();
        meshes.add(new Mesh("Floor", plane(STAGE_HALF, 0), 0));
        meshes.add(new Mesh("Wall", cube(0.5f), 1));
        meshes.add(new Mesh("Glass", cube(0.5f), 2));

        final List<ImportedMaterial> materials = new ArrayList<>();
        materials.add(
                new ImportedMaterial(
                        "Floor",
                        new Vector4f(0.35f, 0.35f, 0.35f, 1),
                        0.8f,
                        0,
                        new Vector3f(),
                        Map.of()));
        materials.add(
                new ImportedMaterial(
                        "Wall",
                        new Vector4f(0.6f, 0.58f, 0.55f, 1),
                        0.9f,
                        0,
                        new Vector3f(),
                        Map.of()));
        materials.add(
                new ImportedMaterial(
                        "Glass",
                        new Vector4f(0.55f, 0.75f, 1, 0.3f),
                        0.05f,
                        0,
                        new Vector3f(),
                        Map.of()));

        final Matrix4d identity = new Matrix4d();
        final Node root = new Node("Particle stage", identity, null, identity, new int[0]);
        root.addChild(new Node("Floor", identity, root, root.world(), new int[] {0}));
        root.addChild(
                box(
                        root,
                        "Back wall",
                        1,
                        0,
                        WALL_HEIGHT / 2,
                        -WALL_DISTANCE,
                        2 * STAGE_HALF,
                        WALL_HEIGHT,
                        WALL_THICKNESS));
        root.addChild(
                box(
                        root,
                        "Side wall",
                        1,
                        -WALL_DISTANCE,
                        WALL_HEIGHT / 2,
                        0,
                        WALL_THICKNESS,
                        WALL_HEIGHT,
                        2 * STAGE_HALF));
        root.addChild(
                box(
                        root,
                        "Glass in front",
                        2,
                        -PANE_SIDE,
                        PANE_HEIGHT / 2,
                        PANE_DISTANCE,
                        PANE_WIDTH,
                        PANE_HEIGHT,
                        PANE_THICKNESS));
        root.addChild(
                box(
                        root,
                        "Glass behind",
                        2,
                        PANE_SIDE,
                        PANE_HEIGHT / 2,
                        -PANE_DISTANCE,
                        PANE_WIDTH,
                        PANE_HEIGHT,
                        PANE_THICKNESS));
        return new ImportedScene(
                PARTICLE_STAGE, meshes, materials, root, List.of(), 1, 1, List.of(), 0);
    }

    /**
     * A node drawing the unit cube mesh moved and stretched into a box.
     *
     * @param root Its parent.
     * @param name Its name.
     * @param mesh The cube mesh, with its material.
     * @param x Its middle's x.
     * @param y Its middle's y.
     * @param z Its middle's z.
     * @param width Its size along x.
     * @param height Its size along y.
     * @param depth Its size along z.
     * @return The node.
     */
    private static Node box(
            Node root,
            String name,
            int mesh,
            double x,
            double y,
            double z,
            double width,
            double height,
            double depth) {
        final Matrix4d transform = new Matrix4d().translation(x, y, z).scale(width, height, depth);
        return new Node(name, transform, root, root.world(), new int[] {mesh});
    }

    /**
     * A sphere around the origin, with texture coordinates wrapping once around and pole to pole.
     *
     * @param radius The radius.
     * @return The mesh.
     */
    static MeshData sphere(float radius) {
        final int columns = SPHERE_SEGMENTS + 1;
        final int rows = SPHERE_RINGS + 1;
        final float[] v = new float[columns * rows * STRIDE];
        int at = 0;
        for (int ring = 0; ring < rows; ++ring) {
            final double theta = Math.PI * ring / SPHERE_RINGS;
            for (int segment = 0; segment < columns; ++segment) {
                final double phi = 2 * Math.PI * segment / SPHERE_SEGMENTS;
                final float nx = (float) (Math.sin(theta) * Math.cos(phi));
                final float ny = (float) Math.cos(theta);
                final float nz = (float) (-Math.sin(theta) * Math.sin(phi));
                // Along increasing u, around the sphere
                final float tx = (float) -Math.sin(phi);
                final float tz = (float) -Math.cos(phi);
                at =
                        vertex(
                                v,
                                at,
                                nx * radius,
                                ny * radius,
                                nz * radius,
                                nx,
                                ny,
                                nz,
                                tx,
                                0,
                                tz,
                                (float) segment / SPHERE_SEGMENTS,
                                (float) ring / SPHERE_RINGS);
            }
        }
        final int[] indices = new int[SPHERE_SEGMENTS * SPHERE_RINGS * 6];
        int i = 0;
        for (int ring = 0; ring < SPHERE_RINGS; ++ring) {
            for (int segment = 0; segment < SPHERE_SEGMENTS; ++segment) {
                final int a = ring * columns + segment;
                final int b = a + columns;
                // Counter-clockwise seen from outside
                indices[i++] = a;
                indices[i++] = b;
                indices[i++] = a + 1;
                indices[i++] = a + 1;
                indices[i++] = b;
                indices[i++] = b + 1;
            }
        }
        return mesh(v, indices, radius, radius, radius);
    }

    /**
     * A cube around the origin, each face with the whole texture.
     *
     * @param half Half its size.
     * @return The mesh.
     */
    static MeshData cube(float half) {
        // Each face: its normal, then the direction u runs, both as axis vectors
        final float[][] faces = {
            {1, 0, 0, 0, 0, -1},
            {-1, 0, 0, 0, 0, 1},
            {0, 1, 0, 1, 0, 0},
            {0, -1, 0, 1, 0, 0},
            {0, 0, 1, 1, 0, 0},
            {0, 0, -1, -1, 0, 0}
        };
        final float[] v = new float[faces.length * 4 * STRIDE];
        final int[] indices = new int[faces.length * 6];
        int at = 0;
        int i = 0;
        for (int f = 0; f < faces.length; ++f) {
            final Vector3f n = new Vector3f(faces[f][0], faces[f][1], faces[f][2]);
            final Vector3f t = new Vector3f(faces[f][3], faces[f][4], faces[f][5]);
            // v runs down the texture, so up the face is -v
            final Vector3f up = n.cross(t, new Vector3f());
            final int first = f * 4;
            final float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (float[] corner : corners) {
                final Vector3f p = new Vector3f(n).fma(corner[0], t).fma(corner[1], up).mul(half);
                at =
                        vertex(
                                v,
                                at,
                                p.x,
                                p.y,
                                p.z,
                                n.x,
                                n.y,
                                n.z,
                                t.x,
                                t.y,
                                t.z,
                                (corner[0] + 1) / 2,
                                (1 - corner[1]) / 2);
            }
            indices[i++] = first;
            indices[i++] = first + 1;
            indices[i++] = first + 2;
            indices[i++] = first;
            indices[i++] = first + 2;
            indices[i++] = first + 3;
        }
        return mesh(v, indices, half, half, half);
    }

    /**
     * A flat square facing up, with the whole texture across it.
     *
     * @param half Half its width.
     * @param height Its height.
     * @return The mesh.
     */
    static MeshData plane(float half, float height) {
        final float[] v = new float[4 * STRIDE];
        int at = 0;
        final float[][] corners = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
        for (float[] corner : corners) {
            at =
                    vertex(
                            v,
                            at,
                            corner[0] * half,
                            height,
                            corner[1] * half,
                            0,
                            1,
                            0,
                            1,
                            0,
                            0,
                            (corner[0] + 1) / 2,
                            (corner[1] + 1) / 2);
        }
        // Counter-clockwise seen from above
        final int[] indices = {0, 1, 2, 0, 2, 3};
        final MeshData mesh = mesh(v, indices, half, 0, half);
        mesh.getAabbMin().y = height;
        mesh.getAabbMax().y = height;
        return mesh;
    }

    /**
     * Write one vertex, with its bitangent, pointing up the texture, worked out from the normal and
     * tangent.
     *
     * @param v The vertex data.
     * @param at Where the vertex starts.
     * @param px The position's x.
     * @param py The position's y.
     * @param pz The position's z.
     * @param nx The normal's x.
     * @param ny The normal's y.
     * @param nz The normal's z.
     * @param tx The tangent's x, along increasing u.
     * @param ty The tangent's y.
     * @param tz The tangent's z.
     * @param u The texture coordinate across.
     * @param w The texture coordinate down.
     * @return Where the next vertex starts.
     */
    private static int vertex(
            float[] v,
            int at,
            float px,
            float py,
            float pz,
            float nx,
            float ny,
            float nz,
            float tx,
            float ty,
            float tz,
            float u,
            float w) {
        // Up the texture, as assimp gives imported meshes' bitangents
        final Vector3f bitangent = new Vector3f(nx, ny, nz).cross(tx, ty, tz);
        final float[] values = {
            px, py, pz, nx, ny, nz, tx, ty, tz, bitangent.x, bitangent.y, bitangent.z, u, w
        };
        System.arraycopy(values, 0, v, at, STRIDE);
        return at + STRIDE;
    }

    /**
     * Wrap vertex data as a mesh, bounded by a box around the origin.
     *
     * @param v The vertex data.
     * @param indices The triangles.
     * @param hx Half the box's width.
     * @param hy Half its height.
     * @param hz Half its depth.
     * @return The mesh.
     */
    private static MeshData mesh(float[] v, int[] indices, float hx, float hy, float hz) {
        return new MeshData(
                new Vector3f(-hx, -hy, -hz),
                new Vector3f(hx, hy, hz),
                v.length / STRIDE,
                v,
                indices,
                0,
                new byte[0]);
    }
}
