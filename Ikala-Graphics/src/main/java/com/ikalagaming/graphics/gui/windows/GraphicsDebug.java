package com.ikalagaming.graphics.gui.windows;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.GraphicsSettings;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.benchmark.BlockMeshes;
import com.ikalagaming.graphics.benchmark.GateBenchmark;
import com.ikalagaming.graphics.benchmark.GateScene;
import com.ikalagaming.graphics.benchmark.SyntheticSections;
import com.ikalagaming.graphics.benchmark.SyntheticTerrain;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.component.Checkbox;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.component.Slider;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Alignment;
import com.ikalagaming.graphics.lod.LodPanel;
import com.ikalagaming.graphics.scene.MediumRegistry;
import com.ikalagaming.graphics.scene.PhysicalSky;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.vulkan.ClusterMath;
import com.ikalagaming.graphics.vulkan.FilterView;
import com.ikalagaming.graphics.vulkan.FrameTimings;
import com.ikalagaming.graphics.vulkan.InstanceRegistry;
import com.ikalagaming.graphics.vulkan.PointShadows;
import com.ikalagaming.graphics.vulkan.Presentation;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.VoxelOitMath;
import com.ikalagaming.graphics.vulkan.VulkanInstance;
import com.ikalagaming.graphics.vulkan.stages.InstanceDrawUpdate;
import com.ikalagaming.graphics.vulkan.stages.LightCull;

