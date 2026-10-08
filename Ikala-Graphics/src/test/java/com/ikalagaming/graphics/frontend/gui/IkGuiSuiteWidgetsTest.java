package com.ikalagaming.graphics.frontend.gui;

import static com.ikalagaming.graphics.frontend.gui.IkGuiTestContext.chord;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.data.IkString;
import com.ikalagaming.graphics.frontend.gui.data.InputTextState;
import com.ikalagaming.graphics.frontend.gui.data.ListClipper;
import com.ikalagaming.graphics.frontend.gui.data.MultiSelectIO;
import com.ikalagaming.graphics.frontend.gui.data.MultiSelectState;
import com.ikalagaming.graphics.frontend.gui.data.Payload;
import com.ikalagaming.graphics.frontend.gui.data.SelectionBasicStorage;
import com.ikalagaming.graphics.frontend.gui.data.SelectionRequest;
import com.ikalagaming.graphics.frontend.gui.data.TabBar;
import com.ikalagaming.graphics.frontend.gui.data.Table;
import com.ikalagaming.graphics.frontend.gui.data.TypingSelectRequest;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Axis;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.enums.SelectionRequestType;
import com.ikalagaming.graphics.frontend.gui.enums.SliderDataType;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;
import com.ikalagaming.graphics.frontend.gui.flags.ActivateFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ButtonFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ChildFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ColorEditFlags;
import com.ikalagaming.graphics.frontend.gui.flags.DragDropFlags;
import com.ikalagaming.graphics.frontend.gui.flags.HoveredFlags;
import com.ikalagaming.graphics.frontend.gui.flags.InputFlags;
import com.ikalagaming.graphics.frontend.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.frontend.gui.flags.MultiSelectFlags;
import com.ikalagaming.graphics.frontend.gui.flags.SelectableFlags;
import com.ikalagaming.graphics.frontend.gui.flags.SliderFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TabBarFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TabItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableColumnFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TypingSelectFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.KeyChord;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import org.joml.Vector2f;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Widget tests ported from the Dear ImGui test suite (imgui_tests_widgets.cpp), which is MIT
 * licensed. Each test notes the name of the upstream test it is ported from.
 */
