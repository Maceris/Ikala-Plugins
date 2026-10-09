package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.callback.*;
import com.ikalagaming.graphics.gui.util.Hash;

import lombok.NonNull;

/**
 * Handles one type of entry in the .ini file, e.g. "[Window][Name]". Any of the functions may be
 * null, except the read open/line functions if the type is to be loaded, and the write all
 * function.
 */
public class SettingsHandler {
    /** Short description stored in .ini file. Disallowed characters: '[' ']'. */
    public final String typeName;

    /** The hash of type name. */
    public final int typeHash;

    /** Clear all settings data. */
    public SettingsClearAllFunction clearAllFunction;

    /** Read: Called before reading (in registration order). */
    public SettingsReadInitFunction readInitFunction;

    /** Read: Called when entering into a new ini entry e.g. "[Window][Name]". */
    public SettingsReadOpenFunction readOpenFunction;

    /** Read: Called for every line of text within an ini entry. */
    public SettingsReadLineFunction readLineFunction;

    /** Read: Called after reading (in registration order). */
    public SettingsApplyAllFunction applyAllFunction;

    /** Write: Output every entry into output buffer. */
    public SettingsWriteAllFunction writeAllFunction;

    /** Cleanup or patch settings, e.g. discarding old entries. */
    public SettingsCleanupFunction cleanupFunction;

    public Object userData;

    /**
     * Create a handler for a type of .ini entry.
     *
     * @param typeName The type name, which must not contain '[' or ']'.
     */
    public SettingsHandler(@NonNull String typeName) {
        this.typeName = typeName;
        typeHash = Hash.getID(typeName);
    }
}
