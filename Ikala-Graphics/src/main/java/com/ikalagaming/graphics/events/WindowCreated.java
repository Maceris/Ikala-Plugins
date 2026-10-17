package com.ikalagaming.graphics.events;

import com.ikalagaming.event.Event;

/**
 * Fired off when a new GLFW window has been created: the main window, windows made with {@link
 * com.ikalagaming.graphics.GraphicsManager#createWindow}, and the windows IkGui makes for its
 * viewports.
 *
 * @author Ches Burks
 */
public class WindowCreated extends Event {

    private final long windowID;

    /**
     * Constructs a new window created event.
     *
     * @param id The window handle.
     */
    public WindowCreated(final long id) {
        windowID = id;
    }

    /**
     * Returns the window handle.
     *
     * @return the GLFW window ID.
     */
    public long getID() {
        return windowID;
    }
}
