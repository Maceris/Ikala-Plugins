package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.scene.Projection;

import lombok.NonNull;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4d;

import java.util.Arrays;

/**
 * Keeps the sun's far shadow cascades in place in the world, so what doesn't move (baked sections
 * and level of detail) is drawn into their maps once and kept, and only what moves is drawn every
 * frame, into a separate map.
 *
 * <p>A cached cascade is a sphere around the camera, not around its slice of the view, so turning
 * never moves it. Its center is the camera's position snapped to a grid a quarter of its reach wide
 * in the light's view, and the sphere is that much bigger than the reach, so the slice always fits
 * while the camera stays within a step. When the camera crosses into another step, the cascade
 * moves and its map is drawn again whole.
 *
 * <p>The sun's direction is taken in steps of {@link #SUN_STEP_DEGREES}. When it moves a step, the
 * cascades are drawn again with the new direction one a frame, nearest first, so a moving sun costs
 * about one cascade a frame.
 *
 * <p>Things that change inside a cached cascade, such as an edited section, are reported with
 * {@link #invalidate} as boxes in the world. Each frame the texels they cover are drawn again, in
 * one rectangle per cascade.
 *
 * <p>Render thread only.
 */
public class SunShadowCache {

    /** What of a cascade's map is drawn this frame. */
    public enum Redraw {
        /** Nothing: the map is kept as it is. */
        NONE,
        /** The texels in the cascade's redraw rectangle. */
        PART,
        /** The whole map. */
        ALL
    }

    /** Which cascades are cached, nearest first. The nearest is small and drawn every frame. */
    public static final boolean[] CACHED = {false, true, true};

    /**
     * The size of a cached cascade's map of what doesn't move, in texels. Bigger than a fitted
     * cascade's, since a sphere around the camera is about twice the one around a slice.
     */
    public static final int STATIC_MAP_SIZE = 4096;

    /**
     * The size of a cached cascade's map of what moves, in texels. It covers the same area as the
     * static map, so the light stage reads both at the same place.
     */
    public static final int DYNAMIC_MAP_SIZE = CascadeShadowSplit.SHADOW_MAP_SIZE;

    /** How far the sun's direction moves before the cascades follow it, in degrees. */
    public static final double SUN_STEP_DEGREES = 0.25;

    /** How many steps of the snapping grid a cached cascade's reach spans. */
    static final int RECENTER_STEPS = 4;

    /**
     * Texels added around a changed box before it is drawn again: the light stage's filter reads
     * two around a point, and the depth bias reaches one further.
     */
    static final int DIRTY_MARGIN_TEXELS = 3;

    /** Values in a redraw rectangle. */
    private static final int RECT_VALUES = 4;

    /** Corners of a box. */
    private static final int BOX_CORNERS = 8;

    /** Where a cached cascade is in the world, and what its map holds. */
    private static final class Placement {
        /** The center, in the world, on the snapping grid. */
        final Vector3d center = new Vector3d();

        /** The way the sun's light traveled when it was drawn. */
        final Vector3d direction = new Vector3d();

        /** The radius of the sphere it covers. */
        double radius;

        /** Half the width of the map, in meters. */
        double extent;

        /** How wide a texel is, in meters. */
        double texelSize;

        /** From the world relative to the center to the map's clip space. */
        final Matrix4d fromCenter = new Matrix4d();

        /** The light's view relative to the center, for cull matrices of parts. */
        final Matrix4d viewFromCenter = new Matrix4d();

        /** How far the map reaches along the light, from its eye. */
        double depth;

        /** Whether the map holds anything yet. */
        boolean valid;
    }

    /** Each cascade's placement; unused for those that aren't cached. */
    private final Placement[] placements;

    /** Each cascade's texels to draw again, min x, min y, max x, max y; empty while max <= min. */
    private final int[][] dirty;

    /** Whether each cascade was drawn with an older sun step than the current one. */
    private final boolean[] sunBehind;

    /** The current sun step, or zero before the first frame. */
    private final Vector3d sun = new Vector3d();

