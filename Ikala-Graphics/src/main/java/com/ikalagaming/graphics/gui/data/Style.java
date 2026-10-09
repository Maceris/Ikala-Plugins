package com.ikalagaming.graphics.gui.data;

import lombok.NonNull;

/** A style, which is the colors and variables that decide how the GUI looks. */
public class Style {
    public final StyleColors color;
    public final StyleVariables variable;

    /** Create the default style, with the dark theme. */
    public Style() {
        color = new StyleColors();
        variable = new StyleVariables();
    }

    /**
     * Create a copy of another style.
     *
     * @param other The style to copy.
     */
    public Style(@NonNull Style other) {
        color = new StyleColors(other.color);
        variable = new StyleVariables(other.variable);
    }

    /**
     * Copy all the colors and variables from another style.
     *
     * @param other The style to copy.
     */
    public void set(@NonNull Style other) {
        color.set(other.color);
        variable.set(other.variable);
    }
}
