package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.index.AssetIndex;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.TextureView;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.graph.GraphEditor;
import com.ikalagaming.graphics.ui.graph.GraphNodeView;
import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.GraphHistory;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;
import com.ikalagaming.graphics.ui.graph.model.PinTypes;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The References panel: a graph of one file from the asset index, what uses it on the left and what
 * it uses on the right. A click on another file makes it the middle. A missing reference is a red
 * node titled with the name that failed to resolve, and a file several folders provide says which
 * of them wins. The graph is read-only; it is rebuilt when the index changes.
 */
@Slf4j
public final class ReferenceViewer {
    /** The ID of the graph's canvas, for scripts. */
    static final String CANVAS_ID = "reference-canvas";

    /** The type of every pin: a reference to a file. */
    static final String PIN = "asset";

    /** The most files shown on each side; the rest are counted in the toolbar. */
    static final int MAX_SIDE = 40;

    /** What node types are called in the search, which is never used on a locked graph. */
    private static final String CATEGORY = "References";

    /** Space between a column and the middle node, in canvas units. */
    private static final float COLUMN_GAP = 120;

    /** The most nodes in a column; a longer side wraps into more columns. */
    private static final int COLUMN_ROWS = 8;

    /** Space between nodes in a column, in canvas units. */
    private static final float ROW_GAP = 16;

    /** Extra space above a node with a comment bubble, which floats over the node above. */
    private static final float COMMENT_ROOM = 28;

    /** Roughly how wide a character of a node's title or pin is, in canvas units. */
    private static final float CHAR_WIDTH = 8;

    /** The toolbar's height. */
    private static final float TOOLBAR_HEIGHT = 30;

    /** The color of reference pins and links. */
    private static final int PIN_COLOR = Color.rgba(0.55f, 0.7f, 0.9f, 1);

    /**
     * One file or missing name around the middle.
     *
     * @param label What the reference is, like {@code material}.
     * @param file The file, or null if the reference resolves to nothing.
     * @param name What the reference names.
     */
    public record Side(@NonNull String label, @Nullable Path file, @NonNull String name) {}

    /**
     * The files around one file, as the graph shows them.
     *
     * @param center The file in the middle.
     * @param users What uses it, at most {@link #MAX_SIDE}.
     * @param uses What it uses, at most {@link #MAX_SIDE}.
     * @param moreUsers How many users aren't shown.
     * @param moreUses How many uses aren't shown.
     */
    public record Around(
            @NonNull Path center,
            @NonNull List<Side> users,
            @NonNull List<Side> uses,
            int moreUsers,
            int moreUses) {

        /**
         * Read what is around a file.
         *
         * @param snapshot The index.
         * @param center The file.
         * @return Its users and uses.
         */
        static Around of(AssetIndex.@NonNull Snapshot snapshot, @NonNull Path center) {
            final List<Side> users = new ArrayList<>();
            for (AssetIndex.Link link : snapshot.usedBy(center)) {
                users.add(new Side(link.label(), link.file(), link.name()));
            }
            final List<Side> uses = new ArrayList<>();
            for (AssetIndex.Link link : snapshot.uses(center)) {
                uses.add(new Side(link.label(), link.file(), link.name()));
            }
            // Files of a kind together, so a column reads as looks, graphs and so on
            final java.util.Comparator<Side> order =
                    java.util.Comparator.comparing((Side side) -> typeOf(snapshot, side))
                            .thenComparing(Side::label)
                            .thenComparing(Side::name)
                            .thenComparing(side -> String.valueOf(side.file()));
            users.sort(order);
            return new Around(
                    center,
                    List.copyOf(users.subList(0, Math.min(MAX_SIDE, users.size()))),
                    List.copyOf(uses.subList(0, Math.min(MAX_SIDE, uses.size()))),
                    Math.max(0, users.size() - MAX_SIDE),
                    Math.max(0, uses.size() - MAX_SIDE));
        }

        /**
         * What kind of file a side is, for ordering.
         *
         * @param snapshot The index.
         * @param side The file.
         * @return Its type, or empty for a missing one.
         */
        private static String typeOf(AssetIndex.Snapshot snapshot, Side side) {
            final AssetIndex.Entry entry = side.file() == null ? null : snapshot.entry(side.file());
            return entry == null ? "" : entry.type();
        }
    }

