package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.flags.SelectableFlags;

import lombok.Getter;
import lombok.NonNull;

/**
 * An IkGui selectable row, for lists. It highlights when selected, and is a real IkGui item, so
 * lists can be navigated with the arrow keys or a gamepad. Give it {@code Sizing.grow()} width, or
 * put it in a column that stretches, so the whole row can be clicked.
 */
public class Selectable extends Node<Selectable> {
    /**
     * The text on the row.
     *
     * @return The text.
     */
    @Getter private String text;

    /**
     * Whether the row is highlighted as selected.
     *
     * @return True if it is selected.
     */
    @Getter private boolean selected;

    /** Runs on the render thread after the frame the row is clicked in. */
    private Runnable onClick;

    /**
     * Create a row.
     *
     * @param id The ID, which must be unique among its siblings.
     * @param text The text on the row.
     */
    public Selectable(@NonNull String id, @NonNull String text) {
        super(id);
        this.text = text;
    }

    /**
     * Change the text.
     *
     * @param newText The text.
     * @return This row.
     */
    public Selectable text(@NonNull String newText) {
        if (!text.equals(newText)) {
            text = newText;
            markDirty();
        }
        return this;
    }

    /**
     * Highlight the row as selected, or not.
     *
     * @param select Whether it is selected.
     * @return This row.
     */
    public Selectable selected(boolean select) {
        selected = select;
        return this;
    }

    /**
     * Set what happens when the row is clicked or activated. It runs on the render thread after the
     * GUI is drawn, outside rendering.
     *
     * @param action The action, or null for none.
     * @return This row.
     */
    public Selectable onClick(Runnable action) {
        onClick = action;
        return this;
    }

    @Override
    protected void measure(@NonNull LayoutContext context, float[] out) {
        context.text().measure(text, fontPixels, out);
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        IkGui.setCursorScreenPos(rect.getLeft(), rect.getTop());
        // ### keeps the ID the same when the text changes
        if (IkGui.selectable(
                text + "###selectable",
                selected,
                SelectableFlags.NONE,
                rect.getWidth(),
                rect.getHeight())) {
            frame.fire(onClick);
        }
    }
}
