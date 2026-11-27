package com.ikalagaming.graphics.ui.graph;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.flags.DrawFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;
import com.ikalagaming.graphics.ui.graph.model.PinTypes;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector2f;

import java.util.List;

/**
 * One node of a {@link GraphEditor}, on its canvas: a title bar, then a row for each pin, inputs on
 * the left edge and outputs on the right, each a dot in its type's color. Draws only; the editor
 * reads the mouse for every node at once. Sizes are in canvas units and scale with the zoom.
 */
public class GraphNodeView extends Node<GraphNodeView> {
    /** How wide a node is, in units. */
    public static final float WIDTH = 160;

    /** How tall its title bar is. */
    public static final float TITLE = 24;

    /** How tall each pin row is. */
    public static final float ROW = 22;

    /** The space under the last row. */
    public static final float BOTTOM = 6;

    /** A pin dot's radius. */
    public static final float PIN_RADIUS = 5;

    /** How far from a pin's dot the mouse still reaches it, as a multiple of its radius. */
    static final float PIN_REACH = 2.2f;

    /** The body. */
    private static final int BODY = Color.rgba(0.16f, 0.17f, 0.19f, 0.96f);

    /** The title bar. */
    private static final int TITLE_BAR = Color.rgba(0.26f, 0.30f, 0.38f, 1);

    /** The text. */
    private static final int TEXT = Color.rgba(0.92f, 0.93f, 0.95f, 1);

    /** The outline of a node. */
    private static final int OUTLINE = Color.rgba(0.05f, 0.05f, 0.06f, 1);

    /** The outline of a selected node. */
    private static final int SELECTED = Color.rgba(1.0f, 0.72f, 0.2f, 1);

    /** The outline of a node with an error. */
    private static final int ERROR = Color.rgba(0.95f, 0.25f, 0.25f, 1);

    /** A pin whose type isn't registered. */
    private static final int UNKNOWN_PIN = Color.rgba(0.6f, 0.6f, 0.6f, 1);

    /** The editor it belongs to. */
    private final GraphEditor editor;

    /**
     * The graph node it shows. -- GETTER -- The graph node's ID.
     *
     * @return The ID.
     */
    @Getter private final String nodeId;

    /**
     * Show a graph node.
     *
     * @param editor The editor.
     * @param nodeId The graph node's ID, which is also this view's ID.
     * @param rows How many pin rows it has: the more of its inputs and outputs.
     */
    GraphNodeView(@NonNull GraphEditor editor, @NonNull String nodeId, int rows) {
        super(nodeId);
        this.editor = editor;
        this.nodeId = nodeId;
        width(Sizing.fixed(WIDTH));
        height(Sizing.fixed(height(rows)));
    }

    /**
     * How tall a node with some pin rows is.
     *
     * @param rows The rows.
     * @return The height, in units.
     */
    static float height(int rows) {
        return TITLE + Math.max(1, rows) * ROW + BOTTOM;
    }

    /**
     * Pixels per unit, from the size the canvas laid the node out at.
     *
     * @return The scale.
     */
    float scale() {
        final RectFloat rect = getRect();
        return rect.getWidth() / WIDTH;
    }

    /**
     * Where a pin's dot is on the screen.
     *
     * @param input Whether it is an input, on the left edge, or an output, on the right.
     * @param index Its row.
     * @param out Receives the point.
     * @return The point.
     */
    Vector2f pinPosition(boolean input, int index, @NonNull Vector2f out) {
        final RectFloat rect = getRect();
        final float scale = scale();
        return out.set(
                input ? rect.getLeft() : rect.getRight(),
                rect.getTop() + (TITLE + ROW * index + ROW / 2) * scale);
    }

    @Override
    public String styleType() {
        return "graph-editor-node";
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        final Graph graph = editor.getGraph();
        final Graph.Node node = graph.node(nodeId);
        final NodeTypes.NodeType type =
                node == null ? null : graph.getNodeTypes().get(node.getType());
        if (type == null) {
            return;
        }
        final DrawList drawList = IkGui.getWindowDrawList();
        final RectFloat rect = getRect();
        final float scale = scale();
        // The theme's rounding, as buttons and frames have, scaled with the zoom
        final float rounding = IkGui.getStyle().variable.frameRounding * scale;
        final float left = rect.getLeft();
        final float top = rect.getTop();
        final float right = rect.getRight();
        final float bottom = rect.getBottom();
        final String error = editor.errorOf(nodeId);
        final boolean selected = editor.isSelected(nodeId);
        final int outline = error != null ? ERROR : selected ? SELECTED : OUTLINE;
        final float border = selected || error != null ? 2 : 1;
        // The border is a filled shape behind the node, and the body and title bar are inset by
        // its width with their rounding cut to match: a stroked outline over the fills lets the
        // fills' edge pixels show past it at the rounded corners
        final float inner = Math.max(0, rounding - border);
        drawList.addRectFilled(
                left, top, right, bottom, outline, rounding, DrawFlags.ROUND_CORNERS_ALL);
        drawList.addRectFilled(
                left + border,
                top + border,
                right - border,
                bottom - border,
                BODY,
                inner,
                DrawFlags.ROUND_CORNERS_ALL);
        drawList.addRectFilled(
                left + border,
                top + border,
                right - border,
                top + TITLE * scale,
                TITLE_BAR,
                inner,
                DrawFlags.ROUND_CORNERS_TOP);
        final int font = Math.max(1, Math.round(ROW * scale * 0.62f));
        final float textInset = 8 * scale;
        drawList.addText(
                font, left + textInset, top + (TITLE * scale - font) / 2, TEXT, type.title(), 0);
        final Vector2f at = new Vector2f();
        drawPins(drawList, type.inputs(), true, font, at);
        drawPins(drawList, type.outputs(), false, font, at);
    }

    /**
     * Draw a side's pins: each dot, filled if linked, and its name.
     *
     * @param drawList Where to draw.
     * @param pins The pins.
     * @param input Whether they are inputs.
     * @param font The text size, in pixels.
     * @param at Scratch.
     */
    private void drawPins(
            DrawList drawList, List<NodeTypes.Pin> pins, boolean input, int font, Vector2f at) {
        final float scale = scale();
        final float radius = PIN_RADIUS * scale;
        final float inset = (PIN_RADIUS + 5) * scale;
        for (int i = 0; i < pins.size(); ++i) {
            final NodeTypes.Pin pin = pins.get(i);
            pinPosition(input, i, at);
            final int color = pinColor(pin.type());
            if (editor.isLinked(nodeId, pin.name(), input)) {
                drawList.addCircleFilled(at.x, at.y, radius, color);
            } else {
                drawList.addCircleFilled(at.x, at.y, radius, BODY);
                drawList.addCircle(at.x, at.y, radius, color, Math.max(1, 1.5f * scale));
            }
            final float width = IkGui.calcTextSize(pin.name()).x * font / IkGui.getFontSize();
            final float x = input ? at.x + inset : at.x - inset - width;
            drawList.addText(font, x, at.y - font / 2f, TEXT, pin.name(), 0);
        }
    }

    /**
     * A pin type's color.
     *
     * @param type The pin type's ID.
     * @return The color.
     */
    int pinColor(@NonNull String type) {
        final PinTypes.PinType found = editor.getGraph().getPinTypes().get(type);
        return found == null ? UNKNOWN_PIN : found.color();
    }
}
