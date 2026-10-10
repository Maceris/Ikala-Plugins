package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.ui.style.ComputedStyle;
import com.ikalagaming.graphics.ui.style.StyleKey;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lays out a tree of nodes. Each axis is done in two passes, widths before heights:
 *
 * <ol>
 *   <li>Fit, bottom-up: each node works out how big its content is.
 *   <li>Size, top-down: each container gives its children their final sizes, sharing leftover space
 *       between growing children.
 * </ol>
 *
 * Then positions are set top-down. Doing widths first leaves room for wrapped text later, whose
 * height depends on its final width.
 *
 * <p>Pure: it only reads nodes and the {@link LayoutContext}, so it can be tested without IkGui.
 */
public final class LayoutEngine {

    /**
     * The last layout of a root, so an unchanged tree isn't laid out again.
     *
     * @param x The left edge.
     * @param y The top edge.
     * @param width The width, or -1 for a root laid out at its fitted size.
     * @param height The height, or -1 for a root laid out at its fitted size.
     * @param font The font size the root inherits, in pixels.
     * @param context The layout context.
     */
    private record Placement(
            float x, float y, float width, float height, float font, LayoutContext context) {}

    /** Where each root was last placed. */
    private final Map<Node<?>, Placement> lastPlacements = new WeakHashMap<>();

    /**
     * Lay out a tree to fill a rectangle, if it changed since last time.
     *
     * @param root The root node, which is given the whole rectangle whatever its sizing says.
     * @param x The left edge, in pixels.
     * @param y The top edge, in pixels.
     * @param width The width, in pixels.
     * @param height The height, in pixels.
     * @param context The layout context.
     * @return True if the tree was laid out, false if nothing had changed.
     */
    public boolean layout(
            @NonNull Node<?> root,
            float x,
            float y,
            float width,
            float height,
            @NonNull LayoutContext context) {
        // The content of a scroll is laid out on its own, but keeps the scroll's font
        float inherited =
                root.parent != null ? root.parent.fontPixels : context.fontSize() * context.scale();
        Placement placement = new Placement(x, y, width, height, inherited, context);
        if (!root.dirty && placement.equals(lastPlacements.get(root))) {
            return false;
        }
        resolveStyles(root, inherited, context);
        for (Axis axis : Axis.values()) {
            fit(root, axis, context);
            root.size[axis.index()] = axis == Axis.X ? width : height;
            sizeChildren(root, axis, context);
        }
        position(root, x, y, context);
        root.clean();
        lastPlacements.put(root, placement);
        return true;
    }

    /**
     * Lay out a tree at its own preferred size, with its top left at a point, if it changed since
     * last time. Used for content placed freely, like a canvas's children, which have no parent
     * rectangle to fill.
     *
     * @param root The root node, sized by its own sizing, with fractions of the parent as zero.
     * @param x The left edge, in pixels.
     * @param y The top edge, in pixels.
     * @param inheritedFont The font size the root inherits if it sets none, in pixels.
     * @param context The layout context.
     * @return True if the tree was laid out, false if nothing had changed.
     */
    public boolean layoutFitted(
            @NonNull Node<?> root,
            float x,
            float y,
            float inheritedFont,
            @NonNull LayoutContext context) {
        Placement placement = new Placement(x, y, -1, -1, inheritedFont, context);
        if (!root.dirty && placement.equals(lastPlacements.get(root))) {
            return false;
        }
        resolveStyles(root, inheritedFont, context);
        for (Axis axis : Axis.values()) {
            fit(root, axis, context);
            root.size[axis.index()] = preferred(root, axis, context);
            sizeChildren(root, axis, context);
        }
        position(root, x, y, context);
        root.clean();
        lastPlacements.put(root, placement);
        return true;
    }

