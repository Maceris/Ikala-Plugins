package com.ikalagaming.graphics.ui.graph.model;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * A graph of nodes and the links between their pins. Node types and pin types come from the caller,
 * through {@link NodeTypes} and {@link PinTypes}; the graph only keeps each input to one link and
 * refuses links its types can't take. Changes go through {@link GraphHistory} to be undone. Not
 * thread safe; the editor uses it on the render thread.
 */
public final class Graph {
    /** A node: its type, where it is, and the parameters set on it. */
    public static final class Node {
        /** Its ID, unique in the graph and kept for life. */
        @Getter private final String id;

        /** Its node type's ID. */
        @Getter private final String type;

        /** Its position, in canvas units. */
        @Getter private float x;

        /** Its position, in canvas units. */
        @Getter private float y;

        /** Its parameters set away from their defaults, sorted by name. */
        private final Map<String, String> params = new TreeMap<>();

        /** Whether it shows only its title. */
        @Getter private boolean collapsed;

        /** Whether its pins without links are hidden. */
        @Getter private boolean hideUnused;

        /** A note shown over it, or null for none. */
        @Getter private String comment;

        /**
         * Make a node.
         *
         * @param id Its ID.
         * @param type Its node type's ID.
         * @param x Its position across.
         * @param y Its position down.
         */
        Node(@NonNull String id, @NonNull String type, float x, float y) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
        }

        /**
         * The parameters set on it, sorted by name.
         *
         * @return The parameters. Don't change the map.
         */
        public Map<String, String> getParams() {
            return Collections.unmodifiableMap(params);
        }

