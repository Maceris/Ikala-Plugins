package com.ikalagaming.graphics.asset;

import static com.ikalagaming.graphics.asset.AssetFormat.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

class AssetValidatorInspectTest {

    private static final int DEMO = SectionTag.of("DEMO");
    private static final int FUTR = SectionTag.of("FUTR");

    /** META, then DEMO (known to this reader), FUTR (unknown, optional), and DEMO again. */
    private static ByteBuffer sample() {
        return new AssetWriter()
                .metadata(AssetMetadata.of("Test", "1"))
                .section(DEMO, 1, true, ByteBuffer.wrap(new byte[] {1, 2, 3, 4}))
                .section(FUTR, 7, false, ByteBuffer.wrap(new byte[] {5, 6}))
                .section(DEMO, 1, false, ByteBuffer.wrap(new byte[] {7, 8, 9}))
                .toBuffer()
                .order(ByteOrder.LITTLE_ENDIAN);
    }

    private static AssetValidator.Inspection inspect(ByteBuffer file) {
        return AssetValidator.inspect(file, KnownSections.graphics().with(DEMO, 1));
    }

    private static int entryBase(int index) {
        return HEADER_SIZE + index * TABLE_ENTRY_SIZE;
    }

    @Test
    void validFilesListEveryEntry() {
        AssetValidator.Inspection inspection = inspect(sample());

        assertTrue(inspection.isReadable());
        assertEquals("Test", inspection.metadata().exporter());
        List<AssetValidator.Entry> entries = inspection.entries();
        assertEquals(4, entries.size());
        for (AssetValidator.Entry entry : entries) {
            assertTrue(entry.crcMatches(), "Entry " + entry.index());
            assertNotNull(entry.section());
            assertEquals(entry.length(), entry.section().length());
        }
        assertTrue(entries.get(0).isRequired(), "META is always required");
        assertTrue(entries.get(1).known());
        assertFalse(entries.get(2).known(), "FUTR is unknown");
        assertEquals(7, entries.get(2).version());
    }

    @Test
    void checksumMismatchesAreOnTheRightEntry() {
        ByteBuffer file = sample();
        int offset = (int) file.getLong(entryBase(3) + ENTRY_OFFSET);
        file.put(offset, (byte) 0x7F);

        AssetValidator.Inspection inspection = inspect(file);

        assertFalse(inspection.isReadable());
        assertEquals(1, inspection.count(AssetValidator.Severity.ERROR));
        assertFalse(inspection.entries().get(3).crcMatches());
        assertNotNull(inspection.entries().get(3).actualCrc());
        assertNotNull(inspection.entries().get(3).section(), "Still readable for inspection");
        assertTrue(inspection.entries().get(1).crcMatches());
    }

    @Test
    void outOfBoundsEntriesHaveNoSectionButTheRestStillRead() {
        ByteBuffer file = sample();
        file.putLong(entryBase(2) + ENTRY_LENGTH, 1_000_000);

        AssetValidator.Inspection inspection = inspect(file);

        AssetValidator.Entry broken = inspection.entries().get(2);
        assertNull(broken.section());
        assertNull(broken.actualCrc());
        assertFalse(broken.crcMatches());
        assertEquals(1_000_000, broken.length());
        assertNotNull(inspection.entries().get(1).section());
        assertNotNull(inspection.entries().get(3).section());
        assertNotNull(inspection.metadata());
    }

    @Test
    void unreadableHeadersGiveNoEntries() {
        ByteBuffer file = sample();
        file.put(1, (byte) 'X');

        AssetValidator.Inspection inspection = inspect(file);

        assertNull(inspection.header());
        assertTrue(inspection.entries().isEmpty());
        assertEquals(1, inspection.count(AssetValidator.Severity.ERROR));
    }
}
