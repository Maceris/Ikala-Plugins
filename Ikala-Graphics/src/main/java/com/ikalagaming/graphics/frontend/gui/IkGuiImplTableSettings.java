package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.SortDirection;
import com.ikalagaming.graphics.frontend.gui.flags.TableColumnFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableFlags;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Table settings, saved to and loaded from the .ini file.
 *
 * <ol>
 *   <li>The settings handler reads the .ini file into table settings.
 *   <li>tableLoadSettings(): when a table is created, bind the table to its settings and load them.
 *   <li>tableSaveSettings(): when table properties are modified, save the table data into its
 *       settings, and mark the .ini as dirty.
 *   <li>The settings handler writes the table settings into the .ini file.
 * </ol>
 */
@Slf4j
class IkGuiImplTableSettings {
    /** The type name for table settings in the .ini file. */
    static final String TABLE_TYPE_NAME = "Table";

    private static final Pattern TABLE_NAME = Pattern.compile("0x([0-9a-fA-F]+),(\\d+)");
    private static final Pattern REF_SCALE = Pattern.compile("RefScale=([-+0-9.eE]+)");
    private static final Pattern LAST_USED = Pattern.compile("LastUsed=(\\d+)");
    private static final Pattern COLUMN = Pattern.compile("Column\\s+(\\d+)(.*)");
    private static final Pattern WIDTH = Pattern.compile("Width=(-?\\d+)");
    private static final Pattern WEIGHT = Pattern.compile("Weight=([-+0-9.eE]+)");
    private static final Pattern VISIBLE = Pattern.compile("Visible=(\\d+)");
    private static final Pattern ORDER = Pattern.compile("Order=(-?\\d+)");
    private static final Pattern SORT = Pattern.compile("Sort=(\\d+)([v^])");
    private static final Pattern ID = Pattern.compile("(?<![A-Za-z])ID=0x([0-9a-fA-F]+)");

    static Context context;

    /**
     * Create settings for a table, recycling existing settings with the same ID if they are big
     * enough.
     *
     * @param id The table ID.
     * @param columnsCount The number of columns.
     * @return The settings.
     */
    static TableSettings tableSettingsCreate(int id, int columnsCount) {
        final TableSettings existing = tableSettingsFindByID(id);
        if (existing != null) {
            if (existing.columnsCountMax >= columnsCount) {
                // Recycle
                existing.init(id, columnsCount, existing.columnsCountMax);
                return existing;
            }
            // Invalidate the storage, we won't fit because of a count change
            existing.id = 0;
        }
        final TableSettings settings = new TableSettings(id, columnsCount);
        context.settingsTables.add(settings);
        return settings;
    }

    /**
     * Find existing settings.
     *
     * @param id The table ID.
     * @return The settings, or null if there are none.
     */
    static TableSettings tableSettingsFindByID(int id) {
        for (TableSettings settings : context.settingsTables) {
            if (settings.id == id) {
                return settings;
            }
        }
        return null;
    }

    /**
     * Get the settings bound to a table.
     *
     * @param table The table.
     * @return The settings, or null if there are none.
     */
    static TableSettings tableGetBoundSettings(@NonNull Table table) {
        final TableSettings settings = table.settings;
        if (settings != null && settings.id != table.id) {
            IkGuiImplDebugTools.reportError(log, "Table settings ID mismatch");
            return null;
        }
        return settings;
    }

    /**
     * Restore the initial state of a table (with or without saved settings).
     *
     * @param table The table.
     */
    static void tableResetSettings(@NonNull Table table) {
        table.isInitializing = true;
        table.isSettingsDirty = true;
        table.isResetAllRequest = false;
        // Don't reload from the .ini file
        table.isSettingsRequestLoad = false;
        // Mark as nothing loaded so our initialized data becomes authoritative
        table.settingsLoadedFlags = TableFlags.NONE;
    }

