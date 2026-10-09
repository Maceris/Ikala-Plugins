package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.GuiInputTextCallbackData;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.data.InputTextState;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.enums.SliderDataType;
import com.ikalagaming.graphics.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Tests for text inputs and the scalar inputs built on them. These run headless. */
class IkGuiInputTextTest {

    private Context context;

    /** The bounding box of the last item, recorded by the UI code in tests. */
    private final Vector2f itemMin = new Vector2f();

    private final Vector2f itemMax = new Vector2f();

    /** The return value of the widget in the last frame. */
    private boolean lastResult;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configInputTrickleEventQueue = false;
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

    /**
     * Wrap a widget in a host window, recording its bounding box and result.
     *
     * @param widget The widget, which returns its result.
     * @return The UI for a frame.
     */
    private Runnable window(java.util.function.BooleanSupplier widget) {
        return () -> {
            IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
            IkGui.setNextWindowSize(500, 400, Condition.FIRST_USE_EVER);
            IkGui.begin("Host", null, WindowFlags.NONE);
            lastResult = widget.getAsBoolean();
            IkGui.getItemRectMin(itemMin);
            IkGui.getItemRectMax(itemMax);
            IkGui.end();
        };
    }

    private void click(float x, float y, Runnable ui) {
        context.io.addMousePosEvent(x, y);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame(ui);
    }

    /** Click near the left of the last item, which activates text inputs. */
    private void clickItem(Runnable ui) {
        frame(ui);
        click(itemMin.x + 5, (itemMin.y + itemMax.y) / 2, ui);
    }

    private void type(String text, Runnable ui) {
        context.io.addInputCharacters(text);
        frame(ui);
    }

    /**
     * Press and release a key, while holding modifier keys.
     *
     * @param key The key.
     * @param ui The UI.
     * @param modifiers Modifier keys to hold.
     * @return The widget result on the frame the key was pressed.
     */
    private boolean press(Key key, Runnable ui, Key... modifiers) {
        for (Key modifier : modifiers) {
            context.io.addKeyEvent(modifier, true);
        }
        context.io.addKeyEvent(key, true);
        frame(ui);
        final boolean result = lastResult;
        context.io.addKeyEvent(key, false);
        for (Key modifier : modifiers) {
            context.io.addKeyEvent(modifier, false);
        }
        frame(ui);
        return result;
    }

    @Test
    void testTypingEditsString() {
        final IkString text = new IkString(32);
        final AtomicBoolean changed = new AtomicBoolean(false);
        final Runnable ui =
                window(
                        () -> {
                            final boolean result = IkGui.inputText("text", text);
                            changed.compareAndSet(false, result);
                            return result;
                        });
        clickItem(ui);
        assertNotEquals(0, context.activeID);
        assertFalse(changed.get());

        type("abc", ui);
        assertEquals("abc", text.get());
        assertTrue(changed.get());

        press(Key.BACKSPACE, ui);
        assertEquals("ab", text.get());
        press(Key.ARROW_LEFT, ui);
        type("X", ui);
        assertEquals("aXb", text.get());
        press(Key.HOME, ui);
        press(Key.DELETE, ui);
        assertEquals("Xb", text.get());
    }

    @Test
    void testSelectAllAndReplace() {
        final IkString text = new IkString("hello world", 32);
        final Runnable ui = window(() -> IkGui.inputText("text", text));
        clickItem(ui);
        press(Key.A, ui, Key.LEFT_CTRL);
        type("x", ui);
        assertEquals("x", text.get());
    }

    @Test
    void testEscapeRevertsAndDeactivates() {
        final IkString text = new IkString("abc", 32);
        final Runnable ui = window(() -> IkGui.inputText("text", text));
        clickItem(ui);
        press(Key.END, ui);
        type("def", ui);
        assertEquals("abcdef", text.get());

        press(Key.ESCAPE, ui);
        assertEquals("abc", text.get());
        assertEquals(0, context.activeID);
    }

