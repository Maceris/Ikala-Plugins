package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.ChildFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.graph.Edge;
import com.ikalagaming.graphics.ui.graph.Highlight;
import com.ikalagaming.graphics.ui.style.StyleKey;

import lombok.NonNull;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A view of children placed freely, at positions set with {@link Node#position(float, float)}, that
 * the user can pan and zoom, like a quest graph or skill tree. It is an IkGui child window, and its
 * children are real IkGui items, so they can be clicked, and keyboard and gamepad navigation pans
 * to the one with focus.
 *
 * <p>Zooming scales the layout of the children, the same as the UI scale does, so text is drawn at
 * the zoomed size and stays sharp.
 *
 * <p>The mouse wheel zooms around the cursor. Dragging with the middle button, or with the left
 * button on empty space, pans. With {@link #draggable(boolean)}, the user can drag children with
 * the left button too.
 */
public class Canvas extends Container<Canvas> {
    /** Told when the user finishes dragging a child. */
    @FunctionalInterface
    public interface MoveListener {
        /**
         * A child was dragged to a new position.
         *
         * @param node The child.
         * @param x Its new left edge, in canvas UI units.
         * @param y Its new top edge, in canvas UI units.
         */
        void moved(@NonNull Node<?> node, float x, float y);
    }

    /** How much each notch of the mouse wheel zooms by. */
    private static final float ZOOM_STEP = 1.1f;

    /** The zoom, where 1 is the normal UI size. */
    private float zoom = 1;

    /** The smallest zoom. */
    private float minZoom = 0.25f;

    /** The largest zoom. */
    private float maxZoom = 4;

    /** Whether the user can drag children. */
    private boolean draggable;

    /** Told when the user finishes dragging a child, or null. */
    private MoveListener onMoved;

    /** The spacing of background grid lines, or null for none. */
    private Length gridSpacing;

    /** Whether to draw a border around the view. */
    private boolean border;

    /** A scroll position to apply on the next frame, or null. */
    private float[] pendingScroll;

    /** Whether the view has been centered on the children yet. */
    private boolean placed;

    /** A canvas position to center on during the next frame, or null. */
    private float[] pendingCenter;

    /** The child being dragged, or null. */
    private Node<?> dragging;

    /** Whether the left button went down on empty space, so the drag pans. */
    private boolean panning;

    /** Whether the child being dragged has moved, so releasing it is a move and not a click. */
    private boolean wasDragged;

    /** The child under the mouse this frame, or null. */
    private Node<?> hoveredChild;

    /** The links between children, in drawing order. */
    final List<Edge> edges = new ArrayList<>();

    /** Which links light up while the mouse is over a child. */
    Highlight highlight = Highlight.EDGES;

    /** Pixels per canvas UI unit, from the last frame. */
    private float unit = 1;

    /** Where canvas position (0, 0) was on screen, from the last frame. */
    private final Vector2f origin = new Vector2f();

    /**
     * Create an empty canvas that grows to fill its parent.
     *
     * @param id The ID, which must be unique among its siblings.
     */
    public Canvas(@NonNull String id) {
        super(id);
        width(Sizing.grow());
        height(Sizing.grow());
    }

    /**
     * Set the zoom.
     *
     * @param newZoom The zoom, where 1 is the normal UI size. It is clamped to the zoom range.
     * @return This canvas.
     */
    public Canvas zoom(float newZoom) {
        zoom = Math.clamp(newZoom, minZoom, maxZoom);
        return this;
    }

    /**
     * The current zoom.
     *
     * @return The zoom, where 1 is the normal UI size.
     */
    public float getZoom() {
        return zoom;
    }

    /**
     * Set how far the user can zoom out and in.
     *
     * @param min The smallest zoom.
     * @param max The largest zoom.
     * @return This canvas.
     * @throws IllegalArgumentException If the range is empty or not positive.
     */
    public Canvas zoomRange(float min, float max) {
        if (min <= 0 || max < min) {
            throw new IllegalArgumentException("Invalid zoom range " + min + " to " + max);
        }
        minZoom = min;
        maxZoom = max;
        zoom = Math.clamp(zoom, minZoom, maxZoom);
        return this;
    }

    /**
     * Let the user drag children around.
     *
     * @param allow Whether children can be dragged.
     * @return This canvas.
     */
    public Canvas draggable(boolean allow) {
        draggable = allow;
        return this;
    }

    /**
     * Set what happens when the user finishes dragging a child. It runs on the render thread after
     * the frame, outside rendering. The child's position has already changed by then.
     *
     * @param listener The listener, or null for none.
     * @return This canvas.
     */
    public Canvas onMoved(MoveListener listener) {
        onMoved = listener;
        return this;
    }

    /**
     * Draw grid lines behind the children.
     *
     * @param spacing The space between lines, in canvas units, or null for no lines.
     * @return This canvas.
     */
    public Canvas gridSpacing(Length spacing) {
        gridSpacing = spacing;
        return this;
    }

    /**
     * Draw a border around the view.
     *
     * @param show Whether to draw the border.
     * @return This canvas.
     */
    public Canvas border(boolean show) {
        border = show;
        return this;
    }

    /**
     * Pan so a canvas position is in the middle of the view, from the next frame.
     *
     * @param x The position's x, in canvas UI units.
     * @param y The position's y, in canvas UI units.
     * @return This canvas.
     */
    public Canvas centerOn(float x, float y) {
        pendingCenter = new float[] {x, y};
        placed = true;
        return this;
    }

    /**
     * Link two children, by their IDs. The link is drawn from the first to the second while both
     * are in the canvas, styled by the theme as an {@code edge}.
     *
     * @param fromId The ID of the child the link leaves.
     * @param toId The ID of the child the link enters.
     * @return The link, to set its ports, route and classes.
     */
    public Edge connect(@NonNull String fromId, @NonNull String toId) {
        Edge edge = new Edge(fromId, toId, edges::remove);
        edges.add(edge);
        return edge;
    }

    /**
     * Remove the links from one child to another.
     *
     * @param fromId The ID of the child the links leave.
     * @param toId The ID of the child the links enter.
     * @return This canvas.
     */
    public Canvas disconnect(@NonNull String fromId, @NonNull String toId) {
        edges.removeIf(edge -> edge.getFrom().equals(fromId) && edge.getTo().equals(toId));
        return this;
    }

    /**
     * Remove every link.
     *
     * @return This canvas.
     */
    public Canvas clearEdges() {
        edges.clear();
        return this;
    }

    /**
     * The links, in drawing order.
     *
     * @return The links, which can't be modified through this list.
     */
    public List<Edge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * Set which links light up while the mouse is over a child.
     *
     * @param mode The highlight.
     * @return This canvas.
     */
    public Canvas highlight(@NonNull Highlight mode) {
        highlight = mode;
        return this;
    }

    /**
     * The child under the mouse during the last frame.
     *
     * @return The child, or null if the mouse isn't over one.
     */
    public Node<?> getHoveredChild() {
        return hoveredChild;
    }

    /**
     * The child the user is dragging.
     *
     * @return The child, or null if nothing is being dragged.
     */
    public Node<?> getDragging() {
        return dragging;
    }

    /**
     * Pixels per canvas UI unit during the last frame: the UI scale times the zoom.
     *
     * @return The scale.
     */
    public float getUnit() {
        return unit;
    }

    /**
     * Convert a canvas position to screen pixels, as of the last frame.
     *
     * @param x The position's x, in canvas UI units.
     * @param y The position's y, in canvas UI units.
     * @return The screen position.
     */
    public Vector2f toScreen(float x, float y) {
        return new Vector2f(origin.x + x * unit, origin.y + y * unit);
    }

    @Override
    public String styleType() {
        return "canvas";
    }

    @Override
    boolean laysOutOwnContent() {
        return true;
    }

    /**
     * The layout context for the children: the canvas's own, scaled by the zoom.
     *
     * @param context The canvas's layout context.
     * @return The zoomed context.
     */
    LayoutContext zoomed(@NonNull LayoutContext context) {
        return new LayoutContext(
                context.scale() * zoom,
                context.fontSize(),
                context.framePaddingX() * zoom,
                context.framePaddingY() * zoom,
                context.text(),
                context.theme());
    }

    /**
     * The area the children cover, in canvas UI units, from their last layout.
     *
     * @return The bounds, or an empty rectangle at the origin if there are no children.
     */
    private RectFloat childBounds() {
        boolean any = false;
        float minX = 0;
        float minY = 0;
        float maxX = 0;
        float maxY = 0;
        for (Node<?> child : children) {
            if (!child.visible) {
                continue;
            }
            float width = unit > 0 ? child.size[0] / unit : 0;
            float height = unit > 0 ? child.size[1] / unit : 0;
            if (!any) {
                minX = child.positionX;
                minY = child.positionY;
                maxX = child.positionX + width;
                maxY = child.positionY + height;
                any = true;
            } else {
                minX = Math.min(minX, child.positionX);
                minY = Math.min(minY, child.positionY);
                maxX = Math.max(maxX, child.positionX + width);
                maxY = Math.max(maxY, child.positionY + height);
            }
        }
        return new RectFloat(minX, minY, maxX, maxY);
    }

    /**
     * Whether the children have been laid out yet, so their sizes are known.
     *
     * @return True once any visible child has a size.
     */
    private boolean childrenLaidOut() {
        for (Node<?> child : children) {
            if (child.visible && (child.size[0] > 0 || child.size[1] > 0)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        LayoutContext context = zoomed(frame.context());
        float newUnit = context.scale();
        RectFloat bounds = childBounds();
        // The children can be panned to the edges of the view, and then a view further
        float marginX = rect.getWidth();
        float marginY = rect.getHeight();
        IkGui.setNextWindowContentSize(
                bounds.getWidth() * newUnit + 2 * marginX,
                bounds.getHeight() * newUnit + 2 * marginY);
        applyPendingScroll(bounds, newUnit, marginX, marginY);

        IkGuiStyler.Pushed pushed = IkGuiStyler.push(IkGuiStyler.Kind.CHILD, this, frame);
        IkGui.setCursorScreenPos(rect.getLeft(), rect.getTop());
        boolean open =
                IkGui.beginChild(
                        "canvas",
                        rect.getWidth(),
                        rect.getHeight(),
                        border ? ChildFlags.BORDERS : ChildFlags.NONE,
                        WindowFlags.NO_SCROLLBAR | WindowFlags.NO_SCROLL_WITH_MOUSE);
        pushed.pop();
        if (open) {
            // The cursor starts at the content's top left, already moved by the scroll
            Vector2f start = IkGui.getCursorScreenPos();
            unit = newUnit;
            origin.set(
                    start.x + marginX - bounds.getLeft() * unit,
                    start.y + marginY - bounds.getTop() * unit);

            // Lay out every child first, so links can be drawn under them
            float font = fontPixels * zoom;
            hoveredChild = null;
            Vector2f mouse = IkGui.getMousePos();
            boolean windowHovered = IkGui.isWindowHovered();
            for (Node<?> child : children) {
                if (!child.visible) {
                    continue;
                }
                float x = origin.x + child.positionX * unit;
                float y = origin.y + child.positionY * unit;
                frame.engine().layoutFitted(child, x, y, font, context);
                if (windowHovered && child.rect.contains(mouse.x, mouse.y)) {
                    // Later children are drawn on top, so the last one under the mouse wins
                    hoveredChild = child;
                }
            }

            DrawList drawList = IkGui.getWindowDrawList();
            drawGrid(drawList, frame);
            CanvasEdges.draw(this, drawList, context);
            for (Node<?> child : children) {
                child.submitTree(frame);
            }
            handleInput(frame, mouse, windowHovered);
            panToNavigatedChild();
        }
        IkGui.endChild();
    }

    /**
     * Pan sideways to the item keyboard or gamepad navigation just moved to, if it is out of view.
     * IkGui only scrolls windows sideways to navigated items when they have a horizontal scrollbar,
     * which a canvas hides, while it keeps them in view vertically itself.
     */
    private void panToNavigatedChild() {
        Context g = IkGui.getContext();
        Window window = IkGuiInternal.getCurrentWindow();
        if (g.navJustMovedToID == 0 || g.navFocusedWindow != window) {
            return;
        }
        RectFloat relative = window.navRectRelative[g.navLayer];
        float left = relative.getLeft() + window.cursorStartPosition.x;
        float right = relative.getRight() + window.cursorStartPosition.x;
        float viewLeft = window.rectInner.getLeft();
        float viewRight = window.rectInner.getRight();
        float spacing = IkGui.getStyle().variable.itemSpacing.x;
        if (left < viewLeft) {
            IkGui.setScrollX(IkGui.getScrollX() + left - viewLeft - spacing);
        } else if (right > viewRight) {
            IkGui.setScrollX(IkGui.getScrollX() + right - viewRight + spacing);
        }
    }

    /**
     * Draw the background grid lines, if there are any.
     *
     * @param drawList The canvas window's draw list.
     * @param frame Details about the current frame.
     */
    private void drawGrid(@NonNull DrawList drawList, @NonNull UiFrame frame) {
        if (gridSpacing == null) {
            return;
        }
        Integer color = style.color(StyleKey.BORDER);
        if (color == null) {
            return;
        }
        // Canvas units are scaled by the zoom, so the spacing is resolved against a unit of 1
        float spacing = gridSpacing.resolve(0, 1, fontPixels / Math.max(unit, 1e-6f)) * unit;
        if (spacing < 4) {
            // Too dense to see, so skip the lines rather than fill the view
            return;
        }
        float left = drawList.getClipRectMinX();
        float top = drawList.getClipRectMinY();
        float right = drawList.getClipRectMaxX();
        float bottom = drawList.getClipRectMaxY();
        float firstX = origin.x + (float) Math.ceil((left - origin.x) / spacing) * spacing;
        for (float x = firstX; x < right; x += spacing) {
            drawList.addLine(x, top, x, bottom, color);
        }
        float firstY = origin.y + (float) Math.ceil((top - origin.y) / spacing) * spacing;
        for (float y = firstY; y < bottom; y += spacing) {
            drawList.addLine(left, y, right, y, color);
        }
    }

    /**
     * Set the scroll for a pending center, or center on the children the first time, before the
     * child window begins so the scroll applies this frame.
     *
     * @param bounds The children's bounds, in canvas units.
     * @param newUnit Pixels per canvas unit this frame.
     * @param marginX The margin around the children, horizontally, in pixels.
     * @param marginY The margin around the children, vertically, in pixels.
     */
    private void applyPendingScroll(RectFloat bounds, float newUnit, float marginX, float marginY) {
        float[] center = pendingCenter;
        if (center == null && !placed && childrenLaidOut()) {
            center = new float[] {bounds.getCenterX(), bounds.getCenterY()};
        }
        if (center != null) {
            // The content starts at the scroll position, so the center goes to the middle
            float scrollX =
                    marginX + (center[0] - bounds.getLeft()) * newUnit - rect.getWidth() / 2;
            float scrollY =
                    marginY + (center[1] - bounds.getTop()) * newUnit - rect.getHeight() / 2;
            pendingScroll = new float[] {scrollX, scrollY};
            pendingCenter = null;
            placed = true;
        }
        if (pendingScroll != null) {
            IkGui.setNextWindowScroll(pendingScroll[0], pendingScroll[1]);
            pendingScroll = null;
        }
    }

    /**
     * Zoom with the wheel, pan with the middle button or a drag on empty space, and drag children.
     * Runs after the children are submitted, so the items under the mouse are known.
     *
     * @param frame Details about the current frame.
     * @param mouse The mouse position.
     * @param windowHovered Whether the mouse is over the canvas.
     */
    private void handleInput(@NonNull UiFrame frame, Vector2f mouse, boolean windowHovered) {
        float scrollX = IkGui.getScrollX();
        float scrollY = IkGui.getScrollY();
        Vector2f delta = IkGui.getIO().mouseDelta;

        float wheel = IkGui.getIO().mouseWheel;
        if (windowHovered && wheel != 0 && !IkGui.isAnyItemActive()) {
            zoomAround(mouse, wheel, scrollX, scrollY);
        }

        if (windowHovered && IkGui.isMouseClicked(MouseButton.LEFT)) {
            if (draggable && hoveredChild != null) {
                dragging = hoveredChild;
            } else if (hoveredChild == null && !IkGui.isAnyItemHovered()) {
                panning = true;
            }
        }
        if (!IkGui.isMouseDown(MouseButton.LEFT)) {
            if (dragging != null) {
                Node<?> moved = dragging;
                MoveListener listener = onMoved;
                if (listener != null && wasDragged) {
                    frame.fire(() -> listener.moved(moved, moved.positionX, moved.positionY));
                }
            }
            dragging = null;
            panning = false;
            wasDragged = false;
        }

        if (dragging != null && IkGui.isMouseDragging(MouseButton.LEFT)) {
            if (!wasDragged) {
                // Now it's a drag, not a click, so the child mustn't also be pressed
                IkGuiInternal.clearActiveID();
                wasDragged = true;
            }
            dragging.position(
                    dragging.positionX + delta.x / unit, dragging.positionY + delta.y / unit);
        } else if ((panning && IkGui.isMouseDragging(MouseButton.LEFT))
                || (windowHovered && IkGui.isMouseDragging(MouseButton.MIDDLE))) {
            IkGui.setScrollX(scrollX - delta.x);
            IkGui.setScrollY(scrollY - delta.y);
        }
    }

    /**
     * Zoom with the mouse wheel, keeping the canvas position under the mouse where it is.
     *
     * @param mouse The mouse position.
     * @param wheel How far the wheel turned.
     * @param scrollX The current horizontal scroll.
     * @param scrollY The current vertical scroll.
     */
    private void zoomAround(Vector2f mouse, float wheel, float scrollX, float scrollY) {
        float newZoom = Math.clamp(zoom * (float) Math.pow(ZOOM_STEP, wheel), minZoom, maxZoom);
        if (newZoom == zoom) {
            return;
        }
        // The canvas position under the mouse
        float canvasX = (mouse.x - origin.x) / unit;
        float canvasY = (mouse.y - origin.y) / unit;
        float newUnit = unit * newZoom / zoom;
        // origin = contentStart - scroll + margin - min * unit, and the point must stay put
        float originShiftX = canvasX * (newUnit - unit);
        float originShiftY = canvasY * (newUnit - unit);
        RectFloat bounds = childBounds();
        float minShiftX = bounds.getLeft() * (newUnit - unit);
        float minShiftY = bounds.getTop() * (newUnit - unit);
        zoom = newZoom;
        pendingScroll =
                new float[] {
                    scrollX + originShiftX - minShiftX, scrollY + originShiftY - minShiftY
                };
    }
}
