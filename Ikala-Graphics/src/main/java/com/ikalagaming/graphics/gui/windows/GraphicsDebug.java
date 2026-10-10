package com.ikalagaming.graphics.gui.windows;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.benchmark.GateBenchmark;
import com.ikalagaming.graphics.benchmark.GateScene;
import com.ikalagaming.graphics.benchmark.SyntheticTerrain;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.component.Checkbox;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.component.Slider;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Alignment;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.vulkan.ClusterMath;
import com.ikalagaming.graphics.vulkan.FilterView;
import com.ikalagaming.graphics.vulkan.FrameTimings;
import com.ikalagaming.graphics.vulkan.InstanceRegistry;
import com.ikalagaming.graphics.vulkan.VulkanInstance;
import com.ikalagaming.graphics.vulkan.stages.InstanceDrawUpdate;
import com.ikalagaming.graphics.vulkan.stages.LightCull;

import lombok.NonNull;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

public class GraphicsDebug extends GuiWindow {

    public static final String WINDOW_NAME = "Graphics Debug";

    private final Checkbox fogEnabled;
    private final Checkbox wireframeEnabled;

    private final Checkbox showPointLights;
    private final Checkbox showSpotLights;
    private final Checkbox showDirectionalLight;
    private final Checkbox showEntityBounds;
    private final Checkbox showShadowCascades;
    private final Checkbox showNormals;
    private final Checkbox showTangents;
    private final Slider normalLength;
    private final Checkbox freezeObserver;
    private final Checkbox disableCulling;
    private final Checkbox disableOcclusion;
    private final Checkbox showUiShowcase;
    private final Slider fogDensity;
    private final Slider directionalLightX;
    private final Slider directionalLightY;
    private final Slider directionalLightZ;
    private final Slider directionalLightIntensity;

    /** The display names of the filter views, in ordinal order. */
    private final String[] filterViewNames;

    /** The selected filter view's ordinal. */
    private final IkInt filterView;

    /** The benchmark recipe picked, by ordinal. */
    private final IkInt benchmarkRecipe = new IkInt(SyntheticTerrain.Recipe.TERRAIN.ordinal());

    /** The benchmark view distance picked, in sections. */
    private final int[] benchmarkRadius = {12};

    /** How many torches the benchmark scatters over its world. */
    private final int[] benchmarkLights = {0};

    /** The most torches the benchmark slider offers. */
    private static final int MAX_BENCHMARK_LIGHTS = 8000;

    /** Whether the benchmark leaves its world in place when it finishes. */
    private final IkBoolean benchmarkKeepWorld = new IkBoolean(false);

    /** The benchmark recipes' names, in ordinal order. */
    private static final String[] RECIPE_NAMES =
            Arrays.stream(SyntheticTerrain.Recipe.values()).map(Enum::name).toArray(String[]::new);

    /** Whether the filter view selection changed this frame. */
    private boolean filterViewChanged;

    /**
     * Whether we added the filter stage to show a g-buffer view, so we can take it back out when
     * going back to the default.
     */
    private boolean addedFilterStage;