    /**
     * Work out how big a tree's content is, without placing it. Used to size surfaces that fit
     * their content.
     *
     * @param root The root node.
     * @param context The layout context.
     * @param out Receives the width at 0 and the height at 1, including the root's padding.
     */
    public static void fitSize(@NonNull Node<?> root, @NonNull LayoutContext context, float[] out) {
        resolveStyles(root, context.fontSize() * context.scale(), context);
        for (Axis axis : Axis.values()) {
            fit(root, axis, context);
            out[axis.index()] = root.fit[axis.index()];
        }
    }

    /**
     * Work out every node's style, padding, gap and font size, top-down. A node's own setters win
     * over its style, and nodes without a font size use their parent's.
     *
     * @param node The node.
     * @param inherited The parent's font size in pixels.
     * @param context The layout context.
     */
    private static void resolveStyles(Node<?> node, float inherited, LayoutContext context) {
        ComputedStyle style = context.theme().compute(node.styleType(), node.classes, node.inline);
        node.style = style;
        node.scale = context.scale();

        Insets padding = node.padding != null ? node.padding : style.insets(StyleKey.PADDING);
        node.resolvedPadding = padding != null ? padding : node.defaultPadding(context);

        if (node instanceof Flex<?> flex) {
            Length gap = flex.gap != null ? flex.gap : style.length(StyleKey.GAP);
            flex.resolvedGap = gap != null ? gap : Length.ZERO;
        }
        if (node instanceof Grid grid) {
            Length gap = grid.gap != null ? grid.gap : style.length(StyleKey.GAP);
            if (gap == null) {
                gap = Length.ZERO;
            }
            grid.resolvedColumnGap = grid.columnGap != null ? grid.columnGap : gap;
            grid.resolvedRowGap = grid.rowGap != null ? grid.rowGap : gap;
        }

        Float styleFont = style.number(StyleKey.FONT_SIZE);
        if (node.fontSize != null) {
            node.fontPixels = node.fontSize * context.scale();
        } else if (styleFont != null) {
            node.fontPixels = styleFont * context.scale();
        } else {
            node.fontPixels = inherited;
        }
        node.ownFont = node.fontSize != null || styleFont != null;

        if (node instanceof Canvas) {
            // A canvas styles its children itself, with its zoomed font
            return;
        }
        for (Node<?> child : node.children) {
            resolveStyles(child, node.fontPixels, context);
        }
    }

    /**
     * The fit pass: work out content sizes bottom-up.
     *
     * @param node The node.
     * @param axis The axis.
     * @param context The layout context.
     */
    private static void fit(Node<?> node, Axis axis, LayoutContext context) {
        if (node instanceof Canvas) {
            // Its children are placed freely and laid out by the canvas, so they don't size it
            node.fit[axis.index()] = paddingSum(node, axis, 0, context);
            return;
        }
        for (Node<?> child : node.children) {
            if (child.visible) {
                fit(child, axis, context);
            }
        }
        float content;
        if (node instanceof Grid grid) {
            content = gridContent(grid, axis, context);
        } else if (node instanceof Flex<?> flex && flex.mainAxis == axis) {
            content = gaps(flex, context);
            for (Node<?> child : node.children) {
                if (child.visible) {
                    content += preferred(child, axis, context);
                }
            }
        } else if (node instanceof Container<?>) {
            content = 0;
            for (Node<?> child : node.children) {
                if (child.visible) {
                    float extent = preferred(child, axis, context);
                    if (node instanceof Overlay && !child.anchors.stretches(axis)) {
                        // An offset point anchor needs room for the offset too
                        extent += Math.abs(offsetMin(child, axis, 0, context));
                    }
                    content = Math.max(content, extent);
                }
            }
        } else {
            float[] measured = new float[2];
            node.measure(context, measured);
            content = measured[axis.index()];
        }
        node.fit[axis.index()] = content + paddingSum(node, axis, 0, context);
    }

