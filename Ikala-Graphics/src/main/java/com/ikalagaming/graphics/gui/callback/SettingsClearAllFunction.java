package com.ikalagaming.graphics.gui.callback;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.SettingsHandler;

import lombok.NonNull;

/** Used in the {@link SettingsHandler}. */
@FunctionalInterface
public interface SettingsClearAllFunction {
    /**
     * Clear all settings data.
     *
     * @param context The context.
     * @param handler The handler.
     */
    void clearAll(@NonNull Context context, @NonNull SettingsHandler handler);
}