    public GraphicsDebug() {
        super(WINDOW_NAME, WindowFlags.NONE);
        setScale(0.34f, 0.45f);
        setDisplacement(0.01f, 0.05f);
        setAlignment(Alignment.NORTH_EAST);

        fogEnabled = new Checkbox("Fog enabled", false);
        wireframeEnabled = new Checkbox("Wireframe enabled", false);
        showPointLights = new Checkbox("Point lights", false);
        showSpotLights = new Checkbox("Spot lights", false);
        showDirectionalLight = new Checkbox("Directional light", false);
        showEntityBounds = new Checkbox("Instance bounds", false);
        showShadowCascades = new Checkbox("Shadow cascades", false);
        showNormals = new Checkbox("Normals", false);
        showTangents = new Checkbox("Tangents", false);
        normalLength = new Slider("Normal length", 0.1f, 0.01f, 1f);
        freezeObserver = new Checkbox("Freeze observer", false);
        disableCulling = new Checkbox("Draw everything (no culling)", false);
        disableOcclusion = new Checkbox("Draw hidden things (no occlusion culling)", false);
        showUiShowcase = new Checkbox("UI showcase", false);
        fogDensity = new Slider("Fog Density", 0, 0, 1);
        directionalLightX = new Slider("Directional Light X", 0, -1, 1);
        directionalLightY = new Slider("Directional Light Y", 0, -1, 1);
        directionalLightZ = new Slider("Directional Light Z", 0, -1, 1);
        directionalLightIntensity = new Slider("Directional Light Intensity", 0, 0, 4f);
        FilterView[] views = FilterView.values();
        filterViewNames = new String[views.length];
        for (int i = 0; i < views.length; ++i) {
            filterViewNames[i] = views[i].getDisplayName();
        }
        filterView = new IkInt(FilterView.DEFAULT.ordinal());

        addChild(fogEnabled);
        addChild(fogDensity);
        addChild(wireframeEnabled);
        addChild(showPointLights);
        addChild(showSpotLights);
        addChild(showDirectionalLight);
        addChild(showEntityBounds);
        addChild(showShadowCascades);
        addChild(showNormals);
        addChild(showTangents);
        addChild(normalLength);
        addChild(freezeObserver);
        addChild(disableCulling);
        addChild(disableOcclusion);
        addChild(showUiShowcase);
        addChild(directionalLightX);
        addChild(directionalLightY);
        addChild(directionalLightZ);
        addChild(directionalLightIntensity);
    }

    @Override
    public void draw(int width, int height) {
        IkGui.setNextWindowViewport(IkGui.getMainViewport().id);
        IkGui.setNextWindowPos(
                getActualDisplaceX() * width, getActualDisplaceY() * height, Condition.ONCE);
        IkGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, Condition.ONCE);
        IkGui.begin(title, windowOpen, windowFlags);

