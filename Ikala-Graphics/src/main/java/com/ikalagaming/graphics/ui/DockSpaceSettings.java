package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.SettingsHandler;

import lombok.NonNull;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Which panels each {@link DockSpace} has placed, kept in the GUI's ini file beside the layout. A
 * panel its layout names but that is missing here was added to the layout after the user's layout
 * was saved, so the dock space docks it where the layout says, once, instead of leaving it
 * floating. Entries look like {@code [DockSpace][B85D8FE5]} followed by {@code Placed=files} lines.
 */
public final class DockSpaceSettings {
    /** The ini entry type. */
    static final String TYPE_NAME = "DockSpace";

    /** What starts each placed panel's line. */
    private static final String PLACED = "Placed=";

    /** Static methods only. */
    private DockSpaceSettings() {}

    /**
     * Add the handler to the current IkGui context. Call it once, right after the context is made
     * and before its ini file is read, or saved entries are skipped.
     */
    public static void install() {
        if (IkGuiInternal.findSettingsHandler(TYPE_NAME) != null) {
            return;
        }
        final SettingsHandler handler = new SettingsHandler(TYPE_NAME);
        final Map<Integer, Set<String>> placed = new HashMap<>();
        handler.userData = placed;
        handler.clearAllFunction = (context, self) -> placed.clear();
        handler.readInitFunction = (context, self) -> placed.clear();
        handler.readOpenFunction = DockSpaceSettings::readOpen;
        handler.readLineFunction = DockSpaceSettings::readLine;
        handler.writeAllFunction = DockSpaceSettings::writeAll;
        IkGuiInternal.addSettingsHandler(handler);
    }

    /**
     * The panels a dock space has placed, which the caller may add to.
     *
     * @param dockID The dock space's ID.
     * @return The panel IDs, or null if the handler isn't installed.
     */
    static @Nullable Set<String> placed(int dockID) {
        final Map<Integer, Set<String>> all = all();
        return all == null ? null : all.computeIfAbsent(dockID, id -> new LinkedHashSet<>());
    }

    /**
     * Every dock space's placed panels.
     *
     * @return The map, or null if the handler isn't installed.
     */
    @SuppressWarnings("unchecked")
    private static @Nullable Map<Integer, Set<String>> all() {
        final SettingsHandler handler = IkGuiInternal.findSettingsHandler(TYPE_NAME);
        return handler == null ? null : (Map<Integer, Set<String>>) handler.userData;
    }

    /**
     * Start reading an entry.
     *
     * @param context The context.
     * @param handler The handler.
     * @param name The dock space's ID, in hex.
     * @return The set to read panels into, or a throwaway one for a name that isn't an ID.
     */
    @SuppressWarnings("unchecked")
    private static Object readOpen(
            @NonNull Context context, @NonNull SettingsHandler handler, @NonNull String name) {
        try {
            final int dockID = Integer.parseUnsignedInt(name.trim(), 16);
            return ((Map<Integer, Set<String>>) handler.userData)
                    .computeIfAbsent(dockID, id -> new LinkedHashSet<>());
        } catch (NumberFormatException e) {
            return new LinkedHashSet<String>();
        }
    }

    /**
     * Read one line of an entry.
     *
     * @param context The context.
     * @param handler The handler.
     * @param entry The set from {@link #readOpen}.
     * @param line The line.
     */
    @SuppressWarnings("unchecked")
    private static void readLine(
            @NonNull Context context,
            @NonNull SettingsHandler handler,
            @NonNull Object entry,
            @NonNull String line) {
        if (line.startsWith(PLACED) && line.length() > PLACED.length()) {
            ((Set<String>) entry).add(line.substring(PLACED.length()));
        }
    }

    /**
     * Write every dock space's entry.
     *
     * @param context The context.
     * @param handler The handler.
     * @param out Where to write.
     */
    @SuppressWarnings("unchecked")
    private static void writeAll(
            @NonNull Context context,
            @NonNull SettingsHandler handler,
            @NonNull StringBuilder out) {
        for (Map.Entry<Integer, Set<String>> dock :
                ((Map<Integer, Set<String>>) handler.userData).entrySet()) {
            if (dock.getValue().isEmpty()) {
                continue;
            }
            out.append('[')
                    .append(TYPE_NAME)
                    .append("][")
                    .append(String.format("%08X", dock.getKey()))
                    .append("]\n");
            for (String panel : dock.getValue()) {
                out.append(PLACED).append(panel).append('\n');
            }
            out.append('\n');
        }
    }
}
