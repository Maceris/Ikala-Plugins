package com.ikalagaming.graphics.ui.dialog;

import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Justify;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Layer;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Row;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.TextInput;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.VirtualGrid;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * A dialog over the whole window for picking a folder, a file to open or a file to save, drawn as
 * retained UI. Plugins open it through {@code GraphicsContext.ui()}; one is open at a time, and
 * opening another cancels the first.
 *
 * <p>A dimmed backdrop covers everything behind it, so the rest of the UI can't be used until it
 * closes. A single click on a row selects it, a double click opens a folder or picks a file. The
 * folder last picked from is remembered for each mode while the program runs.
 *
 * <p>Its nodes have fixed IDs under {@link #SURFACE_ID}, so scripts can drive it: type a path into
 * {@code file-dialog//path}, a name into {@code file-dialog//name}, then click {@code
 * file-dialog//ok}.
 */
@Slf4j
public final class FileDialog {
    /** What the dialog picks. */
    public enum Mode {
        /** A folder. */
        FOLDER,
        /** An existing file to open. */
        OPEN,
        /** A file to save to, which may not exist yet. */
        SAVE
    }

    /**
     * What to ask for.
     *
     * @param mode What is picked.
     * @param title The dialog's title.
     * @param start The folder to start in, or null for the one last picked from in this mode.
     * @param extensions The file extensions to list, without the dot, or empty for every file. A
     *     name saved without one of them gets the first.
     * @param fileName The name to start with when saving, or an empty string.
     */
    public record Options(
            @NonNull Mode mode,
            @NonNull String title,
            @Nullable Path start,
            @NonNull List<String> extensions,
            @NonNull String fileName) {
        /**
         * Ask for a folder.
         *
         * @param title The dialog's title.
         * @param start The folder to start in, or null for the last one.
         * @return The options.
         */
        public static Options folder(@NonNull String title, @Nullable Path start) {
            return new Options(Mode.FOLDER, title, start, List.of(), "");
        }

        /**
         * Ask for a file to open.
         *
         * @param title The dialog's title.
         * @param start The folder to start in, or null for the last one.
         * @param extensions The extensions to list, or none for every file.
         * @return The options.
         */
        public static Options open(
                @NonNull String title, @Nullable Path start, @NonNull String... extensions) {
            return new Options(Mode.OPEN, title, start, List.of(extensions), "");
        }

        /**
         * Ask for a file to save to.
         *
         * @param title The dialog's title.
         * @param start The folder to start in, or null for the last one.
         * @param fileName The name to start with.
         * @param extensions The extensions to list, or none for every file.
         * @return The options.
         */
        public static Options save(
                @NonNull String title,
                @Nullable Path start,
                @NonNull String fileName,
                @NonNull String... extensions) {
            return new Options(Mode.SAVE, title, start, List.of(extensions), fileName);
        }
    }

    /** The dialog's surface ID. */
    public static final String SURFACE_ID = "file-dialog";

    /** The surface ID of the backdrop behind it. */
    public static final String BACKDROP_ID = "file-dialog-backdrop";

    /** The path field's node ID. */
    public static final String PATH_ID = "path";

    /** The name field's node ID. */
    public static final String NAME_ID = "name";

    /** The list's node ID. */
    public static final String LIST_ID = "list";

    /** The OK button's node ID. */
    public static final String OK_ID = "ok";

    /** The Cancel button's node ID. */
    public static final String CANCEL_ID = "cancel";

    /** The Up button's node ID. */
    public static final String UP_ID = "up";

    /** The status line's node ID. */
    public static final String STATUS_ID = "status";

    /** The style class of the dimmed backdrop. */
    private static final String BACKDROP_CLASS = "dialog-backdrop";

    /** The dialog's size, in units. */
    private static final float WIDTH = 640;

    /** The dialog's height, in units. */
    private static final float HEIGHT = 480;

    /** How wide the list rows are, in units: the dialog less its padding and a scrollbar. */
    private static final float ROW_WIDTH = WIDTH - 40;

    /** How tall the list rows are, in ems. */
    private static final float ROW_HEIGHT = 1.3f;

    /** The folder last picked from in each mode. */
    private static final Map<Mode, Path> LAST_FOLDERS = new ConcurrentHashMap<>();

    /** The dialog open now, or null. */
    private static final AtomicReference<FileDialog> OPEN = new AtomicReference<>();

    /** The plugin's UI, which shows the surfaces. */
    private final UI ui;

    /** Whether the plugin that opened it has unloaded, so its callback must not run. */
    private final BooleanSupplier ownerClosed;

    /** What was asked for. */
    private final Options options;

    /** Gets the path picked, or null if canceled. */
    private final Consumer<Path> chosen;

    /** The folder shown. */
    private final FileListing listing;

    /** The path field. */
    private final TextInput path;

    /** The name field. */
    private final TextInput name;

    /** The list of folders and files. */
    private final VirtualGrid list;

    /** The status line. */
    private final Label status;

    /** The entry selected, or -1 for none. */
    private int selected = -1;

    /** A file asked to be saved over once, so asking again replaces it. */
    private Path replacing;

    /** Whether it has closed, so it answers only once. */
    private boolean finished;

    /**
     * Build a dialog; it shows when opened.
     *
     * @param ui The plugin's UI.
     * @param ownerClosed Whether the plugin has unloaded.
     * @param options What to ask for.
     * @param chosen Gets the path picked.
     */
    private FileDialog(UI ui, BooleanSupplier ownerClosed, Options options, Consumer<Path> chosen) {
        this.ui = ui;
        this.ownerClosed = ownerClosed;
        this.options = options;
        this.chosen = chosen;
        Path start = options.start();
        if (start == null) {
            start = LAST_FOLDERS.getOrDefault(options.mode(), Path.of(""));
        }
        listing = new FileListing(start, options.mode() == Mode.FOLDER, options.extensions());
        path = new TextInput(PATH_ID, "").onSubmit(this::typedPath);
        name = new TextInput(NAME_ID, options.fileName());
        list =
                new VirtualGrid(LIST_ID)
                        .columns(1)
                        .cellSize(Length.u(ROW_WIDTH), Length.em(ROW_HEIGHT))
                        .gap(Length.ZERO)
                        .border(true);
        status = new Label(STATUS_ID, "").classes("muted");
    }

    /**
     * Open a dialog, replacing any open one, which is canceled. Its answer comes on the render
     * thread: the path picked, absolute, or null if it was canceled. It isn't answered if the
     * plugin that opened it unloads first.
     *
     * @param ui The plugin's UI.
     * @param ownerClosed Whether the plugin has unloaded.
     * @param options What to ask for.
     * @param chosen Gets the path picked, or null.
     */
    public static void open(
            @NonNull UI ui,
            @NonNull BooleanSupplier ownerClosed,
            @NonNull Options options,
            @NonNull Consumer<Path> chosen) {
        ui.post(
                () -> {
                    FileDialog dialog = new FileDialog(ui, ownerClosed, options, chosen);
                    FileDialog previous = OPEN.getAndSet(dialog);
                    if (previous != null) {
                        // Its surfaces are replaced by this one's, so only its answer is left
                        previous.answer(null);
                    }
                    dialog.show();
                });
    }

    /**
     * Whether a dialog is open.
     *
     * @return True if one is open.
     */
    public static boolean isOpen() {
        return OPEN.get() != null;
    }

    /** Build the surfaces and show them, the backdrop first so the dialog draws over it. */
    private void show() {
        ui.show(
                ui.surface(BACKDROP_ID)
                        .anchors(Anchors.fill())
                        .layer(Layer.OVERLAY)
                        .classes(BACKDROP_CLASS));

        Row drives = new Row("drives").gap(4);
        List<Path> roots = FileListing.roots();
        for (int r = 0; r < roots.size() && roots.size() > 1; ++r) {
            final Path root = roots.get(r);
            drives.add(new Button("root" + r, root.toString()).onClick(() -> navigate(root)));
        }
        String okText =
                switch (options.mode()) {
                    case FOLDER -> "Choose folder";
                    case OPEN -> "Open";
                    case SAVE -> "Save";
                };
        Column content =
                new Column("content")
                        .gap(6)
                        .align(Align.STRETCH)
                        .width(Sizing.grow())
                        .height(Sizing.grow())
                        .add(
                                new Label("title", options.title()).classes("title"),
                                new Row("nav")
                                        .gap(4)
                                        .align(Align.CENTER)
                                        .add(path, new Button(UP_ID, "Up").onClick(this::up)),
                                drives,
                                list,
                                new Row("name-row")
                                        .gap(4)
                                        .align(Align.CENTER)
                                        .add(
                                                new Label(
                                                        "name-label",
                                                        options.mode() == Mode.FOLDER
                                                                ? "Folder"
                                                                : "File name"),
                                                name),
                                status,
                                new Row("buttons")
                                        .gap(4)
                                        .justify(Justify.END)
                                        .add(
                                                new Button(CANCEL_ID, "Cancel")
                                                        .onClick(this::cancel),
                                                new Button(OK_ID, okText).onClick(this::ok)),
                                // Escape cancels, wherever the keyboard focus is
                                new Immediate("keys", this::keys)
                                        .width(Sizing.fixed(1))
                                        .height(Sizing.fixed(1)));
        ui.show(
                ui.surface(SURFACE_ID)
                        .anchors(Anchors.center())
                        .width(Sizing.fixed(WIDTH))
                        .height(Sizing.fixed(HEIGHT))
                        .layer(Layer.OVERLAY)
                        .content(content));
        listed();
    }

    /**
     * Cancel on Escape.
     *
     * @param frame The UI frame.
     */
    private void keys(UiFrame frame) {
        if (IkGui.isKeyPressed(Key.ESCAPE, false)) {
            frame.fire(this::cancel);
        }
    }

    /** Show what the listing has now, after it changed folder. */
    private void listed() {
        selected = -1;
        replacing = null;
        path.text(listing.getDirectory().toString());
        if (options.mode() == Mode.FOLDER) {
            name.text("");
        }
        List<FileListing.Entry> entries = listing.getEntries();
        list.items(entries.size(), this::row);
        String error = listing.getError();
        if (error != null) {
            status.text(error);
        } else if (entries.isEmpty()) {
            status.text(
                    options.mode() == Mode.FOLDER
                            ? "No folders in here"
                            : "Nothing in here to pick");
        } else {
            status.text("");
        }
    }

    /**
     * Make the row for an entry.
     *
     * @param index The entry's index.
     * @return The row.
     */
    private Selectable row(int index) {
        FileListing.Entry entry = listing.getEntries().get(index);
        return new Selectable("entry", entry.folder() ? entry.name() + "/" : entry.name())
                .selected(index == selected)
                .onClick(() -> select(index))
                .onDoubleClick(() -> activate(index));
    }

    /**
     * Select an entry, putting its name in the name field.
     *
     * @param index The entry's index.
     */
    private void select(int index) {
        if (index >= listing.getEntries().size()) {
            return;
        }
        selected = index;
        replacing = null;
        FileListing.Entry entry = listing.getEntries().get(index);
        if (options.mode() == Mode.FOLDER || !entry.folder()) {
            name.text(entry.name());
        }
        status.text("");
        // The rows are made again with the new selection
        list.refresh();
    }

    /**
     * Open a folder or pick a file, for a double click.
     *
     * @param index The entry's index.
     */
    private void activate(int index) {
        if (index >= listing.getEntries().size()) {
            return;
        }
        FileListing.Entry entry = listing.getEntries().get(index);
        if (entry.folder()) {
            navigate(entry.path());
        } else {
            pickFile(entry.path());
        }
    }

    /**
     * Show a folder.
     *
     * @param folder The folder.
     */
    private void navigate(Path folder) {
        listing.navigate(folder);
        listed();
    }

    /** Show the parent folder. */
    private void up() {
        listing.up();
        listed();
    }

    /**
     * Go to a typed path: a folder is shown, a file is shown in its folder and selected.
     *
     * @param text The path.
     */
    private void typedPath(String text) {
        Path typed = listing.resolve(text);
        if (typed == null) {
            listed();
            return;
        }
        if (Files.isRegularFile(typed) && typed.getParent() != null) {
            navigate(typed.getParent());
            if (options.mode() != Mode.FOLDER) {
                name.text(FileListing.name(typed));
            }
            return;
        }
        navigate(typed);
    }

    /** Pick what the name field and selection say, or go into a folder named there. */
    private void ok() {
        Path named = listing.resolve(name.text());
        if (options.mode() == Mode.FOLDER) {
            Path folder = named == null ? listing.getDirectory() : named;
            if (!Files.isDirectory(folder)) {
                status.text("There is no folder " + folder);
                return;
            }
            answer(folder);
            return;
        }
        if (named == null) {
            status.text(
                    listing.getError() != null
                            ? listing.getError()
                            : "Pick a file, or type its name");
            return;
        }
        if (Files.isDirectory(named)) {
            navigate(named);
            return;
        }
        pickFile(named);
    }

    /**
     * Pick a file: one that exists when opening, or any in a folder that exists when saving, asking
     * first before replacing one.
     *
     * @param file The file.
     */
    private void pickFile(Path file) {
        if (options.mode() == Mode.OPEN) {
            if (!Files.isRegularFile(file)) {
                status.text("There is no file " + file.getFileName());
                return;
            }
            answer(file);
            return;
        }
        Path parent = file.getParent();
        Path target =
                parent == null
                        ? file
                        : parent.resolve(listing.withExtension(FileListing.name(file)));
        if (parent == null || !Files.isDirectory(parent)) {
            status.text("There is no folder " + parent);
            return;
        }
        if (Files.exists(target) && !target.equals(replacing)) {
            replacing = target;
            name.text(FileListing.name(target));
            status.text(FileListing.name(target) + " already exists; Save again to replace it");
            return;
        }
        answer(target);
    }

    /** Close without picking anything. */
    private void cancel() {
        answer(null);
    }

    /**
     * Close the dialog and give the answer, once.
     *
     * @param picked The path picked, or null if canceled.
     */
    private void answer(@Nullable Path picked) {
        if (finished) {
            return;
        }
        finished = true;
        // Only this dialog's own surfaces are removed; a newer one has taken their IDs, and an
        // unloaded plugin's went with it
        if (OPEN.compareAndSet(this, null) && !ownerClosed.getAsBoolean()) {
            ui.remove(SURFACE_ID);
            ui.remove(BACKDROP_ID);
        }
        if (picked != null) {
            Path absolute = picked.toAbsolutePath().normalize();
            Path folder = Files.isDirectory(absolute) ? absolute : absolute.getParent();
            if (folder != null) {
                LAST_FOLDERS.put(options.mode(), folder);
            }
            picked = absolute;
        }
        if (ownerClosed.getAsBoolean()) {
            return;
        }
        try {
            chosen.accept(picked);
        } catch (RuntimeException e) {
            log.warn("Handling the file dialog's answer failed", e);
        }
    }
}
