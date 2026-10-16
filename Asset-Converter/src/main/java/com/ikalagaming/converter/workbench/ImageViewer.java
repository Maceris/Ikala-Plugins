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

import javax.annotation.Nullable;

/**
 * Shows an image the way an image viewer does: pan by dragging, zoom with the wheel around the
 * cursor, one channel at a time, any mip level, with an exposure for HDR images, and the value of
 * the pixel under the mouse. Textures whose format can be sRGB or linear can be switched between
 * the two while shown. Draws with plain IkGui calls inside the workbench's immediate areas. Render
 * thread only.
 */
public class ImageViewer {
    /** The view's background, behind the image. */
    private static final int BACKGROUND = Color.rgba(0.08f, 0.08f, 0.09f, 1.0f);

    /** The outline around the image, so its edges show against the background. */
    private static final int OUTLINE = Color.rgba(0.35f, 0.35f, 0.4f, 1.0f);

    /** The highlight around the hovered pixel, once pixels are large enough to see. */
    private static final int HOVER_OUTLINE = Color.rgba(1.0f, 0.85f, 0.3f, 1.0f);

    /** Pixels at least this large on screen get the hover outline. */
    private static final float HOVER_OUTLINE_SCALE = 8;

    /** The exposure slider's range, in stops either way. */
    private static final float EXPOSURE_RANGE = 10;

    /**
     * The image shown.
     *
     * @return The image, or null if none is open.
     */
    @Getter private @Nullable ImageFile image;

    /**
     * The texture the image was loaded as.
     *
     * @return The texture, or null if none is open.
     */
    @Getter private @Nullable TextureHandle texture;

    /** The color space the texture is read in. */
    private ColorSpace colorSpace = ColorSpace.SRGB;

    /** Switches textures between color spaces. */
    private final Textures textures;

    /** Where the image sits in the view. */
    private final ImageViewport viewport = new ImageViewport();

    /** Whether to fit the image to the view on the next draw, once its size is known. */
    private boolean fitPending;

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

    /** The image pixel under the mouse, or -1 if it isn't over the image. */
    private int hoverX = -1;

    /** The image pixel under the mouse, or -1 if it isn't over the image. */
    private int hoverY = -1;

    /** The hovered pixel's value, reused. */
    private final float[] hoverValue = new float[ImageFile.CHANNELS];

    /** Whether the hovered pixel could be read back. */
    private boolean hoverKnown;

    /** Scratch for reading the mouse and layout, reused. */
    private final Vector2f scratch = new Vector2f();

    /**
     * Set up the viewer.
     *
     * @param textures Switches the shown texture between color spaces.
     */
    public ImageViewer(@NonNull Textures textures) {
        this.textures = textures;
    }

    /**
     * Show a new image, fitted to the view, with the view settings guessed from it.
     *
     * @param newImage The image.
     * @param newTexture The texture it was loaded as.
     * @param loadedAs The color space it was loaded in.
     */
    public void show(
            @NonNull ImageFile newImage,
            @NonNull TextureHandle newTexture,
            @NonNull ColorSpace loadedAs) {
        image = newImage;
        texture = newTexture;
        colorSpace = loadedAs;
        fitPending = true;
        mipLevel[0] = -1;
        exposure[0] = 0;
        channel = TextureView.Channel.RGBA;
        linear = newImage.depth() == ImageFile.Depth.HALF;
        normalMap = ImageFile.looksLikeNormalMap(newImage.path().getFileName().toString());
        hoverX = -1;
        hoverY = -1;
    }

    /** Stop showing the image. The caller releases the texture. */
    public void clear() {
        image = null;
        texture = null;
    }

    /**
     * Draw the image, and pan and zoom it with the mouse. Fills the immediate area.
     *
     * @param frame The current frame.
     */
    public void drawView(@NonNull UiFrame frame) {
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

        final ImageFile shown = image;
        final TextureHandle shownTexture = texture;
        if (shown == null || shownTexture == null) {
            hoverX = -1;
            hoverY = -1;
            IkGui.setCursorScreenPos(origin.x + 8, origin.y + 8);
            IkGui.textDisabled("Pick an image in the file list to view it");
            return;
        }
        if (fitPending) {
            viewport.fit(shown.width(), shown.height(), width, height);
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
            viewport.fit(shown.width(), shown.height(), width, height);
        }

        drawImage(drawList, shown, shownTexture, origin.x, origin.y, width, height);

        hoverX = -1;
        hoverY = -1;
        if (hovered) {
            final int x = viewport.imageX(cursorX);
            final int y = viewport.imageY(cursorY);
            if (x >= 0 && y >= 0 && x < shown.width() && y < shown.height()) {
                hoverX = x;
                hoverY = y;
                hoverKnown = shown.pixel(x, y, hoverValue) != null;
                outlineHovered(drawList, origin.x, origin.y);
            }
        }
    }

