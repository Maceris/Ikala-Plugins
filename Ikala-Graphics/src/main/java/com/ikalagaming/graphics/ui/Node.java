package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.style.ComputedStyle;
import com.ikalagaming.graphics.ui.style.Style;

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

    /** Space between the edges and the content, or null to use the style's. */
    Insets padding;

    /** The style classes, in order; later ones win. */
    List<String> classes = List.of();

    /** The node's own style, which wins over its classes. */
    Style inline = Style.EMPTY;

    /**
     * The style computed by the last layout, for drawing.
     *
     * @return The computed style.
     */
    @Getter ComputedStyle style = ComputedStyle.EMPTY;

    /** The padding the last layout used: the node's own, the style's, or the default. */
    Insets resolvedPadding = Insets.NONE;

    /** Whether this node sets its own font size, rather than using its parent's. */
    boolean ownFont;

    /** Where the node sits when its parent is an {@link Overlay}. */
    Anchors anchors = Anchors.topLeft();

    /** Where the node sits when its parent is a {@link Canvas}, in UI units. */
    float positionX;

    /** Where the node sits when its parent is a {@link Canvas}, in UI units. */
    float positionY;

    /** The font size in UI units, or null to use the style's or the parent's. */
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

    /** Pixels per UI unit, from the last layout, which includes a canvas's zoom. */
    float scale = 1;

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
     * Set the style classes, replacing any already set. Classes come from the theme; later ones win
     * over earlier ones.
     *
     * @param names The class names.
     * @return This node.
     */
    public S classes(@NonNull String... names) {
        classes = List.of(names);
        markDirty();
        return self();
    }

    /**
     * The style classes, in order.
     *
     * @return The class names.
     */
    public List<String> getClasses() {
        return classes;
    }

    /**
     * Set the node's own style, which wins over its classes and type defaults. Explicit setters
     * like {@link #padding(Insets)} still win over it.
     *
     * @param newStyle The style.
     * @return This node.
     */
    public S style(@NonNull Style newStyle) {
        inline = newStyle;
        markDirty();
        return self();
    }

    /**
     * The name of this kind of node in themes, such as {@code button}. Theme type defaults are
     * looked up by it.
     *
     * @return The type name.
     */
    public String styleType() {
        return "node";
    }

    /**
     * The padding to use when neither the node nor its style sets any.
     *
     * @param context The layout context.
     * @return The default padding.
     */
    protected Insets defaultPadding(@NonNull LayoutContext context) {
        return Insets.NONE;
    }

    /**
     * The padding the last layout used.
     *
     * @return The padding.
     */
    public Insets getResolvedPadding() {
        return resolvedPadding;
    }

    /**
     * Pixels per UI unit in the last layout: the UI scale, times the zoom of any canvas the node is
     * on. For drawing code that sizes things in UI units.
     *
     * @return The scale.
     */
    public float getScale() {
        return scale;
    }

    /**
     * The font size the last layout used, in pixels.
     *
     * @return The font size.
     */
    public float getFontPixels() {
        return fontPixels;
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
     * Set where the node sits when its parent is a {@link Canvas}: its top left, in the canvas's UI
     * units, before zooming. Other parents ignore it.
     *
     * @param x The left edge.
     * @param y The top edge.
     * @return This node.
     */
    public S position(float x, float y) {
        positionX = x;
        positionY = y;
        markDirty();
        return self();
    }

    /**
     * Where the node sits when its parent is a canvas, horizontally.
     *
     * @return The left edge, in UI units.
     */
    public float getPositionX() {
        return positionX;
    }

    /**
     * Where the node sits when its parent is a canvas, vertically.
     *
     * @return The top edge, in UI units.
     */
    public float getPositionY() {
        return positionY;
    }

    /**
     * Whether this node lays out its own children while it is submitted, like a scroll that needs
     * its view size first. The layout engine leaves the children of such nodes alone.
     *
     * @return True if the children are laid out by the node itself.
     */
    boolean laysOutOwnContent() {
        return false;
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
        boolean pushedFont = ownFont;
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
        // Scrolls and canvases lay out their own content, which stays dirty until then
        if (!laysOutOwnContent()) {
            children.forEach(Node::clean);
        }
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + "]";
    }
}
