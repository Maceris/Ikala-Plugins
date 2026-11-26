package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.gui.enums.Direction;
import com.ikalagaming.graphics.gui.flags.DockNodeFlags;

import lombok.NonNull;

import java.util.List;

/**
 * An area that {@link Panel}s can dock into, filling its rectangle. The middle is see-through and
 * lets the mouse through while nothing is docked there, so a 3D view behind the UI can show.
 *
 * <p>The first time it is drawn with no saved layout, and whenever {@link #resetLayout()} is
 * called, the panels are docked as its {@link DockLayout} says. After that IkGui keeps the user's
 * layout, and saves it with the other window settings.
 */
public class DockSpace extends Node<DockSpace> {
    /** The layout to start from. */
    private final DockLayout layout;

    /** Whether to rebuild the layout the next time it is drawn. */
    private boolean resetRequested;

    /**
     * Create a dock space that grows to fill its parent.
     *
     * @param id The ID, unique among its siblings, which the saved layout is kept under.
     * @param layout Where panels go before the user moves them.
     */
    public DockSpace(@NonNull String id, @NonNull DockLayout layout) {
        super(id);
        this.layout = layout;
        width(Sizing.grow());
        height(Sizing.grow());
    }

    /** Put every panel back where the layout says, the next time the dock space is drawn. */
    public void resetLayout() {
        resetRequested = true;
    }

    @Override
    public String styleType() {
        return "dock-space";
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        final int dockID = IkGui.getID("dock");
        final float width = Math.max(1, rect.getWidth());
        final float height = Math.max(1, rect.getHeight());
        if (resetRequested || IkGuiInternal.dockBuilderGetNode(dockID) == null) {
            resetRequested = false;
            build(dockID, width, height);
        }
        IkGui.setCursorScreenPos(rect.getLeft(), rect.getTop());
        IkGui.dockSpace(dockID, width, height, DockNodeFlags.PASSTHROUGH_CENTRAL_NODE);
    }

    /**
     * Make the layout's nodes and dock its panels into them.
     *
     * @param dockID The dock space's ID.
     * @param width The dock space's width, in pixels.
     * @param height The dock space's height, in pixels.
     */
    private void build(int dockID, float width, float height) {
        IkGuiInternal.dockBuilderRemoveNode(dockID);
        IkGuiInternal.dockBuilderAddNode(dockID, DockNodeFlags.INTERNAL_DOCK_SPACE);
        IkGuiInternal.dockBuilderSetNodePos(dockID, rect.getLeft(), rect.getTop());
        IkGuiInternal.dockBuilderSetNodeSize(dockID, width, height);
        // Every node is made first and the panels docked after, so splitting a node never has to
        // decide where panels already in it go
        final List<DockLayout.Split> splits = layout.getSplits();
        final int[] nodes = new int[splits.size()];
        final IkInt middle = new IkInt(dockID);
        final IkInt taken = new IkInt(0);
        final IkInt rest = new IkInt(0);
        for (int i = 0; i < nodes.length; ++i) {
            final DockLayout.Split split = splits.get(i);
            if (split.ofPrevious()) {
                IkGuiInternal.dockBuilderSplitNode(
                        nodes[i - 1], direction(split.side()), split.fraction(), taken, rest);
                nodes[i - 1] = rest.get();
            } else {
                IkGuiInternal.dockBuilderSplitNode(
                        middle.get(), direction(split.side()), split.fraction(), taken, middle);
            }
            nodes[i] = taken.get();
        }
        for (int i = 0; i < nodes.length; ++i) {
            dock(splits.get(i).panels(), nodes[i]);
        }
        dock(layout.getCenter(), middle.get());
        IkGuiInternal.dockBuilderFinish(dockID);
    }

    /**
     * Dock panels into a node.
     *
     * @param panelIDs The panels' IDs.
     * @param nodeID The dock node.
     */
    private static void dock(@NonNull Iterable<String> panelIDs, int nodeID) {
        for (String panelID : panelIDs) {
            IkGuiInternal.dockBuilderDockWindow(Panel.windowID(panelID), nodeID);
        }
    }

    /**
     * The dock builder's direction for a side.
     *
     * @param side The side.
     * @return The direction.
     */
    private static Direction direction(@NonNull DockLayout.Side side) {
        return switch (side) {
            case LEFT -> Direction.LEFT;
            case RIGHT -> Direction.RIGHT;
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }
}
