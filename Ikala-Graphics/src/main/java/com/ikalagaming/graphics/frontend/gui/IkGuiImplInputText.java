package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.*;
import com.ikalagaming.graphics.frontend.gui.flags.*;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.KeyChord;
import com.ikalagaming.graphics.frontend.gui.util.MathUtil;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;
import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.math.BigInteger;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text inputs (inputText(), inputTextMultiline(), inputTextWithHint()), and the scalar inputs that
 * are built on them (inputInt(), inputFloat(), inputScalar(), etc.).
 *
 * <p>Dear ImGui edits a char buffer provided by the caller. Here we edit an {@link IkString}
 * instead, and all positions are in Java (UTF-16) characters rather than UTF-8 bytes.
 */
@Slf4j
class IkGuiImplInputText {
    /**
     * Upper limit for displaying single line text. The pathological worst case is a long line
     * without any newlines, which would produce a huge number of draw commands.
     */
    private static final int BUF_DISPLAY_MAX_LENGTH = 2 * 1024 * 1024;

    /** The capacity of the temporary string used to edit scalars as text. */
    private static final int SCALAR_BUFFER_SIZE = 64;

    /** Matches the start of a floating point number, like scanf("%f") would read. */
    private static final Pattern FLOAT_PATTERN =
            Pattern.compile("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    /** Matches the start of a decimal integer, like scanf("%d") would read. */
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^[+-]?\\d+");

    /** Matches the start of a hexadecimal integer, like scanf("%x") would read. */
    private static final Pattern HEXADECIMAL_PATTERN =
            Pattern.compile("^[+-]?(0[xX])?[0-9a-fA-F]+");

    /** Matches the start of an octal integer, like scanf("%o") would read. */
    private static final Pattern OCTAL_PATTERN = Pattern.compile("^[+-]?[0-7]+");

    static Context context;

    // ---------------------------------------------------------------------------------------
    // Text inputs
    // ---------------------------------------------------------------------------------------

    /**
     * A single line text input.
     *
     * @param label The label, which is also used for the ID.
     * @param text The text to edit.
     * @param inputTextFlags Input text flags.
     * @param callback The callback for any InputTextFlags.CALLBACK_* flags, may be null.
     * @return True if the text was edited (or validated with ENTER_RETURNS_TRUE).
     * @see InputTextFlags
     */
    public static boolean inputText(
            String label,
            @NonNull IkString text,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        if ((inputTextFlags & InputTextFlags.INTERNAL_MULTILINE) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Call inputTextMultiline() for multi-line text inputs");
            inputTextFlags &= ~InputTextFlags.INTERNAL_MULTILINE;
        }
        return inputTextEx(label, null, text, 0.0f, 0.0f, inputTextFlags, callback);
    }

    /**
     * A multi-line text input.
     *
     * @param label The label, which is also used for the ID.
     * @param text The text to edit.
     * @param width The width, 0 for the default item width, negative to align to the right.
     * @param height The height, 0 for a default of 8 lines, negative to align to the bottom.
     * @param inputTextFlags Input text flags.
     * @param callback The callback for any InputTextFlags.CALLBACK_* flags, may be null.
     * @return True if the text was edited (or validated with ENTER_RETURNS_TRUE).
     * @see InputTextFlags
     */
    public static boolean inputTextMultiline(
            String label,
            @NonNull IkString text,
            float width,
            float height,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return inputTextEx(
                label,
                null,
                text,
                width,
                height,
                inputTextFlags | InputTextFlags.INTERNAL_MULTILINE,
                callback);
    }

    /**
     * A single line text input that displays a hint when empty.
     *
     * @param label The label, which is also used for the ID.
     * @param hint The text to display when the input is empty.
     * @param text The text to edit.
     * @param inputTextFlags Input text flags.
     * @param callback The callback for any InputTextFlags.CALLBACK_* flags, may be null.
     * @return True if the text was edited (or validated with ENTER_RETURNS_TRUE).
     * @see InputTextFlags
     */
    public static boolean inputTextWithHint(
            String label,
            String hint,
            @NonNull IkString text,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        if ((inputTextFlags & InputTextFlags.INTERNAL_MULTILINE) != 0) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Call inputTextMultiline() or inputTextEx() manually for multi-line text with hints");
            inputTextFlags &= ~InputTextFlags.INTERNAL_MULTILINE;
        }
        return inputTextEx(label, hint, text, 0.0f, 0.0f, inputTextFlags, callback);
    }

    /**
     * Fetch the input text state for an item, if it is the one being edited (or the last one that
     * was).
     *
     * @param id The item ID.
     * @return The state, or null if the state belongs to a different item.
     */
    static InputTextState getInputTextState(int id) {
        return id != 0 && context.inputTextState.id == id ? context.inputTextState : null;
    }

    /**
     * Whether the last item is an active text input. This may be useful to apply workarounds based
     * on distinguishing whether an item is active as a text input field.
     *
     * @return True if the last item is being edited as text.
     */
    static boolean isItemActiveAsInputText() {
        return context.activeID != 0
                && context.activeID == context.lastItemData.id
                && context.inputTextState.id == context.lastItemData.id;
    }

    /**
     * Check if a shortcut was pressed, routed to the input text. This uses the default routing
     * policy (focused), so other code can also use shortcuts like Ctrl+A in the same window.
     *
     * @param id The ID of the input text, which is the owner.
     * @param keyMods The modifiers that need to be held.
     * @param key The key.
     * @param repeat Whether to allow repeats.
     * @return True if the shortcut was pressed.
     * @see KeyModFlags
     */
    private static boolean shortcut(int id, int keyMods, @NonNull Key key, boolean repeat) {
        return IkGuiImplKeys.shortcut(
                KeyChord.of(keyMods, key), repeat ? InputFlags.REPEAT : InputFlags.NONE, id);
    }

