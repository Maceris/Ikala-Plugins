package com.ikalagaming.database;

import com.ikalagaming.launcher.Constants;
import com.ikalagaming.plugins.Plugin;

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

	private Database database;

	@Override
	public boolean onDisable() {
		database.closeConnection();
		database = null;
		return true;
	}

	@Override
	public boolean onEnable() {
		final String location =
			System.getProperty("user.dir") + Constants.PLUGIN_FOLDER_PATH
				+ DatabasePlugin.PLUGIN_NAME + Constants.DATA_PATH + "database";
		database = new Database(location);
		database.createConnection();
		return true;
	}
}
