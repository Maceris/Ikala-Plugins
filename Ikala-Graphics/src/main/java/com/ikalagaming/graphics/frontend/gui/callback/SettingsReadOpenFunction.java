package com.ikalagaming.graphics.frontend.gui.callback;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.SettingsHandler;

import lombok.NonNull;

/** Used in the {@link SettingsHandler}. */
@FunctionalInterface
public interface SettingsReadOpenFunction {
    /**
     * Read: Called when entering into a new ini entry e.g. "[Window][Name]".
     *
     * @param context The context.
     * @param handler The handler.
     * @param name The entry name.
     * @return The object to store subsequent entry data in.
     */
    Object readOpen(
            @NonNull Context context, @NonNull SettingsHandler handler, @NonNull String name);
    // TODO(ches) more specific entry type
}
