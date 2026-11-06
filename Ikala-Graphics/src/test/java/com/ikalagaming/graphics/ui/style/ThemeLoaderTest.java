package com.ikalagaming.graphics.ui.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.StyleColors;
import com.ikalagaming.graphics.gui.data.StyleVariables;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.Length;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

class ThemeLoaderTest {

    private static Theme load(String yaml) {
        return load(yaml, Theme.EMPTY);
    }

    private static Theme load(String yaml, Theme base) {
        return ThemeLoader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test", base);
    }

    private static ComputedStyle compute(Theme theme, String type, String... classes) {
        return theme.activate(Set.of()).compute(type, List.of(classes), Style.EMPTY);
    }

    private static String failure(String yaml) {
        return assertThrows(ThemeException.class, () -> load(yaml)).getMessage();
    }

    @Test
    void valuesConvertToTheirProperties() {
        Theme theme =
                load(
                        """
                        name: sample
                        types:
                          button:
                            text: "#112233"
                            background: "#11223344"
                            rounding: 2em
                            borderSize: 1.5u
                            gap: 50%
                            fontSize: 14
                            padding: 6
                        classes:
                          two: { padding: [1, 2] }
                          four: { padding: [1, 2, 3, 4] }
                        """);

        assertEquals("sample", theme.getName());
        ComputedStyle button = compute(theme, "button");
        assertEquals(0x112233FF, button.color(StyleKey.TEXT), "Opaque without alpha");
        assertEquals(0x11223344, button.color(StyleKey.BACKGROUND));
        assertEquals(Length.em(2), button.length(StyleKey.ROUNDING));
        assertEquals(Length.u(1.5f), button.length(StyleKey.BORDER_SIZE));
        assertEquals(Length.percent(0.5f), button.length(StyleKey.GAP));
        assertEquals(14f, button.number(StyleKey.FONT_SIZE));
        assertEquals(Insets.all(6), button.insets(StyleKey.PADDING));

        // CSS order: vertical then horizontal, or top, right, bottom, left
        assertEquals(
                Insets.symmetric(Length.u(2), Length.u(1)),
                compute(theme, "x", "two").insets(StyleKey.PADDING));
        assertEquals(
                new Insets(Length.u(4), Length.u(1), Length.u(2), Length.u(3)),
                compute(theme, "x", "four").insets(StyleKey.PADDING));
    }

    @Test
    void tokensChainAndConvertWhereUsed() {
        Theme theme =
                load(
                        """
                        tokens:
                          spacing.medium: 8
                          gutter: $spacing.medium
                        types:
                          row: { gap: $gutter, padding: $gutter, fontSize: $spacing.medium }
                        """);

        ComputedStyle row = compute(theme, "row");
        assertEquals(Length.u(8), row.length(StyleKey.GAP));
        assertEquals(Insets.all(8), row.insets(StyleKey.PADDING));
        assertEquals(8f, row.number(StyleKey.FONT_SIZE));
    }

    @Test
    void statesAndVariantsLoad() {
        Theme theme =
                load(
                        """
                        tokens: { spacing.medium: 8 }
                        variants:
                          compact: { spacing.medium: 6 }
                        types:
                          button:
                            gap: $spacing.medium
                            hovered: { background: "#FF0000" }
                        """);

        ComputedStyle button = compute(theme, "button");
        assertEquals(0xFF0000FF, button.getInState(StyleKey.BACKGROUND, StyleState.HOVERED));
        assertEquals(
                Length.u(6),
                theme.activate(Set.of("compact"))
                        .compute("button", List.of(), Style.EMPTY)
                        .length(StyleKey.GAP));
    }

    @Test
    void extensionsMayUseTheBaseThemesTokens() {
        Theme base = load("tokens: { color.error: \"#FF0000\" }");
        Theme extension = load("classes: { plugin.error: { text: $color.error } }", base);

        ComputedStyle style = compute(base.with(extension), "label", "plugin.error");

        assertEquals(0xFF0000FF, style.color(StyleKey.TEXT));
    }

