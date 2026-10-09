package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.ColorEditFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Color;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for color edits, pickers, buttons, plots, and progress bars. These run headless. */
class IkGuiColorTest {

    /** How much precision we expect in float comparisons. */
    private static final float DELTA = 0.01f;

    private Context context;

    /** The screen position of the cursor before the widget, recorded by the UI code in tests. */
    private final Vector2f cursor = new Vector2f();

    /** The bounding box of the last item, recorded by the UI code in tests. */
    private final Vector2f itemMin = new Vector2f();

    private final Vector2f itemMax = new Vector2f();

    /** The default item width of the host window, recorded by the UI code in tests. */
    private float itemWidth;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        // Process one input event per frame for each input, so we can be precise about timing
        context.io.configInputTrickleEventQueue = true;
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

    private void moveMouse(float x, float y) {
        context.io.addMousePosEvent(x, y);
    }

    private void mouse(MouseButton button, boolean down) {
        context.io.addMouseButtonEvent(button, down);
    }

    private Runnable window(Runnable body) {
        return () -> {
            IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
            IkGui.setNextWindowSize(500, 600, Condition.FIRST_USE_EVER);
            IkGui.begin("Host", null, WindowFlags.NONE);
            itemWidth = IkGui.calculateItemWidth();
            IkGui.getCursorScreenPos(cursor);
            body.run();
            IkGui.getItemRectMin(itemMin);
            IkGui.getItemRectMax(itemMax);
            IkGui.end();
        };
    }

    private void click(float x, float y, Runnable ui) {
        moveMouse(x, y);
        frame(ui);
        mouse(MouseButton.LEFT, true);
        frame(ui);
        mouse(MouseButton.LEFT, false);
        frame(ui);
    }

    @Test
    void testRgbToHsv() {
        final float[] hsv = new float[3];
        assertTrue(Color.rgbTohsv(new float[] {0.5f, 0.8f, 0.2f}, hsv));
        assertArrayEquals(new float[] {0.25f, 0.75f, 0.8f}, hsv, 0.0001f);

        final float[] rgb = new float[3];
        assertTrue(Color.hsvToColor(hsv, rgb));
        assertArrayEquals(new float[] {0.5f, 0.8f, 0.2f}, rgb, 0.0001f);
    }

    @Test
    void testParseHexColor() {
        final int[] result = new int[4];
        IkGuiImplColor.parseHexColor("#FF8000", false, result);
        assertArrayEquals(new int[] {255, 128, 0, 255}, result);

        IkGuiImplColor.parseHexColor("#11223344", true, result);
        assertArrayEquals(new int[] {0x11, 0x22, 0x33, 0x44}, result);

        IkGuiImplColor.parseHexColor("#11223344", false, result);
        assertArrayEquals(new int[] {0x11, 0x22, 0x33, 255}, result, "Alpha is ignored");

        IkGuiImplColor.parseHexColor("  #A", true, result);
        assertArrayEquals(new int[] {10, 0, 0, 255}, result, "Missing components default");

        IkGuiImplColor.parseHexColor("#xyz", true, result);
        assertArrayEquals(new int[] {0, 0, 0, 255}, result);
    }

    @Test
    void testColorButton() {
        final float[] color = {1.0f, 0.0f, 0.0f, 0.5f};
        final int[] presses = {0};
        final Runnable ui =
                window(
                        () -> {
                            if (IkGui.colorButton("Red", color)) {
                                ++presses[0];
                            }
                        });
        frame(ui);
        final float frameHeight = IkGui.getFrameHeight();
        assertEquals(frameHeight, itemMax.x - itemMin.x, DELTA);
        assertEquals(frameHeight, itemMax.y - itemMin.y, DELTA);

        click((itemMin.x + itemMax.x) / 2, (itemMin.y + itemMax.y) / 2, ui);
        assertEquals(1, presses[0]);

        // Explicit sizes, and 3 component colors
        final float[] rgb = {0.0f, 1.0f, 0.0f};
        frame(window(() -> IkGui.colorButton("Green", rgb, ColorEditFlags.NONE, 40, 20)));
        assertEquals(40, itemMax.x - itemMin.x, DELTA);
        assertEquals(20, itemMax.y - itemMin.y, DELTA);
    }

