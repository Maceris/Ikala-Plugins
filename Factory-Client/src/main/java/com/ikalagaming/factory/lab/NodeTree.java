package com.ikalagaming.factory.lab;

import com.ikalagaming.factory.world.gen.debug.SliceImages;
import com.ikalagaming.factory.world.gen.debug.WorldgenDebug;
import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.enums.Condition;

import lombok.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Lab's node tree: the target's density tree, each node with its range over the current view,
 * the axes it depends on, and a small live image of its own output over the slice, so you can see
 * where a composition goes wrong. Clicking a row opens or closes it; its View button makes it the
 * main view's target, and the tree stays rooted where it was. Render thread only, except where
 * noted.
 */
final class NodeTree {

    /** The width of a thumbnail, in pixels. */
    static final int THUMBNAIL_SIZE = 64;

    /** How many nodes are shown at most, so a huge tree stays responsive. */
    private static final int MAX_ROWS = 400;

    /** Draws thumbnails. */
    private final LabRenderer renderer;

    /** Told when a node's View button is pressed, with its target. */
    private final Consumer<String> onPick;

    /** The tree being shown, or null. */
    private WorldgenDebug.Bounds root;

    /** The world generation the tree came from. */
    private WorldgenDebug debug;

    /** What the thumbnails are drawn over, at thumbnail size. */
    private SliceImages.Request slice;

    /** Thumbnails by node origin, for the current slice. */
    private final Map<String, LabRenderer.Frame> thumbnails = new HashMap<>();

    /** Origins whose thumbnails are being drawn. */
    private final Set<String> pending = new HashSet<>();

    /** Posts work back to the render thread. */
    private final Consumer<Runnable> post;

    /** Rows drawn this frame, against {@link #MAX_ROWS}. */
    private int rows;

    /**
     * Set up the tree.
     *
     * @param renderer Draws thumbnails.
     * @param post Runs work on the render thread.
     * @param onPick Told when a node is picked, with its target.
     */
    NodeTree(
            @NonNull LabRenderer renderer,
            @NonNull Consumer<Runnable> post,
            @NonNull Consumer<String> onPick) {
        this.renderer = renderer;
        this.post = post;
        this.onPick = onPick;
    }

    /**
     * Show a new tree, dropping old thumbnails.
     *
     * @param debug The world generation it came from.
     * @param tree The bounds tree over the view, or null for none.
     * @param thumbnailSlice The slice thumbnails show, at thumbnail size.
     */
    void show(WorldgenDebug debug, WorldgenDebug.Bounds tree, SliceImages.Request thumbnailSlice) {
        clear();
        this.debug = debug;
        this.root = tree;
        this.slice = thumbnailSlice;
    }

    /** Drop the tree and release its thumbnails. */
    void clear() {
        thumbnails.values().forEach(frame -> renderer.release(frame.texture()));
        thumbnails.clear();
        pending.clear();
        root = null;
    }

    /**
     * Draw the tree with IkGui, inside the side panel.
     *
     * @param viewing The main view's target, to mark the node it shows.
     */
    void draw(@NonNull String viewing) {
        if (root == null) {
            IkGui.textWrapped("Pick a density, parameter or noise target to see its tree.");
            return;
        }
        rows = 0;
        IkGui.setNextItemOpen(true, Condition.ONCE);
        drawNode(root, 0, viewing);
        if (rows >= MAX_ROWS) {
            IkGui.text("(Only the first " + MAX_ROWS + " nodes are shown)");
        }
    }

    /**
     * Draw one node and, if open, its children.
     *
     * @param node The node's bounds.
     * @param index Its index among its siblings, for a unique ID.
     * @param viewing The main view's target.
     */
    private void drawNode(WorldgenDebug.@NonNull Bounds node, int index, @NonNull String viewing) {
        if (++rows > MAX_ROWS) {
            return;
        }
        final String origin = node.origin() == null ? "" : node.origin();
        final String label =
                String.format(
                        "%s  [%.3g, %.3g]  %s###%d:%s",
                        node.type(),
                        node.bounds().min(),
                        node.bounds().max(),
                        verdict(node),
                        index,
                        origin);
        // Clicking the row only opens and closes it; viewing is its own button
        final boolean open = IkGui.treeNode(label);
        IkGui.setItemTooltip(origin.isEmpty() ? node.type() : origin);
        if (!origin.isEmpty()) {
            final String target = WorldgenDebug.targetOf(origin);
            IkGui.sameLine();
            if (target.equals(viewing)) {
                IkGui.textDisabled("(viewing)");
            } else {
                IkGui.pushID(label);
                if (IkGui.smallButton("View")) {
                    onPick.accept(target);
                }
                IkGui.setItemTooltip("Show this node in the main view; the tree stays as it is.");
                IkGui.popID();
            }
        }
        if (!open) {
            return;
        }
        drawThumbnail(origin);
        for (int i = 0; i < node.children().size(); ++i) {
            drawNode(node.children().get(i), i, viewing);
        }
        IkGui.treePop();
    }

    /**
     * Whether a node is certainly solid, certainly air or mixed over the view.
     *
     * @param node The node's bounds.
     * @return A short word.
     */
    private static String verdict(WorldgenDebug.@NonNull Bounds node) {
        if (node.bounds().min() > 0) {
            return "solid";
        }
        if (node.bounds().max() <= 0) {
            return "air";
        }
        return "mixed";
    }

    /**
     * Draw a node's thumbnail, asking for it if it isn't drawn yet.
     *
     * @param origin The node's origin.
     */
    private void drawThumbnail(@NonNull String origin) {
        final LabRenderer.Frame frame = thumbnails.get(origin);
        if (frame != null) {
            IkGui.image(
                    frame.texture(),
                    THUMBNAIL_SIZE,
                    THUMBNAIL_SIZE * (float) slice.height() / slice.width());
            IkGui.sameLine();
            IkGui.text(frame.millis() + " ms");
            return;
        }
        IkGui.text("(drawing...)");
        if (origin.isEmpty() || pending.contains(origin) || debug == null) {
            return;
        }
        final Optional<DensityNode> densityNode = debug.nodeAt(origin);
        if (densityNode.isEmpty()) {
            return;
        }
        pending.add(origin);
        final SliceImages.Request request = slice;
        final WorldgenDebug source = debug;
        renderer.thumbnail(source, densityNode.get(), request)
                .thenAccept(
                        drawn ->
                                post.accept(
                                        () -> {
                                            // Dropped if the tree or view changed meanwhile
                                            if (slice == request && debug == source) {
                                                thumbnails.put(origin, drawn);
                                            } else {
                                                renderer.release(drawn.texture());
                                            }
                                        }));
    }
}
