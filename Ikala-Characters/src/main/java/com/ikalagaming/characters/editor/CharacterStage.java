package com.ikalagaming.characters.editor;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.InstanceHandle;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;

import lombok.NonNull;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

/**
 * A plain place to look at a character: a floor to stand on and cast shadows onto, a sun and sky
 * light, and the world renderer, with nothing else of the editor's in the way. Entering remembers
 * the renderer, lights and camera, and leaving puts them back. Render thread only.
 *
 * @author Ches Burks
 */
public final class CharacterStage {
    /**
     * Where the stage is, away from the origin, where demos put things.
     *
     * @see #origin()
     */
    private static final Vector3dc ORIGIN = new Vector3d(0, 0, 500);

    /** Half the floor's width, in meters. */
    private static final float FLOOR_HALF = 6;

    /** Floats per vertex in mesh data. */
    private static final int STRIDE = 14;

    /** The sun's color. */
    private static final Vector3f SUN_COLOR = new Vector3f(1, 0.97f, 0.9f);

    /** Which way the sun shines: from high up, in front and to the side of the character. */
    private static final Vector3f SUN_DIRECTION = new Vector3f(-0.35f, -0.8f, -0.5f);

    /** The sun's strength, as the workbench uses. */
    private static final float SUN_INTENSITY = 3;

    /** The sky light's strength. */
    private static final float AMBIENT_INTENSITY = 0.3f;

    /** The floor's model ID. */
    private static final String FLOOR_ID = "characters/editor-floor";

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** Whether the stage is set. */
    private boolean entered;

    /** The renderer before entering. */
    private RenderConfig previousPipeline;

    /** The sun before entering. */
    private DirectionalLight previousSun;

    /** The sky light before entering. */
    private AmbientLight previousAmbient;

    /** Where the camera was before entering. */
    private final Vector3d previousPosition = new Vector3d();

    /** How the camera was turned before entering. */
    private final Vector2f previousRotation = new Vector2f();

    /** The floor, while entered. */
    private Model floor;

    /** The floor's material, while entered. */
    private Material floorMaterial;

    /** The floor's instance, while entered. */
    private InstanceHandle floorInstance;

    /**
     * Set up a stage; nothing changes until it is entered.
     *
     * @param graphics The plugin's graphics context.
     */
    public CharacterStage(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
    }

    /**
     * Where characters stand: the middle of the floor.
     *
     * @return The point. Don't change it.
     */
    public static Vector3dc origin() {
        return ORIGIN;
    }

    /** Switch the renderer to the stage, remembering what to put back. */
    public void enter() {
        if (entered) {
            return;
        }
        entered = true;
        final Scene scene = GraphicsManager.getScene();
        previousPipeline = GraphicsManager.getPipelineConfig();
        previousSun = scene.getSceneLights().getDirLight();
        previousAmbient = scene.getSceneLights().getAmbientLight();
        previousPosition.set(scene.getCamera().getPosition());
        previousRotation.set(scene.getCamera().getRotation());
        GraphicsManager.swapPipeline(RenderConfig.WORLD);
        scene.getSceneLights()
                .setDirLight(new DirectionalLight(SUN_COLOR, SUN_DIRECTION, SUN_INTENSITY));
        scene.getSceneLights()
                .setAmbientLight(new AmbientLight(new Vector3f(1, 1, 1), AMBIENT_INTENSITY));

        floorMaterial = new Material();
        floorMaterial.getBaseColor().set(0.55f, 0.55f, 0.53f, 1);
        floorMaterial.setRoughness(0.85f);
        scene.getMaterialCache().addMaterial(floorMaterial);
        scene.getMaterialCache().setDirty(true);
        final MeshData mesh = floorMesh();
        mesh.setMaterial(floorMaterial);
        floor = new Model(FLOOR_ID);
        floor.getMeshDataList().add(mesh);
        graphics.meshes().register(floor);
        scene.addModel(floor);
        floorInstance = graphics.instances().place(floor, ORIGIN, new Quaternionf(), 1);
    }

    /**
     * A flat square facing up, with normals, tangents and texture coordinates.
     *
     * @return The mesh.
     */
    private static MeshData floorMesh() {
        final float[] v = new float[4 * STRIDE];
        final float[][] corners = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
        int at = 0;
        for (float[] corner : corners) {
            // Position, normal up, tangent along x, bitangent up the texture, coordinates
            final float[] vertex = {
                corner[0] * FLOOR_HALF,
                0,
                corner[1] * FLOOR_HALF,
                0,
                1,
                0,
                1,
                0,
                0,
                0,
                0,
                -1,
                (corner[0] + 1) / 2,
                (corner[1] + 1) / 2
            };
            System.arraycopy(vertex, 0, v, at, STRIDE);
            at += STRIDE;
        }
        // Counter-clockwise seen from above
        final int[] indices = {0, 1, 2, 0, 2, 3};
        return new MeshData(
                new Vector3f(-FLOOR_HALF, 0, -FLOOR_HALF),
                new Vector3f(FLOOR_HALF, 0, FLOOR_HALF),
                4,
                v,
                indices,
                0,
                new byte[0]);
    }

    /** Take the floor out and put the renderer, lights and camera back as they were. */
    public void leave() {
        if (!entered) {
            return;
        }
        entered = false;
        final Scene scene = GraphicsManager.getScene();
        if (scene == null || graphics.isClosed()) {
            return;
        }
        graphics.instances().remove(floorInstance);
        scene.removeModel(FLOOR_ID);
        scene.getMaterialCache().removeMaterial(floorMaterial);
        scene.getMaterialCache().setDirty(true);
        floorInstance = null;
        floor = null;
        floorMaterial = null;
        scene.getSceneLights().setDirLight(previousSun);
        scene.getSceneLights().setAmbientLight(previousAmbient);
        scene.getCamera().setPosition(previousPosition.x, previousPosition.y, previousPosition.z);
        scene.getCamera().setRotation(previousRotation.x, previousRotation.y);
        GraphicsManager.swapPipeline(previousPipeline);
    }
}
