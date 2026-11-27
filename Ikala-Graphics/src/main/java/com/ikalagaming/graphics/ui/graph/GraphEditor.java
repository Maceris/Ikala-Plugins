package com.ikalagaming.graphics.ui.graph;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Canvas;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.GraphFile;
import com.ikalagaming.graphics.ui.graph.model.GraphHistory;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Edits a {@link Graph} on a {@link Canvas}: its nodes with their pins, the links between them, and
 * every edit through a {@link GraphHistory} so it can be undone. The graph's node and pin types
 * come from the caller, who gives them meaning; the editor only keeps to their pins' types.
 *
 * <p>With the mouse: drag from a pin to link it, refused if the types can't link and through a
 * conversion node if they need one; drag a linked input to pick its link up; drop a link on empty
 * canvas, or press Tab, for a search list of node types, filtered to those with a fitting pin.
 * Click a node to select it, Ctrl-click to add or take it away, drag on empty canvas for a
 * selection box, and drag selected nodes together. Delete removes the selection, Ctrl+C, Ctrl+V and
 * Ctrl+D copy, paste and duplicate it, and Ctrl+Z and Ctrl+Y undo and redo. The middle button pans
 * and the wheel zooms.
 *
 * <p>Put {@link #getCanvas()} in a surface or panel. Render thread only.
 */
public class GraphEditor {
    /** What a copied part of a graph starts with on the clipboard. */
    public static final String CLIPBOARD_HEADER = "# ikala graph\n";

    /** How far a duplicate lands from what it copied, in canvas units. */
    static final float DUPLICATE_OFFSET = 32;

    /** How many node types the search list shows. */
    static final int SEARCH_ROWS = 24;

    /** How far the mouse must move, in pixels, before a press on a node drags it. */
    static final float DRAG_SLOP = 3;

    /** How far a link's curve reaches out of its pins, at the least, in units. */
    static final float CURVE_REACH = 50;

    /** A link's width, in units. */
    static final float LINK_WIDTH = 2.5f;

    /** The selection box's fill. */
    private static final int BOX_FILL = Color.rgba(1.0f, 0.72f, 0.2f, 0.12f);

    /** The selection box's outline. */
    private static final int BOX_LINE = Color.rgba(1.0f, 0.72f, 0.2f, 0.8f);

    /** A link that would be refused. */
    private static final int REFUSED = Color.rgba(0.95f, 0.25f, 0.25f, 1);

    /** The name of the search popup. */
    private static final String SEARCH_POPUP = "graph-search";

    /**
     * The canvas the graph is drawn on. -- GETTER -- The canvas, to put in the UI.
     *
     * @return The canvas.
     */
    @Getter private final Canvas canvas;

    /**
     * The graph's history, through which every edit goes. -- GETTER -- The history.
     *
     * @return The history.
     */
    @Getter private final GraphHistory history;

    /** The views of the graph's nodes, by node ID. */
    private final Map<String, GraphNodeView> views = new HashMap<>();

    /** The history's change count the views were last made for. */
    private long viewsFor = -1;

    /** The selected nodes, in the order selected. */
    private final Set<String> selection = new LinkedHashSet<>();

    /** Errors to draw on nodes, from the caller's validation, or null for none. */
    private Function<Graph, Map<String, String>> validator;

    /** The errors by node, from the last validation. */
    private Map<String, String> errors = Map.of();

    /** The history's change count the errors are for. */
    private long errorsFor = -1;

    /** What the mouse is doing. */
    private enum Drag {
        /** Nothing. */
        NONE,
        /** Pressed on a node; a drag past the slop moves the selection. */
        PRESSED,
        /** Moving the selected nodes. */
        MOVING,
        /** Dragging a link from a pin. */
        LINKING,
        /** Dragging a selection box. */
        BOX
    }

    /** What the mouse is doing. */
    private Drag drag = Drag.NONE;

    /** Where the drag started, on the screen. */
    private final Vector2f dragStart = new Vector2f();

    /** The graph before a move, to note the move once it ends. */
    private Graph beforeMove;

    /** The pin a link is dragged from. */
    private PinRef linkFrom;

    /** The link picked up from an input, removed if dropped nowhere. */
    private Graph.Link pickedUp;

    /** The selection before a box drag with Ctrl held, which the box adds to. */
    private final Set<String> boxBase = new LinkedHashSet<>();

    /** The search popup's filter. */
    private final IkString searchText = new IkString(64);

    /** Where the search popup was opened, in canvas units: where a picked node goes. */
    private final Vector2f searchAt = new Vector2f();

    /** The pin a picked node links to, or null for none. */
    private PinRef searchLink;

    /** Whether the search popup should open this frame. */
    private boolean searchRequested;

    /** Whether the search field should take the keyboard. */
    private boolean searchFocus;

    /** Whether the search list was open the last frame it was drawn. */
    private boolean searchOpen;

    /** Scratch for pin positions. */
    private final Vector2f scratch = new Vector2f();

    /** Scratch for pin positions. */
    private final Vector2f scratch2 = new Vector2f();

    /**
     * A pin of a node.
     *
     * @param node The node's ID.
     * @param pin The pin's name.
     * @param input Whether it is an input.
     * @param type The pin type's ID.
     */
    public record PinRef(
            @NonNull String node, @NonNull String pin, boolean input, @NonNull String type) {}

    /**
     * Edit a graph.
     *
     * @param id The canvas's ID.
     * @param history The graph's history.
     */
    public GraphEditor(@NonNull String id, @NonNull GraphHistory history) {
        this.history = history;
        canvas =
                new Canvas(id)
                        .draggable(false)
                        .leftDragPans(false)
                        .underlay(this::drawLinks)
                        .overlay(this::handleInput);
    }

    /**
     * The graph edited.
     *
     * @return The graph.
     */
    public Graph getGraph() {
        return history.getGraph();
    }

    /**
     * Check the graph after each change, and draw the errors on the nodes they name.
     *
     * @param check Gives each node with a problem its message.
     * @return This editor.
     */
    public GraphEditor validator(Function<Graph, Map<String, String>> check) {
        validator = check;
        errorsFor = -1;
        return this;
    }

    /**
     * The selected nodes.
     *
     * @return Their IDs, in the order selected.
     */
    public Set<String> getSelection() {
        selection.retainAll(nodeIds());
        return Collections.unmodifiableSet(selection);
    }

    /**
     * Select nodes, in place of the selection.
     *
     * @param ids The nodes.
     */
    public void select(@NonNull Iterable<String> ids) {
        selection.clear();
        for (String id : ids) {
            selection.add(id);
        }
    }

    /**
     * Whether a node is selected.
     *
     * @param id The node.
     * @return True if selected.
     */
    public boolean isSelected(@NonNull String id) {
        return selection.contains(id);
    }

    /**
     * The error on a node, from the caller's validation.
     *
     * @param id The node.
     * @return The message, or null if it has none.
     */
    public @Nullable String errorOf(@NonNull String id) {
        return errors.get(id);
    }

    /**
     * Whether a pin has a link.
     *
     * @param node The node.
     * @param pin The pin.
     * @param input Whether it is an input.
     * @return True if linked.
     */
    public boolean isLinked(@NonNull String node, @NonNull String pin, boolean input) {
        for (Graph.Link link : getGraph().links()) {
            if (input
                    ? link.toNode().equals(node) && link.toPin().equals(pin)
                    : link.fromNode().equals(node) && link.fromPin().equals(pin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The IDs of the graph's nodes.
     *
     * @return The IDs.
     */
    private Set<String> nodeIds() {
        final Set<String> ids = new LinkedHashSet<>();
        for (Graph.Node node : getGraph().nodes()) {
            ids.add(node.getId());
        }
        return ids;
    }

    /**
     * Add a node, as picking it in the search does, and select it.
     *
     * @param type The node type.
     * @param x Where, across, in canvas units.
     * @param y Where, down.
     * @return The node's ID.
     */
    public String addNode(@NonNull String type, float x, float y) {
        final String[] added = new String[1];
        final NodeTypes.NodeType found = getGraph().getNodeTypes().get(type);
        final String title = found == null ? type : found.title();
        history.edit("Add " + title, graph -> added[0] = graph.add(type, x, y).getId());
        select(List.of(added[0]));
        return added[0];
    }

    /**
     * Link an output to an input, as a drag does.
     *
     * @param fromNode The node with the output.
     * @param fromPin The output.
     * @param toNode The node with the input.
     * @param toPin The input.
     * @return What happened; a refused link changes nothing.
     */
    public Graph.LinkResult link(
            @NonNull String fromNode,
            @NonNull String fromPin,
            @NonNull String toNode,
            @NonNull String toPin) {
        final Graph before = getGraph().copy();
        final Graph.LinkResult result = getGraph().link(fromNode, fromPin, toNode, toPin);
        if (result != Graph.LinkResult.REFUSED) {
            history.record(
                    result == Graph.LinkResult.CONVERTED ? "Link with a conversion" : "Link",
                    before);
        }
        return result;
    }

    /**
     * Set a node's parameter, so it can be undone.
     *
     * @param node The node.
     * @param name The parameter.
     * @param value The value, or null for its default.
     */
    public void setParam(@NonNull String node, @NonNull String name, @Nullable String value) {
        history.edit("Set " + name, graph -> graph.setParam(node, name, value));
    }

    /** Remove the selected nodes and their links. */
    public void deleteSelection() {
        final Set<String> doomed = new LinkedHashSet<>(getSelection());
        if (doomed.isEmpty()) {
            return;
        }
        history.edit("Delete", graph -> graph.remove(doomed));
        selection.clear();
    }

    /**
     * The selected nodes and the links among them, as clipboard text.
     *
     * @return The text, or null if nothing is selected.
     */
    public @Nullable String copySelection() {
        if (getSelection().isEmpty()) {
            return null;
        }
        return CLIPBOARD_HEADER + GraphFile.write(getGraph().part(selection));
    }

    /**
     * Add nodes from clipboard text, their top left corner at a point, and select them.
     *
     * @param text The text, from {@link #copySelection()}.
     * @param x Where, across, in canvas units.
     * @param y Where, down.
     * @return The new nodes, or none if the text isn't a copied graph.
     */
    public List<String> paste(@Nullable String text, float x, float y) {
        if (text == null || !text.startsWith(CLIPBOARD_HEADER)) {
            return List.of();
        }
        final Graph part = new Graph(getGraph().getNodeTypes(), getGraph().getPinTypes());
        try {
            GraphFile.read(text.substring(CLIPBOARD_HEADER.length()), part, (t, v, p) -> {});
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        if (part.nodes().isEmpty()) {
            return List.of();
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        for (Graph.Node node : part.nodes()) {
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
        }
        final float dx = x - minX;
        final float dy = y - minY;
        final List<String> added = new ArrayList<>();
        history.edit("Paste", graph -> added.addAll(graph.paste(part, dx, dy)));
        select(added);
        return added;
    }

    /**
     * Copy the selected nodes beside themselves.
     *
     * @return The new nodes.
     */
    public List<String> duplicateSelection() {
        final String text = copySelection();
        if (text == null) {
            return List.of();
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        for (String id : selection) {
            final Graph.Node node = getGraph().node(id);
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
        }
        return paste(text, minX + DUPLICATE_OFFSET, minY + DUPLICATE_OFFSET);
    }

    /** Undo the last edit, keeping the selection to nodes that are left. */
    public void undo() {
        history.undo();
        selection.retainAll(nodeIds());
    }

    /** Redo the last edit undone. */
    public void redo() {
        history.redo();
        selection.retainAll(nodeIds());
    }

    /**
     * Make the canvas's children match the graph's nodes, after a change.
     *
     * @param frame The current frame.
     */
    private void syncViews(UiFrame frame) {
        final Graph graph = getGraph();
        final long changes = history.getChanges();
        if (changes != viewsFor) {
            viewsFor = changes;
            final Set<String> ids = nodeIds();
            for (String id : new ArrayList<>(views.keySet())) {
                if (!ids.contains(id)) {
                    canvas.remove(views.remove(id));
                }
            }
            for (Graph.Node node : graph.nodes()) {
                if (!views.containsKey(node.getId())) {
                    final NodeTypes.NodeType type = graph.getNodeTypes().get(node.getType());
                    final int rows =
                            type == null
                                    ? 1
                                    : Math.max(type.inputs().size(), type.outputs().size());
                    final GraphNodeView view = new GraphNodeView(this, node.getId(), rows);
                    views.put(node.getId(), view);
                    canvas.add(view);
                }
            }
        }
        // Positions every frame, since a move changes them without a new edit until it ends
        for (Graph.Node node : graph.nodes()) {
            final GraphNodeView view = views.get(node.getId());
            if (view != null
                    && (view.getPositionX() != node.getX() || view.getPositionY() != node.getY())) {
                view.position(node.getX(), node.getY());
            }
        }
        if (validator != null && errorsFor != changes) {
            errorsFor = changes;
            errors = Map.copyOf(validator.apply(graph));
        }
    }

    /**
     * Draw the links, and the one being dragged, under the nodes.
     *
     * @param frame The current frame.
     */
    private void drawLinks(UiFrame frame) {
        syncViews(frame);
        final DrawList drawList = IkGui.getWindowDrawList();
        final float unit = canvas.getUnit();
        for (Graph.Link link : getGraph().links()) {
            final Vector2f from = pinAt(link.fromNode(), link.fromPin(), false, scratch);
            final Vector2f to = pinAt(link.toNode(), link.toPin(), true, scratch2);
            if (from == null || to == null) {
                continue;
            }
            final NodeTypes.Pin pin = getGraph().outputPin(link.fromNode(), link.fromPin());
            final int color = colorOf(pin == null ? "" : pin.type());
            curve(drawList, from.x, from.y, to.x, to.y, color, unit);
        }
    }

    /**
     * Draw a link's curve, leaving its output to the right and entering its input from the left.
     *
     * @param drawList Where to draw.
     * @param fromX The output's position.
     * @param fromY The output's position.
     * @param toX The input's position.
     * @param toY The input's position.
     * @param color The color.
     * @param unit Pixels per canvas unit.
     */
    static void curve(
            DrawList drawList,
            float fromX,
            float fromY,
            float toX,
            float toY,
            int color,
            float unit) {
        final float reach = Math.max(CURVE_REACH * unit, Math.abs(toX - fromX) * 0.5f);
        drawList.addBezierCubic(
                fromX,
                fromY,
                fromX + reach,
                fromY,
                toX - reach,
                toY,
                toX,
                toY,
                color,
                Math.max(1, LINK_WIDTH * unit));
    }

    /**
     * A pin type's color.
     *
     * @param type The type's ID.
     * @return The color.
     */
    private int colorOf(String type) {
        final var found = getGraph().getPinTypes().get(type);
        return found == null ? Color.rgba(0.6f, 0.6f, 0.6f, 1) : found.color();
    }

    /**
     * Where a pin is on the screen, from its node's view.
     *
     * @param node The node.
     * @param pin The pin.
     * @param input Whether it is an input.
     * @param out Receives the point.
     * @return The point, or null if the node or pin isn't shown.
     */
    private @Nullable Vector2f pinAt(String node, String pin, boolean input, Vector2f out) {
        final GraphNodeView view = views.get(node);
        final NodeTypes.NodeType type = getGraph().typeOf(node);
        if (view == null || type == null) {
            return null;
        }
        final List<NodeTypes.Pin> pins = input ? type.inputs() : type.outputs();
        for (int i = 0; i < pins.size(); ++i) {
            if (pins.get(i).name().equals(pin)) {
                return view.pinPosition(input, i, out);
            }
        }
        return null;
    }

    /**
     * The pin under a point on the screen.
     *
     * @param x The point.
     * @param y The point.
     * @return The pin, or null if there is none.
     */
    @Nullable PinRef pinUnder(float x, float y) {
        final Graph graph = getGraph();
        PinRef found = null;
        float best = Float.MAX_VALUE;
        for (Graph.Node node : graph.nodes()) {
            final GraphNodeView view = views.get(node.getId());
            final NodeTypes.NodeType type = graph.getNodeTypes().get(node.getType());
            if (view == null || type == null) {
                continue;
            }
            final float reach = GraphNodeView.PIN_RADIUS * GraphNodeView.PIN_REACH * view.scale();
            for (int side = 0; side < 2; ++side) {
                final boolean input = side == 0;
                final List<NodeTypes.Pin> pins = input ? type.inputs() : type.outputs();
                for (int i = 0; i < pins.size(); ++i) {
                    view.pinPosition(input, i, scratch);
                    final float distance = scratch.distance(x, y);
                    if (distance <= reach && distance < best) {
                        best = distance;
                        found =
                                new PinRef(
                                        node.getId(),
                                        pins.get(i).name(),
                                        input,
                                        pins.get(i).type());
                    }
                }
            }
        }
        return found;
    }

    /**
     * The node under a point on the screen: the last drawn, which is on top.
     *
     * @param x The point.
     * @param y The point.
     * @return The node's ID, or null.
     */
    @Nullable String nodeUnder(float x, float y) {
        String found = null;
        for (Node<?> child : canvas.getChildren()) {
            if (child instanceof GraphNodeView view && view.getRect().contains(x, y)) {
                found = view.getNodeId();
            }
        }
        return found;
    }

    /**
     * Read the mouse and keys, after the nodes are drawn: drags, clicks, the selection box, the
     * search list and the shortcuts. Draws the link and box being dragged over the nodes.
     *
     * @param frame The current frame.
     */
    private void handleInput(UiFrame frame) {
        final int id = IkGui.getID("graph-editor");
        final IkIO io = IkGui.getIO();
        final Vector2f mouse = IkGui.getMousePos();
        final boolean hovered = IkGui.isWindowHovered();
        final boolean popupOpen = IkGui.isPopupOpen(SEARCH_POPUP);

        if (hovered && !popupOpen && IkGui.isMouseClicked(MouseButton.LEFT)) {
            press(id, mouse, io.keyCtrl);
        }
        if (drag != Drag.NONE) {
            IkGuiInternal.keepAliveID(id);
            if (IkGui.isMouseDown(MouseButton.LEFT)) {
                dragging(mouse);
            } else {
                release(mouse, io.keyCtrl);
                if (IkGui.getContext().activeID == id) {
                    IkGuiInternal.clearActiveID();
                }
            }
        }
        drawDragged(mouse);

        if ((hovered || IkGui.isWindowFocused()) && !popupOpen && !io.wantTextInput) {
            shortcuts(mouse, io);
        }
        searchPopup(frame);
    }

    /**
     * Start what a press does: a link from a pin, a selection and maybe a move on a node, or a
     * selection box on empty canvas.
     *
     * @param id The editor's item ID, made active so the canvas and window leave the drag alone.
     * @param mouse The mouse.
     * @param ctrl Whether Ctrl is held.
     */
    private void press(int id, Vector2f mouse, boolean ctrl) {
        dragStart.set(mouse);
        IkGuiInternal.setActiveID(id, IkGuiInternal.getCurrentWindow());
        final PinRef pin = pinUnder(mouse.x, mouse.y);
        if (pin != null) {
            pickedUp = null;
            linkFrom = pin;
            if (pin.input()) {
                final Graph.Link link = getGraph().linkInto(pin.node(), pin.pin());
                if (link != null) {
                    // Pick the link up by its input: it hangs from its output until dropped
                    pickedUp = link;
                    final NodeTypes.Pin output =
                            getGraph().outputPin(link.fromNode(), link.fromPin());
                    linkFrom =
                            new PinRef(
                                    link.fromNode(),
                                    link.fromPin(),
                                    false,
                                    output == null ? pin.type() : output.type());
                }
            }
            drag = Drag.LINKING;
            return;
        }
        final String node = nodeUnder(mouse.x, mouse.y);
        if (node != null) {
            if (ctrl) {
                if (!selection.remove(node)) {
                    selection.add(node);
                }
                drag = Drag.NONE;
                IkGuiInternal.clearActiveID();
                return;
            }
            if (!selection.contains(node)) {
                select(List.of(node));
            }
            drag = Drag.PRESSED;
            return;
        }
        boxBase.clear();
        if (ctrl) {
            boxBase.addAll(selection);
        } else {
            selection.clear();
        }
        drag = Drag.BOX;
    }

    /**
     * Carry on a drag while the button is held.
     *
     * @param mouse The mouse.
     */
    private void dragging(Vector2f mouse) {
        if (drag == Drag.PRESSED && mouse.distance(dragStart) > DRAG_SLOP) {
            drag = Drag.MOVING;
            beforeMove = getGraph().copy();
        }
        if (drag == Drag.MOVING) {
            final Vector2f delta = IkGui.getIO().mouseDelta;
            final float unit = canvas.getUnit();
            for (String id : getSelection()) {
                final Graph.Node node = getGraph().node(id);
                getGraph().move(id, node.getX() + delta.x / unit, node.getY() + delta.y / unit);
            }
        } else if (drag == Drag.BOX) {
            selection.clear();
            selection.addAll(boxBase);
            final RectFloat box = box(mouse);
            for (Node<?> child : canvas.getChildren()) {
                if (child instanceof GraphNodeView view && overlaps(box, view.getRect())) {
                    selection.add(view.getNodeId());
                }
            }
        }
    }

    /**
     * Finish a drag when the button is let go.
     *
     * @param mouse The mouse.
     * @param ctrl Whether Ctrl is held.
     */
    private void release(Vector2f mouse, boolean ctrl) {
        final Drag ended = drag;
        drag = Drag.NONE;
        if (ended == Drag.MOVING && beforeMove != null) {
            history.record(selection.size() == 1 ? "Move" : "Move nodes", beforeMove);
            beforeMove = null;
        } else if (ended == Drag.LINKING) {
            dropLink(mouse);
        }
    }

    /**
     * Drop the link being dragged: onto a pin to link, onto empty canvas to pick a node for it, or
     * back where a picked-up link came from.
     *
     * @param mouse The mouse.
     */
    private void dropLink(Vector2f mouse) {
        final PinRef from = linkFrom;
        final Graph.Link lifted = pickedUp;
        linkFrom = null;
        pickedUp = null;
        if (from == null) {
            return;
        }
        final PinRef target = pinUnder(mouse.x, mouse.y);
        if (target != null && target.input() != from.input()) {
            final PinRef output = from.input() ? target : from;
            final PinRef input = from.input() ? from : target;
            if (lifted != null
                    && lifted.toNode().equals(input.node())
                    && lifted.toPin().equals(input.pin())) {
                // Put back where it was
                return;
            }
            final Graph before = getGraph().copy();
            if (lifted != null) {
                getGraph().unlink(lifted);
            }
            final Graph.LinkResult result =
                    getGraph().link(output.node(), output.pin(), input.node(), input.pin());
            if (result == Graph.LinkResult.REFUSED) {
                getGraph().restore(before);
                return;
            }
            history.record(
                    result == Graph.LinkResult.CONVERTED ? "Link with a conversion" : "Link",
                    before);
            return;
        }
        if (lifted != null) {
            // Dropped away from any pin: the link is gone
            history.edit("Unlink", graph -> graph.unlink(lifted));
            return;
        }
        if (target == null && nodeUnder(mouse.x, mouse.y) == null) {
            openSearch(mouse, from);
        }
    }

    /**
     * Draw the link or selection box being dragged.
     *
     * @param mouse The mouse.
     */
    private void drawDragged(Vector2f mouse) {
        final DrawList drawList = IkGui.getWindowDrawList();
        if (drag == Drag.LINKING && linkFrom != null) {
            final Vector2f pin = pinAt(linkFrom.node(), linkFrom.pin(), linkFrom.input(), scratch);
            if (pin == null) {
                return;
            }
            int color = colorOf(linkFrom.type());
            final PinRef target = pinUnder(mouse.x, mouse.y);
            if (target != null && target.input() != linkFrom.input()) {
                final String outType = linkFrom.input() ? target.type() : linkFrom.type();
                final String inType = linkFrom.input() ? linkFrom.type() : target.type();
                if (getGraph().getPinTypes().conversion(outType, inType).kind()
                        == com.ikalagaming.graphics.ui.graph.model.PinTypes.Kind.REFUSED) {
                    color = REFUSED;
                }
            }
            final float unit = canvas.getUnit();
            if (linkFrom.input()) {
                curve(drawList, mouse.x, mouse.y, pin.x, pin.y, color, unit);
            } else {
                curve(drawList, pin.x, pin.y, mouse.x, mouse.y, color, unit);
            }
        } else if (drag == Drag.BOX) {
            final RectFloat box = box(mouse);
            drawList.addRectFilled(
                    box.getLeft(), box.getTop(), box.getRight(), box.getBottom(), BOX_FILL);
            drawList.addRect(
                    box.getLeft(), box.getTop(), box.getRight(), box.getBottom(), BOX_LINE);
        }
    }

    /**
     * The selection box, from where the drag started to the mouse.
     *
     * @param mouse The mouse.
     * @return The box, on the screen.
     */
    private RectFloat box(Vector2f mouse) {
        return new RectFloat(
                Math.min(dragStart.x, mouse.x),
                Math.min(dragStart.y, mouse.y),
                Math.max(dragStart.x, mouse.x),
                Math.max(dragStart.y, mouse.y));
    }

    /**
     * Whether two rectangles overlap.
     *
     * @param a One.
     * @param b The other.
     * @return True if they share any area.
     */
    private static boolean overlaps(RectFloat a, RectFloat b) {
        return a.getLeft() < b.getRight()
                && b.getLeft() < a.getRight()
                && a.getTop() < b.getBottom()
                && b.getTop() < a.getBottom();
    }

    /**
     * The keyboard shortcuts.
     *
     * @param mouse The mouse, where pasted nodes and the search go.
     * @param io IkGui's input.
     */
    private void shortcuts(Vector2f mouse, IkIO io) {
        if (IkGui.isKeyPressed(Key.DELETE, false)) {
            deleteSelection();
        }
        if (IkGui.isKeyPressed(Key.TAB, false) && drag == Drag.NONE) {
            openSearch(mouse, null);
        }
        if (!io.keyCtrl) {
            return;
        }
        if (IkGui.isKeyPressed(Key.Z, false)) {
            if (io.keyShift) {
                redo();
            } else {
                undo();
            }
        } else if (IkGui.isKeyPressed(Key.Y, false)) {
            redo();
        } else if (IkGui.isKeyPressed(Key.C, false)) {
            final String text = copySelection();
            if (text != null) {
                IkGui.setClipboardText(text);
            }
        } else if (IkGui.isKeyPressed(Key.V, false)) {
            final Vector2f at = canvas.fromScreen(mouse.x, mouse.y);
            paste(IkGui.getClipboardText(), at.x, at.y);
        } else if (IkGui.isKeyPressed(Key.D, false)) {
            duplicateSelection();
        }
    }

    /**
     * Open the search list at a point, to add a node there.
     *
     * @param mouse The point, on the screen.
     * @param link A pin to link the new node to, or null.
     */
    private void openSearch(Vector2f mouse, @Nullable PinRef link) {
        final Vector2f at = canvas.fromScreen(mouse.x, mouse.y);
        searchAt.set(at);
        searchLink = link;
        searchText.set("", true);
        searchRequested = true;
        searchFocus = true;
    }

    /**
     * Open the search list at the canvas point given, for scripts and tests.
     *
     * @param x Where, across, in canvas units.
     * @param y Where, down.
     */
    public void openSearchAt(float x, float y) {
        searchAt.set(x, y);
        searchLink = null;
        searchText.set("", true);
        searchRequested = true;
        searchFocus = true;
    }

    /**
     * Draw the search list, if it is open: a filter, then the node types that fit, by category.
     *
     * @param frame The current frame.
     */
    private void searchPopup(UiFrame frame) {
        if (searchRequested) {
            searchRequested = false;
            IkGui.openPopup(SEARCH_POPUP);
        }
        searchOpen = IkGui.beginPopup(SEARCH_POPUP);
        if (!searchOpen) {
            return;
        }
        if (searchFocus) {
            IkGui.setKeyboardFocusHere();
            searchFocus = false;
        }
        final boolean entered =
                IkGui.inputTextWithHint(
                        "##search", "Search nodes", searchText, InputTextFlags.ENTER_RETURNS_TRUE);
        final PinRef link = searchLink;
        final List<NodeTypes.NodeType> found =
                getGraph()
                        .getNodeTypes()
                        .search(
                                searchText.get(),
                                getGraph().getPinTypes(),
                                link == null ? null : link.type(),
                                link != null && !link.input());
        NodeTypes.NodeType picked = entered && !found.isEmpty() ? found.getFirst() : null;
        String category = null;
        int shown = 0;
        for (NodeTypes.NodeType type : found) {
            if (shown++ >= SEARCH_ROWS) {
                break;
            }
            if (!type.category().equals(category)) {
                category = type.category();
                IkGui.textDisabled(category);
            }
            if (IkGui.selectable("  " + type.title() + "##" + type.id())) {
                picked = type;
            }
        }
        if (found.isEmpty()) {
            IkGui.textDisabled("No node fits");
        }
        if (IkGui.isKeyPressed(Key.ESCAPE, false)) {
            IkGui.closeCurrentPopup();
        }
        if (picked != null) {
            final NodeTypes.NodeType chosen = picked;
            IkGui.closeCurrentPopup();
            frame.fire(() -> place(chosen, link));
        }
        IkGui.endPopup();
    }

    /**
     * Add a node picked in the search where it was opened, linked to the pin it was opened from.
     *
     * @param type The node type.
     * @param link The pin, or null.
     */
    private void place(NodeTypes.NodeType type, @Nullable PinRef link) {
        final Graph before = getGraph().copy();
        final Graph.Node node = getGraph().add(type.id(), searchAt.x, searchAt.y);
        if (link != null) {
            final NodeTypes.Pin pin =
                    NodeTypes.fittingPin(
                            type, getGraph().getPinTypes(), link.type(), !link.input());
            if (pin != null) {
                if (link.input()) {
                    getGraph().link(node.getId(), pin.name(), link.node(), link.pin());
                } else {
                    getGraph().link(link.node(), link.pin(), node.getId(), pin.name());
                }
            }
        }
        history.record("Add " + type.title(), before);
        select(List.of(node.getId()));
    }

    /**
     * Pick the search list's first fitting node for a pin, as dropping a link on empty canvas and
     * pressing Enter does, for scripts and tests.
     *
     * @param query The filter.
     * @param at Where, in canvas units.
     * @param link The pin to link to, or null.
     * @return The new node's ID, or null if none fits.
     */
    public @Nullable String placeFirst(
            @NonNull String query, @NonNull Vector2f at, @Nullable PinRef link) {
        final List<NodeTypes.NodeType> found =
                getGraph()
                        .getNodeTypes()
                        .search(
                                query,
                                getGraph().getPinTypes(),
                                link == null ? null : link.type(),
                                link != null && !link.input());
        if (found.isEmpty()) {
            return null;
        }
        searchAt.set(at);
        place(found.getFirst(), link);
        return getSelection().iterator().next();
    }

    /**
     * Whether the search list is open.
     *
     * @return True if open.
     */
    public boolean isSearchOpen() {
        return searchOpen;
    }

    /**
     * The view of a node, for tests and scripts that click it.
     *
     * @param id The node.
     * @return Its view, or null before it is drawn.
     */
    public @Nullable GraphNodeView viewOf(@NonNull String id) {
        return views.get(id);
    }

    /**
     * Where a pin is on the screen, for tests and scripts that drag it.
     *
     * @param node The node.
     * @param pin The pin.
     * @param input Whether it is an input.
     * @return The point, or null if it isn't drawn.
     */
    public @Nullable Vector2f pinOnScreen(
            @NonNull String node, @NonNull String pin, boolean input) {
        final Vector2f at = pinAt(node, pin, input, new Vector2f());
        return at == null ? null : new Vector2f(at);
    }
}
