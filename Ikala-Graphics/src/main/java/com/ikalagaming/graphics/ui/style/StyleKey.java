package com.ikalagaming.graphics.ui.style;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** A property a style can set, with the type of value it takes and its name in theme files. */
@RequiredArgsConstructor
public enum StyleKey {
    /** The text color. */
    TEXT("text", ValueType.COLOR),
    /** The fill color: a button's face, a panel's background. */
    BACKGROUND("background", ValueType.COLOR),
    /** The border color. */
    BORDER("border", ValueType.COLOR),
    /** A highlight color for custom drawing, like an icon or a selection mark. */
    ACCENT("accent", ValueType.COLOR),
    /** How round the corners are. */
    ROUNDING("rounding", ValueType.LENGTH),
    /** How thick the border is, 0 for none. */
    BORDER_SIZE("borderSize", ValueType.LENGTH),
    /** Space between the edges and the content. */
    PADDING("padding", ValueType.INSETS),
    /** Space between the children of a row or column. */
    GAP("gap", ValueType.LENGTH),
    /** The font size, in UI units. */
    FONT_SIZE("fontSize", ValueType.NUMBER);

    /** The kinds of value a property takes. */
    public enum ValueType {
        /** A packed RGBA color, red in the high byte. */
        COLOR,
        /** A {@link com.ikalagaming.graphics.ui.Length}. */
        LENGTH,
        /** {@link com.ikalagaming.graphics.ui.Insets}. */
        INSETS,
        /** A plain number, as a Float. */
        NUMBER
    }

    /**
     * The name used in theme files.
     *
     * @return The name.
     */
    @Getter private final String yamlName;

    /**
     * The kind of value this property takes.
     *
     * @return The value type.
     */
    @Getter private final ValueType type;

    /**
     * Find a property by its theme file name.
     *
     * @param name The name.
     * @return The property, or null if there is none with that name.
     */
    public static StyleKey byName(@NonNull String name) {
        for (StyleKey key : values()) {
            if (key.yamlName.equals(name)) {
                return key;
            }
        }
        return null;
    }
}
