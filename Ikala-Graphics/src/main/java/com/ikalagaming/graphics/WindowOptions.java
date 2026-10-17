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
 * @param guiInput Whether the window's mouse, keyboard and focus go to IkGui. With IkGui viewports
 *     on, the windows IkGui creates send theirs too.
 * @param visible Whether to show the window as soon as it is created. Hidden windows are shown with
 *     {@link Window#show(boolean)} once they are ready.
 */
public record WindowOptions(
        @NonNull String title,
        int width,
        int height,
        boolean decorated,
        boolean resizable,
        boolean floating,
        boolean focusOnShow,
        boolean icon,
        boolean guiInput,
        boolean visible) {

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
                true,
                true,
                true);
    }

    /**
     * The options for a plain extra window, decorated and resizable, whose input doesn't go to
     * IkGui.
     *
     * @param title The title of the window.
     * @param width The width in screen coordinates.
     * @param height The height in screen coordinates.
     * @return The options for the window.
     */
    public static WindowOptions plain(@NonNull String title, int width, int height) {
        return new WindowOptions(title, width, height, true, true, false, true, true, false, true);
    }

    /**
     * The options for a window IkGui creates for a viewport, which starts hidden, sends its input
     * to IkGui, and doesn't take the focus when shown.
     *
     * @param width The width in screen coordinates.
     * @param height The height in screen coordinates.
     * @param decorated Whether it gets a title bar and border from the OS.
     * @param floating Whether it stays on top of other windows.
     * @return The options for the window.
     */
    public static WindowOptions guiViewport(
            int width, int height, boolean decorated, boolean floating) {
        return new WindowOptions(
                "Untitled", width, height, decorated, true, floating, false, false, true, false);
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
