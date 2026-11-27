package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.texgraph.Baker;
import com.ikalagaming.converter.texgraph.CoreNodes;
import com.ikalagaming.converter.texgraph.Evaluator;
import com.ikalagaming.converter.texgraph.GraphParameter;
import com.ikalagaming.converter.texgraph.OutputNodes;
import com.ikalagaming.converter.texgraph.SubgraphMaker;
import com.ikalagaming.converter.texgraph.SubgraphNodes;
import com.ikalagaming.converter.texgraph.TextureGraph;
import com.ikalagaming.converter.texgraph.TextureLibraries;
import com.ikalagaming.converter.texgraph.TextureLibrary;
import com.ikalagaming.graphics.Textures;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.SavedState;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.graph.GraphEditor;
import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.GraphHistory;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * The workbench's graph panel: the graph editor on texture graphs, opening and saving {@code
 * *.texgraph.yml} files and baking them to the maps the game reads. Node errors come from
 * evaluating the graph, and each node shows a swatch of what it makes. D over a node shows it in
 * the Viewer, live. Holding 1, A, M or L and clicking places a constant, add, multiply or lerp. The
 * view's pan, zoom and shown node are kept per graph file in the GUI's saved state, never in the
 * graph. Render thread only.
 */
@Slf4j
public class GraphTool {
    /** What a texture graph file's name ends with. */
    public static final String SUFFIX = TextureGraph.SUFFIX;

    /** The toolbar's height. */
    private static final float TOOLBAR_HEIGHT = 30;

    /** The node types and what they do. */
    private final TextureLibrary library = TextureLibraries.standard();

    /** The UI, for the file dialogs. */
    private final UI ui;

    /**
     * The texture graph: its nodes and its size, seed and bake target. -- GETTER -- The texture
     * graph.
     *
     * @return The texture graph.
     */
    @Getter private final TextureGraph texture;

    /**
     * The graph's history, through which every edit goes. -- GETTER -- The history.
     *
     * @return The history.
     */
    @Getter private final GraphHistory history;

    /**
     * The editor. -- GETTER -- The editor.
     *
     * @return The editor.
     */
    @Getter private final GraphEditor editor;

    /**
     * Evaluates the graph, keeping each node's outputs until something upstream changes. -- GETTER
     * -- The evaluator.
     *
     * @return The evaluator.
     */
    @Getter private final Evaluator evaluator = new Evaluator(library);

    /**
     * The file the graph was opened from or saved to. -- GETTER -- The file.
     *
     * @return The file, or null for a new graph.
     */
    @Getter private @Nullable Path file;

    /** What happened last, for the toolbar. */
    private String status = "";

    /** Reports what happened, to the workbench's status line. */
    private final Consumer<String> report;

    /** Shows a baked texture, in the viewer. */
    private final Consumer<Path> show;

    /** The parameter fields, by node and parameter, while a node is selected. */
    private final Map<String, IkString> fields = new HashMap<>();

    /** The field being typed in, which keeps its text until left, or null. */
    private String activeField;

    /**
     * Swatches, the shown node, the seed grid, Measure and pin ranges. -- GETTER -- The previews.
     *
     * @return The previews.
     */
    @Getter private final GraphPreviews previews;

    /** What the saved view state names this tool's entries with. */
    private static final String SAVED = "Texture graph ";

    /** The saved view's key for the x at its middle. */
    private static final String VIEW_X = "x";

    /** The saved view's key for the y at its middle. */
    private static final String VIEW_Y = "y";

    /** The saved view's key for its zoom. */
    private static final String VIEW_ZOOM = "zoom";

    /** The saved view's key for the shown node. */
    private static final String VIEW_SHOWN = "shown";

    /** Frames left before the view is saved, so a view just restored isn't overwritten first. */
    private int viewSettles;

    /** The folder in the converter's data holding the starter graphs. */
    static final String STARTERS = "starters";

    /** The starter graphs, found when first asked for. */
    private @Nullable List<Path> starterFiles;

    /** How often subgraph files are checked for changes, in nanoseconds. */
    private static final long SUBGRAPH_CHECK_NANOS = 1_000_000_000L;

    /** The graphs a subgraph was opened from, the latest first, for going back. */
    private final Deque<Path> parents = new ArrayDeque<>();