    /**
     * How big a child wants to be along an axis before its parent's size is known. Fractions of the
     * parent count as zero.
     *
     * @param child The child.
     * @param axis The axis.
     * @param context The layout context.
     * @return The preferred size in pixels.
     */
    private static float preferred(Node<?> child, Axis axis, LayoutContext context) {
        return switch (sizing(child, axis)) {
            case Sizing.Fixed fixed -> resolve(fixed.length(), 0, child, context);
            case Sizing.Fit fit ->
                    clamp(child, axis, child.fit[axis.index()], fit.min(), fit.max(), 0, context);
            case Sizing.Grow grow ->
                    clamp(child, axis, child.fit[axis.index()], grow.min(), grow.max(), 0, context);
        };
    }

    /**
     * The size pass: give each child of a node its final size along an axis, then recurse.
     *
     * @param node The node, whose own size along the axis is already final.
     * @param axis The axis.
     * @param context The layout context.
     */
    private static void sizeChildren(Node<?> node, Axis axis, LayoutContext context) {
        // Scrolls and canvases lay out their content themselves, once they know their view
        if (node.children.isEmpty() || node.laysOutOwnContent()) {
            return;
        }
        int i = axis.index();
        float available = node.size[i] - paddingSum(node, axis, node.size[i], context);
        if (node instanceof Grid grid) {
            sizeCells(grid, axis, available, context);
        } else if (node instanceof Flex<?> flex && flex.mainAxis == axis) {
            sizeAlongLine(flex, axis, available, context);
        } else {
            for (Node<?> child : node.children) {
                if (!child.visible) {
                    continue;
                }
                if (node instanceof Overlay && child.anchors.stretches(axis)) {
                    float min =
                            anchorMin(child, axis) * available
                                    + offsetMin(child, axis, available, context);
                    float max =
                            anchorMax(child, axis) * available
                                    + offsetMax(child, axis, available, context);
                    child.size[i] = Math.max(0, max - min);
                } else {
                    boolean stretch = node instanceof Flex<?> flex && flex.align == Align.STRETCH;
                    child.size[i] = crossSize(child, axis, available, stretch, context);
                }
            }
        }
        for (Node<?> child : node.children) {
            if (child.visible) {
                sizeChildren(child, axis, context);
            }
        }
    }

    /**
     * Size a child that isn't laid out along a line on this axis: across a row or column, or in an
     * overlay without stretching anchors.
     *
     * @param child The child.
     * @param axis The axis.
     * @param available The parent's content size along the axis.
     * @param stretch Whether children that fit their content should fill the parent instead.
     * @param context The layout context.
     * @return The size in pixels.
     */
    private static float crossSize(
            Node<?> child, Axis axis, float available, boolean stretch, LayoutContext context) {
        float fitSize = child.fit[axis.index()];
        return switch (sizing(child, axis)) {
            case Sizing.Fixed fixed -> resolve(fixed.length(), available, child, context);
            case Sizing.Fit fit ->
                    clamp(
                            child,
                            axis,
                            stretch ? available : fitSize,
                            fit.min(),
                            fit.max(),
                            available,
                            context);
            case Sizing.Grow grow ->
                    clamp(child, axis, available, grow.min(), grow.max(), available, context);
        };
    }

