package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.flags.ConditionAllowed;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Hash;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Settings, saved to and loaded from .ini files.
 *
 * <p>The .ini format is a series of entries of the form "[Type][Name]", each followed by lines of
 * data for that entry, which are parsed by the {@link SettingsHandler} registered for the type.
 */
@Slf4j
class IkGuiImplConfig {

    /** The type name for window settings in the .ini file. */
    static final String WINDOW_TYPE_NAME = "Window";

    private static final Pattern WINDOW_POS = Pattern.compile("Pos=(-?\\d+),(-?\\d+)");
    private static final Pattern WINDOW_SIZE = Pattern.compile("Size=(-?\\d+),(-?\\d+)");
    private static final Pattern WINDOW_VIEWPORT_ID =
            Pattern.compile("ViewportId=0x([0-9a-fA-F]+)");
    private static final Pattern WINDOW_VIEWPORT_POS =
            Pattern.compile("ViewportPos=(-?\\d+),(-?\\d+)");
    private static final Pattern WINDOW_COLLAPSED = Pattern.compile("Collapsed=(-?\\d+)");
    private static final Pattern WINDOW_IS_CHILD = Pattern.compile("IsChild=(-?\\d+)");
    private static final Pattern WINDOW_LAST_USED = Pattern.compile("LastUsed=(\\d+)");
    private static final Pattern WINDOW_DOCK_ID =
            Pattern.compile("DockId=0x([0-9a-fA-F]+)(?:,(-?\\d+))?");
    private static final Pattern WINDOW_CLASS_ID = Pattern.compile("ClassId=0x([0-9a-fA-F]+)");

    static Context context;

    /**
     * Register a handler for a type of .ini entry. There can only be one handler per type name.
     *
     * @param handler The handler.
     */
    static void addSettingsHandler(@NonNull SettingsHandler handler) {
        if (findSettingsHandler(handler.typeName) != null) {
            IkGuiImplDebugTools.reportError(
                    log, "A settings handler for type {} already exists", handler.typeName);
            return;
        }
        context.settingsHandlers.add(handler);
    }

    /**
     * Clean up or patch settings, e.g. discarding entries that have not been used recently.
     *
     * @param args What to clean up.
     */
    static void cleanupIniSettings(@NonNull SettingsCleanupArgs args) {
        if (args.discardAll) {
            for (SettingsHandler handler : context.settingsHandlers) {
                if ((args.typeHashFilter == 0 || handler.typeHash == args.typeHashFilter)
                        && handler.clearAllFunction != null) {
                    handler.clearAllFunction.clearAll(context, handler);
                }
            }
        }
        final int sessionDate = context.platformIO.platformSessionDate;
        if (sessionDate != 0 && args.discardOlderThanMonths != 0) {
            args.discardOlderThanDate = subtractMonths(sessionDate, args.discardOlderThanMonths);
        }
        for (SettingsHandler handler : context.settingsHandlers) {
            if ((args.typeHashFilter == 0 || handler.typeHash == args.typeHashFilter)
                    && handler.cleanupFunction != null) {
                handler.cleanupFunction.cleanup(context, handler, args);
            }
        }
    }

    /** Clear all settings (windows, tables, docking, etc.). */
    static void clearIniSettings() {
        context.settingsIniData.setLength(0);
        for (SettingsHandler handler : context.settingsHandlers) {
            if (handler.clearAllFunction != null) {
                handler.clearAllFunction.clearAll(context, handler);
            }
        }
    }

    /**
     * Clear the settings for a window, reverting it to its initial state. This includes enabling
     * the {@link com.ikalagaming.graphics.gui.enums.Condition#FIRST_USE_EVER} and ONCE conditions
     * again.
     *
     * @param name The window name.
     */
    static void clearWindowSettings(@NonNull String name) {
        final Window window = IkGuiInternal.findWindowByName(name);
        if (window != null) {
            window.flags |= WindowFlags.NO_SAVED_SETTINGS;
            initOrLoadWindowSettings(window, null);
            if (window.dockID != 0) {
                IkGuiInternal.dockContextProcessUndockWindow(window, true);
            }
        }
        final WindowSettings settings =
                window != null
                        ? findWindowSettingsByWindow(window)
                        : findWindowSettingsByID(Hash.getID(name));
        if (settings != null) {
            settings.wantDelete = true;
        }
    }

