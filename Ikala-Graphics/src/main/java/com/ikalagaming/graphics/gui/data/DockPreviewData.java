package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.enums.Direction;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;

/** The results of calculating where a payload would be docked, used to preview docking. */
public class DockPreviewData {
    /** A tentative node, used to calculate the shape of the docking preview. */
    public final DockNode futureNode;

    public boolean isDropAllowed;
    public boolean isCenterAvailable;
    public boolean isSidesAvailable;

    /**
     * Set when hovering the drop rect, as opposed to an implicit split direction of none when
     * hovering the window.
     */
    public boolean isSplitDirectionExplicit;

    public DockNode splitNode;
    public @NonNull Direction splitDirection;
    public float splitRatio;

    /**
     * The drop rectangles to draw, indexed by direction + 1 (so the center is index 0). These may
     * be slightly different from the hit testing drop rects. Inverted rectangles are not drawn.
     */
    public final RectFloat[] dropRectsDraw;

    public DockPreviewData() {
        futureNode = new DockNode(0);
        isDropAllowed = false;
        isCenterAvailable = false;
        isSidesAvailable = false;
        isSplitDirectionExplicit = false;
        splitNode = null;
        splitDirection = Direction.NONE;
        splitRatio = 0.0f;
        dropRectsDraw = new RectFloat[5];
        for (int i = 0; i < dropRectsDraw.length; ++i) {
            dropRectsDraw[i] =
                    new RectFloat(
                            Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        }
    }
}
