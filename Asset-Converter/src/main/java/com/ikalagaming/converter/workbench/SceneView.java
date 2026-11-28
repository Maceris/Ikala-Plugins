package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.converter.scene.ImportedScene;
import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.converter.scene.PreviewShapes;
import com.ikalagaming.converter.scene.ScenePicker;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.graphics.DebugShapeHandle;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.LightHandle;
import com.ikalagaming.graphics.Lights;
import com.ikalagaming.graphics.PreviewView;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.data.ListClipper;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.ButtonFlags;
import com.ikalagaming.graphics.gui.flags.SliderFlags;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.scene.LightUnits;
import com.ikalagaming.graphics.scene.PhysicalSky;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.SkyboxLayout;
import com.ikalagaming.graphics.scene.debug.DebugBox;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.vulkan.FilterView;
import com.ikalagaming.launcher.PluginFolder;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

/**
 * The 3D viewer: shows a scene placed by a {@link ScenePlacement} in a {@link PreviewView} of its
 * own, lit by its own sun and sky, so the world and the renderer's settings are left alone. Lists
 * the scene's nodes in an outliner, picks meshes with the mouse and outlines them, shows details of
 * the scene and the selection, edits the lighting and, through a {@link MaterialEditor}, the
 * materials, and turns the camera around the scene with the mouse. Render thread only.
 */
public class SceneView implements Viewer {
    /** How wide the view mode list is. */
    private static final float VIEW_MODE_WIDTH = 140;

    /** How wide the lighting preset list is. */
    private static final float PRESET_WIDTH = 160;

    /** The size the preview starts at, before the panel it is drawn in is measured. */
    private static final int START_SIZE = 512;

    /** Behind the preview before its first frame is drawn. */
    private static final int BACKGROUND = Color.rgba(0.08f, 0.08f, 0.09f, 1.0f);

    /** The sun's color. */
    private static final Vector3fc SUN_COLOR = new Vector3f(1, 0.97f, 0.9f);

    /** The sun's direction. */
    private static final Vector3fc SUN_DIRECTION = new Vector3f(0.4f, -0.8f, 0.45f);

    /** The sun's illuminance, in lux: full daylight. */
    private static final float SUN_INTENSITY = LightUnits.DAYLIGHT_LUX;

    /** The ambient light's luminance, in cd/m^2: a bright overcast sky. */
    private static final float AMBIENT_INTENSITY = LightUnits.physical(0.3f);

    /** The selected node's outline. */
    private static final int HIGHLIGHT = Color.rgba(1.0f, 0.75f, 0.2f, 1.0f);

    /** How steeply the camera looks down at a scene it frames. */
    private static final float FRAMING_PITCH = 0.45f;

    /** How far the mouse may move between press and release for it to count as a click. */
    private static final float CLICK_SLOP = 4;

    /** The strongest sun the slider offers, in lux: beyond noon on a mountain. */
    private static final float MAX_SUN = 200_000;

    /** The brightest ambient sky the slider offers, in cd/m^2. */
    private static final float MAX_AMBIENT = 100_000;

    /** The exposure values the slider offers: from a dim night to a snowfield in sun. */
    private static final float MIN_EV = -4;

    private static final float MAX_EV = 18;

    /** The most the imported lights' strength can be scaled by. */
    private static final float MAX_LIGHT_SCALE = 20;

    /** Edits the scene's materials. */
    private final MaterialEditor materials;

    /** Builds and shows level of detail of the scene. */
    private final LodSection lod;

    /** The mesh export section. */
    private final ExportSection export;

    /** Watches particles on the particle stage. */
    @Getter private final ParticleViewer particles;

    /**
     * The grid, bounds and other checks drawn over the scene. -- GETTER -- The overlays.
     *
     * @return The overlays.
     */
    @Getter private final SceneOverlays overlays;

    /**
     * The view beside a pinned image of it. -- GETTER -- The A/B comparison.
     *
     * @return The comparison.
     */
    @Getter private final Comparison comparison;

    /** Whether a left drag that started on the split line is moving it. */
    private boolean splitDragging;

    /** How near the split line, in pixels, a press grabs it. */
    private static final float SPLIT_GRAB = 8;

    /** The selected mesh of the selected node, a scene mesh index, or -1. */
    private int selectedMesh = -1;

    /**
     * What the scene is drawn into, made when the first scene is shown and kept until the viewer
     * closes. -- GETTER -- The preview the scene is drawn into.
     *
     * @return The preview, or null before a scene is shown.
     */
    @Getter private @Nullable PreviewView preview;

    /** Places the scene's lights in the preview. */
    private @Nullable Lights previewLights;

    /** The skybox the preview started with, for going back to the plain sky. */
    private @Nullable TextureHandle plainSkybox;

    /** How the plain skybox lays the sky out. */
    private SkyboxLayout plainSkyboxLayout = SkyboxLayout.CUBE_FACES;

    /** Closes the scene, for the scene section's button. */
    private Runnable closeScene = () -> {};

    /** What can be shown: shapes, stages and context scenes, picked in the toolbar. */
    public interface SceneChoices {
        /**
         * The scenes' names, in the list's order.
         *
         * @return The names.
         */
        String[] names();

        /**
         * The scene shown.
         *
         * @return Its index, or -1 for one opened from a file.
         */
        int current();

        /**
         * Show a scene.
         *
         * @param index Its index.
         */
        void pick(int index);
    }

    /** What the toolbar's scene list offers, or null for no list. */
    private @Nullable SceneChoices choices;

    /** Scratch for the scene list. */
    private final IkInt choice = new IkInt(0);

    /** How wide the scene list is. */
    private static final float SCENE_WIDTH = 170;

    /** The lighting presets, read the first time a scene is shown. */
    private List<LightingPreset> presets;

    /** Their names, for the toolbar's list. */
    private String[] presetNames = new String[0];

    /** The preset the scene is lit by, an index into {@link #presets}. */
    private final IkInt preset = new IkInt(0);

    /** What the view can show in place of the lit scene, in the toolbar's order. */
    static final List<FilterView> VIEW_MODES =
            List.of(
                    FilterView.DEFAULT,
                    FilterView.BASE_COLOR,
                    FilterView.NORMAL,
                    FilterView.ROUGHNESS,
                    FilterView.METALLIC,
                    FilterView.FAR_COLOR,
                    FilterView.MATERIAL,
                    FilterView.DEPTH);

    /** The view modes' names, for the toolbar's list. */
    private static final String[] VIEW_MODE_NAMES =
            VIEW_MODES.stream()
                    .map(mode -> mode == FilterView.DEFAULT ? "Lit" : mode.getDisplayName())
                    .toArray(String[]::new);

