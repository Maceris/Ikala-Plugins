package com.ikalagaming.database;

import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 * The plugin class for the database plugin.
 *
 * @author Ches Burks
 */
@Slf4j
public class DatabasePlugin extends Plugin {
    /** The name of the plugin in Java for convenience, should match the name in plugin.yml. */
    public static final String PLUGIN_NAME = "Ikala-Database";

    @Getter private Database database;

    @Override
    public String getName() {
        return DatabasePlugin.PLUGIN_NAME;
    }

    @Override
    public boolean onLoad() {
        File db =
                PluginFolder.getResource(
                        DatabasePlugin.PLUGIN_NAME, ResourceType.DATA, "mainDatabase");
        database = new Database(db.getAbsolutePath());
        log.debug("Database object created for {}", db.getAbsolutePath());
        database.createConnection();
        return true;
    }

    @Override
    public boolean onUnload() {
        database.closeConnection();
        database = null;
        return true;
    }
}