    @Test
    void testEscapeClearsAll() {
        final IkString text = new IkString("abc", 32);
        final Runnable ui =
                window(() -> IkGui.inputText("text", text, InputTextFlags.ESCAPE_CLEARS_ALL));
        clickItem(ui);
        final int id = context.activeID;
        press(Key.ESCAPE, ui);
        assertEquals("", text.get());
        assertEquals(id, context.activeID);
        // Escape on empty text deactivates
        press(Key.ESCAPE, ui);
        assertEquals(0, context.activeID);
    }

    @Test
    void testUndoRedo() {
        final IkString text = new IkString(32);
        final Runnable ui = window(() -> IkGui.inputText("text", text));
        clickItem(ui);
        type("a", ui);
        type("b", ui);
        assertEquals("ab", text.get());

        press(Key.Z, ui, Key.LEFT_CTRL);
        assertEquals("a", text.get());
        press(Key.Z, ui, Key.LEFT_CTRL);
        assertEquals("", text.get());
        press(Key.Y, ui, Key.LEFT_CTRL);
        assertEquals("a", text.get());
        press(Key.Z, ui, Key.LEFT_CTRL, Key.LEFT_SHIFT);
        assertEquals("ab", text.get());
    }

    @Test
    void testCapacityLimitsLength() {
        final IkString limited = new IkString(3);
        final Runnable ui = window(() -> IkGui.inputText("text", limited));
        clickItem(ui);
        type("abcdef", ui);
        assertEquals("abc", limited.get());
    }

    @Test
    void testResizableStringGrows() {
        final IkString resizable = new IkString(3);
        resizable.inputData.isResizable = true;
        final Runnable ui = window(() -> IkGui.inputText("text", resizable));
        clickItem(ui);
        type("abcdef", ui);
        assertEquals("abcdef", resizable.get());
        assertTrue(resizable.getBufferSize() >= 6);
    }

    @Test
    void testCharacterFilters() {
        final IkString decimal = new IkString(32);
        final Runnable ui =
                window(() -> IkGui.inputText("text", decimal, InputTextFlags.CHARS_DECIMAL));
        clickItem(ui);
        type("1a2,b+", ui);
        // ',' is turned into the decimal point
        assertEquals("12.+", decimal.get());
    }

    @Test
    void testUppercaseAndNoBlank() {
        final IkString text = new IkString(32);
        final Runnable ui =
                window(
                        () ->
                                IkGui.inputText(
                                        "text",
                                        text,
                                        InputTextFlags.CHARS_UPPERCASE
                                                | InputTextFlags.CHARS_NO_BLANK));
        clickItem(ui);
        type("a b\tc", ui);
        assertEquals("ABC", text.get());
    }

    @Test
    void testCharFilterCallback() {
        final IkString text = new IkString(32);
        final GuiInputTextCallback onlyVowels =
                new GuiInputTextCallback() {
                    @Override
                    public void accept(GuiInputTextCallbackData data) {
                        if ("aeiou".indexOf(data.getEventChar()) < 0) {
                            data.setEventChar(0);
                        } else {
                            data.setEventChar(Character.toUpperCase(data.getEventChar()));
                        }
                    }
                };
        final Runnable ui =
                window(
                        () ->
                                IkGui.inputText(
                                        "text",
                                        text,
                                        InputTextFlags.CALLBACK_CHAR_FILTER,
                                        onlyVowels));
        clickItem(ui);
        type("banana", ui);
        assertEquals("AAA", text.get());
    }

    @Test
    void testEditCallbackCanModifyText() {
        final IkString text = new IkString(32);
        final AtomicInteger edits = new AtomicInteger();
        final GuiInputTextCallback callback =
                new GuiInputTextCallback() {
                    @Override
                    public void accept(GuiInputTextCallbackData data) {
                        edits.incrementAndGet();
                        if (data.getBuf().endsWith("!")) {
                            data.insertChars(data.getBufTextLen(), "?");
                        }
                    }
                };
        final Runnable ui =
                window(() -> IkGui.inputText("text", text, InputTextFlags.CALLBACK_EDIT, callback));
        clickItem(ui);
        type("a", ui);
        assertEquals(1, edits.get());
        type("!", ui);
        assertEquals("a!?", text.get());
    }

