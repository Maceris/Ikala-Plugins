package com.ikalagaming.graphics.frontend.gui.flags;

/**
 * Flags for colorEdit3()/colorEdit4(), colorPicker3()/colorPicker4(), colorButton(), and
 * IkIO.configColorEditFlags.
 */
public class ColorEditFlags {
    public static final int NONE = 0;

    /**
     * ColorEdit, ColorPicker, ColorButton: ignore the alpha component (will only read 3 components
     * from the input array).
     */
    public static final int NO_ALPHA = 1 << 1;

    /** ColorEdit: disable the picker when clicking on the color square. */
    public static final int NO_PICKER = 1 << 2;

    /** ColorEdit: disable toggling the options menu when right-clicking on inputs/small preview. */
    public static final int NO_OPTIONS = 1 << 3;

    /**
     * ColorEdit, ColorPicker: disable the color square preview next to the inputs (e.g. to show
     * only the inputs).
     */
    public static final int NO_SMALL_PREVIEW = 1 << 4;

    /**
     * ColorEdit, ColorPicker: disable the input sliders/text widgets (e.g. to show only the small
     * preview color square).
     */
    public static final int NO_INPUTS = 1 << 5;

    /** ColorEdit, ColorPicker, ColorButton: disable the tooltip when hovering the preview. */
    public static final int NO_TOOLTIP = 1 << 6;

    /**
     * ColorEdit, ColorPicker: disable display of the inline text label (the label is still
     * forwarded to the tooltip and picker).
     */
    public static final int NO_LABEL = 1 << 7;

    /**
     * ColorPicker: disable the bigger color preview on the right side of the picker, use the small
     * color square preview instead.
     */
    public static final int NO_SIDE_PREVIEW = 1 << 8;

    /** ColorEdit: disable the drag and drop target/source. ColorButton: disable the source. */
    public static final int NO_DRAG_DROP = 1 << 9;

    /** ColorButton: disable the border (which is enforced by default). */
    public static final int NO_BORDER = 1 << 10;

    /**
     * ColorEdit: disable rendering the R/G/B/A color markers. May also be disabled globally by
     * setting style.colorMarkerSize to 0.
     */
    public static final int NO_COLOR_MARKERS = 1 << 11;

    /**
     * ColorEdit, ColorPicker, ColorButton: disable alpha in the preview. Contrary to NO_ALPHA, it
     * may still be edited when calling colorEdit4()/colorPicker4(). For colorButton() this does the
     * same as NO_ALPHA.
     */
    public static final int ALPHA_OPAQUE = 1 << 12;

    /**
     * ColorEdit, ColorPicker, ColorButton: disable rendering a checkerboard background behind
     * transparent colors.
     */
    public static final int ALPHA_NO_BACKGROUND = 1 << 13;

    /** ColorEdit, ColorPicker, ColorButton: display a half opaque / half transparent preview. */
    public static final int ALPHA_PREVIEW_HALF = 1 << 14;

    /** ColorEdit, ColorPicker: show a vertical alpha bar/gradient in the picker. */
    public static final int ALPHA_BAR = 1 << 18;

    /**
     * (WIP) ColorEdit: Currently only disables the 0.0f..1.0f limits in RGBA editing (note: you
     * probably want to use the FLOAT flag as well).
     */
    public static final int HDR = 1 << 19;

    /**
     * [Display] ColorEdit: override the display type among RGB/HSV/Hex. ColorPicker: select any
     * combination using one or more of RGB/HSV/Hex.
     */
    public static final int DISPLAY_RGB = 1 << 20;

    /**
     * [Display]
     *
     * @see #DISPLAY_RGB
     */
    public static final int DISPLAY_HSV = 1 << 21;

    /**
     * [Display]
     *
     * @see #DISPLAY_RGB
     */
    public static final int DISPLAY_HEX = 1 << 22;

    /** [DataType] ColorEdit, ColorPicker, ColorButton: display values formatted as 0..255. */
    public static final int UINT8 = 1 << 23;

    /**
     * [DataType] ColorEdit, ColorPicker, ColorButton: display values formatted as 0.0f..1.0f floats
     * instead of 0..255 integers. No round-trip of values via integers.
     */
    public static final int FLOAT = 1 << 24;

    /** [Picker] ColorPicker: bar for Hue, rectangle for Saturation/Value. */
    public static final int PICKER_HUE_BAR = 1 << 25;

    /** [Picker] ColorPicker: wheel for Hue, triangle for Saturation/Value. */
    public static final int PICKER_HUE_WHEEL = 1 << 26;

    /**
     * [Picker] ColorPicker: disable rotating the Saturation/Value triangle. Best set in
     * IkIO.configColorEditFlags once.
     */
    public static final int PICKER_NO_ROTATE = 1 << 27;

    /** [Input] ColorEdit, ColorPicker: input and output data in RGB format. */
    public static final int INPUT_RGB = 1 << 28;

    /** [Input] ColorEdit, ColorPicker: input and output data in HSV format. */
    public static final int INPUT_HSV = 1 << 29;

    /**
     * Default options, copied to IkIO.configColorEditFlags during initialization. The user can
     * change these through the options menu, unless NO_OPTIONS is used.
     */
    public static final int DEFAULT_OPTIONS = UINT8 | DISPLAY_RGB | INPUT_RGB | PICKER_HUE_BAR;

    // Masks
    public static final int ALPHA_MASK =
            NO_ALPHA | ALPHA_OPAQUE | ALPHA_NO_BACKGROUND | ALPHA_PREVIEW_HALF;
    public static final int DISPLAY_MASK = DISPLAY_RGB | DISPLAY_HSV | DISPLAY_HEX;
    public static final int DATA_TYPE_MASK = UINT8 | FLOAT;
    public static final int PICKER_MASK = PICKER_HUE_WHEEL | PICKER_HUE_BAR;
    public static final int INPUT_MASK = INPUT_RGB | INPUT_HSV;

    /** Private constructor so this is not instantiated. */
    private ColorEditFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
