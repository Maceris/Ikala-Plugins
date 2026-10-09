package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;

import lombok.NonNull;

/**
 * An IkGui button. It is a real IkGui item, so it can be clicked, reached with Tab or a gamepad,
 * and activated with Space, Enter or the gamepad's accept button.
 */
public class Button extends Node<Button> {
    /** The text on the button. */
    private String text;

    /** Runs on the render thread after the frame the button is pressed in. */
    private Runnable onClick;

    /** Whether the button takes navigation focus when its surface first gets focus. */
    private boolean autofocus;

    /**
     * Create a button.
     *
     * @param id The ID, which must be unique among its siblings.
     * @param text The text on the button.
     */
    public Button(@NonNull String id, @NonNull String text) {
        super(id);
        this.text = text;
    }

    /**
     * The text on the button.
     *
     * @return The text.
     */
    public String getText() {
        return text;
    }

    /**
     * Change the text on the button.
     *
     * @param newText The text.
     * @return This button.
     */
    public Button text(@NonNull String newText) {
        if (!text.equals(newText)) {
            text = newText;
            markDirty();
        }
        return this;
    }

    /**
     * Set what happens when the button is pressed. It runs on the render thread after the GUI is
     * drawn, outside rendering, so it can do things like load models.
     *
     * @param action The action, or null for none.
     * @return This button.
     */
    public Button onClick(Runnable action) {
        onClick = action;
        return this;
    }

    /**
     * Take navigation focus when the surface first gets focus, so a gamepad or keyboard can use it
     * straight away.
     *
     * @param focus Whether to take focus.
     * @return This button.
     */
    public Button autofocus(boolean focus) {
        autofocus = focus;
        return this;
    }

    @Override
    protected void measure(@NonNull LayoutContext context, float[] out) {
        context.text().measure(text, fontPixels, out);
        out[0] += 2 * context.framePaddingX();
        out[1] += 2 * context.framePaddingY();
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        IkGui.setCursorScreenPos(rect.getLeft(), rect.getTop());
        // ### keeps the ID the same when the text changes
        if (IkGui.button(text + "###button", rect.getWidth(), rect.getHeight())) {
            frame.fire(onClick);
        }
        if (autofocus) {
            IkGui.setItemDefaultFocus();
        }
    }
}
