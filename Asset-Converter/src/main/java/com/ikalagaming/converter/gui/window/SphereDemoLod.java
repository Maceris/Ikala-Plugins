package com.ikalagaming.converter.gui.window;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.converter.scene.SceneLod;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.InstanceHandle;
import com.ikalagaming.graphics.Instances;
import com.ikalagaming.graphics.LodHandle;
import com.ikalagaming.graphics.LodInstanceHandle;
import com.ikalagaming.graphics.Lods;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.lod.DagBuilder;
import com.ikalagaming.graphics.lod.LodDag;
import com.ikalagaming.graphics.lod.LodMesh;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.FrameTimings;
import com.ikalagaming.graphics.vulkan.LodRenderer;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The sphere demo's window, with the way back to the menu and a level of detail switch: draws its
 * opaque balls as instances of one level of detail model of the shader ball, each with its own
 * material, instead of whole meshes, and can scatter rows of white copies behind them to see what
 * that saves. Translucent balls stay meshes, since level of detail draws only opaque surfaces.
 */
@Slf4j
final class SphereDemoLod {

    /** The window's name. */
    private static final String WINDOW = "Sphere demo";

    /** The startup property that turns level of detail on as soon as the demo opens. */
    private static final String LOD_PROPERTY = "ikala.test.sphereLod";

    /** The startup property that sets the error threshold to start with, in pixels. */
    private static final String ERROR_PROPERTY = "ikala.test.sphereError";

    /** The startup property that sets how many rows of copies to scatter. */
    private static final String ROWS_PROPERTY = "ikala.test.sphereRows";

    /** The balls' scale, from the model's units. */
    private static final float SCALE = 0.003f;

    /** Balls in a row of copies. */
    private static final int ROW_LENGTH = 11;

    /** The most rows of copies to choose. */
    private static final int MAX_ROWS = 200;

    /** Where the rows of copies start along z, behind the demo's own rows. */
    private static final int ROWS_START = -3;

    /** The error threshold to start with, in pixels. */
    private static final float DEFAULT_THRESHOLD = 1;

    /** The largest error threshold to choose, in pixels. */
    private static final float MAX_THRESHOLD = 16;

    /** The window's corner. */
    private static final float MARGIN = 25;

    /** Nanoseconds in a second. */
    private static final double NANOS_PER_SECOND = 1e9;

    /** How often the frame time is logged. */
    private static final long LOG_INTERVAL_NANOS = 5_000_000_000L;

    /** One ball, drawn either way. */
    private static final class Ball {
        /** Where it is. */
        final Vector3d position;

        /** What it is drawn with. */
        final Material material;

        /** Whether level of detail can draw it. */
        final boolean opaque;

        /** Its mesh instance, while it is drawn that way. */
        InstanceHandle plain;

        /** Its level of detail instance, while it is drawn that way. */
        LodInstanceHandle lod;

        /**
         * Keep a ball.
         *
         * @param position Where it is.
         * @param material What it is drawn with.
         */
        Ball(@NonNull Vector3d position, @NonNull Material material) {
            this.position = position;
            this.material = material;
            this.opaque = material.getTransparency() == Material.Transparency.OPAQUE;
        }
    }

    /** The converter's graphics context. */
    private final GraphicsContext graphics;

    /** The shader ball. */
    private final Model ball;

    /** The ball's own material, which instances draw with theirs. */
    private final Material ballMaterial;

    /** The demo's balls. */
    private final List<Ball> balls = new ArrayList<>();

    /** The copies scattered behind them. */
    private final List<Ball> copies = new ArrayList<>();

    /** What copies are drawn with. */
    private final Material copyMaterial = new Material();

    /** Whether the balls are drawn through level of detail. */
    private final IkBoolean useLod = new IkBoolean(false);

    /** Rows of copies. */
    private final int[] rows = {0};

    /** The error threshold. */
    private final float[] threshold = {
        Float.parseFloat(System.getProperty(ERROR_PROPERTY, String.valueOf(DEFAULT_THRESHOLD)))
    };

    /** The model, once built. Render thread. */
    private LodHandle model;

    /** Whether the model is being built. Render thread. */
    private boolean building;

    /** When the build started. */
    private long buildStarted;

    /** Whether the balls are drawn through level of detail now. Render thread. */
    private boolean drawnLod;

    /** When the frame time was last logged. Render thread. */
    private long lastLog = System.nanoTime();

    /** Takes out the rest of the demo and shows the menu again, once the balls are gone. */
    private Runnable back = () -> {};

    /** Whether the demo was left. Render thread. */
    private boolean closed;

