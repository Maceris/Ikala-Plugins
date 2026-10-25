package com.ikalagaming.graphics.graph;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * The sky occlusion map: what doesn't move (baked sections, level of detail, and placed instances
 * that stay put) drawn from straight above over the whole view, at low resolution, so the light
 * stage can tell how far below the highest thing over it a point is. Points well below it get
 * little sky light, so a cave or a room past the shadows' reach isn't lit like open ground.
 * Instances that keep moving are left out, see {@link
 * com.ikalagaming.graphics.vulkan.InstanceTable#FLAG_MOVING}.
 *
 * <p>It is kept in place in the world like the sun's cached cascades, see {@link SunShadowCache}: a
 * square around the camera, snapped to a grid a quarter of the view distance wide, drawn whole when
 * it moves and in part where something inside it changes.
 *
 * <p>Render thread only.
 */
public class SkyOcclusion {

    /** The width and height of the map, in texels. */
    public static final int MAP_SIZE = 2048;

    /**
     * How far below the highest thing over it a point must be before it starts losing sky light, in
     * meters, so a surface doesn't shade itself. Mirrors {@code SKY_BIAS} in lighting.glsl.
     */
    public static final float BIAS = 0.5f;

    /**
     * Over how many meters below that a point goes from all of the sky light to none. Mirrors
     * {@code SKY_FADE} in lighting.glsl.
     */
    public static final float FADE = 4;

    /** Straight down, the way the map looks. */
    private static final Vector3dc DOWN = new Vector3d(0, -1, 0);

    /** Values in a redraw rectangle. */
    private static final int RECT_VALUES = 4;

    /** Where the map is in the world, and what it holds. */
    private final SunShadowCache.Placement placement = new SunShadowCache.Placement();

    /** The texels to draw again, min x, min y, max x, max y; empty while max <= min. */
    private final int[] dirty = new int[RECT_VALUES];

    /** Times the map was drawn whole. */
    private long fullRedraws;

    /** Times part of the map was drawn. */
    private long partRedraws;

    /** Texels drawn again, by whole maps and parts. */
    private long texelsRedrawn;

    /**
     * What the map has done.
     *
     * @return The numbers.
     */
    public SunShadowCache.Stats stats() {
        return new SunShadowCache.Stats(fullRedraws, partRedraws, 0, texelsRedrawn);
    }

    /** Forget what the map holds, so it is drawn whole next frame. */
    public void invalidateAll() {
        placement.valid = false;
    }

    /**
     * Note that something that doesn't move changed inside a box, so the texels it covers are drawn
     * again next frame.
     *
     * @param min The box's minimum corner, in the world.
     * @param max The box's maximum corner, in the world.
     */
    public void invalidate(double @NonNull [] min, double @NonNull [] max) {
        if (!placement.valid) {
            return;
        }
        final int[] rect = SunShadowCache.rectOf(placement, min, max);
        if (rect != null) {
            SunShadowCache.addRect(dirty, rect);
        }
    }

    /**
     * Work out this frame's view: move the map if the camera left its step, and say what of it is
     * drawn.
     *
     * @param view The view to fill out.
     * @param cameraPosition The camera's world position, the render space origin.
     * @param viewDistance How far the view reaches, in meters.
     */
    public void plan(
            @NonNull CascadeShadowSplit view,
            @NonNull Vector3dc cameraPosition,
            double viewDistance) {
        final SunShadowCache.Placement wanted =
                SunShadowCache.place(viewDistance, DOWN, cameraPosition, MAP_SIZE);
        final boolean moved =
                !placement.valid
                        || !wanted.center.equals(placement.center)
                        || wanted.radius != placement.radius;
        if (moved) {
            SunShadowCache.copy(wanted, placement);
            placement.valid = true;
            SunShadowCache.clear(dirty);
            view.redraw = SunShadowCache.Redraw.ALL;
            fullRedraws += 1;
            texelsRedrawn += (long) MAP_SIZE * MAP_SIZE;
        } else if (dirty[2] > dirty[0] && dirty[3] > dirty[1]) {
            view.redraw = SunShadowCache.Redraw.PART;
            System.arraycopy(dirty, 0, view.redrawRect, 0, RECT_VALUES);
            SunShadowCache.clear(dirty);
            partRedraws += 1;
            final int[] rect = view.redrawRect;
            texelsRedrawn += (long) (rect[2] - rect[0]) * (rect[3] - rect[1]);
        } else {
            view.redraw = SunShadowCache.Redraw.NONE;
        }
        SunShadowCache.fill(view, placement, cameraPosition, 0);
    }

    /**
     * How much sky light reaches a point, as the light stage works it out for one texel of the map.
     *
     * @param covered How far below the highest thing over it the point is, in meters; negative
     *     above it.
     * @param floor The least sky light a point gets however deep it is, from 0 to 1.
     * @return The sky light, from the floor to 1.
     */
    public static float visibility(float covered, float floor) {
        final float t = Math.clamp((covered - BIAS) / FADE, 0f, 1f);
        // Smoothstep, as the shader does
        final float shade = t * t * (3 - 2 * t);
        return floor + (1 - floor) * (1 - shade);
    }
}
