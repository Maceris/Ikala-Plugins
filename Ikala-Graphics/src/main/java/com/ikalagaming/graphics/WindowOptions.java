package com.ikalagaming.graphics;

import lombok.NonNull;

/**
 * How to create a {@link Window}.
 *
 * @param title The title of the window, shown in the title bar and task bar.
 * @param width The width in screen coordinates, or 0 to maximize the window on the primary monitor.
 * @param height The height in screen coordinates, or 0 to maximize the window on the primary
 *     monitor.
 * @param decorated Whether the window has a title bar and border from the OS.
 * @param resizable Whether the user can resize the window by dragging its border.
 * @param floating Whether the window stays on top of other windows.
 * @param focusOnShow Whether the window takes the input focus when it is shown.
 * @param icon Whether to give the window the program icon.
 */
public record WindowOptions(
        @NonNull String title,
        int width,
        int height,
        boolean decorated,
        boolean resizable,
        boolean floating,
        boolean focusOnShow,
        boolean icon) {

    /**
     * The options for the main window, which is decorated and resizable, and maximized unless the
     * settings ask for a size.
     *
     * @param title The title of the window.
     * @param settings The graphics settings, for the requested size.
     * @return The options for the main window.
     */
    public static WindowOptions mainWindow(
            @NonNull String title, @NonNull GraphicsSettings settings) {
        final boolean sized =
                settings.requestedWindowWidth > 0 && settings.requestedWindowHeight > 0;
        return new WindowOptions(
                title,
                sized ? settings.requestedWindowWidth : 0,
                sized ? settings.requestedWindowHeight : 0,
                true,
                true,
                false,
                true,
                true);
    }

    /**
     * Whether the window is maximized on the primary monitor instead of having a set size.
     *
     * @return True if the width or height is not set.
     */
    public boolean maximized() {
        return width <= 0 || height <= 0;
    }
}
