package com.ikalagaming.factory.lab;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.graphics.CameraManager;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.SectionBaker;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
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
import org.joml.Vector3f;

import java.util.*;

/**
 * Builds a level of detail DAG from Lab chunks and shows it: each chunk is baked on the CPU with
 * the same placements and hidden faces the 3D preview composes, and its opaque triangles become one
 * section of input, so graphics never learns about chunks or the corpus format. Showing hides every
 * surface and turns on the scene, like looking at the preview, with a window holding the {@link
 * LodPanel} and the way back.
 *
 * <p>Render thread only, except {@link #stop}.
 */
final class LabLod implements AutoCloseable {

    /** The window shown while looking at the LOD. */
    private static final String WINDOW = "Worldgen Lab LOD";

    /** How far the camera stands back from the chunks, as a multiple of their size. */
    private static final double CAMERA_DISTANCE = 0.9;

    /** How high the camera stands above the chunks' middle, as a multiple of their size. */
    private static final double CAMERA_HEIGHT = 0.6;

    /** The sun's color. */
    private static final Vector3f SUN_COLOR = new Vector3f(1, 0.97f, 0.9f);

    /** The sun's direction. */
    private static final Vector3f SUN_DIRECTION = new Vector3f(0.4f, -0.8f, 0.45f);

    /** The sun's intensity. */
    private static final float SUN_INTENSITY = 3;

    /** The ambient light's intensity. */
    private static final float AMBIENT_INTENSITY = 0.3f;

    /** How far the window starts from the left edge, in pixels. */
    private static final float MARGIN = 16;

    /** How far the window starts from the top, below the toolbar, in pixels. */
    private static final float TOP = 40;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** Runs after going back to the Lab. */
    private final Runnable onBack;

    /** The panel, while showing. */
    private LodPanel panel;

    /** Whether the LOD is showing. Set on the render thread, read from any. */
    private volatile boolean showing;

    /** The pipeline before showing. */
    private int previousPipeline;

    /** The sun before showing. */
    private DirectionalLight previousSun;

    /** The ambient light before showing. */
    private AmbientLight previousAmbient;

    /**
     * Set up; nothing is built until {@link #show}.
     *
     * @param graphics The plugin's graphics context.
     * @param onBack Runs on the render thread after going back to the Lab.
     */
    LabLod(@NonNull GraphicsContext graphics, @NonNull Runnable onBack) {
        this.graphics = graphics;
        this.onBack = onBack;
    }

    /**
     * Whether the LOD is showing.
     *
     * @return True while showing.
     */
    boolean isShowing() {
        return showing;
    }

    /**
     * Build a DAG from chunks and show it, hiding the Lab.
     *
     * @param name What is being built, for the panel.
     * @param chunks The chunks; faces on their outer edge show.
     * @param meshes The block meshes, by block name. Blocks without one are skipped.
     */
    void show(
            @NonNull String name,
            @NonNull Map<ChunkPos, Chunk> chunks,
            @NonNull Map<String, BlockMeshes.Entry> meshes) {
        if (chunks.isEmpty()) {
            return;
        }
        final Scene scene = GraphicsManager.getScene();
        if (scene == null) {
            return;
        }
        if (showing) {
            closePanel();
        } else {
            showing = true;
            previousPipeline = GraphicsManager.getPipelineConfig();
            previousSun = scene.getSceneLights().getDirLight();
            previousAmbient = scene.getSceneLights().getAmbientLight();
        }
        // The debug stage draws the panel's bounds and locked edge overlays
        GraphicsManager.swapPipeline(
                previousPipeline
                        | RenderConfig.SCENE_ENABLED_MASK
                        | RenderConfig.GUI_ENABLED_MASK
                        | RenderConfig.DEBUG_ENABLED_MASK);
        scene.getSceneLights()
                .setDirLight(new DirectionalLight(SUN_COLOR, SUN_DIRECTION, SUN_INTENSITY));
        scene.getSceneLights()
                .setAmbientLight(new AmbientLight(new Vector3f(1, 1, 1), AMBIENT_INTENSITY));
        lookAt(scene, chunks.keySet());
        // Material indices are read here, on the render thread, for the build thread to use
        final MaterialCache cache = scene.getMaterialCache();
        final Map<String, int[]> materials = new HashMap<>();
        final Map<Integer, String> names = new HashMap<>();
        for (Map.Entry<String, BlockMeshes.Entry> entry : meshes.entrySet()) {
            final Material material = entry.getValue().material();
            final int[] byFace = new int[SectionBaker.FACE_KINDS];
            byFace[SectionBaker.SIDE] = cache.getMaterialIndex(material);
            byFace[SectionBaker.TOP] = cache.getMaterialIndex(material.forTop());
            byFace[SectionBaker.BOTTOM] = cache.getMaterialIndex(material.forBottom());
            materials.put(entry.getKey(), byFace);
            names.putIfAbsent(byFace[SectionBaker.SIDE], entry.getKey());
            names.putIfAbsent(byFace[SectionBaker.TOP], entry.getKey() + " top");
            names.putIfAbsent(byFace[SectionBaker.BOTTOM], entry.getKey() + " bottom");
        }
        // Copies in the same order, so the same chunks always build the same DAG
        final Map<ChunkPos, Chunk> box = new LinkedHashMap<>(chunks);
        final Map<String, BlockMeshes.Entry> blocks = new HashMap<>(meshes);
        panel =
                new LodPanel(
                        graphics,
                        name,
                        () -> bake(box, blocks, materials),
                        index -> names.getOrDefault(index, "material " + index));
        GraphicsManager.getUiManager().setSurfacesHidden(true);
        final WindowManager windows = GraphicsManager.getWindowManager();
        windows.addWindow(graphics, WINDOW, new LodWindow());
        windows.show(WINDOW);
    }

