package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.frontend.gui.util.MathUtil;
import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal state of the currently focused/edited text input box. For a given item ID, access with
 * IkGuiInternal.getInputTextState().
 *
 * <p>This also contains a port of the editing logic of stb_textedit, which Dear ImGui uses, along
 * with the modifications Dear ImGui makes to it for word wrapping. Rather than UTF-8 bytes, all
 * positions are Java (UTF-16) character indices, and we take care not to split surrogate pairs.
 */
public class InputTextState {
    /** The maximum number of undo records that are kept. */
    private static final int UNDO_STATE_COUNT = 99;

    /** The maximum number of characters stored by undo records. */
    private static final int UNDO_CHAR_COUNT = 999;

    /** Keyboard input to move the cursor left. */
    public static final int K_LEFT = 0x200000;

    /** Keyboard input to move the cursor right. */
    public static final int K_RIGHT = 0x200001;

    /** Keyboard input to move the cursor up. */
    public static final int K_UP = 0x200002;

    /** Keyboard input to move the cursor down. */
    public static final int K_DOWN = 0x200003;

    /** Keyboard input to move the cursor to the start of the line. */
    public static final int K_LINE_START = 0x200004;

    /** Keyboard input to move the cursor to the end of the line. */
    public static final int K_LINE_END = 0x200005;

    /** Keyboard input to move the cursor to the start of the text. */
    public static final int K_TEXT_START = 0x200006;

    /** Keyboard input to move the cursor to the end of the text. */
    public static final int K_TEXT_END = 0x200007;

    /** Keyboard input to delete the selection or the character under the cursor. */
    public static final int K_DELETE = 0x200008;

    /** Keyboard input to delete the selection or the character left of the cursor. */
    public static final int K_BACKSPACE = 0x200009;

    /** Keyboard input to undo. */
    public static final int K_UNDO = 0x20000A;

    /** Keyboard input to redo. */
    public static final int K_REDO = 0x20000B;

    /** Keyboard input to move the cursor left one word. */
    public static final int K_WORD_LEFT = 0x20000C;

    /** Keyboard input to move the cursor right one word. */
    public static final int K_WORD_RIGHT = 0x20000D;

    /** Keyboard input to move the cursor up a page. */
    public static final int K_PAGE_UP = 0x20000E;

    /** Keyboard input to move the cursor down a page. */
    public static final int K_PAGE_DOWN = 0x20000F;

    /** Modifier for keyboard inputs that extends the selection. */
    public static final int K_SHIFT = 0x400000;

    /** Characters that separate words, in addition to blanks. */
    private static final int[] SEPARATORS = {
        ',', 0x3001, '.', 0x3002, ';', 0xFF1B, '(', 0xFF08, ')', 0xFF09, '{', 0xFF5B, '}', 0xFF5D,
        '[', 0x300C, ']', 0x300D, '|', 0xFF5C, '!', 0xFF01, '\\', 0xFFE5, '/', 0x30FB, 0xFF0F, '\n',
        '\r',
    };

    /**
     * A record of an edit, which can be applied to undo (or redo) it.
     *
     * @param where Where the edit happened.
     * @param deleteLength The number of characters to delete at that position to revert it.
     * @param insertText The text to insert at that position to revert it.
     */
    private record UndoRecord(int where, int deleteLength, @NonNull String insertText) {}

    /**
     * Information about where a character is located.
     *
     * @param x The x position of the character.
     * @param firstChar The first character of the row.
     * @param length The length of the row.
     * @param previousFirst The first character of the previous row.
     */
    private record FindState(float x, int firstChar, int length, int previousFirst) {}

    /** The widget ID owning the text state. */
    public int id;

    /**
     * A copy of the inputText() flags.
     *
     * @see InputTextFlags
     */
    public int flags;

    /** The text being edited. For read-only fields, a copy of the user text. */
    public final StringBuilder text;

    /** The value to revert to when pressing Escape, the user text at the time of focus. */
    public String textToRevertTo;

    /** Temporary storage for callbacks, to support automatic reconciling of the undo stack. */
    public String callbackTextBackup;

    /** The capacity of the user string, in characters. */
    public int bufCapacity;

    /** Whether the text can grow beyond the capacity. */
    public boolean resizable;

    /**
     * Horizontal offset (managed manually) and vertical scrolling (pulled from the child window's
     * own scroll for multi-line).
     */
    public final Vector2f scroll;

    /** The last line count, for debugging. */
    public int lineCount;

    /** The word wrapping width, 0 if not wrapping. */
    public float wrapWidth;

    /**
     * Timer for cursor blinking in seconds, reset on every user action so the cursor reappears
     * immediately.
     */
    public float cursorAnim;

    /** Set when we want scrolling to follow the current cursor position. */
    public boolean cursorFollow;

    /** Set when we want scrolling to be centered over the cursor position. */
    public boolean cursorCenterY;

    /** After a double click to select all, we ignore further mouse drags to update selection. */
    public boolean selectedAllMouseLock;

    /** Edited since activated. */
    public boolean editedBefore;

    /** Edited this frame. */
    public boolean editedThisFrame;

    /** Validated this frame. */
    public boolean validatedThisFrame;

    /** Force a reload of the user string, so that it may be modified externally. */
    public boolean wantReloadUserBuf;

    /**
     * Track the last movement direction (LEFT or RIGHT), so that when the cursor is on a word
     * wrapping boundary we can display it on either line depending on the last move.
     */
    public Direction lastMoveDirectionLR;

    /** The selection start to use when reloading the user string. */
    public int reloadSelectionStart;

    /** The selection end to use when reloading the user string. */
    public int reloadSelectionEnd;

    /** The font size used to lay out the text, set every frame by the input text widget. */
    public int fontSize;

    /** Whether to use Mac OS X style word movement, set every frame by the input text widget. */
    public boolean macOSXBehaviors;

    /** Position of the text cursor within the string. */
    public int cursor;

    /** Selection start point. Equal to the selection end if there is no selection. */
    public int selectStart;

    /**
     * Selection end point. Note that start may be less than or greater than end (e.g. when dragging
     * the mouse, start is where the initial click was, and you can drag in either direction).
     */
    public int selectEnd;

    /** Whether typing overwrites characters instead of inserting them. */
    public boolean insertMode;

