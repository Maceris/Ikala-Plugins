package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.Textures;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.data.TextureView;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.ButtonFlags;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.ui.UiFrame;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector2f;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

/**
 * Shows an image the way an image viewer does: pan by dragging, zoom with the wheel around the
 * cursor, one channel at a time, any mip level, with an exposure for HDR images, the value of the
 * pixel under the mouse, and 3x3 tiling to check that a texture repeats without seams. Measure
 * works out its average linear albedo, and capture saves it as shown. Textures whose format can be
 * sRGB or linear can be switched between the two while shown. The viewer owns its texture and
 * releases it on close. Draws with plain IkGui calls inside the host's immediate area. Render
 * thread only.
 */
public class TextureViewer implements Viewer {
    /** The view's background, behind the image. */
    private static final int BACKGROUND = Color.rgba(0.08f, 0.08f, 0.09f, 1.0f);

    /** The outline around the image, so its edges show against the background. */
    private static final int OUTLINE = Color.rgba(0.35f, 0.35f, 0.4f, 1.0f);

    /** The outline around the middle tile when tiled, so the original copy can be told apart. */
    private static final int TILE_OUTLINE = Color.rgba(0.55f, 0.55f, 0.6f, 0.6f);

    /** The highlight around the hovered pixel, once pixels are large enough to see. */
    private static final int HOVER_OUTLINE = Color.rgba(1.0f, 0.85f, 0.3f, 1.0f);

    /** Pixels at least this large on screen get the hover outline. */
    private static final float HOVER_OUTLINE_SCALE = 8;

    /** The exposure slider's range, in stops either way. */
    private static final float EXPOSURE_RANGE = 10;

    /** How many copies across and down a tiled view shows. */
    static final int TILES = 3;

    /** The widest or tallest capture, in pixels, so a tiled large image can't run out of memory. */
    static final int MAX_CAPTURE_SIDE = 16_384;

    /**
     * The image shown.
     *
     * @return The image.
     */
    @Getter private ImageFile image;

    /**
     * The texture the image was loaded as, which the viewer owns.
     *
     * @return The texture.
     */
    @Getter private TextureHandle texture;

    /** The color space the texture is read in. */
    private ColorSpace colorSpace;

    /** Loads and releases textures, and switches their color spaces. */
    private final Textures textures;

    /** Where captures are encoded and written, off the render thread. */
    private final Executor background;

    /** Uses the shown image as the sky, or null if it can't be, as for a swatch. */
    private final @Nullable Consumer<ImageFile> useAsSky;

    /** What the toolbar calls this viewer. */
    private final String kind;

    /** Whether the toolbar controls and full properties show; a swatch hides them. */
    private final boolean chrome;

    /** Where the image sits in the view. */
    private final ImageViewport viewport = new ImageViewport();

    /** Whether to fit the image to the view on the next draw, once its size is known. */
    private boolean fitPending = true;

    /** The view's size last frame, for the fit and 1:1 buttons. */
    private final Vector2f viewSize = new Vector2f();

    /** Which channels to show. */
    private TextureView.Channel channel = TextureView.Channel.RGBA;

    /** The mip level to show, -1 to pick from the size on screen. */
    private final int[] mipLevel = {-1};

    /** How many stops brighter to show the colors. */
    private final float[] exposure = {0};

    /**
     * Whether a texture that can't be sRGB holds linear light to encode to sRGB to show, like an
     * HDR image. Textures read as sRGB are always encoded.
     */
    private boolean linear;

    /** Whether to rebuild a tangent space normal from x and y. */
    private boolean normalMap;

    /** Whether to show the image over a checkerboard by its alpha. */
    private boolean checker = true;

    /** Whether to show texels as hard edged squares once they are larger than screen pixels. */
    private boolean nearestWhenZoomed = true;

    /** Whether to show 3x3 copies, to see the seams where the texture repeats. */
    private boolean tiled;

    /** The image pixel under the mouse, or -1 if it isn't over the image. */
    private int hoverX = -1;

    /** The image pixel under the mouse, or -1 if it isn't over the image. */
    private int hoverY = -1;

    /** The hovered pixel's value, reused. */
    private final float[] hoverValue = new float[ImageFile.CHANNELS];

