package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.scene.ImportedScene;
import com.ikalagaming.converter.scene.ImportedScene.Node;
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
 * scene with a sun, lists the scene's nodes in an outliner, outlines the selected node's meshes,
 * shows details of the scene and the selected node, and turns the camera around the scene with the
 * mouse. Render thread only.
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
     */
    public SceneView(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
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
        frame(newPlacement.getImported().root());
        placeTestCamera();
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
        select(null);
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
     * Select a node, outlining its meshes.
     *
     * @param node The node, or null for none.
     */
    private void select(@Nullable Node node) {
        for (DebugShapeHandle handle : highlights) {
            graphics.debug().remove(handle);
        }
        highlights.clear();
        selected = node;
        final ScenePlacement shown = placement;
        if (node == null || shown == null) {
            return;
        }
        final Vector3d scale = node.world().getScale(new Vector3d());
        final Matrix3d rotation = new Matrix3d();
        node.world().get3x3(rotation);
        rotation.scale(1 / scale.x, 1 / scale.y, 1 / scale.z);
        final Quaterniond orientation = rotation.getNormalizedRotation(new Quaterniond());
        for (int index : node.meshes()) {
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
                select(node);
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
        IkGui.separatorText("Scene");
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
            IkGui.endTable();
        }
        if (!imported.warnings().isEmpty()
                && IkGui.collapsingHeader(
                        "Warnings (" + imported.warnings().size() + ")###warnings")) {
            for (String warning : imported.warnings()) {
                IkGui.textWrapped(warning);
            }
        }
        if (IkGui.button("Frame scene")) {
            frame(imported.root());
        }
        IkGui.sameLine();
        if (IkGui.button("Close scene")) {
            frame.fire(closeScene);
        }
        IkGui.textDisabled("Left drag turns, middle drag pans, wheel zooms");
        IkGui.textWrapped(CameraManager.CONTROLS);

        IkGui.separatorText("Selected");
        final Node node = selected;
        if (node == null) {
            IkGui.textDisabled("Pick a node in the outliner");
            return;
        }
        drawNode(shown, imported, node);
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
            IkGui.bulletText(
                    String.format(
                            "%s, %,d triangles, %s",
                            mesh.name(), mesh.triangles(), material.name()));
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
        if (IkGui.isMouseDragging(MouseButton.LEFT, 0)) {
            orbit.turn(camera, io.mouseDelta.x, io.mouseDelta.y);
        } else if (IkGui.isMouseDragging(MouseButton.MIDDLE, 0)) {
            orbit.pan(camera, io.mouseDelta.x, io.mouseDelta.y, io.displaySize.y);
        }
        if (io.mouseWheel != 0) {
            orbit.zoom(camera, io.mouseWheel);
        }
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
