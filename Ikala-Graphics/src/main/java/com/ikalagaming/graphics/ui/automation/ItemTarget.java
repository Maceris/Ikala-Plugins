package com.ikalagaming.graphics.ui.automation;

import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Surface;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Picks out an IkGui item drawn in immediate mode, like a slider inside an {@code Immediate} area,
 * by where it is and its label.
 *
 * <p>The scope says where to look:
 *
 * <ul>
 *   <li>A {@link Selector}, like {@code tools//materials}: items drawn inside that node's
 *       rectangle, in the window of the surface it is in, including child windows such as an {@code
 *       Immediate} area's.
 *   <li>{@code window=Name}: items in a plain IkGui window and its child windows, by the window's
 *       name, with or without the part from {@code ##} on.
 *   <li>{@code *}: items in any window.
 * </ul>
 *
 * <p>Items in popups, like the list a combo box opens, are found from any scope, since they are
 * drawn outside the node or window that opened them.
 *
 * <p>The label matches the label a widget was given, like {@code Roughness##base}, either all of
 * it, the shown part before {@code ##} ({@code Roughness}), or only the ID part ({@code ##base}).
 * Widgets without a label, like plain text, can't be found.
 */
public final class ItemTarget {
    /** How many labels a miss lists. */
    private static final int MAX_LISTED = 20;

    /** The window name prefix of retained surfaces, as {@code Surface} names its windows. */
    private static final String SURFACE_WINDOW_PREFIX = "###ui/";

    /** The scope prefix for plain IkGui windows. */
    private static final String WINDOW_PREFIX = "window=";

    /**
     * What looking found.
     *
     * @param item The one visible item, or null.
     * @param problem Why there is no item, or null if there is.
     */
    record Lookup(ItemRecorder.Item item, String problem) {}

    /** The scope as written. */
    private final String scope;

    /** The scope as a selector, or null for a window or any window. */
    private final Selector selector;

    /** The label to look for. */
    private final String label;

    /**
     * Create a target.
     *
     * @param scope Where to look.
     * @param label The item's label.
     * @throws IllegalArgumentException If the scope or label is written wrong.
     */
    ItemTarget(@NonNull String scope, @NonNull String label) {
        if (label.isEmpty()) {
            throw new IllegalArgumentException("An item label can't be empty");
        }
        if (scope.startsWith(WINDOW_PREFIX) && scope.length() == WINDOW_PREFIX.length()) {
            throw new IllegalArgumentException("'" + scope + "' names no window");
        }
        this.scope = scope;
        this.label = label;
        selector =
                "*".equals(scope) || scope.startsWith(WINDOW_PREFIX) ? null : Selector.parse(scope);
    }

    /**
     * Look for the item.
     *
     * @param run The run.
     * @return What was found.
     * @throws UiAutomationException If more than one visible item matches.
     */
    Lookup find(@NonNull Run run) {
        ItemRecorder recorder = run.items();
        if (!recorder.isRecording()) {
            return new Lookup(null, "IkGui items aren't being recorded yet");
        }
        Node<?> node = null;
        String rootName = null;
        if (selector != null) {
            Run.Lookup found = run.find(selector);
            if (found.shown() == null) {
                return new Lookup(null, found.problem());
            }
            node = found.shown();
            Surface surface = surfaceOf(run, node);
            if (surface == null) {
                return new Lookup(null, "'" + run.pathOf(node) + "' isn't in a shown surface");
            }
            rootName = SURFACE_WINDOW_PREFIX + surface.getId();
        } else if (scope.startsWith(WINDOW_PREFIX)) {
            rootName = scope.substring(WINDOW_PREFIX.length());
        }

        List<ItemRecorder.Item> inScope = new ArrayList<>();
        for (ItemRecorder.Item item : recorder.recent()) {
            if (item.label != null && inScope(item, node, rootName)) {
                inScope.add(item);
            }
        }
        List<ItemRecorder.Item> matches =
                inScope.stream().filter(item -> labelMatches(item.label)).toList();
        List<ItemRecorder.Item> visible =
                matches.stream().filter(ItemRecorder.Item::isVisible).toList();
        if (visible.size() == 1) {
            return new Lookup(visible.getFirst(), null);
        }
        if (visible.size() > 1) {
            List<String> found =
                    visible.stream()
                            .sorted(Comparator.comparingDouble(item -> item.rect.getTop()))
                            .map(ItemTarget::describe)
                            .toList();
            throw new UiAutomationException(
                    "'" + this + "' is ambiguous: " + String.join(", ", found));
        }
        if (!matches.isEmpty()) {
            return new Lookup(null, "'" + this + "' is scrolled or clipped out of view");
        }
        TreeSet<String> labels = new TreeSet<>();
        inScope.forEach(item -> labels.add(item.label));
        String where =
                selector != null
                        ? "'" + scope + "'"
                        : rootName != null ? "window '" + rootName + "'" : "any window";
        if (labels.isEmpty()) {
            return new Lookup(null, "no labelled IkGui items in " + where);
        }
        List<String> listed = labels.stream().limit(MAX_LISTED).toList();
        return new Lookup(
                null,
                "no item in "
                        + where
                        + " is labelled '"
                        + label
                        + "' (labels: "
                        + String.join(", ", listed)
                        + (labels.size() > MAX_LISTED ? ", ..." : "")
                        + ")");
    }

    /**
     * Whether an item is in the scope.
     *
     * @param item The item.
     * @param node The node it must be inside, or null.
     * @param rootName The name of the root window it must be in, or null for any.
     * @return True if it is in the scope.
     */
    private static boolean inScope(ItemRecorder.Item item, Node<?> node, String rootName) {
        Window window = item.window;
        if (window == null) {
            return false;
        }
        Window root = window.rootWindow != null ? window.rootWindow : window;
        if (isPopup(window) || isPopup(root)) {
            return true;
        }
        if (rootName != null && !nameMatches(root.name, rootName)) {
            return false;
        }
        if (node == null) {
            return true;
        }
        RectFloat rect = node.getRect();
        float x = item.visible.getCenterX();
        float y = item.visible.getCenterY();
        return x >= rect.getLeft()
                && x <= rect.getRight()
                && y >= rect.getTop()
                && y <= rect.getBottom();
    }

    /**
     * Whether a window is a popup, like a combo list or a menu.
     *
     * @param window The window.
     * @return True for popups.
     */
    private static boolean isPopup(Window window) {
        return (window.flags & WindowFlags.INTERNAL_POPUP) != 0;
    }

    /**
     * Whether a window name matches what a scope asks for.
     *
     * @param name The window's name.
     * @param wanted The name asked for.
     * @return True for the whole name, or the shown part before {@code ##}.
     */
    private static boolean nameMatches(String name, String wanted) {
        if (name == null) {
            return false;
        }
        if (name.equals(wanted)) {
            return true;
        }
        int hashes = name.indexOf("##");
        return hashes >= 0 && name.substring(0, hashes).equals(wanted);
    }

    /**
     * Whether an item's label matches.
     *
     * @param itemLabel The label the widget was given.
     * @return True if it is the one asked for.
     */
    private boolean labelMatches(String itemLabel) {
        if (itemLabel.equals(label)) {
            return true;
        }
        int hashes = itemLabel.indexOf("##");
        if (hashes < 0) {
            return false;
        }
        if (label.startsWith("##")) {
            return itemLabel.substring(hashes).equals(label);
        }
        return itemLabel.substring(0, hashes).equals(label);
    }

    /**
     * The surface a node is in.
     *
     * @param run The run.
     * @param node The node.
     * @return The surface, or null.
     */
    private static Surface surfaceOf(Run run, Node<?> node) {
        Node<?> root = node;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        for (Surface surface : run.surfaces()) {
            if (surface.getContent() == root) {
                return surface;
            }
        }
        return null;
    }

    /**
     * An item, for messages.
     *
     * @param item The item.
     * @return Its label, window and position.
     */
    static String describe(ItemRecorder.Item item) {
        return String.format(
                Locale.ROOT,
                "'%s' in '%s' at (%.0f, %.0f)",
                item.label,
                item.window == null ? "?" : item.window.name,
                item.rect.getLeft(),
                item.rect.getTop());
    }

    @Override
    public String toString() {
        return scope + ", " + label;
    }
}
