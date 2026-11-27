package com.ikalagaming.graphics.ui.graph;

import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.data.TextureView;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
 * <p>To keep a large graph readable: double-click a link for a reroute dot; drag a comment frame by
 * its title, which brings the nodes in it along, or by its corner to size it; right-click a node,
 * frame or empty space for collapsing, hiding unused pins, comments, frames, aligning,
 * straightening, upstream and downstream selection and removing unused nodes; hold a key the caller
 * bound to a node type and click empty space to place one; Ctrl+F finds nodes by name, parameter or
 * comment; the minimap in the corner shows the whole graph and pans where clicked. A node's error
 * shows when the mouse is over it.
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

    /** The name of a node's or the selection's context menu. */
    private static final String NODE_MENU = "graph-node-menu";

    /** The name of empty space's context menu. */
    private static final String CANVAS_MENU = "graph-canvas-menu";

    /** The name of a frame's context menu. */
    private static final String FRAME_MENU = "graph-frame-menu";

    /** The name of the comment popup. */
    private static final String COMMENT_POPUP = "graph-comment";

    /** The name of the frame popup. */
    private static final String FRAME_POPUP = "graph-frame";

    /** The name of the find popup. */
    private static final String FIND_POPUP = "graph-find";

    /** How tall a frame's title strip is, in units. */
    static final float FRAME_TITLE = 26;

    /** How big a frame's resize corner is, in units. */
    static final float FRAME_HANDLE = 14;

    /** How far around new frames reach past what they hold, in units. */
    static final float FRAME_MARGIN = 24;

    /** A new frame's color: a see-through blue gray. */
    public static final int FRAME_COLOR = Color.rgba(0.35f, 0.45f, 0.6f, 0.35f);

    /** The frame colors offered when editing one. */
    static final int[] FRAME_COLORS = {
        FRAME_COLOR,
        Color.rgba(0.6f, 0.35f, 0.35f, 0.35f),
        Color.rgba(0.35f, 0.6f, 0.4f, 0.35f),
        Color.rgba(0.6f, 0.55f, 0.3f, 0.35f),
        Color.rgba(0.5f, 0.38f, 0.62f, 0.35f)
    };

    /** How close, in pixels, a double-click must be to a link to put a reroute on it. */
    static final float LINK_REACH = 6;

    /** The minimap's size, in pixels. */
    static final float MINIMAP_WIDTH = 200;

    /** The minimap's size, in pixels. */
    static final float MINIMAP_HEIGHT = 130;

    /** How far the minimap sits from the canvas's corner, in pixels. */
    static final float MINIMAP_INSET = 10;

    /** The minimap's background. */
    private static final int MINIMAP_BACK = Color.rgba(0.08f, 0.08f, 0.1f, 0.85f);

    /** A node on the minimap. */
    private static final int MINIMAP_NODE = Color.rgba(0.55f, 0.6f, 0.7f, 1);

    /** The view's rectangle on the minimap. */
    private static final int MINIMAP_VIEW = Color.rgba(1, 1, 1, 0.9f);

    /** The digit keys, by digit, for placing nodes. */
    private static final Key[] DIGITS = {
        Key.ZERO, Key.ONE, Key.TWO, Key.THREE, Key.FOUR, Key.FIVE, Key.SIX, Key.SEVEN, Key.EIGHT,
        Key.NINE
    };

    /** Which way to line up the selection. */
    public enum Align {
        /** Left edges. */
        LEFT,
        /** Right edges. */
        RIGHT,
        /** Top edges. */
        TOP,
        /** Middles, across. */
        MIDDLE
    }

    /** The frame being dragged or sized. */
    private String frameDragged;

    /** The nodes moving with the frame being dragged. */
    private List<String> frameNodes = List.of();

    /** The node, frame or link the open context menu is for. */
    private String menuTarget;

    /** The link the open context menu is for, or null. */
    private Graph.Link menuLink;

    /** Where the open context menu was opened, in canvas units. */
    private final Vector2f menuAt = new Vector2f();

    /** Which context menu to open this frame, or null. */
    private String menuRequested;

    /** Which popup to open this frame, besides the search, or null. */
    private String popupRequested;

    /** The text being edited in the comment or frame popup. */
    private final IkString popupText = new IkString(256);

    /** The color picked in the frame popup. */
    private int popupColor;

    /** The node or frame the comment or frame popup edits. */
    private String popupTarget;

    /** The find popup's filter. */
    private final IkString findText = new IkString(64);

    /**
     * Whether the minimap shows. -- GETTER -- Whether the minimap shows.
     *
     * @return True if it shows.
     */
    @Getter private boolean minimap = true;

    /** Where the minimap was drawn last, on the screen, or empty before. */
    private final RectFloat minimapRect = new RectFloat();

    /** The canvas area the minimap shows, in units: left, top, and units per pixel. */
    private final float[] minimapMap = new float[3];

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
        BOX,
        /** Moving a frame and the nodes in it. */
        FRAME_MOVE,
        /** Sizing a frame by its corner. */
        FRAME_SIZE,
        /** Panning from the minimap. */
        MINIMAP
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

    /** Gives nodes their previews, or null for none. */
    private @Nullable Function<String, NodePreview> previews;

    /** Draws the caller's items in a node's menu, or null. */
    private @Nullable Consumer<String> nodeMenuItems;

    /** Gives pins their tooltips, or null. */
    private @Nullable PinDescriber pinTooltips;

    /** What keys do to nodes. */
    private final Map<Key, Consumer<String>> nodeKeys = new HashMap<>();

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
                        .overlay(this::handleInput)
                        .points(this::pointNamed);
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

    /** Check the graph again on the next frame, as when something it reads from outside changed. */
    public void revalidate() {
        errorsFor = -1;
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
     * What a node shows under its pins: an image, such as what the node makes, and a line of text
     * under it.
     *
     * @param texture The texture to show.
     * @param view How to show it.
     * @param minU The texture coordinate at the image's left edge.
     * @param minV The texture coordinate at its top edge.
     * @param maxU The texture coordinate at its right edge.
     * @param maxV The texture coordinate at its bottom edge.
     * @param aspect The image's width over its height, which it keeps.
     * @param caption The text under the image, or null for none.
     * @param marked Whether the node is marked in its title bar, as the one being shown elsewhere.
     */
    public record NodePreview(
            @NonNull TextureHandle texture,
            @NonNull TextureView view,
            float minU,
            float minV,
            float maxU,
            float maxV,
            float aspect,
            @Nullable String caption,
            boolean marked) {}

    /**
     * Give nodes previews: an image under their pins, from the caller, asked for each frame.
     *
     * @param source Gives a node's preview by its ID, or null for none; null to show none.
     * @return This editor.
     */
    public GraphEditor previews(@Nullable Function<String, NodePreview> source) {
        previews = source;
        return this;
    }

    /**
     * A node's preview.
     *
     * @param id The node.
     * @return The preview, or null for none.
     */
    @Nullable NodePreview previewOf(@NonNull String id) {
        return previews == null ? null : previews.apply(id);
    }

    /**
     * Add the caller's items to the end of a node's right-click menu. The items are drawn with
     * {@link IkGui#menuItem} while the menu is open.
     *
     * @param items Draws the items for a node, given its ID; null for none.
     * @return This editor.
     */
    public GraphEditor nodeMenuItems(@Nullable Consumer<String> items) {
        nodeMenuItems = items;
        return this;
    }

    /**
     * Show the caller's text in a tooltip while the mouse is over a pin, such as what flows out of
     * it.
     *
     * @param describe Gives a pin's text from its node, name and whether it is an input, or null
     *     for none; null to show nothing.
     * @return This editor.
     */
    public GraphEditor pinTooltips(@Nullable PinDescriber describe) {
        pinTooltips = describe;
        return this;
    }

    /** Gives text about a pin. */
    @FunctionalInterface
    public interface PinDescriber {
        /**
         * Text about a pin.
         *
         * @param node The node's ID.
         * @param pin The pin's name.
         * @param input Whether it is an input.
         * @return The text, or null for none.
         */
        @Nullable String describe(@NonNull String node, @NonNull String pin, boolean input);
    }

    /**
     * Do something to a node when a key is pressed without Ctrl while the editor is hovered or
     * focused: to the node under the mouse, or else the only selected node.
     *
     * @param key The key.
     * @param action Gets the node's ID.
     * @return This editor.
     */
    public GraphEditor nodeKey(@NonNull Key key, @NonNull Consumer<String> action) {
        nodeKeys.put(key, action);
        return this;
    }

    /**
     * The canvas position at the middle of the view, from the last frame, to save the view.
     *
     * @return The position, in canvas units.
     */
    public Vector2f getViewCenter() {
        final RectFloat rect = canvas.getRect();
        return canvas.fromScreen(
                (rect.getLeft() + rect.getRight()) / 2, (rect.getTop() + rect.getBottom()) / 2);
    }

    /**
     * Show a saved view: a zoom, and a canvas position in the middle, from the next frame.
     *
     * @param x The position's x, in canvas units.
     * @param y The position's y, in canvas units.
     * @param zoom The zoom.
     */
    public void setView(float x, float y, float zoom) {
        canvas.zoom(zoom);
        canvas.centerOn(x, y);
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
                    final GraphNodeView view = new GraphNodeView(this, node.getId());
                    views.put(node.getId(), view);
                    canvas.add(view);
                }
            }
        }
        // Collapsing, hiding pins and linking all change sizes; a view only resizes on change
        for (GraphNodeView view : views.values()) {
            view.fit();
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
        drawFrames(drawList, unit);
        for (Graph.Link link : getGraph().links()) {
            final Vector2f from = pinAt(link.fromNode(), link.fromPin(), false, scratch);
            final Vector2f to = pinAt(link.toNode(), link.toPin(), true, scratch2);
            if (from == null || to == null) {
                continue;
            }
            // The type that flows, so a link out of a reroute takes the color of its source
            final int color =
                    colorOf(getGraph().effectiveType(link.fromNode(), link.fromPin(), false));
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
        return view == null ? null : view.pinPosition(input, pin, out);
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
            if (view == null || !view.pinsGrabbable()) {
                continue;
            }
            final float reach = GraphNodeView.PIN_RADIUS * GraphNodeView.PIN_REACH * view.scale();
            for (int side = 0; side < 2; ++side) {
                final boolean input = side == 0;
                final List<NodeTypes.Pin> pins = view.shownPins(input);
                for (int i = 0; i < pins.size(); ++i) {
                    if (view.pinPosition(input, pins.get(i).name(), scratch) == null) {
                        continue;
                    }
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
        final boolean popupOpen = anyPopupOpen();

        if (hovered && !popupOpen && drag == Drag.NONE) {
            if (IkGui.isMouseDoubleClicked(MouseButton.LEFT)
                    && !(minimap && minimapRect.contains(mouse.x, mouse.y))
                    && pinUnder(mouse.x, mouse.y) == null
                    && nodeUnder(mouse.x, mouse.y) == null) {
                final Graph.Link link = linkUnder(mouse.x, mouse.y);
                if (link != null) {
                    final Vector2f at = canvas.fromScreen(mouse.x, mouse.y);
                    insertReroute(link, at.x, at.y);
                }
            } else if (IkGui.isMouseClicked(MouseButton.LEFT)) {
                press(id, mouse, io.keyCtrl);
            } else if (IkGui.isMouseClicked(MouseButton.RIGHT)) {
                openMenuAt(mouse);
            }
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
        drawMinimap();
        if (hovered && drag == Drag.NONE && !popupOpen) {
            final PinRef pin = pinTooltips == null ? null : pinUnder(mouse.x, mouse.y);
            final String about =
                    pin == null ? null : pinTooltips.describe(pin.node(), pin.pin(), pin.input());
            final String node = nodeUnder(mouse.x, mouse.y);
            final String error = node == null ? null : errorOf(node);
            if (about != null) {
                IkGui.setTooltip(about);
            } else if (error != null) {
                IkGui.setTooltip(error);
            }
        }

        if ((hovered || IkGui.isWindowFocused()) && !popupOpen && !io.wantTextInput) {
            shortcuts(mouse, io);
        }
        searchPopup(frame);
        menus();
        commentPopup();
        framePopup();
        findPopup();
    }

    /**
     * Whether any of the editor's popups is open, so clicks and keys go to it.
     *
     * @return True if one is.
     */
    private boolean anyPopupOpen() {
        return IkGui.isPopupOpen(SEARCH_POPUP)
                || IkGui.isPopupOpen(NODE_MENU)
                || IkGui.isPopupOpen(CANVAS_MENU)
                || IkGui.isPopupOpen(FRAME_MENU)
                || IkGui.isPopupOpen(COMMENT_POPUP)
                || IkGui.isPopupOpen(FRAME_POPUP)
                || IkGui.isPopupOpen(FIND_POPUP);
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
        if (minimap && minimapRect.contains(mouse.x, mouse.y)) {
            drag = Drag.MINIMAP;
            panFromMinimap(mouse);
            return;
        }
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
        if (pressFrame(mouse, ctrl)) {
            return;
        }
        final String keyed = keyedType();
        if (keyed != null) {
            final Vector2f at = canvas.fromScreen(mouse.x, mouse.y);
            addNode(keyed, at.x, at.y);
            drag = Drag.NONE;
            IkGuiInternal.clearActiveID();
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
        } else if (drag == Drag.FRAME_MOVE || drag == Drag.FRAME_SIZE) {
            final Vector2f delta = IkGui.getIO().mouseDelta;
            final float unit = canvas.getUnit();
            final Graph.Frame frame = getGraph().frame(frameDragged);
            if (frame != null) {
                final float dx = delta.x / unit;
                final float dy = delta.y / unit;
                if (drag == Drag.FRAME_MOVE) {
                    getGraph()
                            .placeFrame(
                                    frame.getId(),
                                    frame.getX() + dx,
                                    frame.getY() + dy,
                                    frame.getWidth(),
                                    frame.getHeight());
                    for (String id : frameNodes) {
                        final Graph.Node node = getGraph().node(id);
                        if (node != null) {
                            getGraph().move(id, node.getX() + dx, node.getY() + dy);
                        }
                    }
                } else {
                    getGraph()
                            .placeFrame(
                                    frame.getId(),
                                    frame.getX(),
                                    frame.getY(),
                                    frame.getWidth() + dx,
                                    frame.getHeight() + dy);
                }
            }
        } else if (drag == Drag.MINIMAP) {
            panFromMinimap(mouse);
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
        } else if ((ended == Drag.FRAME_MOVE || ended == Drag.FRAME_SIZE) && beforeMove != null) {
            if (mouse.distance(dragStart) > DRAG_SLOP) {
                history.record(ended == Drag.FRAME_MOVE ? "Move frame" : "Size frame", beforeMove);
            } else {
                getGraph().restore(beforeMove);
            }
            beforeMove = null;
            frameDragged = null;
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
        if (io.keyCtrl && IkGui.isKeyPressed(Key.F, false)) {
            findText.set("", true);
            popupRequested = FIND_POPUP;
            return;
        }
        if (IkGui.isKeyPressed(Key.TAB, false) && drag == Drag.NONE) {
            openSearch(mouse, null);
        }
        if (!io.keyCtrl) {
            for (Map.Entry<Key, Consumer<String>> bound : nodeKeys.entrySet()) {
                if (!IkGui.isKeyPressed(bound.getKey(), false)) {
                    continue;
                }
                String target = nodeUnder(mouse.x, mouse.y);
                if (target == null && selection.size() == 1) {
                    target = selection.iterator().next();
                }
                if (target != null) {
                    bound.getValue().accept(target);
                }
            }
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

    /**
     * Start dragging a frame by its title or sizing it by its corner, if the press is on one. The
     * topmost frame wins: the last drawn.
     *
     * @param mouse The mouse.
     * @param ctrl Whether Ctrl is held, which adds the frame to the selection.
     * @return True if the press was on a frame's title or corner.
     */
    private boolean pressFrame(Vector2f mouse, boolean ctrl) {
        final Vector2f at = canvas.fromScreen(mouse.x, mouse.y);
        final List<Graph.Frame> frames = new ArrayList<>(getGraph().frames());
        Collections.reverse(frames);
        for (Graph.Frame frame : frames) {
            final boolean corner =
                    at.x >= frame.getX() + frame.getWidth() - FRAME_HANDLE
                            && at.x <= frame.getX() + frame.getWidth()
                            && at.y >= frame.getY() + frame.getHeight() - FRAME_HANDLE
                            && at.y <= frame.getY() + frame.getHeight();
            final boolean title =
                    at.x >= frame.getX()
                            && at.x <= frame.getX() + frame.getWidth()
                            && at.y >= frame.getY()
                            && at.y <= frame.getY() + FRAME_TITLE;
            if (!corner && !title) {
                continue;
            }
            if (ctrl) {
                if (!selection.remove(frame.getId())) {
                    selection.add(frame.getId());
                }
            } else if (!selection.contains(frame.getId())) {
                select(List.of(frame.getId()));
            }
            frameDragged = frame.getId();
            frameNodes = getGraph().nodesIn(frame.getId());
            beforeMove = getGraph().copy();
            drag = corner ? Drag.FRAME_SIZE : Drag.FRAME_MOVE;
            return true;
        }
        return false;
    }

    /**
     * The node type whose bound key is held, for placing it with a click.
     *
     * @return The type's ID, or null if no bound key is held.
     */
    private @Nullable String keyedType() {
        for (Map.Entry<Character, String> binding : getGraph().getNodeTypes().keys().entrySet()) {
            final Key key = keyFor(binding.getKey());
            if (key != null && IkGui.isKeyDown(key)) {
                return binding.getValue();
            }
        }
        return null;
    }

    /**
     * The key for a character: a letter or a digit.
     *
     * @param character The character, upper case.
     * @return The key, or null if it isn't a letter or digit.
     */
    static @Nullable Key keyFor(char character) {
        if (character >= '0' && character <= '9') {
            return DIGITS[character - '0'];
        }
        if (character >= 'A' && character <= 'Z') {
            return Key.valueOf(String.valueOf(character));
        }
        return null;
    }

    /**
     * Draw the comment frames: a see-through body, a title strip with the title, and a corner to
     * size it by. Selected frames are outlined.
     *
     * @param drawList Where to draw.
     * @param unit Pixels per canvas unit.
     */
    private void drawFrames(DrawList drawList, float unit) {
        final float rounding = IkGui.getStyle().variable.frameRounding * unit;
        final int font = Math.max(1, Math.round(GraphNodeView.ROW * unit * 0.62f));
        for (Graph.Frame frame : getGraph().frames()) {
            final Vector2f min = canvas.toScreen(frame.getX(), frame.getY());
            final Vector2f max =
                    canvas.toScreen(
                            frame.getX() + frame.getWidth(), frame.getY() + frame.getHeight());
            final int color = frame.getColor();
            drawList.addRectFilled(min.x, min.y, max.x, max.y, color, rounding);
            final int strip = (color & 0xFFFFFF00) | Math.min(255, (color & 0xFF) * 2);
            drawList.addRectFilled(
                    min.x,
                    min.y,
                    max.x,
                    min.y + FRAME_TITLE * unit,
                    strip,
                    rounding,
                    com.ikalagaming.graphics.gui.flags.DrawFlags.ROUND_CORNERS_TOP);
            drawList.addText(
                    font,
                    min.x + 8 * unit,
                    min.y + (FRAME_TITLE * unit - font) / 2,
                    Color.rgba(1, 1, 1, 0.95f),
                    frame.getText(),
                    0);
            final float handle = FRAME_HANDLE * unit;
            drawList.addTriangleFilled(
                    max.x, max.y - handle, max.x, max.y, max.x - handle, max.y, strip);
            if (selection.contains(frame.getId())) {
                drawList.addRect(
                        min.x, min.y, max.x, max.y, GraphNodeView.SELECTED, rounding, 0, 2);
            }
        }
    }

    /**
     * The link nearest a point on the screen, if it is close enough.
     *
     * @param x The point.
     * @param y The point.
     * @return The link, or null.
     */
    @Nullable Graph.Link linkUnder(float x, float y) {
        final float unit = canvas.getUnit();
        Graph.Link best = null;
        float bestDistance = LINK_REACH;
        for (Graph.Link link : getGraph().links()) {
            final Vector2f from = pinAt(link.fromNode(), link.fromPin(), false, scratch);
            final Vector2f to = pinAt(link.toNode(), link.toPin(), true, scratch2);
            if (from == null || to == null) {
                continue;
            }
            final float distance = curveDistance(from.x, from.y, to.x, to.y, unit, x, y);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = link;
            }
        }
        return best;
    }

    /**
     * How far a point is from a link's curve, by sampling it as {@link #curve} draws it.
     *
     * @param fromX The output's position.
     * @param fromY The output's position.
     * @param toX The input's position.
     * @param toY The input's position.
     * @param unit Pixels per canvas unit.
     * @param x The point.
     * @param y The point.
     * @return The distance, in pixels.
     */
    static float curveDistance(
            float fromX, float fromY, float toX, float toY, float unit, float x, float y) {
        final float reach = Math.max(CURVE_REACH * unit, Math.abs(toX - fromX) * 0.5f);
        final int steps = 32;
        float best = Float.MAX_VALUE;
        float lastX = fromX;
        float lastY = fromY;
        for (int i = 1; i <= steps; ++i) {
            final float t = i / (float) steps;
            final float u = 1 - t;
            final float px =
                    u * u * u * fromX
                            + 3 * u * u * t * (fromX + reach)
                            + 3 * u * t * t * (toX - reach)
                            + t * t * t * toX;
            final float py =
                    u * u * u * fromY
                            + 3 * u * u * t * fromY
                            + 3 * u * t * t * toY
                            + t * t * t * toY;
            best = Math.min(best, segmentDistance(lastX, lastY, px, py, x, y));
            lastX = px;
            lastY = py;
        }
        return best;
    }

    /**
     * How far a point is from a line segment.
     *
     * @param ax The segment's start.
     * @param ay The segment's start.
     * @param bx The segment's end.
     * @param by The segment's end.
     * @param x The point.
     * @param y The point.
     * @return The distance.
     */
    private static float segmentDistance(float ax, float ay, float bx, float by, float x, float y) {
        final float dx = bx - ax;
        final float dy = by - ay;
        final float length = dx * dx + dy * dy;
        final float t =
                length == 0 ? 0 : Math.clamp(((x - ax) * dx + (y - ay) * dy) / length, 0, 1);
        final float cx = ax + t * dx - x;
        final float cy = ay + t * dy - y;
        return (float) Math.sqrt(cx * cx + cy * cy);
    }

    /**
     * Put a reroute dot on a link, splitting it in two.
     *
     * @param link The link.
     * @param x Where, across, in canvas units: the dot's middle.
     * @param y Where, down.
     * @return The reroute's ID.
     */
    public String insertReroute(@NonNull Graph.Link link, float x, float y) {
        final String[] added = new String[1];
        final float half = GraphNodeView.REROUTE_SIZE / 2;
        history.edit(
                "Add reroute",
                graph -> {
                    graph.unlink(link);
                    final Graph.Node reroute = graph.add(NodeTypes.REROUTE, x - half, y - half);
                    graph.link(link.fromNode(), link.fromPin(), reroute.getId(), "in");
                    graph.link(reroute.getId(), "out", link.toNode(), link.toPin());
                    added[0] = reroute.getId();
                });
        select(List.of(added[0]));
        return added[0];
    }

    /**
     * Collapse nodes to their titles, or open them.
     *
     * @param ids The nodes.
     * @param collapsed Whether to collapse them.
     */
    public void setCollapsed(@NonNull Collection<String> ids, boolean collapsed) {
        history.edit(
                collapsed ? "Collapse" : "Expand",
                graph -> ids.forEach(id -> graph.setCollapsed(id, collapsed)));
    }

    /**
     * Hide nodes' unused pins, or show them.
     *
     * @param ids The nodes.
     * @param hide Whether to hide them.
     */
    public void setHideUnused(@NonNull Collection<String> ids, boolean hide) {
        history.edit(
                hide ? "Hide unused pins" : "Show all pins",
                graph -> ids.forEach(id -> graph.setHideUnused(id, hide)));
    }

    /**
     * Put a comment over a node, or take it away.
     *
     * @param id The node.
     * @param comment The comment, or null or blank for none.
     */
    public void setComment(@NonNull String id, @Nullable String comment) {
        history.edit("Comment", graph -> graph.setComment(id, comment));
    }

    /**
     * Put a comment frame around the selected nodes, or an empty one at a point.
     *
     * @param text Its title.
     * @param x Where, if nothing is selected, in canvas units.
     * @param y Where, if nothing is selected.
     * @return The frame's ID.
     */
    public String frameSelection(@NonNull String text, float x, float y) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (String id : getSelection()) {
            final Graph.Node node = getGraph().node(id);
            final float[] size = sizeOf(id);
            if (node == null || size == null) {
                continue;
            }
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + size[0]);
            maxY = Math.max(maxY, node.getY() + size[1]);
        }
        final float left;
        final float top;
        final float width;
        final float height;
        if (minX == Float.MAX_VALUE) {
            left = x;
            top = y;
            width = 320;
            height = 200;
        } else {
            left = minX - FRAME_MARGIN;
            top = minY - FRAME_MARGIN - FRAME_TITLE;
            width = maxX - minX + 2 * FRAME_MARGIN;
            height = maxY - minY + 2 * FRAME_MARGIN + FRAME_TITLE;
        }
        final String[] added = new String[1];
        history.edit(
                "Add frame",
                graph ->
                        added[0] =
                                graph.addFrame(left, top, width, height, text, FRAME_COLOR)
                                        .getId());
        select(List.of(added[0]));
        return added[0];
    }

    /**
     * A node's size on the canvas, from its view.
     *
     * @param id The node.
     * @return Its width and height, in canvas units, or null before it is drawn.
     */
    private float[] sizeOf(String id) {
        final GraphNodeView view = views.get(id);
        final float unit = canvas.getUnit();
        if (view == null || unit <= 0) {
            return null;
        }
        return new float[] {view.getRect().getWidth() / unit, view.getRect().getHeight() / unit};
    }

    /**
     * Line the selected nodes up.
     *
     * @param how Which edges or middles to line up.
     */
    public void align(@NonNull Align how) {
        final List<String> nodes = new ArrayList<>();
        for (String id : getSelection()) {
            if (getGraph().node(id) != null && sizeOf(id) != null) {
                nodes.add(id);
            }
        }
        if (nodes.size() < 2) {
            return;
        }
        float target =
                switch (how) {
                    case LEFT, TOP, MIDDLE -> Float.MAX_VALUE;
                    case RIGHT -> -Float.MAX_VALUE;
                };
        float middleSum = 0;
        for (String id : nodes) {
            final Graph.Node node = getGraph().node(id);
            final float[] size = sizeOf(id);
            switch (how) {
                case LEFT -> target = Math.min(target, node.getX());
                case RIGHT -> target = Math.max(target, node.getX() + size[0]);
                case TOP -> target = Math.min(target, node.getY());
                case MIDDLE -> middleSum += node.getX() + size[0] / 2;
            }
        }
        final float middle = middleSum / nodes.size();
        final float edge = target;
        history.edit(
                "Align",
                graph -> {
                    for (String id : nodes) {
                        final Graph.Node node = graph.node(id);
                        final float[] size = sizeOf(id);
                        switch (how) {
                            case LEFT -> graph.move(id, edge, node.getY());
                            case RIGHT -> graph.move(id, edge - size[0], node.getY());
                            case TOP -> graph.move(id, node.getX(), edge);
                            case MIDDLE -> graph.move(id, middle - size[0] / 2, node.getY());
                        }
                    }
                });
    }

    /**
     * Move a node up or down so its first linked input is level with the output feeding it, which
     * straightens that link.
     *
     * @param id The node.
     * @return True if it had a linked input to straighten.
     */
    public boolean straighten(@NonNull String id) {
        final GraphNodeView view = views.get(id);
        if (view == null) {
            return false;
        }
        for (NodeTypes.Pin pin : view.shownPins(true)) {
            final Graph.Link link = getGraph().linkInto(id, pin.name());
            if (link == null) {
                continue;
            }
            final Vector2f in = pinAt(id, pin.name(), true, new Vector2f());
            final Vector2f out = pinAt(link.fromNode(), link.fromPin(), false, new Vector2f());
            if (in == null || out == null) {
                return false;
            }
            final float dy = (out.y - in.y) / canvas.getUnit();
            final Graph.Node node = getGraph().node(id);
            history.edit("Straighten", graph -> graph.move(id, node.getX(), node.getY() + dy));
            return true;
        }
        return false;
    }

    /**
     * Select a node and everything upstream of it.
     *
     * @param id The node.
     */
    public void selectUpstream(@NonNull String id) {
        final List<String> chosen = new ArrayList<>(getGraph().upstream(id));
        chosen.addFirst(id);
        select(chosen);
    }

    /**
     * Select a node and everything downstream of it.
     *
     * @param id The node.
     */
    public void selectDownstream(@NonNull String id) {
        final List<String> chosen = new ArrayList<>(getGraph().downstream(id));
        chosen.addFirst(id);
        select(chosen);
    }

    /**
     * Remove the nodes nothing uses.
     *
     * @return How many were removed.
     */
    public int removeUnused() {
        final Set<String> unused = getGraph().unused();
        if (!unused.isEmpty()) {
            history.edit("Remove unused nodes", graph -> graph.remove(unused));
            selection.removeAll(unused);
        }
        return unused.size();
    }

    /**
     * The nodes and frames whose title, ID, parameters or comment contain some text, ignoring case.
     *
     * @param query The text.
     * @return Their IDs, nodes first, in the order added.
     */
    public List<String> find(@NonNull String query) {
        final String wanted = query.trim().toLowerCase(java.util.Locale.ROOT);
        final List<String> found = new ArrayList<>();
        if (wanted.isEmpty()) {
            return found;
        }
        for (Graph.Node node : getGraph().nodes()) {
            final NodeTypes.NodeType type = getGraph().typeOf(node.getId());
            final StringBuilder text = new StringBuilder(node.getId()).append(' ');
            if (type != null) {
                text.append(type.title()).append(' ');
            }
            node.getParams()
                    .forEach(
                            (name, value) ->
                                    text.append(name).append(' ').append(value).append(' '));
            if (node.getComment() != null) {
                text.append(node.getComment());
            }
            if (text.toString().toLowerCase(java.util.Locale.ROOT).contains(wanted)) {
                found.add(node.getId());
            }
        }
        for (Graph.Frame frame : getGraph().frames()) {
            if ((frame.getId() + " " + frame.getText())
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains(wanted)) {
                found.add(frame.getId());
            }
        }
        return found;
    }

    /**
     * Select a node or frame and pan the view to its middle.
     *
     * @param id The node or frame.
     */
    public void focus(@NonNull String id) {
        final Graph.Node node = getGraph().node(id);
        final Graph.Frame frame = getGraph().frame(id);
        if (node != null) {
            final float[] size = sizeOf(id);
            final float width = size == null ? GraphNodeView.WIDTH : size[0];
            final float height = size == null ? GraphNodeView.TITLE : size[1];
            canvas.centerOn(node.getX() + width / 2, node.getY() + height / 2);
        } else if (frame != null) {
            canvas.centerOn(
                    frame.getX() + frame.getWidth() / 2, frame.getY() + frame.getHeight() / 2);
        } else {
            return;
        }
        select(List.of(id));
    }

    /**
     * Where the minimap was drawn last, on the screen.
     *
     * @return A copy of its rectangle, empty when it is hidden or before it is drawn.
     */
    public RectFloat getMinimapRect() {
        return new RectFloat(minimapRect);
    }

    /**
     * Show or hide the minimap.
     *
     * @param show Whether it shows.
     */
    public void setMinimap(boolean show) {
        minimap = show;
    }

    /**
     * Draw the minimap in the canvas's bottom right corner: every frame and node as a box, and the
     * view as an outline.
     */
    private void drawMinimap() {
        minimapRect.set(0, 0, 0, 0);
        if (!minimap || getGraph().nodes().isEmpty()) {
            return;
        }
        // The clip rectangle leaves out the scrollbars, which would take the minimap's clicks
        final DrawList drawList = IkGui.getWindowDrawList();
        final float viewLeft = drawList.getClipRectMinX();
        final float viewTop = drawList.getClipRectMinY();
        final float viewRight = drawList.getClipRectMaxX();
        final float viewBottom = drawList.getClipRectMaxY();
        final float right = viewRight - MINIMAP_INSET;
        final float bottom = viewBottom - MINIMAP_INSET;
        final float left = right - MINIMAP_WIDTH;
        final float top = bottom - MINIMAP_HEIGHT;
        if (left < viewLeft || top < viewTop) {
            return;
        }
        // What to fit: every node and frame, and the view
        final Vector2f viewMin = canvas.fromScreen(viewLeft, viewTop);
        final Vector2f viewMax = canvas.fromScreen(viewRight, viewBottom);
        float minX = viewMin.x;
        float minY = viewMin.y;
        float maxX = viewMax.x;
        float maxY = viewMax.y;
        for (Graph.Node node : getGraph().nodes()) {
            final float[] size = sizeOf(node.getId());
            final float w = size == null ? GraphNodeView.WIDTH : size[0];
            final float h = size == null ? GraphNodeView.TITLE : size[1];
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + w);
            maxY = Math.max(maxY, node.getY() + h);
        }
        for (Graph.Frame frame : getGraph().frames()) {
            minX = Math.min(minX, frame.getX());
            minY = Math.min(minY, frame.getY());
            maxX = Math.max(maxX, frame.getX() + frame.getWidth());
            maxY = Math.max(maxY, frame.getY() + frame.getHeight());
        }
        final float scale = Math.max((maxX - minX) / MINIMAP_WIDTH, (maxY - minY) / MINIMAP_HEIGHT);
        minimapMap[0] = minX;
        minimapMap[1] = minY;
        minimapMap[2] = Math.max(scale, 1e-3f);
        minimapRect.set(left, top, right, bottom);
        drawList.addRectFilled(left, top, right, bottom, MINIMAP_BACK, 4);
        for (Graph.Frame frame : getGraph().frames()) {
            drawList.addRectFilled(
                    mapX(frame.getX()),
                    mapY(frame.getY()),
                    mapX(frame.getX() + frame.getWidth()),
                    mapY(frame.getY() + frame.getHeight()),
                    frame.getColor());
        }
        for (Graph.Node node : getGraph().nodes()) {
            final float[] size = sizeOf(node.getId());
            final float w = size == null ? GraphNodeView.WIDTH : size[0];
            final float h = size == null ? GraphNodeView.TITLE : size[1];
            drawList.addRectFilled(
                    mapX(node.getX()),
                    mapY(node.getY()),
                    Math.max(mapX(node.getX()) + 1, mapX(node.getX() + w)),
                    Math.max(mapY(node.getY()) + 1, mapY(node.getY() + h)),
                    selection.contains(node.getId()) ? GraphNodeView.SELECTED : MINIMAP_NODE);
        }
        drawList.addRect(
                mapX(viewMin.x), mapY(viewMin.y), mapX(viewMax.x), mapY(viewMax.y), MINIMAP_VIEW);
    }

    /**
     * A canvas position across, on the minimap.
     *
     * @param x The position, in canvas units.
     * @return The point on the screen.
     */
    private float mapX(float x) {
        return minimapRect.getLeft() + (x - minimapMap[0]) / minimapMap[2];
    }

    /**
     * A canvas position down, on the minimap.
     *
     * @param y The position, in canvas units.
     * @return The point on the screen.
     */
    private float mapY(float y) {
        return minimapRect.getTop() + (y - minimapMap[1]) / minimapMap[2];
    }

    /**
     * Pan the view to the canvas point under the mouse on the minimap.
     *
     * @param mouse The mouse.
     */
    private void panFromMinimap(Vector2f mouse) {
        if (minimapRect.getWidth() <= 0) {
            return;
        }
        final float x =
                minimapMap[0]
                        + (Math.clamp(mouse.x, minimapRect.getLeft(), minimapRect.getRight())
                                        - minimapRect.getLeft())
                                * minimapMap[2];
        final float y =
                minimapMap[1]
                        + (Math.clamp(mouse.y, minimapRect.getTop(), minimapRect.getBottom())
                                        - minimapRect.getTop())
                                * minimapMap[2];
        canvas.centerOn(x, y);
    }

    /**
     * Open the context menu for what is under a right-click: a node, a frame, or empty space.
     *
     * @param mouse The mouse.
     */
    private void openMenuAt(Vector2f mouse) {
        final Vector2f at = canvas.fromScreen(mouse.x, mouse.y);
        menuAt.set(at);
        menuLink = null;
        final String node = nodeUnder(mouse.x, mouse.y);
        if (node != null) {
            if (!selection.contains(node)) {
                select(List.of(node));
            }
            menuTarget = node;
            menuRequested = NODE_MENU;
            return;
        }
        final List<Graph.Frame> frames = new ArrayList<>(getGraph().frames());
        Collections.reverse(frames);
        for (Graph.Frame frame : frames) {
            if (frame.contains(at.x, at.y)) {
                menuTarget = frame.getId();
                menuRequested = FRAME_MENU;
                return;
            }
        }
        menuTarget = null;
        menuLink = linkUnder(mouse.x, mouse.y);
        menuRequested = CANVAS_MENU;
    }

    /** Draw whichever context menu is open, and do what is picked. */
    private void menus() {
        if (menuRequested != null) {
            IkGui.openPopup(menuRequested);
            menuRequested = null;
        }
        if (popupRequested != null) {
            IkGui.openPopup(popupRequested);
            popupRequested = null;
        }
        if (IkGui.beginPopup(NODE_MENU)) {
            nodeMenu();
            IkGui.endPopup();
        }
        if (IkGui.beginPopup(FRAME_MENU)) {
            frameMenu();
            IkGui.endPopup();
        }
        if (IkGui.beginPopup(CANVAS_MENU)) {
            canvasMenu();
            IkGui.endPopup();
        }
    }

    /** The menu for a node and the selection it is in. */
    private void nodeMenu() {
        final String id = menuTarget;
        final Graph.Node node = id == null ? null : getGraph().node(id);
        if (node == null) {
            IkGui.closeCurrentPopup();
            return;
        }
        final Set<String> chosen = new LinkedHashSet<>(getSelection());
        chosen.add(id);
        if (IkGui.menuItem(node.isCollapsed() ? "Expand" : "Collapse")) {
            setCollapsed(chosen, !node.isCollapsed());
        }
        if (IkGui.menuItem(node.isHideUnused() ? "Show all pins" : "Hide unused pins")) {
            setHideUnused(chosen, !node.isHideUnused());
        }
        if (IkGui.menuItem("Comment...")) {
            popupTarget = id;
            popupText.set(node.getComment() == null ? "" : node.getComment(), true);
            popupRequested = COMMENT_POPUP;
        }
        IkGui.separator();
        if (IkGui.menuItem("Select upstream")) {
            selectUpstream(id);
        }
        if (IkGui.menuItem("Select downstream")) {
            selectDownstream(id);
        }
        if (IkGui.menuItem("Straighten")) {
            straighten(id);
        }
        if (chosen.size() > 1) {
            IkGui.separator();
            if (IkGui.menuItem("Align left")) {
                align(Align.LEFT);
            }
            if (IkGui.menuItem("Align right")) {
                align(Align.RIGHT);
            }
            if (IkGui.menuItem("Align tops")) {
                align(Align.TOP);
            }
            if (IkGui.menuItem("Align middles")) {
                align(Align.MIDDLE);
            }
        }
        IkGui.separator();
        if (IkGui.menuItem("Frame selection")) {
            frameSelection("Comment", menuAt.x, menuAt.y);
        }
        if (IkGui.menuItem("Delete", "Delete")) {
            deleteSelection();
        }
        if (nodeMenuItems != null) {
            IkGui.separator();
            nodeMenuItems.accept(id);
        }
    }

    /** The menu for a comment frame. */
    private void frameMenu() {
        final Graph.Frame frame = menuTarget == null ? null : getGraph().frame(menuTarget);
        if (frame == null) {
            IkGui.closeCurrentPopup();
            return;
        }
        if (IkGui.menuItem("Edit...")) {
            popupTarget = frame.getId();
            popupText.set(frame.getText(), true);
            popupColor = frame.getColor();
            popupRequested = FRAME_POPUP;
        }
        if (IkGui.menuItem("Select the nodes in it")) {
            select(getGraph().nodesIn(frame.getId()));
        }
        if (IkGui.menuItem("Delete frame")) {
            history.edit("Delete frame", graph -> graph.remove(List.of(frame.getId())));
            selection.remove(frame.getId());
        }
    }

    /** The menu for empty space, or a link. */
    private void canvasMenu() {
        if (IkGui.menuItem("Add node...", "Tab")) {
            searchAt.set(menuAt);
            searchLink = null;
            searchText.set("", true);
            searchRequested = true;
            searchFocus = true;
        }
        if (menuLink != null && IkGui.menuItem("Add reroute")) {
            insertReroute(menuLink, menuAt.x, menuAt.y);
        }
        if (IkGui.menuItem(getSelection().isEmpty() ? "Add frame" : "Frame selection")) {
            frameSelection("Comment", menuAt.x, menuAt.y);
        }
        if (IkGui.menuItem("Find...", "Ctrl+F")) {
            findText.set("", true);
            popupRequested = FIND_POPUP;
        }
        IkGui.separator();
        if (IkGui.menuItem("Remove unused nodes")) {
            removeUnused();
        }
        if (IkGui.menuItem("Show the minimap", null, minimap)) {
            minimap = !minimap;
        }
    }

    /** The popup that edits a node's comment. */
    private void commentPopup() {
        if (!IkGui.beginPopup(COMMENT_POPUP)) {
            return;
        }
        if (IkGui.isWindowAppearing()) {
            IkGui.setKeyboardFocusHere();
        }
        final boolean entered =
                IkGui.inputTextWithHint(
                        "##comment", "Comment", popupText, InputTextFlags.ENTER_RETURNS_TRUE);
        if (entered || IkGui.button("OK")) {
            if (popupTarget != null) {
                setComment(popupTarget, popupText.get());
            }
            IkGui.closeCurrentPopup();
        }
        IkGui.sameLine();
        if (IkGui.button("Cancel") || IkGui.isKeyPressed(Key.ESCAPE, false)) {
            IkGui.closeCurrentPopup();
        }
        IkGui.endPopup();
    }

    /** The popup that edits a frame's title and color. */
    private void framePopup() {
        if (!IkGui.beginPopup(FRAME_POPUP)) {
            return;
        }
        if (IkGui.isWindowAppearing()) {
            IkGui.setKeyboardFocusHere();
        }
        final boolean entered =
                IkGui.inputTextWithHint(
                        "##frame", "Title", popupText, InputTextFlags.ENTER_RETURNS_TRUE);
        for (int i = 0; i < FRAME_COLORS.length; ++i) {
            if (i > 0) {
                IkGui.sameLine();
            }
            final Vector2f at = IkGui.getCursorScreenPos();
            if (IkGui.invisibleButton("color" + i, 22, 22)) {
                popupColor = FRAME_COLORS[i];
            }
            final int opaque = FRAME_COLORS[i] | 0xFF;
            IkGui.getWindowDrawList().addRectFilled(at.x, at.y, at.x + 22, at.y + 22, opaque, 3);
            if (popupColor == FRAME_COLORS[i]) {
                IkGui.getWindowDrawList()
                        .addRect(at.x, at.y, at.x + 22, at.y + 22, Color.rgba(1, 1, 1, 1), 3, 0, 2);
            }
        }
        if (entered || IkGui.button("OK")) {
            final String id = popupTarget;
            final String text = popupText.get();
            final int color = popupColor;
            if (id != null) {
                history.edit("Edit frame", graph -> graph.setFrameLook(id, text, color));
            }
            IkGui.closeCurrentPopup();
        }
        IkGui.sameLine();
        if (IkGui.button("Cancel") || IkGui.isKeyPressed(Key.ESCAPE, false)) {
            IkGui.closeCurrentPopup();
        }
        IkGui.endPopup();
    }

    /** The popup that finds nodes and frames by text, and goes to the one picked. */
    private void findPopup() {
        if (!IkGui.beginPopup(FIND_POPUP)) {
            return;
        }
        if (IkGui.isWindowAppearing()) {
            IkGui.setKeyboardFocusHere();
        }
        final boolean entered =
                IkGui.inputTextWithHint(
                        "##find",
                        "Find nodes, parameters, comments",
                        findText,
                        InputTextFlags.ENTER_RETURNS_TRUE);
        final List<String> found = find(findText.get());
        String picked = entered && !found.isEmpty() ? found.getFirst() : null;
        int shown = 0;
        for (String id : found) {
            if (shown++ >= SEARCH_ROWS) {
                break;
            }
            final NodeTypes.NodeType type = getGraph().typeOf(id);
            final Graph.Frame frame = getGraph().frame(id);
            final String label =
                    type != null
                            ? type.title() + "  (" + id + ")"
                            : frame == null ? id : "Frame " + frame.getText();
            if (IkGui.selectable(label + "##" + id)) {
                picked = id;
            }
        }
        if (found.isEmpty() && !findText.get().isBlank()) {
            IkGui.textDisabled("Nothing matches");
        }
        if (IkGui.isKeyPressed(Key.ESCAPE, false)) {
            IkGui.closeCurrentPopup();
        }
        if (picked != null) {
            focus(picked);
            IkGui.closeCurrentPopup();
        }
        IkGui.endPopup();
    }

    /**
     * A named point on the canvas, in IkGui screen pixels, for UI automation to move and click at:
     *
     * <ul>
     *   <li>{@code node <id>}: the middle of the node's title
     *   <li>{@code in <id> <pin>}, {@code out <id> <pin>}: an input's or output's dot
     *   <li>{@code link <node>.<pin> <node>.<pin>}: the middle of the link from an output to an
     *       input
     *   <li>{@code frame <id>}: a frame's title; {@code corner <id>}: its sizing corner
     *   <li>{@code minimap}: the middle of the minimap; {@code minimap <x> <y>}: a point across it,
     *       from 0 to 1
     *   <li>{@code at <x> <y>}: a point in canvas units
     * </ul>
     *
     * @param name The point's name.
     * @return The point, or null if there is no such point now.
     */
    public @Nullable Vector2f pointNamed(@NonNull String name) {
        final String[] words = name.trim().split("\\s+");
        try {
            return switch (words[0]) {
                case "node" -> {
                    final GraphNodeView view = views.get(words[1]);
                    if (view == null || getGraph().node(words[1]) == null) {
                        yield null;
                    }
                    final RectFloat rect = view.getRect();
                    final float title =
                            view.isReroute()
                                    ? rect.getHeight()
                                    : GraphNodeView.TITLE * canvas.getUnit();
                    yield new Vector2f(
                            (rect.getLeft() + rect.getRight()) / 2, rect.getTop() + title / 2);
                }
                case "in", "out" -> pinOnScreen(words[1], words[2], "in".equals(words[0]));
                case "link" -> {
                    final String[] from = words[1].split("\\.");
                    final String[] to = words[2].split("\\.");
                    final Vector2f start = pinAt(from[0], from[1], false, new Vector2f());
                    final Vector2f end = pinAt(to[0], to[1], true, new Vector2f());
                    final Graph.Link link = getGraph().linkInto(to[0], to[1]);
                    if (start == null
                            || end == null
                            || link == null
                            || !link.fromNode().equals(from[0])
                            || !link.fromPin().equals(from[1])) {
                        yield null;
                    }
                    // The curve is symmetric, so its middle is halfway between its ends
                    yield start.add(end).mul(0.5f);
                }
                case "frame", "corner" -> {
                    final Graph.Frame frame = getGraph().frame(words[1]);
                    if (frame == null) {
                        yield null;
                    }
                    yield "frame".equals(words[0])
                            ? canvas.toScreen(
                                    frame.getX() + frame.getWidth() / 2,
                                    frame.getY() + FRAME_TITLE / 2)
                            : canvas.toScreen(
                                    frame.getX() + frame.getWidth() - FRAME_HANDLE / 3,
                                    frame.getY() + frame.getHeight() - FRAME_HANDLE / 3);
                }
                case "minimap" -> {
                    if (minimapRect.getWidth() <= 0) {
                        yield null;
                    }
                    final float across = words.length > 2 ? Float.parseFloat(words[1]) : 0.5f;
                    final float down = words.length > 2 ? Float.parseFloat(words[2]) : 0.5f;
                    yield new Vector2f(
                            minimapRect.getLeft() + across * minimapRect.getWidth(),
                            minimapRect.getTop() + down * minimapRect.getHeight());
                }
                case "at" ->
                        canvas.toScreen(Float.parseFloat(words[1]), Float.parseFloat(words[2]));
                default -> null;
            };
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            return null;
        }
    }
}