    /** Whole cascades drawn since the cache was made. */
    private long fullRedraws;

    /** Parts of cascades drawn since the cache was made. */
    private long partRedraws;

    /** Texels drawn again since the cache was made, by whole cascades and parts. */
    private long texelsRedrawn;

    /** Make an empty cache, which draws every cascade whole the first frame. */
    public SunShadowCache() {
        placements = new Placement[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];
        dirty = new int[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT][RECT_VALUES];
        sunBehind = new boolean[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];
        for (int i = 0; i < placements.length; ++i) {
            placements[i] = new Placement();
        }
    }

    /**
     * What the cache has done.
     *
     * @param fullRedraws Whole cascades drawn.
     * @param partRedraws Parts of cascades drawn.
     * @param texelsRedrawn Texels drawn again, by whole cascades and parts.
     */
    public record Stats(long fullRedraws, long partRedraws, long texelsRedrawn) {}

    /**
     * What the cache has done since it was made.
     *
     * @return The numbers.
     */
    public Stats stats() {
        return new Stats(fullRedraws, partRedraws, texelsRedrawn);
    }

    /** Forget what every map holds, so each is drawn whole next frame. */
    public void invalidateAll() {
        for (Placement placement : placements) {
            placement.valid = false;
        }
    }

    /**
     * Note that something that doesn't move changed inside a box, so the texels it covers in every
     * cached cascade are drawn again next frame.
     *
     * @param min The box's minimum corner, in the world.
     * @param max The box's maximum corner, in the world.
     */
    public void invalidate(double @NonNull [] min, double @NonNull [] max) {
        for (int i = 0; i < placements.length; ++i) {
            final Placement placement = placements[i];
            if (!CACHED[i] || !placement.valid) {
                continue;
            }
            final int[] rect = rectOf(placement, min, max);
            if (rect == null) {
                continue;
            }
            final int[] to = dirty[i];
            if (to[2] <= to[0] || to[3] <= to[1]) {
                System.arraycopy(rect, 0, to, 0, RECT_VALUES);
            } else {
                to[0] = Math.min(to[0], rect[0]);
                to[1] = Math.min(to[1], rect[1]);
                to[2] = Math.max(to[2], rect[2]);
                to[3] = Math.max(to[3], rect[3]);
            }
        }
    }

    /**
     * The texels a box covers in a cascade's map, with the margin, or null if it misses the map.
     *
     * @param placement The cascade.
     * @param min The box's minimum corner, in the world.
     * @param max The box's maximum corner, in the world.
     * @return Min x, min y, max x, max y, the max exclusive.
     */
    private static int[] rectOf(
            @NonNull Placement placement, double @NonNull [] min, double @NonNull [] max) {
        double x0 = Double.POSITIVE_INFINITY;
        double y0 = Double.POSITIVE_INFINITY;
        double x1 = Double.NEGATIVE_INFINITY;
        double y1 = Double.NEGATIVE_INFINITY;
        final Vector4d corner = new Vector4d();
        for (int c = 0; c < BOX_CORNERS; ++c) {
            corner.set(
                            (c & 1) == 0 ? min[0] : max[0],
                            (c & 2) == 0 ? min[1] : max[1],
                            (c & 4) == 0 ? min[2] : max[2],
                            1)
                    .sub(placement.center.x, placement.center.y, placement.center.z, 0);
            placement.fromCenter.transform(corner);
            final double x = (corner.x / corner.w * 0.5 + 0.5) * STATIC_MAP_SIZE;
            final double y = (corner.y / corner.w * 0.5 + 0.5) * STATIC_MAP_SIZE;
            x0 = Math.min(x0, x);
            y0 = Math.min(y0, y);
            x1 = Math.max(x1, x);
            y1 = Math.max(y1, y);
        }
        final int[] rect = {
            Math.max(0, (int) Math.floor(x0) - DIRTY_MARGIN_TEXELS),
            Math.max(0, (int) Math.floor(y0) - DIRTY_MARGIN_TEXELS),
            Math.min(STATIC_MAP_SIZE, (int) Math.ceil(x1) + DIRTY_MARGIN_TEXELS),
            Math.min(STATIC_MAP_SIZE, (int) Math.ceil(y1) + DIRTY_MARGIN_TEXELS)
        };
        return rect[2] <= rect[0] || rect[3] <= rect[1] ? null : rect;
    }