    /**
     * A graph built around a file.
     *
     * @param graph The graph.
     * @param center The middle node's ID.
     * @param files The file each node shows, by node ID; missing references have none.
     * @param errors Each missing reference's node, with what is missing.
     * @param width How wide all the nodes span, in canvas units.
     * @param height How tall the tallest column is, in canvas units.
     */
    public record Built(
            @NonNull Graph graph,
            @NonNull String center,
            @NonNull Map<String, Path> files,
            @NonNull Map<String, String> errors,
            float width,
            float height) {}

    /** The graph shown, read-only. */
    private final GraphHistory history;

    /** The node types of the graph shown, one per node, for its title and pins. */
    private final NodeTypes nodeTypes = new NodeTypes();

    /** The pin types: references. */
    private final PinTypes pinTypes = new PinTypes();

    /** Draws the graph. */
    @Getter private final GraphEditor editor;

    /** The index read. */
    private final Supplier<AssetIndex.Snapshot> index;

    /** A file's thumbnail, or null until there is one. */
    private final Function<AssetIndex.Entry, TextureHandle> thumbnails;

    /** Opens a file in the workbench. */
    private final Consumer<Path> open;

    /** The file in the middle, or null before one is shown. */
    @Getter private @Nullable Path center;

    /** Files shown before, newest first, for going back. */
    private final Deque<Path> back = new ArrayDeque<>();

    /** The index the graph was built from. */
    private AssetIndex.@Nullable Snapshot builtFrom;

    /** The graph shown, or null before one is built. */
    @Getter private @Nullable Built built;

    /** What is around the middle, as shown. */
    @Getter private @Nullable Around around;

    /** Counts builds, so each gets its own node IDs and the editor makes fresh views. */
    private int generation;

    /**
     * How many more frames frame the whole graph. A new graph's views have no size on their first
     * frame, so the canvas can't scroll as far as it needs to until they are laid out.
     */
    private int framesToFit;

    /** Frames a graph is framed for, once shown or once the panel's size changes. */
    private static final int FIT_FRAMES = 3;

    /** The canvas's size when the graph was framed, to frame it again when that changes. */
    private float framedWidth;

    /** The canvas's height when the graph was framed. */
    private float framedHeight;

    /**
     * Make the panel's viewer.
     *
     * @param index The asset index, as of now.
     * @param thumbnails A file's thumbnail, or null until it is made.
     * @param open Opens a file in the workbench.
     */
    ReferenceViewer(
            @NonNull Supplier<AssetIndex.Snapshot> index,
            @NonNull Function<AssetIndex.Entry, TextureHandle> thumbnails,
            @NonNull Consumer<Path> open) {
        this.index = index;
        this.thumbnails = thumbnails;
        this.open = open;
        pinTypes.register(new PinTypes.PinType(PIN, "Reference", PIN_COLOR));
        history = new GraphHistory(new Graph(nodeTypes, pinTypes));
        history.setLocked(true);
        editor =
                new GraphEditor(CANVAS_ID, history)
                        .validator(graph -> built == null ? Map.of() : built.errors())
                        .previews(this::preview)
                        .nodeMenuItems(this::nodeMenuItems);
        // A graph this small doesn't need one, and it would cover the right column
        editor.setMinimap(false);
    }

    /**
     * The panel's content: a toolbar, then the graph filling the rest.
     *
     * @return The content.
     */
    Node<?> content() {
        return new Column("references-root")
                .gap(4)
                .height(Sizing.grow())
                .add(
                        new Immediate("references-toolbar", this::drawToolbar)
                                .height(Sizing.fixed(TOOLBAR_HEIGHT)),
                        editor.getCanvas());
    }

