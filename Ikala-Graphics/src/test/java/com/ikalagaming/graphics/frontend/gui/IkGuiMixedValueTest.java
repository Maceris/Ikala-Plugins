package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.DrawData;
import com.ikalagaming.graphics.frontend.gui.data.DrawList;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;
import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Color;

import org.joml.Vector2f;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/** Tests for mixed values, pushing one component of a style variable, and float style colors. */
class IkGuiMixedValueTest {
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

    private static void beginHost() {
        IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
        IkGui.setNextWindowSize(400, 400, Condition.FIRST_USE_EVER);
        IkGui.begin("Host", null, WindowFlags.NONE);
    }

    /** Run some widgets in a window, returning the text they rendered. */
    private String renderedText(Runnable widgets) {
        final Runnable ui =
                () -> {
                    beginHost();
                    widgets.run();
                    IkGui.end();
                };
        frame(ui);
        frame(
                () -> {
                    beginHost();
                    IkGui.logToClipboard();
                    widgets.run();
                    IkGui.logFinish();
                    IkGui.end();
                });
        return IkGui.getClipboardText();
    }

    private boolean anyErrors() {
        return context.debugLogBuffer.snapshot().stream()
                .anyMatch(line -> line.contains("[ikgui-error]"));
    }

    @Test
    void testPushStyleVarX() {
        final Vector2f before = new Vector2f(context.style.variable.framePadding);
        frame(
                () -> {
                    IkGui.pushStyleVarX(StyleVariable.FRAME_PADDING, 13);
                    assertEquals(13, context.style.variable.framePadding.x);
                    assertEquals(before.y, context.style.variable.framePadding.y);
                    IkGui.pushStyleVarY(StyleVariable.FRAME_PADDING, 7);
                    assertEquals(new Vector2f(13, 7), context.style.variable.framePadding);
                    IkGui.popStyleVar(2);
                });
        assertEquals(before, context.style.variable.framePadding);
        assertFalse(anyErrors(), context.debugLogBuffer.getText());
    }

    @Test
    void testPushStyleVarXNeedsTwoFloats() {
        final float before = context.style.variable.frameRounding;
        frame(() -> IkGui.pushStyleVarX(StyleVariable.FRAME_ROUNDING, 5));
        // Nothing was pushed, so there's nothing left on the stack at the end of the frame
        assertEquals(before, context.style.variable.frameRounding);
        assertTrue(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("is not 2 floats")),
                context.debugLogBuffer.getText());
        assertFalse(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("Missing popStyleVar()")));
    }

    @Test
    void testGetStyleColorVec4() {
        assertEquals(new Vector4f(1, 1, 1, 1), IkGui.getStyleColorVec4(ColorType.TEXT));
        frame(
                () -> {
                    IkGui.pushStyleColor(ColorType.TEXT, Color.rgba(255, 0, 0, 255));
                    final Vector4f pushed = new Vector4f();
                    IkGui.getStyleColorVec4(ColorType.TEXT, pushed);
                    assertEquals(new Vector4f(1, 0, 0, 1), pushed);
                    IkGui.popStyleColor();
                });
        assertEquals(new Vector4f(1, 1, 1, 1), IkGui.getStyleColorVec4(ColorType.TEXT));
    }

    @Test
    void testDragsAndSlidersShowTheMixedLabel() {
        final float[] value = {12.0f};
        final Runnable widgets =
                () -> {
                    IkGui.dragFloat("Drag", value);
                    IkGui.sliderFloat("Slider", value, 0, 100);
                    IkGui.vSliderFloat("VSlider", 20, 80, value, 0, 100);
                };
        final String normal = renderedText(widgets);
        assertTrue(normal.contains("{ 12.000 }"), normal);
        assertFalse(normal.contains("{ - }"), normal);

        final String mixed =
                renderedText(
                        () -> {
                            IkGui.pushItemFlag(ItemFlags.MIXED_VALUE, true);
                            widgets.run();
                            IkGui.popItemFlag();
                        });
        assertFalse(mixed.contains("12.000"), mixed);
        // The drag and the slider, the vertical slider doesn't decorate its value
        assertTrue(mixed.contains("{ - } Drag"), mixed);
        assertTrue(mixed.contains("{ - } Slider"), mixed);
        assertTrue(mixed.contains("- VSlider"), mixed);

        context.mixedValueLabel = "(various)";
        final String custom =
                renderedText(
                        () -> {
                            IkGui.pushItemFlag(ItemFlags.MIXED_VALUE, true);
                            widgets.run();
                            IkGui.popItemFlag();
                        });
        assertTrue(custom.contains("{ (various) }"), custom);
        assertFalse(anyErrors(), context.debugLogBuffer.getText());
    }

    /** Count the circles drawn in a color. */
    private static int circlesInColor(DrawList drawList, int color) {
        int circles = 0;
        final int count = drawList.commandBuffer.limit() / DrawData.SIZE_OF_DRAW_COMMAND;
        for (int i = 0; i < count; ++i) {
            final int offset = i * DrawData.SIZE_OF_DRAW_COMMAND;
            if (drawList.commandBuffer.getInt(offset + 16)
                    != DrawList.ElementType.CIRCLE.getTypeID()) {
                continue;
            }
            final int detail =
                    drawList.commandBuffer.getInt(offset + 4) * DrawData.SIZE_OF_POINT_DETAIL;
            if (drawList.pointDetailBuffer.getInt(detail + 8) == color) {
                circles++;
            }
        }
        return circles;
    }

    @Test
    void testMixedRadioButtonHasNoMark() {
        final int markColor = Color.rgba(1, 2, 3, 255);
        context.style.color.set(ColorType.CHECK_MARK, markColor);
        final boolean[] mixed = {false};
        final Runnable ui =
                () -> {
                    beginHost();
                    IkGui.pushItemFlag(ItemFlags.MIXED_VALUE, mixed[0]);
                    IkGui.radioButton("Radio", true);
                    IkGui.popItemFlag();
                    IkGui.end();
                };
        frame(ui);
        frame(ui);
        final Window window = IkGuiInternal.findWindowByName("Host");
        assertEquals(1, circlesInColor(window.drawList, markColor));
        mixed[0] = true;
        frame(ui);
        assertEquals(0, circlesInColor(window.drawList, markColor));
    }

    @Test
    void testMixedValuesDemo() {
        final float[] backup = IkGuiDemo.mixedValuesItems.clone();
        try {
            final Runnable section =
                    () -> {
                        IkGui.setNextItemOpen(true, Condition.ALWAYS);
                        IkGuiDemo.showWidgetsMixedValues();
                    };
            // The values differ, so the reference widgets show the mixed label
            final String mixed = renderedText(section);
            assertTrue(mixed.contains("{ - }"), mixed);

            Arrays.fill(IkGuiDemo.mixedValuesItems, 5.0f);
            final String same = renderedText(section);
            assertTrue(same.contains("{ 5.000 }"), same);
            assertFalse(anyErrors(), context.debugLogBuffer.getText());
        } finally {
            System.arraycopy(backup, 0, IkGuiDemo.mixedValuesItems, 0, backup.length);
        }
    }
}
