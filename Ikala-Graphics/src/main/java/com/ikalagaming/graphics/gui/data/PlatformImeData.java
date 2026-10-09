package com.ikalagaming.graphics.gui.data;

import org.joml.Vector2f;

/**
 * Data passed to PlatformIO.setImeDataFunction, so the platform can position an input method editor
 * (IME) for typing languages like Chinese/Japanese/Korean.
 */
public class PlatformImeData {
    /** A widget wants the IME to be visible. */
    public boolean wantVisible;

    /** A widget wants text input, not necessarily IME to be visible. */
    public boolean wantTextInput;

    /** Position of the input cursor. */
    public final Vector2f inputPosition;

    /** Line height. */
    public float inputLineHeight;

    /** ID of the platform window/viewport. */
    public int viewportID;

    public PlatformImeData() {
        inputPosition = new Vector2f();
    }

    /**
     * Copy another instance.
     *
     * @param other The data to copy.
     */
    public void set(PlatformImeData other) {
        wantVisible = other.wantVisible;
        wantTextInput = other.wantTextInput;
        inputPosition.set(other.inputPosition);
        inputLineHeight = other.inputLineHeight;
        viewportID = other.viewportID;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PlatformImeData other)) {
            return false;
        }
        return wantVisible == other.wantVisible
                && wantTextInput == other.wantTextInput
                && inputPosition.equals(other.inputPosition)
                && inputLineHeight == other.inputLineHeight
                && viewportID == other.viewportID;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                wantVisible, wantTextInput, inputPosition, inputLineHeight, viewportID);
    }
}
