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
import com.ikalagaming.graphics.ui.graph.model.GraphFile;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;
import com.ikalagaming.graphics.ui.graph.model.PinTypes;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * One node of a {@link GraphEditor}, on its canvas: a title bar, then a row for each pin, inputs on
 * the left edge and outputs on the right, each a dot in its type's color. A collapsed node shows
 * only its title, its links meeting its sides; a node hiding unused pins shows only linked ones; a
 * reroute is a single dot. A node the caller gives a preview shows an image under its pins, and a
 * line of text under that. A comment floats above. Draws only; the editor reads the mouse for every
 * node at once. Sizes are in canvas units and scale with the zoom.
 */
public class GraphNodeView extends Node<GraphNodeView> {
    /** How wide a node is, in units. */
    public static final float WIDTH = 160;

    /** Text size as a part of a row's height: the title and pin names. */
    static final float TEXT_SIZE = 0.62f;

    /** Space between a pin's dot and its name, and around the title, in units. */
    static final float TEXT_PAD = 8;

    /** How tall its title bar is. */
    public static final float TITLE = 24;

    /** How tall each pin row is. */
    public static final float ROW = 22;

    /** The space under the last row. */
    public static final float BOTTOM = 6;

    /** How tall a node's preview image is, in units. */
    public static final float PREVIEW = 64;

    /** How tall the line of text under a preview is, in units. */
    public static final float CAPTION = 16;

    /** The mark in the title bar of a node that is shown elsewhere. */
    private static final int MARK = Color.rgba(0.35f, 0.85f, 0.45f, 1);

    /** A pin dot's radius. */
    public static final float PIN_RADIUS = 5;

    /** A reroute's size, in units. */
    public static final float REROUTE_SIZE = 20;

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
    static final int SELECTED = Color.rgba(1.0f, 0.72f, 0.2f, 1);

    /** The outline of a node with an error. */
    static final int ERROR = Color.rgba(0.95f, 0.25f, 0.25f, 1);

    /** A comment bubble. */
    private static final int BUBBLE = Color.rgba(0.95f, 0.93f, 0.78f, 0.95f);

    /** A comment's text. */
    private static final int BUBBLE_TEXT = Color.rgba(0.1f, 0.1f, 0.1f, 1);

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
     * What its size was made for, so the editor knows when to make it again: collapsed, a reroute,
     * or the number of pin rows. -- GETTER -- The shape's key.
     *
     * @return The key.
     */
    @Getter private int shape = -1;

    /** How wide the node is, in units: wide enough for its title and pin names. */
    private float unitsWide = WIDTH;

