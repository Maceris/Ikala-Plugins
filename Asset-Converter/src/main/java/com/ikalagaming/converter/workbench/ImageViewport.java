package com.ikalagaming.converter.workbench;

import lombok.Getter;

/**
 * Where an image sits in a view that can pan and zoom: the scale, in screen pixels per image pixel,
 * and the offset of the image's top left from the view's. Plain math, so it can be tested; {@link
 * ImageViewer} drives it from the mouse.
 */
public class ImageViewport {
    /** The smallest scale, so a huge image can still shrink to a few pixels. */
    static final float MIN_SCALE = 1.0f / 64;

    /** The largest scale, enough to see single texels of a small icon. */
    static final float MAX_SCALE = 256;

    /** How much one notch of the mouse wheel zooms by. */
    static final float ZOOM_STEP = 1.25f;

    /**
     * Screen pixels per image pixel.
     *
     * @return The scale.
     */
    @Getter private float scale = 1;

    /**
     * How far right of the view's left edge the image's left edge is, in screen pixels.
     *
     * @return The offset.
     */
    @Getter private float offsetX;

    /**
     * How far below the view's top edge the image's top edge is, in screen pixels.
     *
     * @return The offset.
     */
    @Getter private float offsetY;

    /**
     * Show the whole image, as large as fits, in the middle of the view.
     *
     * @param imageWidth The image's width in pixels.
     * @param imageHeight The image's height in pixels.
     * @param viewWidth The view's width.
     * @param viewHeight The view's height.
     */
    public void fit(int imageWidth, int imageHeight, float viewWidth, float viewHeight) {
        scale =
                clampScale(
                        Math.min(
                                viewWidth / Math.max(1, imageWidth),
                                viewHeight / Math.max(1, imageHeight)));
        center(imageWidth, imageHeight, viewWidth, viewHeight);
    }

    /**
     * Show the image one image pixel per screen pixel, in the middle of the view.
     *
     * @param imageWidth The image's width in pixels.
     * @param imageHeight The image's height in pixels.
     * @param viewWidth The view's width.
     * @param viewHeight The view's height.
     */
    public void actualSize(int imageWidth, int imageHeight, float viewWidth, float viewHeight) {
        scale = 1;
        center(imageWidth, imageHeight, viewWidth, viewHeight);
    }

    /**
     * Put the image in the middle of the view at the current scale.
     *
     * @param imageWidth The image's width in pixels.
     * @param imageHeight The image's height in pixels.
     * @param viewWidth The view's width.
     * @param viewHeight The view's height.
     */
    private void center(int imageWidth, int imageHeight, float viewWidth, float viewHeight) {
        // Whole pixels, so 1:1 doesn't land between screen pixels and blur
        offsetX = (float) Math.floor((viewWidth - imageWidth * scale) / 2);
        offsetY = (float) Math.floor((viewHeight - imageHeight * scale) / 2);
    }

    /**
     * Zoom by mouse wheel notches, keeping the image point under the cursor where it is.
     *
     * @param notches How far the wheel turned, positive to zoom in.
     * @param cursorX The cursor, relative to the view's left edge.
     * @param cursorY The cursor, relative to the view's top edge.
     */
    public void zoom(float notches, float cursorX, float cursorY) {
        final float next = clampScale(scale * (float) Math.pow(ZOOM_STEP, notches));
        final float ratio = next / scale;
        offsetX = cursorX - (cursorX - offsetX) * ratio;
        offsetY = cursorY - (cursorY - offsetY) * ratio;
        scale = next;
    }

    /**
     * Move the image.
     *
     * @param dx How far right, in screen pixels.
     * @param dy How far down, in screen pixels.
     */
    public void pan(float dx, float dy) {
        offsetX += dx;
        offsetY += dy;
    }

    /**
     * The image column under a point in the view, which may be outside the image.
     *
     * @param viewX The point, relative to the view's left edge.
     * @return The column.
     */
    public int imageX(float viewX) {
        return (int) Math.floor((viewX - offsetX) / scale);
    }

    /**
     * The image row under a point in the view, which may be outside the image.
     *
     * @param viewY The point, relative to the view's top edge.
     * @return The row.
     */
    public int imageY(float viewY) {
        return (int) Math.floor((viewY - offsetY) / scale);
    }

    /**
     * Keep a scale within the limits.
     *
     * @param value The scale.
     * @return The scale, clamped.
     */
    private static float clampScale(float value) {
        return Math.clamp(value, MIN_SCALE, MAX_SCALE);
    }
}
