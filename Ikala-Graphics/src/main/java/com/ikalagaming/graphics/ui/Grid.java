package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.ListClipper;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A container that lays its children out in rows of cells, left to right and then top to bottom,
 * like an inventory. Cells are all the same size: a fixed size, or the size of the largest child.
 * The number of columns is fixed, or as many as fit the width.
 *
 * <p>Only the rows that can be seen are submitted to IkGui, through a {@link ListClipper}, which
 * also keeps keyboard and gamepad navigation working into rows that are scrolled away. Every child
 * is still laid out; for very large lists, use {@link VirtualGrid}, which only creates the nodes
 * that can be seen.
 */
public class Grid extends Container<Grid> {
    /** The number of columns, or 0 to fit as many as the width allows. */
    int columns;

    /** The width of a cell, or null to use the widest child. */
    Length cellWidth;

    /** The height of a cell, or null to use the tallest child. */
    Length cellHeight;

    /** Space between cells in both directions, or null to use the style's gap. */
    Length gap;

    /** Space between columns, overriding the gap, or null. */
    Length columnGap;

    /** Space between rows, overriding the gap, or null. */
    Length rowGap;

    /** The space between columns the last layout used. */
    Length resolvedColumnGap = Length.ZERO;

    /** The space between rows the last layout used. */
    Length resolvedRowGap = Length.ZERO;

    /** The number of columns the last layout used. */
    int resolvedColumns = 1;

    /** The cell width and height the last layout used, in pixels. */
    final float[] cellPixels = new float[2];

    /** The space between columns and rows the last layout used, in pixels. */
    final float[] gapPixels = new float[2];

    /**
     * Create a grid with as many columns as fit, sized to the largest child. It grows to fill its
     * parent's width, since fitted columns need a width to fit into; give it a fit width with fixed
     * columns to make it as wide as its columns.
     *
     * @param id The ID, which must be unique among its siblings.
     */
    public Grid(@NonNull String id) {
        super(id);
        width(Sizing.grow());
    }

    /**
     * Set the number of columns.
     *
     * @param count The number of columns, or 0 to fit as many as the width allows.
     * @return This grid.
     * @throws IllegalArgumentException If the count is negative.
     */
    public Grid columns(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("A grid can't have " + count + " columns");
        }
        columns = count;
        markDirty();
        return this;
    }

    /**
     * Give every cell a fixed size.
     *
     * @param width The cell width.
     * @param height The cell height.
     * @return This grid.
     */
    public Grid cellSize(@NonNull Length width, @NonNull Length height) {
        cellWidth = width;
        cellHeight = height;
        markDirty();
        return this;
    }

    /**
     * Give every cell a fixed size.
     *
     * @param width The cell width in UI units.
     * @param height The cell height in UI units.
     * @return This grid.
     */
    public Grid cellSize(float width, float height) {
        return cellSize(Length.u(width), Length.u(height));
    }

    /**
     * Size cells to the largest child again.
     *
     * @return This grid.
     */
    public Grid fitCells() {
        cellWidth = null;
        cellHeight = null;
        markDirty();
        return this;
    }

    /**
     * Set the space between cells, in both directions.
     *
     * @param length The gap.
     * @return This grid.
     */
    public Grid gap(@NonNull Length length) {
        gap = length;
        markDirty();
        return this;
    }

    /**
     * Set the space between cells, in both directions.
     *
     * @param units The gap in UI units.
     * @return This grid.
     */
    public Grid gap(float units) {
        return gap(Length.u(units));
    }

    /**
     * Set the space between columns, overriding the gap.
     *
     * @param length The gap.
     * @return This grid.
     */
    public Grid columnGap(@NonNull Length length) {
        columnGap = length;
        markDirty();
        return this;
    }

    /**
     * Set the space between rows, overriding the gap.
     *
     * @param length The gap.
     * @return This grid.
     */
    public Grid rowGap(@NonNull Length length) {
        rowGap = length;
        markDirty();
        return this;
    }

    /**
     * The number of columns the last layout used.
     *
     * @return The column count.
     */
    public int getResolvedColumns() {
        return resolvedColumns;
    }

    @Override
    public String styleType() {
        return "grid";
    }

    /**
     * The children that take part in layout, in order.
     *
     * @return The visible children.
     */
    List<Node<?>> shownChildren() {
        List<Node<?>> shown = new ArrayList<>(children.size());
        for (Node<?> child : children) {
            if (child.visible) {
                shown.add(child);
            }
        }
        return shown;
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        drawPanel(frame);
        List<Node<?>> shown = shownChildren();
        if (shown.isEmpty()) {
            return;
        }
        int columnCount = Math.max(1, resolvedColumns);
        int rows = (shown.size() + columnCount - 1) / columnCount;
        // Cells sit at the top left of their cell, so the first child's top is the first row's
        float top = shown.getFirst().rect.getTop();
        float pitch = cellPixels[1] + gapPixels[1];
        IkGui.setCursorScreenPos(rect.getLeft(), top);
        ListClipper clipper = new ListClipper();
        clipper.begin(rows, pitch);
        while (clipper.step()) {
            for (int row = clipper.displayStart; row < clipper.displayEnd; ++row) {
                for (int column = 0; column < columnCount; ++column) {
                    int index = row * columnCount + column;
                    if (index < shown.size()) {
                        shown.get(index).submitTree(frame);
                    }
                }
            }
        }
    }
}
