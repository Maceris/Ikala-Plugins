package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.ui.graph.Edge;
import com.ikalagaming.graphics.ui.graph.Highlight;
import com.ikalagaming.graphics.ui.graph.PathStroke;
import com.ikalagaming.graphics.ui.graph.Port;
import com.ikalagaming.graphics.ui.graph.Route;
import com.ikalagaming.graphics.ui.graph.Router;
import com.ikalagaming.graphics.ui.style.ComputedStyle;
import com.ikalagaming.graphics.ui.style.StyleKey;
import com.ikalagaming.graphics.ui.style.StyleState;

import lombok.NonNull;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Routes, styles and draws the links between a canvas's children. */
final class CanvasEdges {
    /** The line color when the theme doesn't set one. */
    static final int DEFAULT_LINE = 0x8C8C8CFF;

    /** The line color of related links when the theme doesn't set one. */
    static final int DEFAULT_RELATED_LINE = 0xFFC83CFF;

    /** The line width when the theme doesn't set one, in UI units. */
    static final float DEFAULT_SIZE = 2;

    /** The line width of related links when the theme doesn't set one, in UI units. */
    static final float DEFAULT_RELATED_SIZE = 3;

    /** How far curves reach out from the nodes at least, in UI units. */
    private static final float CURVE_REACH = 30;

    /** The style states of a link that isn't related to the hovered node. */
    private static final Set<StyleState> NORMAL = Set.of();

    /** The style states of a link that lights up. */
    private static final Set<StyleState> RELATED = Set.of(StyleState.RELATED);

    /**
     * A link that will be drawn.
     *
     * @param edge The edge.
     * @param link Where it goes.
     * @param style Its computed style.
     */
    private record Drawn(Edge edge, Router.Link link, ComputedStyle style) {}

    /**
     * Draw a canvas's links, under its children, which must be laid out this frame. Links that
     * light up are drawn last, so they are on top of any trunk they share.
     *
     * @param canvas The canvas.
     * @param drawList The canvas window's draw list.
     * @param context The canvas's zoomed layout context.
     */
    static void draw(
            @NonNull Canvas canvas, @NonNull DrawList drawList, @NonNull LayoutContext context) {
        if (canvas.edges.isEmpty()) {
            return;
        }
        Map<String, Node<?>> children = new HashMap<>();
        for (Node<?> child : canvas.children) {
            if (child.visible) {
                children.put(child.getId(), child);
            }
        }

        List<Drawn> drawn = new ArrayList<>();
        List<Router.Link> links = new ArrayList<>();
        List<Highlight.Link> highlightLinks = new ArrayList<>();
        for (Edge edge : canvas.edges) {
            Node<?> from = children.get(edge.getFrom());
            Node<?> to = children.get(edge.getTo());
            if (from == null || to == null) {
                continue;
            }
            ComputedStyle style =
                    context.theme().compute("edge", edge.getClasses(), edge.getStyle());
            Route route = edge.getRoute();
            if (route == null) {
                route = Route.parse(style.words(StyleKey.ROUTE, NORMAL));
            }
            if (route == null) {
                route = Route.ORTHOGONAL;
            }
            Port fromPort = edge.getFromPort();
            Port toPort = edge.getToPort();
            Port.Side[] sides =
                    Router.resolveSides(from.rect, fromPort.side(), to.rect, toPort.side());
            Router.Link link =
                    new Router.Link(
                            from,
                            sides[0],
                            Router.anchor(from.rect, sides[0], fromPort.offset()),
                            to,
                            sides[1],
                            Router.anchor(to.rect, sides[1], toPort.offset()),
                            route);
            drawn.add(new Drawn(edge, link, style));
            links.add(link);
            highlightLinks.add(new Highlight.Link(edge.getFrom(), edge.getTo()));
        }

        List<Float> midlines = Router.midlines(links);
        Node<?> hovered =
                canvas.getDragging() != null ? canvas.getDragging() : canvas.getHoveredChild();
        Set<Integer> related =
                canvas.highlight.related(highlightLinks, hovered == null ? null : hovered.getId());
        float unit = context.scale();
        float font = canvas.fontPixels * canvas.getZoom();
        // Everything else first, then the links that light up on top
        for (boolean lit : new boolean[] {false, true}) {
            for (int i = 0; i < drawn.size(); ++i) {
                if (related.contains(i) != lit) {
                    continue;
                }
                Drawn item = drawn.get(i);
                PathStroke stroke = stroke(item.style(), lit, unit, font);
                Router.Path path = Router.route(item.link(), midlines.get(i), CURVE_REACH * unit);
                List<Vector2f> points = path.points();
                if (path.curve()) {
                    stroke.drawCurve(
                            drawList, points.get(0), points.get(1), points.get(2), points.get(3));
                } else {
                    stroke.draw(drawList, points);
                }
            }
        }
    }

    /**
     * Work out how to draw a link from its style.
     *
     * @param style The link's computed style.
     * @param lit Whether it lights up.
     * @param unit Pixels per UI unit, including the zoom.
     * @param font The font size in pixels, for lengths in ems.
     * @return The stroke.
     */
    static PathStroke stroke(@NonNull ComputedStyle style, boolean lit, float unit, float font) {
        Set<StyleState> states = lit ? RELATED : NORMAL;
        Object color = style.get(StyleKey.LINE, states);
        int line =
                color instanceof Integer packed
                        ? packed
                        : (lit ? DEFAULT_RELATED_LINE : DEFAULT_LINE);
        float width =
                style.get(StyleKey.LINE_SIZE, states) instanceof Length size
                        ? size.resolve(0, unit, font)
                        : (lit ? DEFAULT_RELATED_SIZE : DEFAULT_SIZE) * unit;
        float[] dash = null;
        List<Float> pattern = style.numbers(StyleKey.DASH, states);
        if (pattern != null && !pattern.isEmpty()) {
            dash = new float[pattern.size()];
            for (int i = 0; i < dash.length; ++i) {
                dash[i] = pattern.get(i) * unit;
            }
        }
        int strokes =
                style.get(StyleKey.STROKES, states) instanceof Float count
                        ? Math.max(1, Math.round(count))
                        : 1;
        float strokeGap =
                style.get(StyleKey.STROKE_GAP, states) instanceof Length gap
                        ? gap.resolve(0, unit, font)
                        : width;
        PathStroke.Corner corner =
                PathStroke.Corner.parse(style.words(StyleKey.CORNER, states), unit);
        PathStroke.Arrow arrow = PathStroke.Arrow.parse(style.words(StyleKey.ARROW, states));
        return new PathStroke(width, line, dash, strokes, strokeGap, corner, arrow);
    }

    /** Static helpers only. */
    private CanvasEdges() {}
}