    /**
     * Put a file in the middle, remembering the one there to go back to.
     *
     * @param file The file.
     */
    void show(@NonNull Path file) {
        final Path normal = file.toAbsolutePath().normalize();
        if (normal.equals(center)) {
            return;
        }
        if (center != null) {
            back.push(center);
        }
        center = normal;
        builtFrom = null;
    }

    /** Go back to the file shown before, if any. */
    public void goBack() {
        if (!back.isEmpty()) {
            center = back.pop();
            builtFrom = null;
        }
    }

    /**
     * Select a shown node by its title, as a click would, for scripts: a file is made the middle on
     * the next frame.
     *
     * @param title The node's title, a file name or a missing name.
     * @return Whether there was such a node.
     */
    public boolean selectTitled(@NonNull String title) {
        final Built shown = built;
        if (shown == null) {
            return false;
        }
        for (Graph.Node node : shown.graph().nodes()) {
            final NodeTypes.NodeType type = nodeTypes.get(node.getType());
            if (type != null && type.title().equals(title)) {
                editor.select(List.of(node.getId()));
                return true;
            }
        }
        return false;
    }

    /**
     * Rebuild if the middle or the index changed, then draw the toolbar: back, open, and what is
     * shown.
     *
     * @param frame The current frame.
     */
    private void drawToolbar(UiFrame frame) {
        followClick(frame);
        final AssetIndex.Snapshot snapshot = index.get();
        if (center != null && snapshot != builtFrom) {
            rebuild(snapshot);
        }
        // Framed again as the panel changes size, the first frames it shows included
        final var size = editor.getCanvas().getRect();
        if (size.getWidth() != framedWidth || size.getHeight() != framedHeight) {
            framesToFit = FIT_FRAMES;
            framedWidth = size.getWidth();
            framedHeight = size.getHeight();
        }
        if (framesToFit > 0 && built != null && fit(built)) {
            --framesToFit;
        }
        if (IkGui.button("Back") && !back.isEmpty()) {
            frame.fire(this::goBack);
        }
        IkGui.sameLine();
        final Path shown = center;
        if (IkGui.button("Open") && shown != null) {
            frame.fire(() -> open.accept(shown));
        }
        IkGui.sameLine();
        IkGui.textUnformatted(summary(snapshot));
    }

    /**
     * What the toolbar says is shown.
     *
     * @param snapshot The index.
     * @return The text.
     */
    private String summary(AssetIndex.Snapshot snapshot) {
        final Path shown = center;
        if (shown == null) {
            return "Pick Show references on an asset's menu to see what uses it and what it uses";
        }
        if (snapshot.entry(shown) == null) {
            return shown.getFileName() + " isn't under an asset root";
        }
        final Around now = around;
        if (now == null) {
            return shown.getFileName().toString();
        }
        final int users = now.users().size() + now.moreUsers();
        final int uses = now.uses().size() + now.moreUses();
        final StringBuilder text =
                new StringBuilder()
                        .append(shown.getFileName())
                        .append(": used by ")
                        .append(users)
                        .append(", uses ")
                        .append(uses);
        if (now.moreUsers() + now.moreUses() > 0) {
            text.append(" (")
                    .append(now.moreUsers() + now.moreUses())
                    .append(" not shown, the first ")
                    .append(MAX_SIDE)
                    .append(" a side are)");
        }
        return text.toString();
    }

