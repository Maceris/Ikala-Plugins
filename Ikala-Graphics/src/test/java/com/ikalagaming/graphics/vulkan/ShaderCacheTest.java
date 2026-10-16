package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Tests for caching compiled shader modules on disk. */
class ShaderCacheTest {

    /** A compiler description for keys. */
    private static final String COMPILER = "test compiler";

    /**
     * Bytes shaped like a SPIR-V module: the magic number, the rest of a header, and a body.
     *
     * @param words How many words after the magic number.
     * @return The bytes.
     */
    private static byte[] spirv(int words) {
        final ByteBuffer buffer =
                ByteBuffer.allocate(Integer.BYTES * (words + 1)).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0x07230203);
        for (int i = 0; i < words; ++i) {
            buffer.putInt(i * 31 + 7);
        }
        return buffer.array();
    }

    /** A saved module reads back the same, and saving leaves the buffer where it was. */
    @Test
    void testRoundTrip(@TempDir Path folder) {
        final ShaderCache cache = new ShaderCache(folder.resolve("shaders"));
        final String key = ShaderCache.key("void main() {}", 1, COMPILER);
        final byte[] module = spirv(12);
        final ByteBuffer buffer = ByteBuffer.allocateDirect(module.length + 8);
        buffer.position(4);
        buffer.put(module);
        buffer.position(4).limit(4 + module.length);

        cache.save(key, buffer);

        assertEquals(4, buffer.position(), "Saving doesn't move the buffer");
        assertArrayEquals(module, new ShaderCache(folder.resolve("shaders")).load(key));
    }

    /** A key never saved is a miss, even before the folder exists. */
    @Test
    void testMiss(@TempDir Path folder) {
        final ShaderCache cache = new ShaderCache(folder.resolve("missing"));
        assertNull(cache.load(ShaderCache.key("void main() {}", 1, COMPILER)));
    }

    /** Keys change with the source, the shader kind and the compiler. */
    @Test
    void testKeysFollowInputs() {
        final String key = ShaderCache.key("void main() {}", 1, COMPILER);
        assertEquals(key, ShaderCache.key("void main() {}", 1, COMPILER));
        assertNotEquals(key, ShaderCache.key("void main() { }", 1, COMPILER));
        assertNotEquals(key, ShaderCache.key("void main() {}", 2, COMPILER));
        assertNotEquals(key, ShaderCache.key("void main() {}", 1, COMPILER + " v2"));
    }

    /** A file that isn't SPIR-V is a miss, and is deleted. */
    @Test
    void testCorruptFileDropped(@TempDir Path folder) throws IOException {
        final ShaderCache cache = new ShaderCache(folder);
        final String key = ShaderCache.key("void main() {}", 1, COMPILER);
        cache.save(key, ByteBuffer.wrap(spirv(12)));
        final Path file = cache.fileOf(key);
        final byte[] bytes = Files.readAllBytes(file);
        bytes[0] ^= (byte) 0x5A;
        Files.write(file, bytes);

        assertNull(cache.load(key));
        assertFalse(Files.exists(file), "The damaged file was deleted");
    }

    /** A file cut short of whole words is a miss. */
    @Test
    void testTruncatedFileDropped(@TempDir Path folder) throws IOException {
        final ShaderCache cache = new ShaderCache(folder);
        final String key = ShaderCache.key("void main() {}", 1, COMPILER);
        final byte[] module = spirv(12);
        Files.write(cache.fileOf(key), Arrays.copyOf(module, module.length - 1));

        assertNull(cache.load(key));
    }
}