    /**
     * Size the children of a row or column along its line, sharing leftover space between the
     * growing children by weight. A growing child that hits its minimum or maximum stops there, and
     * the rest is shared between the others.
     *
     * @param flex The row or column.
     * @param axis The axis it lays out along.
     * @param available Its content size along the axis.
     * @param context The layout context.
     */
    private static void sizeAlongLine(
            Flex<?> flex, Axis axis, float available, LayoutContext context) {
        int i = axis.index();
        float used = gaps(flex, context);
        List<Node<?>> growing = new ArrayList<>();
        for (Node<?> child : flex.children) {
            if (!child.visible) {
                continue;
            }
            Sizing sizing = sizing(child, axis);
            child.size[i] =
                    switch (sizing) {
                        case Sizing.Fixed fixed ->
                                resolve(fixed.length(), available, child, context);
                        case Sizing.Fit fit ->
                                clamp(
                                        child,
                                        axis,
                                        child.fit[i],
                                        fit.min(),
                                        fit.max(),
                                        available,
                                        context);
                        case Sizing.Grow grow -> {
                            growing.add(child);
                            yield clamp(
                                    child,
                                    axis,
                                    child.fit[i],
                                    grow.min(),
                                    grow.max(),
                                    available,
                                    context);
                        }
                    };
            used += child.size[i];
        }

        float remaining = available - used;
        // Each round either shares out everything or pins at least one child at a limit
        while (!growing.isEmpty() && Math.abs(remaining) > 1e-3f) {
            float totalWeight = 0;
            for (Node<?> child : growing) {
                totalWeight += ((Sizing.Grow) sizing(child, axis)).weight();
            }
            List<Node<?>> pinned = new ArrayList<>();
            float[] targets = new float[growing.size()];
            for (int c = 0; c < growing.size(); ++c) {
                Node<?> child = growing.get(c);
                Sizing.Grow grow = (Sizing.Grow) sizing(child, axis);
                // Grow weights are always positive, so totalWeight is too
                float target = child.size[i] + remaining * grow.weight() / totalWeight;
                targets[c] = clamp(child, axis, target, grow.min(), grow.max(), available, context);
                if (targets[c] != target) {
                    pinned.add(child);
                }
            }
            if (pinned.isEmpty()) {
                for (int c = 0; c < growing.size(); ++c) {
                    growing.get(c).size[i] = targets[c];
                }
                break;
            }
            for (int c = 0; c < growing.size(); ++c) {
                Node<?> child = growing.get(c);
                if (pinned.contains(child)) {
                    remaining -= targets[c] - child.size[i];
                    child.size[i] = targets[c];
                }
            }
            growing.removeAll(pinned);
        }
    }

    /**
     * Set the rectangle of a node, then position its children inside it.
     *
     * @param node The node.
     * @param x The left edge.
     * @param y The top edge.
     * @param context The layout context.
     */
    private static void position(Node<?> node, float x, float y, LayoutContext context) {
        node.rect.set(x, y, x + node.size[0], y + node.size[1]);
        if (node.children.isEmpty() || node.laysOutOwnContent()) {
            return;
        }
        float left = x + resolve(node.resolvedPadding.left(), node.size[0], node, context);
        float top = y + resolve(node.resolvedPadding.top(), node.size[1], node, context);
        float[] start = {left, top};
        float[] available = {
            node.size[0] - paddingSum(node, Axis.X, node.size[0], context),
            node.size[1] - paddingSum(node, Axis.Y, node.size[1], context)
        };

        if (node instanceof Flex<?> flex) {
            positionLine(flex, start, available, context);
            return;
        }
        if (node instanceof Grid grid) {
            positionCells(grid, start, context);
            return;
        }
        for (Node<?> child : node.children) {
            if (!child.visible) {
                continue;
            }
            float[] childStart = new float[2];
            for (Axis axis : Axis.values()) {
                int i = axis.index();
                if (child.anchors.stretches(axis)) {
                    childStart[i] =
                            start[i]
                                    + anchorMin(child, axis) * available[i]
                                    + offsetMin(child, axis, available[i], context);
                } else {
                    float anchor = anchorMin(child, axis);
                    childStart[i] =
                            start[i]
                                    + anchor * (available[i] - child.size[i])
                                    + offsetMin(child, axis, available[i], context);
                }
            }
            position(child, childStart[0], childStart[1], context);
        }
    }

