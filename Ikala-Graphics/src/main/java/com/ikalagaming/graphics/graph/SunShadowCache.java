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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Keeps the sun's far shadow cascades in place in the world, so what doesn't move (baked sections
 * and level of detail) is drawn into their maps once and kept, and only what moves is drawn every
 * frame, into a separate map.
 *
 * <p>A cached cascade is a sphere around the camera, not around its slice of the view, so turning
 * never moves it. Its center is the camera's position snapped to a grid a quarter of its reach wide
 * across the light, and the sphere is that much bigger than the reach, so the slice always fits
 * while the camera stays within a step. When the camera crosses into another step, the cascade
 * moves by whole texels: its map is shifted along, see {@link Redraw#SCROLL}, and only the strip it
 * uncovers is drawn. Along the light the center is snapped to a grid {@link #ALONG_LIGHT_STEPS}
 * times coarser, the map reaching that much further both ways, so the map's depths stay put while
 * the camera walks; crossing that grid, or both grids across the light at once, draws the map again
 * whole.
 *
 * <p>The sun's direction is taken in steps of {@link #SUN_STEP_DEGREES}. When it moves a step, the
 * cascades are drawn again with the new direction one a frame, nearest first, so a moving sun costs
 * about one cascade a frame.
 *
 * <p>Things that change inside a cached cascade, such as an edited section, are reported with
 * {@link #invalidate} as boxes in the world. The texels they cover wait as a few rectangles per
 * cascade, those close together merged, and each frame the oldest is drawn again, with whatever
 * near it fits in without drawing much more: changes scattered over the map are drawn over a few
 * frames rather than as one rectangle around them all.
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
        /**
         * The map's contents shifted by the cascade's scroll, in texels, as the cascade moved
         * across the light by whole texels, then the texels in its redraw rectangle, the strip the
         * shift uncovered.
         */
        SCROLL,
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
     * How many steps across the light one step along it is. The map reaches half a step further
     * each way along the light, so a few times the cascade's reach keeps it from being drawn again
     * whole as the camera walks, for some loss of depth precision.
     */
    static final int ALONG_LIGHT_STEPS = 16;

    /**
     * Texels added around a changed box before it is drawn again: the light stage's filter reads
     * two around a point, and the depth bias reaches one further.
     */
    static final int DIRTY_MARGIN_TEXELS = 3;

    /** Values in a redraw rectangle. */
    private static final int RECT_VALUES = 4;

    /** The most rectangles a cascade keeps waiting; past it, two are merged into one. */
    static final int MAX_PENDING = 32;

    /**
     * How much bigger than the two apart the rectangle around two may be for them to be drawn as
     * one.
     */
    static final double MERGE_SLACK = 1.25;

    /** Corners of a box. */
    private static final int BOX_CORNERS = 8;

    /** Where a cached cascade is in the world, and what its map holds. */
    static final class Placement {
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

        /** The width and height of the map, in texels. */
        int mapSize;
    }

    /** Each cascade's placement; unused for those that aren't cached. */
    private final Placement[] placements;

    /**
     * Each cascade's texels waiting to be drawn again, oldest first, as rectangles min x, min y,
     * max x, max y, the max exclusive.
     */
    private final List<List<int[]>> pending;

    /** Whether each cascade was drawn with an older sun step than the current one. */
    private final boolean[] sunBehind;

    /** The current sun step, or zero before the first frame. */
    private final Vector3d sun = new Vector3d();

    /** Whole cascades drawn since the cache was made. */
    private long fullRedraws;

    /** Parts of cascades drawn since the cache was made. */
    private long partRedraws;

    /** Cascades shifted along since the cache was made. */
    private long scrolls;

    /** Texels drawn again since the cache was made, by whole cascades and parts. */
    private long texelsRedrawn;

    /** Make an empty cache, which draws every cascade whole the first frame. */
    public SunShadowCache() {
        placements = new Placement[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];
        pending = new ArrayList<>(CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT);
        for (int i = 0; i < CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT; ++i) {
            pending.add(new ArrayList<>());
        }
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
     * @param scrolls Cascades shifted along, each drawing the strip it uncovered.
     * @param texelsRedrawn Texels drawn again, by whole cascades, parts and strips.
     */
    public record Stats(long fullRedraws, long partRedraws, long scrolls, long texelsRedrawn) {}

    /**
     * What the cache has done since it was made.
     *
     * @return The numbers.
     */
    public Stats stats() {
        return new Stats(fullRedraws, partRedraws, scrolls, texelsRedrawn);
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
            addPending(pending.get(i), rect);
        }
    }

    /**
     * Add a rectangle to those waiting: merged into one that it fits with, or kept apart, the two
     * that fit best merged once there are too many.
     *
     * @param waiting The rectangles waiting.
     * @param rect The rectangle.
     */
    static void addPending(@NonNull List<int[]> waiting, int @NonNull [] rect) {
        for (int[] other : waiting) {
            if (fits(other, rect)) {
                addRect(other, rect);
                return;
            }
        }
        waiting.add(rect.clone());
        if (waiting.size() <= MAX_PENDING) {
            return;
        }
        int bestA = 0;
        int bestB = 1;
        long bestGrowth = Long.MAX_VALUE;
        for (int a = 0; a < waiting.size(); ++a) {
            for (int b = a + 1; b < waiting.size(); ++b) {
                final long growth =
                        area(union(waiting.get(a), waiting.get(b)))
                                - area(waiting.get(a))
                                - area(waiting.get(b));
                if (growth < bestGrowth) {
                    bestGrowth = growth;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        addRect(waiting.get(bestA), waiting.remove(bestB));
    }

    /**
     * Take this frame's rectangle from those waiting: the oldest, with every other that fits with
     * it.
     *
     * @param waiting The rectangles waiting, not empty.
     * @param out Where the rectangle goes.
     */
    static void takePending(@NonNull List<int[]> waiting, int @NonNull [] out) {
        final int[] rect = waiting.remove(0);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int k = 0; k < waiting.size(); ++k) {
                if (fits(rect, waiting.get(k))) {
                    addRect(rect, waiting.remove(k));
                    grew = true;
                    break;
                }
            }
        }
        System.arraycopy(rect, 0, out, 0, RECT_VALUES);
    }

    /**
     * Whether two rectangles are drawn as one: the rectangle around both is little bigger than the
     * two apart.
     *
     * @param a A rectangle.
     * @param b Another.
     * @return True if so.
     */
    static boolean fits(int @NonNull [] a, int @NonNull [] b) {
        return area(union(a, b)) <= MERGE_SLACK * (area(a) + area(b));
    }

    /**
     * The rectangle around two.
     *
     * @param a A rectangle.
     * @param b Another.
     * @return A new rectangle around both.
     */
    static int[] union(int @NonNull [] a, int @NonNull [] b) {
        return new int[] {
            Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])
        };
    }

    /**
     * A rectangle's area.
     *
     * @param rect The rectangle.
     * @return Its area in texels.
     */
    static long area(int @NonNull [] rect) {
        return (long) Math.max(0, rect[2] - rect[0]) * Math.max(0, rect[3] - rect[1]);
    }

    /**
     * Grow a rectangle to hold another.
     *
     * @param to The rectangle, empty while max <= min.
     * @param rect The rectangle to add.
     */
    static void addRect(int @NonNull [] to, int @NonNull [] rect) {
        if (to[2] <= to[0] || to[3] <= to[1]) {
            System.arraycopy(rect, 0, to, 0, RECT_VALUES);
        } else {
            to[0] = Math.min(to[0], rect[0]);
            to[1] = Math.min(to[1], rect[1]);
            to[2] = Math.max(to[2], rect[2]);
            to[3] = Math.max(to[3], rect[3]);
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
    static int[] rectOf(
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
            final double x = (corner.x / corner.w * 0.5 + 0.5) * placement.mapSize;
            final double y = (corner.y / corner.w * 0.5 + 0.5) * placement.mapSize;
            x0 = Math.min(x0, x);
            y0 = Math.min(y0, y);
            x1 = Math.max(x1, x);
            y1 = Math.max(y1, y);
        }
        final int[] rect = {
            Math.max(0, (int) Math.floor(x0) - DIRTY_MARGIN_TEXELS),
            Math.max(0, (int) Math.floor(y0) - DIRTY_MARGIN_TEXELS),
            Math.min(placement.mapSize, (int) Math.ceil(x1) + DIRTY_MARGIN_TEXELS),
            Math.min(placement.mapSize, (int) Math.ceil(y1) + DIRTY_MARGIN_TEXELS)
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
                    place(
                            depths[i] * (double) cornerLength,
                            facing,
                            cameraPosition,
                            STATIC_MAP_SIZE);
            final boolean moved =
                    !placement.valid
                            || !wanted.center.equals(placement.center)
                            || !wanted.direction.equals(placement.direction)
                            || wanted.radius != placement.radius;
            if (moved) {
                final int[] shift =
                        scrollable(placement, wanted) ? shiftOf(placement, wanted) : null;
                copy(wanted, placement);
                placement.valid = true;
                sunBehind[i] = !placement.direction.equals(sun);
                sunRedrawn |= catchUp;
                if (shift != null) {
                    cascade.redraw = Redraw.SCROLL;
                    cascade.scroll[0] = shift[0];
                    cascade.scroll[1] = shift[1];
                    stripOf(shift, placement.mapSize, cascade.redrawRect);
                    // What was waiting to be drawn again moved with the map: drawn from next frame
                    final List<int[]> waiting = pending.get(i);
                    for (int k = waiting.size() - 1; k >= 0; --k) {
                        shiftRect(waiting.get(k), shift, placement.mapSize);
                        if (area(waiting.get(k)) == 0) {
                            waiting.remove(k);
                        }
                    }
                } else {
                    pending.get(i).clear();
                    cascade.redraw = Redraw.ALL;
                }
            } else if (!pending.get(i).isEmpty()) {
                cascade.redraw = Redraw.PART;
                takePending(pending.get(i), cascade.redrawRect);
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
    static Vector3d upFor(@NonNull Vector3dc direction) {
        return Math.abs(direction.y()) > 0.99 ? new Vector3d(0, 0, 1) : new Vector3d(0, 1, 0);
    }

    /**
     * Where a cached cascade would be for the camera where it is.
     *
     * @param reach How far from the camera the slice reaches, at its far corners.
     * @param direction The way the sun's light travels, normalized.
     * @param cameraPosition The camera's world position.
     * @param mapSize The width and height of its map, in texels.
     * @return The placement, not yet valid.
     */
    static Placement place(
            double reach,
            @NonNull Vector3dc direction,
            @NonNull Vector3dc cameraPosition,
            int mapSize) {
        final Placement placement = new Placement();
        placement.direction.set(direction);
        final double step = reach / RECENTER_STEPS;
        final double radius =
                Math.ceil((reach + step) / CascadeShadowSplit.RADIUS_STEP)
                        * CascadeShadowSplit.RADIUS_STEP;
        final double texelSize = 2 * radius / (mapSize - 2 * CascadeShadowSplit.MARGIN_TEXELS);
        // A whole number of texels, so moving by steps keeps the world on the texel grid
        final double grid = Math.max(1, Math.floor(step / texelSize)) * texelSize;
        placement.radius = radius;
        placement.mapSize = mapSize;
        placement.texelSize = texelSize;
        placement.extent = radius + CascadeShadowSplit.MARGIN_TEXELS * texelSize;

        // The camera in the light's view, snapped to the grid, and back
        final Vector3d up = upFor(direction);
        final Matrix4d rotation = new Matrix4d().lookAlong(direction, up);
        final Vector3d center = rotation.transformPosition(new Vector3d(cameraPosition));
        // The light's view looks along -z, so z is along the light
        final double along = grid * ALONG_LIGHT_STEPS;
        center.set(
                Math.rint(center.x / grid) * grid,
                Math.rint(center.y / grid) * grid,
                Math.rint(center.z / along) * along);
        rotation.invert(new Matrix4d()).transformPosition(center);
        placement.center.set(center);

        // Half a step along the light further each way, for a camera anywhere in the step
        final double back = placement.extent + CascadeShadowSplit.CASTER_REACH + along / 2;
        placement.depth = back + placement.extent + along / 2;
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
     * Whether a cascade can move to a new placement by shifting its map: drawn the same way, the
     * light's direction, the sphere and the map's size unchanged, and the center moved only across
     * the light, so every depth in the map stays as it is.
     *
     * @param from Where it is.
     * @param to Where it goes.
     * @return True if its map can be shifted along.
     */
    static boolean scrollable(@NonNull Placement from, @NonNull Placement to) {
        if (!from.valid
                || !from.direction.equals(to.direction)
                || from.radius != to.radius
                || from.mapSize != to.mapSize) {
            return false;
        }
        final double along = new Vector3d(to.center).sub(from.center).dot(from.direction);
        return Math.abs(along) < from.texelSize / 2;
    }

    /**
     * How far a cascade's map shifts as it moves to a new placement, in texels, if it moves along
     * one axis of the map by less than its width: crossing both axes' steps at once uncovers two
     * strips, drawn whole instead.
     *
     * @param from Where it is.
     * @param to Where it goes, {@link #scrollable} from it.
     * @return The shift in x and y, one of them 0, or null if it can't be shifted.
     */
    static int[] shiftOf(@NonNull Placement from, @NonNull Placement to) {
        final double[] before = texelOf(from, from.center);
        final double[] after = texelOf(to, from.center);
        final int[] shift = {
            (int) Math.rint(after[0] - before[0]), (int) Math.rint(after[1] - before[1])
        };
        if ((shift[0] != 0) == (shift[1] != 0)
                || Math.abs(shift[0]) >= from.mapSize
                || Math.abs(shift[1]) >= from.mapSize) {
            return null;
        }
        return shift;
    }

    /**
     * Where a world point is in a cascade's map, in texels, x and y as in {@link #rectOf}.
     *
     * @param placement The cascade.
     * @param point The world point.
     * @return Its texel coordinates, not rounded.
     */
    static double[] texelOf(@NonNull Placement placement, @NonNull Vector3dc point) {
        final Vector4d at =
                new Vector4d(point, 1)
                        .sub(placement.center.x, placement.center.y, placement.center.z, 0);
        placement.fromCenter.transform(at);
        return new double[] {
            (at.x / at.w * 0.5 + 0.5) * placement.mapSize,
            (at.y / at.w * 0.5 + 0.5) * placement.mapSize
        };
    }

    /**
     * The strip a shift uncovers: the texels whose contents come from outside the map before it.
     *
     * @param shift The shift in x and y, one of them 0.
     * @param mapSize The map's width and height, in texels.
     * @param rect Where the strip goes, min x, min y, max x, max y, the max exclusive.
     */
    static void stripOf(int @NonNull [] shift, int mapSize, int @NonNull [] rect) {
        rect[0] = 0;
        rect[1] = 0;
        rect[2] = mapSize;
        rect[3] = mapSize;
        if (shift[0] > 0) {
            rect[2] = shift[0];
        } else if (shift[0] < 0) {
            rect[0] = mapSize + shift[0];
        } else if (shift[1] > 0) {
            rect[3] = shift[1];
        } else {
            rect[1] = mapSize + shift[1];
        }
    }

    /**
     * Move a rectangle of a map along with the map's contents, clipped to the map.
     *
     * @param rect The rectangle, empty while max <= min, emptied if it leaves the map.
     * @param shift The shift in x and y.
     * @param mapSize The map's width and height, in texels.
     */
    static void shiftRect(int @NonNull [] rect, int @NonNull [] shift, int mapSize) {
        if (rect[2] <= rect[0] || rect[3] <= rect[1]) {
            return;
        }
        rect[0] = Math.max(0, rect[0] + shift[0]);
        rect[1] = Math.max(0, rect[1] + shift[1]);
        rect[2] = Math.min(mapSize, rect[2] + shift[0]);
        rect[3] = Math.min(mapSize, rect[3] + shift[1]);
        if (rect[2] <= rect[0] || rect[3] <= rect[1]) {
            clear(rect);
        }
    }

    /**
     * Copy a placement over another.
     *
     * @param from The placement to copy.
     * @param to Where it goes.
     */
    static void copy(@NonNull Placement from, @NonNull Placement to) {
        to.center.set(from.center);
        to.direction.set(from.direction);
        to.radius = from.radius;
        to.extent = from.extent;
        to.texelSize = from.texelSize;
        to.depth = from.depth;
        to.mapSize = from.mapSize;
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
    static void fill(
            @NonNull CascadeShadowSplit cascade,
            @NonNull Placement placement,
            @NonNull Vector3dc cameraPosition,
            float splitDistance) {
        final Vector3d offset = new Vector3d(cameraPosition).sub(placement.center);
        toFloat(new Matrix4d(placement.fromCenter).translate(offset), cascade.getProjViewMatrix());
        cascade.splitDistance = splitDistance;
        cascade.texelSize = (float) placement.texelSize;
        cascade.mapSize = placement.mapSize;
        cascade.depthRange = (float) placement.depth;
        cascade.cached = true;
        switch (cascade.redraw) {
            case ALL -> {
                cascade.staticCullMatrix.set(cascade.getProjViewMatrix());
                cascade.staticCulledAway = false;
            }
            case PART, SCROLL -> {
                // An orthographic box around just the texels drawn again
                final int[] rect = cascade.redrawRect;
                final double texel = 2 * placement.extent / placement.mapSize;
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
    static void toFloat(@NonNull Matrix4d from, @NonNull Matrix4f to) {
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
            case SCROLL -> {
                scrolls += 1;
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
    static void clear(int @NonNull [] rect) {
        Arrays.fill(rect, 0);
    }
}