    /** Page size in number of rows. Must be greater than 0 for page up/down in multi-line. */
    public int rowCountPerPage;

    /** Whether {@link #preferredX} is used. */
    public boolean hasPreferredX;

    /** Where the cursor tries to seek to along x when moving up and down. */
    public float preferredX;

    /** Whether the text is a single line. */
    public boolean singleLine;

    /** Undo records, the most recent last. */
    private final List<UndoRecord> undoStack;

    /** Redo records, the most recent last. */
    private final List<UndoRecord> redoStack;

    /** The number of characters stored in undo records. */
    private int undoCharCount;

    /** The number of characters stored in redo records. */
    private int redoCharCount;

    /**
     * Fetch the number of undo records, for debugging.
     *
     * @return The number of edits that can be undone.
     */
    public int getUndoRecordCount() {
        return undoStack.size();
    }

    /**
     * Fetch the number of redo records, for debugging.
     *
     * @return The number of edits that can be redone.
     */
    public int getRedoRecordCount() {
        return redoStack.size();
    }

    /**
     * Fetch the number of characters stored by the undo and redo records, for debugging.
     *
     * @return The number of stored characters.
     */
    public int getUndoCharCount() {
        return undoCharCount + redoCharCount;
    }

    /** The start of each row of the text, for the current layout. */
    private final IntArrayList rowStarts;

    /** The text that was laid out. */
    private String layoutText;

    /** Whether the layout needs to be rebuilt. */
    private boolean layoutDirty;

    /** The font size the layout was built with. */
    private int layoutFontSize;

    /** The wrap width the layout was built with. */
    private float layoutWrapWidth;

    /** The flags the layout was built with. */
    private int layoutFlags;

    /** Whether the layout was built for a single line. */
    private boolean layoutSingleLine;

    public InputTextState() {
        text = new StringBuilder();
        textToRevertTo = "";
        callbackTextBackup = "";
        scroll = new Vector2f();
        lastMoveDirectionLR = Direction.NONE;
        undoStack = new ArrayList<>();
        redoStack = new ArrayList<>();
        rowStarts = new IntArrayList();
        layoutText = "";
        layoutDirty = true;
    }

    // ---------------------------------------------------------------------------------------
    // Basic state
    // ---------------------------------------------------------------------------------------

    /** Clear the text. */
    public void clearText() {
        text.setLength(0);
        textChanged();
        cursorClamp();
    }

    /** Release the memory held by the state. */
    public void clearFreeMemory() {
        text.setLength(0);
        text.trimToSize();
        textToRevertTo = "";
        callbackTextBackup = "";
        textChanged();
    }

    /**
     * The length of the text.
     *
     * @return The length of the text, in characters.
     */
    public int getTextLength() {
        return text.length();
    }

    /**
     * Fetch the current text.
     *
     * @return The text being edited.
     */
    public String getText() {
        return text.toString();
    }

    /**
     * Replace the text, without recording undo information. Used to load the text from the user.
     *
     * @param newText The new text.
     */
    public void setText(@NonNull CharSequence newText) {
        text.setLength(0);
        text.append(newText);
        textChanged();
    }

    /** Mark the text as changed so that the layout is rebuilt. */
    public void textChanged() {
        layoutDirty = true;
    }

    /** After a user input the cursor stays on for a while without blinking. */
    public void cursorAnimReset() {
        cursorAnim = -0.30f;
    }

    /** Clamp the cursor and selection to the text. */
    public void cursorClamp() {
        final int length = text.length();
        cursor = Math.min(cursor, length);
        selectStart = Math.min(selectStart, length);
        selectEnd = Math.min(selectEnd, length);
    }

    public boolean hasSelection() {
        return selectStart != selectEnd;
    }

    public void clearSelection() {
        selectStart = selectEnd = cursor;
    }

    public int getCursorPos() {
        return cursor;
    }

    public int getSelectionStart() {
        return selectStart;
    }

    public int getSelectionEnd() {
        return selectEnd;
    }

    /**
     * Set the selection, with the cursor at the end.
     *
     * @param start The selection start.
     * @param end The selection end.
     */
    public void setSelection(int start, int end) {
        selectStart = start;
        cursor = selectEnd = end;
    }

    /**
     * The position the cursor tries to seek to along x when moving up and down.
     *
     * @return The preferred x offset, or -1 if there is none.
     */
    public float getPreferredOffsetX() {
        return hasPreferredX ? preferredX : -1;
    }

    /** Select all the text. */
    public void selectAll() {
        selectStart = 0;
        cursor = selectEnd = text.length();
        hasPreferredX = false;
    }

    /**
     * If you modify the underlying user string while the input is active, call this to reload it
     * and select all the text.
     */
    public void reloadUserBufAndSelectAll() {
        wantReloadUserBuf = true;
        reloadSelectionStart = 0;
        reloadSelectionEnd = Integer.MAX_VALUE;
    }

    /**
     * If you modify the underlying user string while the input is active, call this to reload it
     * and keep the selection.
     */
    public void reloadUserBufAndKeepSelection() {
        wantReloadUserBuf = true;
        reloadSelectionStart = selectStart;
        reloadSelectionEnd = selectEnd;
    }

    /**
     * If you modify the underlying user string while the input is active, call this to reload it
     * and move the cursor to the end.
     */
    public void reloadUserBufAndMoveToEnd() {
        wantReloadUserBuf = true;
        reloadSelectionStart = reloadSelectionEnd = Integer.MAX_VALUE;
    }

    /**
     * Process a keyboard input.
     *
     * @param key One of the K_* constants, optionally combined with K_SHIFT.
     */
    public void onKeyPressed(int key) {
        key(key);
        cursorFollow = true;
        cursorAnimReset();
        final int keyUnshifted = key & ~K_SHIFT;
        if (keyUnshifted == K_LEFT
                || keyUnshifted == K_LINE_START
                || keyUnshifted == K_TEXT_START
                || keyUnshifted == K_BACKSPACE
                || keyUnshifted == K_WORD_LEFT) {
            lastMoveDirectionLR = Direction.LEFT;
        } else if (keyUnshifted == K_RIGHT
                || keyUnshifted == K_LINE_END
                || keyUnshifted == K_TEXT_END
                || keyUnshifted == K_DELETE
                || keyUnshifted == K_WORD_RIGHT) {
            lastMoveDirectionLR = Direction.RIGHT;
        }
    }

