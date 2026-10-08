package com.ikalagaming.graphics.frontend.gui.data;

/**
 * Simple column measurement, used for menu items (icon, label, shortcut, check mark) so that all
 * items in a menu line up. Widths are fed back from one frame to the next.
 */
public class MenuColumns {
    /** The index of the icon column. */
    private static final int ICON = 0;

    /** The index of the label column. */
    private static final int LABEL = 1;

    /** The index of the shortcut column. */
    private static final int SHORTCUT = 2;

    /** The index of the check mark (or sub-menu arrow) column. */
    private static final int MARK = 3;

    /** The total width from the previous frame. */
    public float totalWidth;

    /** The total width being accumulated during the current frame. */
    public float nextTotalWidth;

    /** The spacing between columns. */
    public float spacing;

    /** Offset of the icon column, always 0 for now. */
    public float offsetIcon;

    /** Offset of the label column. */
    public float offsetLabel;

    /** Offset of the shortcut column. */
    public float offsetShortcut;

    /** Offset of the check mark column. */
    public float offsetMark;

    /** Width of each column: icon, label, shortcut, mark. */
    private final float[] widths;

    public MenuColumns() {
        widths = new float[4];
    }

    /**
     * Called at the start of the window each frame, to lock in the previous frames widths.
     *
     * @param spacing The spacing between columns.
     * @param windowReappearing If the window just appeared, in which case old widths are reset.
     */
    public void update(float spacing, boolean windowReappearing) {
        if (windowReappearing) {
            clearWidths();
        }
        this.spacing = (int) spacing;
        calcNextTotalWidth(true);
        clearWidths();
        totalWidth = nextTotalWidth;
        nextTotalWidth = 0;
    }

    /**
     * Declare the widths of the current item, and return the width needed by the menu.
     *
     * @param iconWidth The width of the icon.
     * @param labelWidth The width of the label.
     * @param shortcutWidth The width of the shortcut text.
     * @param markWidth The width of the check mark or arrow.
     * @return The total width the menu needs.
     */
    public float declColumns(
            float iconWidth, float labelWidth, float shortcutWidth, float markWidth) {
        widths[ICON] = Math.max(widths[ICON], (int) iconWidth);
        widths[LABEL] = Math.max(widths[LABEL], (int) labelWidth);
        widths[SHORTCUT] = Math.max(widths[SHORTCUT], (int) shortcutWidth);
        widths[MARK] = Math.max(widths[MARK], (int) markWidth);
        calcNextTotalWidth(false);
        return Math.max(totalWidth, nextTotalWidth);
    }

    /**
     * Calculate the total width of the columns, optionally updating the offsets of each column.
     *
     * @param updateOffsets Whether to update the column offsets.
     */
    private void calcNextTotalWidth(boolean updateOffsets) {
        float offset = 0;
        boolean wantSpacing = false;
        for (int i = 0; i < widths.length; ++i) {
            final float width = widths[i];
            if (wantSpacing && width > 0) {
                offset += spacing;
            }
            wantSpacing |= width > 0;
            if (updateOffsets) {
                switch (i) {
                    case LABEL -> offsetLabel = offset;
                    case SHORTCUT -> offsetShortcut = offset;
                    case MARK -> offsetMark = offset;
                    default -> offsetIcon = 0;
                }
            }
            offset += width;
        }
        nextTotalWidth = offset;
    }

    /** Reset all the column widths to 0. */
    private void clearWidths() {
        for (int i = 0; i < widths.length; ++i) {
            widths[i] = 0;
        }
    }
}
