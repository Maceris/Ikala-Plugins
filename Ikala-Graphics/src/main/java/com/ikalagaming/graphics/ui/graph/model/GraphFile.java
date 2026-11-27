package com.ikalagaming.graphics.ui.graph.model;

import lombok.NonNull;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A graph as YAML text, written so a change is a small diff: nodes keyed by their IDs and written
 * sorted, positions rounded to the grid, each parameter on its own line with defaults left out, and
 * links one per line, sorted.
 *
 * <pre>
 * nodes:
 *   add1:
 *     type: math/add
 *     version: 1
 *     at: [96, 48]
 *     set:
 *       b: '2'
 *   constant1:
 *     type: math/constant
 *     version: 1
 *     at: [0, 48]
 * links:
 *   - constant1.value > add1.a
 * frames:
 *   frame1:
 *     at: [-16, -40]
 *     size: [320, 200]
 *     color: '#FF8040A0'
 *     text: Inputs
 * </pre>
 *
 * A node can also have {@code collapsed: true}, {@code hide unused: true} and a {@code comment}. A
 * frame's color is {@code '#RRGGBBAA'} in hex, the order IkGui packs colors in.
 *
 * <p>A node written by an older version of its type is upgraded as it is read, through the caller's
 * {@link Upgrader}.
 */
public final class GraphFile {
    /** The grid positions are rounded to, in canvas units. */
    public static final float GRID = 8;

    /** What separates a link's output from its input. */
    static final String ARROW = " > ";

    /** Not made. */
    private GraphFile() {}

    /** Brings a node's parameters from an older version of its type up to date. */
    @FunctionalInterface
    public interface Upgrader {
        /**
         * Upgrade a node's parameters.
         *
         * @param type The node type's ID.
         * @param fromVersion The version the file was written with.
         * @param params The parameters as read, changed in place.
         */
        void upgrade(@NonNull String type, int fromVersion, @NonNull Map<String, String> params);
    }

    /**
     * Write a graph.
     *
     * @param graph The graph.
     * @return The YAML.
     */
    public static String write(@NonNull Graph graph) {
        final StringBuilder out = new StringBuilder();
        final List<Graph.Node> nodes = new ArrayList<>(graph.nodes());
        nodes.sort(Comparator.comparing(Graph.Node::getId));
        out.append("nodes:");
        if (nodes.isEmpty()) {
            out.append(" {}");
        }
        out.append('\n');
        for (Graph.Node node : nodes) {
            final NodeTypes.NodeType type = graph.getNodeTypes().get(node.getType());
            out.append("  ").append(node.getId()).append(":\n");
            out.append("    type: ").append(quote(node.getType())).append('\n');
            out.append("    version: ").append(type == null ? 1 : type.version()).append('\n');
            out.append("    at: [")
                    .append(onGrid(node.getX()))
                    .append(", ")
                    .append(onGrid(node.getY()))
                    .append("]\n");
            if (node.isCollapsed()) {
                out.append("    collapsed: true\n");
            }
            if (node.isHideUnused()) {
                out.append("    hide unused: true\n");
            }
            if (node.getComment() != null) {
                out.append("    comment: ").append(quote(node.getComment())).append('\n');
            }
            if (!node.getParams().isEmpty()) {
                out.append("    set:\n");
                for (Map.Entry<String, String> param : node.getParams().entrySet()) {
                    out.append("      ")
                            .append(quote(param.getKey()))
                            .append(": ")
                            .append(quote(param.getValue()))
                            .append('\n');
                }
            }
        }
        final List<String> links = new ArrayList<>();
        for (Graph.Link link : graph.links()) {
            links.add(
                    link.fromNode()
                            + "."
                            + link.fromPin()
                            + ARROW
                            + link.toNode()
                            + "."
                            + link.toPin());
        }
        links.sort(null);
        out.append("links:");
        if (links.isEmpty()) {
            out.append(" []");
        }
        out.append('\n');
        for (String link : links) {
            out.append("  - ").append(quote(link)).append('\n');
        }
        final List<Graph.Frame> frames = new ArrayList<>(graph.frames());
        if (!frames.isEmpty()) {
            frames.sort(Comparator.comparing(Graph.Frame::getId));
            out.append("frames:\n");
            for (Graph.Frame frame : frames) {
                out.append("  ").append(frame.getId()).append(":\n");
                out.append("    at: [")
                        .append(onGrid(frame.getX()))
                        .append(", ")
                        .append(onGrid(frame.getY()))
                        .append("]\n");
                out.append("    size: [")
                        .append(onGrid(frame.getWidth()))
                        .append(", ")
                        .append(onGrid(frame.getHeight()))
                        .append("]\n");
                out.append("    color: '")
                        .append(String.format("#%08X", frame.getColor()))
                        .append("'\n");
                out.append("    text: ").append(quote(frame.getText())).append('\n');
            }
        }
        return out.toString();
    }