    /**
     * Set up the switch.
     *
     * @param ball The shader ball.
     * @param ballMaterial Its own material.
     */
    SphereDemoLod(@NonNull Model ball, @NonNull Material ballMaterial) {
        this.graphics = GraphicsManager.forPlugin(ConverterPlugin.PLUGIN_NAME);
        this.ball = ball;
        this.ballMaterial = ballMaterial;
        copyMaterial.getBaseColor().set(0.9f, 0.9f, 0.9f, 1.0f);
        copyMaterial.setRoughness(0.5f);
        GraphicsManager.getScene().getMaterialCache().addMaterial(copyMaterial);
    }

    /**
     * Keep one of the demo's balls, placed as a mesh instance.
     *
     * @param position Where it is.
     * @param material What it is drawn with.
     * @param plain Its mesh instance.
     */
    void add(
            @NonNull Vector3d position, @NonNull Material material, @NonNull InstanceHandle plain) {
        final Ball kept = new Ball(position, material);
        kept.plain = plain;
        balls.add(kept);
    }

    /**
     * Show the window, turning level of detail on and scattering copies if asked at startup.
     *
     * @param back Takes out the rest of the demo and shows the menu again, once the balls are gone.
     */
    void open(@NonNull Runnable back) {
        this.back = back;
        GraphicsManager.getWindowManager().addWindow(graphics, WINDOW, new DemoWindow());
        GraphicsManager.getWindowManager().show(WINDOW);
        rows[0] = Math.clamp(Integer.getInteger(ROWS_PROPERTY, 0), 0, MAX_ROWS);
        scatter();
        if (Boolean.getBoolean(LOD_PROPERTY)) {
            useLod.set(true);
        }
    }

    /** Put the copies to match the rows asked for. Render thread. */
    private void scatter() {
        final int wanted = rows[0] * ROW_LENGTH;
        while (copies.size() > wanted) {
            hide(copies.removeLast());
        }
        while (copies.size() < wanted) {
            final int i = copies.size();
            final Ball copy =
                    new Ball(
                            new Vector3d(i % ROW_LENGTH, 0, ROWS_START - i / ROW_LENGTH),
                            copyMaterial);
            copies.add(copy);
            show(copy, drawnLod);
        }
    }

    /**
     * Draw a ball one way or the other.
     *
     * @param target The ball.
     * @param lod Whether through level of detail.
     */
    private void show(@NonNull Ball target, boolean lod) {
        hide(target);
        if (lod && target.opaque) {
            final Lods lods = graphics.lods();
            target.lod = lods.place(model, target.position, new Quaternionf(), SCALE);
            lods.setMaterial(target.lod, ballMaterial, target.material);
        } else {
            final Instances instances = graphics.instances();
            target.plain = instances.place(ball, target.position, new Quaternionf(), SCALE);
            instances.setMaterial(target.plain, 0, target.material);
        }
    }

    /**
     * Stop drawing a ball.
     *
     * @param target The ball.
     */
    private void hide(@NonNull Ball target) {
        if (target.plain != null) {
            graphics.instances().remove(target.plain);
            target.plain = null;
        }
        if (target.lod != null) {
            graphics.lods().remove(target.lod);
            target.lod = null;
        }
    }

    /** Build the ball's model in the background, then draw through it. Render thread. */
    private void build() {
        building = true;
        buildStarted = System.nanoTime();
        final MaterialCache cache = GraphicsManager.getScene().getMaterialCache();
        final List<MeshData> meshes = List.copyOf(ball.getMeshDataList());
        CompletableFuture.supplyAsync(
                        () -> {
                            final LodMesh mesh =
                                    SceneLod.ofModel(meshes, cache::getMaterialIndex, true);
                            final DagBuilder.Options defaults = DagBuilder.Options.defaults();
                            // No baking or blending, which would fix one look for every instance;
                            // coverage volumes name their materials, so instances' remaps reach
                            // them
                            return DagBuilder.build(
                                    List.of(mesh),
                                    new DagBuilder.Options(
                                                    defaults.virtualEdgeFactor(),
                                                    defaults.lockOpenBorder(),
                                                    defaults.threads(),
                                                    false,
                                                    defaults.visibilityReach(),
                                                    defaults.mergePlanar(),
                                                    null)
                                            .withVolumes(true));
                        })
                .whenComplete(
                        (dag, failure) ->
                                graphics.ui()
                                        .post(
                                                () -> {
                                                    if (!graphics.isClosed()) {
                                                        built(dag, failure);
                                                    }
                                                }));
    }

    /**
     * Take the built model. Render thread.
     *
     * @param dag The model, or null if the build failed.
     * @param failure Why it failed, or null.
     */
    private void built(LodDag dag, Throwable failure) {
        building = false;
        if (closed) {
            return;
        }
        if (failure != null || dag == null) {
            log.warn("Could not build the shader ball's level of detail", failure);
            useLod.set(false);
            return;
        }
        log.info(
                "Built the shader ball's level of detail in {} ms: {} clusters",
                Math.round((System.nanoTime() - buildStarted) / 1e6),
                dag.clusters().size());
        model = graphics.lods().addModel(dag);
        graphics.lods().setThreshold(model, threshold[0]);
    }