    @Test
    void testEnterReturnsTrue() {
        final IkString text = new IkString(32);
        final Runnable ui =
                window(() -> IkGui.inputText("text", text, InputTextFlags.ENTER_RETURNS_TRUE));
        clickItem(ui);
        type("hi", ui);
        assertFalse(lastResult);
        assertTrue(press(Key.ENTER, ui));
        assertEquals("hi", text.get());
        assertEquals(0, context.activeID);
    }

    @Test
    void testMultilineEnterInsertsNewline() {
        final IkString text = new IkString(64);
        final Runnable ui = window(() -> IkGui.inputTextMultiline("text", text));
        clickItem(ui);
        assertNotEquals(0, context.activeID);
        type("one", ui);
        press(Key.ENTER, ui);
        type("two", ui);
        assertEquals("one\ntwo", text.get());

        // Up moves to the previous line, keeping the column
        press(Key.ARROW_UP, ui);
        type("X", ui);
        assertEquals("oneX\ntwo", text.get());

        // Ctrl+Enter validates
        press(Key.ENTER, ui, Key.LEFT_CTRL);
        assertEquals(0, context.activeID);
    }

    @Test
    void testLiveEditOffAppliesOnValidate() {
        final IkString text = new IkString(32);
        final Runnable ui =
                window(
                        () -> {
                            IkGui.pushItemFlag(
                                    com.ikalagaming.graphics.gui.flags.ItemFlags
                                            .LIVE_EDIT_ON_INPUT_TEXT,
                                    false);
                            final boolean result = IkGui.inputText("text", text);
                            IkGui.popItemFlag();
                            return result;
                        });
        clickItem(ui);
        type("abc", ui);
        assertEquals("", text.get());
        press(Key.ENTER, ui);
        assertEquals("abc", text.get());
    }

    @Test
    void testCopyPaste() {
        final IkString text = new IkString("copy me", 32);
        final IkString other = new IkString(32);
        final Runnable ui =
                window(
                        () -> {
                            IkGui.inputText("text", text);
                            return IkGui.inputText("other", other);
                        });
        frame(ui);
        // Activate the first input, which is above the last item
        final float lineHeight = itemMax.y - itemMin.y;
        click(itemMin.x + 5, itemMin.y - lineHeight / 2, ui);
        press(Key.A, ui, Key.LEFT_CTRL);
        press(Key.C, ui, Key.LEFT_CTRL);
        assertEquals("copy me", IkGui.getClipboardText());

        clickItem(ui);
        press(Key.V, ui, Key.LEFT_CTRL);
        assertEquals("copy me", other.get());
    }

    @Test
    void testPasswordCannotBeCopied() {
        final IkString text = new IkString("secret", 32);
        IkGui.setClipboardText("unchanged");
        final Runnable ui = window(() -> IkGui.inputText("pw", text, InputTextFlags.PASSWORD));
        clickItem(ui);
        press(Key.A, ui, Key.LEFT_CTRL);
        press(Key.C, ui, Key.LEFT_CTRL);
        assertEquals("unchanged", IkGui.getClipboardText());
        assertEquals("******", InputTextState.passwordText(text.get()));
    }

    @Test
    void testReadOnlyCannotBeEdited() {
        final IkString text = new IkString("fixed", 32);
        final Runnable ui = window(() -> IkGui.inputText("text", text, InputTextFlags.READ_ONLY));
        clickItem(ui);
        type("abc", ui);
        press(Key.BACKSPACE, ui);
        assertEquals("fixed", text.get());
    }

    @Test
    void testInputIntParsesOnValidate() {
        final int[] value = {5};
        final Runnable ui = window(() -> IkGui.inputInt("int", value, 0));
        clickItem(ui);
        // Clicking selects all, so typing replaces the value
        type("42", ui);
        // Scalars only apply the value when deactivated by default
        assertEquals(5, value[0]);
        assertTrue(press(Key.ENTER, ui));
        assertEquals(42, value[0]);
    }

    @Test
    void testInputFloatParsesWhenDeactivatedByClickingAway() {
        final float[] value = {1.0f};
        final Runnable ui = window(() -> IkGui.inputFloat("float", value));
        clickItem(ui);
        type("2.5", ui);
        // Click on empty space in the window to deactivate
        click(itemMin.x + 5, itemMax.y + 100, ui);
        assertEquals(0, context.activeID);
        assertEquals(2.5f, value[0], 0.0001f);
    }

