package com.ikalagaming.converter.inspector;

import com.ikalagaming.graphics.asset.AssetFormat;
import com.ikalagaming.graphics.asset.AssetValidator;
import com.ikalagaming.graphics.asset.KnownSections;

import lombok.Getter;
import lombok.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
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
 * What the asset inspector shows: the folder being browsed, and the container that is open. It has
 * no UI, so it can be tested; {@link AssetInspectorWindow} draws it. Not thread safe, it is used
 * from the render thread.
 */
public class AssetInspector {
    /** Files bigger than this are not opened, since they are read into memory. */
    static final long MAX_FILE_SIZE = 64L * 1024 * 1024;

    /**
     * Something in the folder being browsed.
     *
     * @param path The full path.
     * @param name The name to show.
     * @param directory Whether it is a folder.
     */
    public record DirectoryEntry(@NonNull Path path, @NonNull String name, boolean directory) {}

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
    @Getter private List<DirectoryEntry> entries = List.of();

    /**
     * Whether every file is listed, not just containers.
     *
     * @return True if all files are listed.
     */
    @Getter private boolean showAllFiles;

    /**
     * The open file.
     *
     * @return The file, or null if none is open.
     */
    @Getter private @Nullable Path file;

    /**
     * The size of the open file.
     *
     * @return The size in bytes.
     */
    @Getter private long fileSize;

    /**
     * What was read from the open file.
     *
     * @return The inspection, or null if no file is open.
     */
    @Getter private @Nullable AssetValidator.Inspection inspection;

    /**
     * The selected section, an index into the inspection's entries.
     *
     * @return The index, or -1 for none.
     */
    @Getter private int selected = -1;

    /**
     * Why the last action failed, such as a file that couldn't be read.
     *
     * @return The message, or null if the last action worked.
     */
    @Getter private @Nullable String error;

    /**
     * Start browsing a folder.
     *
     * @param start The folder to start in.
     */
    public AssetInspector(@NonNull Path start) {
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
     * List every file or only containers.
     *
     * @param all Whether to list every file.
     */
    public void setShowAllFiles(boolean all) {
        showAllFiles = all;
        navigate(directory);
    }

    /**
     * Open a typed path: browse it if it is a folder, open it if it is a file.
     *
     * @param text The path.
     */
    public void openPath(@NonNull String text) {
        Path path;
        try {
            path = Path.of(text.trim());
        } catch (InvalidPathException e) {
            error = "Not a valid path: " + text;
            return;
        }
        if (Files.isDirectory(path)) {
            navigate(path);
        } else {
            open(path);
        }
    }

    /**
     * Read and inspect a container. Broken files are still opened, so their problems can be shown.
     *
     * @param path The file.
     */
    public void open(@NonNull Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            long size = Files.size(absolute);
            if (size > MAX_FILE_SIZE) {
                error = absolute.getFileName() + " is " + size + " bytes, too large to inspect";
                return;
            }
            byte[] bytes = Files.readAllBytes(absolute);
            inspection = AssetValidator.inspect(ByteBuffer.wrap(bytes), KnownSections.graphics());
            file = absolute;
            fileSize = bytes.length;
            selected = -1;
            error = null;
        } catch (IOException | SecurityException e) {
            error = "Can't read " + absolute + ": " + e.getMessage();
        }
    }

    /**
     * Select a section to preview.
     *
     * @param index The entry index, or -1 for none.
     */
    public void select(int index) {
        int count = inspection == null ? 0 : inspection.entries().size();
        selected = index >= 0 && index < count ? index : -1;
    }

    /**
     * The selected entry.
     *
     * @return The entry, or null if none is selected.
     */
    public @Nullable AssetValidator.Entry selectedEntry() {
        return selected < 0 ? null : inspection.entries().get(selected);
    }

    /**
     * List a folder: folders first, then files, each by name ignoring case.
     *
     * @param folder The folder.
     * @param all Whether to list every file, or only containers.
     * @return The entries.
     * @throws IOException If the folder can't be read.
     */
    static List<DirectoryEntry> list(@NonNull Path folder, boolean all) throws IOException {
        try (Stream<Path> children = Files.list(folder)) {
            return children.map(
                            child ->
                                    new DirectoryEntry(
                                            child,
                                            child.getFileName().toString(),
                                            Files.isDirectory(child)))
                    .filter(
                            entry ->
                                    entry.directory()
                                            || all
                                            || entry.name()
                                                    .toLowerCase()
                                                    .endsWith(AssetFormat.EXTENSION))
                    .sorted(
                            Comparator.comparing((DirectoryEntry entry) -> !entry.directory())
                                    .thenComparing(
                                            DirectoryEntry::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }
}