    /**
     * Create settings for a window, and add them to the context.
     *
     * @param name The window name.
     * @return The new settings.
     */
    static WindowSettings createNewWindowSettings(@NonNull String name) {
        // Preserve the full string when debugging .ini settings, to make inspection easier
        String storedName = name;
        if (!context.io.configDebugIniSettings) {
            final int idStart = name.indexOf("###");
            if (idStart >= 0) {
                storedName = name.substring(idStart);
            }
        }
        final WindowSettings settings = new WindowSettings(storedName);
        settings.id = Hash.getID(storedName);
        context.settingsWindows.add(settings);
        return settings;
    }

    /**
     * Find a settings handler by type name.
     *
     * @param typeName The type name.
     * @return The handler, or null if there is none for that type.
     */
    static SettingsHandler findSettingsHandler(@NonNull String typeName) {
        final int typeHash = Hash.getID(typeName);
        for (SettingsHandler handler : context.settingsHandlers) {
            if (handler.typeHash == typeHash) {
                return handler;
            }
        }
        return null;
    }

    /**
     * Find window settings by window ID. There is no way to find them by name because the docking
     * system doesn't always hold on to names.
     *
     * @param id The window ID.
     * @return The settings, or null if there are none.
     */
    static WindowSettings findWindowSettingsByID(int id) {
        for (WindowSettings settings : context.settingsWindows) {
            if (settings.id == id && !settings.wantDelete) {
                return settings;
            }
        }
        return null;
    }

    /**
     * Find the settings for a window, which is faster than searching by ID if the window already
     * knows its settings.
     *
     * @param window The window.
     * @return The settings, or null if there are none.
     */
    static WindowSettings findWindowSettingsByWindow(@NonNull Window window) {
        if (window.settings != null) {
            return window.settings;
        }
        return findWindowSettingsByID(window.id);
    }

    /**
     * Set up the initial window state, then apply any settings. Sets the ONCE, APPEARING, and
     * FIRST_USE_EVER conditions, FIRST_USE_EVER is cleared again if there are settings.
     *
     * @param window The window.
     * @param settings The settings, which may be null.
     */
    static void initOrLoadWindowSettings(@NonNull Window window, WindowSettings settings) {
        // Initial window state with a default position. Use setNextWindowPos() with the
        // appropriate condition flag to change the initial position of a window.
        final Viewport mainViewport = IkGuiImplViewports.getMainViewport();
        window.position.set(mainViewport.position.x + 60, mainViewport.position.y + 60);
        window.viewportPosition.set(mainViewport.position);
        window.size.set(0, 0);
        window.sizeFull.set(0, 0);
        window.setConditionAllowFlags(ConditionAllowed.ALL, true);
        applyWindowSettings(window, settings);
        if (settings != null) {
            settings.lastUsedDate = context.sessionDate;
        }
    }