    /**
     * Make a clicked file the middle: one node selected, other than the middle, once the mouse is
     * up. The graph is locked, so a drag moves nothing.
     *
     * @param frame The current frame.
     */
    private void followClick(UiFrame frame) {
        final Built shown = built;
        if (shown == null || IkGui.isMouseDown(MouseButton.LEFT)) {
            return;
        }
        final var selection = editor.getSelection();
        if (selection.size() != 1) {
            return;
        }
        final String id = selection.iterator().next();
        final Path file = shown.files().get(id);
        if (!id.equals(shown.center()) && file != null) {
            editor.select(List.of());
            frame.fire(() -> show(file));
        }
    }

    /**
     * Build the graph around the middle from the index, replacing the one shown.
     *
     * @param snapshot The index.
     */
    private void rebuild(AssetIndex.Snapshot snapshot) {
        builtFrom = snapshot;
        final Path shown = center;
        if (shown == null) {
            return;
        }
        for (Graph.Node node : history.getGraph().nodes()) {
            nodeTypes.remove(node.getType());
        }
        final boolean newCenter = around == null || !around.center().equals(shown);
        around = Around.of(snapshot, shown);
        ++generation;
        built = build(snapshot, around, nodeTypes, pinTypes, "g" + generation + "/");
        history.getGraph().restore(built.graph());
        // Forgets nothing, as nothing is ever edited, but makes the editor sync its views
        history.clear();
        editor.select(List.of());
        editor.revalidate();
        if (newCenter) {
            framesToFit = FIT_FRAMES;
        }
    }

    /**
     * Zoom and pan so the whole graph shows on the canvas, once it has been laid out.
     *
     * @param graph The graph.
     * @return Whether it was framed; not before the canvas has a size.
     */
    private boolean fit(Built graph) {
        final var canvas = editor.getCanvas();
        final var room = canvas.getRect();
        if (room.getWidth() <= 0 || room.getHeight() <= 0 || canvas.getZoom() <= 0) {
            return false;
        }
        // Pixels per canvas unit at a zoom of 1: the UI scale
        final float scale = canvas.getUnit() / canvas.getZoom();
        final float margin = 80;
        final float zoom =
                Math.clamp(
                        Math.min(
                                room.getWidth() / scale / (graph.width() + margin),
                                room.getHeight() / scale / (graph.height() + margin)),
                        0.25f,
                        1f);
        editor.setView(graph.width() / 2 - columnStart(graph), 0, zoom);
        return true;
    }

    /**
     * How far left of the middle node's left edge the graph starts.
     *
     * @param graph The graph.
     * @return The distance, in canvas units.
     */
    private static float columnStart(Built graph) {
        float least = 0;
        for (Graph.Node node : graph.graph().nodes()) {
            least = Math.min(least, node.getX());
        }
        return -least;
    }

