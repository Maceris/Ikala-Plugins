package com.ikalagaming.graphics.frontend.gui.callback;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.SettingsHandler;

import lombok.NonNull;

/** Used in the {@link SettingsHandler}. */
@FunctionalInterface
public interface SettingsReadLineFunction {
    /**
     * Read: Called for every line of text within an ini entry.
     *
     * @param context The context.
     * @param handler The handler.
     * @param entry The settings data block to populate.
     * @param line The line being read.
     */
    void readLine(
            @NonNull Context context,
            @NonNull SettingsHandler handler,
            @NonNull Object entry,
            @NonNull String line);
    // TODO(ches) more specific entry type
}
