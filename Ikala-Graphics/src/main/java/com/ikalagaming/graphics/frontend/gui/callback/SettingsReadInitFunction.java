package com.ikalagaming.graphics.frontend.gui.callback;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.SettingsHandler;

import lombok.NonNull;

/** Used in the {@link SettingsHandler}. */
@FunctionalInterface
public interface SettingsReadInitFunction {
    /**
     * Read: Called before reading (in registration order).
     *
     * @param context The context.
     * @param handler The handler.
     */
    void readInit(@NonNull Context context, @NonNull SettingsHandler handler);
}