    /**
     * Build the graph around a file: a node per file, users in a column on the left linked into the
     * middle node's inputs, uses in a column on the right linked from its outputs.
     *
     * @param snapshot The index, for types and providers.
     * @param around What is around the file.
     * @param nodeTypes Where each node's type is registered.
     * @param pinTypes The pin types, with {@link #PIN}.
     * @param prefix Put before every node ID and type, to tell builds apart.
     * @return The graph.
     */
    static Built build(
            AssetIndex.@NonNull Snapshot snapshot,
            @NonNull Around around,
            @NonNull NodeTypes nodeTypes,
            @NonNull PinTypes pinTypes,
            @NonNull String prefix) {
        final Graph graph = new Graph(nodeTypes, pinTypes);
        final Map<String, Path> files = new HashMap<>();
        final Map<String, String> errors = new LinkedHashMap<>();
        final List<NodeTypes.Pin> inputs = new ArrayList<>();
        final List<NodeTypes.Pin> outputs = new ArrayList<>();
        final List<String> inputNames = pinNames(around.users());
        final List<String> outputNames = pinNames(around.uses());
        for (String name : inputNames) {
            inputs.add(new NodeTypes.Pin(name, PIN));
        }
        for (String name : outputNames) {
            outputs.add(new NodeTypes.Pin(name, PIN));
        }

        final String centerId = prefix + "center";
        final String centerTitle = around.center().getFileName().toString();
        nodeTypes.register(
                new NodeTypes.NodeType(
                        centerId, centerTitle, CATEGORY, inputs, outputs, List.of(), 1));
        final float centerWidth = widthOf(centerTitle, inputNames, outputNames);
        final float centerHeight =
                GraphNodeView.TITLE
                        + Math.max(1, Math.max(inputs.size(), outputs.size())) * GraphNodeView.ROW
                        + GraphNodeView.BOTTOM
                        + previewRoom(snapshot, around.center());

        final List<Placed> left =
                sideNodes(snapshot, nodeTypes, around.users(), prefix + "user", true);
        final List<Placed> right =
                sideNodes(snapshot, nodeTypes, around.uses(), prefix + "use", false);
        graph.put(centerId, centerId, 0, -centerHeight / 2);
        files.put(centerId, around.center());
        graph.setComment(centerId, comment(snapshot, around.center()));

        // Long sides wrap into more columns, outward from the middle
        float leftEdge = 0;
        float height = centerHeight;
        int index = 0;
        for (List<Placed> column : columns(left)) {
            final float columnWidth = widest(column);
            leftEdge -= COLUMN_GAP + columnWidth;
            final float columnHeight = columnHeight(column);
            height = Math.max(height, columnHeight);
            float y = -columnHeight / 2;
            for (Placed placed : column) {
                y += placed.comment() == null ? 0 : COMMENT_ROOM;
                put(graph, placed, leftEdge, y, files, errors);
                graph.link(placed.id(), placed.pin(), centerId, inputNames.get(index++));
                y += placed.height() + ROW_GAP;
            }
        }
        float rightEdge = centerWidth;
        index = 0;
        for (List<Placed> column : columns(right)) {
            final float columnHeight = columnHeight(column);
            height = Math.max(height, columnHeight);
            float y = -columnHeight / 2;
            for (Placed placed : column) {
                y += placed.comment() == null ? 0 : COMMENT_ROOM;
                put(graph, placed, rightEdge + COLUMN_GAP, y, files, errors);
                graph.link(centerId, outputNames.get(index++), placed.id(), placed.pin());
                y += placed.height() + ROW_GAP;
            }
            rightEdge += COLUMN_GAP + widest(column);
        }
        return new Built(
                graph,
                centerId,
                Map.copyOf(files),
                Map.copyOf(errors),
                rightEdge - leftEdge,
                height);
    }

    /**
     * Split a side into columns of at most {@link #COLUMN_ROWS} nodes, the first nearest the
     * middle.
     *
     * @param side The side's nodes, in order.
     * @return The columns.
     */
    private static List<List<Placed>> columns(List<Placed> side) {
        final List<List<Placed>> columns = new ArrayList<>();
        for (int start = 0; start < side.size(); start += COLUMN_ROWS) {
            columns.add(side.subList(start, Math.min(side.size(), start + COLUMN_ROWS)));
        }
        return columns;
    }

    /**
     * A node beside the middle, before it is placed.
     *
     * @param id Its ID and type.
     * @param pin Its one pin's name.
     * @param side What it is.
     * @param comment Its comment, or null.
     * @param width How wide it is, roughly.
     * @param height How tall it is.
     */
    private record Placed(
            String id,
            String pin,
            Side side,
            @Nullable String comment,
            float width,
            float height) {}

