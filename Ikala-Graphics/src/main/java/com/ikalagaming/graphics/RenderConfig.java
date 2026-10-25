package com.ikalagaming.graphics;

import lombok.With;

/**
 * Which parts of the frame the renderer draws. The renderer builds and keeps one pipeline per
 * configuration, so switching back and forth is cheap.
 *
 * <p>Filters and debug shapes are drawn on the world, so without it they draw nothing. How much the
 * world costs, like shadow quality, is up to {@link GraphicsSettings}, and debug views like
 * wireframe are in {@link com.ikalagaming.graphics.scene.debug.DebugVisualizers}.
 *
 * <pre>
 * GraphicsManager.swapPipeline(RenderConfig.WORLD);
 * GraphicsManager.swapPipeline(GraphicsManager.getPipelineConfig().withDebug(true));
 * </pre>
 *
 * @param world Whether to draw the world: everything placed in the scene, lit by its lights and
 *     sky, with shadows, under the sky.
 * @param filters Whether to run the post filters over the world.
 * @param debug Whether to draw debug shapes over the world, depth tested against it.
 * @param gui Whether to draw the GUI.
 */
@With
public record RenderConfig(boolean world, boolean filters, boolean debug, boolean gui) {
    /** Draw nothing. */
    public static final RenderConfig NONE = new RenderConfig(false, false, false, false);

    /** Only the GUI, as on a menu with nothing behind it. */
    public static final RenderConfig GUI = NONE.withGui(true);

    /** The world with the GUI over it, as in game. */
    public static final RenderConfig WORLD = GUI.withWorld(true);

    /**
     * Whether filters actually run, which needs the world.
     *
     * @return True if the filter stage runs.
     */
    public boolean runsFilters() {
        return world && filters;
    }

    /**
     * Whether debug shapes are actually drawn, which needs the world.
     *
     * @return True if the debug stage runs.
     */
    public boolean drawsDebug() {
        return world && debug;
    }
}
