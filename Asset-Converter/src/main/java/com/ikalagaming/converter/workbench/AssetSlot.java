package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.index.AssetIndex;
import com.ikalagaming.converter.index.AssetSearch;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.util.Color;

import lombok.NonNull;
import org.joml.Vector2f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Slots: the widget for an asset reference, drawn in place by panels that draw with IkGui each
 * frame. A slot shows the file it names, with its thumbnail, outlined red when the file isn't
 * there; takes a file dragged from the Assets panel, one found with its search picker, or the
 * Assets panel's selection; opens the file in the workbench; and clears. Each accepts only files of
 * its types in the asset index. Render thread, apart from {@link #request}.
 */
public final class AssetSlot {
    /** The size of a slot's thumbnail, in pixels. */
    public static final float THUMBNAIL = 48;

    /** The most files the picker lists. */
    static final int PICKER_ROWS = 60;

    /** The color of a missing file's name and outline. */
    private static final int MISSING = Color.rgba(1.0f, 0.35f, 0.35f, 1.0f);

    /**
     * What a slot was changed to this frame.
     *
     * @param file The file it now names, or null if it was cleared.
     */
    public record Change(@Nullable Path file) {}

    /**
     * How a slot looked when last drawn, for scripts.
     *
     * @param label Its label.
     * @param file The file it names, or null.
     * @param missing Whether that file isn't there.
     */
    public record Shown(@NonNull String label, @Nullable Path file, boolean missing) {}

    /** A change a script asked for, done the next time the slot draws. */
    private record Request(String action, @Nullable String argument) {}

    /** The asset index, as of now. */
    private final Supplier<AssetIndex.Snapshot> index;

    /** A file's thumbnail, or null until there is one. */
    private final Function<AssetIndex.Entry, TextureHandle> thumbnails;

    /** The Assets panel's selection, or null. */
    private final Supplier<Path> selection;

    /** Opens a file in the workbench. */
    private final Consumer<Path> open;

    /** The picker's search text. */
    private final IkString query = new IkString(128);

    /** Guards {@link #requests}, which scripts add to from their own thread. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Changes scripts asked for, by slot label. */
    private final Map<String, Request> requests = new HashMap<>();

    /** Each slot as last drawn, by label, for scripts. */
    private final Map<String, Shown> shown = new LinkedHashMap<>();

    /**
     * Make the slots' shared state.
     *
     * @param index The asset index, as of now.
     * @param thumbnails A file's thumbnail, or null until it is made.
     * @param selection The Assets panel's selection, or null.
     * @param open Opens a file in the workbench.
     */
    public AssetSlot(
            @NonNull Supplier<AssetIndex.Snapshot> index,
            @NonNull Function<AssetIndex.Entry, TextureHandle> thumbnails,
            @NonNull Supplier<Path> selection,
            @NonNull Consumer<Path> open) {
        this.index = index;
        this.thumbnails = thumbnails;
        this.selection = selection;
        this.open = open;
    }

    /**
     * Draw a slot.
     *
     * @param label What the slot is, shown above its file and unique among the slots drawn.
     * @param types The asset types it takes.
     * @param file The file it names, or null when empty.
     * @param texture What to show as its picture, or null to show the file's thumbnail.
     * @return What it was changed to this frame, or null if it wasn't.
     */
    public @Nullable Change draw(
            @NonNull String label,
            @NonNull Set<String> types,
            @Nullable Path file,
            @Nullable TextureHandle texture) {
        final AssetIndex.Snapshot snapshot = index.get();
        final boolean missing = file != null && !Files.isRegularFile(withoutMark(file));
        shown.put(label, new Shown(label, file, missing));
        Change change = scripted(label, types, file, snapshot);

        IkGui.pushID(label);
        IkGui.beginGroup();
        final TextureHandle picture = texture != null ? texture : thumbnailOf(snapshot, file);
        if (picture != null) {
            IkGui.image(picture, THUMBNAIL, THUMBNAIL);
        } else {
            IkGui.dummy(THUMBNAIL, THUMBNAIL);
        }
        IkGui.sameLine();
        IkGui.beginGroup();
        IkGui.text(label);
        if (file == null) {
            IkGui.textDisabled("empty");
        } else if (missing) {
            IkGui.textColored(MISSING, file.getFileName() + " (missing)");
        } else {
            IkGui.textUnformatted(file.getFileName().toString());
        }
        if (file != null && IkGui.isItemHovered()) {
            IkGui.setTooltip(file.toString());
        }
        final Path picked = selection.get();
        final boolean canUse = picked != null && accepts(snapshot, types, picked);
        IkGui.beginDisabled(!canUse);
        // Named apart for scripts; it reads "<" on screen
        if (IkGui.button("<##" + label)) {
            change = new Change(picked);
        }
        IkGui.endDisabled();
        if (IkGui.isItemHovered(
                com.ikalagaming.graphics.gui.flags.HoveredFlags.ALLOW_WHEN_DISABLED)) {
            IkGui.setTooltip(
                    canUse
                            ? "Use " + picked.getFileName() + ", selected in the Assets panel"
                            : "Select a file this slot takes in the Assets panel to use it here");
        }
        IkGui.sameLine();
        if (IkGui.button("Pick...")) {
            query.set("");
            IkGui.openPopup("picker");
        }
        if (file != null && !missing) {
            IkGui.sameLine();
            if (IkGui.button("Open")) {
                open.accept(withoutMark(file));
            }
        }
        if (file != null) {
            IkGui.sameLine();
            if (IkGui.button("Clear")) {
                change = new Change(null);
            }
        }
        IkGui.endGroup();
        IkGui.endGroup();
        if (missing) {
            outline();
        }
        final Change dropped = dropTarget(snapshot, types);
        if (dropped != null) {
            change = dropped;
        }
        final Change chosen = drawPicker(snapshot, types);
        if (chosen != null) {
            change = chosen;
        }
        IkGui.popID();
        return change;
    }

    /**
     * Ask for a change to a slot, done the next time it draws, as if by hand: "drop path", "pick
     * text" (the picker's first match), "use" (the Assets panel's selection), or "clear". Any
     * thread.
     *
     * @param label The slot's label.
     * @param action What to do.
     * @param argument The path or text, or null.
     */
    public void request(@NonNull String label, @NonNull String action, @Nullable String argument) {
        lock.lock();
        try {
            requests.put(label, new Request(action, argument));
        } finally {
            lock.unlock();
        }
    }

    /**
     * The slots as last drawn.
     *
     * @return Each slot, in the order first drawn.
     */
    public List<Shown> shown() {
        return List.copyOf(shown.values());
    }

    /**
     * Whether a slot takes a file: one in the asset index of one of its types.
     *
     * @param snapshot The index.
     * @param types The slot's types.
     * @param file The file.
     * @return True if it does.
     */
    static boolean accepts(AssetIndex.Snapshot snapshot, Set<String> types, Path file) {
        final AssetIndex.Entry entry = snapshot.entry(file);
        return entry != null && types.contains(entry.type());
    }

    /**
     * The files the picker lists: those of the slot's types matching the search, best first.
     *
     * @param snapshot The index.
     * @param types The slot's types.
     * @param text The search.
     * @return The files, at most {@link #PICKER_ROWS}.
     */
    static List<AssetIndex.Entry> candidates(
            AssetIndex.Snapshot snapshot, Set<String> types, String text) {
        final List<AssetIndex.Entry> found = new ArrayList<>();
        for (AssetIndex.Entry entry :
                AssetSearch.find(snapshot, new AssetSearch.Query(text, null, null, null), null)) {
            if (types.contains(entry.type())) {
                found.add(entry);
                if (found.size() == PICKER_ROWS) {
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Do the change a script asked for, if any.
     *
     * @param label The slot.
     * @param types Its types.
     * @param file What it names now.
     * @param snapshot The index.
     * @return The change, or null.
     */
    private @Nullable Change scripted(
            String label, Set<String> types, @Nullable Path file, AssetIndex.Snapshot snapshot) {
        final Request request;
        lock.lock();
        try {
            request = requests.remove(label);
        } finally {
            lock.unlock();
        }
        if (request == null) {
            return null;
        }
        return switch (request.action()) {
            case "drop" -> {
                final Path dropped = Path.of(request.argument()).toAbsolutePath().normalize();
                yield accepts(snapshot, types, dropped) ? new Change(dropped) : null;
            }
            case "pick" -> {
                final List<AssetIndex.Entry> found =
                        candidates(
                                snapshot,
                                types,
                                request.argument() == null ? "" : request.argument());
                yield found.isEmpty() ? null : new Change(found.getFirst().file());
            }
            case "use" -> {
                final Path picked = selection.get();
                yield picked != null && accepts(snapshot, types, picked)
                        ? new Change(picked)
                        : null;
            }
            case "clear" -> file == null ? null : new Change(null);
            default -> null;
        };
    }

    /** Outline the slot just drawn in the missing color. */
    private static void outline() {
        final Vector2f min = IkGui.getItemRectMin();
        final Vector2f max = IkGui.getItemRectMax();
        final DrawList drawList = IkGui.getWindowDrawList();
        drawList.addRect(min.x - 2, min.y - 2, max.x + 2, max.y + 2, MISSING, 3);
    }

    /**
     * Take a file dragged from the Assets panel onto the slot just drawn, if it is one it takes.
     *
     * @param snapshot The index.
     * @param types The slot's types.
     * @return The change, or null.
     */
    private static @Nullable Change dropTarget(AssetIndex.Snapshot snapshot, Set<String> types) {
        if (!IkGui.beginDragDropTarget()) {
            return null;
        }
        Change change = null;
        final Object dropped = IkGui.acceptDragDropPayload(AssetBrowser.DRAG_TYPE);
        if (dropped instanceof Path file && accepts(snapshot, types, file)) {
            change = new Change(file);
        }
        IkGui.endDragDropTarget();
        return change;
    }

    /**
     * Draw the search picker, if open: a search box and the matching files of the slot's types.
     *
     * @param snapshot The index.
     * @param types The slot's types.
     * @return The file picked, or null.
     */
    private @Nullable Change drawPicker(AssetIndex.Snapshot snapshot, Set<String> types) {
        if (!IkGui.beginPopup("picker")) {
            return null;
        }
        Change change = null;
        if (IkGui.isWindowAppearing()) {
            IkGui.setKeyboardFocusHere();
        }
        IkGui.inputTextWithHint("##search", "Search", query);
        final List<AssetIndex.Entry> found = candidates(snapshot, types, query.get());
        if (found.isEmpty()) {
            IkGui.textDisabled("Nothing of this kind matches");
        }
        for (AssetIndex.Entry entry : found) {
            if (IkGui.selectable(entry.file().getFileName() + "##" + entry.file())) {
                change = new Change(entry.file());
                IkGui.closeCurrentPopup();
            }
            if (IkGui.isItemHovered()) {
                IkGui.setTooltip(entry.file().toString());
            }
        }
        IkGui.endPopup();
        return change;
    }

    /**
     * A file's thumbnail from the index, or null.
     *
     * @param snapshot The index.
     * @param file The file, or null.
     * @return The thumbnail, or null until made.
     */
    private @Nullable TextureHandle thumbnailOf(AssetIndex.Snapshot snapshot, @Nullable Path file) {
        if (file == null) {
            return null;
        }
        final AssetIndex.Entry entry = snapshot.entry(withoutMark(file));
        return entry == null ? null : thumbnails.apply(entry);
    }

    /**
     * A file's path without a mark naming a map in it, like a texture file's normal map.
     *
     * @param file The path.
     * @return The file itself.
     */
    static Path withoutMark(Path file) {
        final String text = file.toString();
        final int hash = text.lastIndexOf('#');
        return hash > text.lastIndexOf(java.io.File.separatorChar) && hash > 0
                ? Path.of(text.substring(0, hash))
                : file;
    }
}