    /**
     * Show a graph node.
     *
     * @param editor The editor.
     * @param nodeId The graph node's ID, which is also this view's ID.
     */
    GraphNodeView(@NonNull GraphEditor editor, @NonNull String nodeId) {
        super(nodeId);
        this.editor = editor;
        this.nodeId = nodeId;
        width(Sizing.fixed(WIDTH));
        height(Sizing.fixed(height(1)));
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
     * Size the view for its node: a dot for a reroute, a title for a collapsed node, else a row for
     * each shown pin. Cheap when nothing changed.
     */
    void fit() {
        final int now = shapeKey();
        if (now == shape) {
            return;
        }
        shape = now;
        if (isReroute()) {
            width(Sizing.fixed(REROUTE_SIZE));
            height(Sizing.fixed(REROUTE_SIZE));
        } else if (isCollapsed()) {
            unitsWide = wideEnough();
            width(Sizing.fixed(unitsWide));
            height(Sizing.fixed(TITLE));
        } else {
            unitsWide = wideEnough();
            width(Sizing.fixed(unitsWide));
            height(
                    Sizing.fixed(
                            height(Math.max(shownPins(true).size(), shownPins(false).size()))
                                    + previewHeight(editor.previewOf(nodeId))));
        }
    }

    /**
     * How much taller a preview makes a node.
     *
     * @param preview The preview, or null for none.
     * @return The extra height, in units.
     */
    private static float previewHeight(@Nullable GraphEditor.NodePreview preview) {
        if (preview == null) {
            return 0;
        }
        return PREVIEW + BOTTOM + (preview.caption() == null ? 0 : CAPTION);
    }

    /**
     * How wide the node must be for its title and each row's input and output names, at least
     * {@link #WIDTH}.
     *
     * @return The width, in units.
     */
    private float wideEnough() {
        final NodeTypes.NodeType type = editor.getGraph().typeOf(nodeId);
        if (type == null) {
            return WIDTH;
        }
        final String title = isCollapsed() ? type.title() + "  ..." : type.title();
        // Room for the title, and the error mark after it
        float wide = textUnits(title) + 3 * TEXT_PAD + ROW * TEXT_SIZE;
        if (!isCollapsed()) {
            final List<NodeTypes.Pin> inputs = shownPins(true);
            final List<NodeTypes.Pin> outputs = shownPins(false);
            final float pinRoom = 2 * (PIN_RADIUS + 5);
            for (int row = 0; row < Math.max(inputs.size(), outputs.size()); ++row) {
                final float in = row < inputs.size() ? textUnits(inputs.get(row).name()) : 0;
                final float out = row < outputs.size() ? textUnits(outputs.get(row).name()) : 0;
                wide = Math.max(wide, in + out + pinRoom + 2 * TEXT_PAD);
            }
        }
        // Whole grid steps, so nodes line up as their files round them
        return Math.max(WIDTH, (float) Math.ceil(wide / GraphFile.GRID) * GraphFile.GRID);
    }

    /**
     * How wide text is drawn, in units.
     *
     * @param text The text.
     * @return The width.
     */
    private static float textUnits(String text) {
        return IkGui.calcTextSize(text).x / IkGui.getFontSize() * ROW * TEXT_SIZE;
    }

    /**
     * A number that changes when the view's size must.
     *
     * @return The key.
     */
    private int shapeKey() {
        if (isReroute()) {
            return -2;
        }
        if (isCollapsed()) {
            return -3;
        }
        final GraphEditor.NodePreview preview = editor.previewOf(nodeId);
        final int extra = preview == null ? 0 : preview.caption() == null ? 1 << 16 : 2 << 16;
        return Math.max(shownPins(true).size(), shownPins(false).size()) + extra;
    }

    /**
     * Whether the node is a reroute dot.
     *
     * @return True if so.
     */
    boolean isReroute() {
        final Graph.Node node = editor.getGraph().node(nodeId);
        return node != null && NodeTypes.REROUTE.equals(node.getType());
    }

    /**
     * Whether the node shows only its title.
     *
     * @return True if collapsed.
     */
    boolean isCollapsed() {
        final Graph.Node node = editor.getGraph().node(nodeId);
        return node != null && node.isCollapsed();
    }

    /**
     * The pins shown on a side, top to bottom: all of them, or only linked ones while unused pins
     * are hidden.
     *
     * @param input Whether the inputs, or the outputs.
     * @return The pins.
     */
    List<NodeTypes.Pin> shownPins(boolean input) {
        final Graph graph = editor.getGraph();
        final Graph.Node node = graph.node(nodeId);
        final NodeTypes.NodeType type =
                node == null ? null : graph.getNodeTypes().get(node.getType());
        if (type == null) {
            return List.of();
        }
        final List<NodeTypes.Pin> pins = input ? type.inputs() : type.outputs();
        if (!node.isHideUnused()) {
            return pins;
        }
        final List<NodeTypes.Pin> shown = new ArrayList<>();
        for (NodeTypes.Pin pin : pins) {
            if (editor.isLinked(nodeId, pin.name(), input)) {
                shown.add(pin);
            }
        }
        return shown;
    }

    /**
     * Pixels per unit, from the size the canvas laid the node out at.
     *
     * @return The scale.
     */
    float scale() {
        final RectFloat rect = getRect();
        return rect.getWidth() / (isReroute() ? REROUTE_SIZE : unitsWide);
    }

    /**
     * Where a pin's dot is on the screen: on its row, or for a collapsed node at the middle of its
     * title's side, or for a reroute at the dot's sides.
     *
     * @param input Whether it is an input, on the left edge, or an output, on the right.
     * @param name The pin's name.
     * @param out Receives the point.
     * @return The point, or null if the pin isn't shown.
     */
    @Nullable Vector2f pinPosition(boolean input, @NonNull String name, @NonNull Vector2f out) {
        final RectFloat rect = getRect();
        final float x = input ? rect.getLeft() : rect.getRight();
        if (isReroute() || isCollapsed()) {
            return out.set(x, (rect.getTop() + rect.getBottom()) / 2);
        }
        final List<NodeTypes.Pin> pins = shownPins(input);
        for (int i = 0; i < pins.size(); ++i) {
            if (pins.get(i).name().equals(name)) {
                return out.set(x, rect.getTop() + (TITLE + ROW * i + ROW / 2) * scale());
            }
        }
        return null;
    }

    /**
     * Whether a pin can be grabbed with the mouse: shown on a node that isn't collapsed.
     *
     * @return True if its pins can be grabbed.
     */
    boolean pinsGrabbable() {
        return !isCollapsed();
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
        final String error = editor.errorOf(nodeId);
        final boolean selected = editor.isSelected(nodeId);
        final int outline = error != null ? ERROR : selected ? SELECTED : OUTLINE;
        if (isReroute()) {
            drawReroute(drawList, rect, outline);
        } else {
            drawNode(drawList, rect, type, outline, selected || error != null ? 2 : 1);
        }
        if (node.getComment() != null) {
            drawComment(drawList, rect, node.getComment());
        }
    }

    /**
     * Draw a reroute: a dot in the color of what passes through it.
     *
     * @param drawList Where to draw.
     * @param rect The view's rectangle.
     * @param outline The outline's color.
     */
    private void drawReroute(DrawList drawList, RectFloat rect, int outline) {
        final float x = (rect.getLeft() + rect.getRight()) / 2;
        final float y = (rect.getTop() + rect.getBottom()) / 2;
        final float radius = rect.getWidth() / 2;
        final int color = pinColor(editor.getGraph().effectiveType(nodeId, "out", false));
        drawList.addCircleFilled(x, y, radius, outline);
        drawList.addCircleFilled(x, y, radius * 0.7f, color);
    }

    /**
     * Draw a node: its border, body, title and pins.
     *
     * @param drawList Where to draw.
     * @param rect The view's rectangle.
     * @param type Its type.
     * @param outline The border's color.
     * @param border The border's width, in pixels.
     */
    private void drawNode(
            DrawList drawList, RectFloat rect, NodeTypes.NodeType type, int outline, float border) {
        final float scale = scale();
        // The theme's rounding, as buttons and frames have, scaled with the zoom
        final float rounding = IkGui.getStyle().variable.frameRounding * scale;
        final float left = rect.getLeft();
        final float top = rect.getTop();
        final float right = rect.getRight();
        final float bottom = rect.getBottom();
        final boolean collapsed = isCollapsed();
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
                collapsed ? TITLE_BAR : BODY,
                inner,
                DrawFlags.ROUND_CORNERS_ALL);
        if (!collapsed) {
            drawList.addRectFilled(
                    left + border,
                    top + border,
                    right - border,
                    top + TITLE * scale,
                    TITLE_BAR,
                    inner,
                    DrawFlags.ROUND_CORNERS_TOP);
        }
        final int font = Math.max(1, Math.round(ROW * scale * TEXT_SIZE));
        final float textInset = TEXT_PAD * scale;
        final String title = collapsed ? type.title() + "  ..." : type.title();
        drawList.addText(font, left + textInset, top + (TITLE * scale - font) / 2, TEXT, title, 0);
        if (editor.errorOf(nodeId) != null) {
            drawList.addText(
                    font,
                    right - textInset - font / 2f,
                    top + (TITLE * scale - font) / 2,
                    ERROR,
                    "!",
                    0);
        }
        final GraphEditor.NodePreview preview = collapsed ? null : editor.previewOf(nodeId);
        if (preview != null && preview.marked()) {
            final float size = font * 0.6f;
            final float markRight = right - textInset - font;
            final float markTop = top + (TITLE * scale - size) / 2;
            drawList.addRectFilled(
                    markRight - size,
                    markTop,
                    markRight,
                    markTop + size,
                    MARK,
                    size / 4,
                    DrawFlags.ROUND_CORNERS_ALL);
        }
        if (!collapsed) {
            final Vector2f at = new Vector2f();
            drawPins(drawList, shownPins(true), true, font, at);
            drawPins(drawList, shownPins(false), false, font, at);
            if (preview != null) {
                final int rows =
                        Math.max(1, Math.max(shownPins(true).size(), shownPins(false).size()));
                drawPreview(drawList, rect, preview, top + (TITLE + rows * ROW + BOTTOM) * scale);
            }
        }
    }