    /**
     * Save the table data into its settings, and mark the .ini as dirty.
     *
     * @param table The table.
     */
    static void tableSaveSettings(@NonNull Table table) {
        table.isSettingsDirty = false;
        if ((table.flags & TableFlags.NO_SAVED_SETTINGS) != 0) {
            return;
        }

        // Bind or create the settings data
        TableSettings settings = tableGetBoundSettings(table);
        if (settings != null && table.columnsCount > settings.columnsCountMax) {
            // Invalidate the storage, we won't fit because of a count change
            settings.id = 0;
            settings = null;
        }
        if (settings == null) {
            settings = tableSettingsCreate(table.id, table.columnsCount);
            table.settings = settings;
        }
        settings.columnsCount = table.columnsCount;
        settings.lastUsedDate = context.sessionDate;

        // Serialize the table/columns into the settings/column settings
        boolean saveRefScale = false;
        settings.saveFlags = TableFlags.NONE;
        for (int n = 0; n < table.columnsCount; ++n) {
            final TableColumn column = table.columns[n];
            final TableColumnSettings columnSettings = settings.columns[n];
            final boolean isStretch = (column.flags & TableColumnFlags.WIDTH_STRETCH) != 0;
            final float widthOrWeight = isStretch ? column.stretchWeight : column.widthRequest;
            columnSettings.id = column.id;
            columnSettings.widthOrWeight = widthOrWeight;
            columnSettings.index = n;
            columnSettings.displayOrder = column.displayOrder;
            columnSettings.sortOrder = column.sortOrder;
            columnSettings.sortDirection = column.sortDirection;
            columnSettings.isEnabled = column.isUserEnabled ? 1 : 0;
            columnSettings.isStretch = isStretch;
            if (!isStretch) {
                saveRefScale = true;
            }

            // We skip saving some data in the .ini file when they are unnecessary to restore our
            // state. Fixed widths where the initial width was derived from auto-fit will always be
            // saved, since the initial width/weight will be 0.
            if (widthOrWeight != column.initStretchWeightOrWidth) {
                settings.saveFlags |= TableFlags.RESIZABLE;
            }
            if (column.displayOrder != n) {
                settings.saveFlags |= TableFlags.REORDERABLE;
            }
            if (column.sortOrder != -1) {
                // Because saving the sort order itself is gated, make sure every column is saved
                settings.saveFlags |= TableFlags.SORTABLE | TableFlags.REORDERABLE;
            }
            if (column.isUserEnabled != ((column.flags & TableColumnFlags.DEFAULT_HIDE) == 0)) {
                settings.saveFlags |= TableFlags.HIDEABLE;
            }
        }
        settings.saveFlags &= table.flags;
        settings.refScale = saveRefScale ? table.refScale : 0.0f;

        IkGuiInternal.markIniSettingsDirty();
    }

    /**
     * Bind a table to its settings. tableUpdateLayout() will then call
     * tableLoadSettingsForColumns() to apply the data.
     *
     * @param table The table.
     */
    static void tableLoadSettings(@NonNull Table table) {
        if ((table.flags & TableFlags.NO_SAVED_SETTINGS) != 0) {
            // Done
            table.isSettingsRequestLoad = false;
            return;
        }

        // Bind settings
        final TableSettings settings;
        if (table.settings == null) {
            settings = tableSettingsFindByID(table.id);
            if (settings == null) {
                return;
            }
            // Allow settings if the column count changed
            if (settings.columnsCount != table.columnsCount) {
                table.isSettingsDirty = true;
            }
            table.settings = settings;
        } else {
            settings = tableGetBoundSettings(table);
            if (settings == null) {
                return;
            }
        }

        table.settingsLoadedFlags = settings.saveFlags;
        table.refScale = settings.refScale;
        settings.lastUsedDate = context.sessionDate;
    }

