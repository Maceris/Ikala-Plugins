package com.ikalagaming.factory.lab;

import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.debug.WorldgenDebug;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the Lab's slice view is looking: the plane, the world position at the middle of the view,
 * and how many blocks each pixel covers. Pure math, so it can be tested without a window.
 *
 * <p>The first axis of the plane runs across the view and the second down it, as {@link
 * WorldgenDebug#pixelPosition} has them; the remaining axis is the slice coordinate.
 */
public final class LabView {

    /** The most zoomed in, in blocks per pixel: each block 8 pixels across. */
    public static final double MIN_SCALE = 1.0 / 8;

    /** The most zoomed out, in blocks per pixel. */
    public static final double MAX_SCALE = 65_536;

    /** How much one notch of the mouse wheel zooms. */
    public static final double WHEEL_ZOOM = 1.25;

    /**
     * The plane. -- GETTER -- The plane.
     *
     * @return The plane.
     */
    @Getter private WorldgenDebug.Plane plane = WorldgenDebug.Plane.XZ;

    /** The world position at the middle of the view, as x, y, z. */
    private final double[] center = {0, 0, 0};

    /**
     * Blocks per pixel. -- GETTER -- Blocks per pixel.
     *
     * @return The scale.
     */
    @Getter private double scale = 1;

    /**
     * The world position at the middle of the view.
     *
     * @return A copy, as x, y, z.
     */
    public double[] getCenter() {
        return center.clone();
    }

    /**
     * Look at a position.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     */
    public void setCenter(double x, double y, double z) {
        center[0] = x;
        center[1] = y;
        center[2] = z;
    }

    /**
     * Switch planes, keeping the center.
     *
     * @param plane The new plane.
     */
    public void setPlane(@NonNull WorldgenDebug.Plane plane) {
        this.plane = plane;
    }

    /**
     * Set the zoom.
     *
     * @param blocksPerPixel Blocks per pixel, clamped to the allowed range.
     */
    public void setScale(double blocksPerPixel) {
        scale = Math.clamp(blocksPerPixel, MIN_SCALE, MAX_SCALE);
    }

    /**
     * The axis index across the view.
     *
     * @return 0 for x, 1 for y, 2 for z.
     */
    public int acrossAxis() {
        return plane == WorldgenDebug.Plane.ZY ? 2 : 0;
    }

    /**
     * The axis index down the view. Y runs up the screen, so moving down the view lowers it.
     *
     * @return 0 for x, 1 for y, 2 for z.
     */
    public int downAxis() {
        return plane == WorldgenDebug.Plane.XZ ? 2 : 1;
    }

    /**
     * The axis the view slices across.
     *
     * @return 0 for x, 1 for y, 2 for z.
     */
    public int sliceAxis() {
        return switch (plane) {
            case XZ -> 1;
            case XY -> 2;
            case ZY -> 0;
        };
    }

    /**
     * Which way the down axis's coordinate goes as rows go down.
     *
     * @return 1 for z, which grows down the view, -1 for y, which grows up it.
     */
    private int downSign() {
        return plane == WorldgenDebug.Plane.XZ ? 1 : -1;
    }

    /**
     * The slice coordinate.
     *
     * @return The coordinate along the slice axis.
     */
    public double getSlice() {
        return center[sliceAxis()];
    }

    /**
     * Move the slice.
     *
     * @param coordinate The coordinate along the slice axis.
     */
    public void setSlice(double coordinate) {
        center[sliceAxis()] = coordinate;
    }

    /**
     * The world position of the top-left pixel, for {@link WorldgenDebug#pixelPosition}.
     *
     * @param width The view's width in pixels.
     * @param height The view's height in pixels.
     * @return The position, as x, y, z.
     */
    public double[] origin(int width, int height) {
        double[] origin = center.clone();
        origin[acrossAxis()] -= width / 2.0 * scale;
        origin[downAxis()] -= downSign() * height / 2.0 * scale;
        return origin;
    }

    /**
     * The world position under a pixel.
     *
     * @param pixelX The pixel's column, from the left.
     * @param pixelY The pixel's row, from the top.
     * @param width The view's width in pixels.
     * @param height The view's height in pixels.
     * @return The position, as x, y, z.
     */
    public double[] worldAt(double pixelX, double pixelY, int width, int height) {
        double[] p = center.clone();
        p[acrossAxis()] += (pixelX - width / 2.0) * scale;
        p[downAxis()] += downSign() * (pixelY - height / 2.0) * scale;
        return p;
    }

    /**
     * Drag the view, so the world moves with the mouse.
     *
     * @param dxPixels How far the mouse moved right.
     * @param dyPixels How far the mouse moved down.
     */
    public void pan(double dxPixels, double dyPixels) {
        center[acrossAxis()] -= dxPixels * scale;
        center[downAxis()] -= downSign() * dyPixels * scale;
    }

    /**
     * Zoom, keeping the world position under a pixel where it is.
     *
     * @param notches Mouse wheel notches, positive to zoom in.
     * @param pixelX The pixel's column.
     * @param pixelY The pixel's row.
     * @param width The view's width in pixels.
     * @param height The view's height in pixels.
     */
    public void zoomAt(double notches, double pixelX, double pixelY, int width, int height) {
        final double[] before = worldAt(pixelX, pixelY, width, height);
        setScale(scale / Math.pow(WHEEL_ZOOM, notches));
        final double[] after = worldAt(pixelX, pixelY, width, height);
        for (int axis = 0; axis < center.length; ++axis) {
            center[axis] += before[axis] - after[axis];
        }
    }

    /**
     * The chunks the view's slice passes through, for exporting a list to tests or the {@code hash}
     * tool.
     *
     * @param width The view's width in pixels.
     * @param height The view's height in pixels.
     * @return The chunk positions.
     */
    public List<ChunkPos> visibleChunks(int width, int height) {
        final double[] a = worldAt(0, 0, width, height);
        final double[] b = worldAt(width, height, width, height);
        long[] min = new long[a.length];
        long[] max = new long[a.length];
        for (int axis = 0; axis < a.length; ++axis) {
            final double low = Math.min(a[axis], b[axis]);
            // The view covers up to its far edge, not including it; the slice axis is one value
            final double high =
                    a[axis] == b[axis] ? a[axis] : Math.nextDown(Math.max(a[axis], b[axis]));
            min[axis] = (long) StrictMath.floor(low) >> World.CHUNK_SHIFT;
            max[axis] = (long) StrictMath.floor(high) >> World.CHUNK_SHIFT;
        }
        List<ChunkPos> chunks = new ArrayList<>();
        for (long y = min[1]; y <= max[1]; ++y) {
            for (long z = min[2]; z <= max[2]; ++z) {
                for (long x = min[0]; x <= max[0]; ++x) {
                    chunks.add(new ChunkPos((int) x, (int) y, (int) z));
                }
            }
        }
        return chunks;
    }
}
