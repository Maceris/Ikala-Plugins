package com.ikalagaming.graphics.ui.spec;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Canvas;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Flex;
import com.ikalagaming.graphics.ui.Grid;
import com.ikalagaming.graphics.ui.Justify;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Overlay;
import com.ikalagaming.graphics.ui.Row;
import com.ikalagaming.graphics.ui.Scroll;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.TextInput;
import com.ikalagaming.graphics.ui.VirtualGrid;
import com.ikalagaming.graphics.ui.graph.Edge;
import com.ikalagaming.graphics.ui.graph.GraphNode;
import com.ikalagaming.graphics.ui.graph.Highlight;
import com.ikalagaming.graphics.ui.graph.Port;
import com.ikalagaming.graphics.ui.graph.Route;
import com.ikalagaming.graphics.ui.style.StyleKey;
import com.ikalagaming.graphics.ui.style.StyleParser;
import com.ikalagaming.graphics.ui.style.ThemeException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The node types specs can use, by name. Graphics registers the built-in types; plugins add their
 * own, which are removed when the plugin unloads. Safe from any thread.
 */
@Slf4j
public final class NodeTypes {

    /**
     * A registered type.
     *
     * @param factory Makes nodes of the type.
     * @param owner The plugin context that registered it, or null for built-in types.
     */
    record Entry(@NonNull NodeFactory factory, GraphicsContext owner) {}

    /** Types by name. */
    private final Map<String, Entry> types = new ConcurrentHashMap<>();

    /** Create a registry with the built-in types. */
    public NodeTypes() {
        builtIn("column", (id, p) -> flex(new Column(id), p));
        builtIn("row", (id, p) -> flex(new Row(id), p));
        builtIn("overlay", (id, p) -> new Overlay(id));
        builtIn("scroll", (id, p) -> new Scroll(id).border(p.bool("border", false)));
        builtIn(
                "label",
                (id, p) -> {
                    Label label = new Label(id, "");
                    p.text("text", "", label::text);
                    return label;
                });
        builtIn(
                "button",
                (id, p) -> {
                    Button button = new Button(id, "");
                    p.text("text", "", button::text);
                    button.onClick(p.handler("onClick", button));
                    button.autofocus(p.bool("autofocus", false));
                    return button;
                });
        builtIn(
                "selectable",
                (id, p) -> {
                    Selectable selectable = new Selectable(id, "");
                    p.text("text", "", selectable::text);
                    p.flag("selected", false, selectable::selected);
                    selectable.onClick(p.handler("onClick", selectable));
                    return selectable;
                });
        builtIn(
                "text-input",
                (id, p) -> {
                    TextInput input = new TextInput(id, "");
                    p.text("text", "", input::text);
                    input.onSubmit(p.valueHandler("onSubmit", input));
                    return input;
                });
        builtIn("grid", NodeTypes::grid);
        builtIn("virtual-grid", NodeTypes::virtualGrid);
        builtIn("canvas", NodeTypes::canvas);
        builtIn("graph-node", NodeTypes::graphNode);
    }

    /**
     * Make a grid.
     *
     * @param id The ID.
     * @param properties Its properties.
     * @return The grid.
     */
    private static Grid grid(String id, SpecProperties properties) {
        Grid grid = new Grid(id).columns(columns(properties));
        if (properties.has("cell")) {
            Length[] cell =
                    SpecValues.lengths(properties.raw("cell"), 2, properties.path() + ".cell");
            grid.cellSize(cell[0], cell[1]);
        }
        Length gap = properties.length("gap", StyleKey.GAP);
        if (gap != null) {
            grid.gap(gap);
        }
        Length rowGap = properties.length("rowGap", null);
        if (rowGap != null) {
            grid.rowGap(rowGap);
        }
        Length columnGap = properties.length("columnGap", null);
        if (columnGap != null) {
            grid.columnGap(columnGap);
        }
        return grid;
    }

    /**
     * Make a virtual grid. Its cells come from the node's repeat.
     *
     * @param id The ID.
     * @param properties Its properties.
     * @return The grid.
     */
    private static VirtualGrid virtualGrid(String id, SpecProperties properties) {
        VirtualGrid grid = new VirtualGrid(id).columns(columns(properties));
        if (properties.has("cell")) {
            Length[] cell =
                    SpecValues.lengths(properties.raw("cell"), 2, properties.path() + ".cell");
            grid.cellSize(cell[0], cell[1]);
        }
        Length gap = properties.length("gap", null);
        if (gap != null) {
            grid.gap(gap);
        }
        grid.border(properties.bool("border", false));
        return grid;
    }

