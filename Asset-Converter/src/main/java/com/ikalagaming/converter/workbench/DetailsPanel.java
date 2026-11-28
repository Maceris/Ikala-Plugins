package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.content.GameContent;
import com.ikalagaming.converter.content.GameValues;
import com.ikalagaming.converter.index.AssetIndex;
import com.ikalagaming.converter.index.AssetValues;
import com.ikalagaming.converter.index.EditorTypes;
import com.ikalagaming.converter.index.MaterialValues;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.flags.HoveredFlags;
import com.ikalagaming.graphics.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.util.schema.Field;
import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * The Details panel: the values of the files selected in the Assets panel, as fields made from
 * their kind's schema. Several files of a kind show each field once, a dash where they differ, and
 * an edit sets it on all of them. A value other than its default has a Reset button. Save writes
 * each changed file with only what differs from the defaults. Edits wait, unsaved, while other
 * files are selected. Render thread.
 */
@Slf4j
public final class DetailsPanel {
    /** The most IDs a reference's picker lists. */
    private static final int PICKER_ROWS = 60;

    /**
     * A file being shown or edited.
     *
     * @param file The file.
     * @param format How its values are read and written.
     * @param loaded Its values as read.
     * @param current Its values as edited, by field name; a missing one is its default.
     * @param modified The file's time when read, to read it again if it changes.
     */
    public record Edited(
            Path file,
            AssetValues format,
            Schema.Values loaded,
            Map<String, Object> current,
            long modified) {
        /**
         * Whether it has edits not saved.
         *
         * @return True if so.
         */
        public boolean dirty() {
            return !current.equals(loaded.given());
        }

        /**
         * A field's value as edited: the file's, or the default.
         *
         * @param field The field.
         * @return The value, or null.
         */
        public @Nullable Object value(Field field) {
            return current.containsKey(field.name())
                    ? current.get(field.name())
                    : field.defaultValue();
        }
    }

    /** The asset index, as of now. */
    private final Supplier<AssetIndex.Snapshot> index;

    /** The Assets panel's selection. */
    private final Supplier<List<Path>> selection;

    /** Says what happened, in the status line. */
    private final Consumer<String> report;

    /** Files read, and maybe edited, by path. */
    private final Map<Path, Edited> files = new LinkedHashMap<>();

    /** Scratch for text fields. */
    private final IkString text = new IkString(256);

    /** Scratch for the reference picker's search. */
    private final IkString pickerQuery = new IkString(128);

    /** Scratch for number lists. */
    private final float[] floats = new float[4];

    /** Scratch for a number. */
    private final double[] number = new double[1];

    /** Scratch for a whole number. */
    private final int[] whole = new int[1];

    /**
     * Make the panel's state.
     *
     * @param index The asset index, as of now.
     * @param selection The Assets panel's selection.
     * @param report Says what happened.
     */
    public DetailsPanel(
            @NonNull Supplier<AssetIndex.Snapshot> index,
            @NonNull Supplier<List<Path>> selection,
            @NonNull Consumer<String> report) {
        this.index = index;
        this.selection = selection;
        this.report = report;
    }

    /**
     * The values of a file, if its kind has a schema.
     *
     * @param entry The file's entry.
     * @param snapshot The index.
     * @return How its values are read and written, or empty.
     */
    static Optional<AssetValues> valuesOf(AssetIndex.Entry entry, AssetIndex.Snapshot snapshot) {
        if (EditorTypes.RENDER_MATERIAL.equals(entry.type())
                || "material source".equals(entry.type())) {
            return Optional.of(MaterialValues.of(entry, snapshot));
        }
        return GameContent.available() ? GameValues.of(entry, snapshot) : Optional.empty();
    }