    @Test
    void testInputIntStepButtons() {
        final int[] value = {5};
        final Runnable ui = window(() -> IkGui.inputInt("int", value, 1, 10, 0));
        frame(ui);
        // The last item is the group, the "+" button is just before the label
        final float buttonSize = IkGui.getFrameHeight();
        final float spacing = context.style.variable.itemInnerSpacing.x;
        final float labelWidth = IkGui.calcTextSize("int").x;
        final float plusX = itemMax.x - labelWidth - spacing - buttonSize / 2;
        final float y = (itemMin.y + itemMax.y) / 2;
        click(plusX, y, ui);
        assertEquals(6, value[0]);
        click(plusX - buttonSize - spacing, y, ui);
        assertEquals(5, value[0]);
    }

    @Test
    void testCtrlClickSliderEditsAsText() {
        final float[] value = {0.0f};
        final Runnable ui = window(() -> IkGui.sliderFloat("slider", value, 0.0f, 1.0f));
        frame(ui);
        context.io.addKeyEvent(Key.LEFT_CTRL, true);
        click(itemMin.x + 50, (itemMin.y + itemMax.y) / 2, ui);
        context.io.addKeyEvent(Key.LEFT_CTRL, false);
        frame(ui);
        assertEquals(0.0f, value[0], 0.0001f);
        assertTrue(IkGuiImplInputText.tempInputIsActive(context.activeID));

        type("5", ui);
        press(Key.ENTER, ui);
        // Not clamped without CLAMP_ON_INPUT
        assertEquals(5.0f, value[0], 0.0001f);
        assertEquals(0, context.activeID);
    }

    @Test
    void testDataTypeApplyFromText() {
        final int[] ints = {0};
        assertTrue(
                IkGuiImplInputText.dataTypeApplyFromText(
                        "  12abc", SliderDataType.INT, ints, 0, "%d", null));
        assertEquals(12, ints[0]);
        assertTrue(
                IkGuiImplInputText.dataTypeApplyFromText(
                        "FFFFFFFF", SliderDataType.INT, ints, 0, "%08X", null));
        assertEquals(-1, ints[0]);
        assertFalse(
                IkGuiImplInputText.dataTypeApplyFromText(
                        "", SliderDataType.INT, ints, 0, "%d", null));
        assertTrue(
                IkGuiImplInputText.dataTypeApplyFromText(
                        "", SliderDataType.INT, ints, 0, "%d", 0.0));
        assertEquals(0, ints[0]);

        final double[] doubles = {0};
        assertTrue(
                IkGuiImplInputText.dataTypeApplyFromText(
                        "1e3x", SliderDataType.DOUBLE, doubles, 0, "%.3f", null));
        assertEquals(1000.0, doubles[0], 0.0);
        assertFalse(
                IkGuiImplInputText.dataTypeApplyFromText(
                        "abc", SliderDataType.DOUBLE, doubles, 0, "%.3f", null));

        final byte[] bytes = {0};
        IkGuiImplInputText.dataTypeApplyFromText("300", SliderDataType.BYTE, bytes, 0, "%d", null);
        assertEquals(127, bytes[0]);

        assertEquals("%.3f", IkGuiImplInputText.trimFormatDecorations("value: %.3f units"));
        assertEquals("", IkGuiImplInputText.trimFormatDecorations("no value"));
        assertEquals("FFFFFFFF", IkGuiImplSliders.formatScalar(SliderDataType.INT, -1, "%08X"));
    }

    // ---------------------------------------------------------------------------------------
    // Editing state
    // ---------------------------------------------------------------------------------------

    private InputTextState newState(String text, boolean singleLine) {
        final InputTextState state = new InputTextState();
        state.fontSize = context.fontSize;
        state.bufCapacity = 1000;
        state.initialize(singleLine);
        state.setText(text);
        return state;
    }

