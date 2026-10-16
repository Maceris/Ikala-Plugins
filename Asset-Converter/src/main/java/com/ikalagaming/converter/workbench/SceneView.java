package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.scene.ImportedScene;
import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.converter.scene.ScenePicker;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.graphics.CameraManager;
import com.ikalagaming.graphics.DebugShapeHandle;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.debug.DebugBox;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.ui.UiFrame;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Shows a scene put in the world by a {@link ScenePlacement}: switches the renderer to draw the
 * scene with a sun, lists the scene's nodes in an outliner, picks meshes with the mouse and
 * outlines them, shows details of the scene and the selection, edits the lighting and, through a
 * {@link MaterialEditor}, the materials, and turns the camera around the scene with the mouse.
 * Render thread only.
 */
public class SceneView {
    /** The window that reads the mouse for the orbit camera, drawing nothing. */
    static final String INPUT_WINDOW = "Workbench scene input";

    /** The startup property that places the camera once a scene is shown, for captures. */
    static final String CAMERA_PROPERTY = "ikala.test.camera";

    /** The sun's color. */
    private static final Vector3f SUN_COLOR = new Vector3f(1, 0.97f, 0.9f);

    /** The sun's direction. */
    private static final Vector3f SUN_DIRECTION = new Vector3f(0.4f, -0.8f, 0.45f);

    /** The sun's intensity. */
    private static final float SUN_INTENSITY = 3;

    /** The ambient light's intensity. */
    private static final float AMBIENT_INTENSITY = 0.3f;

    /** The selected node's outline. */
    private static final int HIGHLIGHT = Color.rgba(1.0f, 0.75f, 0.2f, 1.0f);

    /** How steeply the camera looks down at a scene it frames. */
    private static final float FRAMING_PITCH = 0.45f;

    /** How far the mouse may move between press and release for it to count as a click. */
    private static final float CLICK_SLOP = 4;

    /** The strongest sun the slider offers. */
    private static final float MAX_SUN = 20;

    /** The strongest ambient light the slider offers. */
    private static final float MAX_AMBIENT = 3;

    /** The brightest exposure the slider offers. */
    private static final float MAX_EXPOSURE = 8;

    /** The most the imported lights' strength can be scaled by. */
    private static final float MAX_LIGHT_SCALE = 20;

    /** Edits the scene's materials. */
    private final MaterialEditor materials;

    /** The selected mesh of the selected node, a scene mesh index, or -1. */
    private int selectedMesh = -1;

    /** The exposure before entering. */
    private float previousExposure = 1;

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

    /** Filters the outliner by name. */
    private final IkString filter = new IkString(64);

    /** Whether the renderer was switched to draw the scene, so it is switched back. */
    private boolean entered;

    /** The pipeline before entering. */
    private int previousPipeline;

    /** The sun before entering. */
    private DirectionalLight previousSun;

    /** The ambient light before entering. */
    private AmbientLight previousAmbient;

    /** The camera position before entering. */
    private final Vector3d previousPosition = new Vector3d();

