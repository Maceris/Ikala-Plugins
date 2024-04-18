package com.ikalagaming.factory.saves;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.auth.Crypto;
import com.ikalagaming.util.SafeResourceLoader;
import com.ikalagaming.util.SystemProperties;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.security.KeyPair;

/** Utilities relating to the user data folder and its shared contents. */
@Slf4j
public class UserDataUtil {

    /** The full path to the folder where all the game data will be stored. */
    public static final String USER_DATA_FOLDER =
            SystemProperties.getHomeDir() + File.separator + ".lotomation";

    /** The full path to the file where we store private and public keys for authorization. */
    private static final String KEYS_FILE = USER_DATA_FOLDER + File.separator + "auth_keys.pem";

    /** Create the user data folder if it does not already exist. */
    public static void createUserDataFolder() {
        File folder = new File(USER_DATA_FOLDER);
        if (!folder.exists() && (!folder.mkdir())) {
            log.error(
                    SafeResourceLoader.getStringFormatted(
                            "DATA_FOLDER_CREATION_FAILED",
                            FactoryPlugin.getResourceBundle(),
                            USER_DATA_FOLDER));
        }
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
                KEYS_FILE);
        KeyPair result = Crypto.generateKeyPair();
        if (!Crypto.writeKeysToFile(result, new File(KEYS_FILE))) {
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
        return Crypto.readKeysFromFile(new File(KEYS_FILE))
                .orElseGet(UserDataUtil::generateAndStoreKeyPair);
    }

    /** Private constructor so that this class is not instantiated. */
    private UserDataUtil() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
