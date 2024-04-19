package com.ikalagaming.factory.gui.component.util;

import lombok.*;

@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class Style {
    /**
     * Defines the different shapes of a component.
     * <li><b>SQUARE</b> - A regular rectangle. Square as in right angles, rather than a literal
     *     square shape.
     * <li><b>ROUNDED</b> - A rounded rectangle.
     * </ul>
     */
    public enum Shape {
        /**
         * A rectangle with sharp corners. Square as in right angles, rather than a literal square
         * shape.
         */
        SQUARE,
        /** A rounded rectangle. */
        ROUNDED
    }

    /**
     * The colors of a window and its border. Borders are a darker shade of the base color.
     *
     * <ul>
     *   <li><b>LIGHT</b> (white)
     *   <li><b>DARK</b> (dark grey)
     *   <li><b>RED</b>
     *   <li><b>GREEN</b>
     *   <li><b>BLUE</b>
     *   <li><b>YELLOW</b>
     * </ul>
     */
    public enum ColorScheme {
        /** White with grey borders. */
        LIGHT,
        /** Grey with dark grey borders. */
        DARK,
        /** Red with darker red borders. */
        RED,
        /** Green with darker green borders. */
        GREEN,
        /** Blue with darker blue borders. */
        BLUE,
        /** Yellow with dark yellow borders. */
        YELLOW
    }

    /** The shape of the component. */
    private Shape shape;

    /** The color scheme to use for the component. */
    private ColorScheme colorScheme;

    /** Whether we fill the background/contents of the component. */
    private boolean filled;

    /** Whether we draw a border on the edges of the component. */
    private boolean bordered;
}
