package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;

import lombok.Getter;
import lombok.NonNull;

import java.util.function.Consumer;

/**
 * An IkGui checkbox with a label after it. Clicking it flips it and tells {@link
 * #onChange(Consumer)}; code can also set it, as when what it shows changes elsewhere.
 */
public class Checkbox extends Node<Checkbox> {

    /** The box's side and its frame padding, as a share of a line of text. */
    private static final float BOX_LINES = 1.3f;

    /** The gap between the box and the label, as a share of a line. */
    private static final float GAP_LINES = 0.3f;

    /**
     * The label.
     *
     * @return The text.
     */
    @Getter private String text;

    /**
     * Whether it is checked.
     *
     * @return True if checked.
     */
    @Getter private boolean checked;

    /** Runs on the render thread after the GUI is drawn, with the new state, when clicked. */
    private Consumer<Boolean> onChange;

    /**
     * A checkbox.
     *
     * @param id The node's ID.
     * @param text The label.
     */
    public Checkbox(@NonNull String id, @NonNull String text) {
        super(id);
        this.text = text;
    }

    /**
     * Change the label.
     *
     * @param newText The label.
     * @return This checkbox.
     */
    public Checkbox text(@NonNull String newText) {
        if (!text.equals(newText)) {
            text = newText;
            markDirty();
        }
        return this;
    }

    /**
     * Check or clear it.
     *
     * @param check Whether it is checked.
     * @return This checkbox.
     */
    public Checkbox checked(boolean check) {
        checked = check;
        return this;
    }

    /**
     * Set what happens when the player clicks it.
     *
     * @param action Gets the new state, or null for nothing.
     * @return This checkbox.
     */
    public Checkbox onChange(Consumer<Boolean> action) {
        onChange = action;
        return this;
    }

    @Override
    protected void measure(@NonNull LayoutContext context, float[] out) {
        context.text().measure(text, fontPixels, out);
        final float line = out[1];
        out[0] += line * (BOX_LINES + GAP_LINES);
        out[1] = line * BOX_LINES;
    }

    @Override
    public String styleType() {
        return "label";
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        IkGuiStyler.Pushed pushed = IkGuiStyler.push(IkGuiStyler.Kind.LABEL, this, frame);
        IkGui.setCursorScreenPos(rect.getLeft(), rect.getTop());
        // ### keeps the ID the same when the text changes
        if (IkGui.checkbox(text + "###checkbox", checked)) {
            checked = !checked;
            if (onChange != null) {
                final Consumer<Boolean> action = onChange;
                final boolean now = checked;
                frame.fire(() -> action.accept(now));
            }
        }
        recordItem(IkGui.getItemID());
        pushed.pop();
    }
}