    /** Whether the hovered pixel could be read back. */
    private boolean hoverKnown;

    /** The last measured average linear red, green, blue and luminance, or null. */
    private @Nullable float[] measured;

    /** Scratch for reading the mouse and layout, reused. */
    private final Vector2f scratch = new Vector2f();

    /**
     * Show an image, fitted to the view, with the view settings guessed from it.
     *
     * @param textures Releases the texture on close, and switches its color space.
     * @param background Where captures are encoded and written.
     * @param image The image.
     * @param texture The texture it was loaded as, which the viewer now owns.
     * @param loadedAs The color space it was loaded in.
     * @param useAsSky Uses an image as the sky, for HDR environment maps.
     */
    public TextureViewer(
            @NonNull Textures textures,
            @NonNull Executor background,
            @NonNull ImageFile image,
            @NonNull TextureHandle texture,
            @NonNull ColorSpace loadedAs,
            @NonNull Consumer<ImageFile> useAsSky) {
        this(textures, background, image, texture, loadedAs, useAsSky, "Texture", true);
    }

    /**
     * Show an image.
     *
     * @param textures Releases the texture on close, and switches its color space.
     * @param background Where captures are encoded and written.
     * @param image The image.
     * @param texture The texture it was loaded as, which the viewer now owns.
     * @param loadedAs The color space it was loaded in.
     * @param useAsSky Uses an image as the sky, or null if it can't be.
     * @param kind What the toolbar calls this viewer.
     * @param chrome Whether the toolbar controls and full properties show.
     */
    TextureViewer(
            @NonNull Textures textures,
            @NonNull Executor background,
            @NonNull ImageFile image,
            @NonNull TextureHandle texture,
            @NonNull ColorSpace loadedAs,
            @Nullable Consumer<ImageFile> useAsSky,
            @NonNull String kind,
            boolean chrome) {
        this.textures = textures;
        this.background = background;
        this.useAsSky = useAsSky;
        this.kind = kind;
        this.chrome = chrome;
        this.image = image;
        this.texture = texture;
        colorSpace = loadedAs;
        linear = image.depth() == ImageFile.Depth.HALF;
        normalMap = ImageFile.looksLikeNormalMap(image.path().getFileName().toString());
    }

    /**
     * Show new pixels in place of the image, keeping the view settings, as a swatch does when what
     * it shows changes. The old texture is released.
     *
     * @param newImage The image.
     * @param newTexture Its texture, which the viewer now owns.
     */
    void replace(@NonNull ImageFile newImage, @NonNull TextureHandle newTexture) {
        final boolean resized =
                newImage.width() != image.width() || newImage.height() != image.height();
        final TextureHandle old = texture;
        image = newImage;
        texture = newTexture;
        measured = null;
        if (resized) {
            fitPending = true;
        }
        if (old != newTexture) {
            textures.release(old);
        }
    }

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public @Nullable Path file() {
        return chrome ? image.path() : null;
    }

    @Override
    public void close() {
        textures.release(texture);
    }

    /**
     * Whether 3x3 copies are shown.
     *
     * @return True if tiled.
     */
    public boolean isTiled() {
        return tiled;
    }

    /**
     * Show 3x3 copies, or one.
     *
     * @param tile Whether to tile.
     */
    public void setTiled(boolean tile) {
        if (tile != tiled) {
            tiled = tile;
            fitPending = true;
        }
    }

    /**
     * How many copies across and down are shown.
     *
     * @return 3 when tiled, 1 otherwise.
     */
    private int tiles() {
        return tiled ? TILES : 1;
    }

    @Override
    public void drawToolbar(@NonNull UiFrame frame) {
        if (!chrome) {
            return;
        }
        for (TextureView.Channel option : TextureView.Channel.values()) {
            IkGui.sameLine();
            if (IkGui.radioButton(option.label, channel == option)) {
                channel = option;
            }
        }
        IkGui.sameLine();
        if (IkGui.checkbox("Tile 3x3", tiled)) {
            setTiled(!tiled);
        }
        IkGui.setItemTooltip("Show the texture repeated, to see the seams where its edges meet");
        IkGui.sameLine();
        if (IkGui.button("Fit")) {
            fit();
        }
        IkGui.sameLine();
        if (IkGui.button("1:1")) {
            viewport.actualSize(
                    image.width() * tiles(), image.height() * tiles(), viewSize.x, viewSize.y);
        }
        IkGui.sameLine();
        IkGui.text(String.format("%.0f%%", viewport.getScale() * 100));
    }

