package com.ikalagaming.graphics.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ikalagaming.graphics.ui.graph.PathStroke;
import com.ikalagaming.graphics.ui.style.ActiveTheme;
import com.ikalagaming.graphics.ui.style.ComputedStyle;
import com.ikalagaming.graphics.ui.style.Style;
import com.ikalagaming.graphics.ui.style.StyleKey;
import com.ikalagaming.graphics.ui.style.StyleState;
import com.ikalagaming.graphics.ui.style.Theme;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** How a canvas's links get their look from the theme. */
class CanvasEdgesTest {

    private static final int GRAY = 0x808080FF;
    private static final int GOLD = 0xFFC800FF;
    private static final int RED = 0xFF0000FF;

    /** A theme with an edge type, a related state, and a locked class. */
    private static final ActiveTheme THEME =
            Theme.builder("edges")
                    .type(
                            "edge",
                            Style.builder()
                                    .set(StyleKey.LINE, GRAY)
                                    .set(StyleKey.LINE_SIZE, Length.u(2))
                                    .set(StyleKey.CORNER, "round 4")
                                    .state(
                                            StyleState.RELATED,
                                            Style.builder()
                                                    .set(StyleKey.LINE, GOLD)
                                                    .set(StyleKey.LINE_SIZE, Length.u(4))
                                                    .build())
                                    .build())
                    .styleClass(
                            "locked",
                            Style.builder()
                                    .set(StyleKey.LINE, RED)
                                    .set(StyleKey.DASH, List.of(6f, 3f))
                                    .set(StyleKey.STROKES, 3f)
                                    .set(StyleKey.ARROW, "end")
                                    .build())
                    .build()
                    .activate(Set.of());

    private static ComputedStyle edge(String... classes) {
        return THEME.compute("edge", List.of(classes), Style.EMPTY);
    }

    @Test
    void linksUseTheEdgeType() {
        PathStroke stroke = CanvasEdges.stroke(edge(), false, 1, 10);
        assertEquals(GRAY, stroke.color());
        assertEquals(2, stroke.width(), 1e-4);
        assertEquals(PathStroke.CornerKind.ROUND, stroke.corner().kind());
        assertEquals(4, stroke.corner().size(), 1e-4);
        assertNull(stroke.dash());
        assertEquals(1, stroke.strokes());
    }

    @Test
    void relatedLinksUseTheRelatedState() {
        PathStroke stroke = CanvasEdges.stroke(edge(), true, 1, 10);
        assertEquals(GOLD, stroke.color());
        assertEquals(4, stroke.width(), 1e-4);
    }

    @Test
    void classesRestyleLinks() {
        PathStroke stroke = CanvasEdges.stroke(edge("locked"), false, 1, 10);
        assertEquals(RED, stroke.color());
        assertArrayEquals(new float[] {6, 3}, stroke.dash(), 1e-4f);
        assertEquals(3, stroke.strokes());
        // The gap between parallel strokes defaults to the line width
        assertEquals(2, stroke.strokeGap(), 1e-4);
        assertEquals(PathStroke.Arrow.END, stroke.arrow());
    }

    @Test
    void sizesFollowTheZoom() {
        PathStroke stroke = CanvasEdges.stroke(edge("locked"), false, 2.5f, 10);
        assertEquals(5, stroke.width(), 1e-4);
        assertArrayEquals(new float[] {15, 7.5f}, stroke.dash(), 1e-4f);
        assertEquals(10, stroke.corner().size(), 1e-4);
    }

    @Test
    void linksWithoutAThemeUseDefaults() {
        ComputedStyle none =
                Theme.builder("empty")
                        .build()
                        .activate(Set.of())
                        .compute("edge", List.of(), Style.EMPTY);
        PathStroke normal = CanvasEdges.stroke(none, false, 1, 10);
        PathStroke related = CanvasEdges.stroke(none, true, 1, 10);
        assertEquals(CanvasEdges.DEFAULT_LINE, normal.color());
        assertEquals(CanvasEdges.DEFAULT_RELATED_LINE, related.color());
        assertEquals(CanvasEdges.DEFAULT_RELATED_SIZE, related.width(), 1e-4);
        assertEquals(PathStroke.Corner.SHARP, normal.corner());
    }
}
