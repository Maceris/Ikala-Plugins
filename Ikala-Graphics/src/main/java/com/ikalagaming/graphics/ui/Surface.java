package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.style.ComputedStyle;
import com.ikalagaming.graphics.ui.style.Style;
import com.ikalagaming.graphics.ui.style.StyleKey;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector2f;

import java.util.List;

/**
 * The root of a piece of retained UI, drawn as one IkGui window. Its anchors and sizing place the
 * window on the screen, and its content is laid out inside the window.
 *
 * <p>Create surfaces through {@code GraphicsContext.ui()}, which owns them, so they are removed
 * when the plugin that made them unloads.
 */
public class Surface {
    /** Window flags every surface uses. */
    private static final int BASE_FLAGS =
            WindowFlags.NO_TITLE_BAR
                    | WindowFlags.NO_RESIZE
                    | WindowFlags.NO_COLLAPSE
                    | WindowFlags.NO_SAVED_SETTINGS
                    | WindowFlags.NO_SCROLLBAR
                    | WindowFlags.NO_SCROLL_WITH_MOUSE;

    /**
     * The ID, unique among all surfaces. -- GETTER -- The ID.
     *
     * @return The ID, unique among all surfaces.
     */
    @Getter private final String id;

    /**
     * The root of the content. -- GETTER -- The root of the content.
     *
     * @return The content node.
     */
    @Getter private Node<?> content;

    /** Where the window sits on the screen. */
    private Anchors anchors = Anchors.center();

    /** How the window width is decided. */
    private Sizing width = Sizing.fit();

    /** How the window height is decided. */
    private Sizing height = Sizing.fit();

    /** How the window stacks with other windows. */
    private Layer layer = Layer.NORMAL;

    /** Whether the user can move the window. */
    private boolean movable;

    /** Extra IkGui window flags. */
    private int extraFlags;

    /** The style classes, in order; later ones win. */
    private List<String> classes = List.of();

    /** The surface's own style, which wins over its classes. */
    private Style inline = Style.EMPTY;

    /**
     * Whether the surface is drawn. -- GETTER -- Whether the surface is drawn.
     *
     * @return True if it is shown.
     */
    @Getter private volatile boolean visible = true;

    /**
     * Create a surface with an empty column as its content.
     *
     * @param id The ID, unique among all surfaces.
     */
    public Surface(@NonNull String id) {
        this.id = id;
        content = new Column("content");
    }

    /**
     * Replace the root of the content.
     *
     * @param root The new content, which must not have a parent.
     * @return This surface.
     */
    public Surface content(@NonNull Node<?> root) {
        if (root.parent != null) {
            throw new IllegalArgumentException(root + " is already in " + root.parent);
        }
        content = root;
        return this;
    }

    /**
     * Set where the window sits on the screen.
     *
     * @param newAnchors The anchors, relative to the screen.
     * @return This surface.
     */
    public Surface anchors(@NonNull Anchors newAnchors) {
        anchors = newAnchors;
        return this;
    }

    /**
     * Set how the window width is decided. Fractions are of the screen.
     *
     * @param sizing The width.
     * @return This surface.
     */
    public Surface width(@NonNull Sizing sizing) {
        width = sizing;
        return this;
    }

    /**
     * Set how the window height is decided. Fractions are of the screen.
     *
     * @param sizing The height.
     * @return This surface.
     */
    public Surface height(@NonNull Sizing sizing) {
        height = sizing;
        return this;
    }

    /**
     * Set how the window stacks with other windows.
     *
     * @param newLayer The layer.
     * @return This surface.
     */
    public Surface layer(@NonNull Layer newLayer) {
        layer = newLayer;
        return this;
    }

    /**
     * Let the user move the window. It is placed by its anchors once, then stays where the user
     * puts it.
     *
     * @return This surface.
     */
    public Surface movable() {
        movable = true;
        return this;
    }

    /**
     * Set the style classes of the window, replacing any already set. The theme's {@code surface}
     * type style applies first.
     *
     * @param names The class names.
     * @return This surface.
     */
    public Surface classes(@NonNull String... names) {
        classes = List.of(names);
        return this;
    }

    /**
     * Set the window's own style, which wins over its classes.
     *
     * @param style The style.
     * @return This surface.
     */
    public Surface style(@NonNull Style style) {
        inline = style;
        return this;
    }

    /**
     * Make the window see-through and ignore all input, behind everything else. For HUDs.
     *
     * @return This surface.
     */
    public Surface hud() {
        layer = Layer.BACKGROUND;
        extraFlags |= WindowFlags.NO_BACKGROUND | WindowFlags.NO_INPUTS;
        return this;
    }

    /**
     * Don't draw the window background, while still taking input.
     *
     * @return This surface.
     */
    public Surface transparent() {
        extraFlags |= WindowFlags.NO_BACKGROUND;
        return this;
    }

    /**
     * Show or hide the surface.
     *
     * @param show Whether to draw it.
     */
    void setVisible(boolean show) {
        visible = show;
    }

    /**
     * The IkGui window flags for this surface.
     *
     * @return The flags.
     */
    int windowFlags() {
        int flags = BASE_FLAGS | extraFlags;
        if (!movable) {
            flags |= WindowFlags.NO_MOVE;
        }
        if (layer == Layer.BACKGROUND) {
            flags |= WindowFlags.NO_BRING_TO_FRONT_ON_FOCUS;
        }
        return flags;
    }

    /**
     * The IkGui window name, which only needs to be unique.
     *
     * @return The window name.
     */
    String windowName() {
        return "###ui/" + id;
    }

