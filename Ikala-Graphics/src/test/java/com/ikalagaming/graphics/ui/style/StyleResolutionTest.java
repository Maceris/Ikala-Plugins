package com.ikalagaming.graphics.ui.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ikalagaming.graphics.ui.Length;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

class StyleResolutionTest {

    private static final int RED = 0xFF0000FF;
    private static final int GREEN = 0x00FF00FF;
    private static final int BLUE = 0x0000FFFF;
    private static final int WHITE = 0xFFFFFFFF;

    private static Style text(int color) {
        return Style.builder().color(StyleKey.TEXT, color).build();
    }

    private final Theme theme =
            Theme.builder("test")
                    .token("color.accent", RED)
                    .token("spacing", 8)
                    .variant("compact", "spacing", 4)
                    .type(
                            "button",
                            Style.builder()
                                    .color(StyleKey.TEXT, RED)
                                    .token(StyleKey.BACKGROUND, "color.accent")
                                    .token(StyleKey.GAP, "spacing")
                                    .state(
                                            StyleState.HOVERED,
                                            Style.builder()
                                                    .color(StyleKey.BACKGROUND, GREEN)
                                                    .build())
                                    .state(
                                            StyleState.HELD,
                                            Style.builder()
                                                    .color(StyleKey.BACKGROUND, BLUE)
                                                    .build())
                                    .build())
                    .styleClass("green", text(GREEN))
                    .styleClass("blue", text(BLUE))
                    .build();

    private ComputedStyle compute(Style inline, String... classes) {
        return theme.activate(Set.of()).compute("button", List.of(classes), inline);
    }

    @Test
    void typeThenClassesInOrderThenInline() {
        assertEquals(RED, compute(Style.EMPTY).color(StyleKey.TEXT));
        assertEquals(GREEN, compute(Style.EMPTY, "green").color(StyleKey.TEXT));
        assertEquals(BLUE, compute(Style.EMPTY, "green", "blue").color(StyleKey.TEXT));
        assertEquals(GREEN, compute(Style.EMPTY, "blue", "green").color(StyleKey.TEXT));
        assertEquals(WHITE, compute(text(WHITE), "green").color(StyleKey.TEXT));
        // Untouched values still come from the type
        assertEquals(RED, compute(text(WHITE), "green").color(StyleKey.BACKGROUND));
    }

    @Test
    void statesLayerOverTheBase() {
        ComputedStyle style = compute(Style.EMPTY);

        assertEquals(RED, style.get(StyleKey.BACKGROUND, Set.of()));
        assertEquals(GREEN, style.get(StyleKey.BACKGROUND, Set.of(StyleState.HOVERED)));
        assertEquals(
                BLUE,
                style.get(StyleKey.BACKGROUND, EnumSet.of(StyleState.HOVERED, StyleState.HELD)),
                "Held wins over hovered");
        assertEquals(RED, style.get(StyleKey.TEXT, Set.of(StyleState.HOVERED)), "Falls back");
        assertNull(style.getInState(StyleKey.TEXT, StyleState.HOVERED));
    }

    @Test
    void inlineStatesMergeWithTypeStates() {
        Style inline = Style.builder().state(StyleState.HOVERED, text(WHITE)).build();
        ComputedStyle style = compute(inline);

        assertEquals(WHITE, style.getInState(StyleKey.TEXT, StyleState.HOVERED));
        assertEquals(GREEN, style.getInState(StyleKey.BACKGROUND, StyleState.HOVERED));
    }

    @Test
    void variantsChangeTokens() {
        assertEquals(Length.u(8), compute(Style.EMPTY).length(StyleKey.GAP));
        assertEquals(
                Length.u(4),
                theme.activate(Set.of("compact"))
                        .compute("button", List.of(), Style.EMPTY)
                        .length(StyleKey.GAP));
    }

    @Test
    void extensionsAddAndOverrideClasses() {
        Theme extension = Theme.builder("plugin").styleClass("blue", text(WHITE)).build();
        ActiveTheme combined = theme.with(extension).activate(Set.of());

        assertEquals(
                WHITE,
                combined.compute("button", List.of("blue"), Style.EMPTY).color(StyleKey.TEXT));
        assertEquals("test", theme.with(extension).getName(), "The base keeps its name");
    }

    @Test
    void brokenReferencesAreLeftUnset() {
        Theme broken =
                Theme.builder("broken")
                        .token("loop", new Token("loop"))
                        .type(
                                "button",
                                Style.builder()
                                        .token(StyleKey.TEXT, "missing")
                                        .token(StyleKey.BORDER, "loop")
                                        .color(StyleKey.BACKGROUND, RED)
                                        .build())
                        .build();
        ComputedStyle style =
                broken.activate(Set.of()).compute("button", List.of("nope"), Style.EMPTY);

        assertNull(style.color(StyleKey.TEXT));
        assertNull(style.color(StyleKey.BORDER));
        assertEquals(RED, style.color(StyleKey.BACKGROUND));
    }

    @Test
    void computedStylesAreCached() {
        ActiveTheme active = theme.activate(Set.of());

        assertSame(
                active.compute("button", List.of("green"), Style.EMPTY),
                active.compute("button", List.of("green"), Style.EMPTY));
    }

    @Test
    void tokensCanBeReadDirectly() {
        ActiveTheme active = theme.activate(Set.of());

        assertEquals(RED, active.color("color.accent", WHITE));
        assertEquals(WHITE, active.color("color.missing", WHITE));
    }

    @Test
    void wrongValueTypesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Style.builder().set(StyleKey.TEXT, Length.u(1)));
    }
}
