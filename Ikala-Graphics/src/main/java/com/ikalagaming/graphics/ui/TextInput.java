package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.gui.data.GuiInputTextCallbackData;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.flags.InputTextFlags;

import lombok.NonNull;

import java.util.function.Consumer;

/**
 * An IkGui single line text field. It grows to fill the width of its parent by default, and is one
 * line of text tall plus IkGui's frame padding.
 *
 * <p>The text can be read at any time on the render thread. {@link #onSubmit(Consumer)} hears about
 * it when the user presses Enter, or leaves the field after changing it.
 */
public class TextInput extends Node<TextInput> {
    /** The most text the field holds before it grows its buffer. */
    private static final int INITIAL_CAPACITY = 256;

    /** The text being edited. */
    private final IkString buffer;

    /** Runs on the render thread with the text, after the frame it is submitted in. */
    private Consumer<String> onSubmit;

    /** Runs when Escape is pressed while the field is being edited. */
    private Runnable onCancel;

    /** Whether only Enter submits, not leaving the field after changing it. */
    private boolean enterOnly;

    /** Whether the field takes the keyboard the next time it is drawn. */
    private boolean focusNext;

    /**
     * Whether the cursor goes to the end once the field is active. Focusing a field from code
     * selects all of its text, so the first key typed would replace text it opened with, like the
     * {@code /} of a chat command.
     */
    private boolean cursorToEnd;

    /** Moves the cursor to the end, once, on the first frame the field is active after focus. */
    private final GuiInputTextCallback toEnd =
            new GuiInputTextCallback() {
                @Override
                public void accept(GuiInputTextCallbackData data) {
                    if (cursorToEnd) {
                        final int length = data.getBufTextLen();
                        data.setCursorPos(length);
                        data.setSelection(length, length);
                        cursorToEnd = false;
                    }
                }
            };

    /**
     * The text last handed to {@link #onSubmit}, so leaving the field after Enter doesn't repeat.
     */
    private String lastSubmitted;

    /**
     * Create a text field.
     *
     * @param id The ID, which must be unique among its siblings.
     * @param text The starting text.
     */
    public TextInput(@NonNull String id, @NonNull String text) {
        super(id);
        buffer = new IkString(text, Math.max(INITIAL_CAPACITY, text.length() * 2));
        width(Sizing.grow());
    }

    /**
     * The current text. Render thread only.
     *
     * @return The text.
     */
    public String text() {
        return buffer.get();
    }

    @Override
    public String getText() {
        return buffer.get();
    }

    /**
     * Replace the text. Render thread only once shown.
     *
     * @param text The new text.
     * @return This field.
     */
    public TextInput text(@NonNull String text) {
        buffer.set(text, true);
        return this;
    }

    /**
     * Set what happens when the text is submitted, by pressing Enter or leaving the field after
     * changing it. It runs on the render thread after the GUI is drawn, outside rendering.
     *
     * @param action Receives the text, or null for nothing.
     * @return This field.
     */
    public TextInput onSubmit(Consumer<String> action) {
        onSubmit = action;
        return this;
    }

    /**
     * Set what happens when Escape is pressed while the field is being edited, like closing a chat
     * box. It runs on the render thread after the GUI is drawn.
     *
     * @param action The action, or null for nothing.
     * @return This field.
     */
    public TextInput onCancel(Runnable action) {
        onCancel = action;
        return this;
    }

    /**
     * Submit only on Enter, not when the field is left after a change, as for a chat line.
     *
     * @param only True for Enter only.
     * @return This field.
     */
    public TextInput submitOnEnterOnly(boolean only) {
        enterOnly = only;
        return this;
    }

    /**
     * Take the keyboard the next time the field is drawn, so typing goes straight into it after any
     * text it already has.
     *
     * @return This field.
     */
    public TextInput focus() {
        focusNext = true;
        cursorToEnd = true;
        return this;
    }

    @Override
    public String styleType() {
        return "text-input";
    }

    @Override
    protected Insets defaultPadding(@NonNull LayoutContext context) {
        return context.framePadding();
    }

    @Override
    protected void measure(@NonNull LayoutContext context, float[] out) {
        // Any text is one line tall; the width comes from the sizing, and the padding is added by
        // the layout
        context.text().measure(" ", fontPixels, out);
        out[0] = 0;
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        IkGuiStyler.Pushed pushed = IkGuiStyler.push(IkGuiStyler.Kind.TEXT_INPUT, this, frame);
        IkGui.setCursorScreenPos(rect.getLeft(), rect.getTop());
        IkGui.setNextItemWidth(rect.getWidth());
        if (focusNext) {
            IkGui.setKeyboardFocusHere();
            focusNext = false;
        }
        boolean entered =
                IkGui.inputText(
                        "##input",
                        buffer,
                        InputTextFlags.ENTER_RETURNS_TRUE
                                | (cursorToEnd ? InputTextFlags.CALLBACK_ALWAYS : 0),
                        toEnd);
        recordItem(IkGui.getItemID());
        // Escape ends editing inside inputText, so the field may already be inactive
        if (onCancel != null
                && (IkGui.isItemActive() || IkGui.isItemDeactivated())
                && IkGui.isKeyPressed(Key.ESCAPE, false)) {
            frame.fire(onCancel);
        }
        boolean leftAfterEdit = !enterOnly && IkGui.isItemDeactivatedAfterEdit();
        String text = buffer.get();
        if (entered || (leftAfterEdit && !text.equals(lastSubmitted))) {
            lastSubmitted = text;
            if (onSubmit != null) {
                Consumer<String> action = onSubmit;
                frame.fire(() -> action.accept(text));
            }
        }
        pushed.pop();
    }
}