    /**
     * Draw a node's preview: its image as large as fits, centered, and its caption under it.
     *
     * @param drawList Where to draw.
     * @param rect The node's rectangle.
     * @param preview The preview.
     * @param top Where the image's area starts, in pixels.
     */
    private void drawPreview(
            DrawList drawList, RectFloat rect, GraphEditor.NodePreview preview, float top) {
        final float scale = scale();
        final float room = rect.getWidth() - 2 * TEXT_PAD * scale;
        final float tall = PREVIEW * scale;
        final float aspect = preview.aspect() > 0 ? preview.aspect() : 1;
        final float width = Math.min(room, tall * aspect);
        final float height = width / aspect;
        final float left = (rect.getLeft() + rect.getRight() - width) / 2;
        final float imageTop = top + (tall - height) / 2;
        drawList.addImageView(
                preview.texture(),
                left,
                imageTop,
                left + width,
                imageTop + height,
                preview.minU(),
                preview.minV(),
                preview.maxU(),
                preview.maxV(),
                preview.view());
        if (preview.caption() != null) {
            final int font = Math.max(1, Math.round(CAPTION * scale * 0.8f));
            final float textWidth =
                    IkGui.calcTextSize(preview.caption()).x * font / IkGui.getFontSize();
            drawList.addText(
                    font,
                    (rect.getLeft() + rect.getRight() - textWidth) / 2,
                    top + tall + (CAPTION * scale - font) / 2,
                    TEXT,
                    preview.caption(),
                    0);
        }
    }

