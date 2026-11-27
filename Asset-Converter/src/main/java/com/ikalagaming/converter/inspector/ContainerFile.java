package com.ikalagaming.converter.inspector;

import com.ikalagaming.graphics.asset.AssetHeader;
import com.ikalagaming.graphics.asset.AssetMetadata;
import com.ikalagaming.graphics.asset.AssetValidator;
import com.ikalagaming.graphics.asset.KnownSections;

import lombok.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@code .ika} container read whole and inspected: its header, metadata, sections and problems.
 * Broken files are still read, so their problems can be shown. Has no UI, so it can be tested; the
 * workbench's container viewer shows it.
 *
 * @param path The file, absolute.
 * @param size The file's size in bytes.
 * @param inspection What was read from it.
 */
public record ContainerFile(
        @NonNull Path path, long size, @NonNull AssetValidator.Inspection inspection) {
    /** Files bigger than this are not read, since they are read into memory. */
    public static final long MAX_FILE_SIZE = 64L * 1024 * 1024;

    /**
     * Read and inspect a container.
     *
     * @param path The file.
     * @return What was read.
     * @throws IOException If it can't be read or is too large.
     */
    public static ContainerFile read(@NonNull Path path) throws IOException {
        final Path absolute = path.toAbsolutePath().normalize();
        final long size = Files.size(absolute);
        if (size > MAX_FILE_SIZE) {
            throw new IOException(
                    String.format(
                            "%s is %,d bytes, too large to inspect", absolute.getFileName(), size));
        }
        final byte[] bytes = Files.readAllBytes(absolute);
        return new ContainerFile(
                absolute,
                bytes.length,
                AssetValidator.inspect(ByteBuffer.wrap(bytes), KnownSections.graphics()));
    }

    /**
     * A one-line summary: the size, format version and exporter.
     *
     * @return For example "1,234 bytes, format 1.0, from Asset-Converter 1.2".
     */
    public String summary() {
        final StringBuilder text = new StringBuilder(String.format("%,d bytes", size));
        final AssetHeader header = inspection.header();
        if (header != null) {
            text.append(
                    String.format(", format %d.%d", header.formatMajor(), header.formatMinor()));
        }
        final AssetMetadata metadata = inspection.metadata();
        if (metadata != null && metadata.exporter() != null) {
            text.append(", from ").append(metadata.exporter());
            if (metadata.exporterVersion() != null) {
                text.append(' ').append(metadata.exporterVersion());
            }
        }
        return text.toString();
    }

    /**
     * Whether it can be read, and how many errors and warnings it has.
     *
     * @return For example "OK", "Readable, 1 warning" or "Not readable: 2 errors, 0 warnings".
     */
    public String status() {
        final long warnings = inspection.count(AssetValidator.Severity.WARNING);
        if (inspection.isReadable()) {
            return warnings == 0 ? "OK" : "Readable, " + plural(warnings, "warning");
        }
        return "Not readable: "
                + plural(inspection.count(AssetValidator.Severity.ERROR), "error")
                + ", "
                + plural(warnings, "warning");
    }

    /**
     * A count and a noun, plural unless the count is one.
     *
     * @param count The count.
     * @param noun The singular noun.
     * @return For example "1 error" or "2 errors".
     */
    static String plural(long count, @NonNull String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
