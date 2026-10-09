package com.ikalagaming.graphics.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.style.Style;
import com.ikalagaming.graphics.ui.style.StyleKey;
import com.ikalagaming.graphics.ui.style.Theme;

import lombok.NonNull;
import org.junit.jupiter.api.Test;

import java.util.Set;

class LayoutEngineTest {

    /** Text is half as wide as the font size per character, and one font size tall. */
    private static final LayoutContext.TextMeasurer MEASURER =
            (text, fontPixels, out) -> {
                out[0] = text.length() * fontPixels * 0.5f;
                out[1] = fontPixels;
            };

    private static final LayoutContext SCALE_1 = new LayoutContext(1, 10, 4, 3, MEASURER);
    private static final LayoutContext SCALE_2 = new LayoutContext(2, 10, 4, 3, MEASURER);

    /** A leaf with a fixed content size. */
    private static final class Box extends Node<Box> {
        private final float contentWidth;
        private final float contentHeight;

        Box(String id, float contentWidth, float contentHeight) {
            super(id);
            this.contentWidth = contentWidth;
            this.contentHeight = contentHeight;
        }

        @Override
        protected void measure(@NonNull LayoutContext context, float[] out) {
            out[0] = contentWidth;
            out[1] = contentHeight;
        }
    }

    private final LayoutEngine engine = new LayoutEngine();

    private static void assertRect(float left, float top, float right, float bottom, Node<?> node) {
        RectFloat rect = node.getRect();
        String message = node + " was " + rect;
        assertEquals(left, rect.getLeft(), 1e-3, message);
        assertEquals(top, rect.getTop(), 1e-3, message);
        assertEquals(right, rect.getRight(), 1e-3, message);
        assertEquals(bottom, rect.getBottom(), 1e-3, message);
    }

    @Test
    void fixedPercentAndEmSizes() {
        Box units = new Box("units", 0, 0).width(Sizing.fixed(50));
        Box percent = new Box("percent", 0, 0).width(Sizing.percent(0.25f));
        Box em = new Box("em", 0, 0).width(Sizing.fixed(Length.em(2)));
        Row row = new Row("row").add(units, percent, em);

        engine.layout(row, 0, 0, 400, 100, SCALE_1);
        assertEquals(50, units.getRect().getWidth(), 1e-3);
        assertEquals(100, percent.getRect().getWidth(), 1e-3);
        assertEquals(20, em.getRect().getWidth(), 1e-3);

        // UI units and font sizes double, fractions of the parent don't
        engine.layout(row, 0, 0, 400, 100, SCALE_2);
        assertEquals(100, units.getRect().getWidth(), 1e-3);
        assertEquals(100, percent.getRect().getWidth(), 1e-3);
        assertEquals(40, em.getRect().getWidth(), 1e-3);
    }

    @Test
    void fontSizesAreInherited() {
        Box em = new Box("em", 0, 0).width(Sizing.fixed(Length.em(1)));
        Column inner = new Column("inner").add(em);
        Column outer = new Column("outer").fontSize(30).add(inner);

        engine.layout(outer, 0, 0, 100, 100, SCALE_1);
        assertEquals(30, em.getRect().getWidth(), 1e-3);
    }

    @Test
    void containersFitTheirChildrenWithPaddingAndGaps() {
        Box first = new Box("first", 30, 20);
        Box second = new Box("second", 50, 10);
        Column column = new Column("column").padding(Insets.all(10)).gap(5).add(first, second);
        Overlay root = new Overlay("root").add(column);

        engine.layout(root, 0, 0, 1000, 1000, SCALE_1);

        assertRect(0, 0, 70, 55, column);
        assertRect(10, 10, 40, 30, first);
        assertRect(10, 35, 60, 45, second);
    }

    @Test
    void growingChildrenShareLeftoverSpaceByWeight() {
        Box fixed = new Box("fixed", 0, 0).width(Sizing.fixed(60));
        Box one = new Box("one", 0, 0).width(Sizing.grow(1));
        Box two = new Box("two", 0, 0).width(Sizing.grow(2));
        Row row = new Row("row").add(fixed, one, two);

        engine.layout(row, 0, 0, 300, 50, SCALE_1);

        assertRect(0, 0, 60, 0, fixed);
        assertRect(60, 0, 140, 0, one);
        assertRect(140, 0, 300, 0, two);
    }