    /** Fit the image, or its tiles, to the view. */
    private void fit() {
        viewport.fit(image.width() * tiles(), image.height() * tiles(), viewSize.x, viewSize.y);
    }

    /**
     * Draw the image, and pan and zoom it with the mouse. Fills the immediate area.
     *
     * @param frame The current frame.
     */
    @Override
    public void drawContent(@NonNull UiFrame frame) {
        final DrawList drawList = IkGui.getWindowDrawList();
        final Vector2f origin = IkGui.getCursorScreenPos();
        IkGui.getContentRegionAvailable(viewSize);
        final float width = Math.max(1, viewSize.x);
        final float height = Math.max(1, viewSize.y);
        drawList.addRectFilled(origin.x, origin.y, origin.x + width, origin.y + height, BACKGROUND);

        // Catches the mouse, so dragging pans the image instead of moving the window or camera
        IkGui.invisibleButton(
                "image",
                width,
                height,
                ButtonFlags.MOUSE_BUTTON_LEFT | ButtonFlags.MOUSE_BUTTON_MIDDLE);
        final boolean hovered = IkGui.isItemHovered();
        final boolean active = IkGui.isItemActive();

        final int tiles = tiles();
        if (fitPending) {
            viewport.fit(image.width() * tiles, image.height() * tiles, width, height);
            fitPending = false;
        }

        final IkIO io = IkGui.getIO();
        IkGui.getMousePos(scratch);
        final float cursorX = scratch.x - origin.x;
        final float cursorY = scratch.y - origin.y;
        if (active
                && (IkGui.isMouseDragging(MouseButton.LEFT, 0)
                        || IkGui.isMouseDragging(MouseButton.MIDDLE, 0))) {
            viewport.pan(io.mouseDelta.x, io.mouseDelta.y);
        }
        if (hovered && io.mouseWheel != 0) {
            viewport.zoom(io.mouseWheel, cursorX, cursorY);
        }
        if (hovered && IkGui.isMouseDoubleClicked(MouseButton.LEFT)) {
            viewport.fit(image.width() * tiles, image.height() * tiles, width, height);
        }

        drawImage(drawList, origin.x, origin.y, width, height, tiles);

        hoverX = -1;
        hoverY = -1;
        if (hovered) {
            final int x = viewport.imageX(cursorX);
            final int y = viewport.imageY(cursorY);
            if (x >= 0 && y >= 0 && x < image.width() * tiles && y < image.height() * tiles) {
                hoverX = x % image.width();
                hoverY = y % image.height();
                hoverKnown = image.pixel(hoverX, hoverY, hoverValue) != null;
                outlineHovered(drawList, origin.x, origin.y, x, y);
            }
        }
    }

    /**
     * Draw the parts of the image's copies inside the view, and an outline around them.
     *
     * @param drawList Where to draw.
     * @param left The view's left edge on screen.
     * @param top The view's top edge on screen.
     * @param width The view's width.
     * @param height The view's height.
     * @param tiles How many copies across and down.
     */
    private void drawImage(
            DrawList drawList, float left, float top, float width, float height, int tiles) {
        final float scale = viewport.getScale();
        final float tileWidth = image.width() * scale;
        final float tileHeight = image.height() * scale;
        final float originX = left + viewport.getOffsetX();
        final float originY = top + viewport.getOffsetY();
        final TextureView view = currentView();
        for (int ty = 0; ty < tiles; ++ty) {
            for (int tx = 0; tx < tiles; ++tx) {
                final float imageLeft = originX + tx * tileWidth;
                final float imageTop = originY + ty * tileHeight;
                final float imageRight = imageLeft + tileWidth;
                final float imageBottom = imageTop + tileHeight;
                // Only the visible part, with matching texture coordinates, so zooming far in
                // doesn't make a huge quad
                final float minX = Math.max(left, imageLeft);
                final float minY = Math.max(top, imageTop);
                final float maxX = Math.min(left + width, imageRight);
                final float maxY = Math.min(top + height, imageBottom);
                if (minX < maxX && minY < maxY) {
                    drawList.addImageView(
                            texture,
                            minX,
                            minY,
                            maxX,
                            maxY,
                            (minX - imageLeft) / tileWidth,
                            (minY - imageTop) / tileHeight,
                            (maxX - imageLeft) / tileWidth,
                            (maxY - imageTop) / tileHeight,
                            view);
                }
            }
        }
        drawList.addRect(
                originX - 1,
                originY - 1,
                originX + tiles * tileWidth + 1,
                originY + tiles * tileHeight + 1,
                OUTLINE);
        if (tiles > 1) {
            final int middle = tiles / 2;
            final float x = originX + middle * tileWidth;
            final float y = originY + middle * tileHeight;
            drawList.addRect(x, y, x + tileWidth, y + tileHeight, TILE_OUTLINE);
        }
    }