    @Test
    void testColorEditRejectsShortArrays() {
        final float[] rgb = {0.1f, 0.2f, 0.3f};
        final boolean[] results = {true, true};
        frame(
                window(
                        () -> {
                            results[0] = IkGui.colorEdit4("Edit4", rgb);
                            results[1] = IkGui.colorEdit3("Edit3", rgb);
                        }));
        assertFalse(results[0]);
        assertFalse(results[1]);
        assertArrayEquals(new float[] {0.1f, 0.2f, 0.3f}, rgb);
    }

    @Test
    void testColorEditLayout() {
        final float[] color = {0.5f, 0.5f, 0.5f, 1.0f};
        final Runnable ui = window(() -> IkGui.colorEdit4("Color", color));
        frame(ui);
        frame(ui);

        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, "Color", false, -1.0f);
        final float innerSpacing = context.style.variable.itemInnerSpacing.x;
        assertEquals(cursor.x, itemMin.x, DELTA);
        assertEquals(itemWidth + innerSpacing + labelSize.x, itemMax.x - itemMin.x, 1.0f);
        assertEquals(IkGui.getFrameHeight(), itemMax.y - itemMin.y, DELTA);
        // Nothing was edited, so the values should be untouched by the conversions
        assertArrayEquals(new float[] {0.5f, 0.5f, 0.5f, 1.0f}, color);
    }

    @Test
    void testColorEditDragRed() {
        final float[] color = {0.5f, 0.5f, 0.5f, 1.0f};
        final boolean[] changed = {false};
        final Runnable ui =
                window(
                        () -> {
                            if (IkGui.colorEdit4("Color", color)) {
                                changed[0] = true;
                            }
                        });
        frame(ui);
        frame(ui);

        // The red drag is the first component on the left
        final float y = (itemMin.y + itemMax.y) / 2;
        final float startX = itemMin.x + 10;
        moveMouse(startX, y);
        frame(ui);
        mouse(MouseButton.LEFT, true);
        frame(ui);
        for (int i = 1; i <= 10; ++i) {
            moveMouse(startX + 5 * i, y);
            frame(ui);
        }
        mouse(MouseButton.LEFT, false);
        frame(ui);

        assertTrue(changed[0]);
        assertTrue(color[0] > 0.6f, "Red should have increased, but was " + color[0]);
        assertEquals(0.5f, color[1], DELTA);
        assertEquals(0.5f, color[2], DELTA);
        assertEquals(1.0f, color[3], DELTA);
    }

    @Test
    void testColorEditOpensPicker() {
        final float[] color = {0.25f, 0.5f, 0.75f, 1.0f};
        final Runnable ui = window(() -> IkGui.colorEdit4("Color", color));
        frame(ui);
        frame(ui);

        // The color button is to the right of the inputs
        final float frameHeight = IkGui.getFrameHeight();
        final float innerSpacing = context.style.variable.itemInnerSpacing.x;
        final float widthInputs = itemWidth - (frameHeight + innerSpacing);
        final float buttonX = itemMin.x + widthInputs + innerSpacing + frameHeight / 2;
        final float buttonY = itemMin.y + frameHeight / 2;
        click(buttonX, buttonY, ui);

        assertEquals(1, context.openPopupStack.size());
        assertArrayEquals(color, context.colorPickerReference);
        frame(ui);
        assertEquals(1, context.openPopupStack.size(), "The picker should stay open");
    }

    @Test
    void testColorPickerHueBar() {
        final float[] color = {1.0f, 0.0f, 0.0f, 1.0f};
        final int flags =
                ColorEditFlags.PICKER_HUE_BAR
                        | ColorEditFlags.NO_SIDE_PREVIEW
                        | ColorEditFlags.NO_INPUTS;
        final Runnable ui = window(() -> IkGui.colorPicker4("##picker", color, flags));
        frame(ui);
        frame(ui);

        final float barsWidth = IkGui.getFrameHeight();
        final float innerSpacing = context.style.variable.itemInnerSpacing.x;
        final float svSize = itemWidth - (barsWidth + innerSpacing);
        final float pickerX = cursor.x;
        final float pickerY = cursor.y;

        // Saturation 0.5, value 0.75, hue stays red
        click(pickerX + (svSize - 1) * 0.5f, pickerY + (svSize - 1) * 0.25f, ui);
        assertArrayEquals(new float[] {0.75f, 0.375f, 0.375f, 1.0f}, color, DELTA);

        // Hue 0.5, which is cyan
        click(pickerX + svSize + innerSpacing + barsWidth / 2, pickerY + (svSize - 1) * 0.5f, ui);
        assertArrayEquals(new float[] {0.375f, 0.75f, 0.75f, 1.0f}, color, DELTA);
    }

    @Test
    void testColorPickerHueWheel() {
        final float[] color = {1.0f, 0.0f, 0.0f, 1.0f};
        final int flags =
                ColorEditFlags.PICKER_HUE_WHEEL
                        | ColorEditFlags.NO_SIDE_PREVIEW
                        | ColorEditFlags.NO_INPUTS;
        final Runnable ui = window(() -> IkGui.colorPicker4("##picker", color, flags));
        frame(ui);
        frame(ui);

        final float barsWidth = IkGui.getFrameHeight();
        final float innerSpacing = context.style.variable.itemInnerSpacing.x;
        final float svSize = itemWidth - (barsWidth + innerSpacing);
        final float centerX = cursor.x + (svSize + barsWidth) * 0.5f;
        final float centerY = cursor.y + svSize * 0.5f;
        final float radius = svSize * 0.5f - svSize * 0.08f * 0.5f;

        // Straight down on screen is a quarter turn, hue 0.25
        click(centerX, centerY + radius, ui);
        assertArrayEquals(new float[] {0.5f, 1.0f, 0.0f, 1.0f}, color, DELTA);
    }

    @Test
    void testColorPickerAlphaBar() {
        final float[] color = {1.0f, 0.0f, 0.0f, 1.0f};
        final int flags =
                ColorEditFlags.PICKER_HUE_BAR
                        | ColorEditFlags.NO_SIDE_PREVIEW
                        | ColorEditFlags.NO_INPUTS
                        | ColorEditFlags.ALPHA_BAR;
        final Runnable ui = window(() -> IkGui.colorPicker4("##picker", color, flags));
        frame(ui);
        frame(ui);

        final float barsWidth = IkGui.getFrameHeight();
        final float innerSpacing = context.style.variable.itemInnerSpacing.x;
        final float svSize = itemWidth - 2 * (barsWidth + innerSpacing);
        final float alphaBarX = cursor.x + svSize + 2 * innerSpacing + barsWidth * 1.5f;

        click(alphaBarX, cursor.y + (svSize - 1) * 0.75f, ui);
        assertArrayEquals(new float[] {1.0f, 0.0f, 0.0f, 0.25f}, color, DELTA);
    }

    @Test
    void testColorPickerWithInputs() {
        final float[] color = {0.2f, 0.4f, 0.6f, 0.8f};
        final float[] reference = {0.0f, 0.0f, 0.0f, 1.0f};
        final boolean[] changed = {false};
        final Runnable ui =
                window(
                        () ->
                                changed[0] |=
                                        IkGui.colorPicker4(
                                                "Picker",
                                                color,
                                                ColorEditFlags.ALPHA_BAR,
                                                reference));
        frame(ui);
        frame(ui);
        assertFalse(changed[0]);
        assertArrayEquals(new float[] {0.2f, 0.4f, 0.6f, 0.8f}, color);
    }

    @Test
    void testConfigColorEditFlagsSanitized() {
        context.io.configColorEditFlags =
                ColorEditFlags.DISPLAY_RGB | ColorEditFlags.DISPLAY_HSV | ColorEditFlags.FLOAT;
        frame(() -> {});
        final int flags = context.io.configColorEditFlags;
        assertEquals(1, Integer.bitCount(flags & ColorEditFlags.DISPLAY_MASK));
        assertEquals(ColorEditFlags.FLOAT, flags & ColorEditFlags.DATA_TYPE_MASK);
        assertEquals(ColorEditFlags.PICKER_HUE_BAR, flags & ColorEditFlags.PICKER_MASK);
        assertEquals(ColorEditFlags.INPUT_RGB, flags & ColorEditFlags.INPUT_MASK);
    }

    @Test
    void testProgressBar() {
        final float[] available = {0};
        frame(
                window(
                        () -> {
                            available[0] = IkGui.getContentRegionAvailableX();
                            IkGui.progressBar(0.5f);
                        }));
        final float height =
                IkGuiInternal.getFontSize() + context.style.variable.framePadding.y * 2.0f;
        // The default size spans the available width
        assertEquals(available[0], itemMax.x - itemMin.x, DELTA);
        assertEquals(height, itemMax.y - itemMin.y, DELTA);

        frame(window(() -> IkGui.progressBar(0.5f, 0, 0)));
        assertEquals(itemWidth, itemMax.x - itemMin.x, DELTA);

        frame(window(() -> IkGui.progressBar(-1.0f, 100, 30, "Loading")));
        assertEquals(100, itemMax.x - itemMin.x, DELTA);
        assertEquals(30, itemMax.y - itemMin.y, DELTA);

        // Out of range and NaN values shouldn't cause any problems
        frame(window(() -> IkGui.progressBar(Float.NaN)));
        frame(window(() -> IkGui.progressBar(2.0f)));
    }

    @Test
    void testLabelText() {
        frame(window(() -> IkGui.labelText("Label", "Value")));
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, "Label", false, -1.0f);
        final float innerSpacing = context.style.variable.itemInnerSpacing.x;
        assertEquals(itemWidth + innerSpacing + labelSize.x, itemMax.x - itemMin.x, DELTA);
    }

    @Test
    void testPlotHover() {
        final float[] values = {0.0f, 1.0f, 4.0f, 9.0f, 16.0f};
        final int[] hovered = {-2};
        final Runnable ui =
                window(
                        () ->
                                hovered[0] =
                                        IkGuiImplMiscWidgets.plotEx(
                                                IkGuiImplMiscWidgets.PlotType.LINES,
                                                "Lines",
                                                i -> values[i],
                                                values.length,
                                                0,
                                                null,
                                                Float.MAX_VALUE,
                                                Float.MAX_VALUE,
                                                0,
                                                80));
        frame(ui);
        assertEquals(-1, hovered[0]);
        assertEquals(80, itemMax.y - itemMin.y, DELTA);

        // The plot frame is the item width, the label is after that
        final float padding = context.style.variable.framePadding.x;
        final float innerWidth = itemWidth - padding * 2;
        moveMouse(itemMin.x + padding + innerWidth * 0.6f, (itemMin.y + itemMax.y) / 2);
        frame(ui);
        frame(ui);
        // 4 line segments, so 60% of the way along is the third segment
        assertEquals(2, hovered[0]);

        // The public versions draw the same thing
        frame(
                window(
                        () -> {
                            IkGui.plotLines("Lines", values, values.length);
                            IkGui.plotHistogram("Histogram", values, values.length);
                            IkGui.plotHistogram(
                                    "Negative", new float[] {-1.0f, 2.0f, -3.0f}, 3, 1, "Overlay");
                        }));
    }
}
