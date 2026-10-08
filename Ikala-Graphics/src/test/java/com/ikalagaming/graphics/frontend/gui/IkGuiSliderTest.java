package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.IkByte;
import com.ikalagaming.graphics.frontend.gui.data.IkDouble;
import com.ikalagaming.graphics.frontend.gui.data.IkFloat;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.data.IkLong;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.enums.SliderDataType;
import com.ikalagaming.graphics.frontend.gui.flags.SliderFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for drags, sliders, and their data type helpers. These run headless. */
class IkGuiSliderTest {

    /** How much precision we expect in float comparisons. */
    private static final float DELTA = 0.001f;

    private Context context;

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

    private static void frames(int count, Runnable ui) {
        for (int i = 0; i < count; ++i) {
            frame(ui);
        }
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
            IkGui.setNextWindowSize(500, 400, Condition.FIRST_USE_EVER);
            IkGui.begin("Host", null, WindowFlags.NONE);
            itemWidth = IkGui.calculateItemWidth();
            body.run();
            IkGui.getItemRectMin(itemMin);
            IkGui.getItemRectMax(itemMax);
            IkGui.end();
        };
    }

    /**
     * Press the left mouse button at a position, drag horizontally in steps, and release.
     *
     * @param startX The start x position.
     * @param y The y position.
     * @param distance How far to drag.
     * @param ui The UI for each frame.
     */
    private void drag(float startX, float y, float distance, Runnable ui) {
        moveMouse(startX, y);
        frame(ui);
        mouse(MouseButton.LEFT, true);
        frame(ui);
        final int steps = 10;
        for (int i = 1; i <= steps; ++i) {
            moveMouse(startX + distance * i / steps, y);
            frame(ui);
        }
        mouse(MouseButton.LEFT, false);
        frame(ui);
    }

    private void click(float x, float y, Runnable ui) {
        moveMouse(x, y);
        frame(ui);
        mouse(MouseButton.LEFT, true);
        frame(ui);
        mouse(MouseButton.LEFT, false);
        frame(ui);
    }

    // ---------------------------------------------------------------------------------------
    // Data type helpers
    // ---------------------------------------------------------------------------------------

    @Test
    void testFormatScalar() {
        assertEquals("1.500", IkGuiImplSliders.formatScalar(SliderDataType.FLOAT, 1.5, "%.3f"));
        assertEquals("42", IkGuiImplSliders.formatScalar(SliderDataType.INT, 42, "%d"));
        assertEquals("90 deg", IkGuiImplSliders.formatScalar(SliderDataType.FLOAT, 90, "%.0f deg"));
        assertEquals("7", IkGuiImplSliders.formatScalar(SliderDataType.LONG, 7, "%lld"));
        assertEquals("7", IkGuiImplSliders.formatScalar(SliderDataType.LONG, 7, "%I64d"));
        assertEquals("7", IkGuiImplSliders.formatScalar(SliderDataType.INT, 7, "%u"));
        assertEquals(
                "Value:  3.14 units",
                IkGuiImplSliders.formatScalar(
                        SliderDataType.FLOAT, 3.141_59, "Value: %5.2f units"));
        assertEquals("50%", IkGuiImplSliders.formatScalar(SliderDataType.INT, 50, "%d%%"));
        assertEquals("hidden", IkGuiImplSliders.formatScalar(SliderDataType.INT, 50, "hidden"));
        // Integers can be displayed with float formats
        assertEquals("3.0", IkGuiImplSliders.formatScalar(SliderDataType.INT, 3, "%.1f"));
    }

    @Test
    void testParseFormatPrecision() {
        assertEquals(3, IkGuiImplSliders.parseFormatPrecision("%.3f", 0));
        assertEquals(0, IkGuiImplSliders.parseFormatPrecision("%.0f deg", 3));
        assertEquals(5, IkGuiImplSliders.parseFormatPrecision("%d", 5));
        assertEquals(5, IkGuiImplSliders.parseFormatPrecision("no value", 5));
        assertEquals(-1, IkGuiImplSliders.parseFormatPrecision("%e", 3));
        assertEquals(2, IkGuiImplSliders.parseFormatPrecision("x = %8.2f", 3));
    }

    @Test
    void testRoundScalarWithFormat() {
        assertEquals(
                1.235,
                IkGuiImplSliders.roundScalarWithFormat("%.3f", SliderDataType.DOUBLE, 1.234_56),
                1e-9);
        assertEquals(
                2.0,
                IkGuiImplSliders.roundScalarWithFormat("%.0f deg", SliderDataType.DOUBLE, 1.6),
                1e-9);
        // No value displayed, so no rounding
        assertEquals(
                1.234_56,
                IkGuiImplSliders.roundScalarWithFormat("hidden", SliderDataType.DOUBLE, 1.234_56),
                1e-9);
    }

    @Test
    void testToTypeClampsAndTruncates() {
        assertEquals(127, IkGuiImplSliders.toType(SliderDataType.BYTE, 300), 0);
        assertEquals(-128, IkGuiImplSliders.toType(SliderDataType.BYTE, -300), 0);
        assertEquals(3, IkGuiImplSliders.toType(SliderDataType.INT, 3.9), 0);
        assertEquals((float) 0.1, IkGuiImplSliders.toType(SliderDataType.FLOAT, 0.1), 0);
    }

    @Test
    void testScaleRatioLinearAndLogarithmic() {
        final SliderDataType type = SliderDataType.FLOAT;
        assertEquals(0.5f, IkGuiImplSliders.scaleRatioFromValue(type, 5, 0, 10, 0, 0), DELTA);
        assertEquals(5.0, IkGuiImplSliders.scaleValueFromRatio(type, 0.5f, 0, 10, 0, 0), DELTA);
        // Logarithmic: halfway between 1 and 100 is 10
        assertEquals(
                0.5f, IkGuiImplSliders.scaleRatioFromValue(type, 10, 1, 100, 0.001f, 0), DELTA);
        assertEquals(
                10.0, IkGuiImplSliders.scaleValueFromRatio(type, 0.5f, 1, 100, 0.001f, 0), 0.01);
        // Integers round so the grab matches the click
        assertEquals(5, IkGuiImplSliders.scaleValueFromRatioInteger(0.46f, 0, 10, 0, 0));
        assertEquals(0.5f, IkGuiImplSliders.scaleRatioFromValueInteger(5, 0, 10, 0, 0), DELTA);
        // Extents are exact
        assertEquals(100.0, IkGuiImplSliders.scaleValueFromRatio(type, 1.0f, 1, 100, 0.001f, 0), 0);
    }

    @Test
    void testIntegerScalingIsExactForLongs() {
        // These limits can't be told apart as doubles, so this used to collapse to one value
        final long min = Long.MAX_VALUE / 2 - 100;
        final long max = Long.MAX_VALUE / 2;
        assertEquals(min, IkGuiImplSliders.scaleValueFromRatioInteger(0.0f, min, max, 0, 0));
        assertEquals(max, IkGuiImplSliders.scaleValueFromRatioInteger(1.0f, min, max, 0, 0));
        assertEquals(min + 50, IkGuiImplSliders.scaleValueFromRatioInteger(0.5f, min, max, 0, 0));
        assertEquals(0.5f, IkGuiImplSliders.scaleRatioFromValueInteger(min + 50, min, max, 0, 0));
        // Reversed ranges work too
        assertEquals(max, IkGuiImplSliders.scaleValueFromRatioInteger(0.0f, max, min, 0, 0));
        // Huge ranges don't overflow
        assertEquals(
                Long.MAX_VALUE,
                IkGuiImplSliders.scaleValueFromRatioInteger(
                        1.0f, Long.MIN_VALUE, Long.MAX_VALUE, 0, 0));
        assertEquals(
                0.5f,
                IkGuiImplSliders.scaleRatioFromValueInteger(
                        0, Long.MIN_VALUE, Long.MAX_VALUE, 0, 0),
                DELTA);
    }

    // ---------------------------------------------------------------------------------------
    // Drags
    // ---------------------------------------------------------------------------------------

    @Test
    void testDragFloatChangesWithMouseMovement() {
        final float[] value = {0.0f};
        final boolean[] changed = {false};
        Runnable ui = window(() -> changed[0] |= IkGui.dragFloat("Drag", value, 0.5f));
        frames(2, ui);
        drag(itemMin.x + 10, itemMin.y + 5, 100, ui);
        assertTrue(changed[0]);
        // The drag threshold eats a few pixels, after that each pixel is worth 0.5
        assertTrue(value[0] > 40.0f, "value was " + value[0]);
        assertTrue(value[0] <= 50.0f, "value was " + value[0]);
        assertEquals(0, context.activeID);
    }

    @Test
    void testDragIntClampsToRange() {
        final int[] value = {5};
        Runnable ui = window(() -> IkGui.dragInt("Drag", value, 1.0f, 0, 20));
        frames(2, ui);
        drag(itemMin.x + 10, itemMin.y + 5, 200, ui);
        assertEquals(20, value[0]);
        drag(itemMin.x + 210, itemMin.y + 5, -200, ui);
        assertEquals(0, value[0]);
    }

    @Test
    void testDragWrapAround() {
        final int[] value = {8};
        Runnable ui =
                window(
                        () ->
                                IkGui.dragInt(
                                        "Drag", value, 1.0f, 0, 9, "%d", SliderFlags.WRAP_AROUND));
        frames(2, ui);
        drag(itemMin.x + 10, itemMin.y + 5, 10, ui);
        assertTrue(value[0] >= 0 && value[0] <= 9);
        assertTrue(value[0] < 8, "value should have wrapped, was " + value[0]);
    }

    @Test
    void testDragCanceledWithRightClick() {
        final float[] value = {1.0f};
        Runnable ui = window(() -> IkGui.dragFloat("Drag", value, 1.0f));
        frames(2, ui);
        final float x = itemMin.x + 10;
        final float y = itemMin.y + 5;
        moveMouse(x, y);
        frame(ui);
        mouse(MouseButton.LEFT, true);
        frame(ui);
        for (int i = 1; i <= 5; ++i) {
            moveMouse(x + i * 10, y);
            frame(ui);
        }
        assertTrue(value[0] > 1.0f);
        mouse(MouseButton.RIGHT, true);
        frame(ui);
        mouse(MouseButton.RIGHT, false);
        frame(ui);
        assertEquals(1.0f, value[0], DELTA);
        assertEquals(0, context.activeID);
        mouse(MouseButton.LEFT, false);
        frame(ui);
    }

    @Test
    void testDragFloat3EditsOneComponent() {
        final float[] values = {1, 2, 3};
        final Vector2f groupMin = new Vector2f();
        final Vector2f groupMax = new Vector2f();
        Runnable ui =
                window(
                        () -> {
                            IkGui.dragFloat3("Position", values);
                            IkGui.getItemRectMin(groupMin);
                            IkGui.getItemRectMax(groupMax);
                        });
        frames(2, ui);
        // The group covers three frames and the label, drag in the middle third of the frames
        final float framesWidth = itemWidth;
        final float middleX = groupMin.x + framesWidth * 0.5f;
        drag(middleX, groupMin.y + 5, 50, ui);
        assertEquals(1.0f, values[0], DELTA);
        assertTrue(values[1] > 2.0f);
        assertEquals(3.0f, values[2], DELTA);
        assertTrue(groupMax.x > groupMin.x + framesWidth);
    }

    @Test
    void testDragIntRange2KeepsMinBelowMax() {
        final int[] min = {10};
        final int[] max = {20};
        final Vector2f groupMin = new Vector2f();
        Runnable ui =
                window(
                        () -> {
                            IkGui.dragIntRange2("Range", min, max, 1.0f, 0, 100);
                            IkGui.getItemRectMin(groupMin);
                        });
        frames(2, ui);
        drag(groupMin.x + 10, groupMin.y + 5, 100, ui);
        assertEquals(20, min[0]);
        assertEquals(20, max[0]);
    }

    @Test
    void testDragScalarBoxesAndTypes() {
        final IkDouble doubleValue = new IkDouble(0.0);
        final IkLong longValue = new IkLong(0L);
        final IkByte byteValue = new IkByte((byte) 120);
        Runnable ui =
                window(
                        () -> {
                            IkGui.dragScalar("double", SliderDataType.DOUBLE, doubleValue, 0.25f);
                            IkGui.dragScalar("long", SliderDataType.LONG, longValue, 2.0f);
                            IkGui.dragScalar("byte", SliderDataType.BYTE, byteValue, 1.0f);
                        });
        frames(2, ui);
        final float lineHeight = IkGui.getFrameHeightWithSpacing();
        // itemMin is the last item (byte), work up from there
        final float byteY = itemMin.y + 5;
        drag(itemMin.x + 10, byteY - lineHeight * 2, 40, ui);
        drag(itemMin.x + 10, byteY - lineHeight, 40, ui);
        drag(itemMin.x + 10, byteY, 40, ui);
        assertTrue(doubleValue.get() > 0.0);
        assertTrue(longValue.get() > 0L);
        assertEquals(0, longValue.get() % 2, "long value should step by 2");
        // Bytes are clamped to the range of the type
        assertEquals(127, byteValue.get());
    }

    // ---------------------------------------------------------------------------------------
    // Sliders
    // ---------------------------------------------------------------------------------------

    @Test
    void testSliderFloatClickSetsValue() {
        final float[] value = {0.0f};
        Runnable ui = window(() -> IkGui.sliderFloat("Slider", value, 0.0f, 10.0f));
        frames(2, ui);
        final float frameRight = itemMin.x + itemWidth;
        click(frameRight - 1, itemMin.y + 5, ui);
        assertEquals(10.0f, value[0], DELTA);
        click(itemMin.x + 1, itemMin.y + 5, ui);
        assertEquals(0.0f, value[0], DELTA);
        click((itemMin.x + frameRight) * 0.5f, itemMin.y + 5, ui);
        assertEquals(5.0f, value[0], 0.2f);
    }

    @Test
    void testSliderIntAndGrabSize() {
        final int[] value = {0};
        Runnable ui = window(() -> IkGui.sliderInt("Slider", value, 0, 4));
        frames(2, ui);
        final float frameRight = itemMin.x + itemWidth;
        click(itemMin.x + (frameRight - itemMin.x) * 0.6f, itemMin.y + 5, ui);
        // With 5 values each one gets a fifth of the slider, so 60% is the fourth value
        assertEquals(3, value[0]);
    }

    @Test
    void testSliderDragging() {
        final float[] value = {0.0f};
        Runnable ui = window(() -> IkGui.sliderFloat("Slider", value, 0.0f, 100.0f, "%.0f"));
        frames(2, ui);
        final float frameRight = itemMin.x + itemWidth;
        drag(itemMin.x + 2, itemMin.y + 5, frameRight - itemMin.x, ui);
        assertEquals(100.0f, value[0], DELTA);
        // Values are rounded to the format
        assertEquals(Math.round(value[0]), value[0], 0);
    }

    @Test
    void testVSliderTopIsMax() {
        final IkFloat value = new IkFloat(0.0f);
        Runnable ui = window(() -> IkGui.vSliderFloat("##v", 20, 100, value, 0.0f, 1.0f));
        frames(2, ui);
        click(itemMin.x + 10, itemMin.y + 1, ui);
        assertEquals(1.0f, value.get(), DELTA);
        click(itemMin.x + 10, itemMax.y - 1, ui);
        assertEquals(0.0f, value.get(), DELTA);
    }

    @Test
    void testSliderAngle() {
        final float[] radians = {0.0f};
        Runnable ui = window(() -> IkGui.sliderAngle("Angle", radians, -180, 180));
        frames(2, ui);
        final float frameRight = itemMin.x + itemWidth;
        click(frameRight - 1, itemMin.y + 5, ui);
        assertEquals((float) Math.PI, radians[0], DELTA);
    }

    @Test
    void testLogarithmicSlider() {
        final float[] value = {1.0f};
        Runnable ui =
                window(
                        () ->
                                IkGui.sliderFloat(
                                        "Log",
                                        value,
                                        1.0f,
                                        10_000.0f,
                                        "%.3f",
                                        SliderFlags.LOGARITHMIC | SliderFlags.NO_ROUND_TO_FORMAT));
        frames(2, ui);
        final float frameRight = itemMin.x + itemWidth;
        click((itemMin.x + frameRight) * 0.5f, itemMin.y + 5, ui);
        // Halfway along a logarithmic 1..10000 slider is around 100
        assertTrue(value[0] > 50 && value[0] < 200, "value was " + value[0]);
    }

    @Test
    void testReadOnlyItemFlagPreventsEditing() {
        final float[] value = {0.0f};
        Runnable ui =
                window(
                        () -> {
                            IkGui.pushItemFlag(
                                    com.ikalagaming.graphics.frontend.gui.flags.ItemFlags.READ_ONLY,
                                    true);
                            IkGui.sliderFloat("Slider", value, 0.0f, 10.0f);
                            IkGui.popItemFlag();
                        });
        frames(2, ui);
        click(itemMin.x + itemWidth - 1, itemMin.y + 5, ui);
        assertEquals(0.0f, value[0], DELTA);
        assertFalse(value[0] > 0);
    }

    // ---------------------------------------------------------------------------------------
    // Exact limits
    // ---------------------------------------------------------------------------------------

    @Test
    void testLongSliderEndsAreExact() {
        final long min = Long.MAX_VALUE / 2 - 100;
        final long max = Long.MAX_VALUE / 2;
        final IkLong value = new IkLong(min + 10);
        Runnable ui =
                window(() -> IkGui.sliderScalar("Slider", SliderDataType.LONG, value, min, max));
        frames(2, ui);
        click(itemMin.x + itemWidth - 1, itemMin.y + 5, ui);
        assertEquals(max, value.get());
        click(itemMin.x + 1, itemMin.y + 5, ui);
        assertEquals(min, value.get());
        // The middle lands in the middle of the range, not on a rounded double
        click(itemMin.x + itemWidth * 0.5f, itemMin.y + 5, ui);
        assertTrue(value.get() > min + 25 && value.get() < max - 25, "value was " + value.get());
    }

    @Test
    void testLongDragStepsByOne() {
        final long start = Long.MAX_VALUE / 2 - 1000;
        final IkLong value = new IkLong(start);
        Runnable ui = window(() -> IkGui.dragScalar("Drag", SliderDataType.LONG, value, 1.0f));
        frames(2, ui);
        drag(itemMin.x + 10, itemMin.y + 5, 30, ui);
        // The drag threshold eats a few pixels, then each pixel is worth 1
        final long moved = value.get() - start;
        assertTrue(moved > 20 && moved <= 30, "moved by " + moved);
    }

    @Test
    void testLongDragCancelRestoresExactValue() {
        final long start = Long.MAX_VALUE - 3;
        final IkLong value = new IkLong(start);
        Runnable ui = window(() -> IkGui.dragScalar("Drag", SliderDataType.LONG, value, 1.0f));
        frames(2, ui);
        final float x = itemMin.x + 10;
        final float y = itemMin.y + 5;
        moveMouse(x, y);
        frame(ui);
        mouse(MouseButton.LEFT, true);
        frame(ui);
        for (int i = 1; i <= 5; ++i) {
            moveMouse(x - i * 10, y);
            frame(ui);
        }
        assertTrue(value.get() < start);
        mouse(MouseButton.RIGHT, true);
        frame(ui);
        mouse(MouseButton.RIGHT, false);
        frame(ui);
        assertEquals(start, value.get());
        mouse(MouseButton.LEFT, false);
        frame(ui);
    }

    @Test
    void testDragWithoutLimitsSaturatesAtTheTypeRange() {
        final IkByte byteValue = new IkByte((byte) 120);
        Runnable ui = window(() -> IkGui.dragScalar("byte", SliderDataType.BYTE, byteValue, 1.0f));
        frames(2, ui);
        drag(itemMin.x + 10, itemMin.y + 5, 100, ui);
        assertEquals(127, byteValue.get());

        final IkLong longValue = new IkLong(Long.MAX_VALUE - 5);
        Runnable longUI =
                window(() -> IkGui.dragScalar("long", SliderDataType.LONG, longValue, 1.0f));
        frames(2, longUI);
        // Wait long enough that pressing in the same place isn't a double click
        context.frameStartTime -= 1000;
        drag(itemMin.x + 10, itemMin.y + 5, 100, longUI);
        // Without limits, the type's limits are used, so it doesn't overflow
        assertEquals(Long.MAX_VALUE, longValue.get());
    }

    @Test
    void testOneSidedLimit() {
        final IkDouble value = new IkDouble(1.0);
        Runnable ui =
                window(
                        () ->
                                IkGui.dragScalar(
                                        "Drag", SliderDataType.DOUBLE, value, 1.0f, 0.0, null));
        frames(2, ui);
        drag(itemMin.x + 100, itemMin.y + 5, -60, ui);
        assertEquals(0.0, value.get(), 0);
        drag(itemMin.x + 10, itemMin.y + 5, 200, ui);
        assertTrue(value.get() > 150, "value was " + value.get());
    }

    @Test
    void testSliderNeedsBothLimits() {
        final IkInt value = new IkInt(3);
        final boolean[] result = {true};
        Runnable ui =
                window(
                        () ->
                                result[0] =
                                        IkGui.sliderScalar(
                                                "Slider", SliderDataType.INT, value, 0, null));
        frame(ui);
        assertFalse(result[0]);
        assertTrue(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("Sliders need both a min and a max")),
                context.debugLogBuffer.getText());
    }

    @Test
    void testNonIntegerLimitForIntegerTypeIsReported() {
        final IkInt value = new IkInt(3);
        frame(window(() -> IkGui.dragScalar("Drag", SliderDataType.INT, value, 1.0f, 0.5, 10)));
        assertTrue(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("is not a whole number")),
                context.debugLogBuffer.getText());
    }

    /** Ctrl+Click the last item, type some text and press enter. */
    private void typeIntoItem(String text, Runnable ui) {
        context.io.addKeyEvent(Key.LEFT_CTRL, true);
        click(itemMin.x + 10, itemMin.y + 5, ui);
        context.io.addKeyEvent(Key.LEFT_CTRL, false);
        frame(ui);
        context.io.addInputCharacters(text);
        frames(text.length() + 5, ui);
        context.io.addKeyEvent(Key.ENTER, true);
        frames(2, ui);
        context.io.addKeyEvent(Key.ENTER, false);
        frames(2, ui);
    }

    @Test
    void testCtrlClickInputIsExactForLongs() {
        final IkLong value = new IkLong(0);
        Runnable ui = window(() -> IkGui.dragScalar("Drag", SliderDataType.LONG, value, 1.0f));
        frames(2, ui);
        typeIntoItem("4611686018427387903", ui);
        assertEquals(4611686018427387903L, value.get());
    }

    @Test
    void testCtrlClickInputClampsWhenAsked() {
        final IkLong value = new IkLong(5);
        Runnable ui =
                window(
                        () ->
                                IkGui.dragScalar(
                                        "Drag",
                                        SliderDataType.LONG,
                                        value,
                                        1.0f,
                                        0L,
                                        Long.MAX_VALUE - 1,
                                        null,
                                        SliderFlags.CLAMP_ON_INPUT));
        frames(2, ui);
        typeIntoItem("9223372036854775807", ui);
        assertEquals(Long.MAX_VALUE - 1, value.get());
    }

    @Test
    void testInputScalarStepsSaturate() {
        final IkLong value = new IkLong(Long.MAX_VALUE - 1);
        Runnable ui = window(() -> IkGui.inputScalar("##Input", SliderDataType.LONG, value, 5L));
        frames(2, ui);
        // The group ends with the + button
        final float plusX = itemMax.x - IkGui.getFrameHeight() * 0.5f;
        click(plusX, itemMin.y + 5, ui);
        assertEquals(Long.MAX_VALUE, value.get());
    }
}
