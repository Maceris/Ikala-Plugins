package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.texgraph.Baker;
import com.ikalagaming.converter.texgraph.CoreNodes;
import com.ikalagaming.converter.texgraph.Evaluator;
import com.ikalagaming.converter.texgraph.OutputNodes;
import com.ikalagaming.converter.texgraph.TextureGraph;
import com.ikalagaming.converter.texgraph.TextureLibraries;
import com.ikalagaming.converter.texgraph.TextureLibrary;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.graph.GraphEditor;
import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.GraphHistory;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * The workbench's graph panel: the graph editor on texture graphs, opening and saving {@code
 * *.texgraph.yml} files and baking them to the maps the game reads. Node errors come from
 * evaluating the graph. Holding 1, A, M or L and clicking places a constant, add, multiply or lerp.
 * Render thread only.
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
     * Set up the tool with an empty graph.
     *
     * @param ui The UI, for the file dialogs.
     * @param report Shows what happened in the workbench's status line.
     * @param show Shows a baked texture.
     */
    public GraphTool(
            @NonNull UI ui, @NonNull Consumer<String> report, @NonNull Consumer<Path> show) {
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
        editor = new GraphEditor("graph-canvas", history).validator(this::validate);
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
        if (IkGui.button("New")) {
            frame.fire(this::newGraph);
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
        IkGui.endTable();
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
        evaluator.clear();
        file = null;
        status = "";
        fields.clear();
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
            problems = read.read(Files.readString(path));
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
        evaluator.clear();
        editor.select(List.of());
        fields.clear();
        file = path.toAbsolutePath();
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
        try {
            Files.writeString(path, texture.write());
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
