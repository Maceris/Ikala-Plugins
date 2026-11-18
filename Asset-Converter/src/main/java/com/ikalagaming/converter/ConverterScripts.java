package com.ikalagaming.converter;

import com.ikalagaming.converter.gui.window.MainMenu;
import com.ikalagaming.converter.inspector.AssetInspectorWindow;
import com.ikalagaming.converter.workbench.Workbench;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.vulkan.FrameTimings;
import com.ikalagaming.plugins.PluginInfo;
import com.ikalagaming.plugins.PluginManager;

import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.util.function.Consumer;

/**
 * What the converter's scripts can call, through the global {@code converter}, like {@code
 * converter.showInspector()}. Static methods only, so the global is the class and scripts hold no
 * plugin objects.
 *
 * <p>Scripts run on their own thread, so what changes the scene or the UI is posted to the render
 * thread, and done a frame or so later; scripts wait on what they need to see with {@code
 * ui.test()} steps.
 */
@Slf4j
public final class ConverterScripts {
    /** The name scripts use. */
    public static final String GLOBAL = "converter";

    /**
     * The converter's version.
     *
     * @return The version from its plugin.yml, or an empty string if it isn't loaded.
     */
    public static String version() {
        return PluginManager.getInstance()
                .getInfo(ConverterPlugin.PLUGIN_NAME)
                .map(PluginInfo::getVersion)
                .orElse("");
    }

    /** Show the asset inspector. */
    public static void showInspector() {
        GraphicsManager.forPlugin(ConverterPlugin.PLUGIN_NAME)
                .ui()
                .setVisible(AssetInspectorWindow.SURFACE_ID, true);
    }

    /**
     * Show or hide the main menu.
     *
     * @param visible Whether to show it.
     */
    public static void showMenu(boolean visible) {
        GraphicsManager.forPlugin(ConverterPlugin.PLUGIN_NAME)
                .ui()
                .setVisible(MainMenu.SURFACE_ID, visible);
    }

    /**
     * Write a line to the log, for checks to report what they found.
     *
     * @param message The line, in plain ASCII.
     */
    public static void log(String message) {
        log.info("Script: {}", message);
    }

    /** Bytes in a kibibyte. */
    private static final double BYTES_PER_KIB = 1024.0;

    /** The render thread's allocated bytes at the last {@link #logAllocation}, -1 before any. */
    private static long lastAllocated = -1;

    /** The frame number at the last {@link #logAllocation}. */
    private static long lastFrame;

