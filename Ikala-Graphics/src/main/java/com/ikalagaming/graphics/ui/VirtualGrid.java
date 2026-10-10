package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.data.ListClipper;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.flags.ChildFlags;
import com.ikalagaming.graphics.ui.style.StyleKey;

import lombok.NonNull;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A scrolling grid of cells that only creates the nodes it shows, for lists far too long to build
 * in full, like an asset browser. Cells come from a {@link Factory} by index, and are released
 * again once they are scrolled well out of view.
 *
 * <p>Every cell has the same fixed size, since cells that were never created can't be measured.
 * Columns are fixed, or as many as fit the width, like {@link Grid}. It is an IkGui child window,
 * so it scrolls, and keyboard and gamepad navigation moves into rows that aren't created yet.
 */
public class VirtualGrid extends Node<VirtualGrid> {
    /** Makes the nodes for cells, and is told when they are no longer needed. */
    public interface Factory {
        /**
         * Make the node for a cell. Called on the render thread, the first time the cell is shown.
         *
         * @param index The cell index, from 0.
         * @return The node, which must not have a parent.
         */
        Node<?> create(int index);

        /**
         * The node for a cell was dropped, because it scrolled out of view or the grid changed.
         *
         * @param index The cell index.
         * @param node The node.
         */
        default void release(int index, @NonNull Node<?> node) {}
    }

    /** How many rows beyond the shown ones keep their nodes, so scrolling back is cheap. */
    private static final int KEPT_ROWS = 4;

    /** The number of cells. */
    private int count;

    /** Makes cell nodes, or null for none. */
    private Factory factory;

    /** The number of columns, or 0 to fit as many as the width allows. */
    private int columns;

    /** The cell width. */
    private Length cellWidth = Length.u(64);

    /** The cell height. */
    private Length cellHeight = Length.u(64);

    /** Space between cells in both directions, or null to use the style's gap. */
    private Length gap;

    /** Whether to draw a border around the view. */
    private boolean border;

    /** The nodes made so far, by cell index. */
    private final Map<Integer, Node<?>> made = new HashMap<>();

    /** The number of columns the last frame used. */
    private int resolvedColumns = 1;

    /** The child window from the last frame it was open, or null. */
    private Window window;

    /** Where the first row starts in the child window when it isn't scrolled, in pixels. */
    private float contentTop;

    /** The distance from one row to the next in the last frame, in pixels. */
    private float rowPitch;

    /**
     * Create an empty virtual grid that grows to fill its parent.
     *
     * @param id The ID, which must be unique among its siblings.
     */
    public VirtualGrid(@NonNull String id) {
        super(id);
        width(Sizing.grow());
        height(Sizing.grow());
    }

    /**
     * Set what the grid shows, dropping every cell made so far.
     *
     * @param cellCount The number of cells.
     * @param cellFactory Makes the node for each cell.
     * @return This grid.
     */
    public VirtualGrid items(int cellCount, @NonNull Factory cellFactory) {
        releaseAll();
        factory = cellFactory;
        return count(cellCount);
    }

    /**
     * Change the number of cells. Cells that still exist keep their nodes.
     *
     * @param cellCount The number of cells.
     * @return This grid.
     * @throws IllegalArgumentException If the count is negative.
     */
    public VirtualGrid count(int cellCount) {
        if (cellCount < 0) {
            throw new IllegalArgumentException("A grid can't have " + cellCount + " cells");
        }
        count = cellCount;
        List<Integer> gone = new ArrayList<>();
        for (int index : made.keySet()) {
            if (index >= count) {
                gone.add(index);
            }
        }
        gone.forEach(this::release);
        return this;
    }

    /**
     * Drop every cell, so they are made again when shown, like after the data they show changed.
     *
     * @return This grid.
     */
    public VirtualGrid refresh() {
        releaseAll();
        return this;
    }

