package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;
import com.ikalagaming.graphics.scene.lights.LightRegistry;
import com.ikalagaming.graphics.scene.lights.SceneLights;
import com.ikalagaming.graphics.vulkan.DeletionQueue;
import com.ikalagaming.graphics.vulkan.FilterView;
import com.ikalagaming.graphics.vulkan.GeometryArena;
import com.ikalagaming.graphics.vulkan.InstanceTable;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** A scene to be rendered, containing the items and lighting. */
@Getter
@Slf4j
public class Scene {
    /**
     * The camera for the scene.
     *
     * @return The scene's camera.
     */
    private final Camera camera;

    /**
     * The fog for the scene.
     *
     * @param fog The new fog settings to use.
     * @return The current fog settings.
     */
    private final Fog fog;

    /**
     * The material cache to use for this scene.
     *
     * @return The material cache.
     */
    private final MaterialCache materialCache;

    /**
     * The view whose instances this scene draws, 0 for the world. -- GETTER -- The view this scene
     * draws.
     *
     * @return The view ID, 0 for the world.
     */
    private final int viewID;

    /** The list of models, mapped by ID. */
    private final Map<String, Model> modelMap;

    /**
     * The projection matrix for the scene.
     *
     * @return The projection matrix information.
     */
    private final Projection projection;

    /**
     * The scene lighting information.
     *
     * @param sceneLights The lights to render with.
     * @return The lights used in the scene.
     */
    private final SceneLights sceneLights;

    /**
     * The point lights and spotlights plugins placed, through {@link
     * com.ikalagaming.graphics.Lights}.
     *
     * @return The light registry.
     */
    private final LightRegistry lightRegistry;

    /**
     * The medium volumes plugins placed, through {@link com.ikalagaming.graphics.Media}.
     *
     * @return The medium registry.
     */
    private final MediumRegistry mediumRegistry;

    /**
     * The particle emitters plugins placed, through {@link com.ikalagaming.graphics.Particles}.
     *
     * @return The emitter registry.
     */
    private final EmitterRegistry emitterRegistry;

    /**
     * The water tiles plugins placed, through {@link com.ikalagaming.graphics.Water}.
     *
     * @return The water registry.
     */
    private final WaterRegistry waterRegistry;

    /**
     * The particles' time: paused, stepped, slowed or cleared, and the live count.
     *
     * @return The particle clock.
     */
    private final ParticleClock particleClock;

    /**
     * The particle effects plugins loaded from files.
     *
     * @return The effect library.
     */
    private final EffectLibrary effectLibrary;

    /**
     * The texture of the skybox, which may be null if we want the sky to be just a single diffuse
     * color.
     */
    private TextureHandle skyboxTexture;

    /**
     * How the skybox texture lays the sky out. -- GETTER -- How the skybox texture lays the sky
     * out.
     *
     * @return The layout.
     */
    private volatile SkyboxLayout skyboxLayout = SkyboxLayout.CUBE_FACES;

    /** The diffuse color of the skybox, for use when there is no texture. */
    private final Vector4f skyboxDiffuse;

    /**
     * The physical sky drawn in place of the skybox, lighting the scene from it, or null for the
     * skybox. -- GETTER -- The physical sky. -- SETTER -- Draw a physical sky, or the skybox again.
     *
     * @param physicalSky The physical sky, or null for the skybox.
     * @return The physical sky, or null.
     */
    @Setter private volatile PhysicalSky physicalSky;

    /**
     * Whether the exposure adapts to the scene, as an eye does, with {@link
     * #getExposureCompensation()} shifting it, rather than being {@link #getExposureValue()}. --
     * GETTER -- Whether the exposure adapts. -- SETTER -- Make the exposure adapt or not.
     *
     * @param autoExposure Whether it adapts.
     * @return Whether it adapts.
     */
    @Setter private volatile boolean autoExposure;

    /**
     * Which built-in debug visualizations to draw.
     *
     * @return The debug visualization settings.
     */
    private final DebugVisualizers debugVisualizers;

    /**
     * How far the view reaches by default, in meters: twelve sections.
     *
     * @see #getViewDistance()
     */
    public static final double DEFAULT_VIEW_DISTANCE = 12.0f * Sections.SECTION_SIZE;

    /**
     * How far the view reaches, in meters. Lights are sorted into clusters out to here; later the
     * shadows and level of detail follow it too. Things past it can still be drawn, out to the far
     * plane.
     *
     * @return The view distance.
     */
    private volatile double viewDistance;