import lombok.NonNull;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphicsDebug extends GuiWindow {

    /** Texels in a million, for the sun shadow cache's numbers. */
    private static final double TEXELS_PER_MEGA = 1e6;

    /** The brightest the ambient light slider goes, in cd/m^2. */
    private static final float MAX_AMBIENT_INTENSITY = 2;

    /** The ambient light intensity slider's value. */
    private final float[] ambientIntensity = new float[1];

    /** The ambient light color picker's value, red, green, blue. */
    private final float[] ambientColor = new float[3];

    /** The point shadow budget slider's value. Render thread only. */
    private static final int[] POINT_SHADOW_BUDGET = new int[1];

    /** The occupancy rays a pixel slider's value. Render thread only. */
    private static final int[] RAYS_PER_PIXEL = new int[1];

    /** The sky light floor slider's value. Render thread only. */
    private static final float[] SKY_LIGHT_FLOOR = new float[1];

    /** The occupancy ambient occlusion reach slider's value. Render thread only. */
    private static final float[] AO_REACH = new float[1];

    /** The shortest reach the AO slider allows, in meters: one finest voxel. */
    private static final float MIN_AO_REACH = 1;

    /** The longest reach the AO slider allows, in meters. */
    private static final float MAX_AO_REACH = 32;

    public static final String WINDOW_NAME = "Graphics Debug";

    /** The densest fog the slider goes to, extinction per meter. */
    private static final float MAX_FOG_DENSITY = 0.05f;

    /** The fastest fog thins with height on the slider, per meter. */
    private static final float MAX_FOG_FALLOFF = 0.2f;

    /** The fog's base height's range on the slider, in meters. */
    private static final float MIN_FOG_BASE = -64;

    private static final float MAX_FOG_BASE = 256;

    /** The fog's phase anisotropy's range on the slider, either way. */
    private static final float MAX_FOG_ANISOTROPY = 0.9f;

    private final Checkbox fogEnabled;
    private final Checkbox fogHaze;
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
    private final Slider fogFalloff;
    private final Slider fogBase;
    private final Slider fogAnisotropy;
    private final Slider exposure;
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

    /** The highest exposure the slider offers, two stops brighter. */
    private static final float MAX_EXPOSURE = 4;

    /** The most torches the benchmark slider offers. */
    private static final int MAX_BENCHMARK_LIGHTS = 8000;

    /** Whether the benchmark leaves its world in place when it finishes. */
    private final IkBoolean benchmarkKeepWorld = new IkBoolean(false);

    /** Whether the benchmark world's opaque blocks are drawn by live level of detail. */
    private final IkBoolean benchmarkLod = new IkBoolean(false);

    /** Whether the benchmark also checks the level of detail gate. */
    private final IkBoolean benchmarkGate = new IkBoolean(false);

    /** Whether the kept benchmark world is being dug on a timer. */
    private final IkBoolean benchmarkRapidDig = new IkBoolean(false);

    /** Digs a second when digging on a timer. */
    private static final double RAPID_DIGS_PER_SECOND = 10;

    /** The level of detail colorings' names, in ordinal order. */
    private static final String[] LOD_COLOR_NAMES = {
        "Textures", "Clusters", "Groups", "Levels", "Cells"
    };

    /** The level of detail coloring combo's value. Render thread only. */
    private static final IkInt LOD_COLORS = new IkInt(DebugVisualizers.LodColors.NONE.ordinal());

    /** The volume distance to go back to when volume bricks are turned on again, in meters. */
    private static double volumeDistanceWhenOn = new GraphicsSettings().volumeDistance;

    /** The benchmark recipes' names, in ordinal order. */
    private static final String[] RECIPE_NAMES =
            Arrays.stream(SyntheticTerrain.Recipe.values()).map(Enum::name).toArray(String[]::new);

    /** The level of detail recipe picked, by ordinal. */
    private final IkInt lodRecipe = new IkInt(SyntheticTerrain.Recipe.TERRAIN.ordinal());

    /** How many columns of sections the level of detail world has along each side. */
    private final int[] lodColumns = {4};

    /** Whether the level of detail world is built in region cells. */
    private final IkBoolean lodCells = new IkBoolean(false);

    /** The most columns the level of detail slider offers. */
    private static final int MAX_LOD_COLUMNS = 12;

    /** The seed of the level of detail world. */
    private static final long LOD_SEED = 0x1CA1A;

    /** How high above the level of detail world the camera starts, in blocks. */
    private static final double LOD_CAMERA_HEIGHT = 60;

    /** How far the camera starts back from the level of detail world, in blocks. */
    private static final double LOD_CAMERA_BACK = 40;

    /** How far down the camera looks at the level of detail world, in radians. */
    private static final float LOD_CAMERA_PITCH = 0.5f;

    /** The sun's intensity while the level of detail world is shown. */
    private static final float LOD_SUN_INTENSITY = 3;

    /** The ambient light's intensity while the level of detail world is shown. */
    private static final float LOD_AMBIENT_INTENSITY = 0.25f;

    /** The level of detail panel, while one is shown. */
    private LodPanel lodPanel;

    /** The block materials the level of detail world added to the scene. */
    private final List<Material> lodMaterials = new ArrayList<>();

    /** The block textures the level of detail world loaded, to release. */
    private final List<TextureHandle> lodTextures = new ArrayList<>();

    /** The pipeline before the level of detail world, to put back. */
    private RenderConfig lodPreviousPipeline;

    /** The sun before the level of detail world, to put back. */
    private DirectionalLight lodPreviousSun;

    /** The ambient light before the level of detail world, to put back. */
    private AmbientLight lodPreviousAmbient;

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
        fogDensity = new Slider("Fog density (per m)", 0, 0, MAX_FOG_DENSITY);
        fogFalloff = new Slider("Fog height falloff (per m)", 0, 0, MAX_FOG_FALLOFF);
        fogBase = new Slider("Fog base height (m)", 0, MIN_FOG_BASE, MAX_FOG_BASE);
        fogAnisotropy = new Slider("Fog anisotropy", 0, -MAX_FOG_ANISOTROPY, MAX_FOG_ANISOTROPY);
        fogHaze = new Checkbox("Atmosphere haze", false);
        exposure = new Slider("Exposure", 1, 0, MAX_EXPOSURE);
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
        addChild(fogFalloff);
        addChild(fogBase);
        addChild(fogAnisotropy);
        addChild(fogHaze);
        addChild(exposure);
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
        setNextWindowPosInMainWindow(
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
                            i < InstanceDrawUpdate.LIST_SKY;
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
                    final boolean cache = GraphicsManager.getSettings().sunShadowCache;
                    if (IkGui.checkbox("Cache sun shadows", cache)) {
                        GraphicsManager.getSettings().sunShadowCache = !cache;
                    }
                    IkGui.setItemTooltip(
                            "Keep the far cascades in place in the world and draw what doesn't"
                                    + " move into them only when it changes. Off, every cascade is"
                                    + " drawn whole every frame.");
                    final var cacheStats = renderer.getState().sunShadows.stats();
                    IkGui.text(
                            String.format(
                                    "Sun shadow redraws: %,d whole, %,d in part, %,d shifted,"
                                            + " %,.1f M texels",
                                    cacheStats.fullRedraws(),
                                    cacheStats.partRedraws(),
                                    cacheStats.scrolls(),
                                    cacheStats.texelsRedrawn() / TEXELS_PER_MEGA));
                    IkGui.text(
                            String.format(
                                    "Translucent meshes drawn: %,d",
                                    drawn[InstanceDrawUpdate.LIST_TRANSLUCENT]));
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

            if (IkGui.collapsingHeader("Windows")) {
                drawWindows();
            }

            if (IkGui.collapsingHeader("Lights")) {
                drawLights(scene);
            }

            if (IkGui.collapsingHeader("Transparency")) {
                drawTransparency(scene);
            }

            if (IkGui.collapsingHeader("Benchmark")) {
                drawBenchmark();
            }

            if (IkGui.collapsingHeader("Level of detail")) {
                drawLod(scene);
            }

            if (IkGui.collapsingHeader("Render Config Info")) {
                RenderConfig config = GraphicsManager.getPipelineConfig();
                final String flagString = "%12s - %s";
                IkGui.text(String.format(flagString, "World", config.world()));
                IkGui.text(String.format(flagString, "Filters", config.filters()));
                IkGui.text(String.format(flagString, "Debug", config.debug()));
                IkGui.text(String.format(flagString, "Gui", config.gui()));
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
                boolean renderingScene = GraphicsManager.getPipelineConfig().world();

                if (!renderingScene) {
                    IkGui.beginDisabled();
                    IkGui.text("(Not rendering scene, controls disabled)");
                }
                fogEnabled.draw(width, height);
                fogDensity.draw(width, height);
                fogFalloff.draw(width, height);
                fogBase.draw(width, height);
                fogAnisotropy.draw(width, height);
                fogHaze.draw(width, height);
                final Scene fogScene = GraphicsManager.getScene();
                if (fogScene != null) {
                    IkGui.text(
                            String.format(
                                    "Medium volumes: %d of %d",
                                    fogScene.getMediumRegistry().getCount(),
                                    MediumRegistry.MAX_VOLUMES));
                }
                exposure.draw(width, height);
                wireframeEnabled.draw(width, height);
                directionalLightX.draw(width, height);
                directionalLightY.draw(width, height);
                directionalLightZ.draw(width, height);
                directionalLightIntensity.draw(width, height);
                drawAmbientControls();
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
                drawLodColors(scene);
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
            final RenderConfig config = GraphicsManager.getPipelineConfig();
            if (!config.debug()) {
                GraphicsManager.swapPipeline(config.withDebug(true));
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

        final RenderConfig config = GraphicsManager.getPipelineConfig();
        if (view != FilterView.DEFAULT) {
            // The filter needs the world to run over
            if (!config.filters() && config.world()) {
                GraphicsManager.swapPipeline(config.withFilters(true));
                addedFilterStage = true;
            }
        } else if (addedFilterStage) {
            if (config.filters()) {
                GraphicsManager.swapPipeline(config.withFilters(false));
            }
            addedFilterStage = false;
        }
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene) {
        super.handleGuiInput(scene);

        if (fogEnabled.checkResult()) {
            scene.getFog().setActive(fogEnabled.getState());
            return true;
        }
        if (fogHaze.checkResult()) {
            scene.getFog().setHaze(fogHaze.getState());
        }
        if (wireframeEnabled.checkResult()) {
            scene.getDebugVisualizers().setWireframe(wireframeEnabled.getState());
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
        if (fogFalloff.checkResult()) {
            scene.getFog().setHeightFalloff(fogFalloff.getValue());
        }
        if (fogBase.checkResult()) {
            scene.getFog().setBaseHeight(fogBase.getValue());
        }
        if (fogAnisotropy.checkResult()) {
            scene.getFog().setAnisotropy(fogAnisotropy.getValue());
        }
        if (exposure.checkResult()) {
            scene.setExposure(exposure.getValue());
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
    public void updateValues(@NonNull Scene scene) {
        super.updateValues(scene);

        fogEnabled.setState(scene.getFog().isActive());
        fogHaze.setState(scene.getFog().isHaze());
        wireframeEnabled.setState(scene.getDebugVisualizers().isWireframe());
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer != null) {
            filterView.set(renderer.getFilterView().ordinal());
        }

        DirectionalLight directionalLight = scene.getSceneLights().getDirLight();
        Vector3f directionalLightDir = directionalLight.getDirection();
        fogDensity.setValue(scene.getFog().getDensity());
        fogFalloff.setValue(scene.getFog().getHeightFalloff());
        fogBase.setValue(scene.getFog().getBaseHeight());
        fogAnisotropy.setValue(scene.getFog().getAnisotropy());
        exposure.setValue(scene.getExposure());
        directionalLightX.setValue(directionalLightDir.x());
        directionalLightY.setValue(directionalLightDir.y());
        directionalLightZ.setValue(directionalLightDir.z());
        directionalLightIntensity.setValue(directionalLight.getIntensity());
    }

    /**
     * The level of detail coloring, for every level of detail world and instance, offered while
     * there are any.
     *
     * @param scene The scene.
     */
    private static void drawLodColors(@NonNull Scene scene) {
        final DebugVisualizers visualizers = scene.getDebugVisualizers();
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        final boolean any =
                renderer != null
                        && renderer.getState().lods != null
                        && renderer.getState().lods.hasWorlds();
        if (!any) {
            IkGui.beginDisabled();
        }
        LOD_COLORS.set(visualizers.getLodColors().ordinal());
        if (IkGui.combo("LOD colors", LOD_COLORS, LOD_COLOR_NAMES)) {
            visualizers.setLodColors(DebugVisualizers.LodColors.values()[LOD_COLORS.get()]);
        }
        IkGui.setItemTooltip(
                any
                        ? "Color level of detail by cluster, by the group that simplifies"
                                + " together, by level, or by region cell, still lit."
                        : "There is no level of detail to color yet.");
        if (!any) {
            IkGui.endDisabled();
        }
    }

    /**
     * Show every IkGui viewport and the window it is drawn in, and every window's swapchain, for
     * finding out why a window shows nothing.
     */
    private static void drawWindows() {
        IkGui.text("IkGui viewports:");
        for (Viewport viewport : IkGui.getPlatformIO().viewports) {
            final String window =
                    viewport.platformHandle instanceof Window owner ? owner.getTitle() : "none";
            IkGui.text(
                    String.format(
                            "  %08X at %.0f, %.0f, %.0f x %.0f, in %s%s",
                            viewport.id,
                            viewport.position.x,
                            viewport.position.y,
                            viewport.size.x,
                            viewport.size.y,
                            window,
                            viewport == IkGui.getMainViewport() ? " (main)" : ""));
        }
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null) {
            return;
        }
        IkGui.text("Swapchains:");
        for (Presentation presentation : renderer.getState().presentations.values()) {
            final Window window = presentation.getWindow();
            IkGui.text(
                    String.format(
                            "  %s: %d x %d%s%s",
                            window.getTitle(),
                            presentation.swapchainWidth,
                            presentation.swapchainHeight,
                            window == GraphicsManager.getSceneWindow() ? ", scene" : "",
                            presentation.updateSwapchain ? ", waiting to be remade" : ""));
        }
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

        // Sky light under cover, from the sky occlusion map
        final var settings = GraphicsManager.getSettings();
        if (IkGui.checkbox("Light from the sky", settings.environmentLighting)) {
            settings.environmentLighting = !settings.environmentLighting;
        }
        IkGui.setItemTooltip(
                "Take ambient light from the skybox: reflections by roughness and diffuse light by"
                        + " direction, averaging the ambient light. Off, it is the same from every"
                        + " direction.");
        if (IkGui.checkbox("Sky light around cover", settings.environmentOcclusion)) {
            settings.environmentOcclusion = !settings.environmentOcclusion;
        }
        IkGui.setItemTooltip(
                "Bend the sky's diffuse light toward where the occupancy volume finds the sky, and"
                        + " hide its reflections where a ray along them meets something.");
        IkGui.sameLine();
        final Scene skyScene = GraphicsManager.getScene();
        if (skyScene != null && IkGui.checkbox("Physical sky", skyScene.getPhysicalSky() != null)) {
            skyScene.setPhysicalSky(skyScene.getPhysicalSky() == null ? new PhysicalSky() : null);
        }
        IkGui.setItemTooltip(
                "Draw Earth's atmosphere lit by the sun in place of the skybox, and light the"
                        + " scene from it, as bright as the sun makes it; the ambient light is not"
                        + " used.");
        if (skyScene != null && IkGui.checkbox("Auto exposure", skyScene.isAutoExposure())) {
            skyScene.setAutoExposure(!skyScene.isAutoExposure());
        }
        IkGui.setItemTooltip(
                "Adapt the exposure to the scene as an eye does, the scene's exposure compensating"
                        + " it, within 4 stops down and 6 up.");
        if (IkGui.checkbox("Sky occlusion", settings.skyOcclusion)) {
            settings.skyOcclusion = !settings.skyOcclusion;
        }
        IkGui.setItemTooltip(
                "Dim ambient light where something stands above a point, from a map of what"
                        + " doesn't move drawn from straight above, so caves and rooms aren't lit"
                        + " like open ground.");
        IkGui.sameLine();
        if (IkGui.checkbox("Sky occlusion view", visualizers.isSkyOcclusionView())) {
            visualizers.setSkyOcclusionView(!visualizers.isSkyOcclusionView());
        }
        IkGui.setItemTooltip("Show how much sky light reaches each pixel, in gray.");
        SKY_LIGHT_FLOOR[0] = (float) settings.skyLightFloor;
        if (IkGui.sliderFloat("Sky light under cover", SKY_LIGHT_FLOOR, 0, 1)) {
            settings.skyLightFloor = SKY_LIGHT_FLOOR[0];
        }
        IkGui.setItemTooltip("The least ambient light a point well under cover gets.");
        // The occupancy volume
        if (IkGui.checkbox("Occupancy volume", settings.occupancy)) {
            settings.occupancy = !settings.occupancy;
        }
        IkGui.setItemTooltip(
                "Keep a voxel volume of what doesn't move around the camera, for ambient occlusion"
                        + " and light visibility.");
        IkGui.sameLine();
        if (IkGui.checkbox("Occupancy view", visualizers.isOccupancyView())) {
            visualizers.setOccupancyView(!visualizers.isOccupancyView());
        }
        IkGui.setItemTooltip(
                "Gray for how much is around each surface in the volume, red where a surface's own"
                        + " voxel is empty.");
        if (IkGui.checkbox("Large-scale AO", settings.occupancyAo)) {
            settings.occupancyAo = !settings.occupancyAo;
        }
        IkGui.setItemTooltip(
                "Dim ambient light where the occupancy volume has something in the way nearby.");
        IkGui.sameLine();
        if (IkGui.checkbox("AO view", visualizers.isOccupancyAoView())) {
            visualizers.setOccupancyAoView(!visualizers.isOccupancyAoView());
        }
        IkGui.setItemTooltip("Show how much ambient light the volume lets through, in gray.");
        AO_REACH[0] = (float) settings.occupancyAoReach;
        if (IkGui.sliderFloat("AO reach", AO_REACH, MIN_AO_REACH, MAX_AO_REACH)) {
            settings.occupancyAoReach = AO_REACH[0];
        }
        IkGui.setItemTooltip("How far, in meters, to look for something in the way.");
        if (IkGui.checkbox("Light rays", settings.occupancyLightRays)) {
            settings.occupancyLightRays = !settings.occupancyLightRays;
        }
        IkGui.setItemTooltip(
                "Keep lights without a shadow map from shining through what the volume holds.");
        IkGui.sameLine();
        if (IkGui.checkbox("Rays view", visualizers.isOccupancyRayView())) {
            visualizers.setOccupancyRayView(!visualizers.isOccupancyRayView());
        }
        IkGui.setItemTooltip(
                "Gray for how much of the rayed lights' light gets through, blue where none"
                        + " reaches.");
        RAYS_PER_PIXEL[0] = settings.occupancyRaysPerPixel;
        if (IkGui.sliderInt(
                "Rays a pixel", RAYS_PER_PIXEL, 0, ShaderBindings.Light.MAX_OCCUPANCY_RAYS)) {
            settings.occupancyRaysPerPixel = RAYS_PER_PIXEL[0];
        }
        IkGui.setItemTooltip(
                "How many lights a pixel marches a ray for, the strongest first; the rest shine"
                        + " through.");
        final var occupancyRenderer = GraphicsManager.getRenderInstance();
        if (occupancyRenderer != null && occupancyRenderer.getState().occupancy != null) {
            final var volume = occupancyRenderer.getState().occupancy;
            IkGui.text(
                    String.format(
                            "Occupancy: %,d sections waiting, %,.1f M triangles voxelized",
                            volume.getQueuedSections(),
                            volume.getTrianglesVoxelized() / TEXELS_PER_MEGA));
        }

        // Temporal anti-aliasing
        if (IkGui.checkbox("Temporal AA", settings.taa)) {
            settings.taa = !settings.taa;
        }
        IkGui.setItemTooltip(
                "Sample each frame's pixels a little apart and blend them into the frames before.");
        IkGui.sameLine();
        if (IkGui.checkbox("History view", visualizers.isTaaView())) {
            visualizers.setTaaView(!visualizers.isTaaView());
        }
        IkGui.setItemTooltip(
                "Gray for how many frames each pixel's history holds, red where it started over.");

        // Point light shadows: the budget and what it reached
        POINT_SHADOW_BUDGET[0] = settings.pointShadowBudget;
        if (IkGui.sliderInt("Point shadow maps", POINT_SHADOW_BUDGET, 0, PointShadows.MAX_MAPS)) {
            settings.pointShadowBudget = POINT_SHADOW_BUDGET[0];
        }
        IkGui.setItemTooltip(
                "The most cube shadow maps a frame for lights that cast shadows; a tight group of"
                        + " lights shares one.");
        final var skyRenderer = GraphicsManager.getRenderInstance();
        if (skyRenderer != null && skyRenderer.getState().pointShadows != null) {
            final PointShadows points = skyRenderer.getState().pointShadows;
            IkGui.text(
                    String.format(
                            "Point shadows: %,d of %,d casting lights in %,d maps, %,d faces drawn",
                            points.getShadowedLights(),
                            points.getCastingLights(),
                            points.getMapCount(),
                            points.getFaceRedraws()));
        }
        if (skyRenderer != null && skyRenderer.getState().skyOcclusion != null) {
            final var skyStats = skyRenderer.getState().skyOcclusion.stats();
            IkGui.text(
                    String.format(
                            "Sky map redraws: %,d whole, %,d in part, %,.1f M texels",
                            skyStats.fullRedraws(),
                            skyStats.partRedraws(),
                            skyStats.texelsRedrawn() / TEXELS_PER_MEGA));
        }
    }

    /**
     * Pick how translucent surfaces are weighted, and look inside the transparency volume.
     *
     * @param scene The scene.
     */
    private static void drawTransparency(@NonNull Scene scene) {
        final DebugVisualizers visualizers = scene.getDebugVisualizers();
        final GraphicsSettings settings = GraphicsManager.getSettings();
        if (IkGui.checkbox("Volume bricks", settings.volumeDistance > 0)) {
            if (settings.volumeDistance > 0) {
                volumeDistanceWhenOn = settings.volumeDistance;
                settings.volumeDistance = 0;
            } else {
                settings.volumeDistance = volumeDistanceWhenOn;
            }
        }
        IkGui.setItemTooltip(
                "Draw distant leaves, glass and water as volume bricks. Off, every section draws"
                        + " its own cut out and translucent meshes at any distance.");
        if (IkGui.checkbox("Color by source", visualizers.isVolumeSources())) {
            visualizers.setVolumeSources(!visualizers.isVolumeSources());
        }
        IkGui.setItemTooltip(
                "Draw sections' cut out meshes cyan and volume bricks magenta, still lit, to see"
                        + " where each draws and whether they line up in the hand over.");
        if (IkGui.checkbox("Voxel-based weights", visualizers.isVoxelTransparency())) {
            visualizers.setVoxelTransparency(!visualizers.isVoxelTransparency());
        }
        IkGui.setItemTooltip(
                "Weight each translucent surface by how much light gets through to it, from a"
                        + " low resolution volume of what is in front, which blends layers a few"
                        + " slices apart as if sorted. Off, surfaces are weighted by depth alone"
                        + " (weighted blended transparency).");
        IkGui.text(
                String.format(
                        "Volume: %d px tiles, %d slices from %.1f m to %.0f m",
                        VoxelOitMath.TILE_SIZE,
                        VoxelOitMath.SLICES,
                        VoxelOitMath.FIRST_DEPTH,
                        scene.getViewDistance()));
        final int[] slice = {visualizers.getTransmittanceSlice()};
        if (IkGui.sliderInt("Show slice", slice, -1, VoxelOitMath.SLICES - 1)) {
            visualizers.setTransmittanceSlice(slice[0]);
        }
        IkGui.setItemTooltip(
                "Show how much light gets through to the far end of one slice of the volume, from"
                        + " black for none to white for all of it, over the whole screen. -1 shows"
                        + " the scene. Only with voxel-based weights.");
    }

    /**
     * Build a level of detail DAG of a synthetic world and look at it.
     *
     * @param scene The scene.
     */
    private void drawLod(@NonNull Scene scene) {
        IkGui.textWrapped(
                "Bakes a made-up world on the CPU, builds its cluster DAG, and draws the cut seen"
                        + " from the observer. Freeze the observer to fly out and look at the cut.");
        IkGui.combo("Recipe##lod", lodRecipe, RECIPE_NAMES);
        IkGui.sliderInt("Columns##lod", lodColumns, 1, MAX_LOD_COLUMNS);
        IkGui.checkbox("Region cells##lod", lodCells);
        if (IkGui.button("Build LOD")) {
            startLod(scene);
        }
        if (lodPanel != null) {
            IkGui.sameLine();
            if (IkGui.button("Remove LOD")) {
                removeLod(scene);
                return;
            }
            lodPanel.draw();
        }
    }

    /**
     * Start building a level of detail world, and set the scene up to show it.
     *
     * @param scene The scene.
     */
    private void startLod(@NonNull Scene scene) {
        removeLod(scene);
        final BlockMeshes.Textured blocks =
                BlockMeshes.textured(
                        GraphicsManager.forPlugin(GraphicsPlugin.PLUGIN_NAME).textures());
        final MeshData[] meshes = blocks.meshes();
        blocks.materials().forEach(scene.getMaterialCache()::addMaterial);
        lodMaterials.addAll(blocks.materials());
        lodTextures.addAll(blocks.textures());
        final int[] sides = new int[meshes.length];
        final int[] tops = new int[meshes.length];
        final int[] bottoms = new int[meshes.length];
        final Map<Integer, String> materialNames = new HashMap<>();
        for (int block = 1; block < meshes.length; ++block) {
            final Material material = meshes[block].getMaterial();
            final String name = SyntheticTerrain.name(block);
            sides[block] = scene.getMaterialCache().getMaterialIndex(material);
            tops[block] = scene.getMaterialCache().getMaterialIndex(material.forTop());
            bottoms[block] = scene.getMaterialCache().getMaterialIndex(material.forBottom());
            materialNames.putIfAbsent(sides[block], name);
            materialNames.putIfAbsent(tops[block], name + " top");
            materialNames.putIfAbsent(bottoms[block], name + " bottom");
        }
        scene.getMaterialCache().setDirty(true);
        final BakeSource[] sources = BlockMeshes.sources(meshes);
        final SyntheticTerrain.Recipe recipe = SyntheticTerrain.Recipe.values()[lodRecipe.get()];
        final int columns = lodColumns[0];
        lodPreviousPipeline = GraphicsManager.getPipelineConfig();
        // The debug stage draws the bounds and locked edge overlays
        GraphicsManager.swapPipeline(RenderConfig.WORLD.withDebug(true));
        lodPreviousSun = scene.getSceneLights().getDirLight();
        lodPreviousAmbient = scene.getSceneLights().getAmbientLight();
        scene.getSceneLights()
                .setDirLight(
                        new DirectionalLight(
                                new Vector3f(1, 0.95f, 0.85f),
                                new Vector3f(0.4f, -0.8f, 0.45f),
                                LOD_SUN_INTENSITY));
        scene.getSceneLights()
                .setAmbientLight(new AmbientLight(new Vector3f(1, 1, 1), LOD_AMBIENT_INTENSITY));
        final double middle = columns * SyntheticTerrain.SECTION / 2.0;
        scene.getCamera()
                .setPosition(
                        middle,
                        LOD_CAMERA_HEIGHT,
                        columns * SyntheticTerrain.SECTION + LOD_CAMERA_BACK);
        scene.getCamera().setRotation(LOD_CAMERA_PITCH, 0);
        lodPanel =
                new LodPanel(
                        GraphicsManager.forPlugin(GraphicsPlugin.PLUGIN_NAME),
                        recipe.name().toLowerCase() + " " + columns + "x" + columns,
                        () ->
                                SyntheticSections.bake(
                                        recipe, LOD_SEED, columns, sources, sides, tops, bottoms),
                        material -> materialNames.getOrDefault(material, "material " + material),
                        lodCells.get());
    }

    /**
     * Remove the level of detail world and put the scene back as it was.
     *
     * @param scene The scene.
     */
    private void removeLod(@NonNull Scene scene) {
        if (lodPanel == null) {
            return;
        }
        lodPanel.close();
        lodPanel = null;
        for (Material material : lodMaterials) {
            scene.getMaterialCache().removeMaterial(material);
        }
        lodMaterials.clear();
        final GraphicsContext graphics = GraphicsManager.forPlugin(GraphicsPlugin.PLUGIN_NAME);
        lodTextures.forEach(graphics.textures()::release);
        lodTextures.clear();
        scene.getMaterialCache().setDirty(true);
        scene.getSceneLights().setDirLight(lodPreviousSun);
        scene.getSceneLights().setAmbientLight(lodPreviousAmbient);
        GraphicsManager.swapPipeline(lodPreviousPipeline);
    }

    /** Edit the scene's ambient light: how bright it is, and its color. */
    private void drawAmbientControls() {
        final Scene scene = GraphicsManager.getScene();
        if (scene == null) {
            return;
        }
        final AmbientLight ambient = scene.getSceneLights().getAmbientLight();
        ambientIntensity[0] = ambient.getIntensity();
        if (IkGui.sliderFloat("Ambient intensity", ambientIntensity, 0, MAX_AMBIENT_INTENSITY)) {
            ambient.setIntensity(ambientIntensity[0]);
        }
        IkGui.setItemTooltip(
                "Light arriving equally from everywhere, dimmed under cover by sky occlusion.");
        final Vector3f color = ambient.getColor();
        ambientColor[0] = color.x;
        ambientColor[1] = color.y;
        ambientColor[2] = color.z;
        if (IkGui.colorEdit3("Ambient color", ambientColor)) {
            color.set(ambientColor[0], ambientColor[1], ambientColor[2]);
        }
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
        IkGui.sliderInt("Radius (sections)", benchmarkRadius, 6, 32);
        // Its own ID, apart from the Lights header's
        IkGui.sliderInt("Lights##benchmark", benchmarkLights, 0, MAX_BENCHMARK_LIGHTS);
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
        IkGui.checkbox("Level of detail##benchmark", benchmarkLod);
        IkGui.setItemTooltip(
                "Draw the world's opaque blocks with live level of detail built from its sections.");
        IkGui.sameLine();
        IkGui.checkbox("Gate checks", benchmarkGate);
        IkGui.setItemTooltip(
                "After the turn, check the level of detail's cut for cracks at four headings, dig "
                        + GateBenchmark.GATE_DIGS
                        + " blocks near the camera timing each edit, and report what the disk cache"
                        + " did while loading. Draws with level of detail.");
        if (IkGui.button("Run")) {
            GateBenchmark.start(
                    SyntheticTerrain.Recipe.values()[benchmarkRecipe.get()],
                    benchmarkRadius[0],
                    benchmarkLights[0],
                    benchmarkKeepWorld.get(),
                    benchmarkLod.get(),
                    benchmarkGate.get());
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
            if (IkGui.button("Dig near camera")) {
                run.dig(GraphicsManager.getScene());
            }
            IkGui.sameLine();
            if (IkGui.checkbox("Dig 10 a second", benchmarkRapidDig)) {
                run.setDigRate(benchmarkRapidDig.get() ? RAPID_DIGS_PER_SECOND : 0);
            }
            final List<double[]> latencies = run.getDigLatencies();
            if (!latencies.isEmpty()) {
                final double[] last = latencies.get(latencies.size() - 1);
                IkGui.text(
                        String.format(
                                "%d digs; last drawn after %.0f ms, in level of detail after %.0f"
                                        + " ms; %d sections drawing themselves",
                                latencies.size(),
                                last[0],
                                last[1],
                                GraphicsManager.getRenderInstance()
                                        .getState()
                                        .sections
                                        .getOverrideCount()));
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
        for (String line : GateBenchmark.gateLines(results)) {
            IkGui.textWrapped(line);
        }
        drawTimingTable("benchmark", results.timings());
    }
}
