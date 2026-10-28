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
     * Whether GUI windows can be dragged out of the main window into windows of their own, using
     * IkGui viewports. Read when graphics start.
     */
    public boolean guiViewports = true;

    /**
     * Where IkGui saves window positions, sizes and docking between runs, or null to not save them.
     * Read when graphics start.
     */
    public String guiLayoutFile = null;

    /**
     * The target frames per second. If zero (or negative), this will be interpreted as requesting
     * VSync and thus will be effectively whatever the monitor's refresh rate is.
     */
    public int targetFPS = 0;

    /**
     * The most error, in pixels, a level of detail cluster may have and be drawn, for worlds added
     * after it is set; see {@link Lods#setThreshold}. At 1, Nanite's choice, the switch between
     * levels can't be seen; at 4, the default, the cut on block terrain was 42% smaller and the
     * difference barely seen (2026-10-14).
     */
    public double lodThresholdPixels = 4;

    /**
     * Whether level of detail groups far enough out that textures can't be told apart, past where
     * one texture repeat drops under the error threshold, blend the look of the surface they stand
     * for per vertex instead of keeping one material a triangle, for live worlds added after it is
     * set.
     */
    public boolean lodAverageMaterials = true;

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
     * The most level of detail mesh bytes to keep on the GPU. When the cut wants more, only the
     * groups most over the error threshold are loaded, and the rest are drawn by their coarser
     * parents; past it, groups no longer wanted go at once instead of after a delay. Within the
     * geometry arena's 2 GiB of vertices with room to spare.
     */
    public long lodResidentBytes = 3L << 29;

    /**
     * The folder built level of detail cells are cached in, so an area seen before loads without
     * building; null or blank for no cache. Compiled shaders are cached in a {@code shaders} folder
     * inside it. Set from the {@code lod-cache-folder} config value.
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
     * Whether volume bricks cast the shadows of the cut out and translucent blocks they stand in
     * for, once those blocks' own meshes are off the GPU. Off, far leaves cast no sun shadow.
     */
    public boolean volumeShadows = true;

    /**
     * Whether see-through surfaces, like glass, cast colored shadows: the sun through them is
     * dimmed and tinted as the camera sees a white light through them. Off, they cast none.
     */
    public boolean translucentShadows = true;

    /**
     * Whether coarse volume bricks stand in for far sections' bricks, each covering several
     * sections with wider voxels where they are too small on screen to tell apart, so the brick
     * atlas reaches much further. Off, every section has its own brick, and the farthest go without
     * once the atlas is full.
     */
    public boolean coarseBricks = true;

    /**
     * Whether live worlds' level of detail turns parts finer than a group's resolution into
     * coverage volumes, as the asset workbench can, instead of keeping them as triangles that stop
     * the DAG halving. Read when a plugin's live world starts. Sparse volumes fill their bricks
     * thinly, so worlds with few fine parts pay GPU memory for little.
     */
    public boolean lodVolumes = true;

    /**
     * How small a coverage volume's sphere must be on screen, as a radius in pixels, before it is
     * drawn as its shell, a hull of its voxels with a hashed alpha test, rather than walked brick
     * by brick. 0 never draws shells.
     */
    public float lodShellPixels = 16;

    /**
     * Whether ambient light is dimmed where something stands above a point, from the sky occlusion
     * map, so caves and rooms aren't lit like open ground.
     */
    public boolean skyOcclusion = true;

    /**
     * Whether ambient light comes from the sky, reflected by roughness and spread over normals by
     * its spherical harmonics, rather than the same from every direction. Either way its average is
     * the ambient light's color times its intensity.
     */
    public boolean environmentLighting = true;

    /**
     * Whether what the occupancy volume holds shapes the sky's light: its diffuse light comes from
     * the way the ambient occlusion finds most open, and a ray along each smooth surface's
     * reflection hides the sky where something is in the way.
     */
    public boolean environmentOcclusion = true;

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

    /**
     * How many particles the GPU's pool holds, all emitters together; spawns past it are dropped.
     * Read when graphics starts. 80 bytes each, plus 12 bytes of lists.
     */
    public int maxParticles = 1 << 18;
}
