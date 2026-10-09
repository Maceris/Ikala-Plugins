package com.ikalagaming.graphics.gui.callback;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.SettingsHandler;

import lombok.NonNull;

/** Used in the {@link SettingsHandler}. */
@FunctionalInterface
public interface SettingsWriteAllFunction {
    /**
     * Write: Output every entry into the out buffer.
     *
     * @param context The context.
     * @param handler The handler.
     * @param outBuffer The output buffer.
     */
    void writeAll(
            @NonNull Context context,
            @NonNull SettingsHandler handler,
            @NonNull StringBuilder outBuffer);
}
