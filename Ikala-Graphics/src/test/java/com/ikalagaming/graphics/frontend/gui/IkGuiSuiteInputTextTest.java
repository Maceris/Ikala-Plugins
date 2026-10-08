package com.ikalagaming.graphics.frontend.gui;

import static com.ikalagaming.graphics.frontend.gui.IkGuiTestContext.chord;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.frontend.gui.data.GuiInputTextCallbackData;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.data.IkString;
import com.ikalagaming.graphics.frontend.gui.data.InputTextState;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;
import com.ikalagaming.graphics.frontend.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.KeyChord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Input text tests ported from the Dear ImGui test suite (imgui_tests_widgets_inputtext.cpp), which
 * is MIT licensed. Each test notes the name of the upstream test it is ported from.
 *
 * <p>Text lengths are in Java chars rather than UTF-8 bytes.
 */
class IkGuiSuiteInputTextTest {
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

    private InputTextState state() {
        return ctx.context.inputTextState;
    }

    /** widgets_inputtext_basic. */
    @Test
    void testBasic() {
        final IkString buf = new IkString(256);
        final boolean[] readOnly = {false};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(200, 200);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText(
                            "InputText",
                            buf,
                            readOnly[0] ? InputTextFlags.READ_ONLY : InputTextFlags.NONE);
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        for (int step = 0; step < 2; ++step) {
            ctx.context.io.configInputTextEnterKeepActive = step == 1;
            ctx.yieldFrame();

            // Insert
            buf.set("Hello");
            ctx.itemClick("InputText");
            ctx.keyCharsAppendEnter("World123©");
            assertEquals("HelloWorld123©", buf.get());
            assertEquals(14, state().text.length());

            // Delete
            ctx.itemClick("InputText");
            ctx.keyPress(Key.END);
            // Select the last two characters
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_LEFT), 2);
            // Delete the selection and two more characters
            ctx.keyPress(Key.BACKSPACE, 3);
            ctx.keyPress(Key.ENTER);
            assertEquals("HelloWorld", buf.get());
            assertEquals(10, state().text.length());
            // In case of configInputTextEnterKeepActive
            ctx.keyPress(Key.ESCAPE);

            // Insert, cancel
            ctx.itemClick("InputText");
            ctx.keyPress(Key.END);
            ctx.keyChars("XXXXX");
            ctx.keyPress(Key.ESCAPE);
            assertEquals("HelloWorld", buf.get());
            assertEquals(10, state().text.length());

            // Delete, cancel
            ctx.itemClick("InputText");
            ctx.keyPress(Key.END);
            ctx.keyPress(Key.BACKSPACE, 5);
            ctx.keyPress(Key.ESCAPE);
            assertEquals("HelloWorld", buf.get());
            assertEquals(10, state().text.length());

            // Read-only mode
            buf.set("Some read-only text.");
            readOnly[0] = true;
            ctx.yieldFrame();
            ctx.itemClick("InputText");
            ctx.keyCharsAppendEnter("World123");
            assertEquals("Some read-only text.", buf.get());
            assertEquals(20, state().text.length());

            // Disabling read-only while active, while the state's text was unused
            state().clearText();
            ctx.itemClick("InputText");
            readOnly[0] = false;
            ctx.yieldFrames(2);
            assertEquals("Some read-only text.", buf.get());
            assertEquals(20, state().getTextLength());