    /**
     * Write how much the render thread allocated per frame since the last call to the log, for
     * checks that look for per-frame garbage. The first call only starts counting.
     *
     * @param label What the line is about, in plain ASCII.
     */
    public static void logAllocation(String label) {
        final var renderer = GraphicsManager.getRenderInstance();
        if (renderer == null
                || !(ManagementFactory.getThreadMXBean()
                        instanceof com.sun.management.ThreadMXBean threads)) {
            log.info("Allocation {}: none", label);
            return;
        }
        // The render thread is the one that started the app
        long renderThread = -1;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if ("main".equals(thread.getName())) {
                renderThread = thread.threadId();
            }
        }
        final long allocated = threads.getThreadAllocatedBytes(renderThread);
        final long frame = renderer.getState().frameNumber;
        if (lastAllocated >= 0 && frame > lastFrame) {
            log.info(
                    "Allocation {}: {} KiB per frame over {} frames",
                    label,
                    String.format(
                            "%.2f",
                            (allocated - lastAllocated) / BYTES_PER_KIB / (frame - lastFrame)),
                    frame - lastFrame);
        } else {
            log.info("Allocation {}: counting from frame {}", label, frame);
        }
        lastAllocated = allocated;
        lastFrame = frame;
    }

    /**
     * Write how long each part of a frame takes to the log, averaged over the last couple of
     * seconds, for checks that measure.
     *
     * @param label What the line is about, in plain ASCII.
     */
    public static void logTimings(String label) {
        final var renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().frameTimings == null) {
            log.info("Timings {}: none", label);
            return;
        }
        final StringBuilder line = new StringBuilder();
        for (FrameTimings.Timing timing : renderer.getState().frameTimings.snapshot()) {
            line.append(
                    String.format(
                            "%n    %-28s %8.3f ms avg %8.3f ms p95 %8.3f ms max",
                            timing.name(), timing.average(), timing.p95(), timing.max()));
        }
        log.info("Timings {}:{}", label, line);
    }

    /**
     * Open the workbench on a file, or on a preview shape named as "preview:sphere", "preview:cube"
     * or "preview:plane", hiding the main menu.
     *
     * @param what The file or preview.
     */
    public static void openWorkbench(String what) {
        onWorkbench(
                workbench -> {
                    showMenu(false);
                    workbench.setVisible(true);
                    workbench.openNamed(what);
                });
    }

    /**
     * Open a tool another plugin offers, as the main menu's button for it does, for checks that
     * can't click the menu.
     *
     * @param id The tool's ID, like "characters/editor".
     */
    public static void openTool(String id) {
        final var ui = graphics().ui();
        ui.post(
                () -> {
                    final var tool =
                            ui.tools().get().stream()
                                    .filter(offered -> offered.id().equals(id))
                                    .findFirst();
                    if (tool.isEmpty()) {
                        log.warn("Script: no tool {}", id);
                        return;
                    }
                    showMenu(false);
                    tool.get().open().accept(() -> showMenu(true));
                });
    }

    /**
     * Put the camera somewhere, for checks to look at a scene from a known place. The scene view's
     * orbit follows it.
     *
     * @param x Where it is.
     * @param y Where it is.
     * @param z Where it is.
     * @param pitch How far it looks down, in radians.
     * @param yaw How far it turns, in radians.
     */
    public static void placeCamera(double x, double y, double z, double pitch, double yaw) {
        graphics()
                .ui()
                .post(
                        () -> {
                            final var camera = GraphicsManager.getScene().getCamera();
                            camera.setPosition(x, y, z);
                            camera.setRotation((float) pitch, (float) yaw);
                        });
    }

    /**
     * Set how small a coverage volume must be on screen to be drawn as its shell, for checks that
     * compare shells with volumes.
     *
     * @param pixels The sphere's radius on screen, in pixels; 0 never draws shells.
     */
    public static void setShellPixels(double pixels) {
        graphics().ui().post(() -> GraphicsManager.getSettings().lodShellPixels = (float) pixels);
    }

    /**
     * Move the camera by some amount, for checks that fly through a scene a frame at a time.
     *
     * @param dx How far along x.
     * @param dy How far along y.
     * @param dz How far along z.
     */
    public static void moveCamera(double dx, double dy, double dz) {
        graphics()
                .ui()
                .post(
                        () -> {
                            final var camera = GraphicsManager.getScene().getCamera();
                            final var at = camera.getPosition();
                            camera.setPosition(at.x + dx, at.y + dy, at.z + dz);
                        });
    }

    /**
     * Run something with the workbench on the render thread, if the plugin is enabled.
     *
     * @param action What to do.
     */
    private static void onWorkbench(Consumer<Workbench> action) {
        graphics()
                .ui()
                .post(
                        () ->
                                PluginManager.getInstance()
                                        .getPlugin(ConverterPlugin.PLUGIN_NAME)
                                        .filter(ConverterPlugin.class::isInstance)
                                        .map(plugin -> ((ConverterPlugin) plugin).getWorkbench())
                                        .ifPresent(action));
    }

    /**
     * The converter's graphics context.
     *
     * @return The context.
     */
    private static GraphicsContext graphics() {
        return GraphicsManager.forPlugin(ConverterPlugin.PLUGIN_NAME);
    }

    /** Static methods only. */
    private ConverterScripts() {}
}