    /**
     * Apply loaded settings to the columns of a table.
     *
     * @param table The table.
     */
    static void tableLoadSettingsForColumns(@NonNull Table table) {
        for (TableColumn column : table.columns) {
            column.isLoadedSettings = false;
        }
        final TableSettings settings = tableGetBoundSettings(table);
        if (settings == null) {
            return;
        }

        // This is handled in the code below
        table.settingsLoadedFlags |= TableFlags.REORDERABLE;

        // Fast path
        final TableColumnSettings[] columnSettings = settings.columns;
        int matches = 0;
        for (int n = 0; n < table.columnsCount; ++n) {
            if (n >= settings.columnsCount || columnSettings[n].id != table.columns[n].id) {
                break;
            }
            tableLoadSettingsForColumn(table.columns[n], columnSettings[n], settings.saveFlags);
            matches++;
        }
        if (matches == settings.columnsCount) {
            return;
        }
        final int settingsStart = matches;
        for (int n = settingsStart; n < settings.columnsCount; ++n) {
            columnSettings[n].isLoaded = false;
        }

        // Find matches for named columns
        for (TableColumn column : table.columns) {
            if (column.id == 0 || column.isLoadedSettings) {
                continue;
            }
            for (int n = settingsStart; n < settings.columnsCount; ++n) {
                if (columnSettings[n].id == column.id && !columnSettings[n].isLoaded) {
                    tableLoadSettingsForColumn(column, columnSettings[n], settings.saveFlags);
                    break;
                }
            }
        }

        // Remaining entries are matched sequentially
        int dstIndex = 0;
        for (int n = settingsStart; n < settings.columnsCount; ++n) {
            if (columnSettings[n].isLoaded) {
                continue;
            }
            while (dstIndex < table.columnsCount && table.columns[dstIndex].isLoadedSettings) {
                dstIndex++;
            }
            if (dstIndex >= table.columnsCount) {
                break;
            }
            tableLoadSettingsForColumn(
                    table.columns[dstIndex], columnSettings[n], settings.saveFlags);
            dstIndex++;
        }
    }

    /**
     * Apply loaded settings to a column.
     *
     * @param column The column.
     * @param columnSettings The settings.
     * @param loadFlags Which data to load, using table flags.
     */
    static void tableLoadSettingsForColumn(
            @NonNull TableColumn column,
            @NonNull TableColumnSettings columnSettings,
            int loadFlags) {
        column.isLoadedSettings = true;
        columnSettings.isLoaded = true;
        if ((loadFlags & TableFlags.RESIZABLE) != 0) {
            if (columnSettings.isStretch) {
                column.stretchWeight = columnSettings.widthOrWeight;
            } else {
                column.widthRequest = columnSettings.widthOrWeight;
            }
            column.autoFitQueue = 0;
        }
        if ((loadFlags & TableFlags.REORDERABLE) != 0) {
            column.displayOrder = columnSettings.displayOrder;
        } else {
            // Because the default depends on the previous index, we need to set that up and can't
            // rely on tableInitColumnDefaults()
            column.displayOrder = columnSettings.index;
        }
        if ((loadFlags & TableFlags.HIDEABLE) != 0 && columnSettings.isEnabled != -1) {
            column.isUserEnabled = columnSettings.isEnabled == 1;
            column.isUserEnabledNextFrame = column.isUserEnabled;
        }
        column.sortOrder = columnSettings.sortOrder;
        column.sortDirection = columnSettings.sortDirection;
    }

    // ---------------------------------------------------------------------------------------------
    // Settings handler
    // ---------------------------------------------------------------------------------------------

    /** Register the settings handler for tables. */
    static void tableSettingsAddSettingsHandler() {
        final SettingsHandler handler = new SettingsHandler(TABLE_TYPE_NAME);
        handler.clearAllFunction = IkGuiImplTableSettings::tableSettingsHandlerClearAll;
        handler.cleanupFunction = IkGuiImplTableSettings::tableSettingsHandlerCleanup;
        handler.readOpenFunction = IkGuiImplTableSettings::tableSettingsHandlerReadOpen;
        handler.readLineFunction = IkGuiImplTableSettings::tableSettingsHandlerReadLine;
        handler.applyAllFunction = IkGuiImplTableSettings::tableSettingsHandlerApplyAll;
        handler.writeAllFunction = IkGuiImplTableSettings::tableSettingsHandlerWriteAll;
        IkGuiImplConfig.addSettingsHandler(handler);
    }