    /**
     * Work out this frame's cascades: fit the near ones to their slices, move the cached ones if
     * the camera left their step or the sun moved, and say what of each map is drawn.
     *
     * @param cascades The cascades to fill out.
     * @param inverseProjection The inverse of the camera's projection matrix.
     * @param inverseView The inverse of the camera's view matrix, from view space to render space.
     * @param cameraPosition The camera's world position, the render space origin.
     * @param lightDirection The way the sun's light travels.
     * @param shadowDistance How far the shadows reach, in meters.
     * @param enabled Whether to cache; if not, every cascade is fitted to its slice and drawn
     *     whole, as without a cache, and the cached maps are forgotten.
     */
    public void plan(
            @NonNull CascadeShadowSplit @NonNull [] cascades,
            @NonNull Matrix4fc inverseProjection,
            @NonNull Matrix4fc inverseView,
            @NonNull Vector3dc cameraPosition,
            @NonNull Vector3fc lightDirection,
            double shadowDistance,
            boolean enabled) {
        final float[] depths = CascadeShadowSplit.splitDepths(shadowDistance);
        final Vector3d direction = new Vector3d(lightDirection);
        if (direction.lengthSquared() == 0) {
            direction.set(0, -1, 0);
        }
        direction.normalize();
        if (sun.lengthSquared() == 0 || Math.toDegrees(sun.angle(direction)) > SUN_STEP_DEGREES) {
            sun.set(direction);
            for (int i = 0; i < sunBehind.length; ++i) {
                sunBehind[i] = placements[i].valid;
            }
        }
        final Vector3f[] rays = CascadeShadowSplit.cornerRays(inverseProjection);
        float cornerLength = 0;
        for (Vector3f ray : rays) {
            cornerLength = Math.max(cornerLength, ray.length());
        }
        // One cascade a frame may catch up with the sun, nearest first
        boolean sunRedrawn = false;
        for (int i = 0; i < cascades.length; ++i) {
            final float near = i == 0 ? 0 : depths[i - 1];
            final CascadeShadowSplit cascade = cascades[i];
            if (!enabled || !CACHED[i]) {
                fit(cascade, near, depths[i], rays, inverseView, cameraPosition, direction, i);
                placements[i].valid = false;
                continue;
            }
            final Placement placement = placements[i];
            boolean catchUp = placement.valid && sunBehind[i] && !sunRedrawn;
            final Vector3d facing =
                    !placement.valid || catchUp
                            ? new Vector3d(sun)
                            : new Vector3d(placement.direction);
            final Placement wanted =
                    place(depths[i] * (double) cornerLength, facing, cameraPosition);
            final boolean moved =
                    !placement.valid
                            || !wanted.center.equals(placement.center)
                            || !wanted.direction.equals(placement.direction)
                            || wanted.radius != placement.radius;
            if (moved) {
                copy(wanted, placement);
                placement.valid = true;
                sunBehind[i] = !placement.direction.equals(sun);
                sunRedrawn |= catchUp;
                clear(dirty[i]);
                cascade.redraw = Redraw.ALL;
            } else if (dirty[i][2] > dirty[i][0] && dirty[i][3] > dirty[i][1]) {
                cascade.redraw = Redraw.PART;
                System.arraycopy(dirty[i], 0, cascade.redrawRect, 0, RECT_VALUES);
                clear(dirty[i]);
            } else {
                cascade.redraw = Redraw.NONE;
            }
            fill(cascade, placement, cameraPosition, -depths[i]);
            countRedraw(cascade);
        }
    }