    /**
     * Read a grid's column count.
     *
     * @param properties Its properties.
     * @return The column count, 0 to fit as many as the width allows.
     * @throws SpecException If it is negative or not a whole number.
     */
    private static int columns(SpecProperties properties) {
        float columns = properties.number("columns", 0);
        if (columns < 0 || columns != Math.floor(columns)) {
            throw new SpecException(
                    properties.path() + ".columns: expected a whole number, not " + columns);
        }
        return (int) columns;
    }

    /**
     * Make a canvas, with its links.
     *
     * @param id The ID.
     * @param properties Its properties.
     * @return The canvas.
     */
    private static Canvas canvas(String id, SpecProperties properties) {
        Canvas canvas = new Canvas(id);
        if (properties.has("zoomRange")) {
            float[] range =
                    SpecValues.numbers(
                            properties.raw("zoomRange"), 2, properties.path() + ".zoomRange");
            try {
                canvas.zoomRange(range[0], range[1]);
            } catch (IllegalArgumentException e) {
                throw new SpecException(properties.path() + ".zoomRange: " + e.getMessage(), e);
            }
        }
        canvas.zoom(properties.number("zoom", 1));
        canvas.draggable(properties.bool("draggable", false));
        canvas.border(properties.bool("border", false));
        canvas.highlight(properties.choice("highlight", Highlight.class, Highlight.EDGES));
        Length spacing = properties.length("gridSpacing", null);
        if (spacing != null) {
            canvas.gridSpacing(spacing);
        }
        BiConsumer<Node<?>, String> moved = properties.nodeHandler("onMove");
        if (moved != null) {
            canvas.onMoved((node, x, y) -> moved.accept(node, x + "," + y));
        }
        if (properties.has(SpecKeys.EDGES)) {
            edges(canvas, properties);
        }
        return canvas;
    }

    /** The keys an edge can have. */
    private static final Set<String> EDGE_KEYS =
            Set.of(
                    SpecKeys.FROM,
                    SpecKeys.TO,
                    SpecKeys.PORTS,
                    SpecKeys.ROUTE,
                    SpecKeys.CLASSES,
                    SpecKeys.STYLE);

