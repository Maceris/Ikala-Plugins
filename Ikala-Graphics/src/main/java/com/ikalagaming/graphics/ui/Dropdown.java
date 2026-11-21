package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;

import lombok.Getter;
import lombok.NonNull;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * An IkGui combo box: shows the chosen option, and opens a list of every option when clicked. It is
 * a real IkGui item, so it can be reached with the keyboard or a gamepad, and the open list can be
 * moved through with the arrow keys. It grows to fill its width unless sized otherwise.
 */
public class Dropdown extends Node<Dropdown> {

    /**
     * The options' text, in order.
     *
     * @return The options.
     */
    @Getter private List<String> options = List.of();

    /**
     * Which option is chosen, or -1 for none.
     *
     * @return The chosen option's index.
     */
    @Getter private int selected = -1;

    /** Runs on the render thread with the index the player chose. */
    private IntConsumer onChange;

    /**
     * Create a dropdown.
     *
     * @param id The ID, which must be unique among its siblings.
     */
    public Dropdown(@NonNull String id) {
        super(id);
        width(Sizing.grow());
    }

    /**
     * Change the options. The chosen index is kept if it is still in range, and cleared otherwise.
     *
     * @param newOptions The options' text, in order.
     * @return This dropdown.
     */
    public Dropdown options(@NonNull List<String> newOptions) {
        if (!options.equals(newOptions)) {
            options = List.copyOf(newOptions);
            if (selected >= options.size()) {
                selected = -1;
            }
            markDirty();
        }
        return this;
    }

    /**
     * Choose an option, without running {@link #onChange(IntConsumer)}.
     *
     * @param index The option's index, or -1 for none. Out of range means none.
     * @return This dropdown.
     */
    public Dropdown selected(int index) {
        selected = index >= 0 && index < options.size() ? index : -1;
        return this;
    }

    /**
     * Set what happens when the player chooses an option, even the one already chosen. It runs on
     * the render thread after the GUI is drawn, outside rendering.
     *
     * @param action Receives the chosen index, or null for nothing.
     * @return This dropdown.
     */
    public Dropdown onChange(IntConsumer action) {
        onChange = action;
        return this;
    }

    /**
     * The chosen option's text.
     *
     * @return The text, or blank if nothing is chosen.
     */
    public String getText() {
        return selected < 0 ? "" : options.get(selected);
    }

    @Override
    public String styleType() {
        return "dropdown";
    }

    @Override
    protected Insets defaultPadding(@NonNull LayoutContext context) {
        return context.framePadding();
    }

    @Override
    protected void measure(@NonNull LayoutContext context, float[] out) {
        // One line tall, as wide as the widest option; the arrow and padding come from the layout
        float widest = 0;
        for (String option : options) {
            context.text().measure(option, fontPixels, out);
            widest = Math.max(widest, out[0]);
        }
        context.text().measure(" ", fontPixels, out);
        out[0] = widest + out[1];
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        IkGuiStyler.Pushed pushed = IkGuiStyler.push(IkGuiStyler.Kind.TEXT_INPUT, this, frame);
        IkGui.setCursorScreenPos(rect.getLeft(), rect.getTop());
        IkGui.setNextItemWidth(rect.getWidth());
        // ## keeps the label hidden, and lets automation find it by ID
        boolean open = IkGui.beginCombo("##" + getId(), getText());
        recordItem(IkGui.getItemID());
        if (open) {
            for (int i = 0; i < options.size(); ++i) {
                boolean chosen = i == selected;
                // ### keeps each row's ID the same when the text changes
                if (IkGui.selectable(options.get(i) + "###option" + i, chosen)) {
                    selected = i;
                    if (onChange != null) {
                        final int index = i;
                        frame.fire(() -> onChange.accept(index));
                    }
                }
                if (chosen) {
                    IkGui.setItemDefaultFocus();
                }
            }
            IkGui.endCombo();
        }
        pushed.pop();
    }
}
