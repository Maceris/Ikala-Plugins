package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.SettingsHandler;

import lombok.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Small bits of view state kept between runs in the GUI's ini file beside window and dock layout,
 * like where a tool's view was panned to, so remembering them never touches the files being edited.
 * Each entry has a name and holds keys with text values, written as:
 *
 * <pre>
 * [Saved][Texture graph D:/art/grass.texgraph.yml]
 * zoom=1.5
 * </pre>
 *
 * Names and keys can't hold line breaks, and keys can't hold '='. Installed before the ini file is
 * read. Render thread only.
 */
public final class SavedState {
    /** The ini section's type name. */
    static final String TYPE_NAME = "Saved";

    /** Not made. */
    private SavedState() {}

    /** Register the settings handler, once, before the ini file is read. */
    public static void install() {
        if (IkGuiInternal.findSettingsHandler(TYPE_NAME) != null) {
            return;
        }
        final SettingsHandler handler = new SettingsHandler(TYPE_NAME);
        final Map<String, Map<String, String>> entries = new LinkedHashMap<>();
        handler.userData = entries;
        handler.clearAllFunction = (context, self) -> entries.clear();
        handler.readInitFunction = (context, self) -> entries.clear();
        handler.readOpenFunction = SavedState::readOpen;
        handler.readLineFunction = SavedState::readLine;
        handler.writeAllFunction = SavedState::writeAll;
        IkGuiInternal.addSettingsHandler(handler);
    }

    /**
     * An entry's values.
     *
     * @param name The entry.
     * @return A copy of its keys and values, or null if there is no such entry or the handler isn't
     *     installed.
     */
    public static @Nullable Map<String, String> get(@NonNull String name) {
        final Map<String, Map<String, String>> entries = entries();
        final Map<String, String> entry = entries == null ? null : entries.get(clean(name));
        return entry == null ? null : new LinkedHashMap<>(entry);
    }

    /**
     * Replace an entry's values, and have the ini file written soon.
     *
     * @param name The entry.
     * @param values Its keys and values; empty to remove it.
     */
    public static void put(@NonNull String name, @NonNull Map<String, String> values) {
        final Map<String, Map<String, String>> entries = entries();
        if (entries == null) {
            return;
        }
        final String key = clean(name);
        final Map<String, String> old = entries.get(key);
        if (values.isEmpty()) {
            if (old != null) {
                entries.remove(key);
                IkGuiInternal.markIniSettingsDirty();
            }
            return;
        }
        final Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((k, v) -> copy.put(clean(k).replace("=", ""), clean(v)));
        if (!copy.equals(old)) {
            entries.put(key, copy);
            IkGuiInternal.markIniSettingsDirty();
        }
    }

    /**
     * Text without line breaks or brackets that would end an ini section's name.
     *
     * @param text The text.
     * @return It, cleaned.
     */
    private static String clean(String text) {
        return text.replace('\n', ' ').replace('\r', ' ').replace(']', ')');
    }

    /**
     * The handler's entries.
     *
     * @return The entries, or null if not installed.
     */
    @SuppressWarnings("unchecked")
    private static @Nullable Map<String, Map<String, String>> entries() {
        final SettingsHandler handler = IkGuiInternal.findSettingsHandler(TYPE_NAME);
        return handler == null ? null : (Map<String, Map<String, String>>) handler.userData;
    }

    @SuppressWarnings("unchecked")
    private static Object readOpen(
            @NonNull Context context, @NonNull SettingsHandler handler, @NonNull String name) {
        return ((Map<String, Map<String, String>>) handler.userData)
                .computeIfAbsent(name, n -> new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    private static void readLine(
            @NonNull Context context,
            @NonNull SettingsHandler handler,
            @NonNull Object entry,
            @NonNull String line) {
        final int equals = line.indexOf('=');
        if (equals > 0) {
            ((Map<String, String>) entry)
                    .put(line.substring(0, equals), line.substring(equals + 1));
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeAll(
            @NonNull Context context,
            @NonNull SettingsHandler handler,
            @NonNull StringBuilder out) {
        for (Map.Entry<String, Map<String, String>> entry :
                ((Map<String, Map<String, String>>) handler.userData).entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            out.append('[').append(TYPE_NAME).append("][").append(entry.getKey()).append("]\n");
            for (Map.Entry<String, String> value : entry.getValue().entrySet()) {
                out.append(value.getKey()).append('=').append(value.getValue()).append('\n');
            }
            out.append('\n');
        }
    }
}