    /**
     * Register the node types of one side's files.
     *
     * @param snapshot The index.
     * @param nodeTypes Where the types go.
     * @param sides The files.
     * @param prefix Each node's ID, before its number.
     * @param users Whether they use the middle, so their pin is an output; else an input.
     * @return The nodes, in order.
     */
    private static List<Placed> sideNodes(
            AssetIndex.Snapshot snapshot,
            NodeTypes nodeTypes,
            List<Side> sides,
            String prefix,
            boolean users) {
        final List<Placed> placed = new ArrayList<>();
        for (int i = 0; i < sides.size(); ++i) {
            final Side side = sides.get(i);
            final String id = prefix + i;
            final AssetIndex.Entry entry = side.file() == null ? null : snapshot.entry(side.file());
            // The pin says what the file is; a missing one only what it was referenced as
            final String pin = entry != null ? entry.type() : side.file() == null ? "missing" : "";
            final String title =
                    side.file() == null ? side.name() : side.file().getFileName().toString();
            final List<NodeTypes.Pin> one = List.of(new NodeTypes.Pin(pin, PIN));
            nodeTypes.register(
                    new NodeTypes.NodeType(
                            id,
                            title,
                            CATEGORY,
                            users ? List.of() : one,
                            users ? one : List.of(),
                            List.of(),
                            1));
            final String comment = side.file() == null ? null : comment(snapshot, side.file());
            placed.add(
                    new Placed(
                            id,
                            pin,
                            side,
                            comment,
                            widthOf(title, List.of(pin), List.of()),
                            GraphNodeView.TITLE
                                    + GraphNodeView.ROW
                                    + GraphNodeView.BOTTOM
                                    + (side.file() == null
                                            ? 0
                                            : previewRoom(snapshot, side.file()))));
        }
        return placed;
    }

    /**
     * Put a placed node in the graph, with its comment and what it shows.
     *
     * @param graph The graph.
     * @param placed The node.
     * @param x Its left edge.
     * @param y Its top edge.
     * @param files Gets the node's file.
     * @param errors Gets the node's error, if its reference is missing.
     */
    private static void put(
            Graph graph,
            Placed placed,
            float x,
            float y,
            Map<String, Path> files,
            Map<String, String> errors) {
        graph.put(placed.id(), placed.id(), x, y);
        graph.setComment(placed.id(), placed.comment());
        final Side side = placed.side();
        if (side.file() == null) {
            errors.put(placed.id(), "Missing " + side.label() + ": " + side.name());
        } else {
            files.put(placed.id(), side.file());
        }
    }

    /**
     * Names for the middle node's pins, one per reference, each the reference's label, numbered
     * when one repeats, since a node's pins need names of their own.
     *
     * @param sides The references.
     * @return The names, in order.
     */
    static List<String> pinNames(List<Side> sides) {
        final Map<String, Integer> counts = new HashMap<>();
        for (Side side : sides) {
            counts.merge(side.label(), 1, Integer::sum);
        }
        final Map<String, Integer> seen = new HashMap<>();
        final List<String> names = new ArrayList<>();
        for (Side side : sides) {
            if (counts.get(side.label()) == 1) {
                names.add(side.label());
            } else {
                names.add(side.label() + " " + seen.merge(side.label(), 1, Integer::sum));
            }
        }
        return names;
    }

    /**
     * Roughly how wide a node is for its title and pin names, never under the usual width.
     *
     * @param title The title.
     * @param inputs The input names.
     * @param outputs The output names.
     * @return The width, in canvas units.
     */
    private static float widthOf(String title, List<String> inputs, List<String> outputs) {
        int longestIn = 0;
        for (String name : inputs) {
            longestIn = Math.max(longestIn, name.length());
        }
        int longestOut = 0;
        for (String name : outputs) {
            longestOut = Math.max(longestOut, name.length());
        }
        final float text = Math.max(title.length() + 4, longestIn + longestOut + 6) * CHAR_WIDTH;
        return Math.max(GraphNodeView.WIDTH, text);
    }

    /**
     * The widest node in a column.
     *
     * @param column The column.
     * @return The width, in canvas units.
     */
    private static float widest(List<Placed> column) {
        float widest = 0;
        for (Placed placed : column) {
            widest = Math.max(widest, placed.width());
        }
        return widest;
    }

