package com.ikalagaming.factory.saves;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.auth.Crypto;
import com.ikalagaming.launcher.AppPaths;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.security.KeyPair;

/** Utilities relating to the user data folder and its shared contents. */
@Slf4j
public class UserDataUtil {

    /** The folder in the application's Home that holds this computer's identity. */
    public static final String IDENTITY_FOLDER = "identity";

    /** The file in the identity folder that holds the keys for signing in to servers. */
    public static final String KEYS_FILE_NAME = "identity.key";

    /** Create the identity folder in the Home if it does not already exist. */
    public static void createUserDataFolder() {
        File folder = UserDataUtil.keysFile().getParentFile();
        if (!folder.exists() && !folder.mkdirs()) {
            log.error(
                    SafeResourceLoader.getStringFormatted(
                            "DATA_FOLDER_CREATION_FAILED",
                            FactoryPlugin.getResourceBundle(),
                            folder.toString()));
        }
    }

    /**
     * The file that holds the keys for signing in to servers.
     *
     * @return The keys file, in the identity folder in the application's Home.
     */
    private static File keysFile() {
        return AppPaths.home()
                .resolve(UserDataUtil.IDENTITY_FOLDER)
                .resolve(UserDataUtil.KEYS_FILE_NAME)
                .toFile();
    }

    /**
     * Generate a new key pair, store it in the expected file for next time, then return it.
     *
     * @return The newly generated key pair.
     */
    private static KeyPair generateAndStoreKeyPair() {
        log.debug(
                SafeResourceLoader.getString(
                        "KEY_FILE_GENERATING", FactoryPlugin.getResourceBundle()),
                UserDataUtil.keysFile());
        KeyPair result = Crypto.generateKeyPair();
        if (!Crypto.writeKeysToFile(result, UserDataUtil.keysFile())) {
            log.warn(
                    SafeResourceLoader.getString(
                            "KEY_FILE_CANT_CREATE", FactoryPlugin.getResourceBundle()));
        }
        return result;
    }

    /**
     * Load the key pair from the user data folder, or generate and store one then return that.
     *
     * @return The key pair for this computer.
     */
    public static KeyPair getKeyPair() {
        return Crypto.readKeysFromFile(UserDataUtil.keysFile())
                .orElseGet(UserDataUtil::generateAndStoreKeyPair);
    }

    /** Private constructor so that this class is not instantiated. */
    private UserDataUtil() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
