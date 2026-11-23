package com.ikalagaming.factory.saves;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.auth.Identity;
import com.ikalagaming.factory.auth.KnownHosts;
import com.ikalagaming.launcher.AppPaths;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The player's identity in the application's Home: the master secret their key for every server
 * comes from, and the host keys of servers they have joined. See the Accounts and Identity design
 * doc.
 */
@Slf4j
public class UserDataUtil {

    /** The folder in the application's Home that holds this computer's identity. */
    public static final String IDENTITY_FOLDER = "identity";

    /** The file in the identity folder that holds the master secret. */
    public static final String KEYS_FILE_NAME = "identity.key";

    /** Guards loading the identity. */
    private static final ReentrantLock LOCK = new ReentrantLock();

    /** The identity, once loaded. */
    private static Identity identity;

    /** Create the identity folder in the Home if it does not already exist. */
    public static void createUserDataFolder() {
        final Path folder = keysFile().getParent();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            log.error(
                    SafeResourceLoader.getStringFormatted(
                            "DATA_FOLDER_CREATION_FAILED",
                            FactoryPlugin.getResourceBundle(),
                            folder.toString()),
                    e);
        }
    }

    /**
     * The file that holds the master secret.
     *
     * @return The file, in the identity folder in the application's Home.
     */
    private static Path keysFile() {
        return AppPaths.home().resolve(IDENTITY_FOLDER).resolve(KEYS_FILE_NAME);
    }

    /**
     * The player's identity, made the first time it is needed.
     *
     * @return The identity.
     * @throws UncheckedIOException If the file can't be read or written.
     */
    public static Identity identity() {
        LOCK.lock();
        try {
            if (identity == null) {
                identity = Identity.loadOrCreate(keysFile());
            }
            return identity;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * The host keys of servers the player has joined.
     *
     * @return The known hosts, in the Home.
     */
    public static KnownHosts knownHosts() {
        return new KnownHosts(AppPaths.home().resolve(KnownHosts.FILE));
    }

    /** Private constructor so that this class is not instantiated. */
    private UserDataUtil() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
