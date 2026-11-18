package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.GraphicsManager;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.CRC32C;

/**
 * Compiled SPIR-V on disk, one {@value #EXTENSION} file per module, named by a hash of the source
 * with its includes and everything else that changes what the compiler makes, so a shader whose
 * source and includes haven't changed since any earlier session is read instead of compiled. Each
 * file starts with a small header holding the SPIR-V's checksum, so a damaged file is a miss and is
 * compiled again rather than handed to the driver.
 *
 * <p>It lives in a {@value #FOLDER} folder inside the level of detail cache's folder. The LOD cache
 * only counts and evicts its own files at the top of that folder, so it never sees these. Nothing
 * is evicted here: modules are small, and an edit only leaves one stale file behind.
 *
 * <p>Safe from any thread.
 */
@Slf4j
final class ShaderCache {

    /** The folder inside the level of detail cache's folder. */
    static final String FOLDER = "shaders";

    /** The extension of a module's file. */
    static final String EXTENSION = ".spv";

    /** The extension of a file being written. */
    private static final String WRITING = ".tmp";

    /**
     * Changes when what goes into a key or the file layout changes, so old files are never read.
     * Version 2 added the header with a checksum.
     */
    private static final int FORMAT_VERSION = 2;

    /** "IKSV" as a little endian int, the first word of every cache file. */
    private static final int FILE_MAGIC = 0x56534B49;

    /** The bytes before the SPIR-V in a file: the magic, the format version and the CRC-32C. */
    static final int FILE_HEADER_BYTES = Integer.BYTES * 3;

    /**
     * The largest file read. Modules are tens of kilobytes; anything near this is damage, not a
     * shader.
     */
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

    /** The first word of every SPIR-V module. */
    private static final int SPIRV_MAGIC = 0x07230203;

    /** The bytes in a SPIR-V header, five words. */
    private static final int SPIRV_HEADER_BYTES = 20;

    /** The folder. */
    private final Path folder;

    /**
     * A cache in a folder, made when the first module is saved.
     *
     * @param folder The folder.
     */
    ShaderCache(@NonNull Path folder) {
        this.folder = folder;
    }

    /**
     * The cache the graphics settings ask for, in the level of detail cache's folder.
     *
     * @return The cache, or null if caching is off.
     */
    static ShaderCache fromSettings() {
        final String folder = GraphicsManager.getSettings().lodCacheDirectory;
        if (folder == null || folder.isBlank()) {
            return null;
        }
        return new ShaderCache(Path.of(folder, FOLDER));
    }

    /**
     * The key for a module.
     *
     * @param source The module's source followed by the files it includes.
     * @param kind The shaderc shader kind.
     * @param compiler What compiled it and how, such as the library version and options, so
     *     changing either compiles again.
     * @return The key, a hex hash.
     */
    static String key(@NonNull String source, int kind, @NonNull String compiler) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform has it
            throw new IllegalStateException(e);
        }
        final ByteBuffer header = ByteBuffer.allocate(Integer.BYTES * 2);
        header.putInt(FORMAT_VERSION).putInt(kind);
        digest.update(header.array());
        digest.update(compiler.getBytes(StandardCharsets.UTF_8));
        // So the compiler string and source can't run into each other
        digest.update((byte) 0);
        digest.update(source.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Read a module, if it is there, whole, and looks like SPIR-V. A file that isn't is a miss, and
     * is deleted so the next compile replaces it.
     *
     * @param key The module's key.
     * @return The SPIR-V bytes, or null.
     */
    byte[] load(@NonNull String key) {
        final Path file = fileOf(key);
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                log.warn("The cached shader {} is too large, compiling it again", file);
                deleteQuietly(file);
                return null;
            }
            final byte[] spirv = unwrap(Files.readAllBytes(file));
            if (spirv == null) {
                log.warn("The cached shader {} is damaged, compiling it again", file);
                deleteQuietly(file);
                return null;
            }
            return spirv;
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            log.warn(
                    "The cached shader {} can't be read, compiling it again: {}",
                    file,
                    e.toString());
            deleteQuietly(file);
            return null;
        }
    }

    /**
     * Write a module, replacing the file whole. A module that can't be written is only logged: it
     * will be compiled again next time.
     *
     * @param key The module's key.
     * @param spirv The SPIR-V bytes, from its position to its limit, which are left as they were.
     */
    void save(@NonNull String key, @NonNull ByteBuffer spirv) {
        final byte[] bytes = wrap(spirv);
        final Path file = fileOf(key);
        final Path writing = folder.resolve(key + EXTENSION + WRITING);
        try {
            Files.createDirectories(folder);
            Files.write(writing, bytes);
            try {
                Files.move(
                        writing,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(writing, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("The shader {} couldn't be cached: {}", file, e.toString());
            deleteQuietly(writing);
        }
    }

    /**
     * Forget a module the driver refused even though its file was whole, so it is compiled and
     * saved again.
     *
     * @param key The module's key.
     */
    void drop(@NonNull String key) {
        deleteQuietly(fileOf(key));
    }

    /**
     * Where a module's file is.
     *
     * @param key The module's key.
     * @return The path.
     */
    Path fileOf(@NonNull String key) {
        return folder.resolve(key + EXTENSION);
    }

    /**
     * Lay out a module as its file holds it: the magic, the format version and the SPIR-V's
     * CRC-32C, then the SPIR-V.
     *
     * @param spirv The SPIR-V bytes, from its position to its limit, which are left as they were.
     * @return The file's bytes.
     */
    static byte[] wrap(@NonNull ByteBuffer spirv) {
        final ByteBuffer body = spirv.duplicate();
        final CRC32C crc = new CRC32C();
        crc.update(body.duplicate());
        final ByteBuffer out =
                ByteBuffer.allocate(FILE_HEADER_BYTES + body.remaining())
                        .order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(FILE_MAGIC).putInt(FORMAT_VERSION).putInt((int) crc.getValue()).put(body);
        return out.array();
    }

    /**
     * The SPIR-V in a module's file, if the file is whole: the header matches, the checksum is
     * right, and what follows could be a SPIR-V module.
     *
     * @param file The file's bytes.
     * @return The SPIR-V bytes, or null if the file is damaged or from another version.
     */
    static byte[] unwrap(byte @NonNull [] file) {
        if (file.length < FILE_HEADER_BYTES) {
            return null;
        }
        final ByteBuffer in = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        if (in.getInt(0) != FILE_MAGIC || in.getInt(Integer.BYTES) != FORMAT_VERSION) {
            return null;
        }
        final byte[] spirv = Arrays.copyOfRange(file, FILE_HEADER_BYTES, file.length);
        final CRC32C crc = new CRC32C();
        crc.update(spirv);
        if ((int) crc.getValue() != in.getInt(Integer.BYTES * 2) || !isSpirv(spirv)) {
            return null;
        }
        return spirv;
    }

    /**
     * Whether bytes could be a SPIR-V module: whole words, at least a header, and the magic number
     * first.
     *
     * @param bytes The bytes.
     * @return True if they could be.
     */
    private static boolean isSpirv(byte[] bytes) {
        if (bytes.length < SPIRV_HEADER_BYTES || bytes.length % Integer.BYTES != 0) {
            return false;
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(0) == SPIRV_MAGIC;
    }

    /**
     * Delete a file, ignoring failure.
     *
     * @param file The file.
     */
    private static void deleteQuietly(@NonNull Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // Compiled again next time either way
        }
    }
}