    /** The camera rotation before entering. */
    private final Vector2f previousRotation = new Vector2f();

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
    }

    /**
     * Show a placed scene, switching the renderer to draw it and framing it.
     *
     * @param newPlacement The scene, already placed.
     */
    public void show(@NonNull ScenePlacement newPlacement) {
        clear();
        placement = newPlacement;
        enter();
        useFileSun();
        frame(newPlacement.getImported().root());
        placeTestCamera();
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
        GraphicsManager.getScene()
                .getSceneLights()
                .setDirLight(
                        new DirectionalLight(
                                new Vector3f(sun.color()),
                                new Vector3f(sun.direction()),
                                SUN_INTENSITY * sun.intensity()));
    }

    /**
     * Put the camera where the startup property says, as "x,y,z,pitch,yaw", for captures that can't
     * move it with the mouse.
     */
    private static void placeTestCamera() {
        final String property = System.getProperty(CAMERA_PROPERTY);
        if (property == null) {
            return;
        }
        final String[] parts = property.split(",");
        if (parts.length != 5) {
            return;
        }
        try {
            final var camera = GraphicsManager.getScene().getCamera();
            camera.setPosition(
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()));
            camera.setRotation(
                    Float.parseFloat(parts[3].trim()), Float.parseFloat(parts[4].trim()));
        } catch (NumberFormatException e) {
            // Leave the framed camera as it is
        }
    }

    /** Take the shown scene out of the world, if there is one, leaving the renderer as it is. */
    public void clear() {
        select(null, -1);
        materials.clear();
        if (placement != null) {
            placement.remove();
            placement = null;
        }
        textureProgress = "";
    }

    /** Take the scene out and put the renderer, lights and camera back as they were. */
    public void close() {
        clear();
        leave();
    }

    /**
     * Note how far texture loading has come.
     *
     * @param progress The progress.
     */
    public void setTextureProgress(@NonNull String progress) {
        textureProgress = progress;
    }

    /** Switch the renderer to draw the scene, with a sun, remembering what to put back. */
    private void enter() {
        if (entered) {
            return;
        }
        final Scene scene = GraphicsManager.getScene();
        entered = true;
        previousPipeline = GraphicsManager.getPipelineConfig();
        previousSun = scene.getSceneLights().getDirLight();
        previousAmbient = scene.getSceneLights().getAmbientLight();
        previousExposure = scene.getExposure();
        previousPosition.set(scene.getCamera().getPosition());
        previousRotation.set(scene.getCamera().getRotation());
        // The debug stage draws the selection's outline
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
        GraphicsManager.getWindowManager().addWindow(graphics, INPUT_WINDOW, new InputWindow());
        GraphicsManager.getWindowManager().show(INPUT_WINDOW);
    }

    /** Put the renderer, lights and camera back as they were before entering. */
    private void leave() {
        if (!entered) {
            return;
        }
        entered = false;
        GraphicsManager.getWindowManager().removeWindow(INPUT_WINDOW);
        final Scene scene = GraphicsManager.getScene();
        if (scene == null) {
            return;
        }
        scene.getSceneLights().setDirLight(previousSun);
        scene.getSceneLights().setAmbientLight(previousAmbient);
        scene.setExposure(previousExposure);
        scene.getCamera().setPosition(previousPosition.x, previousPosition.y, previousPosition.z);
        scene.getCamera().setRotation(previousRotation.x, previousRotation.y);
        GraphicsManager.swapPipeline(previousPipeline);
    }

    /**
     * Point the camera at a node and everything below it.
     *
     * @param node The node.
     */
    private void frame(@NonNull Node node) {
        final ScenePlacement shown = placement;
        if (shown == null || !shown.getImported().bounds(node, min, max)) {
            return;
        }
        final Vector3d center = new Vector3d(min).add(max).mul(0.5);
        final double radius = Math.max(0.01, max.distance(min) / 2);
        final var camera = GraphicsManager.getScene().getCamera();
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
                    graphics.debug().add(new DebugBox(center, half, orientation, HIGHLIGHT, true)));
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
        final boolean open =
                IkGui.treeNodeEx(
                        Integer.toHexString(System.identityHashCode(node)), flags, label(node));
        clicked(node);
        if (open && !node.children().isEmpty()) {
            for (Node child : node.children()) {
                drawNode(shown, child);
            }
            IkGui.treePop();
        }
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
            IkGui.treeNodeEx(
                    Integer.toHexString(System.identityHashCode(node)), flags, label(node));
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
     * @param closeScene Closes the scene.
     */
    public void drawProperties(@NonNull UiFrame frame, @NonNull Runnable closeScene) {
        final ScenePlacement shown = placement;
        if (shown == null) {
            IkGui.textDisabled("No scene open");
            return;
        }
        final ImportedScene imported = shown.getImported();
        if (IkGui.collapsingHeader("Scene###scene", TreeNodeFlags.DEFAULT_OPEN)) {
            drawScene(frame, shown, imported, closeScene);
        }
        if (IkGui.collapsingHeader("Lighting###lighting", TreeNodeFlags.DEFAULT_OPEN)) {
            drawLighting(shown);
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
        IkGui.textWrapped(CameraManager.CONTROLS);
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
        final Scene scene = GraphicsManager.getScene();
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
        if (IkGui.sliderFloat("Sun strength", value, 0, MAX_SUN, "%.2f")) {
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
        if (IkGui.sliderFloat("Ambient strength", value, 0, MAX_AMBIENT, "%.2f")) {
            ambient.setIntensity(value[0]);
        }
        value[0] = scene.getExposure();
        if (IkGui.sliderFloat("Exposure", value, 0.05f, MAX_EXPOSURE, "%.2f")) {
            scene.setExposure(value[0]);
        }
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
     * Turn the camera around the scene with the mouse where no UI is: left drag turns, middle drag
     * pans, and the wheel zooms. The right button still flies, as everywhere.
     */
    private void handleMouse() {
        final IkIO io = IkGui.getIO();
        final var camera = GraphicsManager.getScene().getCamera();
        orbit.follow(camera);
        if (io.wantCaptureMouse) {
            return;
        }
        if (IkGui.isMouseReleased(MouseButton.LEFT)
                && IkGui.getMouseDragDelta(MouseButton.LEFT, 0).length() < CLICK_SLOP) {
            pick();
        }
        if (IkGui.isMouseDragging(MouseButton.LEFT, 0)) {
            orbit.turn(camera, io.mouseDelta.x, io.mouseDelta.y);
        } else if (IkGui.isMouseDragging(MouseButton.MIDDLE, 0)) {
            orbit.pan(camera, io.mouseDelta.x, io.mouseDelta.y, io.displaySize.y);
        }
        if (io.mouseWheel != 0) {
            orbit.zoom(camera, io.mouseWheel);
        }
    }

    /**
     * Select the mesh under the mouse, or nothing if there is none, by casting a ray from the
     * camera through it.
     */
    private void pick() {
        final ScenePlacement shown = placement;
        if (shown == null) {
            return;
        }
        final IkIO io = IkGui.getIO();
        final var camera = GraphicsManager.getScene().getCamera();
        final Vector3d direction =
                rayDirection(
                        IkGui.getMousePos().x / io.displaySize.x,
                        IkGui.getMousePos().y / io.displaySize.y,
                        io.displaySize.x / Math.max(1, io.displaySize.y),
                        camera.getRotation().x,
                        camera.getRotation().y);
        final ScenePicker.Hit hit =
                ScenePicker.pick(shown.getImported(), camera.getPosition(), direction);
        if (hit == null) {
            select(null, -1);
        } else {
            select(hit.node(), hit.sceneMesh());
        }
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

    /** Reads the mouse each frame for the orbit camera. Draws no window. */
    private final class InputWindow extends GuiWindow {
        /** Set up the window. */
        InputWindow() {
            super(INPUT_WINDOW, WindowFlags.NO_SAVED_SETTINGS);
        }

        @Override
        public void draw(final int width, final int height) {
            if (placement != null) {
                handleMouse();
            }
        }

        @Override
        public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
            return false;
        }
    }
}