    /** Each subgraph type registered, with its file's version when registered. */
    private final Map<String, String> subgraphVersions = new HashMap<>();

    /** When subgraph files were last checked. */
    private long subgraphsChecked;

    /**
     * The graph's parameters and the shared files it reads. -- GETTER -- The parameter section.
     *
     * @return The section.
     */
    @Getter private final ParameterPanel parameterPanel;

    /** The saved state entry for choices that hold for every graph. */
    private static final String SAVED_ALL = "Texture graphs";

    /** The saved choice of whether nodes show swatches. */
    private static final String SWATCHES = "swatches";

    /**
     * Whether nodes show swatches; they make nodes taller, so a graph laid out without them can
     * overlap. -- GETTER -- Whether nodes show swatches.
     *
     * @return True if they do.
     */
    @Getter private boolean swatches = true;

    /**
     * Show swatches on the nodes or not, remembered between runs.
     *
     * @param show Whether to show them.
     */
    public void setSwatches(boolean show) {
        swatches = show;
        SavedState.put(SAVED_ALL, Map.of(SWATCHES, Boolean.toString(show)));
    }

    /**
     * Set up the tool with an empty graph.
     *
     * @param ui The UI, for the file dialogs.
     * @param textures Uploads swatches and maps.
     * @param background Where captures are written.
     * @param report Shows what happened in the workbench's status line.
     * @param show Shows a baked texture.
     * @param host Shows nodes in the Viewer and maps on the 3D view's shape.
     */
    public GraphTool(
            @NonNull UI ui,
            @NonNull Textures textures,
            @NonNull Executor background,
            @NonNull Consumer<String> report,
            @NonNull Consumer<Path> show,
            @NonNull GraphPreviews.Host host) {
        this.ui = ui;
        this.report = report;
        this.show = show;
        library.getNodeTypes()
                .bindKey('1', CoreNodes.CONSTANT)
                .bindKey('A', "math/add")
                .bindKey('M', "math/multiply")
                .bindKey('L', "math/lerp");
        texture = new TextureGraph(library);
        history = new GraphHistory(texture.getGraph());
        parameterPanel =
                new ParameterPanel(
                        texture,
                        this::folder,
                        this::settingsChanged,
                        this::revalidate,
                        this::bakeUsers,
                        report);
        previews =
                new GraphPreviews(
                        textures, background, host, library, texture, evaluator, this::folder);
        previews.onSeedPicked(
                seed -> {
                    texture.setSeed(seed);
                    settingsChanged();
                });
        final Map<String, String> saved = SavedState.get(SAVED_ALL);
        swatches = saved == null || !"false".equals(saved.get(SWATCHES));
        editor =
                new GraphEditor("graph-canvas", history)
                        .validator(this::validate)
                        .previews(id -> swatches ? previews.preview(id) : null)
                        .pinTooltips(previews::describePin)
                        .nodeKey(Key.D, previews::toggleShown)
                        .nodeMenuItems(this::nodeMenuItems);
    }

    /**
     * The graph panel's items in a node's menu: showing it in the Viewer, and measuring it.
     *
     * @param id The node.
     */
    private void nodeMenuItems(String id) {
        final boolean isShown = id.equals(previews.getShown());
        if (IkGui.menuItem(isShown ? "Stop showing in the Viewer" : "Show in the Viewer", "D")) {
            previews.toggleShown(id);
        }
        if (IkGui.menuItem("Measure")) {
            final String result = previews.measure(id);
            report.accept(
                    result == null ? id + " has nothing to measure yet" : "Measured " + result);
        }
        final Graph.Node node = history.getGraph().node(id);
        if (node != null && node.getType().startsWith(SubgraphNodes.TYPE_PREFIX)) {
            IkGui.separator();
            if (IkGui.menuItem("Open subgraph")) {
                openSubgraph(node.getType());
            }
        }
        if (IkGui.menuItem("Make subgraph from selection...", null, false, file != null)) {
            askToMakeSubgraph();
        }
    }

    /**
     * Open the subgraph a node places, remembering this graph to go back to.
     *
     * @param id The node.
     * @return True if it was opened.
     */
    public boolean openSubgraphOf(@NonNull String id) {
        final Graph.Node node = history.getGraph().node(id);
        if (node == null || !node.getType().startsWith(SubgraphNodes.TYPE_PREFIX)) {
            return false;
        }
        openSubgraph(node.getType());
        return !parents.isEmpty();
    }