    /**
     * The camera's exposure while it doesn't adapt, in EV100: lower is brighter. {@link
     * LightUnits#DEFAULT_EV100}, about 14.8, suits a sunny day; an evening indoors is about 7. --
     * GETTER -- The exposure. -- SETTER -- Set the exposure.
     *
     * @param exposureValue The exposure value.
     * @return The exposure value.
     */
    @Setter private volatile float exposureValue;

    /**
     * How much brighter than the adapted exposure the scene is shown while the exposure adapts, in
     * stops: 1 is twice as bright, -1 half. -- GETTER -- The compensation. -- SETTER -- Set the
     * compensation.
     *
     * @param exposureCompensation The compensation, in stops.
     * @return The compensation, in stops.
     */
    @Setter private volatile float exposureCompensation;

    /**
     * What every light is multiplied by as it is uploaded, so the GPU's half floats hold the
     * scene's light at any brightness; the exposure takes it back out, so it changes nothing shown.
     * See {@link LightUnits}. -- GETTER -- The pre-exposure. -- SETTER -- Set the pre-exposure,
     * above 0, such as higher for a night lit only by the moon.
     *
     * @param preExposure The pre-exposure.
     * @return The pre-exposure.
     */
    @Setter private volatile float preExposure;

    /**
     * What the filter stage shows for this scene, like a preview showing its roughness, or null for
     * the renderer's own choice. It shows only while the pipeline drawing the scene runs filters.
     *
     * @param filterView The view, or null.
     * @return The view, or null.
     */
    @Setter private volatile FilterView filterView;

    /**
     * Whether temporal anti-aliasing runs for this scene, when the settings turn it on. A preview
     * drawn supersampled turns it off, so a still image has no jitter to settle.
     *
     * @param temporalAa Whether it may run.
     * @return Whether it may run.
     */
    @Setter private volatile boolean temporalAa = true;

    /** The observer while it is frozen, or null while it follows the camera. */
    @Getter(AccessLevel.NONE)
    private volatile Observer frozenObserver;

    /**
     * Set up a new scene.
     *
     * @param width The screen width, in pixels.
     * @param height The screen height, in pixels.
     */
    public Scene(int width, int height) {
        this(width, height, new MaterialCache(), 0);
    }

    /**
     * Set up a scene that one view draws, like a preview, sharing another scene's materials so the
     * GPU's material list covers both.
     *
     * @param width The width it is drawn at, in pixels.
     * @param height The height it is drawn at, in pixels.
     * @param materials The material cache to share.
     * @param viewID The view whose instances it draws, 0 for the world.
     */
    public Scene(int width, int height, @NonNull MaterialCache materials, int viewID) {
        this.viewID = viewID;
        modelMap = new ConcurrentHashMap<>();
        projection = new Projection(width, height);
        materialCache = materials;
        sceneLights = new SceneLights();
        lightRegistry = new LightRegistry();
        mediumRegistry = new MediumRegistry();
        emitterRegistry = new EmitterRegistry();
        waterRegistry = new WaterRegistry();
        particleClock = new ParticleClock();
        effectLibrary = new EffectLibrary();
        viewDistance = DEFAULT_VIEW_DISTANCE;
        exposureValue = LightUnits.DEFAULT_EV100;
        exposureCompensation = 0;
        preExposure = LightUnits.REFERENCE_PRE_EXPOSURE;
        camera = new Camera();
        fog = new Fog();
        skyboxDiffuse = new Vector4f(0.65f, 0.65f, 0.65f, 1f);
        debugVisualizers = new DebugVisualizers();
        frozenObserver = null;
    }

    /**
     * What the drawn scene color is multiplied by before tone mapping. Without adapting, the
     * exposure value's multiplier with the pre-exposure taken back out; while adapting, only the
     * compensation, since the adapted exposure is measured from the pre-exposed color itself.
     *
     * @return The multiplier.
     */
    public float displayExposure() {
        if (autoExposure) {
            return (float) Math.pow(2, exposureCompensation);
        }
        return LightUnits.exposure(exposureValue) / preExposure;
    }

    /**
     * The point of view for deciding what is visible, which level of detail to draw, and what to
     * stream in. Follows the camera unless it was frozen.
     *
     * @return The observer.
     * @see Observer
     */
    public Observer getObserver() {
        Observer frozen = frozenObserver;
        if (frozen != null) {
            return frozen;
        }
        // Asked for many times a frame; observers are immutable, so the last serves while the
        // camera stays put
        final Observer last = lastObserver;
        if (last != null && last.matches(camera, projection)) {
            return last;
        }
        final Observer made = new Observer(camera, projection);
        lastObserver = made;
        return made;
    }