    @Test
    void growingStopsAtTheMaximumAndTheRestGoesToOthers() {
        Box capped = new Box("capped", 0, 0).width(Sizing.grow(1, Length.ZERO, Length.u(50)));
        Box open = new Box("open", 0, 0).width(Sizing.grow(1));
        Row row = new Row("row").add(capped, open);

        engine.layout(row, 0, 0, 300, 50, SCALE_1);

        assertEquals(50, capped.getRect().getWidth(), 1e-3);
        assertEquals(250, open.getRect().getWidth(), 1e-3);
    }

    @Test
    void growingStartsFromTheMinimumAndContent() {
        Box fixed = new Box("fixed", 0, 0).width(Sizing.fixed(60));
        Box withMin = new Box("min", 0, 0).width(Sizing.grow(1, Length.u(30), Sizing.NO_MAX));
        Box content = new Box("content", 4, 0).width(Sizing.grow(1));
        Row row = new Row("row").add(fixed, withMin, content);

        // 100 wide: 60 fixed, 30 minimum, 4 content, 6 left to share equally
        engine.layout(row, 0, 0, 100, 50, SCALE_1);

        assertEquals(33, withMin.getRect().getWidth(), 1e-3);
        assertEquals(7, content.getRect().getWidth(), 1e-3);
    }

    @Test
    void justifyPlacesChildrenAlongTheLine() {
        Box a = new Box("a", 20, 10);
        Box b = new Box("b", 20, 10);
        Row row = new Row("row").add(a, b);

        row.justify(Justify.CENTER);
        engine.layout(row, 0, 0, 200, 100, SCALE_1);
        assertEquals(80, a.getRect().getLeft(), 1e-3);
        assertEquals(100, b.getRect().getLeft(), 1e-3);

        row.justify(Justify.END);
        engine.layout(row, 0, 0, 200, 100, SCALE_1);
        assertEquals(160, a.getRect().getLeft(), 1e-3);
        assertEquals(180, b.getRect().getLeft(), 1e-3);

        row.justify(Justify.SPACE_BETWEEN);
        engine.layout(row, 0, 0, 200, 100, SCALE_1);
        assertEquals(0, a.getRect().getLeft(), 1e-3);
        assertEquals(180, b.getRect().getLeft(), 1e-3);
    }

    @Test
    void alignPlacesChildrenAcrossTheLine() {
        Box a = new Box("a", 20, 10);
        Box fixed = new Box("fixed", 20, 10).height(Sizing.fixed(10));
        Row row = new Row("row").add(a, fixed);

        row.align(Align.CENTER);
        engine.layout(row, 0, 0, 200, 100, SCALE_1);
        assertEquals(45, a.getRect().getTop(), 1e-3);

        row.align(Align.END);
        engine.layout(row, 0, 0, 200, 100, SCALE_1);
        assertEquals(90, a.getRect().getTop(), 1e-3);

        // Fitted children fill the row, fixed ones keep their size
        row.align(Align.STRETCH);
        engine.layout(row, 0, 0, 200, 100, SCALE_1);
        assertRect(0, 0, 20, 100, a);
        assertRect(20, 0, 40, 10, fixed);
    }

    @Test
    void overlayChildrenFollowTheirAnchors() {
        Box corner = new Box("corner", 20, 10).anchors(Anchors.bottomRight());
        Box centered =
                new Box("centered", 20, 10)
                        .anchors(Anchors.center().offset(Length.u(5), Length.u(-5)));
        Box filled = new Box("filled", 0, 0).anchors(Anchors.fill(Length.u(10)));
        Overlay overlay = new Overlay("overlay").add(corner, centered, filled);

        engine.layout(overlay, 0, 0, 200, 100, SCALE_1);

        assertRect(180, 90, 200, 100, corner);
        assertRect(95, 40, 115, 50, centered);
        assertRect(10, 10, 190, 90, filled);
    }

    @Test
    void layoutIsOffsetByTheRootPosition() {
        Box box = new Box("box", 20, 10).anchors(Anchors.bottomRight());
        Overlay overlay = new Overlay("overlay").padding(Insets.all(5)).add(box);

        engine.layout(overlay, 100, 200, 200, 100, SCALE_1);

        assertRect(100, 200, 300, 300, overlay);
        assertRect(275, 285, 295, 295, box);
    }

    @Test
    void hiddenChildrenTakeNoSpaceOrGap() {
        Box a = new Box("a", 20, 10);
        Box hidden = new Box("hidden", 20, 10).visible(false);
        Box b = new Box("b", 20, 10);
        Row row = new Row("row").gap(5).add(a, hidden, b);

        engine.layout(row, 0, 0, 200, 100, SCALE_1);

        assertEquals(25, b.getRect().getLeft(), 1e-3);
    }