    /**
     * Process a character input.
     *
     * @param c The unicode code point that was typed.
     */
    public void onCharPressed(int c) {
        textInput(Character.toString(c));
        cursorFollow = true;
        cursorAnimReset();
    }

    /**
     * Reset the editing state.
     *
     * @param isSingleLine Whether the text is a single line.
     */
    public void initialize(boolean isSingleLine) {
        undoStack.clear();
        redoStack.clear();
        undoCharCount = 0;
        redoCharCount = 0;
        selectEnd = selectStart = 0;
        cursor = 0;
        hasPreferredX = false;
        preferredX = 0;
        singleLine = isSingleLine;
        insertMode = false;
        rowCountPerPage = 0;
    }

    // ---------------------------------------------------------------------------------------
    // Text access
    // ---------------------------------------------------------------------------------------

    /**
     * Fetch a character of the text.
     *
     * @param index The index.
     * @return The character, or 0 if the index is out of range.
     */
    public char getChar(int index) {
        if (index < 0 || index >= text.length()) {
            return 0;
        }
        return text.charAt(index);
    }

    /**
     * Fetch the code point at an index.
     *
     * @param index The index.
     * @return The code point, or 0 if the index is out of range.
     */
    private int getCodePoint(int index) {
        if (index < 0 || index >= text.length()) {
            return 0;
        }
        return Character.codePointAt(text, index);
    }

    /**
     * Find the index of the next character, skipping over surrogate pairs.
     *
     * @param index The current index.
     * @return The index of the next character, which is length + 1 if we are at the end.
     */
    public int getNextCharIndex(int index) {
        final int length = text.length();
        if (index >= length) {
            return length + 1;
        }
        if (Character.isHighSurrogate(text.charAt(index))
                && index + 1 < length
                && Character.isLowSurrogate(text.charAt(index + 1))) {
            return index + 2;
        }
        return index + 1;
    }

    /**
     * Find the index of the previous character, skipping over surrogate pairs.
     *
     * @param index The current index.
     * @return The index of the previous character, or -1 if we are at the start.
     */
    public int getPreviousCharIndex(int index) {
        if (index <= 0) {
            return -1;
        }
        if (index >= 2
                && Character.isLowSurrogate(text.charAt(index - 1))
                && Character.isHighSurrogate(text.charAt(index - 2))) {
            return index - 2;
        }
        return index - 1;
    }

    /**
     * Delete characters from the text, without recording undo information.
     *
     * @param pos The position to delete at.
     * @param count The number of characters to delete.
     */
    public void deleteChars(int pos, int count) {
        text.delete(pos, pos + count);
        editedBefore = editedThisFrame = true;
        textChanged();
    }

    /**
     * Insert characters into the text, without recording undo information. If the text is not
     * resizable, the inserted text is truncated to fit.
     *
     * @param pos The position to insert at.
     * @param newText The text to insert.
     * @return The number of characters that were actually inserted.
     */
    public int insertChars(int pos, @NonNull CharSequence newText) {
        int newTextLength = newText.length();
        final int available = bufCapacity - text.length();
        if (!resizable && newTextLength > available) {
            newTextLength = Math.max(0, available);
            if (newTextLength > 0 && Character.isHighSurrogate(newText.charAt(newTextLength - 1))) {
                --newTextLength;
            }
        }
        if (newTextLength == 0) {
            return 0;
        }
        text.insert(pos, newText, 0, newTextLength);
        editedBefore = editedThisFrame = true;
        textChanged();
        return newTextLength;
    }

    // ---------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------

    /**
     * Fetch the text as it is displayed, with password characters replaced by '*'.
     *
     * @return The display text.
     */
    public String getDisplayText() {
        updateLayout();
        return layoutText;
    }

    /** Rebuild the row layout if anything changed. */
    private void updateLayout() {
        if (!layoutDirty
                && layoutFontSize == fontSize
                && layoutWrapWidth == wrapWidth
                && layoutFlags == flags
                && layoutSingleLine == singleLine) {
            return;
        }
        layoutDirty = false;
        layoutFontSize = fontSize;
        layoutWrapWidth = wrapWidth;
        layoutFlags = flags;
        layoutSingleLine = singleLine;
        layoutText = (flags & InputTextFlags.PASSWORD) != 0 ? passwordText(text) : text.toString();
        buildRowStarts(layoutText, !singleLine, wrapWidth, fontSize, rowStarts);
    }

    /**
     * Create the display text for a password field.
     *
     * @param value The real text.
     * @return A string of '*' characters, one per character so indices match the real text.
     */
    public static String passwordText(@NonNull CharSequence value) {
        return "*".repeat(value.length());
    }

    /**
     * Find the start of each row of text. Rows are split at newlines for multi-line text, and if a
     * wrap width is provided, long rows are wrapped, preferably after blanks. Wrapped rows keep
     * their trailing blanks.
     *
     * @param value The text.
     * @param multiline Whether the text is multi-line.
     * @param wrapWidth The width to wrap at, 0 or less for no wrapping.
     * @param fontSize The font size.
     * @param output Where to store the starting index of each row. There is always at least one
     *     row. If the text ends with a newline, there is an empty row at the end.
     */
    public static void buildRowStarts(
            @NonNull CharSequence value,
            boolean multiline,
            float wrapWidth,
            int fontSize,
            @NonNull IntArrayList output) {
        output.clear();
        output.addInt(0);
        if (!multiline) {
            return;
        }
        final int length = value.length();
        int lineStart = 0;
        while (true) {
            int lineEnd = lineStart;
            while (lineEnd < length && value.charAt(lineEnd) != '\n') {
                ++lineEnd;
            }
            if (wrapWidth > 0.0f) {
                wrapRow(value, lineStart, lineEnd, wrapWidth, fontSize, output);
            }
            if (lineEnd >= length) {
                break;
            }
            lineStart = lineEnd + 1;
            output.addInt(lineStart);
        }
    }

