package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * The depth pyramid and occlusion test the GPU uses, written out in Java so they can be tested and
 * so the shaders have a reference. {@code depth_pyramid.comp} builds the pyramid and {@code
 * cull.comp} tests against it; keep them in step with this.
 *
 * <p>Level 0 is half the depth buffer's size, and each level after that halves again, rounding
 * down, but never below one texel: {@code size(L) = max(1, size >> (L + 1))}. Each texel holds the
 * farthest depth of the texels under it. Halving an odd size leaves one texel over, so the last
 * texel of a row or column also takes in the one past it. That keeps the mapping simple: depth
 * pixel {@code p} is under texel {@code min(p >> (L + 1), size(L) - 1)} at level {@code L}.
 *
 * <p>Depth is 0 at the near plane and 1 at the far plane, and cleared to 1, so farther is larger
 * and the reduction keeps the maximum. Something is hidden if its nearest point is farther than the
 * farthest depth everywhere it could cover on screen.
 */
public final class DepthPyramidMath {

    /** The most levels a pyramid has, which covers depth buffers up to 65536 pixels across. */
    public static final int MAX_LEVELS = 16;

    /**
     * How far in front of the eye, in clip space w, a box's corners must all be for it to be
     * projected. A box closer than that may be around the eye, so it is never hidden.
     */
    public static final float MIN_CLIP_W = 1e-5f;

    /**
     * A depth pyramid.
     *
     * @param width The width of the depth buffer it was built from, in pixels.
     * @param height The height of the depth buffer it was built from, in pixels.
     * @param levels Each level's depths, row by row.
     */
    public record Pyramid(int width, int height, float[][] levels) {}

    /**
     * The size of a level along one axis.
     *
     * @param size The depth buffer's size along that axis.
     * @param level The level.
     * @return How many texels the level has along that axis.
     */
    public static int levelSize(int size, int level) {
        return Math.max(1, size >> (level + 1));
    }

    /**
     * How many levels a pyramid needs to get down to one texel.
     *
     * @param width The depth buffer's width.
     * @param height The depth buffer's height.
     * @return The level count, at least 1.
     */
    public static int levelCount(int width, int height) {
        int levels = 1;
        while (levels < MAX_LEVELS
                && (levelSize(width, levels - 1) > 1 || levelSize(height, levels - 1) > 1)) {
            levels += 1;
        }
        return levels;
    }

    /**
     * Which source texels an output texel covers along one axis. The last one takes in the rest,
     * which is one more when the source size is odd.
     *
     * @param out The output texel.
     * @param outSize The output size.
     * @param sourceSize The source size.
     * @return The last source texel covered, the first being {@code min(2 * out, sourceSize - 1)}.
     */
    public static int lastSource(int out, int outSize, int sourceSize) {
        return out == outSize - 1 ? sourceSize - 1 : 2 * out + 1;
    }

    /**
     * Build a pyramid the way {@code depth_pyramid.comp} does.
     *
     * @param depth The depth buffer, row by row.
     * @param width Its width.
     * @param height Its height.
     * @return The pyramid.
     */
    public static Pyramid build(float @NonNull [] depth, int width, int height) {
        final int count = levelCount(width, height);
        final float[][] levels = new float[count][];
        float[] source = depth;
        int sourceWidth = width;
        int sourceHeight = height;
        for (int level = 0; level < count; ++level) {
            final int outWidth = levelSize(width, level);
            final int outHeight = levelSize(height, level);
            final float[] out = new float[outWidth * outHeight];
            for (int y = 0; y < outHeight; ++y) {
                final int y0 = Math.min(2 * y, sourceHeight - 1);
                final int y1 = lastSource(y, outHeight, sourceHeight);
                for (int x = 0; x < outWidth; ++x) {
                    final int x0 = Math.min(2 * x, sourceWidth - 1);
                    final int x1 = lastSource(x, outWidth, sourceWidth);
                    float farthest = 0;
                    for (int sy = y0; sy <= y1; ++sy) {
                        for (int sx = x0; sx <= x1; ++sx) {
                            farthest = Math.max(farthest, source[sy * sourceWidth + sx]);
                        }
                    }
                    out[y * outWidth + x] = farthest;
                }
            }
            levels[level] = out;
            source = out;
            sourceWidth = outWidth;
            sourceHeight = outHeight;
        }
        return new Pyramid(width, height, levels);
    }

    /**
     * The lowest level at which a rectangle of depth pixels is under at most 2×2 texels.
     *
     * @param x0 The leftmost pixel.
     * @param y0 The top pixel.
     * @param x1 The rightmost pixel.
     * @param y1 The bottom pixel.
     * @param width The depth buffer's width.
     * @param height The depth buffer's height.
     * @param levels How many levels the pyramid has.
     * @return The level.
     */
    public static int pickLevel(int x0, int y0, int x1, int y1, int width, int height, int levels) {
        int level = 0;
        while (level < levels - 1
                && (texel(x1, width, level) - texel(x0, width, level) > 1
                        || texel(y1, height, level) - texel(y0, height, level) > 1)) {
            level += 1;
        }
        return level;
    }

