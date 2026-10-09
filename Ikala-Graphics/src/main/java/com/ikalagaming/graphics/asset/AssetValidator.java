package com.ikalagaming.graphics.asset;

import static com.ikalagaming.graphics.asset.AssetFormat.*;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32C;
import javax.annotation.Nullable;

/**
 * Checks containers against the format. This is the only place the format rules live: {@link
 * AssetFile} reads through it, so anything the validator accepts can be read.
 */
public final class AssetValidator {
    /** How serious a problem is. */
    public enum Severity {
        /** The file can still be read, but something is unusual. */
        WARNING,
        /** The file can't be read. */
        ERROR
    }

    /**
     * Something wrong with a container.
     *
     * @param severity How serious the problem is.
     * @param message A description of the problem.
     */
    public record Problem(@NonNull Severity severity, @NonNull String message) {
        @Override
        public String toString() {
            return severity + ": " + message;
        }
    }

    /**
     * One row of a container's section table, as found in the file, for tools that show broken
     * files.
     *
     * @param index The position in the table.
     * @param tag The section tag.
     * @param version The section version.
     * @param flags The section flags.
     * @param offset The payload offset from the start of the file.
     * @param length The payload length.
     * @param storedCrc The CRC-32C the table says the payload has.
     * @param actualCrc The CRC-32C the payload really has, or null if it is outside the file.
     * @param known Whether the reader understands this tag and version.
     * @param section The section, or null if its payload is outside the file.
     */
    public record Entry(
            int index,
            int tag,
            int version,
            int flags,
            long offset,
            long length,
            int storedCrc,
            @Nullable Integer actualCrc,
            boolean known,
            @Nullable Section section) {

        /**
         * Whether readers must understand this section to use the file.
         *
         * @return Whether the required flag is set.
         */
        public boolean isRequired() {
            return (flags & Section.FLAG_REQUIRED) != 0;
        }

        /**
         * Whether the payload is inside the file and matches its checksum.
         *
         * @return True if the checksum is right.
         */
        public boolean crcMatches() {
            return actualCrc != null && actualCrc == storedCrc;
        }
    }

    /**
     * Everything that could be read from a container, whether or not it is valid.
     *
     * @param header The header, or null if it couldn't be read.
     * @param entries Every row of the section table, or none if the table couldn't be read.
     * @param metadata The metadata, or null if it is missing or broken.
     * @param problems Every problem found.
     */
    public record Inspection(
            @Nullable AssetHeader header,
            @NonNull List<Entry> entries,
            @Nullable AssetMetadata metadata,
            @NonNull List<Problem> problems) {

        /**
         * Count the problems of one severity.
         *
         * @param severity The severity to count.
         * @return How many problems have it.
         */
        public long count(@NonNull Severity severity) {
            return problems.stream().filter(p -> p.severity() == severity).count();
        }

        /**
         * Whether the file can be read.
         *
         * @return True if there are no errors.
         */
        public boolean isReadable() {
            return count(Severity.ERROR) == 0;
        }
    }

