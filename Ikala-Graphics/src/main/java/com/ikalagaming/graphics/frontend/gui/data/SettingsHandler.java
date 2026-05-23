package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.callback.*;

public class SettingsHandler {
    /** Short description stored in .ini file. Disallowed characters: '[' ']'. */
    public String typeName;

    /** The hash of type name. */
    public int typeHash;

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

    public Object userData;
}
