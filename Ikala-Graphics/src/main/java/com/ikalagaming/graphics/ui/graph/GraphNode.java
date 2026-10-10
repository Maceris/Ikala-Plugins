package com.ikalagaming.graphics.ui.graph;

import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.flags.DrawFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.CustomItem;
import com.ikalagaming.graphics.ui.ItemState;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.style.StyleKey;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector2f;

import java.util.Locale;

/**
 * A node for graphs, like a quest or a skill: a framed shape with an optional icon, a badge like
 * {@code 3/3}, and a label. It is a real IkGui item, so it can be clicked and navigated to.
 *
 * <p>Its look comes from the theme, as the style type {@code graph-node}: {@code background},
 * {@code border} and {@code borderSize} for the frame, {@code glow} and {@code glowSize} for a soft
 * glow around it, {@code accent} for the badge, {@code text} for the label, and {@code shape}. Give
 * nodes classes like {@code locked} or {@code done} to change their look by state.
 */
public class GraphNode extends CustomItem<GraphNode> {
    /** The shape of a node's frame. */
    public enum Shape {
        /** A rectangle. */
        RECT,
        /** A rectangle with rounded corners. */
        ROUNDED,
        /** A rectangle with its corners cut off. */
        OCTAGON,
        /** A hexagon, pointed at the left and right. */
        HEXAGON;

