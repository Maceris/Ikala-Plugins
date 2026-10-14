package com.ikalagaming.converter.workbench;

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
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * The folder the workbench is browsing, listing folders and the files it can open. It has no UI, so
 * it can be tested; {@link Workbench} draws it. Not thread safe, it is used from the render thread.
 */
public class FileBrowser {
    /**
     * Something in the folder being browsed.
     *
     * @param path The full path.
     * @param name The name to show.
     * @param directory Whether it is a folder.
     * @param kind What kind of asset a file is, {@link AssetKind#UNKNOWN} for folders.
     */
    public record Entry(
            @NonNull Path path, @NonNull String name, boolean directory, @NonNull AssetKind kind) {}

    /**
     * The folder being browsed.
     *
     * @return The folder.
     */
    @Getter private Path directory;

    /**
     * The folders and files in the folder being browsed: folders first, then files, by name.
     *
     * @return The entries.
     */
    @Getter private List<Entry> entries = List.of();

    /**
     * Whether every file is listed, not just ones the workbench can open.
     *
     * @return True if all files are listed.
     */
    @Getter private boolean showAllFiles;

    /**
     * Why the last action failed, such as a folder that couldn't be read.
     *
     * @return The message, or null if the last action worked.
     */
    @Getter private @Nullable String error;

    /**
     * Start browsing a folder.
     *
     * @param start The folder to start in.
     */
    public FileBrowser(@NonNull Path start) {
        navigate(start);
    }

    /**
     * The roots of the file system, like the drives on Windows.
     *
     * @return The roots.
     */
    public List<Path> roots() {
        List<Path> roots = new ArrayList<>();
        FileSystems.getDefault().getRootDirectories().forEach(roots::add);
        return roots;
    }

    /**
     * Browse a folder.
     *
     * @param folder The folder.
     */
    public void navigate(@NonNull Path folder) {
        Path absolute = folder.toAbsolutePath().normalize();
        try {
            entries = list(absolute, showAllFiles);
            directory = absolute;
            error = null;
        } catch (IOException | SecurityException e) {
            error = "Can't list " + absolute + ": " + e.getMessage();
        }
    }

    /** Browse the parent of the current folder, if there is one. */
    public void up() {
        Path parent = directory.getParent();
        if (parent != null) {
            navigate(parent);
        }
    }

    /**
     * List every file or only ones the workbench can open.
     *
     * @param all Whether to list every file.
     */
    public void setShowAllFiles(boolean all) {
        showAllFiles = all;
        navigate(directory);
    }

    /**
     * Turn typed text into a path, noting the error if it isn't one.
     *
     * @param text The path.
     * @return The path, or null if the text isn't a valid path.
     */
    public @Nullable Path parse(@NonNull String text) {
        try {
            error = null;
            return Path.of(text.trim());
        } catch (InvalidPathException e) {
            error = "Not a valid path: " + text;
            return null;
        }
    }

    /**
     * List a folder: folders first, then files, each by name ignoring case.
     *
     * @param folder The folder.
     * @param all Whether to list every file, or only ones the workbench can open.
     * @return The entries.
     * @throws IOException If the folder can't be read.
     */
    static List<Entry> list(@NonNull Path folder, boolean all) throws IOException {
        try (Stream<Path> children = Files.list(folder)) {
            return children.map(FileBrowser::entry)
                    .filter(entry -> entry.directory() || all || entry.kind() != AssetKind.UNKNOWN)
                    .sorted(
                            Comparator.comparing((Entry entry) -> !entry.directory())
                                    .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    /**
     * Describe a path in a folder.
     *
     * @param path The path.
     * @return Its entry.
     */
    private static Entry entry(@NonNull Path path) {
        final boolean directory = Files.isDirectory(path);
        return new Entry(
                path,
                path.getFileName().toString(),
                directory,
                directory ? AssetKind.UNKNOWN : AssetKind.of(path));
    }
}