        /**
         * A copy of the node, under another ID.
         *
         * @param newId The ID.
         * @return The copy.
         */
        Node copy(String newId) {
            final Node copy = new Node(newId, type, x, y);
            copy.params.putAll(params);
            copy.collapsed = collapsed;
            copy.hideUnused = hideUnused;
            copy.comment = comment;
            return copy;
        }
    }

    /**
     * A link from an output pin to an input pin.
     *
     * @param fromNode The node with the output.
     * @param fromPin The output's name.
     * @param toNode The node with the input.
     * @param toPin The input's name.
     */
    public record Link(
            @NonNull String fromNode,
            @NonNull String fromPin,
            @NonNull String toNode,
            @NonNull String toPin) {}

    /** A colored frame with a title, drawn behind the nodes it holds, which move with it. */
    public static final class Frame {
        /** Its ID, unique among the frames. */
        @Getter private final String id;

        /** Its left edge, in canvas units. */
        @Getter private float x;

        /** Its top edge. */
        @Getter private float y;

        /** Its width. */
        @Getter private float width;

        /** Its height. */
        @Getter private float height;

        /** Its title. */
        @Getter private String text;

        /** Its color, packed as by {@code Color.rgba}; drawn see-through. */
        @Getter private int color;

        /**
         * Make a frame.
         *
         * @param id Its ID.
         * @param x Its left edge.
         * @param y Its top edge.
         * @param width Its width.
         * @param height Its height.
         * @param text Its title.
         * @param color Its color.
         */
        Frame(String id, float x, float y, float width, float height, String text, int color) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.text = text;
            this.color = color;
        }

        /**
         * A copy of the frame.
         *
         * @return The copy.
         */
        Frame copy() {
            return new Frame(id, x, y, width, height, text, color);
        }

        /**
         * Whether a point is inside the frame.
         *
         * @param px The point across.
         * @param py The point down.
         * @return True if inside.
         */
        public boolean contains(float px, float py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    /** What linking two pins did. */
    public enum LinkResult {
        /** They were linked. */
        LINKED,
        /** A conversion node was put between them. */
        CONVERTED,
        /** Their types can't be linked, or a pin doesn't exist. */
        REFUSED
    }

    /** The node types. */
    @Getter private final NodeTypes nodeTypes;

    /** The pin types. */
    @Getter private final PinTypes pinTypes;

    /** The nodes by ID, in the order added. */
    private final Map<String, Node> nodes = new LinkedHashMap<>();

    /** The links, in the order made. */
    private final List<Link> links = new ArrayList<>();

    /** The comment frames by ID, in the order added, which is the order drawn. */
    private final Map<String, Frame> frames = new LinkedHashMap<>();

    /**
     * Make an empty graph.
     *
     * @param nodeTypes The node types it can hold.
     * @param pinTypes The pin types and their conversions.
     */
    public Graph(@NonNull NodeTypes nodeTypes, @NonNull PinTypes pinTypes) {
        this.nodeTypes = nodeTypes;
        this.pinTypes = pinTypes;
    }

    /**
     * Every node, in the order added.
     *
     * @return The nodes.
     */
    public Collection<Node> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * A node by ID.
     *
     * @param id The ID.
     * @return The node, or null if there is none.
     */
    public @Nullable Node node(@NonNull String id) {
        return nodes.get(id);
    }

    /**
     * Every link, in the order made.
     *
     * @return The links.
     */
    public List<Link> links() {
        return Collections.unmodifiableList(links);
    }

    /**
     * The link into an input, if any.
     *
     * @param node The node.
     * @param pin The input's name.
     * @return The link, or null.
     */
    public @Nullable Link linkInto(@NonNull String node, @NonNull String pin) {
        for (Link link : links) {
            if (link.toNode().equals(node) && link.toPin().equals(pin)) {
                return link;
            }
        }
        return null;
    }

    /**
     * Add a node, with an ID made from its type's last part and a number, like {@code add2}.
     *
     * @param type The node type's ID.
     * @param x Its position across, in canvas units.
     * @param y Its position down.
     * @return The node.
     * @throws IllegalArgumentException If there is no such node type.
     */
    public Node add(@NonNull String type, float x, float y) {
        if (nodeTypes.get(type) == null) {
            throw new IllegalArgumentException("No node type " + type);
        }
        final Node node = new Node(freshId(type), type, x, y);
        nodes.put(node.id, node);
        return node;
    }

    /**
     * An unused ID for a node of a type.
     *
     * @param type The node type's ID.
     * @return The ID.
     */
    private String freshId(String type) {
        // The last part, and for a type naming a file, like a subgraph's, the file's name alone
        String last = type.substring(Math.max(type.lastIndexOf('/'), type.lastIndexOf(':')) + 1);
        if (last.indexOf('.') > 0) {
            last = last.substring(0, last.indexOf('.'));
        }
        final String base = last.replaceAll("[^A-Za-z0-9_]", "_");
        int number = 1;
        while (nodes.containsKey(base + number)) {
            ++number;
        }
        return base + number;
    }

    /**
     * Put a node in with its own ID, as when reading a file.
     *
     * @param id The ID.
     * @param type The node type's ID.
     * @param x Its position across.
     * @param y Its position down.
     * @return The node.
     * @throws IllegalArgumentException If the ID is taken or there is no such node type.
     */
    public Node put(@NonNull String id, @NonNull String type, float x, float y) {
        if (nodes.containsKey(id)) {
            throw new IllegalArgumentException("Node " + id + " is already in the graph");
        }
        if (nodeTypes.get(type) == null) {
            throw new IllegalArgumentException("No node type " + type);
        }
        final Node node = new Node(id, type, x, y);
        nodes.put(id, node);
        return node;
    }

    /**
     * Move a node.
     *
     * @param id The node.
     * @param x Its position across.
     * @param y Its position down.
     */
    public void move(@NonNull String id, float x, float y) {
        final Node node = nodes.get(id);
        if (node != null) {
            node.x = x;
            node.y = y;
        }
    }

    /**
     * Set a parameter, or back to its default. A name the node's type doesn't have is ignored.
     *
     * @param id The node.
     * @param name The parameter.
     * @param value The value, or null for the default.
     */
    public void setParam(@NonNull String id, @NonNull String name, @Nullable String value) {
        final Node node = nodes.get(id);
        if (node == null) {
            return;
        }
        final NodeTypes.NodeType type = nodeTypes.get(node.type);
        final String defaultValue = type == null ? null : type.defaultOf(name);
        if (defaultValue == null) {
            return;
        }
        if (value == null || value.equals(defaultValue)) {
            node.params.remove(name);
        } else {
            node.params.put(name, value);
        }
    }

    /**
     * A parameter's value: set on the node, or its default.
     *
     * @param id The node.
     * @param name The parameter.
     * @return The value, or null if there is no such node or parameter.
     */
    public @Nullable String param(@NonNull String id, @NonNull String name) {
        final Node node = nodes.get(id);
        if (node == null) {
            return null;
        }
        final String set = node.params.get(name);
        if (set != null) {
            return set;
        }
        final NodeTypes.NodeType type = nodeTypes.get(node.type);
        return type == null ? null : type.defaultOf(name);
    }

    /**
     * Remove nodes and every link to and from them.
     *
     * @param ids The nodes.
     */
    public void remove(@NonNull Collection<String> ids) {
        for (String id : ids) {
            nodes.remove(id);
            frames.remove(id);
        }
        links.removeIf(link -> ids.contains(link.fromNode()) || ids.contains(link.toNode()));
    }

    /**
     * Remove a link.
     *
     * @param link The link.
     */
    public void unlink(@NonNull Link link) {
        links.remove(link);
    }

    /**
     * Link an output to an input, replacing the input's link, if their types allow: directly, or
     * through a conversion node put halfway between them.
     *
     * @param fromNode The node with the output.
     * @param fromPin The output.
     * @param toNode The node with the input.
     * @param toPin The input.
     * @return What happened.
     */
    public LinkResult link(
            @NonNull String fromNode,
            @NonNull String fromPin,
            @NonNull String toNode,
            @NonNull String toPin) {
        final NodeTypes.Pin output = outputPin(fromNode, fromPin);
        final NodeTypes.Pin input = inputPin(toNode, toPin);
        if (output == null || input == null || fromNode.equals(toNode)) {
            return LinkResult.REFUSED;
        }
        final PinTypes.Conversion conversion =
                pinTypes.conversion(
                        effectiveType(fromNode, fromPin, false),
                        effectiveType(toNode, toPin, true));
        switch (conversion.kind()) {
            case DIRECT -> {
                replaceInto(toNode, toPin, new Link(fromNode, fromPin, toNode, toPin));
                return LinkResult.LINKED;
            }
            case NODE -> {
                final NodeTypes.NodeType converter = nodeTypes.get(conversion.nodeType());
                if (converter == null
                        || converter.inputs().isEmpty()
                        || converter.outputs().isEmpty()) {
                    return LinkResult.REFUSED;
                }
                final Node from = nodes.get(fromNode);
                final Node to = nodes.get(toNode);
                final Node middle = add(converter.id(), (from.x + to.x) / 2, (from.y + to.y) / 2);
                links.add(
                        new Link(
                                fromNode,
                                fromPin,
                                middle.id,
                                converter.inputs().getFirst().name()));
                replaceInto(
                        toNode,
                        toPin,
                        new Link(middle.id, converter.outputs().getFirst().name(), toNode, toPin));
                return LinkResult.CONVERTED;
            }
            default -> {
                return LinkResult.REFUSED;
            }
        }
    }

    /**
     * Link into an input, dropping the link it had.
     *
     * @param node The node.
     * @param pin The input.
     * @param link The new link.
     */
    private void replaceInto(String node, String pin, Link link) {
        links.removeIf(old -> old.toNode().equals(node) && old.toPin().equals(pin));
        links.add(link);
    }

    /**
     * An output pin of a node.
     *
     * @param node The node.
     * @param pin The output's name.
     * @return The pin, or null if the node, its type or the pin is missing.
     */
    public @Nullable NodeTypes.Pin outputPin(@NonNull String node, @NonNull String pin) {
        final NodeTypes.NodeType type = typeOf(node);
        return type == null ? null : type.output(pin);
    }

    /**
     * An input pin of a node.
     *
     * @param node The node.
     * @param pin The input's name.
     * @return The pin, or null if the node, its type or the pin is missing.
     */
    public @Nullable NodeTypes.Pin inputPin(@NonNull String node, @NonNull String pin) {
        final NodeTypes.NodeType type = typeOf(node);
        return type == null ? null : type.input(pin);
    }

    /**
     * A node's type.
     *
     * @param node The node's ID.
     * @return The type, or null if the node or its type is missing.
     */
    public @Nullable NodeTypes.NodeType typeOf(@NonNull String node) {
        final Node found = nodes.get(node);
        return found == null ? null : nodeTypes.get(found.type);
    }

    /**
     * A copy of the whole graph, sharing its types.
     *
     * @return The copy.
     */
    public Graph copy() {
        final Graph copy = new Graph(nodeTypes, pinTypes);
        copy.restore(this);
        return copy;
    }

    /**
     * Make this graph the same as another, keeping this object.
     *
     * @param other The graph to copy.
     */
    public void restore(@NonNull Graph other) {
        if (other == this) {
            return;
        }
        nodes.clear();
        for (Node node : other.nodes.values()) {
            nodes.put(node.id, node.copy(node.id));
        }
        links.clear();
        links.addAll(other.links);
        frames.clear();
        for (Frame frame : other.frames.values()) {
            frames.put(frame.id, frame.copy());
        }
    }

    /**
     * Copy some nodes, and the links among them, as a graph of their own, for the clipboard.
     *
     * @param ids The nodes.
     * @return The part.
     */
    public Graph part(@NonNull Set<String> ids) {
        final Graph part = new Graph(nodeTypes, pinTypes);
        for (Node node : nodes.values()) {
            if (ids.contains(node.id)) {
                part.nodes.put(node.id, node.copy(node.id));
            }
        }
        for (Link link : links) {
            if (ids.contains(link.fromNode()) && ids.contains(link.toNode())) {
                part.links.add(link);
            }
        }
        for (Frame frame : frames.values()) {
            if (ids.contains(frame.id)) {
                part.frames.put(frame.id, frame.copy());
            }
        }
        return part;
    }

    /**
     * Add a copy of another graph's nodes and links, under fresh IDs, moved by an offset, as when
     * pasting. Nodes of types this graph doesn't know are left out, with their links.
     *
     * @param part The nodes to add.
     * @param dx How far to move them across.
     * @param dy How far to move them down.
     * @return The new nodes' IDs, in the part's order.
     */
    public List<String> paste(@NonNull Graph part, float dx, float dy) {
        final Map<String, String> renamed = new HashMap<>();
        final List<String> added = new ArrayList<>();
        for (Node node : part.nodes.values()) {
            if (nodeTypes.get(node.type) == null) {
                continue;
            }
            final Node copy = node.copy(freshId(node.type));
            copy.x += dx;
            copy.y += dy;
            nodes.put(copy.id, copy);
            renamed.put(node.id, copy.id);
            added.add(copy.id);
        }
        for (Link link : part.links) {
            final String from = renamed.get(link.fromNode());
            final String to = renamed.get(link.toNode());
            if (from != null && to != null) {
                links.add(new Link(from, link.fromPin(), to, link.toPin()));
            }
        }
        for (Frame frame : part.frames.values()) {
            final Frame copy =
                    addFrame(
                            frame.x + dx,
                            frame.y + dy,
                            frame.width,
                            frame.height,
                            frame.text,
                            frame.color);
            added.add(copy.id);
        }
        return added;
    }

    /**
     * Collapse a node to its title, or open it again.
     *
     * @param id The node.
     * @param collapsed Whether it shows only its title.
     */
    public void setCollapsed(@NonNull String id, boolean collapsed) {
        final Node node = nodes.get(id);
        if (node != null) {
            node.collapsed = collapsed;
        }
    }

    /**
     * Hide a node's pins that have no links, or show them again.
     *
     * @param id The node.
     * @param hide Whether to hide them.
     */
    public void setHideUnused(@NonNull String id, boolean hide) {
        final Node node = nodes.get(id);
        if (node != null) {
            node.hideUnused = hide;
        }
    }

    /**
     * Put a note over a node, or take it away.
     *
     * @param id The node.
     * @param comment The note, or null or blank for none.
     */
    public void setComment(@NonNull String id, @Nullable String comment) {
        final Node node = nodes.get(id);
        if (node != null) {
            node.comment = comment == null || comment.isBlank() ? null : comment;
        }
    }

    /**
     * Every comment frame, in the order added.
     *
     * @return The frames.
     */
    public Collection<Frame> frames() {
        return Collections.unmodifiableCollection(frames.values());
    }

    /**
     * A comment frame by ID.
     *
     * @param id The ID.
     * @return The frame, or null if there is none.
     */
    public @Nullable Frame frame(@NonNull String id) {
        return frames.get(id);
    }

    /**
     * Add a comment frame, with an ID like {@code frame2}.
     *
     * @param x Its left edge, in canvas units.
     * @param y Its top edge.
     * @param width Its width.
     * @param height Its height.
     * @param text Its title.
     * @param color Its color.
     * @return The frame.
     */
    public Frame addFrame(
            float x, float y, float width, float height, @NonNull String text, int color) {
        int number = 1;
        while (frames.containsKey("frame" + number) || nodes.containsKey("frame" + number)) {
            ++number;
        }
        return putFrame("frame" + number, x, y, width, height, text, color);
    }

    /**
     * Put a comment frame in with its own ID, as when reading a file, replacing one with that ID.
     *
     * @param id The ID.
     * @param x Its left edge.
     * @param y Its top edge.
     * @param width Its width.
     * @param height Its height.
     * @param text Its title.
     * @param color Its color.
     * @return The frame.
     */
    public Frame putFrame(
            @NonNull String id,
            float x,
            float y,
            float width,
            float height,
            @NonNull String text,
            int color) {
        final Frame frame =
                new Frame(id, x, y, Math.max(1, width), Math.max(1, height), text, color);
        frames.put(id, frame);
        return frame;
    }

    /**
     * Move and size a comment frame.
     *
     * @param id The frame.
     * @param x Its left edge.
     * @param y Its top edge.
     * @param width Its width.
     * @param height Its height.
     */
    public void placeFrame(@NonNull String id, float x, float y, float width, float height) {
        final Frame frame = frames.get(id);
        if (frame != null) {
            frame.x = x;
            frame.y = y;
            frame.width = Math.max(1, width);
            frame.height = Math.max(1, height);
        }
    }

    /**
     * Change a comment frame's title and color.
     *
     * @param id The frame.
     * @param text Its title.
     * @param color Its color.
     */
    public void setFrameLook(@NonNull String id, @NonNull String text, int color) {
        final Frame frame = frames.get(id);
        if (frame != null) {
            frame.text = text;
            frame.color = color;
        }
    }

    /**
     * The nodes whose top left corner is inside a frame, which move with it.
     *
     * @param id The frame.
     * @return The nodes' IDs.
     */
    public List<String> nodesIn(@NonNull String id) {
        final Frame frame = frames.get(id);
        final List<String> inside = new ArrayList<>();
        if (frame == null) {
            return inside;
        }
        for (Node node : nodes.values()) {
            if (frame.contains(node.x, node.y)) {
                inside.add(node.id);
            }
        }
        return inside;
    }

    /**
     * The type a pin carries: its own, or for the editor's reroute pins, the type of what is linked
     * into them, followed upstream through reroutes and named reroutes. {@link PinTypes#ANY} if
     * nothing typed is linked in.
     *
     * @param node The node.
     * @param pin The pin.
     * @param input Whether it is an input.
     * @return The type's ID.
     */
    public String effectiveType(@NonNull String node, @NonNull String pin, boolean input) {
        return effectiveType(node, pin, input, new java.util.HashSet<>());
    }

    /**
     * The type a pin carries, not visiting a node twice.
     *
     * @param node The node.
     * @param pin The pin.
     * @param input Whether it is an input.
     * @param seen The nodes visited.
     * @return The type's ID.
     */
    private String effectiveType(String node, String pin, boolean input, Set<String> seen) {
        final NodeTypes.Pin declared = input ? inputPin(node, pin) : outputPin(node, pin);
        if (declared == null) {
            return PinTypes.ANY;
        }
        if (!PinTypes.ANY.equals(declared.type()) || !seen.add(node)) {
            return declared.type();
        }
        final Node found = nodes.get(node);
        final String type = found.type;
        if (NodeTypes.RECEIVE.equals(type)) {
            final String name = param(node, NodeTypes.NAME);
            for (Node sender : nodes.values()) {
                if (NodeTypes.SEND.equals(sender.type)
                        && name != null
                        && name.equals(param(sender.id, NodeTypes.NAME))) {
                    return effectiveType(sender.id, "in", true, seen);
                }
            }
            return PinTypes.ANY;
        }
        if (input || NodeTypes.REROUTE.equals(type)) {
            final Link into = linkInto(node, input ? pin : "in");
            if (into != null) {
                return effectiveType(into.fromNode(), into.fromPin(), false, seen);
            }
        }
        return PinTypes.ANY;
    }

    /**
     * The nodes a node's inputs come from, near and far, through named reroutes too.
     *
     * @param id The node.
     * @return The nodes upstream, not counting it.
     */
    public Set<String> upstream(@NonNull String id) {
        final Set<String> found = new java.util.LinkedHashSet<>();
        final java.util.ArrayDeque<String> open = new java.util.ArrayDeque<>();
        open.push(id);
        while (!open.isEmpty()) {
            final String at = open.pop();
            for (Link link : links) {
                if (link.toNode().equals(at) && found.add(link.fromNode())) {
                    open.push(link.fromNode());
                }
            }
            final Node node = nodes.get(at);
            if (node != null && NodeTypes.RECEIVE.equals(node.type)) {
                for (Node sender : namedPartners(node, NodeTypes.SEND)) {
                    if (found.add(sender.id)) {
                        open.push(sender.id);
                    }
                }
            }
        }
        found.remove(id);
        return found;
    }

    /**
     * The nodes a node's outputs go to, near and far, through named reroutes too.
     *
     * @param id The node.
     * @return The nodes downstream, not counting it.
     */
    public Set<String> downstream(@NonNull String id) {
        final Set<String> found = new java.util.LinkedHashSet<>();
        final java.util.ArrayDeque<String> open = new java.util.ArrayDeque<>();
        open.push(id);
        while (!open.isEmpty()) {
            final String at = open.pop();
            for (Link link : links) {
                if (link.fromNode().equals(at) && found.add(link.toNode())) {
                    open.push(link.toNode());
                }
            }
            final Node node = nodes.get(at);
            if (node != null && NodeTypes.SEND.equals(node.type)) {
                for (Node receiver : namedPartners(node, NodeTypes.RECEIVE)) {
                    if (found.add(receiver.id)) {
                        open.push(receiver.id);
                    }
                }
            }
        }
        found.remove(id);
        return found;
    }

    /**
     * The named reroutes of a kind with the same name as a node.
     *
     * @param node A send or receive node.
     * @param kind The other kind's type ID.
     * @return The partners.
     */
    private List<Node> namedPartners(Node node, String kind) {
        final String name = param(node.id, NodeTypes.NAME);
        final List<Node> partners = new ArrayList<>();
        for (Node other : nodes.values()) {
            if (kind.equals(other.type)
                    && name != null
                    && name.equals(param(other.id, NodeTypes.NAME))) {
                partners.add(other);
            }
        }
        return partners;
    }

    /**
     * The nodes nothing uses: those that are neither an end of the graph, a node of a type with no
     * outputs, nor upstream of one.
     *
     * @return The unused nodes' IDs.
     */
    public Set<String> unused() {
        final Set<String> used = new java.util.HashSet<>();
        for (Node node : nodes.values()) {
            final NodeTypes.NodeType type = nodeTypes.get(node.type);
            if (type != null && type.outputs().isEmpty() && !NodeTypes.SEND.equals(type.id())) {
                used.add(node.id);
                used.addAll(upstream(node.id));
            }
        }
        final Set<String> unused = new java.util.LinkedHashSet<>();
        for (Node node : nodes.values()) {
            if (!used.contains(node.id)) {
                unused.add(node.id);
            }
        }
        return unused;
    }
}