            // Space as a key (instead of as a character) doesn't conflict with nav activation
            readOnly[0] = false;
            ctx.itemClick("InputText");
            ctx.keyCharsReplace("Hello");
            assertEquals(ctx.getID("InputText"), ctx.context.activeID);
            // Should not add text, should not validate
            ctx.keyPress(Key.SPACE);
            assertEquals(ctx.getID("InputText"), ctx.context.activeID);
            assertEquals("Hello", buf.get());
        }
        assertNotNull(state());
    }

    /**
     * A string that grows as needed, like the std::string inputs upstream.
     *
     * @param text The initial text.
     * @return The string.
     */
    private static IkString resizable(String text) {
        final IkString result = new IkString(text);
        result.inputData.isResizable = true;
        return result;
    }

    /** widgets_inputtext_undo_redo. */
    @Test
    void testUndoRedo() {
        final IkString other = new IkString(256);
        final IkString large = new IkString(10_000);
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(IkGuiInternal.getFontSize() * 50, 0);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.text("length = " + large.getLength());
                    IkGui.inputText("Other", other);
                    IkGui.inputTextMultiline(
                            "InputText", large, -1, IkGuiInternal.getFontSize() * 20);
                    IkGui.end();
                });

        // Start with a 350 characters buffer
        large.set("xxxxxxx abcdefghijklmnopqrstuvwxyz\n".repeat(10));
        assertEquals(350, large.getLength());

        ctx.setRef("Test Window");
        // Make sure the undo state gets cleared
        ctx.itemClick("Other");
        ctx.itemClick("InputText");
        assertEquals(ctx.context.activeID, state().id);
        assertEquals(0, state().getUndoRecordCount());
        assertEquals(0, state().getRedoRecordCount());

        // Select all, copy, paste 3 times
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.A));
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.C));
        // Go to the end, clearing the selection
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.END));
        for (int n = 0; n < 3; ++n) {
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.V));
        }
        assertEquals(350 * 4, large.getLength());
        assertEquals(3, state().getUndoRecordCount());

        // Undo x2
        assertEquals(0, state().getRedoRecordCount());
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        assertEquals(350 * 2, large.getLength());
        assertEquals(1, state().getUndoRecordCount());
        assertEquals(2, state().getRedoRecordCount());

        // Undo x1 should discard a redo record
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        assertEquals(350, large.getLength());
    }

    /** widgets_inputtext_undo_reset. */
    @Test
    void testUndoReset() {
        final IkString str = resizable("");
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText("Field1", str, InputTextFlags.CALLBACK_HISTORY);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Field1");
        ctx.keyCharsAppend("Hello, world!");
        assertTrue(state().getUndoRecordCount() > 0);
        ctx.keyPress(Key.ESCAPE);
        // Changing the contents while inactive resets the undo stack
        str.set("Foobar");
        ctx.itemClick("Field1");
        assertEquals(0, state().getUndoRecordCount());
    }

    /** widgets_inputtext_undo_callback. */
    @Test
    void testUndoCallback() {
        final IkString str = resizable("");
        final GuiInputTextCallback callback =
                new GuiInputTextCallback() {
                    @Override
                    public void accept(GuiInputTextCallbackData data) {
                        if (data.getEventFlag() == InputTextFlags.CALLBACK_HISTORY) {
                            data.insertChars(data.getCursorPos(), ", world!");
                        }
                    }
                };
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText("Field1", str, InputTextFlags.CALLBACK_HISTORY, callback);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Field1");
        ctx.keyCharsAppend("Hello");
        // Trigger a modification from the callback
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals("Hello, world!", str.get());
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        assertEquals("Hello", str.get());
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Y));
        assertEquals("Hello, world!", str.get());
    }

    /** widgets_inputtext_text_ownership. */
    @Test
    void testTextOwnership() {
        final IkString bufUser = new IkString(256);
        final String[] bufVisible = {""};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGuiInternal.logToBuffer(-1);
                    // No label, to simplify the capture
                    IkGui.inputText("##InputText", bufUser);
                    bufVisible[0] = ctx.context.logBuffer.toString();
                    IkGui.logFinish();
                    IkGui.text("Captured: \"" + bufVisible[0] + "\"");
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.yieldFrame();

        assertEquals("{ }", bufVisible[0]);
        bufUser.set("Hello");
        ctx.yieldFrame();
        assertEquals("{ Hello }", bufVisible[0]);
        ctx.itemClick("##InputText");
        ctx.keyCharsAppend("1");
        ctx.yieldFrame();
        assertEquals("Hello1", bufUser.get());
        assertEquals("{ Hello1 }", bufVisible[0]);

        // Because the item is active, it owns the source data
        bufUser.set("Overwritten");
        ctx.yieldFrame();
        assertEquals("Hello1", bufUser.get());
        assertEquals("{ Hello1 }", bufVisible[0]);

        // Reloading the user buffer
        bufUser.set("OverwrittenAgain");
        final InputTextState inputState =
                IkGuiImplInputText.getInputTextState(ctx.getID("##InputText"));
        assertNotNull(inputState);
        inputState.reloadUserBufAndSelectAll();
        ctx.yieldFrame();
        assertEquals("OverwrittenAgain", bufUser.get());
        assertEquals("{ OverwrittenAgain }", bufVisible[0]);

        // Verify the reverted value, the reload shouldn't have overridden the revert
        ctx.keyPress(Key.ESCAPE);
        assertEquals(0, ctx.context.activeID);
        assertEquals("Hello", bufUser.get());

        // The input text state is still holding on to the last active state, so check that
        // inputText() picks up external changes
        bufUser.set("Hello2");
        ctx.yieldFrame();
        assertEquals("Hello2", bufUser.get());
        assertEquals("{ Hello2 }", bufVisible[0]);
    }

    /** widgets_inputtext_id_conflict. */
    @Test
    void testIdConflict() {
        final IkString str = new IkString(256);
        final IkInt value = new IkInt(0);
        final int[] step = {0};
        final int[] frame = {0};
        ctx.setGui(
                () -> {
                    frame[0]++;
                    IkGui.setNextWindowSize(IkGuiInternal.getFontSize() * 50, 0);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    if (step[0] == 0) {
                        if (frame[0] < 50) {
                            IkGui.button("Hello");
                        } else {
                            IkGui.inputText("Hello", str);
                        }
                    }
                    if (step[0] == 1) {
                        if (value.get() == 0) {
                            if (IkGui.beginCombo("Hello", "Previews")) {
                                IkGui.selectable("Dummy");
                                IkGui.endCombo();
                            }
                        } else {
                            IkGui.inputInt("Hello", value, 0, 100, InputTextFlags.CHARS_NO_BLANK);
                        }
                    }
                    if (step[0] == 2) {
                        IkGui.inputTextMultiline("Hello", str);
                    }
                    IkGui.end();
                });

        // Toggling from a button to a text input
        ctx.setRef("Test Window");
        ctx.itemHoldForFrames("Hello", 100);
        ctx.itemClick("Hello");
        InputTextState inputState = IkGuiImplInputText.getInputTextState(ctx.getID("Hello"));
        assertNotNull(inputState);
        assertTrue(inputState.singleLine);

        // Toggling from an int input to a combo
        step[0] = 1;
        value.set(1);
        ctx.yieldFrame();
        ctx.itemClick("Hello");
        ctx.keyCharsReplace("0");
        ctx.yieldFrames(2);

        // Toggling from single to multi-line is a little bit ill-defined
        step[0] = 2;
        ctx.yieldFrame();
        ctx.itemClick("Hello");
        inputState = IkGuiImplInputText.getInputTextState(ctx.getID("Hello"));
        assertNotNull(inputState);
        assertFalse(inputState.singleLine);
    }

    /** widgets_inputtext_tab_double_insertion. */
    @Test
    void testTabDoubleInsertion() {
        final IkString str = new IkString(256);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText("Field", str, InputTextFlags.ALLOW_TAB_INPUT);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Field");
        // A backend that supplies both the tab key and the tab character
        ctx.context.io.addInputCharacter('\t');
        ctx.keyPress(Key.TAB);
        assertEquals("\t", str.get());
    }

    /** widgets_inputtext_esc_revert. */
    @Test
    void testEscRevert() {
        final IkString str = new IkString(256);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText("Field", str);
                    IkGui.end();
                });
        // Upstream also runs this with a mixed value, which it now skips
        for (int step = 0; step < 2; ++step) {
            final String initialValue = step == 0 ? "" : "initial";
            str.set(initialValue);
            ctx.setRef("Test Window");
            ctx.itemInput("Field");
            ctx.keyCharsReplace("text");
            assertEquals("text", str.get());
            // Reset the input to the initial value
            ctx.keyPress(Key.ESCAPE);
            assertEquals(initialValue, str.get());
            ctx.itemInput("Field");
            // Undo
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
            assertEquals("text", str.get());
            // Deactivate, otherwise setting the text in the next step would be ignored
            ctx.keyPress(Key.ENTER);
        }
    }

    /** widgets_inputtext_esc_clear. */
    @Test
    void testEscClear() {
        final IkString str = new IkString(256);
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(200, 200);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final boolean ret =
                            IkGui.inputText("InputText", str, InputTextFlags.ESCAPE_CLEARS_ALL);
                    status.queryInc(ret);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("InputText");
        ctx.keyChars("H");
        ctx.keyChars("ello");
        assertTrue(status.retValue >= 2);
        assertEquals("Hello", str.get());
        status.clear();

        // The first escape press clears the buffer, notifying of a change if any
        ctx.keyPress(Key.ESCAPE);
        assertEquals(1, status.retValue);
        assertEquals(0, status.deactivated);
        assertEquals("", str.get());
        assertEquals(ctx.getID("InputText"), ctx.context.activeID);
        status.clear();

        // The second escape press deactivates the item
        ctx.keyPress(Key.ESCAPE);
        assertEquals(0, status.retValue);
        assertEquals(1, status.deactivated);
        assertEquals("", str.get());
        assertEquals(0, ctx.context.activeID);
    }

    /** widgets_inputtext_cursor. */
    @Test
    void testCursor() {
        final IkString str = resizable("");
        final int lineCount = 10;
        ctx.setGui(
                () -> {
                    final float height = lineCount * 0.5f * IkGuiInternal.getFontSize();
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.inputTextMultiline(
                            "Field", str, 300, height, InputTextFlags.ENTER_RETURNS_TRUE);
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        // The line feed is part of the characters per line
        final int charCountPerLine = 10;
        final StringBuilder text = new StringBuilder();
        for (int n = 0; n < lineCount; ++n) {
            text.append(String.valueOf(n).repeat(charCountPerLine - 1));
            if (n < lineCount - 1) {
                text.append('\n');
            }
        }
        str.set(text.toString());

        ctx.itemInput("Field");

        final InputTextState inputState = IkGuiImplInputText.getInputTextState(ctx.getID("Field"));
        assertNotNull(inputState);
        assertEquals(lineCount, inputState.lineCount);

        final int pageSize = (lineCount / 2) - 1;

        final int cursorPosBeginOfFirstLine = 0;
        final int cursorPosEndOfFirstLine = charCountPerLine - 1;
        final int cursorPosMiddleOfFirstLine = charCountPerLine / 2;
        final int cursorPosEndOfLastLine = str.getLength();
        final int cursorPosBeginOfLastLine = cursorPosEndOfLastLine - charCountPerLine + 1;
        final int cursorPosMiddle = str.getLength() / 2;

        final java.util.function.IntConsumer setCursorPosition =
                cursor -> {
                    inputState.cursor = cursor;
                    inputState.hasPreferredX = false;
                };

        // Do all the tests twice: with no trailing line feed, and with one
        for (int i = 0; i < 2; ++i) {
            final boolean hasTrailingLineFeed = i == 1;
            if (hasTrailingLineFeed) {
                setCursorPosition.accept(cursorPosEndOfLastLine);
                ctx.keyCharsAppend("\n");
            }
            final int eof = str.getLength();

            // Beginning of the text
            setCursorPosition.accept(0);
            ctx.keyPress(Key.ARROW_UP);
            assertEquals(0, inputState.cursor);
            setCursorPosition.accept(0);
            ctx.keyPress(Key.ARROW_LEFT);
            assertEquals(0, inputState.cursor);
            setCursorPosition.accept(0);
            ctx.keyPress(Key.ARROW_DOWN);
            assertEquals(charCountPerLine, inputState.cursor);
            setCursorPosition.accept(0);
            ctx.keyPress(Key.ARROW_RIGHT);
            assertEquals(1, inputState.cursor);

            // End of the first line
            setCursorPosition.accept(cursorPosEndOfFirstLine);
            ctx.keyPress(Key.ARROW_UP);
            assertEquals(cursorPosEndOfFirstLine, inputState.cursor);
            setCursorPosition.accept(cursorPosEndOfFirstLine);
            ctx.keyPress(Key.ARROW_LEFT);
            assertEquals(cursorPosEndOfFirstLine - 1, inputState.cursor);
            setCursorPosition.accept(cursorPosEndOfFirstLine);
            ctx.keyPress(Key.ARROW_DOWN);
            assertEquals(cursorPosEndOfFirstLine + charCountPerLine, inputState.cursor);
            setCursorPosition.accept(cursorPosEndOfFirstLine);
            ctx.keyPress(Key.ARROW_RIGHT);
            assertEquals(cursorPosEndOfFirstLine + 1, inputState.cursor);

            // Beginning of the last line
            setCursorPosition.accept(cursorPosBeginOfLastLine);
            ctx.keyPress(Key.ARROW_UP);
            assertEquals(cursorPosBeginOfLastLine - charCountPerLine, inputState.cursor);
            setCursorPosition.accept(cursorPosBeginOfLastLine);
            ctx.keyPress(Key.ARROW_LEFT);
            assertEquals(cursorPosBeginOfLastLine - 1, inputState.cursor);
            setCursorPosition.accept(cursorPosBeginOfLastLine);
            ctx.keyPress(Key.ARROW_DOWN);
            assertEquals(hasTrailingLineFeed ? eof : cursorPosEndOfLastLine, inputState.cursor);
            setCursorPosition.accept(cursorPosBeginOfLastLine);
            ctx.keyPress(Key.ARROW_RIGHT);
            assertEquals(cursorPosBeginOfLastLine + 1, inputState.cursor);

            // End of the last line
            setCursorPosition.accept(cursorPosEndOfLastLine);
            ctx.keyPress(Key.ARROW_UP);
            assertEquals(cursorPosEndOfLastLine - charCountPerLine, inputState.cursor);
            setCursorPosition.accept(cursorPosEndOfLastLine);
            ctx.keyPress(Key.ARROW_LEFT);
            assertEquals(cursorPosEndOfLastLine - 1, inputState.cursor);
            setCursorPosition.accept(cursorPosEndOfLastLine);
            ctx.keyPress(Key.ARROW_DOWN);
            assertEquals(hasTrailingLineFeed ? eof : cursorPosEndOfLastLine, inputState.cursor);
            setCursorPosition.accept(cursorPosEndOfLastLine);
            ctx.keyPress(Key.ARROW_RIGHT);
            assertEquals(cursorPosEndOfLastLine + (hasTrailingLineFeed ? 1 : 0), inputState.cursor);

            // In the middle of the content
            setCursorPosition.accept(cursorPosMiddle);
            ctx.keyPress(Key.ARROW_UP);
            assertEquals(cursorPosMiddle - charCountPerLine, inputState.cursor);
            setCursorPosition.accept(cursorPosMiddle);
            ctx.keyPress(Key.ARROW_LEFT);
            assertEquals(cursorPosMiddle - 1, inputState.cursor);
            setCursorPosition.accept(cursorPosMiddle);
            ctx.keyPress(Key.ARROW_DOWN);
            assertEquals(cursorPosMiddle + charCountPerLine, inputState.cursor);
            setCursorPosition.accept(cursorPosMiddle);
            ctx.keyPress(Key.ARROW_RIGHT);
            assertEquals(cursorPosMiddle + 1, inputState.cursor);

            // Home/End go to the beginning/end of the line
            setCursorPosition.accept(cursorPosMiddle);
            ctx.keyPress(Key.HOME);
            assertEquals(((lineCount / 2) - 1) * charCountPerLine, inputState.cursor);
            setCursorPosition.accept(cursorPosMiddle);
            ctx.keyPress(Key.END);
            assertEquals((lineCount / 2) * charCountPerLine - 1, inputState.cursor);

            // Ctrl+Home/End go to the beginning/end of the text
            setCursorPosition.accept(cursorPosMiddle);
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.HOME));
            assertEquals(0, inputState.cursor);
            setCursorPosition.accept(cursorPosMiddle);
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.END));
            assertEquals(cursorPosEndOfLastLine + (hasTrailingLineFeed ? 1 : 0), inputState.cursor);

            // Page up/down
            setCursorPosition.accept(cursorPosBeginOfFirstLine);
            ctx.keyPress(Key.PAGE_DOWN);
            assertEquals(
                    cursorPosBeginOfFirstLine + charCountPerLine * pageSize, inputState.cursor);
            ctx.keyPress(Key.PAGE_UP);
            assertEquals(cursorPosBeginOfFirstLine, inputState.cursor);

            setCursorPosition.accept(cursorPosMiddleOfFirstLine);
            ctx.keyPress(Key.PAGE_DOWN);
            assertEquals(
                    cursorPosMiddleOfFirstLine + charCountPerLine * pageSize, inputState.cursor);
            ctx.keyPress(Key.PAGE_DOWN);
            assertEquals(
                    cursorPosMiddleOfFirstLine + charCountPerLine * pageSize * 2,
                    inputState.cursor);
            ctx.keyPress(Key.PAGE_DOWN);
            assertEquals(eof, inputState.cursor);

            // We started paging down from the middle of a line, so even at the end (with X = 0),
            // paging up should bring us one page up to the middle of the line
            final int cursorPosBeginCurrentLine =
                    (inputState.cursor / charCountPerLine) * charCountPerLine;
            ctx.keyPress(Key.PAGE_UP);
            assertEquals(
                    cursorPosBeginCurrentLine
                            - (pageSize * charCountPerLine)
                            + (charCountPerLine / 2),
                    inputState.cursor);
        }

        // Cursor positioning after a new line, broken line indexing may cause problems here
        ctx.keyCharsReplaceEnter("foo");
        assertEquals(4, inputState.cursor);

        // Empty buffer
        ctx.keyCharsReplace("");
        assertEquals(0, inputState.cursor);
        assertEquals(0, inputState.getTextLength());
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(0, inputState.cursor);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(0, inputState.cursor);
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(0, inputState.cursor);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(0, inputState.cursor);

        // A long line followed by a line feed, pressing down
        ctx.keyCharsReplace("Click this text then press down-arrow twice to cause an assert.\n");
        assertEquals(inputState.getTextLength(), inputState.cursor);
        ctx.keyPress(Key.ARROW_DOWN);
    }

    /** widgets_inputtext_cursor_prevnext_words. */
    @Test
    void testCursorPrevNextWords() {
        final IkString str = resizable("Hello world. Foo.bar!!!");
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.inputTextMultiline("Field", str);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Field");
        final InputTextState inputState =
                IkGuiImplInputText.getInputTextState(ctx.getID("//Test Window/Field"));
        assertNotNull(inputState);

        for (int osMode = 0; osMode < 2; ++osMode) {
            ctx.context.io.configMacOSXBehaviors = osMode == 1;
            // For the behavior change to update
            ctx.yieldFrame();
            final boolean isOSX = ctx.context.io.configMacOSXBehaviors;
            final int wordMod = isOSX ? KeyModFlags.ALT : KeyModFlags.CTRL;
            final int chordWordPrev = chord(wordMod, Key.ARROW_LEFT);
            final int chordWordNext = chord(wordMod, Key.ARROW_RIGHT);

            // Set 1
            ctx.keyCharsReplace("Hello world. Foo.bar!!!");
            ctx.keyPress(Key.HOME);
            assertEquals(0, inputState.getCursorPos()); // "|Hello "
            if (!isOSX) {
                // Windows
                ctx.keyPress(chordWordNext);
                assertEquals(6, inputState.getCursorPos()); // "Hello |"
                ctx.keyPress(chordWordPrev);
                assertEquals(0, inputState.getCursorPos()); // "|Hello "
                ctx.keyPress(Key.ARROW_RIGHT, 5);
                assertEquals(5, inputState.getCursorPos()); // "Hello| "
                ctx.keyPress(chordWordNext);
                assertEquals(6, inputState.getCursorPos()); // "Hello |"

                ctx.keyPress(chordWordNext);
                assertEquals(6 + 5, inputState.getCursorPos()); // "Hello world|."
                ctx.keyPress(chordWordNext);
                assertEquals(6 + 5 + 2, inputState.getCursorPos()); // "Hello world. |"

                ctx.keyPress(chordWordNext);
                assertEquals(6 + 5 + 2 + 3, inputState.getCursorPos()); // "Hello world. Foo|."
                ctx.keyPress(chordWordNext);
                assertEquals(6 + 5 + 2 + 3 + 1, inputState.getCursorPos()); // "... Foo.|"

                ctx.keyPress(chordWordNext);
                assertEquals(6 + 5 + 2 + 3 + 1 + 3, inputState.getCursorPos()); // "Foo.bar|"

                ctx.keyPress(chordWordNext);
                assertEquals(6 + 5 + 2 + 3 + 1 + 3 + 3, inputState.getCursorPos()); // "bar!!!|"

                ctx.keyPress(Key.END);
                ctx.keyPress(chordWordPrev);
                assertEquals(6 + 5 + 2 + 3 + 1 + 3, inputState.getCursorPos()); // "bar|!!!"

                ctx.keyPress(chordWordPrev);
                assertEquals(6 + 5 + 2 + 3 + 1, inputState.getCursorPos()); // "Foo.|bar!!!"

                ctx.keyPress(chordWordPrev);
                assertEquals(6 + 5 + 2 + 3, inputState.getCursorPos()); // "Foo|.bar!!!"

                ctx.keyPress(chordWordPrev);
                assertEquals(6 + 5 + 2, inputState.getCursorPos()); // "world. |Foo.bar!!!"

                ctx.keyPress(chordWordPrev);
                assertEquals(6 + 5, inputState.getCursorPos()); // "Hello world|. Foo"
                ctx.keyPress(chordWordPrev);
                assertEquals(6, inputState.getCursorPos()); // "Hello |world. Foo"

                ctx.keyPress(chordWordPrev);
                assertEquals(0, inputState.getCursorPos()); // "|Hello world. Foo"

                // No-op
                ctx.keyPress(chordWordPrev);
                assertEquals(0, inputState.getCursorPos());
            } else {
                // OSX
                ctx.keyPress(chordWordNext);
                assertEquals(5, inputState.getCursorPos()); // "Hello|"
                ctx.keyPress(chordWordPrev);
                assertEquals(0, inputState.getCursorPos()); // "|Hello "
                ctx.keyPress(Key.ARROW_RIGHT, 5);
                assertEquals(5, inputState.getCursorPos()); // "Hello| "

                // Upstream notes this doesn't match the desirable OSX behavior
                ctx.keyPress(chordWordNext);
                assertEquals(5 + 1 + 5 + 1, inputState.getCursorPos()); // "Hello world.| "
            }

            // Set 2: non-latin characters, with a double-width space. Upstream counts UTF-8
            // bytes, which are 3 per character here.
            ctx.keyCharsReplace("ハロー　世界。");
            ctx.keyPress(Key.HOME);
            assertEquals(0, inputState.getCursorPos()); // "|HARO- "
            if (!isOSX) {
                // Windows
                ctx.keyPress(chordWordNext);
                assertEquals(4, inputState.getCursorPos()); // "HARO- |"
                ctx.keyPress(chordWordPrev);
                assertEquals(0, inputState.getCursorPos()); // "|HARO- "
                ctx.keyPress(Key.ARROW_RIGHT, 3);
                assertEquals(3, inputState.getCursorPos()); // "HARO-| "
                ctx.keyPress(chordWordNext);
                assertEquals(4, inputState.getCursorPos()); // "HARO- |"

                ctx.keyPress(chordWordNext);
                assertEquals(4 + 2, inputState.getCursorPos()); // "HARO- SEKAI|."
                ctx.keyPress(chordWordNext);
                assertEquals(4 + 3, inputState.getCursorPos()); // "HARO- SEKAI.|"

                ctx.keyPress(Key.END);
                assertEquals(4 + 3, inputState.getCursorPos()); // "HARO- SEKAI.|"

                ctx.keyPress(chordWordPrev);
                assertEquals(4 + 2, inputState.getCursorPos()); // "HARO- SEKAI|."
                ctx.keyPress(chordWordPrev);
                assertEquals(4, inputState.getCursorPos()); // "HARO- |"

                ctx.keyPress(chordWordPrev);
                assertEquals(0, inputState.getCursorPos()); // "|HARO- SEKAI."

                // No-op
                ctx.keyPress(chordWordPrev);
                assertEquals(0, inputState.getCursorPos());
            }

            // Set 3: multiple spaces
            ctx.keyCharsReplace("Hello     world.....HELLO");
            ctx.keyPress(Key.HOME);
            assertEquals(0, inputState.getCursorPos()); // "|Hello     World"
            if (!isOSX) {
                // Windows
                ctx.keyPress(chordWordNext);
                assertEquals(10, inputState.getCursorPos()); // "Hello     |world"
                ctx.keyPress(chordWordNext);
                assertEquals(15, inputState.getCursorPos()); // "Hello     world|"
                ctx.keyPress(chordWordNext);
                assertEquals(20, inputState.getCursorPos()); // "Hello     world.....|"
                ctx.keyPress(chordWordNext);
                assertEquals(25, inputState.getCursorPos()); // "world.....HELLO|"
                ctx.keyPress(chordWordPrev);
                assertEquals(20, inputState.getCursorPos()); // "Hello     world.....|"
                ctx.keyPress(chordWordPrev);
                assertEquals(15, inputState.getCursorPos()); // "Hello     world|"
                ctx.keyPress(chordWordPrev);
                assertEquals(10, inputState.getCursorPos()); // "Hello     |world"
                ctx.keyPress(chordWordPrev);
                assertEquals(0, inputState.getCursorPos()); // "|Hello     World"
            }
        }
    }

    /** widgets_inputtext_password. */
    @Test
    void testPassword() {
        final IkString password = new IkString(64);
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.inputText("Password", password, InputTextFlags.PASSWORD);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Password");
        ctx.keyCharsAppendEnter("Totally not Password123");
        ctx.itemDoubleClick("Password");
        final InputTextState inputState =
                IkGuiImplInputText.getInputTextState(ctx.getID("Password"));
        assertNotNull(inputState);
        // The flags persist
        assertTrue((inputState.flags & InputTextFlags.PASSWORD) != 0);
        // Selecting words doesn't leak the spaces in passwords
        assertEquals(0, inputState.selectStart);
        assertEquals(23, inputState.selectEnd);
        assertEquals(23, inputState.cursor);
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.ARROW_LEFT));
        assertEquals(0, inputState.cursor);
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.ARROW_RIGHT));
        assertEquals(23, inputState.cursor);
    }

    /** widgets_inputtext_scrolling. */
    @Test
    void testScrolling() {
        final IkString str = resizable("");
        ctx.setGui(
                () -> {
                    final float height = 5 * IkGuiInternal.getFontSize();
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.inputTextMultiline(
                            "Field", str, 300, height, InputTextFlags.ENTER_RETURNS_TRUE);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemInput("Field");
        for (int n = 0; n < 10; ++n) {
            ctx.keyCharsAppend("Line " + n + "\n");
        }
        ctx.keyPress(Key.ARROW_DOWN);
        ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
        ctx.keyPress(Key.ARROW_UP);
        ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));

        final Window childWindow = ctx.windowInfo("//Test Window/Field");
        assertNotNull(childWindow);
        final int selectionLength = "Line 9\n".length();

        // Scrolling preserves the cursor and selection
        for (int n = 0; n < 3; ++n) {
            final InputTextState inputState =
                    IkGuiImplInputText.getInputTextState(ctx.getID("Field"));
            assertNotNull(inputState);
            assertTrue(inputState.hasSelection());
            assertEquals(selectionLength, Math.abs(inputState.selectEnd - inputState.selectStart));
            assertEquals(inputState.cursor, inputState.selectEnd);
            assertEquals(inputState.getTextLength() - selectionLength, inputState.cursor);
            if (n == 1) {
                ctx.scrollToBottom(childWindow);
            } else {
                ctx.scrollToTop(childWindow);
            }
        }

        final InputTextState inputState = IkGuiImplInputText.getInputTextState(ctx.getID("Field"));
        assertNotNull(inputState);
        assertEquals(0.0f, childWindow.scrollPosition.y);
        // Moving the cursor scrolls back to it
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(inputState.getTextLength(), inputState.cursor);
        assertEquals(childWindow.scrollMax.y, childWindow.scrollPosition.y);
    }

    /** widgets_inputtext_filters. */
    @Test
    void testFilters() {
        final IkString defaultText = resizable("");
        final IkString decimal = resizable("");
        final IkString scientific = resizable("");
        final IkString hex = resizable("");
        final IkString uppercase = resizable("");
        final IkString noBlank = resizable("");
        final IkString custom = resizable("");
        // Only allow the letters in "imgui"
        final GuiInputTextCallback filterImGuiLetters =
                new GuiInputTextCallback() {
                    @Override
                    public void accept(GuiInputTextCallbackData data) {
                        if (data.getEventChar() >= 256
                                || "imgui".indexOf(data.getEventChar()) < 0) {
                            data.setEventChar(0);
                        }
                    }
                };
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText("default", defaultText);
                    IkGui.inputText("decimal", decimal, InputTextFlags.CHARS_DECIMAL);
                    IkGui.inputText("scientific", scientific, InputTextFlags.CHARS_SCIENTIFIC);
                    IkGui.inputText(
                            "hexadecimal",
                            hex,
                            InputTextFlags.CHARS_HEXADECIMAL | InputTextFlags.CHARS_UPPERCASE);
                    IkGui.inputText("uppercase", uppercase, InputTextFlags.CHARS_UPPERCASE);
                    IkGui.inputText("no blank", noBlank, InputTextFlags.CHARS_NO_BLANK);
                    IkGui.inputText(
                            "\"imgui\" letters",
                            custom,
                            InputTextFlags.CALLBACK_CHAR_FILTER,
                            filterImGuiLetters);
                    IkGui.end();
                });
        final String inputText = "Some fancy Input Text in 0.. 1.. 2.. 3!";
        ctx.setRef("Test Window");
        ctx.itemClick("default");
        ctx.keyCharsAppendEnter(inputText);
        assertEquals(inputText, defaultText.get());

        ctx.itemClick("decimal");
        ctx.keyCharsAppendEnter(inputText);
        assertEquals("0..1..2..3", decimal.get());

        // Commas are replaced by periods in decimal and scientific fields
        ctx.itemClick("decimal");
        ctx.keyCharsReplaceEnter(".,.,");
        assertEquals("....", decimal.get());

        ctx.itemClick("scientific");
        ctx.keyCharsAppendEnter(inputText);
        assertEquals("ee0..1..2..3", scientific.get());

        ctx.itemClick("hexadecimal");
        ctx.keyCharsAppendEnter(inputText);
        assertEquals("EFACE0123", hex.get());

        ctx.itemClick("uppercase");
        ctx.keyCharsAppendEnter(inputText);
        assertEquals("SOME FANCY INPUT TEXT IN 0.. 1.. 2.. 3!", uppercase.get());

        ctx.itemClick("no blank");
        ctx.keyCharsAppendEnter(inputText);
        assertEquals("SomefancyInputTextin0..1..2..3!", noBlank.get());

        ctx.itemClick("\"imgui\" letters");
        ctx.keyCharsAppendEnter(inputText);
        assertEquals("mui", custom.get());
    }

    /** widgets_inputtext_callback_misc. */
    @Test
    void testCallbackMisc() {
        final IkString completionBuffer = resizable("");
        final IkString historyBuffer = resizable("");
        final IkString editBuffer = resizable("");
        final int[] editCount = {0};
        final GuiInputTextCallback callback =
                new GuiInputTextCallback() {
                    @Override
                    public void accept(GuiInputTextCallbackData data) {
                        if (data.getEventFlag() == InputTextFlags.CALLBACK_COMPLETION) {
                            // Append lots of text to avoid relying on existing capacity
                            data.insertChars(
                                    data.getCursorPos(), ".......................................");
                        } else if (data.getEventFlag() == InputTextFlags.CALLBACK_HISTORY) {
                            if (data.getEventKey() == Key.ARROW_UP) {
                                data.deleteChars(0, data.getBufTextLen());
                                data.insertChars(0, "Pressed Up!");
                                data.selectAll();
                            } else if (data.getEventKey() == Key.ARROW_DOWN) {
                                data.deleteChars(0, data.getBufTextLen());
                                data.insertChars(0, "Pressed Down!");
                                data.selectAll();
                            }
                        } else if (data.getEventFlag() == InputTextFlags.CALLBACK_EDIT) {
                            // Toggle the case of the first character
                            final StringBuilder buffer = data.getBuffer();
                            final char c = buffer.charAt(0);
                            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                                buffer.setCharAt(0, (char) (c ^ 32));
                            }
                            data.setBufDirty(true);
                            editCount[0]++;
                        }
                    }
                };
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.inputText(
                            "Completion",
                            completionBuffer,
                            InputTextFlags.CALLBACK_COMPLETION,
                            callback);
                    IkGui.inputText(
                            "History", historyBuffer, InputTextFlags.CALLBACK_HISTORY, callback);
                    IkGui.inputText("Edit", editBuffer, InputTextFlags.CALLBACK_EDIT, callback);
                    IkGui.sameLine();
                    IkGui.text("(" + editCount[0] + ")");
                    IkGui.end();
                });
        final String dots = ".......................................";

        ctx.setRef("Test Window");
        ctx.itemClick("Completion");
        ctx.keyCharsAppend("Hello World");
        assertEquals("Hello World", completionBuffer.get());
        ctx.keyPress(Key.TAB);
        assertEquals("Hello World" + dots, completionBuffer.get());
        ctx.keyChars("!!");
        assertEquals("Hello World" + dots + "!!", completionBuffer.get());

        ctx.keyCharsReplace("Hello World");
        assertEquals("Hello World", completionBuffer.get());
        ctx.keyPress(Key.TAB);
        assertEquals("Hello World" + dots, completionBuffer.get());

        // Undo after callback changes
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        assertEquals("Hello World", completionBuffer.get());

        ctx.itemClick("History");
        ctx.keyCharsAppend("ABCDEF");
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        assertEquals("ABCDE", historyBuffer.get());
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        assertEquals("ABC", historyBuffer.get());
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Y));
        assertEquals("ABCD", historyBuffer.get());
        ctx.keyPress(Key.ARROW_UP);
        assertEquals("Pressed Up!", historyBuffer.get());
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals("Pressed Down!", historyBuffer.get());

        // Undo after callback changes
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        assertEquals("Pressed Up!", historyBuffer.get());
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        assertEquals("ABCD", historyBuffer.get());

        ctx.itemClick("Edit");
        assertEquals("", editBuffer.get());
        assertEquals(0, editCount[0]);
        ctx.keyCharsAppend("h");
        assertEquals("H", editBuffer.get());
        assertEquals(1, editCount[0]);
        ctx.keyCharsAppend("e");
        assertEquals("he", editBuffer.get());
        assertEquals(2, editCount[0]);
        ctx.keyCharsAppend("llo");
        assertEquals("Hello", editBuffer.get());
        // Typing "llo" in one frame is considered one edit
        assertTrue(editCount[0] <= 3);
    }

    /** widgets_inputtext_callback_replace. */
    @Test
    void testCallbackReplace() {
        final IkString str = new IkString(256);
        final boolean[] disableCallback = {false};
        final GuiInputTextCallback callback =
                new GuiInputTextCallback() {
                    @Override
                    public void accept(GuiInputTextCallbackData data) {
                        final int cursor = data.getCursorPos();
                        if (cursor >= 3
                                && "abc"
                                        .contentEquals(
                                                data.getBuffer().subSequence(cursor - 3, cursor))) {
                            data.deleteChars(cursor - 3, 3);
                            data.insertChars(data.getCursorPos(), "好!");
                            data.setSelectionStart(data.getCursorPos() - 2);
                            data.setSelectionEnd(data.getCursorPos());
                        }
                    }
                };
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText(
                            "Hello",
                            str,
                            disableCallback[0]
                                    ? InputTextFlags.NONE
                                    : InputTextFlags.CALLBACK_ALWAYS,
                            callback);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemInput("Hello");
        final InputTextState inputState = state();
        assertNotNull(inputState);
        assertEquals(ctx.getID("Hello"), inputState.id);
        ctx.keyCharsAppend("ab");
        assertEquals(2, inputState.getTextLength());
        assertEquals("ab", inputState.getText());
        assertEquals(2, inputState.cursor);
        // The callback triggers here
        ctx.keyCharsAppend("c");
        assertEquals(2, inputState.getTextLength());
        assertEquals("好!", inputState.getText());
        assertEquals(2, inputState.cursor);
        assertEquals(0, inputState.selectStart);
        assertEquals(2, inputState.selectEnd);

        // Undo after callback changes. Disable the callback, otherwise the "abc" after undoing
        // would immediately be rewritten.
        disableCallback[0] = true;
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        assertEquals("abc", inputState.getText());
    }

    /** widgets_inputtext_callback_resize. */
    @Test
    void testCallbackResize() {
        final IkString str = resizable("abcd");
        str.inputData.resizeFactor = 0;
        final int[] field1Capacity = {-1};
        final String[] field1Text = {null};
        final int[] field2Capacity = {-1};
        final String[] field2Text = {null};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.inputText("Field1", str, InputTextFlags.ENTER_RETURNS_TRUE)) {
                        field1Capacity[0] = str.getBufferSize();
                        field1Text[0] = str.get();
                    }
                    final IkString strLocalUnsaved = resizable("abcd");
                    strLocalUnsaved.inputData.resizeFactor = 0;
                    if (IkGui.inputText(
                            "Field2", strLocalUnsaved, InputTextFlags.ENTER_RETURNS_TRUE)) {
                        field2Capacity[0] = strLocalUnsaved.getBufferSize();
                        field2Text[0] = strLocalUnsaved.get();
                    }
                    IkGui.end();
                });
        assertEquals(4, str.getBufferSize());
        ctx.setRef("Test Window");
        ctx.itemInput("Field1");
        ctx.keyCharsAppendEnter("hello");
        ctx.itemInput("Field2");
        ctx.keyCharsAppendEnter("hello");
        assertEquals(4 + 5, field1Capacity[0]);
        assertEquals("abcdhello", field1Text[0]);
        assertEquals(4 + 5, field2Capacity[0]);
        assertEquals("abcdhello", field2Text[0]);
    }

    /** widgets_inputtext_callback_resize2. */
    @Test
    void testCallbackResize2() {
        final IkString str = new IkString(0);
        // Resizing the string triggered by a change from within another callback
        final GuiInputTextCallback callback =
                new GuiInputTextCallback() {
                    @Override
                    public void accept(GuiInputTextCallbackData data) {
                        if (data.getEventFlag() == InputTextFlags.CALLBACK_HISTORY) {
                            data.insertChars(0, "foo");
                        }
                    }
                };
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText(
                            "Field1",
                            str,
                            InputTextFlags.CALLBACK_HISTORY | InputTextFlags.CALLBACK_RESIZE,
                            callback);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Field1");
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals("foo", str.get());
    }

    /** widgets_inputtext_callback_resize3. */
    @Test
    void testCallbackResize3() {
        // Start with a capacity just under the test data
        final IkString str = new IkString(15);
        str.inputData.isResizable = true;
        final GuiInputTextCallback callback =
                new GuiInputTextCallback() {
                    @Override
                    public void accept(GuiInputTextCallbackData data) {
                        if (data.getEventFlag() == InputTextFlags.CALLBACK_COMPLETION) {
                            data.deleteChars(0, data.getBufTextLen());
                            // Insert 20 characters
                            data.insertChars(0, "12345678901234567890");
                        }
                    }
                };
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText("Field1", str, InputTextFlags.CALLBACK_COMPLETION, callback);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Field1");
        // 16 characters in one input
        IkGui.setClipboardText("abcdefghijklmnop");
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.V));
        final InputTextState inputState = IkGuiImplInputText.getInputTextState(ctx.getID("Field1"));
        assertNotNull(inputState);
        assertEquals(16, inputState.getTextLength());
        ctx.keyPress(Key.TAB);
        assertTrue(inputState.getTextLength() >= 20);
        ctx.keyPress(Key.ENTER);
        assertEquals("12345678901234567890", str.get());
    }

    /** widgets_inputtext_insert_truncate. */
    @Test
    void testInsertTruncate() {
        // Room for 10 characters
        final IkString str = new IkString(10);
        final GuiInputTextCallback callback =
                new GuiInputTextCallback() {
                    @Override
                    public void accept(GuiInputTextCallbackData data) {
                        if (data.getEventFlag() == InputTextFlags.CALLBACK_COMPLETION) {
                            if (data.getSelectionEnd() != data.getSelectionStart()) {
                                data.deleteChars(
                                        Math.min(data.getSelectionStart(), data.getSelectionEnd()),
                                        Math.abs(
                                                data.getSelectionEnd() - data.getSelectionStart()));
                            }
                            data.insertChars(data.getCursorPos(), "22222");
                        }
                    }
                };
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText("Field", str, InputTextFlags.CALLBACK_COMPLETION, callback);
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        // Truncated partial insertion, by pasting and then through a callback
        for (int n = 0; n < 2; ++n) {
            ctx.itemInput("Field");
            ctx.keyCharsReplace("00000111");

            IkGui.setClipboardText("22222");
            if (n == 0) {
                ctx.keyPress(chord(KeyModFlags.CTRL, Key.V));
            } else {
                ctx.keyPress(Key.TAB);
            }
            assertEquals("0000011122", str.get());
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
            assertEquals("00000111", str.get());
            // Select the trailing "11"
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_LEFT), 2);
            if (n == 0) {
                ctx.keyPress(chord(KeyModFlags.CTRL, Key.V));
            } else {
                ctx.keyPress(Key.TAB);
            }
            assertEquals("0000012222", str.get());
            if (n == 0) {
                // The callback version does it in one operation
                ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
                assertEquals("000001", str.get());
            }
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
            assertEquals("00000111", str.get());
        }
    }

    /**
     * widgets_inputtext_insert_truncate_2, which upstream tests truncating in the middle of UTF-8
     * sequences. Here we test not splitting surrogate pairs instead.
     */
    @Test
    void testInsertTruncate2() {
        final IkString str = new IkString(3);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText("Field", str);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemInput("Field");
        // "CC" + a supplementary character, which takes two chars
        ctx.keyCharsReplace("CC𐌼");
        assertEquals("CC", str.get());
        ctx.keyCharsReplace("C𐌼");
        assertEquals("C𐌼", str.get());
        ctx.keyCharsReplace("CCCC");
        assertEquals("CCC", str.get());
    }

    /** widgets_inputtext_nav. */
    @Test
    void testNav() {
        final IkString str = new IkString(256);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final float width = 50;
                    IkGui.button("UL", width, 0);
                    IkGui.sameLine();
                    IkGui.button("U", width, 0);
                    IkGui.sameLine();
                    IkGui.button("UR", width, 0);
                    IkGui.button("L", width, 0);
                    IkGui.sameLine();
                    IkGui.setNextItemWidth(width);
                    IkGui.inputText("##Field", str, InputTextFlags.ALLOW_TAB_INPUT);
                    IkGui.sameLine();
                    IkGui.button("R", width, 0);
                    IkGui.button("DL", width, 0);
                    IkGui.sameLine();
                    IkGui.button("D", width, 0);
                    IkGui.sameLine();
                    IkGui.button("DR", width, 0);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("##Field");
        // Left and right move the text cursor, up and down navigate
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(ctx.getID("##Field"), ctx.context.navID);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("##Field"), ctx.context.navID);
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(ctx.getID("U"), ctx.context.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("D"), ctx.context.navID);
    }

    /** widgets_inputtext_zero_buffer. */
    @Test
    void testZeroBuffer() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText("Field", new IkString(0));
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Field");
        ctx.keyChars("abc");
    }

    /**
     * widgets_inputtext_temp_buffer_2: reverting while the user copies to and from a temporary
     * buffer.
     */
    @Test
    void testTempBuffer2() {
        final IkInt intStored = new IkInt(0);
        final IkInt intTemp = new IkInt(0);
        final IkString strStored = new IkString(256);
        final IkString strTemp = new IkString(256);
        final int[] stepVar = {0};
        final int[] inputTextFlags = {InputTextFlags.NONE};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    intTemp.set(intStored.get());

                    final boolean isMultiline = (stepVar[0] % 4) == 2;
                    final boolean isLiveEdit = (stepVar[0] & 4) == 0;

                    IkGui.pushItemFlag(ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR, isLiveEdit);
                    if (IkGui.inputInt("Field1", intTemp) && IkGui.isItemDeactivatedAfterEdit()) {
                        intStored.set(intTemp.get());
                    }
                    IkGui.popItemFlag();

                    strTemp.set(strStored.get());
                    final boolean ret;
                    IkGui.pushItemFlag(ItemFlags.LIVE_EDIT_ON_INPUT_TEXT, isLiveEdit);
                    if (isMultiline) {
                        ret = IkGui.inputTextMultiline("Field2", strTemp, 0, 0, inputTextFlags[0]);
                    } else {
                        ret = IkGui.inputText("Field2", strTemp, inputTextFlags[0]);
                    }
                    IkGui.popItemFlag();
                    if (ret && IkGui.isItemDeactivatedAfterEdit()) {
                        strStored.set(strTemp.get());
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        for (int step = 0; step < 8; ++step) {
            final boolean isEnterKeepActive = (step % 2) == 1;
            final boolean isMultiline = (step % 4) == 2;
            final boolean isLiveEdit = (step & 4) == 0;
            ctx.context.io.configInputTextEnterKeepActive = isEnterKeepActive;
            stepVar[0] = step;
            ctx.yieldFrame();

            ctx.itemClick("Field1");
            ctx.keyCharsReplaceEnter("123");
            assertEquals(123, intStored.get());
            ctx.itemClick("Field1");
            ctx.keyCharsReplace("200");
            assertEquals(123, intStored.get());
            assertEquals(isLiveEdit ? 200 : 123, intTemp.get());
            ctx.keyPress(Key.ESCAPE);
            assertEquals(123, intStored.get());
            assertEquals(123, intTemp.get());

            for (int subStep = 0; subStep < 3; ++subStep) {
                inputTextFlags[0] =
                        subStep == 0 ? InputTextFlags.NONE : InputTextFlags.ESCAPE_CLEARS_ALL;
                ctx.yieldFrame();

                ctx.itemClick("Field2");
                final String step1 = (subStep == 0 || subStep == 1) ? "abc" : "";
                ctx.keyCharsReplace(step1);
                ctx.keyPress(
                        isMultiline ? chord(KeyModFlags.CTRL, Key.ENTER) : KeyChord.of(Key.ENTER));
                assertEquals(step1, strStored.get());

                // Tab takes the deactivation path
                ctx.itemClick("Field2");
                ctx.keyCharsReplace("eee");
                assertEquals(step1, strStored.get());
                assertEquals(isLiveEdit ? "eee" : step1, strTemp.get());
                ctx.keyPress(Key.TAB);
                assertEquals("eee", strStored.get());
                assertEquals("eee", strTemp.get());
                strStored.set(step1);

                ctx.itemClick("Field2");
                ctx.keyCharsReplace("fff");
                assertEquals(step1, strStored.get());
                assertEquals(isLiveEdit ? "fff" : step1, strTemp.get());
                ctx.keyPress(Key.ESCAPE);
                assertEquals(step1, strStored.get());

                // The caller copies from the temporary to the stored text when deactivated after
                // an edit, then back the next frame
                if ((inputTextFlags[0] & InputTextFlags.ESCAPE_CLEARS_ALL) != 0 && !isLiveEdit) {
                    continue;
                }
                if ((inputTextFlags[0] & InputTextFlags.ESCAPE_CLEARS_ALL) != 0) {
                    assertEquals("", strTemp.get());
                } else {
                    assertEquals(step1, strTemp.get());
                }
            }
        }
    }

    /** widgets_inputtext_clipboard. */
    @Test
    void testClipboard() {
        final IkString text = new IkString(256);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText("Field", text, InputTextFlags.ENTER_RETURNS_TRUE);
                    IkGui.end();
                });
        final String initialClipboard = IkGui.getClipboardText();
        assertTrue(initialClipboard == null || initialClipboard.isEmpty());
        ctx.setRef("Test Window");

        for (int variant = 0; variant < 2; ++variant) {
            // Reset the state
            IkGuiInternal.clearActiveID();
            ctx.yieldFrame();
            text.set("Hello, world!");

            final int chordCopy =
                    variant == 0
                            ? chord(KeyModFlags.CTRL, Key.C)
                            : chord(KeyModFlags.CTRL, Key.INSERT);
            final int chordCut =
                    variant == 0
                            ? chord(KeyModFlags.CTRL, Key.X)
                            : chord(KeyModFlags.CTRL, Key.DELETE);
            final int chordPaste =
                    variant == 0
                            ? chord(KeyModFlags.CTRL, Key.V)
                            : chord(KeyModFlags.SHIFT, Key.INSERT);

            // Copying without a selection copies everything
            ctx.itemClick("Field");
            ctx.keyPress(chordCopy);
            assertEquals("Hello, world!", IkGui.getClipboardText(), "variant " + variant);

            // Copying with a selection
            ctx.itemClick("Field");
            ctx.keyPress(Key.HOME);
            // Select the first word
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_RIGHT), 5);
            ctx.keyPress(chordCopy);
            assertEquals("Hello", IkGui.getClipboardText());

            // Cutting a selection
            ctx.itemClick("Field");
            ctx.keyPress(Key.HOME);
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ARROW_RIGHT), 5);
            ctx.keyPress(chordCut);
            assertEquals("Hello", IkGui.getClipboardText());
            assertEquals(", world!", text.get());

            // Pasting over a selection
            ctx.itemClick("Field");
            IkGui.setClipboardText("həˈlō");
            ctx.keyPress(Key.HOME);
            ctx.keyPress(chordPaste);
            assertEquals("həˈlō, world!", text.get());

            // Pasting a line feed into a single line input
            ctx.itemInputValue("Field", "");
            ctx.itemInput("Field");
            IkGui.setClipboardText("this is a\nsentence");
            ctx.keyPress(chordPaste);
            assertEquals("this is a sentence", text.get());
        }
    }

    /** widgets_inputtext_hover. */
    @Test
    void testHover() {
        final IkString str = new IkString(256);
        final boolean[] hovered = {false};
        final int[] itemID = {0};
        final int[] expectedID = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputTextMultiline("Field", str);
                    itemID[0] = IkGui.getItemID();
                    expectedID[0] = IkGui.getID("Field");
                    hovered[0] = IkGui.isItemHovered();
                    IkGui.text("hovered: " + hovered[0]);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.mouseMove("Field");
        assertEquals(expectedID[0], itemID[0]);
        assertTrue(hovered[0]);
    }

    /** widgets_inputtext_multiline_status. */
    @Test
    void testMultilineStatus() {
        final IkString str = new IkString(256);
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputTextMultiline("Field", str);
                    status.querySet(false);
                    IkGui.text("IsItemActive: " + status.active);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Field");
        final int inputID = ctx.getID("Field");
        assertEquals(inputID, ctx.context.activeID);
        assertEquals(1, status.active);
        ctx.keyCharsReplace("1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n13\n14\n15\n");
        final Window window = ctx.windowInfo("Field");
        assertNotNull(window);
        // The text input is still considered active while using its scrollbar
        final int scrollbarID = IkGuiTestContext.getWindowScrollbarID(window, true);
        ctx.mouseMove(scrollbarID);
        ctx.mouseDown(MouseButton.LEFT);
        assertEquals(scrollbarID, ctx.context.activeID);
        assertEquals(1, status.active);
        ctx.mouseUp(MouseButton.LEFT);
        assertEquals(inputID, ctx.context.activeID);
        assertEquals(1, status.active);
    }

    /** widgets_inputtext_multiline_refocus. */
    @Test
    void testMultilineRefocus() {
        final IkString str = new IkString(256);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.inputTextMultiline(
                            "Field",
                            str,
                            0,
                            0,
                            InputTextFlags.CTRL_ENTER_FOR_NEW_LINE
                                    | InputTextFlags.ENTER_RETURNS_TRUE)) {
                        IkGui.setKeyboardFocusHere(-1);
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final int inputID = ctx.getID("Field");
        ctx.itemClick("Field");
        assertEquals(inputID, ctx.context.activeID);
        ctx.keyChars("Hello");
        ctx.keyPress(Key.ENTER);
        assertEquals(inputID, ctx.context.activeID);
    }

    /** widgets_inputtext_multiline_enter. */
    @Test
    void testMultilineEnter() {
        final IkString str = new IkString(256);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("Above");
                    IkGui.inputTextMultiline("Field", str);
                    IkGui.button("Below");
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final int inputID = ctx.getID("Field");
        ctx.itemClick("Above");
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(0, ctx.context.activeID);
        // Enter both enters the child and activates the input
        ctx.keyPress(Key.ENTER);
        assertEquals(inputID, ctx.context.activeID);
        ctx.keyChars("Hello");
        ctx.keyPress(Key.ENTER);
        ctx.keyChars("World");
        assertEquals("Hello\nWorld", str.get());
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.ENTER));
        ctx.yieldFrames(2);
        assertEquals(0, ctx.context.activeID);
        assertEquals(inputID, ctx.context.navID);

        ctx.keyPress(Key.TAB);
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.TAB));
        assertEquals(inputID, ctx.context.activeID);
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.A));
        ctx.keyPress(Key.DELETE);
        assertEquals("", str.get());
        ctx.keyChars("Hello");
        ctx.keyPress(Key.ENTER);
        ctx.keyChars("World");
        assertEquals("Hello\nWorld", str.get());
    }

    /** widgets_inputtext_multiline_tab. */
    @Test
    void testMultilineTab() {
        final IkString str = new IkString(256);
        final boolean[] allowTabInput = {false};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("Above");
                    IkGui.inputTextMultiline(
                            "Field",
                            str,
                            0,
                            0,
                            allowTabInput[0]
                                    ? InputTextFlags.ALLOW_TAB_INPUT
                                    : InputTextFlags.NONE);
                    IkGui.button("Below");
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final int inputID = ctx.getID("Field");

        allowTabInput[0] = false;
        ctx.itemClick("Above");
        // The highlight appears
        ctx.keyPress(Key.TAB);
        ctx.keyPress(Key.TAB);
        ctx.yieldFrame();
        assertEquals(inputID, ctx.context.activeID);
        assertEquals(inputID, ctx.context.navID);
        ctx.keyPress(Key.TAB);
        assertEquals(ctx.getID("Below"), ctx.context.navID);
        assertEquals(0, ctx.context.activeID);

        allowTabInput[0] = true;
        ctx.itemClick("Above");
        // The highlight appears
        ctx.keyPress(Key.TAB);
        ctx.keyPress(Key.TAB);
        assertEquals(inputID, ctx.context.navID);
        // Not activated
        assertEquals(0, ctx.context.activeID);
        ctx.keyPress(Key.TAB);
        assertEquals(ctx.getID("Below"), ctx.context.navID);
        assertEquals(0, ctx.context.activeID);
    }

    /** widgets_inputtext_multiline_linecount. */
    @Test
    void testMultilineLineCount() {
        final IkString buf = resizable("");
        final int[] flags = {InputTextFlags.NONE};
        final Window[] childWindow = {null};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.inputTextMultiline("Field", buf, 500, 0, flags[0]);
                    IkGui.beginChild("Field");
                    childWindow[0] = IkGuiInternal.getCurrentWindow();
                    IkGui.endChild();
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Field");
        ctx.keyPress(Key.ESCAPE);
        final InputTextState inputState = IkGuiImplInputText.getInputTextState(ctx.getID("Field"));
        assertNotNull(inputState);
        assertNotNull(childWindow[0]);
        final float paddingY = ctx.context.style.variable.framePadding.y * 2.0f;

        for (int n = 0; n < 4; ++n) {
            final boolean testWhileActive = (n % 2) == 1;
            final boolean isWordWrap = (n % 4) == 2;
            // No actual word wrapping expected
            flags[0] = isWordWrap ? InputTextFlags.WORD_WRAP : InputTextFlags.NONE;
            ctx.yieldFrame();

            // No trailing line feed
            ctx.itemInput("Field");
            ctx.keyCharsReplace("aaaa\nbbb\nccc");
            ctx.yieldFrame();
            if (testWhileActive) {
                assertEquals(3, inputState.lineCount);
            } else {
                ctx.keyPress(chord(KeyModFlags.CTRL, Key.ENTER));
            }
            assertEquals(IkGui.getTextLineHeight() * 3 + paddingY, childWindow[0].contentSize.y);

            ctx.itemInput("Field");
            ctx.keyCharsReplace("aaaa\nbbb\nccc\na");
            ctx.yieldFrame();
            if (testWhileActive) {
                assertEquals(4, inputState.lineCount);
            } else {
                ctx.keyPress(chord(KeyModFlags.CTRL, Key.ENTER));
            }
            assertEquals(IkGui.getTextLineHeight() * 4 + paddingY, childWindow[0].contentSize.y);

            // A trailing line feed starts a new line
            ctx.itemInput("Field");
            ctx.keyCharsReplace("aaaa\nbbb\nccc\n");
            ctx.yieldFrame();
            if (testWhileActive) {
                assertEquals(4, inputState.lineCount);
            } else {
                ctx.keyPress(chord(KeyModFlags.CTRL, Key.ENTER));
            }
            assertEquals(IkGui.getTextLineHeight() * 4 + paddingY, childWindow[0].contentSize.y);
        }
    }

    /**
     * widgets_inputtext_special_key_chars: backends that send characters for tab, enter and space
     * key presses.
     */
    @Test
    void testSpecialKeyChars() {
        final IkString fieldText = new IkString(256);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputTextMultiline(
                            "Field", fieldText, 0, 0, InputTextFlags.ALLOW_TAB_INPUT);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Field");
        ctx.yieldFrame();
        final var io = ctx.context.io;

        io.addKeyEvent(Key.TAB, true);
        io.addInputCharacter('\t');
        ctx.yieldFrame();
        io.addKeyEvent(Key.TAB, false);
        ctx.yieldFrame();
        assertEquals("\t", fieldText.get());
        io.addKeyEvent(Key.BACKSPACE, true);
        ctx.yieldFrame();
        io.addKeyEvent(Key.BACKSPACE, false);
        ctx.yieldFrame();

        io.addKeyEvent(Key.ENTER, true);
        io.addInputCharacter('\r');
        ctx.yieldFrame();
        io.addKeyEvent(Key.ENTER, false);
        ctx.yieldFrame();
        assertEquals("\n", fieldText.get());
        io.addKeyEvent(Key.BACKSPACE, true);
        ctx.yieldFrame();
        io.addKeyEvent(Key.BACKSPACE, false);
        ctx.yieldFrame();

        io.addKeyEvent(Key.SPACE, true);
        io.addInputCharacter(' ');
        ctx.yieldFrame();
        io.addKeyEvent(Key.SPACE, false);
        ctx.yieldFrame();
        assertEquals(" ", fieldText.get());
    }

    /** widgets_inputtext_deactivate_apply: the value is applied on the deactivation frame. */
    @Test
    void testDeactivateApply() {
        final float[][] value = {new float[1], new float[1], new float[1]};
        final IkBoolean useTempVar = new IkBoolean(false);
        final boolean[] useLiveEdit = {false};
        final boolean[] useMixedValue = {false};
        final int[] deactivatedField = {-1};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.checkbox("Use Temp Var", useTempVar);
                    IkGui.pushItemFlag(ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR, useLiveEdit[0]);
                    IkGui.pushItemFlag(ItemFlags.MIXED_VALUE, useMixedValue[0]);
                    final String[] labels = {"x", "y", "z"};
                    for (int i = 0; i < 3; ++i) {
                        final float[] temp = {value[i][0]};
                        final float[] p = useTempVar.get() ? temp : value[i];
                        IkGui.inputFloat(labels[i], p, 0, 0, "%.3f");
                        if (IkGui.isItemDeactivatedAfterEdit()) {
                            deactivatedField[0] = i;
                            if (useTempVar.get()) {
                                value[i][0] = temp[0];
                            }
                        }
                    }
                    IkGui.popItemFlag();
                    IkGui.popItemFlag();
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final Runnable reset =
                () -> {
                    for (float[] component : value) {
                        component[0] = 0;
                    }
                    deactivatedField[0] = -1;
                };

        for (int variant = 0; variant < 4; ++variant) {
            useLiveEdit[0] = (variant & 1) != 0;
            useMixedValue[0] = (variant & 2) != 0;
            for (int step = 0; step < 3; ++step) {
                useTempVar.set(step > 0);
                if (step == 0) {
                    ctx.windowResize("", 300, IkGui.getFrameHeight() * 20);
                }
                // An edge case where the deactivated input is immediately clipped out
                if (step == 2) {
                    ctx.windowResize("", 300, IkGui.getFrameHeight());
                }
                final String description =
                        "step "
                                + step
                                + ", live edit "
                                + useLiveEdit[0]
                                + ", mixed "
                                + useMixedValue[0];

                // Click the next item
                reset.run();
                ctx.itemClick("y");
                // Input a value, but don't press enter
                ctx.keyCharsReplace("123.0");
                if (!useTempVar.get()) {
                    assertEquals(
                            useLiveEdit[0] ? 123.0f : 0.0f,
                            value[1][0],
                            description + ", click next");
                }
                ctx.itemClick("z");
                assertEquals(123.0f, value[1][0], description + ", click next");
                assertEquals(1, deactivatedField[0], description + ", click next");
                ctx.keyPress(Key.ESCAPE);

                // Click the previous item
                reset.run();
                ctx.itemClick("y");
                ctx.keyCharsReplace("123.0");
                ctx.itemClick("x");
                assertEquals(123.0f, value[1][0], description + ", click previous");
                assertEquals(1, deactivatedField[0], description + ", click previous");
                ctx.keyPress(Key.ESCAPE);

                // Tab to the next item
                reset.run();
                ctx.itemClick("y");
                ctx.keyCharsReplace("123.0");
                ctx.keyPress(Key.TAB);
                assertEquals(123.0f, value[1][0], description + ", tab next");
                assertEquals(1, deactivatedField[0], description + ", tab next");
                ctx.keyPress(Key.ESCAPE);

                // Tab to the previous item
                reset.run();
                ctx.itemClick("y");
                ctx.keyCharsReplace("123.0");
                ctx.keyPress(chord(KeyModFlags.SHIFT, Key.TAB));
                assertEquals(123.0f, value[1][0], description + ", tab previous");
                assertEquals(1, deactivatedField[0], description + ", tab previous");
                ctx.keyPress(Key.ESCAPE);
            }
        }
    }

    /** widgets_inputtext_wordwrap_1. Upstream assumes a monospace font for up/down movement. */
    @Test
    void testWordWrap1() {
        final IkString str = new IkString(256);
        final float[] width = {120.0f};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    if (IkGui.smallButton("Pattern 1")) {
                        str.set(
                                "Aaaaa Aaaaa AAAAA\n" // 0..17
                                        + "  Bbbbbb Bbbbbb BBBBBB\n" // 18..40
                                        + "Ccccccc Cccccccc CCCCCCCC\n"); // 41..66
                    }
                    if (IkGui.smallButton("Pattern 2")) {
                        str.set(
                                "Aaaaa Aaaaa AAAAA\n" // 0..17
                                        + "  Bbbbbbbbb Bbbbbb BBBBB\n" // 18..42
                                        + "Ccccccccc Cccccccc CCCCCC\n"); // 43..58
                    }
                    IkGui.dragFloat("Width", width, 1.0f, 0.0f, 200.0f);
                    IkGui.pushStyleVarFloat2(StyleVariable.FRAME_PADDING, 0.0f, 0.0f);
                    IkGui.setNextItemWidth(width[0]);
                    IkGui.inputTextMultiline("Field", str, 0, 0, InputTextFlags.WORD_WRAP);
                    IkGui.popStyleVar();
                    IkGui.end();
                });
        final float scrollbarSize = ctx.context.style.variable.scrollbarSize;
        width[0] = IkGui.calcTextSize("Ccccccc Cccccccc CCCCCCCC").x + 10.0f + scrollbarSize;

        ctx.setRef("Test Window");
        ctx.itemClick("Pattern 1");
        ctx.itemClick("Field");

        // No wrapping yet
        // Aaaaa Aaaaa AAAAA           //  0..17
        //   Bbbbbb Bbbbbb BBBBBB      // 18..40
        // Ccccccc Cccccccc CCCCCCCC   // 41..66
        final InputTextState s = IkGuiImplInputText.getInputTextState(ctx.getID("Field"));
        assertNotNull(s);
        assertEquals(67, s.getCursorPos());
        ctx.keyPress(Key.HOME);
        assertEquals(67, s.getCursorPos());
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(66, s.getCursorPos());
        ctx.keyPress(Key.HOME);
        assertEquals(41, s.getCursorPos());
        ctx.keyPress(Key.HOME);
        assertEquals(41, s.getCursorPos());
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(40, s.getCursorPos()); // after the last B
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(66 - 3, s.getCursorPos());
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(40, s.getCursorPos()); // after the last B
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(17, s.getCursorPos()); // after the last A

        // Wrapping 1
        // Aaaaa Aaaaa AAAAA    //  0..17
        //   Bbbbbb Bbbbbb_     // 18..34
        // BBBBBB               // 34..40
        // Ccccccc Cccccccc_    // 41..58
        // CCCCCCCC             // 58..66
        width[0] = IkGui.calcTextSize("Aaaaa Aaaaa AAAAA ").x + scrollbarSize;
        ctx.yieldFrames(2);
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.END));
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(66, s.getCursorPos());
        ctx.keyPress(Key.HOME);
        assertEquals(58, s.getCursorPos());
        ctx.keyPress(Key.HOME);
        assertEquals(41, s.getCursorPos());
        ctx.keyPress(Key.HOME);
        assertEquals(41, s.getCursorPos());
        ctx.keyPress(Key.END);
        assertEquals(58, s.getCursorPos()); // after the second "Ccccccc", before the space
        ctx.keyPress(Key.ARROW_LEFT);
        ctx.keyPress(Key.END);
        assertEquals(58, s.getCursorPos());
        ctx.keyPress(Key.END);
        assertEquals(66, s.getCursorPos());
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(40 + 1 + 8, s.getCursorPos()); // after the first "Ccccccc"
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(40, s.getCursorPos()); // after the last B
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(17 + 1 + 8, s.getCursorPos()); // after the first "  Bbbbbb"

        ctx.keyPress(chord(KeyModFlags.CTRL, Key.HOME));
        assertEquals(0, s.getCursorPos());
        ctx.keyPress(Key.END);
        assertEquals(17, s.getCursorPos()); // after the last "AAAAA"
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(34, s.getCursorPos()); // after the middle "Bbbbb_"
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(17, s.getCursorPos()); // back

        ctx.keyPress(chord(KeyModFlags.CTRL, Key.END));
        assertEquals(67, s.getCursorPos());
        assertEquals(-1.0f, s.getPreferredOffsetX());
        assertEquals(Direction.RIGHT, s.lastMoveDirectionLR);
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(58, s.getCursorPos());
        assertEquals(0.0f, s.getPreferredOffsetX());
        assertEquals(Direction.LEFT, s.lastMoveDirectionLR);

        ctx.keyPress(chord(KeyModFlags.CTRL, Key.HOME));
        assertEquals(0, s.getCursorPos());
        assertEquals(-1.0f, s.getPreferredOffsetX());
        assertEquals(Direction.LEFT, s.lastMoveDirectionLR);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(18, s.getCursorPos());
        assertEquals(Direction.LEFT, s.lastMoveDirectionLR);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(34, s.getCursorPos());
        assertEquals(Direction.LEFT, s.lastMoveDirectionLR);
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(18, s.getCursorPos());
        s.lastMoveDirectionLR = Direction.RIGHT;
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(34, s.getCursorPos());
        assertEquals(Direction.LEFT, s.lastMoveDirectionLR);

        // Wrapping 2
        // Aaaaa_    //  0..6
        // Aaaaa_    //  6..12
        // AAAAA     // 12..17
        // __Bbbbbb  // 18..26
        // _Bbbbbb   // 26..34
        // BBBBBB    // 34..40
        // Ccccccc_  // 41..49
        // Cccccccc  // 49..57
        // _CCCCCCCC // 57..66
        width[0] = IkGui.calcTextSize("Aaaaa    ").x + scrollbarSize;
        ctx.yieldFrames(2);
        ctx.itemClick("Field");
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.HOME));
        assertEquals(0, s.getCursorPos());
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(6, s.getCursorPos());
        ctx.keyPress(Key.END);
        assertEquals(12, s.getCursorPos()); // on the wrapping point
        assertEquals(Direction.RIGHT, s.lastMoveDirectionLR);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(17, s.getCursorPos());
        ctx.keyPress(Key.HOME);
        assertEquals(12, s.getCursorPos()); // on the wrapping point
        assertEquals(Direction.LEFT, s.lastMoveDirectionLR);
        ctx.keyPress(Key.HOME);
        assertEquals(0, s.getCursorPos());

        // Wrapping 3
        // Aaaaa Aaaaa AAAAA    //  0..17
        //   Bbbbbbbbb_         // 18..30
        // Bbbbbb BBBBB         // 30..42
        // Ccccccccc_           // 43..53
        // Cccccccc CCCCCC      // 53..68
        width[0] = IkGui.calcTextSize("Aaaaa Aaaaa AAAAA").x + 4 + scrollbarSize;
        ctx.itemClick("Pattern 2");
        ctx.yieldFrames(2);
        ctx.itemClick("Field");
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.HOME));
        assertEquals(0, s.getCursorPos());
        ctx.keyPress(Key.END);
        assertEquals(17, s.getCursorPos());
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(30, s.getCursorPos());
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(42, s.getCursorPos());
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(53, s.getCursorPos());
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(68, s.getCursorPos());
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(69, s.getCursorPos()); // the line feed
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(68, s.getCursorPos());
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(53, s.getCursorPos());
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(42, s.getCursorPos());
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(30, s.getCursorPos());
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(17, s.getCursorPos());
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(17, s.getCursorPos());
    }
}
