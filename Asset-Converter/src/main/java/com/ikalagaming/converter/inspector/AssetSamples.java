package com.ikalagaming.converter.inspector;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.graphics.asset.AssetMetadata;
import com.ikalagaming.graphics.asset.AssetWriter;
import com.ikalagaming.graphics.asset.SectionTag;

import lombok.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Writes sample containers for trying out the asset inspector: a valid one, one with a section no
 * reader knows, and a few broken ones. They are written through the real writer, and the broken
 * ones are damaged afterwards, so they look like real files.
 */
public final class AssetSamples {
    /** A section of floats, known to nobody, so readers skip it. */
    static final int DEMO = SectionTag.of("DEMO");

    /** A section of UTF-8 text, also skipped by readers. */
    static final int NOTE = SectionTag.of("NOTE");

    /** A section from a made up future version of the format. */
    static final int FUTURE = SectionTag.of("FUTR");

    /** The folder samples go in, under the working directory. */
    public static final String FOLDER = "asset-samples";

    /** The valid sample. */
    public static final String VALID = "valid.ika";

    /** A sample with an unknown optional section, which is still readable. */
    public static final String UNKNOWN_OPTIONAL = "unknown-optional.ika";

    /** A sample with an unknown required section, which readers must reject. */
    public static final String UNKNOWN_REQUIRED = "unknown-required.ika";

    /** A sample with a damaged payload. */
    public static final String BAD_CHECKSUM = "bad-checksum.ika";

    /** A sample cut short. */
    public static final String TRUNCATED = "truncated.ika";

    /**
     * Write every sample into a folder, creating it if needed.
     *
     * @param folder Where to write them.
     * @return The files written.
     * @throws IOException If a file can't be written.
     */
    public static List<Path> write(@NonNull Path folder) throws IOException {
        Files.createDirectories(folder);
        List<Path> written = new ArrayList<>();

        byte[] valid = toBytes(base().toBuffer());
        written.add(write(folder.resolve(VALID), valid));

        written.add(
                write(
                        folder.resolve(UNKNOWN_OPTIONAL),
                        toBytes(base().section(FUTURE, 3, false, floats(9, 8, 7)).toBuffer())));
        written.add(
                write(
                        folder.resolve(UNKNOWN_REQUIRED),
                        toBytes(base().section(FUTURE, 3, true, floats(9, 8, 7)).toBuffer())));

        byte[] damaged = valid.clone();
        // Flip a bit near the end, which is inside the last section's payload
        damaged[damaged.length - 2] ^= 0x10;
        written.add(write(folder.resolve(BAD_CHECKSUM), damaged));

        written.add(write(folder.resolve(TRUNCATED), Arrays.copyOf(valid, valid.length - 10)));
        return written;
    }

    /**
     * A writer with the metadata and the sections every sample shares.
     *
     * @return The writer.
     */
    private static AssetWriter base() {
        return new AssetWriter()
                .metadata(
                        AssetMetadata.of(ConverterPlugin.PLUGIN_NAME, "samples")
                                .with(AssetMetadata.SOURCE, "AssetSamples"))
                .section(DEMO, 1, false, floats(0.5f, 1.5f, 2.5f, 3.5f))
                .section(
                        NOTE,
                        1,
                        false,
                        ByteBuffer.wrap(
                                "Sample container for the asset inspector."
                                        .getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Little-endian floats.
     *
     * @param values The values.
     * @return A buffer holding them.
     */
    private static ByteBuffer floats(float... values) {
        ByteBuffer buffer =
                ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            buffer.putFloat(value);
        }
        return buffer.flip();
    }

    /**
     * Copy a buffer's contents into an array.
     *
     * @param buffer The buffer, from its position to its limit.
     * @return The bytes.
     */
    private static byte[] toBytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    /**
     * Write a file, replacing it if it exists.
     *
     * @param path Where to write.
     * @param bytes What to write.
     * @return The path.
     * @throws IOException If it can't be written.
     */
    private static Path write(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
        return path;
    }

    /** Static helpers only. */
    private AssetSamples() {}
}
