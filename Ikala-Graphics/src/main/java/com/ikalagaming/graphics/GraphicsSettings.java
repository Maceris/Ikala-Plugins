package com.ikalagaming.graphics;

import lombok.NonNull;

public class GraphicsSettings {

    /** The general rendering quality. */
    public enum Quality {
        // TODO(ches) document specific technical details. ... Once we decide what they are.
        // NOTE(ches) doesn't do anything yet. Intended to do things like enable ray tracing or
        // crank down effects
        /** Intentionally lowered quality. */
        LOW,
        /** Regular quality. */
        MEDIUM,
        /** Extra quality. */
        HIGH,
    }

    /** Whether anti-aliasing is enabled. */
    public boolean antiAliasing = true;

    /**
     * The graphics quality. Should not be set directly once rendering is up and running, see {@link
     * com.ikalagaming.graphics.GraphicsManager#setQuality(Quality)}. Fine to set before initial
     * setup though.
     */
    @NonNull public Quality quality = Quality.MEDIUM;

    /** The height of the window in pixels. Only respected if greater than 0. */
    public int requestedWindowHeight = 0;

    /** The width of the window in pixels. Only respected if greater than 0. */
    public int requestedWindowWidth = 0;

    /**
     * The target frames per second. If zero (or negative), this will be interpreted as requesting
     * VSync and thus will be effectively whatever the monitor's refresh rate is.
     */
    public int targetFPS = 0;

    /**
     * The most error, in pixels, a level of detail cluster may have and be drawn, for worlds added
     * after it is set; see {@link Lods#setThreshold}. At 1, Nanite's choice, the switch between
     * levels can't be seen.
     */
    public double lodThresholdPixels = 1;

    /**
     * How near the observer, in world units, a section feeding level of detail draws its own opaque
     * blocks while its newest bake isn't in the level of detail yet, as right after an edit.
     * Further away, changes show once the level of detail is rebuilt.
     */
    public double lodOverrideDistance = 32;

    /**
     * Whether level of detail keeps on the GPU only what a cut near the observer can need, loading
     * and evicting groups as it moves. Off, everything stays on the GPU, for comparisons.
     */
    public boolean lodStreaming = true;

    /**
     * The most level of detail mesh bytes to keep on the GPU; past it, groups no longer wanted go
     * at once instead of after a delay.
     */
    public long lodResidentBytes = 1L << 30;

    /**
     * The folder built level of detail cells are cached in, so an area seen before loads without
     * building; null or blank for no cache. Set from the {@code lod-cache-folder} config value.
     */
    public String lodCacheDirectory = "cache/lod";

    /**
     * The most bytes the level of detail cache keeps, least recently used out. Set from the {@code
     * lod-cache-max-mb} config value.
     */
    public long lodCacheMaxBytes = 2048L * 1024 * 1024;

    /**
     * Whether the sun's far shadow cascades are kept in place in the world, so what doesn't move is
     * drawn into them only when it changes. Off, every cascade is drawn whole every frame.
     */
    public boolean sunShadowCache = true;

    /**
     * Whether ambient light is dimmed where something stands above a point, from the sky occlusion
     * map, so caves and rooms aren't lit like open ground.
     */
    public boolean skyOcclusion = true;

    /**
     * The least ambient light a point well under cover gets, as a part of open ground's, from 0 for
     * none to 1 for no dimming.
     */
    public double skyLightFloor = 0.25;

    /**
     * The most point light shadow maps a frame, each a cube map one light or a tight group of
     * lights shares; 0 for no point light shadows. At most {@code PointShadows.MAX_MAPS}.
     */
    public int pointShadowBudget = 8;

    /** The farthest apart shadow-casting lights may be and share a map, in meters. */
    public double pointShadowShareExtent = 2;

    /**
     * Whether the occupancy volume is kept up to date: which voxels around the camera hold
     * something that doesn't move, for ambient occlusion and light visibility.
     */
    public boolean occupancy = true;

    /**
     * Whether the occupancy volume dims ambient light where something nearby blocks the way out, in
     * corners, under trees and indoors. Needs {@link #occupancy}.
     */
    public boolean occupancyAo = true;

    /**
     * How far the occupancy volume's ambient occlusion looks for something in the way, in meters.
     */
    public double occupancyAoReach = 8;

    /**
     * Whether lights without a shadow map are kept from shining through what the occupancy volume
     * holds, by marching a ray from each surface toward them. Needs {@link #occupancy}.
     */
    public boolean occupancyLightRays = true;

    /**
     * How many lights without a shadow map march a ray a pixel, the strongest there first; the rest
     * shine through. At most {@code ShaderBindings.Light.MAX_OCCUPANCY_RAYS}.
     */
    public int occupancyRaysPerPixel = 8;

    /**
     * How far from the observer, in meters, sections draw their own cut out and translucent blocks,
     * like leaves and glass. Further away, a volume of each section's blocks is ray marched in
     * their place, the two blended over a stretch centered here. 0 or less draws every section's
     * own blocks, with no volumes.
     */
    public double volumeDistance = 96;

    /**
     * Whether temporal anti-aliasing is on: each frame samples its pixels a little apart, and is
     * blended into the history of the frames before.
     */
    public boolean taa = true;
}
