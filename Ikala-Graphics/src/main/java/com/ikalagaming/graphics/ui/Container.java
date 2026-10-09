package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.ui.style.StyleKey;

import lombok.NonNull;

/**
 * A node that holds other nodes.
 *
 * @param <S> The container's own type, so chained setters keep it.
 */
public abstract class Container<S extends Container<S>> extends Node<S> {

    /**
     * Create a container.
     *
     * @param id The ID, which must be unique among its siblings.
     */
    protected Container(@NonNull String id) {
        super(id);
    }

    /**
     * Add children at the end.
     *
     * @param nodes The nodes to add, which must not already have a parent.
     * @return This container.
     * @throws IllegalArgumentException If a node already has a parent, or its ID is already used by
     *     a child.
     */
    public S add(@NonNull Node<?>... nodes) {
        for (Node<?> node : nodes) {
            if (node.parent != null) {
                throw new IllegalArgumentException(node + " is already in " + node.parent);
            }
            if (find(node.getId()) != null) {
                throw new IllegalArgumentException(
                        this + " already has a child with the ID " + node.getId());
            }
            node.parent = this;
            children.add(node);
        }
        markDirty();
        return self();
    }

    /**
     * Remove a child.
     *
     * @param node The child to remove.
     * @return This container.
     */
    public S remove(@NonNull Node<?> node) {
        if (children.remove(node)) {
            node.parent = null;
            markDirty();
        }
        return self();
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        drawPanel(frame);
        super.submit(frame);
    }

    /**
     * Draw the container's background and border, if its style sets them, so a container can be a
     * panel.
     *
     * @param frame Details about the current frame.
     */
    protected final void drawPanel(@NonNull UiFrame frame) {
        Integer background = style.color(StyleKey.BACKGROUND);
        Integer border = style.color(StyleKey.BORDER);
        Length borderSize = style.length(StyleKey.BORDER_SIZE);
        if (background == null && (border == null || borderSize == null)) {
            return;
        }
        Length roundingLength = style.length(StyleKey.ROUNDING);
        float rounding =
                roundingLength == null
                        ? 0
                        : roundingLength.resolve(0, frame.context().scale(), fontPixels);
        DrawList drawList = IkGui.getWindowDrawList();
        if (background != null) {
            drawList.addRectFilled(
                    rect.getLeft(),
                    rect.getTop(),
                    rect.getRight(),
                    rect.getBottom(),
                    background,
                    rounding);
        }
        if (border != null && borderSize != null) {
            float thickness = borderSize.resolve(0, frame.context().scale(), fontPixels);
            if (thickness > 0) {
                drawList.addRect(
                        rect.getLeft(),
                        rect.getTop(),
                        rect.getRight(),
                        rect.getBottom(),
                        border,
                        rounding,
                        com.ikalagaming.graphics.gui.flags.DrawFlags.NONE,
                        thickness);
            }
        }
    }

    /**
     * Remove every child, for rebuilding a list.
     *
     * @return This container.
     */
    public S clear() {
        if (!children.isEmpty()) {
            children.forEach(child -> child.parent = null);
            children.clear();
            markDirty();
        }
        return self();
    }

    /**
     * Find a direct child by ID.
     *
     * @param childId The ID to look for.
     * @return The child, or null if there isn't one.
     */
    public Node<?> find(@NonNull String childId) {
        for (Node<?> child : children) {
            if (child.getId().equals(childId)) {
                return child;
            }
        }
        return null;
    }
}