    /**
     * Work out where the window goes on the screen.
     *
     * @param viewport The usable area of the screen.
     * @param context The layout context.
     * @param windowPadding IkGui's padding inside windows, in pixels.
     * @return The window rectangle.
     */
    RectFloat place(
            @NonNull RectFloat viewport, @NonNull LayoutContext context, Vector2f windowPadding) {
        float[] contentFit = new float[2];
        LayoutEngine.fitSize(content, context, contentFit);
        float[] origin = {viewport.getLeft(), viewport.getTop()};
        float[] screen = {viewport.getWidth(), viewport.getHeight()};
        float[] padding = {windowPadding.x * 2, windowPadding.y * 2};
        float[] min = {anchors.minX(), anchors.minY()};
        float[] max = {anchors.maxX(), anchors.maxY()};
        Length[] offsetMin = {anchors.offsetMinX(), anchors.offsetMinY()};
        Length[] offsetMax = {anchors.offsetMaxX(), anchors.offsetMaxY()};
        Sizing[] sizing = {width, height};
        float fontPixels = context.fontSize() * context.scale();

        float[] start = new float[2];
        float[] size = new float[2];
        for (int i = 0; i < 2; ++i) {
            float low = offsetMin[i].resolve(screen[i], context.scale(), fontPixels);
            if (min[i] != max[i]) {
                float high = offsetMax[i].resolve(screen[i], context.scale(), fontPixels);
                start[i] = origin[i] + min[i] * screen[i] + low;
                size[i] = Math.max(0, (max[i] - min[i]) * screen[i] + high - low);
                continue;
            }
            size[i] =
                    switch (sizing[i]) {
                        case Sizing.Fixed fixed ->
                                fixed.length().resolve(screen[i], context.scale(), fontPixels);
                        case Sizing.Fit fit ->
                                clamp(
                                        contentFit[i] + padding[i],
                                        fit.min(),
                                        fit.max(),
                                        screen[i],
                                        context);
                        case Sizing.Grow grow ->
                                clamp(screen[i], grow.min(), grow.max(), screen[i], context);
                    };
            start[i] = origin[i] + min[i] * (screen[i] - size[i]) + low;
        }
        return new RectFloat(start[0], start[1], start[0] + size[0], start[1] + size[1]);
    }

    /**
     * Clamp a size.
     *
     * @param value The size.
     * @param min The minimum.
     * @param max The maximum.
     * @param screen The size of the screen on that axis.
     * @param context The layout context.
     * @return The clamped size.
     */
    private static float clamp(
            float value, Length min, Length max, float screen, LayoutContext context) {
        float fontPixels = context.fontSize() * context.scale();
        float low = min.resolve(screen, context.scale(), fontPixels);
        float high =
                max.units() == Float.MAX_VALUE
                        ? Float.MAX_VALUE
                        : max.resolve(screen, context.scale(), fontPixels);
        return Math.clamp(value, low, high);
    }

    /**
     * Draw the surface as an IkGui window. Render thread only, between IkGui's new frame and
     * render.
     *
     * @param viewport The usable area of the screen.
     * @param frame Details about this frame.
     * @param engine The layout engine.
     */
    void draw(@NonNull RectFloat viewport, @NonNull UiFrame frame, @NonNull LayoutEngine engine) {
        final LayoutContext layoutContext = frame.context();
        final float fontPixels = layoutContext.fontSize() * layoutContext.scale();
        final ComputedStyle style = layoutContext.theme().compute("surface", classes, inline);
        final Insets padding = style.insets(StyleKey.PADDING);
        final Vector2f windowPadding =
                padding == null
                        ? new Vector2f(IkGui.getStyle().variable.windowPadding)
                        : new Vector2f(
                                padding.left().resolve(0, layoutContext.scale(), fontPixels),
                                padding.top().resolve(0, layoutContext.scale(), fontPixels));
        final RectFloat window = place(viewport, layoutContext, windowPadding);
        final Condition condition = movable ? Condition.ONCE : Condition.ALWAYS;
        IkGui.setNextWindowPos(window.getLeft(), window.getTop(), condition);
        IkGui.setNextWindowSize(window.getWidth(), window.getHeight(), condition);
        // The window reads its colors, borders and padding when it begins
        final IkGuiStyler.Pushed pushed =
                IkGuiStyler.push(
                        IkGuiStyler.pushes(
                                IkGuiStyler.Kind.SURFACE,
                                style,
                                padding,
                                layoutContext.scale(),
                                fontPixels));
        final boolean open = IkGui.begin(windowName(), windowFlags());
        pushed.pop();
        switch (layer) {
            case BACKGROUND ->
                    IkGuiInternal.bringWindowToDisplayBack(IkGuiInternal.getCurrentWindow());
            case OVERLAY ->
                    IkGuiInternal.bringWindowToDisplayFront(IkGuiInternal.getCurrentWindow());
            case NORMAL -> {}
        }
        if (open) {
            final LayoutContext context = frame.context();
            final Vector2f start = IkGui.getCursorScreenPos();
            final Vector2f available = IkGui.getContentRegionAvailable();
            engine.layout(content, start.x, start.y, available.x, available.y, context);
            // Text that doesn't set its own size follows the UI scale too
            final boolean scaled = context.scale() != 1;
            if (scaled) {
                IkGui.pushFontSize(Math.round(context.fontSize() * context.scale()));
            }
            content.submitTree(frame);
            if (scaled) {
                IkGui.popFont();
            }
        }
        IkGui.end();
    }
}
