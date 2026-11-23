package com.ikalagaming.graphics;

import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.windows.GraphicsDebug;
import com.ikalagaming.graphics.input.InputAction;
import com.ikalagaming.graphics.input.KeyBinding;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;

import java.util.function.BooleanSupplier;

/**
 * The Graphics plugin's own part of the debug overlay: the key that toggles it, and a Graphics
 * section with the frame rate, the Graphics Debug window and the most used visualizers.
 */
final class GraphicsOverlay {

    /** The Graphics section's ID. */
    static final String SECTION = "graphics:graphics";

    /** Where the Graphics section goes, after the game's sections. */
    static final int ORDER = 900;

    /** Not made. */
    private GraphicsOverlay() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * Register the toggle key and add the Graphics section.
     *
     * @param graphics The Graphics plugin's own graphics.
     */
    static void install(@NonNull GraphicsContext graphics) {
        graphics.registerAction(
                new InputAction(
                        GraphicsManager.DEBUG_OVERLAY_ACTION,
                        text("CONTROLS_CATEGORY_DEBUG"),
                        text("CONTROLS_DEBUG_OVERLAY"),
                        KeyBinding.of(Key.F3)));
        final DebugVisualizers visualizers = GraphicsManager.getScene().getDebugVisualizers();
        graphics.debugSection(SECTION, "Graphics", ORDER, false)
                .line(
                        "fps",
                        () ->
                                String.format(
                                        "%d FPS, %s",
                                        GraphicsManager.getLastFPS(),
                                        GraphicsManager.getSettings().quality))
                .toggle(
                        "window",
                        "Graphics Debug window",
                        () ->
                                GraphicsManager.getWindowManager()
                                        .isVisible(GraphicsDebug.WINDOW_NAME),
                        show ->
                                GraphicsManager.getWindowManager()
                                        .setVisible(GraphicsDebug.WINDOW_NAME, show))
                .toggle(
                        "wireframe",
                        "Wireframe",
                        visualizers::isWireframe,
                        visualizers::setWireframe)
                .toggle(
                        "bounds",
                        "Instance bounds",
                        visualizers::isEntityBounds,
                        visualizers::setEntityBounds)
                .toggle(
                        "cascades",
                        "Shadow cascades",
                        visualizers::isShadowCascades,
                        visualizers::setShadowCascades)
                .toggle(
                        "lights",
                        "Light gizmos",
                        lights(visualizers),
                        show -> {
                            visualizers.setPointLights(show);
                            visualizers.setSpotLights(show);
                            visualizers.setDirectionalLight(show);
                        })
                .toggle(
                        "culling",
                        "Culling off",
                        visualizers::isCullingDisabled,
                        visualizers::setCullingDisabled);
    }

    /**
     * Whether any light gizmo shows.
     *
     * @param visualizers The visualizers.
     * @return Reads it.
     */
    private static BooleanSupplier lights(@NonNull DebugVisualizers visualizers) {
        return () ->
                visualizers.isPointLights()
                        || visualizers.isSpotLights()
                        || visualizers.isDirectionalLight();
    }

    /**
     * A string from the Graphics plugin's bundle.
     *
     * @param key The key.
     * @return The string.
     */
    private static String text(@NonNull String key) {
        return SafeResourceLoader.getString(key, GraphicsPlugin.getResourceBundle());
    }
}