    /**
     * Read a canvas's links.
     *
     * @param canvas The canvas.
     * @param properties Its properties.
     * @throws SpecException If a link is written wrong.
     */
    private static void edges(Canvas canvas, SpecProperties properties) {
        String path = properties.path() + "." + SpecKeys.EDGES;
        List<?> edges = SpecValues.list(properties.raw(SpecKeys.EDGES), path);
        for (int i = 0; i < edges.size(); ++i) {
            String where = path + "[" + i + "]";
            Map<String, Object> written = SpecValues.map(edges.get(i), where);
            for (String key : written.keySet()) {
                if (!EDGE_KEYS.contains(key)) {
                    throw new SpecException(where + "." + key + ": unknown property for an edge");
                }
            }
            if (!written.containsKey(SpecKeys.FROM) || !written.containsKey(SpecKeys.TO)) {
                throw new SpecException(where + ": an edge needs from and to");
            }
            Edge edge =
                    canvas.connect(
                            SpecValues.name(written.get(SpecKeys.FROM), where + ".from"),
                            SpecValues.name(written.get(SpecKeys.TO), where + ".to"));
            Object ports = written.get(SpecKeys.PORTS);
            if (ports != null) {
                List<String> sides = SpecValues.names(ports, where + ".ports");
                if (sides.size() != 2) {
                    throw new SpecException(where + ".ports: expected [from side, to side]");
                }
                try {
                    edge.ports(Port.parse(sides.get(0)), Port.parse(sides.get(1)));
                } catch (IllegalArgumentException e) {
                    throw new SpecException(where + ".ports: " + e.getMessage(), e);
                }
            }
            Object route = written.get(SpecKeys.ROUTE);
            if (route != null) {
                Route parsed = Route.parse(String.valueOf(route));
                if (parsed == null) {
                    throw new SpecException(
                            where + ".route: use straight, curve or orthogonal, not " + route);
                }
                edge.route(parsed);
            }
            Object classes = written.get(SpecKeys.CLASSES);
            if (classes instanceof String text) {
                // Can be bound, like "{perk.state}", so a link restyles when its state changes
                properties.bindText(
                        text,
                        where + ".classes",
                        names ->
                                edge.classes(
                                        names.isBlank()
                                                ? new String[0]
                                                : names.trim().split("\\s+")));
            } else if (classes != null) {
                edge.classes(SpecValues.names(classes, where + ".classes"));
            }
            Object style = written.get(SpecKeys.STYLE);
            if (style != null) {
                try {
                    edge.style(StyleParser.style(style, where + ".style", null));
                } catch (ThemeException e) {
                    throw new SpecException(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Make a graph node.
     *
     * @param id The ID.
     * @param properties Its properties.
     * @return The node.
     */
    private static GraphNode graphNode(String id, SpecProperties properties) {
        GraphNode node = new GraphNode(id);
        node.shape(properties.choice("shape", GraphNode.Shape.class, null));
        properties.text("badge", "", node::badge);
        properties.text("label", "", node::label);
        properties.value(
                "icon",
                value -> {
                    if (value != null && !(value instanceof TextureHandle)) {
                        throw new SpecException(
                                properties.path() + ".icon: expected a {binding} to a texture");
                    }
                    node.icon((TextureHandle) value);
                });
        node.onActivate(properties.handler("onClick", node));
        node.autofocus(properties.bool("autofocus", false));
        return node;
    }

    /**
     * Read the properties every row and column has.
     *
     * @param flex The row or column.
     * @param properties Its properties.
     * @return The row or column.
     */
    private static Flex<?> flex(Flex<?> flex, SpecProperties properties) {
        Length gap = properties.length("gap", StyleKey.GAP);
        if (gap != null) {
            flex.gap(gap);
        }
        flex.align(properties.choice("align", Align.class, Align.START));
        flex.justify(properties.choice("justify", Justify.class, Justify.START));
        return flex;
    }

    /**
     * Register a built-in type.
     *
     * @param name The type name.
     * @param factory Makes nodes of the type.
     */
    private void builtIn(String name, NodeFactory factory) {
        types.put(name, new Entry(factory, null));
    }

    /**
     * Register a plugin's node type.
     *
     * @param owner The plugin's context, which owns the type until it unloads.
     * @param name The type name. Prefix it with the plugin, like {@code converter.slot}, so it
     *     doesn't clash.
     * @param factory Makes nodes of the type.
     * @throws IllegalArgumentException If the name is taken by a built-in type or another plugin.
     */
    public void register(
            @NonNull GraphicsContext owner, @NonNull String name, @NonNull NodeFactory factory) {
        types.compute(
                name,
                (key, existing) -> {
                    if (existing != null
                            && (existing.owner() == null
                                    || !existing.owner().getOwner().equals(owner.getOwner()))) {
                        throw new IllegalArgumentException(
                                "The node type " + name + " is already registered");
                    }
                    // A reloaded plugin may register again before its old instance unloads
                    return new Entry(factory, owner);
                });
    }

    /**
     * Find a type.
     *
     * @param name The type name.
     * @return The type, or null if there is none with that name.
     */
    Entry get(@NonNull String name) {
        return types.get(name);
    }

    /**
     * The names of every type, for messages.
     *
     * @return The names, sorted.
     */
    public Set<String> names() {
        return new TreeSet<>(types.keySet());
    }

    /**
     * Remove every type a plugin registered. Called when it unloads.
     *
     * @param owner The plugin's context.
     * @return The names removed.
     */
    public Set<String> removeAllOwnedBy(@NonNull GraphicsContext owner) {
        Set<String> removed = new HashSet<>();
        types.entrySet()
                .removeIf(
                        entry -> {
                            if (entry.getValue().owner() == owner) {
                                removed.add(entry.getKey());
                                return true;
                            }
                            return false;
                        });
        if (!removed.isEmpty()) {
            log.debug("Removed node types {} owned by {}", removed, owner.getOwnerKey());
        }
        return removed;
    }
}
