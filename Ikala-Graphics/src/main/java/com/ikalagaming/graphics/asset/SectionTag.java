package com.ikalagaming.graphics.asset;

import lombok.NonNull;

/**
 * Four character section tags. A tag is stored as a little-endian int with the first character in
 * the lowest byte, so the characters read in order in a hex dump of the file.
 */
public final class SectionTag {
    /** Metadata about the container, see {@link AssetMetadata}. Required in every container. */
    public static final int META = of("META");

    /**
     * Convert four printable ASCII characters into a tag.
     *
     * @param name The tag name, exactly four printable ASCII characters.
     * @return The tag.
     * @throws IllegalArgumentException If the name is not four printable ASCII characters.
     */
    public static int of(@NonNull String name) {
        if (name.length() != 4) {
            throw new IllegalArgumentException("Section tags are four characters: " + name);
        }
        int tag = 0;
        for (int i = 0; i < 4; ++i) {
            char c = name.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new IllegalArgumentException(
                        "Section tags are printable ASCII characters: " + name);
            }
            tag |= c << (8 * i);
        }
        return tag;
    }

    /**
     * Convert a tag to readable text. Bytes that are not printable ASCII are shown as hex escapes,
     * so tags read from damaged files can still be shown.
     *
     * @param tag The tag.
     * @return The tag as text.
     */
    public static String toString(int tag) {
        StringBuilder result = new StringBuilder(4);
        for (int i = 0; i < 4; ++i) {
            int c = (tag >>> (8 * i)) & 0xFF;
            if (c >= 0x20 && c <= 0x7E) {
                result.append((char) c);
            } else {
                result.append(String.format("\\x%02X", c));
            }
        }
        return result.toString();
    }

    /** Constants and helpers only. */
    private SectionTag() {}
}