    /**
     * Draw the part of the image inside the view, and an outline around it.
     *
     * @param drawList Where to draw.
     * @param shown The image.
     * @param shownTexture Its texture.
     * @param left The view's left edge on screen.
     * @param top The view's top edge on screen.
     * @param width The view's width.
     * @param height The view's height.
     */
    private void drawImage(
            DrawList drawList,
            ImageFile shown,
            TextureHandle shownTexture,
            float left,
            float top,
            float width,
            float height) {
        final float scale = viewport.getScale();
        final float imageLeft = left + viewport.getOffsetX();
        final float imageTop = top + viewport.getOffsetY();
        final float imageRight = imageLeft + shown.width() * scale;
        final float imageBottom = imageTop + shown.height() * scale;
        // Only the visible part, with matching texture coordinates, so zooming far in doesn't
        // make a huge quad
        final float minX = Math.max(left, imageLeft);
        final float minY = Math.max(top, imageTop);
        final float maxX = Math.min(left + width, imageRight);
        final float maxY = Math.min(top + height, imageBottom);
        if (minX < maxX && minY < maxY) {
            final float imageWidth = imageRight - imageLeft;
            final float imageHeight = imageBottom - imageTop;
            drawList.addImageView(
                    shownTexture,
                    minX,
                    minY,
                    maxX,
                    maxY,
                    (minX - imageLeft) / imageWidth,
                    (minY - imageTop) / imageHeight,
                    (maxX - imageLeft) / imageWidth,
                    (maxY - imageTop) / imageHeight,
                    currentView());
        }
        drawList.addRect(imageLeft - 1, imageTop - 1, imageRight + 1, imageBottom + 1, OUTLINE);
    }

    /**
     * Outline the hovered pixel, once pixels are large enough for it to help.
     *
     * @param drawList Where to draw.
     * @param left The view's left edge on screen.
     * @param top The view's top edge on screen.
     */
    private void outlineHovered(DrawList drawList, float left, float top) {
        final float scale = viewport.getScale();
        if (scale < HOVER_OUTLINE_SCALE) {
            return;
        }
        final float x = left + viewport.getOffsetX() + hoverX * scale;
        final float y = top + viewport.getOffsetY() + hoverY * scale;
        drawList.addRect(x, y, x + scale, y + scale, HOVER_OUTLINE);
    }

    /**
     * How the shader should show the image, from the settings.
     *
     * @return The view.
     */
    TextureView currentView() {
        final ImageFile shown = image;
        // Textures read as sRGB are encoded by the GUI already
        final boolean encode = linear && shown != null && !ColorSpace.canSwitch(shown.format());
        return new TextureView(
                channel,
                mipLevel[0],
                exposure[0],
                nearestWhenZoomed && viewport.getScale() > 1,
                encode,
                normalMap,
                checker && channel == TextureView.Channel.RGBA);
    }

    /**
     * Draw the image's details and the view settings.
     *
     * @param frame The current frame.
     */
    public void drawProperties(@NonNull UiFrame frame) {
        final ImageFile shown = image;
        if (shown == null) {
            IkGui.textDisabled("No image open");
            return;
        }
        IkGui.separatorText("Image");
        if (IkGui.beginTable("info", 2, TableFlags.ROW_BACKGROUND)) {
            row("File", shown.path().getFileName().toString());
            row("Size", shown.width() + " x " + shown.height());
            row("Channels", shown.channels());
            if (shown.dds() == null) {
                row("Depth", shown.depth().label);
            }
            row("Read as", colorSpace.apply(shown.format()).name());
            row(
                    "Mip levels",
                    shown.mipLevels() + (shown.dds() == null ? ", made on load" : ", stored"));
            row("File size", sizeText(shown.fileSize()));
            row("Data size", sizeText(shown.dataBytes()));
            IkGui.endTable();
        }

        if (ColorSpace.canSwitch(shown.format())) {
            IkGui.separatorText("Color space");
            for (ColorSpace option : ColorSpace.values()) {
                if (option != ColorSpace.SRGB) {
                    IkGui.sameLine();
                }
                final String label = option == ColorSpace.SRGB ? "sRGB (color)" : "Linear (data)";
                if (IkGui.radioButton(label, colorSpace == option) && colorSpace != option) {
                    colorSpace = option;
                    textures.setColorSpace(texture, option);
                }
            }
            IkGui.textDisabled("Colors like base color are sRGB; normal and packed maps linear");
        }

        IkGui.separatorText("View");
        for (TextureView.Channel option : TextureView.Channel.values()) {
            if (option != TextureView.Channel.RGBA) {
                IkGui.sameLine();
            }
            if (IkGui.radioButton(option.label, channel == option)) {
                channel = option;
            }
        }
        IkGui.sliderInt(
                "Mip level", mipLevel, -1, shown.mipLevels() - 1, mipLevel[0] < 0 ? "auto" : "%d");
        IkGui.sliderFloat("Exposure", exposure, -EXPOSURE_RANGE, EXPOSURE_RANGE, "%.1f stops");
        if (!ColorSpace.canSwitch(shown.format())) {
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
        if (IkGui.button("Fit")) {
            viewport.fit(shown.width(), shown.height(), viewSize.x, viewSize.y);
        }
        IkGui.sameLine();
        if (IkGui.button("1:1")) {
            viewport.actualSize(shown.width(), shown.height(), viewSize.x, viewSize.y);
        }
        IkGui.sameLine();
        IkGui.text(String.format("%.0f%%", viewport.getScale() * 100));
        IkGui.textDisabled("Drag to pan, wheel to zoom, double click to fit");

        IkGui.separatorText("Pixel");
        if (hoverX < 0) {
            IkGui.textDisabled("Hover over the image");
        } else {
            IkGui.text("x " + hoverX + ", y " + hoverY);
            if (hoverKnown) {
                IkGui.text(pixelText(shown.depth(), hoverValue));
                IkGui.textDisabled("As stored in the top level");
            } else {
                IkGui.textDisabled("Can't read back " + shown.format().name());
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
