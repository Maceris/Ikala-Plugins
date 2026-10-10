package com.ikalagaming.graphics.scene.debug;

import lombok.Getter;
import lombok.Setter;

/**
 * Which built-in debug visualizations to draw. The renderer reads these every frame, and they only
 * show when the debug stage is part of the pipeline.
 */
@Getter
@Setter
public class DebugVisualizers {
    /**
     * Mark each point light's position, and its range with a sphere.
     *
     * @param pointLights Whether to show point lights.
     * @return Whether point lights are shown.
     */
    private volatile boolean pointLights;

    /**
     * Show each spot light's cone.
     *
     * @param spotLights Whether to show spot lights.
     * @return Whether spot lights are shown.
     */
    private volatile boolean spotLights;

    /**
     * Show an arrow in front of the camera, pointing the way the directional light travels.
     *
     * @param directionalLight Whether to show the directional light.
     * @return Whether the directional light is shown.
     */
    private volatile boolean directionalLight;

    /**
     * Show a box around each mesh of each entity.
     *
     * @param entityBounds Whether to show entity bounds.
     * @return Whether entity bounds are shown.
     */
    private volatile boolean entityBounds;

    /**
     * Show the area each shadow cascade covers, and in a lighter color the slice of the camera's
     * view it was fit around.
     *
     * @param shadowCascades Whether to show the shadow cascades.
     * @return Whether the shadow cascades are shown.
     */
    private volatile boolean shadowCascades;

    /**
     * Show a line along the normal of every vertex.
     *
     * @param normals Whether to show normals.
     * @return Whether normals are shown.
     */
    private volatile boolean normals;

    /**
     * Show a line along the tangent of every vertex.
     *
     * @param tangents Whether to show tangents.
     * @return Whether tangents are shown.
     */
    private volatile boolean tangents;

    /**
     * How long the normal and tangent lines are, in world units.
     *
     * @param normalLength The new line length.
     * @return The line length.
     */
    private volatile float normalLength = 0.1f;

    /**
     * Show the observer's frustum while it is frozen.
     *
     * @param observerFrustum Whether to show the frozen observer.
     * @return Whether the frozen observer is shown.
     */
    private volatile boolean observerFrustum;

    /**
     * Whether culling is turned off, so everything is drawn, to compare against. Not a visualizer,
     * so {@link #anyEnabled()} ignores it.
     *
     * @param cullingDisabled Whether to draw everything.
     * @return Whether everything is drawn.
     */
    private volatile boolean cullingDisabled;

    /**
     * Whether occlusion culling is turned off, so everything inside the view is drawn, to compare
     * against. Not a visualizer, so {@link #anyEnabled()} ignores it.
     *
     * @param occlusionDisabled Whether to draw hidden things too.
     * @return Whether hidden things are drawn too.
     */
    private volatile boolean occlusionDisabled;

    /**
     * Whether any visualizer is turned on.
     *
     * @return True if at least one visualizer is on.
     */
    public boolean anyEnabled() {
        return pointLights
                || spotLights
                || directionalLight
                || entityBounds
                || shadowCascades
                || normals
                || tangents
                || observerFrustum;
    }
}