    /** The last observer made by {@link #getObserver()}, for when the camera hasn't changed. */
    private volatile Observer lastObserver;

    /**
     * Stop the observer following the camera, keeping its current point of view until {@link
     * #unfreezeObserver()}. Lets the camera inspect culling and streaming decisions from outside.
     */
    public void freezeObserver() {
        frozenObserver = new Observer(camera, projection);
    }

    /** Make the observer follow the camera again. */
    public void unfreezeObserver() {
        frozenObserver = null;
    }

    /**
     * Whether the observer is frozen.
     *
     * @return True if the observer is not following the camera.
     */
    public boolean isObserverFrozen() {
        return frozenObserver != null;
    }

    /**
     * Add a model to the model map. A different model already added with the same ID is replaced,
     * and its GPU resources are released.
     *
     * @param model The model to add.
     */
    public void addModel(@NonNull Model model) {
        Model previous = modelMap.put(model.getId(), model);
        if (previous != null && previous != model) {
            releaseResources(previous);
        }
    }

    /**
     * Remove a model from the scene, releasing its GPU resources as replacing it would. Nothing
     * happens if no model has the ID. Render thread only.
     *
     * @param id The model's ID.
     */
    public void removeModel(@NonNull String id) {
        Model previous = modelMap.remove(id);
        if (previous != null) {
            releaseResources(previous);
        }
    }

    /**
     * Queue up deletion of a model's GPU resources: its instances, its meshes in the shared
     * geometry buffers, and its own buffers.
     *
     * @param model The model.
     */
    private static void releaseResources(@NonNull Model model) {
        DeletionQueue deletionQueue = GraphicsManager.getDeletionQueue();
        Consumer<SharedBuffer> delete =
                buffer -> {
                    if (buffer != null) {
                        deletionQueue.add(buffer);
                    }
                };
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        GeometryArena geometry = renderer == null ? null : renderer.getState().geometry;
        InstanceTable instances = renderer == null ? null : renderer.getState().instances;
        if (instances != null) {
            // Whichever plugins placed them, the scene no longer draws them
            instances.getRegistry().removeAllOf(model).forEach(instances::retire);
        }
        delete.accept(model.getAnimationBuffer());
        delete.accept(model.getEntityAnimationOffsetsBuffer());
        for (MeshData mesh : model.getMeshDataList()) {
            delete.accept(mesh.getBoneWeightBuffer());
            delete.accept(mesh.getAnimationTargetBuffer());
            if (geometry != null) {
                // Whichever plugin registered it, the scene no longer draws it
                geometry.release(mesh.getMesh());
            }
        }
    }

    /**
     * Queue up deletion of every GPU resource the scene owns, which are the models' meshes and
     * buffers, and the material buffers. Textures belong to the plugins that loaded them. The scene
     * should not be rendered afterward.
     */
    public void cleanup() {
        for (Model model : modelMap.values()) {
            releaseResources(model);
        }
        modelMap.clear();
        materialCache.cleanup();
        setSkyboxTexture(null);
    }

    /**
     * Change how far the view reaches.
     *
     * @param viewDistance The view distance, in meters, kept within the far plane.
     * @throws IllegalArgumentException If it isn't positive.
     */
    public void setViewDistance(double viewDistance) {
        if (viewDistance <= 0) {
            throw new IllegalArgumentException("The view distance must be positive");
        }
        this.viewDistance = Math.min(viewDistance, Projection.Z_FAR);
    }

    /**
     * Update the projection matrix for when the screen is resized.
     *
     * @param width The new screen width, in pixels.
     * @param height The new screen height, in pixels.
     */
    public void resize(int width, int height) {
        projection.updateProjMatrix(width, height);
    }

    /**
     * Set the new skybox texture. The scene doesn't take ownership: the plugin that loaded the
     * texture releases it.
     *
     * @param texture The texture, or null if the skybox should be untextured.
     */
    public void setSkyboxTexture(TextureHandle texture) {
        setSkyboxTexture(texture, SkyboxLayout.CUBE_FACES);
    }

    /**
     * Set the new skybox texture and how it lays the sky out, such as an HDR environment map. The
     * scene doesn't take ownership: the plugin that loaded the texture releases it.
     *
     * @param texture The texture, or null if the skybox should be untextured.
     * @param layout How the texture lays the sky out.
     */
    public void setSkyboxTexture(TextureHandle texture, @NonNull SkyboxLayout layout) {
        skyboxLayout = layout;
        skyboxTexture = texture;
    }
}