    @Test
    void testWordMovement() {
        final InputTextState state = newState("hello big, world", true);
        state.onKeyPressed(InputTextState.K_WORD_RIGHT);
        assertEquals(6, state.cursor);
        state.onKeyPressed(InputTextState.K_WORD_RIGHT);
        assertEquals(9, state.cursor);
        state.onKeyPressed(InputTextState.K_WORD_RIGHT);
        assertEquals(11, state.cursor);
        state.onKeyPressed(InputTextState.K_WORD_LEFT);
        assertEquals(9, state.cursor);
        state.onKeyPressed(InputTextState.K_WORD_LEFT | InputTextState.K_SHIFT);
        assertEquals(6, state.cursor);
        assertEquals(9, state.selectStart);
        assertTrue(state.hasSelection());
    }

    @Test
    void testSurrogatePairsAreNotSplit() {
        final String emoji = new String(Character.toChars(0x1F600));
        final InputTextState state = newState("a" + emoji + "b", true);
        state.onKeyPressed(InputTextState.K_RIGHT);
        state.onKeyPressed(InputTextState.K_RIGHT);
        assertEquals(3, state.cursor);
        state.onKeyPressed(InputTextState.K_BACKSPACE);
        assertEquals("ab", state.getText());
        assertEquals(1, state.cursor);
    }

    @Test
    void testOverwriteMode() {
        final InputTextState state = newState("abc", true);
        state.insertMode = true;
        state.onCharPressed('X');
        assertEquals("Xbc", state.getText());
        state.onKeyPressed(InputTextState.K_UNDO);
        assertEquals("abc", state.getText());
    }

    @Test
    void testMultilineRowsAndVerticalMovement() {
        final InputTextState state = newState("ab\ncdef\n", false);
        assertEquals(3, state.getRowCount());
        assertEquals(3, state.getRowStart(1));
        assertEquals(8, state.getRowStart(2));

        state.cursor = 1;
        state.onKeyPressed(InputTextState.K_DOWN);
        assertEquals(4, state.cursor);
        state.onKeyPressed(InputTextState.K_DOWN);
        assertEquals(8, state.cursor);
        state.onKeyPressed(InputTextState.K_UP);
        assertEquals(4, state.cursor);
        state.onKeyPressed(InputTextState.K_LINE_END);
        assertEquals(7, state.cursor);
        state.onKeyPressed(InputTextState.K_LINE_START);
        assertEquals(3, state.cursor);
    }

    @Test
    void testWordWrapRows() {
        final InputTextState state = newState("aaa bbb ccc", false);
        state.wrapWidth =
                com.ikalagaming.graphics.gui.data.DrawList.calcTextWidth(
                        context.fontSize, "aaa bbb ");
        // Wraps after the blank, which stays on the first row
        assertEquals(2, state.getRowCount());
        assertEquals(8, state.getRowStart(1));

        // End goes to the visual end of the row, and is displayed on that row
        state.cursor = 1;
        state.onKeyPressed(InputTextState.K_LINE_END);
        assertEquals(8, state.cursor);
        assertEquals(0, state.getRowIndex(state.cursor, true));
        state.onKeyPressed(InputTextState.K_LINE_START);
        assertEquals(0, state.cursor);

        // Words that are too long are broken
        state.setText("aaaaaaaaaaaa");
        state.wrapWidth =
                com.ikalagaming.graphics.gui.data.DrawList.calcTextWidth(context.fontSize, "aaaaa");
        assertTrue(state.getRowCount() >= 3);
    }

    @Test
    void testReconcileUndoState() {
        final InputTextState state = newState("hello world", true);
        state.reconcileUndoState("hello world", "hello there world");
        state.setText("hello there world");
        state.onKeyPressed(InputTextState.K_UNDO);
        assertEquals("hello world", state.getText());
        state.onKeyPressed(InputTextState.K_REDO);
        assertEquals("hello there world", state.getText());
    }

    @Test
    void testIkString() {
        final IkString text = new IkString("abc", 4);
        text.set("abcdef");
        assertEquals("abcd", text.get());
        text.set("abcdef", true);
        assertEquals("abcdef", text.get());
        assertTrue(text.getBufferSize() >= 6);
        assertEquals(new IkString("x"), new IkString("x", 10));
    }
}
