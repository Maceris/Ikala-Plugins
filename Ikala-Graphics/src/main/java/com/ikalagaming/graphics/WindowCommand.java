package com.ikalagaming.graphics;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.WindowFlags;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * The {@value #COMMAND} console command, for trying out extra windows: {@code window open [width
 * height]} opens a blank one, {@code window close} closes every extra window, {@code window list}
 * lists them all, {@code window scene <number|main>} draws the scene in another window, and {@code
 * window viewport} shows or hides a GUI window placed half outside the main window, which gets a
 * window of its own when IkGui viewports are on.
 */
@Slf4j
final class WindowCommand {

    /** The name of the command. */
    static final String COMMAND = "window";

    /** The size of a window opened without one, in screen coordinates. */
    private static final int DEFAULT_WIDTH = 640;

    /** The height of a window opened without a size, in screen coordinates. */
    private static final int DEFAULT_HEIGHT = 360;

    /** The name the viewport test window is shown under. */
    private static final String VIEWPORT_TEST_WINDOW = "graphics/viewport-test";

    /**
     * A GUI window that starts out over the right edge of the main window, so it can't be merged
     * into the main viewport. Unlike other GUI windows, it isn't kept in the main viewport.
     */
    private static final class ViewportTestWindow extends GuiWindow {
        /** Create the window. */
        ViewportTestWindow() {
            super("Viewport test", WindowFlags.NONE);
        }

        @Override
        public void draw(final int width, final int height) {
            final Viewport main = IkGui.getMainViewport();
            IkGui.setNextWindowPos(
                    main.position.x + main.size.x - 150,
                    main.position.y + 120,
                    Condition.APPEARING);
            IkGui.setNextWindowSize(320, 160, Condition.APPEARING);
            if (IkGui.begin(title, windowOpen, windowFlags)) {
                final Viewport viewport = IkGui.getWindowViewport();
                IkGui.text("This window can leave the main window.");
                IkGui.text(
                        viewport == main
                                ? "It is in the main viewport."
                                : "It has a viewport of its own.");
            }
            IkGui.end();
        }
    }

    /** Counts opened windows, to give each a different title. */
    private static int opened = 0;

    /**
     * Run the command. The console runs it on its own thread, so the work happens on the main
     * thread.
     *
     * @param args The arguments after the command name.
     */
    static void run(@NonNull List<String> args) {
        final String action = args.isEmpty() ? "open" : args.getFirst();
        switch (action) {
            case "open" -> {
                final int[] size = parseSize(args);
                if (size != null) {
                    GraphicsManager.runOnMainThread(() -> open(size[0], size[1]));
                }
            }
            case "close" -> GraphicsManager.runOnMainThread(WindowCommand::closeExtraWindows);
            case "list" -> GraphicsManager.runOnMainThread(WindowCommand::list);
            case "viewport" -> GraphicsManager.runOnMainThread(WindowCommand::toggleViewportTest);
            case "scene" -> {
                final String which = args.size() < 2 ? "main" : args.get(1);
                GraphicsManager.runOnMainThread(() -> moveScene(which));
            }
            default ->
                    log.info(
                            "Usage: window [open [width height] | close | list | scene"
                                    + " <number|main> | viewport]");
        }
    }

    /**
     * Read the window size from the arguments.
     *
     * @param args The arguments, with the action first.
     * @return The width and height, or null if they aren't valid.
     */
    private static int[] parseSize(@NonNull List<String> args) {
        if (args.size() < 3) {
            return new int[] {DEFAULT_WIDTH, DEFAULT_HEIGHT};
        }
        try {
            final int width = Integer.parseInt(args.get(1));
            final int height = Integer.parseInt(args.get(2));
            if (width > 0 && height > 0) {
                return new int[] {width, height};
            }
        } catch (NumberFormatException e) {
            // Logged below
        }
        log.info("The window size must be two positive whole numbers");
        return null;
    }

    /**
     * Open a blank window. Main thread.
     *
     * @param width The width in screen coordinates.
     * @param height The height in screen coordinates.
     */
    private static void open(int width, int height) {
        opened += 1;
        Window window =
                GraphicsManager.createWindow(
                        WindowOptions.plain("Window " + opened, width, height));
        log.info("Opened {} ({}x{})", window.getTitle(), window.getWidth(), window.getHeight());
    }

    /** Close every window except the main one. Main thread. */
    private static void closeExtraWindows() {
        final List<Window> windows = GraphicsManager.getWindows();
        for (int i = 1; i < windows.size(); ++i) {
            GraphicsManager.closeWindow(windows.get(i));
        }
        log.info("Closed {} windows", Math.max(0, windows.size() - 1));
    }

    /**
     * Draw the scene in another window. Main thread.
     *
     * @param which The number in the window's title, or main for the main window.
     */
    private static void moveScene(@NonNull String which) {
        final String title = "main".equals(which) ? null : "Window " + which;
        for (Window window : GraphicsManager.getWindows()) {
            final boolean match =
                    title == null
                            ? window == GraphicsManager.getMainWindow()
                            : title.equals(window.getTitle());
            if (match) {
                GraphicsManager.setSceneWindow(window);
                log.info("Drawing the scene in {}", window.getTitle());
                return;
            }
        }
        log.info("There is no open window {}", which);
    }

    /** Show the viewport test window, or hide it if it is showing. Main thread. */
    private static void toggleViewportTest() {
        final var windows = GraphicsManager.getWindowManager();
        if (windows.isVisible(VIEWPORT_TEST_WINDOW)) {
            windows.removeWindow(VIEWPORT_TEST_WINDOW);
            log.info("Hid the viewport test window");
            return;
        }
        final ViewportTestWindow window = new ViewportTestWindow();
        windows.addWindow(
                GraphicsManager.forPlugin(GraphicsPlugin.PLUGIN_NAME),
                VIEWPORT_TEST_WINDOW,
                window);
        windows.show(VIEWPORT_TEST_WINDOW);
        log.info("Showed the viewport test window");
    }

    /** Log every open window. Main thread. */
    private static void list() {
        for (Window window : GraphicsManager.getWindows()) {
            log.info(
                    "{}{}{} ({}x{})",
                    window.getTitle(),
                    window == GraphicsManager.getMainWindow() ? " (main)" : "",
                    window == GraphicsManager.getSceneWindow() ? " (scene)" : "",
                    window.getWidth(),
                    window.getHeight());
        }
    }

    /** Private constructor so this class is not initialized. */
    private WindowCommand() {}
}