    /**
     * Everything learned while checking a file.
     *
     * @param header The header, or null if it couldn't be read.
     * @param sections The sections that could be located, in table order.
     * @param metadata The metadata, or null if it is missing or broken.
     * @param problems Every problem found.
     */
    record Result(
            AssetHeader header,
            List<Section> sections,
            AssetMetadata metadata,
            List<Problem> problems) {
        /**
         * Find the first error.
         *
         * @return The first error, or null if there are none.
         */
        Problem firstError() {
            return problems.stream()
                    .filter(p -> p.severity() == Severity.ERROR)
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * A table entry, as read from the file.
     *
     * @param index The position in the table.
     * @param tag The section tag.
     * @param version The section version.
     * @param flags The section flags.
     * @param offset The payload offset from the start of the file.
     * @param length The payload length.
     * @param crc The expected CRC-32C of the payload.
     */
    private record Row(
            int index, int tag, int version, int flags, long offset, long length, int crc) {
        String describe() {
            return "Section " + index + " (" + SectionTag.toString(tag) + ")";
        }
    }

    /**
     * Check a container for problems. Never throws for a malformed file.
     *
     * @param file The whole file, from its position to its limit. The buffer is not modified.
     * @return Every problem found, empty if the file is fine.
     */
    public static List<Problem> validate(@NonNull ByteBuffer file) {
        return validate(file, KnownSections.graphics());
    }

    /**
     * Check a container for problems, for a reader that knows a different set of sections. Never
     * throws for a malformed file.
     *
     * @param file The whole file, from its position to its limit. The buffer is not modified.
     * @param known The sections the reader understands.
     * @return Every problem found, empty if the file is fine.
     */
    public static List<Problem> validate(@NonNull ByteBuffer file, @NonNull KnownSections known) {
        return check(file, known).problems();
    }

    /**
     * Check a container and collect what could be read from it.
     *
     * @param file The whole file, from its position to its limit. The buffer is not modified.
     * @param known The sections the reader understands.
     * @return What was found.
     */
    static Result check(@NonNull ByteBuffer file, @NonNull KnownSections known) {
        Inspection inspection = inspect(file, known);
        List<Section> sections =
                inspection.entries().stream()
                        .map(Entry::section)
                        .filter(section -> section != null)
                        .toList();
        return new Result(
                inspection.header(), sections, inspection.metadata(), inspection.problems());
    }

    /**
     * Read as much of a container as possible, even a broken one, and report every problem. Never
     * throws for a malformed file. For tools that show files, like the asset inspector.
     *
     * @param file The whole file, from its position to its limit. The buffer is not modified.
     * @param known The sections the reader understands.
     * @return What was found.
     */
    public static Inspection inspect(@NonNull ByteBuffer file, @NonNull KnownSections known) {
        ByteBuffer buffer = file.slice().order(ByteOrder.LITTLE_ENDIAN);
        List<Problem> problems = new ArrayList<>();
        List<Section> sections = new ArrayList<>();
        List<Entry> found = new ArrayList<>();
        AssetHeader header = readHeader(buffer, problems);
        if (header == null) {
            return new Inspection(null, List.of(), null, problems);
        }

        List<Row> entries = readTable(buffer, problems);
        if (entries == null) {
            return new Inspection(header, List.of(), null, problems);
        }

        long tableEnd =
                buffer.getLong(HEADER_TABLE_OFFSET)
                        + (long) entries.size() * buffer.getInt(HEADER_TABLE_ENTRY_SIZE);
        long headerEnd = Integer.toUnsignedLong(buffer.getInt(HEADER_HEADER_SIZE));
        List<Row> inBounds = new ArrayList<>();
        for (Row entry : entries) {
            Integer actualCrc = checkEntry(buffer, entry, known, problems);
            Section section = null;
            if (actualCrc != null) {
                inBounds.add(entry);
                ByteBuffer payload = buffer.slice((int) entry.offset(), (int) entry.length());
                section = new Section(entry.tag(), entry.version(), entry.flags(), payload);
                sections.add(section);
            }
            found.add(
                    new Entry(
                            entry.index(),
                            entry.tag(),
                            entry.version(),
                            entry.flags(),
                            entry.offset(),
                            entry.length(),
                            entry.crc(),
                            actualCrc,
                            known.isKnown(entry.tag(), entry.version()),
                            section));
        }
        checkOverlaps(inBounds, headerEnd, tableEnd, problems);
        AssetMetadata metadata = checkMetadata(sections, problems);
        return new Inspection(header, List.copyOf(found), metadata, problems);
    }

    /**
     * Read and check the header.
     *
     * @param buffer The file.
     * @param problems Where to report problems.
     * @return The header, or null if the rest of the file can't be located.
     */
    private static AssetHeader readHeader(ByteBuffer buffer, List<Problem> problems) {
        byte[] magic = new byte[MAGIC.length];
        if (buffer.remaining() < magic.length) {
            error(
                    problems,
                    "File is " + buffer.remaining() + " bytes, too short to be a container");
            return null;
        }
        buffer.get(0, magic);
        if (!Arrays.equals(magic, MAGIC)) {
            error(problems, "Not an asset container, the magic bytes don't match");
            return null;
        }
        if (buffer.remaining() < HEADER_SIZE) {
            error(problems, "File is " + buffer.remaining() + " bytes, too short for the header");
            return null;
        }

        int major = Short.toUnsignedInt(buffer.getShort(HEADER_FORMAT_MAJOR));
        int minor = Short.toUnsignedInt(buffer.getShort(HEADER_FORMAT_MINOR));
        if (major == 0 || major > FORMAT_MAJOR) {
            error(
                    problems,
                    "Format version %d.%d is not supported, %d.x is the newest readable",
                    major,
                    minor,
                    FORMAT_MAJOR);
            return null;
        }
        if (major == FORMAT_MAJOR && minor > FORMAT_MINOR) {
            problems.add(
                    new Problem(
                            Severity.WARNING,
                            String.format(
                                    "Format version %d.%d is newer than %d.%d, newer fields are"
                                            + " ignored",
                                    major, minor, FORMAT_MAJOR, FORMAT_MINOR)));
        }

        long headerSize = Integer.toUnsignedLong(buffer.getInt(HEADER_HEADER_SIZE));
        if (headerSize < HEADER_SIZE || headerSize > buffer.remaining()) {
            error(problems, "Header size %d is out of range", headerSize);
            return null;
        }

        long fileLength = buffer.getLong(HEADER_FILE_LENGTH);
        if (fileLength != buffer.remaining()) {
            error(
                    problems,
                    "Header says the file is %s bytes, but it is %d bytes",
                    Long.toUnsignedString(fileLength),
                    buffer.remaining());
        }
        int flags = buffer.getInt(HEADER_FLAGS);
        return new AssetHeader(major, minor, flags, fileLength);
    }

    /**
     * Read the section table.
     *
     * @param buffer The file.
     * @param problems Where to report problems.
     * @return The entries, or null if the table itself is out of bounds.
     */
    private static @Nullable List<Row> readTable(ByteBuffer buffer, List<Problem> problems) {
        long count = Integer.toUnsignedLong(buffer.getInt(HEADER_SECTION_COUNT));
        long entrySize = Integer.toUnsignedLong(buffer.getInt(HEADER_TABLE_ENTRY_SIZE));
        long tableOffset = buffer.getLong(HEADER_TABLE_OFFSET);
        if (entrySize < TABLE_ENTRY_SIZE) {
            error(problems, "Table entry size %d is smaller than %d", entrySize, TABLE_ENTRY_SIZE);
            return null;
        }
        // Both are unsigned 32 bit values, so the product can overflow a long
        long tableSize;
        try {
            tableSize = Math.multiplyExact(count, entrySize);
        } catch (ArithmeticException e) {
            tableSize = Long.MAX_VALUE;
        }
        if (tableOffset < 0
                || tableOffset > buffer.remaining()
                || tableSize > buffer.remaining() - tableOffset) {
            error(
                    problems,
                    "Section table of %d entries at offset %s runs past the end of the file",
                    count,
                    Long.toUnsignedString(tableOffset));
            return null;
        }

        List<Row> entries = new ArrayList<>((int) count);
        for (int i = 0; i < count; ++i) {
            int base = (int) (tableOffset + i * entrySize);
            entries.add(
                    new Row(
                            i,
                            buffer.getInt(base + ENTRY_TAG),
                            Short.toUnsignedInt(buffer.getShort(base + ENTRY_VERSION)),
                            Short.toUnsignedInt(buffer.getShort(base + ENTRY_FLAGS)),
                            buffer.getLong(base + ENTRY_OFFSET),
                            buffer.getLong(base + ENTRY_LENGTH),
                            buffer.getInt(base + ENTRY_CRC)));
        }
        return entries;
    }

    /**
     * Check one table entry: bounds, alignment, checksum, and whether it must be understood.
     *
     * @param buffer The file.
     * @param entry The entry.
     * @param known The sections the reader understands.
     * @param problems Where to report problems.
     * @return The payload's actual CRC-32C, or null if the payload is outside the file.
     */
    private static @Nullable Integer checkEntry(
            ByteBuffer buffer, Row entry, KnownSections known, List<Problem> problems) {
        if (entry.offset() < 0
                || entry.length() < 0
                || entry.offset() > buffer.remaining()
                || entry.length() > buffer.remaining() - entry.offset()) {
            error(
                    problems,
                    "%s at offset %s with length %s runs past the end of the file",
                    entry.describe(),
                    Long.toUnsignedString(entry.offset()),
                    Long.toUnsignedString(entry.length()));
            return null;
        }
        if (entry.offset() % ALIGNMENT != 0) {
            error(
                    problems,
                    "%s at offset %d is not aligned to %d bytes",
                    entry.describe(),
                    entry.offset(),
                    ALIGNMENT);
        }

        CRC32C crc = new CRC32C();
        crc.update(buffer.slice((int) entry.offset(), (int) entry.length()));
        if ((int) crc.getValue() != entry.crc()) {
            error(
                    problems,
                    "%s checksum is %08X, expected %08X",
                    entry.describe(),
                    (int) crc.getValue(),
                    entry.crc());
        }

        boolean required = (entry.flags() & Section.FLAG_REQUIRED) != 0;
        if (required && !known.isKnown(entry.tag(), entry.version())) {
            error(
                    problems,
                    "%s version %d is required but not supported",
                    entry.describe(),
                    entry.version());
        }
        return (int) crc.getValue();
    }

    /**
     * Check that no two payloads overlap each other, the header, or the table.
     *
     * @param entries The entries whose payloads are inside the file.
     * @param headerEnd The end of the header.
     * @param tableEnd The end of the section table.
     * @param problems Where to report problems.
     */
    private static void checkOverlaps(
            List<Row> entries, long headerEnd, long tableEnd, List<Problem> problems) {
        List<Row> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(Row::offset));
        // Empty payloads take no space, so they can't overlap anything
        sorted.removeIf(entry -> entry.length() == 0);
        long reservedEnd = Math.max(headerEnd, tableEnd);
        Row previous = null;
        for (Row entry : sorted) {
            if (entry.offset() < reservedEnd) {
                error(problems, "%s overlaps the header or section table", entry.describe());
            }
            if (previous != null && entry.offset() < previous.offset() + previous.length()) {
                error(
                        problems,
                        "%s overlaps section %d (%s)",
                        entry.describe(),
                        previous.index(),
                        SectionTag.toString(previous.tag()));
            }
            previous = entry;
        }
    }

    /**
     * Check that there is exactly one valid META section with the required keys.
     *
     * @param sections The readable sections.
     * @param problems Where to report problems.
     * @return The metadata, or null if it is missing or broken.
     */
    private static AssetMetadata checkMetadata(List<Section> sections, List<Problem> problems) {
        List<Section> meta = sections.stream().filter(s -> s.tag() == SectionTag.META).toList();
        if (meta.isEmpty()) {
            error(problems, "There is no META section");
            return null;
        }
        if (meta.size() > 1) {
            error(problems, "There are %d META sections, expected one", meta.size());
        }
        Section section = meta.getFirst();
        if (section.version() != AssetMetadata.VERSION) {
            // A required META of an unknown version is reported with the other required sections
            if (!section.isRequired()) {
                error(problems, "META version %d is not supported", section.version());
            }
            return null;
        }
        AssetMetadata metadata;
        try {
            metadata = AssetMetadata.decode(section.data());
        } catch (AssetFormatException e) {
            error(problems, "META is malformed: %s", e.getMessage());
            return null;
        }
        for (String key : List.of(AssetMetadata.EXPORTER, AssetMetadata.EXPORTER_VERSION)) {
            if (metadata.get(key) == null) {
                error(problems, "META is missing the required key '%s'", key);
            }
        }
        return metadata;
    }

    /**
     * Report an error.
     *
     * @param problems Where to report it.
     * @param format The message format.
     * @param args The message arguments.
     */
    private static void error(List<Problem> problems, String format, Object... args) {
        problems.add(new Problem(Severity.ERROR, String.format(format, args)));
    }

    /** Static checks only. */
    private AssetValidator() {}
}