        /**
         * Read a shape from a style or spec.
         *
         * @param words The text, like {@code octagon}.
         * @return The shape, or null if it isn't one.
         */
        public static Shape parse(String words) {
            if (words == null) {
                return null;
            }
            try {
                return valueOf(words.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** The frame color when the theme doesn't set one. */
    private static final int DEFAULT_BACKGROUND = 0x2B2B30FF;

    /** The border color when the theme doesn't set one. */
    private static final int DEFAULT_BORDER = 0x8C8C8CFF;

    /** The badge color when the theme doesn't set one. */
    private static final int DEFAULT_ACCENT = 0xFFC83CFF;

    /** The badge text color. */
    private static final int BADGE_TEXT = 0x141414FF;

    /** The label color when the theme doesn't set one. */
    private static final int DEFAULT_TEXT = 0xFFFFFFFF;

    /** The border width when the theme doesn't set one, in UI units. */
    private static final float DEFAULT_BORDER_SIZE = 2;

    /** How far a glow reaches when the theme sets a color but no size, in UI units. */
    private static final float DEFAULT_GLOW_SIZE = 8;

    /**
     * The shape, or null to use the style's.
     *
     * @return The shape, which may be null.
     */
    @Getter private Shape shape;

    /**
     * The icon, or null for none.
     *
     * @return The icon.
     */
    @Getter private TextureHandle icon;

    /**
     * The badge text, or null for none.
     *
     * @return The badge.
     */
    @Getter private String badge;

    /**
     * The label, or null for none.
     *
     * @return The label.
     */
    @Getter private String label;

    /**
     * Create a node, 48 UI units square.
     *
     * @param id The ID, which must be unique among its siblings.
     */
    public GraphNode(@NonNull String id) {
        super(id);
        width(Sizing.fixed(48));
        height(Sizing.fixed(48));
    }

    /**
     * Set the shape, instead of the style's.
     *
     * @param newShape The shape, or null to use the style's.
     * @return This node.
     */
    public GraphNode shape(Shape newShape) {
        shape = newShape;
        return this;
    }

    /**
     * Set the icon, drawn inside the frame.
     *
     * @param texture The icon, or null for none.
     * @return This node.
     */
    public GraphNode icon(TextureHandle texture) {
        icon = texture;
        return this;
    }

    /**
     * Set the badge, drawn over the bottom of the frame, like a rank.
     *
     * @param text The badge, or null for none.
     * @return This node.
     */
    public GraphNode badge(String text) {
        badge = text == null || text.isEmpty() ? null : text;
        return this;
    }

    /**
     * Set the label, drawn in the middle of the frame, under the icon if there is one.
     *
     * @param text The label, or null for none.
     * @return This node.
     */
    public GraphNode label(String text) {
        label = text == null || text.isEmpty() ? null : text;
        return this;
    }

    @Override
    public String styleType() {
        return "graph-node";
    }

    /**
     * The shape to draw: the node's own, the style's, or a rectangle.
     *
     * @param state The interaction state.
     * @return The shape.
     */
    Shape shapeFor(@NonNull ItemState state) {
        if (shape != null) {
            return shape;
        }
        Shape styled =
                Shape.parse(style(StyleKey.SHAPE, state) instanceof String words ? words : null);
        return styled != null ? styled : Shape.RECT;
    }

    /**
     * The corners of a shape's outline, for the shapes drawn as polygons.
     *
     * @param shape The shape.
     * @param bounds The rectangle it fills.
     * @return The corners in order, or null for shapes drawn as rectangles.
     */
    static Vector2f[] outline(@NonNull Shape shape, @NonNull RectFloat bounds) {
        float left = bounds.getLeft();
        float top = bounds.getTop();
        float right = bounds.getRight();
        float bottom = bounds.getBottom();
        return switch (shape) {
            case RECT, ROUNDED -> null;
            case OCTAGON -> {
                // A regular octagon cuts each corner at about 29% of the side
                float cut = 0.29f * Math.min(bounds.getWidth(), bounds.getHeight());
                yield new Vector2f[] {
                    new Vector2f(left + cut, top),
                    new Vector2f(right - cut, top),
                    new Vector2f(right, top + cut),
                    new Vector2f(right, bottom - cut),
                    new Vector2f(right - cut, bottom),
                    new Vector2f(left + cut, bottom),
                    new Vector2f(left, bottom - cut),
                    new Vector2f(left, top + cut)
                };
            }
            case HEXAGON -> {
                float inset = bounds.getWidth() * 0.25f;
                float middle = bounds.getCenterY();
                yield new Vector2f[] {
                    new Vector2f(left, middle),
                    new Vector2f(left + inset, top),
                    new Vector2f(right - inset, top),
                    new Vector2f(right, middle),
                    new Vector2f(right - inset, bottom),
                    new Vector2f(left + inset, bottom)
                };
            }
        };
    }

    /**
     * Grow an outline away from its center, for a glow around it.
     *
     * @param points The outline.
     * @param bounds The rectangle the outline fills.
     * @param amount How far to grow it.
     * @return The grown outline.
     */
    private static Vector2f[] grown(Vector2f[] points, RectFloat bounds, float amount) {
        Vector2f center = new Vector2f(bounds.getCenterX(), bounds.getCenterY());
        Vector2f[] result = new Vector2f[points.length];
        for (int i = 0; i < points.length; ++i) {
            Vector2f away = new Vector2f(points[i]).sub(center);
            float length = away.length();
            result[i] =
                    length < 1e-4f
                            ? new Vector2f(points[i])
                            : away.mul((length + amount) / length).add(center);
        }
        return result;
    }

    /**
     * Resolve a length from the style in pixels, for the current state.
     *
     * @param key A length property.
     * @param state The interaction state.
     * @param fallback The length in UI units if the style doesn't set one.
     * @param unit Pixels per UI unit.
     * @return The length in pixels.
     */
    private float pixels(StyleKey key, ItemState state, float fallback, float unit) {
        return style(key, state) instanceof Length length
                ? length.resolve(0, unit, getFontPixels())
                : fallback * unit;
    }

    @Override
    protected void draw(
            @NonNull DrawList drawList, @NonNull RectFloat bounds, @NonNull ItemState state) {
        float unit = getScale();
        Shape drawnShape = shapeFor(state);
        Vector2f[] points = outline(drawnShape, bounds);
        float rounding =
                drawnShape == Shape.ROUNDED ? pixels(StyleKey.ROUNDING, state, 8, unit) : 0;

        Object glow = style(StyleKey.GLOW, state);
        if (glow instanceof Integer glowColor) {
            float size = pixels(StyleKey.GLOW_SIZE, state, DEFAULT_GLOW_SIZE, unit);
            if (points != null) {
                Vector2f[] around = grown(points, bounds, size);
                drawList.addConvexPolyFilled(around, around.length, glowColor, size);
            } else {
                drawList.addRectFilled(
                        bounds.getLeft() - size,
                        bounds.getTop() - size,
                        bounds.getRight() + size,
                        bounds.getBottom() + size,
                        glowColor,
                        rounding + size,
                        DrawFlags.ROUND_CORNERS_ALL,
                        size);
            }
        }

        int background = color(StyleKey.BACKGROUND, state, DEFAULT_BACKGROUND);
        int border = color(StyleKey.BORDER, state, DEFAULT_BORDER);
        float borderSize = pixels(StyleKey.BORDER_SIZE, state, DEFAULT_BORDER_SIZE, unit);
        if (points != null) {
            drawList.addConvexPolyFilled(points, points.length, background);
            if (borderSize > 0) {
                drawList.addPolyline(points, points.length, border, true, borderSize);
            }
        } else {
            drawList.addRectFilled(
                    bounds.getLeft(),
                    bounds.getTop(),
                    bounds.getRight(),
                    bounds.getBottom(),
                    background,
                    rounding);
            if (borderSize > 0) {
                drawList.addRect(
                        bounds.getLeft(),
                        bounds.getTop(),
                        bounds.getRight(),
                        bounds.getBottom(),
                        border,
                        rounding,
                        DrawFlags.NONE,
                        borderSize);
            }
        }

        int fontSize = Math.max(1, Math.round(getFontPixels()));
        float labelHeight = label == null ? 0 : fontSize;
        if (icon != null) {
            // The icon fills the middle, leaving room for the label under it
            float inset = Math.min(bounds.getWidth(), bounds.getHeight()) * 0.18f;
            drawList.addImage(
                    icon,
                    bounds.getLeft() + inset,
                    bounds.getTop() + inset,
                    bounds.getRight() - inset,
                    bounds.getBottom() - inset - labelHeight);
        }
        if (label != null) {
            float width = DrawList.calcTextWidth(fontSize, label);
            float y =
                    icon != null
                            ? bounds.getBottom() - labelHeight - borderSize
                            : bounds.getCenterY() - labelHeight / 2;
            drawList.addText(
                    fontSize,
                    bounds.getCenterX() - width / 2,
                    y,
                    color(StyleKey.TEXT, state, DEFAULT_TEXT),
                    label);
        }
        if (badge != null) {
            drawBadge(drawList, bounds, state, fontSize, unit);
        }
    }

    /**
     * Draw the badge, centered on the bottom edge of the frame.
     *
     * @param drawList Where to draw.
     * @param bounds The node's rectangle.
     * @param state The interaction state.
     * @param nodeFont The node's font size in pixels.
     * @param unit Pixels per UI unit.
     */
    private void drawBadge(
            DrawList drawList, RectFloat bounds, ItemState state, int nodeFont, float unit) {
        int fontSize = Math.max(1, Math.round(nodeFont * 0.8f));
        float padding = 3 * unit;
        float width = DrawList.calcTextWidth(fontSize, badge) + 2 * padding;
        float height = fontSize + padding;
        float left = bounds.getCenterX() - width / 2;
        float top = bounds.getBottom() - height / 2;
        drawList.addRectFilled(
                left,
                top,
                left + width,
                top + height,
                color(StyleKey.ACCENT, state, DEFAULT_ACCENT),
                2 * unit);
        drawList.addText(fontSize, left + padding, top + padding / 2, BADGE_TEXT, badge);
    }
}