    /**
     * Find word wrapping points within a single line of text.
     *
     * @param value The text.
     * @param start The start of the line.
     * @param end The end of the line, not including the newline.
     * @param wrapWidth The width to wrap at.
     * @param fontSize The font size.
     * @param output Where to add the start of each new row.
     */
    private static void wrapRow(
            @NonNull CharSequence value,
            int start,
            int end,
            float wrapWidth,
            int fontSize,
            @NonNull IntArrayList output) {
        // Input text keeps the blanks at the end of rows, so every character is shown somewhere
        int rowStart = start;
        while (rowStart < end) {
            final int eol =
                    DrawList.calcWordWrapPosition(fontSize, value, rowStart, end, wrapWidth, true);
            if (eol >= end) {
                return;
            }
            output.addInt(eol);
            rowStart = eol;
        }
    }

    /**
     * The number of rows of text.
     *
     * @return The row count, at least 1.
     */
    public int getRowCount() {
        updateLayout();
        return rowStarts.size();
    }

    /**
     * The index of the first character of a row.
     *
     * @param row The row.
     * @return The start of the row.
     */
    public int getRowStart(int row) {
        updateLayout();
        return rowStarts.getInt(row);
    }

    /**
     * The index after the last character of a row, including any newline.
     *
     * @param row The row.
     * @return The end of the row.
     */
    public int getRowEnd(int row) {
        updateLayout();
        return row + 1 < rowStarts.size() ? rowStarts.getInt(row + 1) : text.length();
    }

    /**
     * The index after the last displayed character of a row, which excludes the newline.
     *
     * @param row The row.
     * @return The end of the row contents.
     */
    public int getRowContentEnd(int row) {
        final int end = getRowEnd(row);
        if (end > getRowStart(row) && text.charAt(end - 1) == '\n') {
            return end - 1;
        }
        return end;
    }

