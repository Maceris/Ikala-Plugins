package com.ikalagaming.graphics.events;

import com.ikalagaming.event.Event;

/**
 * Fired off when a GLFW window is about to be destroyed while graphics keep running, like an extra
 * window being closed or IkGui closing a viewport's window. Not fired as graphics shut down. The
 * handle can't be used once listeners see it, since the window is destroyed right after.
 *
 * @author Ches Burks
 */
public class WindowDestroyed extends Event {

    private final long windowID;

    /**
     * Constructs a new window destroyed event.
     *
     * @param id The window handle.
     */
    public WindowDestroyed(final long id) {
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
