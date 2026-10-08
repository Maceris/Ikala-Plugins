package com.ikalagaming.graphics.frontend.gui.data;

/** Arguments for cleaning up .ini settings, see IkGuiInternal.cleanupIniSettings(). */
public class SettingsCleanupArgs {
    /**
     * Set to restrict cleanup to a given .ini type, e.g. the hash of "Window". Otherwise every type
     * supporting cleanup is affected.
     */
    public int typeHashFilter = 0;

    /** Enable to discard entries older than this many months. */
    public int discardOlderThanMonths = 0;

    /** Enable to discard entries missing a date. */
    public boolean discardWhenMissingDate = false;

    /**
     * Enable to discard all entries. Same as calling clearIniSettings(), except it may be filtered.
     */
    public boolean discardAll = false;

    /** Enable to write the current session date to all supporting entries. */
    public boolean setCurrentSessionDateToAll = false;

    /** Enable to write the current session date to all supporting entries missing a date. */
    public boolean setCurrentSessionDateWhenMissingDate = false;

    /** Internal: the computed cutoff date as YYYYMMDD, entries before this are discarded. */
    public int discardOlderThanDate = 0;
}