    @Test
    void labelsAndButtonsMeasureTheirText() {
        Label label = new Label("label", "abcd");
        Button button = new Button("button", "abcd");
        Column column = new Column("column").add(label, button);
        Overlay root = new Overlay("root").add(column);

        engine.layout(root, 0, 0, 500, 500, SCALE_1);

        // 4 characters at 10 pixels is 20 by 10; buttons add frame padding on both sides
        assertRect(0, 0, 20, 10, label);
        assertRect(0, 10, 28, 26, button);
    }

    @Test
    void onlyChangedTreesAreLaidOutAgain() {
        Box box = new Box("box", 20, 10);
        Column column = new Column("column").add(box);

        assertTrue(engine.layout(column, 0, 0, 100, 100, SCALE_1));
        assertFalse(engine.layout(column, 0, 0, 100, 100, SCALE_1), "Nothing changed");

        box.width(Sizing.fixed(40));
        assertTrue(column.dirty, "A change marks the ancestors");
        assertTrue(engine.layout(column, 0, 0, 100, 100, SCALE_1));
        assertEquals(40, box.getRect().getWidth(), 1e-3);

        assertTrue(engine.layout(column, 0, 0, 120, 100, SCALE_1), "The space changed");
        assertTrue(engine.layout(column, 0, 0, 120, 100, SCALE_2), "The scale changed");
    }

    @Test
    void nodesCanOnlyHaveOneParent() {
        Box box = new Box("box", 0, 0);
        Column first = new Column("first").add(box);

        assertThrows(IllegalArgumentException.class, () -> new Column("second").add(box));
        assertThrows(
                IllegalArgumentException.class, () -> first.add(new Box("box", 0, 0)), "Same ID");

        first.remove(box);
        new Column("second").add(box);
    }

    private static LayoutContext themed(Theme theme) {
        return new LayoutContext(1, 10, 4, 3, MEASURER, theme.activate(Set.of()));
    }

    @Test
    void classesSetPaddingGapAndFontSize() {
        Theme theme =
                Theme.builder("test")
                        .token("spacing", 5)
                        .styleClass(
                                "roomy",
                                Style.builder()
                                        .token(StyleKey.PADDING, "spacing")
                                        .token(StyleKey.GAP, "spacing")
                                        .set(StyleKey.FONT_SIZE, 20f)
                                        .build())
                        .build();
        Label first = new Label("first", "ab");
        Label second = new Label("second", "ab");
        Column column = new Column("column").classes("roomy").add(first, second);
        Overlay root = new Overlay("root").add(column);

        engine.layout(root, 0, 0, 500, 500, themed(theme));

        // Labels inherit the 20 pixel font: 2 characters are 20 wide and 20 tall
        assertRect(5, 5, 25, 25, first);
        assertRect(5, 30, 25, 50, second);
        assertRect(0, 0, 30, 55, column);
    }

    @Test
    void explicitSettersWinOverTheStyle() {
        Theme theme =
                Theme.builder("test")
                        .type(
                                "column",
                                Style.builder().set(StyleKey.PADDING, Insets.all(9)).build())
                        .build();
        Box box = new Box("box", 10, 10);
        Column column = new Column("column").padding(Insets.all(2)).add(box);

        engine.layout(column, 0, 0, 100, 100, themed(theme));

        assertEquals(2, box.getRect().getLeft(), 1e-3);
    }

    @Test
    void buttonsWithoutAThemeUseIkGuisFramePadding() {
        Button button = new Button("button", "abcd");
        Overlay root = new Overlay("root").add(button);

        engine.layout(root, 0, 0, 500, 500, SCALE_2);

        // 4 characters at 20 pixels, plus 4 by 3 pixels of frame padding each side
        assertRect(0, 0, 48, 26, button);
    }

    @Test
    void changingTheThemeLaysOutAgain() {
        Theme wide =
                Theme.builder("wide")
                        .type("row", Style.builder().set(StyleKey.GAP, Length.u(10)).build())
                        .build();
        Box a = new Box("a", 10, 10);
        Box b = new Box("b", 10, 10);
        Row row = new Row("row").add(a, b);

        engine.layout(row, 0, 0, 100, 100, SCALE_1);
        assertEquals(10, b.getRect().getLeft(), 1e-3);

        assertTrue(engine.layout(row, 0, 0, 100, 100, themed(wide)));
        assertEquals(20, b.getRect().getLeft(), 1e-3);
    }
}
