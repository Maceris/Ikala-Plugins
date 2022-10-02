package com.ikalagaming.database;

import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;

import java.io.File;

/**
 * The plugin class for the database plugin.
 *
 * @author Ches Burks
 *
 */
public class DatabasePlugin extends Plugin {
	/**
	 * The name of the plugin in Java for convenience, should match the name in
	 * plugin.yml.
	 */
	public static final String PLUGIN_NAME = "Ikala-Database";

	@Getter
	private Database database;

	@Override
	public boolean onDisable() {
		this.database.closeConnection();
		this.database = null;
		return true;
	}

	@Override
	public boolean onEnable() {
		File db = PluginFolder.getResource(DatabasePlugin.PLUGIN_NAME,
			ResourceType.DATA, "mainDatabase");
		this.database = new Database(db.getAbsolutePath());
		this.database.createConnection();
		return true;
	}
}