    /**
     * Find which row a position is displayed on.
     *
     * @param pos The position.
     * @param useDirection Whether to display the cursor at the end of the previous row when it is
     *     on a word wrapping boundary and the last movement was to the right.
     * @return The row index.
     */
    public int getRowIndex(int pos, boolean useDirection) {
        updateLayout();
        int low = 0;
        int high = rowStarts.size() - 1;
        while (low < high) {
            final int mid = (low + high + 1) >>> 1;
            if (rowStarts.getInt(mid) <= pos) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        if (useDirection
                && low > 0
                && wrapWidth > 0.0f
                && lastMoveDirectionLR == Direction.RIGHT
                && pos == rowStarts.getInt(low)
                && text.charAt(pos - 1) != '\n') {
            --low;
        }
        return low;
    }

    /**
     * Measure the width of part of the display text.
     *
     * @param start The start index.
     * @param end The end index.
     * @return The width in pixels.
     */
    public float measure(int start, int end) {
        updateLayout();
        if (end <= start) {
            return 0.0f;
        }
        final float[] offsets = DrawList.calcTextOffsets(fontSize, layoutText, start, end);
        return offsets[offsets.length - 1];
    }

    /**
     * The height of a line of text in pixels, matching IkGui.getTextLineHeight().
     *
     * @return The line height.
     */
    private float getLineHeight() {
        final Context context = IkGui.getContext();
        return (int) ((float) context.dpiScaleScreen / context.dpiScaleFont * fontSize);
    }

    /**
     * Calculate where the cursor is displayed, relative to the top left of the text.
     *
     * @param pos The cursor position.
     * @param output Where to store the x offset and the y offset of the bottom of the line.
     */
    public void getCursorOffset(int pos, @NonNull Vector2f output) {
        final int row = getRowIndex(pos, true);
        output.x = measure(getRowStart(row), pos);
        output.y = (row + 1) * getLineHeight();
    }

    // ---------------------------------------------------------------------------------------
    // Mouse input handling
    // ---------------------------------------------------------------------------------------

    /**
     * Traverse the layout to locate the nearest character to a display position.
     *
     * @param x The x position relative to the text.
     * @param y The y position relative to the text.
     * @param sideOnLine Where to store whether the position is on the right side of the line (1) or
     *     not (0).
     * @return The index of the character.
     */
    private int locateCoord(float x, float y, int[] sideOnLine) {
        final int length = text.length();
        final float lineHeight = getLineHeight();
        sideOnLine[0] = 0;

        // Search rows to find one that straddles y
        final int rowCount = getRowCount();
        int row = -1;
        for (int r = 0; r < rowCount && getRowStart(r) < length; ++r) {
            if (r == 0 && y < 0) {
                return 0;
            }
            if (y < (r + 1) * lineHeight) {
                row = r;
                break;
            }
        }

        // Below all text, return 'after' the last character
        if (row < 0) {
            sideOnLine[0] = 1;
            return length;
        }

        final int rowStart = getRowStart(row);
        final int rowEnd = getRowEnd(row);
        // Check if it's before the beginning of the line
        if (x < 0) {
            return rowStart;
        }

        final int contentEnd = getRowContentEnd(row);
        final float[] offsets =
                DrawList.calcTextOffsets(fontSize, getDisplayText(), rowStart, contentEnd);
        // Check if it's before the end of the line
        if (x < offsets[offsets.length - 1]) {
            // Search characters in the row for one that straddles x
            for (int k = 0; k < contentEnd - rowStart; ) {
                final int next = getNextCharIndex(rowStart + k) - rowStart;
                final float previousX = offsets[k];
                final float width = offsets[next] - previousX;
                if (x < previousX + width) {
                    sideOnLine[0] = k == 0 ? 0 : 1;
                    if (x < previousX + width / 2) {
                        return rowStart + k;
                    }
                    return rowStart + next;
                }
                k = next;
            }
        }

        // If the last character is a newline, return that, otherwise return 'after' it
        sideOnLine[0] = 1;
        if (rowEnd > rowStart && text.charAt(rowEnd - 1) == '\n') {
            return rowEnd - 1;
        }
        return rowEnd;
    }

    /**
     * On mouse down, move the cursor to the clicked location and reset the selection.
     *
     * @param x The x position relative to the text.
     * @param y The y position relative to the text.
     */
    public void click(float x, float y) {
        // In single line mode, always make y = 0. This lets the drag keep working if the mouse
        // goes off the top or bottom of the text.
        if (singleLine) {
            y = 0;
        }
        final int[] sideOnLine = new int[1];
        cursor = locateCoord(x, y, sideOnLine);
        selectStart = cursor;
        selectEnd = cursor;
        hasPreferredX = false;
        lastMoveDirectionLR = sideOnLine[0] != 0 ? Direction.RIGHT : Direction.LEFT;
    }

    /**
     * On mouse drag, move the cursor and selection endpoint to the clicked location.
     *
     * @param x The x position relative to the text.
     * @param y The y position relative to the text.
     */
    public void drag(float x, float y) {
        if (singleLine) {
            y = 0;
        }
        if (selectStart == selectEnd) {
            selectStart = cursor;
        }
        final int[] sideOnLine = new int[1];
        cursor = selectEnd = locateCoord(x, y, sideOnLine);
        lastMoveDirectionLR = sideOnLine[0] != 0 ? Direction.RIGHT : Direction.LEFT;
    }

    // ---------------------------------------------------------------------------------------
    // Keyboard input handling
    // ---------------------------------------------------------------------------------------

    /**
     * Find the location of a character, and information about the previous row in case we get a
     * move up event.
     *
     * @param n The character index.
     * @return The location information.
     */
    private FindState findCharPos(int n) {
        final int length = text.length();
        if (n == length && singleLine) {
            return new FindState(measure(0, length), 0, length, 0);
        }
        final int row = getRowIndex(n, true);
        final int first = getRowStart(row);
        final int rowLength = getRowEnd(row) - first;
        final int previousFirst = row > 0 ? getRowStart(row - 1) : 0;
        return new FindState(measure(first, n), first, rowLength, previousFirst);
    }

    /** Make the selection/cursor state valid if the string was altered. */
    public void clamp() {
        final int length = text.length();
        if (hasSelection()) {
            selectStart = Math.min(selectStart, length);
            selectEnd = Math.min(selectEnd, length);
            // If clamping forced them to be equal, move the cursor to match
            if (selectStart == selectEnd) {
                cursor = selectStart;
            }
        }
        cursor = Math.min(cursor, length);
    }

    /**
     * Delete characters while updating undo.
     *
     * @param where The position.
     * @param length The number of characters.
     */
    private void delete(int where, int length) {
        makeUndoDelete(where, length);
        deleteChars(where, length);
        hasPreferredX = false;
    }

    /** Delete the selection. */
    private void deleteSelection() {
        clamp();
        if (hasSelection()) {
            if (selectStart < selectEnd) {
                delete(selectStart, selectEnd - selectStart);
                selectEnd = cursor = selectStart;
            } else {
                delete(selectEnd, selectStart - selectEnd);
                selectStart = cursor = selectEnd;
            }
            hasPreferredX = false;
        }
    }

    /** Canonicalize the selection so start is less than or equal to end. */
    private void sortSelection() {
        if (selectEnd < selectStart) {
            final int temp = selectEnd;
            selectEnd = selectStart;
            selectStart = temp;
        }
    }

    /** Move the cursor to the first character of the selection. */
    private void moveToFirst() {
        if (hasSelection()) {
            sortSelection();
            cursor = selectStart;
            selectEnd = selectStart;
            hasPreferredX = false;
        }
    }

    /** Move the cursor to the last character of the selection. */
    private void moveToLast() {
        if (hasSelection()) {
            sortSelection();
            clamp();
            cursor = selectEnd;
            selectStart = selectEnd;
            hasPreferredX = false;
        }
    }

    /**
     * Find the start of the line the cursor is on, supporting word wrapping.
     *
     * @param pos The cursor position.
     * @return The start of the line.
     */
    private int moveLineStart(int pos) {
        if (singleLine) {
            return 0;
        }
        if (wrapWidth > 0.0f) {
            final int rowStart = getRowStart(getRowIndex(pos, true));
            // If we are already on a visible beginning of line, return the real beginning of line
            if (rowStart != pos) {
                return rowStart;
            }
        }
        while (pos > 0) {
            final int previous = getPreviousCharIndex(pos);
            if (getChar(previous) == '\n') {
                break;
            }
            pos = previous;
        }
        return pos;
    }

    /**
     * Find the end of the line the cursor is on, supporting word wrapping.
     *
     * @param pos The cursor position.
     * @return The end of the line.
     */
    private int moveLineEnd(int pos) {
        final int length = text.length();
        if (singleLine) {
            return length;
        }
        if (wrapWidth > 0.0f) {
            final int row = getRowIndex(pos, true);
            final int rowEnd = getRowEnd(row);
            final boolean wrapped =
                    row + 1 < getRowCount() && rowEnd > 0 && text.charAt(rowEnd - 1) != '\n';
            if (wrapped) {
                // If we are already on a visible end of line, switch to the regular handler
                final boolean alreadyAtEnd = rowEnd == pos && lastMoveDirectionLR != Direction.LEFT;
                if (!alreadyAtEnd && rowEnd > pos) {
                    return rowEnd;
                }
            }
        }
        while (pos < length && getChar(pos) != '\n') {
            pos = getNextCharIndex(pos);
        }
        return pos;
    }

    /**
     * Check if a character is a blank (space, tab, ideographic space).
     *
     * @param c The character.
     * @return True if the character is blank.
     */
    public static boolean isBlank(int c) {
        return c == ' ' || c == '\t' || c == 0x3000;
    }

    /**
     * Check if a character separates words.
     *
     * @param c The character.
     * @return True if the character is a separator.
     */
    private static boolean isSeparator(int c) {
        for (int separator : SEPARATORS) {
            if (c == separator) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an index is a word boundary, when moving right to left.
     *
     * @param index The index.
     * @return True if the index is a word boundary.
     */
    private boolean isWordBoundaryFromRight(int index) {
        // For passwords, we don't want actions such as Ctrl+Arrow to leak the fact that the
        // underlying data are blanks or separators.
        if ((flags & InputTextFlags.PASSWORD) != 0 || index <= 0) {
            return false;
        }
        final int current = getCodePoint(index);
        final int previous = getCodePoint(getPreviousCharIndex(index));
        final boolean previousWhite = isBlank(previous);
        final boolean previousSeparator = isSeparator(previous);
        final boolean currentWhite = isBlank(current);
        final boolean currentSeparator = isSeparator(current);
        return ((previousWhite || previousSeparator) && !(currentSeparator || currentWhite))
                || (currentSeparator && !previousSeparator);
    }

    /**
     * Check if an index is a word boundary, when moving left to right.
     *
     * @param index The index.
     * @return True if the index is a word boundary.
     */
    private boolean isWordBoundaryFromLeft(int index) {
        if ((flags & InputTextFlags.PASSWORD) != 0 || index <= 0) {
            return false;
        }
        final int previous = getCodePoint(index);
        final int current = getCodePoint(getPreviousCharIndex(index));
        final boolean previousWhite = isBlank(previous);
        final boolean previousSeparator = isSeparator(previous);
        final boolean currentWhite = isBlank(current);
        final boolean currentSeparator = isSeparator(current);
        return (previousWhite && !(currentSeparator || currentWhite))
                || (currentSeparator && !previousSeparator);
    }

    /**
     * Move left one word.
     *
     * @param index The current position.
     * @return The new position.
     */
    private int moveWordLeft(int index) {
        index = getPreviousCharIndex(index);
        while (index >= 0 && !isWordBoundaryFromRight(index)) {
            index = getPreviousCharIndex(index);
        }
        return Math.max(index, 0);
    }

    /**
     * Move right one word, Mac OS X style (to the end of the word).
     *
     * @param index The current position.
     * @return The new position.
     */
    public int moveWordRightMac(int index) {
        final int length = text.length();
        index = getNextCharIndex(index);
        while (index < length && !isWordBoundaryFromLeft(index)) {
            index = getNextCharIndex(index);
        }
        return Math.min(index, length);
    }

    /**
     * Move right one word, Windows style (to the start of the next word).
     *
     * @param index The current position.
     * @return The new position.
     */
    private int moveWordRightWindows(int index) {
        final int length = text.length();
        index = getNextCharIndex(index);
        while (index < length && !isWordBoundaryFromRight(index)) {
            index = getNextCharIndex(index);
        }
        return Math.min(index, length);
    }

    /**
     * Move right one word, depending on the platform.
     *
     * @param index The current position.
     * @return The new position.
     */
    private int moveWordRight(int index) {
        return macOSXBehaviors ? moveWordRightMac(index) : moveWordRightWindows(index);
    }

    /** Update the selection and cursor to match each other. */
    public void prepSelectionAtCursor() {
        if (!hasSelection()) {
            selectStart = selectEnd = cursor;
        } else {
            cursor = selectEnd;
        }
    }

    /**
     * Delete the selection.
     *
     * @return True if anything was cut.
     */
    public boolean cut() {
        if (hasSelection()) {
            deleteSelection();
            hasPreferredX = false;
            return true;
        }
        return false;
    }

    /**
     * Replace the selection with text.
     *
     * @param newText The text to paste.
     * @return True if the text was inserted.
     */
    public boolean paste(@NonNull CharSequence newText) {
        // If there's a selection, the paste should delete it
        clamp();
        deleteSelection();
        // Try to insert the characters
        final int inserted = insertChars(cursor, newText);
        if (inserted > 0) {
            makeUndoInsert(cursor, inserted);
            cursor += inserted;
            hasPreferredX = false;
            return true;
        }
        return false;
    }

    /**
     * Process text input, replacing the selection.
     *
     * @param newText The text that was input.
     */
    public void textInput(@NonNull String newText) {
        // Can't add a newline in single line mode
        if (newText.isEmpty() || (newText.charAt(0) == '\n' && singleLine)) {
            return;
        }

        if (insertMode && !hasSelection() && cursor < text.length()) {
            final int replacedLength = getNextCharIndex(cursor) - cursor;
            makeUndoReplace(cursor, replacedLength, newText.length());
            deleteChars(cursor, replacedLength);
            final int inserted = insertChars(cursor, newText);
            if (inserted > 0) {
                cursor += inserted;
                hasPreferredX = false;
            }
        } else {
            // Implicitly clamps
            deleteSelection();
            final int inserted = insertChars(cursor, newText);
            if (inserted > 0) {
                makeUndoInsert(cursor, inserted);
                cursor += inserted;
                hasPreferredX = false;
            }
        }
    }

    /**
     * Replace all the text, as a single undo record.
     *
     * @param newText The new text.
     */
    public void replace(@NonNull CharSequence newText) {
        makeUndoReplace(0, text.length(), newText.length());
        deleteChars(0, text.length());
        cursor = selectStart = selectEnd = 0;
        if (newText.isEmpty()) {
            return;
        }
        final int inserted = insertChars(0, newText);
        if (inserted > 0) {
            cursor = selectStart = selectEnd = inserted;
            hasPreferredX = false;
        }
    }

    /**
     * Process a keyboard input.
     *
     * @param key One of the K_* constants, optionally combined with K_SHIFT.
     */
    private void key(int key) {
        switch (key) {
            case K_UNDO -> {
                undo();
                hasPreferredX = false;
            }
            case K_REDO -> {
                redo();
                hasPreferredX = false;
            }
            case K_LEFT -> {
                // If there's a selection, move the cursor to the start of the selection
                if (hasSelection()) {
                    moveToFirst();
                } else if (cursor > 0) {
                    cursor = getPreviousCharIndex(cursor);
                }
                hasPreferredX = false;
            }
            case K_RIGHT -> {
                // If there's a selection, move the cursor to the end of the selection
                if (hasSelection()) {
                    moveToLast();
                } else {
                    cursor = getNextCharIndex(cursor);
                }
                clamp();
                hasPreferredX = false;
            }
            case K_LEFT | K_SHIFT -> {
                clamp();
                prepSelectionAtCursor();
                // Move the selection left
                if (selectEnd > 0) {
                    selectEnd = getPreviousCharIndex(selectEnd);
                }
                cursor = selectEnd;
                hasPreferredX = false;
            }
            case K_WORD_LEFT -> {
                if (hasSelection()) {
                    moveToFirst();
                } else {
                    cursor = moveWordLeft(cursor);
                    clamp();
                }
            }
            case K_WORD_LEFT | K_SHIFT -> {
                if (!hasSelection()) {
                    prepSelectionAtCursor();
                }
                cursor = moveWordLeft(cursor);
                selectEnd = cursor;
                clamp();
            }
            case K_WORD_RIGHT -> {
                if (hasSelection()) {
                    moveToLast();
                } else {
                    cursor = moveWordRight(cursor);
                    clamp();
                }
            }
            case K_WORD_RIGHT | K_SHIFT -> {
                if (!hasSelection()) {
                    prepSelectionAtCursor();
                }
                cursor = moveWordRight(cursor);
                selectEnd = cursor;
                clamp();
            }
            case K_RIGHT | K_SHIFT -> {
                prepSelectionAtCursor();
                // Move the selection right
                selectEnd = getNextCharIndex(selectEnd);
                clamp();
                cursor = selectEnd;
                hasPreferredX = false;
            }
            case K_DOWN, K_DOWN | K_SHIFT, K_PAGE_DOWN, K_PAGE_DOWN | K_SHIFT -> keyDown(key);
            case K_UP, K_UP | K_SHIFT, K_PAGE_UP, K_PAGE_UP | K_SHIFT -> keyUp(key);
            case K_DELETE, K_DELETE | K_SHIFT -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursor < text.length()) {
                    delete(cursor, getNextCharIndex(cursor) - cursor);
                }
                hasPreferredX = false;
            }
            case K_BACKSPACE, K_BACKSPACE | K_SHIFT -> {
                if (hasSelection()) {
                    deleteSelection();
                } else {
                    clamp();
                    if (cursor > 0) {
                        final int previous = getPreviousCharIndex(cursor);
                        delete(previous, cursor - previous);
                        cursor = previous;
                    }
                }
                hasPreferredX = false;
            }
            case K_TEXT_START -> {
                cursor = selectStart = selectEnd = 0;
                hasPreferredX = false;
            }
            case K_TEXT_END -> {
                cursor = text.length();
                selectStart = selectEnd = 0;
                hasPreferredX = false;
            }
            case K_TEXT_START | K_SHIFT -> {
                prepSelectionAtCursor();
                cursor = selectEnd = 0;
                hasPreferredX = false;
            }
            case K_TEXT_END | K_SHIFT -> {
                prepSelectionAtCursor();
                cursor = selectEnd = text.length();
                hasPreferredX = false;
            }
            case K_LINE_START -> {
                clamp();
                moveToFirst();
                cursor = moveLineStart(cursor);
                hasPreferredX = false;
            }
            case K_LINE_END -> {
                clamp();
                moveToLast();
                cursor = moveLineEnd(cursor);
                hasPreferredX = false;
            }
            case K_LINE_START | K_SHIFT -> {
                clamp();
                prepSelectionAtCursor();
                cursor = moveLineStart(cursor);
                selectEnd = cursor;
                hasPreferredX = false;
            }
            case K_LINE_END | K_SHIFT -> {
                clamp();
                prepSelectionAtCursor();
                cursor = moveLineEnd(cursor);
                selectEnd = cursor;
                hasPreferredX = false;
            }
            default -> {
                // Not a key we handle
            }
        }
    }

    /**
     * Move the cursor down a row or a page.
     *
     * @param key The key, K_DOWN or K_PAGE_DOWN, optionally with K_SHIFT.
     */
    private void keyDown(int key) {
        final boolean select = (key & K_SHIFT) != 0;
        final boolean isPage = (key & ~K_SHIFT) == K_PAGE_DOWN;
        final int rowCount = isPage ? rowCountPerPage : 1;

        if (!isPage && singleLine) {
            // Up and down in single line behave like left and right
            key(K_RIGHT | (key & K_SHIFT));
            return;
        }

        if (select) {
            prepSelectionAtCursor();
        } else if (hasSelection()) {
            moveToLast();
        }

        // Compute the current position of the cursor
        clamp();
        FindState find = findCharPos(cursor);
        int findFirst = find.firstChar();
        int findLength = find.length();

        for (int j = 0; j < rowCount; ++j) {
            final float goalX = hasPreferredX ? preferredX : find.x();
            final int start = findFirst + findLength;

            if (findLength == 0) {
                break;
            }

            // Find the character position down a row. There is no row after the end of the text.
            cursor = start;
            final int row = getRowIndex(start, false);
            final boolean rowExists = getRowStart(row) == start;
            final int rowContentEnd = rowExists ? getRowContentEnd(row) : start;
            final float[] offsets =
                    DrawList.calcTextOffsets(fontSize, getDisplayText(), start, rowContentEnd);
            while (cursor < rowContentEnd) {
                final int next = getNextCharIndex(cursor);
                if (offsets[next - start] > goalX) {
                    break;
                }
                cursor = next;
            }
            clamp();

            if (cursor == findFirst + findLength) {
                lastMoveDirectionLR = Direction.LEFT;
            }
            hasPreferredX = true;
            preferredX = goalX;

            if (select) {
                selectEnd = cursor;
            }

            // Go to the next line
            findFirst = start;
            findLength = rowExists ? getRowEnd(row) - start : 0;
            find = new FindState(find.x(), findFirst, findLength, find.previousFirst());
        }
    }

    /**
     * Move the cursor up a row or a page.
     *
     * @param key The key, K_UP or K_PAGE_UP, optionally with K_SHIFT.
     */
    private void keyUp(int key) {
        final boolean select = (key & K_SHIFT) != 0;
        final boolean isPage = (key & ~K_SHIFT) == K_PAGE_UP;
        final int rowCount = isPage ? rowCountPerPage : 1;

        if (!isPage && singleLine) {
            // Up and down in single line behave like left and right
            key(K_LEFT | (key & K_SHIFT));
            return;
        }

        if (select) {
            prepSelectionAtCursor();
        } else if (hasSelection()) {
            moveToFirst();
        }

        // Compute the current position of the cursor
        clamp();
        final FindState find = findCharPos(cursor);
        int findFirst = find.firstChar();
        int findPreviousFirst = find.previousFirst();

        for (int j = 0; j < rowCount; ++j) {
            final float goalX = hasPreferredX ? preferredX : find.x();

            // Can only go up if there's a previous row
            if (findPreviousFirst == findFirst) {
                break;
            }

            // Find the character position up a row
            cursor = findPreviousFirst;
            final int row = getRowIndex(findPreviousFirst, false);
            final int rowContentEnd = getRowContentEnd(row);
            final float[] offsets =
                    DrawList.calcTextOffsets(
                            fontSize, getDisplayText(), findPreviousFirst, rowContentEnd);
            while (cursor < rowContentEnd) {
                final int next = getNextCharIndex(cursor);
                if (offsets[next - findPreviousFirst] > goalX) {
                    break;
                }
                cursor = next;
            }
            clamp();

            if (cursor == findFirst) {
                lastMoveDirectionLR = Direction.RIGHT;
            } else if (cursor == findPreviousFirst) {
                lastMoveDirectionLR = Direction.LEFT;
            }
            hasPreferredX = true;
            preferredX = goalX;

            if (select) {
                selectEnd = cursor;
            }

            // Go to the previous line
            findFirst = findPreviousFirst;
            findPreviousFirst = row > 0 ? getRowStart(row - 1) : findPreviousFirst;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Undo processing
    // ---------------------------------------------------------------------------------------

    /** Discard all the redo records. */
    private void flushRedo() {
        redoStack.clear();
        redoCharCount = 0;
    }

    /**
     * Add an undo record, discarding old records if we run out of room.
     *
     * @param where Where the edit happened.
     * @param deleteLength The number of characters to delete to revert it.
     * @param insertText The text to insert to revert it.
     */
    private void createUndo(int where, int deleteLength, @NonNull String insertText) {
        flushRedo();
        // If the record is too large to store at all, clear everything
        if (insertText.length() > UNDO_CHAR_COUNT) {
            undoStack.clear();
            undoCharCount = 0;
            return;
        }
        while (!undoStack.isEmpty()
                && (undoStack.size() >= UNDO_STATE_COUNT
                        || undoCharCount + insertText.length() > UNDO_CHAR_COUNT)) {
            undoCharCount -= undoStack.removeFirst().insertText().length();
        }
        undoStack.add(new UndoRecord(where, deleteLength, insertText));
        undoCharCount += insertText.length();
    }

    /**
     * Copy part of the text, clamped to the text.
     *
     * @param start The start index.
     * @param length The number of characters.
     * @return The text.
     */
    private String substring(int start, int length) {
        final int textLength = text.length();
        start = MathUtil.clamp(start, 0, textLength);
        return text.substring(start, MathUtil.clamp((long) start + length, start, textLength));
    }

    /**
     * Record the insertion of characters.
     *
     * @param where The position.
     * @param length The number of characters inserted.
     */
    private void makeUndoInsert(int where, int length) {
        createUndo(where, length, "");
    }

    /**
     * Record the deletion of characters, before they are deleted.
     *
     * @param where The position.
     * @param length The number of characters that will be deleted.
     */
    private void makeUndoDelete(int where, int length) {
        createUndo(where, 0, substring(where, length));
    }

    /**
     * Record the replacement of characters, before they are replaced.
     *
     * @param where The position.
     * @param oldLength The number of characters that will be removed.
     * @param newLength The number of characters that will be inserted.
     */
    private void makeUndoReplace(int where, int oldLength, int newLength) {
        createUndo(where, newLength, substring(where, oldLength));
    }

    /** Undo the last edit. */
    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        final UndoRecord undo = undoStack.removeLast();
        undoCharCount -= undo.insertText().length();

        // The redo record will need to re-insert the characters that get deleted
        final String deleted = substring(undo.where(), undo.deleteLength());
        if (undo.deleteLength() > 0) {
            deleteChars(undo.where(), deleted.length());
        }
        int inserted = 0;
        if (!undo.insertText().isEmpty()) {
            inserted = insertChars(undo.where(), undo.insertText());
        }
        cursor = undo.where() + inserted;

        while (!redoStack.isEmpty()
                && (redoStack.size() >= UNDO_STATE_COUNT
                        || redoCharCount + deleted.length() > UNDO_CHAR_COUNT)) {
            redoCharCount -= redoStack.removeFirst().insertText().length();
        }
        if (deleted.length() <= UNDO_CHAR_COUNT) {
            redoStack.add(new UndoRecord(undo.where(), inserted, deleted));
            redoCharCount += deleted.length();
        }
    }

    /** Redo the last undone edit. */
    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        final UndoRecord redo = redoStack.removeLast();
        redoCharCount -= redo.insertText().length();

        // The undo record needs to store the characters that get deleted
        final String deleted = substring(redo.where(), redo.deleteLength());
        if (redo.deleteLength() > 0) {
            deleteChars(redo.where(), deleted.length());
        }
        int inserted = 0;
        if (!redo.insertText().isEmpty()) {
            inserted = insertChars(redo.where(), redo.insertText());
        }
        cursor = redo.where() + inserted;

        while (!undoStack.isEmpty()
                && (undoStack.size() >= UNDO_STATE_COUNT
                        || undoCharCount + deleted.length() > UNDO_CHAR_COUNT)) {
            undoCharCount -= undoStack.removeFirst().insertText().length();
        }
        if (deleted.length() <= UNDO_CHAR_COUNT) {
            undoStack.add(new UndoRecord(redo.where(), inserted, deleted));
            undoCharCount += deleted.length();
        }
    }

    /**
     * Find the shortest single replacement that turns the old text into the new text, and record it
     * as an undo record. This doesn't alter the text.
     *
     * @param oldText The text before an external modification.
     * @param newText The text after an external modification.
     */
    public void reconcileUndoState(@NonNull CharSequence oldText, @NonNull CharSequence newText) {
        final int oldLength = oldText.length();
        final int newLength = newText.length();
        final int shorterLength = Math.min(oldLength, newLength);
        int firstDiff = 0;
        while (firstDiff < shorterLength
                && oldText.charAt(firstDiff) == newText.charAt(firstDiff)) {
            ++firstDiff;
        }
        if (firstDiff == oldLength && firstDiff == newLength) {
            return;
        }

        int oldLastDiff = oldLength - 1;
        int newLastDiff = newLength - 1;
        while (oldLastDiff >= firstDiff
                && newLastDiff >= firstDiff
                && oldText.charAt(oldLastDiff) == newText.charAt(newLastDiff)) {
            --oldLastDiff;
            --newLastDiff;
        }

        final int insertLength = newLastDiff - firstDiff + 1;
        final int deleteLength = oldLastDiff - firstDiff + 1;
        if (insertLength > 0 || deleteLength > 0) {
            createUndo(
                    firstDiff,
                    Math.max(0, insertLength),
                    oldText.subSequence(firstDiff, firstDiff + Math.max(0, deleteLength))
                            .toString());
        }
    }

    /**
     * The number of undo records, for debugging.
     *
     * @return The number of undo records.
     */
    public int getUndoCount() {
        return undoStack.size();
    }

    /**
     * The number of redo records, for debugging.
     *
     * @return The number of redo records.
     */
    public int getRedoCount() {
        return redoStack.size();
    }
}
