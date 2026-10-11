package com.ikalagaming.converter.gui.window;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.converter.ModelConverter;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.WindowManager;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.lod.LodMesh;
import com.ikalagaming.graphics.lod.LodPanel;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The level of detail prototype for single meshes: loads a model, builds its cluster DAG, and shows
 * the cut through a {@link LodPanel}. The model is scaled to a fixed size, so the camera and its
 * speed suit any model; the cut is chosen in pixels, so the scale doesn't change it. Each of the
 * model's meshes keeps its own color, without textures.
 *
 * <p>Opening hides every surface and turns on the scene with a sun, with a window holding the panel
 * and the way back to the menu. Render thread only, except {@link #stop}.
 */
@Slf4j
public final class LodPrototype {

    /** The tool's ID. */
    public static final String TOOL_ID = "converter/lod-prototype";

    /** The tool's label, in menus. */
    public static final String LABEL = "LOD prototype";

    /** The model built from, in the plugin's data folder. */
    private static final String MODEL = "models/shader_ball.obj";

    /** The window shown with the LOD. */
    private static final String WINDOW = "LOD prototype";

    /** The size the model is scaled to, along its longest side. */
    private static final double SIZE = 16;

    /** How far the camera stands back from the model, as a multiple of its size. */
    private static final double CAMERA_DISTANCE = 2;

    /** How high the camera stands above the model's middle, as a multiple of its size. */
    private static final double CAMERA_HEIGHT = 0.6;

    /**
     * How far left of the model the camera stands, as a multiple of its size, so the model isn't
     * behind the window.
     */
    private static final double CAMERA_LEFT = 0.6;

    /**
     * The color of a mesh whose material was textured, since textures are dropped, or white, which
     * hides the shading.
     */
    private static final Vector3f CLAY = new Vector3f(0.75f, 0.45f, 0.35f);

    /** A color channel at least this bright counts as white. */
    private static final float WHITE = 0.95f;

    /** The sun's color. */
    private static final Vector3f SUN_COLOR = new Vector3f(1, 0.97f, 0.9f);

    /** The sun's direction. */
    private static final Vector3f SUN_DIRECTION = new Vector3f(0.4f, -0.8f, 0.45f);

    /** The sun's intensity. */
    private static final float SUN_INTENSITY = 3;

    /** The ambient light's intensity. */
    private static final float AMBIENT_INTENSITY = 0.3f;

    /** How rough the model's materials are. */
    private static final float ROUGHNESS = 0.5f;

    /** How far the window starts from the left edge, in pixels. */
    private static final float MARGIN = 16;

    /** How far the window starts from the top, below the toolbar, in pixels. */
    private static final float TOP = 40;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Floats per vertex in a mesh. */
    private static final int STRIDE = MeshData.VERTEX_SIZE_IN_FLOATS;

    /** Vertices in a triangle. */
    private static final int CORNERS = 3;

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** Materials added to the scene, one per mesh, to remove again. */
    private final List<Material> materials = new CopyOnWriteArrayList<>();

    /** The panel, while open. */
    private LodPanel panel;

    /** Whether the tool is open. Set on the render thread, read from any. */
    private volatile boolean open;

    /** The pipeline before opening. */
    private int previousPipeline;

    /** The sun before opening. */
    private DirectionalLight previousSun;

    /** The ambient light before opening. */
    private AmbientLight previousAmbient;

    /**
     * Set up the tool; nothing is loaded until it is opened.
     *
     * @param graphics The plugin's graphics context.
     */
    public LodPrototype(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
    }

    /** Load the model, start building its DAG, and show it. Render thread. */
    public void open() {
        final Scene scene = GraphicsManager.getScene();
        if (open || scene == null) {
            return;
        }
        final Model model;
        try {
            // Its own cache, so the model's materials stay out of the scene
            model =
                    ModelConverter.loadModel(
                            new ModelConverter.ModelLoadRequest(
                                    "lod-prototype",
                                    ConverterPlugin.PLUGIN_NAME,
                                    MODEL,
                                    new MaterialCache(),
                                    false));
        } catch (RuntimeException e) {
            log.warn("Could not load {} for the LOD prototype", MODEL, e);
            return;
        }
        final List<MeshData> meshes = model.getMeshDataList();
        final int[] indices = new int[meshes.size()];
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < meshes.size(); ++i) {
            Material material = new Material();
            final Material loaded = meshes.get(i).getMaterial();
            final Vector4f color = loaded == null ? null : loaded.getBaseColor();
            if (loaded != null
                    && loaded.getTexture() == null
                    && Math.min(color.x, Math.min(color.y, color.z)) < WHITE) {
                material.getBaseColor().set(color.x, color.y, color.z, 1);
            } else {
                material.getBaseColor().set(CLAY.x, CLAY.y, CLAY.z, 1);
            }
            material.setRoughness(ROUGHNESS);
            scene.getMaterialCache().addMaterial(material);
            materials.add(material);
            indices[i] = scene.getMaterialCache().getMaterialIndex(material);
            names.add("mesh " + i);
        }
        scene.getMaterialCache().setDirty(true);
        open = true;
        previousPipeline = GraphicsManager.getPipelineConfig();
        previousSun = scene.getSceneLights().getDirLight();
        previousAmbient = scene.getSceneLights().getAmbientLight();
        // The debug stage draws the panel's bounds and locked edge overlays
        GraphicsManager.swapPipeline(
                RenderConfig.builder()
                        .withAnimation()
                        .withScene()
                        .withSkybox()
                        .withGui()
                        .withDebug()
                        .build());
        scene.getSceneLights()
                .setDirLight(new DirectionalLight(SUN_COLOR, SUN_DIRECTION, SUN_INTENSITY));
        scene.getSceneLights()
                .setAmbientLight(new AmbientLight(new Vector3f(1, 1, 1), AMBIENT_INTENSITY));
        final double scale = SIZE / longestSide(meshes);
        // Stand in front of the model, above it and to its left, facing -z and down
        final double back = SIZE * CAMERA_DISTANCE;
        final double up = SIZE * CAMERA_HEIGHT;
        scene.getCamera().setPosition(-SIZE * CAMERA_LEFT, up, back);
        scene.getCamera().setRotation((float) Math.atan2(up, back), 0);
        panel =
                new LodPanel(
                        graphics,
                        MODEL,
                        () -> centered(meshes, indices, scale),
                        index -> {
                            for (int i = 0; i < indices.length; ++i) {
                                if (indices[i] == index) {
                                    return names.get(i);
                                }
                            }
                            return "material " + index;
                        });
        GraphicsManager.getUiManager().setSurfacesHidden(true);
        final WindowManager windows = GraphicsManager.getWindowManager();
        windows.addWindow(graphics, WINDOW, new LodWindow());
        windows.show(WINDOW);
    }

    /**
     * The longest side of the meshes' bounds together.
     *
     * @param meshes The meshes.
     * @return The length, at least a tiny bit more than 0.
     */
    private static double longestSide(@NonNull List<MeshData> meshes) {
        final double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        final double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (MeshData mesh : meshes) {
            final Vector3f low = mesh.getAabbMin();
            final Vector3f high = mesh.getAabbMax();
            for (int axis = 0; axis < AXES; ++axis) {
                min[axis] = Math.min(min[axis], low.get(axis));
                max[axis] = Math.max(max[axis], high.get(axis));
            }
        }
        double longest = Double.MIN_NORMAL;
        for (int axis = 0; axis < AXES; ++axis) {
            longest = Math.max(longest, max[axis] - min[axis]);
        }
        return longest;
    }

    /**
     * Read the meshes, scaled, with their bounds' center at the origin. On the panel's build
     * thread.
     *
     * @param meshes The meshes.
     * @param materials Each mesh's material index.
     * @param scale How much to scale them by.
     * @return One welded mesh per model mesh.
     */
    private static List<LodMesh> centered(
            @NonNull List<MeshData> meshes, int @NonNull [] materials, double scale) {
        final float[] center = new float[AXES];
        final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (MeshData mesh : meshes) {
            for (int axis = 0; axis < AXES; ++axis) {
                min[axis] = Math.min(min[axis], mesh.getAabbMin().get(axis));
                max[axis] = Math.max(max[axis], mesh.getAabbMax().get(axis));
            }
        }
        for (int axis = 0; axis < AXES; ++axis) {
            center[axis] = (min[axis] + max[axis]) / 2;
        }
        List<LodMesh> out = new ArrayList<>();
        for (int m = 0; m < meshes.size(); ++m) {
            final MeshData mesh = meshes.get(m);
            LodMesh.Builder builder = LodMesh.builder(new double[AXES]);
            final float[] data = mesh.getVertexData();
            final int[] welded = new int[mesh.getVertexCount()];
            for (int v = 0; v < welded.length; ++v) {
                welded[v] =
                        builder.vertex(
                                (float) ((data[v * STRIDE] - center[0]) * scale),
                                (float) ((data[v * STRIDE + 1] - center[1]) * scale),
                                (float) ((data[v * STRIDE + 2] - center[2]) * scale));
            }
            final int[] indices = mesh.getIndices();
            for (int i = 0; i + 2 < indices.length; i += CORNERS) {
                builder.triangle(
                        welded[indices[i]],
                        welded[indices[i + 1]],
                        welded[indices[i + 2]],
                        materials[m]);
            }
            out.add(builder.build());
        }
        return out;
    }

    /** The window shown with the LOD, holding its panel and the way back. */
    private final class LodWindow extends GuiWindow {
        /** Set up the window. */
        LodWindow() {
            super(WINDOW, WindowFlags.ALWAYS_AUTO_RESIZE | WindowFlags.NO_SAVED_SETTINGS);
        }

        @Override
        public void draw(final int width, final int height) {
            IkGui.setNextWindowViewport(IkGui.getMainViewport().id);
            IkGui.setNextWindowPos(MARGIN, TOP, Condition.ONCE);
            IkGui.begin(title, windowOpen, windowFlags);
            final boolean back = IkGui.button("Back to the menu") || !windowOpen.get();
            IkGui.text("Hold the right mouse button to turn; W, A, S, D, Space and Shift to move.");
            if (panel != null) {
                panel.draw();
            }
            IkGui.end();
            if (back) {
                // After the window ends, since going back removes it
                back();
            }
        }

        @Override
        public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
            return false;
        }
    }

    /** Remove the LOD, put the scene back, and show the menu again. Render thread. */
    private void back() {
        if (!open) {
            return;
        }
        open = false;
        if (panel != null) {
            panel.close();
            panel = null;
        }
        removeMaterials();
        restoreScene(previousSun, previousAmbient, previousPipeline);
        GraphicsManager.getWindowManager().removeWindow(WINDOW);
        GraphicsManager.getUiManager().setSurfacesHidden(false);
    }

    /** Remove the model's materials from the scene. Safe from any thread; the cache locks. */
    private void removeMaterials() {
        final Scene scene = GraphicsManager.getScene();
        if (scene != null) {
            materials.forEach(scene.getMaterialCache()::removeMaterial);
            scene.getMaterialCache().setDirty(true);
        }
        materials.clear();
    }

    /**
     * Put back the lights and pipeline from before opening. Render thread.
     *
     * @param sun The sun.
     * @param ambient The ambient light.
     * @param pipeline The pipeline configuration.
     */
    private static void restoreScene(DirectionalLight sun, AmbientLight ambient, int pipeline) {
        final Scene scene = GraphicsManager.getScene();
        if (scene != null && sun != null) {
            scene.getSceneLights().setDirLight(sun);
            scene.getSceneLights().setAmbientLight(ambient);
            GraphicsManager.swapPipeline(pipeline);
        }
    }

    /**
     * Undo everything outside what the plugin's graphics context owns. Safe from any thread, so it
     * works while the plugin is disabled: surfaces show again and materials go at once, and the
     * panel's models and the scene are put back through the UI manager's own queue, which outlives
     * the plugin. The panel's meshes, instances and window are the context's, released with it.
     */
    public void stop() {
        if (!open) {
            return;
        }
        open = false;
        GraphicsManager.getUiManager().setSurfacesHidden(false);
        removeMaterials();
        final LodPanel closing = panel;
        panel = null;
        final DirectionalLight sun = previousSun;
        final AmbientLight ambient = previousAmbient;
        final int pipeline = previousPipeline;
        GraphicsManager.getUiManager()
                .post(
                        () -> {
                            if (closing != null) {
                                closing.close();
                            }
                            restoreScene(sun, ambient, pipeline);
                        });
    }
}