    private static void tableSettingsHandlerClearAll(
            @NonNull Context ctx, @NonNull SettingsHandler handler) {
        for (Table table : ctx.tables) {
            table.settings = null;
        }
        ctx.settingsTables.clear();
    }

    private static void tableSettingsHandlerCleanup(
            @NonNull Context ctx,
            @NonNull SettingsHandler handler,
            @NonNull SettingsCleanupArgs args) {
        for (Table table : ctx.tables) {
            table.settings = null;
        }
        for (TableSettings settings : ctx.settingsTables) {
            final boolean isValid = settings.lastUsedDate != 0;
            if (args.discardOlderThanDate != 0
                    && settings.lastUsedDate < args.discardOlderThanDate) {
                settings.id = 0;
            } else if (args.discardWhenMissingDate && !isValid) {
                settings.id = 0;
            } else if (args.setCurrentSessionDateToAll
                    || (args.setCurrentSessionDateWhenMissingDate && !isValid)) {
                settings.lastUsedDate = ctx.sessionDate;
            }
        }
    }

    /** Apply to existing tables, if any. */
    private static void tableSettingsHandlerApplyAll(
            @NonNull Context ctx, @NonNull SettingsHandler handler) {
        for (Table table : ctx.tables) {
            table.isSettingsRequestLoad = true;
            table.settings = null;
        }
    }

