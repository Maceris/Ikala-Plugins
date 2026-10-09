package com.ikalagaming.graphics.asset;

import lombok.NonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

/**
 * A container that has been read and checked. Section payloads are views of the original buffer, so
 * nothing is copied after the file is read.
 *
 * <p>Sections whose tag or version graphics doesn't know are kept, so tools can still show them.
 * Readers ask for the tags they understand and ignore the rest.
 */
public final class AssetFile {
    /** The header. */
    private final AssetHeader header;

    /** All sections, in table order. */
    private final List<Section> sections;

    /** The META section contents. */
    private final AssetMetadata metadata;

    /** Warnings from reading the file. */
    private final List<AssetValidator.Problem> warnings;

    /**
     * Wrap a checked file.
     *
     * @param result The validator result, which has no errors.
     */
    private AssetFile(AssetValidator.Result result) {
        header = result.header();
        sections = result.sections();
        metadata = result.metadata();
        warnings = List.copyOf(result.problems());
    }

    /**
     * Read a container that is already in memory, for example a resource loaded from a plugin.
     *
     * @param file The whole file, from its position to its limit. The buffer is not modified, and
     *     must not be changed while sections are in use.
     * @return The container.
     * @throws AssetFormatException If the file breaks the format.
     */
    public static AssetFile read(@NonNull ByteBuffer file) {
        return read(file, KnownSections.graphics());
    }

    /**
     * Read a container that is already in memory, for a reader that knows a different set of
     * sections.
     *
     * @param file The whole file, from its position to its limit. The buffer is not modified, and
     *     must not be changed while sections are in use.
     * @param known The sections the reader understands.
     * @return The container.
     * @throws AssetFormatException If the file breaks the format.
     */
    public static AssetFile read(@NonNull ByteBuffer file, @NonNull KnownSections known) {
        AssetValidator.Result result = AssetValidator.check(file, known);
        AssetValidator.Problem error = result.firstError();
        if (error != null) {
            throw new AssetFormatException(error.message());
        }
        return new AssetFile(result);
    }

    /**
     * Read a container from a file. Files over 2 GiB are not supported yet.
     *
     * <p>The file is read into memory rather than memory-mapped, because on Windows a mapped file
     * can't be overwritten or deleted until the garbage collector unmaps it, which would stop the
     * editor from exporting over an asset it has open. Revisit once arena-scoped mappings that can
     * be closed explicitly are available (Java 22).
     *
     * @param path The file to read.
     * @return The container.
     * @throws AssetFormatException If the file breaks the format.
     * @throws UncheckedIOException If the file can't be read.
     */
    public static AssetFile open(@NonNull Path path) {
        return open(path, KnownSections.graphics());
    }

    /**
     * Read a container from a file, for a reader that knows a different set of sections. Files over
     * 2 GiB are not supported yet.
     *
     * @param path The file to read.
     * @param known The sections the reader understands.
     * @return The container.
     * @throws AssetFormatException If the file breaks the format.
     * @throws UncheckedIOException If the file can't be read.
     */
    public static AssetFile open(@NonNull Path path, @NonNull KnownSections known) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size > Integer.MAX_VALUE) {
                throw new AssetFormatException(
                        path + " is " + size + " bytes, larger than supported");
            }
            ByteBuffer file = ByteBuffer.allocate((int) size);
            while (file.hasRemaining()) {
                if (channel.read(file) < 0) {
                    throw new AssetFormatException(path + " got shorter while it was read");
                }
            }
            return read(file.flip(), known);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }

    /**
     * The file header.
     *
     * @return The header.
     */
    public AssetHeader header() {
        return header;
    }

    /**
     * The contents of the META section.
     *
     * @return The metadata.
     */
    public AssetMetadata metadata() {
        return metadata;
    }

    /**
     * Every section, in the order they appear in the table, including META and any unknown ones.
     *
     * @return The sections.
     */
    public List<Section> sections() {
        return sections;
    }

    /**
     * Find the first section with a tag.
     *
     * @param tag The tag to look for.
     * @return The first section with that tag, if any.
     */
    public Optional<Section> section(int tag) {
        return sections.stream().filter(s -> s.tag() == tag).findFirst();
    }

    /**
     * Find every section with a tag.
     *
     * @param tag The tag to look for.
     * @return The sections with that tag, in table order.
     */
    public List<Section> sections(int tag) {
        return sections.stream().filter(s -> s.tag() == tag).toList();
    }

    /**
     * Warnings found while reading, such as a newer minor format version.
     *
     * @return The warnings, empty if there were none.
     */
    public List<AssetValidator.Problem> warnings() {
        return warnings;
    }
}
