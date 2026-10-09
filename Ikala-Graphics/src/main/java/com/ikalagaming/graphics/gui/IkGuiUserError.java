package com.ikalagaming.graphics.gui;

/**
 * Thrown when incorrect API usage is detected, such as a missing end() call, and {@link
 * com.ikalagaming.graphics.gui.data.IkIO#configErrorRecoveryEnableAssert} is set. This is the
 * equivalent of a failed IM_ASSERT_USER_ERROR() in Dear ImGui.
 */
public class IkGuiUserError extends RuntimeException {
    /**
     * Create an error.
     *
     * @param message The description of the incorrect usage.
     * @param cause The cause, may be null.
     */
    public IkGuiUserError(String message, Throwable cause) {
        super(message, cause);
    }
}