    /**
     * How tall a column is, gaps and comment bubbles included.
     *
     * @param column The column.
     * @return The height, in canvas units.
     */
    private static float columnHeight(List<Placed> column) {
        float height = 0;
        for (Placed placed : column) {
            height += placed.height() + ROW_GAP + (placed.comment() == null ? 0 : COMMENT_ROOM);
        }
        return Math.max(0, height - ROW_GAP);
    }

    /**
     * How much taller a file's node is for its thumbnail, once there is one.
     *
     * @param snapshot The index.
     * @param file The file.
     * @return The room, in canvas units.
     */
    private static float previewRoom(AssetIndex.Snapshot snapshot, Path file) {
        final AssetIndex.Entry entry = snapshot.entry(file);
        if (entry == null || !hasThumbnail(entry)) {
            return 0;
        }
        return GraphNodeView.PREVIEW + GraphNodeView.BOTTOM + GraphNodeView.CAPTION;
    }

    /**
     * Whether a file gets a thumbnail.
     *
     * @param entry The file.
     * @return True if one can be made or rendered.
     */
    private static boolean hasThumbnail(AssetIndex.Entry entry) {
        return com.ikalagaming.converter.index.Thumbnails.canMake(entry)
                || ThumbnailStudio.canRender(entry);
    }

    /**
     * What a node's bubble says about the folders providing its file: which one wins, or that this
     * one loses.
     *
     * @param snapshot The index.
     * @param file The file.
     * @return The text, or null when only one folder provides it.
     */
    static @Nullable String comment(AssetIndex.Snapshot snapshot, Path file) {
        final List<Path> providers = snapshot.providers(file);
        if (providers.size() < 2) {
            return null;
        }
        final Path winner = providers.getLast();
        if (!winner.equals(file.toAbsolutePath().normalize())) {
            return "Overridden by " + folderOf(snapshot, winner);
        }
        final StringBuilder text = new StringBuilder("Overrides ");
        for (int i = 0; i < providers.size() - 1; ++i) {
            text.append(i == 0 ? "" : ", ").append(folderOf(snapshot, providers.get(i)));
        }
        return text.toString();
    }

    /**
     * Which folder provides a file: its root's namespace, or the root folder's name.
     *
     * @param snapshot The index.
     * @param file The file.
     * @return The name.
     */
    private static String folderOf(AssetIndex.Snapshot snapshot, Path file) {
        final AssetIndex.Entry entry = snapshot.entry(file);
        if (entry == null) {
            return file.toString();
        }
        final AssetIndex.Root root = snapshot.roots().get(entry.root());
        return root.namespace() != null
                ? root.namespace()
                : String.valueOf(root.folder().getFileName());
    }

    /**
     * A node's preview: its file's thumbnail, with the file's type under it, once made.
     *
     * @param id The node.
     * @return The preview, or null for none.
     */
    private GraphEditor.@Nullable NodePreview preview(String id) {
        final Built shown = built;
        final AssetIndex.Snapshot snapshot = builtFrom;
        if (shown == null || snapshot == null) {
            return null;
        }
        final Path file = shown.files().get(id);
        final AssetIndex.Entry entry = file == null ? null : snapshot.entry(file);
        if (entry == null || !hasThumbnail(entry)) {
            return null;
        }
        final TextureHandle texture = thumbnails.apply(entry);
        if (texture == null) {
            return null;
        }
        return new GraphEditor.NodePreview(
                texture, TextureView.PLAIN, 0, 0, 1, 1, 1, entry.type(), id.equals(shown.center()));
    }

    /**
     * The items in a node's right-click menu: opening its file, or showing its references.
     *
     * @param id The node.
     */
    private void nodeMenuItems(String id) {
        final Built shown = built;
        final Path file = shown == null ? null : shown.files().get(id);
        if (file == null) {
            return;
        }
        if (IkGui.menuItem("Open")) {
            open.accept(file);
        }
        if (!id.equals(shown.center()) && IkGui.menuItem("Show its references")) {
            show(file);
        }
    }
}