    /** Draw every ball the way the switch says, once the model is there. Render thread. */
    private void update() {
        final boolean wanted = useLod.get();
        if (wanted && model == null) {
            if (!building) {
                build();
            }
            return;
        }
        if (wanted != drawnLod) {
            drawnLod = wanted;
            for (Ball each : balls) {
                show(each, wanted);
            }
            for (Ball each : copies) {
                show(each, wanted);
            }
        }
    }

    /**
     * The GPU's time a frame, over the last few seconds.
     *
     * @return The timing, or null if there is none yet.
     */
    private static FrameTimings.Timing gpuTotal() {
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().frameTimings == null) {
            return null;
        }
        for (FrameTimings.Timing timing : renderer.getState().frameTimings.snapshot()) {
            if (FrameTimings.GPU_TOTAL.equals(timing.name())) {
                return timing;
            }
        }
        return null;
    }

    /**
     * Take out every ball, the model and the materials, then go back to the menu. Render thread.
     */
    private void close() {
        if (closed) {
            return;
        }
        closed = true;
        balls.forEach(this::hide);
        copies.forEach(this::hide);
        if (model != null) {
            graphics.lods().remove(model);
            model = null;
        }
        final MaterialCache cache = GraphicsManager.getScene().getMaterialCache();
        for (Ball each : balls) {
            cache.removeMaterial(each.material);
        }
        cache.removeMaterial(copyMaterial);
        cache.removeMaterial(ballMaterial);
        cache.setDirty(true);
        balls.clear();
        copies.clear();
        GraphicsManager.getWindowManager().removeWindow(WINDOW);
        back.run();
    }

    /** How many triangles the balls draw as meshes. */
    private long meshTriangles() {
        long triangles = 0;
        for (MeshData mesh : ball.getMeshDataList()) {
            triangles += mesh.getIndices().length / 3;
        }
        return triangles * (balls.size() + copies.size());
    }

    /** The demo's window. */
    private final class DemoWindow extends GuiWindow {
        /** Set up the window. */
        DemoWindow() {
            super(WINDOW, WindowFlags.ALWAYS_AUTO_RESIZE | WindowFlags.NO_SAVED_SETTINGS);
        }

        @Override
        public void draw(final int width, final int height) {
            update();
            setNextWindowPosInMainWindow(MARGIN, MARGIN, Condition.ONCE);
            IkGui.begin(title, windowOpen, windowFlags);
            final boolean leave = IkGui.button("Back to the menu") || !windowOpen.get();
            IkGui.checkbox("Level of detail", useLod);
            if (IkGui.sliderInt("Rows of copies", rows, 0, MAX_ROWS)) {
                scatter();
            }
            if (IkGui.sliderFloat("Error (pixels)", threshold, 0.25f, MAX_THRESHOLD)
                    && model != null) {
                graphics.lods().setThreshold(model, threshold[0]);
            }
            final int count = balls.size() + copies.size();
            if (building) {
                IkGui.text(
                        String.format(
                                "Building the ball's level of detail... %.0f s",
                                (System.nanoTime() - buildStarted) / NANOS_PER_SECOND));
            } else if (drawnLod) {
                final LodRenderer.Stats stats = graphics.lods().stats();
                IkGui.text(
                        String.format(
                                "%,d balls: %,d triangles in %,d clusters, %,d hidden, instead"
                                        + " of %,d.",
                                count,
                                stats.selectedTriangles(),
                                stats.selectedClusters(),
                                stats.occluded(),
                                meshTriangles()));
            } else {
                IkGui.text(String.format("%,d balls: %,d triangles.", count, meshTriangles()));
            }
            final FrameTimings.Timing gpu = gpuTotal();
            if (gpu != null) {
                IkGui.text(
                        String.format(
                                "GPU: %.2f ms a frame, %.2f ms at the 95th percentile.",
                                gpu.average(), gpu.p95()));
                if (System.nanoTime() - lastLog >= LOG_INTERVAL_NANOS) {
                    lastLog = System.nanoTime();
                    log.info(
                            "Sphere demo: {} balls, level of detail {}, {} triangles, GPU {} ms"
                                    + " (p95 {} ms)",
                            count,
                            drawnLod ? "on" : "off",
                            drawnLod
                                    ? graphics.lods().stats().selectedTriangles()
                                    : meshTriangles(),
                            String.format("%.2f", gpu.average()),
                            String.format("%.2f", gpu.p95()));
                }
            }
            IkGui.end();
            if (leave) {
                // After the window ends, since leaving removes it
                close();
            }
        }

        @Override
        public boolean handleGuiInput(@NonNull Scene scene) {
            return false;
        }
    }
}
