package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.TextureView;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;

import java.util.function.Supplier;

/**
 * A texture drawn in the node's rectangle. With no size set, the node measures the size it was
 * given, so it can be laid out like any other leaf. It submits an item, so it can have a tooltip, a
 * context menu, and be dragged from or dropped on.
 */
public class Image extends Node<Image> {
    /** How the texture fills the node. */
    public enum Fit {
        /** Stretched to the whole rectangle. */
        STRETCH,
        /** As large as fits while keeping its shape, centered. */
        CONTAIN
    }

    /** A pixel-sharp view of the whole texture. */
    private static final TextureView NEAREST =
            new TextureView(TextureView.Channel.RGBA, -1, 0, true, false, false, false);

    /** The texture, or null to draw nothing. */
    private TextureHandle texture;

    /** Gives the texture each frame, or null to use {@link #texture}. */
    private Supplier<TextureHandle> source;

    /** The texture's width over its height, for {@link Fit#CONTAIN}. */
    private float aspect = 1;

    /** The size the node asks for, in UI units. */
    private float naturalWidth;

    /** The size the node asks for, in UI units. */
    private float naturalHeight;

    /** How the texture fills the node. */
    private Fit fit = Fit.CONTAIN;

    /** Whether each texel shows as a hard edged square, for pixel art. */
    private boolean pixelSharp;

    /** What clicking does, or null for nothing. */
    private Runnable onClick;

    /** Whether the mouse is over the image, reused each frame. */
    private final IkBoolean hovered = new IkBoolean(false);

    /** Whether the image is held down, reused each frame. */
    private final IkBoolean held = new IkBoolean(false);

    /**
     * Create an image node showing nothing yet.
     *
     * @param id The ID, unique among its siblings.
     */
    public Image(@NonNull String id) {
        super(id);
    }

    /**
     * Show a texture. Render thread only once the node is shown.
     *
     * @param handle The texture, or null to show nothing.
     * @param width The texture's width, in texels, for keeping its shape.
     * @param height The texture's height, in texels.
     * @return This node.
     */
    public Image texture(TextureHandle handle, int width, int height) {
        texture = handle;
        source = null;
        aspect = width > 0 && height > 0 ? (float) width / height : 1;
        return self();
    }

    /**
     * Show whatever texture a source gives each frame, like a preview's, which appears after its
     * first frame and changes when it is resized. Its shape comes from the texture's size.
     *
     * @param textures Gives the texture, or null to show nothing that frame.
     * @return This node.
     */
    public Image texture(@NonNull Supplier<TextureHandle> textures) {
        source = textures;
        texture = null;
        return self();
    }

    /**
     * Set the size the node asks for, before its parent's sizing.
     *
     * @param width The width, in UI units.
     * @param height The height, in UI units.
     * @return This node.
     */
    public Image size(float width, float height) {
        naturalWidth = width;
        naturalHeight = height;
        markDirty();
        return self();
    }

    /**
     * Set how the texture fills the node.
     *
     * @param newFit The fit.
     * @return This node.
     */
    public Image fit(@NonNull Fit newFit) {
        fit = newFit;
        return self();
    }

    /**
     * Show each texel as a hard edged square instead of filtering, for pixel art.
     *
     * @param sharp Whether to.
     * @return This node.
     */
    public Image pixelSharp(boolean sharp) {
        pixelSharp = sharp;
        return self();
    }

    /**
     * Do something when the image is clicked.
     *
     * @param action What to do, run on the render thread outside drawing, or null for nothing.
     * @return This node.
     */
    public Image onClick(Runnable action) {
        onClick = action;
        return self();
    }

    @Override
    public String styleType() {
        return "image";
    }

    @Override
    protected void measure(@NonNull LayoutContext context, float[] out) {
        out[0] = naturalWidth * context.scale();
        out[1] = naturalHeight * context.scale();
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        final RectFloat bounds = new RectFloat(rect);
        final int id = IkGui.getID("image");
        IkGui.setCursorScreenPos(bounds.getLeft(), bounds.getTop());
        IkGuiInternal.itemSize(bounds.getWidth(), bounds.getHeight());
        recordItem(id);
        if (!IkGuiInternal.itemAdd(bounds, id)) {
            return;
        }
        // Pressing makes it the active item, which dragging from it needs
        final boolean pressed = IkGuiInternal.buttonBehavior(bounds, id, hovered, held, 0);
        if (pressed && onClick != null) {
            frame.fire(onClick);
        }
        final TextureHandle shown = source == null ? texture : source.get();
        if (shown == null) {
            return;
        }
        final float shape =
                source == null || shown.height() <= 0
                        ? aspect
                        : (float) shown.width() / shown.height();
        float minX = bounds.getLeft();
        float minY = bounds.getTop();
        float maxX = bounds.getRight();
        float maxY = bounds.getBottom();
        if (fit == Fit.CONTAIN) {
            final float width = bounds.getWidth();
            final float height = bounds.getHeight();
            final float shownWidth = Math.min(width, height * shape);
            final float shownHeight = shownWidth / shape;
            minX += (width - shownWidth) / 2;
            minY += (height - shownHeight) / 2;
            maxX = minX + shownWidth;
            maxY = minY + shownHeight;
        }
        final DrawList drawList = IkGui.getWindowDrawList();
        if (pixelSharp) {
            drawList.addImageView(shown, minX, minY, maxX, maxY, 0, 0, 1, 1, NEAREST);
        } else {
            drawList.addImage(shown, minX, minY, maxX, maxY);
        }
    }
}
