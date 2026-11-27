package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.graph.GraphEditor;
import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.GraphFile;
import com.ikalagaming.graphics.ui.graph.model.GraphHistory;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;
import com.ikalagaming.graphics.ui.graph.model.PinTypes;

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
 * The workbench's graph panel: the generic graph editor on a small demo set of math and color
 * nodes, opening and saving {@code *.graph.yml} files. It stands in for texture graphs until they
 * have their own nodes, so the editor can be used and checked in the real workbench. Render thread
 * only.
 */
@Slf4j
public class GraphTool {
    /** What a demo graph file's name ends with. */
    public static final String SUFFIX = ".graph.yml";

    /** The toolbar's height. */
    private static final float TOOLBAR_HEIGHT = 30;

    /** A pin type's ID. */
    static final String SCALAR = "scalar";

    /** A pin type's ID. */
    static final String MASK = "mask";

    /** A pin type's ID. */
    static final String COLOR = "color";

    /** The output node type's ID. */
    static final String OUTPUT = "out/output";

    /** The UI, for the file dialogs. */
    private final UI ui;

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
     * The file the graph was opened from or saved to. -- GETTER -- The file.
     *
     * @return The file, or null for a new graph.
     */
    @Getter private @Nullable Path file;

    /** What happened last, for the toolbar. */
    private String status = "";

    /** Reports what happened, to the workbench's status line. */
    private final Consumer<String> report;

    /** The parameter fields, by node and parameter, while a node is selected. */
    private final Map<String, IkString> fields = new HashMap<>();

    /** The field being typed in, which keeps its text until left, or null. */
    private String activeField;

    /**
     * Set up the tool with an empty graph.
     *
     * @param ui The UI, for the file dialogs.
     * @param report Shows what happened in the workbench's status line.
     */
    public GraphTool(@NonNull UI ui, @NonNull Consumer<String> report) {
        this.ui = ui;
        this.report = report;
        history = new GraphHistory(new Graph(nodeTypes(), pinTypes()));
        editor = new GraphEditor("graph-canvas", history).validator(GraphTool::validate);
    }

    /**
     * The demo's pin types: scalars, masks that are scalars from 0 to 1, and colors. A mask links
     * into a scalar as it is, and a scalar into a color through a Gray node.
     *
     * @return The types.
     */
    static PinTypes pinTypes() {
        return new PinTypes()
                .register(
                        new PinTypes.PinType(SCALAR, "Scalar", Color.rgba(0.62f, 0.64f, 0.68f, 1)))
                .register(new PinTypes.PinType(MASK, "Mask", Color.rgba(0.95f, 0.95f, 0.95f, 1)))
                .register(new PinTypes.PinType(COLOR, "Color", Color.rgba(1.0f, 0.78f, 0.25f, 1)))
                .convertDirectly(MASK, SCALAR)
                .convertWith(SCALAR, COLOR, "convert/gray");
    }

    /**
     * The demo's node types. Holding 1, A, M or L and clicking empty space places a constant, add,
     * multiply or lerp.
     *
     * @return The types.
     */
    static NodeTypes nodeTypes() {
        return new NodeTypes()
                .register(
                        type(
                                "math/constant",
                                "Constant",
                                "Inputs",
                                List.of(),
                                List.of(pin("value", SCALAR)),
                                List.of(new NodeTypes.Param("value", "0"))))
                .register(
                        type(
                                "math/color",
                                "Color",
                                "Inputs",
                                List.of(),
                                List.of(pin("color", COLOR)),
                                List.of(
                                        new NodeTypes.Param("r", "1"),
                                        new NodeTypes.Param("g", "1"),
                                        new NodeTypes.Param("b", "1"))))
                .register(
                        type(
                                "math/checker",
                                "Checker",
                                "Inputs",
                                List.of(),
                                List.of(pin("mask", MASK)),
                                List.of(new NodeTypes.Param("size", "4"))))
                .register(binary("math/add", "Add"))
                .register(binary("math/multiply", "Multiply"))
                .register(
                        type(
                                "math/lerp",
                                "Lerp",
                                "Math",
                                List.of(pin("a", COLOR), pin("b", COLOR), pin("t", SCALAR)),
                                List.of(pin("color", COLOR)),
                                List.of()))
                .register(
                        type(
                                "convert/gray",
                                "Gray",
                                "Convert",
                                List.of(pin("value", SCALAR)),
                                List.of(pin("color", COLOR)),
                                List.of()))
                .register(
                        type(
                                OUTPUT,
                                "Output",
                                "Output",
                                List.of(pin("color", COLOR)),
                                List.of(),
                                List.of()))
                .bindKey('1', "math/constant")
                .bindKey('A', "math/add")
                .bindKey('M', "math/multiply")
                .bindKey('L', "math/lerp");
    }