    /**
     * Outline the hovered pixel, once pixels are large enough for it to help.
     *
     * @param drawList Where to draw.
     * @param left The view's left edge on screen.
     * @param top The view's top edge on screen.
     * @param x The hovered pixel across all the copies.
     * @param y The hovered pixel down all the copies.
     */
    private void outlineHovered(DrawList drawList, float left, float top, int x, int y) {
        final float scale = viewport.getScale();
        if (scale < HOVER_OUTLINE_SCALE) {
            return;
        }
        final float screenX = left + viewport.getOffsetX() + x * scale;
        final float screenY = top + viewport.getOffsetY() + y * scale;
        drawList.addRect(screenX, screenY, screenX + scale, screenY + scale, HOVER_OUTLINE);
    }

    /**
     * Whether the shader encodes the texture's values to sRGB to show them: a texture that can't be
     * read as sRGB but holds linear light, like an HDR image. Textures read as sRGB are encoded by
     * the GUI already.
     *
     * @return True if the view encodes.
     */
    private boolean encodes() {
        return linear && !ColorSpace.canSwitch(image.format());
    }

    /**
     * Whether the stored values are sRGB encoded colors, which the GPU decodes when sampled.
     *
     * @return True if read as sRGB.
     */
    private boolean readAsSrgb() {
        return colorSpace == ColorSpace.SRGB && ColorSpace.canSwitch(image.format());
    }

    /**
     * How the shader should show the image, from the settings.
     *
     * @return The view.
     */
    TextureView currentView() {
        return new TextureView(
                channel,
                mipLevel[0],
                exposure[0],
                nearestWhenZoomed && viewport.getScale() > 1,
                encodes(),
                normalMap,
                checker && channel == TextureView.Channel.RGBA);
    }

    /**
     * Work out the image's average linear albedo, for the properties panel: from its pixels if they
     * can be read back, else from the average worked out when the texture loaded.
     */
    public void measure() {
        measured = average(image, readAsSrgb());
        if (measured == null) {
            final float[] loaded = textures.average(texture);
            if (loaded != null) {
                measured =
                        new float[] {
                            loaded[0],
                            loaded[1],
                            loaded[2],
                            luminance(loaded[0], loaded[1], loaded[2])
                        };
            }
        }
    }

    /**
     * The last measure: average linear red, green, blue, then luminance.
     *
     * @return The values, or null if not measured since the image last changed.
     */
    public @Nullable float[] getMeasured() {
        return measured;
    }

    /**
     * The average linear color of an image's top level, weighted by alpha so cut-out pixels don't
     * count, and its luminance.
     *
     * @param image The image.
     * @param srgb Whether its values are sRGB encoded, to decode first.
     * @return Red, green, blue and luminance, or null if its pixels can't be read back or it is
     *     fully transparent.
     */
    static @Nullable float[] average(@NonNull ImageFile image, boolean srgb) {
        if (image.pixels() == null) {
            return null;
        }
        final float[] pixel = new float[ImageFile.CHANNELS];
        double r = 0;
        double g = 0;
        double b = 0;
        double weight = 0;
        for (int y = 0; y < image.height(); ++y) {
            for (int x = 0; x < image.width(); ++x) {
                image.pixel(x, y, pixel);
                final double alpha = pixel[3];
                r += alpha * (srgb ? toLinear(pixel[0]) : pixel[0]);
                g += alpha * (srgb ? toLinear(pixel[1]) : pixel[1]);
                b += alpha * (srgb ? toLinear(pixel[2]) : pixel[2]);
                weight += alpha;
            }
        }
        if (weight <= 0) {
            return null;
        }
        final float red = (float) (r / weight);
        final float green = (float) (g / weight);
        final float blue = (float) (b / weight);
        return new float[] {red, green, blue, luminance(red, green, blue)};
    }