    /**
     * Apply settings to a window, and set up auto-fitting.
     *
     * @param window The window.
     * @param settings The settings, which may be null.
     */
    static void applyWindowSettings(@NonNull Window window, WindowSettings settings) {
        if (settings != null) {
            // Positions are stored relative to the viewport
            window.viewportPosition.set(IkGuiImplViewports.getMainViewport().position);
            if (settings.viewportID != 0) {
                window.viewportID = settings.viewportID;
                window.viewportPosition.set(
                        settings.viewportPosition.x, settings.viewportPosition.y);
            }
            window.position.set(
                    (float) Math.floor(settings.position.x + window.viewportPosition.x),
                    (float) Math.floor(settings.position.y + window.viewportPosition.y));
            if (settings.size.x > 0 && settings.size.y > 0) {
                window.size.set(settings.size.x, settings.size.y);
                window.sizeFull.set(settings.size.x, settings.size.y);
                window.autoFitFramesX.set(0);
                window.autoFitFramesY.set(0);
            }
            window.collapsed = settings.collapsed;
            window.dockID = settings.dockID;
            window.dockOrder = (short) settings.dockOrder;
            window.setConditionAllowFlags(ConditionAllowed.FIRST_USE_EVER, false);
        }

        // So the first call to calculate the window content sizes doesn't return crazy values
        window.cursorStartPosition.set(window.position);
        window.cursorMaxPosition.set(window.position);
        window.cursorIdealMaxPosition.set(window.position);
        if ((window.flags & WindowFlags.ALWAYS_AUTO_RESIZE) != 0) {
            window.autoFitFramesX.set(2);
            window.autoFitFramesY.set(2);
            window.autoFitOnlyGrows = false;
        } else {
            window.autoFitFramesX.set(window.size.x <= 0.0f ? 2 : 0);
            window.autoFitFramesY.set(window.size.y <= 0.0f ? 2 : 0);
            window.autoFitOnlyGrows =
                    window.autoFitFramesX.get() > 0 || window.autoFitFramesY.get() > 0;
        }
    }