    /**
     * Set the number of columns.
     *
     * @param columnCount The number of columns, or 0 to fit as many as the width allows.
     * @return This grid.
     */
    public VirtualGrid columns(int columnCount) {
        if (columnCount < 0) {
            throw new IllegalArgumentException("A grid can't have " + columnCount + " columns");
        }
        columns = columnCount;
        return this;
    }

    /**
     * Set the size of every cell.
     *
     * @param width The cell width.
     * @param height The cell height.
     * @return This grid.
     */
    public VirtualGrid cellSize(@NonNull Length width, @NonNull Length height) {
        cellWidth = width;
        cellHeight = height;
        return this;
    }

    /**
     * Set the size of every cell.
     *
     * @param width The cell width in UI units.
     * @param height The cell height in UI units.
     * @return This grid.
     */
    public VirtualGrid cellSize(float width, float height) {
        return cellSize(Length.u(width), Length.u(height));
    }

    /**
     * Set the space between cells, in both directions.
     *
     * @param length The gap.
     * @return This grid.
     */
    public VirtualGrid gap(@NonNull Length length) {
        gap = length;
        return this;
    }

    /**
     * Draw a border around the view.
     *
     * @param show Whether to draw the border.
     * @return This grid.
     */
    public VirtualGrid border(boolean show) {
        border = show;
        return this;
    }

    /**
     * The number of cells.
     *
     * @return The count.
     */
    public int getCount() {
        return count;
    }

    /**
     * The number of columns the last frame used.
     *
     * @return The column count.
     */
    public int getResolvedColumns() {
        return resolvedColumns;
    }

    /**
     * The node made for a cell, if it is currently made. Render thread only.
     *
     * @param index The cell index.
     * @return The node, or null if the cell has no node right now.
     */
    public Node<?> getCell(int index) {
        return made.get(index);
    }

    @Override
    public List<Node<?>> getContentNodes() {
        // In index order, so searches go through cells the way they are shown
        return List.copyOf(new TreeMap<>(made).values());
    }

    @Override
    public boolean scrollIntoView(@NonNull Node<?> target) {
        for (Map.Entry<Integer, Node<?>> entry : made.entrySet()) {
            for (Node<?> node = target; node != null; node = node.parent) {
                if (node == entry.getValue()) {
                    return scrollToCell(entry.getKey());
                }
            }
        }
        return false;
    }

    /**
     * Scroll so a cell's row is in the middle of the view, from the next frame, which makes the
     * cell if it isn't made yet. Render thread only.
     *
     * @param index The cell index.
     * @return True if the grid has been shown and has that cell, so it will scroll.
     */
    public boolean scrollToCell(int index) {
        if (window == null || index < 0 || index >= count) {
            return false;
        }
        int row = index / resolvedColumns;
        float local = contentTop + row * rowPitch + rowPitch / 2 - window.scrollPosition.y;
        IkGuiInternal.setScrollFromPosY(window, local, 0.5f);
        return true;
    }

    /**
     * How many cells have nodes right now. Render thread only.
     *
     * @return The number of cells with nodes.
     */
    public int getMadeCount() {
        return made.size();
    }

    /** Drop every cell made so far, telling the factory. */
    public void releaseAll() {
        for (int index : new ArrayList<>(made.keySet())) {
            release(index);
        }
    }

    /**
     * Drop one cell's node.
     *
     * @param index The cell index.
     */
    private void release(int index) {
        Node<?> node = made.remove(index);
        if (node != null) {
            node.parent = null;
            if (factory != null) {
                factory.release(index, node);
            }
        }
    }

    /**
     * The node for a cell, making it if needed.
     *
     * @param index The cell index.
     * @return The node, or null if there is no factory or it made nothing.
     */
    private Node<?> cell(int index) {
        Node<?> node = made.get(index);
        if (node == null && factory != null) {
            node = factory.create(index);
            if (node == null) {
                return null;
            }
            if (node.parent != null) {
                throw new IllegalArgumentException(node + " is already in " + node.parent);
            }
            // Linked to the grid so it inherits its font, but not one of its children
            node.parent = this;
            made.put(index, node);
        }
        return node;
    }

