package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkString;
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

    @Override
    protected void measure(@NonNull LayoutContext context, float[] out) {
        // Any text is one line tall; the width comes from the sizing
        context.text().measure(" ", fontPixels, out);
        out[0] = 0;
        out[1] += 2 * context.framePaddingY();
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        IkGui.setCursorScreenPos(rect.getLeft(), rect.getTop());
        IkGui.setNextItemWidth(rect.getWidth());
        boolean entered = IkGui.inputText("##input", buffer, InputTextFlags.ENTER_RETURNS_TRUE);
        boolean leftAfterEdit = IkGui.isItemDeactivatedAfterEdit();
        String text = buffer.get();
        if (entered || (leftAfterEdit && !text.equals(lastSubmitted))) {
            lastSubmitted = text;
            if (onSubmit != null) {
                Consumer<String> action = onSubmit;
                frame.fire(() -> action.accept(text));
            }
        }
    }
}