    /**
     * As the input text retains text data while active, and users may not retain it themselves, we
     * need a hook to reapply data back to the user string on the deactivation frame.
     *
     * @param id The ID of the item being deactivated.
     */
    static void inputTextDeactivateHook(int id) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_ACTIVE_ID, "InputTextDeactivateHook() id = 0x%08X", id);
        final InputTextState state = context.inputTextState;
        if (id == 0 || state.id != id || context.activeID != id) {
            return;
        }
        if (!state.editedBefore) {
            return;
        }
        state.editedBefore = false;
        context.inputTextDeactivatedState.id = state.id;
        context.inputTextDeactivatedState.elapseFrame = context.frameCount + 1;
        context.inputTextDeactivatedState.text =
                (state.flags & InputTextFlags.READ_ONLY) != 0 ? "" : state.getText();
    }

    /**
     * Filter a character that was input.
     *
     * @param state The input text state.
     * @param c The character (unicode code point).
     * @param callback The user callback, may be null.
     * @param allowedChars If not empty, the only characters that are allowed.
     * @param inputSourceIsClipboard Whether the character came from the clipboard.
     * @return The (possibly replaced) character, or -1 to discard it.
     */
    private static int filterCharacter(
            @NonNull InputTextState state,
            int c,
            GuiInputTextCallback callback,
            @NonNull String allowedChars,
            boolean inputSourceIsClipboard) {
        final int flags = state.flags;
        final boolean multiline = (flags & InputTextFlags.INTERNAL_MULTILINE) != 0;

        // Filter non-printable characters
        boolean applyNamedFilters = true;
        if (c < 0x20) {
            boolean pass = false;
            if (c == '\n' && inputSourceIsClipboard && !multiline) {
                // In single line mode, replace newlines with a space
                c = ' ';
                pass = true;
            }
            pass |= c == '\n' && multiline;
            pass |= c == '\t' && (flags & InputTextFlags.ALLOW_TAB_INPUT) != 0;
            if (!pass) {
                return -1;
            }
            // Override named filters below so newlines and tabs can still be inserted
            applyNamedFilters = false;
        }

        if (!inputSourceIsClipboard) {
            // We ignore the ASCII representation of delete (emitted from backspace on OS X)
            if (c == 127) {
                return -1;
            }
            // Filter the private unicode range, GLFW on OS X seems to send private characters
            // for special keys like arrow keys
            if (c >= 0xE000 && c <= 0xF8FF) {
                return -1;
            }
        }

        if (!Character.isValidCodePoint(c)) {
            return -1;
        }

        // Generic named filters
        final int namedFilters =
                InputTextFlags.CHARS_DECIMAL
                        | InputTextFlags.CHARS_HEXADECIMAL
                        | InputTextFlags.CHARS_UPPERCASE
                        | InputTextFlags.CHARS_NO_BLANK
                        | InputTextFlags.CHARS_SCIENTIFIC
                        | InputTextFlags.INTERNAL_LOCALIZE_DECIMAL_POINT;
        if (applyNamedFilters && (flags & namedFilters) != 0) {
            final char decimalPoint = getLocaleDecimalPoint();
            if ((flags
                                    & (InputTextFlags.CHARS_DECIMAL
                                            | InputTextFlags.CHARS_SCIENTIFIC
                                            | InputTextFlags.INTERNAL_LOCALIZE_DECIMAL_POINT))
                            != 0
                    && (c == '.' || c == ',')) {
                c = decimalPoint;
            }

            // Full-width to half-width conversion for numeric fields
            if ((flags
                                    & (InputTextFlags.CHARS_DECIMAL
                                            | InputTextFlags.CHARS_SCIENTIFIC
                                            | InputTextFlags.CHARS_HEXADECIMAL))
                            != 0
                    && c >= 0xFF01
                    && c <= 0xFF5E) {
                c = c - 0xFF01 + 0x21;
            }

            final boolean isDigit = c >= '0' && c <= '9';
            final boolean isOperator = c == '-' || c == '+' || c == '*' || c == '/';
            // Allow 0-9 . - + * /
            if ((flags & InputTextFlags.CHARS_DECIMAL) != 0
                    && !isDigit
                    && c != decimalPoint
                    && !isOperator) {
                return -1;
            }
            // Allow 0-9 . - + * / e E
            if ((flags & InputTextFlags.CHARS_SCIENTIFIC) != 0
                    && !isDigit
                    && c != decimalPoint
                    && !isOperator
                    && c != 'e'
                    && c != 'E') {
                return -1;
            }
            // Allow 0-9 a-f A-F
            if ((flags & InputTextFlags.CHARS_HEXADECIMAL) != 0
                    && !isDigit
                    && !(c >= 'a' && c <= 'f')
                    && !(c >= 'A' && c <= 'F')) {
                return -1;
            }
            // Turn a-z into A-Z
            if ((flags & InputTextFlags.CHARS_UPPERCASE) != 0 && c >= 'a' && c <= 'z') {
                c += 'A' - 'a';
            }
            if ((flags & InputTextFlags.CHARS_NO_BLANK) != 0 && InputTextState.isBlank(c)) {
                return -1;
            }
        }

        // The allowed characters of the string
        if (applyNamedFilters && !allowedChars.isEmpty() && allowedChars.indexOf(c) < 0) {
            return -1;
        }

        // Custom callback filter
        if ((flags & InputTextFlags.CALLBACK_CHAR_FILTER) != 0 && callback != null) {
            final GuiInputTextCallbackData callbackData = new GuiInputTextCallbackData();
            callbackData.setId(state.id);
            callbackData.setFlags(flags);
            callbackData.setEventFlag(InputTextFlags.CALLBACK_CHAR_FILTER);
            callbackData.setEventChar(c);
            callbackData.setEventActivated(
                    context.activeID == state.id && context.activeIDIsJustActivated);
            callbackData.setCursorPos(state.cursor);
            callbackData.setSelectionStart(state.selectStart);
            callbackData.setSelectionEnd(state.selectEnd);
            callback.accept(callbackData);
            c = callbackData.getEventChar();
            if (c == 0) {
                return -1;
            }
        }

        return c;
    }

    /**
     * The decimal point character for the platform locale.
     *
     * @return The decimal point.
     */
    private static char getLocaleDecimalPoint() {
        return context.platformIO != null ? context.platformIO.localeDecimalPoint : '.';
    }

    /**
     * Calculate which lines of text are visible within a clip rectangle.
     *
     * @param clipRect The clip rectangle.
     * @param posY The y position of the first line.
     * @param lineHeight The height of each line.
     * @param output Where to store the first visible line and the line after the last visible one.
     */
    private static void calcClipRectVisibleItemsY(
            @NonNull RectFloat clipRect, float posY, float lineHeight, int @NonNull [] output) {
        // Overestimate
        output[0] = Math.max((int) ((clipRect.getTop() - posY) / lineHeight), 0);
        output[1] =
                Math.max((int) Math.ceil((clipRect.getBottom() - posY) / lineHeight), output[0]);
    }

    /**
     * Measure the width of part of a single line of text.
     *
     * @param text The text.
     * @param start The start index.
     * @param end The end index.
     * @return The width in pixels.
     */
    private static float measure(@NonNull String text, int start, int end) {
        if (end <= start) {
            return 0.0f;
        }
        final float[] offsets = DrawList.calcTextOffsets(context.fontSize, text, start, end);
        return offsets[offsets.length - 1];
    }

    /**
     * Edit a string of text. While the input is active, it holds a private copy of the text and
     * applies it back to the string, so changing the string while the input is active has no effect
     * (see InputTextState.reloadUserBuf*() for that).
     *
     * @param label The label, which is also used for the ID.
     * @param hint The text to display when the input is empty, may be null.
     * @param buf The text to edit.
     * @param sizeX The width, 0 for the default item width, negative to align to the right.
     * @param sizeY The height, 0 for the default, negative to align to the bottom.
     * @param flags Input text flags.
     * @param callback The callback for any InputTextFlags.CALLBACK_* flags, may be null.
     * @return True if the text was edited (or validated with ENTER_RETURNS_TRUE).
     * @see InputTextFlags
     */
    static boolean inputTextEx(
            String label,
            String hint,
            @NonNull IkString buf,
            float sizeX,
            float sizeY,
            int flags,
            GuiInputTextCallback callback) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (label == null) {
            label = "";
        }

        if ((flags & InputTextFlags.CALLBACK_HISTORY) != 0
                && (flags & InputTextFlags.INTERNAL_MULTILINE) != 0) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Can't use CALLBACK_HISTORY with multi-line text, they both use up/down keys");
            flags &= ~InputTextFlags.CALLBACK_HISTORY;
        }
        if ((flags & InputTextFlags.CALLBACK_COMPLETION) != 0
                && (flags & InputTextFlags.ALLOW_TAB_INPUT) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Can't use CALLBACK_COMPLETION with ALLOW_TAB_INPUT, they both use tab");
            flags &= ~InputTextFlags.CALLBACK_COMPLETION;
        }
        if ((flags & InputTextFlags.ELIDE_LEFT) != 0
                && (flags & InputTextFlags.INTERNAL_MULTILINE) != 0) {
            IkGuiImplDebugTools.reportError(log, "Multi-line text does not work with ELIDE_LEFT");
            flags &= ~InputTextFlags.ELIDE_LEFT;
        }
        if ((flags & InputTextFlags.WORD_WRAP) != 0
                && ((flags & InputTextFlags.PASSWORD) != 0
                        || (flags & InputTextFlags.INTERNAL_MULTILINE) == 0)) {
            IkGuiImplDebugTools.reportError(
                    log, "WORD_WRAP only works with multi-line text, and not with PASSWORD");
            flags &= ~InputTextFlags.WORD_WRAP;
        }

        final IkIO io = context.io;
        final StyleVariables style = context.style.variable;
        // The height of a line in pixels, unlike context.fontSize which is in points
        final float fontSize = IkGuiImplLayout.getTextLineHeight();

        final boolean isMultiline = (flags & InputTextFlags.INTERNAL_MULTILINE) != 0;

        // Open the group before calling getID() because groups track IDs created within their
        // scope (including the scrollbar)
        if (isMultiline) {
            IkGuiImplLayout.beginGroup();
        }
        final int id = window.getID(label);
        final String labelDisplayed = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, labelDisplayed, false, -1.0f);
        // Arbitrary default of 8 lines high for multi-line
        final Vector2f frameSize =
                IkGuiInternal.calcItemSize(
                        new Vector2f(sizeX, sizeY),
                        IkGuiImplUtils.calculateItemWidth(),
                        (isMultiline ? fontSize * 8.0f : labelSize.y)
                                + style.framePadding.y * 2.0f);
        final float totalSizeX =
                frameSize.x + (labelSize.x > 0.0f ? style.itemInnerSpacing.x + labelSize.x : 0.0f);

        final float startX = window.cursorPosition.x;
        final float startY = window.cursorPosition.y;
        final RectFloat frameBB =
                new RectFloat(startX, startY, startX + frameSize.x, startY + frameSize.y);
        final RectFloat totalBB =
                new RectFloat(startX, startY, startX + totalSizeX, startY + frameSize.y);

        Window drawWindow = window;
        final Vector2f innerSize = new Vector2f(frameSize);
        final LastItemData itemDataBackup = new LastItemData();
        if (isMultiline) {
            final Vector2f backupPos = new Vector2f(window.cursorPosition);
            IkGuiInternal.itemSize(totalBB, style.framePadding.y);
            // Mimic some of the itemAdd() logic, and add the deactivated state check
            final boolean noClip =
                    context.inputTextDeactivatedState.id == id
                            || context.activeID == id
                            || context.navActivateID == id;
            if (!IkGuiInternal.itemAdd(totalBB, id, frameBB, ItemFlags.INTERNAL_INPUTABLE)
                    && !noClip) {
                IkGuiImplLayout.endGroup();
                return false;
            }
            itemDataBackup.set(context.lastItemData);
            window.cursorPosition.set(backupPos);

            // Prevent navigation activation from explicit tabbing when our widget accepts tab
            // inputs, this allows cycling through widgets without stopping
            if (context.navActivateID == id
                    && (context.navActivateFlags & ActivateFlags.FROM_TABBING) != 0
                    && (context.navActivateFlags & ActivateFlags.FROM_FOCUS_API) == 0
                    && (flags & InputTextFlags.ALLOW_TAB_INPUT) != 0) {
                context.navActivateID = 0;
            }

            // Prevent navigation activation from reactivating in beginChild() when we are
            // already active
            final int backupActivateID = context.navActivateID;
            if (context.activeID == id) {
                context.navActivateID = 0;
            }

            // We reproduce the contents of beginChild() with the frame style in order to provide
            // the label, so our window internal data is easier to read/debug
            IkGuiImplUtils.pushStyleColor(
                    ColorType.CHILD_BACKGROUND,
                    IkGuiImplUtils.getColor(ColorType.FRAME_BACKGROUND));
            IkGuiImplUtils.pushStyleVarFloat(StyleVariable.CHILD_ROUNDING, style.frameRounding);
            IkGuiImplUtils.pushStyleVarFloat(
                    StyleVariable.CHILD_BORDER_SIZE, style.frameBorderSize);
            // Ensure there is no clip rect so mouse hover can reach the frame padding edges
            IkGuiImplUtils.pushStyleVarFloat2(StyleVariable.WINDOW_PADDING, 0.0f, 0.0f);
            final boolean childVisible =
                    IkGuiImplWindows.beginChild(
                            label,
                            id,
                            frameSize.x,
                            frameSize.y,
                            ChildFlags.BORDERS,
                            WindowFlags.NO_MOVE);
            context.navActivateID = backupActivateID;
            IkGuiImplUtils.popStyleVar(3);
            IkGuiImplUtils.popStyleColor();
            if (!childVisible && !noClip) {
                IkGuiImplWindows.endChild();
                IkGuiImplLayout.endGroup();
                return false;
            }
            // The child window
            drawWindow = context.windowCurrent;
            drawWindow.cursorPosition.add(style.framePadding);
            innerSize.x -= drawWindow.scrollbarSizes.x;

            context.lastItemData.id = id;
            context.lastItemData.itemFlags = itemDataBackup.itemFlags;
            context.lastItemData.statusFlags = itemDataBackup.statusFlags;
        } else {
            IkGuiInternal.itemSize(totalBB, style.framePadding.y);
            if ((flags & InputTextFlags.INTERNAL_TEMP_INPUT) == 0
                    && !IkGuiInternal.itemAdd(totalBB, id, frameBB, ItemFlags.INTERNAL_INPUTABLE)) {
                return false;
            }
        }

        // Ensure the mouse cursor is set even after switching to keyboard/gamepad mode
        boolean hovered =
                IkGuiInternal.itemHoverable(
                        frameBB,
                        id,
                        context.lastItemData.itemFlags
                                | ItemFlags.INTERNAL_NO_NAV_DISABLE_MOUSE_HOVER);
        if (hovered) {
            IkGuiImplUtils.setMouseCursor(MouseCursor.TEXT_INPUT);
        }
        if (hovered && context.navHighlightItemUnderNav) {
            hovered = false;
        }

        // We are only allowed to access the state if we are the widget it belongs to
        InputTextState state = getInputTextState(id);

        if ((context.lastItemData.itemFlags & ItemFlags.READ_ONLY) != 0) {
            flags |= InputTextFlags.READ_ONLY;
        }
        final boolean isReadOnly = (flags & InputTextFlags.READ_ONLY) != 0;
        final boolean isPassword = (flags & InputTextFlags.PASSWORD) != 0;
        final boolean isUndoable = (flags & InputTextFlags.NO_UNDO_REDO) == 0;
        final boolean isResizable =
                (flags & InputTextFlags.CALLBACK_RESIZE) != 0 || buf.inputData.isResizable;
        final boolean isMixed = (context.lastItemData.itemFlags & ItemFlags.MIXED_VALUE) != 0;

        // Word wrapping: enforcing a fixed width not altered by the vertical scrollbar makes
        // things easier, so we account for the scrollbar space if it is not visible
        final boolean isWordWrap = (flags & InputTextFlags.WORD_WRAP) != 0;
        float wrapWidth = 0.0f;
        if (isWordWrap) {
            wrapWidth =
                    Math.max(
                            1.0f,
                            IkGuiImplUtils.getContentRegionAvailableX()
                                    + (drawWindow.scrollbarY ? 0.0f : -style.scrollbarSize));
        }

        final int leftIndex = MouseButton.LEFT.index;
        final boolean userClicked = hovered && io.mouseClicked[leftIndex];
        final boolean inputRequestedByNav = context.activeID != id && context.navActivateID == id;
        final boolean inputRequestedByReactivate = context.inputTextReactivateID == id;
        final boolean inputRequestedByUser =
                userClicked
                        || (context.activeID == 0
                                && (flags & InputTextFlags.INTERNAL_TEMP_INPUT) != 0
                                && context.inputTextDeactivatedState.id != id);
        final int scrollbarID = isMultiline && state != null ? drawWindow.getID("#SCROLLY") : 0;
        final boolean userScrollFinish =
                isMultiline
                        && state != null
                        && context.activeID == 0
                        && context.activeIDPreviousFrame == scrollbarID;
        final boolean userScrollActive =
                isMultiline && state != null && context.activeID == scrollbarID;
        boolean clearActiveID = false;
        boolean selectAll = false;

        float scrollY = isMultiline ? drawWindow.scrollPosition.y : Float.MAX_VALUE;

        final String userText = buf.get();
        final boolean initReloadFromUserBuf = state != null && state.wantReloadUserBuf;
        final boolean initChangedSpecsMultiline = state != null && state.singleLine != !isMultiline;
        final boolean initChangedSpecsReadOnly =
                state != null && ((state.flags ^ flags) & InputTextFlags.READ_ONLY) != 0;
        final boolean initMakeActive =
                inputRequestedByUser
                        || inputRequestedByNav
                        || inputRequestedByReactivate
                        || userScrollFinish;
        if (initReloadFromUserBuf) {
            state.wantReloadUserBuf = false;
            state.reconcileUndoState(state.text, userText);
            state.setText(userText);
            // Will be clamped to bounds below
            state.selectStart = state.reloadSelectionStart;
            state.cursor = state.selectEnd = state.reloadSelectionEnd;
        } else if ((initMakeActive && context.activeID != id)
                || initChangedSpecsMultiline
                || initChangedSpecsReadOnly) {
            // Access the state even if we don't own it yet
            state = context.inputTextState;
            state.cursorAnimReset();

            // Back up the state of the deactivating item so they'll have a chance to write to
            // their string on the same frame they report isItemDeactivatedAfterEdit()
            if (state.id != id
                    && state.id == context.activeID
                    && initMakeActive
                    && context.activeID != id) {
                inputTextDeactivateHook(state.id);
            }

            // Take a copy of the initial value. From the moment we are focused we normally ignore
            // the contents of the string (unless we are in read-only mode).
            if (!userScrollFinish) {
                state.textToRevertTo = userText;
            }

            // Preserve the cursor position and undo/redo stack if we come back to the same widget
            boolean recycleState = state.id == id && !initChangedSpecsMultiline;
            if (recycleState
                    && !initChangedSpecsReadOnly
                    && !state.text.toString().equals(userText)) {
                recycleState = false;
            }

            // Start editing
            state.id = id;
            state.editedBefore = false;
            state.setText(userText);

            // Find the initial scroll position for right alignment
            state.scroll.set(0.0f, 0.0f);
            if ((flags & InputTextFlags.ELIDE_LEFT) != 0) {
                state.scroll.x +=
                        Math.max(
                                0.0f,
                                measure(userText, 0, userText.length())
                                        - frameSize.x
                                        + style.framePadding.x * 2.0f);
            }

            // Recycle the existing cursor/selection/undo stack but clamp the position. A single
            // mouse click will override the cursor position immediately.
            if (!recycleState) {
                state.initialize(!isMultiline);
            }

            if (!isMultiline) {
                if ((flags & InputTextFlags.AUTO_SELECT_ALL) != 0 || isMixed) {
                    selectAll = true;
                }
                if (inputRequestedByNav
                        && (!recycleState
                                || (context.navActivateFlags & ActivateFlags.TRY_TO_PRESERVE_STATE)
                                        == 0)) {
                    selectAll = true;
                }
                if (userClicked && io.keyCtrl) {
                    selectAll = true;
                }
            }

            if ((flags & InputTextFlags.ALWAYS_OVERWRITE) != 0) {
                state.insertMode = true;
            }
        }

        final boolean isOSX = io.configMacOSXBehaviors;
        if (initMakeActive && context.activeID != id) {
            IkGuiInternal.setActiveID(id, window);
            IkGuiImplNav.setFocusID(id, window);
            IkGuiInternal.focusWindow(window, WindowFocusRequestFlags.NONE);
            if (inputRequestedByNav) {
                IkGuiImplNav.setNavCursorVisibleAfterMove();
            }
        }
        if (context.activeID == id && state != null) {
            // Declare some inputs, the others are registered and polled via shortcut() routing
            for (Key key :
                    new Key[] {
                        Key.ARROW_LEFT,
                        Key.ARROW_RIGHT,
                        Key.DELETE,
                        Key.BACKSPACE,
                        Key.HOME,
                        Key.END
                    }) {
                IkGuiImplKeys.setKeyOwner(key, id, 0);
            }
            if (userClicked) {
                IkGuiImplKeys.setKeyOwner(Key.MOUSE_LEFT, id, 0);
            }
            context.activeIDUsingNavDirMask |=
                    (1 << Direction.LEFT.ordinal()) | (1 << Direction.RIGHT.ordinal());
            if (isMultiline || (flags & InputTextFlags.CALLBACK_HISTORY) != 0) {
                context.activeIDUsingNavDirMask |=
                        (1 << Direction.UP.ordinal()) | (1 << Direction.DOWN.ordinal());
                IkGuiImplKeys.setKeyOwner(Key.ARROW_UP, id, 0);
                IkGuiImplKeys.setKeyOwner(Key.ARROW_DOWN, id, 0);
            }
            if (isMultiline) {
                IkGuiImplKeys.setKeyOwner(Key.PAGE_UP, id, 0);
                IkGuiImplKeys.setKeyOwner(Key.PAGE_DOWN, id, 0);
            }
            // May be a problem to always steal Alt on OS X, would ideally still allow an
            // uninterrupted Alt down-up to toggle the menu
            if (isOSX) {
                IkGuiImplKeys.setKeyOwner(Key.MOD_ALT, id, 0);
            }

            // Expose scroll in a manner that is agnostic to us using a child window
            if (isMultiline) {
                state.scroll.y = drawWindow.scrollPosition.y;
            }

            // Read-only mode always reads from the user string. Refresh the copy when active.
            if (isReadOnly && !state.text.toString().equals(userText)) {
                state.setText(userText);
            }
            state.cursorClamp();
        }

        // We have an edge case if the active ID was set through another widget (e.g. widget
        // being swapped), clear the ID immediately
        if (context.activeID == id && state == null) {
            IkGuiInternal.clearActiveID();
        }

        // Release focus when we click outside
        if (context.activeID == id && io.mouseClicked[leftIndex] && !initMakeActive) {
            clearActiveID = true;
        }

        // Lock the decision of whether we are going to take the path displaying the cursor or
        // selection
        boolean renderCursor = context.activeID == id || (state != null && userScrollActive);
        boolean renderSelection =
                state != null && (state.hasSelection() || selectAll) && renderCursor;
        boolean valueChanged = false;
        boolean validated = false;

        // Select the text to render
        final boolean bufDisplayFromState =
                (renderCursor || renderSelection || context.activeID == id)
                        && !isReadOnly
                        && state != null;

        if (state != null && state.id == id) {
            state.flags = flags;
            state.fontSize = context.fontSize;
            state.macOSXBehaviors = isOSX;
            state.editedThisFrame = state.validatedThisFrame = false;

            // Word wrapping: attempt to keep the cursor in view while resizing the frame/parent
            if (isWordWrap && state.wrapWidth != wrapWidth) {
                state.cursorCenterY = true;
                state.wrapWidth = wrapWidth;
                renderCursor = true;
            }
        }

        // Process mouse inputs and character inputs
        if (context.activeID == id && state != null) {
            state.bufCapacity = buf.getBufferSize();
            state.resizable = isResizable;
            state.wrapWidth = wrapWidth;

            // Although we are active we don't prevent the mouse from hovering other elements
            // unless we are interacting right now with the widget
            context.activeIDAllowOverlap = !io.mouseDown[leftIndex];

            // Edit in progress
            final float mouseX =
                    io.mousePosition.x - frameBB.getLeft() - style.framePadding.x + state.scroll.x;
            final float mouseY =
                    isMultiline
                            ? io.mousePosition.y - drawWindow.cursorPosition.y
                            : fontSize * 0.5f;

            if (selectAll) {
                state.selectAll();
                state.selectedAllMouseLock = true;
            } else if (hovered && io.mouseClickedCount[leftIndex] >= 2 && !io.keyShift) {
                state.click(mouseX, mouseY);
                final int multiClickCount = io.mouseClickedCount[leftIndex] - 2;
                if (multiClickCount % 2 == 0) {
                    // Double click: select a word. We always use the "Mac" word advance for
                    // double click select.
                    final boolean isBeginningOfLine =
                            state.cursor == 0 || state.getChar(state.cursor - 1) == '\n';
                    if (state.hasSelection() || !isBeginningOfLine) {
                        state.onKeyPressed(InputTextState.K_WORD_LEFT);
                    }
                    if (!state.hasSelection()) {
                        state.prepSelectionAtCursor();
                    }
                    state.cursor = state.moveWordRightMac(state.cursor);
                    state.selectEnd = state.cursor;
                    state.clamp();
                } else {
                    // Triple click: select the line
                    final boolean isEndOfLine = state.getChar(state.cursor) == '\n';
                    // Temporarily disable wrapping so we use the real line start
                    state.wrapWidth = 0.0f;
                    state.onKeyPressed(InputTextState.K_LINE_START);
                    state.onKeyPressed(InputTextState.K_LINE_END | InputTextState.K_SHIFT);
                    state.onKeyPressed(InputTextState.K_RIGHT | InputTextState.K_SHIFT);
                    state.wrapWidth = wrapWidth;
                    if (!isEndOfLine && isMultiline) {
                        final int temp = state.selectStart;
                        state.selectStart = state.selectEnd;
                        state.selectEnd = temp;
                        state.cursor = state.selectEnd;
                    }
                    state.cursorFollow = false;
                }
                state.cursorAnimReset();
            } else if (io.mouseClicked[leftIndex] && !state.selectedAllMouseLock) {
                if (hovered) {
                    if (io.keyShift) {
                        state.drag(mouseX, mouseY);
                    } else {
                        state.click(mouseX, mouseY);
                    }
                    state.cursorAnimReset();
                }
            } else if (io.mouseDown[leftIndex]
                    && !state.selectedAllMouseLock
                    && (io.mouseDelta.x != 0.0f || io.mouseDelta.y != 0.0f)) {
                state.drag(mouseX, mouseY);
                state.cursorAnimReset();
                state.cursorFollow = true;
            }
            if (state.selectedAllMouseLock && !io.mouseDown[leftIndex]) {
                state.selectedAllMouseLock = false;
            }

            // We expect backends to emit a Tab key but some also emit a Tab character which we
            // ignore
            if ((flags & InputTextFlags.ALLOW_TAB_INPUT) != 0
                    && !isReadOnly
                    && shortcut(id, KeyModFlags.NONE, Key.TAB, true)) {
                final int c =
                        filterCharacter(state, '\t', callback, buf.inputData.allowedChars, false);
                if (c >= 0) {
                    state.onCharPressed(c);
                }
            }

            // Process regular text input (before we check for Return because using some IME
            // will effectively send a Return). We ignore Ctrl inputs, but need to allow Alt+Ctrl
            // as some keyboards (e.g. German) use AltGr (which is Alt+Ctrl) to input certain
            // characters.
            final boolean ignoreCharInputs = (io.keyCtrl && !io.keyAlt) || (isOSX && io.keyCtrl);
            if (!io.inputQueueCharacters.isEmpty()) {
                if (!ignoreCharInputs && !isReadOnly && !inputRequestedByNav) {
                    io.inputQueueCharacters
                            .codePoints()
                            .forEach(
                                    codePoint -> {
                                        // Skip tab, see above
                                        if (codePoint == '\t') {
                                            return;
                                        }
                                        final int c =
                                                filterCharacter(
                                                        context.inputTextState,
                                                        codePoint,
                                                        callback,
                                                        buf.inputData.allowedChars,
                                                        false);
                                        if (c >= 0) {
                                            context.inputTextState.onCharPressed(c);
                                        }
                                    });
                }
                // Consume characters
                io.inputQueueCharacters.setLength(0);
            }
        }

        // Process other shortcuts/key presses
        boolean revertEdit = false;
        if (context.activeID == id
                && state != null
                && !context.activeIDIsJustActivated
                && !clearActiveID) {
            final int rowCountPerPage =
                    Math.max((int) ((innerSize.y - style.framePadding.y) / fontSize), 1);
            state.rowCountPerPage = rowCountPerPage;

            final int kMask = io.keyShift ? InputTextState.K_SHIFT : 0;
            // OS X style: text editing cursor movement using Alt instead of Ctrl
            final boolean isWordMoveKeyDown = isOSX ? io.keyAlt : io.keyCtrl;
            // OS X style: line/text start and end using Cmd+Arrows instead of Home/End
            final boolean isStartEndKeyDown = isOSX && io.keyCtrl && !io.keySuper && !io.keyAlt;

            final int ctrl = KeyModFlags.CTRL;
            final int shift = KeyModFlags.SHIFT;
            final int none = KeyModFlags.NONE;
            final boolean isCut =
                    (shortcut(id, ctrl, Key.X, true) || shortcut(id, shift, Key.DELETE, true))
                            && !isReadOnly
                            && !isPassword
                            && (!isMultiline || state.hasSelection());
            final boolean isCopy =
                    (shortcut(id, ctrl, Key.C, false) || shortcut(id, ctrl, Key.INSERT, false))
                            && !isPassword
                            && (!isMultiline || state.hasSelection());
            final boolean isPaste =
                    (shortcut(id, ctrl, Key.V, true) || shortcut(id, shift, Key.INSERT, true))
                            && !isReadOnly;
            final boolean isUndo = shortcut(id, ctrl, Key.Z, true) && !isReadOnly && isUndoable;
            final boolean isRedo =
                    (shortcut(id, ctrl, Key.Y, true) || shortcut(id, ctrl | shift, Key.Z, true))
                            && !isReadOnly
                            && isUndoable;
            final boolean isSelectAll = shortcut(id, ctrl, Key.A, false);

            // We allow validate/cancel with the gamepad, to make it easier to undo an accidental
            // press with no keyboard wired
            final boolean navGamepadActive =
                    (io.configFlags & ConfigFlags.NAV_ENABLE_GAMEPAD) != 0
                            && (io.backendFlags & BackendFlags.HAS_GAMEPAD) != 0;
            final boolean isGamepadValidate =
                    navGamepadActive
                            && IkGuiImplKeys.isKeyPressed(
                                    IkGuiImplNav.navGamepadActivateKey(), InputFlags.NONE, id);
            final boolean isEnter =
                    shortcut(id, none, Key.ENTER, true)
                            || shortcut(id, none, Key.NUMPAD_ENTER, true);
            final boolean isCtrlEnter =
                    shortcut(id, ctrl, Key.ENTER, true)
                            || shortcut(id, ctrl, Key.NUMPAD_ENTER, true);
            final boolean isShiftEnter =
                    shortcut(id, shift, Key.ENTER, true)
                            || shortcut(id, shift, Key.NUMPAD_ENTER, true);
            final boolean isCancel =
                    shortcut(id, none, Key.ESCAPE, true)
                            || (navGamepadActive
                                    && shortcut(
                                            id, none, IkGuiImplNav.navGamepadCancelKey(), true));

            if (IkGuiImplKeys.isKeyPressed(Key.ARROW_LEFT, InputFlags.REPEAT, id)) {
                state.onKeyPressed(
                        (isStartEndKeyDown
                                        ? InputTextState.K_LINE_START
                                        : isWordMoveKeyDown
                                                ? InputTextState.K_WORD_LEFT
                                                : InputTextState.K_LEFT)
                                | kMask);
            } else if (IkGuiImplKeys.isKeyPressed(Key.ARROW_RIGHT, InputFlags.REPEAT, id)) {
                state.onKeyPressed(
                        (isStartEndKeyDown
                                        ? InputTextState.K_LINE_END
                                        : isWordMoveKeyDown
                                                ? InputTextState.K_WORD_RIGHT
                                                : InputTextState.K_RIGHT)
                                | kMask);
            } else if (IkGuiImplKeys.isKeyPressed(Key.ARROW_UP, InputFlags.REPEAT, id)
                    && isMultiline) {
                if (io.keyCtrl) {
                    IkGuiInternal.setScrollY(
                            drawWindow, Math.max(drawWindow.scrollPosition.y - fontSize, 0.0f));
                } else {
                    state.onKeyPressed(
                            (isStartEndKeyDown ? InputTextState.K_TEXT_START : InputTextState.K_UP)
                                    | kMask);
                }
            } else if (IkGuiImplKeys.isKeyPressed(Key.ARROW_DOWN, InputFlags.REPEAT, id)
                    && isMultiline) {
                if (io.keyCtrl) {
                    IkGuiInternal.setScrollY(
                            drawWindow,
                            Math.min(
                                    drawWindow.scrollPosition.y + fontSize,
                                    IkGuiImplUtils.getScrollMaxY()));
                } else {
                    state.onKeyPressed(
                            (isStartEndKeyDown ? InputTextState.K_TEXT_END : InputTextState.K_DOWN)
                                    | kMask);
                }
            } else if (IkGuiImplKeys.isKeyPressed(Key.PAGE_UP, InputFlags.REPEAT, id)
                    && isMultiline) {
                state.onKeyPressed(InputTextState.K_PAGE_UP | kMask);
                scrollY -= rowCountPerPage * fontSize;
            } else if (IkGuiImplKeys.isKeyPressed(Key.PAGE_DOWN, InputFlags.REPEAT, id)
                    && isMultiline) {
                state.onKeyPressed(InputTextState.K_PAGE_DOWN | kMask);
                scrollY += rowCountPerPage * fontSize;
            } else if (IkGuiImplKeys.isKeyPressed(Key.HOME, InputFlags.REPEAT, id)) {
                state.onKeyPressed(
                        (io.keyCtrl ? InputTextState.K_TEXT_START : InputTextState.K_LINE_START)
                                | kMask);
            } else if (IkGuiImplKeys.isKeyPressed(Key.END, InputFlags.REPEAT, id)) {
                state.onKeyPressed(
                        (io.keyCtrl ? InputTextState.K_TEXT_END : InputTextState.K_LINE_END)
                                | kMask);
            } else if (IkGuiImplKeys.isKeyPressed(Key.DELETE, InputFlags.REPEAT, id)
                    && !isReadOnly
                    && !isCut) {
                // OS X doesn't seem to have Super+Delete to delete until end of line, so we don't
                // emulate that (as opposed to Super+Backspace)
                if (!state.hasSelection() && isWordMoveKeyDown) {
                    state.onKeyPressed(InputTextState.K_WORD_RIGHT | InputTextState.K_SHIFT);
                }
                state.onKeyPressed(InputTextState.K_DELETE | kMask);
            } else if (IkGuiImplKeys.isKeyPressed(Key.BACKSPACE, InputFlags.REPEAT, id)
                    && !isReadOnly) {
                if (!state.hasSelection()) {
                    if (isWordMoveKeyDown) {
                        state.onKeyPressed(InputTextState.K_WORD_LEFT | InputTextState.K_SHIFT);
                    } else if (isOSX && io.keyCtrl && !io.keyAlt && !io.keySuper) {
                        state.onKeyPressed(InputTextState.K_LINE_START | InputTextState.K_SHIFT);
                    }
                }
                state.onKeyPressed(InputTextState.K_BACKSPACE | kMask);
            } else if (isEnter || isCtrlEnter || isShiftEnter || isGamepadValidate) {
                // Determine if we turn Enter into a newline character
                final boolean ctrlEnterForNewLine =
                        (flags & InputTextFlags.CTRL_ENTER_FOR_NEW_LINE) != 0;
                final boolean isNewLine =
                        isMultiline
                                && !isGamepadValidate
                                && (isShiftEnter
                                        || (isEnter && !ctrlEnterForNewLine)
                                        || (isCtrlEnter && ctrlEnterForNewLine));
                if (!isNewLine) {
                    validated = clearActiveID = true;
                    if (io.configInputTextEnterKeepActive
                            && !isMultiline
                            && !isCtrlEnter
                            && !isShiftEnter) {
                        // Queue reactivation, so that e.g. isItemDeactivatedAfterEdit() works
                        state.selectAll();
                        context.inputTextReactivateID = id;
                    }
                } else if (!isReadOnly) {
                    // Insert a newline
                    final int c =
                            filterCharacter(
                                    state, '\n', callback, buf.inputData.allowedChars, false);
                    if (c >= 0) {
                        state.onCharPressed(c);
                    }
                }
            } else if (isCancel) {
                if ((flags & InputTextFlags.ESCAPE_CLEARS_ALL) != 0) {
                    if (state.getTextLength() > 0) {
                        revertEdit = true;
                    } else {
                        renderCursor = renderSelection = false;
                        clearActiveID = true;
                    }
                } else {
                    clearActiveID = revertEdit = true;
                    renderCursor = renderSelection = false;
                }
            } else if (isUndo || isRedo) {
                state.onKeyPressed(isUndo ? InputTextState.K_UNDO : InputTextState.K_REDO);
                state.clearSelection();
            } else if (isSelectAll) {
                state.selectAll();
                state.cursorFollow = true;
            } else if (isCut || isCopy) {
                // Cut, copy
                final int start =
                        state.hasSelection() ? Math.min(state.selectStart, state.selectEnd) : 0;
                final int end =
                        state.hasSelection()
                                ? Math.max(state.selectStart, state.selectEnd)
                                : state.getTextLength();
                IkGuiImplUtils.setClipboardText(state.text.substring(start, end));
                if (isCut) {
                    if (!state.hasSelection()) {
                        state.selectAll();
                    }
                    state.cursorFollow = true;
                    state.cut();
                }
            } else if (isPaste) {
                final String clipboard = IkGuiImplUtils.getClipboardText();
                if (clipboard != null && !clipboard.isEmpty()) {
                    // Filter the pasted text
                    final StringBuilder filtered = new StringBuilder(clipboard.length());
                    final InputTextState pasteState = state;
                    clipboard
                            .codePoints()
                            .forEach(
                                    codePoint -> {
                                        final int c =
                                                filterCharacter(
                                                        pasteState,
                                                        codePoint,
                                                        callback,
                                                        buf.inputData.allowedChars,
                                                        true);
                                        if (c >= 0) {
                                            filtered.appendCodePoint(c);
                                        }
                                    });
                    // If everything was filtered, ignore the pasting operation
                    if (!filtered.isEmpty()) {
                        state.paste(filtered);
                        state.cursorFollow = true;
                    }
                }
            }

            // Update the render selection flag after events have been handled, so the selection
            // highlight can be displayed during the same frame
            renderSelection |= state.hasSelection() && renderCursor;
        }

        // Process revert and user callbacks
        String applyNewText = null;
        if (context.activeID == id && state != null) {
            if (revertEdit && !isReadOnly) {
                if ((flags & InputTextFlags.ESCAPE_CLEARS_ALL) != 0) {
                    // Clear input
                    applyNewText = "";
                    valueChanged = true;
                    state.replace("");
                } else if (!state.text.toString().equals(state.textToRevertTo)) {
                    // Restore the initial value. Push records onto the undo stack so we can undo
                    // the revert itself.
                    applyNewText = state.textToRevertTo;
                    valueChanged = true;
                    state.replace(state.textToRevertTo);
                }
            }

            // User callback
            final int callbackFlags =
                    InputTextFlags.CALLBACK_COMPLETION
                            | InputTextFlags.CALLBACK_HISTORY
                            | InputTextFlags.CALLBACK_EDIT
                            | InputTextFlags.CALLBACK_ALWAYS;
            if ((flags & callbackFlags) != 0 && callback != null) {
                // The reason we specify the usage semantic (completion/history) is that
                // completion needs to disable keyboard tabbing
                int eventFlag = 0;
                Key eventKey = Key.NONE;
                if ((flags & InputTextFlags.CALLBACK_COMPLETION) != 0
                        && shortcut(id, KeyModFlags.NONE, Key.TAB, false)) {
                    eventFlag = InputTextFlags.CALLBACK_COMPLETION;
                    eventKey = Key.TAB;
                } else if ((flags & InputTextFlags.CALLBACK_HISTORY) != 0
                        && IkGuiImplKeys.isKeyPressed(Key.ARROW_UP, InputFlags.REPEAT, id)) {
                    eventFlag = InputTextFlags.CALLBACK_HISTORY;
                    eventKey = Key.ARROW_UP;
                } else if ((flags & InputTextFlags.CALLBACK_HISTORY) != 0
                        && IkGuiImplKeys.isKeyPressed(Key.ARROW_DOWN, InputFlags.REPEAT, id)) {
                    eventFlag = InputTextFlags.CALLBACK_HISTORY;
                    eventKey = Key.ARROW_DOWN;
                } else if ((flags & InputTextFlags.CALLBACK_EDIT) != 0 && state.editedThisFrame) {
                    eventFlag = InputTextFlags.CALLBACK_EDIT;
                } else if ((flags & InputTextFlags.CALLBACK_ALWAYS) != 0) {
                    eventFlag = InputTextFlags.CALLBACK_ALWAYS;
                }

                if (eventFlag != 0) {
                    // Undo stack reconciling needs a backup of the text
                    state.callbackTextBackup = state.getText();

                    final GuiInputTextCallbackData callbackData = new GuiInputTextCallbackData();
                    callbackData.setId(id);
                    callbackData.setFlags(flags);
                    callbackData.setEventFlag(eventFlag);
                    callbackData.setEventActivated(
                            context.activeID == state.id && context.activeIDIsJustActivated);
                    callbackData.setEventKey(eventKey);
                    callbackData.setBuffer(state.text);
                    callbackData.setBufSize(state.bufCapacity);
                    callbackData.setResizable(isResizable);
                    callbackData.setBufDirty(false);
                    callbackData.setCursorPos(state.cursor);
                    callbackData.setSelectionStart(state.selectStart);
                    callbackData.setSelectionEnd(state.selectEnd);

                    // Call user code
                    callback.accept(callbackData);

                    // Read back what the user may have modified
                    boolean bufDirty = callbackData.isBufDirty();
                    if (callbackData.getBuffer() != state.text) {
                        // The buffer was replaced, rather than modified
                        state.text.setLength(0);
                        state.text.append(callbackData.getBuffer());
                        bufDirty = true;
                    }
                    if (bufDirty || callbackData.getCursorPos() != state.cursor) {
                        state.cursorFollow = true;
                    }
                    final int length = state.getTextLength();
                    state.cursor = MathUtil.clamp(callbackData.getCursorPos(), 0, length);
                    state.selectStart = MathUtil.clamp(callbackData.getSelectionStart(), 0, length);
                    state.selectEnd = MathUtil.clamp(callbackData.getSelectionEnd(), 0, length);
                    if (isResizable) {
                        state.bufCapacity = Math.max(state.bufCapacity, callbackData.getBufSize());
                    }
                    if (bufDirty) {
                        // The callback may update the buffer and thus set bufDirty even in
                        // read-only mode
                        state.textChanged();
                        state.reconcileUndoState(state.callbackTextBackup, state.text);
                        state.editedBefore = state.editedThisFrame = true;
                        state.cursorAnimReset();
                        if (isReadOnly) {
                            buf.set(state.getText(), true);
                        }
                    }
                }
            }

            // Write back the result string if modified
            if (!isReadOnly) {
                final String stateText = state.getText();
                if ((context.lastItemData.itemFlags & ItemFlags.LIVE_EDIT_ON_INPUT_TEXT) != 0) {
                    // Apply when modified
                    if (!stateText.equals(userText)
                            || (isMixed && (state.editedThisFrame || validated))) {
                        applyNewText = stateText;
                        valueChanged = true;
                    }
                } else {
                    // Apply on validation/deactivation, otherwise cancel out previous apply
                    // attempts (e.g. revert)
                    valueChanged =
                            (validated || clearActiveID || revertEdit)
                                    && (!stateText.equals(userText) || (isMixed && validated));
                    applyNewText = valueChanged ? stateText : null;
                }
            }
        }

        // Handle reapplying the final data on deactivation (see inputTextDeactivateHook()). This
        // is used when e.g. losing focus or tabbing out into another input text, which may
        // already be using the state.
        if (context.inputTextDeactivatedState.id == id) {
            if (((context.activeID != id && IkGuiImplUtils.isItemDeactivated())
                            || (context.activeID == id
                                    && (flags & InputTextFlags.INTERNAL_TEMP_INPUT) != 0))
                    && !isReadOnly
                    && !context.inputTextDeactivatedState.text.equals(userText)) {
                applyNewText = context.inputTextDeactivatedState.text;
                valueChanged = true;
            }
            context.inputTextDeactivatedState.id = 0;
        }

        // Write back the result to the user string. This can currently only happen when we are
        // active or just deactivated.
        if (applyNewText != null) {
            if ((flags & InputTextFlags.CALLBACK_RESIZE) != 0
                    && callback != null
                    && applyNewText.length() > buf.getBufferSize()) {
                // Notify the callback that the string is growing
                final GuiInputTextCallbackData callbackData = new GuiInputTextCallbackData();
                callbackData.setId(id);
                callbackData.setFlags(flags);
                callbackData.setEventFlag(InputTextFlags.CALLBACK_RESIZE);
                callbackData.setEventActivated(
                        state != null
                                && context.activeID == state.id
                                && context.activeIDIsJustActivated);
                callbackData.setBuffer(new StringBuilder(applyNewText));
                callbackData.setBufSize(applyNewText.length());
                callbackData.setResizable(true);
                callback.accept(callbackData);
            }
            buf.set(applyNewText, isResizable);
        }

        // Release the active ID at the end of the function (so e.g. pressing Return still does a
        // final application of the value)
        if (context.activeID == id && clearActiveID && state != null) {
            // Data already applied, avoid inputTextDeactivateHook() taking a record now or later
            // if the same ID is activated again without editing
            state.editedBefore = false;
            IkGuiInternal.clearActiveID();
        }

        // Render the frame
        if (!isMultiline) {
            IkGuiImplNav.renderNavCursor(frameBB, id, NavRenderCursorFlags.NONE, -1.0f);
            IkGuiInternal.renderFrame(
                    frameBB.getLeft(),
                    frameBB.getTop(),
                    frameBB.getRight(),
                    frameBB.getBottom(),
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.FRAME_BACKGROUND),
                    true,
                    style.frameRounding);
        }

        final Vector2f drawPos =
                isMultiline
                        ? new Vector2f(drawWindow.cursorPosition)
                        : new Vector2f(
                                frameBB.getLeft() + style.framePadding.x,
                                frameBB.getTop() + style.framePadding.y);
        // Not using the frame max because we have adjusted the size
        final RectFloat clipRect =
                new RectFloat(
                        frameBB.getLeft(),
                        frameBB.getTop(),
                        frameBB.getLeft() + innerSize.x,
                        frameBB.getTop() + innerSize.y);
        if (isMultiline) {
            clipRect.clipWith(drawWindow.rectCurrentClip);
        }

        // Display the hint when the contents are empty. A callback could have modified the
        // underlying string, so we need to check again.
        final String currentText = buf.get();
        final boolean isDisplayingHint =
                hint != null
                        && (bufDisplayFromState
                                ? state.getTextLength() == 0
                                : currentText.isEmpty())
                        && !isMixed;
        final boolean isDisplayingMixed = isMixed && context.activeID != id && applyNewText == null;
        final boolean displayFromState;
        String bufDisplay;
        if (isDisplayingMixed && context.mixedValueLabel != null) {
            bufDisplay = context.mixedValueLabel;
            renderCursor = renderSelection = false;
            displayFromState = false;
        } else if (isDisplayingHint) {
            bufDisplay = hint;
            displayFromState = false;
        } else {
            displayFromState = (renderCursor || renderSelection) && state != null;
            if (displayFromState) {
                // Read-only text is refreshed from the user string when active
                bufDisplay = state.getDisplayText();
            } else {
                bufDisplay = isPassword ? InputTextState.passwordText(currentText) : currentText;
            }
        }

        // Build the row index for easy data access
        final IntArrayList rowStarts = new IntArrayList();
        if (displayFromState) {
            for (int i = 0; i < state.getRowCount(); ++i) {
                rowStarts.addInt(state.getRowStart(i));
            }
        } else {
            InputTextState.buildRowStarts(
                    bufDisplay, isMultiline, wrapWidth, context.fontSize, rowStarts);
        }
        final int lineCount = rowStarts.size();

        // Calculate visibility
        final int[] lineVisible = {0, 1};
        if (isMultiline) {
            calcClipRectVisibleItemsY(clipRect, drawPos.y, fontSize, lineVisible);
        }
        lineVisible[1] = Math.min(lineVisible[1], lineCount);

        // Store the text height (we don't need the width)
        final float textSizeY = lineCount * fontSize;

        // Calculate the blinking cursor position
        final Vector2f cursorOffset = new Vector2f(0.0f, 0.0f);
        if (renderCursor && state != null) {
            if (displayFromState) {
                state.getCursorOffset(state.cursor, cursorOffset);
            } else {
                cursorOffset.set(0.0f, fontSize);
            }
        }
        float drawScrollX = 0.0f;
        final DrawList drawList = drawWindow.drawList;

        // Render the text, with the selection. We currently only render the selection when the
        // widget is active or while scrolling.
        if ((renderCursor || renderSelection) && state != null) {
            state.lineCount = lineCount;

            // Scroll
            float newScrollY = scrollY;
            if (renderCursor && state.cursorFollow) {
                // Horizontal scroll in chunks of quarter width
                if ((flags & InputTextFlags.NO_HORIZONTAL_SCROLL) == 0) {
                    final float scrollIncrementX = innerSize.x * 0.25f;
                    final float visibleWidth = innerSize.x - style.framePadding.x;
                    if (cursorOffset.x < state.scroll.x) {
                        state.scroll.x =
                                IkGuiInternal.truncate(
                                        Math.max(0.0f, cursorOffset.x - scrollIncrementX));
                    } else if (cursorOffset.x - visibleWidth >= state.scroll.x) {
                        state.scroll.x =
                                IkGuiInternal.truncate(
                                        cursorOffset.x - visibleWidth + scrollIncrementX);
                    }
                } else {
                    state.scroll.x = 0.0f;
                }

                // Vertical scroll
                if (isMultiline) {
                    // Test if the cursor is vertically visible
                    if (cursorOffset.y - fontSize < scrollY) {
                        newScrollY = Math.max(0.0f, cursorOffset.y - fontSize);
                    } else if (cursorOffset.y - (innerSize.y - style.framePadding.y * 2.0f)
                            >= scrollY) {
                        newScrollY = cursorOffset.y - innerSize.y + style.framePadding.y * 2.0f;
                    }
                }
                state.cursorFollow = false;
            }
            if (state.cursorCenterY) {
                if (isMultiline) {
                    newScrollY =
                            cursorOffset.y - fontSize - (innerSize.y * 0.5f - style.framePadding.y);
                }
                state.cursorCenterY = false;
                renderCursor = false;
            }
            if (newScrollY != scrollY) {
                final float scrollMaxY =
                        Math.max((textSizeY + style.framePadding.y * 2.0f) - innerSize.y, 0.0f);
                scrollY = MathUtil.clamp(newScrollY, 0.0f, scrollMaxY);
                // Manipulate the cursor position immediately to avoid a frame of lag
                drawPos.y += drawWindow.scrollPosition.y - scrollY;
                drawWindow.scrollPosition.y = scrollY;
                calcClipRectVisibleItemsY(clipRect, drawPos.y, fontSize, lineVisible);
                lineVisible[1] = Math.min(lineVisible[1], lineCount);
            }

            // Draw the selection
            drawScrollX = state.scroll.x;
            if (renderSelection && displayFromState) {
                final int backgroundColor =
                        IkGuiImplUtils.applyGlobalAlpha(
                                IkGuiImplUtils.getColor(
                                        ColorType.TEXT_SELECTED_BACKGROUND,
                                        renderCursor ? 1.0f : 0.6f),
                                false);
                final float backgroundOffsetUp = isMultiline ? 0.0f : -1.0f;
                final float backgroundOffsetDown = isMultiline ? 0.0f : 2.0f;
                // So we can see selected empty lines
                final float backgroundEndOfLineWidth =
                        IkGuiInternal.truncate(
                                DrawList.calcTextWidth(context.fontSize, " ") * 0.5f);

                final int textSelectedBegin = Math.min(state.selectStart, state.selectEnd);
                final int textSelectedEnd = Math.max(state.selectStart, state.selectEnd);
                final int textLength = bufDisplay.length();
                for (int line = lineVisible[0]; line < lineVisible[1]; ++line) {
                    final int lineStart = state.getRowStart(line);
                    final int lineEnd = state.getRowEnd(line);
                    final int lineContentEnd = state.getRowContentEnd(line);
                    final boolean endIsWrap = lineContentEnd == lineEnd && lineEnd < textLength;
                    final int lineSelectedBegin = Math.max(textSelectedBegin, lineStart);
                    final int lineSelectedEnd = Math.min(textSelectedEnd, lineContentEnd);

                    final float selectionStartX = measure(bufDisplay, lineStart, lineSelectedBegin);
                    float rectWidth = 0.0f;
                    if (lineSelectedBegin < lineSelectedEnd) {
                        rectWidth +=
                                measure(bufDisplay, lineStart, lineSelectedEnd) - selectionStartX;
                    }
                    if (textSelectedBegin <= lineContentEnd
                            && textSelectedEnd > lineContentEnd
                            && !endIsWrap) {
                        rectWidth += backgroundEndOfLineWidth;
                    }
                    if (rectWidth == 0.0f) {
                        continue;
                    }

                    final float minX = drawPos.x - drawScrollX + selectionStartX;
                    final float minY = drawPos.y + line * fontSize;
                    final RectFloat rect =
                            new RectFloat(
                                    minX,
                                    minY + backgroundOffsetUp,
                                    minX + rectWidth,
                                    minY + backgroundOffsetDown + fontSize);
                    rect.clipWith(clipRect);
                    if (rect.getRight() > rect.getLeft() && rect.getBottom() > rect.getTop()) {
                        drawList.addRectFilled(
                                rect.getLeft(),
                                rect.getTop(),
                                rect.getRight(),
                                rect.getBottom(),
                                backgroundColor);
                    }
                }
            }
        }

        // Find the render position for right alignment (single line only)
        if (context.activeID != id
                && (flags & InputTextFlags.ELIDE_LEFT) != 0
                && !renderCursor
                && !renderSelection) {
            drawPos.x =
                    Math.min(
                            drawPos.x,
                            frameBB.getRight()
                                    - measure(bufDisplay, 0, bufDisplay.length())
                                    - style.framePadding.x);
        }

        // Render the text
        if ((isMultiline || bufDisplay.length() < BUF_DISPLAY_MAX_LENGTH)
                && lineVisible[0] < lineVisible[1]) {
            final int textColor =
                    IkGuiImplUtils.getColorWithGlobalAlpha(
                            isDisplayingMixed || isDisplayingHint
                                    ? ColorType.TEXT_DISABLED
                                    : ColorType.TEXT);
            drawList.pushClipRect(
                    clipRect.getLeft(),
                    clipRect.getTop(),
                    clipRect.getRight(),
                    clipRect.getBottom(),
                    true);
            for (int line = lineVisible[0]; line < lineVisible[1]; ++line) {
                final int lineStart = rowStarts.getInt(line);
                int lineEnd =
                        line + 1 < lineCount ? rowStarts.getInt(line + 1) : bufDisplay.length();
                if (lineEnd > lineStart && bufDisplay.charAt(lineEnd - 1) == '\n') {
                    --lineEnd;
                }
                if (lineEnd > lineStart) {
                    drawList.addText(
                            context.fontSize,
                            drawPos.x - drawScrollX,
                            drawPos.y + line * fontSize,
                            textColor,
                            bufDisplay.substring(lineStart, lineEnd));
                }
            }
            drawList.popClipRect();
        }

        // Render the blinking cursor
        if (renderCursor && state != null) {
            state.cursorAnim += io.deltaTime / 1000.0f;
            final boolean cursorIsVisible =
                    !io.configInputTextCursorBlink
                            || state.cursorAnim <= 0.0f
                            || state.cursorAnim % 1.20f <= 0.80f;
            final float cursorScreenX =
                    IkGuiInternal.truncate(drawPos.x + cursorOffset.x - drawScrollX);
            final float cursorScreenY = IkGuiInternal.truncate(drawPos.y + cursorOffset.y);
            final RectFloat cursorScreenRect =
                    new RectFloat(
                            cursorScreenX,
                            cursorScreenY - fontSize + 0.5f,
                            cursorScreenX + 1.0f,
                            cursorScreenY - 1.5f);
            if (cursorIsVisible && cursorScreenRect.overlaps(clipRect)) {
                drawList.addLine(
                        cursorScreenRect.getLeft(),
                        cursorScreenRect.getTop(),
                        cursorScreenRect.getLeft(),
                        cursorScreenRect.getBottom(),
                        IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.INPUT_TEXT_CURSOR),
                        style.inputTextCursorSize);
            }
            // Notify the platform of the text input position for IME (-1 x offset so that the
            // IME can cover our cursor). We make sure we don't set this on the deactivating frame.
            if (!isReadOnly && context.activeID == id) {
                final PlatformImeData imeData = context.platformImeData;
                imeData.wantVisible = true;
                imeData.wantTextInput = true;
                imeData.inputPosition.set(cursorScreenX - 1.0f, cursorScreenY - fontSize);
                imeData.inputLineHeight = fontSize;
                imeData.viewportID = window.viewport != null ? window.viewport.id : 0;
            }
        }

        if (isMultiline) {
            // For focus requests to work on our multi-line we need to ensure our child itemAdd()
            // call specifies the inputable flag
            IkGuiImplLayout.dummy(0.0f, textSizeY + style.framePadding.y);
            context.nextItemData.itemFlags |= ItemFlags.INTERNAL_INPUTABLE | ItemFlags.NO_TAB_STOP;
            IkGuiImplWindows.endChild();
            itemDataBackup.statusFlags |=
                    context.lastItemData.statusFlags & ItemStatusFlags.HOVERED_WINDOW;

            // ...and then we need to undo the group overriding the last item data, unless the
            // scrollbar is active
            IkGuiImplLayout.endGroup();
            if (context.lastItemData.id == 0
                    || context.lastItemData.id != drawWindow.getID("#SCROLLY")) {
                context.lastItemData.id = id;
                context.lastItemData.itemFlags = itemDataBackup.itemFlags;
                context.lastItemData.statusFlags = itemDataBackup.statusFlags;
            }
        }

        // Log as text
        if (context.logEnabled && (!isPassword || isDisplayingHint)) {
            IkGuiImplLogging.logSetNextTextDecoration("{", "}");
            IkGuiImplLogging.logRenderedText(drawPos.y, bufDisplay);
        }

        if (labelSize.x > 0.0f) {
            IkGuiInternal.renderText(
                    frameBB.getRight() + style.itemInnerSpacing.x,
                    frameBB.getTop() + style.framePadding.y,
                    labelDisplayed,
                    false);
        }

        if (state != null && validated) {
            state.validatedThisFrame = true;
        }
        if (valueChanged) {
            IkGuiInternal.markItemEdited(id);
        }

        IkGuiInternal.testEngineItemInfo(
                id, label, context.lastItemData.statusFlags | ItemStatusFlags.INPUTABLE);
        if ((flags & InputTextFlags.ENTER_RETURNS_TRUE) != 0) {
            return validated;
        }
        return valueChanged;
    }

    // ---------------------------------------------------------------------------------------
    // Temporary inputs (Ctrl+Click on drags and sliders)
    // ---------------------------------------------------------------------------------------

    /**
     * Whether a temporary text input is active for an item, e.g. when Ctrl+Clicking a slider.
     *
     * @param id The ID of the item.
     * @return True if the item is being edited as text.
     */
    static boolean tempInputIsActive(int id) {
        return (context.tempInputID == id && context.activeID == id)
                || context.inputTextDeactivatedState.id == id;
    }

    /**
     * Create a text input in place of another active widget (e.g. when Ctrl+Clicking on a drag or
     * slider). This must be submitted right after the item it is overlaying.
     *
     * @param bb The bounding box of the item.
     * @param id The ID of the item, which must match the ID of the label.
     * @param label The label.
     * @param buf The text to edit.
     * @param flags Input text flags.
     * @param callback The callback for any InputTextFlags.CALLBACK_* flags, may be null.
     * @return True if the text was edited.
     */
    static boolean tempInputText(
            @NonNull RectFloat bb,
            int id,
            @NonNull String label,
            @NonNull IkString buf,
            int flags,
            GuiInputTextCallback callback) {
        // On the first frame, the temp input ID is 0, then on subsequent frames it becomes the
        // ID. We clear the active ID on the first frame to allow the input text taking it back.
        final Window window = context.windowCurrent;

        final boolean isDeactivated = context.inputTextDeactivatedState.id == id;
        final boolean isActive = context.tempInputID == id;
        if (!isActive && !isDeactivated) {
            IkGuiInternal.clearActiveID();
        }

        final Vector2f backupPos = new Vector2f(window.cursorPosition);
        window.cursorPosition.set(bb.getLeft(), bb.getTop());
        // Using INTERNAL_TEMP_INPUT will skip itemAdd() so we poke here
        context.lastItemData.itemFlags |= ItemFlags.ALLOW_DUPLICATE_ID;
        final boolean valueChanged =
                inputTextEx(
                        label,
                        null,
                        buf,
                        bb.getWidth(),
                        bb.getHeight(),
                        flags | InputTextFlags.INTERNAL_TEMP_INPUT | InputTextFlags.AUTO_SELECT_ALL,
                        callback);
        // Not done by inputTextEx() because of INTERNAL_TEMP_INPUT
        IkGuiInternal.keepAliveID(id);
        if (!isActive && !isDeactivated) {
            // First frame we started displaying the input text, we expect it to take the
            // active ID
            if (context.activeID != id) {
                IkGuiImplDebugTools.reportError(
                        log, "Temporary text input for {} did not become active", label);
            }
            context.tempInputID = context.activeID;
        }
        if (isActive && context.activeID != id) {
            context.tempInputID = 0;
        }
        window.cursorPosition.set(backupPos);
        return valueChanged;
    }

    /**
     * Edit a scalar as text in place of another widget (e.g. when Ctrl+Clicking a drag or slider).
     *
     * <p>Note that drags and sliders only forward the min/max values for clamping if the
     * SliderFlags.CLAMP_ON_INPUT flag is set. This way we allow Ctrl+Click manual input to set a
     * value out of bounds, for maximum flexibility.
     *
     * @param bb The bounding box of the item.
     * @param id The ID of the item.
     * @param label The label.
     * @param dataType The data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @param format The display format.
     * @param clampMin The minimum to clamp to, may be null.
     * @param clampMax The maximum to clamp to, may be null.
     * @return True if the value changed.
     */
    static boolean tempInputScalar(
            @NonNull RectFloat bb,
            int id,
            @NonNull String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            String format,
            Number clampMin,
            Number clampMax) {
        String trimmedFormat = trimFormatDecorations(format == null ? "" : format);
        if (trimmedFormat.isEmpty()) {
            trimmedFormat = dataType.getDefaultFormat();
        }
        final String text =
                IkGuiImplSliders.formatScalar(
                                dataType,
                                IkGuiImplSliders.readNumber(dataType, data, index),
                                trimmedFormat)
                        .trim();

        final int flags =
                InputTextFlags.AUTO_SELECT_ALL | InputTextFlags.INTERNAL_LOCALIZE_DECIMAL_POINT;
        // Because tempInputText() doesn't submit a new item, we poke the last item data
        context.lastItemData.itemFlags |= ItemFlags.INTERNAL_NO_MARK_EDITED;
        if ((context.lastItemData.itemFlags & ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR) != 0) {
            context.lastItemData.itemFlags |= ItemFlags.LIVE_EDIT_ON_INPUT_TEXT;
        } else {
            context.lastItemData.itemFlags &= ~ItemFlags.LIVE_EDIT_ON_INPUT_TEXT;
        }
        final IkString buf = new IkString(text, SCALAR_BUFFER_SIZE);
        if (!tempInputText(bb, id, label, buf, flags, null)) {
            return false;
        }

        // Back up the old value
        final Number backup = IkGuiImplSliders.readNumber(dataType, data, index);

        // Apply the new value (or operations) then clamp
        dataTypeApplyFromText(buf.get(), dataType, data, index, trimmedFormat, null);
        if (clampMin != null || clampMax != null) {
            IkGuiImplSliders.clampScalar(dataType, data, index, clampMin, clampMax);
        }

        // Only mark as edited if the new value is different
        context.lastItemData.itemFlags &= ~ItemFlags.INTERNAL_NO_MARK_EDITED;
        boolean valueChanged = !backup.equals(IkGuiImplSliders.readNumber(dataType, data, index));
        if ((context.lastItemData.itemFlags & ItemFlags.MIXED_VALUE) != 0) {
            valueChanged |= mixedValueChanged();
        }

        if (valueChanged) {
            IkGuiInternal.markItemEdited(id);
        }
        return valueChanged;
    }

    /**
     * For mixed values, whether the text input reports a change even when the value is the same.
     *
     * @return True if the input was edited or validated this frame.
     */
    private static boolean mixedValueChanged() {
        final InputTextState state = getInputTextState(context.lastItemData.id);
        if (state == null) {
            return false;
        }
        if ((context.lastItemData.itemFlags & ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR) != 0) {
            return state.editedThisFrame || state.validatedThisFrame;
        }
        return state.validatedThisFrame;
    }

    // ---------------------------------------------------------------------------------------
    // Data type helpers
    // ---------------------------------------------------------------------------------------

    /**
     * Extract the format specifier out of a format string with leading or trailing decorations. For
     * example "blah blah" becomes "", "%.3f" stays the same, and "hello %.3f world" becomes "%.3f".
     *
     * @param format The format string.
     * @return The format specifier, or an empty string if there is none.
     */
    static String trimFormatDecorations(@NonNull String format) {
        final int start = IkGuiImplSliders.findFormatStart(format);
        if (start >= format.length()) {
            return "";
        }
        final int end = IkGuiImplSliders.findFormatEnd(format, start);
        return format.substring(start, end);
    }

    /**
     * Parse a value from text, like scanf() would with the format, and write it into the data.
     * Leading blanks are ignored, as is anything after the number.
     *
     * @param text The text.
     * @param dataType The data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @param format The format, which decides between decimal/hexadecimal/octal for integers.
     * @param valueWhenEmpty The value to use when the text is empty, null to leave the value as is.
     * @return True if the value changed.
     */
    static boolean dataTypeApplyFromText(
            @NonNull String text,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            String format,
            Number valueWhenEmpty) {
        final Number backup = IkGuiImplSliders.readNumber(dataType, data, index);

        int start = 0;
        while (start < text.length() && InputTextState.isBlank(text.charAt(start))) {
            ++start;
        }
        String trimmed = text.substring(start);
        if (trimmed.isEmpty()) {
            if (valueWhenEmpty != null) {
                IkGuiImplSliders.writeNumber(dataType, data, index, valueWhenEmpty);
                return !backup.equals(IkGuiImplSliders.readNumber(dataType, data, index));
            }
            return false;
        }
        // We accept either decimal point, in case the platform uses ','
        final char decimalPoint = getLocaleDecimalPoint();
        if (decimalPoint != '.') {
            trimmed = trimmed.replace(decimalPoint, '.');
        }

        if (dataType.isFloatingPoint()) {
            final Matcher matcher = FLOAT_PATTERN.matcher(trimmed);
            if (!matcher.find()) {
                return false;
            }
            IkGuiImplSliders.writeScalar(
                    dataType, data, index, Double.parseDouble(matcher.group()));
        } else {
            final OptionalLong parsed = parseInteger(trimmed, dataType, format);
            if (parsed.isEmpty()) {
                return false;
            }
            IkGuiImplSliders.writeLong(dataType, data, index, parsed.getAsLong());
        }
        return !backup.equals(IkGuiImplSliders.readNumber(dataType, data, index));
    }

    /**
     * Parse an integer, using the conversion of the format to pick the base.
     *
     * @param text The text, with leading blanks removed.
     * @param dataType The integer data type.
     * @param format The format.
     * @return The parsed value, clamped to the range of a long, or empty if there is no number.
     */
    private static OptionalLong parseInteger(
            @NonNull String text, @NonNull SliderDataType dataType, String format) {
        char conversion = 'd';
        if (format != null) {
            final int formatStart = IkGuiImplSliders.findFormatStart(format);
            if (formatStart < format.length()) {
                final int formatEnd = IkGuiImplSliders.findFormatEnd(format, formatStart);
                conversion = Character.toLowerCase(format.charAt(formatEnd - 1));
            }
        }
        final int radix;
        final Pattern pattern;
        switch (conversion) {
            case 'x' -> {
                radix = 16;
                pattern = HEXADECIMAL_PATTERN;
            }
            case 'o' -> {
                radix = 8;
                pattern = OCTAL_PATTERN;
            }
            default -> {
                radix = 10;
                pattern = DECIMAL_PATTERN;
            }
        }
        final Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return OptionalLong.empty();
        }
        String number = matcher.group();
        final boolean negative = number.startsWith("-");
        if (number.startsWith("-") || number.startsWith("+")) {
            number = number.substring(1);
        }
        if (radix == 16 && (number.startsWith("0x") || number.startsWith("0X"))) {
            number = number.substring(2);
        }
        final BigInteger parsed = new BigInteger(number, radix);
        BigInteger result = negative ? parsed.negate() : parsed;

        // Like scanf, hexadecimal input wraps around for 32-bit integers (e.g. FFFFFFFF is -1)
        if (radix == 16 && dataType == SliderDataType.INT && result.bitLength() <= 32) {
            return OptionalLong.of(result.intValue());
        }
        final BigInteger longMin = BigInteger.valueOf(Long.MIN_VALUE);
        final BigInteger longMax = BigInteger.valueOf(Long.MAX_VALUE);
        result = result.max(longMin).min(longMax);
        return OptionalLong.of(result.longValue());
    }

    // ---------------------------------------------------------------------------------------
    // Scalar inputs
    // ---------------------------------------------------------------------------------------

    /**
     * Set the reference value for the next inputScalar(), which is used for
     * InputTextFlags.PARSE_EMPTY_REF_VAL and DISPLAY_EMPTY_REF_VAL. Otherwise the reference value
     * is zero.
     *
     * @param value The reference value, which should be a Long for integer types.
     */
    static void setNextItemRefVal(@NonNull Number value) {
        context.nextItemData.fieldFlags |= NextItemFlags.HAS_REFERENCE_VALUE;
        context.nextItemData.referenceValue = value;
    }

    /**
     * An input for a scalar value, with optional +/- step buttons.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param data An array of the data type, or one of the Ik* boxes.
     * @param step The step for the +/- buttons, or null for no buttons.
     * @param stepFast The step for the buttons while holding Ctrl, or null to use the step.
     * @param format The display format, null for the default for the data type.
     * @param inputTextFlags Input text flags.
     * @return True if the value changed (or validated with ENTER_RETURNS_TRUE).
     */
    public static boolean inputScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number step,
            Number stepFast,
            String format,
            int inputTextFlags) {
        if (!IkGuiImplSliders.validateData(dataType, data, 1)) {
            return false;
        }
        return inputScalarIndex(label, dataType, data, 0, step, stepFast, format, inputTextFlags);
    }

    /**
     * Multiple scalar inputs on one line, for editing multiple components.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param data An array of the data type.
     * @param components The number of components.
     * @param step The step for the +/- buttons, or null for no buttons.
     * @param stepFast The step for the buttons while holding Ctrl, or null to use the step.
     * @param format The display format, null for the default for the data type.
     * @param inputTextFlags Input text flags.
     * @return True if any value changed.
     */
    public static boolean inputScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            Number step,
            Number stepFast,
            String format,
            int inputTextFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (!IkGuiImplSliders.validateData(dataType, data, components)) {
            return false;
        }
        if (label == null) {
            label = "";
        }

        boolean valueChanged = false;
        IkGuiImplLayout.beginGroup();
        IkGuiImplUtils.pushID(label);
        IkGuiImplLayout.pushMultiItemsWidths(components, IkGuiImplUtils.calculateItemWidth());
        for (int i = 0; i < components; ++i) {
            IkGuiImplUtils.pushID(i);
            if (i > 0) {
                IkGuiImplLayout.sameLine(0, context.style.variable.itemInnerSpacing.x);
            }
            valueChanged |=
                    inputScalarIndex("", dataType, data, i, step, stepFast, format, inputTextFlags);
            IkGuiImplUtils.popID();
            IkGuiImplLayout.popItemWidth();
        }
        IkGuiImplUtils.popID();

        final String labelDisplayed = Hash.getDisplayedText(label);
        if (!labelDisplayed.isEmpty()) {
            IkGuiImplLayout.sameLine(0.0f, context.style.variable.itemInnerSpacing.x);
            IkGuiImplText.textEx(labelDisplayed);
        }

        IkGuiImplLayout.endGroup();
        return valueChanged;
    }

    /**
     * An input for one value of a scalar array.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param data The data, which has already been validated.
     * @param index The index of the value in the data.
     * @param step The step for the +/- buttons, or null for no buttons.
     * @param stepFast The step for the buttons while holding Ctrl, or null to use the step.
     * @param format The display format, null for the default for the data type.
     * @param flags Input text flags.
     * @return True if the value changed (or validated with ENTER_RETURNS_TRUE).
     */
    private static boolean inputScalarIndex(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            Number step,
            Number stepFast,
            String format,
            int flags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (label == null) {
            label = "";
        }

        final StyleVariables style = context.style.variable;
        if (format == null) {
            format = dataType.getDefaultFormat();
        }

        final Number referenceValue =
                (context.nextItemData.fieldFlags & NextItemFlags.HAS_REFERENCE_VALUE) != 0
                        ? context.nextItemData.referenceValue
                        : IkGuiImplSliders.zeroNumber(dataType);

        final Number value = IkGuiImplSliders.readNumber(dataType, data, index);
        final String text =
                (flags & InputTextFlags.DISPLAY_EMPTY_REF_VAL) != 0
                                && IkGuiImplSliders.compareNumbers(dataType, value, referenceValue)
                                        == 0
                        ? ""
                        : IkGuiImplSliders.formatScalar(dataType, value, format);
        final IkString buf = new IkString(text, SCALAR_BUFFER_SIZE);

        // Disable the markItemEdited() call in inputText, but keep the edited status flag. We
        // call markItemEdited() ourselves by comparing the actual data rather than the string.
        context.nextItemData.itemFlags |= ItemFlags.INTERNAL_NO_MARK_EDITED;
        flags |= InputTextFlags.AUTO_SELECT_ALL | InputTextFlags.INTERNAL_LOCALIZE_DECIMAL_POINT;

        final boolean hasStepButtons = step != null;
        final float buttonSize = hasStepButtons ? IkGuiImplLayout.getFrameHeight() : 0.0f;
        boolean ret;
        if (hasStepButtons) {
            // The only purpose of the group here is to allow the caller to query item data, e.g.
            // isItemActive()
            IkGuiImplLayout.beginGroup();
            IkGuiImplUtils.pushID(label);
            IkGuiImplUtils.setNextItemWidth(
                    Math.max(
                            1.0f,
                            IkGuiImplUtils.calculateItemWidth()
                                    - (buttonSize + style.itemInnerSpacing.x) * 2));
            // pushID(label) + "" gives us the expected ID from the outside point of view
            ret = inputTextEx("", null, buf, 0.0f, 0.0f, flags, null);
            IkGuiInternal.testEngineItemInfo(
                    context.lastItemData.id,
                    label,
                    context.lastItemData.statusFlags | ItemStatusFlags.INPUTABLE);
        } else {
            ret = inputTextEx(label, null, buf, 0.0f, 0.0f, flags, null);
        }

        // Apply
        final Number valueWhenEmpty =
                (flags & InputTextFlags.PARSE_EMPTY_REF_VAL) != 0 ? referenceValue : null;
        boolean valueChanged = false;
        if ((context.lastItemData.itemFlags & ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR) != 0) {
            // We would be using ret if ENTER_RETURNS_TRUE was not involved
            final boolean inputEdited =
                    (context.lastItemData.statusFlags & ItemStatusFlags.EDITED_INTERNAL) != 0;
            if (inputEdited) {
                valueChanged =
                        dataTypeApplyFromText(
                                buf.get(), dataType, data, index, format, valueWhenEmpty);
            }
        } else if (context.deactivatedItemData.id == context.lastItemData.id) {
            valueChanged =
                    dataTypeApplyFromText(buf.get(), dataType, data, index, format, valueWhenEmpty);
        }
        if ((context.lastItemData.itemFlags & ItemFlags.MIXED_VALUE) != 0) {
            valueChanged |= mixedValueChanged();
        }

        // Step buttons
        if (hasStepButtons) {
            final float backupFramePaddingX = style.framePadding.x;
            style.framePadding.x = style.framePadding.y;
            final boolean readOnly = (flags & InputTextFlags.READ_ONLY) != 0;
            if (readOnly) {
                IkGuiImplUtils.beginDisabled(true);
            }
            IkGuiImplUtils.pushItemFlag(ItemFlags.BUTTON_REPEAT, true);
            final Number actualStep = context.io.keyCtrl && stepFast != null ? stepFast : step;
            IkGuiImplLayout.sameLine(0, style.itemInnerSpacing.x);
            if (IkGuiImplButtons.buttonEx("-", buttonSize, buttonSize, ButtonFlags.NONE)) {
                IkGuiImplSliders.applyStep(dataType, data, index, false, actualStep);
                valueChanged = ret = true;
            }
            IkGuiImplLayout.sameLine(0, style.itemInnerSpacing.x);
            if (IkGuiImplButtons.buttonEx("+", buttonSize, buttonSize, ButtonFlags.NONE)) {
                IkGuiImplSliders.applyStep(dataType, data, index, true, actualStep);
                valueChanged = ret = true;
            }
            IkGuiImplUtils.popItemFlag();
            if (readOnly) {
                IkGuiImplUtils.endDisabled();
            }

            final String labelDisplayed = Hash.getDisplayedText(label);
            if (!labelDisplayed.isEmpty()) {
                IkGuiImplLayout.sameLine(0, style.itemInnerSpacing.x);
                IkGuiImplText.textEx(labelDisplayed);
            }
            style.framePadding.x = backupFramePaddingX;

            IkGuiImplUtils.popID();
            IkGuiImplLayout.endGroup();
        }

        context.lastItemData.itemFlags &= ~ItemFlags.INTERNAL_NO_MARK_EDITED;
        if (valueChanged) {
            IkGuiInternal.markItemEdited(context.lastItemData.id);
        }

        if ((flags & InputTextFlags.ENTER_RETURNS_TRUE) != 0) {
            return ret;
        }
        return valueChanged;
    }

    public static boolean inputDouble(
            String label,
            double @NonNull [] value,
            double step,
            double stepFast,
            String format,
            int inputTextFlags) {
        return inputScalar(
                label,
                SliderDataType.DOUBLE,
                value,
                step > 0.0 ? step : null,
                stepFast > 0.0 ? stepFast : null,
                format,
                inputTextFlags);
    }

    public static boolean inputFloat(
            String label,
            float @NonNull [] value,
            float step,
            float stepFast,
            String format,
            int inputTextFlags) {
        return inputScalar(
                label,
                SliderDataType.FLOAT,
                value,
                step > 0.0f ? step : null,
                stepFast > 0.0f ? stepFast : null,
                format,
                inputTextFlags);
    }

    public static boolean inputFloat2(
            String label, float @NonNull [] values, String format, int inputTextFlags) {
        return inputScalarN(
                label, SliderDataType.FLOAT, values, 2, null, null, format, inputTextFlags);
    }

    public static boolean inputFloat3(
            String label, float @NonNull [] values, String format, int inputTextFlags) {
        return inputScalarN(
                label, SliderDataType.FLOAT, values, 3, null, null, format, inputTextFlags);
    }

    public static boolean inputFloat4(
            String label, float @NonNull [] values, String format, int inputTextFlags) {
        return inputScalarN(
                label, SliderDataType.FLOAT, values, 4, null, null, format, inputTextFlags);
    }

    public static boolean inputInt(
            String label, int @NonNull [] value, int step, int stepFast, int inputTextFlags) {
        // Hexadecimal input is provided as a convenience, but the flag name is awkward.
        // Typically you'd use inputText() to parse your own data if you want to handle prefixes.
        final String format =
                (inputTextFlags & InputTextFlags.CHARS_HEXADECIMAL) != 0 ? "%08X" : "%d";
        return inputScalar(
                label,
                SliderDataType.INT,
                value,
                step > 0 ? step : null,
                stepFast > 0 ? stepFast : null,
                format,
                inputTextFlags);
    }

    public static boolean inputInt2(String label, int @NonNull [] values, int inputTextFlags) {
        return inputScalarN(label, SliderDataType.INT, values, 2, null, null, "%d", inputTextFlags);
    }

    public static boolean inputInt3(String label, int @NonNull [] values, int inputTextFlags) {
        return inputScalarN(label, SliderDataType.INT, values, 3, null, null, "%d", inputTextFlags);
    }

    public static boolean inputInt4(String label, int @NonNull [] values, int inputTextFlags) {
        return inputScalarN(label, SliderDataType.INT, values, 4, null, null, "%d", inputTextFlags);
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplInputText() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