    /** Whether the view is supersampled, kept across previews. */
    private boolean supersample;

    /** How much of half its longest side a thumbnail's subject is framed by, a little margin. */
    private static final double THUMBNAIL_FILL = 0.6;

    /** Whether it renders thumbnails: a fixed size, never drawn in a panel, no debug drawing. */
    private boolean thumbnails;

    /**
     * Whether the next thumbnail is a material's ball, framed by its longest side so it fills the
     * square. Anything else is framed by its corners' sphere, as a box's corners reach past that.
     */
    @Setter(AccessLevel.PACKAGE)
    private boolean framingBall;

    /** What the view shows, an index into {@link #VIEW_MODES}. */
    private final IkInt viewMode = new IkInt(0);

    /** The point lights the preset placed, removed when another is picked. */
    private final List<LightHandle> presetLights = new ArrayList<>();

    /** Where the preview was drawn on screen last frame, for picking. */
    private final Vector2f viewOrigin = new Vector2f();

    /** The size the preview was drawn at on screen last frame. */
    private final Vector2f viewSize = new Vector2f(START_SIZE, START_SIZE);

    /** Scratch for the mouse position. */
    private final Vector2f mouse = new Vector2f();

    /** Scratch for sliders. */
    private final float[] value = new float[1];

    /** Scratch for color pickers. */
    private final float[] color3 = new float[3];

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /**
     * The scene shown.
     *
     * @return The placement, or null if none is shown.
     */
    @Getter private @Nullable ScenePlacement placement;

    /** The selected node, or null. */
    private @Nullable Node selected;

    /** The outlines of the selected node's meshes. */
    private final List<DebugShapeHandle> highlights = new ArrayList<>();

    /** Turns the camera around the scene. */
    private final OrbitCamera orbit = new OrbitCamera();

    /** Whether a left drag is turning the camera. */
    private boolean turning;

    /** Whether the left button went down over the preview, so its release may pick. */
    private boolean leftPressed;

    /**
     * Each outliner node's ID and label, made once rather than every frame, since a scene can have
     * thousands of nodes. Cleared when the scene changes.
     */
    private final Map<Node, String[]> outlinerText = new IdentityHashMap<>();

    /** Clips long runs of leaf nodes in the outliner to the rows in view. */
    private final ListClipper outlinerClipper = new ListClipper();

    /** What the current turn swings around. */
    private final Vector3d pivot = new Vector3d();

    /** Filters the outliner by name. */
    private final IkString filter = new IkString(64);

    /** The environment map the sky shows, which the workbench owns, or null for the plain sky. */
    private TextureHandle sky;

    /** What the sky is called, for the lighting panel. */
    private String skyName = "";

    /** Goes back to the plain sky, releasing the map. */
    private Runnable clearSky = () -> {};

    /** How far texture loading has come, for the details. */
    private String textureProgress = "";

    /** Scratch for bounds. */
    private final Vector3d min = new Vector3d();

    /** Scratch for bounds. */
    private final Vector3d max = new Vector3d();

    /**
     * Set up the view; nothing changes until a scene is shown.
     *
     * @param graphics The plugin's graphics context.
     * @param materials Edits the scene's materials.
     */
    public SceneView(@NonNull GraphicsContext graphics, @NonNull MaterialEditor materials) {
        this.graphics = graphics;
        this.materials = materials;
        lod = new LodSection(graphics);
        export = new ExportSection(graphics);
        particles = new ParticleViewer(graphics);
        overlays = new SceneOverlays(graphics);
        comparison = new Comparison(graphics.textures(), graphics.ui());
    }

    /**
     * Render thumbnails rather than show in a panel: a square of a fixed size, supersampled so a
     * still has no jitter, without the debug outline. Before anything is shown.
     *
     * @param size The side, in pixels.
     */
    void useForThumbnails(int size) {
        thumbnails = true;
        supersample = true;
        viewSize.set(size, size);
    }

    /**
     * Open a material file onto every mesh of the scene's first node that has any, as the shape of
     * a preview scene is, and select that node. Render thread, with a scene shown.
     *
     * @param file The material, binary or text.
     * @return True if it was read and put on a mesh.
     */
    public boolean wearMaterial(@NonNull Path file) {
        final ScenePlacement shown = placement;
        if (shown == null) {
            return false;
        }
        final Material material = materials.open(shown, file);
        if (material == null) {
            return false;
        }
        for (Node node : shown.getImported().root().children()) {
            if (node.meshes().length > 0) {
                for (int mesh : node.meshes()) {
                    shown.assign(node, mesh, material);
                }
                select(node, -1);
                return true;
            }
        }
        return false;
    }

    /** The material a texture graph's maps were last put on, kept to change in place. */
    private @Nullable Material graphMaterial;

    /** The scene {@link #graphMaterial} was added to. */
    private @Nullable ScenePlacement graphMaterialPlacement;