    /**
     * A node type at version 1.
     *
     * @param id The ID.
     * @param title The title.
     * @param category The category.
     * @param inputs The inputs.
     * @param outputs The outputs.
     * @param params The parameters.
     * @return The type.
     */
    private static NodeTypes.NodeType type(
            String id,
            String title,
            String category,
            List<NodeTypes.Pin> inputs,
            List<NodeTypes.Pin> outputs,
            List<NodeTypes.Param> params) {
        return new NodeTypes.NodeType(id, title, category, inputs, outputs, params, 1);
    }

    /**
     * A math node of two scalars.
     *
     * @param id The ID.
     * @param title The title.
     * @return The type.
     */
    private static NodeTypes.NodeType binary(String id, String title) {
        return type(
                id,
                title,
                "Math",
                List.of(pin("a", SCALAR), pin("b", SCALAR)),
                List.of(pin("result", SCALAR)),
                List.of());
    }

    /**
     * A pin.
     *
     * @param name The name.
     * @param type The type.
     * @return The pin.
     */
    private static NodeTypes.Pin pin(String name, String type) {
        return new NodeTypes.Pin(name, type);
    }

    /**
     * The demo's checks: every input linked, and one output.
     *
     * @param graph The graph.
     * @return Each node's problem.
     */
    static Map<String, String> validate(@NonNull Graph graph) {
        final Map<String, String> errors = new LinkedHashMap<>();
        int outputs = 0;
        for (Graph.Node node : graph.nodes()) {
            final NodeTypes.NodeType type = graph.typeOf(node.getId());
            if (type == null) {
                continue;
            }
            if (OUTPUT.equals(type.id()) && ++outputs > 1) {
                errors.put(node.getId(), "A graph has one output");
            }
            for (NodeTypes.Pin input : type.inputs()) {
                if (graph.linkInto(node.getId(), input.name()) == null) {
                    errors.put(node.getId(), "Input " + input.name() + " isn't linked");
                    break;
                }
            }
        }
        return errors;
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
     * Draw the toolbar: new, open, save, undo and redo, and the file.
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
                                    "Open graph",
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
                (file == null ? "New graph" : file.getFileName().toString())
                        + (history.isDirty() ? " *" : "")
                        + (status.isEmpty() ? "" : "  " + status));
    }

    /**
     * Draw the selected node's details and parameters, for the properties panel.
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
            IkGui.textDisabled(
                    editor.getSelection().isEmpty()
                            ? "Select a node to see its parameters"
                            : editor.getSelection().size() + " nodes selected");
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
                IkGui.tableNextRow();
                IkGui.tableNextColumn();
                IkGui.textUnformatted(param.name());
                IkGui.tableNextColumn();
                final String value = graph.param(id, param.name());
                final String key = id + "/" + param.name();
                final IkString field = fields.computeIfAbsent(key, k -> new IkString(64));
                if (!key.equals(activeField) && !field.get().equals(value)) {
                    // Undo, or another edit, changed it while it wasn't being typed in
                    field.set(value, true);
                }
                IkGui.setNextItemWidth(-1);
                final boolean entered =
                        IkGui.inputText(
                                "##" + param.name(), field, InputTextFlags.ENTER_RETURNS_TRUE);
                if (IkGui.isItemActive()) {
                    activeField = key;
                } else if (key.equals(activeField)) {
                    activeField = null;
                }
                if ((entered || IkGui.isItemDeactivatedAfterEdit()) && !field.get().equals(value)) {
                    final String edited = field.get();
                    frame.fire(() -> editor.setParam(id, param.name(), edited));
                }
            }
            IkGui.endTable();
        }
    }

    /** Start a new, empty graph. */
    public void newGraph() {
        history.getGraph()
                .restore(
                        new Graph(
                                history.getGraph().getNodeTypes(),
                                history.getGraph().getPinTypes()));
        history.clear();
        editor.select(List.of());
        file = null;
        status = "";
        fields.clear();
    }

    /**
     * Open a graph file.
     *
     * @param path The file.
     * @return True if it was read.
     */
    public boolean open(@NonNull Path path) {
        final Graph read =
                new Graph(history.getGraph().getNodeTypes(), history.getGraph().getPinTypes());
        final List<String> problems;
        try {
            problems = GraphFile.read(Files.readString(path), read, (type, from, params) -> {});
        } catch (IOException | RuntimeException e) {
            log.warn("Could not open the graph {}", path, e);
            status = "Can't open " + path.getFileName() + ": " + e.getMessage();
            report.accept(status);
            return false;
        }
        history.getGraph().restore(read);
        history.clear();
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
            Files.writeString(path, GraphFile.write(history.getGraph()));
        } catch (IOException e) {
            log.warn("Could not save the graph to {}", path, e);
            status = "Can't save: " + e.getMessage();
            report.accept(status);
            return false;
        }
        file = path.toAbsolutePath();
        history.markSaved();
        status = "";
        report.accept("Saved " + path.getFileName());
        return true;
    }

    /** Ask where to save, then save. */
    private void askToSave() {
        ui.saveFile(
                "Save graph",
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
