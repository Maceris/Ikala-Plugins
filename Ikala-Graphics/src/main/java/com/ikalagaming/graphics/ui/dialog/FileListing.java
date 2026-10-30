package com.ikalagaming.graphics.ui.dialog;

import lombok.Getter;
import lombok.NonNull;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * The folder a file dialog is showing: its folders, then the files it may pick. It has no UI, so it
 * can be tested; {@link FileDialog} draws it. Not thread safe, it is used from the render thread.
 */
public class FileListing {
    /**
     * Something in the folder.
     *
     * @param path The full path.
     * @param name The name to show.
     * @param folder Whether it is a folder.
     */
    public record Entry(@NonNull Path path, @NonNull String name, boolean folder) {}

    /** Whether only folders are listed, for picking a folder. */
    private final boolean foldersOnly;

    /** The file extensions listed, lowercase without the dot, or empty for every file. */
    private final List<String> extensions;

    /**
     * The folder being shown.
     *
     * @return The folder, absolute.
     */
    @Getter private Path directory;

    /**
     * The folders and files in the folder: folders first, then files, each by name ignoring case.
     *
     * @return The entries.
     */
    @Getter private List<Entry> entries = List.of();

    /**
     * Why the last action failed, such as a folder that couldn't be read.
     *
     * @return The message, or null if the last action worked.
     */
    @Getter private @Nullable String error;

    /**
     * Start listing a folder. If it can't be read, the working folder is listed instead.
     *
     * @param start The folder to start in.
     * @param foldersOnly Whether to list only folders.
     * @param extensions The file extensions to list, without the dot, or empty for every file.
     */
    public FileListing(@NonNull Path start, boolean foldersOnly, @NonNull List<String> extensions) {
        this.foldersOnly = foldersOnly;
        this.extensions =
                extensions.stream()
                        .map(extension -> extension.replaceFirst("^\\.", ""))
                        .map(extension -> extension.toLowerCase(Locale.ROOT))
                        .toList();
        navigate(start);
        if (directory == null) {
            String failed = error;
            navigate(Path.of(""));
            error = failed;
        }
    }

    /**
     * The roots of the file system, like the drives on Windows.
     *
     * @return The roots.
     */
    public static List<Path> roots() {
        List<Path> roots = new ArrayList<>();
        FileSystems.getDefault().getRootDirectories().forEach(roots::add);
        return roots;
    }

    /**
     * Show a folder. If it can't be read, the folder shown stays and the error is noted.
     *
     * @param folder The folder.
     * @return True if it is shown now.
     */
    public boolean navigate(@NonNull Path folder) {
        Path absolute = folder.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            error = "There is no folder " + absolute;
            return false;
        }
        try {
            entries = list(absolute);
            directory = absolute;
            error = null;
            return true;
        } catch (IOException | SecurityException e) {
            error = "Can't list " + absolute + ": " + e.getMessage();
            return false;
        }
    }

    /** Show the parent of the folder, if there is one. */
    public void up() {
        Path parent = directory.getParent();
        if (parent != null) {
            navigate(parent);
        }
    }

    /** List the folder again, for files that changed. */
    public void refresh() {
        navigate(directory);
    }

    /**
     * Turn typed text into a path, relative to the folder shown unless it is absolute.
     *
     * @param text The text.
     * @return The path, or null if the text is empty or isn't a path, noting the error.
     */
    public @Nullable Path resolve(@NonNull String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            error = null;
            return directory.resolve(trimmed).normalize();
        } catch (InvalidPathException e) {
            error = "Not a valid path: " + trimmed;
            return null;
        }
    }

    /**
     * Whether a file is one the listing shows: any file if there are no extensions, otherwise one
     * ending in one of them, ignoring case.
     *
     * @param file The file.
     * @return True if it matches.
     */
    public boolean matches(@NonNull Path file) {
        if (extensions.isEmpty()) {
            return true;
        }
        Path name = file.getFileName();
        if (name == null) {
            return false;
        }
        String lower = name.toString().toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(extension -> lower.endsWith("." + extension));
    }

    /**
     * A file name to save as, with the first extension added if it ends in none of them.
     *
     * @param name The name typed.
     * @return The name to use.
     */
    public String withExtension(@NonNull String name) {
        String trimmed = name.trim();
        if (extensions.isEmpty() || matches(Path.of(trimmed))) {
            return trimmed;
        }
        return trimmed + "." + extensions.get(0);
    }

    /**
     * What to show for a path: its name, or the whole path for a root like a drive.
     *
     * @param path The path.
     * @return The name.
     */
    public static String name(@NonNull Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    /**
     * List a folder: folders first, then matching files, each by name ignoring case. Hidden ones
     * are left out.
     *
     * @param folder The folder.
     * @return The entries.
     * @throws IOException If the folder can't be read.
     */
    private List<Entry> list(Path folder) throws IOException {
        try (Stream<Path> children = Files.list(folder)) {
            return children.filter(child -> !hidden(child))
                    .map(child -> new Entry(child, name(child), Files.isDirectory(child)))
                    .filter(entry -> entry.folder() || (!foldersOnly && matches(entry.path())))
                    .sorted(
                            Comparator.comparing((Entry entry) -> !entry.folder())
                                    .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    /**
     * Whether a file is hidden, like system folders on Windows or dot files elsewhere.
     *
     * @param path The file.
     * @return True if hidden, or if that can't be told.
     */
    private static boolean hidden(Path path) {
        try {
            return Files.isHidden(path);
        } catch (IOException | SecurityException e) {
            return true;
        }
    }
}
