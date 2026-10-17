package com.ikalagaming.graphics;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * The {@value #COMMAND} console command, for trying out extra windows: {@code window open [width
 * height]} opens a blank one, {@code window close} closes every extra window, {@code window list}
 * lists them all, and {@code window scene <number|main>} draws the scene in another window.
 */
@Slf4j
final class WindowCommand {

    /** The name of the command. */
    static final String COMMAND = "window";

    /** The size of a window opened without one, in screen coordinates. */
    private static final int DEFAULT_WIDTH = 640;

    /** The height of a window opened without a size, in screen coordinates. */
    private static final int DEFAULT_HEIGHT = 360;

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
            case "scene" -> {
                final String which = args.size() < 2 ? "main" : args.get(1);
                GraphicsManager.runOnMainThread(() -> moveScene(which));
            }
            default ->
                    log.info(
                            "Usage: window [open [width height] | close | list | scene"
                                    + " <number|main>]");
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