    /**
     * Fit a cascade that isn't cached to its slice, to be drawn whole.
     *
     * @param cascade The cascade.
     * @param near The depth of the slice's near end.
     * @param far The depth of the slice's far end.
     * @param rays The view space rays through the screen's corners.
     * @param inverseView The inverse of the camera's view matrix.
     * @param cameraPosition The camera's world position.
     * @param direction The way the sun's light travels, normalized.
     * @param index Which cascade it is, which decides its map's size.
     */
    private static void fit(
            @NonNull CascadeShadowSplit cascade,
            float near,
            float far,
            @NonNull Vector3f @NonNull [] rays,
            @NonNull Matrix4fc inverseView,
            @NonNull Vector3dc cameraPosition,
            @NonNull Vector3d direction,
            int index) {
        final Vector3d up = upFor(direction);
        final Matrix4d lightRotation = new Matrix4d().lookAlong(direction, up);
        CascadeShadowSplit.fitSlice(
                cascade,
                Math.max(near, Projection.Z_NEAR),
                far,
                rays,
                inverseView,
                cameraPosition,
                direction,
                up,
                lightRotation,
                lightRotation.invert(new Matrix4d()),
                CACHED[index] ? STATIC_MAP_SIZE : CascadeShadowSplit.SHADOW_MAP_SIZE);
    }

    /**
     * An up direction for the light's view: any that isn't along the light, since it only turns the
     * map.
     *
     * @param direction The way the light travels, normalized.
     * @return The up direction.
     */
    private static Vector3d upFor(@NonNull Vector3dc direction) {
        return Math.abs(direction.y()) > 0.99 ? new Vector3d(0, 0, 1) : new Vector3d(0, 1, 0);
    }

    /**
     * Where a cached cascade would be for the camera where it is.
     *
     * @param reach How far from the camera the slice reaches, at its far corners.
     * @param direction The way the sun's light travels, normalized.
     * @param cameraPosition The camera's world position.
     * @return The placement, not yet valid.
     */
    private static Placement place(
            double reach, @NonNull Vector3dc direction, @NonNull Vector3dc cameraPosition) {
        final Placement placement = new Placement();
        placement.direction.set(direction);
        final double step = reach / RECENTER_STEPS;
        final double radius =
                Math.ceil((reach + step) / CascadeShadowSplit.RADIUS_STEP)
                        * CascadeShadowSplit.RADIUS_STEP;
        final double texelSize =
                2 * radius / (STATIC_MAP_SIZE - 2 * CascadeShadowSplit.MARGIN_TEXELS);
        // A whole number of texels, so moving by steps keeps the world on the texel grid
        final double grid = Math.max(1, Math.floor(step / texelSize)) * texelSize;
        placement.radius = radius;
        placement.texelSize = texelSize;
        placement.extent = radius + CascadeShadowSplit.MARGIN_TEXELS * texelSize;

        // The camera in the light's view, snapped to the grid, and back
        final Vector3d up = upFor(direction);
        final Matrix4d rotation = new Matrix4d().lookAlong(direction, up);
        final Vector3d center = rotation.transformPosition(new Vector3d(cameraPosition));
        center.set(
                Math.rint(center.x / grid) * grid,
                Math.rint(center.y / grid) * grid,
                Math.rint(center.z / grid) * grid);
        rotation.invert(new Matrix4d()).transformPosition(center);
        placement.center.set(center);

        final double back = placement.extent + CascadeShadowSplit.CASTER_REACH;
        placement.depth = back + placement.extent;
        // Right handed, as the fitted cascades are, see CascadeShadowSplit
        placement.viewFromCenter.setLookAt(
                -direction.x() * back,
                -direction.y() * back,
                -direction.z() * back,
                0,
                0,
                0,
                up.x,
                up.y,
                up.z);
        placement
                .fromCenter
                .setOrtho(
                        -placement.extent,
                        placement.extent,
                        -placement.extent,
                        placement.extent,
                        0,
                        placement.depth,
                        true)
                .mul(placement.viewFromCenter);
        return placement;
    }