    /**
     * Open the subgraph a node type places, remembering this graph to go back to.
     *
     * @param type The node's type.
     */
    private void openSubgraph(String type) {
        final Path subgraph = SubgraphNodes.fileOf(folder(), type);
        if (subgraph == null || file == null) {
            return;
        }
        if (history.isDirty()) {
            report.accept("Save the graph before opening one of its subgraphs");
            return;
        }
        final Path parent = file;
        if (open(subgraph)) {
            parents.push(parent);
        }
    }

    /** Go back to the graph the open subgraph was opened from. */
    public void back() {
        if (parents.isEmpty()) {
            return;
        }
        if (history.isDirty()) {
            report.accept("Save the subgraph before going back");
            return;
        }
        open(parents.pop());
    }

    /** Ask where to save a subgraph made from the selection, then make it. */
    private void askToMakeSubgraph() {
        final Set<String> chosen = new HashSet<>(editor.getSelection());
        if (chosen.isEmpty() || file == null) {
            report.accept("Select the nodes to make a subgraph of, in a saved graph");
            return;
        }
        ui.saveFile(
                "Save subgraph",
                file.getParent(),
                "subgraph" + SUFFIX,
                target -> {
                    if (target != null) {
                        makeSubgraph(chosen, target);
                    }
                },
                "yml");
    }

    /**
     * Make a subgraph from nodes and put it in their place, as one step to undo.
     *
     * @param chosen The nodes.
     * @param target The subgraph file to write.
     * @return The node placing it, or null if it couldn't be made.
     */
    public @Nullable String makeSubgraph(@NonNull Set<String> chosen, @NonNull Path target) {
        final Path folder = folder();
        if (folder == null) {
            report.accept("Save the graph before making a subgraph");
            return null;
        }
        final SubgraphMaker.Made made;
        try {
            made = SubgraphMaker.make(texture, folder, chosen, target.toAbsolutePath());
        } catch (IOException | IllegalArgumentException e) {
            status = "Can't make the subgraph: " + e.getMessage();
            report.accept(status);
            return null;
        }
        SubgraphNodes.refresh(made.type(), folder, library, history.getGraph());
        subgraphVersions.put(
                made.type(), SubgraphNodes.version(SubgraphNodes.fileOf(folder, made.type())));
        final String[] placed = new String[1];
        history.edit("Make subgraph", graph -> placed[0] = SubgraphMaker.replace(graph, made));
        editor.select(List.of(placed[0]));
        editor.revalidate();
        status = "";
        report.accept("Made " + target.getFileName() + " from " + made.nodes().size() + " nodes");
        return placed[0];
    }

    /**
     * Keep the subgraph node types up to date: those the graph places and those the search offers,
     * registered again when their files change, so their pins, parameters and every swatch reading
     * them follow; those no longer near the graph leave the search. Done every second on its own.
     */
    public void refreshSubgraphs() {
        final Path folder = folder();
        final Graph graph = history.getGraph();
        final Set<String> wanted = new HashSet<>();
        for (Graph.Node node : graph.nodes()) {
            if (node.getType().startsWith(SubgraphNodes.TYPE_PREFIX)) {
                wanted.add(node.getType());
            }
        }
        if (folder != null) {
            for (Path near : SubgraphNodes.near(folder, file)) {
                wanted.add(SubgraphNodes.typeFor(folder, near));
            }
        }
        boolean changed = false;
        for (String type : wanted) {
            final Path subgraph = SubgraphNodes.fileOf(folder, type);
            final String version = subgraph == null ? "" : SubgraphNodes.version(subgraph);
            if (!version.equals(subgraphVersions.get(type))
                    || library.getNodeTypes().get(type) == null) {
                SubgraphNodes.refresh(type, folder, library, graph);
                subgraphVersions.put(type, version);
                changed = true;
            }
        }
        for (NodeTypes.NodeType type : List.copyOf(library.getNodeTypes().all())) {
            if (type.id().startsWith(SubgraphNodes.TYPE_PREFIX) && !wanted.contains(type.id())) {
                library.unregister(type.id());
                subgraphVersions.remove(type.id());
            }
        }
        if (changed) {
            editor.revalidate();
        }
        subgraphsChecked = System.nanoTime();
    }

