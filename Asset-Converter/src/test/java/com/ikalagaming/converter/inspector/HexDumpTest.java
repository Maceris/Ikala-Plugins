package com.ikalagaming.converter.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

class HexDumpTest {

    @Test
    void linesShowOffsetHexAndText() {
        ByteBuffer data =
                ByteBuffer.wrap("Hello, inspector!\u0001".getBytes(StandardCharsets.US_ASCII));

        String[] lines = HexDump.format(data, 512).split("\n");

        assertEquals(2, lines.length);
        assertEquals(
                "00000000  48 65 6C 6C 6F 2C 20 69  6E 73 70 65 63 74 6F 72  |Hello, inspector|",
                lines[0]);
        assertTrue(lines[1].startsWith("00000010  21 01"));
        assertTrue(lines[1].endsWith("|!.|"), "Unprintable bytes show as dots: " + lines[1]);
    }

    @Test
    void longDataIsCutShort() {
        ByteBuffer data = ByteBuffer.allocate(100);

        String dump = HexDump.format(data, 32);

        assertEquals(3, dump.split("\n").length);
        assertTrue(dump.contains("68 more bytes"));
    }

    @Test
    void theBufferIsNotMoved() {
        ByteBuffer data = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});
        data.position(2);

        String dump = HexDump.format(data, 16);

        assertEquals(2, data.position());
        assertTrue(dump.startsWith("00000000  03 04"), "Starts at the position: " + dump);
    }
}