    /**
     * Position the children of a row or column.
     *
     * @param flex The row or column.
     * @param start The top left of its content.
     * @param available The size of its content.
     * @param context The layout context.
     */
    private static void positionLine(
            Flex<?> flex, float[] start, float[] available, LayoutContext context) {
        int main = flex.mainAxis.index();
        int cross = flex.mainAxis.other().index();
        float gap = resolve(flex.resolvedGap, available[main], flex, context);

        int count = 0;
        float total = 0;
        for (Node<?> child : flex.children) {
            if (child.visible) {
                total += child.size[main];
                ++count;
            }
        }
        total += gap * Math.max(0, count - 1);
        float free = Math.max(0, available[main] - total);
        float cursor = start[main];
        float spacing = gap;
        switch (flex.justify) {
            case START -> {
                /* already there */
            }
            case CENTER -> cursor += free / 2;
            case END -> cursor += free;
            case SPACE_BETWEEN -> spacing += count > 1 ? free / (count - 1) : 0;
        }

        for (Node<?> child : flex.children) {
            if (!child.visible) {
                continue;
            }
            float slack = available[cross] - child.size[cross];
            float crossOffset =
                    switch (flex.align) {
                        case START, STRETCH -> 0;
                        case CENTER -> slack / 2;
                        case END -> slack;
                    };
            float[] childStart = new float[2];
            childStart[main] = cursor;
            childStart[cross] = start[cross] + crossOffset;
            position(child, childStart[0], childStart[1], context);
            cursor += child.size[main] + spacing;
        }
    }

    /**
     * Work out a grid's content size along an axis: its cell size, and how many cells fit in a row
     * or column. Across, the cells are as wide as the widest child unless the grid fixes them.
     * Widths come first: with columns that fit the width, the grid only asks for one cell, and the
     * column count is decided once its width is known. Heights then use that count.
     *
     * @param grid The grid.
     * @param axis The axis.
     * @param context The layout context.
     * @return The content size in pixels, not counting padding.
     */
    private static float gridContent(Grid grid, Axis axis, LayoutContext context) {
        int i = axis.index();
        Length fixedCell = axis == Axis.X ? grid.cellWidth : grid.cellHeight;
        float cell = 0;
        if (fixedCell != null) {
            cell = resolve(fixedCell, 0, grid, context);
        } else {
            for (Node<?> child : grid.children) {
                if (child.visible) {
                    cell = Math.max(cell, preferred(child, axis, context));
                }
            }
        }
        grid.cellPixels[i] = cell;
        Length gapLength = axis == Axis.X ? grid.resolvedColumnGap : grid.resolvedRowGap;
        float gap = resolve(gapLength, 0, grid, context);
        grid.gapPixels[i] = gap;

        int count = grid.shownChildren().size();
        if (count == 0) {
            return 0;
        }
        int cells;
        if (axis == Axis.X) {
            // Until the width is known, fixed columns fill a row and fitted columns ask for one
            int columns = grid.columns > 0 ? Math.min(grid.columns, count) : 1;
            grid.resolvedColumns = columns;
            cells = columns;
        } else {
            int columns = Math.max(1, grid.resolvedColumns);
            cells = (count + columns - 1) / columns;
        }
        return cells * cell + (cells - 1) * gap;
    }

    /**
     * Give a grid's children their sizes along an axis: the cell size for children that grow, and
     * their own size otherwise. Along the width, this is also where the column count is decided,
     * since the grid's width is now known.
     *
     * @param grid The grid.
     * @param axis The axis.
     * @param available The grid's content size along the axis.
     * @param context The layout context.
     */
    private static void sizeCells(Grid grid, Axis axis, float available, LayoutContext context) {
        int i = axis.index();
        if (axis == Axis.X) {
            if (grid.columns > 0) {
                grid.resolvedColumns = grid.columns;
            } else {
                // As many as fit, but no more than there are children
                float pitch = grid.cellPixels[0] + grid.gapPixels[0];
                int fitting =
                        pitch > 0 ? (int) Math.floor((available + grid.gapPixels[0]) / pitch) : 1;
                int count = grid.shownChildren().size();
                grid.resolvedColumns = Math.clamp(fitting, 1, Math.max(1, count));
            }
        }
        float cell = grid.cellPixels[i];
        for (Node<?> child : grid.children) {
            if (child.visible) {
                child.size[i] = crossSize(child, axis, cell, false, context);
            }
        }
    }