        if (isVisible()) {
            recalculate();

            Scene scene = GraphicsManager.getScene();

            if (IkGui.collapsingHeader("Stats")) {
                IkGui.text(String.format("FPS: %d", GraphicsManager.getLastFPS()));
                IkGui.text(
                        String.format(
                                "Point and spot lights: %,d",
                                scene.getLightRegistry().getLightCount()));
                IkGui.text(
                        String.format(
                                "Materials loaded: %,d",
                                GraphicsManager.getScene().getMaterialCache().getMaterialCount()));

                long triangles = 0;
                int meshes = 0;
                int instances = 0;
                final VulkanInstance renderer = GraphicsManager.getRenderInstance();
                final InstanceRegistry registry =
                        renderer == null || renderer.getState().instances == null
                                ? null
                                : renderer.getState().instances.getRegistry();
                for (Model model : scene.getModelMap().values()) {
                    meshes += model.getMeshDataList().size();
                    int modelInstances = registry == null ? 0 : registry.countOf(model);
                    instances += modelInstances;
                    int meshTriangles = 0;
                    for (MeshData mesh : model.getMeshDataList()) {
                        meshTriangles += mesh.getIndices().length / 3;
                    }
                    triangles += (long) meshTriangles * modelInstances;
                }

                IkGui.text(String.format("Models loaded: %,d", scene.getModelMap().size()));
                IkGui.text(String.format("Meshes loaded: %,d", meshes));
                IkGui.text(String.format("Instances: %,d", instances));
                final int[] drawn =
                        renderer == null || renderer.getState().instances == null
                                ? new int[0]
                                : renderer.getState().instances.getDrawnCounts();
                if (drawn.length == InstanceDrawUpdate.COUNTER_COUNT) {
                    StringBuilder shadows = new StringBuilder();
                    for (int i = InstanceDrawUpdate.LIST_FIRST_CASCADE;
                            i < InstanceDrawUpdate.LIST_COUNT;
                            ++i) {
                        shadows.append(i == InstanceDrawUpdate.LIST_FIRST_CASCADE ? "" : " / ")
                                .append(String.format("%,d", drawn[i]));
                    }
                    // Counted per mesh, so a model with several meshes counts more than once
                    final int early = drawn[InstanceDrawUpdate.LIST_SCENE_EARLY];
                    final int late = drawn[InstanceDrawUpdate.LIST_SCENE_LATE];
                    IkGui.text(
                            String.format(
                                    "Meshes drawn: %,d (%,d early, %,d late)",
                                    early + late, early, late));
                    IkGui.text(
                            String.format(
                                    "Meshes hidden by occlusion: %,d",
                                    drawn[InstanceDrawUpdate.COUNTER_OCCLUDED]));
                    IkGui.text("Meshes in shadow cascades: " + shadows);
                }
                IkGui.text(String.format("Triangles: %,d", triangles));
                if (renderer != null && renderer.getState().sections != null) {
                    final var sections = renderer.getState().sections.getStats();
                    IkGui.text(
                            String.format(
                                    "Sections: %,d (%,d drawn, %,d baking), %,d triangles",
                                    sections.sections(),
                                    sections.resident(),
                                    sections.baking(),
                                    sections.triangles()));
                }
            }

            if (IkGui.collapsingHeader("Frame timing")) {
                drawTimings();
            }

            if (IkGui.collapsingHeader("Lights")) {
                drawLights(scene);
            }

            if (IkGui.collapsingHeader("Benchmark")) {
                drawBenchmark();
            }

            if (IkGui.collapsingHeader("Render Config Info")) {
                int config = GraphicsManager.getPipelineConfig();
                final String flagString = "%12s - %s";
                IkGui.text(String.format(flagString, "Error", RenderConfig.hasError(config)));
                IkGui.text(
                        String.format(
                                flagString, "Animation", RenderConfig.hasAnimationStage(config)));
                IkGui.text(
                        String.format(flagString, "Shadow", RenderConfig.hasShadowStage(config)));
                IkGui.text(String.format(flagString, "Scene", RenderConfig.hasSceneStage(config)));
                IkGui.text(
                        String.format(flagString, "Skybox", RenderConfig.hasSkyboxStage(config)));
                IkGui.text(
                        String.format(flagString, "Filter", RenderConfig.hasFilterStage(config)));
                IkGui.text(String.format(flagString, "Gui", RenderConfig.hasGuiStage(config)));
                IkGui.text(
                        String.format(
                                flagString,
                                "Transparency",
                                RenderConfig.hasTransparencyPass(config)));
                IkGui.text(
                        String.format(
                                flagString, "Wireframe", RenderConfig.sceneIsWireframe(config)));
                IkGui.text(String.format(flagString, "Debug", RenderConfig.hasDebugStage(config)));
            }

            if (IkGui.collapsingHeader("Camera")) {
                var camera = GraphicsManager.getCameraManager().getCamera();
                IkGui.text(
                        String.format(
                                "Camera position: x:%.2f, y:%.2f, z:%.2f",
                                camera.getPosition().x(),
                                camera.getPosition().y(),
                                camera.getPosition().z()));

                IkGui.text(
                        String.format(
                                "Camera rotation: x:%.2f, y:%.2f",
                                camera.getRotation().x(), camera.getRotation().y()));
            }

            if (IkGui.collapsingHeader("Scene Controls")) {
                boolean renderingScene =
                        RenderConfig.hasSceneStage(GraphicsManager.getPipelineConfig());

                if (!renderingScene) {
                    IkGui.beginDisabled();
                    IkGui.text("(Not rendering scene, controls disabled)");
                }
                fogEnabled.draw(width, height);
                fogDensity.draw(width, height);
                wireframeEnabled.draw(width, height);
                directionalLightX.draw(width, height);
                directionalLightY.draw(width, height);
                directionalLightZ.draw(width, height);
                directionalLightIntensity.draw(width, height);
                if (!renderingScene) {
                    IkGui.endDisabled();
                }
            }

            if (IkGui.collapsingHeader("Debug Visualization")) {
                showPointLights.draw(width, height);
                showSpotLights.draw(width, height);
                showDirectionalLight.draw(width, height);
                showEntityBounds.draw(width, height);
                showShadowCascades.draw(width, height);
                showNormals.draw(width, height);
                showTangents.draw(width, height);
                normalLength.draw(width, height);
                freezeObserver.draw(width, height);
                disableCulling.draw(width, height);
                disableOcclusion.draw(width, height);
                IkGui.textWrapped(
                        "Culling, level of detail and streaming use the observer, so freeze it to"
                                + " inspect them from outside.");
                if (IkGui.combo("Filter", filterView, filterViewNames)) {
                    filterViewChanged = true;
                }
                IkGui.textWrapped(
                        "The filter can show a g-buffer texture instead of the lit scene. The"
                                + " g-buffer is only drawn while rendering the scene.");
            }

            if (IkGui.collapsingHeader("Retained UI")) {
                showUiShowcase.draw(width, height);
            }
        }

