package com.ikalagaming.graphics.asset;

import static com.ikalagaming.graphics.asset.AssetFormat.*;

import lombok.NonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32C;

/**
 * Builds a container. Add the metadata and sections, then write it out. The META section is always
 * written first, then the other sections in the order they were added.
 *
 * <p>Not thread safe.
 */
public final class AssetWriter {
    /** The metadata, required before writing. */
    private AssetMetadata metadata;

    /** Sections other than META, in order. */
    private final List<Section> sections = new ArrayList<>();

    /**
     * Set the metadata.
     *
     * @param metadata The metadata, which must include the exporter and exporter version.
     * @return This writer.
     * @throws IllegalArgumentException If a required key is missing.
     */
    public AssetWriter metadata(@NonNull AssetMetadata metadata) {
        if (metadata.exporter() == null || metadata.exporterVersion() == null) {
            throw new IllegalArgumentException(
                    "Metadata needs both an exporter and an exporter version");
        }
        this.metadata = metadata;
        return this;
    }

    /**
     * Add a section. The data is not copied until the container is written, so it must not change
     * before then.
     *
     * @param tag The section tag, see {@link SectionTag}. META is set through {@link
     *     #metadata(AssetMetadata)} instead.
     * @param version The version of the section's layout, between 0 and 65535.
     * @param required Whether readers must understand this section to use the file.
     * @param data The payload, from its position to its limit.
     * @return This writer.
     */
    public AssetWriter section(int tag, int version, boolean required, @NonNull ByteBuffer data) {
        if (tag == SectionTag.META) {
            throw new IllegalArgumentException("Set META through metadata(), not as a section");
        }
        sections.add(new Section(tag, version, required ? Section.FLAG_REQUIRED : 0, data));
        return this;
    }

    /**
     * Build the container in memory.
     *
     * @return A new little-endian buffer, positioned at 0, holding the whole file.
     * @throws IllegalStateException If no metadata was set.
     * @throws AssetFormatException If the container would be larger than 2 GiB.
     */
    public ByteBuffer toBuffer() {
        if (metadata == null) {
            throw new IllegalStateException("Containers need metadata, set it before writing");
        }
        List<Section> all = new ArrayList<>(sections.size() + 1);
        all.add(
                new Section(
                        SectionTag.META,
                        AssetMetadata.VERSION,
                        Section.FLAG_REQUIRED,
                        metadata.encode()));
        all.addAll(sections);

        long tableOffset = HEADER_SIZE;
        long[] offsets = new long[all.size()];
        long end = tableOffset + (long) all.size() * TABLE_ENTRY_SIZE;
        for (int i = 0; i < all.size(); ++i) {
            offsets[i] = align(end);
            end = offsets[i] + all.get(i).length();
        }
        if (end > Integer.MAX_VALUE) {
            throw new AssetFormatException(
                    "Container would be " + end + " bytes, larger than supported");
        }

        ByteBuffer file = ByteBuffer.allocate((int) end).order(ByteOrder.LITTLE_ENDIAN);
        file.put(0, MAGIC);
        file.putShort(HEADER_FORMAT_MAJOR, (short) FORMAT_MAJOR);
        file.putShort(HEADER_FORMAT_MINOR, (short) FORMAT_MINOR);
        file.putInt(HEADER_HEADER_SIZE, HEADER_SIZE);
        file.putInt(HEADER_FLAGS, 0);
        file.putInt(HEADER_SECTION_COUNT, all.size());
        file.putInt(HEADER_TABLE_ENTRY_SIZE, TABLE_ENTRY_SIZE);
        file.putLong(HEADER_TABLE_OFFSET, tableOffset);
        file.putLong(HEADER_FILE_LENGTH, end);

        for (int i = 0; i < all.size(); ++i) {
            Section section = all.get(i);
            int entry = (int) (tableOffset + (long) i * TABLE_ENTRY_SIZE);
            CRC32C crc = new CRC32C();
            crc.update(section.data());
            file.putInt(entry + ENTRY_TAG, section.tag());
            file.putShort(entry + ENTRY_VERSION, (short) section.version());
            file.putShort(entry + ENTRY_FLAGS, (short) section.flags());
            file.putLong(entry + ENTRY_OFFSET, offsets[i]);
            file.putLong(entry + ENTRY_LENGTH, section.length());
            file.putInt(entry + ENTRY_CRC, (int) crc.getValue());
            file.put((int) offsets[i], section.data(), 0, section.length());
        }
        return file;
    }

    /**
     * Write the container to a file, replacing it if it exists.
     *
     * @param path Where to write the container.
     * @throws IllegalStateException If no metadata was set.
     * @throws UncheckedIOException If the file can't be written.
     */
    public void write(@NonNull Path path) {
        ByteBuffer file = toBuffer();
        try (FileChannel channel =
                FileChannel.open(
                        path,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
            while (file.hasRemaining()) {
                channel.write(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + path, e);
        }
    }
}