    /** Keep the swatches up to date, and save the view while it changes. */
    private void updatePreviews() {
        if (System.nanoTime() - subgraphsChecked > SUBGRAPH_CHECK_NANOS) {
            refreshSubgraphs();
        }
        previews.update();
        if (file == null) {
            return;
        }
        if (viewSettles > 0) {
            --viewSettles;
            return;
        }
        final Vector2f center = editor.getViewCenter();
        final Map<String, String> view = new LinkedHashMap<>();
        view.put(VIEW_X, Integer.toString(Math.round(center.x)));
        view.put(VIEW_Y, Integer.toString(Math.round(center.y)));
        view.put(VIEW_ZOOM, String.format(Locale.ROOT, "%.3f", editor.getCanvas().getZoom()));
        if (previews.getShown() != null) {
            view.put(VIEW_SHOWN, previews.getShown());
        }
        SavedState.put(SAVED + file, view);
    }

    /** Show the view saved for the graph's file: its pan, zoom and shown node. */
    private void restoreView() {
        viewSettles = 3;
        final Map<String, String> view = file == null ? null : SavedState.get(SAVED + file);
        if (view == null) {
            return;
        }
        try {
            editor.setView(
                    Float.parseFloat(view.getOrDefault(VIEW_X, "0")),
                    Float.parseFloat(view.getOrDefault(VIEW_Y, "0")),
                    Float.parseFloat(view.getOrDefault(VIEW_ZOOM, "1")));
        } catch (NumberFormatException e) {
            log.debug("Ignoring a broken saved view for {}", file);
        }
        final String shownNode = view.get(VIEW_SHOWN);
        if (shownNode != null && history.getGraph().node(shownNode) != null) {
            previews.show(shownNode);
        }
    }

    /**
     * The graph's problems by node: what evaluation reports, and Outputs beyond the first.
     *
     * @param graph The graph.
     * @return Each node's problem.
     */
    Map<String, String> validate(@NonNull Graph graph) {
        final Map<String, String> errors = new LinkedHashMap<>();
        final Evaluator.Settings settings = texture.settings(folder());
        int outputs = 0;
        for (Graph.Node node : graph.nodes()) {
            final NodeTypes.NodeType type = graph.typeOf(node.getId());
            if (type == null) {
                continue;
            }
            if (type.id().startsWith(SubgraphNodes.OUTPUT_PREFIX)) {
                evaluator.input(graph, settings, node.getId(), SubgraphNodes.OUTPUT_IN);
                continue;
            }
            if (OutputNodes.isOutput(type.id())) {
                if (++outputs > 1) {
                    errors.put(node.getId(), "A graph has one Output");
                }
                for (NodeTypes.Pin pin : type.inputs()) {
                    evaluator.input(graph, settings, node.getId(), pin.name());
                }
                continue;
            }
            if (!type.outputs().isEmpty() && !NodeTypes.isEditorType(type.id())) {
                evaluator.output(graph, settings, node.getId(), type.outputs().getFirst().name());
            }
        }
        for (Map.Entry<String, String> error : evaluator.getErrors().entrySet()) {
            if (graph.node(error.getKey()) != null) {
                errors.putIfAbsent(error.getKey(), error.getValue());
            }
        }
        return errors;
    }

    /**
     * The folder relative file names start from: the graph file's.
     *
     * @return The folder, or null for a graph not saved yet.
     */
    private @Nullable Path folder() {
        return file == null ? null : file.getParent();
    }

    /**
     * The panel's content: a toolbar, then the editor filling the rest.
     *
     * @return The content.
     */
    public Node<?> content() {
        return new Column("graph-root")
                .gap(4)
                .add(
                        new Immediate("graph-toolbar", this::drawToolbar)
                                .height(Sizing.fixed(TOOLBAR_HEIGHT)),
                        editor.getCanvas());
    }