    /**
     * The luminance of a linear color, with the Rec. 709 weights.
     *
     * @param r Red.
     * @param g Green.
     * @param b Blue.
     * @return The luminance.
     */
    static float luminance(float r, float g, float b) {
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    /**
     * Decode an sRGB value to linear light.
     *
     * @param value The encoded value, 0 to 1.
     * @return The linear value.
     */
    static float toLinear(float value) {
        return value <= 0.040_45f
                ? value / 12.92f
                : (float) Math.pow((value + 0.055f) / 1.055f, 2.4f);
    }

    /**
     * Encode linear light to an sRGB value.
     *
     * @param value The linear value.
     * @return The encoded value, 0 to 1.
     */
    static float toSrgb(float value) {
        final float clamped = Math.clamp(value, 0, 1);
        return clamped <= 0.003_130_8f
                ? clamped * 12.92f
                : (float) (1.055f * Math.pow(clamped, 1 / 2.4f) - 0.055f);
    }

    @Override
    public boolean canCapture() {
        return image.pixels() != null;
    }

    @Override
    public CompletableFuture<Path> capture(@NonNull Path file) {
        if (image.pixels() == null) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException(
                            "Can't read back " + image.format().name() + " to capture it"));
        }
        final Settings settings =
                new Settings(channel, exposure[0], readAsSrgb(), encodes(), normalMap, tiles());
        final ImageFile shown = image;
        return CompletableFuture.supplyAsync(
                () -> {
                    final BufferedImage rendered = render(shown, settings);
                    try {
                        final Path parent = file.toAbsolutePath().getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        if (!ImageIO.write(rendered, "png", file.toFile())) {
                            throw new IOException("No PNG writer");
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return file;
                },
                background);
    }

    /**
     * How a capture shows the image, as the view does.
     *
     * @param channel Which channels.
     * @param exposure How many stops brighter.
     * @param srgb Whether the stored values are sRGB encoded.
     * @param encode Whether the values are linear light to encode to sRGB.
     * @param normalMap Whether to rebuild z from x and y.
     * @param tiles How many copies across and down.
     */
    record Settings(
            @NonNull TextureView.Channel channel,
            float exposure,
            boolean srgb,
            boolean encode,
            boolean normalMap,
            int tiles) {}

    /**
     * Draw an image as the view shows it, into 8 bit sRGB pixels: the chosen channels, brightened
     * by the exposure, and repeated for tiling.
     *
     * @param image The image, whose pixels can be read back.
     * @param settings How to show it.
     * @return The picture.
     * @throws IllegalArgumentException If it would be larger than {@link #MAX_CAPTURE_SIDE}.
     */
    static BufferedImage render(@NonNull ImageFile image, @NonNull Settings settings) {
        final int width = image.width() * settings.tiles();
        final int height = image.height() * settings.tiles();
        if (width > MAX_CAPTURE_SIDE || height > MAX_CAPTURE_SIDE) {
            throw new IllegalArgumentException(
                    "A capture of " + width + " x " + height + " pixels is too large");
        }
        final BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final float[] pixel = new float[ImageFile.CHANNELS];
        final int[] row = new int[image.width()];
        for (int y = 0; y < image.height(); ++y) {
            for (int x = 0; x < image.width(); ++x) {
                image.pixel(x, y, pixel);
                row[x] = shade(pixel, settings);
            }
            for (int ty = 0; ty < settings.tiles(); ++ty) {
                for (int tx = 0; tx < settings.tiles(); ++tx) {
                    out.setRGB(
                            tx * image.width(),
                            ty * image.height() + y,
                            image.width(),
                            1,
                            row,
                            0,
                            image.width());
                }
            }
        }
        return out;
    }

    /**
     * Shade one pixel as the view shows it.
     *
     * @param pixel Red, green, blue and alpha as stored; changed.
     * @param settings How to show it.
     * @return The pixel as 8 bit ARGB.
     */
    static int shade(float @NonNull [] pixel, @NonNull Settings settings) {
        if (settings.normalMap()) {
            final float nx = pixel[0] * 2 - 1;
            final float ny = pixel[1] * 2 - 1;
            final float nz = (float) Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
            pixel[2] = nz * 0.5f + 0.5f;
        }
        final float alpha;
        switch (settings.channel()) {
            case RGBA -> alpha = pixel[3];
            case RGB -> alpha = 1;
            default -> {
                final int index = settings.channel().ordinal() - TextureView.Channel.RED.ordinal();
                final float value = pixel[index];
                pixel[0] = value;
                pixel[1] = value;
                pixel[2] = value;
                alpha = 1;
            }
        }
        final float gain = (float) Math.pow(2, settings.exposure());
        final boolean color =
                settings.channel() == TextureView.Channel.RGBA
                        || settings.channel() == TextureView.Channel.RGB;
        int argb = Math.round(Math.clamp(alpha, 0, 1) * 255) << 24;
        for (int c = 0; c < 3; ++c) {
            final float shown;
            if (settings.srgb() && color) {
                shown = toSrgb(toLinear(pixel[c]) * gain);
            } else if (settings.encode()) {
                shown = toSrgb(pixel[c] * gain);
            } else {
                shown = pixel[c] * gain;
            }
            argb |= Math.round(Math.clamp(shown, 0, 1) * 255) << (16 - 8 * c);
        }
        return argb;
    }

    /**
     * Draw the image's details and the view settings.
     *
     * @param frame The current frame.
     */
    @Override
    public void drawProperties(@NonNull UiFrame frame) {
        IkGui.separatorText(chrome ? "Image" : kind);
        if (IkGui.beginTable("info", 2, TableFlags.ROW_BACKGROUND)) {
            if (chrome) {
                row("File", image.path().getFileName().toString());
            }
            row("Size", image.width() + " x " + image.height());
            if (chrome) {
                row("Channels", image.channels());
                if (image.dds() == null) {
                    row("Depth", image.depth().label);
                }
                row("Read as", colorSpace.apply(image.format()).name());
                row(
                        "Mip levels",
                        image.mipLevels() + (image.dds() == null ? ", made on load" : ", stored"));
                row("File size", sizeText(image.fileSize()));
                row("Data size", sizeText(image.dataBytes()));
            }
            IkGui.endTable();
        }
        drawMeasure();
        if (!chrome) {
            drawPixel();
            return;
        }
        if (useAsSky != null
                && image.depth() == ImageFile.Depth.HALF
                && image.pixels() != null
                && image.width() == 2 * image.height()) {
            if (IkGui.button("Use as the sky")) {
                useAsSky.accept(image);
            }
            IkGui.setItemTooltip(
                    "Show this environment map around the scene and light it by the map, its"
                            + " reflections by roughness and its diffuse light by direction.");
        }

        if (ColorSpace.canSwitch(image.format())) {
            IkGui.separatorText("Color space");
            for (ColorSpace option : ColorSpace.values()) {
                if (option != ColorSpace.SRGB) {
                    IkGui.sameLine();
                }
                final String label = option == ColorSpace.SRGB ? "sRGB (color)" : "Linear (data)";
                if (IkGui.radioButton(label, colorSpace == option) && colorSpace != option) {
                    colorSpace = option;
                    measured = null;
                    textures.setColorSpace(texture, option);
                }
            }
            IkGui.textDisabled("Colors like base color are sRGB; normal and packed maps linear");
        }

        IkGui.separatorText("View");
        IkGui.sliderInt(
                "Mip level", mipLevel, -1, image.mipLevels() - 1, mipLevel[0] < 0 ? "auto" : "%d");
        IkGui.sliderFloat("Exposure", exposure, -EXPOSURE_RANGE, EXPOSURE_RANGE, "%.1f stops");
        if (!ColorSpace.canSwitch(image.format())) {
            if (IkGui.checkbox("Linear light (encode to sRGB)", linear)) {
                linear = !linear;
            }
            IkGui.setItemTooltip(
                    "For images that store light linearly, like HDR images. This format can't be"
                            + " read as sRGB.");
        }
        if (IkGui.checkbox("Normal map", normalMap)) {
            normalMap = !normalMap;
        }
        IkGui.setItemTooltip("Rebuild z from x and y, so two channel normal maps show too");
        if (IkGui.checkbox("Checkerboard behind alpha", checker)) {
            checker = !checker;
        }
        if (IkGui.checkbox("Hard pixels when zoomed in", nearestWhenZoomed)) {
            nearestWhenZoomed = !nearestWhenZoomed;
        }
        IkGui.textDisabled("Drag to pan, wheel to zoom, double click to fit");
        drawPixel();
    }

    /** Draw the Measure button and the last measure. */
    private void drawMeasure() {
        if (IkGui.button("Measure")) {
            measure();
        }
        IkGui.setItemTooltip(
                "Work out the average linear albedo, weighted by alpha, to compare with real"
                        + " materials: most lie between 0.04 and 0.9");
        final float[] shown = measured;
        IkGui.sameLine();
        if (shown == null) {
            IkGui.textDisabled("Average albedo not measured");
        } else {
            IkGui.text(
                    String.format(
                            "Linear R %.3f  G %.3f  B %.3f, luminance %.3f",
                            shown[0], shown[1], shown[2], shown[3]));
        }
    }

    /** Says more about a pixel than its value, like the palette index it was made from. */
    @FunctionalInterface
    public interface PixelNote {
        /**
         * What to say about a pixel.
         *
         * @param x The column.
         * @param y The row.
         * @return The text, or null for nothing.
         */
        String note(int x, int y);
    }

    /** Says more about the hovered pixel, or null. */
    private PixelNote pixelNote;

    /**
     * Say more about the hovered pixel than its value.
     *
     * @param note What to say, or null for nothing.
     */
    public void setPixelNote(PixelNote note) {
        pixelNote = note;
    }

    /** Draw the hovered pixel's value. */
    private void drawPixel() {
        IkGui.separatorText("Pixel");
        if (hoverX < 0) {
            IkGui.textDisabled("Hover over the image");
        } else {
            IkGui.text("x " + hoverX + ", y " + hoverY);
            if (hoverKnown) {
                IkGui.text(pixelText(image.depth(), hoverValue));
                IkGui.textDisabled("As stored in the top level");
                final String note = pixelNote == null ? null : pixelNote.note(hoverX, hoverY);
                if (note != null) {
                    IkGui.text(note);
                }
            } else {
                IkGui.textDisabled("Can't read back " + image.format().name());
            }
        }
    }

    /**
     * Describe a pixel's value: 0 to 255 for 8 bit images, floats otherwise.
     *
     * @param depth How the image stores channels.
     * @param value The channels, as floats.
     * @return The text.
     */
    static String pixelText(@NonNull ImageFile.Depth depth, float @NonNull [] value) {
        if (depth == ImageFile.Depth.UNORM8) {
            return String.format(
                    "R %d  G %d  B %d  A %d",
                    Math.round(value[0] * 255),
                    Math.round(value[1] * 255),
                    Math.round(value[2] * 255),
                    Math.round(value[3] * 255));
        }
        return String.format(
                "R %.4f  G %.4f  B %.4f  A %.4f", value[0], value[1], value[2], value[3]);
    }

    /**
     * A size in bytes, kilobytes or megabytes, whichever reads best.
     *
     * @param bytes The size in bytes.
     * @return For example "512 bytes", "3.2 KB" or "1.5 MB".
     */
    static String sizeText(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Draw one two-column table row.
     *
     * @param name The first column.
     * @param value The second column.
     */
    private static void row(String name, String value) {
        IkGui.tableNextRow();
        IkGui.tableNextColumn();
        IkGui.textUnformatted(name);
        IkGui.tableNextColumn();
        IkGui.textUnformatted(value);
    }
}