    /**
     * Load settings from a file. Call after createContext() and before the first newFrame().
     * newFrame() automatically calls this with io.iniFilename if settings were not already loaded.
     * Missing files are silently ignored.
     *
     * @param filename The path to the .ini file.
     */
    public static void loadIniSettingsFromDisk(@NonNull String filename) {
        final String data;
        try {
            data = Files.readString(Path.of(filename), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return;
        } catch (IOException e) {
            log.warn("Failed to read settings from {}", filename, e);
            return;
        }
        if (!data.isEmpty()) {
            loadIniSettingsFromMemory(data);
        }
    }

    /**
     * Load settings from .ini data, e.g. from your own data source. Call after createContext() and
     * before the first newFrame(). Parsing is cheap and silently ignores anything it doesn't
     * recognize.
     *
     * @param data The .ini data.
     */
    public static void loadIniSettingsFromMemory(@NonNull String data) {
        context.settingsIniData.setLength(0);
        context.settingsIniData.append(data);

        // Call pre-read handlers. Some types will clear their data (e.g. dock information), some
        // allow merging/overriding (windows).
        for (SettingsHandler handler : context.settingsHandlers) {
            if (handler.readInitFunction != null) {
                handler.readInitFunction.readInit(context, handler);
            }
        }

        Object entryData = null;
        SettingsHandler entryHandler = null;
        for (String line : data.split("[\r\n]+")) {
            if (line.isEmpty() || line.charAt(0) == ';') {
                continue;
            }
            if (line.charAt(0) == '[' && line.charAt(line.length() - 1) == ']') {
                // Parse "[Type][Name]". The name can itself contain [] characters.
                final int typeEnd = line.indexOf(']', 1);
                final int nameStart =
                        typeEnd >= 0 && typeEnd < line.length() - 1
                                ? line.indexOf('[', typeEnd + 1)
                                : -1;
                if (typeEnd < 0 || nameStart < 0) {
                    entryHandler = null;
                    entryData = null;
                    continue;
                }
                final String type = line.substring(1, typeEnd);
                final String name = line.substring(nameStart + 1, line.length() - 1);
                entryHandler = findSettingsHandler(type);
                entryData =
                        entryHandler != null && entryHandler.readOpenFunction != null
                                ? entryHandler.readOpenFunction.readOpen(
                                        context, entryHandler, name)
                                : null;
            } else if (entryHandler != null
                    && entryData != null
                    && entryHandler.readLineFunction != null) {
                // Let the type handler parse the line
                entryHandler.readLineFunction.readLine(context, entryHandler, entryData, line);
            }
        }
        context.settingsLoaded = true;

        // Call post-read handlers
        if (context.io.configIniSettingsAutoDiscardMonths > 0) {
            final SettingsCleanupArgs cleanupArgs = new SettingsCleanupArgs();
            cleanupArgs.discardOlderThanMonths = context.io.configIniSettingsAutoDiscardMonths;
            cleanupIniSettings(cleanupArgs);
        }
        for (SettingsHandler handler : context.settingsHandlers) {
            if (handler.applyAllFunction != null) {
                handler.applyAllFunction.applyAll(context, handler);
            }
        }
    }

    /**
     * Remove a settings handler.
     *
     * @param typeName The type name of the handler.
     */
    static void removeSettingsHandler(@NonNull String typeName) {
        final SettingsHandler handler = findSettingsHandler(typeName);
        if (handler != null) {
            context.settingsHandlers.remove(handler);
        }
    }

    /**
     * Save settings to a file. This is automatically called (if io.iniFilename is not null) a few
     * seconds after any modification that should be reflected in the .ini file, and by
     * destroyContext().
     *
     * @param filename The path to the .ini file.
     */
    public static void saveIniSettingsToDisk(@NonNull String filename) {
        context.settingsDirtyTimer = 0;
        final String data = saveIniSettingsToMemory();
        try {
            Files.writeString(Path.of(filename), data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to save settings to {}", filename, e);
        }
    }

    /**
     * Save settings to a string, which you can save by your own means. Call this when
     * io.wantSaveIniSettings is set, then clear io.wantSaveIniSettings yourself.
     *
     * @return The .ini data.
     */
    public static String saveIniSettingsToMemory() {
        context.settingsDirtyTimer = 0;
        context.settingsIniData.setLength(0);
        for (SettingsHandler handler : context.settingsHandlers) {
            if (handler.writeAllFunction != null) {
                handler.writeAllFunction.writeAll(context, handler, context.settingsIniData);
            }
        }
        return context.settingsIniData.toString();
    }

    /**
     * Subtract months from a YYYYMMDD date, keeping the day.
     *
     * @param date The date as YYYYMMDD.
     * @param months The number of months to subtract.
     * @return The resulting date as YYYYMMDD.
     */
    static int subtractMonths(int date, int months) {
        final int day = date % 100;
        final int totalMonths = (date / 10_000) * 12 + (date / 100) % 100 - 1 - months;
        return (totalMonths / 12) * 10_000 + (totalMonths % 12 + 1) * 100 + day;
    }

    /**
     * Load settings on the first frame (if not explicitly loaded before), and save settings with a
     * delay after the last modification so we don't spam the disk. Called by newFrame().
     */
    static void updateSettings() {
        if (!context.settingsLoaded) {
            if (!context.settingsWindows.isEmpty()) {
                log.warn("Window settings exist before settings were loaded");
            }
            if (context.io.iniFilename != null) {
                loadIniSettingsFromDisk(context.io.iniFilename);
            }
            context.settingsLoaded = true;
        }

        if (context.settingsDirtyTimer > 0) {
            context.settingsDirtyTimer -= context.io.deltaTime;
            if (context.settingsDirtyTimer <= 0) {
                if (context.io.iniFilename != null) {
                    saveIniSettingsToDisk(context.io.iniFilename);
                } else {
                    // Let the user know they can call saveIniSettingsToMemory(), they need to
                    // clear io.wantSaveIniSettings themselves
                    context.io.wantSaveIniSettings = true;
                }
                context.settingsDirtyTimer = 0;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Window settings handler
    // ---------------------------------------------------------------------------------------------

    /** Register the settings handler for windows. */
    static void addWindowSettingsHandler() {
        final SettingsHandler handler = new SettingsHandler(WINDOW_TYPE_NAME);
        handler.clearAllFunction = IkGuiImplConfig::windowSettingsHandlerClearAll;
        handler.cleanupFunction = IkGuiImplConfig::windowSettingsHandlerCleanup;
        handler.readOpenFunction = IkGuiImplConfig::windowSettingsHandlerReadOpen;
        handler.readLineFunction = IkGuiImplConfig::windowSettingsHandlerReadLine;
        handler.applyAllFunction = IkGuiImplConfig::windowSettingsHandlerApplyAll;
        handler.writeAllFunction = IkGuiImplConfig::windowSettingsHandlerWriteAll;
        addSettingsHandler(handler);
    }

    private static void windowSettingsHandlerClearAll(
            @NonNull Context ctx, @NonNull SettingsHandler handler) {
        for (Window window : ctx.windowDisplayOrder) {
            window.settings = null;
        }
        ctx.settingsWindows.clear();
    }

    private static void windowSettingsHandlerCleanup(
            @NonNull Context ctx,
            @NonNull SettingsHandler handler,
            @NonNull SettingsCleanupArgs args) {
        for (WindowSettings settings : ctx.settingsWindows) {
            final boolean isValid = settings.lastUsedDate != 0;
            if ((args.discardOlderThanDate != 0
                            && settings.lastUsedDate < args.discardOlderThanDate)
                    || (args.discardWhenMissingDate && !isValid)) {
                settings.wantDelete = true;
            }
            if (args.setCurrentSessionDateToAll
                    || (args.setCurrentSessionDateWhenMissingDate && !isValid)) {
                settings.lastUsedDate = ctx.sessionDate;
            }
        }
    }

    private static Object windowSettingsHandlerReadOpen(
            @NonNull Context ctx, @NonNull SettingsHandler handler, @NonNull String name) {
        final int id = Hash.getID(name);
        WindowSettings settings = findWindowSettingsByID(id);
        if (settings != null) {
            // Clear the existing entry if recycling it
            settings.clear();
        } else {
            settings = createNewWindowSettings(name);
        }
        settings.id = id;
        settings.wantApply = true;
        return settings;
    }

    private static void windowSettingsHandlerReadLine(
            @NonNull Context ctx,
            @NonNull SettingsHandler handler,
            @NonNull Object entry,
            @NonNull String line) {
        final WindowSettings settings = (WindowSettings) entry;
        try {
            Matcher m;
            if ((m = WINDOW_POS.matcher(line)).matches()) {
                settings.position.set(parseInt(m, 1), parseInt(m, 2));
            } else if ((m = WINDOW_SIZE.matcher(line)).matches()) {
                settings.size.set(parseInt(m, 1), parseInt(m, 2));
            } else if ((m = WINDOW_VIEWPORT_ID.matcher(line)).matches()) {
                settings.viewportID = parseHex(m, 1);
            } else if ((m = WINDOW_VIEWPORT_POS.matcher(line)).matches()) {
                settings.viewportPosition.set(parseInt(m, 1), parseInt(m, 2));
            } else if ((m = WINDOW_COLLAPSED.matcher(line)).matches()) {
                settings.collapsed = parseInt(m, 1) != 0;
            } else if ((m = WINDOW_IS_CHILD.matcher(line)).matches()) {
                settings.isChild = parseInt(m, 1) != 0;
            } else if ((m = WINDOW_LAST_USED.matcher(line)).matches()) {
                settings.lastUsedDate = parseInt(m, 1);
            } else if ((m = WINDOW_DOCK_ID.matcher(line)).matches()) {
                settings.dockID = parseHex(m, 1);
                settings.dockOrder = m.group(2) != null ? parseInt(m, 2) : -1;
            } else if ((m = WINDOW_CLASS_ID.matcher(line)).matches()) {
                settings.classID = parseHex(m, 1);
            }
        } catch (NumberFormatException e) {
            log.debug("Ignoring invalid window settings line '{}'", line);
        }
    }

    private static int parseInt(@NonNull Matcher matcher, int group) {
        return Integer.parseInt(matcher.group(group));
    }

    private static int parseHex(@NonNull Matcher matcher, int group) {
        return Integer.parseUnsignedInt(matcher.group(group), 16);
    }

    /** Apply loaded settings to existing windows, if any. */
    private static void windowSettingsHandlerApplyAll(
            @NonNull Context ctx, @NonNull SettingsHandler handler) {
        for (WindowSettings settings : ctx.settingsWindows) {
            if (settings.wantApply) {
                final Window window = IkGuiInternal.findWindowByID(settings.id);
                if (window != null) {
                    applyWindowSettings(window, settings);
                }
                settings.wantApply = false;
            }
        }
    }

    private static void windowSettingsHandlerWriteAll(
            @NonNull Context ctx, @NonNull SettingsHandler handler, @NonNull StringBuilder out) {
        // Gather data from windows that were active during this session. If a window wasn't
        // opened in this session we preserve its settings.
        for (Window window : ctx.windowDisplayOrder) {
            if ((window.flags & WindowFlags.NO_SAVED_SETTINGS) != 0) {
                continue;
            }

            WindowSettings settings = findWindowSettingsByWindow(window);
            if (settings == null) {
                settings = createNewWindowSettings(window.name);
                window.settings = settings;
            }
            if (settings.id != window.id) {
                IkGuiImplDebugTools.reportError(
                        log, "Window settings ID mismatch for {}", window.name);
            }
            // Positions are stored relative to the viewport
            settings.position.set(
                    (int) (window.position.x - window.viewportPosition.x),
                    (int) (window.position.y - window.viewportPosition.y));
            settings.size.set((int) window.sizeFull.x, (int) window.sizeFull.y);
            settings.viewportID = window.viewportID;
            settings.viewportPosition.set(
                    (int) window.viewportPosition.x, (int) window.viewportPosition.y);
            settings.dockID = window.dockID;
            settings.classID = window.windowClass.classID;
            settings.dockOrder = window.dockOrder;
            settings.collapsed = window.collapsed;
            // We can't rely on the child window flag, since docked windows have it set
            settings.isChild = window.rootWindow != window;
            settings.wantDelete = false;
            settings.lastUsedDate = ctx.sessionDate;
        }

        // Write to the text buffer
        final int defaultViewportID = Viewport.DEFAULT_ID;
        for (WindowSettings settings : ctx.settingsWindows) {
            if (settings.wantDelete) {
                continue;
            }
            out.append('[').append(handler.typeName).append("][").append(settings.name);
            out.append("]\n");
            if (settings.isChild) {
                out.append("IsChild=1\n");
                out.append("Size=").append(settings.size.x).append(',').append(settings.size.y);
                out.append('\n');
            } else {
                if (settings.viewportID != 0 && settings.viewportID != defaultViewportID) {
                    out.append("ViewportPos=").append(settings.viewportPosition.x).append(',');
                    out.append(settings.viewportPosition.y).append('\n');
                    out.append(
                            String.format(Locale.ROOT, "ViewportId=0x%08X\n", settings.viewportID));
                }
                if (settings.position.x != 0
                        || settings.position.y != 0
                        || settings.viewportID == defaultViewportID) {
                    out.append("Pos=").append(settings.position.x).append(',');
                    out.append(settings.position.y).append('\n');
                }
                if (settings.size.x != 0 || settings.size.y != 0) {
                    out.append("Size=").append(settings.size.x).append(',');
                    out.append(settings.size.y).append('\n');
                }
                out.append("Collapsed=").append(settings.collapsed ? 1 : 0).append('\n');
                if (settings.dockID != 0) {
                    if (settings.dockOrder == -1) {
                        out.append(String.format(Locale.ROOT, "DockId=0x%08X\n", settings.dockID));
                    } else {
                        out.append(
                                String.format(
                                        Locale.ROOT,
                                        "DockId=0x%08X,%d\n",
                                        settings.dockID,
                                        settings.dockOrder));
                    }
                    if (settings.classID != 0) {
                        out.append(
                                String.format(Locale.ROOT, "ClassId=0x%08X\n", settings.classID));
                    }
                }
            }
            if (ctx.io.configIniSettingsSaveLastUsedDate && settings.lastUsedDate != 0) {
                out.append(String.format(Locale.ROOT, "LastUsed=%08d\n", settings.lastUsedDate));
            }
            out.append('\n');
        }
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplConfig() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