    /**
     * Draw the toolbar: new, open, save, bake, undo and redo, and the file.
     *
     * @param frame The current frame.
     */
    private void drawToolbar(UiFrame frame) {
        updatePreviews();
        if (!parents.isEmpty()) {
            if (IkGui.button(
                    "Back to " + SubgraphNodes.titleOf(parents.peek().getFileName().toString()))) {
                frame.fire(this::back);
            }
            IkGui.sameLine();
        }
        if (IkGui.button("New")) {
            frame.fire(
                    () -> {
                        parents.clear();
                        newGraph();
                    });
        }
        IkGui.sameLine();
        if (IkGui.button("Open...")) {
            frame.fire(
                    () ->
                            ui.openFile(
                                    "Open texture graph",
                                    file == null ? null : file.toAbsolutePath().getParent(),
                                    chosen -> {
                                        if (chosen != null) {
                                            parents.clear();
                                            open(chosen);
                                        }
                                    },
                                    "yml"));
        }
        IkGui.sameLine();
        if (IkGui.button("Save...")) {
            frame.fire(this::askToSave);
        }
        IkGui.sameLine();
        if (IkGui.button("Bake")) {
            frame.fire(this::bake);
        }
        IkGui.sameLine();
        IkGui.setNextItemWidth(150);
        if (IkGui.beginCombo("##starter", "From a starter")) {
            for (Path starter : starters()) {
                if (IkGui.selectable(starterName(starter))) {
                    frame.fire(
                            () -> {
                                parents.clear();
                                openStarter(starter);
                            });
                }
            }
            IkGui.endCombo();
        }
        IkGui.sameLine();
        if (IkGui.checkbox("Swatches", swatches)) {
            frame.fire(() -> setSwatches(!swatches));
        }
        IkGui.sameLine();
        final String undo = history.nextUndo();
        if (IkGui.button(undo == null ? "Undo" : "Undo " + undo + "###undo")) {
            frame.fire(editor::undo);
        }
        IkGui.sameLine();
        final String redo = history.nextRedo();
        if (IkGui.button(redo == null ? "Redo" : "Redo " + redo + "###redo")) {
            frame.fire(editor::redo);
        }
        IkGui.sameLine();
        IkGui.textDisabled(
                (file == null ? "New texture graph" : file.getFileName().toString())
                        + (history.isDirty() ? " *" : "")
                        + (status.isEmpty() ? "" : "  " + status));
    }

    /**
     * Draw the selected node's details and parameters, or the graph's settings when no node is
     * selected, for the properties panel.
     *
     * @param frame The current frame.
     */
    public void drawProperties(@NonNull UiFrame frame) {
        final Graph graph = history.getGraph();
        IkGui.textUnformatted(
                String.format(
                        "%d nodes, %d links. Drag from a pin to link; Tab or a drop on empty"
                                + " space adds a node; hold 1, A, M or L and click to place one."
                                + " Right-click for more.",
                        graph.nodes().size(), graph.links().size()));
        if (editor.getSelection().size() != 1) {
            if (editor.getSelection().isEmpty()) {
                drawGraphSettings(frame);
            } else {
                IkGui.textDisabled(editor.getSelection().size() + " nodes selected");
            }
            previews.drawProperties(frame);
            return;
        }
        final String id = editor.getSelection().iterator().next();
        final NodeTypes.NodeType type = graph.typeOf(id);
        if (type == null) {
            return;
        }
        IkGui.separator();
        IkGui.textUnformatted(type.title() + "  (" + id + ", " + type.id() + ")");
        final String error = editor.errorOf(id);
        if (error != null) {
            IkGui.textWrapped(error);
        }
        if (type.params().isEmpty()) {
            IkGui.textDisabled("No parameters");
            previews.drawProperties(frame);
            return;
        }
        if (IkGui.beginTable("params", 2, TableFlags.ROW_BACKGROUND)) {
            for (NodeTypes.Param param : type.params()) {
                final String value = graph.param(id, param.name());
                field(
                        id + "/" + param.name(),
                        param.name(),
                        value,
                        edited -> frame.fire(() -> editor.setParam(id, param.name(), edited)));
            }
            IkGui.endTable();
        }
        previews.drawProperties(frame);
    }

