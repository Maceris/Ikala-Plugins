package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;

import lombok.Getter;
import lombok.NonNull;

/** A line of text. */
@Getter
public class Label extends Node<Label> {
    /**
     * The text to show. -- GETTER -- The text being shown.
     *
     * @return The text.
     */
    private String text;

    /**
     * Create a label.
     *
     * @param id The ID, which must be unique among its siblings.
     * @param text The text to show.
     */
    public Label(@NonNull String id, @NonNull String text) {
        super(id);
        this.text = text;
    }

    /**
     * Change the text.
     *
     * @param newText The text to show.
     * @return This label.
     */
    public Label text(@NonNull String newText) {
        if (!text.equals(newText)) {
            text = newText;
            markDirty();
        }
        return this;
    }

    @Override
    protected void measure(@NonNull LayoutContext context, float[] out) {
        context.text().measure(text, fontPixels, out);
    }

    @Override
    public String styleType() {
        return "label";
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        IkGuiStyler.Pushed pushed = IkGuiStyler.push(IkGuiStyler.Kind.LABEL, this, frame);
        IkGui.setCursorScreenPos(rect.getLeft(), rect.getTop());
        IkGui.textUnformatted(text);
        pushed.pop();
    }
}
