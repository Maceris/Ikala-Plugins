package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A piece of retained UI. Nodes form a tree inside a {@link Surface}; the layout engine gives each
 * one a rectangle, and then each is submitted to IkGui at that rectangle.
 *
 * <p>Build trees on any thread while they aren't shown. Once a surface is shown, change its nodes
 * only on the render thread, for example through {@code UI.post}.
 *
 * <p>The setters return the node itself, so they chain: {@code new Button("OK").width(...)}.
 *
 * @param <S> The node's own type, so chained setters keep it.
 */
public abstract class Node<S extends Node<S>> {
    /**
     * The ID, unique among its siblings, which IkGui IDs and later lookups are built from. --
     * GETTER -- The ID, unique among its siblings.
     *
     * @return The ID.
     */
    @Getter private final String id;

    /**
     * The parent, or null for the root of a surface. -- GETTER -- The parent node.
     *
     * @return The parent, or null for the root of a surface.
     */
    @Getter Node<?> parent;

    /** Children, only used by containers. */
    final List<Node<?>> children = new ArrayList<>();

    /** How the width is decided. */
    Sizing width = Sizing.fit();

    /** How the height is decided. */
    Sizing height = Sizing.fit();

    /** Space between the edges and the content. */
    Insets padding = Insets.NONE;

    /** Where the node sits when its parent is an {@link Overlay}. */
    Anchors anchors = Anchors.topLeft();

    /** The font size in UI units, or null to use the parent's. */
    Float fontSize;

    /**
     * Hidden nodes take no space and are not submitted. -- GETTER -- Whether the node is shown.
     *
     * @return True if the node takes part in layout and is drawn.
     */
    @Getter boolean visible = true;

    /** Whether the node or something below it changed since the last layout. */
    boolean dirty = true;

    /** The font size in pixels, from the last layout. */
    float fontPixels;

    /** The content size from the fit passes, including padding, in pixels. */
    final float[] fit = new float[2];

    /** The final size, in pixels. */
    final float[] size = new float[2];

    /** Where the node ended up, in screen pixels. */
    final RectFloat rect = new RectFloat();

    /**
     * Create a node.
     *
     * @param id The ID, which must be unique among its siblings.
     */
    protected Node(@NonNull String id) {
        this.id = id;
    }

    /**
     * This node, as its own type.
     *
     * @return This node.
     */
    @SuppressWarnings("unchecked")
    protected final S self() {
        return (S) this;
    }

    /**
     * Where the node was placed by the last layout, in screen pixels. Read only on the render
     * thread.
     *
     * @return A copy of the rectangle.
     */
    public RectFloat getRect() {
        return new RectFloat(rect);
    }

    /**
     * The children, in order.
     *
     * @return The children, which can't be modified through this list.
     */
    public List<Node<?>> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Set how the width is decided.
     *
     * @param sizing The width.
     * @return This node.
     */
    public S width(@NonNull Sizing sizing) {
        width = sizing;
        markDirty();
        return self();
    }

    /**
     * Set how the height is decided.
     *
     * @param sizing The height.
     * @return This node.
     */
    public S height(@NonNull Sizing sizing) {
        height = sizing;
        markDirty();
        return self();
    }

    /**
     * Set the space between the edges and the content.
     *
     * @param insets The padding.
     * @return This node.
     */
    public S padding(@NonNull Insets insets) {
        padding = insets;
        markDirty();
        return self();
    }

    /**
     * Set where the node sits when its parent is an {@link Overlay}.
     *
     * @param newAnchors The anchors.
     * @return This node.
     */
    public S anchors(@NonNull Anchors newAnchors) {
        anchors = newAnchors;
        markDirty();
        return self();
    }

    /**
     * Set the font size for this node and the nodes below it.
     *
     * @param units The font size in UI units, so it follows the UI scale.
     * @return This node.
     */
    public S fontSize(float units) {
        fontSize = units;
        markDirty();
        return self();
    }

    /**
     * Show or hide the node. Hidden nodes take no space.
     *
     * @param show Whether to show the node.
     * @return This node.
     */
    public S visible(boolean show) {
        visible = show;
        markDirty();
        return self();
    }

    /** Mark this node and its ancestors as needing layout. */
    protected final void markDirty() {
        for (Node<?> node = this; node != null && !node.dirty; node = node.parent) {
            node.dirty = true;
        }
        // The loop stops at a node that is already dirty, which means its ancestors are too
    }

    /**
     * The size of the node's content, not counting padding, before its parent decides its final
     * size. Leaves override this; containers measure their children instead.
     *
     * @param context The layout context.
     * @param out Receives the width at 0 and the height at 1, in pixels.
     */
    protected void measure(@NonNull LayoutContext context, float[] out) {
        out[0] = 0;
        out[1] = 0;
    }

    /**
     * Submit this node to IkGui at {@link #rect}. Called inside the surface's IkGui window, with
     * this node's ID pushed. Containers submit their children.
     *
     * @param frame Details about the current frame.
     */
    protected void submit(@NonNull UiFrame frame) {
        for (Node<?> child : children) {
            child.submitTree(frame);
        }
    }

    /**
     * Submit this node and everything below it, with its ID and font pushed.
     *
     * @param frame Details about the current frame.
     */
    final void submitTree(@NonNull UiFrame frame) {
        if (!visible) {
            return;
        }
        IkGui.pushID(id);
        boolean pushedFont = fontSize != null;
        if (pushedFont) {
            IkGui.pushFontSize(Math.round(fontPixels));
        }
        submit(frame);
        if (pushedFont) {
            IkGui.popFont();
        }
        IkGui.popID();
    }

    /**
     * Mark this node and everything below it as laid out.
     *
     * @return This node.
     */
    final Node<?> clean() {
        dirty = false;
        // A scroll lays out its own content, which stays dirty until then
        if (!(this instanceof Scroll)) {
            children.forEach(Node::clean);
        }
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + "]";
    }
}