    /**
     * Draw the graph's own settings: tile size, seed and what it bakes to.
     *
     * @param frame The current frame.
     */
    private void drawGraphSettings(UiFrame frame) {
        IkGui.separator();
        IkGui.textUnformatted("Texture graph");
        if (!IkGui.beginTable("graph-settings", 2, TableFlags.ROW_BACKGROUND)) {
            return;
        }
        field(
                "graph/width",
                "Width",
                Integer.toString(texture.getWidth()),
                edited -> frame.fire(() -> setSize(edited, true)));
        field(
                "graph/height",
                "Height",
                Integer.toString(texture.getHeight()),
                edited -> frame.fire(() -> setSize(edited, false)));
        field(
                "graph/seed",
                "Seed",
                Long.toString(texture.getSeed()),
                edited ->
                        frame.fire(
                                () -> {
                                    try {
                                        texture.setSeed(Long.parseLong(edited.trim()));
                                        settingsChanged();
                                    } catch (NumberFormatException e) {
                                        report.accept("A seed is a whole number");
                                    }
                                }));
        field(
                "graph/data",
                "Content folder",
                texture.getData() == null ? "" : texture.getData(),
                edited ->
                        frame.fire(
                                () -> {
                                    texture.setData(edited.isBlank() ? null : edited.trim());
                                    settingsChanged();
                                }));
        field(
                "graph/texture",
                "Texture",
                texture.getTexture() == null ? "" : texture.getTexture(),
                edited ->
                        frame.fire(
                                () -> {
                                    texture.setTexture(edited.isBlank() ? null : edited.trim());
                                    settingsChanged();
                                }));
        field(
                "graph/model",
                "Model",
                texture.getModel() == null ? "" : texture.getModel(),
                edited ->
                        frame.fire(
                                () -> {
                                    texture.setModel(edited.isBlank() ? null : edited.trim());
                                    settingsChanged();
                                }));
        field(
                "graph/maps",
                "Maps folder",
                texture.getMaps() == null ? "" : texture.getMaps(),
                edited ->
                        frame.fire(
                                () -> {
                                    texture.setMaps(edited.isBlank() ? null : edited.trim());
                                    settingsChanged();
                                }));
        IkGui.endTable();
        parameterPanel.draw(frame);
    }

