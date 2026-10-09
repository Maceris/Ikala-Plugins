package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.gui.util.MathUtil;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

/**
 * Shared state of inputText(), passed as an argument to your callback when an
 * InputTextFlags.CALLBACK_* flag is used.
 *
 * <ul>
 *   <li>CALLBACK_EDIT: Callback on buffer edit.
 *   <li>CALLBACK_ALWAYS: Callback on each iteration.
 *   <li>CALLBACK_COMPLETION: Callback on pressing TAB.
 *   <li>CALLBACK_HISTORY: Callback on pressing Up/Down arrows.
 *   <li>CALLBACK_CHAR_FILTER: Callback on character inputs to replace or discard them. Modify the
 *       event char to replace or discard (set to 0).
 *   <li>CALLBACK_RESIZE: Callback when the text grows beyond the capacity of the string.
 * </ul>
 *
 * <p>Positions and lengths are in Java (UTF-16) characters. To modify the text buffer in a
 * callback, prefer using {@link #insertChars(int, String)} / {@link #deleteChars(int, int)}. If you
 * modify the {@link #getBuffer() buffer} directly, you need to set {@link #setBufDirty(boolean)
 * bufDirty} so the input text can update its internal state.
 */
@Getter
@NoArgsConstructor
public class GuiInputTextCallbackData {
    /**
     * Which callback this is for, one of the InputTextFlags.CALLBACK_* flags. Read only.
     *
     * @see InputTextFlags
     */
    @Setter private int eventFlag;

    /**
     * The flags that were passed to inputText(). Read only.
     *
     * @see InputTextFlags
     */
    @Setter private int flags;

    /** The widget ID. Read only. */
    @Setter private int id;

    /** The key that was pressed (Up/Down/Tab). Read only. [Completion, History] */
    @Setter private Key eventKey = Key.NONE;

    /** The character input. Replace it with another one, or set it to 0 to drop it. [CharFilter] */
    private int eventChar;

    /** Whether the input field just got activated. Read only. [Always] */
    @Setter private boolean eventActivated;

    /** Set if you modify the buffer directly. [Completion, History, Always] */
    @Setter private boolean bufDirty;

    /**
     * The text buffer. You may modify it directly, but then need to set bufDirty. [Resize,
     * Completion, History, Always]
     */
    @Setter @NonNull private StringBuilder buffer = new StringBuilder();

    /**
     * The capacity of the buffer, the number of characters it can hold. Read only. [Resize,
     * Completion, History, Always]
     */
    @Setter private int bufSize;

    /** Whether the buffer can grow beyond its capacity when inserting text. Read only. */
    @Setter private boolean resizable;

    /** The cursor position. [Completion, History, Always, CharFilter] */
    @Setter private int cursorPos;

    /**
     * The start of the selection, equal to the selection end when there is no selection.
     * [Completion, History, Always, CharFilter]
     */
    @Setter private int selectionStart;

    /** The end of the selection. [Completion, History, Always, CharFilter] */
    @Setter private int selectionEnd;

    /**
     * Set the character input, for character filter callbacks.
     *
     * @param c The new character, or 0 to discard the input.
     */
    public void setEventChar(char c) {
        eventChar = c;
    }

    /**
     * Set the character input, for character filter callbacks.
     *
     * @param c The new character (unicode code point), or 0 to discard the input.
     */
    public void setEventChar(int c) {
        eventChar = c;
    }

    /**
     * The current text.
     *
     * @return The contents of the buffer.
     */
    public String getBuf() {
        return buffer.toString();
    }

    /**
     * The length of the text in the buffer.
     *
     * @return The length of the text, in characters.
     */
    public int getBufTextLen() {
        return buffer.length();
    }

    /**
     * Delete characters from the text. Resets the selection.
     *
     * @param pos The position to start deleting from.
     * @param count The number of characters to delete.
     */
    public void deleteChars(int pos, int count) {
        final int length = buffer.length();
        pos = MathUtil.clamp(pos, 0, length);
        count = MathUtil.clamp(count, 0, length - pos);
        buffer.delete(pos, pos + count);

        if (cursorPos >= pos + count) {
            cursorPos -= count;
        } else if (cursorPos >= pos) {
            cursorPos = pos;
        }
        selectionStart = selectionEnd = cursorPos;
        bufDirty = true;
    }

    /**
     * Insert text. If the string is not resizable, the text is truncated to fit the capacity of the
     * buffer. Resets the selection.
     *
     * @param pos The position to insert at.
     * @param newText The text to insert.
     */
    public void insertChars(int pos, @NonNull String newText) {
        if (newText.isEmpty()) {
            return;
        }
        final boolean isResizable = resizable || (flags & InputTextFlags.CALLBACK_RESIZE) != 0;
        final int length = buffer.length();
        int newTextLength = newText.length();

        // We support partial insertion
        final int available = bufSize - length;
        if (!isResizable && newTextLength > available) {
            newTextLength = Math.max(0, available);
            if (newTextLength > 0 && Character.isHighSurrogate(newText.charAt(newTextLength - 1))) {
                --newTextLength;
            }
        }
        if (newTextLength == 0) {
            return;
        }

        if (isResizable && length + newTextLength > bufSize) {
            bufSize = length + newTextLength;
        }

        pos = MathUtil.clamp(pos, 0, length);
        buffer.insert(pos, newText, 0, newTextLength);

        bufDirty = true;
        if (cursorPos >= pos) {
            cursorPos += newTextLength;
        }
        cursorPos = MathUtil.clamp(cursorPos, 0, buffer.length());
        selectionStart = selectionEnd = cursorPos;
    }

    /** Select all the text. */
    public void selectAll() {
        selectionStart = 0;
        cursorPos = selectionEnd = buffer.length();
    }

    /**
     * Set the selection, moving the cursor to the end.
     *
     * @param start The start of the selection.
     * @param end The end of the selection.
     */
    public void setSelection(int start, int end) {
        final int length = buffer.length();
        selectionStart = MathUtil.clamp(start, 0, length);
        cursorPos = selectionEnd = MathUtil.clamp(end, 0, length);
    }

    /** Clear the selection. */
    public void clearSelection() {
        selectionStart = selectionEnd = buffer.length();
    }

    /**
     * Check if there is any text selected.
     *
     * @return True if there is a selection.
     */
    public boolean hasSelection() {
        return selectionStart != selectionEnd;
    }
}
