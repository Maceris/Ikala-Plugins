package com.ikalagaming.graphics.scene.debug;

/**
 * A wireframe shape drawn for debugging. Positions are in world space. Shapes are copied when they
 * are created, so changing the vectors passed in afterward doesn't change the shape.
 *
 * @see com.ikalagaming.graphics.DebugDraw
 */
public sealed interface DebugShape
        permits DebugArrow, DebugBox, DebugCone, DebugFrustum, DebugLine, DebugSphere {

    /**
     * The color, packed as RGBA with red in the highest byte, like {@link
     * com.ikalagaming.graphics.gui.util.Color#rgba(int, int, int, int)}.
     *
     * @return The packed color.
     */
    int color();

    /**
     * Whether the shape is drawn over everything, instead of being hidden behind the scene.
     *
     * @return True to skip the depth test.
     */
    boolean onTop();
}
