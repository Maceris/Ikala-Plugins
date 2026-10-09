package com.ikalagaming.converter.inspector;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Formats bytes as a classic hex dump: offset, 16 bytes in hex, then the printable characters. */
public final class HexDump {
    /** Bytes per line. */
    public static final int BYTES_PER_LINE = 16;

    /**
     * One line of a dump.
     *
     * @param offset The offset of the first byte, from the start of the data.
     * @param bytes The bytes on the line, as values from 0 to 255. Fewer than 16 on the last line.
     * @param text The bytes as text, with unprintable bytes shown as dots.
     */
    public record Line(int offset, int[] bytes, @NonNull String text) {
        /**
         * The offset as eight hex digits.
         *
         * @return The offset.
         */
        public String offsetText() {
            return String.format("%08X", offset);
        }
    }

    /**
     * Split bytes into dump lines, for showing in a table.
     *
     * @param data The bytes, from their position to their limit. The buffer is not modified.
     * @param maxBytes The most bytes to include.
     * @return The lines.
     */
    public static List<Line> lines(@NonNull ByteBuffer data, int maxBytes) {
        int shown = Math.min(data.remaining(), Math.max(0, maxBytes));
        int start = data.position();
        List<Line> lines = new ArrayList<>();
        for (int line = 0; line < shown; line += BYTES_PER_LINE) {
            int count = Math.min(BYTES_PER_LINE, shown - line);
            int[] bytes = new int[count];
            StringBuilder text = new StringBuilder(count);
            for (int i = 0; i < count; ++i) {
                bytes[i] = data.get(start + line + i) & 0xFF;
                text.append(bytes[i] >= 0x20 && bytes[i] <= 0x7E ? (char) bytes[i] : '.');
            }
            lines.add(new Line(line, bytes, text.toString()));
        }
        return lines;
    }

    /**
     * Format bytes as a hex dump.
     *
     * @param data The bytes, from their position to their limit. The buffer is not modified.
     * @param maxBytes The most bytes to show.
     * @return The dump, one line per 16 bytes, with a note at the end if it was cut short.
     */
    public static String format(@NonNull ByteBuffer data, int maxBytes) {
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (Line line : lines(data, maxBytes)) {
            out.append(line.offsetText()).append(' ');
            for (int i = 0; i < BYTES_PER_LINE; ++i) {
                if (i == BYTES_PER_LINE / 2) {
                    out.append(' ');
                }
                if (i < line.bytes().length) {
                    out.append(String.format(" %02X", line.bytes()[i]));
                } else {
                    out.append("   ");
                }
            }
            out.append("  |").append(line.text()).append("|\n");
            shown += line.bytes().length;
        }
        if (shown < data.remaining()) {
            out.append(String.format("... %d more bytes%n", data.remaining() - shown));
        }
        return out.toString();
    }

    /** Static helpers only. */
    private HexDump() {}
}
