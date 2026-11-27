package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.TextureFilter;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.TextureWrap;
import com.ikalagaming.graphics.Textures;
import com.ikalagaming.graphics.ui.UiFrame;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;

/**
 * A small image made on the CPU, like a texture graph node's output, shown as a texture viewer
 * without its toolbar controls: pan, zoom, the pixel under the mouse and Measure still work. New
 * pixels replace the old ones in place, keeping the view. Pixels are linear light, so they are kept
 * as half floats and encoded to sRGB to show. Render thread only.
 */
public class Swatch implements Viewer {
    /** What the toolbar calls a swatch. */
    static final String KIND = "Swatch";

    /** Uploads the pixels and releases them. */
    private final Textures textures;

    /** Shows the pixels. */
    private final TextureViewer viewer;

    /**
     * Make a swatch of some pixels.
     *
     * @param textures Uploads the pixels and releases them.
     * @param background Where captures are encoded and written.
     * @param name What the pixels are called, for captures.
     * @param rgba Linear red, green, blue and alpha for each pixel, rows from the top.
     * @param width The width in pixels.
     * @param height The height in pixels.
     */
    public Swatch(
            @NonNull Textures textures,
            @NonNull Executor background,
            @NonNull String name,
            float @NonNull [] rgba,
            int width,
            int height) {
        this.textures = textures;
        final ImageFile image = image(name, rgba, width, height);
        viewer =
                new TextureViewer(
                        textures,
                        background,
                        image,
                        upload(image),
                        ColorSpace.LINEAR,
                        null,
                        KIND,
                        false);
    }

    /**
     * Show new pixels, keeping the view; a new size fits the view again.
     *
     * @param name What the pixels are called.
     * @param rgba Linear red, green, blue and alpha for each pixel, rows from the top.
     * @param width The width in pixels.
     * @param height The height in pixels.
     */
    public void update(@NonNull String name, float @NonNull [] rgba, int width, int height) {
        final ImageFile image = image(name, rgba, width, height);
        viewer.replace(image, upload(image));
    }

    /**
     * The texture the pixels are shown from, for drawing the swatch somewhere else, like on a graph
     * node.
     *
     * @return The texture, which the swatch owns.
     */
    public TextureHandle getTexture() {
        return viewer.getTexture();
    }

    /**
     * The viewer showing the pixels, for its measure and settings.
     *
     * @return The viewer.
     */
    public TextureViewer getViewer() {
        return viewer;
    }

    /**
     * Upload pixels as a texture that keeps texels sharp, as swatches are small.
     *
     * @param image The pixels.
     * @return The texture.
     */
    private TextureHandle upload(ImageFile image) {
        return textures.load(
                image.pixels().duplicate().rewind(),
                image.format(),
                image.width(),
                image.height(),
                TextureWrap.REPEAT,
                TextureFilter.PIXELS);
    }

    /**
     * Pack linear pixels as a half float image.
     *
     * @param name What the pixels are called.
     * @param rgba Linear red, green, blue and alpha for each pixel, rows from the top.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @return The image.
     * @throws IllegalArgumentException If the size is not positive or doesn't match the pixels.
     */
    static ImageFile image(@NonNull String name, float @NonNull [] rgba, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("A swatch can't be " + width + " x " + height);
        }
        if (rgba.length != width * height * ImageFile.CHANNELS) {
            throw new IllegalArgumentException(
                    "A "
                            + width
                            + " x "
                            + height
                            + " swatch needs "
                            + width * height * ImageFile.CHANNELS
                            + " values, not "
                            + rgba.length);
        }
        final ByteBuffer pixels =
                ByteBuffer.allocateDirect(rgba.length * Short.BYTES).order(ByteOrder.nativeOrder());
        for (float value : rgba) {
            pixels.putShort(Float.floatToFloat16(value));
        }
        pixels.flip();
        return new ImageFile(
                Path.of(name + ".png"),
                0,
                width,
                height,
                "4 (RGBA)",
                ImageFile.Depth.HALF.format,
                1,
                ImageFile.Depth.HALF,
                pixels,
                null);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public @Nullable Path file() {
        return null;
    }

    @Override
    public void drawContent(@NonNull UiFrame frame) {
        viewer.drawContent(frame);
    }

    @Override
    public void drawProperties(@NonNull UiFrame frame) {
        viewer.drawProperties(frame);
    }

    @Override
    public boolean canCapture() {
        return true;
    }

    @Override
    public CompletableFuture<Path> capture(@NonNull Path file) {
        return viewer.capture(file);
    }

    @Override
    public void close() {
        viewer.close();
    }
}
