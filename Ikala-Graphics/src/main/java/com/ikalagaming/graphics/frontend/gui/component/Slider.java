package com.ikalagaming.graphics.frontend.gui.component;

import imgui.ImGui;
import imgui.flag.ImGuiSliderFlags;

import java.util.Objects;

public class Slider extends Component implements Interactive {

    /** The text to show next to the slider. */
    private final String label;

    /** The underlying value. */
    private final float[] value;

    private final float minValue;
    private final float maxValue;
    private final String format;
    private final int flags;

    /** Whether the user interacted with the slider. */
    private boolean changed;

    /**
     * Create a slider with default format and no flags.
     *
     * @param label Label to use. A null value will result in an empty string.
     * @param initialState Initial value of the slider.
     * @param minValue Minimum allowed value. Will be clamped to the min value of a float if
     *     infinite or NaN.
     * @param maxValue Maximum allowed value. Will be clamped to the max value of a float if
     *     infinite or NaN.
     */
    public Slider(String label, float initialState, float minValue, float maxValue) {
        this(label, initialState, minValue, maxValue, null, ImGuiSliderFlags.None);
    }

    /**
     * Create a nslider with no flags.
     *
     * @param label Label to use. A null value will result in an empty string.
     * @param initialState Initial value of the slider.
     * @param minValue Minimum allowed value. Will be clamped to the min value of a float if
     *     infinite or NaN.
     * @param maxValue Maximum allowed value. Will be clamped to the max value of a float if
     *     infinite or NaN.
     * @param format Format string for the value, defaults to 3 decimal places if left null.
     */
    public Slider(String label, float initialState, float minValue, float maxValue, String format) {
        this(label, initialState, minValue, maxValue, format, ImGuiSliderFlags.None);
    }

    /**
     * Create a new slider.
     *
     * @param label Label to use. A null value will result in an empty string.
     * @param initialState Initial value of the slider.
     * @param minValue Minimum allowed value. Will be clamped to the min value of a float if
     *     infinite or NaN.
     * @param maxValue Maximum allowed value. Will be clamped to the max value of a float if
     *     infinite or NaN.
     * @param format Format string for the value, defaults to 3 decimal places if left null.
     * @param flags Slider flags.
     * @see ImGuiSliderFlags
     */
    public Slider(
            String label,
            float initialState,
            float minValue,
            float maxValue,
            String format,
            int flags) {
        this.label = Objects.requireNonNullElse(label, "");
        this.value = new float[] {initialState};
        this.minValue = Float.isFinite(minValue) ? minValue : Float.MIN_VALUE;
        this.maxValue = Float.isFinite(maxValue) ? maxValue : Float.MAX_VALUE;
        this.format = Objects.requireNonNullElse(format, "%.3f");
        this.flags = flags;
    }

    @Override
    public boolean checkResult() {
        var result = changed;
        changed = false;
        return result;
    }

    @Override
    public void draw(final int width, final int height) {
        if (ImGui.sliderFloat(label, value, minValue, maxValue, format, flags)) {
            changed = true;
        }
    }

    /**
     * Fetch the current value of the slider.
     *
     * @return The current slider value.
     * @see #checkResult() To see if this value has changed.
     */
    public float getValue() {
        return value[0];
    }

    /**
     * Set the new value of the slider. It will be clamped to between the min and max values.
     *
     * @param newValue The new value to use.
     */
    public void setValue(float newValue) {
        value[0] = Math.max(Math.min(newValue, maxValue), minValue);
    }
}
