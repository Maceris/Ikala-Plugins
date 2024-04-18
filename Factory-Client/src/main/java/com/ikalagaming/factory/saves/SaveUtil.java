package com.ikalagaming.factory.saves;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.util.FileUtils;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class SaveUtil {

    /**
     * The format of a valid save folder / name. The folder name on the file system will match the
     * save name.
     */
    public static final String NAME_FORMAT = "[0-9a-zA-Z][0-9a-zA-Z-._ ]*";

    /** The full path to the folder where all the save folders will be stored. */
    public static final String SAVES_FOLDER =
            UserDataUtil.USER_DATA_FOLDER + File.separator + "saves";

    /**
     * Create a folder for the save game if it does not already exist.
     *
     * @param name The save name, which must follow the {@link #NAME_FORMAT} format.
     * @return Whether we created the folder.
     */
    public static boolean createSaveFolder(@NonNull String name) {
        if (!name.matches(NAME_FORMAT)) {
            log.error(
                    SafeResourceLoader.getStringFormatted(
                            "SAVE_FOLDER_INVALID_FORMAT",
                            FactoryClientPlugin.getResourceBundle(),
                            name));
            return false;
        }
        return FileUtils.createFolder(SAVES_FOLDER, name.trim());
    }

    /**
     * List the names of all existing saves.
     *
     * @return The list of save game names.
     */
    public static List<String> getSaves() {
        return FileUtils.getFile(SAVES_FOLDER).map(File::listFiles).stream()
                .flatMap(Arrays::stream)
                .filter(File::isDirectory)
                .map(File::getName)
                .toList();
    }

    /** Private constructor so this class is not instantiated. */
    private SaveUtil() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