    /**
     * Draw a side's pins: each dot, filled if linked, and its name.
     *
     * @param drawList Where to draw.
     * @param pins The pins shown.
     * @param input Whether they are inputs.
     * @param font The text size, in pixels.
     * @param at Scratch.
     */
    private void drawPins(
            DrawList drawList, List<NodeTypes.Pin> pins, boolean input, int font, Vector2f at) {
        final float scale = scale();
        final float radius = PIN_RADIUS * scale;
        final float inset = (PIN_RADIUS + 5) * scale;
        for (NodeTypes.Pin pin : pins) {
            if (pinPosition(input, pin.name(), at) == null) {
                continue;
            }
            final int color = pinColor(editor.getGraph().effectiveType(nodeId, pin.name(), input));
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
     * Draw a comment in a bubble above the node.
     *
     * @param drawList Where to draw.
     * @param rect The node's rectangle.
     * @param comment The comment.
     */
    private void drawComment(DrawList drawList, RectFloat rect, String comment) {
        final float scale = isReroute() ? rect.getWidth() / REROUTE_SIZE : scale();
        final int font = Math.max(1, Math.round(ROW * scale * 0.55f));
        final float pad = 5 * scale;
        final float width = IkGui.calcTextSize(comment).x * font / IkGui.getFontSize();
        final float bottom = rect.getTop() - 4 * scale;
        final float top = bottom - font - 2 * pad;
        final float left = rect.getLeft();
        drawList.addRectFilled(
                left,
                top,
                left + width + 2 * pad,
                bottom,
                BUBBLE,
                IkGui.getStyle().variable.frameRounding * scale,
                DrawFlags.ROUND_CORNERS_ALL);
        drawList.addText(font, left + pad, top + pad, BUBBLE_TEXT, comment, 0);
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