    /**
     * Put textures, like a texture graph's live maps, on every mesh of the scene's first node that
     * has any, in a white material, made once per scene and changed in place after. Render thread,
     * with a scene shown.
     *
     * @param color Base color, sRGB, or null.
     * @param normal The normal map, or null.
     * @param orm Packed occlusion, roughness and metallic, or null.
     * @return True if a mesh took them.
     */
    public boolean wearTextures(
            @Nullable TextureHandle color,
            @Nullable TextureHandle normal,
            @Nullable TextureHandle orm) {
        final ScenePlacement shown = placement;
        if (shown == null) {
            return false;
        }
        final boolean reuse = graphMaterial != null && graphMaterialPlacement == shown;
        final Material material = reuse ? graphMaterial : new Material();
        material.getBaseColor().set(1, 1, 1, 1);
        material.setTexture(color);
        material.setNormalMap(normal);
        material.setOcclusionRoughnessMetal(orm);
        material.setPackedOcclusion(orm != null);
        if (reuse) {
            com.ikalagaming.graphics.GraphicsManager.getScene().getMaterialCache().setDirty(true);
        } else {
            shown.addMaterial("Texture graph", material);
            graphMaterial = material;
            graphMaterialPlacement = shown;
        }
        for (Node node : shown.getImported().root().children()) {
            if (node.meshes().length > 0) {
                for (int mesh : node.meshes()) {
                    shown.assign(node, mesh, material);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Offer scenes to show in the toolbar.
     *
     * @param offered The scenes.
     */
    public void setSceneChoices(@NonNull SceneChoices offered) {
        choices = offered;
    }

    /**
     * Set what the scene section's close button does.
     *
     * @param close Closes the scene.
     */
    public void setCloseScene(@NonNull Runnable close) {
        closeScene = close;
    }

    /**
     * Show a scene, placing it in the preview, made the first time, and framing it.
     *
     * @param newPlacement The scene, ready to place.
     */
    public void show(@NonNull ScenePlacement newPlacement) {
        clear();
        final PreviewView view = openPreview();
        newPlacement.placeIn(view, previewLights);
        placement = newPlacement;
        outlinerText.clear();
        applyPreset();
        useFileSun();
        frame(newPlacement.getImported().root());
        if (PreviewShapes.PARTICLE_STAGE.equals(newPlacement.getImported().file())) {
            particles.start(view);
        }
    }

    /**
     * Light the scene with the file's sun, its first directional light, if it has one: its
     * direction and color, at the usual strength times what the file says.
     */
    private void useFileSun() {
        final ScenePlacement shown = placement;
        final ImportedScene.ImportedLight sun = shown == null ? null : shown.sun();
        if (sun == null) {
            return;
        }
        scene().getSceneLights()
                .setDirLight(
                        new DirectionalLight(
                                new Vector3f(sun.color()),
                                new Vector3f(sun.direction()),
                                SUN_INTENSITY * sun.intensity()));
    }

    /** Take the shown scene out of the preview, if there is one, keeping the preview. */
    public void clear() {
        particles.stop();
        overlays.clear();
        select(null, -1);
        materials.clear();
        lod.close();
        if (placement != null) {
            placement.remove();
            placement = null;
            outlinerText.clear();
        }
        textureProgress = "";
    }

    /** Take the scene out and close the preview. */
    @Override
    public void close() {
        clear();
        comparison.release();
        if (preview != null) {
            preview.close();
            preview = null;
            previewLights = null;
            presetLights.clear();
        }
    }

    /**
     * The lighting presets in the converter's data folder, read once.
     *
     * @return The presets, in their order.
     */
    private List<LightingPreset> presets() {
        if (presets == null) {
            presets =
                    LightingPreset.loadAll(
                            PluginFolder.getResource(
                                            ConverterPlugin.PLUGIN_NAME,
                                            PluginFolder.ResourceType.DATA,
                                            LightingPreset.FOLDER)
                                    .toPath());
            presetNames = presets.stream().map(LightingPreset::name).toArray(String[]::new);
        }
        return presets;
    }

    /**
     * Supersample the view, or go back to temporal anti-aliasing, as the toolbar's box does. Kept
     * for the next scenes shown.
     *
     * @param on Whether to supersample.
     */
    public void setSupersampled(boolean on) {
        supersample = on;
        if (preview != null) {
            preview.setSupersampled(on);
        }
    }

    /**
     * Show the lit scene or what its pixels are lit with, by name, as the toolbar's list does.
     *
     * @param name The mode's name, like "Lit" or "Roughness", ignoring case.
     * @return True if there is such a mode and a scene to show it on.
     */
    public boolean useViewMode(@NonNull String name) {
        for (int i = 0; i < VIEW_MODE_NAMES.length; ++i) {
            if (VIEW_MODE_NAMES[i].equalsIgnoreCase(name) && preview != null) {
                viewMode.set(i);
                preview.setFilterView(VIEW_MODES.get(i));
                return true;
            }
        }
        return false;
    }

    /**
     * Light the scene by a preset, by name, as the toolbar's list does.
     *
     * @param name The preset's name, ignoring case.
     * @return True if there is such a preset and a scene to light.
     */
    public boolean usePreset(@NonNull String name) {
        final List<LightingPreset> all = presets();
        for (int i = 0; i < all.size(); ++i) {
            if (all.get(i).name().equalsIgnoreCase(name)) {
                preset.set(i);
                return applyPreset();
            }
        }
        return false;
    }

    /**
     * Light the scene by the picked preset: its sun, sky, exposure and point lights, these placed
     * around the middle of what is shown.
     *
     * @return True if it was applied: there is a preview and the preset exists.
     */
    private boolean applyPreset() {
        final PreviewView view = preview;
        final Lights lights = previewLights;
        final List<LightingPreset> all = presets();
        if (view == null || lights == null || all.isEmpty()) {
            return false;
        }
        for (LightHandle handle : presetLights) {
            lights.remove(handle);
        }
        presetLights.clear();
        final int index = Math.clamp(preset.get(), 0, all.size() - 1);
        final Vector3d middle = new Vector3d();
        final ScenePlacement shown = placement;
        if (shown != null && shown.getImported().bounds(shown.getImported().root(), min, max)) {
            middle.set(min).add(max).mul(0.5);
        }
        presetLights.addAll(all.get(index).apply(view.getScene(), lights, middle));
        if (sky != null) {
            // An environment map the user picked still lights the scene, in place of the sky
            view.getScene().setPhysicalSky(null);
        }
        return true;
    }

    /**
     * Draw the lighting preset list.
     *
     * @param frame The current frame.
     */
    @Override
    public void drawToolbar(@NonNull UiFrame frame) {
        final SceneChoices offered = choices;
        if (offered != null) {
            final String[] names = offered.names();
            final int current = offered.current();
            IkGui.sameLine();
            IkGui.setNextItemWidth(SCENE_WIDTH);
            choice.set(current);
            final String label = current < 0 ? "From a file" : names[current];
            if (IkGui.beginCombo("##scene-list", label)) {
                for (int i = 0; i < names.length; ++i) {
                    if (IkGui.selectable(names[i] + "##scene" + i, i == current)) {
                        final int picked = i;
                        frame.fire(() -> offered.pick(picked));
                    }
                }
                IkGui.endCombo();
            }
            IkGui.setItemTooltip(
                    "Shapes to try materials on, the particle stage, and scenes to judge a block"
                            + " in: walls, floors and neighbors. Context scenes are .context.yml"
                            + " files in the converter's data folder.");
        }
        if (preview == null) {
            return;
        }
        IkGui.sameLine();
        IkGui.setNextItemWidth(VIEW_MODE_WIDTH);
        if (IkGui.combo("##view-mode", viewMode, VIEW_MODE_NAMES)) {
            preview.setFilterView(VIEW_MODES.get(viewMode.get()));
        }
        IkGui.setItemTooltip(
                "The lit scene, or what each pixel is lit with: its base color, normal,"
                        + " roughness and metalness, the flat color far levels of detail draw,"
                        + " which material it is, or how far away");
        IkGui.sameLine();
        if (IkGui.checkbox("Supersample", preview.isSupersampled())) {
            setSupersampled(!preview.isSupersampled());
        }
        IkGui.setItemTooltip(
                "Draw larger and filter down, without temporal anti-aliasing: a still image with"
                        + " no jitter or ghosting, as far as the window's size allows");
        if (presets().isEmpty()) {
            comparison.drawControls(preview);
            return;
        }
        IkGui.sameLine();
        IkGui.setNextItemWidth(PRESET_WIDTH);
        if (IkGui.combo("##preset", preset, presetNames)) {
            applyPreset();
        }
        IkGui.setItemTooltip(
                "Light the scene the same way each time, in physical units with a fixed"
                        + " exposure, to compare assets. Presets are .lighting.yml files in the"
                        + " converter's data folder.");
        comparison.drawControls(preview);
    }

    /**
     * Make the preview the scene is drawn into, lit by a sun and a bright sky, at the world's
     * exposure, unless it is already open.
     *
     * @return The preview.
     */
    private PreviewView openPreview() {
        if (preview != null) {
            return preview;
        }
        final PreviewView view = graphics.previews().create((int) viewSize.x, (int) viewSize.y);
        // The debug stage draws the selection's outline, which thumbnails leave out
        view.setDrawsDebug(!thumbnails);
        view.setFilterView(VIEW_MODES.get(viewMode.get()));
        view.setSupersampled(supersample);
        previewLights = graphics.previews().lights(view);
        final Scene scene = view.getScene();
        final Scene world = GraphicsManager.getScene();
        if (world != null) {
            scene.setExposureValue(world.getExposureValue());
            scene.setPhysicalSky(world.getPhysicalSky());
        }
        plainSkybox = scene.getSkyboxTexture();
        plainSkyboxLayout = scene.getSkyboxLayout();
        if (sky != null) {
            scene.setSkyboxTexture(sky, SkyboxLayout.EQUIRECTANGULAR);
        }
        scene.getSceneLights()
                .setDirLight(
                        new DirectionalLight(
                                // Copies, since the lighting panel edits the light's in place
                                new Vector3f(SUN_COLOR),
                                new Vector3f(SUN_DIRECTION),
                                SUN_INTENSITY));
        scene.getSceneLights()
                .setAmbientLight(new AmbientLight(new Vector3f(1, 1, 1), AMBIENT_INTENSITY));
        preview = view;
        return view;
    }

    /**
     * The preview's scene.
     *
     * @return The scene.
     * @throws IllegalStateException If no scene was shown yet.
     */
    private Scene scene() {
        final PreviewView view = preview;
        if (view == null) {
            throw new IllegalStateException("No scene is shown");
        }
        return view.getScene();
    }

    @Override
    public String kind() {
        return "3D";
    }

    @Override
    public @Nullable Path file() {
        final ScenePlacement shown = placement;
        return shown == null ? null : shown.getImported().file();
    }

    @Override
    public boolean canCapture() {
        return preview != null && placement != null;
    }

    @Override
    public CompletableFuture<Path> capture(@NonNull Path file) {
        final PreviewView view = preview;
        if (view == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No scene is shown"));
        }
        return view.capture(file);
    }

    /**
     * Note how far texture loading has come.
     *
     * @param progress The progress.
     */
    public void setTextureProgress(@NonNull String progress) {
        textureProgress = progress;
    }

    /**
     * Show an environment map as the sky, lighting the scene, or the plain sky again. The workbench
     * owns the texture, and releases the one before after this.
     *
     * @param texture The equirectangular map, or null for the plain sky.
     * @param name What it is called.
     * @param plainSky Goes back to the plain sky, for the lighting panel's button.
     */
    public void setSky(TextureHandle texture, @NonNull String name, @NonNull Runnable plainSky) {
        clearSky = plainSky;
        sky = texture;
        skyName = name;
        final PreviewView view = preview;
        if (view != null) {
            if (texture != null) {
                view.getScene().setSkyboxTexture(texture, SkyboxLayout.EQUIRECTANGULAR);
            } else {
                view.getScene().setSkyboxTexture(plainSkybox, plainSkyboxLayout);
            }
        }
    }

    /**
     * The bounds a thumbnail frames into {@link #min} and {@link #max}: everything in the scene but
     * the floor, so a preview shape or model fills the square rather than the floor around it.
     *
     * @param scene The scene.
     * @return Whether there is anything but the floor.
     */
    private boolean subjectBounds(ImportedScene scene) {
        final Vector3d partMin = new Vector3d();
        final Vector3d partMax = new Vector3d();
        min.set(Double.POSITIVE_INFINITY);
        max.set(Double.NEGATIVE_INFINITY);
        boolean any = false;
        for (Node child : scene.root().children()) {
            if (!"Floor".equals(child.name()) && scene.bounds(child, partMin, partMax)) {
                min.min(partMin);
                max.max(partMax);
                any = true;
            }
        }
        return any;
    }

    /**
     * Point the camera at a node and everything below it; for a thumbnail, at everything in the
     * scene but the floor.
     *
     * @param node The node.
     */
    private void frame(@NonNull Node node) {
        final ScenePlacement shown = placement;
        if (shown == null
                || !(thumbnails && subjectBounds(shown.getImported())
                        || shown.getImported().bounds(node, min, max))) {
            return;
        }
        final Vector3d center = new Vector3d(min).add(max).mul(0.5);
        // A material's ball fills its square: its longest side is its diameter, so it can't clip
        final double radius =
                Math.max(
                        0.01,
                        thumbnails && framingBall
                                ? Math.max(max.x - min.x, Math.max(max.y - min.y, max.z - min.z))
                                        * THUMBNAIL_FILL
                                : max.distance(min) / 2);
        final var camera = scene().getCamera();
        if (camera.getRotation().x < 0.05f) {
            // Looking level or up, so tip down to see the ground the scene stands on
            camera.setRotation(FRAMING_PITCH, camera.getRotation().y);
        }
        orbit.frame(camera, center, radius);
    }

    /**
     * Select a node, or one of its meshes, outlining what is selected, and edit the mesh's
     * material.
     *
     * @param node The node, or null for none.
     * @param sceneMesh One of its meshes, a scene mesh index, or -1 for the whole node; a node with
     *     one mesh selects that one.
     */
    private void select(@Nullable Node node, int sceneMesh) {
        for (DebugShapeHandle handle : highlights) {
            graphics.debug().remove(handle);
        }
        highlights.clear();
        selected = node;
        selectedMesh = sceneMesh;
        final ScenePlacement shown = placement;
        if (node == null || shown == null) {
            selectedMesh = -1;
            return;
        }
        if (selectedMesh < 0 && node.meshes().length == 1) {
            selectedMesh = node.meshes()[0];
        }
        if (selectedMesh >= 0) {
            materials.select(shown.getMaterials().indexOf(shown.materialOf(node, selectedMesh)));
        }
        final Vector3d scale = node.world().getScale(new Vector3d());
        final Matrix3d rotation = new Matrix3d();
        node.world().get3x3(rotation);
        rotation.scale(1 / scale.x, 1 / scale.y, 1 / scale.z);
        final Quaterniond orientation = rotation.getNormalizedRotation(new Quaterniond());
        for (int index : node.meshes()) {
            if (sceneMesh >= 0 && index != sceneMesh) {
                continue;
            }
            final MeshData data = shown.getImported().meshes().get(index).data();
            final Vector3d localCenter =
                    new Vector3d(data.getAabbMin()).add(new Vector3d(data.getAabbMax())).mul(0.5);
            final Vector3d half =
                    new Vector3d(data.getAabbMax()).sub(new Vector3d(data.getAabbMin())).mul(0.5);
            half.mul(scale);
            final Vector3d center = node.world().transformPosition(localCenter);
            highlights.add(
                    graphics.debug()
                            .add(
                                    new DebugBox(center, half, orientation, HIGHLIGHT, true),
                                    preview));
        }
    }

    /**
     * Draw the outliner: the scene's nodes as a tree, filtered by name. Clicking selects a node,
     * double clicking frames it.
     *
     * @param frame The current frame.
     */
    public void drawOutliner(@NonNull UiFrame frame) {
        final ScenePlacement shown = placement;
        if (shown == null) {
            IkGui.textDisabled("Open a model to see its nodes");
            return;
        }
        IkGui.setNextItemWidth(-1);
        IkGui.inputTextWithHint("##filter", "Filter by name", filter);
        final String query = filter.get().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            drawNode(shown, shown.getImported().root());
        } else {
            drawMatches(shown, shown.getImported().root(), query);
        }
    }

    /**
     * Draw a node of the tree and, if it is open, its children.
     *
     * @param shown The scene.
     * @param node The node.
     */
    private void drawNode(ScenePlacement shown, Node node) {
        int flags = TreeNodeFlags.OPEN_ON_ARROW | TreeNodeFlags.SPAN_AVAIL_WIDTH;
        if (node.children().isEmpty()) {
            flags |= TreeNodeFlags.LEAF | TreeNodeFlags.NO_TREE_PUSH_ON_OPEN;
        }
        if (node == selected) {
            flags |= TreeNodeFlags.SELECTED;
        }
        if (node.parent() == null) {
            flags |= TreeNodeFlags.DEFAULT_OPEN;
        }
        final String[] text = outlinerText(node);
        final boolean open = IkGui.treeNodeEx(text[0], flags, text[1]);
        clicked(node);
        if (open && !node.children().isEmpty()) {
            final List<Node> children = node.children();
            if (allLeaves(children)) {
                // Rows of the same height: only those in view are drawn, as a scene can have
                // thousands of meshes under one node
                outlinerClipper.begin(children.size());
                while (outlinerClipper.step()) {
                    for (int i = outlinerClipper.displayStart;
                            i < outlinerClipper.displayEnd;
                            ++i) {
                        drawNode(shown, children.get(i));
                    }
                }
                outlinerClipper.end();
            } else {
                for (Node child : children) {
                    drawNode(shown, child);
                }
            }
            IkGui.treePop();
        }
    }

    /**
     * Whether nodes have no children of their own, so each is one row of the outliner.
     *
     * @param nodes The nodes.
     * @return True if none has children.
     */
    private static boolean allLeaves(@NonNull List<Node> nodes) {
        for (Node node : nodes) {
            if (!node.children().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Draw every node whose name matches, as a flat list.
     *
     * @param shown The scene.
     * @param node Where to start looking.
     * @param query The lower case text to look for.
     */
    private void drawMatches(ScenePlacement shown, Node node, String query) {
        if (node.name().toLowerCase(Locale.ROOT).contains(query)) {
            int flags =
                    TreeNodeFlags.LEAF
                            | TreeNodeFlags.NO_TREE_PUSH_ON_OPEN
                            | TreeNodeFlags.SPAN_AVAIL_WIDTH;
            if (node == selected) {
                flags |= TreeNodeFlags.SELECTED;
            }
            final String[] text = outlinerText(node);
            IkGui.treeNodeEx(text[0], flags, text[1]);
            clicked(node);
        }
        for (Node child : node.children()) {
            drawMatches(shown, child, query);
        }
    }

    /**
     * Select or frame the node just drawn, if it was clicked.
     *
     * @param node The node.
     */
    private void clicked(Node node) {
        if (IkGui.isItemClicked() && !IkGui.isItemToggledOpen()) {
            if (IkGui.isMouseDoubleClicked(MouseButton.LEFT)) {
                frame(node);
            }
            if (node != selected) {
                select(node, -1);
            }
        }
    }

    /**
     * A node's ID and label in the outliner, made the first time they are asked for.
     *
     * @param node The node.
     * @return The ID, then the label.
     */
    private String[] outlinerText(Node node) {
        return outlinerText.computeIfAbsent(
                node,
                n -> new String[] {Integer.toHexString(System.identityHashCode(n)), label(n)});
    }

    /**
     * A node's label in the outliner: its name, and how many meshes it draws.
     *
     * @param node The node.
     * @return The label.
     */
    private static String label(Node node) {
        if (node.meshes().length == 0) {
            return node.name();
        }
        return node.name()
                + "  ("
                + node.meshes().length
                + (node.meshes().length == 1 ? " mesh)" : " meshes)");
    }

    /**
     * Draw details of the scene and the selected node, and buttons to frame and close.
     *
     * @param frame The current frame.
     */
    @Override
    public void drawProperties(@NonNull UiFrame frame) {
        final ScenePlacement shown = placement;
        if (shown == null) {
            IkGui.textDisabled("No scene open");
            return;
        }
        final ImportedScene imported = shown.getImported();
        if (particles.isActive()
                && IkGui.collapsingHeader("Particles###particles", TreeNodeFlags.DEFAULT_OPEN)) {
            particles.draw();
        }
        if (IkGui.collapsingHeader("Scene###scene", TreeNodeFlags.DEFAULT_OPEN)) {
            drawScene(frame, shown, imported, closeScene);
        }
        if (IkGui.collapsingHeader("Lighting###lighting", TreeNodeFlags.DEFAULT_OPEN)) {
            drawLighting(shown);
        }
        if (IkGui.collapsingHeader("Overlays###overlays") && preview != null) {
            overlays.drawControls(preview);
        }
        if (IkGui.collapsingHeader("Selected###selected", TreeNodeFlags.DEFAULT_OPEN)) {
            final Node node = selected;
            if (node == null) {
                IkGui.textDisabled("Click a mesh in the scene, or pick a node in the outliner");
            } else {
                drawNode(shown, imported, node);
            }
        }
        if (IkGui.collapsingHeader("Material###material", TreeNodeFlags.DEFAULT_OPEN)) {
            materials.draw(frame, shown, selected, selectedMesh);
        }
        if (IkGui.collapsingHeader("Level of detail###lod", TreeNodeFlags.DEFAULT_OPEN)) {
            lod.draw(shown, selected, preview);
        }
        if (IkGui.collapsingHeader("Export###export")) {
            export.draw(shown, selected);
        }
    }

    /**
     * Draw the scene's details, and buttons to frame and close it.
     *
     * @param frame The current frame.
     * @param shown The scene.
     * @param imported What was read.
     * @param closeScene Closes the scene.
     */
    private void drawScene(
            UiFrame frame, ScenePlacement shown, ImportedScene imported, Runnable closeScene) {
        if (IkGui.beginTable("scene", 2, TableFlags.ROW_BACKGROUND)) {
            row("File", imported.file().getFileName().toString());
            row("Nodes", String.format("%,d", imported.nodeCount()));
            row("Meshes", String.format("%,d", imported.meshes().size()));
            row("Materials", String.format("%,d", imported.materials().size()));
            row("Triangles", String.format("%,d", imported.drawnTriangles()));
            if (imported.drawnTriangles() != imported.uniqueTriangles()) {
                row("Stored once", String.format("%,d", imported.uniqueTriangles()));
            }
            row(
                    "Instances",
                    String.format(
                            "%,d of %,d models, %,d baked",
                            shown.instanceCount(), shown.modelCount(), shown.getBakedNodes()));
            row("Unit", unitText(imported.unitScale()));
            row("Up axis", "xyz".charAt(Math.clamp(imported.upAxis(), 0, 2)) + ", made y");
            row("Read in", String.format("%,d ms", imported.readMillis()));
            row("Textures", textureProgress.isEmpty() ? "none" : textureProgress);
            row("Lights", lightText(imported));
            IkGui.endTable();
        }
        if (!imported.warnings().isEmpty()
                && IkGui.collapsingHeader(
                        "Warnings (" + imported.warnings().size() + ")###warnings")) {
            for (String warning : imported.warnings()) {
                IkGui.textWrapped(warning);
            }
        }
        if (IkGui.checkbox("Normal maps point green down (DirectX)", shown.isNormalGreenDown())) {
            shown.setNormalGreenDown(!shown.isNormalGreenDown());
        }
        IkGui.setItemTooltip(
                "Guessed from the file: on for FBX, off for the rest. Bumps look lit from below"
                        + " when it is wrong.");
        if (IkGui.button("Frame scene")) {
            frame(imported.root());
        }
        IkGui.sameLine();
        if (IkGui.button("Close scene")) {
            frame.fire(closeScene);
        }
        IkGui.textDisabled("Click picks, left drag turns, middle drag pans, wheel zooms");
    }

    /**
     * Describe the file's lights.
     *
     * @param imported What was read.
     * @return For example "1 directional, 4 point".
     */
    static String lightText(@NonNull ImportedScene imported) {
        final int[] counts = new int[ImportedScene.LightType.values().length];
        for (ImportedScene.ImportedLight light : imported.lights()) {
            ++counts[light.type().ordinal()];
        }
        final StringBuilder text = new StringBuilder();
        for (ImportedScene.LightType type : ImportedScene.LightType.values()) {
            if (counts[type.ordinal()] > 0) {
                if (!text.isEmpty()) {
                    text.append(", ");
                }
                text.append(counts[type.ordinal()])
                        .append(' ')
                        .append(type.name().toLowerCase(Locale.ROOT));
            }
        }
        return text.isEmpty() ? "none" : text.toString();
    }

    /**
     * Draw the lighting: the sun's direction, color and strength, the ambient light, the exposure,
     * and how strong the file's lights shine.
     *
     * @param shown The scene.
     */
    private void drawLighting(ScenePlacement shown) {
        final Scene scene = scene();
        final DirectionalLight sun = scene.getSceneLights().getDirLight();
        // The direction the light travels, down for a sun overhead
        final Vector3f direction = sun.getDirection();
        final float length = Math.max(1e-6f, direction.length());
        final float elevation = (float) Math.toDegrees(Math.asin(-direction.y / length));
        final float azimuth = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
        final float[] angles = {azimuth, elevation};
        if (IkGui.sliderFloat2("Sun around, up", angles, -180, 180)) {
            final double around = Math.toRadians(angles[0]);
            final double up = Math.toRadians(Math.clamp(angles[1], 1, 90));
            direction.set(
                    (float) (Math.cos(up) * Math.sin(around)),
                    (float) -Math.sin(up),
                    (float) (Math.cos(up) * Math.cos(around)));
        }
        color3[0] = sun.getColor().x;
        color3[1] = sun.getColor().y;
        color3[2] = sun.getColor().z;
        if (IkGui.colorEdit3("Sun color", color3)) {
            sun.getColor().set(color3[0], color3[1], color3[2]);
        }
        value[0] = sun.getIntensity();
        if (IkGui.sliderFloat("Sun (lux)", value, 0, MAX_SUN, "%.0f", SliderFlags.LOGARITHMIC)) {
            sun.setIntensity(value[0]);
        }
        if (shown.sun() != null) {
            if (IkGui.button("Use the file's sun")) {
                useFileSun();
            }
        }

        final AmbientLight ambient = scene.getSceneLights().getAmbientLight();
        color3[0] = ambient.getColor().x;
        color3[1] = ambient.getColor().y;
        color3[2] = ambient.getColor().z;
        if (IkGui.colorEdit3("Ambient color", color3)) {
            ambient.getColor().set(color3[0], color3[1], color3[2]);
        }
        value[0] = ambient.getIntensity();
        if (IkGui.sliderFloat(
                "Sky (cd/m^2)", value, 0, MAX_AMBIENT, "%.0f", SliderFlags.LOGARITHMIC)) {
            ambient.setIntensity(value[0]);
        }
        value[0] = scene.getExposureValue();
        if (IkGui.sliderFloat("Exposure (EV100)", value, MIN_EV, MAX_EV, "%.1f")) {
            scene.setExposureValue(value[0]);
        }
        IkGui.setItemTooltip("Fixed, so two assets compare under the same exposure");
        if (IkGui.checkbox("Physical sky", scene.getPhysicalSky() != null)) {
            scene.setPhysicalSky(scene.getPhysicalSky() == null ? new PhysicalSky() : null);
        }
        if (sky != null) {
            IkGui.text("Sky: " + skyName);
            IkGui.sameLine();
            if (IkGui.button("Plain sky")) {
                clearSky.run();
            }
            IkGui.setItemTooltip(
                    "Go back to the plain sky. The ambient light still sets how bright the map"
                            + " lights the scene; the physical sky takes its place while on.");
        } else {
            IkGui.textDisabled("Open an HDR image and use it as the sky to light by it");
        }
        IkGui.setItemTooltip(
                "Earth's atmosphere lit by the sun as the sky, lighting the scene as bright as the"
                        + " sun makes it. The ambient light is not used while it is on.");
        if (shown.lightCount() > 0) {
            value[0] = shown.getLightScale();
            if (IkGui.sliderFloat("File's lights strength", value, 0, MAX_LIGHT_SCALE, "%.2fx")) {
                shown.setLightScale(value[0]);
            }
            IkGui.setItemTooltip(
                    "Files fold their lights' strength into their color in their own units, so"
                            + " this scales what was read.");
        }
    }

    /**
     * Draw a selected node's details.
     *
     * @param shown The scene.
     * @param imported What was read.
     * @param node The node.
     */
    private void drawNode(ScenePlacement shown, ImportedScene imported, Node node) {
        final Vector3d position = node.world().getTranslation(new Vector3d());
        final Vector3d scale = node.world().getScale(new Vector3d());
        final Matrix3d rotation = new Matrix3d();
        node.world().get3x3(rotation);
        rotation.scale(1 / scale.x, 1 / scale.y, 1 / scale.z);
        final Vector3d angles = rotation.getEulerAnglesXYZ(new Vector3d());
        if (IkGui.beginTable("node", 2, TableFlags.ROW_BACKGROUND)) {
            row("Name", node.name());
            row("Path", path(node));
            row("Position", String.format("%.3f, %.3f, %.3f", position.x, position.y, position.z));
            row(
                    "Rotation",
                    String.format(
                            "%.1f, %.1f, %.1f degrees",
                            Math.toDegrees(angles.x),
                            Math.toDegrees(angles.y),
                            Math.toDegrees(angles.z)));
            row("Scale", String.format("%.4f, %.4f, %.4f", scale.x, scale.y, scale.z));
            row(
                    "Drawn as",
                    node.meshes().length == 0
                            ? "nothing"
                            : shown.isBaked(node)
                                    ? "baked into world space"
                                    : shown.instancesOf(node).size() + " instances");
            row("Children", Integer.toString(node.children().size()));
            IkGui.endTable();
        }
        if (IkGui.button("Frame selected")) {
            frame(node);
        }
        for (int index : node.meshes()) {
            final ImportedScene.Mesh mesh = imported.meshes().get(index);
            final ImportedScene.ImportedMaterial material =
                    imported.materials().get(mesh.material());
            if (IkGui.selectable(
                    String.format(
                                    "%s, %,d triangles, %s",
                                    mesh.name(), mesh.triangles(), material.name())
                            + "##mesh"
                            + index,
                    index == selectedMesh)) {
                select(node, index);
            }
            for (var texture : material.textures().entrySet()) {
                IkGui.textDisabled(
                        "    "
                                + texture.getKey().name().toLowerCase(Locale.ROOT)
                                + ": "
                                + (texture.getValue().found() == null
                                        ? "missing " + texture.getValue().named()
                                        : texture.getValue().found().getFileName()));
            }
        }
    }

    /**
     * The names of a node's parents and itself, from the root.
     *
     * @param node The node.
     * @return For example "Root / Building / Window".
     */
    private static String path(Node node) {
        StringBuilder text = new StringBuilder(node.name());
        for (Node parent = node.parent(); parent != null; parent = parent.parent()) {
            text.insert(0, parent.name() + " / ");
        }
        return text.toString();
    }

    /**
     * Describe the scale applied for the file's unit.
     *
     * @param scale Meters per file unit.
     * @return For example "meters" or "centimeters, scaled by 0.01".
     */
    static String unitText(double scale) {
        if (Math.abs(scale - 1) < 1e-9) {
            return "meters";
        }
        if (Math.abs(scale - 0.01) < 1e-9) {
            return "centimeters, scaled by 0.01";
        }
        return String.format("scaled by %s", scale);
    }

    /**
     * Draw one two-column table row.
     *
     * @param name The first column.
     * @param value The second column.
     */
    private static void row(String name, String value) {
        IkGui.tableNextRow();
        IkGui.tableNextColumn();
        IkGui.textUnformatted(name);
        IkGui.tableNextColumn();
        IkGui.textUnformatted(value);
    }

    /**
     * Draw the preview, filling the immediate area, and turn the camera around the scene with the
     * mouse over it: left drag turns, middle drag pans, and the wheel zooms. A left click picks.
     *
     * @param frame The current frame.
     */
    @Override
    public void drawContent(@NonNull UiFrame frame) {
        final PreviewView view = preview;
        if (view == null || placement == null) {
            IkGui.textDisabled(
                    "Pick a scene above, or open a model, a material, a look or a container");
            return;
        }
        IkGui.getCursorScreenPos(viewOrigin);
        IkGui.getContentRegionAvailable(viewSize);
        viewSize.x = Math.max(1, (float) Math.floor(viewSize.x));
        viewSize.y = Math.max(1, (float) Math.floor(viewSize.y));
        view.resize((int) viewSize.x, (int) viewSize.y);
        // Drawn every frame while shown, as the world was: materials, textures and lights change
        // in many places, and the editor draws nothing else
        view.requestFrames(2);
        plainSkyFromAmbient(view.getScene());

        final DrawList drawList = IkGui.getWindowDrawList();
        final float right = viewOrigin.x + viewSize.x;
        final float bottom = viewOrigin.y + viewSize.y;
        overlays.update(placement, view);
        final TextureHandle texture = view.getTexture();
        IkGui.getMousePos(mouse);
        if (texture == null) {
            drawList.addRectFilled(viewOrigin.x, viewOrigin.y, right, bottom, BACKGROUND);
        } else {
            comparison.draw(
                    drawList,
                    texture,
                    viewOrigin.x,
                    viewOrigin.y,
                    viewSize.x,
                    viewSize.y,
                    mouse.x,
                    splitDragging);
        }
        // Catches the mouse, so dragging turns the camera instead of moving the window
        IkGui.invisibleButton(
                "scene",
                viewSize.x,
                viewSize.y,
                ButtonFlags.MOUSE_BUTTON_LEFT | ButtonFlags.MOUSE_BUTTON_MIDDLE);
        handleMouse(view, IkGui.isItemHovered(), IkGui.isItemActive());
    }

    /**
     * Draw the plain sky, with neither a physical sky nor a map, as bright as the ambient light, so
     * it follows the exposure like everything it lights: black with no sky light, as in a cave. The
     * renderer draws a plain sky at a fixed color otherwise.
     *
     * @param scene The preview's scene.
     */
    static void plainSkyFromAmbient(@NonNull Scene scene) {
        if (scene.getPhysicalSky() != null || scene.getSkyboxTexture() != null) {
            return;
        }
        final AmbientLight ambient = scene.getSceneLights().getAmbientLight();
        final float scale = ambient.getIntensity() * scene.getPreExposure();
        // The sky shader decodes the color as sRGB
        scene.getSkyboxDiffuse()
                .set(
                        encodeSrgb(ambient.getColor().x * scale),
                        encodeSrgb(ambient.getColor().y * scale),
                        encodeSrgb(ambient.getColor().z * scale),
                        1);
    }

    /**
     * Encode a linear value as sRGB, beyond 1 too, unlike {@link TextureViewer#toSrgb}, since a sky
     * can be brighter than white before the exposure.
     *
     * @param linear The linear value.
     * @return The sRGB value.
     */
    static float encodeSrgb(float linear) {
        if (linear <= 0.003_130_8f) {
            return Math.max(0, linear * 12.92f);
        }
        return (float) (1.055 * Math.pow(linear, 1 / 2.4) - 0.055);
    }

    /**
     * Turn, pan and zoom the camera, and pick, from the mouse over the preview.
     *
     * @param view The preview.
     * @param hovered Whether the mouse is over the preview.
     * @param active Whether a press that started over the preview is held.
     */
    private void handleMouse(PreviewView view, boolean hovered, boolean active) {
        final IkIO io = IkGui.getIO();
        final var camera = view.getScene().getCamera();
        orbit.follow(camera);
        if (hovered && IkGui.isMouseClicked(MouseButton.LEFT)) {
            leftPressed = true;
            // A press on the split line moves it rather than turning or picking
            final float splitX = viewOrigin.x + viewSize.x * comparison.getSplit();
            splitDragging =
                    comparison.getMode() == Comparison.Mode.SPLIT
                            && Math.abs(mouse.x - splitX) <= SPLIT_GRAB;
        }
        if (splitDragging) {
            if (!IkGui.isMouseDown(MouseButton.LEFT)) {
                splitDragging = false;
                leftPressed = false;
            }
            return;
        }
        // A press that never became a drag is a click; turning is still set on the release frame
        if (IkGui.isMouseReleased(MouseButton.LEFT)) {
            if (leftPressed && !turning && hovered) {
                pick();
            }
            leftPressed = false;
        }
        // Only a drag past the click slop turns or pans, so a click that wobbles a pixel or two
        // picks without moving the camera
        if (active && leftPressed && IkGui.isMouseDragging(MouseButton.LEFT, CLICK_SLOP)) {
            if (!turning) {
                turning = true;
                pivotPoint(pivot);
            }
            orbit.turn(camera, pivot, io.mouseDelta.x, io.mouseDelta.y);
        } else {
            turning = false;
            if (active && IkGui.isMouseDragging(MouseButton.MIDDLE, CLICK_SLOP)) {
                orbit.pan(camera, io.mouseDelta.x, io.mouseDelta.y, viewSize.y);
            }
        }
        if (hovered && io.mouseWheel != 0) {
            orbit.zoom(camera, io.mouseWheel);
        }
    }

    /**
     * What a turn swings around: the middle of what is selected; with nothing selected, the surface
     * where the drag started, so turning from inside a scene keeps what is under the mouse in
     * place; or failing that, the middle of the whole scene.
     *
     * @param out Where to put it.
     */
    private void pivotPoint(@NonNull Vector3d out) {
        final ScenePlacement shown = placement;
        if (shown == null) {
            out.set(orbit.getTarget());
            return;
        }
        final ImportedScene imported = shown.getImported();
        if (selected == null) {
            // Where the press was, before the drag moved it past the slop
            final var dragged = IkGui.getMouseDragDelta(MouseButton.LEFT, 0);
            if (hitAt(IkGui.getMousePos().x - dragged.x, IkGui.getMousePos().y - dragged.y, out)
                    != null) {
                return;
            }
        }
        final Node around = selected == null ? imported.root() : selected;
        if (imported.bounds(around, min, max) || imported.bounds(imported.root(), min, max)) {
            out.set(min).add(max).mul(0.5);
        } else {
            out.set(orbit.getTarget());
        }
    }

    /**
     * Select the mesh under the mouse, or nothing if there is none, by casting a ray from the
     * camera through it.
     */
    private void pick() {
        if (placement == null) {
            return;
        }
        IkGui.getMousePos(mouse);
        final ScenePicker.Hit hit = hitAt(mouse.x, mouse.y, new Vector3d());
        if (hit == null) {
            select(null, -1);
        } else {
            select(hit.node(), hit.sceneMesh());
        }
    }

    /**
     * Find the mesh at a point on the screen, by casting a ray from the camera through it.
     *
     * @param x The point across the window, in pixels.
     * @param y The point down the window, in pixels.
     * @param point Receives where the ray hits, if it does.
     * @return The hit, or null if the ray hits nothing or no scene is shown.
     */
    @Nullable private ScenePicker.Hit hitAt(double x, double y, @NonNull Vector3d point) {
        final ScenePlacement shown = placement;
        if (shown == null) {
            return null;
        }
        final PreviewView view = preview;
        if (view == null) {
            return null;
        }
        final var camera = view.getScene().getCamera();
        // Measured from the preview's corner on screen, which the mouse shares
        final Vector3d direction =
                rayDirection(
                        (x - viewOrigin.x) / viewSize.x,
                        (y - viewOrigin.y) / viewSize.y,
                        viewSize.x / Math.max(1, viewSize.y),
                        camera.getRotation().x,
                        camera.getRotation().y);
        final ScenePicker.Hit hit =
                ScenePicker.pick(shown.getImported(), camera.getPosition(), direction);
        if (hit != null) {
            point.set(direction).mul(hit.distance()).add(camera.getPosition());
        }
        return hit;
    }

    /**
     * The direction from the camera through a point on the screen, in the world.
     *
     * @param x The point across the screen, 0 at the left to 1 at the right.
     * @param y The point down the screen, 0 at the top to 1 at the bottom.
     * @param aspect The screen's width over its height.
     * @param pitch The camera's pitch, positive looking down.
     * @param yaw The camera's yaw.
     * @return The direction, normalized.
     */
    static Vector3d rayDirection(double x, double y, double aspect, double pitch, double yaw) {
        final double tan = Math.tan(OrbitCamera.FIELD_OF_VIEW / 2);
        // In view space: x right, y up, looking down -z
        final Vector3d view = new Vector3d((2 * x - 1) * tan * aspect, (1 - 2 * y) * tan, -1);
        // The camera's view is rotated by pitch then yaw; undo it
        return new Matrix3d().rotateX(pitch).rotateY(yaw).transpose().transform(view).normalize();
    }
}
