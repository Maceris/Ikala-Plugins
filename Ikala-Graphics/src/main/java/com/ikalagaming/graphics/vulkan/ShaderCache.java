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
import java.util.HexFormat;

/**
 * Compiled SPIR-V on disk, one {@value #EXTENSION} file per module, named by a hash of the source
 * with its includes and everything else that changes what the compiler makes, so a shader whose
 * source and includes haven't changed since any earlier session is read instead of compiled.
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

    /** Changes when what goes into a key changes, so old files are never read. */
    private static final int FORMAT_VERSION = 1;

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
     * Read a module, if it is there and looks like SPIR-V. A file that doesn't is a miss, and is
     * deleted so the next compile replaces it.
     *
     * @param key The module's key.
     * @return The SPIR-V bytes, or null.
     */
    byte[] load(@NonNull String key) {
        final Path file = fileOf(key);
        try {
            final byte[] bytes = Files.readAllBytes(file);
            if (!isSpirv(bytes)) {
                log.warn("The cached shader {} isn't SPIR-V, compiling it again", file);
                deleteQuietly(file);
                return null;
            }
            return bytes;
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
        final byte[] bytes = new byte[spirv.remaining()];
        spirv.duplicate().get(bytes);
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
     * Where a module's file is.
     *
     * @param key The module's key.
     * @return The path.
     */
    Path fileOf(@NonNull String key) {
        return folder.resolve(key + EXTENSION);
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
