package com.ikalagaming.graphics.asset;

import static com.ikalagaming.graphics.asset.AssetFormat.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32C;

class AssetFileTest {

    private static final int VERT = SectionTag.of("VERT");
    private static final int NOTE = SectionTag.of("NOTE");
    private static final int FUTR = SectionTag.of("FUTR");
    private static final KnownSections KNOWN = KnownSections.graphics().with(VERT, 3);

    private static ByteBuffer bytes(int... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length);
        for (int value : values) {
            buffer.put((byte) value);
        }
        return buffer.flip();
    }

    private static ByteBuffer floats(float... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            buffer.putFloat(value);
        }
        return buffer.flip();
    }

    private static AssetWriter writer() {
        return new AssetWriter().metadata(AssetMetadata.of("Test", "1.2.3"));
    }

    /** A file with META, a required VERT, and two optional NOTE sections. */
    private static ByteBuffer sample() {
        return writer().section(VERT, 3, true, floats(1.5f, -2f, 3.25f))
                .section(NOTE, 1, false, bytes(1, 2, 3))
                .section(NOTE, 1, false, bytes(4, 5, 6, 7, 8))
                .toBuffer();
    }

    private static int entryBase(int index) {
        return HEADER_SIZE + index * TABLE_ENTRY_SIZE;
    }

    private static void fixCrc(ByteBuffer file, int index) {
        int base = entryBase(index);
        long offset = file.getLong(base + ENTRY_OFFSET);
        long length = file.getLong(base + ENTRY_LENGTH);
        CRC32C crc = new CRC32C();
        crc.update(file.slice((int) offset, (int) length));
        file.putInt(base + ENTRY_CRC, (int) crc.getValue());
    }

    private static boolean hasError(ByteBuffer file, String fragment) {
        return AssetValidator.validate(file, KNOWN).stream()
                .anyMatch(
                        p ->
                                p.severity() == AssetValidator.Severity.ERROR
                                        && p.message().contains(fragment));
    }

    @Test
    void roundTrip() {
        AssetFile file = AssetFile.read(sample(), KNOWN);

        assertEquals(FORMAT_MAJOR, file.header().formatMajor());
        assertEquals(FORMAT_MINOR, file.header().formatMinor());
        assertEquals("Test", file.metadata().exporter());
        assertEquals("1.2.3", file.metadata().exporterVersion());
        assertTrue(file.warnings().isEmpty());

        List<Section> sections = file.sections();
        assertEquals(
                List.of(SectionTag.META, VERT, NOTE, NOTE),
                sections.stream().map(Section::tag).toList());

        Section vert = file.section(VERT).orElseThrow();
        assertEquals(3, vert.version());
        assertTrue(vert.isRequired());
        assertEquals(floats(1.5f, -2f, 3.25f), vert.data());

        List<Section> notes = file.sections(NOTE);
        assertEquals(2, notes.size());
        assertFalse(notes.get(0).isRequired());
        assertEquals(bytes(1, 2, 3), notes.get(0).data());
        assertEquals(bytes(4, 5, 6, 7, 8), notes.get(1).data());
    }

    @Test
    void payloadsAreAligned() {
        ByteBuffer file = sample().order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 4; ++i) {
            assertEquals(0, file.getLong(entryBase(i) + ENTRY_OFFSET) % ALIGNMENT);
        }
    }

    @Test
    void sectionDataIsLittleEndianAndIndependent() {
        Section vert = AssetFile.read(sample(), KNOWN).section(VERT).orElseThrow();

        ByteBuffer first = vert.data();
        assertEquals(ByteOrder.LITTLE_ENDIAN, first.order());
        assertEquals(1.5f, first.getFloat());
        // Moving one view doesn't move the next one
        assertEquals(1.5f, vert.data().getFloat());
        assertTrue(vert.data().isReadOnly());
    }

    @Test
    void readDoesNotMoveTheBuffer() {
        ByteBuffer file = sample();
        AssetFile.read(file, KNOWN);
        assertEquals(0, file.position());
    }

    @Test
    void unknownOptionalSectionIsSkippable() {
        ByteBuffer file = writer().section(FUTR, 9, false, bytes(1)).toBuffer();
        assertTrue(AssetValidator.validate(file, KNOWN).isEmpty());
        assertEquals(1, AssetFile.read(file, KNOWN).sections(FUTR).size());
    }

    @Test
    void unknownRequiredSectionIsRejected() {
        ByteBuffer file = writer().section(FUTR, 9, true, bytes(1)).toBuffer();
        assertTrue(hasError(file, "FUTR"));
        assertThrows(AssetFormatException.class, () -> AssetFile.read(file, KNOWN));
    }

    @Test
    void readersOnlyAcceptSectionsTheyKnow() {
        ByteBuffer file = sample();
        // Graphics alone doesn't know VERT version 3, a reader that registers it does
        assertThrows(AssetFormatException.class, () -> AssetFile.read(file));
        assertEquals(4, AssetFile.read(file, KNOWN).sections().size());
        assertTrue(hasError(writer().section(VERT, 4, true, bytes()).toBuffer(), "VERT"));
    }

    @Test
    void newerMajorVersionIsRejected() {
        ByteBuffer file = sample().order(ByteOrder.LITTLE_ENDIAN);
        file.putShort(HEADER_FORMAT_MAJOR, (short) (FORMAT_MAJOR + 1));
        assertThrows(AssetFormatException.class, () -> AssetFile.read(file, KNOWN));
    }

    @Test
    void newerMinorVersionWarns() {
        ByteBuffer file = sample().order(ByteOrder.LITTLE_ENDIAN);
        file.putShort(HEADER_FORMAT_MINOR, (short) (FORMAT_MINOR + 1));
        AssetFile read = AssetFile.read(file, KNOWN);
        assertEquals(1, read.warnings().size());
        assertEquals(FORMAT_MINOR + 1, read.header().formatMinor());
    }

    /**
     * Build what a future minor version might write: a longer header and longer table entries, both
     * with extra fields this version doesn't know about.
     */
    @Test
    void largerHeaderAndEntriesAreRead() {
        final int extraHeader = 16;
        final int extraEntry = 8;
        AssetFile original = AssetFile.read(sample(), KNOWN);
        List<Section> sections = original.sections();

        int headerSize = HEADER_SIZE + extraHeader;
        int entrySize = TABLE_ENTRY_SIZE + extraEntry;
        long end = headerSize + (long) sections.size() * entrySize;
        long[] offsets = new long[sections.size()];
        for (int i = 0; i < sections.size(); ++i) {
            offsets[i] = align(end);
            end = offsets[i] + sections.get(i).length();
        }

        ByteBuffer file = ByteBuffer.allocate((int) end).order(ByteOrder.LITTLE_ENDIAN);
        file.put(0, MAGIC);
        file.putShort(HEADER_FORMAT_MAJOR, (short) FORMAT_MAJOR);
        file.putShort(HEADER_FORMAT_MINOR, (short) (FORMAT_MINOR + 1));
        file.putInt(HEADER_HEADER_SIZE, headerSize);
        file.putInt(HEADER_SECTION_COUNT, sections.size());
        file.putInt(HEADER_TABLE_ENTRY_SIZE, entrySize);
        file.putLong(HEADER_TABLE_OFFSET, headerSize);
        file.putLong(HEADER_FILE_LENGTH, end);
        file.putLong(HEADER_SIZE, -1L); // an unknown new header field
        for (int i = 0; i < sections.size(); ++i) {
            Section section = sections.get(i);
            int base = headerSize + i * entrySize;
            CRC32C crc = new CRC32C();
            crc.update(section.data());
            file.putInt(base + ENTRY_TAG, section.tag());
            file.putShort(base + ENTRY_VERSION, (short) section.version());
            file.putShort(base + ENTRY_FLAGS, (short) section.flags());
            file.putLong(base + ENTRY_OFFSET, offsets[i]);
            file.putLong(base + ENTRY_LENGTH, section.length());
            file.putInt(base + ENTRY_CRC, (int) crc.getValue());
            file.putLong(base + TABLE_ENTRY_SIZE, -1L); // an unknown new entry field
            file.put((int) offsets[i], section.data(), 0, section.length());
        }

        AssetFile read = AssetFile.read(file, KNOWN);
        assertEquals(original.sections(), read.sections());
        assertEquals(original.metadata(), read.metadata());
    }

    @Test
    void truncatedFileIsReported() {
        ByteBuffer file = sample();
        ByteBuffer truncated = file.slice(0, file.limit() - 1);
        assertTrue(hasError(truncated, "Header says the file is"));
        assertTrue(hasError(truncated, "past the end"));
        assertTrue(hasError(file.slice(0, 20), "too short for the header"));
        assertTrue(hasError(file.slice(0, 3), "too short to be a container"));
    }

    @Test
    void badMagicIsReported() {
        ByteBuffer file = sample();
        file.put(1, (byte) 'X');
        assertTrue(hasError(file, "magic"));
    }

    @Test
    void textModeMangledFileIsReported() {
        // A CRLF to LF conversion drops the 0D in the magic
        ByteBuffer file = sample();
        ByteBuffer mangled = ByteBuffer.allocate(file.limit() - 1);
        mangled.put(file.slice(0, 4)).put(file.slice(5, file.limit() - 5)).flip();
        assertTrue(hasError(mangled, "magic"));
    }

    @Test
    void sectionPastTheEndIsReported() {
        ByteBuffer file = sample().order(ByteOrder.LITTLE_ENDIAN);
        file.putLong(entryBase(1) + ENTRY_LENGTH, 1_000_000);
        assertTrue(hasError(file, "Section 1 (VERT)"));
        assertThrows(AssetFormatException.class, () -> AssetFile.read(file, KNOWN));
    }

    @Test
    void overlappingSectionsAreReported() {
        ByteBuffer file = sample().order(ByteOrder.LITTLE_ENDIAN);
        // Point the second NOTE at the first one's payload
        file.putLong(entryBase(3) + ENTRY_OFFSET, file.getLong(entryBase(2) + ENTRY_OFFSET));
        fixCrc(file, 3);
        assertTrue(hasError(file, "overlaps section"));
    }

    @Test
    void sectionOverTheTableIsReported() {
        ByteBuffer file = sample().order(ByteOrder.LITTLE_ENDIAN);
        file.putLong(entryBase(2) + ENTRY_OFFSET, HEADER_SIZE);
        fixCrc(file, 2);
        assertTrue(hasError(file, "overlaps the header or section table"));
    }

    @Test
    void misalignedSectionIsReported() {
        ByteBuffer file = sample().order(ByteOrder.LITTLE_ENDIAN);
        int base = entryBase(3);
        file.putLong(base + ENTRY_OFFSET, file.getLong(base + ENTRY_OFFSET) + 1);
        file.putLong(base + ENTRY_LENGTH, 1);
        fixCrc(file, 3);
        assertTrue(hasError(file, "not aligned"));
    }

    @Test
    void flippedPayloadByteIsReported() {
        ByteBuffer file = sample().order(ByteOrder.LITTLE_ENDIAN);
        int offset = (int) file.getLong(entryBase(1) + ENTRY_OFFSET);
        file.put(offset, (byte) (file.get(offset) ^ 0x10));
        assertTrue(hasError(file, "checksum"));
    }

    @Test
    void hugeSectionCountIsReported() {
        ByteBuffer file = sample().order(ByteOrder.LITTLE_ENDIAN);
        file.putInt(HEADER_SECTION_COUNT, -1);
        file.putInt(HEADER_TABLE_ENTRY_SIZE, -1);
        assertTrue(hasError(file, "Section table"));
    }

    @Test
    void metadataWithoutExporterIsReported() {
        ByteBuffer meta = new AssetMetadata(java.util.Map.of("other", "value")).encode();
        ByteBuffer file = sample().order(ByteOrder.LITTLE_ENDIAN);
        int base = entryBase(0);
        int offset = (int) file.getLong(base + ENTRY_OFFSET);
        // The replacement must fit where the original was
        assertTrue(meta.remaining() <= file.getLong(base + ENTRY_LENGTH));
        file.put(offset, meta, 0, meta.remaining());
        file.putLong(base + ENTRY_LENGTH, meta.remaining());
        fixCrc(file, 0);
        assertTrue(hasError(file, "exporter"));
    }

    @Test
    void missingMetadataIsReported() {
        ByteBuffer file = sample().order(ByteOrder.LITTLE_ENDIAN);
        file.putInt(entryBase(0) + ENTRY_TAG, SectionTag.of("XXXX"));
        assertTrue(hasError(file, "no META"));
    }

    @Test
    void validatorNeverThrowsOnGarbage() {
        java.util.Random random = new java.util.Random(1234);
        ByteBuffer good = sample();
        for (int i = 0; i < 2000; ++i) {
            ByteBuffer file = ByteBuffer.allocate(good.limit()).put(good.duplicate()).flip();
            // Corrupt a few bytes, mostly in the header and table where it matters most
            for (int j = 0; j < 4; ++j) {
                int at = random.nextInt(i % 2 == 0 ? Math.min(200, file.limit()) : file.limit());
                file.put(at, (byte) random.nextInt());
            }
            AssetValidator.validate(file);
        }
    }

    @Test
    void writerRequiresMetadata() {
        assertThrows(IllegalStateException.class, () -> new AssetWriter().toBuffer());
        assertThrows(
                IllegalArgumentException.class,
                () -> new AssetWriter().metadata(new AssetMetadata(java.util.Map.of())));
        assertThrows(
                IllegalArgumentException.class,
                () -> writer().section(SectionTag.META, 1, true, bytes()));
    }

    @Test
    void openMatchesRead(@TempDir Path directory) {
        Path path = directory.resolve("sample" + EXTENSION);
        writer().section(VERT, 3, true, floats(1.5f, -2f, 3.25f)).write(path);

        AssetFile opened = AssetFile.open(path, KNOWN);
        AssetFile read =
                AssetFile.read(
                        writer().section(VERT, 3, true, floats(1.5f, -2f, 3.25f)).toBuffer(),
                        KNOWN);
        assertEquals(read.sections(), opened.sections());
        assertEquals(read.metadata(), opened.metadata());
        // The file isn't held open, so the editor can export over an asset it has loaded
        writer().write(path);
        assertEquals(1, AssetFile.open(path, KNOWN).sections().size());
    }
}