    /**
     * Copy a placement over another.
     *
     * @param from The placement to copy.
     * @param to Where it goes.
     */
    private static void copy(@NonNull Placement from, @NonNull Placement to) {
        to.center.set(from.center);
        to.direction.set(from.direction);
        to.radius = from.radius;
        to.extent = from.extent;
        to.texelSize = from.texelSize;
        to.depth = from.depth;
        to.fromCenter.set(from.fromCenter);
        to.viewFromCenter.set(from.viewFromCenter);
    }

    /**
     * Fill out a cached cascade's matrices for this frame, from where it is in the world.
     *
     * @param cascade The cascade.
     * @param placement Where it is.
     * @param cameraPosition The camera's world position, the render space origin.
     * @param splitDistance The view space z of its slice's far end.
     */
    private static void fill(
            @NonNull CascadeShadowSplit cascade,
            @NonNull Placement placement,
            @NonNull Vector3dc cameraPosition,
            float splitDistance) {
        final Vector3d offset = new Vector3d(cameraPosition).sub(placement.center);
        toFloat(new Matrix4d(placement.fromCenter).translate(offset), cascade.getProjViewMatrix());
        cascade.splitDistance = splitDistance;
        cascade.texelSize = (float) placement.texelSize;
        cascade.mapSize = STATIC_MAP_SIZE;
        cascade.cached = true;
        switch (cascade.redraw) {
            case ALL -> {
                cascade.staticCullMatrix.set(cascade.getProjViewMatrix());
                cascade.staticCulledAway = false;
            }
            case PART -> {
                // An orthographic box around just the texels drawn again
                final int[] rect = cascade.redrawRect;
                final double texel = 2 * placement.extent / STATIC_MAP_SIZE;
                final Matrix4d part =
                        new Matrix4d()
                                .setOrtho(
                                        -placement.extent + rect[0] * texel,
                                        -placement.extent + rect[2] * texel,
                                        -placement.extent + rect[1] * texel,
                                        -placement.extent + rect[3] * texel,
                                        0,
                                        placement.depth,
                                        true)
                                .mul(placement.viewFromCenter)
                                .translate(offset);
                toFloat(part, cascade.staticCullMatrix);
                cascade.staticCulledAway = false;
            }
            case NONE -> {
                cascade.staticCullMatrix.set(cascade.getProjViewMatrix());
                cascade.staticCulledAway = true;
            }
        }
    }

    /**
     * Copy a double matrix into a float one.
     *
     * @param from The matrix.
     * @param to Where it goes.
     */
    private static void toFloat(@NonNull Matrix4d from, @NonNull Matrix4f to) {
        to.set(
                (float) from.m00(),
                (float) from.m01(),
                (float) from.m02(),
                (float) from.m03(),
                (float) from.m10(),
                (float) from.m11(),
                (float) from.m12(),
                (float) from.m13(),
                (float) from.m20(),
                (float) from.m21(),
                (float) from.m22(),
                (float) from.m23(),
                (float) from.m30(),
                (float) from.m31(),
                (float) from.m32(),
                (float) from.m33());
    }

    /**
     * Count a cascade's redraw this frame.
     *
     * @param cascade The cascade.
     */
    private void countRedraw(@NonNull CascadeShadowSplit cascade) {
        final long size = cascade.getMapSize();
        switch (cascade.redraw) {
            case ALL -> {
                fullRedraws += 1;
                texelsRedrawn += size * size;
            }
            case PART -> {
                partRedraws += 1;
                final int[] rect = cascade.redrawRect;
                texelsRedrawn += (long) (rect[2] - rect[0]) * (rect[3] - rect[1]);
            }
            case NONE -> {
                // Nothing drawn
            }
        }
    }

    /**
     * Empty a rectangle.
     *
     * @param rect The rectangle.
     */
    private static void clear(int @NonNull [] rect) {
        Arrays.fill(rect, 0);
    }
}
