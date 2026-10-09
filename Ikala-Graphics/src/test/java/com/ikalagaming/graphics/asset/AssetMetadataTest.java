package com.ikalagaming.graphics.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

class AssetMetadataTest {

    @Test
    void roundTripKeepsOrderAndUnicode() {
        AssetMetadata metadata =
                AssetMetadata.of("Asset-Converter", "0.0.1")
                        .with(AssetMetadata.SOURCE, "models/sphère.obj")
                        .with("ключ", "値");

        AssetMetadata decoded = AssetMetadata.decode(metadata.encode());

        assertEquals(metadata, decoded);
        assertEquals(
                List.of(
                        AssetMetadata.EXPORTER,
                        AssetMetadata.EXPORTER_VERSION,
                        AssetMetadata.SOURCE,
                        "ключ"),
                List.copyOf(decoded.properties().keySet()));
        assertEquals("値", decoded.get("ключ"));
    }

    @Test
    void lengthsAreLittleEndian() {
        ByteBuffer encoded = AssetMetadata.of("a", "b").encode();
        assertEquals(ByteOrder.LITTLE_ENDIAN, encoded.order());
        assertEquals(2, encoded.get(0));
        assertEquals(0, encoded.get(3));
    }

    @Test
    void malformedPayloadsAreRejected() {
        ByteBuffer good = AssetMetadata.of("a", "b").encode();

        ByteBuffer truncated = good.slice(0, good.limit() - 1);
        assertThrows(AssetFormatException.class, () -> AssetMetadata.decode(truncated));

        ByteBuffer hugeCount = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0, -1);
        assertThrows(AssetFormatException.class, () -> AssetMetadata.decode(hugeCount));

        ByteBuffer trailing =
                ByteBuffer.allocate(good.limit() + 1).put(good.duplicate()).put((byte) 0).flip();
        assertThrows(AssetFormatException.class, () -> AssetMetadata.decode(trailing));

        ByteBuffer badUtf8 =
                ByteBuffer.allocate(4 + 4 + 1 + 4 + 1)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(1)
                        .putInt(1)
                        .put((byte) 0xFF)
                        .putInt(1)
                        .put((byte) 'x')
                        .flip();
        assertThrows(AssetFormatException.class, () -> AssetMetadata.decode(badUtf8));

        ByteBuffer repeated =
                ByteBuffer.allocate(4 + 2 * (4 + 1 + 4 + 1))
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(2)
                        .putInt(1)
                        .put((byte) 'k')
                        .putInt(1)
                        .put((byte) 'v')
                        .putInt(1)
                        .put((byte) 'k')
                        .putInt(1)
                        .put((byte) 'w')
                        .flip();
        assertThrows(AssetFormatException.class, () -> AssetMetadata.decode(repeated));
    }

    @Test
    void tagsRoundTrip() {
        int tag = SectionTag.of("VERT");
        assertEquals("VERT", SectionTag.toString(tag));
        // First character in the lowest byte, so the file reads "VERT" in a hex dump
        assertEquals('V', tag & 0xFF);
        assertEquals("\\x00\\x01AB", SectionTag.toString(0x4241_0100));
        assertThrows(IllegalArgumentException.class, () -> SectionTag.of("TOOLONG"));
        assertThrows(IllegalArgumentException.class, () -> SectionTag.of("abéc"));
    }
}
