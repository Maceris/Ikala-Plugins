package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.Style;
import com.ikalagaming.graphics.frontend.gui.data.StyleColors;
import com.ikalagaming.graphics.frontend.gui.data.StyleVariables;
import com.ikalagaming.graphics.frontend.gui.data.TabBar;
import com.ikalagaming.graphics.frontend.gui.data.TabItem;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Color;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/** Tests for the style presets, copying styles, and the style editor. */
class IkGuiStyleTest {
    private Context context;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    private static void frame(Runnable ui) {
        IkGui.newFrame();
        ui.run();
        IkGui.render();
    }

    @Test
    void testDarkThemeMatchesUpstream() {
        final StyleColors colors = new StyleColors();
        // ImVec4(0.06f, 0.06f, 0.06f, 0.94f), rounded like ImGui does
        assertEquals(Color.rgba(15, 15, 15, 240), colors.get(ColorType.WINDOW_BACKGROUND));
        // Tab = lerp(Header, TitleBgActive, 0.80f)
        assertEquals(Color.rgba(46, 89, 148, 220), colors.get(ColorType.TAB));
        // Separator = Border, and InputTextCursor = Text
        assertEquals(colors.get(ColorType.BORDER), colors.get(ColorType.SEPARATOR));
        assertEquals(colors.get(ColorType.TEXT), colors.get(ColorType.INPUT_TEXT_CURSOR));
        // DockingPreview = HeaderActive * (1, 1, 1, 0.7)
        assertEquals(Color.rgba(66, 150, 250, 179), colors.get(ColorType.DOCKING_PREVIEW));
    }

    @Test
    void testThemesSetEveryColor() {
        final Consumer<StyleColors>[] themes =
                new Consumer[] {
                    (Consumer<StyleColors>) StyleColors::setThemeDark,
                    (Consumer<StyleColors>) StyleColors::setThemeLight,
                    (Consumer<StyleColors>) StyleColors::setThemeClassic
                };
        final int sentinel = Color.rgba(1, 2, 3, 4);
        for (Consumer<StyleColors> theme : themes) {
            final StyleColors colors = new StyleColors();
            for (ColorType type : ColorType.values()) {
                colors.set(type, sentinel);
            }
            theme.accept(colors);
            for (ColorType type : ColorType.values()) {
                assertNotEquals(sentinel, colors.get(type), type + " was not set by the theme");
            }
        }
    }

    @Test
    void testPresetsChangeTheCurrentStyle() {
        IkGui.styleColorsLight();
        assertEquals(Color.rgba(0, 0, 0, 255), IkGui.getColor(ColorType.TEXT));
        IkGui.styleColorsClassic();
        assertEquals(Color.rgba(230, 230, 230, 255), IkGui.getColor(ColorType.TEXT));
        IkGui.styleColorsDark();
        assertEquals(Color.rgba(255, 255, 255, 255), IkGui.getColor(ColorType.TEXT));

        // Other styles can be modified without touching the current one
        final Style other = new Style();
        IkGui.styleColorsLight(other);
        assertEquals(Color.rgba(0, 0, 0, 255), other.color.get(ColorType.TEXT));
        assertEquals(Color.rgba(255, 255, 255, 255), IkGui.getColor(ColorType.TEXT));
    }

    @Test
    void testColorsGetAndSetByType() {
        final StyleColors colors = new StyleColors();
        int value = 1;
        for (ColorType type : ColorType.values()) {
            colors.set(type, value++);
        }
        value = 1;
        for (ColorType type : ColorType.values()) {
            assertEquals(value++, colors.get(type), type.name());
        }
        final StyleColors copy = new StyleColors(colors);
        for (ColorType type : ColorType.values()) {
            assertEquals(colors.get(type), copy.get(type), type.name());
        }
    }

    @Test
    void testStyleVariablesCopyEveryField() throws IllegalAccessException {
        final StyleVariables source = new StyleVariables();
        float next = 1.5f;
        for (Field field : StyleVariables.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            final Class<?> type = field.getType();
            if (type == float.class) {
                field.setFloat(source, next++);
            } else if (type == int.class) {
                field.setInt(source, (int) next++);
            } else if (type == long.class) {
                field.setLong(source, (long) next++);
            } else if (type == boolean.class) {
                field.setBoolean(source, !field.getBoolean(source));
            } else if (type == Vector2f.class) {
                ((Vector2f) field.get(source)).set(next++, next++);
            }
        }
        final StyleVariables copy = new StyleVariables(source);
        for (Field field : StyleVariables.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertEquals(field.get(source), field.get(copy), field.getName());
            if (field.getType() == Vector2f.class) {
                assertNotEquals(
                        System.identityHashCode(field.get(source)),
                        System.identityHashCode(field.get(copy)),
                        field.getName() + " should be copied, not shared");
            }
        }
    }

    @Test
    void testStyleCopyIsIndependent() {
        final Style style = IkGui.getStyle();
        final Style copy = new Style(style);
        style.variable.windowPadding.set(1, 2);
        style.color.set(ColorType.TEXT, Color.rgba(1, 2, 3, 4));
        assertEquals(new Vector2f(8, 8), copy.variable.windowPadding);
        assertEquals(Color.rgba(255, 255, 255, 255), copy.color.get(ColorType.TEXT));
        style.set(copy);
        assertEquals(new Vector2f(8, 8), style.variable.windowPadding);
        assertEquals(Color.rgba(255, 255, 255, 255), style.color.get(ColorType.TEXT));
    }

    @Test
    void testStyleEditorShowsEveryTab() {
        final Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(10, 10, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(600, 700, Condition.FIRST_USE_EVER);
                    IkGui.begin("IkGui Style Editor", null, WindowFlags.NONE);
                    IkGui.showStyleEditor();
                    IkGui.end();
                };
        frame(ui);
        frame(ui);
        TabBar tabBar = null;
        for (TabBar bar : context.tabBars.values()) {
            if (bar.tabs.stream().anyMatch(tab -> "Colors".equals(tab.name))) {
                tabBar = bar;
            }
        }
        assertNotNull(tabBar, "The style editor tab bar was not found");
        for (String name : new String[] {"Sizes", "Colors", "Fonts", "Rendering"}) {
            final TabItem tab =
                    tabBar.tabs.stream().filter(t -> name.equals(t.name)).findFirst().orElseThrow();
            tabBar.nextSelectedTabID = tab.id;
            frame(ui);
            frame(ui);
        }
        assertTrue(context.windowStack.isEmpty());
        assertFalse(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("[ikgui-error]")),
                context.debugLogBuffer.getText());
    }

    @Test
    void testStyleColorNames() {
        assertEquals("WINDOW_BACKGROUND", IkGui.getStyleColorName(ColorType.WINDOW_BACKGROUND));
    }
}
