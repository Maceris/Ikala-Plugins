package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.definition.Kind;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.registry.ContentSource;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkString;

import lombok.NonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * The editor's Content tab: the content folders loaded, which one designs are saved to, and a
 * button that picks another folder to add. Changes go into the plugin's config, so they stay for
 * the next run.
 */
class ContentPanel {
    /** What a namespace may contain, as {@link com.ikalagaming.characters.data.Id} checks. */
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_-]+");

    /** Runs of characters a namespace can't hold, replaced when suggesting one. */
    private static final Pattern NOT_NAMESPACE = Pattern.compile("[^a-z0-9_-]+");

    /** The namespace in a definition's ID, as written in its JSON. */
    private static final Pattern ID_NAMESPACE = Pattern.compile("\"id\"\\s*:\\s*\"([a-z0-9_-]+):");

    /** The most definition files read to find a folder's namespace. */
    private static final int MAX_FILES_READ = 200;

    /** What definition files end with. */
    private static final String JSON = ".json";

    /** The longest namespace that can be typed. */
    private static final int NAMESPACE_CAPACITY = 64;

    /** Changes the content folders and where designs are saved, then loads them again. */
    @FunctionalInterface
    public interface FolderSetter {
        /**
         * Use these content folders from now on.
         *
         * @param folders The folders, in the order they load.
         * @param saveNamespace The namespace of the folder designs are saved to, or null to turn
         *     saving off.
         */
        void set(@NonNull List<ContentSource> folders, @Nullable String saveNamespace);
    }

    /** The plugin's UI, which opens the folder dialog. */
    private final UI ui;

    /** The loaded definitions, to say how many there are after a change. */
    private final Supplier<CharacterRegistry> registry;

    /** The folder picked to add, waiting for its namespace, or null. */
    private Path picked;

    /** The namespace to add the picked folder under. */
    private final IkString namespace = new IkString(NAMESPACE_CAPACITY);

    /** The namespace the picked folder's definitions use, or null if they don't say. */
    private String pickedUses;

    /** Whether designs should be saved to the folder added. */
    private boolean saveHere;

    /** What the last change did. */
    private String status = "";

    /**
     * Set up the tab.
     *
     * @param ui The plugin's UI.
     * @param registry The loaded definitions.
     */
    ContentPanel(@NonNull UI ui, @NonNull Supplier<CharacterRegistry> registry) {
        this.ui = ui;
        this.registry = registry;
    }

    /**
     * Draw the tab.
     *
     * @param settings The editor's settings, with the folders loaded now.
     * @return True if the folders changed, so the settings should be read again.
     */
    boolean draw(@NonNull CharacterEditor.EditorSettings settings) {
        boolean changed = drawLoaded(settings);
        IkGui.spacing();
        if (picked == null) {
            if (IkGui.button("Add folder...")) {
                ui.pickFolder("Add a content folder", startFolder(settings), this::folderPicked);
            }
        } else {
            changed |= drawPicked(settings);
        }
        if (!status.isEmpty()) {
            IkGui.textWrapped(status);
        }
        return changed;
    }

    /**
     * Where the dialog starts: beside the folder designs are saved to, or the first one loaded, or
     * where it was last.
     *
     * @param settings The editor's settings.
     * @return The folder, or null for the dialog's last one.
     */
    private static @Nullable Path startFolder(CharacterEditor.EditorSettings settings) {
        Path folder = settings.saveFolder();
        if (folder == null && !settings.folders().isEmpty()) {
            folder = settings.folders().get(0).root();
        }
        return folder == null ? null : folder.toAbsolutePath().getParent();
    }

    /**
     * Take the folder the dialog picked, suggesting the namespace its definitions use, or one from
     * its name.
     *
     * @param folder The folder, or null if the dialog was canceled.
     */
    private void folderPicked(@Nullable Path folder) {
        if (folder == null) {
            return;
        }
        picked = folder;
        pickedUses = namespaceUsed(folder);
        namespace.set(pickedUses != null ? pickedUses : suggestNamespace(folder), true);
        saveHere = false;
        status = "";
    }

    /**
     * Draw the folders loaded, each with a button to remove it and a box to save designs to it.
     *
     * @param settings The editor's settings.
     * @return True if the folders changed.
     */
    private boolean drawLoaded(CharacterEditor.EditorSettings settings) {
        IkGui.separatorText("Loaded folders");
        List<ContentSource> folders = settings.folders();
        if (folders.isEmpty()) {
            IkGui.textDisabled("None yet; add one below");
        }
        for (int i = 0; i < folders.size(); ++i) {
            ContentSource folder = folders.get(i);
            boolean saves = folder.namespace().equals(settings.saveNamespace());
            IkGui.pushID(i);
            IkGui.textUnformatted(folder.namespace());
            IkGui.sameLine();
            IkGui.textDisabled(folder.name());
            boolean remove = IkGui.button("Remove");
            IkGui.sameLine();
            boolean toggleSave = IkGui.checkbox("Save designs here", saves);
            IkGui.popID();
            if (remove) {
                List<ContentSource> left = new ArrayList<>(folders);
                left.remove(i);
                apply(settings, left, saves ? null : settings.saveNamespace());
                status = "Removed " + folder.namespace() + "; " + loadedCount(settings);
                return true;
            }
            if (toggleSave) {
                apply(settings, folders, saves ? null : folder.namespace());
                status = saves ? "Saving is off" : "Designs save to " + folder.name() + "/design";
                return true;
            }
        }
        return false;
    }

    /**
     * Draw the folder picked, with the namespace to add it under.
     *
     * @param settings The editor's settings.
     * @return True if it was added.
     */
    private boolean drawPicked(CharacterEditor.EditorSettings settings) {
        IkGui.separatorText("Add a folder");
        IkGui.textWrapped(picked.toString());
        IkGui.inputText("Namespace##namespace", namespace);
        String chosen = namespace.get().trim();
        if (pickedUses != null && !pickedUses.equals(chosen)) {
            IkGui.textWrapped(
                    "Its definitions are in the namespace "
                            + pickedUses
                            + "; under any other they won't load");
        }
        if (IkGui.checkbox("Save designs here##save-new", saveHere)) {
            saveHere = !saveHere;
        }
        if (IkGui.button("Add")) {
            String problem = addProblem(settings.folders(), picked, namespace.get().trim());
            if (problem != null) {
                status = problem;
                return false;
            }
            String added = namespace.get().trim();
            List<ContentSource> folders = new ArrayList<>(settings.folders());
            // Forward slashes, like the config is written by hand
            folders.add(new ContentSource(added, picked, picked.toString().replace('\\', '/')));
            apply(settings, folders, saveHere ? added : settings.saveNamespace());
            status = "Added " + added + "; " + loadedCount(settings);
            picked = null;
            return true;
        }
        IkGui.sameLine();
        if (IkGui.button("Cancel")) {
            picked = null;
            status = "";
        }
        return false;
    }

    /**
     * Why a folder can't be added under a namespace.
     *
     * @param loaded The folders loaded now.
     * @param folder The folder.
     * @param chosen The namespace.
     * @return The problem, or null if it can be added.
     */
    static @Nullable String addProblem(
            @NonNull List<ContentSource> loaded, @NonNull Path folder, @NonNull String chosen) {
        if (!NAMESPACE.matcher(chosen).matches()) {
            return "A namespace is lowercase letters, digits, underscores and hyphens, like"
                    + " my_characters";
        }
        Path absolute = folder.toAbsolutePath().normalize();
        for (ContentSource source : loaded) {
            if (source.namespace().equals(chosen)) {
                return "The namespace " + chosen + " is already loaded from " + source.name();
            }
            if (source.root().toAbsolutePath().normalize().equals(absolute)) {
                return "That folder is already loaded as " + source.namespace();
            }
        }
        return null;
    }

    /**
     * The namespace a content folder's definitions use: the one most of the IDs in its definition
     * files start with. Definitions must use their folder's namespace, so this is what it should be
     * added under.
     *
     * @param folder The folder.
     * @return The namespace, or null if no definition file names one.
     */
    static @Nullable String namespaceUsed(@NonNull Path folder) {
        Map<String, Integer> counts = new HashMap<>();
        int read = 0;
        for (Kind kind : Kind.values()) {
            Path kindFolder = folder.resolve(kind.getFolder());
            if (!Files.isDirectory(kindFolder)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(kindFolder)) {
                files = walk.filter(file -> file.toString().endsWith(JSON)).toList();
            } catch (IOException | UncheckedIOException e) {
                continue;
            }
            for (Path file : files) {
                if (read++ >= MAX_FILES_READ) {
                    break;
                }
                try {
                    Matcher ids =
                            ID_NAMESPACE.matcher(Files.readString(file, StandardCharsets.UTF_8));
                    while (ids.find()) {
                        counts.merge(ids.group(1), 1, Integer::sum);
                    }
                } catch (IOException | UncheckedIOException e) {
                    // An unreadable file says nothing about the namespace
                }
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * A namespace for a content folder, from its name: lowercase, with what a namespace can't hold
     * turned into underscores.
     *
     * @param folder The folder.
     * @return The namespace, or an empty string if the name has nothing usable.
     */
    static String suggestNamespace(@NonNull Path folder) {
        Path name = folder.getFileName();
        String lower = (name == null ? "" : name.toString()).toLowerCase(Locale.ROOT);
        // Underscores on the ends are only what was left of spaces or punctuation
        return NOT_NAMESPACE.matcher(lower).replaceAll("_").replaceAll("^_+|_+$", "");
    }

    /**
     * Change the folders and load them again.
     *
     * @param settings The editor's settings.
     * @param folders The folders to use.
     * @param saveNamespace The namespace designs save to, or null.
     */
    private static void apply(
            CharacterEditor.EditorSettings settings,
            List<ContentSource> folders,
            @Nullable String saveNamespace) {
        settings.setFolders().set(List.copyOf(folders), saveNamespace);
    }

    /**
     * How many definitions are loaded, and how many errors the load found, for the status.
     *
     * @param settings The editor's settings, which give the load's problems.
     * @return The text.
     */
    private String loadedCount(CharacterEditor.EditorSettings settings) {
        String loaded = registry.get().size() + " definitions loaded";
        long errors =
                settings.problems().get().stream()
                        .filter(problem -> problem.severity() == Problem.Severity.ERROR)
                        .count();
        if (errors == 0) {
            return loaded;
        }
        Problem first =
                settings.problems().get().stream()
                        .filter(problem -> problem.severity() == Problem.Severity.ERROR)
                        .findFirst()
                        .orElseThrow();
        return loaded
                + ", but "
                + errors
                + (errors == 1 ? " error" : " errors")
                + " kept some out (see the log), like: "
                + first.message();
    }
}
