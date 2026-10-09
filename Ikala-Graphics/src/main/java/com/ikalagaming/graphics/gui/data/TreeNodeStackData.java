package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.util.RectFloat;

/**
 * Data stored for a tree node when it is pushed, used for TreeNodeFlags.NAV_LEFT_JUMPS_TO_PARENT
 * and drawing tree lines.
 */
public class TreeNodeStackData {
    /** The ID of the tree node. */
    public int id;

    /**
     * The tree node flags.
     *
     * @see com.ikalagaming.graphics.gui.flags.TreeNodeFlags
     */
    public int treeNodeFlags;

    /**
     * The item flags of the tree node.
     *
     * @see com.ikalagaming.graphics.gui.flags.ItemFlags
     */
    public int itemFlags;

    /** The navigation rectangle of the tree node. */
    public final RectFloat navRect = new RectFloat(0, 0, 0, 0);

    public float drawLinesX1;
    public float drawLinesToNodeY2;
    public int drawLinesTableColumn;
}