    /**
     * A file's state, read now if it hasn't been or it changed on disk without edits here.
     *
     * @param file The file.
     * @param snapshot The index.
     * @return The state, or null if its kind has no schema or it can't be read.
     */
    @Nullable Edited edited(@NonNull Path file, AssetIndex.@NonNull Snapshot snapshot) {
        final Path normal = file.toAbsolutePath().normalize();
        final Edited known = files.get(normal);
        final long modified = modifiedTime(normal);
        if (known != null && (known.dirty() || known.modified() == modified)) {
            return known;
        }
        final AssetIndex.Entry entry = snapshot.entry(normal);
        if (entry == null) {
            return null;
        }
        final AssetValues format = valuesOf(entry, snapshot).orElse(null);
        if (format == null) {
            return null;
        }
        try {
            final Schema.Values loaded = format.read(normal);
            final Edited fresh =
                    new Edited(
                            normal, format, loaded, new LinkedHashMap<>(loaded.given()), modified);
            files.put(normal, fresh);
            return fresh;
        } catch (IOException | RuntimeException e) {
            log.debug("Can't read the values of {}: {}", normal, e.getMessage());
            return null;
        }
    }

    /**
     * A file's time, or 0 if it can't be read.
     *
     * @param file The file.
     * @return The time, in milliseconds.
     */
    private static long modifiedTime(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * The selected files with values, if they are all of one kind.
     *
     * @return The files' states, empty if none has values or they are of different kinds.
     */
    public List<Edited> shown() {
        final AssetIndex.Snapshot snapshot = index.get();
        final List<Edited> shown = new ArrayList<>();
        for (Path file : selection.get()) {
            final Edited state = edited(file, snapshot);
            if (state != null) {
                shown.add(state);
            }
        }
        if (shown.isEmpty()) {
            return shown;
        }
        final Schema schema = shown.getFirst().format().schema();
        return shown.stream().allMatch(state -> state.format().schema().equals(schema))
                ? shown
                : List.of();
    }

    /**
     * Draw the panel.
     *
     * @param frame The current frame.
     */
    public void draw(@NonNull UiFrame frame) {
        final List<Path> picked = selection.get();
        if (picked.isEmpty()) {
            IkGui.textDisabled(
                    "Select files in the Assets panel to see their values; ctrl-click or"
                            + " shift-click picks several");
            drawUnsaved(frame);
            return;
        }
        final List<Edited> shown = shown();
        if (shown.isEmpty()) {
            IkGui.textDisabled(
                    picked.size() == 1
                            ? picked.getFirst().getFileName() + " has no values to edit here"
                            : "These files have no values to edit, or are of different kinds;"
                                    + " select files of one kind");
            drawUnsaved(frame);
            return;
        }
        final Schema schema = shown.getFirst().format().schema();
        IkGui.textUnformatted(
                shown.size() == 1
                        ? schema.name() + ": " + shown.getFirst().file().getFileName()
                        : shown.size() + " " + schema.name() + " files");
        final long dirty = shown.stream().filter(Edited::dirty).count();
        IkGui.beginDisabled(dirty == 0);
        if (IkGui.button("Save")) {
            frame.fire(() -> save(shown));
        }
        IkGui.sameLine();
        if (IkGui.button("Revert")) {
            frame.fire(() -> revert(shown));
        }
        IkGui.endDisabled();
        IkGui.separator();
        for (Field field : schema.fields()) {
            drawField(field, shown);
        }
        drawProblems(shown);
        drawUnsaved(frame);
    }

    /**
     * Draw one field for the files shown, and set an edit on all of them.
     *
     * @param field The field.
     * @param shown The files.
     */
    private void drawField(Field field, List<Edited> shown) {
        final Object first = shown.getFirst().value(field);
        boolean mixed = false;
        for (Edited state : shown) {
            if (!same(state.value(field), first)) {
                mixed = true;
                break;
            }
        }
        IkGui.pushID(field.name());
        final Object[] change = {null};
        final boolean changed = drawControl(field, first, mixed, change);
        if (IkGui.isItemHovered(HoveredFlags.ALLOW_WHEN_DISABLED)) {
            IkGui.setTooltip(field.description());
        }
        if (mixed) {
            IkGui.sameLine();
            IkGui.textDisabled("-");
            if (IkGui.isItemHovered()) {
                IkGui.setTooltip("The files differ; an edit sets it on all of them");
            }
        }
        boolean differs = false;
        for (Edited state : shown) {
            differs |= !field.isDefault(state.value(field));
        }
        if (differs) {
            IkGui.sameLine();
            if (IkGui.button("Reset")) {
                for (Edited state : shown) {
                    state.current().remove(field.name());
                }
            }
            if (IkGui.isItemHovered()) {
                IkGui.setTooltip("Back to the default: " + describe(field.defaultValue()));
            }
        }
        if (changed) {
            for (Edited state : shown) {
                if (change[0] == null) {
                    state.current().remove(field.name());
                } else {
                    state.current().put(field.name(), change[0]);
                }
            }
        }
        IkGui.popID();
    }

    /**
     * Draw a field's control by its kind.
     *
     * @param field The field.
     * @param value The value shown, the first file's.
     * @param mixed Whether the files differ.
     * @param change Gets the new value, null to take the field out.
     * @return Whether it was changed.
     */
    private boolean drawControl(
            Field field, @Nullable Object value, boolean mixed, Object[] change) {
        final String label = words(field.name());
        switch (field.kind()) {
            case TEXT -> {
                text.set(mixed || value == null ? "" : value.toString());
                if (IkGui.inputTextWithHint(label, mixed ? "mixed" : "", text)) {
                    change[0] = text.get().isEmpty() ? null : text.get();
                    return true;
                }
                return false;
            }
            case REFERENCE -> {
                return drawReference(field, value, mixed, change);
            }
            case CHOICE -> {
                boolean changed = false;
                if (IkGui.beginCombo(label, mixed ? "-" : String.valueOf(value))) {
                    for (String choice : field.choices()) {
                        if (IkGui.selectable(choice, choice.equals(value))) {
                            change[0] = choice;
                            changed = true;
                        }
                    }
                    IkGui.endCombo();
                }
                return changed;
            }
            case FLAG -> {
                if (IkGui.checkbox(label, Boolean.TRUE.equals(value))) {
                    change[0] = !Boolean.TRUE.equals(value);
                    return true;
                }
                return false;
            }
            case NUMBER -> {
                final double now = value instanceof Number n ? n.doubleValue() : 0;
                if (Double.isFinite(field.min())
                        && Double.isFinite(field.max())
                        && field.max() - field.min() <= 1000) {
                    floats[0] = (float) now;
                    if (IkGui.sliderFloat(
                            label, floats, (float) field.min(), (float) field.max())) {
                        change[0] = (double) floats[0];
                        return true;
                    }
                    return false;
                }
                number[0] = now;
                if (IkGui.inputDouble(label, number)) {
                    change[0] = Math.clamp(number[0], field.min(), field.max());
                    return true;
                }
                return false;
            }
            case INTEGER -> {
                final long now = value instanceof Number n ? n.longValue() : 0;
                whole[0] = (int) Math.clamp(now, Integer.MIN_VALUE, Integer.MAX_VALUE);
                final int least = (int) Math.max(field.min(), Integer.MIN_VALUE);
                final int most = (int) Math.min(field.max(), Integer.MAX_VALUE);
                if (IkGui.dragInt(label, whole, 1, least, most)) {
                    change[0] = (long) whole[0];
                    return true;
                }
                return false;
            }
            case TEXT_LIST -> {
                return drawTextList(field, value, change);
            }
            case NUMBER_LIST -> {
                final List<?> list = value instanceof List<?> l ? l : List.of();
                final int size = (int) field.max();
                for (int i = 0; i < size && i < floats.length; ++i) {
                    floats[i] = i < list.size() ? ((Number) list.get(i)).floatValue() : 0;
                }
                final boolean changed =
                        switch (size) {
                            case 3 -> IkGui.dragFloat3(label, floats, 0.01f);
                            case 4 -> IkGui.dragFloat4(label, floats, 0.01f);
                            default -> IkGui.dragFloat(label, floats, 0.01f);
                        };
                if (changed) {
                    final List<Double> numbers = new ArrayList<>();
                    for (int i = 0; i < size && i < floats.length; ++i) {
                        numbers.add((double) floats[i]);
                    }
                    change[0] = List.copyOf(numbers);
                }
                return changed;
            }
        }
        return false;
    }

    /**
     * Draw a reference: its ID, typed or picked from the IDs of its kind in the index.
     *
     * @param field The field.
     * @param value The ID shown.
     * @param mixed Whether the files differ.
     * @param change Gets the new ID, null to take it out.
     * @return Whether it was changed.
     */
    private boolean drawReference(
            Field field, @Nullable Object value, boolean mixed, Object[] change) {
        boolean changed = false;
        text.set(mixed || value == null ? "" : value.toString());
        if (IkGui.inputTextWithHint(words(field.name()), mixed ? "mixed" : "none", text)) {
            change[0] = text.get().isEmpty() ? null : text.get();
            changed = true;
        }
        IkGui.sameLine();
        final String picked = drawPicker(field);
        if (picked != null) {
            change[0] = picked;
            changed = true;
        }
        return changed;
    }

    /**
     * Draw a text list: each entry with a button taking it out, and a picker or box adding one.
     *
     * @param field The field.
     * @param value The list shown.
     * @param change Gets the new list.
     * @return Whether it was changed.
     */
    private boolean drawTextList(Field field, @Nullable Object value, Object[] change) {
        final List<String> list = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) {
                list.add(String.valueOf(item));
            }
        }
        IkGui.textUnformatted(words(field.name()));
        boolean changed = false;
        for (int i = 0; i < list.size(); ++i) {
            IkGui.sameLine();
            if (IkGui.button(list.get(i) + "  x##item" + i)) {
                list.remove(i);
                changed = true;
                break;
            }
        }
        IkGui.sameLine();
        final String picked = field.refersTo() == null ? null : drawPicker(field);
        if (picked != null && !list.contains(picked)) {
            list.add(picked);
            changed = true;
        }
        if (field.refersTo() == null) {
            text.set("");
            if (IkGui.inputTextWithHint("##add", "add", text, InputTextFlags.ENTER_RETURNS_TRUE)
                    && !text.get().isBlank()) {
                list.add(text.get().trim());
                changed = true;
            }
        }
        if (changed) {
            change[0] = List.copyOf(list);
        }
        return changed;
    }

    /**
     * Draw a button opening a picker of the IDs of a field's kind in the index.
     *
     * @param field The field, which refers to a kind.
     * @return The ID picked this frame, or null.
     */
    private @Nullable String drawPicker(Field field) {
        if (IkGui.button("Pick...")) {
            pickerQuery.set("");
            IkGui.openPopup("picker");
        }
        if (!IkGui.beginPopup("picker")) {
            return null;
        }
        String picked = null;
        IkGui.inputTextWithHint("##search", "Search", pickerQuery);
        final String query = pickerQuery.get().toLowerCase(Locale.ROOT);
        int rows = 0;
        for (String id : idsOf(Objects.requireNonNull(field.refersTo()))) {
            if (!id.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            if (IkGui.selectable(id)) {
                picked = id;
                IkGui.closeCurrentPopup();
            }
            if (++rows == PICKER_ROWS) {
                break;
            }
        }
        if (rows == 0) {
            IkGui.textDisabled("Nothing of this kind matches");
        }
        IkGui.endPopup();
        return picked;
    }

    /**
     * The IDs of a kind in the index: names shared by every plugin for tags and game materials,
     * full IDs for the rest.
     *
     * @param kind The kind folder, like {@code materials}.
     * @return The IDs, sorted, without repeats.
     */
    List<String> idsOf(@NonNull String kind) {
        final boolean shared = "tags".equals(kind) || "materials".equals(kind);
        final java.util.SortedSet<String> ids = new java.util.TreeSet<>();
        final AssetIndex.Snapshot snapshot = index.get();
        for (AssetIndex.Entry entry : snapshot.entries()) {
            final AssetIndex.Root root = snapshot.roots().get(entry.root());
            if (!root.content()) {
                continue;
            }
            String relative = root.folder().relativize(entry.file()).toString().replace('\\', '/');
            String namespace = root.namespace();
            if (relative.startsWith("overrides/")) {
                final int slash = relative.indexOf('/', "overrides/".length());
                if (slash < 0) {
                    continue;
                }
                namespace = relative.substring("overrides/".length(), slash);
                relative = relative.substring(slash + 1);
            }
            if (!relative.startsWith(kind + "/") || namespace == null) {
                continue;
            }
            String path = relative.substring(kind.length() + 1);
            final int dot = path.lastIndexOf('.');
            path = dot > 0 ? path.substring(0, dot) : path;
            ids.add(shared ? path : namespace + ":" + path);
        }
        return List.copyOf(ids);
    }

    /**
     * List what is wrong with the files shown, as read.
     *
     * @param shown The files.
     */
    private void drawProblems(List<Edited> shown) {
        boolean any = false;
        for (Edited state : shown) {
            for (Schema.Problem problem : state.loaded().problems()) {
                if (!any) {
                    IkGui.separatorText("Problems");
                    any = true;
                }
                IkGui.textWrapped(
                        (shown.size() > 1 ? state.file().getFileName() + ": " : "")
                                + (problem.error() ? "error: " : "warning: ")
                                + problem.message());
            }
        }
    }

    /**
     * Note files edited but not saved that aren't shown, so they aren't forgotten.
     *
     * @param frame The current frame.
     */
    private void drawUnsaved(UiFrame frame) {
        final List<Edited> unsaved = files.values().stream().filter(Edited::dirty).toList();
        final List<Path> picked = selection.get();
        final List<Edited> hidden =
                unsaved.stream().filter(state -> !picked.contains(state.file())).toList();
        if (hidden.isEmpty()) {
            return;
        }
        IkGui.separator();
        IkGui.textDisabled(hidden.size() + " other files have changes not saved");
        IkGui.sameLine();
        if (IkGui.button("Save them")) {
            frame.fire(() -> save(hidden));
        }
    }

    /**
     * Write files' edits, and read them again.
     *
     * @param states The files.
     * @return How many were written.
     */
    public int save(@NonNull List<Edited> states) {
        int written = 0;
        for (Edited state : states) {
            if (!state.dirty()) {
                continue;
            }
            try {
                state.format().write(state.file(), state.current());
                files.remove(state.file());
                ++written;
            } catch (IOException | RuntimeException e) {
                log.warn("Could not save {}", state.file(), e);
                report.accept(
                        "Couldn't save " + state.file().getFileName() + ": " + e.getMessage());
                return written;
            }
        }
        report.accept("Saved " + written + (written == 1 ? " file" : " files"));
        return written;
    }

    /**
     * Drop files' edits.
     *
     * @param states The files.
     */
    public void revert(@NonNull List<Edited> states) {
        for (Edited state : states) {
            files.remove(state.file());
        }
    }

    /**
     * Set a field on the files shown, as editing its control does, for scripts.
     *
     * @param name The field.
     * @param value The value, of the field's Java type, or null to take it out.
     * @return Whether files are shown that have the field.
     */
    public boolean set(@NonNull String name, @Nullable Object value) {
        final List<Edited> shown = shown();
        if (shown.isEmpty() || shown.getFirst().format().schema().field(name) == null) {
            return false;
        }
        for (Edited state : shown) {
            if (value == null) {
                state.current().remove(name);
            } else {
                state.current().put(name, value);
            }
        }
        return true;
    }

    /**
     * A field's name as words, for its label: {@code specularTint} or {@code block_rules} as
     * "Specular tint" or "Block rules".
     *
     * @param name The name.
     * @return The words.
     */
    static String words(@NonNull String name) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); ++i) {
            final char c = name.charAt(i);
            if (c == '_') {
                out.append(' ');
            } else if (Character.isUpperCase(c) && i > 0) {
                out.append(' ').append(Character.toLowerCase(c));
            } else {
                out.append(i == 0 ? Character.toUpperCase(c) : c);
            }
        }
        return out.toString();
    }

    /**
     * A value as short text, for tooltips.
     *
     * @param value The value.
     * @return The text.
     */
    private static String describe(@Nullable Object value) {
        if (value == null) {
            return "none";
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return "empty";
        }
        return value.toString();
    }

    /**
     * Whether two values are the same, numbers by value.
     *
     * @param a One.
     * @param b The other.
     * @return True if the same.
     */
    private static boolean same(@Nullable Object a, @Nullable Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        return Objects.equals(a, b);
    }
}