    /**
     * Position a grid's children in their cells, left to right and then top to bottom.
     *
     * @param grid The grid.
     * @param start The top left of its content.
     * @param context The layout context.
     */
    private static void positionCells(Grid grid, float[] start, LayoutContext context) {
        int columns = Math.max(1, grid.resolvedColumns);
        float pitchX = grid.cellPixels[0] + grid.gapPixels[0];
        float pitchY = grid.cellPixels[1] + grid.gapPixels[1];
        int index = 0;
        for (Node<?> child : grid.children) {
            if (!child.visible) {
                continue;
            }
            int column = index % columns;
            int row = index / columns;
            position(child, start[0] + column * pitchX, start[1] + row * pitchY, context);
            ++index;
        }
    }

    /**
     * The total size of the gaps in a row or column.
     *
     * @param flex The row or column.
     * @param context The layout context.
     * @return The gaps in pixels. Fractions of the parent count as zero.
     */
    private static float gaps(Flex<?> flex, LayoutContext context) {
        int count = 0;
        for (Node<?> child : flex.children) {
            if (child.visible) {
                ++count;
            }
        }
        return Math.max(0, count - 1) * resolve(flex.resolvedGap, 0, flex, context);
    }

    /**
     * The sizing of a node along an axis.
     *
     * @param node The node.
     * @param axis The axis.
     * @return The width or height sizing.
     */
    private static Sizing sizing(Node<?> node, Axis axis) {
        return axis == Axis.X ? node.width : node.height;
    }

    /**
     * The padding on both sides of an axis.
     *
     * @param node The node.
     * @param axis The axis.
     * @param parent The size fractions are taken of.
     * @param context The layout context.
     * @return The total padding in pixels.
     */
    private static float paddingSum(Node<?> node, Axis axis, float parent, LayoutContext context) {
        Insets padding = node.resolvedPadding;
        return axis == Axis.X
                ? resolve(padding.left(), parent, node, context)
                        + resolve(padding.right(), parent, node, context)
                : resolve(padding.top(), parent, node, context)
                        + resolve(padding.bottom(), parent, node, context);
    }

    /**
     * Clamp a size between a minimum and maximum.
     *
     * @param node The node the limits belong to.
     * @param axis The axis.
     * @param value The size.
     * @param min The minimum.
     * @param max The maximum.
     * @param parent The size fractions are taken of.
     * @param context The layout context.
     * @return The clamped size.
     */
    private static float clamp(
            Node<?> node,
            Axis axis,
            float value,
            Length min,
            Length max,
            float parent,
            LayoutContext context) {
        float low = resolve(min, parent, node, context);
        float high = resolve(max, parent, node, context);
        return Math.clamp(value, low, high);
    }

    /**
     * Convert a length to pixels for a node.
     *
     * @param length The length.
     * @param parent The size fractions are taken of.
     * @param node The node, whose font size ems use.
     * @param context The layout context.
     * @return The length in pixels.
     */
    private static float resolve(Length length, float parent, Node<?> node, LayoutContext context) {
        if (length.units() == Float.MAX_VALUE) {
            return Float.MAX_VALUE;
        }
        return length.resolve(parent, context.scale(), node.fontPixels);
    }

    private static float anchorMin(Node<?> node, Axis axis) {
        return axis == Axis.X ? node.anchors.minX() : node.anchors.minY();
    }

    private static float anchorMax(Node<?> node, Axis axis) {
        return axis == Axis.X ? node.anchors.maxX() : node.anchors.maxY();
    }

    private static float offsetMin(Node<?> node, Axis axis, float parent, LayoutContext context) {
        Length offset = axis == Axis.X ? node.anchors.offsetMinX() : node.anchors.offsetMinY();
        return resolve(offset, parent, node, context);
    }

    private static float offsetMax(Node<?> node, Axis axis, float parent, LayoutContext context) {
        Length offset = axis == Axis.X ? node.anchors.offsetMaxX() : node.anchors.offsetMaxY();
        return resolve(offset, parent, node, context);
    }
}