    /**
     * A position rounded to the grid, as a whole number when it is one.
     *
     * @param value The position.
     * @return The text.
     */
    private static String onGrid(float value) {
        final float rounded = Math.round(value / GRID) * GRID;
        return rounded == (long) rounded ? Long.toString((long) rounded) : Float.toString(rounded);
    }

    /**
     * Quote text in single quotes when YAML could read it as something else.
     *
     * @param text The text.
     * @return The text, quoted if needed.
     */
    static String quote(@NonNull String text) {
        if (text.matches("[A-Za-z_][A-Za-z0-9_/.>\\- ]*")
                && !text.endsWith(" ")
                && !text.contains(": ")
                && !text.contains(" #")
                && !text.matches("(?i)true|false|yes|no|on|off|null|~")) {
            return text;
        }
        return "'" + text.replace("'", "''") + "'";
    }

    /**
     * Read a graph into an empty one.
     *
     * @param text The YAML.
     * @param into The graph to fill, which should be empty.
     * @param upgrader Upgrades nodes written by older versions of their types.
     * @return Problems met, like unknown node types or pins, each of which was left out.
     * @throws IllegalArgumentException If the text isn't a graph.
     */
    public static List<String> read(
            @NonNull String text, @NonNull Graph into, @NonNull Upgrader upgrader) {
        final Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        if (!(document instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("a graph is a map with nodes and links");
        }
        final List<String> problems = new ArrayList<>();
        if (root.get("nodes") instanceof Map<?, ?> nodes) {
            // Sorted, so a file reads the same whatever order its nodes were written in
            final Map<String, Object> sorted = new TreeMap<>();
            nodes.forEach((key, value) -> sorted.put(String.valueOf(key), value));
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                readNode(entry.getKey(), entry.getValue(), into, upgrader, problems);
            }
        }
        if (root.get("links") instanceof List<?> links) {
            for (Object entry : links) {
                readLink(String.valueOf(entry), into, problems);
            }
        }
        if (root.get("frames") instanceof Map<?, ?> frames) {
            final Map<String, Object> sorted = new TreeMap<>();
            frames.forEach((key, value) -> sorted.put(String.valueOf(key), value));
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                readFrame(entry.getKey(), entry.getValue(), into, problems);
            }
        }
        return problems;
    }

    /**
     * Read one node.
     *
     * @param id Its ID.
     * @param value Its map.
     * @param into The graph.
     * @param upgrader Upgrades older nodes.
     * @param problems Receives what was wrong.
     */
    private static void readNode(
            String id, Object value, Graph into, Upgrader upgrader, List<String> problems) {
        if (!(value instanceof Map<?, ?> node) || node.get("type") == null) {
            problems.add("Node " + id + " has no type");
            return;
        }
        final String typeId = String.valueOf(node.get("type"));
        final NodeTypes.NodeType type = into.getNodeTypes().get(typeId);
        if (type == null) {
            problems.add("Node " + id + " is of an unknown type, " + typeId);
            return;
        }
        float x = 0;
        float y = 0;
        if (node.get("at") instanceof List<?> at
                && at.size() == 2
                && at.get(0) instanceof Number ax
                && at.get(1) instanceof Number ay) {
            x = ax.floatValue();
            y = ay.floatValue();
        }
        final Map<String, String> params = new TreeMap<>();
        if (node.get("set") instanceof Map<?, ?> set) {
            set.forEach((key, param) -> params.put(String.valueOf(key), String.valueOf(param)));
        }
        final int version =
                node.get("version") instanceof Number number ? number.intValue() : type.version();
        if (version < type.version()) {
            upgrader.upgrade(typeId, version, params);
        }
        final Graph.Node added;
        try {
            added = into.put(id, typeId, x, y);
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
            return;
        }
        into.setCollapsed(id, Boolean.TRUE.equals(node.get("collapsed")));
        into.setHideUnused(id, Boolean.TRUE.equals(node.get("hide unused")));
        if (node.get("comment") != null) {
            into.setComment(id, String.valueOf(node.get("comment")));
        }
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (type.defaultOf(param.getKey()) == null) {
                problems.add("Node " + id + " has an unknown parameter, " + param.getKey());
                continue;
            }
            into.setParam(added.getId(), param.getKey(), param.getValue());
        }
    }

    /**
     * Read one comment frame.
     *
     * @param id Its ID.
     * @param value Its map.
     * @param into The graph.
     * @param problems Receives what was wrong.
     */
    private static void readFrame(String id, Object value, Graph into, List<String> problems) {
        if (!(value instanceof Map<?, ?> frame)
                || !(frame.get("at") instanceof List<?> at)
                || at.size() != 2
                || !(at.get(0) instanceof Number x)
                || !(at.get(1) instanceof Number y)
                || !(frame.get("size") instanceof List<?> size)
                || size.size() != 2
                || !(size.get(0) instanceof Number width)
                || !(size.get(1) instanceof Number height)) {
            problems.add("Frame " + id + " needs at and size");
            return;
        }
        int color = DEFAULT_FRAME_COLOR;
        final String text = String.valueOf(frame.get("color"));
        if (text.startsWith("#") && text.length() == 9) {
            try {
                color = (int) Long.parseLong(text.substring(1), 16);
            } catch (NumberFormatException e) {
                problems.add("Frame " + id + " has a color that isn't #RRGGBBAA in hex");
            }
        }
        into.putFrame(
                id,
                x.floatValue(),
                y.floatValue(),
                width.floatValue(),
                height.floatValue(),
                frame.get("text") == null ? "" : String.valueOf(frame.get("text")),
                color);
    }

    /** A frame's color when its file doesn't give one: a see-through blue gray. */
    public static final int DEFAULT_FRAME_COLOR = 0x59729959;

    /**
     * Read one link.
     *
     * @param text The link, like {@code constant1.value > add1.a}.
     * @param into The graph.
     * @param problems Receives what was wrong.
     */
    private static void readLink(String text, Graph into, List<String> problems) {
        final int arrow = text.indexOf(ARROW);
        final String from = arrow < 0 ? "" : text.substring(0, arrow).trim();
        final String to = arrow < 0 ? "" : text.substring(arrow + ARROW.length()).trim();
        final int fromDot = from.indexOf('.');
        final int toDot = to.indexOf('.');
        if (fromDot < 0 || toDot < 0) {
            problems.add("Link " + text + " isn't node.pin > node.pin");
            return;
        }
        final String fromNode = from.substring(0, fromDot);
        final String fromPin = from.substring(fromDot + 1);
        final String toNode = to.substring(0, toDot);
        final String toPin = to.substring(toDot + 1);
        if (into.outputPin(fromNode, fromPin) == null || into.inputPin(toNode, toPin) == null) {
            problems.add("Link " + text + " names a missing node or pin");
            return;
        }
        // Read as written: a conversion node made when the link was made is in the file already
        if (into.getPinTypes()
                        .conversion(
                                into.outputPin(fromNode, fromPin).type(),
                                into.inputPin(toNode, toPin).type())
                        .kind()
                != PinTypes.Kind.DIRECT) {
            problems.add("Link " + text + " joins pins whose types don't link directly");
            return;
        }
        into.link(fromNode, fromPin, toNode, toPin);
    }
}
