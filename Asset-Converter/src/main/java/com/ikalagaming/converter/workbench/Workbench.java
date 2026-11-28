package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.converter.content.BlockContent;
import com.ikalagaming.converter.content.ContextScene;
import com.ikalagaming.converter.content.GameContent;
import com.ikalagaming.converter.inspector.AssetSamples;
import com.ikalagaming.converter.inspector.ContainerFile;
import com.ikalagaming.converter.scene.BlockScenes;
import com.ikalagaming.converter.scene.ImportedScene;
import com.ikalagaming.converter.scene.PreviewShapes;
import com.ikalagaming.converter.scene.SceneImporter;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.TextureFilter;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.TextureWrap;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.MaterialSection;
import com.ikalagaming.graphics.asset.MeshSection;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.HoveredFlags;
import com.ikalagaming.graphics.texture.DdsFile;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.DockLayout;
import com.ikalagaming.graphics.ui.DockSpace;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Layer;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Panel;
import com.ikalagaming.graphics.ui.Row;
import com.ikalagaming.graphics.ui.Scroll;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.TextInput;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.style.Style;
import com.ikalagaming.graphics.ui.style.StyleKey;
import com.ikalagaming.launcher.PluginFolder;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * The asset workbench: browse for raw assets and open them. Images, DDS textures included, and
 * {@code .ika} containers show in the Viewer panel's {@link PreviewHost}, through a {@link
 * TextureViewer} or a {@link ContainerViewer}; models and whole scenes show in the Scene panel's
 * host, through a {@link SceneView} drawing its own preview view, with an outliner of their nodes.
 * An image and a scene can be open at once, so an image can be put on the scene's materials. Built
 * on retained UI, as docked panels; the world isn't drawn.
 *
 * <p>Files are read on a background thread, so a large file doesn't stall the editor, and results
 * are handed to the render thread through {@link UI#post}. Everything else is render thread only,
 * except {@link #stop}.
 */
@Slf4j
public class Workbench {
    /** The ID of the top bar surface, with the path and buttons. */
    public static final String SURFACE_ID = "converter/workbench";

    /** The ID of the see-through surface under the top bar that the panels dock into. */
    static final String DOCK_ID = "converter/workbench-dock";

    /** The ID of the files panel. */
    static final String FILES_ID = "converter/workbench-files";

    /** The ID of the outliner panel, with the open scene's nodes. */
    static final String OUTLINER_ID = "converter/workbench-outliner";

    /** The ID of the properties panel. */
    static final String PROPERTIES_ID = "converter/workbench-properties";

    /** The ID of the viewer panel, the preview host showing the open file. */
    static final String VIEWER_ID = "converter/workbench-viewer";

    /** The ID of the panel of preview views. */
    static final String PREVIEWS_ID = "converter/workbench-previews";

    /** The ID of the scene panel, the 3D viewer. */
    static final String SCENE_ID = "converter/workbench-scene";

    /** The ID of the graph panel. */
    static final String GRAPH_ID = "converter/workbench-graph";

    /** The ID of the asset browser panel. */
    static final String ASSETS_ID = "converter/workbench-assets";

    /** The ID of the panel listing the asset index's problems. */
    static final String PROBLEMS_ID = "converter/workbench-problems";

    /** The ID of the reference viewer's panel. */
    static final String REFERENCES_ID = "converter/workbench-references";

    /** The height of the asset roots strip in the files panel. */
    private static final float ROOTS_HEIGHT = 56;

    /** The most problems listed, so a broken folder can't fill the panel for ever. */
    private static final int MAX_PROBLEMS = 500;

    /** A missing reference's text, if the theme has no {@code converter.error} token. */
    private static final int MISSING_COLOR =
            com.ikalagaming.graphics.gui.util.Color.rgba(1.0f, 0.35f, 0.35f, 1.0f);

    /** Every surface and panel, for showing and hiding them together. */
    private static final List<String> SURFACES =
            List.of(
                    SURFACE_ID,
                    DOCK_ID,
                    FILES_ID,
                    OUTLINER_ID,
                    PROPERTIES_ID,
                    VIEWER_ID,
                    SCENE_ID,
                    GRAPH_ID,
                    PREVIEWS_ID,
                    PROBLEMS_ID,
                    ASSETS_ID,
                    REFERENCES_ID);

    /** The height of the top bar: one row, for going back, the layout and the status. */
    private static final float TOP_HEIGHT = 36;

    /** The shader ball model, in the plugin's data folder. */
    private static final String SHADER_BALL = "models/shader_ball.obj";

    /** Where the panels start, before the user moves them. */
    private static final DockLayout LAYOUT =
            new DockLayout()
                    .split(DockLayout.Side.LEFT, 0.17f, FILES_ID)
                    .splitPrevious(DockLayout.Side.BOTTOM, 0.35f, OUTLINER_ID, PROBLEMS_ID)
                    .split(DockLayout.Side.RIGHT, 0.25f, PROPERTIES_ID)
                    .split(DockLayout.Side.BOTTOM, 0.3f, ASSETS_ID)
                    .center(VIEWER_ID, SCENE_ID, GRAPH_ID, REFERENCES_ID, PREVIEWS_ID);

    /** The dock space the panels dock into. */
    private final DockSpace dockSpace = new DockSpace("dock", LAYOUT);

    /**
     * Preview views of shapes, drawn by the renderer into their own textures. -- GETTER -- The
     * preview panel.
     *
     * @return The panel's logic.
     */
    @Getter private final PreviewPanel previewPanel;

    /** Which panel the properties are for, when both could be. */
    enum Mode {
        /** Nothing open. */
        NONE,
        /** The viewer of the open file. */
        VIEWER,
        /** The scene. */
        SCENE,
        /** The graph. */
        GRAPH
    }

    /** The plugin's graphics context, which owns the textures. */
    private final GraphicsContext graphics;

    /** The UI the workbench is shown through. */
    private final UI ui;

    /** Goes back to the menu. */
    private final Runnable back;

    /** What is being browsed. */
    @Getter private final FileBrowser browser;

    /**
     * The asset index of the roots, read in the background. -- GETTER -- The asset index.
     *
     * @return The index service.
     */
    @Getter private final AssetIndexService assets = new AssetIndexService();

    /**
     * The Assets panel. -- GETTER -- The asset browser.
     *
     * @return The asset browser.
     */
    @Getter private final AssetBrowser assetBrowser;

    /**
     * The References panel. -- GETTER -- The reference viewer.
     *
     * @return The reference viewer.
     */
    @Getter private final ReferenceViewer referenceViewer;

    /**
     * Draws asset slots, in the material editor and wherever else they are. -- GETTER -- The slots.
     *
     * @return The slots.
     */
    @Getter private final AssetSlot assetSlots;

    /** The panel the reference viewer is in. */
    private Panel referencesPanel;

    /**
     * Shows the open file, unless it is a scene. -- GETTER -- The preview host.
     *
     * @return The host.
     */
    @Getter private final PreviewHost host;

    /** The panel the host draws in, brought to the front when something opens in it. */
    private Panel viewerPanel;

    /**
     * Shows the open scene. -- GETTER -- The 3D viewer.
     *
     * @return The scene view.
     */
    @Getter private final SceneView sceneView;

    /**
     * Shows the scene view in the scene panel. -- GETTER -- The scene panel's host.
     *
     * @return The host.
     */
    @Getter private final PreviewHost sceneHost;

    /** The panel the scene host draws in. */
    private Panel scenePanel;

    /** The UI frame the viewer panel was last drawn in, to tell which panels show. */
    private int viewerDrawn = -2;

    /** The UI frame the scene panel was last drawn in. */
    private int sceneDrawn = -2;

    /** The UI frame the graph panel was last drawn in. */
    private int graphDrawn = -2;

    /**
     * The graph panel's tool. -- GETTER -- The graph tool.
     *
     * @return The tool.
     */
    @Getter private final GraphTool graphTool;

    /** The panel the graph tool is in. */
    private Panel graphPanel;

    /** Reads files off the render thread, one at a time. */
    private final ExecutorService loader =
            Executors.newSingleThreadExecutor(
                    task -> {
                        Thread thread = new Thread(task, "Workbench loader");
                        thread.setDaemon(true);
                        return thread;
                    });

    /**
     * Counts files asked for, so a slow load that finishes after a newer one was asked for is
     * dropped.
     */
    private final AtomicInteger requests = new AtomicInteger();

    /** Counts scenes, so textures still loading for a closed scene stop. */
    private final AtomicInteger scenes = new AtomicInteger();

    /** The path of the folder or file being looked at. */
    private final TextInput path;

    /** The folder's contents. */
    private final Column files;

    /** Switches between openable files and every file. */
    private final Button filter;

    /** What is happening, or what went wrong. */
    private final Label status;

    /** The open file, if any. */
    private @Nullable Path openFile;

    /** What the middle of the screen shows. */
    private Mode mode = Mode.NONE;

    /** Whether the workbench is showing. */
    private boolean showing;

    /** The environment map lighting the scene, owned here, or null for the plain sky. */
    private TextureHandle skyTexture;

    /**
     * Create the workbench, browsing the working directory.
     *
     * @param graphics The plugin's graphics context.
     * @param ui The UI to show the workbench through.
     * @param back Goes back to the menu, once the workbench has hidden itself.
     */
    public Workbench(@NonNull GraphicsContext graphics, @NonNull UI ui, @NonNull Runnable back) {
        this.graphics = graphics;
        this.ui = ui;
        this.back = back;
        host = new PreviewHost(ui, "Pick an image or a .ika file in the file list to view it");
        sceneHost = new PreviewHost(ui, "Open a model, or try materials on a shape");
        previewPanel = new PreviewPanel(graphics);
        final MaterialEditor materialEditor = new MaterialEditor(graphics.ui(), this::loadTexture);
        sceneView = new SceneView(graphics, materialEditor);
        sceneView.setCloseScene(this::closeScene);
        browser = new FileBrowser(Path.of(""));
        assetBrowser = new AssetBrowser(assets, graphics.textures(), this::open);
        referenceViewer =
                new ReferenceViewer(assets::getSnapshot, assetBrowser::thumbnail, this::open);
        assetBrowser.setShowReferences(this::showReferences);
        assetSlots =
                new AssetSlot(
                        assets::getSnapshot,
                        assetBrowser::thumbnail,
                        assetBrowser::getSelected,
                        this::open);
        materialEditor.setSlots(assetSlots);
        assetBrowser.setStudio(
                new ThumbnailStudio(
                        graphics,
                        assetBrowser.getThumbnails(),
                        new MaterialEditor(
                                graphics.ui(),
                                (file, colorSpace, loaded) ->
                                        loadTexture(file, colorSpace, loaded, false)),
                        this::post));
        sceneView.getParticles().setFolder(browser::getDirectory);
        sceneView.getParticles().setSaved(saved -> refresh());
        path = new TextInput("path", "").onSubmit(this::openPath).width(Sizing.grow());
        files = new Column("files").align(Align.STRETCH);
        filter = new Button("filter", "").onClick(this::toggleFilter);
        sceneView.setSceneChoices(new Scenes());
        // Shown from the start, so its toolbar offers the scenes before one is open
        sceneHost.show(sceneView);
        status = new Label("status", "");
        graphTool =
                new GraphTool(
                        ui,
                        graphics.textures(),
                        loader,
                        status::text,
                        this::open,
                        new GraphPreviews.Host() {
                            @Override
                            public void showViewer(@NonNull Viewer viewer) {
                                // Not brought to the front, so the graph stays in sight
                                host.show(viewer);
                            }

                            @Override
                            public boolean isShown(@NonNull Viewer viewer) {
                                return host.viewer(Viewer.class) == viewer;
                            }

                            @Override
                            public void wearMaps(
                                    @Nullable TextureHandle color,
                                    @Nullable TextureHandle normal,
                                    @Nullable TextureHandle orm) {
                                wearGraphMaps(color, normal, orm);
                            }

                            @Override
                            public void showGraph() {
                                showGraphPanel();
                            }
                        });
        refresh();
    }

    /**
     * Anchors stretching across the screen's width, from a distance below its top down to a
     * distance above its bottom.
     *
     * @param top Where the top edge is, in units below the screen's top.
     * @param bottom Where the bottom edge is, in units above the screen's bottom, or a negative
     *     number for a fixed height below the top.
     * @return The anchors.
     */
    private static Anchors band(float top, float bottom) {
        return new Anchors(
                0, 0, 1, bottom < 0 ? 0 : 1, Length.u(0), Length.u(top), Length.u(0), Length.u(0));
    }

    /**
     * A panel the workbench docks.
     *
     * @param id The panel ID.
     * @param title The title.
     * @param content What it holds.
     * @return The panel, shown.
     */
    private Panel panel(String id, String title, Node<?> content) {
        Panel panel = ui.panel(id, title);
        panel.content(content);
        ui.show(panel);
        return panel;
    }

    /** Make the surfaces and show them hidden, so later toggles keep their state. */
    public void install() {
        // Shapes, stages and context scenes are picked in the 3D view's toolbar
        Column top =
                new Column("top")
                        .padding(Insets.all(4))
                        .align(Align.STRETCH)
                        .add(
                                new Row("toolbar")
                                        .gap(8)
                                        .add(
                                                new Button("back", "Back").onClick(this::goBack),
                                                new Button("reset-layout", "Reset layout")
                                                        .tooltip(
                                                                "Put every panel back where it"
                                                                        + " started")
                                                        .onClick(dockSpace::resetLayout),
                                                status.width(Sizing.grow())));
        ui.show(
                ui.surface(SURFACE_ID)
                        .anchors(band(0, -1))
                        .height(Sizing.fixed(TOP_HEIGHT))
                        .content(top));
        // Behind the panels, see-through where no panel is docked
        ui.show(
                ui.surface(DOCK_ID)
                        .anchors(band(TOP_HEIGHT, 0))
                        .layer(Layer.BACKGROUND)
                        .transparent()
                        .style(Style.builder().set(StyleKey.PADDING, Insets.NONE).build())
                        .content(new Column("root").add(dockSpace)));

        Row where =
                new Row("where")
                        .gap(4)
                        .add(
                                path,
                                new Button("up", "Up").onClick(this::up),
                                new Button("open", "Open").onClick(() -> openPath(path.text())));
        Row tools =
                new Row("file-tools")
                        .gap(4)
                        .add(
                                filter,
                                new Button("samples", "Write samples")
                                        .tooltip(
                                                "Write sample .ika containers, good and broken,"
                                                        + " and browse their folder")
                                        .onClick(this::writeSamples));
        Row drives = new Row("drives").gap(4);
        List<Path> roots = browser.roots();
        if (roots.size() > 1) {
            for (int i = 0; i < roots.size(); ++i) {
                Path root = roots.get(i);
                drives.add(new Button("root" + i, root.toString()).onClick(() -> navigate(root)));
            }
        }
        panel(
                FILES_ID,
                "Files",
                new Column("left")
                        .gap(4)
                        .align(Align.STRETCH)
                        .add(
                                where,
                                tools,
                                drives,
                                new Immediate("asset-roots", this::drawAssetRoots)
                                        .height(Sizing.fixed(ROOTS_HEIGHT)),
                                new Scroll("list")
                                        .border(true)
                                        .height(Sizing.grow())
                                        .content(files)));
        panel(
                OUTLINER_ID,
                "Outliner",
                new Column("outliner-root")
                        .align(Align.STRETCH)
                        .add(new Immediate("outliner", sceneView::drawOutliner)));
        panel(
                PROPERTIES_ID,
                "Properties",
                new Column("right")
                        .align(Align.STRETCH)
                        .add(new Immediate("details", this::drawProperties)));
        viewerPanel =
                panel(
                        VIEWER_ID,
                        "Viewer",
                        new Column("middle")
                                .align(Align.STRETCH)
                                .add(new Immediate("view", this::drawViewer)));
        scenePanel =
                panel(
                        SCENE_ID,
                        "3D view",
                        new Column("scene-root")
                                .align(Align.STRETCH)
                                .add(new Immediate("scene", this::drawScene)));
        graphPanel =
                panel(
                        GRAPH_ID,
                        "Graph",
                        new Column("graph-panel")
                                .align(Align.STRETCH)
                                .add(
                                        new Immediate("graph-seen", this::noteGraphDrawn)
                                                .height(Sizing.fixed(0)),
                                        graphTool.content()));
        referencesPanel = panel(REFERENCES_ID, "References", referenceViewer.content());
        panel(PREVIEWS_ID, "Previews", previewPanel.content());
        panel(ASSETS_ID, "Assets", assetBrowser.content());
        panel(
                PROBLEMS_ID,
                "Problems",
                new Column("problems-root")
                        .align(Align.STRETCH)
                        .add(new Immediate("problems", this::drawProblems)));
        for (String surface : SURFACES) {
            ui.setVisible(surface, false);
        }
    }

    /**
     * Draw the viewer panel, noting that it shows and that a click in it makes the properties its.
     *
     * @param frame The current frame.
     */
    private void drawViewer(UiFrame frame) {
        viewerDrawn = IkGui.getFrameCount();
        if (IkGui.isWindowHovered() && IkGui.isMouseClicked(MouseButton.LEFT)) {
            mode = host.getViewer() != null ? Mode.VIEWER : mode;
        }
        host.draw(frame);
    }

    /**
     * Draw the scene panel, noting that it shows and that a click in it makes the properties its.
     *
     * @param frame The current frame.
     */
    private void drawScene(UiFrame frame) {
        sceneDrawn = IkGui.getFrameCount();
        if (IkGui.isWindowHovered() && IkGui.isMouseClicked(MouseButton.LEFT)) {
            mode = sceneView.getPlacement() != null ? Mode.SCENE : mode;
        }
        sceneHost.draw(frame);
    }

    /**
     * Note that the graph panel shows, and that a click in it makes the properties its.
     *
     * @param frame The current frame.
     */
    private void noteGraphDrawn(UiFrame frame) {
        graphDrawn = IkGui.getFrameCount();
        if (IkGui.isWindowHovered(HoveredFlags.ROOT_AND_CHILD_WINDOWS)
                && IkGui.isMouseClicked(MouseButton.LEFT)) {
            mode = Mode.GRAPH;
        }
    }

    /**
     * Whether the properties are the graph's: when its panel shows and the viewer's and scene's
     * don't, or it was clicked last.
     *
     * @return True for the graph's properties.
     */
    private boolean propertiesForGraph() {
        final int now = IkGui.getFrameCount();
        if (now - graphDrawn > 1) {
            return false;
        }
        final boolean othersShow = now - sceneDrawn <= 1 || now - viewerDrawn <= 1;
        return !othersShow || mode == Mode.GRAPH;
    }

    /**
     * Show a file's references in the References panel, bringing it to the front.
     *
     * @param file The file.
     */
    public void showReferences(@NonNull Path file) {
        referenceViewer.show(file);
        if (referencesPanel != null) {
            referencesPanel.bringToFront();
        }
    }

    /** Bring the graph panel to the front. */
    private void showGraphPanel() {
        mode = Mode.GRAPH;
        if (graphPanel != null) {
            graphPanel.bringToFront();
        }
    }

    /**
     * Whether the properties are the scene's: when only the scene panel shows, or both show and the
     * scene was the last opened or clicked.
     *
     * @return True for the scene's properties, false for the viewer's.
     */
    private boolean propertiesForScene() {
        if (sceneView.getPlacement() == null) {
            return false;
        }
        if (host.getViewer() == null) {
            return true;
        }
        // Panels are drawn in any order, so drawn last frame counts as showing
        final int now = IkGui.getFrameCount();
        final boolean sceneShows = now - sceneDrawn <= 1;
        final boolean viewerShows = now - viewerDrawn <= 1;
        if (sceneShows != viewerShows) {
            return sceneShows;
        }
        return mode == Mode.SCENE;
    }

    /**
     * Show or hide the workbench. Hiding it closes any open scene.
     *
     * @param visible Whether to show it.
     */
    public void setVisible(boolean visible) {
        showing = visible;
        if (visible) {
            previewPanel.open();
        } else {
            previewPanel.close();
            scenes.incrementAndGet();
            sceneView.close();
            if (mode == Mode.SCENE) {
                mode = Mode.NONE;
            }
        }
        updateSurfaces();
    }

    /** Hide the workbench and go back to the menu. */
    private void goBack() {
        setVisible(false);
        back.run();
    }

    /** Show the surfaces, if the workbench is showing. */
    private void updateSurfaces() {
        for (String surface : SURFACES) {
            ui.setVisible(surface, showing);
        }
    }

    /**
     * Draw the properties of whatever the middle shows.
     *
     * @param frame The current frame.
     */
    private void drawProperties(UiFrame frame) {
        drawFileProperties(frame);
        drawReferences(frame);
    }

    /**
     * Draw the asset roots: jumping to one in the file list, adding or removing the folder shown,
     * and how much is indexed.
     *
     * @param frame The current frame.
     */
    private void drawAssetRoots(UiFrame frame) {
        assets.start();
        final List<Path> roots = assets.getRoots();
        IkGui.setNextItemWidth(-110);
        if (IkGui.beginCombo("##asset-roots", "Go to an asset root")) {
            for (Path root : roots) {
                if (IkGui.selectable(rootLabel(root) + "##" + root)) {
                    frame.fire(() -> navigate(root));
                }
                if (IkGui.isItemHovered()) {
                    IkGui.setTooltip(root.toString());
                }
            }
            IkGui.endCombo();
        }
        IkGui.sameLine();
        final Path shown = browser.getDirectory().toAbsolutePath().normalize();
        final boolean isRoot = roots.contains(shown);
        if (IkGui.button(isRoot ? "Remove root" : "Add as root")) {
            frame.fire(
                    () -> {
                        if (isRoot) {
                            assets.removeRoot(shown);
                        } else {
                            assets.addRoot(shown);
                        }
                    });
        }
        IkGui.setItemTooltip(
                isRoot
                        ? "Stop indexing this folder."
                        : "Index this folder's files and their references: a plugin's data folder,"
                                + " whose files have IDs, or a folder of sources like art.");
        final com.ikalagaming.converter.index.AssetIndex.Snapshot snapshot = assets.getSnapshot();
        IkGui.textDisabled(
                String.format(
                        "%d files in %d roots indexed, %d problems%s",
                        snapshot.entries().size(),
                        roots.size(),
                        snapshot.problems().size(),
                        assets.isScanning() ? "; reading..." : ""));
    }

    /**
     * Show one of the asset roots in the file list.
     *
     * @param index Which, from 0.
     */
    public void showRoot(int index) {
        final List<Path> roots = assets.getRoots();
        if (index >= 0 && index < roots.size()) {
            navigate(roots.get(index));
        }
    }

    /**
     * A root's name in the list: its folder's name, after its parent's.
     *
     * @param root The root.
     * @return The name.
     */
    private static String rootLabel(Path root) {
        final Path name = root.getFileName();
        final Path parent = root.getParent();
        if (name == null) {
            return root.toString();
        }
        return parent == null || parent.getFileName() == null
                ? name.toString()
                : parent.getFileName() + "/" + name;
    }

    /**
     * Draw the asset index's problems: each reference that resolves to nothing, and each file that
     * couldn't be read, with its file; a click opens the file.
     *
     * @param frame The current frame.
     */
    private void drawProblems(UiFrame frame) {
        assets.start();
        final List<com.ikalagaming.converter.index.AssetIndex.Problem> problems =
                assets.getSnapshot().problems();
        if (problems.isEmpty()) {
            IkGui.textDisabled(
                    assets.getRoots().isEmpty()
                            ? "No asset roots: add folders in the file list to index them."
                            : "No problems: every reference resolves.");
            return;
        }
        int shown = 0;
        for (com.ikalagaming.converter.index.AssetIndex.Problem problem : problems) {
            if (++shown > MAX_PROBLEMS) {
                IkGui.textDisabled((problems.size() - MAX_PROBLEMS) + " more");
                break;
            }
            final Path file = problem.file();
            if (IkGui.selectable(
                    file.getFileName()
                            + ": "
                            + problem.label()
                            + " "
                            + problem.name()
                            + "##problem"
                            + shown)) {
                frame.fire(() -> open(file));
            }
            if (IkGui.isItemHovered()) {
                IkGui.setTooltip(file.toString());
            }
        }
    }

    /**
     * Draw what the open file references and what references it, from the asset index; a click
     * opens one.
     *
     * @param frame The current frame.
     */
    private void drawReferences(UiFrame frame) {
        if (openFile == null) {
            return;
        }
        IkGui.separator();
        if (!IkGui.collapsingHeader(
                "References", com.ikalagaming.graphics.gui.flags.TreeNodeFlags.DEFAULT_OPEN)) {
            return;
        }
        final com.ikalagaming.converter.index.AssetIndex.Snapshot snapshot = assets.getSnapshot();
        final com.ikalagaming.converter.index.AssetIndex.Entry entry = snapshot.entry(openFile);
        if (entry == null) {
            IkGui.textDisabled(
                    assets.isScanning()
                            ? "Reading the asset roots..."
                            : "Not under an asset root; add its folder as one to see its"
                                    + " references.");
            return;
        }
        IkGui.textUnformatted(entry.type() + (entry.id() == null ? "" : ", " + entry.id()));
        if (IkGui.button("Show references")) {
            final Path shown = openFile;
            frame.fire(() -> showReferences(shown));
        }
        final List<Path> providers = snapshot.providers(openFile);
        if (providers.size() > 1) {
            IkGui.textUnformatted("Provided by " + providers.size() + " folders, the last wins:");
            for (int i = 0; i < providers.size(); ++i) {
                final Path provider = providers.get(i);
                link(
                        frame,
                        "provider" + i,
                        provider,
                        provider + (i == providers.size() - 1 ? "  (wins)" : ""));
            }
        }
        final List<com.ikalagaming.converter.index.AssetIndex.Link> uses = snapshot.uses(openFile);
        IkGui.textUnformatted(uses.isEmpty() ? "Uses nothing" : "Uses:");
        final int missing = frame.theme().color("converter.error", MISSING_COLOR);
        for (int i = 0; i < uses.size(); ++i) {
            final com.ikalagaming.converter.index.AssetIndex.Link use = uses.get(i);
            if (use.file() == null) {
                IkGui.textColored(missing, "  " + use.label() + ": " + use.name() + " (missing)");
            } else {
                link(frame, "use" + i, use.file(), use.label() + ": " + use.name());
            }
        }
        final List<com.ikalagaming.converter.index.AssetIndex.Link> usedBy =
                snapshot.usedBy(openFile);
        IkGui.textUnformatted(usedBy.isEmpty() ? "Used by nothing" : "Used by:");
        for (int i = 0; i < usedBy.size(); ++i) {
            final com.ikalagaming.converter.index.AssetIndex.Link user = usedBy.get(i);
            link(frame, "user" + i, user.file(), user.label() + " in " + user.file().getFileName());
        }
    }

    /**
     * A reference as a line that opens its file when clicked.
     *
     * @param frame The current frame.
     * @param id Its ID, unique in the section.
     * @param file The file.
     * @param text What it shows.
     */
    private void link(UiFrame frame, String id, Path file, String text) {
        if (IkGui.selectable("  " + text + "##" + id)) {
            frame.fire(() -> open(file));
        }
        if (IkGui.isItemHovered()) {
            IkGui.setTooltip(file.toString());
        }
    }

    /**
     * Draw the open file's own properties, by what shows it.
     *
     * @param frame The current frame.
     */
    private void drawFileProperties(UiFrame frame) {
        if (propertiesForGraph()) {
            graphTool.drawProperties(frame);
            return;
        }
        if (propertiesForScene()) {
            sceneHost.drawProperties(frame);
            if (host.getViewer() != null) {
                IkGui.separator();
                if (IkGui.button("Back to the viewer")) {
                    frame.fire(this::showViewerPanel);
                }
            }
            return;
        }
        host.drawProperties(frame);
        if (sceneView.getPlacement() != null) {
            IkGui.separator();
            if (IkGui.button("Back to the scene")) {
                frame.fire(this::showScene);
            }
        }
    }

    /** Bring the scene panel to the front again, after looking at an image. */
    private void showScene() {
        mode = Mode.SCENE;
        if (scenePanel != null) {
            scenePanel.bringToFront();
        }
    }

    /** Bring the viewer panel to the front again, after looking at the scene. */
    private void showViewerPanel() {
        mode = Mode.VIEWER;
        if (viewerPanel != null) {
            viewerPanel.bringToFront();
        }
    }

    /** Close the open scene. */
    private void closeScene() {
        scenes.incrementAndGet();
        sceneView.close();
        mode = host.getViewer() != null ? Mode.VIEWER : Mode.NONE;
        status.text("Closed the scene");
    }

    /**
     * Browse a folder.
     *
     * @param folder The folder.
     */
    private void navigate(Path folder) {
        browser.navigate(folder);
        refresh();
    }

    /** Browse the parent folder. */
    private void up() {
        browser.up();
        refresh();
    }

    /**
     * Browse a folder or open a file, from a typed path.
     *
     * @param text The path.
     */
    private void openPath(String text) {
        Path typed = browser.parse(text);
        if (typed == null) {
            refresh();
        } else if (Files.isDirectory(typed)) {
            navigate(typed);
        } else {
            open(typed);
        }
    }

    /** Switch between listing openable files and every file. */
    private void toggleFilter() {
        browser.setShowAllFiles(!browser.isShowAllFiles());
        refresh();
    }

    /**
     * Open a file, reading it in the background.
     *
     * @param file The file.
     */
    public void open(@NonNull Path file) {
        final Path absolute = file.toAbsolutePath().normalize();
        final AssetKind kind = AssetKind.of(absolute);
        if (kind == AssetKind.UNKNOWN) {
            status.text(
                    "Can't open "
                            + absolute.getFileName()
                            + ", it isn't an image, model, container, material, look, graph"
                            + " or particle effect");
            return;
        }
        if (kind == AssetKind.INSTANCE) {
            openFile = absolute;
            final Path folder = absolute.getParent();
            if (folder != null && !folder.equals(browser.getDirectory())) {
                browser.navigate(folder);
            }
            refresh();
            graphTool.openInstance(absolute);
            showGraphPanel();
            return;
        }
        if (kind == AssetKind.GRAPH) {
            openFile = absolute;
            final Path folder = absolute.getParent();
            if (folder != null && !folder.equals(browser.getDirectory())) {
                browser.navigate(folder);
            }
            refresh();
            graphTool.open(absolute);
            showGraphPanel();
            return;
        }
        if (kind == AssetKind.LOOK) {
            openFile = absolute;
            final Path folder = absolute.getParent();
            if (folder != null && !folder.equals(browser.getDirectory())) {
                browser.navigate(folder);
            }
            refresh();
            openLook(absolute);
            return;
        }
        if (kind == AssetKind.MATERIAL) {
            sceneShown = -1;
            openFile = absolute;
            final Path folder = absolute.getParent();
            if (folder != null && !folder.equals(browser.getDirectory())) {
                browser.navigate(folder);
            }
            refresh();
            openPreview(PreviewShapes.Shape.SPHERE, () -> wearMaterial(absolute));
            return;
        }
        if (kind == AssetKind.PARTICLE_EFFECT) {
            openFile = absolute;
            final Path folder = absolute.getParent();
            if (folder != null && !folder.equals(browser.getDirectory())) {
                browser.navigate(folder);
            }
            refresh();
            if (sceneView.getParticles().isActive()) {
                sceneView.getParticles().openFile(absolute);
            } else {
                openParticleStage(absolute);
            }
            return;
        }
        final int request = requests.incrementAndGet();
        openFile = absolute;
        // List the file's folder, for a file opened by a typed path
        final Path folder = absolute.getParent();
        if (folder != null && !folder.equals(browser.getDirectory())) {
            browser.navigate(folder);
        }
        status.text("Loading " + absolute.getFileName() + "...");
        refresh();
        if (kind == AssetKind.MODEL) {
            sceneShown = -1;
            loader.execute(() -> loadScene(absolute, request));
        } else if (kind == AssetKind.CONTAINER) {
            loader.execute(() -> loadContainer(absolute, request));
        } else {
            loader.execute(() -> load(absolute, request));
        }
    }

    /**
     * Post a change to the render thread, unless the plugin unloaded.
     *
     * @param change The change.
     */
    private void post(Runnable change) {
        try {
            ui.post(change);
        } catch (IllegalStateException unloaded) {
            // The plugin unloaded while loading, so there is nothing to tell
        }
    }

    /** The scenes made in code, first in the 3D view's list, before the context scenes. */
    private static final List<String> BUILT_IN_SCENES =
            List.of(
                    PreviewShapes.Shape.SPHERE.label,
                    PreviewShapes.Shape.CUBE.label,
                    PreviewShapes.Shape.PLANE.label,
                    "Shader ball",
                    "Particle stage");

    /** The index in the scene list of the particle stage. */
    private static final int PARTICLE_STAGE_INDEX = 4;

    /** The index in the scene list of the shader ball. */
    private static final int SHADER_BALL_INDEX = 3;

    /** The context scenes, read the first time the list is shown. */
    private List<ContextScene> contexts;

    /** The scene list's entry shown, or -1 for a file. */
    private int sceneShown = -1;

    /** The look of the block context scenes show, or null for gray stand-ins. */
    private @Nullable Path subjectLook;

    /**
     * The context scenes in the converter's data folder, read once; none without game content to
     * draw their blocks with.
     *
     * @return The scenes.
     */
    private List<ContextScene> contexts() {
        if (contexts == null) {
            contexts =
                    GameContent.available()
                            ? ContextScene.loadAll(
                                    PluginFolder.getResource(
                                                    ConverterPlugin.PLUGIN_NAME,
                                                    PluginFolder.ResourceType.DATA,
                                                    ContextScene.FOLDER)
                                            .toPath())
                            : List.of();
        }
        return contexts;
    }

    /**
     * Show a scene from the 3D view's list, by name, as picking it does.
     *
     * @param name The scene's name, ignoring case.
     * @return True if there is one by that name.
     */
    public boolean pickScene(@NonNull String name) {
        final Scenes scenes = new Scenes();
        final String[] names = scenes.names();
        for (int i = 0; i < names.length; ++i) {
            if (names[i].equalsIgnoreCase(name)) {
                scenes.pick(i);
                return true;
            }
        }
        return false;
    }

    /** The 3D view's scene list: the built-in scenes, then the context scenes. */
    private final class Scenes implements SceneView.SceneChoices {
        @Override
        public String[] names() {
            final List<String> names = new ArrayList<>(BUILT_IN_SCENES);
            for (ContextScene context : contexts()) {
                names.add(context.name());
            }
            return names.toArray(new String[0]);
        }

        @Override
        public int current() {
            return sceneShown;
        }

        @Override
        public void pick(int index) {
            if (index < 0) {
                return;
            }
            if (index < SHADER_BALL_INDEX) {
                sceneShown = index;
                openPreview(PreviewShapes.Shape.values()[index]);
            } else if (index == SHADER_BALL_INDEX) {
                openShaderBall();
                sceneShown = index;
            } else if (index == PARTICLE_STAGE_INDEX) {
                sceneShown = index;
                openParticleStage(null);
            } else if (index - BUILT_IN_SCENES.size() < contexts().size()) {
                sceneShown = index;
                openContext(contexts().get(index - BUILT_IN_SCENES.size()));
            }
        }
    }

    /**
     * Show a context scene around the block whose look was opened last, building it in the
     * background from the game's content.
     *
     * @param context The scene.
     */
    private void openContext(ContextScene context) {
        final int request = requests.incrementAndGet();
        final Path look = subjectLook;
        status.text("Making " + context.name() + "...");
        loader.execute(
                () -> {
                    try {
                        final BlockScenes.Built built = BlockScenes.build(context, look);
                        final ScenePlacement placement =
                                ScenePlacement.prepare(graphics, built.scene());
                        post(
                                () -> {
                                    sceneLoaded(request, placement);
                                    if (request != requests.get()) {
                                        return;
                                    }
                                    // The render materials' own values, over the plain ones the
                                    // scene was placed with; the textures come from the scene
                                    final List<Material> materials = placement.getMaterials();
                                    for (int i = 0; i < built.values().size(); ++i) {
                                        final Material values = built.values().get(i);
                                        if (values != null && i < materials.size()) {
                                            materials.get(i).setValues(values);
                                        }
                                    }
                                    status.text(
                                            built.missing().isEmpty()
                                                    ? "Showing " + context.name()
                                                    : "Showing "
                                                            + context.name()
                                                            + "; not found, drawn gray: "
                                                            + String.join(", ", built.missing()));
                                });
                    } catch (RuntimeException e) {
                        log.warn("Could not make the scene {}", context.name(), e);
                        final String message =
                                "Can't make " + context.name() + ": " + e.getMessage();
                        post(() -> failed(request, message));
                    }
                });
    }

    /**
     * Open a block's look: show it in the first context scene that has a place for it.
     *
     * @param file The look file.
     */
    private void openLook(Path file) {
        if (!GameContent.available()) {
            status.text("Looks need " + GameContent.CORE_PLUGIN + ", which isn't loaded");
            return;
        }
        if (BlockScenes.blockId(file).isEmpty()) {
            status.text(
                    file.getFileName()
                            + " isn't a block's look: those are in a content folder's"
                            + " looks/block folder");
            return;
        }
        subjectLook = file;
        final List<ContextScene> all = contexts();
        for (int i = 0; i < all.size(); ++i) {
            if (all.get(i).hasSubject()) {
                sceneShown = BUILT_IN_SCENES.size() + i;
                openContext(all.get(i));
                return;
            }
        }
        status.text("No context scene has a place for the block; add one to the data folder");
    }

    /**
     * What a name starts with to open a preview shape instead of a file, as in "preview:sphere".
     */
    static final String PREVIEW_PREFIX = "preview:";

    /** The name after {@link #PREVIEW_PREFIX} that opens the particle stage. */
    static final String PARTICLE_PREVIEW = "particles";

    /**
     * Open what a script names: a file, a preview shape as "preview:sphere", "preview:cube" or
     * "preview:plane", or the particle stage as "preview:particles".
     *
     * @param what The file or preview.
     */
    public void openNamed(@NonNull String what) {
        if (what.equals(PREVIEW_PREFIX + PARTICLE_PREVIEW)) {
            sceneShown = PARTICLE_STAGE_INDEX;
            openParticleStage(null);
            return;
        }
        if (what.startsWith(PREVIEW_PREFIX)) {
            final String name = what.substring(PREVIEW_PREFIX.length()).toUpperCase(Locale.ROOT);
            try {
                final PreviewShapes.Shape shape = PreviewShapes.Shape.valueOf(name);
                sceneShown = shape.ordinal();
                openPreview(shape);
            } catch (IllegalArgumentException e) {
                status.text("No preview shape called " + name.toLowerCase(Locale.ROOT));
            }
            return;
        }
        open(Path.of(what));
    }

    /**
     * Show a preview shape on a floor, to try materials on.
     *
     * @param shape The shape.
     */
    private void openPreview(PreviewShapes.Shape shape) {
        openPreview(shape, () -> {});
    }

    /**
     * Show a preview shape on a floor, then do something with it, like put a material on it.
     *
     * @param shape The shape.
     * @param then Run on the render thread once the shape shows.
     */
    private void openPreview(PreviewShapes.Shape shape, Runnable then) {
        final int request = requests.incrementAndGet();
        status.text("Making a " + shape.label.toLowerCase(Locale.ROOT) + "...");
        loader.execute(
                () -> {
                    final ScenePlacement placement =
                            ScenePlacement.prepare(graphics, PreviewShapes.scene(shape));
                    post(
                            () -> {
                                sceneLoaded(request, placement);
                                if (request == requests.get() && showing) {
                                    then.run();
                                }
                            });
                });
    }

    /**
     * Show a container's mesh in the 3D viewer, standing on a floor, or its material on a sphere.
     *
     * @param file The container.
     * @param tag {@code MeshSection.TAG} or {@code MaterialSection.TAG}.
     */
    private void containerInThreeD(Path file, int tag) {
        if (tag == MaterialSection.TAG) {
            openPreview(PreviewShapes.Shape.SPHERE, () -> wearMaterial(file));
            return;
        }
        final int request = requests.incrementAndGet();
        status.text("Reading the mesh in " + file.getFileName() + "...");
        loader.execute(
                () -> {
                    try {
                        final MeshData mesh =
                                MeshSection.decode(
                                        AssetFile.open(file)
                                                .section(MeshSection.TAG)
                                                .orElseThrow(
                                                        () ->
                                                                new IllegalArgumentException(
                                                                        "it has no mesh")));
                        final String name = PreviewHost.stem(file.getFileName().toString());
                        final ScenePlacement placement =
                                ScenePlacement.prepare(
                                        graphics, PreviewShapes.meshScene(name, mesh));
                        post(() -> sceneLoaded(request, placement));
                    } catch (RuntimeException e) {
                        log.warn("Could not show the mesh in {}", file, e);
                        final String message =
                                "Can't show the mesh in "
                                        + file.getFileName()
                                        + ": "
                                        + e.getMessage();
                        post(() -> failed(request, message));
                    }
                });
    }

    /**
     * Put a material file on the shape shown, saying how it went.
     *
     * @param file The material.
     */
    private void wearMaterial(Path file) {
        if (sceneView.wearMaterial(file)) {
            status.text("Showing " + file.getFileName() + " on a sphere");
        } else {
            status.text("Can't show " + file.getFileName());
        }
    }

    /**
     * Show the stage particles are watched on, with the particle viewer.
     *
     * @param effectFile An effect file to show on it, or null for the first sample.
     */
    private void openParticleStage(@Nullable Path effectFile) {
        final int request = requests.incrementAndGet();
        status.text("Making the particle stage...");
        loader.execute(
                () -> {
                    final ScenePlacement placement =
                            ScenePlacement.prepare(graphics, PreviewShapes.particleStage());
                    post(
                            () -> {
                                sceneLoaded(request, placement);
                                if (effectFile != null && sceneView.getParticles().isActive()) {
                                    sceneView.getParticles().openFile(effectFile);
                                }
                            });
                });
    }

    /** Show the shader ball model the plugin ships, to try materials on. */
    private void openShaderBall() {
        open(
                PluginFolder.getResource(
                                ConverterPlugin.PLUGIN_NAME,
                                PluginFolder.ResourceType.DATA,
                                SHADER_BALL)
                        .toPath());
    }

    /**
     * Load a texture in the background for the material editor, handing it over on the render
     * thread. Textures tile, as materials expect.
     *
     * @param file The image.
     * @param colorSpace The color space to read it in.
     * @param loaded Given the texture on the render thread.
     */
    private void loadTexture(Path file, ColorSpace colorSpace, Consumer<TextureHandle> loaded) {
        loadTexture(file, colorSpace, loaded, true);
    }

    /**
     * Load a texture in the background, handing it over on the render thread, saying so in the
     * status line or not.
     *
     * @param file The image.
     * @param colorSpace The color space to read it in.
     * @param loaded Given the texture on the render thread.
     * @param report Whether to say what happened, as the thumbnail studio doesn't.
     */
    private void loadTexture(
            Path file, ColorSpace colorSpace, Consumer<TextureHandle> loaded, boolean report) {
        if (report) {
            status.text("Loading " + file.getFileName() + "...");
        }
        loader.execute(
                () -> {
                    try {
                        final String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                        final TextureHandle texture =
                                name.endsWith(".ika")
                                                || name.endsWith(".ika" + BlockContent.NORMAL_MAP)
                                        ? ScenePlacement.loadTextureFile(
                                                graphics.textures(), file, colorSpace)
                                        : name.endsWith(".dds")
                                                ? graphics.textures()
                                                        .load(DdsFile.read(file), colorSpace)
                                                : graphics.textures()
                                                        .load(
                                                                file.toString(),
                                                                colorSpace,
                                                                TextureWrap.REPEAT,
                                                                filterFor(file));
                        post(
                                () -> {
                                    loaded.accept(texture);
                                    if (report) {
                                        status.text("Loaded " + file.getFileName());
                                    }
                                });
                    } catch (IOException | RuntimeException e) {
                        log.warn("Could not load texture {}", file, e);
                        if (report) {
                            post(() -> status.text("Can't load " + file.getFileName()));
                        }
                    }
                });
    }

    /**
     * How to sample an image: small ones are pixel art, like 16x16 block textures, and stay sharp
     * up close; larger ones blend, as painted and photographed textures expect.
     *
     * @param file The image.
     * @return The filter; smooth if its size can't be read.
     */
    public static TextureFilter filterFor(@NonNull Path file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) {
                return TextureFilter.SMOOTH;
            }
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return TextureFilter.SMOOTH;
            }
            final ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                final int longest = Math.max(reader.getWidth(0), reader.getHeight(0));
                return longest <= PIXEL_ART_MAX ? TextureFilter.PIXELS : TextureFilter.SMOOTH;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return TextureFilter.SMOOTH;
        }
    }

    /** The longest side, in pixels, of an image taken for pixel art. */
    static final int PIXEL_ART_MAX = 128;

    /**
     * Read a model and work out how to place it. On the loader thread.
     *
     * @param file The model.
     * @param request Which request this is, to drop it if a newer one was made.
     */
    private void loadScene(Path file, int request) {
        if (request != requests.get()) {
            return;
        }
        try {
            final ImportedScene imported =
                    SceneImporter.read(
                            file,
                            message -> {
                                if (request == requests.get()) {
                                    post(() -> status.text(message));
                                }
                            });
            final ScenePlacement placement = ScenePlacement.prepare(graphics, imported);
            post(() -> sceneLoaded(request, placement));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not open {}", file, e);
            final String message = "Can't open " + file.getFileName() + ": " + e.getMessage();
            post(() -> failed(request, message));
        }
    }

    /**
     * Show a read scene in the scene panel, unless a newer file was asked for, then start loading
     * its textures. Render thread.
     *
     * @param request Which request this was.
     * @param placement The scene, ready to place.
     */
    private void sceneLoaded(int request, ScenePlacement placement) {
        if (request != requests.get() || !showing) {
            return;
        }
        final int scene = scenes.incrementAndGet();
        sceneView.show(placement);
        sceneHost.show(sceneView);
        showScene();
        final ImportedScene imported = placement.getImported();
        status.text(
                String.format(
                        "Opened %s in %,d ms: %,d triangles",
                        imported.file().getFileName(),
                        imported.readMillis(),
                        imported.drawnTriangles()));
        loader.execute(
                () ->
                        placement.loadTextures(
                                this::post,
                                () -> scene != scenes.get(),
                                progress ->
                                        post(
                                                () -> {
                                                    if (scene == scenes.get()) {
                                                        sceneView.setTextureProgress(progress);
                                                    }
                                                })));
    }

    /**
     * Read and inspect a container. On the loader thread.
     *
     * @param file The container.
     * @param request Which request this is, to drop it if a newer one was made.
     */
    private void loadContainer(Path file, int request) {
        if (request != requests.get()) {
            return;
        }
        try {
            final ContainerFile container = ContainerFile.read(file);
            post(
                    () -> {
                        if (request != requests.get()) {
                            return;
                        }
                        showViewer(new ContainerViewer(container, this::containerInThreeD));
                        status.text("Opened " + file.getFileName() + ": " + container.status());
                    });
        } catch (IOException | RuntimeException e) {
            log.warn("Could not open {}", file, e);
            final String message = "Can't open " + file.getFileName() + ": " + e.getMessage();
            post(() -> failed(request, message));
        }
    }

    /**
     * Show a viewer in the viewer panel, in front of the panels it shares a dock node with.
     *
     * @param viewer The viewer.
     */
    private void showViewer(Viewer viewer) {
        host.show(viewer);
        mode = Mode.VIEWER;
        updateSurfaces();
        if (viewerPanel != null) {
            viewerPanel.bringToFront();
        }
    }

    /**
     * Put a texture graph's maps on the 3D view's shape, showing a cube first if no scene is shown.
     *
     * @param color Base color, sRGB, or null.
     * @param normal The normal map, or null.
     * @param orm Occlusion, roughness and metallic, or null.
     */
    private void wearGraphMaps(
            @Nullable TextureHandle color,
            @Nullable TextureHandle normal,
            @Nullable TextureHandle orm) {
        if (sceneView.getPlacement() != null) {
            sceneView.wearTextures(color, normal, orm);
            return;
        }
        openPreview(PreviewShapes.Shape.CUBE, () -> sceneView.wearTextures(color, normal, orm));
    }

    /**
     * Show a swatch of pixels in the viewer, as a texture graph node's output would be.
     *
     * @param name What the pixels are called.
     * @param rgba Linear red, green, blue and alpha for each pixel, rows from the top.
     * @param width The width in pixels.
     * @param height The height in pixels.
     */
    public void showSwatch(@NonNull String name, float @NonNull [] rgba, int width, int height) {
        showViewer(new Swatch(graphics.textures(), loader, name, rgba, width, height));
    }

    /**
     * Open one of the sample containers, writing them first.
     *
     * @param name The sample's file name, like "valid.ika".
     */
    public void openSample(@NonNull String name) {
        final Path folder = AssetSamples.folder();
        try {
            AssetSamples.write(folder);
        } catch (IOException e) {
            log.warn("Could not write the asset samples", e);
            status.text("Can't write the samples: " + e.getMessage());
            return;
        }
        open(folder.resolve(name));
    }

    /** Write the sample containers and browse their folder. */
    private void writeSamples() {
        final Path folder = AssetSamples.folder();
        try {
            AssetSamples.write(folder);
            status.text("Wrote " + AssetSamples.count(folder) + " sample containers");
        } catch (IOException e) {
            log.warn("Could not write the asset samples", e);
            status.text("Can't write the samples: " + e.getMessage());
        }
        navigate(folder);
    }

    /**
     * Decode an image and start loading its texture. On the loader thread.
     *
     * @param file The image.
     * @param request Which request this is, to drop it if a newer one was made.
     */
    private void load(Path file, int request) {
        if (request != requests.get()) {
            return;
        }
        final long start = System.nanoTime();
        try {
            final ImageFile image = ImageFile.read(file);
            final ColorSpace colorSpace = image.defaultColorSpace();
            final TextureHandle texture =
                    image.dds() != null
                            // Shown whole, so its edges mustn't blend with the opposite ones
                            ? graphics.textures().load(image.dds(), colorSpace, TextureWrap.CLAMP)
                            : graphics.textures()
                                    .load(
                                            image.pixels(),
                                            colorSpace.apply(image.format()),
                                            image.width(),
                                            image.height());
            final long millis = (System.nanoTime() - start) / 1_000_000;
            post(() -> loaded(request, image, texture, colorSpace, millis));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not open {}", file, e);
            final String message = "Can't open " + file.getFileName() + ": " + e.getMessage();
            post(() -> failed(request, message));
        }
    }

    /**
     * Show a loaded image, unless a newer one was asked for. Render thread.
     *
     * @param request Which request this was.
     * @param image The image.
     * @param texture Its texture.
     * @param colorSpace The color space it was loaded in.
     * @param millis How long decoding took.
     */
    private void loaded(
            int request,
            ImageFile image,
            TextureHandle texture,
            ColorSpace colorSpace,
            long millis) {
        if (request != requests.get()) {
            graphics.textures().release(texture);
            return;
        }
        showViewer(
                new TextureViewer(
                        graphics.textures(), loader, image, texture, colorSpace, this::useAsSky));
        status.text(String.format("Opened %s in %d ms", image.path().getFileName(), millis));
    }

    /**
     * Report a failed load, unless a newer one was asked for. Render thread.
     *
     * @param request Which request this was.
     * @param message What went wrong.
     */
    private void failed(int request, String message) {
        if (request == requests.get()) {
            status.text(message);
        }
    }

    /**
     * Light the scene by an environment map: load its pixels again as the sky's own texture, which
     * outlives the image shown, and show the scene with it. Render thread.
     *
     * @param image The equirectangular image, decoded to half floats.
     */
    private void useAsSky(ImageFile image) {
        final ByteBuffer pixels = image.pixels();
        if (pixels == null) {
            return;
        }
        final TextureHandle texture =
                graphics.textures()
                        .load(
                                pixels.duplicate().rewind(),
                                image.format(),
                                image.width(),
                                image.height(),
                                TextureWrap.REPEAT);
        final TextureHandle old = skyTexture;
        skyTexture = texture;
        sceneView.setSky(texture, image.path().getFileName().toString(), this::clearSky);
        if (old != null) {
            graphics.textures().release(old);
        }
        status.text("Lighting by " + image.path().getFileName());
        if (sceneView.getPlacement() != null) {
            showScene();
        }
    }

    /** Go back to the plain sky, releasing the environment map's texture. Render thread. */
    private void clearSky() {
        final TextureHandle old = skyTexture;
        skyTexture = null;
        sceneView.setSky(null, "", this::clearSky);
        if (old != null) {
            graphics.textures().release(old);
        }
    }

    /**
     * Stop loading and close the scene. Textures, meshes and the like go with the plugin's graphics
     * context; the renderer is put back through the UI manager's queue, which outlives the plugin.
     * Safe from any thread.
     */
    public void stop() {
        assets.close();
        assetBrowser.close();
        requests.incrementAndGet();
        scenes.incrementAndGet();
        loader.shutdownNow();
        GraphicsManager.getUiManager()
                .post(
                        () -> {
                            try {
                                sceneView.close();
                            } catch (IllegalStateException unloaded) {
                                // What the plugin owned went with it; the renderer is put back
                                log.debug("Closing the scene after unloading", unloaded);
                            }
                        });
    }

    /** Update the retained nodes from the browser, after anything changes. */
    private void refresh() {
        path.text((openFile != null ? openFile : browser.getDirectory()).toString());
        filter.text(browser.isShowAllFiles() ? "Show openable only" : "Show all files");

        files.clear();
        if (browser.getDirectory().getParent() != null) {
            files.add(new Selectable("parent", "../").onClick(this::up));
        }
        int index = 0;
        for (FileBrowser.Entry entry : browser.getEntries()) {
            String id = "entry" + index++;
            if (entry.directory()) {
                files.add(
                        new Selectable(id, entry.name() + "/")
                                .onClick(() -> navigate(entry.path())));
            } else {
                files.add(
                        new Selectable(id, entry.name())
                                .selected(entry.path().equals(openFile))
                                .onClick(() -> open(entry.path())));
            }
        }
        if (browser.getError() != null) {
            status.text(browser.getError());
        }
    }
}