    /**
     * Stand south of the chunks and above them, facing north (-z) and down at their middle.
     *
     * @param scene The scene.
     * @param positions The chunks' positions.
     */
    private static void lookAt(@NonNull Scene scene, @NonNull Collection<ChunkPos> positions) {
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
        for (ChunkPos pos : positions) {
            final long[] corner = {pos.blockX(), pos.blockY(), pos.blockZ()};
            for (int axis = 0; axis < AXES; ++axis) {
                min[axis] = Math.min(min[axis], corner[axis]);
                max[axis] = Math.max(max[axis], corner[axis] + World.CHUNK_SIZE);
            }
        }
        long size = 0;
        for (int axis = 0; axis < AXES; ++axis) {
            size = Math.max(size, max[axis] - min[axis]);
        }
        final double back = size * CAMERA_DISTANCE;
        final double up = size * CAMERA_HEIGHT;
        scene.getCamera()
                .setPosition(
                        (min[0] + max[0]) / 2.0,
                        (min[1] + max[1]) / 2.0 + up,
                        (min[2] + max[2]) / 2.0 + back);
        scene.getCamera().setRotation((float) Math.atan2(up, back), 0);
    }

    /**
     * Bake every chunk's opaque blocks into a mesh for building. On the panel's build thread.
     *
     * @param chunks The chunks.
     * @param meshes The block meshes, by block name.
     * @param materials Each block's material indices by face kind (see {@link
     *     SectionBaker#bakeByFace}), by block name.
     * @return One mesh per chunk with opaque triangles, each with the chunk's origin.
     */
    static List<LodMesh> bake(
            @NonNull Map<ChunkPos, Chunk> chunks,
            @NonNull Map<String, BlockMeshes.Entry> meshes,
            @NonNull Map<String, int[]> materials) {
        final Map<BlockMeshes.Entry, int[]> materialOf = new HashMap<>();
        meshes.forEach((block, entry) -> materialOf.put(entry, materials.get(block)));
        List<LodMesh> out = new ArrayList<>();
        for (Map.Entry<ChunkPos, Chunk> entry : chunks.entrySet()) {
            final ChunkPos pos = entry.getKey();
            final LabPreview.Placed placed =
                    LabPreview.place(chunks, meshes, pos, entry.getValue());
            if (placed == null) {
                continue;
            }
            final int count = placed.blocks().length;
            final BakeSource[] sources = new BakeSource[count];
            final int[] blockMaterials = new int[count * SectionBaker.FACE_KINDS];
            for (int i = 0; i < count; ++i) {
                sources[i] = placed.blocks()[i].source();
                System.arraycopy(
                        materialOf.get(placed.blocks()[i]),
                        0,
                        blockMaterials,
                        i * SectionBaker.FACE_KINDS,
                        SectionBaker.FACE_KINDS);
            }
            final SectionBaker.Bucket opaque =
                    SectionBaker.bakeByFace(
                                    sources,
                                    blockMaterials,
                                    placed.packed(),
                                    new byte[count],
                                    placed.faceMasks(),
                                    new short[count])
                            .get(Material.Transparency.OPAQUE);
            if (opaque != null) {
                out.add(
                        LodMesh.fromBaked(
                                opaque, new double[] {pos.blockX(), pos.blockY(), pos.blockZ()}));
            }
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
            setNextWindowPosInMainWindow(MARGIN, TOP, Condition.ONCE);
            IkGui.begin(title, windowOpen, windowFlags);
            final boolean back = IkGui.button("Back to the Lab") || !windowOpen.get();
            IkGui.text(CameraManager.CONTROLS);
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

    /** Remove the LOD, put the scene back, and show the Lab again. Render thread. */
    private void back() {
        if (!showing) {
            return;
        }
        showing = false;
        closePanel();
        restoreScene(previousSun, previousAmbient, previousPipeline);
        GraphicsManager.getWindowManager().removeWindow(WINDOW);
        GraphicsManager.getUiManager().setSurfacesHidden(false);
        graphics.ui().setVisible(WorldgenLab.SURFACE_ID, true);
        onBack.run();
    }

    /** Close the panel, which removes its models and materials. Render thread. */
    private void closePanel() {
        if (panel != null) {
            panel.close();
            panel = null;
        }
    }

    /**
     * Put back the lights and pipeline from before showing. Render thread.
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
     * works while the plugin is disabled: surfaces show again at once, and the panel's models and
     * materials go and the scene is put back through the UI manager's own queue, which outlives the
     * plugin. The panel's meshes and instances are the context's, released with it.
     */
    void stop() {
        if (!showing) {
            return;
        }
        showing = false;
        GraphicsManager.getUiManager().setSurfacesHidden(false);
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

    /** Stop, and remove the window. Render thread. */
    @Override
    public void close() {
        stop();
        if (!graphics.isClosed()) {
            GraphicsManager.getWindowManager().removeWindow(WINDOW);
        }
    }
}
