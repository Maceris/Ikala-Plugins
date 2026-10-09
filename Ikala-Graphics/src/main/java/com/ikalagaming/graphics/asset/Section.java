package com.ikalagaming.graphics.asset;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One section of a container.
 *
 * @param tag The section tag, see {@link SectionTag}.
 * @param version The version of this section's layout, between 0 and 65535.
 * @param flags The section flags, such as {@link #FLAG_REQUIRED}.
 * @param data The payload. The accessor returns a new read-only little-endian view each time, so
 *     callers can move its position freely.
 */
public record Section(int tag, int version, int flags, @NonNull ByteBuffer data) {
    /**
     * Readers that do not know this section's tag or version must reject the file instead of
     * skipping the section.
     */
    public static final int FLAG_REQUIRED = 1;

    /**
     * Create a section.
     *
     * @param tag The section tag, see {@link SectionTag}.
     * @param version The version of this section's layout, between 0 and 65535.
     * @param flags The section flags, between 0 and 65535.
     * @param data The payload, from its position to its limit.
     */
    public Section {
        if (version < 0 || version > 0xFFFF) {
            throw new IllegalArgumentException("Section versions are 16 bits: " + version);
        }
        if (flags < 0 || flags > 0xFFFF) {
            throw new IllegalArgumentException("Section flags are 16 bits: " + flags);
        }
        data = data.slice().asReadOnlyBuffer();
    }

    @Override
    public ByteBuffer data() {
        // duplicate() resets the byte order to big-endian, so set it on every copy
        return data.duplicate().order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Whether readers must understand this section to use the file.
     *
     * @return Whether the required flag is set.
     */
    public boolean isRequired() {
        return (flags & FLAG_REQUIRED) != 0;
    }

    /**
     * The length of the payload.
     *
     * @return The payload length in bytes.
     */
    public int length() {
        return data.remaining();
    }

    @Override
    public String toString() {
        return String.format(
                "Section[tag=%s, version=%d, flags=%d, length=%d]",
                SectionTag.toString(tag), version, flags, length());
    }
}