    private static Object tableSettingsHandlerReadOpen(
            @NonNull Context ctx, @NonNull SettingsHandler handler, @NonNull String name) {
        final Matcher matcher = TABLE_NAME.matcher(name);
        if (!matcher.matches()) {
            return null;
        }
        try {
            final int id = Integer.parseUnsignedInt(matcher.group(1), 16);
            final int columnsCount = Integer.parseInt(matcher.group(2));
            if (columnsCount <= 0 || columnsCount >= IkGuiImplTables.TABLE_MAX_COLUMNS) {
                return null;
            }
            return tableSettingsCreate(id, columnsCount);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void tableSettingsHandlerReadLine(
            @NonNull Context ctx,
            @NonNull SettingsHandler handler,
            @NonNull Object entry,
            @NonNull String line) {
        // "Column 0  Width=100 Visible=1 Order=0 Sort=0v ID=0x42AD2D21"
        final TableSettings settings = (TableSettings) entry;
        try {
            Matcher matcher = REF_SCALE.matcher(line);
            if (matcher.matches()) {
                settings.refScale = Float.parseFloat(matcher.group(1));
                return;
            }
            matcher = LAST_USED.matcher(line);
            if (matcher.matches()) {
                settings.lastUsedDate = Integer.parseInt(matcher.group(1));
                return;
            }

            matcher = COLUMN.matcher(line);
            if (!matcher.matches()) {
                return;
            }
            final int columnIndex = Integer.parseInt(matcher.group(1));
            if (columnIndex < 0 || columnIndex >= settings.columnsCount) {
                return;
            }
            final String rest = matcher.group(2);
            final TableColumnSettings column = settings.columns[columnIndex];
            column.index = columnIndex;
            if ((matcher = WIDTH.matcher(rest)).find()) {
                column.widthOrWeight = Integer.parseInt(matcher.group(1));
                column.isStretch = false;
                settings.saveFlags |= TableFlags.RESIZABLE;
            }
            if ((matcher = WEIGHT.matcher(rest)).find()) {
                column.widthOrWeight = Float.parseFloat(matcher.group(1));
                column.isStretch = true;
                settings.saveFlags |= TableFlags.RESIZABLE;
            }
            if ((matcher = VISIBLE.matcher(rest)).find()) {
                column.isEnabled = Integer.parseInt(matcher.group(1));
                settings.saveFlags |= TableFlags.HIDEABLE;
            }
            if ((matcher = ORDER.matcher(rest)).find()) {
                column.displayOrder = Integer.parseInt(matcher.group(1));
                settings.saveFlags |= TableFlags.REORDERABLE;
            }
            if ((matcher = SORT.matcher(rest)).find()) {
                column.sortOrder = Integer.parseInt(matcher.group(1));
                column.sortDirection =
                        "^".equals(matcher.group(2))
                                ? SortDirection.DESCENDING
                                : SortDirection.ASCENDING;
                settings.saveFlags |= TableFlags.SORTABLE;
            }
            if ((matcher = ID.matcher(rest)).find()) {
                column.id = Integer.parseUnsignedInt(matcher.group(1), 16);
            }
        } catch (NumberFormatException e) {
            log.debug("Ignoring invalid table settings line '{}'", line);
        }
    }

    private static void tableSettingsHandlerWriteAll(
            @NonNull Context ctx, @NonNull SettingsHandler handler, @NonNull StringBuilder out) {
        for (TableSettings settings : ctx.settingsTables) {
            // Skip ditched settings
            if (settings.id == 0) {
                continue;
            }

            // tableSaveSettings() may clear some of these flags when we establish that the data
            // can be stripped (e.g. the order was unchanged). We need to save the entry even if all
            // of them are false, since this records a table with default settings.
            final boolean saveSize = (settings.saveFlags & TableFlags.RESIZABLE) != 0;
            final boolean saveVisible = (settings.saveFlags & TableFlags.HIDEABLE) != 0;
            final boolean saveOrder = (settings.saveFlags & TableFlags.REORDERABLE) != 0;
            final boolean saveSort = (settings.saveFlags & TableFlags.SORTABLE) != 0;

            out.append(
                    String.format(
                            Locale.ROOT,
                            "[%s][0x%08X,%d]\n",
                            handler.typeName,
                            settings.id,
                            settings.columnsCount));
            if (settings.refScale != 0.0f) {
                out.append("RefScale=").append(formatFloat(settings.refScale)).append('\n');
            }
            for (int columnIndex = 0; columnIndex < settings.columnsCount; ++columnIndex) {
                final TableColumnSettings column = settings.columns[columnIndex];
                final boolean saveColumn =
                        saveSize
                                || saveVisible
                                || saveOrder
                                || (saveSort && column.sortOrder != -1);
                if (!saveColumn) {
                    continue;
                }
                out.append(String.format(Locale.ROOT, "Column %-2d", columnIndex));
                if (saveSize && column.isStretch) {
                    out.append(String.format(Locale.ROOT, " Weight=%.4f", column.widthOrWeight));
                }
                if (saveSize && !column.isStretch) {
                    out.append(" Width=").append((int) column.widthOrWeight);
                }
                if (saveVisible) {
                    out.append(" Visible=").append(column.isEnabled);
                }
                if (saveOrder) {
                    out.append(" Order=").append(column.displayOrder);
                }
                if (saveSort && column.sortOrder != -1) {
                    out.append(" Sort=")
                            .append(column.sortOrder)
                            .append(column.sortDirection == SortDirection.ASCENDING ? 'v' : '^');
                }
                if (column.id != 0) {
                    out.append(String.format(Locale.ROOT, " ID=0x%08X", column.id));
                }
                out.append('\n');
            }
            if (ctx.io.configIniSettingsSaveLastUsedDate && settings.lastUsedDate != 0) {
                out.append(String.format(Locale.ROOT, "LastUsed=%08d\n", settings.lastUsedDate));
            }
            out.append('\n');
        }
    }

    /**
     * Format a float like the %g format in C, without trailing zeroes.
     *
     * @param value The value.
     * @return The formatted value.
     */
    private static String formatFloat(float value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Float.toString(value);
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplTableSettings() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
