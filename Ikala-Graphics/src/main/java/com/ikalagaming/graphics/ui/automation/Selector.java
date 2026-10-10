package com.ikalagaming.graphics.ui.automation;

import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Surface;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Picks out nodes on the shown surfaces, written as a path:
 *
 * <ul>
 *   <li>{@code about} is the root of the surface {@code about}.
 *   <li>{@code about/buttons/close} follows child IDs from the root, as {@code SpecInstance.find}
 *       does.
 *   <li>{@code about//close} is a node with ID {@code close} anywhere in the surface, the root
 *       included.
 *   <li>{@code about//text=Close} is a node anywhere under the root showing the text {@code Close}.
 *       The text runs to the end, so it may hold slashes.
 *   <li><code>&#42;//ok</code> looks on every shown surface.
 * </ul>
 *
 * <p>Surface IDs may hold slashes: {@code converter/about//close} is on the surface {@code
 * converter/about}.
 */
public final class Selector {
    /** Prefix of a segment that matches by text instead of ID. */
    private static final String TEXT_PREFIX = "text=";

    /** How many other places a missed ID or text is listed in an error. */
    private static final int MAX_HINTS = 3;

    /**
     * One step of the path.
     *
     * @param anyDepth Whether it matches any node below, rather than only children.
     * @param byText Whether it matches by text, rather than by ID.
     * @param value The ID or text.
     */
    private record Segment(boolean anyDepth, boolean byText, String value) {
        boolean matches(Node<?> node) {
            return value.equals(byText ? node.getText() : node.getId());
        }

        String describe() {
            return byText ? "text '" + value + "'" : "ID '" + value + "'";
        }
    }

    /**
     * What a selector found.
     *
     * @param nodes The nodes, in tree order, which may be empty.
     * @param problem Why nothing was found, or null if something was.
     */
    public record Match(@NonNull List<Node<?>> nodes, String problem) {}

    /** The selector as written. */
    private final String text;

    /**
     * Create a selector.
     *
     * @param text The selector as written.
     */
    private Selector(String text) {
        this.text = text;
    }

    /**
     * Read a selector. Surface IDs may hold slashes, like {@code converter/about//close}, so which
     * part names the surface is only decided against the shown surfaces: the longest ID that starts
     * the selector.
     *
     * @param text The selector, like {@code about//close}.
     * @return The selector.
     * @throws IllegalArgumentException If it is written wrong.
     */
    public static Selector parse(@NonNull String text) {
        if (text.isEmpty() || text.charAt(0) == '/') {
            throw new IllegalArgumentException(
                    "Selector '" + text + "' needs a surface ID or * before the first /");
        }
        // Text runs to the end and may hold slashes, so only the IDs before it are checked
        int byText = text.indexOf("/" + TEXT_PREFIX);
        String path = byText < 0 ? text : text.substring(0, byText);
        if (byText >= 0 && path.endsWith("/")) {
            // The first slash of a //text= segment
            path = path.substring(0, path.length() - 1);
        }
        if (path.contains("///") || path.endsWith("/")) {
            throw new IllegalArgumentException("Selector '" + text + "' has an empty ID");
        }
        return new Selector(text);
    }

    /**
     * Read the path below a surface's root.
     *
     * @param path The path, starting with / or // unless empty.
     * @return The segments.
     */
    private static List<Segment> segments(String path) {
        List<Segment> segments = new ArrayList<>();
        int pos = 0;
        while (pos < path.length()) {
            boolean anyDepth = path.startsWith("//", pos);
            pos += anyDepth ? 2 : 1;
            if (path.startsWith(TEXT_PREFIX, pos)) {
                segments.add(
                        new Segment(anyDepth, true, path.substring(pos + TEXT_PREFIX.length())));
                break;
            }
            int end = path.indexOf('/', pos);
            if (end < 0) {
                end = path.length();
            }
            segments.add(new Segment(anyDepth, false, path.substring(pos, end)));
            pos = end;
        }
        return segments;
    }

    /**
     * Find the nodes this selects. Render thread only.
     *
     * @param surfaces The shown surfaces.
     * @return The nodes, or why there are none.
     */
    public Match find(@NonNull List<Surface> surfaces) {
        List<Node<?>> current = new ArrayList<>();
        String path;
        if ("*".equals(text) || text.startsWith("*/")) {
            surfaces.forEach(shown -> current.add(shown.getContent()));
            path = text.substring(1);
        } else {
            Surface best = null;
            for (Surface shown : surfaces) {
                String id = shown.getId();
                boolean starts = text.equals(id) || text.startsWith(id + "/");
                if (starts && (best == null || id.length() > best.getId().length())) {
                    best = shown;
                }
            }
            if (best != null) {
                current.add(best.getContent());
            }
            path = best == null ? "" : text.substring(best.getId().length());
        }
        if (current.isEmpty()) {
            String ids = surfaces.stream().map(Surface::getId).collect(Collectors.joining(", "));
            return new Match(
                    List.of(),
                    surfaces.isEmpty()
                            ? "no surfaces are shown"
                            : "no shown surface matches '" + text + "' (shown: " + ids + ")");
        }
        boolean atRoots = true;
        for (Segment segment : segments(path)) {
            // From the surface, any depth starts at the roots themselves
            List<Node<?>> next = step(current, segment, atRoots);
            atRoots = false;
            if (next.isEmpty()) {
                return new Match(List.of(), missed(current, segment, surfaces));
            }
            current.clear();
            current.addAll(next);
        }
        return new Match(List.copyOf(current), null);
    }

    /**
     * The nodes one segment reaches from a set of nodes.
     *
     * @param from The nodes to start at.
     * @param segment The segment.
     * @param includeFrom Whether a segment of any depth can match the starting nodes themselves.
     * @return The nodes it matches, each once, in tree order.
     */
    private static List<Node<?>> step(List<Node<?>> from, Segment segment, boolean includeFrom) {
        Set<Node<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Node<?>> found = new ArrayList<>();
        for (Node<?> node : from) {
            if (segment.anyDepth()) {
                if (includeFrom && segment.matches(node) && seen.add(node)) {
                    found.add(node);
                }
                for (Node<?> below : descendants(node)) {
                    if (segment.matches(below) && seen.add(below)) {
                        found.add(below);
                    }
                }
            } else {
                for (Node<?> child : node.getContentNodes()) {
                    if (segment.matches(child) && seen.add(child)) {
                        found.add(child);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Every node below one, in tree order, not counting itself.
     *
     * @param node The node.
     * @return The nodes below it.
     */
    private static List<Node<?>> descendants(Node<?> node) {
        List<Node<?>> found = new ArrayList<>();
        Deque<Node<?>> stack = new ArrayDeque<>();
        pushChildren(stack, node);
        while (!stack.isEmpty()) {
            Node<?> next = stack.pop();
            found.add(next);
            pushChildren(stack, next);
        }
        return found;
    }

    /**
     * Push a node's children so the first one pops first.
     *
     * @param stack The stack.
     * @param node The node.
     */
    private static void pushChildren(Deque<Node<?>> stack, Node<?> node) {
        List<Node<?>> children = node.getContentNodes();
        for (int i = children.size() - 1; i >= 0; --i) {
            stack.push(children.get(i));
        }
    }

    /**
     * Explain why a segment found nothing.
     *
     * @param from The nodes it started at.
     * @param segment The segment.
     * @param surfaces The shown surfaces, to look for the same ID or text elsewhere.
     * @return The explanation.
     */
    private static String missed(List<Node<?>> from, Segment segment, List<Surface> surfaces) {
        StringBuilder message = new StringBuilder();
        String where = from.size() == 1 ? "'" + pathOf(from.getFirst(), surfaces) + "'" : null;
        if (segment.anyDepth()) {
            message.append("nothing under ")
                    .append(where != null ? where : from.size() + " nodes")
                    .append(" has ")
                    .append(segment.describe());
        } else {
            message.append(where != null ? where : "none of " + from.size() + " nodes")
                    .append(" has no child with ")
                    .append(segment.describe());
            if (where != null) {
                String children =
                        from.getFirst().getContentNodes().stream()
                                .map(Node::getId)
                                .collect(Collectors.joining(", "));
                message.append(" (children: ")
                        .append(children.isEmpty() ? "none" : children)
                        .append(')');
            }
        }
        List<String> elsewhere = new ArrayList<>();
        Segment anywhere = new Segment(true, segment.byText(), segment.value());
        for (Surface shown : surfaces) {
            for (Node<?> node : step(List.of(shown.getContent()), anywhere, true)) {
                if (elsewhere.size() < MAX_HINTS) {
                    elsewhere.add(pathOf(node, surfaces));
                }
            }
        }
        if (!elsewhere.isEmpty()) {
            message.append("; found at ").append(String.join(", ", elsewhere));
        }
        return message.toString();
    }

    /**
     * The path of a node, written like a selector.
     *
     * @param node The node.
     * @param surfaces The shown surfaces, to name the one the node is on.
     * @return The path, like {@code about/buttons/close}.
     */
    public static String pathOf(@NonNull Node<?> node, @NonNull List<Surface> surfaces) {
        List<String> ids = new ArrayList<>();
        Node<?> top = node;
        while (top.getParent() != null) {
            ids.add(top.getId());
            top = top.getParent();
        }
        String surfaceId = "?";
        for (Surface shown : surfaces) {
            if (shown.getContent() == top) {
                surfaceId = shown.getId();
            }
        }
        StringBuilder path = new StringBuilder(surfaceId);
        for (int i = ids.size() - 1; i >= 0; --i) {
            path.append('/').append(ids.get(i));
        }
        return path.toString();
    }

    @Override
    public String toString() {
        return text;
    }
}