    @Override
    public String styleType() {
        return "virtual-grid";
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        IkGuiStyler.Pushed pushed = IkGuiStyler.push(IkGuiStyler.Kind.CHILD, this, frame);
        IkGui.setCursorScreenPos(rect.getLeft(), rect.getTop());
        boolean open =
                IkGui.beginChild(
                        "virtual",
                        rect.getWidth(),
                        rect.getHeight(),
                        border ? ChildFlags.BORDERS : ChildFlags.NONE);
        pushed.pop();
        window = open ? IkGuiInternal.getCurrentWindow() : null;
        if (open && count > 0) {
            submitCells(frame);
        }
        IkGui.endChild();
    }

    /**
     * Submit the cells that can be seen, inside the child window, and drop the ones far from view.
     *
     * @param frame Details about the current frame.
     */
    private void submitCells(@NonNull UiFrame frame) {
        LayoutContext context = frame.context();
        float scale = context.scale();
        float width = cellWidth.resolve(0, scale, fontPixels);
        float height = cellHeight.resolve(0, scale, fontPixels);
        Length gapLength = gap != null ? gap : style.length(StyleKey.GAP);
        float space = gapLength == null ? 0 : gapLength.resolve(0, scale, fontPixels);

        // The cursor already includes the scroll offset
        Vector2f start = IkGui.getCursorScreenPos();
        Vector2f view = IkGui.getContentRegionAvailable();
        if (columns > 0) {
            resolvedColumns = columns;
        } else {
            float pitch = width + space;
            int fitting = pitch > 0 ? (int) Math.floor((view.x + space) / pitch) : 1;
            resolvedColumns = Math.clamp(fitting, 1, Math.max(1, count));
        }
        int rows = (count + resolvedColumns - 1) / resolvedColumns;
        float pitchX = width + space;
        float pitchY = height + space;
        rowPitch = pitchY;
        contentTop = start.y + window.scrollPosition.y - window.position.y;

        int firstRow = Integer.MAX_VALUE;
        int lastRow = -1;
        ListClipper clipper = new ListClipper();
        clipper.begin(rows, pitchY);
        while (clipper.step()) {
            for (int row = clipper.displayStart; row < clipper.displayEnd; ++row) {
                firstRow = Math.min(firstRow, row);
                lastRow = Math.max(lastRow, row);
                for (int column = 0; column < resolvedColumns; ++column) {
                    int index = row * resolvedColumns + column;
                    if (index >= count) {
                        break;
                    }
                    Node<?> node = cell(index);
                    if (node == null) {
                        continue;
                    }
                    float x = start.x + column * pitchX;
                    float y = start.y + row * pitchY;
                    frame.engine().layout(node, x, y, width, height, context);
                    // Cells can share IDs, so each is told apart by its index
                    IkGui.pushID(index);
                    node.submitTree(frame);
                    IkGui.popID();
                }
            }
        }
        // Make sure the scroll area covers every row, even the ones never submitted
        IkGui.setCursorScreenPos(start.x, start.y + rows * pitchY - space);
        IkGuiInternal.itemSize(0, 0);

        dropFarCells(firstRow, lastRow);
    }

    /**
     * Drop the nodes of cells well outside the rows that were shown.
     *
     * @param firstRow The first row shown, or {@link Integer#MAX_VALUE} if none were.
     * @param lastRow The last row shown, or -1 if none were.
     */
    private void dropFarCells(int firstRow, int lastRow) {
        List<Integer> far = new ArrayList<>();
        for (int index : made.keySet()) {
            int row = index / resolvedColumns;
            if (row < firstRow - KEPT_ROWS || row > lastRow + KEPT_ROWS) {
                far.add(index);
            }
        }
        far.forEach(this::release);
    }
}
