package com.ikalagaming.graphics.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.enums.StyleVariable;
import com.ikalagaming.graphics.ui.IkGuiStyler.ColorPush;
import com.ikalagaming.graphics.ui.IkGuiStyler.FloatPush;
import com.ikalagaming.graphics.ui.IkGuiStyler.Kind;
import com.ikalagaming.graphics.ui.IkGuiStyler.PairPush;
import com.ikalagaming.graphics.ui.IkGuiStyler.Push;
import com.ikalagaming.graphics.ui.style.ComputedStyle;
import com.ikalagaming.graphics.ui.style.Style;
import com.ikalagaming.graphics.ui.style.StyleKey;
import com.ikalagaming.graphics.ui.style.StyleState;
import com.ikalagaming.graphics.ui.style.Theme;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class IkGuiStylerTest {

    private static final int RED = 0xFF0000FF;
    private static final int GREEN = 0x00FF00FF;
    private static final int BLUE = 0x0000FFFF;

    /** A style setting every property, with hovered, held and selected backgrounds. */
    private static ComputedStyle full() {
        Style style =
                Style.builder()
                        .color(StyleKey.TEXT, RED)
                        .color(StyleKey.BACKGROUND, GREEN)
                        .color(StyleKey.BORDER, BLUE)
                        .set(StyleKey.ROUNDING, Length.u(3))
                        .set(StyleKey.BORDER_SIZE, Length.em(0.5f))
                        .state(
                                StyleState.HOVERED,
                                Style.builder().color(StyleKey.BACKGROUND, BLUE).build())
                        .state(
                                StyleState.HELD,
                                Style.builder().color(StyleKey.BACKGROUND, RED).build())
                        .state(
                                StyleState.SELECTED,
                                Style.builder().color(StyleKey.BACKGROUND, GREEN).build())
                        .build();
        return Theme.builder("t")
                .type("x", style)
                .build()
                .activate(Set.of())
                .compute("x", List.of(), Style.EMPTY);
    }

    private static final Insets PADDING =
            new Insets(Length.u(4), Length.u(3), Length.u(4), Length.u(3));

    @Test
    void buttonsUseTheButtonColorsAndFrame() {
        List<Push> pushes = IkGuiStyler.pushes(Kind.BUTTON, full(), PADDING, 2, 10);

        assertEquals(
                List.of(
                        new ColorPush(ColorType.BUTTON, GREEN),
                        new ColorPush(ColorType.BUTTON_HOVERED, BLUE),
                        new ColorPush(ColorType.BUTTON_ACTIVE, RED),
                        new ColorPush(ColorType.TEXT, RED),
                        new ColorPush(ColorType.BORDER, BLUE),
                        new FloatPush(StyleVariable.FRAME_ROUNDING, 6),
                        new FloatPush(StyleVariable.FRAME_BORDER_SIZE, 5),
                        new PairPush(StyleVariable.FRAME_PADDING, 8, 6)),
                pushes,
                "Units scale by 2, ems by the 10 pixel font");
    }

    @Test
    void textInputsUseTheFrameColors() {
        List<Push> pushes = IkGuiStyler.pushes(Kind.TEXT_INPUT, full(), PADDING, 1, 10);

        assertTrue(pushes.contains(new ColorPush(ColorType.FRAME_BACKGROUND, GREEN)));
        assertTrue(pushes.contains(new ColorPush(ColorType.FRAME_BACKGROUND_HOVERED, BLUE)));
        assertTrue(pushes.contains(new ColorPush(ColorType.FRAME_BACKGROUND_ACTIVE, RED)));
    }

    @Test
    void selectablesUseTheHeaderColors() {
        List<Push> pushes = IkGuiStyler.pushes(Kind.SELECTABLE, full(), null, 1, 10);

        assertTrue(pushes.contains(new ColorPush(ColorType.HEADER, GREEN)), "From selected");
        assertTrue(pushes.contains(new ColorPush(ColorType.HEADER_HOVERED, BLUE)));
        assertTrue(pushes.contains(new ColorPush(ColorType.HEADER_ACTIVE, RED)));
        assertTrue(pushes.contains(new FloatPush(StyleVariable.SELECTABLE_ROUNDING, 3)));
    }

    @Test
    void surfacesUseTheWindowSlots() {
        List<Push> pushes = IkGuiStyler.pushes(Kind.SURFACE, full(), PADDING, 1, 10);

        assertEquals(
                List.of(
                        new ColorPush(ColorType.WINDOW_BACKGROUND, GREEN),
                        new ColorPush(ColorType.BORDER, BLUE),
                        new FloatPush(StyleVariable.WINDOW_BORDER_SIZE, 5),
                        new FloatPush(StyleVariable.WINDOW_ROUNDING, 3),
                        new PairPush(StyleVariable.WINDOW_PADDING, 4, 3)),
                pushes);
    }

    @Test
    void childWindowsAndLabels() {
        assertTrue(
                IkGuiStyler.pushes(Kind.CHILD, full(), null, 1, 10)
                        .contains(new ColorPush(ColorType.CHILD_BACKGROUND, GREEN)));
        assertEquals(
                List.of(new ColorPush(ColorType.TEXT, RED)),
                IkGuiStyler.pushes(Kind.LABEL, full(), null, 1, 10));
    }

    @Test
    void unsetPropertiesPushNothing() {
        for (Kind kind : Kind.values()) {
            assertTrue(
                    IkGuiStyler.pushes(kind, ComputedStyle.EMPTY, null, 1, 10).isEmpty(),
                    kind.toString());
        }
    }
}