class IkGuiSuiteWidgetsTest {
    private IkGuiTestContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new IkGuiTestContext();
    }

    @AfterEach
    void tearDown() {
        try {
            ctx.assertNoErrors();
        } finally {
            ctx.destroy();
        }
    }

    private static final float EPSILON = 0.0001f;

    /** widgets_button_press. */
    @Test
    void testButtonPress() {
        final int[] counts = new int[6];
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Button0")) {
                        counts[0]++;
                    }
                    if (IkGuiImplButtons.buttonEx(
                            "Button1", 0, 0, ButtonFlags.INTERNAL_PRESSED_ON_DOUBLE_CLICK)) {
                        counts[1]++;
                    }
                    if (IkGuiImplButtons.buttonEx(
                            "Button2",
                            0,
                            0,
                            ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE
                                    | ButtonFlags.INTERNAL_PRESSED_ON_DOUBLE_CLICK)) {
                        counts[2]++;
                    }
                    if (IkGuiImplButtons.buttonEx(
                            "Button3",
                            0,
                            0,
                            ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE_ANYWHERE)) {
                        counts[3]++;
                    }
                    IkGui.pushItemFlag(ItemFlags.BUTTON_REPEAT, true);
                    if (IkGuiImplButtons.buttonEx("Button4", 0, 0, ButtonFlags.NONE)) {
                        counts[4]++;
                    }
                    IkGui.popItemFlag();
                    IkGui.end();
                });
        final var io = ctx.context.io;
        ctx.setRef("Test Window");
        ctx.itemClick("Button0");
        assertEquals(1, counts[0]);
        ctx.itemDoubleClick("Button1");
        assertEquals(1, counts[1]);
        ctx.itemDoubleClick("Button2");
        assertEquals(2, counts[2]);

        // PRESSED_ON_CLICK_RELEASE versus PRESSED_ON_CLICK_RELEASE_ANYWHERE
        counts[2] = 0;
        ctx.mouseMove("Button2");
        ctx.mouseDown(MouseButton.LEFT);
        ctx.mouseMove("Button0");
        ctx.mouseUp(MouseButton.LEFT);
        assertEquals(0, counts[2]);
        ctx.mouseMove("Button3");
        ctx.mouseDown(MouseButton.LEFT);
        ctx.mouseMove("Button0");
        ctx.mouseUp(MouseButton.LEFT);
        assertEquals(1, counts[3]);

        // ItemFlags.BUTTON_REPEAT
        final float delay = io.keyRepeatDelay / 1000.0f;
        final float rate = io.keyRepeatRate / 1000.0f;
        final float step = Math.min(delay, rate) * 0.5f;
        ctx.itemClick("Button4");
        assertEquals(1, counts[4]);
        ctx.mouseDown(MouseButton.LEFT);
        assertEquals(2, counts[4]);
        ctx.sleepNoSkip(delay, step);
        ctx.sleepNoSkip(rate, step);
        ctx.sleepNoSkip(rate, step);
        ctx.sleepNoSkip(rate, step);
        assertEquals(2 + 1 + 3, counts[4]);
        ctx.mouseUp(MouseButton.LEFT);

        // BUTTON_REPEAT with navigation
        ctx.navMoveTo("Button4");
        counts[4] = 0;
        ctx.keyDown(Key.SPACE);
        assertEquals(1, counts[4]);
        ctx.sleepNoSkip(delay, step);
        ctx.sleepNoSkip(rate, step);
        ctx.sleepNoSkip(rate, step);
        ctx.sleepNoSkip(rate, step);
        ctx.keyUp(Key.SPACE);
        assertEquals(1 + 1 + 3, counts[4]);
    }

    /** widgets_button_mouse_buttons. */
    @Test
    void testButtonMouseButtons() {
        final int[] counts = new int[6];
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    final String[] labels = {
                        "ButtonL",
                        "ButtonR",
                        "ButtonM",
                        "ButtonLR",
                        "ButtonL-release",
                        "ButtonR-release"
                    };
                    final int[] flags = {
                        ButtonFlags.MOUSE_BUTTON_LEFT,
                        ButtonFlags.MOUSE_BUTTON_RIGHT,
                        ButtonFlags.MOUSE_BUTTON_MIDDLE,
                        ButtonFlags.MOUSE_BUTTON_LEFT | ButtonFlags.MOUSE_BUTTON_RIGHT,
                        ButtonFlags.MOUSE_BUTTON_LEFT | ButtonFlags.INTERNAL_PRESSED_ON_RELEASE,
                        ButtonFlags.MOUSE_BUTTON_RIGHT | ButtonFlags.INTERNAL_PRESSED_ON_RELEASE
                    };
                    for (int i = 0; i < labels.length; ++i) {
                        if (IkGuiImplButtons.buttonEx(labels[i], 0, 0, flags[i])) {
                            counts[i]++;
                        }
                    }
                    for (int n = 0; n < counts.length; ++n) {
                        IkGui.text(n + ": " + counts[n]);
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("ButtonL", MouseButton.LEFT);
        assertEquals(1, counts[0]);
        ctx.itemClick("ButtonR", MouseButton.RIGHT);
        assertEquals(1, counts[1]);
        ctx.itemClick("ButtonM", MouseButton.MIDDLE);
        assertEquals(1, counts[2]);
        ctx.itemClick("ButtonLR", MouseButton.LEFT);
        ctx.itemClick("ButtonLR", MouseButton.RIGHT);
        assertEquals(2, counts[3]);

        counts[3] = 0;
        ctx.mouseMove("ButtonLR");
        ctx.mouseDown(MouseButton.LEFT);
        ctx.mouseDown(MouseButton.RIGHT);
        ctx.mouseUp(MouseButton.LEFT);
        ctx.mouseUp(MouseButton.RIGHT);
        assertEquals(1, counts[3]);

        counts[3] = 0;
        ctx.mouseMove("ButtonLR");
        ctx.mouseDown(MouseButton.LEFT);
        ctx.mouseMove("ButtonR");
        ctx.mouseDown(MouseButton.RIGHT);
        ctx.mouseUp(MouseButton.LEFT);
        ctx.mouseMove("ButtonLR");
        ctx.mouseUp(MouseButton.RIGHT);
        assertEquals(0, counts[3]);
    }

    /** widgets_button_status: the button behavior frame by frame. */
    @Test
    void testButtonStatus() {
        final String[] nextStep = {null};
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final boolean pressed = IkGui.button("Test");
                    status.querySet(false);
                    if (nextStep[0] != null) {
                        // pressed, hovered, active, activated, deactivated
                        final int[] expected =
                                switch (nextStep[0]) {
                                    case "Init" -> new int[] {0, 0, 0, 0, 0};
                                    case "MovedOver" -> new int[] {0, 1, 0, 0, 0};
                                    case "MouseDown" -> new int[] {0, 1, 1, 1, 0};
                                    case "MovedAway" -> new int[] {0, 0, 1, 0, 0};
                                    case "MovedOverAgain" -> new int[] {0, 1, 1, 0, 0};
                                    case "MouseUp" -> new int[] {1, 1, 0, 0, 1};
                                    default -> new int[] {0, 0, 0, 0, 0};
                                };
                        final int[] actual = {
                            pressed ? 1 : 0,
                            status.hovered,
                            status.active,
                            status.activated,
                            status.deactivated
                        };
                        ctx.checkEquals(
                                java.util.Arrays.toString(expected),
                                java.util.Arrays.toString(actual),
                                nextStep[0]);
                    }
                    nextStep[0] = null;

                    // Allows moving the mouse away from the test button
                    IkGui.button("Unused");
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        ctx.mouseMove("Unused");
        nextStep[0] = "Init";
        ctx.yieldFrame();

        ctx.mouseMove("Test");
        nextStep[0] = "MovedOver";
        ctx.yieldFrame();

        nextStep[0] = "MouseDown";
        ctx.mouseDown(MouseButton.LEFT);

        ctx.mouseMove("Unused");
        nextStep[0] = "MovedAway";
        ctx.yieldFrame();

        ctx.mouseMove("Test");
        nextStep[0] = "MovedOverAgain";
        ctx.yieldFrame();

        nextStep[0] = "MouseUp";
        ctx.mouseUp(MouseButton.LEFT);

        ctx.mouseMove("Unused");
        nextStep[0] = "Done";
        ctx.yieldFrame();
    }

    /** widgets_checkbox_001. */
    @Test
    void testCheckbox001() {
        final IkBoolean value = new IkBoolean(false);
        ctx.setGui(
                () -> {
                    IkGui.begin("Window1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.checkbox("Checkbox", value);
                    IkGui.end();
                });
        assertFalse(value.get());
        ctx.setRef("Window1");
        ctx.itemClick("Checkbox");
        assertTrue(value.get());
    }

    /**
     * Create the storage for one value of a data type.
     *
     * @param type The data type.
     * @return An array holding one zero.
     */
    private static Object storageFor(SliderDataType type) {
        return switch (type) {
            case BYTE -> new byte[1];
            case SHORT -> new short[1];
            case INT -> new int[1];
            case LONG -> new long[1];
            case FLOAT -> new float[1];
            case DOUBLE -> new double[1];
        };
    }

    private static Number zeroFor(SliderDataType type) {
        return switch (type) {
            case BYTE -> (byte) 0;
            case SHORT -> (short) 0;
            case INT -> 0;
            case LONG -> 0L;
            case FLOAT -> 0.0f;
            case DOUBLE -> 0.0;
        };
    }

    /**
     * widgets_datatype_1: every scalar type with drags and sliders, with live edits and mixed
     * values. Java has no unsigned types, and arrays can't be written out of bounds, so upstream's
     * sentinel checks don't apply.
     */
    @Test
    void testDatatype1() {
        final int[] widgetType = {0};
        final boolean[] useLiveEdit = {true};
        final boolean[] useMixedValue = {false};
        final SliderDataType[] dataType = {SliderDataType.INT};
        final Object[] data = {new int[1]};
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(200, 200);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.pushItemFlag(ItemFlags.LIVE_EDIT_ON_INPUT, useLiveEdit[0]);
                    IkGui.pushItemFlag(ItemFlags.MIXED_VALUE, useMixedValue[0]);
                    final boolean ret;
                    if (widgetType[0] == 0) {
                        ret = IkGui.dragScalar("Drag", dataType[0], data[0], 0.5f);
                    } else {
                        final Number zero = zeroFor(dataType[0]);
                        ret = IkGui.sliderScalar("Slider", dataType[0], data[0], zero, zero);
                    }
                    status.queryInc(ret);
                    IkGui.popItemFlag();
                    IkGui.popItemFlag();
                    IkGui.end();
                });
        final var io = ctx.context.io;
        ctx.setRef("Test Window");
        for (int step = 0; step < 4; ++step) {
            useLiveEdit[0] = (step & 1) == 0;
            useMixedValue[0] = (step & 2) != 0;
            for (int type = 0; type < 2; ++type) {
                for (SliderDataType t : SliderDataType.values()) {
                    final String description = "step " + step + ", widget " + type + ", " + t;
                    widgetType[0] = type;
                    dataType[0] = t;
                    data[0] = storageFor(t);
                    final String widgetName = type == 0 ? "Drag" : "Slider";

                    if (type == 0) {
                        status.clear();
                        ctx.mouseMove(widgetName);
                        ctx.mouseDown(MouseButton.LEFT);
                        ctx.mouseMoveToPos(io.mousePosition.x + 30, io.mousePosition.y);
                        assertTrue(status.edited >= 1, description);
                        status.clear();
                        ctx.mouseMoveToPos(io.mousePosition.x - 40, io.mousePosition.y);
                        assertTrue(status.edited >= 1, description);
                        ctx.mouseUp(MouseButton.LEFT);
                    }

                    status.clear();
                    ctx.itemInput(widgetName);
                    ctx.keyChars("123");
                    if (!useLiveEdit[0]) {
                        ctx.keyPress(Key.ENTER);
                    }
                    assertTrue(status.retValue >= 1, description);
                    assertTrue(status.edited >= 1, description);
                    status.clear();
                    ctx.yieldFrame();
                    // It doesn't keep returning as edited
                    assertEquals(0, status.retValue, description);
                    assertEquals(0, status.edited, description);

                    status.clear();
                    ctx.keyPress(Key.ENTER);

                    // A single edit with the same result counts as an edit with mixed values
                    // and live edits
                    status.clear();
                    ctx.itemInput(widgetName);
                    ctx.keyPress(chord(KeyModFlags.CTRL, Key.A));
                    ctx.keyPress(chord(KeyModFlags.CTRL, Key.C));
                    ctx.keyPress(chord(KeyModFlags.CTRL, Key.V));
                    assertEquals(
                            useMixedValue[0] && useLiveEdit[0] ? 1 : 0, status.edited, description);
                    ctx.keyPress(Key.ENTER);
                }
            }
        }
    }

    /** widgets_dragslider_as_input: drags and color edits used as text inputs. */
    @Test
    void testDragSliderAsInput() {
        final int[] value = {0};
        final float[] color = new float[4];
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.dragInt("Drag", value);
                    IkGui.colorEdit4("Color", color);
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        assertEquals(0, value[0]);
        ctx.itemInput("Drag");
        assertEquals(ctx.getID("Drag"), ctx.context.activeID);
        ctx.keyCharsAppendEnter("123");
        assertEquals(123, value[0]);

        ctx.itemInput("Color/##Y");
        assertEquals(ctx.getID("Color/##Y"), ctx.context.activeID);
        ctx.keyCharsAppend("123");
        ctx.keyPress(Key.TAB);
        assertEquals(123.0f / 255.0f, color[1], EPSILON);
        ctx.keyCharsAppendEnter("200");
        assertEquals(0.0f, color[0], EPSILON);
        assertEquals(123.0f / 255.0f, color[1], EPSILON);
        assertEquals(200.0f / 255.0f, color[2], EPSILON);
    }

    /** widgets_dragslider_initial_value: context.activeIDValueOnActivation. */
    @Test
    void testDragSliderInitialValue() {
        final float[] floatValue = {0};
        final int[] intValue = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.sliderFloat("SliderFloat", floatValue, 0.0f, 1000.0f);
                    IkGui.dragInt("DragInt", intValue, 1);
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        floatValue[0] = 111.0f;
        intValue[0] = 42;

        ctx.mouseMoveToEdge("SliderFloat", false);
        ctx.mouseDown(MouseButton.LEFT);
        assertEquals(111.0f, g.activeIDValueOnActivation.floatValue());
        ctx.mouseMoveToEdge("SliderFloat", true);
        assertEquals(111.0f, g.activeIDValueOnActivation.floatValue());
        assertNotEquals(111.0f, floatValue[0]);
        ctx.mouseUp(MouseButton.LEFT);

        ctx.mouseMoveToEdge("DragInt", false);
        ctx.mouseDown(MouseButton.LEFT);
        assertEquals(42, g.activeIDValueOnActivation.intValue());
        ctx.mouseMoveToEdge("DragInt", true);
        assertEquals(42, g.activeIDValueOnActivation.intValue());
        assertNotEquals(42, intValue[0]);
        ctx.mouseUp(MouseButton.LEFT);
    }

    /** widgets_dragslider_clamp: sliders and drags clamping values. */
    @Test
    void testDragSliderClamp() {
        final float[] dragValue = {0};
        final float[] dragMin = {0};
        final float[] dragMax = {1};
        final float[] sliderValue = {0};
        final float[] sliderMin = {0};
        final float[] sliderMax = {0};
        final float[] scalarValue = {0};
        final Float[] scalarMin = {null};
        final Float[] scalarMax = {null};
        final int[] sliderFlags = {SliderFlags.NONE};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    final String format = "%.3f";
                    IkGui.sliderFloat(
                            "Slider",
                            sliderValue,
                            sliderMin[0],
                            sliderMax[0],
                            format,
                            sliderFlags[0]);
                    IkGui.dragFloat(
                            "Drag",
                            dragValue,
                            1.0f,
                            dragMin[0],
                            dragMax[0],
                            format,
                            sliderFlags[0]);
                    IkGui.dragScalar(
                            "Scalar",
                            SliderDataType.FLOAT,
                            scalarValue,
                            1.0f,
                            scalarMin[0],
                            scalarMax[0],
                            null,
                            sliderFlags[0]);
                    IkGui.end();
                });
        final var io = ctx.context.io;
        ctx.setRef("Test Window");
        final int[] flagsList = {
            SliderFlags.NONE,
            SliderFlags.CLAMP_ON_INPUT,
            SliderFlags.CLAMP_ZERO_RANGE,
            SliderFlags.ALWAYS_CLAMP
        };
        for (int flags : flagsList) {
            final boolean clampOnInput = (flags & SliderFlags.CLAMP_ON_INPUT) != 0;
            sliderFlags[0] = flags;

            final float[][] sliderMinMax = {{0.0f, 1.0f}, {0.0f, 0.0f}};
            for (float[] minMax : sliderMinMax) {
                final String description = "slider " + minMax[1] + ", flags " + flags;
                sliderValue[0] = 0.0f;
                sliderMin[0] = minMax[0];
                sliderMax[0] = minMax[1];

                ctx.itemInputValue("Slider", 2);
                assertEquals(clampOnInput ? sliderMax[0] : 2.0f, sliderValue[0], description);

                // The higher bound, a click updates the clamping
                ctx.mouseMoveToEdge("Slider", true);
                ctx.mouseDown(MouseButton.LEFT);
                assertEquals(sliderMax[0], sliderValue[0], description);
                ctx.mouseMoveToPos(io.mousePosition.x + 100, io.mousePosition.y);
                ctx.mouseUp(MouseButton.LEFT);
                assertEquals(sliderMax[0], sliderValue[0], description);

                ctx.itemInputValue("Slider", -2);
                assertEquals(clampOnInput ? sliderMin[0] : -2.0f, sliderValue[0], description);

                // The lower bound
                ctx.mouseMoveToEdge("Slider", false);
                ctx.mouseDown(MouseButton.LEFT);
                assertEquals(sliderMin[0], sliderValue[0], description);
                ctx.mouseMoveToPos(io.mousePosition.x - 100, io.mousePosition.y);
                ctx.mouseUp(MouseButton.LEFT);
                assertEquals(sliderMin[0], sliderValue[0], description);
            }

            final float[][] dragMinMax = {
                {0.0f, 1.0f}, {0.0f, 0.0f}, {-Float.MAX_VALUE, Float.MAX_VALUE}
            };
            for (float[] minMax : dragMinMax) {
                final String description = "drag " + minMax[1] + ", flags " + flags;
                dragValue[0] = 0.0f;
                dragMin[0] = minMax[0];
                dragMax[0] = minMax[1];

                // [0, 0] is the same as an unbounded range
                final boolean unbound =
                        (dragMin[0] == 0.0f
                                        && dragMax[0] == 0.0f
                                        && (flags & SliderFlags.CLAMP_ZERO_RANGE) == 0)
                                || (dragMin[0] == -Float.MAX_VALUE
                                        && dragMax[0] == Float.MAX_VALUE);

                ctx.itemInputValue("Drag", -3);
                assertEquals(
                        clampOnInput && !unbound ? dragMin[0] : -3.0f, dragValue[0], description);

                ctx.itemInputValue("Drag", 2);
                assertEquals(
                        clampOnInput && !unbound ? dragMax[0] : 2.0f, dragValue[0], description);

                // The higher bound, a click doesn't update the clamping
                ctx.mouseMove("Drag");
                float valueBeforeClick = dragValue[0];
                ctx.mouseDown(MouseButton.LEFT);
                assertEquals(valueBeforeClick, dragValue[0], description);
                ctx.mouseMoveToPos(io.mousePosition.x + 100, io.mousePosition.y);
                ctx.mouseUp(MouseButton.LEFT);
                if (unbound) {
                    assertTrue(dragValue[0] > valueBeforeClick, description);
                } else {
                    assertEquals(valueBeforeClick, dragValue[0], description);
                }

                // From the higher to the lower bound
                valueBeforeClick = dragValue[0];
                ctx.mouseMove("Drag");
                ctx.mouseDragWithDelta(-100, 0);
                if (unbound) {
                    assertTrue(dragValue[0] < valueBeforeClick, description);
                } else {
                    assertEquals(dragMin[0], dragValue[0], description);
                }

                // From the lower to the higher bound
                valueBeforeClick = dragValue[0];
                ctx.mouseMove("Drag");
                ctx.mouseDragWithDelta(100, 0);
                if (unbound) {
                    assertTrue(dragValue[0] > valueBeforeClick, description);
                } else {
                    assertEquals(dragMax[0], dragValue[0], description);
                }
            }

            final float[][] scalarMinMax = {{-Float.MAX_VALUE, 1.0f}, {0.0f, Float.MAX_VALUE}};
            for (float[] minMax : scalarMinMax) {
                final String description = "scalar " + minMax[0] + ", flags " + flags;
                scalarValue[0] = 0.0f;
                scalarMin[0] =
                        minMax[0] == -Float.MAX_VALUE || minMax[0] == Float.MAX_VALUE
                                ? null
                                : minMax[0];
                scalarMax[0] =
                        minMax[1] == -Float.MAX_VALUE || minMax[1] == Float.MAX_VALUE
                                ? null
                                : minMax[1];
                final boolean unboundMin = scalarMin[0] == null;
                final boolean unboundMax = scalarMax[0] == null;

                ctx.itemInputValue("Scalar", -3);
                assertEquals(
                        clampOnInput && !unboundMin ? minMax[0] : -3.0f,
                        scalarValue[0],
                        description);

                ctx.itemInputValue("Scalar", 2);
                assertEquals(
                        clampOnInput && !unboundMax ? minMax[1] : 2.0f,
                        scalarValue[0],
                        description);

                // The higher bound
                ctx.mouseMove("Scalar");
                float valueBeforeClick = scalarValue[0];
                ctx.mouseDown(MouseButton.LEFT);
                assertEquals(valueBeforeClick, scalarValue[0], description);
                ctx.mouseMoveToPos(io.mousePosition.x + 100, io.mousePosition.y);
                ctx.mouseUp(MouseButton.LEFT);
                if (unboundMax) {
                    assertTrue(scalarValue[0] > valueBeforeClick, description);
                } else {
                    assertEquals(valueBeforeClick, scalarValue[0], description);
                }

                // From the higher to the lower bound
                scalarValue[0] = 50.0f;
                valueBeforeClick = 50.0f;
                ctx.mouseMove("Scalar");
                ctx.mouseDragWithDelta(-100, 0);
                if (unboundMin) {
                    assertTrue(scalarValue[0] < valueBeforeClick, description);
                } else {
                    assertEquals(minMax[0], scalarValue[0], description);
                }

                // From the lower to the higher bound
                scalarValue[0] = 0.0f;
                valueBeforeClick = 0.0f;
                ctx.mouseMove("Scalar");
                ctx.mouseDragWithDelta(100, 0);
                if (unboundMax) {
                    assertTrue(scalarValue[0] > valueBeforeClick, description);
                } else {
                    assertEquals(minMax[1], scalarValue[0], description);
                }
            }
        }
    }

    /** widgets_hover: isItemHovered() on child windows and multi-line text inputs. */
    @Test
    void testHover() {
        final Vector2f pos = new Vector2f();
        final int[] buttonID = {0};
        final boolean[] hovered = new boolean[2];
        final IkString str = new IkString(256);
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.beginChild("Child", 100, 100, ChildFlags.BORDERS, 0);
                    IkGui.getCursorScreenPos(pos);
                    IkGui.dummy(10, 10);
                    IkGui.button("button");
                    buttonID[0] = IkGui.getItemID();
                    IkGui.endChild();
                    hovered[0] = IkGui.isItemHovered();
                    IkGui.inputTextMultiline("##Field", str, -Float.MIN_VALUE, 0);
                    hovered[1] = IkGui.isItemHovered();
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        // Over the child's empty space
        ctx.mouseMoveToPos(pos.x, pos.y);
        assertTrue(hovered[0]);
        assertFalse(hovered[1]);

        // Over the child's item
        ctx.mouseMove(buttonID[0]);
        assertTrue(hovered[0]);
        assertFalse(hovered[1]);

        // Over the multi-line text input, a child in a group
        ctx.mouseMove("##Field");
        assertFalse(hovered[0]);
        assertTrue(hovered[1]);
    }

    /** widgets_item_flags_stack: item flags are inherited by child windows. */
    @Test
    void testItemFlagsStack() {
        ctx.setGui(
                () -> {
                    final var g = ctx.context;
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    ctx.checkEquals(ItemFlags.DEFAULT, g.currentItemFlags, "window");

                    IkGui.beginChild("child1", 100, 100);
                    ctx.checkEquals(ItemFlags.DEFAULT, g.currentItemFlags, "child1");
                    IkGui.button("enable button in child1");
                    IkGui.endChild();

                    IkGui.pushItemFlag(ItemFlags.DISABLED, true);
                    IkGui.button("disabled button in parent");

                    ctx.check((g.currentItemFlags & ItemFlags.DISABLED) != 0, "parent disabled");
                    // Appending
                    IkGui.beginChild("child1");
                    ctx.check((g.currentItemFlags & ItemFlags.DISABLED) != 0, "child1 disabled");
                    IkGui.button("disabled button in child1");
                    IkGui.endChild();

                    // New
                    IkGui.beginChild("child2", 100, 100);
                    ctx.check((g.currentItemFlags & ItemFlags.DISABLED) != 0, "child2 disabled");
                    IkGui.button("disabled button in child2");
                    IkGui.endChild();

                    IkGui.popItemFlag();
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** widgets_status_common: the item status functions for common widgets. */
    @Test
    void testStatusCommon() {
        final int[] step = {0};
        final IkBoolean bool1 = new IkBoolean(false);
        final float[] float1 = {0};
        final IkInt int1 = new IkInt(0);
        final float[] color = new float[4];
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.sliderInt("Step", step, 0, 8);
                    boolean ret = false;
                    switch (step[0]) {
                        case 0 -> ret = IkGui.button("Button");
                        case 1 -> ret = IkGui.checkbox("Checkbox", bool1);
                        case 2 -> ret = IkGui.sliderFloat("Slider", float1, 0.0f, 1.0f);
                        case 3 -> ret = IkGui.inputFloat("InputFloat", float1, 0.0f, 1.0f);
                        case 4 ->
                                ret =
                                        IkGui.inputFloat(
                                                "InputFloat(enter)",
                                                float1,
                                                0.0f,
                                                1.0f,
                                                null,
                                                InputTextFlags.ENTER_RETURNS_TRUE);
                        case 5 -> ret = IkGui.selectable("Selectable", bool1);
                        case 6 ->
                                ret =
                                        IkGui.combo(
                                                "Combo", int1, new String[] {"Zero", "One", "Two"});
                        case 7 -> ret = IkGui.colorEdit4("ColorEdit", color);
                        default -> IkGui.text("Text Without An Identifier");
                    }
                    status.queryInc(ret);
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        final String[] itemNames = {
            "Button",
            "Checkbox",
            "Slider",
            "InputFloat",
            "InputFloat(enter)",
            "Selectable",
            "Combo",
            "ColorEdit/##X"
        };
        for (int s = 0; s < 8; ++s) {
            final String description = itemNames[s];
            final boolean isInput = s == 3 || s == 4;
            step[0] = s;
            float1[0] = 0.0f;
            ctx.yieldFrames(2);
            status.clear();

            ctx.mouseMove(itemNames[s]);
            assertEquals(0, status.retValue, description);
            assertEquals(0, status.activated, description);
            assertEquals(0, status.deactivated, description);
            assertEquals(0, status.deactivatedAfterEdit, description);
            assertEquals(0, status.edited, description);
            assertTrue(status.hovered >= 1, description);
            ctx.mouseDown(MouseButton.LEFT);
            assertEquals(1, status.activated, description);
            assertTrue(status.active >= 1, description);
            assertEquals(0, status.deactivated, description);

            status.clear();
            ctx.yieldFrame();
            assertEquals(1, status.hovered, description);

            if (s == 2 || s == 7) {
                ctx.mouseMoveToEdge(itemNames[s], false);
                ctx.mouseMoveToEdge(itemNames[s], true);
            }

            ctx.mouseUp(MouseButton.LEFT);
            if (isInput) {
                // Edit
                ctx.keyChars("123");
                ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ENTER));
            }
            assertEquals(0, status.activated, description);
            assertEquals(1, status.deactivated, description);

            if (s == 6) {
                ctx.comboClick("Combo/One");
                ctx.comboClick("Combo/Two");
                // Upstream marks the rest of this step as broken
                continue;
            }

            // Not triggered by buttons
            if (s != 0) {
                assertEquals(1, status.deactivatedAfterEdit, description);
            }
        }

        // Upstream intends to test a text item, with no identifier, but uses step 6 (the combo)
        step[0] = 6;
        ctx.yieldFrames(2);
        status.clear();
        ctx.yieldFrame();
        assertEquals(1, status.hovered);
        ctx.mouseDown(MouseButton.LEFT);
        status.clear();
        ctx.yieldFrame();
        assertEquals(1, status.hovered);
        ctx.mouseUp(MouseButton.LEFT);
    }

    /**
     * widgets_status_coloredit: isItemActivated() doesn't trigger when clicking the color button to
     * open the picker.
     */
    @Test
    void testStatusColorEdit() {
        final float[] color = new float[4];
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    final boolean ret = IkGui.colorEdit4("Field", color, ColorEditFlags.NONE);
                    status.queryInc(ret);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Field/##ColorButton");
        assertEquals(0, status.retValue);
        assertEquals(1, status.activated);
        assertEquals(1, status.deactivated);
        assertEquals(0, status.deactivatedAfterEdit);
        assertEquals(0, status.edited);
        status.clear();

        ctx.keyPress(Key.ESCAPE);
        assertEquals(0, status.retValue);
        assertEquals(0, status.activated);
        assertEquals(0, status.deactivated);
        assertEquals(0, status.deactivatedAfterEdit);
        assertEquals(0, status.edited);
    }

    /** widgets_status_inputtext: the item status functions for text inputs. */
    @Test
    void testStatusInputText() {
        final int[] step = {0};
        final IkString str1 = new IkString(256);
        final IkString str2 = new IkString(256);
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.sliderInt("Step", step, 0, 15);
                    IkGui.inputText("Sibling", str2);

                    final boolean isTempApplyOnDeactivate = (step[0] & 2) != 0;
                    final boolean isMultiline = (step[0] & 4) != 0;
                    final boolean isMixedValue = (step[0] & 8) != 0;
                    if (isMixedValue) {
                        IkGui.pushItemFlag(ItemFlags.MIXED_VALUE, true);
                    }

                    final boolean ret;
                    if (isTempApplyOnDeactivate) {
                        final IkString local = new IkString(str1.get(), 128);
                        ret =
                                isMultiline
                                        ? IkGui.inputTextMultiline("Field", local)
                                        : IkGui.inputText("Field", local);
                        if (IkGui.isItemDeactivatedAfterEdit()) {
                            str1.set(local.get());
                        }
                    } else {
                        ret =
                                isMultiline
                                        ? IkGui.inputTextMultiline("Field", str1)
                                        : IkGui.inputText("Field", str1);
                    }
                    status.queryInc(ret);

                    if (isMixedValue) {
                        IkGui.popItemFlag();
                    }
                    IkGui.end();
                });
        final var io = ctx.context.io;
        ctx.setRef("Test Window");
        for (int s = 0; s < 16; ++s) {
            step[0] = s;
            final boolean isEnterKeepActive = (s & 1) != 0;
            final boolean isTempApplyOnDeactivate = (s & 2) != 0;
            final boolean isMultiline = (s & 4) != 0;
            final boolean isMixedValue = (s & 8) != 0;
            if (isEnterKeepActive && isMultiline) {
                // Unsupported upstream so far
                continue;
            }
            final String description = "step " + s;
            io.configInputTextEnterKeepActive = isEnterKeepActive;
            str1.set("");
            ctx.yieldFrame();

            // The activated flag is set
            ctx.itemClick("Field");
            assertEquals(0, status.retValue, description);
            assertEquals(1, status.activated, description);
            assertEquals(0, status.deactivated, description);
            assertEquals(0, status.deactivatedAfterEdit, description);
            assertEquals(0, status.edited, description);
            status.clear();

            // The deactivated flag is set when canceling with escape
            ctx.keyPress(Key.ESCAPE);
            assertEquals(0, status.retValue, description);
            assertEquals(0, status.activated, description);
            assertEquals(1, status.deactivated, description);
            assertEquals(0, status.deactivatedAfterEdit, description);
            assertEquals(0, status.edited, description);
            status.clear();

            // Validating with enter after editing
            ctx.itemClick("Field");
            assertEquals(0, status.retValue, description);
            assertTrue(status.activated != 0, description);
            assertEquals(0, status.deactivated, description);
            assertEquals(0, status.deactivatedAfterEdit, description);
            assertEquals(0, status.edited, description);
            status.clear();
            ctx.keyCharsAppend("Hello");
            assertTrue(status.retValue != 0, description);
            assertEquals(0, status.activated, description);
            assertEquals(0, status.deactivated, description);
            assertEquals(0, status.deactivatedAfterEdit, description);
            assertTrue(status.edited >= 1, description);
            if (!isTempApplyOnDeactivate) {
                assertEquals("Hello", str1.get(), description);
            }
            status.clear();
            ctx.keyPress(isMultiline ? chord(KeyModFlags.CTRL, Key.ENTER) : KeyChord.of(Key.ENTER));
            if (isMixedValue) {
                assertEquals(1, status.retValue, description);
                assertTrue(status.deactivated != 0, description);
                assertTrue(status.deactivatedAfterEdit != 0, description);
                assertEquals(1, status.edited, description);
            } else if (!isTempApplyOnDeactivate) {
                assertEquals(0, status.retValue, description);
                assertTrue(status.deactivated != 0, description);
                assertTrue(status.deactivatedAfterEdit != 0, description);
                assertEquals(0, status.edited, description);
            } else {
                assertTrue(status.retValue >= 1, description);
                assertTrue(status.deactivated != 0, description);
                assertTrue(status.deactivatedAfterEdit != 0, description);
                assertTrue(status.edited >= 1, description);
            }
            assertEquals("Hello", str1.get(), description);
            assertEquals(io.configInputTextEnterKeepActive, status.activated != 0, description);
            if (io.configInputTextEnterKeepActive) {
                ctx.keyPress(Key.ESCAPE);
            }
            status.clear();

            // Validating with tab after editing
            ctx.itemClick("Field");
            ctx.keyCharsAppend(" World");
            assertTrue(status.retValue != 0, description);
            assertTrue(status.activated != 0, description);
            assertEquals(0, status.deactivated, description);
            assertEquals(0, status.deactivatedAfterEdit, description);
            assertTrue(status.edited >= 1, description);
            status.clear();
            ctx.keyPress(Key.TAB);
            if (!isTempApplyOnDeactivate) {
                assertEquals(0, status.retValue, description);
                assertEquals(0, status.edited, description);
            } else {
                assertTrue(status.retValue >= 1, description);
                assertTrue(status.edited >= 1, description);
            }
            assertEquals(0, status.activated, description);
            assertTrue(status.deactivated != 0, description);
            assertTrue(status.deactivatedAfterEdit != 0, description);
            assertEquals("Hello World", str1.get(), description);
            status.clear();

            // Validating by clicking on the earlier sibling after editing
            ctx.itemClick("Field");
            ctx.keyCharsReplace("123");
            assertTrue(status.retValue != 0, description);
            assertTrue(status.activated != 0, description);
            assertEquals(0, status.deactivated, description);
            assertEquals(0, status.deactivatedAfterEdit, description);
            assertTrue(status.edited >= 1, description);
            status.clear();
            ctx.itemClick("Sibling");
            if (!isTempApplyOnDeactivate) {
                assertEquals(0, status.retValue, description);
                assertEquals(0, status.edited, description);
            } else {
                assertTrue(status.retValue >= 1, description);
                assertTrue(status.edited >= 1, description);
            }
            assertEquals(0, status.activated, description);
            assertTrue(status.deactivated != 0, description);
            assertTrue(status.deactivatedAfterEdit != 0, description);
            assertEquals("123", str1.get(), description);
            status.clear();
        }
    }

    /** State for widgets_status_liveedit_off. */
    private static class LiveEditVars {
        final int[] step = {0};
        final IkString str1 = new IkString(256);
        final IkString str2 = new IkString("");
        final float[] floats = new float[3];
        final IkBoolean useLiveEdit = new IkBoolean(false);
        final IkBoolean useMixedValue = new IkBoolean(false);
        final IkInt inputTextFlags = new IkInt(0);
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();

        LiveEditVars() {
            str2.inputData.isResizable = true;
        }

        void reset() {
            step[0] = 0;
            str1.set("");
            str2.set("");
            java.util.Arrays.fill(floats, 0);
            useLiveEdit.set(false);
            useMixedValue.set(false);
            inputTextFlags.set(0);
            status.clear();
        }

        String getStr() {
            return step[0] == 1 ? str2.get() : str1.get();
        }
    }

    /**
     * widgets_status_liveedit_off: text and scalar inputs with the live edit item flags off, and
     * with mixed values.
     */
    @Test
    void testStatusLiveEditOff() {
        final LiveEditVars vars = new LiveEditVars();
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.sliderInt("Step", vars.step, 0, 6);
                    IkGui.checkboxFlags(
                            "InputTextFlags.ENTER_RETURNS_TRUE",
                            vars.inputTextFlags,
                            InputTextFlags.ENTER_RETURNS_TRUE);
                    IkGui.checkboxFlags(
                            "InputTextFlags.ESCAPE_CLEARS_ALL",
                            vars.inputTextFlags,
                            InputTextFlags.ESCAPE_CLEARS_ALL);
                    IkGui.checkbox("LiveEdit", vars.useLiveEdit);
                    IkGui.checkbox("MixedValue", vars.useMixedValue);

                    final int step = vars.step[0];
                    final boolean isNumeric = step >= 3;
                    IkGui.pushItemFlag(
                            isNumeric
                                    ? ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR
                                    : ItemFlags.LIVE_EDIT_ON_INPUT_TEXT,
                            vars.useLiveEdit.get());
                    IkGui.pushItemFlag(ItemFlags.MIXED_VALUE, vars.useMixedValue.get());

                    final int flags = vars.inputTextFlags.get();
                    final boolean ret =
                            switch (step) {
                                case 0 -> IkGui.inputText("Buf", vars.str1, flags);
                                case 1 -> IkGui.inputText("Buf", vars.str2, flags);
                                case 2 -> IkGui.inputTextMultiline("Buf", vars.str1, 0, 0, flags);
                                case 3 ->
                                        IkGui.inputFloat(
                                                "Buf", vars.floats, 0.0f, 0.0f, "%.1f", flags);
                                case 4 ->
                                        IkGui.inputFloat(
                                                "Buf", vars.floats, 1.0f, 100.0f, "%.1f", flags);
                                case 5 ->
                                        IkGui.sliderFloat(
                                                "Buf", vars.floats, -1000.0f, 1000.0f, "%.1f");
                                default ->
                                        IkGui.sliderFloat3(
                                                "Buf", vars.floats, -1000.0f, 1000.0f, "%.1f");
                            };
                    vars.status.queryInc(ret);

                    IkGui.popItemFlag();
                    IkGui.popItemFlag();
                    IkGui.end();
                });
        final var g = ctx.context;
        final var status = vars.status;
        ctx.setRef("Test Window");

        ctx.itemClick("Buf");
        final InputTextState state = IkGuiImplInputText.getInputTextState(ctx.getID("Buf"));
        assertNotNull(state);

        for (int mixedValueStep = 0; mixedValueStep < 2; ++mixedValueStep) {
            for (int step = 0; step < 7; ++step) {
                final boolean isNumeric = step >= 3;
                final boolean isDragSlider = step == 5 || step == 6;
                final boolean isMultiComponents = step == 6;
                final String d = "mixed " + mixedValueStep + ", step " + step;

                vars.reset();
                if (isNumeric) {
                    // "" means 0
                    vars.inputTextFlags.set(
                            vars.inputTextFlags.get() | InputTextFlags.PARSE_EMPTY_REF_VAL);
                }
                vars.step[0] = step;
                vars.useMixedValue.set(mixedValueStep == 1);
                ctx.yieldFrame();
                final boolean mixed = vars.useMixedValue.get();

                // Append text, validate
                status.clear();
                final int itemID = isMultiComponents ? ctx.getID("Buf/$$0") : ctx.getID("Buf");
                ctx.itemInput(itemID);
                ctx.keyChars(isNumeric ? "123" : "Hello");
                ctx.checkEquals(0, status.retValue, d + ", append ret");
                ctx.checkEquals(0, status.edited, d + ", append edited");
                ctx.checkEquals(0, status.deactivated, d + ", append deactivated");
                ctx.checkEquals(0, status.deactivatedAfterEdit, d + ", append deactivated edit");
                ctx.checkEquals("", vars.getStr(), d + ", append str");
                status.clear();
                ctx.keyPress(chord(KeyModFlags.CTRL, Key.ENTER));
                ctx.checkEquals(1, status.retValue, d + ", validate ret");
                ctx.checkEquals(1, status.edited, d + ", validate edited");
                ctx.checkEquals(1, status.deactivated, d + ", validate deactivated");
                ctx.checkEquals(1, status.deactivatedAfterEdit, d + ", validate deactivated edit");
                if (isNumeric) {
                    ctx.checkEquals(123.0f, vars.floats[0], d + ", validate value");
                } else {
                    ctx.checkEquals("Hello", vars.getStr(), d + ", validate str");
                }

                // Modify the text, tab out
                status.clear();
                ctx.itemInput(itemID);
                assertEquals(itemID, g.activeID, d);
                ctx.keyPress(Key.END);
                ctx.keyPress(Key.BACKSPACE, 5);
                ctx.keyChars("777");
                ctx.checkEquals("777", state.getText(), d + ", modify text");
                ctx.checkEquals(0, status.retValue, d + ", modify ret");
                ctx.checkEquals(0, status.edited, d + ", modify edited");
                ctx.checkEquals(0, status.deactivated, d + ", modify deactivated");
                ctx.checkEquals(0, status.deactivatedAfterEdit, d + ", modify deactivated edit");
                if (isNumeric) {
                    ctx.checkEquals(123.0f, vars.floats[0], d + ", modify value");
                } else {
                    ctx.checkEquals("Hello", vars.getStr(), d + ", modify str");
                }
                status.clear();
                ctx.keyPress(Key.TAB);
                ctx.check(g.activeID != itemID, d + ", tab active");
                ctx.checkEquals(1, status.retValue, d + ", tab ret");
                ctx.checkEquals(1, status.edited, d + ", tab edited");
                ctx.checkEquals(1, status.deactivated, d + ", tab deactivated");
                ctx.checkEquals(1, status.deactivatedAfterEdit, d + ", tab deactivated edit");
                if (isNumeric) {
                    ctx.checkEquals(777.0f, vars.floats[0], d + ", tab value");
                } else {
                    ctx.checkEquals("777", vars.getStr(), d + ", tab str");
                }

                // Revert to the previous text
                ctx.keyPress(chord(KeyModFlags.SHIFT, Key.TAB));
                ctx.checkEquals(itemID, g.activeID, d + ", shift tab active");
                ctx.keyPress(chord(KeyModFlags.CTRL, Key.A));
                ctx.keyChars(isNumeric ? "123" : "Hello");
                ctx.keyPress(chord(KeyModFlags.CTRL, Key.ENTER));

                // Activate, validate with no changes
                status.clear();
                ctx.itemInput(itemID);
                ctx.keyPress(Key.END);
                ctx.keyPress(chord(KeyModFlags.CTRL, Key.ENTER));
                ctx.checkEquals(1, status.activated, d + ", no change activated");
                ctx.checkEquals(1, status.deactivated, d + ", no change deactivated");
                // With mixed values, validating always applies an edit
                final int expectedNoChange = mixed ? 1 : 0;
                ctx.checkEquals(expectedNoChange, status.retValue, d + ", no change ret");
                ctx.checkEquals(expectedNoChange, status.edited, d + ", no change edited");
                ctx.checkEquals(
                        expectedNoChange,
                        status.deactivatedAfterEdit,
                        d + ", no change deactivated edit");
                if (isNumeric) {
                    status.clear();
                    ctx.itemInput(itemID);
                    ctx.checkEquals("123.0", state.getText(), d + ", numeric text");
                    ctx.keyPress(Key.END);
                    // Removing the trailing zero gives the same value after parsing
                    ctx.keyPress(Key.BACKSPACE);
                    ctx.keyPress(chord(KeyModFlags.CTRL, Key.ENTER));
                    ctx.checkEquals(123.0f, vars.floats[0], d + ", numeric value");
                    ctx.checkEquals("123.", state.getText(), d + ", numeric trimmed text");
                    ctx.checkEquals(1, status.activated, d + ", numeric activated");
                    ctx.checkEquals(1, status.deactivated, d + ", numeric deactivated");
                    ctx.checkEquals(expectedNoChange, status.retValue, d + ", numeric ret");
                    ctx.checkEquals(expectedNoChange, status.edited, d + ", numeric edited");
                    ctx.checkEquals(
                            expectedNoChange,
                            status.deactivatedAfterEdit,
                            d + ", numeric deactivated edit");
                }

                // Activate, append, delete, validate with no changes in the final output
                status.clear();
                ctx.itemInput(itemID);
                ctx.keyPress(Key.END);
                if (isNumeric) {
                    ctx.keyChars("444");
                    ctx.checkEquals(123.0f, vars.floats[0], d + ", append delete value");
                    ctx.checkEquals("123.0444", state.getText(), d + ", append delete text");
                } else {
                    ctx.keyChars("ABC");
                    ctx.checkEquals("Hello", vars.getStr(), d + ", append delete str");
                    ctx.checkEquals("HelloABC", state.getText(), d + ", append delete text");
                }
                ctx.keyPress(Key.BACKSPACE, 3);
                ctx.keyPress(chord(KeyModFlags.CTRL, Key.ENTER));
                if (isNumeric) {
                    ctx.checkEquals(123.0f, vars.floats[0], d + ", deleted value");
                    ctx.checkEquals("123.0", state.getText(), d + ", deleted text");
                } else {
                    ctx.checkEquals("Hello", vars.getStr(), d + ", deleted str");
                    ctx.checkEquals("Hello", state.getText(), d + ", deleted text");
                }
                ctx.checkEquals(1, status.activated, d + ", deleted activated");
                ctx.checkEquals(1, status.deactivated, d + ", deleted deactivated");
                ctx.checkEquals(expectedNoChange, status.retValue, d + ", deleted ret");
                ctx.checkEquals(expectedNoChange, status.edited, d + ", deleted edited");
                ctx.checkEquals(
                        expectedNoChange,
                        status.deactivatedAfterEdit,
                        d + ", deleted deactivated edit");

                // Activate, append, revert with no changes in the final output
                status.clear();
                ctx.itemInput(itemID);
                ctx.keyPress(Key.END);
                if (isNumeric) {
                    ctx.keyChars("456");
                    ctx.checkEquals(123.0f, vars.floats[0], d + ", revert value");
                    ctx.checkEquals("123.0456", state.getText(), d + ", revert text");
                } else {
                    ctx.keyChars("World");
                    ctx.checkEquals("Hello", vars.getStr(), d + ", revert str");
                    ctx.checkEquals("HelloWorld", state.getText(), d + ", revert text");
                }
                ctx.keyPress(Key.ESCAPE);
                if (isNumeric) {
                    ctx.checkEquals(123.0f, vars.floats[0], d + ", reverted value");
                    ctx.checkEquals("123.0", state.getText(), d + ", reverted text");
                } else {
                    ctx.checkEquals("Hello", vars.getStr(), d + ", reverted str");
                    ctx.checkEquals("Hello", state.getText(), d + ", reverted text");
                }
                // With mixed values and no live edits, activating, appending and reverting
                // doesn't mark it as edited
                ctx.checkEquals(1, status.activated, d + ", reverted activated");
                ctx.checkEquals(1, status.deactivated, d + ", reverted deactivated");
                ctx.checkEquals(0, status.retValue, d + ", reverted ret");
                ctx.checkEquals(0, status.edited, d + ", reverted edited");
                ctx.checkEquals(0, status.deactivatedAfterEdit, d + ", reverted deactivated edit");

                // ESCAPE_CLEARS_ALL clearing the buffer
                if (!isDragSlider) {
                    vars.inputTextFlags.set(
                            vars.inputTextFlags.get() | InputTextFlags.ESCAPE_CLEARS_ALL);
                    ctx.yieldFrame();
                    status.clear();
                    ctx.itemInput(itemID);
                    ctx.keyChars("999");
                    ctx.keyPress(Key.ESCAPE);
                    if (isNumeric) {
                        ctx.checkEquals(123.0f, vars.floats[0], d + ", clear value");
                    } else {
                        // The live buffer is cleared right away on escape, by design
                        ctx.checkEquals("", vars.getStr(), d + ", clear str");
                    }
                    // The edit buffer is cleared
                    ctx.checkEquals("", state.getText(), d + ", clear text");
                    ctx.keyPress(Key.ESCAPE);
                    ctx.checkEquals(1, status.retValue, d + ", cleared ret");
                    ctx.checkEquals(1, status.deactivated, d + ", cleared deactivated");
                    ctx.checkEquals(
                            1, status.deactivatedAfterEdit, d + ", cleared deactivated edit");
                    ctx.checkEquals("", vars.getStr(), d + ", cleared str");

                    // Part 2
                    status.clear();
                    ctx.itemInput(itemID);
                    ctx.keyChars("999");
                    ctx.checkEquals("", vars.getStr(), d + ", clear 2 str");
                    ctx.checkEquals("999", state.getText(), d + ", clear 2 text");
                    ctx.keyPress(Key.ESCAPE);
                    ctx.checkEquals("", vars.getStr(), d + ", clear 2 escape str");
                    ctx.checkEquals("", state.getText(), d + ", clear 2 escape text");
                    ctx.keyPress(chord(KeyModFlags.CTRL, Key.ENTER));
                    ctx.checkEquals(1, status.deactivated, d + ", clear 2 deactivated");
                    ctx.checkEquals(expectedNoChange, status.retValue, d + ", clear 2 ret");
                    ctx.checkEquals(
                            expectedNoChange,
                            status.deactivatedAfterEdit,
                            d + ", clear 2 deactivated edit");
                    ctx.checkEquals("", vars.getStr(), d + ", clear 2 final str");

                    vars.inputTextFlags.set(
                            vars.inputTextFlags.get() & ~InputTextFlags.ESCAPE_CLEARS_ALL);
                }

                // Misc
                if (isDragSlider) {
                    ctx.itemInput(itemID);
                    ctx.keyCharsReplaceEnter("456");
                    ctx.checkEquals(456.0f, vars.floats[0], d + ", misc value");
                    ctx.itemDragWithDelta(itemID, -100, 0);
                    ctx.check(vars.floats[0] < 456.0f, d + ", misc dragged");
                    assertEquals(0, g.activeID, d);
                    ctx.yieldFrames(2);
                    ctx.itemInput(itemID);
                    assertEquals(itemID, g.activeID, d);
                    ctx.keyCharsAppend("222");
                    ctx.check(vars.floats[0] < 456.0f, d + ", misc appended");
                    ctx.keyPress(Key.ENTER);
                    ctx.check(vars.floats[0] < 456_222.0f, d + ", misc entered");

                    // Ctrl+Clicking on a previous widget after an uncommitted edit works
                    ctx.itemInput(itemID);
                    ctx.keyCharsReplace("333");
                    ctx.itemInput("Step");
                    assertEquals(ctx.getID("Step"), g.activeID, d);
                }

                ctx.keyPress(Key.ESCAPE);
                ctx.yieldFrame();
            }
        }
    }

    /**
     * widgets_status_deactivate_interrupted: the deactivation status functions while interrupted,
     * by clicks, clearing the active ID, focus changes, modals and tabbing.
     */
    @Test
    void testStatusDeactivateInterrupted() {
        final int[] step = {0};
        final IkString str1 = new IkString(256);
        final float[] color = new float[4];
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        final java.util.function.BiConsumer<Integer, String> interrupters =
                (offset, modal) -> {
                    IkGuiImplButtons.buttonEx(
                            "Right click (" + (offset == 0 ? 1 : 2) + ")",
                            0,
                            0,
                            ButtonFlags.MOUSE_BUTTON_RIGHT);
                    final Key[] keys =
                            offset == 0
                                    ? new Key[] {Key.F1, Key.F2, Key.F3}
                                    : new Key[] {Key.F5, Key.F6, Key.F7};
                    if (IkGui.shortcut(KeyChord.of(keys[0]), InputFlags.ROUTE_GLOBAL)) {
                        IkGuiInternal.clearActiveID();
                    }
                    if (IkGui.shortcut(KeyChord.of(keys[1]), InputFlags.ROUTE_GLOBAL)) {
                        IkGui.setWindowFocus(null);
                    }
                    if (IkGui.shortcut(KeyChord.of(keys[2]), InputFlags.ROUTE_GLOBAL)) {
                        IkGui.openPopup(modal);
                    }
                    if (IkGui.beginPopupModal(modal)) {
                        IkGui.text("Interrupted!");
                        if (IkGui.shortcut(KeyChord.of(Key.ESCAPE))) {
                            IkGui.closeCurrentPopup();
                        }
                        IkGui.endPopup();
                    }
                };
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.sliderInt("Step", step, 0, 2);
                    interrupters.accept(0, "Modal 1");

                    boolean ret = false;
                    switch (step[0]) {
                        case 0 -> ret = IkGui.inputText("Field", str1);
                        case 1 -> IkGui.inputTextMultiline("Field", str1);
                        case 2 -> ret = IkGui.sliderFloat3("Slider3", color, 0.0f, 1.0f);
                        case 3 -> ret = IkGui.inputFloat3("InputFloat3", color, (String) null);
                        default ->
                                ret =
                                        IkGui.inputFloat3(
                                                "InputFloat3",
                                                color,
                                                null,
                                                InputTextFlags.ENTER_RETURNS_TRUE);
                    }
                    status.queryInc(ret);
                    if (IkGui.isItemActivated()) {
                        str1.set("");
                    }

                    interrupters.accept(1, "Modal 2");
                    if (IkGui.shortcut(KeyChord.of(Key.F9), InputFlags.ROUTE_GLOBAL)) {
                        str1.set("");
                        java.util.Arrays.fill(color, 0);
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        for (int s = 0; s < 5; ++s) {
            step[0] = s;
            final int substepsCount = s == 2 ? 10 : 8;
            for (int substep = 0; substep < substepsCount; ++substep) {
                final String description = "step " + s + "," + substep;
                str1.set("");
                java.util.Arrays.fill(color, 0);
                ctx.yieldFrames(3);

                if (s == 2) {
                    ctx.itemInput("Slider3/$$1");
                } else if (s == 3 || s == 4) {
                    ctx.itemInput("InputFloat3/$$1");
                } else {
                    ctx.itemInput("Field");
                }
                ctx.keyChars("0.5");
                status.clear();
                final boolean odd = (substep & 1) != 0;
                switch (substep / 2) {
                    case 0 ->
                            ctx.itemClick(
                                    odd ? "Right click (2)" : "Right click (1)", MouseButton.RIGHT);
                        // clearActiveID() before or after the active ID
                    case 1 -> ctx.keyPress(odd ? Key.F5 : Key.F1);
                        // setWindowFocus(null) before or after the active ID
                    case 2 -> ctx.keyPress(odd ? Key.F6 : Key.F2);
                        // Opening a modal before or after the active ID
                    case 3 -> ctx.keyPress(odd ? Key.F7 : Key.F3);
                        // Going to the next or previous field
                    default ->
                            ctx.keyPress(
                                    odd ? chord(KeyModFlags.SHIFT, Key.TAB) : KeyChord.of(Key.TAB));
                }
                assertEquals(1, status.deactivated, description);
                assertEquals(1, status.deactivatedAfterEdit, description);
                if (substep / 2 == 4) {
                    // Tabbed
                    assertNotEquals(0, g.activeID, description);
                } else {
                    // Interrupted
                    assertEquals(0, g.activeID, description);
                }
                if (s >= 2) {
                    assertEquals(0.5f, color[1], description);
                } else {
                    assertEquals("0.5", str1.get(), description);
                }
                if (substep == 6 || substep == 7) {
                    // Close the modal
                    ctx.keyPress(Key.ESCAPE);
                }
                ctx.yieldFrames(10);
            }
        }
    }

    /** widgets_status_multicomponent: the status functions for multi-component inputs. */
    @Test
    void testStatusMultiComponent() {
        final IkInt inputTextFlags = new IkInt(0);
        final IkBoolean useLiveEdit = new IkBoolean(false);
        final float[] values = new float[4];
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.checkboxFlags(
                            "InputTextFlags.ENTER_RETURNS_TRUE",
                            inputTextFlags,
                            InputTextFlags.ENTER_RETURNS_TRUE);
                    IkGui.pushItemFlag(ItemFlags.LIVE_EDIT_ON_INPUT, useLiveEdit.get());
                    final boolean ret =
                            IkGui.inputFloat4("Field", values, null, inputTextFlags.get());
                    IkGui.popItemFlag();
                    status.queryInc(ret);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final int field0 = ctx.getID("Field/$$0");
        final int field1 = ctx.getID("Field/$$1");

        for (int stepLiveEdit = 0; stepLiveEdit < 2; ++stepLiveEdit) {
            for (int step = 0; step < 2; ++step) {
                status.clear();
                java.util.Arrays.fill(values, 0);
                inputTextFlags.set(
                        step == 0 ? InputTextFlags.NONE : InputTextFlags.ENTER_RETURNS_TRUE);
                useLiveEdit.set(stepLiveEdit == 0);
                final boolean enterReturnsTrue = step == 1;
                final boolean liveEdit = useLiveEdit.get();
                final String d =
                        "live edit " + liveEdit + ", enter returns true " + enterReturnsTrue;
                ctx.yieldFrame();

                // The activation and deactivation flags
                ctx.itemClick(field0);
                assertEquals(0, status.retValue, d);
                assertEquals(1, status.activated, d);
                assertEquals(0, status.deactivated, d);
                assertEquals(0, status.deactivatedAfterEdit, d);
                status.clear();
                ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ENTER));
                assertEquals(enterReturnsTrue ? 1 : 0, status.retValue, d);
                assertEquals(0, status.activated, d);
                assertEquals(1, status.deactivated, d);
                assertEquals(0, status.deactivatedAfterEdit, d);
                status.clear();

                // Validating with enter after editing
                ctx.itemClick(field0);
                status.clear();
                ctx.keyCharsAppend("123");
                if (enterReturnsTrue || !liveEdit) {
                    assertEquals(0, status.retValue, d);
                } else {
                    assertTrue(status.retValue >= 1, d);
                }
                assertEquals(0, status.activated, d);
                assertEquals(0, status.deactivated, d);
                if (liveEdit) {
                    assertTrue(status.edited >= 1, d);
                } else {
                    assertEquals(0, status.edited, d);
                }
                status.clear();
                ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ENTER));
                assertEquals(enterReturnsTrue || !liveEdit ? 1 : 0, status.retValue, d);
                assertEquals(0, status.activated, d);
                assertEquals(1, status.deactivated, d);
                status.clear();

                // Validating with tab after editing
                ctx.itemClick(field0);
                ctx.keyCharsAppend("456");
                status.clear();
                ctx.keyPress(Key.TAB);
                assertEquals(liveEdit || enterReturnsTrue ? 0 : 1, status.retValue, d);
                assertEquals(1, status.activated, d);
                assertEquals(1, status.deactivated, d);
                assertEquals(1, status.deactivatedAfterEdit, d);
                status.clear();

                // The edited flag on all the components
                // Upstream notes the first click shouldn't be necessary
                ctx.itemClick(field1);
                ctx.itemClick(field0);
                ctx.keyCharsAppend("111");
                if (liveEdit) {
                    assertTrue(status.edited >= 1, d);
                } else {
                    assertEquals(0, status.edited, d);
                }
                ctx.keyPress(Key.TAB);
                assertTrue(status.edited >= 1, d);
                status.clear();
                ctx.keyCharsAppend("222");
                if (liveEdit) {
                    assertTrue(status.edited >= 1, d);
                } else {
                    assertEquals(0, status.edited, d);
                }
                ctx.keyPress(Key.TAB);
                assertTrue(status.edited >= 1, d);
                status.clear();
                ctx.keyCharsAppend("333");
                if (liveEdit) {
                    assertTrue(status.edited >= 1, d);
                } else {
                    assertEquals(0, status.edited, d);
                }

                ctx.keyPress(Key.ESCAPE);
            }
        }
    }

    /**
     * widgets_status_inputfloat_format_mismatch: isItemEdited() when the input and output formats
     * don't match.
     */
    @Test
    void testStatusInputFloatFormatMismatch() {
        final float[] value = {0};
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.pushItemFlag(ItemFlags.LIVE_EDIT_ON_INPUT, true);
                    final boolean ret = IkGui.inputFloat("Field", value);
                    IkGui.popItemFlag();
                    status.queryInc(ret);
                    IkGui.end();
                });
        // "1" is formatted as "1.000", so isItemEdited() shouldn't be reported multiple times
        ctx.setRef("Test Window");
        ctx.itemClick("Field");
        ctx.keyCharsAppend("1");
        assertEquals(1, status.retValue);
        assertEquals(1, status.edited);
        assertEquals(1, status.activated);
        assertEquals(0, status.deactivated);
        assertEquals(0, status.deactivatedAfterEdit);
        ctx.yieldFrame();
        ctx.yieldFrame();
        assertEquals(1, status.edited);
    }

    private static IkGuiTestContext.ItemStatus[] statuses(int count) {
        final IkGuiTestContext.ItemStatus[] result = new IkGuiTestContext.ItemStatus[count];
        for (int i = 0; i < count; ++i) {
            result[i] = new IkGuiTestContext.ItemStatus();
        }
        return result;
    }

    /** widgets_overlap_1: items that allow overlap. */
    @Test
    void testOverlap1() {
        final IkGuiTestContext.ItemStatus[] status = statuses(2);
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    final Vector2f p = IkGui.getCursorScreenPos();
                    IkGui.setNextItemAllowOverlap();
                    final boolean button0Pressed =
                            IkGuiImplButtons.buttonEx("00000", 50, 50, ButtonFlags.ALLOW_OVERLAP);
                    status[0].queryInc(button0Pressed);

                    IkGui.setCursorScreenPos(p.x + 20, p.y + 25);
                    final boolean button1Pressed = IkGui.button("11111", 50, 25);
                    status[1].queryInc(button1Pressed);
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        final RectFloat rect = ctx.itemInfo("00000").rect;
        final float px = rect.getLeft();
        final float py = rect.getTop();

        ctx.mouseMoveToPos(px - 10, py - 10);
        status[0].clear();
        status[1].clear();

        // The mouse goes directly over the overlap
        ctx.mouseTeleportToPos(px + 25, py + 30, true);
        ctx.yieldFrames(2);
        assertEquals(0, status[0].hovered);
        assertTrue(status[1].hovered > 0);

        ctx.mouseClick(MouseButton.LEFT);
        assertEquals(0, status[0].retValue);
        assertEquals(0, status[0].active);
        assertEquals(1, status[1].retValue);
        assertTrue(status[1].active > 0);
    }

    /** widgets_overlap_2: overlap with a slider. */
    @Test
    void testOverlap2() {
        final IkGuiTestContext.ItemStatus[] status = statuses(2);
        final int[] value = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.setNextItemWidth(200.0f);
                    IkGui.setNextItemAllowOverlap();
                    boolean ret = IkGui.sliderInt("##Slider", value, 0, 10);
                    status[0].queryInc(ret);

                    final Vector2f min = new Vector2f();
                    final Vector2f max = new Vector2f();
                    IkGui.getItemRectMin(min);
                    IkGui.getItemRectMax(max);
                    IkGui.setCursorScreenPos(max.x - 40, min.y);
                    ret = IkGui.button("Button");
                    status[1].queryInc(ret);
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        ctx.mouseMoveToEdge("##Slider", false);
        status[0].clear();
        status[1].clear();
        ctx.mouseDown(MouseButton.LEFT);
        assertEquals(0, value[0]);
        ctx.mouseMoveToEdge("##Slider", true);
        assertEquals(10, value[0]);
        assertTrue(status[0].hovered > 0);
        assertEquals(0, status[1].hovered);
        ctx.mouseUp(MouseButton.LEFT);
        status[0].clear();
        status[1].clear();
        ctx.yieldFrame();
        assertEquals(0, status[0].hovered);
        assertTrue(status[1].hovered > 0);
        ctx.mouseClick(MouseButton.LEFT);
        assertEquals(0, status[0].activated);
        assertTrue(status[1].activated > 0);
    }

    /** widgets_overlap_3_timing: the hover timing of items that allow overlap. */
    @Test
    void testOverlap3Timing() {
        final IkGuiTestContext.ItemStatus[] status = statuses(6);
        ctx.setGui(
                () -> {
                    // Ensure the window is visible, since we teleport the mouse
                    final var viewport = IkGui.getMainViewport();
                    IkGui.setNextWindowPos(viewport.position.x, viewport.position.y);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);

                    // Not overlapping, but testing the timing with the allow overlap mode
                    IkGui.separatorText("#7514");
                    IkGui.setNextItemAllowOverlap();
                    status[0].queryInc(IkGui.button("Button0", 120, 50));
                    status[1].queryInc(IkGui.button("Button1", 120, 50));

                    IkGui.separatorText("#7515");
                    final Vector2f pos = new Vector2f();
                    IkGui.getCursorPos(pos);
                    IkGui.setNextItemAllowOverlap();
                    status[2].queryInc(IkGui.button("Button2", 120, 50));
                    IkGui.setCursorPos(pos.x, pos.y + 25);
                    status[3].queryInc(IkGui.button("Button3", 120, 50));

                    IkGui.separatorText("No setNextItemAllowOverlap()");
                    IkGui.getCursorPos(pos);
                    status[4].queryInc(IkGui.button("Button4", 120, 50));
                    IkGui.setCursorPos(pos.x, pos.y + 25);
                    status[5].queryInc(IkGui.button("Button5", 120, 50));
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        // Query ahead to avoid messing with the precise frame timing
        final RectFloat button0 = new RectFloat();
        button0.set(ctx.itemInfo("Button0").rect);
        final RectFloat button1 = new RectFloat();
        button1.set(ctx.itemInfo("Button1").rect);
        final RectFloat button2 = new RectFloat();
        button2.set(ctx.itemInfo("Button2").rect);
        final RectFloat button4 = new RectFloat();
        button4.set(ctx.itemInfo("Button4").rect);

        // #7514, mostly timing
        ctx.mouseMove("Button0");
        status[0].clear();
        status[1].clear();
        ctx.yieldFrames(3);
        assertEquals(3, status[0].hovered);
        assertEquals(0, status[1].hovered);

        ctx.mouseTeleportToPos(button1.getCenterX(), button1.getCenterY(), false);
        ctx.yieldFrame();
        assertEquals(3, status[0].hovered);
        assertEquals(1, status[1].hovered);
        ctx.yieldFrame();
        assertEquals(3, status[0].hovered);
        assertEquals(2, status[1].hovered);

        ctx.mouseTeleportToPos(button0.getCenterX(), button0.getCenterY(), false);
        ctx.yieldFrame();
        // On this frame nothing is hovered
        assertEquals(3, status[0].hovered);
        assertEquals(2, status[1].hovered);
        ctx.yieldFrame();
        // Hovering resumes
        assertEquals(4, status[0].hovered);
        assertEquals(2, status[1].hovered);

        // #7515, timing and duplicate hovers. The top isn't overlapping button 3.
        ctx.mouseTeleportToPos(button2.getLeft(), button2.getTop(), true);
        status[2].clear();
        status[3].clear();
        ctx.yieldFrames(3);
        assertEquals(3, status[2].hovered);
        assertEquals(0, status[3].hovered);
        // Overlapping buttons 2 and 3
        ctx.mouseTeleportToPos(button2.getCenterX(), button2.getBottom() - 1, false);
        ctx.yieldFrame();
        // Upstream notes two items report as hovered here (#7515)
        assertEquals(4, status[2].hovered);
        assertEquals(1, status[3].hovered);
        ctx.yieldFrame();
        assertEquals(4, status[2].hovered);
        assertEquals(2, status[3].hovered);

        // isItemHovered() doesn't mind the hovered ID. The top isn't overlapping button 5.
        ctx.mouseTeleportToPos(button4.getLeft(), button4.getTop(), true);
        status[4].clear();
        status[5].clear();
        ctx.yieldFrames(3);
        assertEquals(3, status[4].hovered);
        assertEquals(0, status[5].hovered);
        // Overlapping buttons 4 and 5, upstream notes both always report as hovered
        ctx.mouseTeleportToPos(button4.getCenterX(), button4.getBottom() - 1, false);
        ctx.yieldFrame();
        assertEquals(4, status[4].hovered);
        assertEquals(1, status[5].hovered);
        ctx.yieldFrame();
        assertEquals(5, status[4].hovered);
        assertEquals(2, status[5].hovered);
    }

    /** widgets_inputscalar_input. */
    @Test
    void testInputScalarInput() {
        final int[] intValue = {0};
        final float[] floatValue = {0};
        final double[] doubleValue = {0};
        final int[] flags = {InputTextFlags.NONE};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    // getID() used to interfere by setting activeIDIsAlive
                    IkGui.getID("Int");
                    IkGui.getID("Float");
                    IkGui.getID("Double");
                    IkGui.inputInt("Int", intValue, 2, 0, flags[0]);
                    IkGui.inputFloat("Float", floatValue, 1.5f, 0.0f, null, flags[0]);
                    IkGui.inputDouble("Double", doubleValue, 1.5, 0.0, null, flags[0]);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        for (int f : new int[] {InputTextFlags.NONE, InputTextFlags.ENTER_RETURNS_TRUE}) {
            flags[0] = f;
            ctx.yieldFrame();
            ctx.itemInputValue("Int", 42);
            assertEquals(42, intValue[0]);
            ctx.itemInputValue("Float", 42.1f);
            assertEquals(42.1f, floatValue[0]);
            ctx.itemInputValue("Double", "123.456789");
            assertEquals(123.456_789, doubleValue[0]);
        }
    }

    /** widgets_inputscalar_step: the step buttons. */
    @Test
    void testInputScalarStep() {
        final int[] intValue = {0};
        final float[] floatValue = {0};
        final double[] doubleValue = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.inputInt("Int", intValue, 2);
                    IkGui.inputFloat("Float", floatValue, 1.5f);
                    IkGui.inputDouble("Double", doubleValue, 1.5);
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        ctx.itemClick("Int/+");
        assertEquals(2, intValue[0]);
        ctx.itemClick("Int/-");
        ctx.itemClick("Int/-");
        assertEquals(-2, intValue[0]);

        ctx.itemClick("Float/+");
        assertEquals(1.5f, floatValue[0]);
        ctx.itemClick("Float/-");
        ctx.itemClick("Float/-");
        assertEquals(-1.5f, floatValue[0]);

        ctx.itemClick("Double/+");
        assertEquals(1.5, doubleValue[0]);
        ctx.itemClick("Double/-");
        ctx.itemClick("Double/-");
        assertEquals(-1.5, doubleValue[0]);
    }

    /**
     * widgets_inputscalar_overflow: applying steps saturates instead of overflowing. Java has no
     * unsigned types, so only the signed ones are tested.
     */
    @Test
    void testInputScalarOverflow() {
        final byte[] b = {2};
        IkGuiImplSliders.applyStep(SliderDataType.BYTE, b, 0, true, (byte) 1);
        assertEquals(3, b[0]);
        b[0] = Byte.MAX_VALUE;
        IkGuiImplSliders.applyStep(SliderDataType.BYTE, b, 0, true, (byte) 1);
        assertEquals(Byte.MAX_VALUE, b[0]);
        b[0] = Byte.MIN_VALUE;
        IkGuiImplSliders.applyStep(SliderDataType.BYTE, b, 0, false, (byte) 1);
        assertEquals(Byte.MIN_VALUE, b[0]);

        final short[] s = {2};
        IkGuiImplSliders.applyStep(SliderDataType.SHORT, s, 0, true, (short) 1);
        assertEquals(3, s[0]);
        s[0] = Short.MAX_VALUE;
        IkGuiImplSliders.applyStep(SliderDataType.SHORT, s, 0, true, (short) 1);
        assertEquals(Short.MAX_VALUE, s[0]);
        s[0] = Short.MIN_VALUE;
        IkGuiImplSliders.applyStep(SliderDataType.SHORT, s, 0, false, (short) 1);
        assertEquals(Short.MIN_VALUE, s[0]);

        final int[] i = {2};
        IkGuiImplSliders.applyStep(SliderDataType.INT, i, 0, true, 1);
        assertEquals(3, i[0]);
        i[0] = Integer.MAX_VALUE;
        IkGuiImplSliders.applyStep(SliderDataType.INT, i, 0, true, 1);
        assertEquals(Integer.MAX_VALUE, i[0]);
        i[0] = Integer.MIN_VALUE;
        IkGuiImplSliders.applyStep(SliderDataType.INT, i, 0, false, 1);
        assertEquals(Integer.MIN_VALUE, i[0]);

        final long[] l = {2};
        IkGuiImplSliders.applyStep(SliderDataType.LONG, l, 0, true, 1L);
        assertEquals(3, l[0]);
        l[0] = Long.MAX_VALUE;
        IkGuiImplSliders.applyStep(SliderDataType.LONG, l, 0, true, 1L);
        assertEquals(Long.MAX_VALUE, l[0]);
        l[0] = Long.MIN_VALUE;
        IkGuiImplSliders.applyStep(SliderDataType.LONG, l, 0, false, 1L);
        assertEquals(Long.MIN_VALUE, l[0]);
    }

    /** widgets_inputscalar_formats: formatting edge cases. */
    @Test
    void testInputScalarFormats() {
        final String[] format = {""};
        final int[] value = {0};
        final String[] text = {""};
        final int[] captureWidget = {-1};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    for (int i = 0; i < 3; ++i) {
                        IkGuiInternal.logToBuffer(-1);
                        if (i == 0) {
                            IkGui.dragInt("Drag", value, 1.0f, 0, 0, format[0]);
                        } else if (i == 1) {
                            IkGui.inputScalar(
                                    "Input", SliderDataType.INT, value, null, null, format[0]);
                        } else {
                            IkGui.sliderInt("Slider", value, -10_000, 10_000, format[0]);
                        }
                        if (captureWidget[0] == i) {
                            text[0] = ctx.context.logBuffer.toString();
                            captureWidget[0] = -1;
                        }
                        IkGui.logFinish();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final java.util.function.IntFunction<String> captureWidgetValue =
                i -> {
                    text[0] = "";
                    captureWidget[0] = i;
                    ctx.yieldFrame();
                    // Strip the widget name
                    final int curly = text[0].indexOf('}');
                    return curly >= 0 ? text[0].substring(0, curly + 1) : text[0];
                };

        final String[] widgetNames = {"Drag", "Input", "Slider"};
        for (int i = 0; i < 3; ++i) {
            final String widgetName = widgetNames[i];

            value[0] = 0;
            format[0] = "%03X";
            ctx.itemInput(widgetName);
            ctx.keyCharsReplaceEnter("7b");
            assertEquals("{ 07B }", captureWidgetValue.apply(i), widgetName);
            assertEquals(0x7B, value[0], widgetName);

            value[0] = 0;
            ctx.itemInput(widgetName);
            ctx.keyCharsReplaceEnter("FF");
            assertEquals("{ 0FF }", captureWidgetValue.apply(i), widgetName);
            assertEquals(0xFF, value[0], widgetName);

            value[0] = 0;
            ctx.itemInput(widgetName);
            ctx.keyCharsReplaceEnter("1FF");
            assertEquals("{ 1FF }", captureWidgetValue.apply(i), widgetName);
            assertEquals(0x1FF, value[0], widgetName);

            value[0] = 0;
            format[0] = "%03d";
            ctx.itemInput(widgetName);
            ctx.keyCharsReplaceEnter("1234");
            assertEquals("{ 1234 }", captureWidgetValue.apply(i), widgetName);
            assertEquals(1234, value[0], widgetName);

            value[0] = 0;
            format[0] = "%03d";
            ctx.itemInput(widgetName);
            ctx.keyCharsReplaceEnter("1235");
            assertEquals("{ 1235 }", captureWidgetValue.apply(i), widgetName);
            assertEquals(1235, value[0], widgetName);

            value[0] = 0;
            format[0] = "%.03d";
            ctx.itemInput(widgetName);
            ctx.keyCharsReplaceEnter("1236");
            assertEquals("{ 1236 }", captureWidgetValue.apply(i), widgetName);
            assertEquals(1236, value[0], widgetName);

            // No % in the format
            value[0] = 0;
            format[0] = "WOW!";
            ctx.itemInput(widgetName);
            ctx.keyCharsReplaceEnter("1237");
            assertEquals(1237, value[0], widgetName);
        }
    }

    /** widgets_tabbar_drawcalls: a tight tab bar doesn't create extra draw commands. */
    @Test
    void testTabBarDrawCalls() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTabBar("Tab Drawcalls")) {
                        for (int i = 0; i < 20; ++i) {
                            if (IkGui.beginTabItem("Tab " + i)) {
                                IkGui.endTabItem();
                            }
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                });
        final Window window = IkGuiInternal.findWindowByName("Test Window");
        ctx.windowResize("Test Window", 300, 300);
        final int drawCalls = window.drawList.getCommandCount();
        ctx.windowResize("Test Window", 1, 1);
        // It may create fewer
        assertTrue(drawCalls >= window.drawList.getCommandCount());
    }

    /** widgets_tabbar_select: selecting tabs, and right clicking to open a popup. */
    @Test
    void testTabBarSelect() {
        final int[] selected = {0};
        final int[] open = {-1};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    open[0] = -1;
                    if (IkGui.beginTabBar("TabBar", TabBarFlags.REORDERABLE)) {
                        for (int n = 0; n < 4; ++n) {
                            if (IkGui.beginTabItem("Tab " + n)) {
                                selected[0] = n;
                                IkGui.endTabItem();
                            }
                            // Clicked rather than released, a release used to reselect the tab
                            // and close the popup
                            if (IkGui.isItemHovered(HoveredFlags.ALLOW_WHEN_BLOCKED_BY_POPUP)
                                    && IkGui.isMouseClicked(MouseButton.RIGHT)) {
                                IkGui.openPopup("Popup " + n);
                            }
                            if (IkGui.beginPopup("Popup " + n)) {
                                open[0] = n;
                                IkGui.text("Popup " + n);
                                IkGui.menuItem("Close");
                                IkGui.endPopup();
                            }
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.mouseMove("TabBar/Tab 1");
        ctx.mouseClick(MouseButton.LEFT);
        // The tab is selected, the popup isn't open
        assertEquals(1, selected[0]);
        assertEquals(-1, open[0]);
        ctx.mouseMove("TabBar/Tab 0");
        ctx.mouseDown(MouseButton.RIGHT);
        // Takes an extra frame
        ctx.yieldFrame();
        assertEquals(0, selected[0]);
        assertEquals(0, open[0]);
        ctx.sleep(0.25f);
        ctx.mouseDown(MouseButton.LEFT);
        // Still selected, the popup is still open
        assertEquals(0, selected[0]);
        assertEquals(0, open[0]);
    }

    /** widgets_tabbar_order: the order of tabs, as they are closed and reopened. */
    @Test
    void testTabBarOrder() {
        final IkBoolean[] openTabs = {
            new IkBoolean(false), new IkBoolean(false), new IkBoolean(false), new IkBoolean(false)
        };
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    for (int n = 0; n < 4; ++n) {
                        IkGui.checkbox("Open Tab " + n, openTabs[n]);
                    }
                    if (IkGui.beginTabBar("TabBar", TabBarFlags.REORDERABLE)) {
                        for (int n = 0; n < 4; ++n) {
                            if (openTabs[n].get() && IkGui.beginTabItem("Tab " + n, openTabs[n])) {
                                IkGui.endTabItem();
                            }
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        final TabBar tabBar = g.tabBars.get(ctx.getID("TabBar"));
        assertNotNull(tabBar);
        assertEquals(0, tabBar.tabs.size());

        openTabs[0].set(true);
        openTabs[1].set(true);
        openTabs[2].set(true);
        // The tab layout needs to be correct for closing tabs
        ctx.yieldFrames(3);
        assertEquals(3, tabBar.tabs.size());
        assertEquals("Tab 0", IkGuiImplTabs.tabBarGetTabName(tabBar.tabs.get(0)));
        assertEquals("Tab 1", IkGuiImplTabs.tabBarGetTabName(tabBar.tabs.get(1)));
        assertEquals("Tab 2", IkGuiImplTabs.tabBarGetTabName(tabBar.tabs.get(2)));

        ctx.tabClose("TabBar/Tab 1");
        ctx.yieldFrames(2);
        assertFalse(openTabs[1].get());
        assertEquals(2, tabBar.tabs.size());
        assertEquals("Tab 0", IkGuiImplTabs.tabBarGetTabName(tabBar.tabs.get(0)));
        assertEquals("Tab 2", IkGuiImplTabs.tabBarGetTabName(tabBar.tabs.get(1)));

        openTabs[1].set(true);
        ctx.yieldFrame();
        assertEquals(3, tabBar.tabs.size());
        assertEquals("Tab 0", IkGuiImplTabs.tabBarGetTabName(tabBar.tabs.get(0)));
        assertEquals("Tab 2", IkGuiImplTabs.tabBarGetTabName(tabBar.tabs.get(1)));
        assertEquals("Tab 1", IkGuiImplTabs.tabBarGetTabName(tabBar.tabs.get(2)));
    }

    /** widgets_tabbar_size: a tab bar declares its unclipped size. */
    @Test
    void testTabBarSize() {
        final boolean[] hasCloseButton = {false};
        final float[] expectedWidth = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTabBar("TabBar")) {
                        expectedWidth[0] = 0.0f;
                        for (int i = 0; i < 3; ++i) {
                            final String label = "TabItem " + i;
                            final IkBoolean tabOpen = new IkBoolean(true);
                            if (IkGui.beginTabItem(label, hasCloseButton[0] ? tabOpen : null)) {
                                IkGui.endTabItem();
                            }
                            if (i > 0) {
                                expectedWidth[0] += ctx.context.style.variable.itemInnerSpacing.x;
                            }
                            expectedWidth[0] +=
                                    IkGuiImplTabs.tabItemCalcSize(
                                                    label, hasCloseButton[0], new Vector2f())
                                            .x;
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                });
        final Window window = IkGuiInternal.findWindowByName("Test Window");

        hasCloseButton[0] = false;
        ctx.yieldFrame();
        assertEquals(
                window.cursorStartPosition.x + expectedWidth[0],
                window.cursorIdealMaxPosition.x,
                EPSILON);

        hasCloseButton[0] = true;
        // The tab bar submits the old size, then the layout updates the sizes
        ctx.yieldFrame();
        // The tab bar submits the new size
        ctx.yieldFrame();
        assertEquals(
                window.cursorStartPosition.x + expectedWidth[0],
                window.cursorIdealMaxPosition.x,
                EPSILON);
    }

    /** widgets_tabbar_tabitem_button. */
    @Test
    void testTabBarTabItemButton() {
        final int[] lastClickedButton = {-1};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.ALWAYS_AUTO_RESIZE | WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTabBar("TabBar")) {
                        if (IkGui.tabItemButton("1", TabItemFlags.NONE)) {
                            lastClickedButton[0] = 1;
                        }
                        if (IkGui.tabItemButton("0", TabItemFlags.NONE)) {
                            lastClickedButton[0] = 0;
                        }
                        if (IkGui.beginTabItem("Tab", null, TabItemFlags.NONE)) {
                            IkGui.endTabItem();
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window/TabBar");

        assertEquals(-1, lastClickedButton[0]);
        ctx.itemClick("1");
        assertEquals(1, lastClickedButton[0]);
        ctx.itemClick("Tab");
        assertEquals(1, lastClickedButton[0]);
        ctx.mouseMove("0");
        ctx.mouseDown(MouseButton.LEFT);
        assertEquals(1, lastClickedButton[0]);
        ctx.mouseUp(MouseButton.LEFT);
        assertEquals(0, lastClickedButton[0]);
    }

    /** widgets_tabbar_tabitem_leading_trailing: leading and trailing tabs keep their places. */
    @Test
    void testTabBarTabItemLeadingTrailing() {
        final IkBoolean windowAutoResize = new IkBoolean(true);
        final int[] tabBarFlags = {0};
        final TabBar[] tabBar = {null};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            (windowAutoResize.get() ? WindowFlags.ALWAYS_AUTO_RESIZE : 0)
                                    | WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.checkbox("WindowFlags.ALWAYS_AUTO_RESIZE", windowAutoResize);
                    if (IkGui.beginTabBar("TabBar", tabBarFlags[0])) {
                        tabBar[0] = ctx.context.currentTabBar;
                        // Intentionally submit the trailing tab early, and the leading tab last
                        if (IkGui.beginTabItem("Trailing", null, TabItemFlags.TRAILING)) {
                            IkGui.endTabItem();
                        }
                        for (int i = 0; i < 3; ++i) {
                            if (IkGui.beginTabItem("Tab " + i, null, TabItemFlags.NONE)) {
                                IkGui.endTabItem();
                            }
                        }
                        if (IkGui.beginTabItem("Leading", null, TabItemFlags.LEADING)) {
                            IkGui.endTabItem();
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                });
        final var io = ctx.context.io;
        tabBarFlags[0] = TabBarFlags.REORDERABLE | TabBarFlags.FITTING_POLICY_SHRINK;
        ctx.yieldFrame();

        ctx.setRef("Test Window/TabBar");
        final String[] tabs = {"Leading", "Tab 0", "Tab 1", "Tab 2", "Trailing"};

        // The relative order of the tabs, which isn't the submission order
        float offsetX = -Float.MAX_VALUE;
        for (String tab : tabs) {
            ctx.mouseMove(tab);
            assertTrue(io.mousePosition.x > offsetX, tab);
            offsetX = io.mousePosition.x;
        }

        // The leading tab can't be reordered over "Tab 0" and vice versa
        ctx.itemDragAndDrop("Leading", "Tab 0");
        assertEquals(ctx.getID("Leading"), tabBar[0].tabs.get(0).id);
        assertEquals(ctx.getID("Tab 0"), tabBar[0].tabs.get(1).id);
        ctx.itemDragAndDrop("Tab 0", "Leading");
        assertEquals(ctx.getID("Leading"), tabBar[0].tabs.get(0).id);
        assertEquals(ctx.getID("Tab 0"), tabBar[0].tabs.get(1).id);

        // The trailing tab can't be reordered over "Tab 2" and vice versa
        ctx.itemDragAndDrop("Trailing", "Tab 2");
        assertEquals(ctx.getID("Trailing"), tabBar[0].tabs.get(4).id);
        assertEquals(ctx.getID("Tab 2"), tabBar[0].tabs.get(3).id);
        ctx.itemDragAndDrop("Tab 2", "Trailing");
        assertEquals(ctx.getID("Trailing"), tabBar[0].tabs.get(4).id);
        assertEquals(ctx.getID("Tab 2"), tabBar[0].tabs.get(3).id);

        // Resize down
        windowAutoResize.set(false);
        final Window window = ctx.getWindowByRef("//Test Window");
        ctx.windowResize("//Test Window", window.size.x * 0.3f, window.size.y);
        for (int i = 0; i < 2; ++i) {
            tabBarFlags[0] =
                    TabBarFlags.REORDERABLE
                            | (i == 0
                                    ? TabBarFlags.FITTING_POLICY_SHRINK
                                    : TabBarFlags.FITTING_POLICY_SCROLL);
            ctx.yieldFrame();
            final String d = "policy " + i;
            assertTrue(ctx.itemInfo("Leading").rectClipped.getWidth() > 1.0f, d);
            assertEquals(0.0f, Math.max(0, ctx.itemInfo("Tab 0").rectClipped.getWidth()), d);
            assertEquals(0.0f, Math.max(0, ctx.itemInfo("Tab 1").rectClipped.getWidth()), d);
            assertEquals(0.0f, Math.max(0, ctx.itemInfo("Tab 2").rectClipped.getWidth()), d);
            assertTrue(ctx.itemInfo("Trailing").rectClipped.getWidth() > 1.0f, d);
        }
    }

    /** widgets_tabbar_reorder: reordering tabs, and TabItemFlags.NO_REORDER. */
    @Test
    void testTabBarReorder() {
        final int[] flags = {TabBarFlags.REORDERABLE};
        final TabBar[] tabBar = {null};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.ALWAYS_AUTO_RESIZE | WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTabBar("TabBar", flags[0])) {
                        tabBar[0] = ctx.context.currentTabBar;
                        final int[] tabFlags = {
                            TabItemFlags.NONE,
                            TabItemFlags.NONE,
                            TabItemFlags.NO_REORDER,
                            TabItemFlags.NONE
                        };
                        for (int i = 0; i < 4; ++i) {
                            if (IkGui.beginTabItem("Tab " + i, null, tabFlags[i])) {
                                IkGui.endTabItem();
                            }
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                });

        // Reset the reorderable flag so the tabs are in their submission order
        flags[0] = TabBarFlags.NONE;
        ctx.yieldFrame();
        flags[0] = TabBarFlags.REORDERABLE;
        ctx.yieldFrame();

        ctx.setRef("Test Window/TabBar");

        ctx.itemDragAndDrop("Tab 0", "Tab 1");
        assertEquals(ctx.getID("Tab 1"), tabBar[0].tabs.get(0).id);
        assertEquals(ctx.getID("Tab 0"), tabBar[0].tabs.get(1).id);

        ctx.itemDragAndDrop("Tab 0", "Tab 1");
        assertEquals(ctx.getID("Tab 0"), tabBar[0].tabs.get(0).id);
        assertEquals(ctx.getID("Tab 1"), tabBar[0].tabs.get(1).id);

        // Tab 2 has the NO_REORDER flag
        ctx.itemDragAndDrop("Tab 0", "Tab 2");
        ctx.itemDragAndDrop("Tab 0", "Tab 3");
        ctx.itemDragAndDrop("Tab 3", "Tab 2");
        assertEquals(ctx.getID("Tab 0"), tabBar[0].tabs.get(1).id);
        assertEquals(ctx.getID("Tab 2"), tabBar[0].tabs.get(2).id);
        assertEquals(ctx.getID("Tab 3"), tabBar[0].tabs.get(3).id);
    }

    /** widgets_tabbar_nested: nested tab bars. */
    @Test
    void testTabBarNested() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    if (IkGui.beginTabBar("TabBar 0")) {
                        if (IkGui.beginTabItem("TabItem")) {
                            // Upstream, many tab bars here invalidate pointers to pooled tab bars
                            for (int i = 0; i < 10; ++i) {
                                if (IkGui.beginTabBar("Inner TabBar " + i)) {
                                    if (IkGui.beginTabItem("Inner TabItem")) {
                                        IkGui.endTabItem();
                                    }
                                    IkGui.endTabBar();
                                }
                            }
                            IkGui.endTabItem();
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(3);
    }

    /** widgets_tabbar_append: appending to a tab bar with a second beginTabBar(). */
    @Test
    void testTabBarAppend() {
        final IkBoolean appendToTabBar = new IkBoolean(true);
        final Vector2f afterActiveTab = new Vector2f();
        final Vector2f afterFirstBeginTabBar = new Vector2f();
        final Vector2f afterFirstWidget = new Vector2f();
        final Vector2f afterSecondBeginTabBar = new Vector2f();
        final Vector2f afterSecondWidget = new Vector2f();
        final Vector2f afterSecondEndTabBar = new Vector2f();
        ctx.setGui(
                () -> {
                    final var g = ctx.context;
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.checkbox("AppendToTabBar", appendToTabBar);
                    if (IkGui.beginTabBar("TabBar")) {
                        afterFirstBeginTabBar.set(g.windowCurrent.cursorPosition);
                        if (IkGui.beginTabItem("Tab 0")) {
                            IkGui.text("Tab 0");
                            IkGui.endTabItem();
                            afterActiveTab.set(g.windowCurrent.cursorPosition);
                        }
                        if (IkGui.beginTabItem("Tab 1")) {
                            for (int i = 0; i < 3; ++i) {
                                IkGui.text("Tab 1 Line " + i);
                            }
                            IkGui.endTabItem();
                            afterActiveTab.set(g.windowCurrent.cursorPosition);
                        }
                        if (IkGui.beginTabItem("Tab 2")) {
                            IkGui.endTabItem();
                            afterActiveTab.set(g.windowCurrent.cursorPosition);
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.text("After first TabBar submission");
                    afterFirstWidget.set(g.windowCurrent.cursorPosition);

                    if (appendToTabBar.get() && IkGui.beginTabBar("TabBar")) {
                        afterSecondBeginTabBar.set(g.windowCurrent.cursorPosition);
                        if (IkGui.beginTabItem("Tab A")) {
                            IkGui.text("I'm tab A");
                            IkGui.endTabItem();
                            afterActiveTab.set(g.windowCurrent.cursorPosition);
                        }
                        IkGui.endTabBar();
                        afterSecondEndTabBar.set(g.windowCurrent.cursorPosition);
                    }
                    IkGui.text("After second TabBar submission");
                    afterSecondWidget.set(g.windowCurrent.cursorPosition);
                    IkGui.end();
                });
        ctx.setRef("Test Window/TabBar");

        final float lineHeight =
                IkGuiInternal.getFontSize() + ctx.context.style.variable.itemSpacing.y;
        for (boolean append : new boolean[] {false, true}) {
            appendToTabBar.set(append);
            ctx.yieldFrame();
            for (String tabName : new String[] {"Tab 0", "Tab 1", "Tab A"}) {
                if (!append && "Tab A".equals(tabName)) {
                    continue;
                }
                final String d = tabName + ", append " + append;
                ctx.itemClick(tabName);
                ctx.yieldFrame();

                final float activeTabHeight = "Tab 1".equals(tabName) ? lineHeight * 3 : lineHeight;
                assertEquals(afterFirstBeginTabBar.y + activeTabHeight, afterActiveTab.y, d);
                assertEquals(afterActiveTab.y + lineHeight, afterFirstWidget.y, d);
                if (append) {
                    assertEquals(afterFirstBeginTabBar.y, afterSecondBeginTabBar.y, d);
                    assertEquals(afterFirstWidget.y, afterSecondEndTabBar.y, d);
                }
                assertEquals(afterFirstWidget.y + lineHeight, afterSecondWidget.y, d);
            }
        }
    }

    /** widgets_tabbar_dockspace: a dockspace inside a tab item. */
    @Test
    void testTabBarDockSpace() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTabBar("TabBar")) {
                        if (IkGui.beginTabItem("TabItem")) {
                            IkGui.dockSpace(IkGui.getID("Hello"), 0, 0);
                            IkGui.endTabItem();
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(3);
    }

    /** widgets_tabbar_tabitem_setselected: TabItemFlags.SET_SELECTED on the first frame. */
    @Test
    void testTabBarTabItemSetSelected() {
        final int[] frame = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTabBar("tab_bar")) {
                        if (IkGui.beginTabItem("TabItem 0")) {
                            IkGui.textUnformatted("First tab content");
                            IkGui.endTabItem();
                        }
                        final boolean tabItemVisible =
                                IkGui.beginTabItem(
                                        "TabItem 1",
                                        null,
                                        frame[0] == 0
                                                ? TabItemFlags.SET_SELECTED
                                                : TabItemFlags.NONE);
                        if (tabItemVisible) {
                            IkGui.textUnformatted("Second tab content");
                            IkGui.endTabItem();
                        }
                        if (frame[0] > 0) {
                            ctx.check(tabItemVisible, "the second tab is selected");
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                    frame[0]++;
                });
        ctx.yieldFrames(2);
    }

    /** widgets_tabbar_tabitem_setwidth: setNextItemWidth() with tab items. */
    @Test
    void testTabBarTabItemSetWidth() {
        final int[] testFlags = {
            TabItemFlags.NONE, TabItemFlags.TRAILING, TabItemFlags.LEADING, TabItemFlags.NONE
        };
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    if (IkGui.beginTabBar("TabBar")) {
                        for (int i = 0; i < testFlags.length; ++i) {
                            IkGui.setNextItemWidth(30.0f + i * 10.0f);
                            if (IkGui.beginTabItem("Tab " + i, null, testFlags[i])) {
                                IkGui.textUnformatted("Content " + i);
                                IkGui.endTabItem();
                            }
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        // Tabs are submitted with an empty rect on their first frame
        ctx.yieldFrame();
        for (int i = 0; i < testFlags.length; ++i) {
            final IkGuiTestContext.ItemInfo info = ctx.itemInfo("TabBar/Tab " + i);
            assertEquals(30.0f + i * 10.0f, info.rect.getWidth(), EPSILON, "Tab " + i);
        }
    }

    /** widgets_tabbar_popup_scrolling_button: the tab list popup and the scrolling buttons. */
    @Test
    void testTabBarPopupScrollingButton() {
        final int tabCount = 9;
        final int[] selected = {-1};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(200, 100);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTabBar(
                            "TabBar",
                            TabBarFlags.TAB_LIST_POPUP_BUTTON
                                    | TabBarFlags.FITTING_POLICY_SCROLL)) {
                        for (int i = 0; i < tabCount; ++i) {
                            if (IkGui.beginTabItem("Tab " + i, null)) {
                                selected[0] = i;
                                IkGui.endTabItem();
                            }
                        }
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        // Make sure the first tab is selected
        ctx.itemClick("TabBar/Tab 0");

        for (int i = 0; i < tabCount; ++i) {
            final String d = "tab " + i;
            ctx.itemClick("TabBar/##<");
            ctx.yieldFrame();
            assertEquals(i == 0 ? 0 : i - 1, selected[0], d);

            ctx.itemClick("TabBar/##v");
            ctx.itemClick("//##Combo_00/Tab " + i);
            ctx.yieldFrame();
            assertEquals(i, selected[0], d);

            ctx.itemClick("TabBar/##>");
            ctx.yieldFrame();
            assertEquals(i == tabCount - 1 ? tabCount - 1 : i + 1, selected[0], d);
        }

        // Click on all the even tabs
        for (int i = 0; i < tabCount / 2; ++i) {
            ctx.itemClick("TabBar/Tab " + (i * 2));
            assertEquals(i * 2, selected[0]);
        }

        // Click on all the odd tabs
        for (int i = 0; i < tabCount / 2; ++i) {
            ctx.itemClick("TabBar/Tab " + (i * 2 + 1));
            assertEquals(i * 2 + 1, selected[0]);
        }
    }

    /** widgets_tabbar_focus_tab_on_first_frame: tabBarQueueFocus() on the appearing frame. */
    @Test
    void testTabBarFocusTabOnFirstFrame() {
        final boolean[] focused = {false};
        final boolean[] firstFrame = {true};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    // Clear any existing data
                    if (firstFrame[0]) {
                        ctx.context.tabBars.remove(IkGui.getID("##TabBar"));
                        firstFrame[0] = false;
                    }
                    if (IkGui.beginTabBar("##TabBar")) {
                        if (!focused[0]) {
                            // Focus once
                            IkGuiImplTabs.tabBarQueueFocus(ctx.context.currentTabBar, "BBB");
                            focused[0] = true;
                        }
                        for (String name : new String[] {"AAA", "BBB", "CCC"}) {
                            if (IkGui.beginTabItem(name)) {
                                IkGui.endTabItem();
                            }
                        }
                        ctx.checkEquals(
                                IkGui.getID("BBB"),
                                ctx.context.currentTabBar.selectedTabID,
                                "selected tab");
                        IkGui.endTabBar();
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** State for the tree node behavior test. */
    private static class TreeNodeTestVars {
        boolean reset = true;
        boolean isOpen = false;
        boolean isMultiSelect = false;
        int toggleCount = 0;
        int dragSourceCount = 0;
        final IkInt treeNodeFlags = new IkInt(0);
        final IkInt multiSelectFlags = new IkInt(0);
    }

    /** widgets_treenode_behaviors: tree node flags, clicks, drag sources and drag-hold to open. */
    @Test
    void testTreeNodeBehaviors() {
        final TreeNodeTestVars vars = new TreeNodeTestVars();
        final IkBoolean isMultiSelect = new IkBoolean(false);
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 100, Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);

                    // To test opening on drag-hold
                    IkGui.colorButton(
                            "Color",
                            new float[] {1.0f, 1.0f, 0.0f, 1.0f},
                            ColorEditFlags.NO_TOOLTIP);

                    if (vars.reset) {
                        IkGui.getStateStorage().setInt(IkGui.getID("AAA"), 0);
                        vars.toggleCount = 0;
                        vars.dragSourceCount = 0;
                    }
                    vars.reset = false;

                    IkGui.text(
                            String.format(
                                    "TreeNodeFlags: 0x%08X, MultiSelect: %b (flags=%08X)",
                                    vars.treeNodeFlags.get(),
                                    vars.isMultiSelect,
                                    vars.multiSelectFlags.get()));
                    IkGui.checkboxFlags(
                            "TreeNodeFlags.OPEN_ON_ARROW",
                            vars.treeNodeFlags,
                            TreeNodeFlags.OPEN_ON_ARROW);
                    IkGui.checkboxFlags(
                            "TreeNodeFlags.OPEN_ON_DOUBLE_CLICK",
                            vars.treeNodeFlags,
                            TreeNodeFlags.OPEN_ON_DOUBLE_CLICK);
                    isMultiSelect.set(vars.isMultiSelect);
                    IkGui.checkbox("IsMultiSelect", isMultiSelect);
                    vars.isMultiSelect = isMultiSelect.get();
                    IkGui.checkboxFlags(
                            "MultiSelectFlags.SELECT_ON_AUTO",
                            vars.multiSelectFlags,
                            MultiSelectFlags.SELECT_ON_AUTO);
                    IkGui.checkboxFlags(
                            "MultiSelectFlags.SELECT_ON_CLICK_RELEASE",
                            vars.multiSelectFlags,
                            MultiSelectFlags.SELECT_ON_CLICK_RELEASE);

                    final boolean multiSelect = vars.isMultiSelect;
                    if (multiSelect) {
                        // Placeholder, won't interact properly
                        IkGui.beginMultiSelect(vars.multiSelectFlags.get());
                        // To enable the multi-select logic
                        IkGui.setNextItemSelectionUserData(0);
                    }
                    vars.isOpen = IkGui.treeNodeEx("AAA", vars.treeNodeFlags.get());
                    if (IkGui.isItemToggledOpen()) {
                        vars.toggleCount++;
                    }
                    if (IkGui.beginDragDropSource()) {
                        vars.dragSourceCount++;
                        IkGui.setDragDropPayload("_TREENODE", (Object) null);
                        IkGui.text("Drag Source Tooltip");
                        IkGui.endDragDropSource();
                    }
                    if (vars.isOpen) {
                        IkGui.text("Contents");
                        IkGui.treePop();
                    }
                    if (IkGui.treeNodeEx("BBB", vars.treeNodeFlags.get())) {
                        IkGui.treePop();
                    }
                    if (multiSelect) {
                        IkGui.endMultiSelect();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final int aaa = ctx.getID("AAA");

        for (int step = 0; step < 3; ++step) {
            vars.isMultiSelect = step == 1 || step == 2;
            vars.multiSelectFlags.set(
                    step == 1
                            ? MultiSelectFlags.SELECT_ON_CLICK_RELEASE
                            : MultiSelectFlags.SELECT_ON_AUTO);
            final String base = "step " + step;

            // TreeNodeFlags.NONE
            {
                final String d = base + ", no flags";
                vars.reset = true;
                vars.treeNodeFlags.set(TreeNodeFlags.NONE);
                ctx.yieldFrame();
                assertTrue(!vars.isOpen && vars.toggleCount == 0, d);

                // Click on the arrow
                ctx.mouseMoveToEdge("AAA", false);
                // Toggles on down when hovering the arrow
                ctx.mouseDown(MouseButton.LEFT);
                assertTrue(vars.isOpen, d);
                ctx.mouseUp(MouseButton.LEFT);
                assertTrue(vars.isOpen, d);
                ctx.mouseClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                ctx.mouseDoubleClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                assertEquals(4, vars.toggleCount, d);

                // Click on the main section. Opening on the arrow is implicit with multi-select,
                // so the main section won't react.
                if (!vars.isMultiSelect) {
                    vars.toggleCount = 0;
                    ctx.mouseMove("AAA");
                    ctx.mouseClick(MouseButton.LEFT);
                    assertTrue(vars.isOpen, d);
                    ctx.mouseClick(MouseButton.LEFT);
                    assertFalse(vars.isOpen, d);
                    ctx.mouseDoubleClick(MouseButton.LEFT);
                    assertFalse(vars.isOpen, d);
                    assertEquals(4, vars.toggleCount, d);
                }

                // The tree node as a drag source
                assertEquals(0, vars.dragSourceCount, d);
                ctx.itemDragWithDelta(aaa, 50, 50);
                assertTrue(vars.dragSourceCount > 0, d);
                assertFalse(vars.isOpen, d);

                // Opening on drag-hold
                ctx.itemDragOverAndHold("Color", "AAA");
                assertTrue(vars.isOpen, d);
            }

            // TreeNodeFlags.OPEN_ON_DOUBLE_CLICK
            {
                final String d = base + ", open on double click";
                vars.reset = true;
                vars.treeNodeFlags.set(TreeNodeFlags.OPEN_ON_DOUBLE_CLICK);
                ctx.yieldFrame();
                assertTrue(!vars.isOpen && vars.toggleCount == 0, d);

                // Click on the arrow. Opening on the arrow is implicit with multi-select.
                if (!vars.isMultiSelect) {
                    ctx.mouseMoveToEdge("AAA", false);
                    ctx.mouseDown(MouseButton.LEFT);
                    assertFalse(vars.isOpen, d);
                    ctx.mouseUp(MouseButton.LEFT);
                    assertFalse(vars.isOpen, d);
                    ctx.mouseClick(MouseButton.LEFT);
                    assertFalse(vars.isOpen, d);
                    assertEquals(0, vars.toggleCount, d);
                    ctx.mouseDoubleClick(MouseButton.LEFT);
                    assertTrue(vars.isOpen, d);
                    ctx.mouseDoubleClick(MouseButton.LEFT);
                    assertFalse(vars.isOpen, d);
                    assertEquals(2, vars.toggleCount, d);
                }

                // Double click on the main section
                vars.toggleCount = 0;
                ctx.mouseMove("AAA");
                ctx.mouseClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                ctx.mouseClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                assertEquals(0, vars.toggleCount, d);
                ctx.mouseDoubleClick(MouseButton.LEFT);
                assertTrue(vars.isOpen, d);
                ctx.mouseDoubleClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                assertEquals(2, vars.toggleCount, d);

                assertEquals(0, vars.dragSourceCount, d);
                ctx.itemDragWithDelta(aaa, 50, 50);
                assertTrue(vars.dragSourceCount > 0, d);
                assertFalse(vars.isOpen, d);

                ctx.itemDragOverAndHold("Color", "AAA");
                assertTrue(vars.isOpen, d);
            }

            // TreeNodeFlags.OPEN_ON_ARROW
            {
                final String d = base + ", open on arrow";
                vars.reset = true;
                vars.treeNodeFlags.set(TreeNodeFlags.OPEN_ON_ARROW);
                ctx.yieldFrame();
                assertTrue(!vars.isOpen && vars.toggleCount == 0, d);

                ctx.mouseMoveToEdge("AAA", false);
                ctx.mouseDown(MouseButton.LEFT);
                assertTrue(vars.isOpen, d);
                ctx.mouseUp(MouseButton.LEFT);
                assertTrue(vars.isOpen, d);
                ctx.mouseClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                assertEquals(2, vars.toggleCount, d);
                ctx.mouseDoubleClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                assertEquals(4, vars.toggleCount, d);

                vars.toggleCount = 0;
                ctx.mouseMove("AAA");
                ctx.mouseClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                ctx.mouseClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                assertEquals(0, vars.toggleCount, d);
                ctx.mouseDoubleClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                assertEquals(0, vars.toggleCount, d);

                assertEquals(0, vars.dragSourceCount, d);
                ctx.itemDragWithDelta(aaa, 50, 50);
                assertTrue(vars.dragSourceCount > 0, d);
                assertFalse(vars.isOpen, d);

                ctx.itemDragOverAndHold("Color", "AAA");
                assertTrue(vars.isOpen, d);
            }

            // TreeNodeFlags.OPEN_ON_ARROW | TreeNodeFlags.OPEN_ON_DOUBLE_CLICK
            {
                final String d = base + ", open on arrow or double click";
                vars.reset = true;
                vars.treeNodeFlags.set(
                        TreeNodeFlags.OPEN_ON_ARROW | TreeNodeFlags.OPEN_ON_DOUBLE_CLICK);
                ctx.yieldFrame();
                assertTrue(!vars.isOpen && vars.toggleCount == 0, d);

                ctx.mouseMoveToEdge("AAA", false);
                ctx.mouseDown(MouseButton.LEFT);
                assertTrue(vars.isOpen, d);
                ctx.mouseUp(MouseButton.LEFT);
                ctx.mouseClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                assertEquals(2, vars.toggleCount, d);
                ctx.mouseDoubleClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                assertEquals(4, vars.toggleCount, d);

                vars.toggleCount = 0;
                ctx.mouseMove("AAA");
                ctx.mouseClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                ctx.mouseClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                assertEquals(0, vars.toggleCount, d);
                ctx.mouseDoubleClick(MouseButton.LEFT);
                assertTrue(vars.isOpen, d);
                ctx.mouseDoubleClick(MouseButton.LEFT);
                assertFalse(vars.isOpen, d);
                assertEquals(2, vars.toggleCount, d);

                assertEquals(0, vars.dragSourceCount, d);
                ctx.itemDragWithDelta(aaa, 50, 50);
                assertTrue(vars.dragSourceCount > 0, d);
                assertFalse(vars.isOpen, d);

                ctx.itemDragOverAndHold("Color", "AAA");
                assertTrue(vars.isOpen, d);
            }
        }
    }

    /** widgets_treenode_span_width: TreeNodeFlags.SPAN_AVAIL_WIDTH and SPAN_FULL_WIDTH. */
    @Test
    void testTreeNodeSpanWidth() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 100, Condition.ALWAYS);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final var g = ctx.context;
                    final Window window = g.windowCurrent;

                    IkGui.setNextItemOpen(true);
                    if (IkGui.treeNodeEx("Parent")) {
                        // The interaction rect doesn't span the entire width of the work area
                        ctx.check(
                                g.lastItemData.rect.getRight() < window.rectWork.getRight(),
                                "parent right edge");
                        // But it starts at the beginning of the work rect for the first level
                        ctx.check(
                                g.lastItemData.rect.getLeft() == window.rectWork.getLeft(),
                                "parent left edge");
                        IkGui.setNextItemOpen(true);
                        if (IkGui.treeNodeEx("Regular")) {
                            ctx.check(
                                    g.lastItemData.rect.getRight() < window.rectWork.getRight(),
                                    "regular right edge");
                            ctx.check(
                                    g.lastItemData.rect.getLeft() > window.rectWork.getLeft(),
                                    "regular left edge");
                            IkGui.treePop();
                        }
                        IkGui.setNextItemOpen(true);
                        if (IkGui.treeNodeEx("SpanAvailWidth", TreeNodeFlags.SPAN_AVAIL_WIDTH)) {
                            // The interaction rect matches the visible frame rect
                            ctx.check(
                                    (g.lastItemData.statusFlags & ItemStatusFlags.HAS_DISPLAY_RECT)
                                            != 0,
                                    "avail has display rect");
                            ctx.check(
                                    sameRect(g.lastItemData.displayRect, g.lastItemData.rect),
                                    "avail display rect");
                            // The interaction rect extends to the end of the available area
                            ctx.check(
                                    g.lastItemData.rect.getRight() == window.rectWork.getRight(),
                                    "avail right edge");
                            IkGui.treePop();
                        }
                        IkGui.setNextItemOpen(true);
                        if (IkGui.treeNodeEx("SpanFullWidth", TreeNodeFlags.SPAN_FULL_WIDTH)) {
                            ctx.check(
                                    (g.lastItemData.statusFlags & ItemStatusFlags.HAS_DISPLAY_RECT)
                                            != 0,
                                    "full has display rect");
                            ctx.check(
                                    sameRect(g.lastItemData.displayRect, g.lastItemData.rect),
                                    "full display rect");
                            ctx.check(
                                    g.lastItemData.rect.getRight() == window.rectWork.getRight(),
                                    "full right edge");
                            // SPAN_FULL_WIDTH also extends the interaction rect to the left
                            ctx.check(
                                    g.lastItemData.rect.getLeft() == window.rectWork.getLeft(),
                                    "full left edge");
                            IkGui.treePop();
                        }
                        IkGui.treePop();
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    private static boolean sameRect(RectFloat a, RectFloat b) {
        return a.getLeft() == b.getLeft()
                && a.getTop() == b.getTop()
                && a.getRight() == b.getRight()
                && a.getBottom() == b.getBottom();
    }

    /** widgets_treenode_padding: padding with the treeNode() + sameLine() idiom. */
    @Test
    void testTreeNodePadding() {
        ctx.setGui(
                () -> {
                    final var style = ctx.context.style.variable;
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    for (int n = 0; n < 2; ++n) {
                        IkGui.pushStyleVarFloat2(StyleVariable.FRAME_PADDING, n == 0 ? 4 : 11, 3);
                        IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, n == 0 ? 8 : 9, 4);
                        IkGui.pushID(n);

                        final float offset = IkGui.getTreeNodeToLabelSpacing();
                        ctx.checkEquals(
                                IkGuiInternal.getFontSize() + style.framePadding.x * 2.0f,
                                offset,
                                "label spacing");

                        final Vector2f p = IkGui.getCursorScreenPos();
                        {
                            final boolean isOpen = IkGui.treeNode("Hello1");
                            IkGui.sameLine();
                            ctx.checkEquals(
                                    p.x
                                            + offset
                                            + IkGui.calcTextSize("Hello1").x
                                            + style.itemSpacing.x,
                                    IkGui.getCursorScreenPos().x,
                                    "Hello1");
                            IkGui.text("World");
                            if (isOpen) {
                                IkGui.treePop();
                            }
                        }
                        {
                            final boolean isOpen = IkGui.treeNode("Hello2");
                            IkGui.sameLine(0, 0);
                            ctx.checkEquals(
                                    p.x + offset + IkGui.calcTextSize("Hello2").x,
                                    IkGui.getCursorScreenPos().x,
                                    "Hello2");
                            IkGui.text("World");
                            if (isOpen) {
                                IkGui.treePop();
                            }
                        }
                        {
                            final boolean isOpen = IkGui.treeNodeEx("##Hello3", 0);
                            IkGui.sameLine();
                            ctx.checkEquals(
                                    p.x + offset + style.itemSpacing.x,
                                    IkGui.getCursorScreenPos().x,
                                    "Hello3");
                            IkGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "World");
                            if (isOpen) {
                                IkGui.treePop();
                            }
                        }
                        {
                            final boolean isOpen = IkGui.treeNodeEx("##Hello4", 0);
                            IkGui.sameLine(0, 0);
                            ctx.checkEquals(p.x + offset, IkGui.getCursorScreenPos().x, "Hello4");
                            IkGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "World");
                            if (isOpen) {
                                IkGui.treePop();
                            }
                        }
                        IkGui.popID();
                        IkGui.popStyleVar(2);
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** widgets_plot_lines_unexpected_input: plotLines() with zero or one values. */
    @Test
    void testPlotLinesUnexpectedInput() {
        ctx.setGui(
                () -> {
                    final float[] values = {0.0f};
                    // Upstream passes a null array here, the port requires an array
                    IkGui.plotLines("PlotLines 1", new float[0], 0);
                    IkGui.plotLines("PlotLines 2", values, 0);
                    IkGui.plotLines("PlotLines 3", values, 1);
                    // If this doesn't crash, it passed
                });
        ctx.yieldFrames(2);
    }

    private static boolean colorEquals(float[] color, int r, int g, int b, int a) {
        final float eps = 0.000_000_1f;
        return Math.abs(color[0] - r / 255.0f) < eps
                && Math.abs(color[1] - g / 255.0f) < eps
                && Math.abs(color[2] - b / 255.0f) < eps
                && Math.abs(color[3] - a / 255.0f) < eps;
    }

    /** widgets_coloredit_hexinput: typing hex values into the color picker. */
    @Test
    void testColorEditHexInput() {
        final float[] color = {1, 0, 0, 1};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.colorEdit4("ColorEdit1", color, ColorEditFlags.DISPLAY_HEX);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("ColorEdit1/##ColorButton");
        ctx.setRef("//$FOCUSED");
        ctx.itemInputValue("##picker/##hex/##Text", "112233");
        assertTrue(colorEquals(color, 0x11, 0x22, 0x33, 0xFF));
        ctx.itemInputValue("##picker/##hex/##Text", "11223344");
        assertTrue(colorEquals(color, 0x11, 0x22, 0x33, 0x44));
        ctx.itemInputValue("##picker/##hex/##Text", "#112233");
        assertTrue(colorEquals(color, 0x11, 0x22, 0x33, 0xFF));
        ctx.itemInputValue("##picker/##hex/##Text", "#11223344");
        assertTrue(colorEquals(color, 0x11, 0x22, 0x33, 0x44));
    }

    /**
     * widgets_coloredit_preserve_hs: hue and saturation are preserved for colors where they are
     * undefined.
     */
    @Test
    void testColorEditPreserveHueSaturation() {
        final int[] step = {0};
        final int[] rgbi = {0};
        final float[] rgbf = {0, 0, 0, 0};
        final IkBoolean useInt = new IkBoolean(false);
        ctx.setGui(
                () -> {
                    final float[] color;
                    if (step[0] == 0) {
                        color = rgbf.clone();
                    } else {
                        final Vector4f c = IkGui.colorConvertU32ToFloat4(rgbi[0]);
                        color = new float[] {c.x, c.y, c.z, c.w};
                    }
                    IkGui.setNextWindowSize(300, 100, Condition.APPEARING);
                    if (IkGui.begin("Window", null, WindowFlags.NO_SAVED_SETTINGS)) {
                        useInt.set(step[0] == 1);
                        if (IkGui.checkbox("Use int", useInt)) {
                            step[0] = useInt.get() ? 1 : 0;
                        }
                        if (IkGui.colorEdit4("Color", color, ColorEditFlags.PICKER_HUE_BAR)) {
                            if (step[0] == 0) {
                                System.arraycopy(color, 0, rgbf, 0, 4);
                            } else {
                                rgbi[0] =
                                        IkGui.colorConvertFloat4ToU32(
                                                color[0], color[1], color[2], color[3]);
                            }
                        }
                    }
                    IkGui.end();
                });

        // Returns {R, G, B, H, S, V}
        final java.util.function.Supplier<float[]> readColor =
                () -> {
                    final float[] rgb;
                    if (step[0] == 0) {
                        rgb = rgbf;
                    } else {
                        final Vector4f c = IkGui.colorConvertU32ToFloat4(rgbi[0]);
                        rgb = new float[] {c.x, c.y, c.z, c.w};
                    }
                    final float[] hsv = new float[3];
                    IkGui.colorConvertRGBtoHSV(rgb, hsv);
                    return new float[] {rgb[0], rgb[1], rgb[2], hsv[0], hsv[1], hsv[2]};
                };
        final int r = 0;
        final int g = 1;
        final int b = 2;
        final int h = 3;
        final int s = 4;
        final int v = 5;

        final var context = ctx.context;
        ctx.itemClick("Window/Color/##ColorButton");
        ctx.setRef("//$FOCUSED");
        final Window popup = context.navFocusedWindow;

        // Variant 0 uses float RGB, variant 1 uses int RGB
        for (int variant = 0; variant < 2; ++variant) {
            step[0] = variant;
            final String d = "variant " + variant;

            // Set the initial color
            ctx.itemClick("##picker/sv");
            ctx.itemClick("##picker/hue");

            float[] colorStart = readColor.get();
            assertNotEquals(0.0f, colorStart[h], d);
            assertNotEquals(0.0f, colorStart[s], d);
            assertNotEquals(0.0f, colorStart[v], d);

            // Set the saturation to 0, the hue must be preserved
            ctx.itemDragWithDelta(ctx.getID("##picker/sv"), -popup.size.x * 0.5f, 0);

            final float eps = variant == 0 ? Math.ulp(1.0f) : 1.0f / 350.0f;

            float[] color = readColor.get();
            assertEquals(0.0f, color[s], d);
            // The hue is undefined
            assertEquals(0.0f, color[h], d);
            // The preserved hue matches the original color
            assertEquals(colorStart[h], context.colorEditSavedHue, eps, d);

            // Saturation preservation during mouse input
            ctx.itemClick("##picker/sv");
            ctx.itemClick("##picker/hue");
            assertNotEquals(0.0f, readColor.get()[s], d);
            colorStart = readColor.get();
            ctx.mouseDown(MouseButton.LEFT);

            // Move the mouse across the hue slider, to the extremes
            final Vector2f mouse = context.io.mousePosition;
            ctx.mouseMoveToPos(mouse.x, mouse.y + popup.size.y * 0.5f);
            ctx.mouseMoveToPos(mouse.x, mouse.y - popup.size.y * 1.0f);
            ctx.mouseUp(MouseButton.LEFT);
            assertEquals(colorStart[s], readColor.get()[s], d);
            assertEquals(colorStart[s], context.colorEditSavedSaturation, eps, d);

            // Reset the color
            ctx.itemClick("##picker/sv");
            ctx.itemClick("##picker/hue");
            colorStart = readColor.get();

            // Set the value to 0, the saturation must be preserved
            ctx.itemDragWithDelta(ctx.getID("##picker/sv"), 0, popup.size.y * 0.5f);
            color = readColor.get();
            assertEquals(0.0f, color[v], d);
            // The saturation is undefined
            assertEquals(0.0f, color[s], d);
            assertEquals(colorStart[s], context.colorEditSavedSaturation, eps, d);

            // Set the color to pure white, and verify it can reach 1, 1, 1
            ctx.itemDragWithDelta(
                    ctx.getID("##picker/sv"), -popup.size.x * 0.5f, -popup.size.y * 0.5f);
            color = readColor.get();
            assertEquals(1.0f, color[r], d);
            assertEquals(1.0f, color[g], d);
            assertEquals(1.0f, color[b], d);

            // Move the hue to the extreme ends, and check that it doesn't wrap around
            ctx.itemDragWithDelta(ctx.getID("##picker/hue"), 0, popup.size.y * 0.5f);
            assertEquals(0.0f, readColor.get()[h], d);
            assertEquals(1.0f, context.colorEditSavedHue, d);
            ctx.itemDragWithDelta(ctx.getID("##picker/hue"), 0, -popup.size.y * 0.5f);
            assertEquals(0.0f, readColor.get()[h], d);
            assertEquals(0.0f, context.colorEditSavedHue, d);

            // Hue preservation during mouse input
            ctx.itemClick("##picker/hue");
            ctx.itemClick("##picker/sv");
            // The hue is defined
            assertNotEquals(0.0f, readColor.get()[h], d);
            colorStart = readColor.get();
            ctx.mouseDown(MouseButton.LEFT);

            // Move the mouse across all the edges
            final float startX = context.io.mousePosition.x;
            final float startY = context.io.mousePosition.y;
            final float left = startX - popup.size.x * 0.5f;
            final float top = startY - popup.size.y * 0.5f;
            final float right = left + popup.size.x;
            final float bottom = top + popup.size.y;
            ctx.mouseMoveToPos(left, top);
            ctx.mouseMoveToPos(right, top);
            ctx.mouseMoveToPos(right, bottom);
            ctx.mouseMoveToPos(left, bottom);
            ctx.mouseMoveToPos(left, top);
            ctx.mouseUp(MouseButton.LEFT);
            // The hue is unchanged during all the operations
            assertEquals(colorStart[h], context.colorEditSavedHue, eps, d);
        }
    }

    /** widgets_dragdrop_coloredit: drag and drop between color edits. */
    @Test
    void testDragDropColorEdit() {
        final float[] color1 = {1, 0, 0, 1};
        final float[] color2 = {0, 1, 0, 1};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 200);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.colorEdit4("ColorEdit1", color1, ColorEditFlags.NONE);
                    IkGui.colorEdit4("ColorEdit2", color2, ColorEditFlags.NONE);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        assertFalse(java.util.Arrays.equals(color1, color2));
        ctx.itemDragAndDrop("ColorEdit1/##ColorButton", "ColorEdit2/##X");
        assertArrayEquals(color1, color2);
    }

    /** widgets_dragdrop_source_null_id: beginDragDropSource() on an item without an ID. */
    @Test
    void testDragDropSourceNullID() {
        final Vector2f srcPos = new Vector2f();
        final Vector2f dstPos = new Vector2f();
        final boolean[] dropped = {false};
        ctx.setGui(
                () -> {
                    IkGui.begin("Null ID Test", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.textUnformatted("Null ID");
                    srcPos.set(IkGui.getItemRectMin()).add(IkGui.getItemRectMax()).mul(0.5f);

                    if (IkGui.beginDragDropSource(DragDropFlags.SOURCE_ALLOW_NULL_ID)) {
                        IkGui.setDragDropPayload("MAGIC", 0xF00);
                        IkGui.endDragDropSource();
                    }
                    IkGui.textUnformatted("Drop Here");
                    dstPos.set(IkGui.getItemRectMin()).add(IkGui.getItemRectMax()).mul(0.5f);

                    if (IkGui.beginDragDropTarget()) {
                        final Integer payload = IkGui.acceptDragDropPayload("MAGIC");
                        if (payload != null) {
                            dropped[0] = true;
                            ctx.checkEquals(0xF00, payload, "payload");
                        }
                        IkGui.endDragDropTarget();
                    }
                    IkGui.end();
                });
        // textUnformatted() has no ID, so we can't use itemDragAndDrop()
        ctx.mouseMoveToPos(srcPos.x, srcPos.y);
        ctx.sleep(0.5f);
        ctx.mouseDown(MouseButton.LEFT);
        ctx.mouseMoveToPos(dstPos.x, dstPos.y);
        ctx.sleep(0.5f);
        ctx.mouseUp(MouseButton.LEFT);
        assertTrue(dropped[0]);
    }

    /** widgets_dragdrop_hold_to_open: the active ID is kept while drag-hold opens tree nodes. */
    @Test
    void testDragDropHoldToOpen() {
        ctx.setGui(
                () -> {
                    ctx.showApp();
                    if (IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS)) {
                        IkGui.button("Drag");
                        if (IkGui.beginDragDropSource()) {
                            IkGui.setDragDropPayload("MAGIC", 0xF00);
                            IkGui.endDragDropSource();
                        }
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        final Vector2f viewportPos = IkGui.getMainViewport().position;
        ctx.windowMove(IkGuiTestContext.DEMO, viewportPos.x + 16, viewportPos.y + 16);
        ctx.windowResize(IkGuiTestContext.DEMO, 400, 800);
        ctx.windowMove("Test Window", 416, 16);

        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.itemCloseAll("");
        // So it's under the test window
        ctx.windowFocus("");

        ctx.setRef("Test Window");
        final int activeID = ctx.getID("Drag");
        ctx.mouseMove("Drag");
        ctx.sleep(0.5f);
        ctx.mouseDown(MouseButton.LEFT);
        ctx.yieldFrame();
        ctx.mouseLiftDragThreshold();
        assertEquals(activeID, g.activeID);

        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.mouseMoveNoFocus("Widgets");
        ctx.sleepNoSkip(1.0f, 1.0f / 60.0f);

        assertNotEquals(0, ctx.itemInfo("Widgets").id);
        assertTrue((ctx.itemInfo("Widgets").statusFlags & ItemStatusFlags.OPENED) != 0);
        assertEquals(activeID, g.activeID);
        ctx.mouseMoveNoFocus("Tree Nodes");
        ctx.sleepNoSkip(1.0f, 1.0f / 60.0f);
        assertTrue((ctx.itemInfo("Tree Nodes").statusFlags & ItemStatusFlags.OPENED) != 0);
        assertEquals(activeID, g.activeID);
        ctx.mouseUp(MouseButton.LEFT);
    }

    /**
     * widgets_dragdrop_overlapping_targets: the smaller of overlapping targets always wins, in
     * either submission order.
     */
    @Test
    void testDragDropOverlappingTargets() {
        final int[] droppedID = {0};
        final int[] count = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.button("Drag");
                    if (IkGui.beginDragDropSource()) {
                        IkGui.setDragDropPayload("_TEST_VALUE", 0xF00D);
                        IkGui.endDragDropSource();
                    }

                    final java.util.function.BiConsumer<String, float[]> renderButton =
                            (name, rect) -> {
                                IkGui.setCursorScreenPos(rect[0], rect[1]);
                                IkGui.button(name, rect[2], rect[3]);
                                if (IkGui.beginDragDropTarget()) {
                                    if (IkGui.acceptDragDropPayload("_TEST_VALUE") != null) {
                                        droppedID[0] = IkGui.getItemID();
                                        count[0]++;
                                    }
                                    IkGui.endDragDropTarget();
                                }
                            };

                    // Render the small button after the big one. The buttons are positioned so
                    // aiming at the center works.
                    final Vector2f pos = IkGui.getCursorScreenPos();
                    renderButton.accept("Big1", new float[] {pos.x, pos.y, 100, 100});
                    renderButton.accept("Small1", new float[] {pos.x + 70, pos.y + 70, 20, 20});

                    // Render the big button after the small one
                    renderButton.accept(
                            "Small2", new float[] {pos.x + 70, pos.y + 110 + 70, 20, 20});
                    renderButton.accept("Big2", new float[] {pos.x, pos.y + 110, 100, 100});
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        for (String target : new String[] {"Big1", "Small1", "Big2", "Small2"}) {
            droppedID[0] = 0;
            count[0] = 0;
            ctx.itemDragAndDrop("Drag", target);
            assertEquals(ctx.getID(target), droppedID[0], target);
            assertEquals(1, count[0], target);
            // Check again, there used to be a bug with overlapping targets
            ctx.yieldFrames(2);
            assertEquals(ctx.getID(target), droppedID[0], target);
            assertEquals(1, count[0], target);
        }
    }

    /** widgets_dragdrop_clear_payload: the payload is cleared right after delivery. */
    @Test
    void testDragDropClearPayload() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.button("Drag Me");
                    if (IkGui.beginDragDropSource()) {
                        IkGui.setDragDropPayload("_TEST_VALUE", (Object) null);
                        IkGui.endDragDropSource();
                    }

                    IkGui.button("Drop Here");
                    boolean justDropped = false;
                    if (IkGui.beginDragDropTarget()) {
                        final Payload payload =
                                IkGuiImplDragDrop.acceptDragDropPayloadInfo(
                                        "_TEST_VALUE", DragDropFlags.NONE);
                        if (payload != null && payload.delivery) {
                            justDropped = true;
                            ctx.check(IkGuiInternal.isDragDropActive(), "active while accepting");
                        }
                        IkGui.endDragDropTarget();
                        // The basic contract for upstream issue #5817
                        if (justDropped) {
                            ctx.check(!IkGuiInternal.isDragDropActive(), "inactive after drop");
                        }
                    }
                    IkGui.end();
                });
        ctx.itemDragAndDrop("//Test Window/Drag Me", "//Test Window/Drop Here");
    }

    /**
     * widgets_dragdrop_no_preview_tooltip: SOURCE_NO_PREVIEW_TOOLTIP sources and
     * ACCEPT_NO_PREVIEW_TOOLTIP targets.
     */
    @Test
    void testDragDropNoPreviewTooltip() {
        final boolean[] tooltipHasBeenVisible = {false};
        final int[] tooltipLastVisibleFrame = {-1};
        final IkInt acceptFlags = new IkInt(0);
        final java.util.function.IntConsumer createDragDropSource =
                flags -> {
                    if (IkGui.beginDragDropSource(flags)) {
                        IkGui.setDragDropPayload("_TEST_VALUE", 0xF00D);
                        IkGui.endDragDropSource();
                    }
                };
        // Upstream sets NoGuiWarmUp, so the UI isn't set until the test starts
        ctx.setGui(
                () -> {
                    final var g = ctx.context;
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.checkboxFlags(
                            "_AcceptNoPreviewTooltip",
                            acceptFlags,
                            DragDropFlags.ACCEPT_NO_PREVIEW_TOOLTIP);
                    IkGui.separator();

                    IkGui.button("Drag Src No Tooltip");
                    createDragDropSource.accept(DragDropFlags.SOURCE_NO_PREVIEW_TOOLTIP);

                    IkGui.button("Drag Extern");
                    if (IkGui.isItemClicked()) {
                        createDragDropSource.accept(
                                DragDropFlags.SOURCE_NO_PREVIEW_TOOLTIP
                                        | DragDropFlags.SOURCE_EXTERN);
                    }

                    IkGui.button("Drag Accept");
                    createDragDropSource.accept(0);

                    IkGui.button("Drop");
                    if (IkGui.beginDragDropTarget()) {
                        IkGui.acceptDragDropPayload("_TEST_VALUE", acceptFlags.get());
                        IkGui.endDragDropTarget();
                    }

                    final Window tooltip = g.tooltipPreviousWindow;
                    if (tooltip != null && (tooltip.active || tooltip.wasActive)) {
                        tooltipLastVisibleFrame[0] = g.frameCount;
                        tooltipHasBeenVisible[0] = true;
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        ctx.mouseMove("Drag Src No Tooltip");
        tooltipHasBeenVisible[0] = false;
        ctx.itemDragAndDrop("Drag Src No Tooltip", "Drop");
        assertFalse(tooltipHasBeenVisible[0]);
        tooltipHasBeenVisible[0] = false;
        ctx.itemDragAndDrop("Drag Extern", "Drop");
        assertFalse(tooltipHasBeenVisible[0]);
        tooltipHasBeenVisible[0] = false;
        ctx.yieldFrame();

        acceptFlags.set(0);
        ctx.itemDragOverAndHold("Drag Accept", "Drop");
        assertTrue(tooltipHasBeenVisible[0]);
        assertEquals(g.frameCount, tooltipLastVisibleFrame[0] + 1);
        tooltipHasBeenVisible[0] = false;

        acceptFlags.set(DragDropFlags.ACCEPT_NO_PREVIEW_TOOLTIP);
        ctx.itemDragOverAndHold("Drag Accept", "Drop");
        // A visible tooltip window gets hidden a frame later, because of the active or was active
        // check
        ctx.yieldFrame();
        assertTrue(tooltipHasBeenVisible[0]);
        assertTrue(tooltipLastVisibleFrame[0] + 1 < g.frameCount);
        ctx.mouseUp(MouseButton.LEFT);
    }

    /** widgets_dragdrop_scroll: the mouse wheel while dragging and dropping or dragging an item. */
    @Test
    void testDragDropScroll() {
        ctx.setGui(ctx::showApp);
        final Window windowDemo = ctx.getWindowByRef(IkGuiTestContext.DEMO);
        final Window windowHello = ctx.getWindowByRef("Hello, world!");
        ctx.windowFocus("Hello, world!");
        ctx.windowFocus(IkGuiTestContext.DEMO);
        final Vector2f viewportPos = IkGui.getMainViewport().position;
        ctx.windowMove("Hello, world!", viewportPos.x + 50.0f, viewportPos.y + 50.0f);
        ctx.windowMove(
                IkGuiTestContext.DEMO,
                windowHello.position.x + windowHello.size.x,
                windowHello.position.y);
        ctx.windowResize("Hello, world!", 400.0f, 200.0f);
        ctx.windowResize(IkGuiTestContext.DEMO, 400.0f, 600.0f);
        ctx.itemOpen(IkGuiTestContext.DEMO + "/Help");

        final String[] items = {
            // The drag and drop system
            "clear color/##ColorButton",
            // An active item. Upstream notes it's unspecified whether dragging while another item
            // is active should be allowed.
            "float"
        };
        for (int n = 0; n < items.length; ++n) {
            final String d = items[n];
            ctx.scrollToTop(IkGuiTestContext.DEMO);
            assertEquals(0.0f, windowDemo.scrollPosition.y, d);
            ctx.mouseMove("Hello, world!/" + items[n]);
            assertFalse(IkGuiInternal.isDragDropActive(), d);
            ctx.mouseDown(MouseButton.LEFT);
            ctx.mouseMoveToPos(
                    windowDemo.position.x + windowDemo.size.x * 0.5f,
                    windowDemo.position.y + windowDemo.size.y * 0.5f);
            if (n == 0) {
                assertTrue(IkGuiInternal.isDragDropActive(), d);
            }
            ctx.mouseWheel(0, -10);
            ctx.mouseUp(MouseButton.LEFT);
            assertTrue(windowDemo.scrollPosition.y > 0.0f, d);
        }
    }

    /** widgets_dragdrop_mouse_buttons: drag and drop with the left, right and middle buttons. */
    @Test
    void testDragDropMouseButtons() {
        final boolean[] pressed = {false};
        final boolean[] dropped = {false};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(0, 0, Condition.APPEARING);
                    if (IkGui.begin("Window", null, WindowFlags.NO_SAVED_SETTINGS)) {
                        pressed[0] |=
                                IkGuiImplButtons.buttonEx(
                                        "Button",
                                        0,
                                        0,
                                        ButtonFlags.MOUSE_BUTTON_LEFT
                                                | ButtonFlags.MOUSE_BUTTON_MIDDLE
                                                | ButtonFlags.MOUSE_BUTTON_RIGHT);
                        if (IkGui.beginDragDropSource()) {
                            for (MouseButton button : MouseButton.values()) {
                                if (button.index >= 0 && IkGui.isMouseDown(button)) {
                                    IkGui.text("Dragged by button " + button.index);
                                }
                            }
                            IkGui.setDragDropPayload("Button", "Works");
                            IkGui.endDragDropSource();
                        }

                        IkGui.button("Drop Here");
                        if (IkGui.beginDragDropTarget()) {
                            final String payload = IkGui.acceptDragDropPayload("Button");
                            if (payload != null) {
                                dropped[0] = "Works".equals(payload);
                            }
                            IkGui.endDragDropTarget();
                        }
                    }
                    IkGui.end();
                });
        ctx.setRef("Window");
        final MouseButton[] buttons = {MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE};

        // Clicking still works
        for (MouseButton button : buttons) {
            pressed[0] = false;
            ctx.itemClick("Button", button);
            assertTrue(pressed[0], button.name());
        }

        // Drag and drop works with all the mouse buttons. Upstream only supports the left, right
        // and middle mouse buttons for this.
        for (MouseButton button : buttons) {
            dropped[0] = false;
            ctx.itemDragAndDrop("Button", "Drop Here", button);
            assertTrue(dropped[0], button.name());
        }
    }

    /**
     * widgets_dragdrop_new_payloads: getDragDropPayloadInfo() returns the payload once it is set.
     */
    @Test
    void testDragDropNewPayloads() {
        final int[] count = {0};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(0, 0, Condition.APPEARING);
                    if (IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS)) {
                        IkGui.button("DragSrc");
                        if (IkGui.beginDragDropSource()) {
                            final Payload payload = IkGui.getDragDropPayloadInfo();
                            if (count[0] <= 1) {
                                ctx.check(payload == null, "no payload yet " + count[0]);
                            } else {
                                ctx.check(payload != null, "payload " + count[0]);
                            }
                            if (count[0] >= 1) {
                                IkGui.setDragDropPayload("Button", "Data");
                            }
                            count[0]++;
                            IkGui.endDragDropSource();
                        }
                        IkGui.button("DragDst");
                    }
                    IkGui.end();
                });
        ctx.itemDragOverAndHold("//Test Window/DragSrc", "//Test Window/DragDst");
        // Make sure enough was tested
        assertTrue(count[0] > 3);
    }

    /** widgets_combo_context_menu: the default context menu along with a combo. */
    @Test
    void testComboContextMenu() {
        final boolean[] comboOpen = {false};
        final boolean[] contextOpen = {false};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    if (IkGui.beginCombo("Combo", "Preview")) {
                        comboOpen[0] = true;
                        IkGui.selectable("Close Combo");
                        IkGui.endCombo();
                    }
                    if (IkGui.beginPopupContextItem()) {
                        if (IkGui.button("Close Context Menu")) {
                            IkGui.closeCurrentPopup();
                        }
                        contextOpen[0] = true;
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        // The context menu
        ctx.mouseMove("Combo");
        ctx.mouseClick(MouseButton.RIGHT);
        assertTrue(!comboOpen[0] && contextOpen[0]);
        ctx.itemClick("//$FOCUSED/Close Context Menu");
        comboOpen[0] = false;
        contextOpen[0] = false;

        // The combo contents
        ctx.itemClick("Combo");
        assertTrue(comboOpen[0] && !contextOpen[0]);
        ctx.itemClick("//$FOCUSED/Close Combo");
        comboOpen[0] = false;
        contextOpen[0] = false;

        // The context menu with Shift+F10
        ctx.navMoveTo("Combo");
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.F10));
        assertTrue(!comboOpen[0] && contextOpen[0]);
        ctx.itemClick("//$FOCUSED/Close Context Menu");
    }

    /** widgets_combo_custom_preview: beginComboPreview() and the layout afterward. */
    @Test
    void testComboCustomPreview() {
        final String[] items = {"AAAA", "BBBB", "CCCC", "DDDD", "EEEE"};
        final int[] step = {0};
        ctx.setGui(
                () -> {
                    final var g = ctx.context;
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("Custom preview:");

                    if (step[0] == 2) {
                        IkGui.setNextItemWidth(IkGui.getFrameHeight() * 2.0f);
                    }

                    final float squareSize = IkGuiInternal.getFontSize();
                    final float[] color = {1.0f, 0.5f, 0.5f, 1.0f};
                    if (IkGui.beginCombo("custom", "")) {
                        for (int n = 0; n < 5; ++n) {
                            IkGui.pushID(n);
                            IkGui.colorButton(
                                    "##color",
                                    color,
                                    ColorEditFlags.NO_TOOLTIP | ColorEditFlags.NO_DRAG_DROP,
                                    squareSize,
                                    squareSize);
                            IkGui.sameLine();
                            IkGui.selectable(items[n]);
                            IkGui.popID();
                        }
                        IkGui.endCombo();
                    }
                    final RectFloat comboRect = new RectFloat(g.lastItemData.rect);
                    final Window window = g.windowCurrent;

                    if (IkGui.beginComboPreview()) {
                        IkGui.colorButton(
                                "##color",
                                color,
                                ColorEditFlags.NO_TOOLTIP | ColorEditFlags.NO_DRAG_DROP,
                                squareSize,
                                squareSize);
                        IkGui.textUnformatted(items[0]);
                        IkGui.endComboPreview();
                    }

                    // The sameLine() restore behavior
                    IkGui.sameLine();
                    ctx.checkEquals(comboRect.getTop(), window.cursorPosition.y, "cursor y");
                    ctx.checkEquals(
                            g.style.variable.framePadding.y,
                            window.baseOffsetCurrentLine,
                            "text base offset");
                    IkGui.text("HELLO");
                    // Upstream also checks draw command merging, which doesn't apply to our draw
                    // list, since it records a command per shape
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        step[0] = 0;
        ctx.windowResize("", 400, 400);
        step[0] = 1;
        ctx.yieldFrames(2);
        step[0] = 2;
        ctx.yieldFrames(2);
    }

    /** widgets_text_null: empty text in textUnformatted() and addText(). */
    @Test
    void testTextNull() {
        ctx.setGui(
                () -> {
                    final String str = "hello world";
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    {
                        final Vector2f p0 = IkGui.getCursorPos();
                        IkGui.textUnformatted(str.substring(0, 5));
                        // Upstream passes an empty range and a null string here
                        IkGui.textUnformatted(str.substring(1, 1));
                        IkGui.textUnformatted("");
                        final Vector2f p1 = IkGui.getCursorPos();
                        ctx.checkEquals(
                                p0.y + IkGui.getTextLineHeightWithSpacing() * 3, p1.y, "height");
                    }
                    {
                        final Vector2f p0 = IkGui.getCursorScreenPos();
                        IkGui.textUnformatted("");
                        IkGui.sameLine(0.0f, 0.0f);
                        final Vector2f p1 = IkGui.getCursorScreenPos();
                        ctx.checkEquals(p0, p1, "empty text width");
                    }
                    {
                        final Vector2f p0 = IkGui.getCursorScreenPos();
                        IkGui.getWindowDrawList()
                                .addText(
                                        IkGui.getFontSize(),
                                        p0.x,
                                        p0.y,
                                        IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT),
                                        str.substring(1, 1));
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** widgets_text_long: the demo's long text example. */
    @Test
    void testTextLong() {
        ctx.setGui(ctx::showApp);
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuClick("Examples/Long text display");
        ctx.setRef("Example: Long text display");
        ctx.itemClick("Add 1000 lines");
        ctx.sleep(0.5f);

        final Window logPanel = ctx.windowInfo("Log");
        assertNotNull(logPanel);
        ctx.scrollToY(logPanel, logPanel.scrollMax.y);
        ctx.sleep(0.5f);
        ctx.itemClick("Clear");
        ctx.comboClick("Test type/Single call to textUnformatted()");
        ctx.comboClick("Test type/Multiple calls to text(), clipped");
        ctx.comboClick("Test type/Multiple calls to text(), not clipped (slow)");
        ctx.windowClose("");
    }

    /** widgets_text_wrapped: wrapped text in a small, scrolling window. */
    @Test
    void testTextWrapped() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.NO_TITLE_BAR);
                    for (int n = 0; n < 6; ++n) {
                        IkGui.text("Dummy " + n);
                    }
                    // Upstream also checks that the vertices fit the item rect when clipped, which
                    // depends on its vertex buffer
                    IkGui.textWrapped(
                            "This is a long wrapped text hello world hello world this is some long"
                                    + " text");
                    for (int n = 0; n < 6; ++n) {
                        IkGui.text("Dummy " + n);
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.windowResize(
                "", IkGuiInternal.getFontSize() * 15, IkGui.getTextLineHeightWithSpacing() * 8);
        ctx.scrollToTop("");
        ctx.scrollToBottom("");
    }

    /** widgets_text_wrapped_2: text wrapping with consecutive separators and a leading newline. */
    @Test
    void testTextWrapped2() {
        final String s1 = "abcde..";
        final String s2 = "abcde.. That";
        final float[] wrapWidth = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.NO_TITLE_BAR);
                    final float localOffsetX = IkGui.getCursorPos().x;
                    wrapWidth[0] = IkGui.calcTextSize(s1).x;

                    // Visualize the output, no actual test
                    IkGui.pushTextWrapPos(wrapWidth[0] + localOffsetX);
                    IkGui.textUnformatted(s1);
                    IkGui.spacing();
                    IkGui.textUnformatted(s2);
                    IkGui.popTextWrapPos();

                    // Text wrapping with a leading newline
                    IkGui.separator();
                    IkGui.textWrapped("\nHello");
                    // On its first frame the window is hidden while it auto-fits, and too narrow
                    // for "Hello", which word wrapping then cuts. Upstream's warm-up frames cover
                    // this.
                    if (!IkGuiInternal.context.windowCurrent.hidden) {
                        ctx.checkEquals(
                                new Vector2f(
                                        IkGui.calcTextSize("Hello").x,
                                        IkGuiInternal.getFontSize() * 2),
                                IkGui.getItemRectSize(),
                                "leading newline size");
                    }
                    IkGui.end();

                    // The text fits exactly, so it isn't wrapped
                    ctx.checkEquals(s1, IkGuiInternal.wrapText(s1, wrapWidth[0]), "no wrap");
                });
        ctx.yieldFrames(2);
    }

    /** widgets_label_text: labelText() layout with multiple lines. */
    @Test
    void testLabelText() {
        ctx.setGui(
                () -> {
                    final var g = ctx.context;
                    IkGui.setNextWindowSize(500, 500, Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);

                    final float multiLineHeight =
                            IkGui.getTextLineHeight() * 3.0f
                                    + g.style.variable.framePadding.y * 2.0f;
                    IkGui.separator();
                    IkGui.labelText("Single line label", "Single line text");
                    ctx.checkEquals(
                            IkGui.getFrameHeight(), g.lastItemData.rect.getHeight(), "single");

                    IkGui.separator();
                    IkGui.labelText("Multi\n line\n label", "Single line text");
                    ctx.checkEquals(
                            multiLineHeight, g.lastItemData.rect.getHeight(), "multi-line label");

                    IkGui.separator();
                    IkGui.labelText("Single line label", "Multi\n line\n text");
                    ctx.checkEquals(
                            multiLineHeight, g.lastItemData.rect.getHeight(), "multi-line text");
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** widgets_menu_hover: hovering across levels of a menu. */
    @Test
    void testMenuHover() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("open")) {
                        IkGui.openPopup("Popup");
                    }
                    if (IkGui.beginPopup("Popup")) {
                        IkGui.menuItem("AAA");
                        IkGui.menuItem("BBB");
                        if (IkGui.beginMenu("CCC")) {
                            IkGui.menuItem("CCC.2");
                            IkGui.endMenu();
                        }
                        if (IkGui.beginMenu("DDD")) {
                            IkGui.menuItem("DDD.2");
                            IkGui.endMenu();
                        }
                        // Not a menu item, because those have custom behavior
                        IkGui.button("EEE");
                        IkGui.menuItem("FFF");
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        ctx.itemClick("open");
        ctx.setRef("//$FOCUSED");
        ctx.mouseMoveNoFocus("BBB");
        ctx.mouseMoveNoFocus("CCC");
        assertNotNull(g.navFocusedWindow);
        assertTrue((g.navFocusedWindow.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0);
        ctx.mouseMoveNoFocus("DDD");
        assertEquals(ctx.getID("DDD"), g.hoveredIDPreviousFrame);
        assertTrue((g.navFocusedWindow.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0);
        ctx.mouseMoveNoFocus("EEE");
        assertEquals(ctx.getID("EEE"), g.hoveredIDPreviousFrame);
        ctx.mouseMoveNoFocus("FFF");
        assertEquals(ctx.getID("FFF"), g.hoveredIDPreviousFrame);
        ctx.yieldFrame();
        assertTrue((g.navFocusedWindow.flags & WindowFlags.INTERNAL_CHILD_MENU) == 0);
    }

    /** widgets_menu_reopen: clicking an already open menu doesn't make it flicker. */
    @Test
    void testMenuReopen() {
        final boolean[] menuIsVisible = {false};
        final boolean[] menuWasOnceNotVisible = {false};
        final boolean[] submenuIsVisible = {false};
        final boolean[] submenuWasOnceNotVisible = {false};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    if (IkGui.beginMenuBar()) {
                        menuIsVisible[0] = false;
                        submenuIsVisible[0] = false;
                        if (IkGui.beginMenu("Menu")) {
                            menuIsVisible[0] = true;
                            IkGui.menuItem("Menu.item");
                            if (IkGui.beginMenu("Submenu")) {
                                submenuIsVisible[0] = true;
                                IkGui.menuItem("Submenu.item");
                                IkGui.endMenu();
                            } else {
                                submenuWasOnceNotVisible[0] = true;
                            }
                            IkGui.endMenu();
                        } else {
                            menuWasOnceNotVisible[0] = true;
                            submenuWasOnceNotVisible[0] = true;
                        }
                        IkGui.endMenuBar();
                    }
                    IkGui.end();
                });
        // Lower level than menuClick(), to make sure focus isn't changed
        ctx.setRef("Test Window");
        ctx.itemClickNoFocus("##MenuBar/Menu");
        assertTrue(menuIsVisible[0]);
        menuWasOnceNotVisible[0] = false;
        ctx.itemClickNoFocus("##MenuBar/Menu");
        assertFalse(menuIsVisible[0]);
        assertTrue(menuWasOnceNotVisible[0]);
        ctx.itemClickNoFocus("##MenuBar/Menu");
        assertTrue(menuIsVisible[0]);

        ctx.setRef("//$FOCUSED");
        menuWasOnceNotVisible[0] = false;
        ctx.itemClickNoFocus("Submenu");
        assertTrue(menuIsVisible[0]);
        assertTrue(submenuIsVisible[0]);
        assertFalse(menuWasOnceNotVisible[0]);
        submenuWasOnceNotVisible[0] = false;

        ctx.itemClickNoFocus("Submenu");
        assertTrue(menuIsVisible[0]);
        assertTrue(submenuIsVisible[0]);
        assertFalse(menuWasOnceNotVisible[0]);
        assertFalse(submenuWasOnceNotVisible[0]);
    }

    /** widgets_menu_reopen_2: menus reopen while moving up or down. */
    @Test
    void testMenuReopen2() {
        ctx.setGui(
                () -> {
                    if (IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS)) {
                        if (IkGui.beginMenu("Nested1")) {
                            if (IkGui.beginMenu("Nested2")) {
                                for (int i = 0; i < 10; ++i) {
                                    if (IkGui.beginMenu(Integer.toString(i))) {
                                        IkGui.text("Nothing here");
                                        IkGui.endMenu();
                                    }
                                }
                                IkGui.endMenu();
                            }
                            IkGui.endMenu();
                        }
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        ctx.mouseMove("Nested1");
        assertEquals(1, g.openPopupStack.size());
        ctx.mouseMove("//$FOCUSED/Nested2");
        assertEquals(2, g.openPopupStack.size());
        ctx.mouseMove("//$FOCUSED/0");

        ctx.setRef("//###Menu_01");

        // Same
        ctx.mouseMove("0");
        assertEquals(3, g.openPopupStack.size());

        // Move down
        ctx.mouseMove("1");
        assertEquals(3, g.openPopupStack.size());
        ctx.mouseMove("2");
        assertEquals(3, g.openPopupStack.size());

        // Move up. Upstream had a bug where closing a menu focused the wrong window.
        ctx.mouseMove("1");
        assertEquals(3, g.openPopupStack.size());

        // Move down instantly, to open menus on successive frames
        final java.util.function.Consumer<String> teleport =
                ref -> {
                    final RectFloat rect = ctx.itemInfo(ref).rectClipped;
                    ctx.mouseTeleportToPos(rect.getCenterX(), rect.getCenterY(), false);
                };
        teleport.accept("3");
        ctx.yieldFrame();
        assertEquals(3, g.openPopupStack.size());
        teleport.accept("5");
        ctx.yieldFrame();
        assertEquals(3, g.openPopupStack.size());
        ctx.yieldFrame();
        assertEquals(3, g.openPopupStack.size());
        teleport.accept("4");
        ctx.yieldFrame();
        assertEquals(3, g.openPopupStack.size());
        teleport.accept("3");
        ctx.yieldFrame();
        assertEquals(3, g.openPopupStack.size());
        ctx.yieldFrame();
        assertEquals(3, g.openPopupStack.size());
    }

    /** widgets_menu_in_popup: menus in a regular window, without a menu bar. */
    @Test
    void testMenuInPopup() {
        ctx.setGui(
                () -> {
                    if (IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS)) {
                        if (IkGui.beginMenu("Menu1")) {
                            IkGui.menuItem("MenuItem1");
                            IkGui.menuItem("MenuItem2");
                            IkGui.endMenu();
                        }
                    }
                    IkGui.end();
                });
        ctx.setRef("//Test Window");
        ctx.menuClick("Menu1/MenuItem1");
        ctx.menuClick("Menu1/MenuItem2");
    }

    /** widgets_menu_mouse_hold: navigating menus with the mouse button held down. */
    @Test
    void testMenuMouseHold() {
        final int[] queryBaseID = {0};
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        final Runnable createMenu =
                () -> {
                    final int baseID = IkGui.getID("");
                    if (queryBaseID[0] == baseID) {
                        status.clear();
                    }
                    if (IkGui.beginMenu("AAA")) {
                        if (IkGui.beginMenu("BBB")) {
                            final boolean clicked = IkGui.menuItem("CCC");
                            if (queryBaseID[0] == baseID) {
                                status.querySet(false);
                                // isItemClicked() fails when the mouse is dragged while pressed
                                if (!IkGui.isItemClicked()) {
                                    status.clicked += clicked ? 1 : 0;
                                }
                            }
                            IkGui.endMenu();
                        }
                        IkGui.menuItem("Item");
                        IkGui.endMenu();
                    }
                };
        final Runnable createMenuBar =
                () -> {
                    IkGui.beginMenuBar();
                    createMenu.run();
                    IkGui.endMenuBar();
                };
        ctx.setGui(
                () -> {
                    // A window with a menu bar
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    createMenuBar.run();

                    // A popup with a menu bar
                    if (IkGui.button("Open Popup")) {
                        IkGui.openPopup("Popup");
                    }
                    if (IkGui.beginPopup("Popup", WindowFlags.MENU_BAR)) {
                        createMenuBar.run();
                        IkGui.endPopup();
                    }

                    // A modal with a menu bar
                    if (IkGui.button("Open Modal")) {
                        IkGui.openPopup("Modal");
                    }
                    if (IkGui.beginPopupModal("Modal", null, WindowFlags.MENU_BAR)) {
                        createMenuBar.run();
                        if (IkGui.isKeyPressed(Key.ESCAPE)) {
                            IkGui.closeCurrentPopup();
                        }
                        IkGui.endPopup();
                    }

                    // A popup with a menu
                    if (IkGui.button("Open Menu")) {
                        IkGui.openPopup("Menu");
                    }
                    if (IkGui.beginPopup("Menu")) {
                        createMenu.run();
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        final String[][] testData = {
            // From a window menu bar
            {null, "##MenuBar"},
            // From a popup menu bar
            {"Open Popup", "##MenuBar"},
            // From a modal menu bar
            {"Open Modal", "##MenuBar"},
            // From a popup menu
            {"Open Menu", ""},
        };
        for (int variant = 0; variant < testData.length; ++variant) {
            final String d = "variant " + variant;
            final String openButton = testData[variant][0];
            final String menuBase = testData[variant][1];
            if (openButton != null) {
                ctx.itemClick("//Test Window/" + openButton);
            } else {
                ctx.windowFocus("//Test Window");
            }
            ctx.setRef("//$FOCUSED");
            queryBaseID[0] = ctx.getID(menuBase);
            if (!menuBase.isEmpty()) {
                ctx.mouseMove(menuBase + "/AAA");
            } else {
                ctx.mouseMoveNoFocus("AAA");
            }
            ctx.mouseDown(MouseButton.LEFT);
            // Upstream notes that pressing a menu whose menu is already open makes it flicker,
            // so it waits a frame for the menu to come back
            ctx.yieldFrame();
            ctx.menuHover("AAA/BBB/CCC");
            assertEquals(0, ctx.context.activeID, d);
            assertEquals(1, status.hovered, d);
            ctx.mouseUp(MouseButton.LEFT);
            assertEquals(1, status.clicked, d);
        }
    }

    /** widgets_menu_menusets: menus in different menu sets don't open each other. */
    @Test
    void testMenuMenuSets() {
        ctx.setGui(ctx::showApp);
        final var g = ctx.context;
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.windowResize("", 350, 600);
        ctx.itemOpen("Popups & Modal windows");
        ctx.itemOpen("Menus inside a regular window");
        final Window demoWindow = ctx.getWindowByRef("");
        // Don't hover any menus
        ctx.mouseMoveToPos(demoWindow.position.x, demoWindow.position.y);

        // 1. Open a menu and hover a menu that doesn't belong to the open menu set
        ctx.menuClick("Examples");
        assertEquals(1, g.openPopupStack.size());
        final int popupParentID = g.openPopupStack.getLast().openParentID;
        ctx.mouseMoveNoFocus("Menus inside a regular window/Menu inside a regular window");
        assertEquals(1, g.openPopupStack.size());
        assertEquals(popupParentID, g.openPopupStack.getLast().openParentID);

        // 2. Hover a menu without another open menu set
        ctx.mouseMoveToPos(demoWindow.position.x, demoWindow.position.y);
        ctx.popupCloseAll();
        ctx.mouseMove("Menus inside a regular window/Menu inside a regular window");
        assertEquals(1, g.openPopupStack.size());
        assertNotEquals(popupParentID, g.openPopupStack.getLast().openParentID);

        // 3. The same as 1, in reverse order
        ctx.mouseMoveNoFocus("##MenuBar/Examples");
        assertEquals(0, g.openPopupStack.size());

        // 4. Clicking another menu set should finally open it
        ctx.menuClick("Examples");
        assertEquals(1, g.openPopupStack.size());
        assertEquals(popupParentID, g.openPopupStack.getLast().openParentID);

        // The menu doesn't close while activating an item and dragging back over the parent menu
        ctx.itemClose("Popups & Modal windows");
        ctx.menuClick("Menu");
        ctx.mouseMove("//$FOCUSED/New");
        final Vector2f pos = new Vector2f(g.io.mousePosition);
        // Click the slider
        ctx.menuClick("Menu/Options/Value");
        ctx.mouseDown(MouseButton.LEFT);
        ctx.yieldFrame();
        assertEquals(2, g.openPopupStack.size());
        assertEquals(ctx.getID("//$FOCUSED/Value"), g.activeID);
        // Move back to the "New" item
        ctx.mouseMoveToPos(pos.x, pos.y);
        assertEquals(2, g.openPopupStack.size());
        assertEquals(ctx.getID("//$FOCUSED/Value"), g.activeID);
        ctx.mouseUp(MouseButton.LEFT);
    }

    /** widgets_menu_append: appending to menus with a second beginMenu(). */
    @Test
    void testMenuAppend() {
        final boolean[] clicked = {false};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Append Menus",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.ALWAYS_AUTO_RESIZE
                                    | WindowFlags.MENU_BAR);
                    IkGui.beginMenuBar();

                    // The menu that we will append to
                    if (IkGui.beginMenu("First Menu")) {
                        IkGui.menuItem("1 First");
                        if (IkGui.beginMenu("Second Menu")) {
                            IkGui.menuItem("2 First");
                            IkGui.endMenu();
                        }
                        IkGui.endMenu();
                    }

                    // Append to the first menu
                    if (IkGui.beginMenu("First Menu")) {
                        if (IkGui.menuItem("1 Second")) {
                            clicked[0] = true;
                        }
                        if (IkGui.beginMenu("Second Menu")) {
                            IkGui.menuItem("2 Second");
                            // To test left/right movement inside
                            IkGui.button("AAA");
                            IkGui.sameLine();
                            IkGui.button("BBB");
                            IkGui.endMenu();
                        }
                        IkGui.endMenu();
                    }
                    IkGui.endMenuBar();
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Append Menus");
        ctx.menuClick("First Menu");
        ctx.menuClick("First Menu/1 First");
        assertFalse(clicked[0]);
        ctx.menuClick("First Menu/1 Second");
        assertTrue(clicked[0]);
        ctx.menuClick("First Menu/Second Menu/2 First");
        ctx.menuClick("First Menu/Second Menu/2 Second");

        // Left/right navigation works in an appended menu
        ctx.menuClick("First Menu/Second Menu/AAA");
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("//$FOCUSED/AAA"), g.navID);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("//$FOCUSED/BBB"), g.navID);
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(ctx.getID("//$FOCUSED/AAA"), g.navID);
    }

    /** widgets_menu_path: the ### operator in menu paths. */
    @Test
    void testMenuPath() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.ALWAYS_AUTO_RESIZE
                                    | WindowFlags.MENU_BAR);
                    if (IkGui.beginMenuBar()) {
                        if (IkGui.beginMenu("First")) {
                            if (IkGui.beginMenu("Second")) {
                                IkGui.menuItem("Item");
                                IkGui.endMenu();
                            }
                            if (IkGui.beginMenu("Third###Hello")) {
                                IkGui.menuItem("Item");
                                IkGui.endMenu();
                            }
                            IkGui.endMenu();
                        }
                        if (IkGui.beginMenu("File###Menu_File")) {
                            IkGui.menuItem("New###Menu_FileNew");
                            IkGui.endMenu();
                        }
                        IkGui.endMenuBar();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.menuClick("First/Second/Item");
        ctx.menuClick("First/Third###Hello/Item");

        ctx.menuClick("First/Second");
        ctx.itemClick("**/Item");

        ctx.menuClick("###Menu_File");
        ctx.menuClick("###Menu_File/###Menu_FileNew");
    }

    /** widgets_menu_separator: logging the text of a separator in a menu bar. */
    @Test
    void testMenuSeparator() {
        final String[] logged = {null};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    if (IkGui.beginMenuBar()) {
                        IkGuiImplLogging.logToBuffer(-1);
                        if (IkGui.beginMenu("File")) {
                            IkGui.endMenu();
                        }
                        IkGui.separator();
                        ctx.checkEquals(
                                IkGui.getFrameHeight(),
                                IkGui.getItemRectMax().y - IkGui.getItemRectMin().y,
                                "separator height");
                        if (IkGui.beginMenu("Edit")) {
                            IkGui.endMenu();
                        }
                        IkGui.endMenuBar();
                        logged[0] = ctx.context.logBuffer.toString();
                        IkGui.logFinish();
                    }
                    IkGui.end();
                });
        assertEquals("[ File ] | [ Edit ]", logged[0]);
    }

    /** widgets_menu_mainmenubar_append: appending to the main menu bar. */
    @Test
    void testMenuMainMenuBarAppend() {
        final boolean[] clicked = {false};
        ctx.setGui(
                () -> {
                    // The menu bar that we will append to
                    if (IkGui.beginMainMenuBar()) {
                        if (IkGui.beginMenu("First Menu")) {
                            IkGui.endMenu();
                        }
                        IkGui.endMainMenuBar();
                    }

                    // Append to it
                    if (IkGui.beginMainMenuBar()) {
                        if (IkGui.beginMenu("Second Menu")) {
                            if (IkGui.menuItem("Second")) {
                                clicked[0] = true;
                            }
                            IkGui.endMenu();
                        }
                        IkGui.endMainMenuBar();
                    }
                });
        ctx.setRef("##MainMenuBar");
        ctx.menuClick("Second Menu/Second");
        assertTrue(clicked[0]);
    }

    /** widgets_menu_mainmenubar_navigation: keyboard navigation in the main menu bar. */
    @Test
    void testMenuMainMenuBarNavigation() {
        ctx.setGui(
                () -> {
                    if (IkGui.beginMainMenuBar()) {
                        if (IkGui.beginMenu("Menu 1")) {
                            if (IkGui.beginMenu("Sub Menu 1")) {
                                IkGui.menuItem("Item 1");
                                IkGui.endMenu();
                            }
                            IkGui.endMenu();
                        }
                        if (IkGui.beginMenu("Menu 2")) {
                            if (IkGui.beginMenu("Sub Menu 2-1")) {
                                IkGui.menuItem("Item 2");
                                IkGui.endMenu();
                            }
                            if (IkGui.beginMenu("Sub Menu 2-2")) {
                                IkGui.menuItem("Item 3");
                                IkGui.endMenu();
                            }
                            IkGui.endMenu();
                        }
                        IkGui.endMainMenuBar();
                    }
                });
        final var g = ctx.context;
        // Upstream writes "##MainMenuBar##MenuBar/...", which relies on its hash being
        // incremental, so the levels are written as separate path segments here
        final String menuBar = "//##MainMenuBar/##MenuBar/";
        ctx.setRef("##MainMenuBar");
        ctx.menuClick("Menu 1");

        // The click doesn't affect the nav ID, which is 0 at this point

        // Basic keyboard navigation
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("//$FOCUSED/Sub Menu 1"), g.navID);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("//$FOCUSED/Item 1"), g.navID);
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(ctx.getID("//$FOCUSED/Sub Menu 1"), g.navID);
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(ctx.getID(menuBar + "Menu 1"), g.navID);

        // Upstream does this as a workaround so its test engine finds "Menu 1" again
        ctx.mouseMove(menuBar + "Menu 2");

        // Nav events forward to the parent when there's no match in the current menu, going from
        // "Menu 1/Sub Menu 1/Item 1" to "Menu 2"
        ctx.itemClick(menuBar + "Menu 1");
        ctx.keyPress(Key.ARROW_DOWN);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("//$FOCUSED/Item 1"), g.navID);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID(menuBar + "Menu 2"), g.navID);

        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("//$FOCUSED/Sub Menu 2-1"), g.navID);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("//$FOCUSED/Item 2"), g.navID);

        ctx.keyPress(Key.ARROW_LEFT);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("//$FOCUSED/Sub Menu 2-2"), g.navID);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("//$FOCUSED/Item 3"), g.navID);
    }

    /** widgets_menu_mainmenubar_release_focus: the main menu bar releases focus. */
    @Test
    void testMenuMainMenuBarReleaseFocus() {
        final IkString str = new IkString(256);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("OK");
                    IkGui.end();
                    if (IkGui.beginMainMenuBar()) {
                        if (IkGui.beginMenu("File")) {
                            IkGui.menuItem("Hedhehog");
                            IkGui.endMenu();
                        }
                        IkGui.button("BUTTON");
                        IkGui.setNextItemWidth(100);
                        IkGui.inputText("InputText1", str);
                        if (IkGui.beginTable("Table", 1)) {
                            IkGui.tableNextColumn();
                            IkGui.setNextItemWidth(100);
                            IkGui.inputText("InputText2", str);
                            IkGui.endTable();
                        }
                        IkGui.endMainMenuBar();
                    }
                });
        final var g = ctx.context;
        final Window testWindow = ctx.getWindowByRef("//Test Window");
        ctx.itemClick("//Test Window/OK");
        assertSame(testWindow, g.navFocusedWindow);

        // Upstream notes this isn't well specified, but moving from the menu layer to the main
        // layer currently happens when focusing a window, which closing a menu does, but clicking
        // a button doesn't. That's why Escape is pressed.
        ctx.setRef("##MainMenuBar");
        ctx.itemClick("##MenuBar/BUTTON");
        assertEquals(ctx.getID("##MenuBar/BUTTON"), g.navID);
        assertEquals(IkGuiImplNav.NAV_LAYER_MENU, g.navLayer);
        assertNotSame(testWindow, g.navFocusedWindow);

        // Leave the menu layer
        ctx.keyPress(Key.ESCAPE);
        assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer);
        assertSame(testWindow, g.navFocusedWindow);

        ctx.menuClick("InputText1");
        assertEquals(IkGuiImplNav.NAV_LAYER_MENU, g.navLayer);
        // Deactivate the text input
        ctx.keyPress(Key.ESCAPE);
        // Leave the menu layer
        ctx.keyPress(Key.ESCAPE);
        ctx.yieldFrame();
        assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer);
        assertSame(testWindow, g.navFocusedWindow);

        ctx.itemClick("##MenuBar/Table/InputText2");
        assertEquals(IkGuiImplNav.NAV_LAYER_MENU, g.navLayer);
        ctx.keyPress(Key.ESCAPE);
        ctx.keyPress(Key.ESCAPE);
        ctx.yieldFrame();
        assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer);
        assertSame(testWindow, g.navFocusedWindow);
    }

    /** A basic selection, with helpers to submit selectable items. */
    private static class ExampleSelection extends SelectionBasicStorage {
        void emitBasicItems(int itemsCount, String labelFormat) {
            for (int n = 0; n < itemsCount; ++n) {
                final boolean selected = contains(getStorageIDFromIndex(n));
                IkGui.setNextItemSelectionUserData(n);
                IkGui.selectable(String.format(labelFormat, n), selected);
            }
        }

        void emitBasicLoop(int multiSelectFlags, int itemsCount, String labelFormat) {
            applyRequests(IkGui.beginMultiSelect(multiSelectFlags, getSize(), itemsCount));
            emitBasicItems(itemsCount, labelFormat);
            applyRequests(IkGui.endMultiSelect());
        }
    }

    /**
     * The shared body of widgets_multiselect_1_selectables and widgets_multiselect_2_treenode.
     *
     * @param treeNodes True to use tree nodes, false to use selectables.
     */
    private void multiSelectBasics(boolean treeNodes) {
        final ExampleSelection selection = new ExampleSelection();
        final int[] multiSelectFlags = {MultiSelectFlags.NONE};
        final int[] windowFlags = {WindowFlags.ALWAYS_AUTO_RESIZE};
        final int itemsCount = 100;
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowPos(
                            IkGui.getMainViewport().position.x,
                            IkGui.getMainViewport().position.y,
                            Condition.APPEARING);
                    IkGui.begin(
                            "Test Window", null, WindowFlags.NO_SAVED_SETTINGS | windowFlags[0]);
                    IkGui.text(String.format("(Size = %3d items)", selection.getSize()));
                    IkGui.separator();

                    MultiSelectIO io =
                            IkGui.beginMultiSelect(
                                    multiSelectFlags[0], selection.getSize(), itemsCount);
                    selection.applyRequests(io);

                    if (treeNodes) {
                        IkGui.pushStyleVarFloat2(
                                StyleVariable.ITEM_SPACING,
                                ctx.context.style.variable.itemSpacing.x,
                                0.0f);
                    }

                    final ListClipper clipper = new ListClipper();
                    clipper.begin(itemsCount);
                    if (io.rangeSourceItem > 0) {
                        clipper.includeItemByIndex((int) io.rangeSourceItem);
                    }
                    while (clipper.step()) {
                        for (int n = clipper.displayStart; n < clipper.displayEnd; ++n) {
                            final String label = String.format("Object %04d", n);
                            final boolean selected = selection.contains(n);
                            IkGui.setNextItemSelectionUserData(n);
                            if (!treeNodes) {
                                IkGui.selectable(label, selected);
                            } else {
                                int flags =
                                        TreeNodeFlags.SPAN_AVAIL_WIDTH
                                                | TreeNodeFlags.OPEN_ON_DOUBLE_CLICK
                                                | TreeNodeFlags.OPEN_ON_ARROW;
                                if (selected) {
                                    flags |= TreeNodeFlags.SELECTED;
                                }
                                if (IkGui.treeNodeEx(label, flags)) {
                                    IkGui.treePop();
                                }
                            }
                        }
                    }

                    if (treeNodes) {
                        IkGui.popStyleVar();
                    }

                    io = IkGui.endMultiSelect();
                    selection.applyRequests(io);
                    IkGui.end();
                });

        // Upstream runs the UI for a couple of frames before the test starts. The window's nav
        // init selects the first item on its second frame, which must happen before the test
        // clears the selection.
        ctx.yieldFrame();

        // This uses mouseMove + mouseDown + mouseUp instead of itemClick, to test the precise
        // mouse up and mouse down reactions
        final var g = ctx.context;
        ctx.setRef("Test Window");
        for (int step = 0; step < 2; ++step) {
            final String d = "step " + step;
            multiSelectFlags[0] =
                    (step == 0
                                    ? MultiSelectFlags.SELECT_ON_AUTO
                                    : MultiSelectFlags.SELECT_ON_CLICK_RELEASE)
                            | MultiSelectFlags.CLEAR_ON_ESCAPE;
            final boolean onRelease =
                    (multiSelectFlags[0] & MultiSelectFlags.SELECT_ON_CLICK_RELEASE) != 0;

            selection.clear();
            ctx.yieldFrame();
            assertEquals(0, selection.getSize(), d);

            // Single click
            ctx.itemClick("Object 0000");
            assertEquals(1, selection.getSize(), d);
            assertTrue(selection.contains(0), d);

            // Clicking another item alters the selection on mouse down
            ctx.mouseMove("Object 0001");
            if (onRelease) {
                ctx.mouseDown(MouseButton.LEFT);
                assertEquals(1, selection.getSize(), d);
                assertTrue(selection.contains(0), d);
                ctx.mouseUp(MouseButton.LEFT);
                assertEquals(1, selection.getSize(), d);
                assertTrue(selection.contains(1), d);
            } else {
                ctx.mouseDown(MouseButton.LEFT);
                assertEquals(1, selection.getSize(), d);
                assertTrue(selection.contains(1), d);
                ctx.mouseUp(MouseButton.LEFT);
            }

            // Ctrl+A
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.A));
            assertEquals(100, selection.getSize(), d);

            // Clicking a selected item clears the other items on mouse up, not mouse down
            ctx.mouseMove("Object 0001");
            ctx.mouseDown(MouseButton.LEFT);
            assertEquals(100, selection.getSize(), d);
            ctx.mouseUp(MouseButton.LEFT);
            assertEquals(1, selection.getSize(), d);
            assertTrue(selection.contains(1), d);

            // Shift+Click
            ctx.itemClick("Object 0001");
            ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
            ctx.mouseMove("Object 0006");
            if (onRelease) {
                ctx.mouseDown(MouseButton.LEFT);
                assertEquals(1, selection.getSize(), d);
                ctx.mouseUp(MouseButton.LEFT);
                assertEquals(6, selection.getSize(), d);
            } else {
                ctx.mouseDown(MouseButton.LEFT);
                assertEquals(6, selection.getSize(), d);
                ctx.mouseUp(MouseButton.LEFT);
            }
            ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));

            // Ctrl+A preserves the range source, which was 0001
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.A));
            assertEquals(100, selection.getSize(), d);
            ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
            ctx.itemClick("Object 0008");
            ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));
            // 0001 to 0008
            assertEquals(8, selection.getSize(), d);

            // A reverse, clipped Shift+Click
            ctx.itemClick("Object 0030");
            ctx.scrollToTop("");
            ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
            ctx.itemClick("Object 0002");
            ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));
            assertEquals(29, selection.getSize(), d);

            // Escape clears the selection
            ctx.keyPress(Key.ESCAPE);
            ctx.yieldFrame();
            assertEquals(0, selection.getSize(), d);

            // Ctrl+Click
            ctx.itemClick("Object 0001");
            assertEquals(1, selection.getSize(), d);
            ctx.keyDown(KeyChord.ofMods(KeyModFlags.CTRL));
            ctx.itemClick("Object 0006");
            ctx.itemClick("Object 0007");
            ctx.keyUp(KeyChord.ofMods(KeyModFlags.CTRL));
            assertEquals(3, selection.getSize(), d);
            ctx.itemClick("Object 0008");
            assertEquals(1, selection.getSize(), d);

            // Shift+Arrow
            ctx.itemClick("Object 0002");
            assertEquals(ctx.getID("Object 0002"), g.navID, d);
            assertEquals(1, selection.getSize(), d);
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_DOWN));
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_DOWN));
            assertEquals(ctx.getID("Object 0004"), g.navID, d);
            assertEquals(3, selection.getSize(), d);

            // Ctrl+Arrow
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.ARROW_DOWN));
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.ARROW_DOWN));
            assertEquals(ctx.getID("Object 0006"), g.navID, d);
            assertEquals(3, selection.getSize(), d);

            // Shift+Arrow after a gap
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_DOWN));
            assertEquals(ctx.getID("Object 0007"), g.navID, d);
            assertEquals(6, selection.getSize(), d);

            // Shift+Arrow reducing the selection
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_UP));
            assertEquals(ctx.getID("Object 0006"), g.navID, d);
            assertEquals(5, selection.getSize(), d);

            // Ctrl+Shift+Arrow moving or appending without reducing the selection
            ctx.keyPress(chord(KeyModFlags.CTRL | KeyModFlags.SHIFT, Key.ARROW_UP), 4);
            assertEquals(ctx.getID("Object 0002"), g.navID, d);
            assertEquals(5, selection.getSize(), d);

            // Shift+Arrow replacing the selection
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_UP));
            assertEquals(ctx.getID("Object 0001"), g.navID, d);
            assertEquals(2, selection.getSize(), d);

            // Arrow replacing the selection
            ctx.keyPress(Key.ARROW_DOWN);
            assertEquals(ctx.getID("Object 0002"), g.navID, d);
            assertEquals(1, selection.getSize(), d);
            assertTrue(selection.contains(2), d);

            // Keyboard activation with space and enter
            ctx.itemClick("Object 0001");
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.ARROW_DOWN));
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.ARROW_DOWN));
            assertEquals(1, selection.getSize(), d);
            assertTrue(selection.contains(1), d);
            // Over 0003 while not selected
            ctx.keyPress(Key.SPACE);
            assertEquals(1, selection.getSize(), d);
            assertTrue(selection.contains(3), d);
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.ARROW_DOWN));
            // Over 0004
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.SPACE));
            assertEquals(2, selection.getSize(), d);
            assertTrue(selection.contains(3), d);
            assertTrue(selection.contains(4), d);
            // Over 0004 while selected
            ctx.keyPress(Key.SPACE);
            assertEquals(1, selection.getSize(), d);
            assertTrue(selection.contains(4), d);
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.ARROW_DOWN), 4);
            // Over 0008 while not selected
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.SPACE));
            assertEquals(5, selection.getSize(), d);
            assertTrue(selection.contains(4), d);
            assertTrue(selection.contains(8), d);

            // Basic keyboard open and close
            if (treeNodes) {
                ctx.itemClick("Object 0001");
                ctx.itemClose("Object 0001");
                ctx.keyPress(Key.ARROW_RIGHT);
                assertTrue(ctx.itemInfo("Object 0001").has(ItemStatusFlags.OPENED), d);
                ctx.keyPress(Key.ARROW_LEFT);
                assertFalse(ctx.itemInfo("Object 0001").has(ItemStatusFlags.OPENED), d);
            }

            // Home and End
            ctx.keyPress(Key.HOME);
            assertEquals(ctx.getID("Object 0000"), g.navID, d);
            assertEquals(1, selection.getSize(), d);
            assertTrue(selection.contains(0), d);
            ctx.keyPress(Key.END);
            assertEquals(ctx.getID("Object 0099"), g.navID, d);
            assertEquals(1, selection.getSize(), d);
            assertTrue(selection.contains(99), d);
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.HOME));
            assertEquals(ctx.getID("Object 0000"), g.navID, d);
            assertEquals(1, selection.getSize(), d);
            assertTrue(selection.contains(99), d);
            // Upstream notes that a Home/End/PageUp/PageDown to the same target doesn't count as
            // just moving to it, which may be reasonable
            ctx.keyPress(Key.HOME);
            assertEquals(1, selection.getSize(), d);
            assertTrue(selection.contains(99), d);
            ctx.keyPress(Key.SPACE);
            assertEquals(1, selection.getSize(), d);
            assertTrue(selection.contains(0), d);
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.END));
            assertEquals(ctx.getID("Object 0099"), g.navID, d);
            assertEquals(100, selection.getSize(), d);

            // Basic navigation from a clipped item
            windowFlags[0] &= ~WindowFlags.ALWAYS_AUTO_RESIZE;
            ctx.yieldFrame();
            ctx.windowResize("", 0.0f, IkGui.getTextLineHeightWithSpacing() * 50.0f);
            ctx.keyPress(Key.HOME);
            ctx.keyPress(Key.ARROW_DOWN, 2);
            assertEquals(ctx.getID("Object 0002"), g.navID, d);
            ctx.mouseMove(g.navID);
            // Scroll with the mouse wheel rather than scrollToBottom(), to keep focus
            for (int n = 0; n < 10; ++n) {
                ctx.mouseWheel(0, -1.0f);
            }
            assertFalse(ctx.itemInfo("Object 0002").has(ItemStatusFlags.VISIBLE), d);
            ctx.keyPress(Key.ARROW_DOWN);
            assertEquals(ctx.getID("Object 0003"), g.navID, d);
            assertTrue(ctx.itemInfo("Object 0003").has(ItemStatusFlags.VISIBLE), d);

            // Page up and page down from a clipped item
            ctx.windowResize("", 0.0f, IkGui.getTextLineHeightWithSpacing() * 40.0f);
            ctx.windowMove(
                    "", IkGui.getMainViewport().position.x, IkGui.getMainViewport().position.y);
            ctx.yieldFrame();
            ctx.keyPress(Key.HOME);
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.PAGE_DOWN));
            assertTrue(selection.getSize() > 0, d);
            // Roughly a page worth of contents
            final int pageWorth = selection.getSize();
            assertTrue(pageWorth > 1, d);
            ctx.keyPress(Key.HOME);
            ctx.keyPress(Key.ARROW_DOWN, 2);
            assertEquals(ctx.getID("Object 0002"), g.navID, d);
            ctx.scrollToBottom("");
            ctx.yieldFrame();
            assertFalse(ctx.itemInfo("Object 0002").has(ItemStatusFlags.VISIBLE), d);
            ctx.keyPress(Key.PAGE_DOWN);
            assertEquals(1, selection.getSize(), d);
            assertTrue(
                    selection.contains(2 + pageWorth)
                            || selection.contains(2 + pageWorth - 1)
                            || selection.contains(2 + pageWorth + 1)
                            || selection.contains(2 + pageWorth + 2),
                    d);
        }
    }

    /** widgets_multiselect_1_selectables. */
    @Test
    void testMultiSelect1Selectables() {
        multiSelectBasics(false);
    }

    /** widgets_multiselect_2_treenode. */
    @Test
    void testMultiSelect2TreeNode() {
        multiSelectBasics(true);
    }

    /** widgets_multiselect_3_multiple: two multi-select scopes in a window. */
    @Test
    void testMultiSelect3Multiple() {
        final ExampleSelection selection0 = new ExampleSelection();
        final ExampleSelection selection1 = new ExampleSelection();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    for (int scope = 0; scope < 2; ++scope) {
                        final ExampleSelection selection = scope == 1 ? selection1 : selection0;
                        IkGui.text("SCOPE " + scope + "\n");
                        IkGui.pushID("Scope " + scope);
                        selection.emitBasicLoop(MultiSelectFlags.NONE, 10, "Object %04d");
                        IkGui.popID();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        selection0.clear();
        selection1.clear();

        ctx.itemClick("Scope 0/Object 0001");
        assertEquals(1, selection0.getSize());
        assertEquals(0, selection1.getSize());

        ctx.itemClick("Scope 1/Object 0002");
        assertEquals(1, selection0.getSize());
        assertEquals(1, selection1.getSize());

        ctx.keyPress(chord(KeyModFlags.CTRL, Key.A));
        assertEquals(1, selection0.getSize());
        assertEquals(10, selection1.getSize());

        // Leaving a scope with a keyboard move
        ctx.itemClick("Scope 1/Object 0000");
        assertEquals(1, selection0.getSize());
        assertEquals(1, selection1.getSize());
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(1, selection0.getSize());
        assertEquals(0, selection1.getSize());
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(0, selection0.getSize());
        assertEquals(1, selection1.getSize());
    }

    /** widgets_multiselect_unfocused: a right click in an unfocused window. */
    @Test
    void testMultiSelectUnfocused() {
        final ExampleSelection selection = new ExampleSelection();
        ctx.setGui(
                () -> {
                    ctx.showApp();
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    selection.emitBasicLoop(MultiSelectFlags.CLEAR_ON_ESCAPE, 50, "Object %03d");
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.itemClick("//Test Window/Object 001");
        assertTrue(selection.getSize() == 1 && selection.contains(1));
        ctx.windowFocus(IkGuiTestContext.DEMO);
        final Window testWindow = ctx.getWindowByRef("Test Window");
        ctx.windowMove(
                IkGuiTestContext.DEMO,
                testWindow.position.x + testWindow.size.x,
                testWindow.position.y);
        assertTrue(selection.getSize() == 1 && selection.contains(1));
        ctx.mouseMoveNoFocus("//Test Window/Object 006");
        assertNotNull(g.navFocusedWindow);
        assertEquals(IkGuiTestContext.DEMO, g.navFocusedWindow.name);
        ctx.mouseClick(MouseButton.RIGHT);
        assertNotNull(g.navFocusedWindow);
        assertEquals("Test Window", g.navFocusedWindow.name);
        assertEquals(1, selection.getSize());
        assertTrue(selection.contains(6));
    }

    /** widgets_multiselect_enter: the Enter key's behavior. */
    @Test
    void testMultiSelectEnter() {
        final ExampleSelection selection = new ExampleSelection();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    selection.emitBasicLoop(MultiSelectFlags.CLEAR_ON_ESCAPE, 50, "Object %03d");
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        // Enter alters the selection, because the current item is not selected
        ctx.itemClick("Object 000");
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.ARROW_DOWN));
        assertEquals(1, selection.getSize());
        assertTrue(selection.contains(0));
        assertFalse(selection.contains(1));
        ctx.keyPress(Key.ENTER);
        assertEquals(1, selection.getSize());
        assertFalse(selection.contains(0));
        assertTrue(selection.contains(1));

        // Enter doesn't alter the selection, because the current item is selected
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_DOWN));
        assertEquals(2, selection.getSize());
        assertTrue(selection.contains(1));
        assertTrue(selection.contains(2));
        ctx.keyPress(Key.ENTER);
        assertEquals(2, selection.getSize());
        assertTrue(selection.contains(1));
        assertTrue(selection.contains(2));
    }

    /** widgets_multiselect_disjoint: collapsing headers between the selection items. */
    @Test
    void testMultiSelectDisjoint() {
        final ExampleSelection selection = new ExampleSelection();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    MultiSelectIO io =
                            IkGui.beginMultiSelect(
                                    MultiSelectFlags.CLEAR_ON_ESCAPE, selection.getSize(), 50);
                    selection.applyRequests(io);
                    for (int n = 0; n < 50; ++n) {
                        if ((n % 10) == 0
                                && IkGui.collapsingHeader(
                                        String.format("Section %02d-> %02d", n, n + 9))) {
                            n += 9;
                            continue;
                        }
                        final boolean selected = selection.contains(n);
                        IkGui.setNextItemSelectionUserData(n);
                        IkGui.selectable(String.format("Object %03d", n), selected);
                    }
                    io = IkGui.endMultiSelect();
                    selection.applyRequests(io);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Object 000");
        assertEquals(1, selection.getSize());
        assertTrue(selection.contains(0));
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_UP));
        assertEquals(1, selection.getSize());
        assertTrue(selection.contains(0));
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_DOWN));
        assertEquals(1, selection.getSize());
        assertTrue(selection.contains(0));
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_DOWN), 10);
        assertEquals(10, selection.getSize());
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_DOWN));
        assertEquals(11, selection.getSize());
    }

    /** widgets_multiselect_nosrc: a range select with no range source. */
    @Test
    void testMultiSelectNoSource() {
        final ExampleSelection selection = new ExampleSelection();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    selection.emitBasicLoop(MultiSelectFlags.CLEAR_ON_ESCAPE, 50, "Object %03d");
                    IkGui.end();
                });
        ctx.context.navID = 0;
        ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
        ctx.itemClick("//Test Window/Object 005");
        assertEquals(6, selection.getSize());
        assertTrue(selection.contains(0));
        assertTrue(selection.contains(5));
    }

    /** widgets_multiselect_singleselect: range selection with MultiSelectFlags.SINGLE_SELECT. */
    @Test
    void testMultiSelectSingleSelect() {
        final ExampleSelection selection = new ExampleSelection();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    selection.emitBasicLoop(MultiSelectFlags.SINGLE_SELECT, 50, "Object %03d");
                    IkGui.end();
                });

        // Shift+Arrow moves
        ctx.itemClick("//Test Window/Object 000");
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_DOWN));
        assertEquals(1, selection.getSize());
        assertFalse(selection.contains(0));
        assertTrue(selection.contains(1));

        // Shift+Click
        ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
        ctx.itemClick("//Test Window/Object 005");
        ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));
        assertEquals(1, selection.getSize());
        assertFalse(selection.contains(1));
        assertTrue(selection.contains(5));

        // Ctrl+Click
        ctx.keyDown(KeyChord.ofMods(KeyModFlags.CTRL));
        ctx.itemClick("//Test Window/Object 006");
        ctx.keyUp(KeyChord.ofMods(KeyModFlags.CTRL));
        assertEquals(1, selection.getSize());
        assertFalse(selection.contains(5));
        assertTrue(selection.contains(6));

        // Ctrl+Down, Ctrl+Space
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.ARROW_DOWN));
        assertEquals(1, selection.getSize());
        assertTrue(selection.contains(6));
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.SPACE));
        assertEquals(1, selection.getSize());
        assertFalse(selection.contains(6));
        assertTrue(selection.contains(7));
    }

    /** widgets_multiselect_nested: nested beginMultiSelect() doesn't crash. */
    @Test
    void testMultiSelectNested() {
        final ExampleSelection selection = new ExampleSelection();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    MultiSelectIO io =
                            IkGui.beginMultiSelect(MultiSelectFlags.NONE, selection.getSize(), 50);
                    selection.applyRequests(io);
                    selection.emitBasicItems(50, "Object %03d");
                    if (IkGui.button("Open Popup")) {
                        IkGui.openPopup("Popup");
                    }
                    if (IkGui.beginPopup("Popup")) {
                        MultiSelectIO io2 =
                                IkGui.beginMultiSelect(
                                        MultiSelectFlags.NONE, selection.getSize(), 50);
                        selection.applyRequests(io2);
                        selection.emitBasicItems(50, "Object %03d");
                        io2 = IkGui.endMultiSelect();
                        selection.applyRequests(io2);
                        IkGui.endPopup();
                    }
                    io = IkGui.endMultiSelect();
                    selection.applyRequests(io);
                    IkGui.end();
                });
        ctx.itemClick("//Test Window/Open Popup");
    }

    /** widgets_multiselect_io_lifetime: the validity of the MultiSelectIO data. */
    @Test
    void testMultiSelectIOLifetime() {
        final ExampleSelection selection = new ExampleSelection();
        final boolean[] check = {false};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    final MultiSelectIO io1 =
                            IkGui.beginMultiSelect(MultiSelectFlags.NONE, selection.getSize(), 50);
                    if (check[0]) {
                        ctx.checkEquals(5L, io1.rangeSourceItem, "begin range source");
                    }
                    selection.emitBasicItems(50, "Object %03d");
                    selection.applyRequests(io1);
                    final MultiSelectIO io2 = IkGui.endMultiSelect();
                    if (check[0]) {
                        ctx.checkEquals(5L, io2.rangeSourceItem, "end range source");
                    }
                    selection.applyRequests(io2);
                    IkGui.end();
                });
        ctx.itemClick("//Test Window/Object 005");
        check[0] = true;
        ctx.yieldFrames(2);
    }

    private static final String[] EXAMPLE_NAMES = {
        "Artichoke",
        "Arugula",
        "Asparagus",
        "Avocado",
        "Bamboo Shoots",
        "Bean Sprouts",
        "Beans",
        "Beet",
        "Belgian Endive",
        "Bell Pepper",
        "Bitter Gourd",
        "Bok Choy",
        "Broccoli",
        "Brussels Sprouts",
        "Burdock Root",
        "Cabbage",
        "Calabash",
        "Capers",
        "Carrot",
        "Cassava",
        "Cauliflower",
        "Celery",
        "Celery Root",
        "Celcuce",
        "Chayote",
        "Chinese Broccoli",
        "Corn",
        "Cucumber",
        "AAAB",
    };

    /** widgets_multiselect_checkboxes: multi-select with checkboxes, for range selection. */
    @Test
    void testMultiSelectCheckboxes() {
        final int itemsCount = 20;
        final IkInt mask = new IkInt(0);
        final boolean[] applyBeginRequests = {false};
        // Apply the requests to a bit mask, where the selection user data is the item index
        final java.util.function.Consumer<MultiSelectIO> applyRequests =
                io -> {
                    for (SelectionRequest req : io.requests) {
                        if (req.type() == SelectionRequestType.SET_ALL) {
                            for (int n = 0; n < itemsCount; ++n) {
                                mask.set(
                                        req.selected()
                                                ? mask.get() | (1 << n)
                                                : mask.get() & ~(1 << n));
                            }
                        }
                        if (req.type() == SelectionRequestType.SET_RANGE) {
                            for (int n = (int) req.rangeFirstItem();
                                    n <= (int) req.rangeLastItem();
                                    ++n) {
                                mask.set(
                                        req.selected()
                                                ? mask.get() | (1 << n)
                                                : mask.get() & ~(1 << n));
                            }
                        }
                    }
                };
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final MultiSelectIO io =
                            IkGui.beginMultiSelect(
                                    MultiSelectFlags.NO_AUTO_CLEAR
                                            | MultiSelectFlags.NO_AUTO_SELECT
                                            | MultiSelectFlags.CLEAR_ON_ESCAPE);
                    IkGui.button("Header");
                    // Verify that the system can work without this
                    if (applyBeginRequests[0]) {
                        applyRequests.accept(io);
                    }
                    for (int n = 0; n < itemsCount; ++n) {
                        IkGui.setNextItemSelectionUserData(n);
                        IkGui.checkboxFlags("Item " + n, mask, 1 << n);
                    }
                    applyRequests.accept(IkGui.endMultiSelect());
                    IkGui.end();
                });

        // Upstream notes this is broken for the second step, if clipped
        mask.set(0);
        applyBeginRequests[0] = true;
        final int all = (1 << itemsCount) - 1;

        // Clicks
        ctx.setRef("Test Window");
        ctx.itemClick("Item 1");
        assertEquals(0x02, mask.get());
        ctx.itemClick("Item 1");
        assertEquals(0, mask.get());
        ctx.itemClick("Item 1");
        ctx.itemClick("Item 2");
        assertEquals(0x02 | 0x04, mask.get());

        // Shift+Click
        ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
        // Set 2-6
        ctx.itemClick("Item 6");
        assertEquals(0x02 | 0x04 | 0x08 | 0x10 | 0x20 | 0x40, mask.get());
        // Clear 2-6
        ctx.itemClick("Item 6");
        assertEquals(0x02, mask.get());
        // Set 2-6
        ctx.itemClick("Item 6");
        // Set 2-7
        ctx.itemClick("Item 7");
        assertEquals(0x02 | 0x04 | 0x08 | 0x10 | 0x20 | 0x40 | 0x80, mask.get());
        ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));

        ctx.keyPress(chord(KeyModFlags.CTRL, Key.A));
        assertEquals(all, mask.get());
        ctx.keyPress(Key.HOME);
        // Skip the button
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(all, mask.get());
        ctx.keyPress(Key.SPACE);
        assertEquals(all ^ 0x01, mask.get());
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.END));
        assertEquals(0, mask.get());

        // Shift+Arrows
        ctx.keyPress(Key.HOME);
        // Skip the button
        ctx.keyPress(Key.ARROW_DOWN);
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_DOWN));
        assertEquals(0, mask.get());
        ctx.keyPress(Key.SPACE);
        assertEquals(0x02, mask.get());
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_DOWN));
        assertEquals(0x02 | 0x04, mask.get());
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_DOWN));
        assertEquals(0x02 | 0x04 | 0x08, mask.get());
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_UP));
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_UP));
        assertEquals(0x02 | 0x04 | 0x08, mask.get());
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_UP));
        assertEquals(0x01 | 0x02 | 0x04 | 0x08, mask.get());

        // Pressing an arrow updates the range source
        ctx.keyPress(Key.ARROW_DOWN, 5);
        // Upstream uses GetFocusID(), which returns the nav ID
        assertEquals(ctx.getID("Item 5"), ctx.context.navID);
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_DOWN));
        assertEquals(0x01 | 0x02 | 0x04 | 0x08, mask.get());

        // MultiSelectFlags.CLEAR_ON_ESCAPE
        ctx.keyPress(Key.ESCAPE);
        assertEquals(0, mask.get());

        // A right click doesn't do anything
        ctx.itemClick("Item 2", MouseButton.RIGHT);
        assertEquals(0, mask.get());
    }

    /** widgets_multiselect_boxselect_1: box selection in the demo's assets browser. */
    @Test
    void testMultiSelectBoxSelect1() {
        ctx.setGui(ctx::showApp);
        final var g = ctx.context;
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuClick("Examples/Assets Browser");
        ctx.setRef("Example: Assets Browser");
        final Window childWindow = ctx.windowInfo("Assets");
        assertNotNull(childWindow);

        final float iconSize = 40.0f;
        final float iconSpacing = 10.0f;

        final var style = g.style.variable;
        ctx.windowResize(
                "",
                (16 * iconSize)
                        + (17 * iconSpacing)
                        + style.windowPadding.x * 4.0f
                        + style.scrollbarSize,
                600);
        ctx.menuClick("File/Clear items");
        ctx.menuClick("File/Add 10000 items");

        // Zoom and unzoom
        ctx.setRef(childWindow);
        ctx.itemClick("$$20");
        ctx.keyDown(KeyChord.ofMods(KeyModFlags.CTRL));
        for (int n = 0; n < 10; ++n) {
            ctx.mouseWheel(0, -1.0f);
        }
        for (int n = 0; n < 10; ++n) {
            ctx.mouseWheel(0, 1.0f);
        }
        ctx.keyUp(KeyChord.ofMods(KeyModFlags.CTRL));
        ctx.keyPress(Key.ESCAPE);

        // Set specific options
        ctx.setRef("Example: Assets Browser");
        ctx.menuClick("Options");
        ctx.itemInputValue("//$FOCUSED/Icon Size", iconSize);
        ctx.itemInputValue("//$FOCUSED/Icon Spacing", (int) iconSpacing);
        ctx.itemInputValue("//$FOCUSED/Icon Hit Spacing", 2);

        ctx.setRef(childWindow);
        ctx.scrollToTop("");
        final MultiSelectState storage = IkGuiInternal.getMultiSelectState(ctx.getID(""));
        assertNotNull(storage);
        assertEquals(0, storage.lastSelectionSize);
        ctx.itemClick("$$7");
        assertEquals(1, storage.lastSelectionSize);
        ctx.keyPress(Key.ESCAPE);
        assertEquals(0, storage.lastSelectionSize);

        // Box select part of a row, then a rectangle
        ctx.itemDragAndDrop("$$0", "$$5");
        assertEquals(6, storage.lastSelectionSize);
        ctx.itemDragAndDrop("$$20", "$$105");
        assertEquals(6 * 6, storage.lastSelectionSize);
        ctx.keyPress(Key.ESCAPE);

        // Box select from the void
        final RectFloat item1 = new RectFloat(ctx.itemInfo("$$1").rect);
        final RectFloat item2 = new RectFloat(ctx.itemInfo("$$2").rect);
        ctx.mouseMoveToPos(
                (item1.getCenterX() + item2.getCenterX()) * 0.5f,
                (item1.getCenterY() + item2.getCenterY()) * 0.5f);
        // Assumes the icon hit spacing is more than 0
        assertFalse(IkGui.isAnyItemHovered());
        assertEquals(0, storage.lastSelectionSize);
        ctx.mouseClick(MouseButton.LEFT);
        // Verify we are over the void
        assertEquals(0, storage.lastSelectionSize);
        ctx.mouseDown(MouseButton.LEFT);
        final RectFloat item22 = ctx.itemInfo("$$22").rect;
        ctx.mouseMoveToPos(item22.getCenterX(), item22.getCenterY());
        ctx.mouseUp(MouseButton.LEFT);
        assertEquals(2 * 5, storage.lastSelectionSize);

        // The first selected item became the nav ID
        assertEquals(ctx.getID("$$2"), g.navID);

        // Box select scrolling, in a very basic way
        assertEquals(0.0f, childWindow.scrollPosition.y);
        ctx.itemClick("$$0");
        ctx.mouseMove("$$3");
        ctx.mouseDown(MouseButton.LEFT);
        ctx.mouseMove("$$5");
        assertEquals(3, storage.lastSelectionSize);
        ctx.mouseMoveToPos(g.io.mousePosition.x, g.io.mousePosition.y + 1000);
        ctx.sleepNoSkip(2.0f, 0.1f);
        ctx.mouseUp(MouseButton.LEFT);
        assertTrue(childWindow.scrollPosition.y > 0.0f);

        // Establish how many rows are visible
        ctx.scrollToTop("");
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.A));
        final int itemCount = storage.lastSelectionSize;
        int rightMostIndex = -1;
        float rightMostX2 = -Float.MAX_VALUE;
        for (int n = 0; n < itemCount; ++n) {
            final IkGuiTestContext.ItemInfo info = ctx.itemInfoOrNull("$$" + n);
            if (info == null || !info.has(ItemStatusFlags.VISIBLE)) {
                break;
            }
            if (rightMostX2 < info.rect.getRight()) {
                rightMostX2 = info.rect.getRight();
                rightMostIndex = n;
            }
        }
        final int countPerLine = rightMostIndex + 1;
        assertTrue(countPerLine > 8, "count per line " + countPerLine);

        // Box select at the bottom while using the mouse wheel, with different pixel offsets, to
        // stress test the effect of clipping on box selection
        for (int offset = 0; offset < 31; ++offset) {
            final String d = "offset " + offset;
            ctx.keyPress(Key.ESCAPE);
            assertEquals(0, storage.lastSelectionSize, d);
            final RectFloat start = ctx.itemInfo("$$114").rect;
            ctx.mouseMoveToPos(start.getCenterX(), start.getTop() + 1 + offset);
            ctx.mouseDown(MouseButton.LEFT);
            assertEquals(ctx.getID("$$114"), g.activeID, d);
            final RectFloat end = ctx.itemInfo("$$118").rect;
            ctx.mouseMoveToPos(end.getCenterX(), end.getTop() + 1 + offset);
            assertEquals(5, storage.lastSelectionSize, d);
            for (int n = 0; n < 3; ++n) {
                ctx.mouseWheel(0, -2.0f);
            }
            for (int n = 0; n < 3; ++n) {
                ctx.mouseWheel(0, 2.0f);
            }
            ctx.mouseUp(MouseButton.LEFT);
            // Anything bigger means we missed some rows
            assertEquals(5, storage.lastSelectionSize, d);
        }

        // A dummy deletion for coverage
        assertEquals(5, storage.lastSelectionSize);
        ctx.keyPress(Key.DELETE);
        assertEquals(1, storage.lastSelectionSize);

        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuClick("Examples/Assets Browser");
    }

    /** widgets_multiselect_boxselect_2: box selection in a table with decorations. */
    @Test
    void testMultiSelectBoxSelect2() {
        final IkInt tableFlags = new IkInt(TableFlags.SCROLL_Y);
        final IkInt multiSelectFlags = new IkInt(MultiSelectFlags.BOX_SELECT_1D);
        final SelectionBasicStorage selection = new SelectionBasicStorage();
        final boolean[] frozenHeaders = {false};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(
                            400.0f, 24 * IkGui.getTextLineHeightWithSpacing(), Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.checkboxFlags("_BordersOuter", tableFlags, TableFlags.BORDERS_OUTER);
                    IkGui.sameLine();
                    IkGui.checkboxFlags("_BordersInner", tableFlags, TableFlags.BORDERS_INNER);
                    IkGui.checkboxFlags(
                            "_BoxSelect1d", multiSelectFlags, MultiSelectFlags.BOX_SELECT_1D);
                    if (IkGui.beginTable("table1", 1, tableFlags.get())) {
                        if (frozenHeaders[0]) {
                            IkGui.tableSetupScrollFreeze(0, 1);
                            IkGui.tableHeadersRow();
                        }
                        MultiSelectIO io =
                                IkGui.beginMultiSelect(
                                        multiSelectFlags.get(), selection.getSize(), 1000);
                        selection.applyRequests(io);
                        for (int i = 0; i < 1000; ++i) {
                            IkGui.setNextItemSelectionUserData(i);
                            IkGui.tableNextColumn();
                            IkGui.selectable(String.format("Item %03d", i), selection.contains(i));
                        }
                        io = IkGui.endMultiSelect();
                        selection.applyRequests(io);
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        final Table table = IkGuiImplTables.tableFindByID(ctx.getID("Test Window/table1"));
        assertNotNull(table);

        // Returns {first, last}
        final java.util.function.Supplier<int[]> findFirstAndLastSelected =
                () -> {
                    int first = -1;
                    int last = -1;
                    for (int n = 0; n < 1000; ++n) {
                        if (!selection.contains(n)) {
                            continue;
                        }
                        if (first == -1) {
                            first = n;
                        }
                        last = n;
                    }
                    return new int[] {first, last};
                };

        for (int step = 0; step < 4; ++step) {
            final String d = "step " + step;
            multiSelectFlags.set(MultiSelectFlags.BOX_SELECT_1D);
            selection.clear();
            tableFlags.set(
                    (step & 1) != 0
                            ? tableFlags.get() | TableFlags.BORDERS_OUTER
                            : tableFlags.get() & ~TableFlags.BORDERS_OUTER);
            frozenHeaders[0] = (step & 2) != 0;
            ctx.yieldFrame();

            ctx.setRef(table.id);
            ctx.mouseMove("Item 001");
            ctx.mouseDown(MouseButton.LEFT);
            ctx.mouseMoveToPos(
                    ctx.context.io.mousePosition.x,
                    ctx.context.io.mousePosition.y + ctx.getWindowByRef("//Test Window").size.y);
            ctx.sleepNoSkip(1.0f, 0.05f);
            assertFalse(selection.contains(0), d);
            int[] range = findFirstAndLastSelected.get();
            assertTrue(selection.getSize() > 1, d);
            assertEquals(1, range[0], d);
            assertTrue(range[1] > 1, d);
            // No gaps in the selection
            assertEquals(range[1] - range[0] + 1, selection.getSize(), d);

            // Scroll back up to unselect. Upstream's mouse move scrolls gradually, which acts like
            // using the mouse wheel while holding the button. Our mouse moves are instant, which
            // fights with box selection scrolling at the edges, so this uses the mouse wheel from
            // the middle of the table, then moves to the item.
            final Window tableWindow = table.innerWindow;
            ctx.mouseMoveToPos(
                    tableWindow.position.x + tableWindow.size.x * 0.5f,
                    tableWindow.position.y + tableWindow.size.y * 0.5f);
            for (int n = 0; n < 100 && tableWindow.scrollPosition.y > 0; ++n) {
                ctx.mouseWheel(0, 5.0f);
            }
            ctx.mouseMove("Item 003");
            assertEquals(3, selection.getSize(), d);
            ctx.mouseUp(MouseButton.LEFT);
            range = findFirstAndLastSelected.get();
            assertEquals(1, range[0], d);
            assertEquals(3, range[1], d);

            multiSelectFlags.set(multiSelectFlags.get() | MultiSelectFlags.SELECT_ON_CLICK_ALWAYS);
            ctx.yieldFrame();
            ctx.itemDragAndDrop("Item 002", "Item 007");
            assertEquals(6, selection.getSize(), d);
            // Box select from inside
            ctx.itemDragAndDrop("Item 003", "Item 006");
            assertEquals(4, selection.getSize(), d);
            multiSelectFlags.set(multiSelectFlags.get() & ~MultiSelectFlags.SELECT_ON_CLICK_ALWAYS);
        }
    }

    /** widgets_multiselect_batch_requests: applying several requests at once to the storage. */
    @Test
    void testMultiSelectBatchRequests() {
        final SelectionBasicStorage selection = new SelectionBasicStorage();
        final MultiSelectIO io = new MultiSelectIO();
        io.itemsCount = 100;
        for (int index = 90; index >= 10; index -= 10) {
            io.requests.add(
                    new SelectionRequest(SelectionRequestType.SET_RANGE, true, 1, index, index));
        }
        selection.applyRequests(io);
        assertEquals(9, selection.getSize());
        io.requests.clear();
        io.requests.add(new SelectionRequest(SelectionRequestType.SET_RANGE, false, 1, 60, 60));
        selection.applyRequests(io);
        assertEquals(8, selection.getSize());

        io.requests.clear();
        io.requests.add(new SelectionRequest(SelectionRequestType.SET_RANGE, true, 1, 12, 15));
        selection.applyRequests(io);
        assertEquals(8 + 4, selection.getSize());
        io.requests.clear();
        io.requests.add(new SelectionRequest(SelectionRequestType.SET_RANGE, false, 1, 12, 15));
        selection.applyRequests(io);
        assertEquals(8, selection.getSize());

        // Multiple ranges, like box selection can make
        io.requests.clear();
        // No effect
        io.requests.add(new SelectionRequest(SelectionRequestType.SET_RANGE, false, 1, 12, 15));
        // Duplicates
        io.requests.add(new SelectionRequest(SelectionRequestType.SET_RANGE, false, 1, 12, 15));
        io.requests.add(new SelectionRequest(SelectionRequestType.SET_RANGE, true, 1, 25, 25));
        io.requests.add(new SelectionRequest(SelectionRequestType.SET_RANGE, false, 1, 40, 40));
        io.requests.add(new SelectionRequest(SelectionRequestType.SET_RANGE, true, 1, 65, 65));
        selection.applyRequests(io);
        assertEquals(8 + 2 - 1, selection.getSize());
    }

    /**
     * widgets_multiselect_cell_width_loss: beginMultiSelect() in a table cell doesn't lose the
     * cell's width.
     */
    @Test
    void testMultiSelectCellWidthLoss() {
        final ExampleSelection selection = new ExampleSelection();
        final boolean[] firstFrame = {true};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(400, 400);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable(
                            "Table",
                            2,
                            TableFlags.SCROLL_X
                                    | TableFlags.SCROLL_Y
                                    | TableFlags.ROW_BACKGROUND
                                    | TableFlags.BORDERS_OUTER
                                    | TableFlags.BORDERS_INNER)) {
                        IkGui.tableSetupColumn("Object");
                        IkGui.tableSetupColumn("Action");
                        IkGui.tableSetupScrollFreeze(0, 1);

                        if (IkGui.tableNextColumn()) {
                            IkGui.textUnformatted("A wide item in column 1");
                        }
                        if (IkGui.tableNextColumn()) {
                            // The width of this was lost by beginMultiSelect()
                            IkGui.textUnformatted("A wide item in column 2");
                        }

                        final int itemsCount = 50;
                        MultiSelectIO io =
                                IkGui.beginMultiSelect(
                                        MultiSelectFlags.CLEAR_ON_ESCAPE
                                                | MultiSelectFlags.BOX_SELECT_1D,
                                        selection.getSize(),
                                        itemsCount);
                        selection.applyRequests(io);

                        final ListClipper clipper = new ListClipper();
                        clipper.begin(itemsCount);
                        if (io.rangeSourceItem != -1) {
                            // Make sure the range source item isn't clipped
                            clipper.includeItemByIndex((int) io.rangeSourceItem);
                        }
                        while (clipper.step()) {
                            for (int n = clipper.displayStart; n < clipper.displayEnd; ++n) {
                                IkGui.tableNextRow();
                                IkGui.tableNextColumn();
                                IkGui.setNextItemSelectionUserData(n);
                                IkGui.selectable(
                                        String.format(
                                                "Object %05d: %s",
                                                n, EXAMPLE_NAMES[n % EXAMPLE_NAMES.length]),
                                        selection.contains(n),
                                        SelectableFlags.SPAN_ALL_COLUMNS
                                                | SelectableFlags.ALLOW_OVERLAP);
                                IkGui.tableNextColumn();

                                if (!firstFrame[0]) {
                                    ctx.check(
                                            IkGui.getContentRegionAvailable().x
                                                    >= IkGui.calcTextSize("A wide item in column 2")
                                                            .x,
                                            "cell width");
                                }
                                IkGui.smallButton("hello");
                            }
                        }
                        io = IkGui.endMultiSelect();
                        selection.applyRequests(io);
                        IkGui.endTable();
                    }
                    IkGui.end();
                    firstFrame[0] = false;
                });
        ctx.yieldFrames(2);
    }

    /**
     * widgets_multiselect_cell_width_loss2: multi-select with skipped column submissions doesn't
     * break table auto-fit.
     */
    @Test
    void testMultiSelectCellWidthLoss2() {
        final boolean[] useMultiSelect = {false};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    // Upstream discards the table's instance and settings on the first frame,
                    // which every test here starts without anyway
                    final int tableFlags =
                            TableFlags.BORDERS
                                    | TableFlags.RESIZABLE
                                    | TableFlags.SIZING_FIXED_FIT
                                    | TableFlags.SCROLL_Y;
                    if (IkGui.beginTable("table1", 2, tableFlags, 400, 400)) {
                        IkGui.tableSetupColumn("Name", TableColumnFlags.NO_HEADER_LABEL);
                        IkGui.tableSetupColumn("Value", TableColumnFlags.NO_HEADER_LABEL);
                        IkGui.tableHeadersRow();

                        final SelectionBasicStorage selection = new SelectionBasicStorage();
                        if (useMultiSelect[0]) {
                            selection.applyRequests(
                                    IkGui.beginMultiSelect(
                                            MultiSelectFlags.NONE, selection.getSize(), 50));
                        }
                        for (int row = 0; row < 50; ++row) {
                            IkGui.tableNextRow();
                            IkGui.tableNextColumn();
                            IkGui.text("Row XXXXX");
                            if (row < 10) {
                                // Only some rows submit content to the second column
                                IkGui.tableNextColumn();
                                IkGui.text("Value Y");
                            }
                        }
                        if (useMultiSelect[0]) {
                            selection.applyRequests(IkGui.endMultiSelect());
                        }

                        final Table table = IkGuiInternal.getCurrentTable();
                        if (table.columns[0].widthAuto > 0.0f) {
                            ctx.checkEquals(
                                    IkGui.calcTextSize("Row XXXXX").x,
                                    table.columns[0].widthAuto,
                                    "column 0 auto width");
                        }
                        if (table.columns[1].widthAuto > 0.0f) {
                            ctx.checkEquals(
                                    IkGui.calcTextSize("Value Y").x,
                                    table.columns[1].widthAuto,
                                    "column 1 auto width");
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        useMultiSelect[0] = false;
        ctx.yieldFrames(3);
        useMultiSelect[0] = true;
        ctx.yieldFrames(3);
    }

    /** widgets_typingselect: basic getTypingSelectRequest() use. */
    @Test
    void testTypingSelect() {
        final ExampleSelection selection = new ExampleSelection();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    final int itemsCount = EXAMPLE_NAMES.length;
                    MultiSelectIO io =
                            IkGui.beginMultiSelect(
                                    MultiSelectFlags.NONE, selection.getSize(), itemsCount);
                    selection.applyRequests(io);

                    int requestFocusIndex = -1;
                    if (IkGui.isWindowFocused()) {
                        final TypingSelectRequest req =
                                IkGuiInternal.getTypingSelectRequest(
                                        TypingSelectFlags.ALLOW_SINGLE_CHAR_MODE);
                        if (req != null) {
                            requestFocusIndex =
                                    IkGuiInternal.typingSelectFindMatch(
                                            req,
                                            itemsCount,
                                            index -> EXAMPLE_NAMES[index % EXAMPLE_NAMES.length],
                                            (int) io.navIDItem);
                        }
                    }

                    for (int n = 0; n < itemsCount; ++n) {
                        IkGui.setNextItemSelectionUserData(n);
                        IkGui.selectable(EXAMPLE_NAMES[n], selection.contains(n));
                        if (n == requestFocusIndex) {
                            IkGui.setKeyboardFocusHere(-1);
                        }
                    }
                    io = IkGui.endMultiSelect();
                    selection.applyRequests(io);
                    IkGui.end();
                });
        final var g = ctx.context;
        final java.util.function.IntPredicate only =
                n -> selection.getSize() == 1 && selection.contains(n);
        ctx.setRef("Test Window");
        // "Arugula"
        ctx.itemClick(EXAMPLE_NAMES[1]);
        assertTrue(only.test(1));
        // Start
        ctx.keyChars("b");
        // At this point, the match was found and setKeyboardFocusHere() was called, but we
        // don't have the result yet
        ctx.yieldFrame();
        // Now the nav request succeeded, and the nav ID is "Bamboo Shoots"
        assertEquals(ctx.getID(EXAMPLE_NAMES[4]), g.navID);
        assertEquals(4, (int) g.navLastValidSelectionUserData);
        assertTrue(only.test(4));

        // Amend, "Bean Sprouts"
        ctx.keyChars("ean");
        ctx.yieldFrame();
        assertTrue(only.test(5));

        // Disambiguate, "Beans"
        ctx.keyChars("s");
        ctx.yieldFrame();
        assertTrue(only.test(6));

        // Extra characters
        ctx.keyChars("ccc");
        ctx.yieldFrame();
        assertTrue(only.test(6));
        ctx.sleepNoSkip(2.0f, 0.5f);

        // A new request, "Cabbage"
        ctx.keyChars("c");
        ctx.yieldFrame();
        assertTrue(only.test(15));

        // Single character mode
        ctx.keyChars("c");
        ctx.yieldFrame();
        assertTrue(only.test(16));
        for (int sel = 17; sel < 28; ++sel) {
            // The next match in single character mode
            ctx.keyChars("c");
            ctx.yieldFrame();
            assertTrue(only.test(sel), "selection " + sel);
        }

        // Single character mode wraps
        ctx.keyChars("c");
        ctx.yieldFrame();
        assertTrue(only.test(15));

        // Quickly leave single character mode
        ctx.keyChars("a");
        ctx.yieldFrame();
        assertTrue(only.test(0));

        // Single character to a full match, "AAAB"
        ctx.keyChars("a");
        ctx.keyChars("a");
        ctx.keyChars("b");
        ctx.yieldFrame();
        assertTrue(only.test(28));

        // The first single character skips the current item
        ctx.itemClick(EXAMPLE_NAMES[1]);
        ctx.keyChars("a");
        ctx.yieldFrame();
        assertTrue(only.test(2));
    }

    /** widgets_selectable_span_all_table: SelectableFlags.SPAN_ALL_COLUMNS in a table. */
    @Test
    void testSelectableSpanAllTable() {
        final IkBoolean selected = new IkBoolean(false);
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final int columnCount = 3;
                    IkGui.beginTable(
                            "table",
                            columnCount,
                            TableFlags.NO_SAVED_SETTINGS | TableFlags.REORDERABLE);
                    for (int i = 0; i < columnCount; ++i) {
                        IkGui.tableSetupColumn(Integer.toString(i + 1));
                    }
                    IkGui.tableHeadersRow();
                    IkGui.tableNextRow();

                    IkGui.pushStyleVarFloat2(StyleVariable.FRAME_PADDING, 0, 0);
                    IkGui.tableSetColumnIndex(0);
                    IkGui.button("C1");
                    IkGui.tableSetColumnIndex(1);
                    IkGui.selectable("Selectable", selected, SelectableFlags.SPAN_ALL_COLUMNS);
                    status.querySet(false);
                    IkGui.tableSetColumnIndex(2);
                    IkGui.button("C3");
                    IkGui.popStyleVar();
                    IkGui.endTable();
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final Table table = IkGuiImplTables.tableFindByID(ctx.getID("table"));
        assertNotNull(table);
        for (int i = 0; i < 2; ++i) {
            // The buttons themselves won't be hovered, the selectable will be
            ctx.mouseMove("table/C1");
            assertEquals(1, status.hovered, "C1 " + i);
            ctx.mouseMove("table/C3");
            assertEquals(1, status.hovered, "C3 " + i);

            // Reorder the columns and test again
            ctx.itemDragAndDrop(
                    IkGuiTestContext.tableGetHeaderID(table, "1"),
                    IkGuiTestContext.tableGetHeaderID(table, "2"));
        }
    }

    /**
     * The selectableInput() snippet from upstream issue #2718, which upstream's test suite defines
     * for widgets_selectable_input.
     */
    private static boolean selectableInput(
            String stringID, boolean selected, int selectableFlags, IkString buf) {
        final var g = IkGuiInternal.context;
        final Window window = g.windowCurrent;
        final float posX = window.cursorPosition.x;
        final float posY = window.cursorPosition.y;

        IkGui.pushID(stringID);
        IkGui.pushStyleVarFloat2(
                StyleVariable.ITEM_SPACING,
                g.style.variable.itemSpacing.x,
                g.style.variable.framePadding.y * 2.0f);
        boolean ret =
                IkGui.selectable(
                        "",
                        selected,
                        selectableFlags
                                | SelectableFlags.ALLOW_DOUBLE_CLICK
                                | SelectableFlags.ALLOW_OVERLAP);
        final int selectableID = g.lastItemData.id;
        IkGui.popStyleVar();

        final int id = IkGui.getID("##Input");
        final boolean tempInputIsActive = IkGuiImplInputText.tempInputIsActive(id);
        final boolean tempInputStart = ret && IkGui.isMouseDoubleClicked(MouseButton.LEFT);
        final boolean tempInputStartByEnter =
                IkGui.isItemFocused()
                        && (IkGui.isKeyPressed(Key.ENTER) || IkGui.isKeyPressed(Key.NUMPAD_ENTER));
        if (tempInputIsActive || tempInputStart || tempInputStartByEnter) {
            if (tempInputStartByEnter && !tempInputIsActive) {
                g.navActivateID = id;
                g.navActivateFlags = ActivateFlags.PREFER_INPUT;
            }
            ret =
                    IkGuiImplInputText.tempInputText(
                            new RectFloat(g.lastItemData.rect),
                            id,
                            "##Input",
                            buf,
                            InputTextFlags.NONE,
                            null);
            if (tempInputIsActive && !IkGuiImplInputText.tempInputIsActive(id)) {
                IkGuiImplNav.setFocusID(selectableID, window);
            }
        } else {
            window.drawList.addText(
                    IkGui.getFontSize(),
                    posX,
                    posY,
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT),
                    buf.get());
        }
        IkGui.popID();
        IkGuiInternal.testEngineItemInfo(
                id, stringID, g.lastItemData.statusFlags | ItemStatusFlags.INPUTABLE);
        return ret;
    }

    /** widgets_selectable_input: the selectableInput() snippet from upstream issue #2718. */
    @Test
    void testSelectableInput() {
        final IkString str = new IkString(256);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    selectableInput("obj1", false, SelectableFlags.NONE, str);
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        ctx.itemDoubleClick("obj1");
        ctx.yieldFrame();
        ctx.keyChars("Hello!");
        ctx.keyPress(Key.ENTER);
        assertEquals("Hello!", str.get());
        assertEquals(ctx.getID("obj1"), g.navID);
    }

    /** The slider test range for a data type, like upstream's GetSliderTestRanges(). */
    private static Number[] getSliderTestRange(SliderDataType type) {
        return switch (type) {
            case BYTE -> new Number[] {Byte.MIN_VALUE, Byte.MAX_VALUE};
            case SHORT -> new Number[] {Short.MIN_VALUE, Short.MAX_VALUE};
            case INT -> new Number[] {Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2};
            case LONG -> new Number[] {Long.MIN_VALUE / 2, Long.MAX_VALUE / 2};
                // Floating point types don't use their limits, because widgets may not be able to
                // display them, since rounding with the format is lossy
            case FLOAT -> new Number[] {-1_000_000_000.0f, 1_000_000_000.0f};
            case DOUBLE -> new Number[] {-1_000_000_000.0, 1_000_000_000.0};
        };
    }

    private static Object newSliderData(SliderDataType type) {
        return switch (type) {
            case BYTE -> new byte[1];
            case SHORT -> new short[1];
            case INT -> new int[1];
            case LONG -> new long[1];
            case FLOAT -> new float[1];
            case DOUBLE -> new double[1];
        };
    }

    private static int compareSliderValues(SliderDataType type, Number a, Number b) {
        return type.isFloatingPoint()
                ? Double.compare(a.doubleValue(), b.doubleValue())
                : Long.compare(a.longValue(), b.longValue());
    }

    /**
     * widgets_slider_ranges: sliders with inverted ranges. The port only has signed data types, so
     * the unsigned types are left out.
     */
    @Test
    void testSliderRanges() {
        final int outOfRange = 1;
        final int midOfRange = 2;
        final SliderDataType[] types = SliderDataType.values();
        final Object[] values = new Object[types.length];
        final Number[][] ranges = new Number[types.length][];
        final int[][] resultFlags = new int[types.length][2];
        for (int t = 0; t < types.length; ++t) {
            values[t] = newSliderData(types[t]);
            ranges[t] = getSliderTestRange(types[t]);
        }
        ctx.setGui(
                () -> {
                    // Submit sliders for each data type, with and without an inverted range
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    for (int t = 0; t < types.length; ++t) {
                        final SliderDataType type = types[t];
                        final Number min = ranges[t][0];
                        final Number max = ranges[t][1];
                        for (int invert = 0; invert < 2; ++invert) {
                            final String label = type.name() + (invert == 0 ? '+' : '-');
                            if (invert != 0) {
                                IkGui.sliderScalar(label, type, values[t], max, min);
                            } else {
                                IkGui.sliderScalar(label, type, values[t], min, max);
                            }
                            final Number cur = (Number) java.lang.reflect.Array.get(values[t], 0);
                            int flags = 0;
                            if (compareSliderValues(type, cur, min) < 0
                                    || compareSliderValues(type, cur, max) > 0) {
                                flags |= outOfRange;
                            }
                            if (compareSliderValues(type, cur, min) > 0
                                    && compareSliderValues(type, cur, max) < 0) {
                                flags |= midOfRange;
                            }
                            resultFlags[t][invert] = flags;
                            IkGui.sameLine();
                            IkGui.text("R:" + flags);
                        }
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        for (int t = 0; t < types.length; ++t) {
            final SliderDataType type = types[t];
            for (int invert = 0; invert < 2; ++invert) {
                final Number left = invert != 0 ? ranges[t][1] : ranges[t][0];
                final Number right = invert != 0 ? ranges[t][0] : ranges[t][1];
                final String label = type.name() + (invert == 0 ? '+' : '-');
                for (int side = 0; side < 2; ++side) {
                    final String d = label + " side " + side;
                    // Click on the center
                    ctx.mouseMove(label);
                    ctx.mouseDown(MouseButton.LEFT);
                    // The value isn't out of range, and is between the min and max
                    assertEquals(0, resultFlags[t][invert] & outOfRange, d);
                    assertNotEquals(0, resultFlags[t][invert] & midOfRange, d);

                    // Drag to the left or right edge
                    ctx.mouseMoveToEdge(label, side != 0);
                    ctx.mouseUp(MouseButton.LEFT);

                    final Number cur = (Number) java.lang.reflect.Array.get(values[t], 0);
                    final String values2 = d + " cur " + cur + " min " + left + " max " + right;
                    // The value isn't out of range, and isn't between the min and max
                    assertEquals(0, resultFlags[t][invert] & outOfRange, values2);
                    assertEquals(0, resultFlags[t][invert] & midOfRange, values2);
                    assertEquals(
                            0, compareSliderValues(type, cur, side == 0 ? left : right), values2);
                }
            }
        }
    }

    /** widgets_slider_logarithmic: a logarithmic slider. */
    @Test
    void testSliderLogarithmic() {
        final float[] value = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.setNextItemWidth(400);
                    IkGui.pushStyleVarFloat(StyleVariable.GRAB_MIN_SIZE, 20.0f);
                    IkGui.sliderFloat(
                            "slider", value, -10.0f, 10.0f, "%.2f", SliderFlags.LOGARITHMIC);
                    IkGui.popStyleVar();
                    IkGui.text(String.format("%.4f", value[0]));
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("slider");
        ctx.yieldFrame();
        assertEquals(0.0f, value[0]);

        ctx.mouseMoveToEdge("slider", true);
        ctx.mouseClick(MouseButton.LEFT);
        ctx.yieldFrame();
        assertEquals(10.0f, value[0]);

        ctx.mouseMoveToEdge("slider", false);
        ctx.mouseClick(MouseButton.LEFT);
        ctx.yieldFrame();
        assertEquals(-10.0f, value[0]);

        // Drag a bit
        ctx.itemClick("slider");
        final float[] offsets = {50.0f, 100.0f, 150.0f, 190.0f};
        final float[] expected = {0.06f, 0.35f, 2.11f, 8.97f};
        for (float sign : new float[] {-1.0f, 1.0f}) {
            for (int i = 0; i < offsets.length; ++i) {
                final String d = "offset " + sign * offsets[i];
                ctx.itemDragWithDelta(ctx.getID("slider"), sign * offsets[i], 0.0f);
                // Upstream notes the exact values depend on the grab size, hence the style var
                assertTrue(
                        value[0] > sign * expected[i] - (expected[i] * 0.20f), d + " " + value[0]);
                assertTrue(
                        value[0] < sign * expected[i] + (expected[i] * 0.20f), d + " " + value[0]);
            }
        }
    }

    /** widgets_slider_nav: keyboard navigation of sliders. */
    @Test
    void testSliderNav() {
        final float[] floatValue = {0};
        final int[] intValue = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.sliderFloat("Slider 1", floatValue, -100.0f, 100.0f, "%.2f");
                    IkGui.sliderInt("Slider 2", intValue, 0, 100);
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");

        ctx.itemNavActivate("Slider 1");
        assertEquals(0.0f, floatValue[0]);
        assertEquals(0, intValue[0]);
        assertEquals(ctx.getID("Slider 1"), g.navID);

        // Upstream notes that the step values aren't exposed and may change
        final float floatStep = 2.0f;
        final float floatStepSlow = 0.2f;
        final float floatStart = floatValue[0];
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(floatStart - floatStep, floatValue[0]);
        ctx.keyDown(KeyChord.ofMods(KeyModFlags.CTRL));
        ctx.keyPress(Key.ARROW_RIGHT);
        ctx.keyUp(KeyChord.ofMods(KeyModFlags.CTRL));
        assertEquals(floatStart - floatStep + floatStepSlow, floatValue[0]);

        ctx.keyPress(Key.ARROW_DOWN);
        ctx.keyPress(Key.SPACE);
        assertEquals(ctx.getID("Slider 2"), g.navID);

        final int intStep = 1;
        final int intStepSlow = 1;
        final int intStart = intValue[0];
        ctx.keyPress(Key.ARROW_RIGHT);
        // Upstream compares against the float value here, which is equal to the int start in
        // effect since both start at 0
        assertEquals(intStart + intStep, intValue[0]);
        ctx.keyDown(KeyChord.ofMods(KeyModFlags.CTRL));
        ctx.keyPress(Key.ARROW_LEFT);
        ctx.keyUp(KeyChord.ofMods(KeyModFlags.CTRL));
        assertEquals(intStart + intStep - intStepSlow, intValue[0]);

        ctx.keyPress(Key.ARROW_UP);
        assertEquals(ctx.getID("Slider 1"), g.navID);
    }

    /** widgets_slider_format: digits around the format specifier don't affect the value. */
    @Test
    void testSliderFormat() {
        final int[] int1 = {0};
        final int[] int2 = {0};
        final float[] float1 = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.sliderInt("Int", int1, 10, 100, "22%d00");
                    // Upstream uses "22%'d", a printf thousands separator flag, which Java writes
                    // as ","
                    IkGui.sliderInt("Int2", int2, 10, 100, "22%,d");
                    IkGui.sliderFloat("Float", float1, 10.0f, 100.0f, "22%.0f00");
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.mouseMoveToEdge("Int", true);
        ctx.mouseClick(MouseButton.LEFT);
        assertEquals(100, int1[0]);
        ctx.mouseMoveToEdge("Int2", true);
        ctx.mouseClick(MouseButton.LEFT);
        assertEquals(100, int2[0]);
        ctx.mouseMoveToEdge("Float", true);
        ctx.mouseClick(MouseButton.LEFT);
        assertEquals(100.0f, float1[0]);
    }

    /** widgets_popup_positioning: tooltip and popup positioning near the viewport edges. */
    @Test
    void testPopupPositioning() {
        final Vector2f size = new Vector2f(50, 50);
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.ALWAYS_AUTO_RESIZE
                                    | WindowFlags.NO_NAV);
                    IkGui.button("Tooltip", 100, 0);
                    if (IkGui.isItemHovered()) {
                        IkGui.beginTooltip();
                        IkGui.invisibleButton("Space", size);
                        IkGui.endTooltip();
                    }
                    if (IkGui.button("Popup", 100, 0)) {
                        IkGui.openPopup("Popup");
                    }
                    if (IkGui.beginPopup("Popup")) {
                        IkGui.invisibleButton("Space", size);
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        final var style = g.style.variable;
        // Upstream uses the main monitor's work area, which matches the main viewport here
        final Vector2f viewportPos = IkGui.getMainViewport().workPosition;
        final Vector2f viewportSize = IkGui.getMainViewport().workSize;

        // {x, y, pivot x, pivot y}, and the expected default, wide and tall directions
        final float[][] positions = {
            // Top left corner
            {viewportPos.x, viewportPos.y, 0.0f, 0.0f},
            // Top edge
            {viewportPos.x + viewportSize.x * 0.5f, viewportPos.y, 0.5f, 0.0f},
            // Top right corner
            {viewportPos.x + viewportSize.x, viewportPos.y, 1.0f, 0.0f},
            // Right edge
            {viewportPos.x + viewportSize.x, viewportPos.y + viewportSize.y * 0.5f, 1.0f, 0.5f},
            // Bottom right corner
            {viewportPos.x + viewportSize.x, viewportPos.y + viewportSize.y, 1.0f, 1.0f},
            // Bottom edge
            {viewportPos.x + viewportSize.x * 0.5f, viewportPos.y + viewportSize.y, 0.5f, 1.0f},
            // Bottom left corner
            {viewportPos.x, viewportPos.y + viewportSize.y, 0.0f, 1.0f},
            // Left edge
            {viewportPos.x, viewportPos.y + viewportSize.y * 0.5f, 0.0f, 0.5f},
        };
        final Direction[][] directions = {
            {Direction.RIGHT, Direction.DOWN, Direction.RIGHT},
            {Direction.RIGHT, Direction.DOWN, Direction.RIGHT},
            {Direction.DOWN, Direction.DOWN, Direction.LEFT},
            {Direction.DOWN, Direction.DOWN, Direction.LEFT},
            {Direction.UP, Direction.UP, Direction.LEFT},
            {Direction.RIGHT, Direction.UP, Direction.RIGHT},
            {Direction.RIGHT, Direction.UP, Direction.RIGHT},
            {Direction.RIGHT, Direction.DOWN, Direction.RIGHT},
        };

        ctx.setRef("Test Window");
        for (int variant = 0; variant < 2; ++variant) {
            final String buttonName = variant != 0 ? "Popup" : "Tooltip";
            // Force the tooltip to be created, so we can get it
            ctx.itemClick(buttonName);
            final Window tooltip =
                    variant != 0 ? g.navFocusedWindow : ctx.getWindowByRef("//##Tooltip_00");
            assertNotNull(tooltip, buttonName);

            for (int c = 0; c < positions.length; ++c) {
                final String d = buttonName + " case " + c;
                size.set(50, 50);
                ctx.windowMove(
                        "", positions[c][0], positions[c][1], positions[c][2], positions[c][3]);
                ctx.itemClick(buttonName);

                // The default location
                assertEquals(ctx.getID(buttonName), g.hoveredIDPreviousFrame, d);
                assertEquals(directions[c][0], tooltip.autoPosLastDirection, d);

                // A wide tooltip, first just wide enough to fit, then wider than the viewport.
                // The location shouldn't change once it is too wide.
                for (int j = 0; j < 2; ++j) {
                    size.set(
                            (j * 0.25f * viewportSize.x)
                                    + (viewportSize.x
                                            - (style.windowPadding.x
                                                            + style.displaySafeAreaPadding.x)
                                                    * 2),
                            50);
                    ctx.itemClick(buttonName);
                    assertEquals(directions[c][1], tooltip.autoPosLastDirection, d + " wide " + j);
                }

                // A tall tooltip, the same way
                for (int j = 0; j < 2; ++j) {
                    size.set(
                            50,
                            (j * 0.25f * viewportSize.x)
                                    + (viewportSize.y
                                            - (style.windowPadding.y
                                                            + style.displaySafeAreaPadding.y)
                                                    * 2));
                    ctx.itemClick(buttonName);
                    assertEquals(directions[c][2], tooltip.autoPosLastDirection, d + " tall " + j);
                }

                assertEquals(ctx.getID(buttonName), g.hoveredIDPreviousFrame, d);
            }
        }
    }

    /** widgets_disabled: disabled items set the hovered ID and take clicks. */
    @Test
    void testDisabled() {
        final boolean[] widgetsDisabled = {false};
        final boolean[] activated = new boolean[4];
        final boolean[] hovered = new boolean[4];
        final boolean[] hoveredDisabled = new boolean[4];
        final Runnable reset =
                () -> {
                    widgetsDisabled[0] = false;
                    java.util.Arrays.fill(activated, false);
                    java.util.Arrays.fill(hovered, false);
                    java.util.Arrays.fill(hoveredDisabled, false);
                };
        final boolean[] firstFrame = {true};
        ctx.setGui(
                () -> {
                    ctx.showApp();
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.ALWAYS_AUTO_RESIZE
                                    | WindowFlags.MENU_BAR);
                    if (IkGui.beginMenuBar()) {
                        if (IkGui.beginMenu("Menu 1")) {
                            if (IkGui.beginMenu("Menu Enabled")) {
                                IkGui.menuItem("Item");
                                IkGui.endMenu();
                            }
                            if (IkGui.beginMenu("Menu Disabled", false)) {
                                IkGui.menuItem("Item");
                                IkGui.endMenu();
                            }
                            IkGui.endMenu();
                        }
                        IkGui.endMenuBar();
                    }

                    final IkBoolean disabledToggle = new IkBoolean(widgetsDisabled[0]);
                    IkGui.checkbox("WidgetsDisabled", disabledToggle);
                    widgetsDisabled[0] = disabledToggle.get();
                    IkGui.selectable("Enabled A");

                    final boolean disabled = widgetsDisabled[0];
                    if (disabled) {
                        IkGui.beginDisabled();
                    }
                    final String[] names = {"Button", "DragFloat", "Selectable"};
                    for (int index = 0; index < 3; ++index) {
                        final boolean ret =
                                switch (index) {
                                    case 0 -> IkGui.button("Button");
                                    case 1 -> IkGui.dragFloat("DragFloat", new float[1]);
                                    default -> IkGui.selectable("Selectable", false, 0);
                                };
                        activated[index] |= ret;
                        hovered[index] |= IkGui.isItemHovered();
                        hoveredDisabled[index] |=
                                IkGui.isItemHovered(HoveredFlags.ALLOW_WHEN_DISABLED);
                        if (firstFrame[0]) {
                            // Make sure the widget has an ID
                            ctx.checkEquals(
                                    IkGui.getID(names[index]), IkGui.getItemID(), names[index]);
                        }
                        IkGui.sameLine();
                        IkGui.text(hovered[index] + "/" + hoveredDisabled[index]);
                    }
                    if (disabled) {
                        IkGui.endDisabled();
                    }

                    activated[3] |=
                            IkGui.selectable(
                                    "SelectableFlag",
                                    false,
                                    disabled ? SelectableFlags.DISABLED : 0);
                    hovered[3] |= IkGui.isItemHovered();
                    hoveredDisabled[3] |= IkGui.isItemHovered(HoveredFlags.ALLOW_WHEN_DISABLED);
                    if (firstFrame[0]) {
                        ctx.checkEquals(
                                IkGui.getID("SelectableFlag"), IkGui.getItemID(), "SelectableFlag");
                    }
                    IkGui.sameLine();
                    IkGui.text(hovered[3] + "/" + hoveredDisabled[3]);

                    IkGui.selectable("Enabled B");

                    // A simple nested check
                    final var style = ctx.context.style.variable;
                    final float alpha0 = style.alpha;
                    IkGui.beginDisabled();
                    ctx.checkEquals(alpha0 * style.disabledAlpha, style.alpha, "disabled alpha");
                    IkGui.beginDisabled();
                    ctx.checkEquals(alpha0 * style.disabledAlpha, style.alpha, "nested alpha");
                    IkGui.endDisabled();
                    ctx.checkEquals(alpha0 * style.disabledAlpha, style.alpha, "disabled alpha 2");
                    IkGui.endDisabled();
                    ctx.checkEquals(alpha0, style.alpha, "restored alpha");

                    IkGui.end();
                    firstFrame[0] = false;
                });
        final var g = ctx.context;
        final String[] disabledItems = {"Button", "DragFloat", "Selectable", "SelectableFlag"};
        final Window window = ctx.getWindowByRef("Test Window");
        ctx.setRef("Test Window");
        widgetsDisabled[0] = true;

        // Navigating over a disabled menu
        ctx.menuHover("Menu 1/Menu Enabled/Item");
        assertNotNull(g.navFocusedWindow);
        ctx.menuHover("Menu 1/Menu Disabled");
        assertNotNull(g.navFocusedWindow);
        assertEquals(Hash.getID("###Menu_00"), Hash.getID(g.navFocusedWindow.name));

        // Navigating over a disabled item
        ctx.itemClick("Enabled A");
        assertEquals(ctx.getID("Enabled A"), g.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        // The disabled items were skipped
        assertEquals(ctx.getID("Enabled B"), g.navID);
        ctx.keyPress(Key.ESCAPE);

        // Clicking a disabled item
        for (int i = 0; i < disabledItems.length; ++i) {
            final String d = disabledItems[i];
            ctx.mouseMove(disabledItems[i]);
            reset.run();
            widgetsDisabled[0] = true;
            ctx.itemClick(disabledItems[i]);
            // Not clicked, because it's disabled
            assertFalse(activated[i], d);
            // Not reported as hovered, because it's disabled
            assertFalse(hovered[i], d);
            // Hovered with HoveredFlags.ALLOW_WHEN_DISABLED
            assertTrue(hoveredDisabled[i], d);
            // The hovered ID is set even when disabled
            assertEquals(ctx.getID(disabledItems[i]), g.hoveredID, d);
        }

        // Clicking a disabled item takes focus
        for (String item : disabledItems) {
            ctx.windowFocus("//" + IkGuiTestContext.DEMO);
            ctx.itemClick(item);
            assertSame(window, g.navFocusedWindow, item);
        }

        // Dragging a disabled item
        final Vector2f windowPos = new Vector2f(window.position);
        for (String item : disabledItems) {
            ctx.mouseMove(item);
            reset.run();
            widgetsDisabled[0] = true;
            ctx.itemDragWithDelta(ctx.getID(item), 30, 0);
            // Disabled items consume the click events
            assertEquals(windowPos, window.position, item);
        }

        // The active ID is cleared when the widget gets disabled while held
        for (String item : disabledItems) {
            widgetsDisabled[0] = false;
            ctx.mouseMove(item);
            ctx.mouseDown(MouseButton.LEFT);
            // The enabled item is active
            assertEquals(ctx.getID(item), g.activeID, item);
            widgetsDisabled[0] = true;
            ctx.yieldFrame();
            // Disabling the item while active deactivates it
            assertEquals(0, g.activeID, item);
            ctx.mouseUp(MouseButton.LEFT);
        }
    }

    /** widgets_disabled_2: beginDisabled() and endDisabled(). */
    @Test
    void testDisabled2() {
        final String[] names = {"A", "B", "C", "D", "E", "F"};
        final IkGuiTestContext.ItemStatus[] statuses = new IkGuiTestContext.ItemStatus[6];
        final int[] flagsBegin = new int[6];
        final int[] flagsEnd = new int[6];
        final float[] alphaBegin = new float[6];
        final float[] alphaEnd = new float[6];
        for (int i = 0; i < 6; ++i) {
            statuses[i] = new IkGuiTestContext.ItemStatus();
        }
        ctx.setGui(
                () -> {
                    final var g = ctx.context;
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.ALWAYS_AUTO_RESIZE
                                    | WindowFlags.MENU_BAR);
                    final int[] index = {0};
                    final java.util.function.Consumer<Boolean> beginDisabled =
                            disabled -> {
                                flagsBegin[index[0]] = g.currentItemFlags;
                                alphaBegin[index[0]] = g.disabledAlphaBackup;
                                IkGui.beginDisabled(disabled);
                                // Add a random flag to mix things up
                                IkGui.pushItemFlag(ItemFlags.NO_NAV, (index[0] % 2) == 0);
                                index[0]++;
                            };
                    final Runnable endDisabled =
                            () -> {
                                index[0]--;
                                IkGui.popItemFlag();
                                IkGui.endDisabled();
                                flagsEnd[index[0]] = g.currentItemFlags;
                                alphaEnd[index[0]] = g.disabledAlphaBackup;
                            };
                    final java.util.function.IntConsumer showStatus =
                            n -> {
                                IkGui.sameLine();
                                IkGui.text(
                                        statuses[n].hovered
                                                + ","
                                                + statuses[n].hoveredAllowDisabled);
                            };

                    beginDisabled.accept(true);
                    statuses[0].queryInc(IkGui.button("A"));
                    showStatus.accept(0);
                    beginDisabled.accept(true);
                    statuses[1].queryInc(IkGui.button("B"));
                    showStatus.accept(1);
                    endDisabled.run();
                    beginDisabled.accept(false);
                    statuses[2].queryInc(IkGui.button("C"));
                    showStatus.accept(2);
                    endDisabled.run();
                    statuses[3].queryInc(IkGui.button("D"));
                    showStatus.accept(3);
                    endDisabled.run();

                    beginDisabled.accept(true);
                    final boolean ret = IkGui.button("E");
                    endDisabled.run();
                    statuses[4].queryInc(ret);
                    showStatus.accept(4);

                    IkGui.beginDisabled(false);
                    statuses[5].queryInc(IkGui.button("F"));
                    showStatus.accept(5);
                    IkGui.endDisabled();
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        for (int i = 0; i < 5; ++i) {
            final String d = names[i];
            final IkGuiTestContext.ItemStatus status = statuses[i];
            status.clear();
            ctx.itemClick(names[i]);
            // No clicks
            assertEquals(0, status.retValue, d);
            assertEquals(0, status.clicked, d);
            assertTrue(status.hoveredAllowDisabled > 0, d);
            // The hovered ID is set
            assertEquals(ctx.getID(names[i]), g.hoveredID, d);
            // The flags and alpha match between the begin and end calls
            assertEquals(flagsBegin[i], flagsEnd[i], d);
            assertEquals(alphaBegin[i], alphaEnd[i], d);
            ctx.mouseDown(MouseButton.LEFT);
            for (IkGuiTestContext.ItemStatus other : statuses) {
                other.clear();
            }
            ctx.yieldFrame();
            assertEquals(0, status.hovered, d);
            assertEquals(1, status.hoveredAllowDisabled, d);

            // Other disabled items do NOT report as hovered
            for (int other = 0; other < 5; ++other) {
                if (other != i) {
                    assertEquals(0, statuses[other].hovered, d + " other " + names[other]);
                    assertEquals(
                            0, statuses[other].hoveredAllowDisabled, d + " other " + names[other]);
                }
            }
            ctx.mouseUp(MouseButton.LEFT);
        }
        ctx.itemClick("E");
        // Relies on the last item storage, not the current state
        assertEquals(0, statuses[4].hovered);
        ctx.itemClick("F");
        // beginDisabled(false) doesn't prevent clicks
        assertEquals(1, statuses[5].retValue);
        assertEquals(1, statuses[5].clicked);
    }

    /** widgets_disabled_nested: nested functions and alpha values while disabled. */
    @Test
    void testDisabledNested() {
        ctx.setGui(
                () -> {
                    final var g = ctx.context;
                    final java.util.function.DoubleSupplier alpha = () -> g.style.variable.alpha;
                    IkGui.pushStyleVarFloat(StyleVariable.ALPHA, 1.0f);
                    IkGui.pushStyleVarFloat(StyleVariable.DISABLED_ALPHA, 0.4f);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);

                    IkGui.beginDisabled();
                    ctx.check((g.currentItemFlags & ItemFlags.DISABLED) != 0, "disabled");
                    ctx.check(Math.abs(alpha.getAsDouble() - 0.4f) < 1e-6, "disabled alpha");

                    // Tooltips are special
                    IkGui.button("Button");
                    if (IkGui.beginItemTooltip()) {
                        IkGui.text("Hello");
                        ctx.check(
                                (g.currentItemFlags & ItemFlags.DISABLED) == 0,
                                "tooltip not disabled");
                        ctx.check(Math.abs(alpha.getAsDouble() - 1.0f) < 1e-6, "tooltip alpha");
                        IkGui.endTooltip();
                    }

                    IkGui.begin("Nested window call");
                    ctx.check(
                            (g.currentItemFlags & ItemFlags.DISABLED) != 0,
                            "nested window disabled");
                    ctx.check(Math.abs(alpha.getAsDouble() - 1.0f) > 1e-6, "nested window alpha");
                    IkGui.end();
                    ctx.check((g.currentItemFlags & ItemFlags.DISABLED) != 0, "after window");
                    ctx.check(Math.abs(alpha.getAsDouble() - 0.4f) < 1e-6, "after window alpha");

                    IkGui.beginChild("Nested Child", 200, 200);
                    ctx.check((g.currentItemFlags & ItemFlags.DISABLED) != 0, "child disabled");
                    ctx.check(Math.abs(alpha.getAsDouble() - 0.4f) < 1e-6, "child alpha");
                    IkGui.endChild();
                    ctx.check((g.currentItemFlags & ItemFlags.DISABLED) != 0, "after child");
                    ctx.check(Math.abs(alpha.getAsDouble() - 0.4f) < 1e-6, "after child alpha");

                    IkGui.endDisabled();
                    ctx.check((g.currentItemFlags & ItemFlags.DISABLED) == 0, "enabled");
                    ctx.check(Math.abs(alpha.getAsDouble() - 1.0f) < 1e-6, "enabled alpha");

                    IkGui.end();
                    IkGui.popStyleVar();
                    IkGui.popStyleVar();
                });
        // Hover the button so the tooltip shows up
        ctx.mouseMove("Test Window/Button");
        ctx.sleep(1.0f);
        ctx.yieldFrames(2);
    }

    /** widgets_disabled_shortcuts: shortcuts don't trigger disabled items. */
    @Test
    void testDisabledShortcuts() {
        final int[] count = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.beginDisabled();

                    // Case 1: setNextItemShortcut() in a disabled block
                    IkGui.text("Ctrl+S");
                    IkGui.setNextItemShortcut(chord(KeyModFlags.CTRL, Key.S));
                    if (IkGui.button("Button")) {
                        count[0]++;
                    }

                    // Case 2: shortcut() in a disabled block
                    IkGui.text("Ctrl+T");
                    if (IkGui.shortcut(chord(KeyModFlags.CTRL, Key.T))) {
                        count[0]++;
                    }
                    IkGui.endDisabled();

                    // Case 3: setNextItemShortcut() NOT in a disabled block, but with a disabled
                    // item
                    IkGui.setNextItemShortcut(chord(KeyModFlags.CTRL, Key.U));
                    if (IkGui.selectable("Ctrl+U", false, SelectableFlags.DISABLED)) {
                        count[0]++;
                    }

                    IkGui.text("Counter = " + count[0]);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.windowFocus("");
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.S));
        assertEquals(0, count[0]);
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.T));
        assertEquals(0, count[0]);
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.U));
        assertEquals(0, count[0]);
    }

    /** widgets_item_using_mouse_wheel: setItemKeyOwner(MOUSE_WHEEL_Y) prevents scrolling. */
    @Test
    void testItemUsingMouseWheel() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(0, 100, Condition.ALWAYS);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_VERTICAL_SCROLLBAR);
                    IkGui.button("You! Shall! Not! Scroll!");
                    IkGui.setItemKeyOwner(Key.MOUSE_WHEEL_Y);
                    for (int i = 0; i < 10; ++i) {
                        IkGui.text("Line " + (i + 1));
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.scrollToTop("");
        final Window window = ctx.getWindowByRef("");
        assertEquals(0.0f, window.scrollPosition.y);
        ctx.mouseMove("You! Shall! Not! Scroll!");
        ctx.mouseWheel(0, -10.0f);
        assertEquals(0.0f, window.scrollPosition.y);
    }

    /**
     * A splitter between two regions, standing in for upstream's Splitter() test engine utility,
     * which isn't ported. This places a splitter after the first region and lets splitterBehavior()
     * resize both.
     */
    private static void splitter(String id, float[] size1, float[] size2, Axis axis) {
        final Window window = IkGuiInternal.context.windowCurrent;
        final float thickness = 4.0f;
        final Vector2f available = IkGui.getContentRegionAvailable();
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat bb =
                axis == Axis.X
                        ? new RectFloat(x + size1[0], y, x + size1[0] + thickness, y + available.y)
                        : new RectFloat(x, y + size1[0], x + available.x, y + size1[0] + thickness);
        IkGuiImplLayout.splitterBehavior(
                bb, IkGui.getID(id), axis, size1, size2, 10.0f, 10.0f, 0.0f, 0, 0);
    }

    /** widgets_splitter: a splitter resizing two child windows. */
    @Test
    void testSplitter() {
        final float[] size1 = {100};
        final float[] size2 = {100};
        final Axis[] axis = {Axis.X};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 200, Condition.ALWAYS);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    splitter("splitter", size1, size2, axis[0]);
                    if (IkGui.beginChild(
                            "Child 1",
                            axis[0] == Axis.X ? size1[0] : 0.0f,
                            axis[0] == Axis.Y ? size1[0] : 0.0f)) {
                        IkGui.textUnformatted("Child 1");
                    }
                    IkGui.endChild();
                    if (axis[0] == Axis.X) {
                        IkGui.sameLine();
                    }
                    if (IkGui.beginChild(
                            "Child 2",
                            axis[0] == Axis.X ? size2[0] : 0.0f,
                            axis[0] == Axis.Y ? size2[0] : 0.0f)) {
                        IkGui.textUnformatted("Child 1");
                    }
                    IkGui.endChild();
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final Window child1 = ctx.windowInfo("Child 1");
        final Window child2 = ctx.windowInfo("Child 2");
        assertNotNull(child1);
        assertNotNull(child2);
        for (Axis a : new Axis[] {Axis.X, Axis.Y}) {
            axis[0] = a;
            size1[0] = 100;
            size2[0] = 100;
            ctx.yieldFrame();
            for (int i = -1; i < 1; ++i) {
                final String d = a + " " + i;
                ctx.itemDragWithDelta(ctx.getID("splitter"), 50.0f * i, 50.0f * i);
                assertEquals(
                        size1[0] + size2[0],
                        a == Axis.X ? child1.size.x + child2.size.x : child1.size.y + child2.size.y,
                        d);
            }
        }
    }
}