    @Test
    void problemsSayWhereTheyAre() {
        assertTrue(failure("colour: 1").contains("unknown key 'colour'"));
        assertTrue(
                failure("classes: { danger: { background: $color.eror } }")
                        .startsWith("classes.danger.background: unknown token $color.eror"));
        assertTrue(
                failure("types: { button: { text: red } }")
                        .startsWith("types.button.text: not a color"));
        assertTrue(
                failure("types: { button: { colour: \"#FFFFFF\" } }")
                        .contains("types.button.colour: unknown style property"));
        assertTrue(
                failure("types: { button: { hovered: { held: { text: \"#FFFFFF\" } } } }")
                        .contains("types.button.hovered.held"),
                "States can't be nested");
        assertTrue(
                failure("tokens: { a: $b, b: $c, c: $a }").contains("token cycle"),
                "Cycles are caught");
        assertTrue(failure("types: { button: { padding: [1, 2, 3] } }").contains("1, 2 or 4"));
        assertTrue(failure("").contains("empty"));
        assertTrue(failure("[1, 2]").contains("expected a map"));
    }

    @Test
    void theDefaultThemeLoadsAndMatchesIkGui() {
        Theme theme = ThemeLoader.loadDefault();

        assertEquals("default", theme.getName());
        // IkGui's default colors and sizes, the Ikala style
        StyleColors colors = new StyleColors();
        StyleVariables sizes = new StyleVariables();

        ComputedStyle button = compute(theme, "button");
        assertEquals(colors.get(ColorType.BUTTON), button.color(StyleKey.BACKGROUND));
        assertEquals(
                colors.get(ColorType.BUTTON_HOVERED),
                button.getInState(StyleKey.BACKGROUND, StyleState.HOVERED));
        assertEquals(
                colors.get(ColorType.BUTTON_ACTIVE),
                button.getInState(StyleKey.BACKGROUND, StyleState.HELD));
        assertEquals(colors.get(ColorType.TEXT), button.color(StyleKey.TEXT));
        assertEquals(
                Insets.symmetric(Length.u(sizes.framePadding.x), Length.u(sizes.framePadding.y)),
                button.insets(StyleKey.PADDING));
        assertEquals(Length.u(sizes.frameRounding), button.length(StyleKey.ROUNDING));

        ComputedStyle input = compute(theme, "text-input");
        assertEquals(colors.get(ColorType.FRAME_BACKGROUND), input.color(StyleKey.BACKGROUND));
        assertEquals(
                colors.get(ColorType.FRAME_BACKGROUND_HOVERED),
                input.getInState(StyleKey.BACKGROUND, StyleState.HOVERED));
        assertEquals(
                colors.get(ColorType.FRAME_BACKGROUND_ACTIVE),
                input.getInState(StyleKey.BACKGROUND, StyleState.HELD));

        ComputedStyle selectable = compute(theme, "selectable");
        assertEquals(
                colors.get(ColorType.HEADER),
                selectable.getInState(StyleKey.BACKGROUND, StyleState.SELECTED));
        assertEquals(
                colors.get(ColorType.HEADER_HOVERED),
                selectable.getInState(StyleKey.BACKGROUND, StyleState.HOVERED));
        assertEquals(
                colors.get(ColorType.HEADER_ACTIVE),
                selectable.getInState(StyleKey.BACKGROUND, StyleState.HELD));

        ComputedStyle surface = compute(theme, "surface");
        assertEquals(colors.get(ColorType.WINDOW_BACKGROUND), surface.color(StyleKey.BACKGROUND));
        assertEquals(colors.get(ColorType.BORDER), surface.color(StyleKey.BORDER));
        assertEquals(Length.u(sizes.windowRounding), surface.length(StyleKey.ROUNDING));
        assertEquals(
                Length.u(sizes.childRounding), compute(theme, "scroll").length(StyleKey.ROUNDING));

        assertEquals(
                colors.get(ColorType.TEXT_DISABLED),
                compute(theme, "label", "muted").color(StyleKey.TEXT));
        assertNotNull(compute(theme, "label", "title").number(StyleKey.FONT_SIZE));
    }
}
