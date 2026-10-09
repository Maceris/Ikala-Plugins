package com.ikalagaming.graphics.asset;

import lombok.NonNull;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The contents of the {@link SectionTag#META META} section: string properties describing the
 * container. New keys can be added at any time without changing the section version.
 *
 * <p>Layout of version 1: a u32 count, then that many key and value pairs. Each string is a u32
 * byte length followed by that many bytes of UTF-8.
 *
 * @param properties The properties, in the order they are written.
 */
public record AssetMetadata(@NonNull Map<String, String> properties) {
    /** The version of the META section layout. */
    public static final int VERSION = 1;

    /** Required. The name of the program or plugin that wrote the container. */
    public static final String EXPORTER = "exporter";

    /** Required. The version of the exporter. */
    public static final String EXPORTER_VERSION = "exporter.version";

    /** Optional. The file the asset was converted from. */
    public static final String SOURCE = "source";

    /** Optional. A hash of the source file, used to tell when it needs converting again. */
    public static final String SOURCE_HASH = "source.hash";

    /** Optional. When the container was written, as an ISO-8601 instant. */
    public static final String CREATED = "created";

    /**
     * Create metadata from properties.
     *
     * @param properties The properties, copied in iteration order. Keys and values may not be null.
     */
    public AssetMetadata {
        Map<String, String> copy = new LinkedHashMap<>();
        properties.forEach(
                (key, value) -> {
                    if (key == null || value == null) {
                        throw new NullPointerException("Metadata keys and values may not be null");
                    }
                    copy.put(key, value);
                });
        properties = Collections.unmodifiableMap(copy);
    }

    /**
     * Create metadata with just the required properties.
     *
     * @param exporter The name of the exporter.
     * @param exporterVersion The version of the exporter.
     * @return The new metadata.
     */
    public static AssetMetadata of(@NonNull String exporter, @NonNull String exporterVersion) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put(EXPORTER, exporter);
        properties.put(EXPORTER_VERSION, exporterVersion);
        return new AssetMetadata(properties);
    }

    /**
     * Create a copy with a property added or replaced.
     *
     * @param key The property key.
     * @param value The property value.
     * @return The new metadata.
     */
    public AssetMetadata with(@NonNull String key, @NonNull String value) {
        Map<String, String> copy = new LinkedHashMap<>(properties);
        copy.put(key, value);
        return new AssetMetadata(copy);
    }

    /**
     * Look up a property.
     *
     * @param key The property key.
     * @return The value, or null if it is not present.
     */
    public String get(@NonNull String key) {
        return properties.get(key);
    }

    /**
     * The name of the exporter that wrote the container.
     *
     * @return The exporter, or null if it is missing.
     */
    public String exporter() {
        return properties.get(EXPORTER);
    }

    /**
     * The version of the exporter that wrote the container.
     *
     * @return The exporter version, or null if it is missing.
     */
    public String exporterVersion() {
        return properties.get(EXPORTER_VERSION);
    }

    /**
     * Encode the properties as a META section payload.
     *
     * @return A new little-endian buffer, positioned at 0, holding the payload.
     */
    public ByteBuffer encode() {
        byte[][] strings = new byte[properties.size() * 2][];
        int size = Integer.BYTES;
        int i = 0;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            strings[i] = entry.getKey().getBytes(StandardCharsets.UTF_8);
            strings[i + 1] = entry.getValue().getBytes(StandardCharsets.UTF_8);
            size += 2 * Integer.BYTES + strings[i].length + strings[i + 1].length;
            i += 2;
        }

        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(properties.size());
        for (byte[] string : strings) {
            buffer.putInt(string.length);
            buffer.put(string);
        }
        return buffer.flip();
    }

    /**
     * Decode a META section payload. Missing required keys are not an error here, the validator
     * reports those.
     *
     * @param payload The payload, from its position to its limit. The buffer is not modified.
     * @return The decoded metadata.
     * @throws AssetFormatException If the payload is malformed.
     */
    public static AssetMetadata decode(@NonNull ByteBuffer payload) {
        ByteBuffer buffer = payload.slice().order(ByteOrder.LITTLE_ENDIAN);
        try {
            long count = Integer.toUnsignedLong(buffer.getInt());
            // Each pair needs at least its two lengths, which bounds the count before we allocate
            if (count > buffer.remaining() / (2L * Integer.BYTES)) {
                throw new AssetFormatException(
                        "Metadata claims " + count + " properties, more than fit in the section");
            }
            Map<String, String> properties = new LinkedHashMap<>();
            for (long i = 0; i < count; ++i) {
                String key = readString(buffer);
                String value = readString(buffer);
                if (properties.put(key, value) != null) {
                    throw new AssetFormatException("Metadata key repeated: " + key);
                }
            }
            if (buffer.hasRemaining()) {
                throw new AssetFormatException(
                        "Metadata has " + buffer.remaining() + " bytes after the last property");
            }
            return new AssetMetadata(properties);
        } catch (BufferUnderflowException e) {
            throw new AssetFormatException("Metadata ends partway through a property", e);
        }
    }

    /**
     * Read a length-prefixed UTF-8 string.
     *
     * @param buffer The buffer to read from, which is advanced past the string.
     * @return The string.
     * @throws AssetFormatException If the length runs past the end or the bytes are not UTF-8.
     */
    private static String readString(ByteBuffer buffer) {
        long length = Integer.toUnsignedLong(buffer.getInt());
        if (length > buffer.remaining()) {
            throw new AssetFormatException(
                    "Metadata string of " + length + " bytes runs past the end of the section");
        }
        ByteBuffer bytes = buffer.slice(buffer.position(), (int) length);
        buffer.position(buffer.position() + (int) length);
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(bytes)
                    .toString();
        } catch (CharacterCodingException e) {
            throw new AssetFormatException("Metadata string is not valid UTF-8", e);
        }
    }
}
