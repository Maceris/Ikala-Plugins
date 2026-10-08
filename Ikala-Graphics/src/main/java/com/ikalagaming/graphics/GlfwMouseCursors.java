package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import com.ikalagaming.graphics.frontend.gui.enums.MouseCursor;

import lombok.NonNull;
import org.lwjgl.glfw.GLFWErrorCallback;

/**
 * The GLFW mouse cursors for IkGui, which show the shape IkGui asks for with getMouseCursor(). This
 * is the mouse cursor part of Dear ImGui's GLFW backend.
 */
class GlfwMouseCursors {
    /** The GLFW calls used to change the cursor, so the logic can be tested without a window. */
    interface CursorTarget {
        /**
         * Fetch the GLFW cursor mode of the window.
         *
         * @return GLFW_CURSOR_NORMAL, GLFW_CURSOR_HIDDEN or GLFW_CURSOR_DISABLED.
         */
        int getCursorMode();

        /**
         * Set the GLFW cursor mode of the window.
         *
         * @param mode GLFW_CURSOR_NORMAL or GLFW_CURSOR_HIDDEN.
         */
        void setCursorMode(int mode);

        /**
         * Set the cursor shape of the window.
         *
         * @param cursor The GLFW cursor handle.
         */
        void setCursor(long cursor);
    }

    /** The GLFW cursor for each IkGui cursor, indexed by ordinal. NULL for NONE. */
    private final long[] cursors;

    /** The last cursor we set, or NULL if the cursor is hidden or needs to be set again. */
    private long lastCursor;

    /**
     * Use the given cursor handles, mostly for testing.
     *
     * @param cursors The GLFW cursor for each IkGui cursor, indexed by ordinal, NULL when missing.
     */
    GlfwMouseCursors(long @NonNull [] cursors) {
        this.cursors = cursors.clone();
        lastCursor = NULL;
    }

    /**
     * Create the standard GLFW cursors. GLFW must be initialized. Cursors the platform doesn't have
     * are left out, and the arrow is shown instead.
     *
     * @return The cursors.
     */
    static GlfwMouseCursors createStandardCursors() {
        final long[] cursors = new long[MouseCursor.values().length];
        // Missing cursors report an error, which we don't want to log since we fall back to the
        // arrow
        final GLFWErrorCallback previousCallback = glfwSetErrorCallback(null);
        cursors[MouseCursor.ARROW.ordinal()] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        cursors[MouseCursor.TEXT_INPUT.ordinal()] = glfwCreateStandardCursor(GLFW_IBEAM_CURSOR);
        cursors[MouseCursor.RESIZE_NS.ordinal()] = glfwCreateStandardCursor(GLFW_VRESIZE_CURSOR);
        cursors[MouseCursor.RESIZE_EW.ordinal()] = glfwCreateStandardCursor(GLFW_HRESIZE_CURSOR);
        cursors[MouseCursor.HAND.ordinal()] = glfwCreateStandardCursor(GLFW_HAND_CURSOR);
        cursors[MouseCursor.RESIZE_ALL.ordinal()] =
                glfwCreateStandardCursor(GLFW_RESIZE_ALL_CURSOR);
        cursors[MouseCursor.RESIZE_NE_SW.ordinal()] =
                glfwCreateStandardCursor(GLFW_RESIZE_NESW_CURSOR);
        cursors[MouseCursor.RESIZE_NW_SE.ordinal()] =
                glfwCreateStandardCursor(GLFW_RESIZE_NWSE_CURSOR);
        cursors[MouseCursor.NOT_ALLOWED.ordinal()] =
                glfwCreateStandardCursor(GLFW_NOT_ALLOWED_CURSOR);
        glfwSetErrorCallback(previousCallback);
        return new GlfwMouseCursors(cursors);
    }

    /** Destroy the GLFW cursors. */
    void destroy() {
        for (int i = 0; i < cursors.length; ++i) {
            if (cursors[i] != NULL) {
                glfwDestroyCursor(cursors[i]);
                cursors[i] = NULL;
            }
        }
        lastCursor = NULL;
    }

    /** Forget the last cursor, so it is set again next time, e.g. after the window changed. */
    void invalidate() {
        lastCursor = NULL;
    }

    /**
     * Show the cursor shape IkGui wants. Call this once per frame, before IkGui.newFrame(). Nothing
     * is changed when the cursor is disabled (e.g. captured for a camera) or when cursor changes
     * are turned off, so code that changes the cursor itself isn't overridden.
     *
     * @param target The window to change the cursor of.
     * @param cursor The cursor IkGui wants, from getMouseCursor().
     * @param noCursorChange Whether ConfigFlags.NO_MOUSE_CURSOR_CHANGE is set.
     * @param drawCursor Whether io.configMouseDrawCursor is set, so IkGui draws the cursor itself.
     */
    void update(
            @NonNull CursorTarget target,
            @NonNull MouseCursor cursor,
            boolean noCursorChange,
            boolean drawCursor) {
        if (noCursorChange || target.getCursorMode() == GLFW_CURSOR_DISABLED) {
            // Invalidate, so if the user changes the cursor we update it next time we can
            lastCursor = NULL;
            return;
        }

        if (cursor == MouseCursor.NONE || drawCursor) {
            // Hide the OS cursor if IkGui is drawing it or if it wants no cursor
            if (lastCursor != NULL || target.getCursorMode() != GLFW_CURSOR_HIDDEN) {
                target.setCursorMode(GLFW_CURSOR_HIDDEN);
                lastCursor = NULL;
            }
            return;
        }

        final long wanted =
                cursors[cursor.ordinal()] != NULL
                        ? cursors[cursor.ordinal()]
                        : cursors[MouseCursor.ARROW.ordinal()];
        if (wanted == lastCursor && target.getCursorMode() == GLFW_CURSOR_NORMAL) {
            return;
        }
        lastCursor = wanted;
        if (wanted != NULL) {
            target.setCursor(wanted);
        }
        target.setCursorMode(GLFW_CURSOR_NORMAL);
    }
}