    /**
     * The texel a depth pixel is under at a level, along one axis.
     *
     * @param pixel The depth pixel.
     * @param size The depth buffer's size along that axis.
     * @param level The level.
     * @return The texel.
     */
    public static int texel(int pixel, int size, int level) {
        return Math.min(pixel >> (level + 1), levelSize(size, level) - 1);
    }

    /**
     * The farthest depth anywhere in a rectangle of depth pixels, as far as the pyramid can tell,
     * which is never nearer than the real farthest depth there.
     *
     * @param pyramid The pyramid.
     * @param x0 The leftmost pixel.
     * @param y0 The top pixel.
     * @param x1 The rightmost pixel.
     * @param y1 The bottom pixel.
     * @return The farthest depth.
     */
    public static float farthest(@NonNull Pyramid pyramid, int x0, int y0, int x1, int y1) {
        final int width = pyramid.width();
        final int height = pyramid.height();
        final int level = pickLevel(x0, y0, x1, y1, width, height, pyramid.levels().length);
        final int levelWidth = levelSize(width, level);
        final float[] texels = pyramid.levels()[level];
        float farthest = 0;
        for (int y = texel(y0, height, level); y <= texel(y1, height, level); ++y) {
            for (int x = texel(x0, width, level); x <= texel(x1, width, level); ++x) {
                farthest = Math.max(farthest, texels[y * levelWidth + x]);
            }
        }
        return farthest;
    }

    /**
     * Whether a box is hidden behind what the pyramid saw. The box's corners are projected to find
     * the pixels it could cover and its nearest depth. Boxes reaching behind the near plane are
     * never hidden.
     *
     * @param projectionView The projection × view matrix the pyramid's depth was drawn with, moved
     *     into the box's space.
     * @param center The box's center.
     * @param extents Half the box's size along each axis.
     * @param pyramid The pyramid.
     * @return True if the box can't be seen.
     */
    public static boolean occluded(
            @NonNull Matrix4fc projectionView,
            @NonNull Vector3fc center,
            @NonNull Vector3fc extents,
            @NonNull Pyramid pyramid) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float nearest = Float.POSITIVE_INFINITY;
        final Vector4f corner = new Vector4f();
        for (int i = 0; i < 8; ++i) {
            corner.set(
                    center.x() + ((i & 1) == 0 ? -extents.x() : extents.x()),
                    center.y() + ((i & 2) == 0 ? -extents.y() : extents.y()),
                    center.z() + ((i & 4) == 0 ? -extents.z() : extents.z()),
                    1);
            projectionView.transform(corner);
            if (corner.w <= MIN_CLIP_W) {
                return false;
            }
            final float x = corner.x / corner.w;
            final float y = corner.y / corner.w;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            nearest = Math.min(nearest, corner.z / corner.w);
        }
        return nearest > farthestOnScreen(pyramid, minX, minY, maxX, maxY);
    }

    /**
     * The farthest depth under a rectangle in normalized device coordinates, clamped to the screen.
     * Device y points up, while pixel rows go down, as the scene's flipped viewport has it.
     *
     * @param pyramid The pyramid.
     * @param minX The left edge.
     * @param minY The bottom edge.
     * @param maxX The right edge.
     * @param maxY The top edge.
     * @return The farthest depth.
     */
    static float farthestOnScreen(
            @NonNull Pyramid pyramid, float minX, float minY, float maxX, float maxY) {
        final int width = pyramid.width();
        final int height = pyramid.height();
        final int x0 = toPixel((minX * 0.5f + 0.5f) * width, width);
        final int x1 = toPixel((maxX * 0.5f + 0.5f) * width, width);
        final int y0 = toPixel((0.5f - maxY * 0.5f) * height, height);
        final int y1 = toPixel((0.5f - minY * 0.5f) * height, height);
        return farthest(pyramid, x0, y0, x1, y1);
    }

    /**
     * The pixel a screen coordinate falls in, kept on screen.
     *
     * @param coordinate The coordinate in pixels.
     * @param size The screen's size along that axis.
     * @return The pixel.
     */
    static int toPixel(float coordinate, int size) {
        return (int) Math.min(Math.max(Math.floor(coordinate), 0), size - 1);
    }

    /**
     * Move the projection × view matrix a pyramid was drawn with into the camera's render space,
     * for when the pyramid was drawn from somewhere else, like a frozen observer. The move is
     * worked out in double precision so it stays exact far from the origin, and is nothing at all
     * when the pyramid was drawn from the camera.
     *
     * @param projectionView The matrix the pyramid was drawn with, relative to where it was drawn.
     * @param from Where it was drawn from, in the world.
     * @param camera The camera's world position, the render space origin.
     * @param out Receives the matrix, which takes render space positions.
     * @return The out matrix.
     */
    public static Matrix4f moveInto(
            @NonNull Matrix4fc projectionView,
            @NonNull Vector3dc from,
            @NonNull Vector3dc camera,
            @NonNull Matrix4f out) {
        // A point p in render space is at p + (camera - from) relative to where it was drawn
        return out.set(projectionView)
                .translate(
                        (float) (camera.x() - from.x()),
                        (float) (camera.y() - from.y()),
                        (float) (camera.z() - from.z()));
    }

    /** Static helpers only. */
    private DepthPyramidMath() {}
}
