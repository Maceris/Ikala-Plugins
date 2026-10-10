package com.ikalagaming.graphics.ui.automation;

import lombok.NonNull;

/** An automation step failed: what it was looking for wasn't there, or the UI didn't respond. */
public class UiAutomationException extends RuntimeException {
    /**
     * Create the exception.
     *
     * @param message What went wrong, including the step.
     */
    public UiAutomationException(@NonNull String message) {
        super(message);
    }
}