    /**
     * Bake again every graph that reads a shared parameter file just saved: those in the folder
     * above the open graph's, and below it, as {@code art/textures/*} graphs share files there.
     *
     * @param shared The shared file.
     * @return How many graphs baked.
     */
    public int bakeUsers(@NonNull Path shared) {
        editor.revalidate();
        if (file == null) {
            return 0;
        }
        final Path root =
                file.getParent().getParent() == null
                        ? file.getParent()
                        : file.getParent().getParent();
        int baked = 0;
        final List<String> problems = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            for (Path graphFile :
                    files.filter(f -> f.toString().endsWith(SUFFIX)).sorted().toList()) {
                final TextureGraph other = new TextureGraph(TextureLibraries.standard());
                try {
                    other.read(Files.readString(graphFile), graphFile.getParent());
                } catch (IOException | RuntimeException e) {
                    continue;
                }
                final boolean reads =
                        other.getShared().stream()
                                .anyMatch(
                                        name ->
                                                graphFile
                                                        .getParent()
                                                        .resolve(name)
                                                        .toAbsolutePath()
                                                        .normalize()
                                                        .equals(shared));
                if (!reads) {
                    continue;
                }
                final Baker.Result result =
                        Baker.bake(
                                other,
                                new Evaluator(other.getLibrary()),
                                graphFile.getParent(),
                                null);
                if (result.ok()) {
                    ++baked;
                } else {
                    problems.add(
                            graphFile.getFileName() + ": " + String.join("; ", result.problems()));
                }
            }
        } catch (IOException e) {
            problems.add(e.getMessage());
        }
        status =
                "Saved "
                        + shared.getFileName()
                        + "; baked "
                        + baked
                        + " graphs reading it"
                        + (problems.isEmpty() ? "" : ", problems: " + String.join("; ", problems));
        report.accept(status);
        return baked;
    }

    /**
     * Set the tile's width or height.
     *
     * @param text The size, in pixels.
     * @param width Whether it is the width.
     */
    private void setSize(String text, boolean width) {
        try {
            final int size = Math.clamp(Integer.parseInt(text.trim()), 1, TextureGraph.MAX_SIZE);
            if (width) {
                texture.setWidth(size);
            } else {
                texture.setHeight(size);
            }
            settingsChanged();
        } catch (NumberFormatException e) {
            report.accept("A size is a whole number of pixels");
        }
    }

    /** The graph's settings changed: they are saved with it, so it is now unsaved. */
    private void settingsChanged() {
        history.markDirty();
    }

    /** Something the graph reads from outside changed, so evaluate it again. */
    private void revalidate() {
        editor.revalidate();
    }

    /**
     * One editable row of a two column table, keeping its text while it is typed in.
     *
     * @param key Which field, unique in the panel.
     * @param label The row's label.
     * @param value The value now.
     * @param edited Gets the new text once the field is left or Enter is pressed.
     */
    private void field(String key, String label, String value, Consumer<String> edited) {
        IkGui.tableNextRow();
        IkGui.tableNextColumn();
        IkGui.textUnformatted(label);
        IkGui.tableNextColumn();
        final IkString text = fields.computeIfAbsent(key, k -> new IkString(256));
        if (!key.equals(activeField) && !text.get().equals(value)) {
            // Undo, or another edit, changed it while it wasn't being typed in
            text.set(value, true);
        }
        IkGui.setNextItemWidth(-1);
        final boolean entered =
                IkGui.inputText("##" + key, text, InputTextFlags.ENTER_RETURNS_TRUE);
        if (IkGui.isItemActive()) {
            activeField = key;
        } else if (key.equals(activeField)) {
            activeField = null;
        }
        if ((entered || IkGui.isItemDeactivatedAfterEdit()) && !text.get().equals(value)) {
            edited.accept(text.get());
        }
    }

    /** Start a new, empty graph. */
    public void newGraph() {
        history.getGraph().restore(library.newGraph());
        history.clear();
        editor.select(List.of());
        texture.setWidth(TextureGraph.DEFAULT_SIZE);
        texture.setHeight(TextureGraph.DEFAULT_SIZE);
        texture.setSeed(1);
        texture.setData(null);
        texture.setTexture(null);
        texture.setModel(null);
        texture.setMaps(null);
        texture.setShared(List.of());
        for (GraphParameter parameter : texture.getParameters()) {
            texture.removeParameter(parameter.name());
        }
        parameterPanel.clear();
        evaluator.clear();
        previews.clear();
        file = null;
        status = "";
        fields.clear();
    }

    /**
     * The starter graphs in the converter's data folder, made from nodes alone, to begin a texture
     * from.
     *
     * @return Their files, by name.
     */
    public List<Path> starters() {
        if (starterFiles == null) {
            final java.io.File folder =
                    com.ikalagaming.launcher.PluginFolder.getResource(
                            com.ikalagaming.converter.ConverterPlugin.PLUGIN_NAME,
                            com.ikalagaming.launcher.PluginFolder.ResourceType.DATA,
                            STARTERS);
            try (java.util.stream.Stream<Path> list = Files.list(folder.toPath())) {
                starterFiles = list.filter(f -> f.toString().endsWith(SUFFIX)).sorted().toList();
            } catch (IOException | RuntimeException e) {
                log.debug("No starter graphs at {}", folder, e);
                starterFiles = List.of();
            }
        }
        return starterFiles;
    }

    /**
     * A starter graph's name.
     *
     * @param file Its file.
     * @return The name without the suffix, with spaces for underscores.
     */
    public static String starterName(@NonNull Path file) {
        final String name = file.getFileName().toString();
        return name.substring(0, name.length() - SUFFIX.length()).replace('_', ' ');
    }

    /**
     * Open a starter graph as a new graph, not yet saved anywhere.
     *
     * @param starter The starter's file.
     * @return True if it was read.
     */
    public boolean openStarter(@NonNull Path starter) {
        if (!open(starter)) {
            return false;
        }
        file = null;
        history.markDirty();
        status = "From the " + starterName(starter) + " starter; save it to bake it";
        report.accept(status);
        return true;
    }

    /**
     * Open a texture graph file.
     *
     * @param path The file.
     * @return True if it was read.
     */
    public boolean open(@NonNull Path path) {
        final TextureGraph read = new TextureGraph(library);
        final List<String> problems;
        try {
            problems = read.read(Files.readString(path), path.toAbsolutePath().getParent());
        } catch (IOException | RuntimeException e) {
            log.warn("Could not open the texture graph {}", path, e);
            status = "Can't open " + path.getFileName() + ": " + e.getMessage();
            report.accept(status);
            return false;
        }
        history.getGraph().restore(read.getGraph());
        history.clear();
        texture.setWidth(read.getWidth());
        texture.setHeight(read.getHeight());
        texture.setSeed(read.getSeed());
        texture.setData(read.getData());
        texture.setTexture(read.getTexture());
        texture.setModel(read.getModel());
        texture.setMaps(read.getMaps());
        texture.setShared(read.getShared());
        for (GraphParameter parameter : texture.getParameters()) {
            texture.removeParameter(parameter.name());
        }
        for (GraphParameter parameter : read.getParameters()) {
            texture.putParameter(parameter);
        }
        parameterPanel.clear();
        evaluator.clear();
        previews.clear();
        editor.select(List.of());
        fields.clear();
        file = path.toAbsolutePath();
        subgraphVersions.clear();
        refreshSubgraphs();
        // The history starts over, so its change count can't tell the editor to check again
        editor.revalidate();
        restoreView();
        status =
                problems.isEmpty()
                        ? ""
                        : problems.size() + " problems, left out: " + String.join("; ", problems);
        report.accept(
                "Opened "
                        + path.getFileName()
                        + (problems.isEmpty() ? "" : " with " + problems.size() + " problems"));
        return true;
    }

    /**
     * Save the graph to a file.
     *
     * @param path The file.
     * @return True if it was written.
     */
    public boolean save(@NonNull Path path) {
        final Path oldFolder = folder();
        final Path newFolder = path.toAbsolutePath().getParent();
        String text = texture.write();
        boolean rebased = false;
        if (oldFolder != null && !oldFolder.equals(newFolder)) {
            // Subgraphs are named from the graph's folder
            for (Graph.Node node : history.getGraph().nodes()) {
                final Path placed = SubgraphNodes.fileOf(oldFolder, node.getType());
                if (placed != null) {
                    text = text.replace(node.getType(), SubgraphNodes.typeFor(newFolder, placed));
                    rebased = true;
                }
            }
        }
        try {
            Files.writeString(path, text);
        } catch (IOException e) {
            log.warn("Could not save the texture graph to {}", path, e);
            status = "Can't save: " + e.getMessage();
            report.accept(status);
            return false;
        }
        final boolean moved = file == null || !file.equals(path.toAbsolutePath());
        file = path.toAbsolutePath();
        if (moved) {
            // Relative file names now start somewhere else
            evaluator.clear();
        }
        history.markSaved();
        if (rebased) {
            // Read back, so the nodes have the types their new folder names them by
            open(path);
        }
        refreshSubgraphs();
        status = "";
        report.accept("Saved " + path.getFileName());
        return true;
    }

    /**
     * Bake the graph to its maps, and show the baked texture.
     *
     * @return What the bake did.
     */
    public Baker.Result bake() {
        if (file == null) {
            final Baker.Result result =
                    new Baker.Result(
                            List.of(), List.of(), List.of("Save the graph before baking it"));
            status = result.problems().getFirst();
            report.accept(status);
            return result;
        }
        // Image files may have changed on disk since they were read
        evaluator.clear();
        final Baker.Result result = Baker.bake(texture, evaluator, file.getParent(), null);
        if (!result.ok()) {
            status = "Can't bake: " + String.join("; ", result.problems());
            report.accept(status);
            return result;
        }
        status =
                "Baked: "
                        + result.written().size()
                        + " written, "
                        + result.unchanged().size()
                        + " unchanged";
        report.accept(status);
        final List<Path> files = new java.util.ArrayList<>(result.written());
        files.addAll(result.unchanged());
        files.stream()
                .filter(f -> !f.getFileName().toString().matches(".*\\.[a-z]+\\.png"))
                .findFirst()
                .ifPresent(show);
        return result;
    }

    /** Ask where to save, then save. */
    private void askToSave() {
        ui.saveFile(
                "Save texture graph",
                file == null ? null : file.getParent(),
                file == null ? "untitled" + SUFFIX : file.getFileName().toString(),
                chosen -> {
                    if (chosen != null) {
                        save(chosen);
                    }
                },
                "yml");
    }
}