        IkGui.end();
    }

    /**
     * Apply changes to the debug visualization checkboxes. Turning any visualizer on adds the debug
     * stage to the pipeline.
     *
     * @param scene The scene.
     */
    private void handleDebugInput(@NonNull Scene scene) {
        DebugVisualizers visualizers = scene.getDebugVisualizers();
        boolean changed = false;
        if (showPointLights.checkResult()) {
            visualizers.setPointLights(showPointLights.getState());
            changed = true;
        }
        if (showSpotLights.checkResult()) {
            visualizers.setSpotLights(showSpotLights.getState());
            changed = true;
        }
        if (showDirectionalLight.checkResult()) {
            visualizers.setDirectionalLight(showDirectionalLight.getState());
            changed = true;
        }
        if (showEntityBounds.checkResult()) {
            visualizers.setEntityBounds(showEntityBounds.getState());
            changed = true;
        }
        if (showShadowCascades.checkResult()) {
            visualizers.setShadowCascades(showShadowCascades.getState());
            changed = true;
        }
        if (showNormals.checkResult()) {
            visualizers.setNormals(showNormals.getState());
            changed = true;
        }
        if (showTangents.checkResult()) {
            visualizers.setTangents(showTangents.getState());
            changed = true;
        }
        if (normalLength.checkResult()) {
            visualizers.setNormalLength(normalLength.getValue());
        }
        if (freezeObserver.checkResult()) {
            if (freezeObserver.getState()) {
                scene.freezeObserver();
            } else {
                scene.unfreezeObserver();
            }
            visualizers.setObserverFrustum(freezeObserver.getState());
            changed = true;
        }
        if (disableCulling.checkResult()) {
            visualizers.setCullingDisabled(disableCulling.getState());
        }
        if (disableOcclusion.checkResult()) {
            visualizers.setOcclusionDisabled(disableOcclusion.getState());
        }
        if (changed && visualizers.anyEnabled()) {
            final int config = GraphicsManager.getPipelineConfig();
            if (!RenderConfig.hasDebugStage(config)) {
                GraphicsManager.swapPipeline(RenderConfig.builder(config).withDebug().build());
            }
        }
    }

    /**
     * Apply a change to the filter view selection. G-buffer views add the filter stage to the
     * pipeline if it's missing, and going back to the default removes it again if we added it.
     */
    private void handleFilterInput() {
        if (!filterViewChanged) {
            return;
        }
        filterViewChanged = false;
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null) {
            return;
        }
        final FilterView view = FilterView.values()[filterView.get()];
        renderer.setFilterView(view);

        final int config = GraphicsManager.getPipelineConfig();
        if (view != FilterView.DEFAULT) {
            // The filter needs something to run over, or the config is an error
            if (!RenderConfig.hasFilterStage(config)
                    && (RenderConfig.hasSceneStage(config)
                            || RenderConfig.hasSkyboxStage(config))) {
                GraphicsManager.swapPipeline(RenderConfig.builder(config).withFilter().build());
                addedFilterStage = true;
            }
        } else if (addedFilterStage) {
            if (RenderConfig.hasFilterStage(config)) {
                GraphicsManager.swapPipeline(RenderConfig.builder(config).withoutFilter().build());
            }
            addedFilterStage = false;
        }
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        super.handleGuiInput(scene, window);

        if (fogEnabled.checkResult()) {
            scene.getFog().setActive(fogEnabled.getState());
            return true;
        }
        if (wireframeEnabled.checkResult()) {
            int oldConfig = GraphicsManager.getPipelineConfig();
            RenderConfig.ConfigBuilder builder = RenderConfig.builder(oldConfig);
            if (wireframeEnabled.getState()) {
                builder.withWireframe();
            } else {
                builder.withoutWireframe();
            }
            GraphicsManager.swapPipeline(builder.build());
        }
        handleDebugInput(scene);
        handleFilterInput();
        if (showUiShowcase.checkResult()) {
            UiShowcase.setShown(showUiShowcase.getState());
        }
        DirectionalLight directionalLight = scene.getSceneLights().getDirLight();
        Vector3f directionalLightDir = directionalLight.getDirection();
        if (fogDensity.checkResult()) {
            scene.getFog().setDensity(fogDensity.getValue());
        }
        if (directionalLightX.checkResult()) {
            directionalLightDir.setComponent(0, directionalLightX.getValue());
        }
        if (directionalLightY.checkResult()) {
            directionalLightDir.setComponent(1, directionalLightY.getValue());
        }
        if (directionalLightZ.checkResult()) {
            directionalLightDir.setComponent(2, directionalLightZ.getValue());
        }
        if (directionalLightIntensity.checkResult()) {
            directionalLight.setIntensity(directionalLightIntensity.getValue());
        }

        return false;
    }

    @Override
    public void updateValues(@NonNull Scene scene, @NonNull Window window) {
        super.updateValues(scene, window);

        fogEnabled.setState(scene.getFog().isActive());
        int config = GraphicsManager.getPipelineConfig();
        wireframeEnabled.setState(RenderConfig.sceneIsWireframe(config));
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer != null) {
            filterView.set(renderer.getFilterView().ordinal());
        }

        DirectionalLight directionalLight = scene.getSceneLights().getDirLight();
        Vector3f directionalLightDir = directionalLight.getDirection();
        fogDensity.setValue(scene.getFog().getDensity());
        directionalLightX.setValue(directionalLightDir.x());
        directionalLightY.setValue(directionalLightDir.y());
        directionalLightZ.setValue(directionalLightDir.z());
        directionalLightIntensity.setValue(directionalLight.getIntensity());
    }

    /** Show how long each part of a frame takes, over the last couple of seconds. */
    private static void drawTimings() {
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().frameTimings == null) {
            return;
        }
        final FrameTimings timings = renderer.getState().frameTimings;
        if (!timings.isSupported()) {
            IkGui.textWrapped("The GPU can't write timestamps, so only CPU time is shown.");
        }
        IkGui.textWrapped(
                "Milliseconds over the last "
                        + FrameTimings.WINDOW
                        + " frames. The CPU time is recording and submitting a frame; the frame"
                        + " rate itself is held to the display's refresh rate.");
        drawTimingTable("timings", timings.snapshot());
    }

    /**
     * Show timings as a table of average, 95th percentile and worst.
     *
     * @param id The table's id.
     * @param rows The timings.
     */
    static void drawTimingTable(String id, List<FrameTimings.Timing> rows) {
        if (!IkGui.beginTable(id, 4)) {
            return;
        }
        IkGui.tableSetupColumn("Part");
        IkGui.tableSetupColumn("Average");
        IkGui.tableSetupColumn("95%");
        IkGui.tableSetupColumn("Worst");
        IkGui.tableHeadersRow();
        for (FrameTimings.Timing row : rows) {
            IkGui.tableNextRow();
            IkGui.tableNextColumn();
            IkGui.text(row.name());
            IkGui.tableNextColumn();
            IkGui.text(String.format("%.3f", row.average()));
            IkGui.tableNextColumn();
            IkGui.text(String.format("%.3f", row.p95()));
            IkGui.tableNextColumn();
            IkGui.text(String.format("%.3f", row.max()));
        }
        IkGui.endTable();
    }

    /**
     * Show how the lights were sorted into clusters, and the heat map toggle.
     *
     * @param scene The scene.
     */
    private static void drawLights(@NonNull Scene scene) {
        IkGui.text(
                String.format(
                        "Point and spot lights: %,d", scene.getLightRegistry().getLightCount()));
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer != null) {
            final LightCull.Stats stats = renderer.getState().lightStats;
            IkGui.text(
                    String.format(
                            "Clusters: %d x %d x %d out to %.0f m",
                            ClusterMath.X, ClusterMath.Y, ClusterMath.Z, scene.getViewDistance()));
            IkGui.text(
                    String.format(
                            "Busiest cluster: %,d lights (%,d at most are listed)",
                            stats.busiest(), ClusterMath.MAX_LIGHTS_PER_CLUSTER));
            IkGui.text(
                    String.format(
                            "Listed across all clusters: %,d (%.1f per cluster)",
                            stats.listed(), stats.listed() / (double) ClusterMath.COUNT));
            if (stats.overflowing() > 0) {
                IkGui.textColored(
                        1,
                        0.4f,
                        0.4f,
                        1,
                        String.format(
                                "%,d clusters touch too many lights; some are dropped",
                                stats.overflowing()));
            }
        }
        final DebugVisualizers visualizers = scene.getDebugVisualizers();
        if (IkGui.checkbox("Cluster heat map", visualizers.isClusterHeatMap())) {
            visualizers.setClusterHeatMap(!visualizers.isClusterHeatMap());
        }
        IkGui.setItemTooltip(
                "Tint each pixel by how many lights its cluster lists, from blue for one to red for "
                        + DebugVisualizers.HEAT_MAP_FULL
                        + " or more.");
    }

    /** Run the gate benchmark and show what it found. */
    private void drawBenchmark() {
        IkGui.textWrapped(
                "Builds a made-up world out to a view distance, bakes it, then turns the camera once"
                        + " around measuring each frame. The gate passes if the GPU's 95th percentile"
                        + " frame is within "
                        + GateBenchmark.GPU_BUDGET_MS
                        + " ms.");
        IkGui.combo("Recipe", benchmarkRecipe, RECIPE_NAMES);
        IkGui.sliderInt("Radius (sections)", benchmarkRadius, 6, 24);
        IkGui.sliderInt("Lights", benchmarkLights, 0, MAX_BENCHMARK_LIGHTS);
        IkGui.setItemTooltip("Torches scattered over the ground, to measure many lights.");
        IkGui.checkbox("Keep world", benchmarkKeepWorld);
        IkGui.sameLine();
        final boolean uiHidden = GraphicsManager.getUiManager().isSurfacesHidden();
        if (IkGui.checkbox("Hide plugin UI", uiHidden)) {
            GraphicsManager.getUiManager().setSurfacesHidden(!uiHidden);
        }
        IkGui.setItemTooltip(
                "Leave the world in place when the run finishes, to fly around it with the debug"
                        + " views and a frozen observer.");
        if (IkGui.button("Run")) {
            GateBenchmark.start(
                    SyntheticTerrain.Recipe.values()[benchmarkRecipe.get()],
                    benchmarkRadius[0],
                    benchmarkLights[0],
                    benchmarkKeepWorld.get());
        }
        IkGui.sameLine();
        if (IkGui.button("Stop")) {
            GateBenchmark.stop();
        }
        final GateBenchmark run = GateBenchmark.getCurrent();
        if (run == null) {
            return;
        }
        if (run.isWorldKept()) {
            IkGui.sameLine();
            if (IkGui.button("Remove world")) {
                GateBenchmark.removeWorld();
            }
        }
        IkGui.text(run.status());
        final GateBenchmark.Results results = run.getResults();
        if (results == null) {
            return;
        }
        final GateScene.LoadStats load = results.load();
        IkGui.text(
                String.format(
                        "%s at radius %d with %,d lights: %s",
                        results.recipe(),
                        results.radius(),
                        results.lights(),
                        results.passed() ? "passed" : "failed"));
        IkGui.text(
                String.format(
                        "%,d of %,d sections with content, %.2f per column",
                        load.contentSections(), load.sectionsInSphere(), load.sectionsPerColumn()));
        IkGui.text(
                String.format(
                        "%,d triangles, %,.0f per section; %,d frames measured",
                        load.triangles(), load.trianglesPerSection(), results.frames()));
        drawTimingTable("benchmark", results.timings());
    }
}
