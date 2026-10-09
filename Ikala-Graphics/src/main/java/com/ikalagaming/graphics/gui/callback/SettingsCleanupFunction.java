package com.ikalagaming.graphics.gui.callback;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.SettingsCleanupArgs;
import com.ikalagaming.graphics.gui.data.SettingsHandler;

import lombok.NonNull;

/** Used in the {@link SettingsHandler}. */
@FunctionalInterface
public interface SettingsCleanupFunction {
    /**
     * Cleanup or patch settings.
     *
     * @param context The context.
     * @param handler The handler.
     * @param args What to clean up.
     */
    void cleanup(
            @NonNull Context context,
            @NonNull SettingsHandler handler,
            @NonNull SettingsCleanupArgs args);
}
